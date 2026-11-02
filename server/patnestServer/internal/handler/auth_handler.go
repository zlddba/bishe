package handler

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"time"

	"patnestServer/internal/cache"
	"patnestServer/internal/db"
	"patnestServer/internal/middleware"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

// Register handles POST /api/v1/auth/register
func Register(c *gin.Context) {
	var req model.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}
	if !validPhone(req.Phone) {
		BadRequest(c, "手机号格式不正确")
		return
	}

	// 手机号是永久身份凭证：uq_users_phone 为全表唯一（含软删行），查重口径与其一致，
	// 避免撞唯一约束时只报笼统的"创建用户失败"。
	if req.Phone != "" {
		var phoneTaken bool
		err := db.Pool.QueryRow(context.Background(),
			"SELECT EXISTS(SELECT 1 FROM users WHERE phone = $1)",
			req.Phone).Scan(&phoneTaken)
		if err != nil {
			InternalError(c, "数据库错误")
			return
		}
		if phoneTaken {
			Conflict(c, "该手机号已被注册")
			return
		}
	}

	if req.Role == "admin" {
		Forbidden(c, "不允许注册管理员账号")
		return
	}

	var exists bool
	err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM users WHERE username = $1 AND deleted_at IS NULL)",
		req.Username).Scan(&exists)
	if err != nil {
		InternalError(c, "数据库错误")
		return
	}
	if exists {
		Conflict(c, "该账号已被占用")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		InternalError(c, "密码加密失败")
		return
	}

	var user model.User
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO users (username, password_hash, nickname, phone, role)
		 VALUES ($1, $2, $3, $4, $5)
		 RETURNING id, username, nickname, role, created_at, updated_at`,
		req.Username, string(hash), nilIfEmpty(req.Nickname), nilIfEmpty(req.Phone), req.Role,
	).Scan(&user.ID, &user.Username, &user.Nickname, &user.Role, &user.CreatedAt, &user.UpdatedAt)
	if err != nil {
		InternalError(c, "创建用户失败")
		return
	}

	accessToken, err := middleware.GenerateAccessToken(user.ID, user.Username, user.Role, "")
	if err != nil {
		InternalError(c, "生成访问令牌失败")
		return
	}
	refreshToken, err := middleware.GenerateRefreshToken(user.ID, user.Username, user.Role, "")
	if err != nil {
		InternalError(c, "生成刷新令牌失败")
		return
	}

	storeRefreshToken(user.ID, refreshToken)

	nickname := ""
	if user.Nickname != nil {
		nickname = *user.Nickname
	}

	Created(c, "registration successful", model.AuthResponse{
		User: model.UserPublic{
			ID:       user.ID,
			Username: user.Username,
			Nickname: nickname,
			Role:     user.Role,
		},
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    7200,
	})
}

// Login handles POST /api/v1/auth/login
func Login(c *gin.Context) {
	var req model.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}

	limitKey := cache.LoginLimitKey(c.ClientIP(), req.Username)
	if cache.LoginBlocked(limitKey) {
		Fail(c, http.StatusTooManyRequests, 42901, "登录尝试过于频繁，请 10 分钟后再试")
		return
	}

	var user model.User
	err := db.Pool.QueryRow(context.Background(),
		`SELECT id, username, password_hash, nickname, role, status, login_fail_count, locked_until
		 FROM users
		 WHERE username = $1 AND deleted_at IS NULL`,
		req.Username,
	).Scan(&user.ID, &user.Username, &user.PasswordHash, &user.Nickname, &user.Role,
		&user.Status, &user.LoginFailCount, &user.LockedUntil)

	if err != nil {
		cache.RecordLoginFailure(limitKey)
		Unauthorized(c, "账号或密码不正确")
		return
	}

	if user.Status == 0 {
		Forbidden(c, "账号已被禁用")
		return
	}

	if user.LockedUntil != nil && user.LockedUntil.After(time.Now()) {
		Forbidden(c, "账号已临时锁定，请稍后再试")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		incrementLoginFail(user.ID, user.LoginFailCount)
		cache.RecordLoginFailure(limitKey)
		Unauthorized(c, "账号或密码不正确")
		return
	}

	resetLoginFail(user.ID)
	cache.ClearLoginFailure(limitKey)

	// 桌面端 / 手机端只允许普通用户（主人、机构）登录，管理员请使用后台管理端。
	if req.Client == "app" && user.Role == "admin" {
		Forbidden(c, "管理员账号请使用后台管理端登录")
		return
	}

	// 冷静期内登录即撤销注销申请（"在冷静期中还登录则不会删除账号"）
	cancelWithdrawn := withdrawCancelOnLogin(context.Background(), user.ID)

	// 管理员单会话：每次登录轮换 session_key，并吊销该账号所有旧刷新令牌。
	sessionKey := ""
	if user.Role == "admin" {
		sessionKey = uuid.NewString()
		_, _ = db.Pool.Exec(context.Background(),
			"UPDATE users SET session_key = $1, updated_at = now() WHERE id = $2", sessionKey, user.ID)
		_, _ = db.Pool.Exec(context.Background(),
			"UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL", user.ID)
	}

	accessToken, err := middleware.GenerateAccessToken(user.ID, user.Username, user.Role, sessionKey)
	if err != nil {
		InternalError(c, "生成访问令牌失败")
		return
	}
	refreshToken, err := middleware.GenerateRefreshToken(user.ID, user.Username, user.Role, sessionKey)
	if err != nil {
		InternalError(c, "生成刷新令牌失败")
		return
	}

	storeRefreshToken(user.ID, refreshToken)

	nickname := ""
	if user.Nickname != nil {
		nickname = *user.Nickname
	}

	OK(c, model.AuthResponse{
		User: model.UserPublic{
			ID:       user.ID,
			Username: user.Username,
			Nickname: nickname,
			Role:     user.Role,
		},
		AccessToken:   accessToken,
		RefreshToken:  refreshToken,
		ExpiresIn:     7200,
		SingleSession: user.Role == "admin",
		CancelWithdrawn: cancelWithdrawn,
	})
}

// Refresh handles POST /api/v1/auth/refresh
func Refresh(c *gin.Context) {
	refreshToken := c.PostForm("refresh_token")
	if refreshToken == "" {
		refreshToken, _ = c.Cookie("refresh_token")
	}
	if refreshToken == "" {
		var body struct {
			RefreshToken string `json:"refresh_token"`
		}
		_ = c.ShouldBindJSON(&body)
		refreshToken = body.RefreshToken
	}
	if refreshToken == "" {
		Unauthorized(c, "缺少刷新令牌")
		return
	}

	claims, err := middleware.ParseToken(refreshToken)
	if err != nil {
		Unauthorized(c, "刷新令牌无效或已过期")
		return
	}

	if claims.Issuer != "patnest-refresh" {
		Unauthorized(c, "令牌类型不正确")
		return
	}

	if claims.Role == "admin" {
		var current *string
		_ = db.Pool.QueryRow(context.Background(),
			"SELECT session_key FROM users WHERE id = $1 AND deleted_at IS NULL", claims.UserID).Scan(&current)
		if claims.SessionKey == "" || current == nil || *current != claims.SessionKey {
			Unauthorized(c, "该管理员账号已在其他设备登录，请重新登录")
			return
		}
	}

	tokenHash := sha256Hash(refreshToken)
	var revoked bool
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM refresh_tokens WHERE token_hash = $1 AND revoked_at IS NOT NULL)",
		tokenHash).Scan(&revoked)
	if revoked {
		Unauthorized(c, "令牌已被吊销")
		return
	}

	accessToken, err := middleware.GenerateAccessToken(claims.UserID, claims.Username, claims.Role, claims.SessionKey)
	if err != nil {
		InternalError(c, "生成访问令牌失败")
		return
	}

	OK(c, gin.H{
		"access_token": accessToken,
		"expires_in":   7200,
	})
}

// Logout handles POST /api/v1/auth/logout
func Logout(c *gin.Context) {
	userID := c.GetInt64("user_id")

	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL",
		userID)

	if role, ok := c.Get("role"); ok && role == "admin" {
		_, _ = db.Pool.Exec(context.Background(),
			"UPDATE users SET session_key = NULL WHERE id = $1", userID)
	}

	c.JSON(http.StatusOK, Response{Code: 0, Message: "logged out"})
}

// GetProfile handles GET /api/v1/auth/me
func GetProfile(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var user model.User
	var points int
	var address, contactName, contactPhone *string
	var cancelMode *string
	var cancelDeadline *time.Time
	err := db.Pool.QueryRow(context.Background(),
		`SELECT id, username, nickname, phone, email, role, avatar_url, status, created_at,
		        points, address, contact_name, contact_phone,
		        cancel_mode, cancel_deadline
		 FROM users WHERE id = $1 AND deleted_at IS NULL`,
		userID,
	).Scan(&user.ID, &user.Username, &user.Nickname, &user.Phone, &user.Email,
		&user.Role, &user.AvatarURL, &user.Status, &user.CreatedAt,
		&points, &address, &contactName, &contactPhone,
		&cancelMode, &cancelDeadline)

	if err != nil {
		NotFound(c, "用户不存在")
		return
	}

	var cancelDeadlineStr *string
	if cancelDeadline != nil {
		s := cancelDeadline.Format(time.RFC3339)
		cancelDeadlineStr = &s
	}

	OK(c, gin.H{
		"id":            user.ID,
		"username":      user.Username,
		"nickname":      user.Nickname,
		"phone":         user.Phone,
		"email":         user.Email,
		"role":          user.Role,
		"avatar_url":    user.AvatarURL,
		"status":        user.Status,
		"created_at":    user.CreatedAt,
		"points":        points,
		"address":       address,
		"contact_name":  contactName,
		"contact_phone": contactPhone,
		"cancel_mode":        cancelMode,
		"cancel_deadline":    cancelDeadlineStr,
	})
}

// --- helpers ---

func nilIfEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func sha256Hash(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func storeRefreshToken(userID int64, token string) {
	expiresAt := time.Now().Add(7 * 24 * time.Hour)
	_, _ = db.Pool.Exec(context.Background(),
		"INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)",
		userID, sha256Hash(token), expiresAt)
}

func incrementLoginFail(userID int64, currentCount int16) {
	newCount := currentCount + 1
	if newCount >= 10 {
		lockUntil := time.Now().Add(15 * time.Minute)
		_, _ = db.Pool.Exec(context.Background(),
			"UPDATE users SET login_fail_count = $1, locked_until = $2 WHERE id = $3",
			newCount, lockUntil, userID)
	} else {
		_, _ = db.Pool.Exec(context.Background(),
			"UPDATE users SET login_fail_count = $1 WHERE id = $2",
			newCount, userID)
	}
}

func resetLoginFail(userID int64) {
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE users SET login_fail_count = 0, locked_until = NULL WHERE id = $1",
		userID)
}
