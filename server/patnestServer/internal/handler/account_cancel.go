package handler

import (
	"context"
	"fmt"
	"log"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

const cancelCoolingPeriod = 72 * time.Hour

// RequestAccountCancel handles POST /api/v1/pet-owner/account/cancel-request
// 申请注销：进入 3 天冷静期（account=整个账户，sitter=仅注销机构）。
// 冷静期内登录会自动撤销；到期由 account-cancel worker 执行。
func RequestAccountCancel(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var req struct {
		Mode string `json:"mode" binding:"required,oneof=account sitter"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	if req.Mode == "sitter" {
		var hasSitter bool
		if err := db.Pool.QueryRow(context.Background(),
			"SELECT EXISTS(SELECT 1 FROM sitters WHERE user_id = $1 AND deleted_at IS NULL)",
			userID).Scan(&hasSitter); err != nil {
			InternalError(c, "数据库错误")
			return
		}
		if !hasSitter {
			BadRequest(c, "当前账号没有关联机构")
			return
		}
	}

	deadline := time.Now().Add(cancelCoolingPeriod)
	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE users
		 SET cancel_mode = $2, cancel_requested_at = now(), cancel_deadline = $3, updated_at = now()
		 WHERE id = $1 AND deleted_at IS NULL`,
		userID, req.Mode, deadline)
	if err != nil {
		InternalError(c, "提交注销申请失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "用户不存在")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'REQUEST_CANCEL', 'user', $2, $3)",
		userID, userID, fmt.Sprintf(`{"mode": "%s"}`, req.Mode))

	OK(c, gin.H{
		"mode":     req.Mode,
		"deadline": deadline.Format(time.RFC3339),
	})
}

// WithdrawAccountCancel handles POST /api/v1/pet-owner/account/cancel-withdraw
// 撤销注销申请（登录时也会自动撤销，见 Login）。
func WithdrawAccountCancel(c *gin.Context) {
	userID := c.GetInt64("user_id")

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE users
		 SET cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL, updated_at = now()
		 WHERE id = $1 AND cancel_requested_at IS NOT NULL`,
		userID)
	if err != nil {
		InternalError(c, "撤销失败")
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "当前没有注销申请")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'WITHDRAW_CANCEL', 'user', $2, '{}')", userID, userID)

	OK(c, gin.H{"withdrawn": true})
}

// withdrawCancelOnLogin 登录成功后自动撤销注销申请，返回是否发生了撤销。
func withdrawCancelOnLogin(ctx context.Context, userID int64) bool {
	tag, err := db.Pool.Exec(ctx,
		`UPDATE users
		 SET cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL, updated_at = now()
		 WHERE id = $1 AND cancel_requested_at IS NOT NULL`,
		userID)
	if err != nil || tag.RowsAffected() == 0 {
		return false
	}
	_, _ = db.Pool.Exec(ctx,
		"SELECT fn_audit($1, 'WITHDRAW_CANCEL', 'user', $2, '{\"by\": \"login\"}')", userID, userID)
	return true
}

// StartAccountCancelWorker 冷静期到期后自动执行注销（每分钟扫描一次）。
func StartAccountCancelWorker(ctx context.Context, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				runDueCancellations(ctx)
			}
		}
	}()
}

func runDueCancellations(ctx context.Context) {
	rows, err := db.Pool.Query(ctx, `
		SELECT id, cancel_mode FROM users
		WHERE cancel_deadline IS NOT NULL AND cancel_deadline <= now()
		ORDER BY cancel_deadline
		LIMIT 20`)
	if err != nil {
		log.Printf("[account-cancel] query failed: %v", err)
		return
	}
	type due struct {
		id   int64
		mode string
	}
	var dues []due
	for rows.Next() {
		var d due
		if rows.Scan(&d.id, &d.mode) == nil {
			dues = append(dues, d)
		}
	}
	rows.Close()

	for _, d := range dues {
		if d.mode == "sitter" {
			if err := executeSitterCancel(ctx, d.id); err != nil {
				log.Printf("[account-cancel] sitter cancel user %d failed: %v", d.id, err)
			} else {
				log.Printf("[account-cancel] sitter cancel executed for user %d", d.id)
			}
			continue
		}
		if err := purgeUserAccount(ctx, d.id); err != nil {
			// 存在订单等业务数据无法物理删除：降级为软删除，进入后台"已注销"列表由管理员处理
			if _, sErr := db.Pool.Exec(ctx,
				`UPDATE users
				 SET deleted_at = now(), status = 0,
				     cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL,
				     updated_at = now()
				 WHERE id = $1`, d.id); sErr != nil {
				log.Printf("[account-cancel] user %d fallback soft-delete failed: %v", d.id, sErr)
			} else {
				log.Printf("[account-cancel] user %d has business data (%v), fallback to soft delete", d.id, err)
			}
			continue
		}
		log.Printf("[account-cancel] user %d purged", d.id)
	}
}

// executeSitterCancel 仅注销机构：软删除机构、角色退回 owner、清除注销申请。
func executeSitterCancel(ctx context.Context, userID int64) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx,
		"UPDATE sitters SET deleted_at = now(), status = 'inactive', updated_at = now() WHERE user_id = $1 AND deleted_at IS NULL",
		userID); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx,
		`UPDATE users
		 SET role = 'owner',
		     cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL,
		     updated_at = now()
		 WHERE id = $1`, userID); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// purgeUserAccount 彻底删除账户：物理删除用户及其个人数据。
// 业务数据（订单、评价等 RESTRICT 引用）存在时整个事务回滚并返回错误，调用方决定如何处理。
func purgeUserAccount(ctx context.Context, userID int64) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	// 个人数据先删：宠物可能被订单引用、机构可能被订单引用，存在业务关联时会失败并整体回滚
	if _, err := tx.Exec(ctx, "DELETE FROM pets WHERE owner_id = $1", userID); err != nil {
		return fmt.Errorf("删除宠物失败: %w", err)
	}
	if _, err := tx.Exec(ctx, "DELETE FROM sitters WHERE user_id = $1", userID); err != nil {
		return fmt.Errorf("删除机构失败: %w", err)
	}
	// 通知/收藏/积分/优惠券/刷新令牌等为 ON DELETE CASCADE，随用户行一并删除；
	// 订单/评价/动态为 RESTRICT，仍存在时这里失败 → 整体回滚
	if _, err := tx.Exec(ctx, "DELETE FROM users WHERE id = $1", userID); err != nil {
		return fmt.Errorf("删除用户失败: %w", err)
	}
	return tx.Commit(ctx)
}
