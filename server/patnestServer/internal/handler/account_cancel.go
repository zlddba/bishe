package handler

import (
	"context"
	"errors"
	"fmt"
	"log"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

const cancelCoolingPeriod = 72 * time.Hour

// sitterCancelBlocker 记录注销机构前必须先处理完的事项。
//
// 机构注销会把 sitters 行软删，而 mySitterID() 只认 deleted_at IS NULL，
// 所以一旦注销：机构端再也查不到自己的 sitter_id，收益页、结算页全部 404，
// 既看不到也申请不了剩余的錢。因此「平台欠机构的钱」和「机构欠主人的服务」
// 都必须在注销之前了结，否则只能靠人工翻库兜底。
//
// 注意：机构注销后账号仍然保留（role 退回 owner），钱包余额还能正常使用，
// 所以这里不检查钱包——那是 account 模式才需要处理的。
type sitterCancelBlocker struct {
	PendingSettlements int64
	PendingNet         float64
	ActiveOrders       int64
}

// message 拼成一句能直接显示给用户的话；没有问题项时返回空串。
func (b sitterCancelBlocker) message() string {
	var parts []string
	if b.PendingSettlements > 0 {
		parts = append(parts, fmt.Sprintf(
			"有 %d 笔结算正在等待平台打款（共 %.2f 元）", b.PendingSettlements, b.PendingNet))
	}
	if b.ActiveOrders > 0 {
		parts = append(parts, fmt.Sprintf("有 %d 笔订单尚未完成", b.ActiveOrders))
	}
	if len(parts) == 0 {
		return ""
	}
	return strings.Join(parts, "；") + "。处理完这些后再申请注销。"
}

// checkSitterCancelBlockers 汇总注销前的待处理事项。
// 未申请的收益不在这里拦截：真正注销时会自动生成一张待结算单（见 settleBeforeCancel）。
func checkSitterCancelBlockers(ctx context.Context, sitterID int64) (sitterCancelBlocker, error) {
	var b sitterCancelBlocker
	if err := db.Pool.QueryRow(ctx, `
		SELECT COUNT(*), COALESCE(SUM(net_amount), 0)::float8
		FROM sitter_settlements
		WHERE sitter_id = $1 AND status = 'pending'`, sitterID).
		Scan(&b.PendingSettlements, &b.PendingNet); err != nil {
		return b, err
	}
	if err := db.Pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM orders
		WHERE sitter_id = $1 AND deleted_at IS NULL
		  AND status IN ('confirmed','accepted','staying')`, sitterID).
		Scan(&b.ActiveOrders); err != nil {
		return b, err
	}
	return b, nil
}

// RequestAccountCancel handles POST /api/v1/pet-owner/account/cancel-request
// 申请注销：进入 3 天冷静期（account=整个账户，sitter=仅注销机构）。
// 申请成功后立即吊销该用户全部刷新令牌（直接退出登录，所有会话无法再续期）；
// 冷静期内重新登录会自动撤销注销申请；到期由 account-cancel worker 执行。
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
		var sitterID int64
		if err := db.Pool.QueryRow(context.Background(),
			"SELECT id FROM sitters WHERE user_id = $1 AND deleted_at IS NULL",
			userID).Scan(&sitterID); err != nil {
			BadRequest(c, "当前账号没有关联机构")
			return
		}
		// 有待打款结算 / 未完成订单时不让提交：冷静期一过就会自动注销，
		// 机构端随即失去所有自助入口，这些钱和服务就再也没人能推动了。
		blocker, err := checkSitterCancelBlockers(context.Background(), sitterID)
		if err != nil {
			InternalError(c, "检查注销条件失败")
			return
		}
		if msg := blocker.message(); msg != "" {
			BadRequest(c, msg)
			return
		}
	}

	if req.Mode == "account" {
		// 未完成订单：不区分身份，作为主人或作为机构都算，否则注销后没人履约
		var activeOrders int64
		if err := db.Pool.QueryRow(context.Background(), `
			SELECT COUNT(*) FROM orders o
			LEFT JOIN sitters s ON s.id = o.sitter_id
			WHERE o.deleted_at IS NULL
			  AND o.status IN ('confirmed','accepted','staying')
			  AND (o.owner_id = $1 OR s.user_id = $1)`, userID).Scan(&activeOrders); err != nil {
			InternalError(c, "检查注销条件失败")
			return
		}
		if activeOrders > 0 {
			BadRequest(c, fmt.Sprintf("有 %d 笔订单尚未完成，请先处理完再注销", activeOrders))
			return
		}
		// 这个账号如果同时是机构，还有待打款结算的话，注销后就没人能确认收款了
		var pending int64
		var pendingNet float64
		if err := db.Pool.QueryRow(context.Background(), `
			SELECT COUNT(*), COALESCE(SUM(st.net_amount), 0)::float8
			FROM sitter_settlements st JOIN sitters s ON s.id = st.sitter_id
			WHERE s.user_id = $1 AND st.status = 'pending'`, userID).
			Scan(&pending, &pendingNet); err != nil {
			InternalError(c, "检查注销条件失败")
			return
		}
		if pending > 0 {
			BadRequest(c, fmt.Sprintf(
				"有 %d 笔结算正在等待平台打款（共 %.2f 元），请等到账后再注销", pending, pendingNet))
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

	// 申请注销后直接退出登录：吊销全部刷新令牌，与 Logout 的吊销逻辑一致
	revokeAllSessions(userID)

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'REQUEST_CANCEL', 'user', $2, $3, NULLIF($4, '')::inet)",
		userID, userID, fmt.Sprintf(`{"mode": "%s"}`, req.Mode), auditClientIP(c))

	resp := gin.H{
		"mode":     req.Mode,
		"deadline": deadline.Format(time.RFC3339),
	}
	// 账户注销时钱包余额会被转成退款单（不是没收），提前告诉用户金额，
	// 前端可以在提示里说清楚「客服会联系你确认收款方式」
	if req.Mode == "account" {
		var balance float64
		if err := db.Pool.QueryRow(context.Background(),
			`SELECT COALESCE(balance, 0)::float8 FROM wallets WHERE user_id = $1`, userID).
			Scan(&balance); err == nil && balance > 0 {
			resp["wallet_refund_amount"] = balance
		}
	}
	OK(c, resp)
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
		"SELECT fn_audit($1, 'WITHDRAW_CANCEL', 'user', $2, '{}', NULLIF($3, '')::inet)", userID, userID, auditClientIP(c))

	OK(c, gin.H{"withdrawn": true})
}

// withdrawCancelOnLogin 登录成功后自动撤销注销申请，返回是否发生了撤销。
// ip 用于审计日志，登录流程里由调用方从 gin.Context 取。
func withdrawCancelOnLogin(ctx context.Context, userID int64, ip string) bool {
	tag, err := db.Pool.Exec(ctx,
		`UPDATE users
		 SET cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL, updated_at = now()
		 WHERE id = $1 AND cancel_requested_at IS NOT NULL`,
		userID)
	if err != nil || tag.RowsAffected() == 0 {
		return false
	}
	_, _ = db.Pool.Exec(ctx,
		"SELECT fn_audit($1, 'WITHDRAW_CANCEL', 'user', $2, '{\"by\": \"login\"}', NULLIF($3, '')::inet)",
		userID, userID, ip)
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
		  AND cancel_blocked_at IS NULL
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
				recordCancelFailure(ctx, d.id, err)
			} else {
				log.Printf("[account-cancel] sitter cancel executed for user %d", d.id)
			}
			continue
		}
		// 先把钱包余额转成退款单（独立事务，先提交）：
		// 账号一注销用户就登录不了，余额留在 wallets 里没有任何入口能取回。
		// 即使后面物理删除失败退回软删，这笔钱也已经变成后台可见的待退款单。
		if _, err := sweepWalletBeforeCancel(ctx, d.id); err != nil {
			// 转出失败就不要再往下走：先注销账号会让这笔钱彻底无人可动。
			recordCancelFailure(ctx, d.id, fmt.Errorf("余额转退款单失败: %w", err))
			continue
		}
		// account 模式不走 executeSitterCancel，如果这个账号同时是机构，
		// 它的未结算收益必须在这里补上，否则机构行被删/软删之后
		// 那段收益就永远没人能发起结算了。
		if err := settleSitterIfAny(ctx, d.id); err != nil {
			recordCancelFailure(ctx, d.id, fmt.Errorf("机构结算失败: %w", err))
			continue
		}
		if err := purgeUserAccount(ctx, d.id); err != nil {
			// 存在订单等业务数据无法物理删除：降级为软删除，进入后台"已注销"列表由管理员处理。
			// 这是预期内分支，不算失败，不计入重试次数。
			// 同时把机构下架——账号都注销了，机构不能再挂在列表里接单
			if _, sErr := db.Pool.Exec(ctx,
				`UPDATE sitters SET deleted_at = now(), status = 'inactive', updated_at = now()
				 WHERE user_id = $1 AND deleted_at IS NULL`, d.id); sErr != nil {
				log.Printf("[account-cancel] user %d fallback: 下架机构失败: %v", d.id, sErr)
			}
			if _, sErr := db.Pool.Exec(ctx,
				`UPDATE users
				 SET deleted_at = now(), status = 0,
				     cancel_mode = NULL, cancel_requested_at = NULL, cancel_deadline = NULL,
				     cancel_attempts = 0, cancel_last_error = NULL, cancel_blocked_at = NULL,
				     updated_at = now()
				 WHERE id = $1`, d.id); sErr != nil {
				recordCancelFailure(ctx, d.id, fmt.Errorf("降级软删除失败: %w", sErr))
			} else {
				log.Printf("[account-cancel] user %d has business data (%v), fallback to soft delete", d.id, err)
			}
			continue
		}
		log.Printf("[account-cancel] user %d purged", d.id)
	}
}

const maxCancelAttempts = 5

// cancelRetryBackoff 失败后的退避间隔（分钟），超过长度就取最后一个。
var cancelRetryBackoff = []int{1, 5, 15, 30, 60}

func cancelBackoffMinutes(attempts int) int {
	idx := attempts - 1
	if idx < 0 {
		idx = 0
	}
	if idx >= len(cancelRetryBackoff) {
		idx = len(cancelRetryBackoff) - 1
	}
	return cancelRetryBackoff[idx]
}

// recordCancelFailure 记录一次注销执行失败。
//
// 做两件事：
//  1. 把 cancel_deadline 往后推（指数退避）。扫描是
//     `ORDER BY cancel_deadline LIMIT 20`，失败账号如果时间不变会一直排在队首，
//     一条永久失败的数据就能把后面所有待注销账号堵死，日志也会无限刷。
//  2. 累计失败次数，达到上限置 cancel_blocked_at 停止自动处理，并写审计，
//     后台「用户管理」里能看到原因并人工重试。
func recordCancelFailure(ctx context.Context, userID int64, cause error) {
	var attempts int
	if err := db.Pool.QueryRow(ctx,
		`SELECT cancel_attempts FROM users WHERE id = $1`, userID).Scan(&attempts); err != nil {
		log.Printf("[account-cancel] user %d 读取失败次数出错: %v", userID, err)
		return
	}
	attempts++

	msg := cause.Error()
	if len(msg) > 250 {
		msg = msg[:250]
	}

	if attempts >= maxCancelAttempts {
		if _, err := db.Pool.Exec(ctx, `
			UPDATE users
			SET cancel_attempts = $2, cancel_last_error = $3,
			    cancel_blocked_at = now(), updated_at = now()
			WHERE id = $1`, userID, attempts, msg); err != nil {
			log.Printf("[account-cancel] user %d 标记停止重试失败: %v", userID, err)
			return
		}
		_, _ = db.Pool.Exec(ctx,
			`SELECT fn_audit($1, 'ACCOUNT_CANCEL_BLOCKED', 'user', $2, $3, NULL)`,
			userID, userID, fmt.Sprintf(`{"attempts": %d, "error": %q}`, attempts, msg))
		log.Printf("[account-cancel] user %d 连续失败 %d 次，已停止自动注销，等待人工处理: %s",
			userID, attempts, msg)
		return
	}

	mins := cancelBackoffMinutes(attempts)
	if _, err := db.Pool.Exec(ctx, `
		UPDATE users
		SET cancel_attempts = $2, cancel_last_error = $3,
		    cancel_deadline = now() + make_interval(mins => $4), updated_at = now()
		WHERE id = $1`, userID, attempts, msg, mins); err != nil {
		log.Printf("[account-cancel] user %d 记录失败次数出错: %v", userID, err)
		return
	}
	log.Printf("[account-cancel] user %d 第 %d 次失败，%d 分钟后重试: %s", userID, attempts, mins, msg)
}

// AdminListCancelFailures handles GET /api/v1/admin/account-cancels/failed
// 列出注销执行失败或已被停止自动处理的账号，后台据此人工介入。
func AdminListCancelFailures(c *gin.Context) {
	page, perPage, offset := paging(c, 100)

	var total int64
	if err := db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FROM users
		WHERE cancel_mode IS NOT NULL AND (cancel_blocked_at IS NOT NULL OR cancel_attempts > 0)`).
		Scan(&total); err != nil {
		InternalError(c, "统计注销异常失败: "+err.Error())
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, username, COALESCE(nickname, ''), role::text, COALESCE(phone, ''),
		       COALESCE(cancel_mode, ''), cancel_attempts, COALESCE(cancel_last_error, ''),
		       COALESCE(to_char(cancel_blocked_at, 'YYYY-MM-DD HH24:MI'), ''),
		       COALESCE(to_char(cancel_deadline, 'YYYY-MM-DD HH24:MI'), '')
		FROM users
		WHERE cancel_mode IS NOT NULL AND (cancel_blocked_at IS NOT NULL OR cancel_attempts > 0)
		ORDER BY cancel_blocked_at DESC NULLS LAST, cancel_attempts DESC, id DESC
		LIMIT $1 OFFSET $2`, perPage, offset)
	if err != nil {
		InternalError(c, "查询注销异常失败: "+err.Error())
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var username, nickname, role, phone, mode, lastError, blockedAt, deadline string
		var attempts int
		if err := rows.Scan(&id, &username, &nickname, &role, &phone, &mode, &attempts,
			&lastError, &blockedAt, &deadline); err != nil {
			continue
		}
		items = append(items, gin.H{
			"user_id": id, "username": username, "nickname": nickname, "role": role, "phone": phone,
			"cancel_mode": mode, "attempts": attempts, "last_error": lastError,
			"blocked_at": blockedAt, "next_attempt_at": deadline,
			"blocked": blockedAt != "",
		})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminRetryCancel handles POST /api/v1/admin/account-cancels/:user_id/retry
// 清除失败计数并立即重新排队（cancel_deadline = now()），让 worker 下一轮再试。
func AdminRetryCancel(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("user_id"), 10, 64)
	if err != nil {
		BadRequest(c, "用户 ID 无效")
		return
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE users
		SET cancel_attempts = 0, cancel_last_error = NULL, cancel_blocked_at = NULL,
		    cancel_deadline = now(), updated_at = now()
		WHERE id = $1 AND cancel_mode IS NOT NULL`, userID)
	if err != nil {
		InternalError(c, "重试失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "该账号当前没有待执行的注销申请")
		return
	}
	writeAudit(c, "ACCOUNT_CANCEL_RETRY", "user", userID, `{"manual": true}`)
	OK(c, gin.H{"user_id": userID, "retried": true})
}

// settleSitterIfAny 账号注销前，如果这个用户还是机构，把未结算收益落成结算单并下架机构。
// 不是机构时什么都不做（最常见的情况）。
func settleSitterIfAny(ctx context.Context, userID int64) error {
	var sitterID int64
	err := db.Pool.QueryRow(ctx,
		"SELECT id FROM sitters WHERE user_id = $1 AND deleted_at IS NULL", userID).Scan(&sitterID)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil
	}
	if err != nil {
		return err
	}

	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if err := settleBeforeCancel(ctx, tx, sitterID); err != nil {
		return err
	}
	// 账已结清，接着把机构下架：账号都要注销了，不能继续出现在机构列表里接单
	if _, err := tx.Exec(ctx,
		"UPDATE sitters SET deleted_at = now(), status = 'inactive', updated_at = now() WHERE id = $1",
		sitterID); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// executeSitterCancel 仅注销机构：自动结算剩余收益 → 软删除机构 → 角色退回 owner。
//
// 顺序很重要：结算必须在软删之前、且在同一个事务里完成。软删之后
// mySitterID() 就查不到这个机构了，机构端连收益页都进不去，
// 那段还没申请的收益会永远没人发起结算。
func executeSitterCancel(ctx context.Context, userID int64) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	var sitterID int64
	if err := tx.QueryRow(ctx,
		"SELECT id FROM sitters WHERE user_id = $1 AND deleted_at IS NULL", userID).
		Scan(&sitterID); err != nil {
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		// 机构已经注销过，只剩用户上的注销标记要清
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

	if err := settleBeforeCancel(ctx, tx, sitterID); err != nil {
		return fmt.Errorf("注销前自动结算失败: %w", err)
	}

	if _, err := tx.Exec(ctx,
		"UPDATE sitters SET deleted_at = now(), status = 'inactive', updated_at = now() WHERE id = $1 AND deleted_at IS NULL",
		sitterID); err != nil {
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

// settleBeforeCancel 把「上次结算之后 → 今天」的收益落成一张待打款结算单。
//
// 这张单在机构注销后仍然留在 sitter_settlements 里（sitter 行只是软删），
// 后台「机构结算」照常能看到并打款，钱的去向有据可查。
func settleBeforeCancel(ctx context.Context, tx pgx.Tx, sitterID int64) error {
	w, err := settlementWindow(ctx, tx, sitterID)
	if err != nil {
		return err
	}
	if w.OrderCount == 0 {
		// 没有未结算收益，不用生成空单
		return nil
	}
	rate := sitterCommissionRateQ(ctx, tx, sitterID)
	_, inserted, err := insertSettlement(ctx, tx, sitterID, w.Start, w.End,
		w.OrderCount, w.GrossCents, rate, "机构注销自动结算")
	if err != nil {
		return err
	}
	if !inserted {
		// 已有重叠的 pending/paid 单（申请注销时已校验过，理论上到不了这里）。
		// 跳过而不是报错：宁可少生成一张单，也不能把注销卡死。
		log.Printf("[account-cancel] sitter %d 存在重叠结算单，跳过自动结算", sitterID)
	}
	return nil
}

// purgeUserAccount 彻底删除账户：物理删除用户及其个人数据。
// 业务数据（订单、评价等 RESTRICT 引用）存在时整个事务回滚并返回错误，调用方决定如何处理。
func purgeUserAccount(ctx context.Context, userID int64) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	// 物理删除会带走 sitters 行，而 sitter_settlements 目前是 ON DELETE RESTRICT，
	// 结算凭证会跟着一起消失；钱包余额也会失去归属。
	// 有未结清款项时一律拒绝物理删除，由调用方降级为软删除。
	//
	// 余额在上一步 sweepWalletBeforeCancel 里已经转成退款单，正常情况下这里为 0；
	// 保留检查是为了兜住「转出后又充值」这类竞态。
	var pendingSettlements int64
	if err := tx.QueryRow(ctx, `
		SELECT COUNT(*) FROM sitter_settlements st
		JOIN sitters s ON s.id = st.sitter_id
		WHERE s.user_id = $1 AND st.status = 'pending'`, userID).Scan(&pendingSettlements); err != nil {
		return fmt.Errorf("检查未结算款项失败: %w", err)
	}
	if pendingSettlements > 0 {
		return fmt.Errorf("还有 %d 笔结算待打款，不能物理删除", pendingSettlements)
	}
	var walletBalance float64
	if err := tx.QueryRow(ctx,
		`SELECT COALESCE(balance, 0)::float8 FROM wallets WHERE user_id = $1`, userID).
		Scan(&walletBalance); err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return fmt.Errorf("检查钱包余额失败: %w", err)
	}
	if walletBalance > 0 {
		return fmt.Errorf("钱包还有 %.2f 元余额，不能物理删除", walletBalance)
	}

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
