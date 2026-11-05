package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"

	"patnestServer/internal/db"
	"patnestServer/internal/model"
	"patnestServer/internal/payment"

	"github.com/gin-gonic/gin"
)

// ensureWallet 保证钱包记录存在（新注册用户首次访问时创建）。
func ensureWallet(userID int64) {
	_, _ = db.Pool.Exec(context.Background(),
		`INSERT INTO wallets (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING`, userID)
}

// WalletOverview GET /api/v1/pet-owner/wallet
func WalletOverview(c *gin.Context) {
	userID := c.GetInt64("user_id")
	ensureWallet(userID)
	var w model.Wallet
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT user_id, balance, frozen_balance, total_recharge, total_consume, updated_at
		 FROM wallets WHERE user_id = $1`, userID).
		Scan(&w.UserID, &w.Balance, &w.FrozenBalance, &w.TotalRecharge, &w.TotalConsume, &w.UpdatedAt); err != nil {
		InternalError(c, "查询钱包失败: "+err.Error())
		return
	}
	OK(c, w)
}

// WalletTransactions GET /api/v1/pet-owner/wallet/transactions
func WalletTransactions(c *gin.Context) {
	userID := c.GetInt64("user_id")
	ensureWallet(userID)
	items, total, err := loadWalletTransactions(userID, c.Query("type"), c)
	if err != nil {
		InternalError(c, "查询流水失败: "+err.Error())
		return
	}
	page, perPage, _ := paging(c, 50)
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// loadWalletTransactions 复用于用户端与后台详情；按 user_id + 可选 type 分页。
func loadWalletTransactions(userID int64, typeFilter string, c *gin.Context) ([]model.WalletTransaction, int, error) {
	_, perPage, offset := paging(c, 50)
	args := []interface{}{userID}
	where := "WHERE user_id = $1"
	if typeFilter != "" {
		args = append(args, typeFilter)
		where += fmt.Sprintf(" AND type = $%d", len(args))
	}
	var total int
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM wallet_transactions `+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	limitArg := len(args) + 1
	offsetArg := len(args) + 2
	args = append(args, perPage, offset)
	rows, err := db.Pool.Query(context.Background(),
		`SELECT id, user_id, type, amount, balance_after, status, channel,
		        COALESCE(ref_type, ''), COALESCE(ref_id, 0), COALESCE(transaction_id, ''), COALESCE(remark, ''),
		        to_char(created_at, 'YYYY-MM-DD HH24:MI')
		 FROM wallet_transactions `+where+
			fmt.Sprintf(` ORDER BY id DESC LIMIT $%d OFFSET $%d`, limitArg, offsetArg), args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	items := make([]model.WalletTransaction, 0)
	for rows.Next() {
		var t model.WalletTransaction
		var createdAt string
		if err := rows.Scan(&t.ID, &t.UserID, &t.Type, &t.Amount, &t.BalanceAfter,
			&t.Status, &t.Channel, &t.RefType, &t.RefID, &t.TransactionID, &t.Remark, &createdAt); err == nil {
			t.CreatedAt = createdAt
			items = append(items, t)
		}
	}
	return items, total, nil
}

// CreateRecharge POST /api/v1/pet-owner/wallet/recharge
func CreateRecharge(c *gin.Context) {
	userID := c.GetInt64("user_id")
	var req model.RechargeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}
	channel := req.Channel
	if channel == "" {
		channel = "mock"
	}
	if channel != "mock" && channel != "wechat" && channel != "alipay" {
		BadRequest(c, "不支持的充值渠道")
		return
	}
	ensureWallet(userID)
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())
	var roID int64
	if err = tx.QueryRow(context.Background(),
		`INSERT INTO recharge_orders (user_id, amount, channel, status)
		 VALUES ($1, $2, $3, 'pending') RETURNING id`,
		userID, req.Amount, channel).Scan(&roID); err != nil {
		InternalError(c, "创建充值单失败: "+err.Error())
		return
	}
	gw := payment.GetGateway(channel)
	params, gerr := gw.CreateCharge(fmt.Sprintf("recharge-%d", roID), req.Amount)
	if gerr != nil {
		InternalError(c, "生成支付参数失败: "+gerr.Error())
		return
	}
	if err = tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败: "+err.Error())
		return
	}
	OK(c, gin.H{"recharge_id": roID, "amount": req.Amount, "channel": channel, "status": "pending", "pay_params": params})
}

// ConfirmRecharge POST /api/v1/pet-owner/wallet/recharge/:id/pay （模拟网关确认，用户本人）
func ConfirmRecharge(c *gin.Context) {
	userID := c.GetInt64("user_id")
	roID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid recharge id")
		return
	}
	_ = confirmRecharge(c, roID, userID)
}

// AdminConfirmRecharge POST /api/v1/admin/wallet/recharges/:id/confirm （后台手动确认，不校验归属）
func AdminConfirmRecharge(c *gin.Context) {
	roID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid recharge id")
		return
	}
	_ = confirmRecharge(c, roID, 0)
}

