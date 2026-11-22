package handler

import (
	"context"
	"crypto/rand"
	"fmt"
	"math/big"
	"os"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"
	"patnestServer/internal/middleware"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

const (
	loginCodeTTL         = 5 * time.Minute
	loginCodeWindow      = 10 * time.Minute
	loginCodeWindowLimit = 5
	loginCodeMaxAttempts = 5
)

// authCodeDemo 是演示模式开关。
//
// 开启后验证码会随接口响应返回，由 KMP 用本地通知展示；这不是短信/推送通道，
// 只适合毕业设计演示。生产环境必须关闭（AUTH_CODE_DEMO=false）。
func authCodeDemo() bool {
	v := strings.TrimSpace(os.Getenv("AUTH_CODE_DEMO"))
	if v == "" {
		return false
	}
	b, err := strconv.ParseBool(v)
	return err == nil && b
}

func sixDigitCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1000000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}

// SendLoginCode handles POST /api/v1/auth/send-code
// 仅演示模式：生成 6 位验证码并把 demo_code 返回给 App 展示。
func SendLoginCode(c *gin.Context) {
	if !authCodeDemo() {
		Forbidden(c, "验证码登录未开启")
		return
	}
	if !ipRateLimit(c, "login-code-send", 10, loginCodeWindow) {
		Fail(c, 429, 42901, "验证码发送过于频繁，请稍后再试")
		return
	}

	var req model.SendLoginCodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请填写手机号")
		return
	}
	phone := strings.TrimSpace(req.Phone)
	if !validPhone(phone) {
		BadRequest(c, "请输入正确的 11 位手机号")
		return
	}

	ctx := context.Background()
	var userID int64
	if err := db.Pool.QueryRow(ctx,
		`SELECT id FROM users WHERE phone = $1 AND deleted_at IS NULL AND status = 1`,
		phone).Scan(&userID); err != nil {
		// 不暴露手机号是否注册；演示模式下也不返回 code。
		OK(c, gin.H{"sent": true, "expires_in": int(loginCodeTTL.Seconds())})
		return
	}

	var recent int
	if err := db.Pool.QueryRow(ctx,
		`SELECT count(*) FROM auth_phone_codes
		 WHERE phone = $1 AND created_at > now() - interval '60 seconds'`,
		phone).Scan(&recent); err != nil {
		InternalError(c, "验证码服务暂不可用")
		return
	}
	if recent > 0 {
		Fail(c, 429, 42901, "验证码已发送，请 60 秒后再试")
		return
	}

	var windowCount int
	if err := db.Pool.QueryRow(ctx,
		`SELECT count(*) FROM auth_phone_codes
		 WHERE phone = $1 AND created_at > now() - interval '10 minutes'`,
		phone).Scan(&windowCount); err != nil {
		InternalError(c, "验证码服务暂不可用")
		return
	}
	if windowCount >= loginCodeWindowLimit {
		Fail(c, 429, 42901, "验证码发送次数过多，请 10 分钟后再试")
		return
	}

	code, err := sixDigitCode()
	if err != nil {
		InternalError(c, "生成验证码失败")
		return
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		InternalError(c, "生成验证码失败")
		return
	}

	_, err = db.Pool.Exec(ctx, `
		INSERT INTO auth_phone_codes (phone, code_hash, expires_at, request_ip)
		VALUES ($1, $2, now() + interval '5 minutes', $3)`,
		phone, string(hash), c.ClientIP())
	if err != nil {
		InternalError(c, "保存验证码失败")
		return
	}
	// 顺手清理一天前的记录，表不会无限增长。
	_, _ = db.Pool.Exec(ctx,
		`DELETE FROM auth_phone_codes WHERE created_at < now() - interval '1 day'`)

	OK(c, gin.H{
		"sent":       true,
		"expires_in": int(loginCodeTTL.Seconds()),
		"demo_code":  code,
	})
}

// LoginByCode handles POST /api/v1/auth/login/code
func LoginByCode(c *gin.Context) {
	if !authCodeDemo() {
		Forbidden(c, "验证码登录未开启")
		return
	}

	var req model.CodeLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请填写手机号和验证码")
		return
	}
	phone := strings.TrimSpace(req.Phone)
	code := strings.TrimSpace(req.Code)
	if !validPhone(phone) {
		BadRequest(c, "请输入正确的 11 位手机号")
		return
	}
	if len(code) != 6 {
		BadRequest(c, "请输入 6 位验证码")
		return
	}

	ctx := context.Background()
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		InternalError(c, "登录失败")
		return
	}
	defer tx.Rollback(ctx)

	var (
		codeID    int64
		codeHash  string
		expiresAt time.Time
		attempts  int
	)
	err = tx.QueryRow(ctx, `
		SELECT id, code_hash, expires_at, attempts
		FROM auth_phone_codes
		WHERE phone = $1 AND consumed_at IS NULL
		ORDER BY id DESC
		LIMIT 1
		FOR UPDATE`, phone).
		Scan(&codeID, &codeHash, &expiresAt, &attempts)
	if err != nil {
		Unauthorized(c, "验证码无效或已过期")
		return
	}
	if time.Now().After(expiresAt) {
		_, _ = tx.Exec(ctx, `UPDATE auth_phone_codes SET consumed_at = now() WHERE id = $1`, codeID)
		_ = tx.Commit(ctx)
		Unauthorized(c, "验证码已过期，请重新获取")
		return
	}
	if attempts >= loginCodeMaxAttempts {
		Unauthorized(c, "验证码错误次数过多，请重新获取")
		return
	}
	if err := bcrypt.CompareHashAndPassword([]byte(codeHash), []byte(code)); err != nil {
		_, _ = tx.Exec(ctx,
			`UPDATE auth_phone_codes SET attempts = attempts + 1 WHERE id = $1`, codeID)
		_ = tx.Commit(ctx)
		Unauthorized(c, "验证码不正确")
		return
	}

	tag, err := tx.Exec(ctx,
		`UPDATE auth_phone_codes SET consumed_at = now()
		 WHERE id = $1 AND consumed_at IS NULL`, codeID)
	if err != nil || tag.RowsAffected() != 1 {
		Unauthorized(c, "验证码已使用，请重新获取")
		return
	}

	var user model.User
	err = tx.QueryRow(ctx, `
		SELECT id, username, password_hash, nickname, phone, role, status, login_fail_count, locked_until,
		       totp_enabled, COALESCE(totp_secret, ''), COALESCE(totp_last_step, 0)
		FROM users
		WHERE phone = $1 AND deleted_at IS NULL`, phone).
		Scan(&user.ID, &user.Username, &user.PasswordHash, &user.Nickname, &user.Phone, &user.Role,
			&user.Status, &user.LoginFailCount, &user.LockedUntil,
			&user.TotpEnabled, &user.TotpSecret, &user.TotpLastStep)
	if err != nil {
		Unauthorized(c, "账号不存在或已注销")
		return
	}
	if err := tx.Commit(ctx); err != nil {
		InternalError(c, "登录失败")
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
	user.Role = middleware.EffectiveRole(user.ID, user.Role)
	if user.Role == "admin" {
		Forbidden(c, "管理员账号请使用后台密码登录")
		return
	}

	client := strings.TrimSpace(req.Client)
	if client == "" {
		client = "app"
	}
	finishLogin(c, user, client)
}
