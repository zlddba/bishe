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

func InitJWT(secret string) {
	jwtSecret = []byte(secret)
}

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
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(2 * time.Hour)),
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

		c.Set("user_id", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("role", claims.Role)
		c.Set("session_key", claims.SessionKey)
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