// confirmRecharge 充值确认核心（模拟网关同步成功）。userID=0 表示后台代确认，跳过归属校验。
func confirmRecharge(c *gin.Context, roID, userID int64) error {
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return err
	}
	defer tx.Rollback(context.Background())
	var uid int64
	var amount float64
	var status, channel string
	if err = tx.QueryRow(context.Background(),
		`SELECT user_id, amount, status, channel FROM recharge_orders WHERE id = $1 FOR UPDATE`, roID).
		Scan(&uid, &amount, &status, &channel); err != nil {
		NotFound(c, "充值单不存在")
		return err
	}
	if userID != 0 && uid != userID {
		Forbidden(c, "不是你的充值单")
		return err
	}
	if status == "paid" {
		// 幂等：重复确认不再入账，但要回传当前余额，避免客户端把余额覆盖成 0
		var bal float64
		_ = db.Pool.QueryRow(context.Background(),
			`SELECT balance FROM wallets WHERE user_id = $1`, uid).Scan(&bal)
		OK(c, gin.H{"status": "paid", "balance": bal})
		return nil
	}
	if status == "failed" || status == "closed" {
		Conflict(c, "充值单已关闭")
		return err
	}
	txnID := fmt.Sprintf("MOCK-RECHARGE-%d", roID)
	if _, err = tx.Exec(context.Background(),
		`UPDATE recharge_orders SET status = 'paid', paid_at = now(), updated_at = now(), transaction_id = $1 WHERE id = $2`,
		txnID, roID); err != nil {
		InternalError(c, "更新充值单失败")
		return err
	}
	var balAfter float64
	if err = tx.QueryRow(context.Background(),
		`UPDATE wallets SET balance = balance + $1, total_recharge = total_recharge + $1, version = version + 1, updated_at = now()
		 WHERE user_id = $2 RETURNING balance`, amount, uid).Scan(&balAfter); err != nil {
		InternalError(c, "更新钱包失败")
		return err
	}
	if _, err = tx.Exec(context.Background(),
		`INSERT INTO wallet_transactions (user_id, type, amount, balance_after, channel, ref_type, ref_id, transaction_id, status)
		 VALUES ($1, 'recharge', $2, $3, $4, 'recharge', $5, $6, 'success')`,
		uid, amount, balAfter, channel, roID, txnID); err != nil {
		InternalError(c, "写入流水失败")
		return err
	}
	if err = tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return err
	}
	OK(c, gin.H{"status": "paid", "balance": balAfter})
	return nil
}

type adminWalletItem struct {
	UserID        int64
	Username      string
	Nickname      string
	Role          string
	Balance       float64
	FrozenBalance float64
	TotalRecharge float64
	TotalConsume  float64
}

