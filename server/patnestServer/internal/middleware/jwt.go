package middleware

import (
	"context"
	"net/http"
	"strings"
	"time"

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

		if claims.Role == "admin" && !adminSessionValid(claims.UserID, claims.SessionKey) {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    20003,
				"message": "该管理员账号已在其他设备登录，当前会话已失效",
			})
			c.Abort()
			return
		}

		// 被禁用、被删除，或发生过一次"重置密码/吊销会话"的账号，此前签发的访问令牌不该继续可用
		if !AccountUsable(claims.UserID, claims.IssuedAt.Time) {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    20001,
				"message": "登录状态已失效，请重新登录",
			})
			c.Abort()
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("role", claims.Role)
		c.Set("session_key", claims.SessionKey)
		// 供"改密码后保留当前会话"使用：把吊销点设在本次登录的签发时间
		c.Set("token_issued_at", claims.IssuedAt.Time)
		c.Next()
	}
}

// adminSessionValid 校验管理员令牌里的会话标识是否仍是账号当前的会话。
func adminSessionValid(userID int64, sessionKey string) bool {
	if sessionKey == "" || db.Pool == nil {
		return false
	}
	var current *string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT session_key FROM users WHERE id = $1 AND deleted_at IS NULL", userID,
	).Scan(&current); err != nil {
		return false
	}
	return current != nil && *current == sessionKey
}

// AccountUsable 判断某个账号当前是否还接受这个时间之前签发的令牌：
// 账号未被禁用、未被软删除，且签发时间晚于最近一次吊销点（token_invalid_at）。
func AccountUsable(userID int64, issuedAt time.Time) bool {
	if db.Pool == nil {
		return false
	}
	var (
		status    int16
		invalidAt *time.Time
	)
	err := db.Pool.QueryRow(context.Background(), `
		SELECT status, token_invalid_at
		FROM users WHERE id = $1 AND deleted_at IS NULL`, userID,
	).Scan(&status, &invalidAt)
	if err != nil {
		return false
	}
	if status != 1 {
		return false
	}
	// 一分钟内签发的令牌一律放过：登录时会顺手吊销旧会话，
	// 应用时钟与数据库时钟的毫秒级偏差可能让刚发出的令牌被判成"早于吊销点"，
	// 表现为登录后立刻 401。这个窗口对安全几乎没有影响（旧令牌最多再活一分钟）
	return invalidAt == nil || issuedAt.Add(time.Minute).After(*invalidAt)
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
		for _, r := range roles {
			if userRole.(string) == r {
				c.Next()
				return
			}
		}
		c.JSON(http.StatusForbidden, gin.H{"code": 20002, "message": "没有访问该功能的权限"})
		c.Abort()
	}
}
