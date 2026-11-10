package middleware

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"time"

	"patnestServer/internal/clientinfo"
	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

var jwtSecret []byte

// accessTTL 是访问令牌有效期，默认 30 分钟，由 JWT_ACCESS_TTL_MINUTES 覆盖。
// 客户端都实现了"撞上 401 就用 refresh_token 静默续期并重放一次"，所以这里可以放心设短。
var accessTTL = 30 * time.Minute

func InitJWT(secret string, ttl time.Duration) {
	jwtSecret = []byte(secret)
	if ttl > 0 {
		accessTTL = ttl
	}
}

// AccessTokenTTLSeconds 给登录/刷新接口回显真实有效期，避免前端按写死的 7200 计算
func AccessTokenTTLSeconds() int { return int(accessTTL.Seconds()) }

type Claims struct {
	UserID   int64  `json:"user_id"`
	Username string `json:"username"`
	Role     string `json:"role"`
	// SessionKey 只有管理员使用：同一账号重复登录会轮换，旧令牌立即失效。
	SessionKey string `json:"sid,omitempty"`
	jwt.RegisteredClaims
}

func GenerateAccessToken(userID int64, username, role, sessionKey string) (string, error) {
	claims := Claims{
		UserID:     userID,
		Username:   username,
		Role:       role,
		SessionKey: sessionKey,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(accessTTL)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Issuer:    "patnest",
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(jwtSecret)
}

func GenerateRefreshToken(userID int64, username, role, sessionKey string) (string, error) {
	claims := Claims{
		UserID:     userID,
		Username:   username,
		Role:       role,
		SessionKey: sessionKey,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(7 * 24 * time.Hour)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Issuer:    "patnest-refresh",
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(jwtSecret)
}

func ParseToken(tokenStr string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		return jwtSecret, nil
	})
	if err != nil {
		return nil, err
	}
	if claims, ok := token.Claims.(*Claims); ok && token.Valid {
		return claims, nil
	}
	return nil, jwt.ErrSignatureInvalid
}

// JWTAuth middleware: validates Bearer token
func JWTAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    20001,
				"message": "缺少登录凭证或格式不正确",
			})
			c.Abort()
			return
		}

		tokenStr := strings.TrimPrefix(authHeader, "Bearer ")
		claims, err := ParseToken(tokenStr)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    20001,
				"message": "登录状态已失效，请重新登录",
			})
			c.Abort()
			return
		}

		// 一次查询同时拿到"账号是否可用 / 当前角色 / 是否有在营机构 / 管理员会话标识"。
		// 角色不直接取令牌里的值，理由见 accountState.effectiveRole。
		state, stateErr := loadAccountState(claims.UserID)
		// 被禁用、被删除，或发生过一次"重置密码/吊销会话"的账号，此前签发的访问令牌不该继续可用
		if stateErr != nil || !state.usable(claims.IssuedAt.Time) {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    20001,
				"message": "登录状态已失效，请重新登录",
			})
			c.Abort()
			return
		}

		role := state.effectiveRole(claims.Role)

		// 管理员会话：同一账号重复登录会轮换 session_key，旧令牌立即失效
		if role == "admin" {
			current := ""
			if state.SessionKey != nil {
				current = *state.SessionKey
			}
			if claims.SessionKey == "" || current != claims.SessionKey {
				c.JSON(http.StatusUnauthorized, gin.H{
					"code":    20003,
					"message": "该管理员账号已在其他设备登录，当前会话已失效",
				})
				c.Abort()
				return
			}
		}

		c.Set("user_id", claims.UserID)
		c.Set("username", claims.Username)
		// role 是"这次请求按哪个角色鉴权"的有效角色，各业务 handler 统一读它
		c.Set("role", role)
		// token_role 保留令牌里的原始值，排查"权限和预期不一致"时能看出是不是旧令牌
		c.Set("token_role", claims.Role)
		c.Set("session_key", claims.SessionKey)
		// 供"改密码后保留当前会话"使用：把吊销点设在本次登录的签发时间
		c.Set("token_issued_at", claims.IssuedAt.Time)

		// 客户端画像：登录时写进 Redis，这里按令牌哈希取回；
		// Redis 没启用或没命中就按 UA 现算，保证 c.GetString("client") 永远有值。
		c.Set("token", tokenStr)
		c.Set("token_hash", clientinfo.HashToken(tokenStr))
		if info, ok := clientinfo.LoadAccess(tokenStr); ok {
			c.Set("client", info.Client)
			c.Set("client_platform", info.Platform)
		} else {
			info = clientinfo.Detect("", c.Request.UserAgent(), role)
			c.Set("client", info.Client)
			c.Set("client_platform", info.Platform)
		}
		c.Next()
	}
}

