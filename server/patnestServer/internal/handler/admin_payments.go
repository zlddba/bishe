package handler

import (
	"context"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// opError 让底层业务函数能把 HTTP 状态码带回 handler。
type opError struct {
	status int
	msg    string
}

func (e *opError) Error() string { return e.msg }

func newOpError(status int, msg string) error { return &opError{status: status, msg: msg} }

func failOp(c *gin.Context, err error) {
	if oe, ok := err.(*opError); ok {
		Fail(c, oe.status, 40001, oe.msg)
		return
	}
	InternalError(c, err.Error())
}

type refundResult struct {
	OrderID       int64   `json:"order_id"`
	OrderNo       string  `json:"order_no"`
	RefundAmount  float64 `json:"refund_amount"`
	RefundType    string  `json:"refund_type"`
	TransactionID string  `json:"transaction_id"`
	PaymentStatus string  `json:"payment_status"`
	OrderStatus   string  `json:"order_status"`
	OwnerID       int64   `json:"-"`
}

// performRefund 是订单退款的唯一实现：追加退款流水、更新订单、释放容量、通知主人。
// 后台直接退款和“主人申请-管理员审批”都走这里。
func performRefund(ctx context.Context, orderID int64, amount *float64, reason string, adminID int64) (*refundResult, error) {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)

	var (
		orderNo       string
		status        string
		paymentStatus string
		totalAmount   float64
		startDate     time.Time
		endDate       time.Time
		petCount      int
		ownerID       int64
		sitterID      int64
	)
	err = tx.QueryRow(ctx,
		`SELECT order_no, status::text, payment_status::text, total_amount,
		        start_date, end_date, pet_count, owner_id, sitter_id
		 FROM orders WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`, orderID,
	).Scan(&orderNo, &status, &paymentStatus, &totalAmount, &startDate, &endDate,
		&petCount, &ownerID, &sitterID)
	if err != nil {
		return nil, newOpError(http.StatusNotFound, "订单不存在")
	}
	if paymentStatus != "paid" {
		return nil, newOpError(http.StatusBadRequest, "该订单当前不是已支付状态，无法退款")
	}
	refundAmount := totalAmount
	if amount != nil {
		refundAmount = *amount
	}
	if refundAmount <= 0 || refundAmount > totalAmount {
		return nil, newOpError(http.StatusBadRequest, "退款金额必须在 0 到订单金额之间")
	}

	// 原支付流水保持不动，单独追加一条退款流水，保证资金记录可追溯
	var (
		paymentID  int64
		paidAmount float64
		method     string
	)
	err = tx.QueryRow(ctx,
		`SELECT id, amount, method::text FROM payments
		 WHERE order_id = $1 AND status = 'paid' ORDER BY id DESC LIMIT 1`,
		orderID).Scan(&paymentID, &paidAmount, &method)
	if err != nil {
		paymentID, paidAmount, method = 0, 0, "mock"
	}

	refundType := "full"
	if refundAmount < totalAmount {
		refundType = "partial"
	}
	rawResponse := fmt.Sprintf(
		`{"refund_reason": %q, "refund_amount": %.2f, "refund_type": %q, "paid_amount": %.2f, "refund_of_payment_id": %d, "operator": %d}`,
		reason, refundAmount, refundType, paidAmount, paymentID, adminID)

	refundTxnID := fmt.Sprintf("REFUND-%s-%d", time.Now().Format("20060102"), orderID)
	if _, err := tx.Exec(ctx,
		`INSERT INTO payments (order_id, amount, method, transaction_id, status, refunded_at, raw_response)
		 VALUES ($1, $2, $3::pay_method, $4, 'refunded', now(), $5::jsonb)`,
		orderID, refundAmount, method, refundTxnID, rawResponse); err != nil {
		return nil, fmt.Errorf("写入退款流水失败: %w", err)
	}

	// 仍在进行中的订单退款时一并取消，避免继续占用容量
	oldActive := isActiveOrderStatus(status)
	if oldActive {
		if _, err := tx.Exec(ctx,
			`UPDATE orders SET payment_status = 'refunded', status = 'cancelled',
			 cancel_reason = $1, cancel_time = now() WHERE id = $2`,
			reason, orderID); err != nil {
			return nil, fmt.Errorf("更新订单状态失败: %w", err)
		}
		if status == "staying" {
			_, _ = tx.Exec(ctx, "SELECT fn_release_capacity($1, $2, $3, $4)",
				sitterID, startDate, endDate, petCount)
		}
	} else {
		if _, err := tx.Exec(ctx, "UPDATE orders SET payment_status = 'refunded' WHERE id = $1", orderID); err != nil {
			return nil, fmt.Errorf("更新订单状态失败: %w", err)
		}
	}

	_, _ = tx.Exec(ctx,
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		ownerID, "订单已退款",
		fmt.Sprintf("订单 %s 已完成退款，金额 %.2f 元，原因：%s", orderNo, refundAmount, reason),
		fmt.Sprintf(`{"order_id": %d, "refund_amount": %.2f}`, orderID, refundAmount))

	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("提交事务失败: %w", err)
	}

	finalStatus := status
	if oldActive {
		finalStatus = "cancelled"
	}
	return &refundResult{
		OrderID: orderID, OrderNo: orderNo, RefundAmount: refundAmount, RefundType: refundType,
		TransactionID: refundTxnID, PaymentStatus: "refunded", OrderStatus: finalStatus, OwnerID: ownerID,
	}, nil
}

// AdminRefundOrder handles POST /api/v1/admin/orders/:id/refund
// 记录退款流水（payments），写退款原因，并把订单标记为已退款/已取消。
func AdminRefundOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的订单ID")
		return
	}

	var req struct {
		Reason string   `json:"reason" binding:"required,min=2,max=255"`
		Amount *float64 `json:"amount" binding:"omitempty,min=0.01"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请填写退款原因（2-255字）: "+err.Error())
		return
	}

	res, err := performRefund(c.Request.Context(), orderID, req.Amount, req.Reason, c.GetInt64("user_id"))
	if err != nil {
		failOp(c, err)
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'REFUND_ORDER', 'order', $2, $3)", c.GetInt64("user_id"), orderID,
		fmt.Sprintf(`{"order_no": %q, "amount": %.2f, "reason": %q}`, res.OrderNo, res.RefundAmount, req.Reason))

	OK(c, res)
}
