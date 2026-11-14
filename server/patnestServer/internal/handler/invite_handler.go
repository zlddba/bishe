package handler

import (
	"context"
	"regexp"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// inviteRewardPoints 与迁移 0022 里 fn_grant_invite_reward 的 v_points 保持一致：
// 被邀请人首单完成时，邀请人与被邀请人各得 100 积分（100 积分 = 1 元）。
const inviteRewardPoints = 100

// 邀请码由数据库 BEFORE INSERT 触发器按主键生成：'PET' || lpad(id, 6, '0')。
// 用户 ID 超过 6 位时 lpad 不截断，因此位数下界用 {6,}。
// 注册入口会先把输入转成大写，这里只接受规范形式。
var inviteCodePattern = regexp.MustCompile(`^PET[0-9]{6,}$`)

func validInviteCodeFormat(code string) bool {
	return inviteCodePattern.MatchString(code)
}

// MyInvite handles GET /api/v1/pet-owner/invite
//
// 返回我的邀请码、邀请来的人数、已发放奖励的人数，以及我作为被邀请人
// 是否已经拿到首单奖励。奖励发放本身在数据库触发器里完成，这里只读。
func MyInvite(c *gin.Context) {
	ctx := context.Background()
	userID := c.GetInt64("user_id")

	var code string
	var invitedBy int64
	if err := db.Pool.QueryRow(ctx,
		`SELECT COALESCE(invite_code, ''), COALESCE(invited_by, 0)
		   FROM users WHERE id = $1`, userID).Scan(&code, &invitedBy); err != nil {
		InternalError(c, "查询邀请信息失败")
		return
	}

	var invitedCount, rewardedCount int
	_ = db.Pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM users WHERE invited_by = $1 AND deleted_at IS NULL`,
		userID).Scan(&invitedCount)
	_ = db.Pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM invite_rewards WHERE inviter_id = $1`,
		userID).Scan(&rewardedCount)

	var rewardOrderID int64
	_ = db.Pool.QueryRow(ctx,
		`SELECT COALESCE(MAX(order_id), 0) FROM invite_rewards WHERE invitee_id = $1`,
		userID).Scan(&rewardOrderID)

	OK(c, gin.H{
		"invite_code":       code,
		"invited_by":        invitedBy,
		"invited_count":     invitedCount,
		"rewarded_count":    rewardedCount,
		"points_per_invite": inviteRewardPoints,
		"my_reward": gin.H{
			"granted":  rewardOrderID > 0,
			"order_id": rewardOrderID,
		},
	})
}