// accountState 是一次请求真正需要的账号状态。
//
// 这几个字段本来分散在两条 SQL 里（角色来自令牌快照、可用性查一次库、管理员会话再查一次），
// 合并成一条之后每个需要登录的请求只多一次往返，同时顺带解决"令牌里的角色过期"问题。
type accountState struct {
	Status     int16
	InvalidAt  *time.Time
	Role       string
	SessionKey *string
	// HasShop 表示这个账号名下有在营机构（active / inactive）。
	// 早期由后台直接建的机构、或审核通过时角色同步失败的账号，users.role still 是 owner，
	// 但机构记录已经存在，这类账号必须能用机构端。
	HasShop bool
}

func loadAccountState(userID int64) (*accountState, error) {
	// 连不上库时一律按"账号不可用"处理：鉴权不能因为数据库没准备好就默认放行
	if db.Pool == nil {
		return nil, errors.New("database not ready")
	}
	var st accountState
	err := db.Pool.QueryRow(context.Background(), `
		SELECT u.status, u.token_invalid_at, u.role::text, u.session_key,
		       EXISTS (
		           SELECT 1 FROM sitters s
		           WHERE s.user_id = u.id
		             AND s.deleted_at IS NULL
		             AND s.status IN ('active', 'inactive')
		       )
		FROM users u
		WHERE u.id = $1 AND u.deleted_at IS NULL`, userID,
	).Scan(&st.Status, &st.InvalidAt, &st.Role, &st.SessionKey, &st.HasShop)
	if err != nil {
		return nil, err
	}
	return &st, nil
}

// usable 判断账号当前是否还接受这个时间之前签发的令牌。
func (s *accountState) usable(issuedAt time.Time) bool {
	if s.Status != 1 {
		return false
	}
	// 一分钟内签发的令牌一律放过：登录时会顺手吊销旧会话，
	// 应用时钟与数据库时钟的毫秒级偏差可能让刚发出的令牌被判成"早于吊销点"，
	// 表现为登录后立刻 401。这个窗口对安全几乎没有影响（旧令牌最多再活一分钟）
	return s.InvalidAt == nil || issuedAt.Add(time.Minute).After(*s.InvalidAt)
}

// effectiveRole 决定这次请求按哪个角色鉴权。
//
// 令牌里的 role 只是签发瞬间的快照：管理员刚把机构申请审过、账号从 owner 升成 sitter 时，
// 用户手里那张令牌还写着 owner，于是 /sitter/* 全被 RequireRole 拦成 403，
// 用户得重新登录才能恢复——这就是"机构改自己的资料却提示权限不足"的根因。
// 所以角色以数据库为准，令牌里的值只在读不到库时兜底。
//
// 另外，机构记录本身才是"能管机构"的能力凭证：名下有在营机构就按 sitter 鉴权，
// 不依赖 users.role 是否被正确升级过。
func (s *accountState) effectiveRole(tokenRole string) string {
	role := s.Role
	if role == "" {
		role = tokenRole
	}
	if role == "owner" && s.HasShop {
		role = "sitter"
	}
	return role
}

// AccountUsable 判断某个账号当前是否还接受这个时间之前签发的令牌：
// 账号未被禁用、未被软删除，且签发时间晚于最近一次吊销点（token_invalid_at）。
func AccountUsable(userID int64, issuedAt time.Time) bool {
	if db.Pool == nil {
		return false
	}
	st, err := loadAccountState(userID)
	if err != nil {
		return false
	}
	return st.usable(issuedAt)
}

// EffectiveRole 给刷新令牌用：换发新令牌时把角色换成数据库里的当前值，
// 避免旧刷新令牌把过期角色一路续下去（角色调整后最多再活一个访问令牌周期）。
func EffectiveRole(userID int64, fallback string) string {
	if db.Pool == nil {
		return fallback
	}
	st, err := loadAccountState(userID)
	if err != nil {
		return fallback
	}
	return st.effectiveRole(fallback)
}

// RequireRole checks that the authenticated user has one of the allowed roles
func RequireRole(roles ...string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userRole, exists := c.Get("role")
		if !exists {
			c.JSON(http.StatusForbidden, gin.H{"code": 20002, "message": "缺少角色信息"})
			c.Abort()
			return
		}
		role, _ := userRole.(string)
		for _, r := range roles {
			if role == r {
				c.Next()
				return
			}
		}
		c.JSON(http.StatusForbidden, gin.H{"code": 20002, "message": "没有访问该功能的权限"})
		c.Abort()
	}
}