// AdminListWallets GET /api/v1/admin/wallets
func AdminListWallets(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	keyword := c.Query("keyword")
	args := []interface{}{}
	where := ""
	if keyword != "" {
		args = append(args, "%"+keyword+"%")
		where = fmt.Sprintf("WHERE u.username ILIKE $%d OR u.nickname ILIKE $%d OR u.phone ILIKE $%d", len(args), len(args), len(args))
	}
	var total int
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM wallets w JOIN users u ON u.id = w.user_id `+where, args...).Scan(&total); err != nil {
		InternalError(c, "统计失败")
		return
	}
	args = append(args, perPage, offset)
	rows, err := db.Pool.Query(context.Background(),
		`SELECT w.user_id, u.username, u.nickname, u.role, w.balance, w.frozen_balance, w.total_recharge, w.total_consume,
		        to_char(w.updated_at, 'YYYY-MM-DD HH24:MI')
		 FROM wallets w JOIN users u ON u.id = w.user_id `+where+
			fmt.Sprintf(` ORDER BY w.balance DESC LIMIT $%d OFFSET $%d`, len(args)-1, len(args)), args...)
	if err != nil {
		InternalError(c, "查询钱包列表失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var it adminWalletItem
		var updatedAt string
		if err := rows.Scan(&it.UserID, &it.Username, &it.Nickname, &it.Role, &it.Balance,
			&it.FrozenBalance, &it.TotalRecharge, &it.TotalConsume, &updatedAt); err == nil {
			items = append(items, gin.H{
				"user_id": it.UserID, "username": it.Username, "nickname": it.Nickname, "role": it.Role,
				"balance": it.Balance, "frozen_balance": it.FrozenBalance,
				"total_recharge": it.TotalRecharge, "total_consume": it.TotalConsume, "updated_at": updatedAt,
			})
		}
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminGetWallet GET /api/v1/admin/wallets/:user_id
func AdminGetWallet(c *gin.Context) {
	uid, err := strconv.ParseInt(c.Param("user_id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid user id")
		return
	}
	var w model.Wallet
	if err = db.Pool.QueryRow(context.Background(),
		`SELECT user_id, balance, frozen_balance, total_recharge, total_consume, updated_at
		 FROM wallets WHERE user_id = $1`, uid).Scan(&w.UserID, &w.Balance, &w.FrozenBalance, &w.TotalRecharge, &w.TotalConsume, &w.UpdatedAt); err != nil {
		NotFound(c, "钱包不存在")
		return
	}
	items, _, _ := loadWalletTransactions(uid, "", c)
	OK(c, gin.H{"wallet": w, "transactions": items})
}

// AdminAdjustWallet POST /api/v1/admin/wallets/:user_id/adjust
func AdminAdjustWallet(c *gin.Context) {
	adminID := c.GetInt64("user_id")
	uid, err := strconv.ParseInt(c.Param("user_id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid user id")
		return
	}
	var req model.WalletAdjustRequest
	if err = c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}
	if req.Amount == 0 {
		BadRequest(c, "调整金额不能为 0")
		return
	}
	ensureWallet(uid)
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())
	txType := "adjust_add"
	if req.Amount < 0 {
		txType = "adjust_sub"
	}
	tag, e := tx.Exec(context.Background(),
		`UPDATE wallets SET balance = balance + $1, version = version + 1, updated_at = now()
		 WHERE user_id = $2 AND (balance + $1) >= 0`, req.Amount, uid)
	if e != nil {
		InternalError(c, "调账失败: "+e.Error())
		return
	}
	if tag.RowsAffected() != 1 {
		Conflict(c, "余额不足，无法扣减")
		return
	}
	var balAfter float64
	_ = tx.QueryRow(context.Background(), `SELECT balance FROM wallets WHERE user_id = $1`, uid).Scan(&balAfter)
	if _, err = tx.Exec(context.Background(),
		`INSERT INTO wallet_transactions (user_id, type, amount, balance_after, channel, ref_type, ref_id, remark, status)
		 VALUES ($1, $2, $3, $4, 'admin', 'admin', $5, $6, 'success')`,
		uid, txType, absFloat(req.Amount), balAfter, adminID, req.Reason); err != nil {
		InternalError(c, "写入流水失败")
		return
	}
	writeAudit(c, "WALLET_ADJUST", "wallet", uid,
		fmt.Sprintf(`{"amount": %s, "type": %q, "reason": %q}`, formatFloat(req.Amount), txType, req.Reason))
	if err = tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	OK(c, gin.H{"balance": balAfter})
}

// AdminListRecharges GET /api/v1/admin/wallet/recharges
func AdminListRecharges(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	args := []interface{}{}
	where := ""
	if status != "" {
		args = append(args, status)
		where = fmt.Sprintf("WHERE r.status = $%d", len(args))
	}
	var total int
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM recharge_orders r JOIN users u ON u.id = r.user_id `+where, args...).Scan(&total); err != nil {
		InternalError(c, "统计失败")
		return
	}
	args = append(args, perPage, offset)
	rows, err := db.Pool.Query(context.Background(),
		`SELECT r.id, r.user_id, u.username, u.nickname, r.amount, r.channel, r.status,
		        COALESCE(r.transaction_id, ''), COALESCE(to_char(r.paid_at, 'YYYY-MM-DD HH24:MI'), ''),
		        COALESCE(to_char(r.created_at, 'YYYY-MM-DD HH24:MI'), '')
		 FROM recharge_orders r JOIN users u ON u.id = r.user_id `+where+
			fmt.Sprintf(` ORDER BY r.id DESC LIMIT $%d OFFSET $%d`, len(args)-1, len(args)), args...)
	if err != nil {
		InternalError(c, "查询充值单失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, uid int64
		var username, nickname, channel, status, txnID, paidAt, createdAt string
		var amount float64
		if err := rows.Scan(&id, &uid, &username, &nickname, &amount, &channel,
			&status, &txnID, &paidAt, &createdAt); err == nil {
			items = append(items, gin.H{
				"id": id, "user_id": uid, "username": username, "nickname": nickname,
				"amount": amount, "channel": channel, "status": status,
				"transaction_id": txnID, "paid_at": paidAt, "created_at": createdAt,
			})
		}
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// PaymentNotify POST /api/v1/payments/recharge/notify/:channel
// 公开路由（无需登录）。真实微信/支付宝网关的异步回调入口；模拟网关不触发此逻辑。
func PaymentNotify(c *gin.Context) {
	channel := c.Param("channel")
	gw := payment.GetGateway(channel)
	body, _ := c.GetRawData()
	txnID, amount, ok, err := gw.VerifyNotify(body, map[string]string{})
	if err != nil || !ok {
		BadRequest(c, "回调校验失败")
		return
	}
	// 真实接入时：按 txnID 定位 recharge_orders 并调用 confirmRecharge；此处仅占位。
	log.Printf("[PaymentNotify] channel=%s txn=%s amount=%.2f (mock no-op)", channel, txnID, amount)
	OK(c, gin.H{"received": true})
}

func absFloat(v float64) float64 {
	if v < 0 {
		return -v
	}
	return v
}

func formatFloat(v float64) string {
	return strconv.FormatFloat(v, 'f', 2, 64)
}
