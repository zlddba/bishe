package handler

import (
	"context"
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

// 钱包余额退款单。
//
// 钱包没有提现接口，账号注销后用户登录不了，余额却还挂在 wallets 里，
// 没有任何入口能取回。注销时把可用余额转成一张 pending 退款单：
// 余额扣到 0、写一条 withdraw 流水（status=pending），后台确认打款后
// 流水转 success；驳回则把钱原路退回钱包并写 refund 流水。
//
// 不对接支付通道是有意的：没有收款账户信息时自动打款打错了追不回来，
// 线下打款 + 运营确认是当前唯一安全的选择。

const withdrawalNoteCancel = "账号注销自动转出"

// errInsufficientBalance 余额不足（并发下余额被花掉也会命中）。
var errInsufficientBalance = errors.New("余额不足")

// withdrawalPayee 收款信息。用户在主动申请时必填，注销自动转出时为空，
// 由客服联系后补录。
type withdrawalPayee struct {
	Name    string
	No      string
	Contact string
}

// createWithdrawal 从可用余额里扣掉 cents 并生成一张待退款单（同时写流水）。
//
// 余额扣减带 `balance >= $1` 条件，并发下不会把余额打成负数；
// 扣款、建单、写流水必须在同一个事务里，否则会出现「钱扣了没单据」。
func createWithdrawal(ctx context.Context, tx pgx.Tx, userID, cents int64,
	source, note string, payee withdrawalPayee) (int64, error) {
	if cents <= 0 {
		return 0, nil
	}

	tag, err := tx.Exec(ctx, `
		UPDATE wallets
		SET balance = balance - $1::bigint / 100.0, version = version + 1, updated_at = now()
		WHERE user_id = $2 AND balance >= $1::bigint / 100.0`, cents, userID)
	if err != nil {
		return 0, err
	}
	if tag.RowsAffected() == 0 {
		return 0, errInsufficientBalance
	}

	var id int64
	if err := tx.QueryRow(ctx, `
		INSERT INTO wallet_withdrawals
		  (user_id, amount, status, source, account_name, account_no, contact, note)
		SELECT $1, $2::bigint / 100.0, 'pending', $3,
		       NULLIF($4, ''), NULLIF($5, ''),
		       COALESCE(NULLIF($6, ''),
		                (SELECT COALESCE(NULLIF(contact_phone, ''), phone) FROM users WHERE id = $1)),
		       NULLIF($7, '')
		RETURNING id`,
		userID, cents, source, payee.Name, payee.No, payee.Contact, note).Scan(&id); err != nil {
		return 0, err
	}

	if _, err := tx.Exec(ctx, `
		INSERT INTO wallet_transactions
		  (user_id, type, amount, balance_after, status, channel, ref_type, ref_id, remark)
		VALUES ($1, 'withdraw', $2::bigint / 100.0,
		        (SELECT balance FROM wallets WHERE user_id = $1),
		        'pending', 'bank', 'withdrawal', $3, $4)`,
		userID, cents, id, note); err != nil {
		return 0, err
	}
	return id, nil
}

// sweepWalletToWithdrawal 把用户可用余额全额转成一张待退款单，返回单号。
// 余额为 0（或没有钱包行）时返回 0, nil。
//
// 重复调用是安全的：第二次余额已经是 0，直接返回 0。
func sweepWalletToWithdrawal(ctx context.Context, tx pgx.Tx, userID int64, note string) (int64, error) {
	var balance float64
	if err := tx.QueryRow(ctx,
		`SELECT balance::float8 FROM wallets WHERE user_id = $1`, userID).Scan(&balance); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return 0, nil
		}
		return 0, err
	}
	// 转成分，避免 float 相减留下 0.009999 这种尾巴
	cents := int64(math.Round(balance * 100))
	if cents <= 0 {
		return 0, nil
	}
	id, err := createWithdrawal(ctx, tx, userID, cents, "cancel", note, withdrawalPayee{})
	if errors.Is(err, errInsufficientBalance) {
		// 并发下余额被别的请求花掉了，本轮跳过；worker 会退避后重试
		return 0, fmt.Errorf("余额已变动，跳过本次转出")
	}
	return id, err
}

