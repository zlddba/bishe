package handler

import (
	"context"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// revokeAllSessions 让一个账号的既有会话全部失效：
// 刷新令牌逐条吊销，访问令牌靠 users.token_invalid_at 这个时间点一次性作废
// （JWT 无状态，不维护 jti 黑名单，中间件只需比较 iat 与吊销点）。
// 用于禁用、删除、重置他人密码、退出登录、申请注销。
func revokeAllSessions(userID int64) {
	if userID == 0 {
		return
	}
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL", userID)
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE users SET token_invalid_at = now() WHERE id = $1", userID)
}

// revokeSessionsBefore 只作废 cutoff 之前签发的访问令牌。
// 改密码用它：其它设备需要重新登录，而自己当前这台设备不该因为改了密码就被踢下线。
func revokeSessionsBefore(userID int64, cutoff time.Time) {
	if userID == 0 || cutoff.IsZero() {
		revokeAllSessions(userID)
		return
	}
	// 刷新令牌同样只吊销 cutoff 之前签发的，当前设备这次登录拿到的那张要继续可用，
	// 否则访问令牌一过期就换不到新的，客户端会被静默踢下线。
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL AND created_at < $2",
		userID, cutoff)
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE users SET token_invalid_at = $1 WHERE id = $2", cutoff, userID)
}

// currentTokenIssuedAt 取本次请求令牌的签发时间，取不到时返回零值。
func currentTokenIssuedAt(c *gin.Context) time.Time {
	if v, ok := c.Get("token_issued_at"); ok {
		if ts, ok := v.(time.Time); ok {
			return ts
		}
	}
	return time.Time{}
}
