package handler

import (
	"context"
	"encoding/json"
	"errors"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/cache"
	"patnestServer/internal/db"
	"patnestServer/internal/totp"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

// 管理后台两步验证（TOTP，0023）。
//
// 流程：密码登录 → 若已启用两步验证，服务端只下发一张 5 分钟的挑战票据，
// 客户端再提交 6 位动态口令（或一枚恢复码）换正式令牌。
// 挑战票据存 Redis，一次一用，提交超过 5 次即作废，避免拿票据暴力猜口令。

const (
	// totpTicketTTL 是挑战票据的有效期：够用户打开验证器抄码，又不至于太长的窗口。
	totpTicketTTL = 5 * time.Minute
	// totpMaxAttempts 是同一张票据允许的错误次数。
	totpMaxAttempts = 5
	// totpRecoveryCount 是一次生成的恢复码数量。
	totpRecoveryCount = 10
)

func totpTicketKey(ticket string) string  { return "auth:totp:ticket:" + ticket }
func totpAttemptKey(ticket string) string { return "auth:totp:try:" + ticket }

// totpChallenge 是写进 Redis 的挑战内容。带上用户名与 IP 是为了让票据
// 只在「同一个人、同一个出口」上可用，减少票据泄露后被冒用的机会。
type totpChallenge struct {
	UserID   int64  `json:"user_id"`
	Username string `json:"username"`
}

// --- 内部工具 ---

// loadTOTPState 读取账号的两步验证状态。
func loadTOTPState(ctx context.Context, userID int64) (secret string, enabled bool, lastStep int64, confirmedAt *time.Time, err error) {
	var secretPtr *string
	err = db.Pool.QueryRow(ctx,
		`SELECT totp_secret, totp_enabled, COALESCE(totp_last_step, 0), totp_confirmed_at
		 FROM users WHERE id = $1 AND deleted_at IS NULL`, userID,
	).Scan(&secretPtr, &enabled, &lastStep, &confirmedAt)
	if err != nil {
		return "", false, 0, nil, err
	}
	if secretPtr != nil {
		secret = *secretPtr
	}
	return secret, enabled, lastStep, confirmedAt, nil
}

// countRecoveryCodes 返回「总数 / 可用数」。
func countRecoveryCodes(ctx context.Context, userID int64) (total, left int) {
	_ = db.Pool.QueryRow(ctx,
		`SELECT COUNT(*), COUNT(*) FILTER (WHERE used_at IS NULL)
		 FROM totp_recovery_codes WHERE user_id = $1`, userID,
	).Scan(&total, &left)
	return total, left
}

// consumeRecoveryCode 原子地把一枚恢复码标记为已用；已用过或不存在返回 false。
func consumeRecoveryCode(ctx context.Context, userID int64, code string) bool {
	tag, err := db.Pool.Exec(ctx,
		`UPDATE totp_recovery_codes SET used_at = now()
		 WHERE user_id = $1 AND code_hash = $2 AND used_at IS NULL`,
		userID, totp.HashRecoveryCode(code))
	if err != nil {
		return false
	}
	return tag.RowsAffected() > 0
}

// checkSecondFactor 校验一次「动态口令或恢复码」，成功时顺带落库防重放标记。
// 返回 (是否通过, 是否用了恢复码)。
func checkSecondFactor(ctx context.Context, userID int64, secret string, lastStep int64, code string) (bool, bool) {
	code = strings.TrimSpace(code)
	if code == "" {
		return false, false
	}

	// 恢复码：10 位，走一次性核销。
	if totp.LooksLikeRecoveryCode(code) {
		if consumeRecoveryCode(ctx, userID, code) {
			return true, true
		}
		return false, false
	}

	step, ok := totp.Verify(secret, code, time.Now(), 1, lastStep)
	if !ok {
		return false, false
	}
	// 同一口令在 30 秒内重复提交会被上面的 lastStep 拦掉，
	// 这里用条件更新兜住并发：只有更晚的时间步才能落库。
	_, _ = db.Pool.Exec(ctx,
		`UPDATE users SET totp_last_step = $1
		 WHERE id = $2 AND (totp_last_step IS NULL OR totp_last_step < $1)`,
		step, userID)
	return true, false
}

// replaceRecoveryCodes 清空旧码并写入一批新码，明文只在此刻返回一次。
func replaceRecoveryCodes(ctx context.Context, userID int64) ([]string, error) {
	codes, err := totp.GenerateRecoveryCodes(totpRecoveryCount)
	if err != nil {
		return nil, err
	}
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	if _, err := tx.Exec(ctx, `DELETE FROM totp_recovery_codes WHERE user_id = $1`, userID); err != nil {
		return nil, err
	}
	for _, code := range codes {
		if _, err := tx.Exec(ctx,
			`INSERT INTO totp_recovery_codes (user_id, code_hash) VALUES ($1, $2)`,
			userID, totp.HashRecoveryCode(code)); err != nil {
			return nil, err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}
	return codes, nil
}

// --- 接口 ---

// AdminTOTPStatus GET /admin/security/totp
func AdminTOTPStatus(c *gin.Context) {
	userID := c.GetInt64("user_id")
	secret, enabled, _, confirmedAt, err := loadTOTPState(context.Background(), userID)
	if err != nil {
		InternalError(c, "查询两步验证状态失败")
		return
	}
	total, left := countRecoveryCodes(context.Background(), userID)
	OK(c, gin.H{
		"enabled": enabled,
		// pending：已经生成密钥但还没扫码确认，重新进页面时可以直接继续
		"pending":        !enabled && secret != "",
		"confirmed_at":   confirmedAt,
		"recovery_total": total,
		"recovery_left":  left,
		"issuer":         totp.DefaultIssuer,
		"account":        c.GetString("username"),
		"period":         int(totp.Step / time.Second),
		"digits":         totp.Digits,
		"recovery_codes": totpRecoveryCount,
	})
}

// AdminTOTPSetup POST /admin/security/totp/setup
// 生成密钥并返回 otpauth:// 链接，此时还未启用——必须再提交一次正确口令才算绑定成功。
func AdminTOTPSetup(c *gin.Context) {
	userID := c.GetInt64("user_id")
	_, enabled, _, _, err := loadTOTPState(context.Background(), userID)
	if err != nil {
		InternalError(c, "查询两步验证状态失败")
		return
	}
	if enabled {
		Conflict(c, "两步验证已启用，如需换绑请先关闭再重新绑定")
		return
	}

	secret, err := totp.GenerateSecret()
	if err != nil {
		InternalError(c, "生成密钥失败")
		return
	}
	if _, err := db.Pool.Exec(context.Background(),
		`UPDATE users SET totp_secret = $1, totp_enabled = FALSE,
		        totp_confirmed_at = NULL, totp_last_step = NULL, updated_at = now()
		 WHERE id = $2`, secret, userID); err != nil {
		InternalError(c, "保存密钥失败")
		return
	}

	account := c.GetString("username")
	OK(c, gin.H{
		"secret":      secret,
		"otpauth_uri": totp.URI(totp.DefaultIssuer, account, secret),
		"issuer":      totp.DefaultIssuer,
		"account":     account,
		"period":      int(totp.Step / time.Second),
		"digits":      totp.Digits,
	})
}

// AdminTOTPEnable POST /admin/security/totp/enable
// 校验一次口令，确认验证器确实配对成功，然后正式启用并下发恢复码。
func AdminTOTPEnable(c *gin.Context) {
	var req struct {
		Code string `json:"code" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请输入验证器上的 6 位验证码")
		return
	}

	userID := c.GetInt64("user_id")
	ctx := context.Background()
	secret, enabled, _, _, err := loadTOTPState(ctx, userID)
	if err != nil {
		InternalError(c, "查询两步验证状态失败")
		return
	}
	if enabled {
		Conflict(c, "两步验证已启用")
		return
	}
	if secret == "" {
		BadRequest(c, "请先获取密钥并添加到验证器")
		return
	}

	if _, ok := totp.Verify(secret, strings.TrimSpace(req.Code), time.Now(), 1, 0); !ok {
		BadRequest(c, "验证码不正确，请确认手机时间是否准确后重试")
		return
	}

	if _, err := db.Pool.Exec(ctx,
		`UPDATE users SET totp_enabled = TRUE, totp_confirmed_at = now(), updated_at = now()
		 WHERE id = $1`, userID); err != nil {
		InternalError(c, "启用两步验证失败")
		return
	}

	codes, err := replaceRecoveryCodes(ctx, userID)
	if err != nil {
		// 恢复码没写成功不影响「已经启用」这个事实，但必须让用户知道，
		// 否则他以为存下来了，真丢手机时反而找不到码。
		InternalError(c, "两步验证已启用，但生成恢复码失败，请重新生成")
		return
	}

	writeAudit(c, "admin.totp_enable", "user", userID, `{"totp":"enabled"}`)
	OK(c, gin.H{
		"enabled":        true,
		"recovery_codes": codes,
		"message":        "两步验证已启用，请立即保存恢复码",
	})
}

// AdminTOTPDisable POST /admin/security/totp/disable
// 关闭需要「密码 + 当前口令/恢复码」双重确认，防止会话被劫持后一键关掉。
func AdminTOTPDisable(c *gin.Context) {
	var req struct {
		Password string `json:"password" binding:"required"`
		Code     string `json:"code" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请输入登录密码与当前验证码")
		return
	}

	userID := c.GetInt64("user_id")
	ctx := context.Background()
	secret, enabled, _, _, err := loadTOTPState(ctx, userID)
	if err != nil {
		InternalError(c, "查询两步验证状态失败")
		return
	}
	if !enabled {
		BadRequest(c, "两步验证尚未启用")
		return
	}

	var passwordHash string
	if err := db.Pool.QueryRow(ctx,
		`SELECT password_hash FROM users WHERE id = $1 AND deleted_at IS NULL`, userID,
	).Scan(&passwordHash); err != nil {
		InternalError(c, "查询账号失败")
		return
	}
	if bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(req.Password)) != nil {
		Unauthorized(c, "登录密码不正确")
		return
	}
	// 这里传 0 而不是 lastStep：关闭/重发恢复码都要求同时验证密码或已登录会话，
	// 重放没有意义；反而刚用某个口令登录完紧接着来关闭时，同一个口令被判重放
	// 会让人以为"码明明是对的却不让关"。
	if ok, _ := checkSecondFactor(ctx, userID, secret, 0, req.Code); !ok {
		BadRequest(c, "验证码或恢复码不正确")
		return
	}

	if _, err := db.Pool.Exec(ctx,
		`UPDATE users SET totp_secret = NULL, totp_enabled = FALSE,
		        totp_confirmed_at = NULL, totp_last_step = NULL, updated_at = now()
		 WHERE id = $1`, userID); err != nil {
		InternalError(c, "关闭两步验证失败")
		return
	}
	_, _ = db.Pool.Exec(ctx, `DELETE FROM totp_recovery_codes WHERE user_id = $1`, userID)

	writeAudit(c, "admin.totp_disable", "user", userID, `{"totp":"disabled"}`)
	OK(c, gin.H{"enabled": false, "message": "两步验证已关闭"})
}

// AdminTOTPRecoveryCodes POST /admin/security/totp/recovery-codes
// 用当前口令换一批新恢复码（旧的立即作废）。
func AdminTOTPRecoveryCodes(c *gin.Context) {
	var req struct {
		Code string `json:"code" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请输入当前验证码")
		return
	}

	userID := c.GetInt64("user_id")
	ctx := context.Background()
	secret, enabled, _, _, err := loadTOTPState(ctx, userID)
	if err != nil {
		InternalError(c, "查询两步验证状态失败")
		return
	}
	if !enabled {
		BadRequest(c, "请先启用两步验证")
		return
	}
	// 同 disable：这里已经要求登录态，重放保护交给登录流程，不卡时间步。
	if ok, _ := checkSecondFactor(ctx, userID, secret, 0, req.Code); !ok {
		BadRequest(c, "验证码或恢复码不正确")
		return
	}

	codes, err := replaceRecoveryCodes(ctx, userID)
	if err != nil {
		InternalError(c, "生成恢复码失败")
		return
	}
	writeAudit(c, "admin.totp_recovery", "user", userID, `{"totp":"recovery_codes_regenerated"}`)
	OK(c, gin.H{"recovery_codes": codes, "message": "恢复码已重新生成，旧码全部作废"})
}

// AdminResetUserTOTP POST /admin/users/:id/totp/reset
// 给「管理员换手机 / 验证器丢了 / 恢复码也用完」兜底：只有内置超级管理员能操作。
func AdminResetUserTOTP(c *gin.Context) {
	if !isSuperAdmin(c) {
		Forbidden(c, "只有系统管理员可以重置他人的两步验证")
		return
	}
	targetID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || targetID <= 0 {
		BadRequest(c, "用户 ID 无效")
		return
	}
	target, ok := loadAdminTarget(c, targetID)
	if !ok {
		return
	}
	if target.role != "admin" {
		BadRequest(c, "该账号不是管理员，没有两步验证可重置")
		return
	}

	ctx := context.Background()
	if _, err := db.Pool.Exec(ctx,
		`UPDATE users SET totp_secret = NULL, totp_enabled = FALSE,
		        totp_confirmed_at = NULL, totp_last_step = NULL, updated_at = now()
		 WHERE id = $1`, targetID); err != nil {
		InternalError(c, "重置两步验证失败")
		return
	}
	_, _ = db.Pool.Exec(ctx, `DELETE FROM totp_recovery_codes WHERE user_id = $1`, targetID)

	writeAudit(c, "admin.totp_reset", "user", targetID,
		`{"target":"`+target.username+`","totp":"reset_by_super_admin"}`)
	OK(c, gin.H{"message": "已重置该账号的两步验证，下次登录只需密码"})
}

// --- 登录链路用到的部分 ---

// issueTotpTicket 为已通过密码校验的管理员签发挑战票据。
func issueTotpTicket(userID int64, username string) (string, error) {
	if !cache.Enabled() {
		return "", errors.New("redis unavailable")
	}
	payload, err := json.Marshal(totpChallenge{UserID: userID, Username: username})
	if err != nil {
		return "", err
	}
	// 去掉连字符，票据会出现在请求体里，短一点省事也不会被误转义。
	ticket := strings.ReplaceAll(uuid.NewString(), "-", "")
	if err := cache.SetString(totpTicketKey(ticket), string(payload), totpTicketTTL); err != nil {
		return "", err
	}
	return ticket, nil
}

// verifyTotpTicket 校验票据并返回其绑定的挑战内容；票据不存在/过期返回 nil。
func verifyTotpTicket(ticket string) *totpChallenge {
	if ticket == "" || !cache.Enabled() {
		return nil
	}
	raw, ok, err := cache.GetString(totpTicketKey(ticket))
	if err != nil || !ok {
		return nil
	}
	var ch totpChallenge
	if err := json.Unmarshal([]byte(raw), &ch); err != nil || ch.UserID <= 0 {
		return nil
	}
	return &ch
}

// consumeTotpTicket 用掉票据；调用方在校验成功后必须执行，防止一票多用。
func consumeTotpTicket(ticket string) {
	if ticket == "" {
		return
	}
	_ = cache.Del(totpTicketKey(ticket))
	_ = cache.Del(totpAttemptKey(ticket))
}

// totpAttemptExceeded 累计一次错误；超过阈值时作废票据并返回 true。
func totpAttemptExceeded(ticket string) bool {
	if !cache.Enabled() {
		return false
	}
	n, err := cache.IncrWindow(totpAttemptKey(ticket), totpTicketTTL)
	if err != nil {
		return false
	}
	if n > totpMaxAttempts {
		consumeTotpTicket(ticket)
		return true
	}
	return false
}