// ApplyWalletWithdrawal handles POST /api/v1/pet-owner/wallet/withdrawals
//
// 用户主动把钱包余额申请退回。钱包没有提现通道，这里只登记申请单，
// 线下打款后由后台「钱包管理 → 余额退款」确认；确认前余额已经扣走，
// 所以不会出现「既在钱包里又在退款中」的重复占用。
func ApplyWalletWithdrawal(c *gin.Context) {
	userID := c.GetInt64("user_id")
	var in struct {
		Amount      float64 `json:"amount" binding:"required,gt=0"`
		AccountName string  `json:"account_name" binding:"required,max=64"`
		AccountNo   string  `json:"account_no" binding:"required,max=128"`
		Contact     string  `json:"contact" binding:"max=64"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写退款金额和收款信息")
		return
	}
	cents := int64(math.Round(in.Amount * 100))
	if cents < 100 {
		BadRequest(c, "退款金额至少 1 元")
		return
	}

	// 同时只允许一笔在处理中，避免同一笔钱被重复申领
	var pending int64
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM wallet_withdrawals WHERE user_id = $1 AND status = 'pending'`, userID).
		Scan(&pending); err != nil {
		InternalError(c, "检查退款状态失败")
		return
	}
	if pending > 0 {
		Conflict(c, "已有一笔退款申请正在处理，请等待完成后再提交")
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())

	id, err := createWithdrawal(context.Background(), tx, userID, cents, "manual", "用户主动申请",
		withdrawalPayee{Name: in.AccountName, No: in.AccountNo, Contact: in.Contact})
	if errors.Is(err, errInsufficientBalance) {
		BadRequest(c, "钱包余额不足")
		return
	}
	if err != nil {
		InternalError(c, "提交退款申请失败: "+err.Error())
		return
	}
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	writeAudit(c, "WALLET_WITHDRAW_APPLY", "withdrawal", id,
		fmt.Sprintf(`{"amount": %.2f}`, in.Amount))
	Created(c, "退款申请已提交，客服会尽快联系你确认打款", gin.H{
		"id": id, "amount": in.Amount,
	})
}

// notifyWithdrawal 给用户写一条站内通知。
//
// notifications.user_id 是 ON DELETE CASCADE，软删除只是置 users.deleted_at，
// 行还在，所以「用户已注销就写不进去」是个误判——写得进去。
// 区别在于：已注销的用户登录不了，看不到这条通知，它只能作为留痕；
// 主动申请退款（未注销）的用户能看到，这条才真正起作用。
func notifyWithdrawal(ctx context.Context, userID, id int64, amount float64, status, note string) {
	title := "余额退款已打款"
	body := fmt.Sprintf("您的余额退款 %.2f 元（单号 #%d）已完成打款，请注意查收。", amount, id)
	if status == "rejected" {
		title = "余额退款被驳回"
		body = fmt.Sprintf("您的余额退款 %.2f 元（单号 #%d）未通过，金额已退回钱包余额。", amount, id)
		if note != "" {
			body += "原因：" + note
		}
	}
	_, _ = db.Pool.Exec(ctx,
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4, 'personal')",
		userID, title, body, fmt.Sprintf(`{"withdrawal_id": %d}`, id))
}

// sweepWalletBeforeCancel 独立事务版：注销流程里先调用它，再决定物理删除还是软删除。
// 单独提交是有意的——即使后面 purge 失败退回软删除，余额也已经安全转成退款单。
func sweepWalletBeforeCancel(ctx context.Context, userID int64) (int64, error) {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback(ctx)

	id, err := sweepWalletToWithdrawal(ctx, tx, userID, withdrawalNoteCancel)
	if err != nil {
		return 0, err
	}
	if err := tx.Commit(ctx); err != nil {
		return 0, err
	}
	return id, nil
}

// AdminListWalletWithdrawals handles GET /api/v1/admin/wallet/withdrawals?status=pending&keyword=张
func AdminListWalletWithdrawals(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	keyword := strings.TrimSpace(c.Query("keyword"))

	var total int64
	if err := db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FROM wallet_withdrawals w JOIN users u ON u.id = w.user_id
		WHERE ($1 = '' OR w.status = $1)
		  AND ($2 = '' OR u.username ILIKE '%' || $2 || '%'
		       OR COALESCE(u.nickname, '') ILIKE '%' || $2 || '%'
		       OR COALESCE(w.contact, '') LIKE '%' || $2 || '%')`,
		status, keyword).Scan(&total); err != nil {
		InternalError(c, "统计退款单失败: "+err.Error())
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT w.id, w.user_id, u.username, COALESCE(u.nickname, ''), u.role::text,
		       w.amount, w.status, w.source,
		       COALESCE(w.account_name, ''), COALESCE(w.account_no, ''), COALESCE(w.contact, ''),
		       COALESCE(w.note, ''), COALESCE(w.admin_note, ''),
		       to_char(w.applied_at, 'YYYY-MM-DD HH24:MI'),
		       COALESCE(to_char(w.paid_at, 'YYYY-MM-DD HH24:MI'), '')
		FROM wallet_withdrawals w JOIN users u ON u.id = w.user_id
		WHERE ($1 = '' OR w.status = $1)
		  AND ($2 = '' OR u.username ILIKE '%' || $2 || '%'
		       OR COALESCE(u.nickname, '') ILIKE '%' || $2 || '%'
		       OR COALESCE(w.contact, '') LIKE '%' || $2 || '%')
		ORDER BY (w.status = 'pending') DESC, w.id DESC
		LIMIT $3 OFFSET $4`, status, keyword, perPage, offset)
	if err != nil {
		InternalError(c, "查询退款单失败: "+err.Error())
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id, userID int64
		var amount float64
		var username, nickname, role, st, source string
		var accountName, accountNo, contact, note, adminNote, applied, paid string
		if err := rows.Scan(&id, &userID, &username, &nickname, &role, &amount, &st, &source,
			&accountName, &accountNo, &contact, &note, &adminNote, &applied, &paid); err != nil {
			continue
		}
		items = append(items, gin.H{
			"id": id, "user_id": userID, "username": username, "nickname": nickname, "role": role,
			"amount": amount, "status": st, "source": source,
			"account_name": accountName, "account_no": accountNo, "contact": contact,
			"note": note, "admin_note": adminNote, "applied_at": applied, "paid_at": paid,
		})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminPayWalletWithdrawal handles POST /api/v1/admin/wallet/withdrawals/:id/pay
// 线下已经打款，这里只做确认：单据转 paid，对应 withdraw 流水转 success。
// 收款信息可以在这里补录（客服拿到卡号后随手记下来，避免只存在聊天记录里）。
func AdminPayWalletWithdrawal(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Note        string `json:"note" binding:"max=255"`
		AccountName string `json:"account_name" binding:"max=64"`
		AccountNo   string `json:"account_no" binding:"max=128"`
	}
	_ = c.ShouldBindJSON(&in)

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())

	var userID int64
	var amount float64
	if err := tx.QueryRow(context.Background(),
		`SELECT user_id, amount::float8 FROM wallet_withdrawals WHERE id = $1`, id).
		Scan(&userID, &amount); err != nil {
		NotFound(c, "退款单不存在")
		return
	}

	// 条件更新兜住重复点击：只有 pending 能转 paid
	tag, err := tx.Exec(context.Background(), `
		UPDATE wallet_withdrawals
		SET status = 'paid', admin_id = $2, admin_note = NULLIF($3, ''), paid_at = now(),
		    account_name = COALESCE(NULLIF($4, ''), account_name),
		    account_no = COALESCE(NULLIF($5, ''), account_no)
		WHERE id = $1 AND status = 'pending'`,
		id, c.GetInt64("user_id"), in.Note, in.AccountName, in.AccountNo)
	if err != nil {
		InternalError(c, "确认打款失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "该退款单已处理")
		return
	}
	if _, err := tx.Exec(context.Background(), `
		UPDATE wallet_transactions SET status = 'success'
		WHERE ref_type = 'withdrawal' AND ref_id = $1 AND status = 'pending'`, id); err != nil {
		InternalError(c, "更新流水失败: "+err.Error())
		return
	}
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	// 通知在事务外发：通知写失败不应该把已经成功的打款回滚
	notifyWithdrawal(context.Background(), userID, id, amount, "paid", in.Note)
	writeAudit(c, "WALLET_WITHDRAW_PAY", "withdrawal", id,
		fmt.Sprintf(`{"user_id": %d, "amount": %.2f}`, userID, amount))
	OK(c, gin.H{"id": id, "status": "paid"})
}

// AdminRejectWalletWithdrawal handles POST /api/v1/admin/wallet/withdrawals/:id/reject
// 驳回：把金额原路退回钱包，并写一条 refund 流水，原来那条 withdraw 置 failed。
func AdminRejectWalletWithdrawal(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Note string `json:"note" binding:"max=255"`
	}
	_ = c.ShouldBindJSON(&in)

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())

	var userID int64
	var amountCents int64
	if err := tx.QueryRow(context.Background(),
		`SELECT user_id, (amount * 100)::bigint FROM wallet_withdrawals WHERE id = $1`, id).
		Scan(&userID, &amountCents); err != nil {
		NotFound(c, "退款单不存在")
		return
	}

	// 用户已经注销的话不能驳回：钱退回一个登录不了的钱包，等于把问题原地挪回去。
	// 这种情况只能打款，或者走线下协商。
	var userDeleted bool
	if err := tx.QueryRow(context.Background(),
		`SELECT deleted_at IS NOT NULL FROM users WHERE id = $1`, userID).Scan(&userDeleted); err != nil {
		NotFound(c, "用户不存在")
		return
	}
	if userDeleted {
		Conflict(c, "该用户已注销，余额退回后无法使用；请改为确认打款，或线下与用户协商")
		return
	}

	tag, err := tx.Exec(context.Background(), `
		UPDATE wallet_withdrawals
		SET status = 'rejected', admin_id = $2, admin_note = NULLIF($3, '')
		WHERE id = $1 AND status = 'pending'`, id, c.GetInt64("user_id"), in.Note)
	if err != nil {
		InternalError(c, "驳回失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "该退款单已处理")
		return
	}

	// 钱原路退回：先确保钱包行存在，再加回去
	if _, err := tx.Exec(context.Background(),
		`INSERT INTO wallets (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING`, userID); err != nil {
		InternalError(c, "创建钱包失败: "+err.Error())
		return
	}
	if _, err := tx.Exec(context.Background(), `
		UPDATE wallets
		SET balance = balance + $1::bigint / 100.0, version = version + 1, updated_at = now()
		WHERE user_id = $2`, amountCents, userID); err != nil {
		InternalError(c, "退回余额失败: "+err.Error())
		return
	}
	if _, err := tx.Exec(context.Background(), `
		UPDATE wallet_transactions SET status = 'failed'
		WHERE ref_type = 'withdrawal' AND ref_id = $1 AND status = 'pending'`, id); err != nil {
		InternalError(c, "更新流水失败: "+err.Error())
		return
	}
	var balAfter float64
	_ = tx.QueryRow(context.Background(),
		`SELECT balance::float8 FROM wallets WHERE user_id = $1`, userID).Scan(&balAfter)
	if _, err := tx.Exec(context.Background(), `
		INSERT INTO wallet_transactions
		  (user_id, type, amount, balance_after, status, channel, ref_type, ref_id, remark)
		VALUES ($1, 'refund', $2::bigint / 100.0, $3, 'success', 'admin', 'withdrawal', $4, $5)`,
		userID, amountCents, balAfter, id, "余额退款被驳回，已退回钱包"); err != nil {
		InternalError(c, "写入流水失败: "+err.Error())
		return
	}
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	notifyWithdrawal(context.Background(), userID, id, centsToYuan(amountCents), "rejected", in.Note)
	writeAudit(c, "WALLET_WITHDRAW_REJECT", "withdrawal", id,
		fmt.Sprintf(`{"user_id": %d, "amount": %.2f}`, userID, centsToYuan(amountCents)))
	OK(c, gin.H{"id": id, "status": "rejected", "balance": balAfter})
}
