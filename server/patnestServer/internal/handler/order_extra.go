package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

type pgQuerier interface {
	QueryRow(context.Context, string, ...interface{}) pgx.Row
	Query(context.Context, string, ...interface{}) (pgx.Rows, error)
}

// computeStayAmount 按机构日单价与节假日上浮规则计算基础住宿费。
func computeStayAmount(ctx context.Context, q pgQuerier, sitterID int64, start, end time.Time, petCount int) (float64, error) {
	var priceMode string
	var dailyPrice, visitPrice float64
	if err := q.QueryRow(ctx,
		"SELECT price_mode::text, daily_price, visit_price FROM sitters WHERE id = $1 AND deleted_at IS NULL",
		sitterID).Scan(&priceMode, &dailyPrice, &visitPrice); err != nil {
		return 0, fmt.Errorf("机构不存在")
	}
	if priceMode != "per_day" {
		return visitPrice * float64(petCount), nil
	}
	type rule struct {
		start, end time.Time
		multiplier float64
	}
	rules := make([]rule, 0)
	rows, err := q.Query(ctx, `
		SELECT start_date, end_date, multiplier FROM sitter_price_rules
		WHERE sitter_id = $1 AND end_date >= $2 AND start_date < $3`, sitterID, start, end)
	if err != nil {
		return 0, err
	}
	for rows.Next() {
		var r rule
		if rows.Scan(&r.start, &r.end, &r.multiplier) == nil {
			rules = append(rules, r)
		}
	}
	rows.Close()

	total := 0.0
	for d := start; d.Before(end); d = d.AddDate(0, 0, 1) {
		multiplier := 1.0
		for _, r := range rules {
			if !d.Before(r.start) && !d.After(r.end) && r.multiplier > multiplier {
				multiplier = r.multiplier
			}
		}
		total += dailyPrice * multiplier
	}
	return total * float64(petCount), nil
}

// RescheduleOrder handles PUT /api/v1/pet-owner/orders/:id/reschedule
func RescheduleOrder(c *gin.Context) {
	orderID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		StartDate string `json:"start_date" binding:"required"`
		EndDate   string `json:"end_date" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写新的起止日期")
		return
	}
	start, err1 := time.Parse("2006-01-02", in.StartDate)
	end, err2 := time.Parse("2006-01-02", in.EndDate)
	if err1 != nil || err2 != nil || !end.After(start) {
		BadRequest(c, "日期范围无效，结束日期必须晚于开始日期")
		return
	}

	ownerID := c.GetInt64("user_id")
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "开启事务失败")
		return
	}
	defer tx.Rollback(context.Background())

	var (
		status, paymentStatus, orderNo string
		sitterID                       int64
		oldStart, oldEnd               time.Time
		petCount                       int
		oldTotal                       float64
	)
	err = tx.QueryRow(context.Background(), `
		SELECT order_no, status::text, payment_status::text, sitter_id, start_date, end_date,
		       pet_count, total_amount
		FROM orders WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL FOR UPDATE`,
		orderID, ownerID).Scan(&orderNo, &status, &paymentStatus, &sitterID, &oldStart, &oldEnd, &petCount, &oldTotal)
	if err != nil {
		NotFound(c, "订单不存在")
		return
	}
	if status != "pending_pay" && status != "confirmed" && status != "accepted" {
		BadRequest(c, "当前订单状态不支持改期")
		return
	}
	if !time.Now().Before(oldStart) {
		BadRequest(c, "订单已开始，不能改期")
		return
	}
	newBase, err := computeStayAmount(context.Background(), tx, sitterID, start, end, petCount)
	if err != nil {
		InternalError(c, err.Error())
		return
	}
	newTotal := newBase
	if paymentStatus == "paid" && newTotal > oldTotal {
		BadRequest(c, "改期后金额高于原订单，请先联系平台补差价")
		return
	}

	// 先释放旧日期占用，再占用新日期
	_, _ = tx.Exec(context.Background(), "SELECT fn_release_capacity($1, $2, $3, $4)",
		sitterID, oldStart, oldEnd, petCount)
	if _, err := tx.Exec(context.Background(), "SELECT fn_reserve_capacity($1, $2, $3, $4)",
		sitterID, start, end, petCount); err != nil {
		Conflict(c, "新日期容量不足")
		return
	}
	totalDays := int(end.Sub(start).Hours() / 24)
	_, err = tx.Exec(context.Background(), `
		UPDATE orders SET start_date = $1, end_date = $2, total_days = $3,
		       estimated_amount = $4, total_amount = $5, rescheduled_count = rescheduled_count + 1,
		       updated_at = now()
		WHERE id = $6`, start, end, totalDays, newBase, newTotal, orderID)
	if err != nil {
		InternalError(c, "改期失败")
		return
	}
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		ownerID, "订单已改期",
		fmt.Sprintf("订单 %s 已改期至 %s 至 %s", orderNo, in.StartDate, in.EndDate),
		fmt.Sprintf(`{"order_id": %d}`, orderID))
	var sitterUserID int64
	_ = tx.QueryRow(context.Background(), "SELECT user_id FROM sitters WHERE id = $1", sitterID).Scan(&sitterUserID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		sitterUserID, "订单改期提醒",
		fmt.Sprintf("订单 %s 改期至 %s 至 %s", orderNo, in.StartDate, in.EndDate),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "改期失败")
		return
	}
	OK(c, gin.H{"order_id": orderID, "start_date": in.StartDate, "end_date": in.EndDate,
		"total_days": totalDays, "total_amount": newTotal})
}

// RenewOrder handles PUT /api/v1/pet-owner/orders/:id/renew
func RenewOrder(c *gin.Context) {
	orderID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		EndDate string `json:"end_date" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写续费后的结束日期")
		return
	}
	newEnd, err := time.Parse("2006-01-02", in.EndDate)
	if err != nil {
		BadRequest(c, "日期格式应为 YYYY-MM-DD")
		return
	}
	ownerID := c.GetInt64("user_id")

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "开启事务失败")
		return
	}
	defer tx.Rollback(context.Background())

	var (
		status, paymentStatus, orderNo string
		sitterID                       int64
		start, oldEnd                  time.Time
		petCount                       int
		oldTotal                       float64
	)
	err = tx.QueryRow(context.Background(), `
		SELECT order_no, status::text, payment_status::text, sitter_id, start_date, end_date,
		       pet_count, total_amount
		FROM orders WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL FOR UPDATE`,
		orderID, ownerID).Scan(&orderNo, &status, &paymentStatus, &sitterID, &start, &oldEnd, &petCount, &oldTotal)
	if err != nil {
		NotFound(c, "订单不存在")
		return
	}
	if paymentStatus != "paid" {
		BadRequest(c, "只有已支付订单可以续费")
		return
	}
	if status == "completed" || status == "cancelled" || status == "pending_pay" {
		BadRequest(c, "当前订单状态不支持续费")
		return
	}
	if !newEnd.After(oldEnd) {
		BadRequest(c, "续费日期必须晚于原结束日期")
		return
	}
	delta := int(newEnd.Sub(oldEnd).Hours() / 24)
	if _, err := tx.Exec(context.Background(), "SELECT fn_reserve_capacity($1, $2, $3, $4)",
		sitterID, oldEnd, newEnd, petCount); err != nil {
		Conflict(c, "续费日期容量不足")
		return
	}
	newBase, err := computeStayAmount(context.Background(), tx, sitterID, start, newEnd, petCount)
	if err != nil {
		InternalError(c, err.Error())
		return
	}
	totalDays := int(newEnd.Sub(start).Hours() / 24)
	_, err = tx.Exec(context.Background(), `
		UPDATE orders SET end_date = $1, total_days = $2, estimated_amount = $3, total_amount = $3,
		       updated_at = now() WHERE id = $4`,
		newEnd, totalDays, newBase, orderID)
	if err != nil {
		InternalError(c, "续费失败")
		return
	}
	// 续费产生的增量以一条待支付流水记录，方便后台对账
	if delta > 0 && newBase > oldTotal {
		_, _ = tx.Exec(context.Background(),
			`INSERT INTO payments (order_id, amount, method, transaction_id, status, raw_response)
			 VALUES ($1, $2, 'mock', $3, 'created', '{"type":"renew_addon"}')`,
			orderID, newBase-oldTotal, fmt.Sprintf("RENEW-%s-%d", time.Now().Format("20060102"), orderID))
	}
	var sitterUserID int64
	_ = tx.QueryRow(context.Background(), "SELECT user_id FROM sitters WHERE id = $1", sitterID).Scan(&sitterUserID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		sitterUserID, "订单续费", fmt.Sprintf("订单 %s 续费至 %s", orderNo, in.EndDate),
		fmt.Sprintf(`{"order_id": %d}`, orderID))
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "续费失败")
		return
	}
	OK(c, gin.H{"order_id": orderID, "end_date": in.EndDate, "total_days": totalDays,
		"total_amount": newBase, "extra_days": delta})
}

// StartOrderExpiryWorker 定时取消超时未支付的订单并释放容量。
func StartOrderExpiryWorker(ctx context.Context, interval, timeout time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				cancelExpiredOrders(ctx, timeout)
			}
		}
	}()
}

func cancelExpiredOrders(ctx context.Context, timeout time.Duration) {
	deadline := time.Now().Add(-timeout)
	rows, err := db.Pool.Query(ctx, `
		SELECT id FROM orders
		WHERE status = 'pending_pay' AND payment_status = 'none'
		  AND deleted_at IS NULL AND created_at < $1
		LIMIT 50`, deadline)
	if err != nil {
		return
	}
	ids := make([]int64, 0)
	for rows.Next() {
		var id int64
		if rows.Scan(&id) == nil {
			ids = append(ids, id)
		}
	}
	rows.Close()

	for _, id := range ids {
		if err := expireOneOrder(ctx, id); err != nil {
			log.Printf("[order-expiry] cancel order %d failed: %v", id, err)
		}
	}
}

func expireOneOrder(ctx context.Context, orderID int64) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	var (
		ownerID         int64
		sitterID        int64
		start, end      time.Time
		petCount        int
		orderNo, status string
	)
	if err := tx.QueryRow(ctx, `
		SELECT order_no, status::text, owner_id, sitter_id, start_date, end_date, pet_count
		FROM orders WHERE id = $1 FOR UPDATE`, orderID,
	).Scan(&orderNo, &status, &ownerID, &sitterID, &start, &end, &petCount); err != nil {
		return err
	}
	if status != "pending_pay" {
		return nil
	}
	if _, err := tx.Exec(ctx, `
		UPDATE orders SET status = 'cancelled', cancel_reason = '待支付超时自动取消',
		       cancel_time = now(), updated_at = now() WHERE id = $1`, orderID); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, "SELECT fn_release_capacity($1, $2, $3, $4)",
		sitterID, start, end, petCount); err != nil {
		return err
	}
	_, _ = tx.Exec(ctx, "SELECT fn_notify($1, 'order_cancelled', $2, $3, $4)",
		ownerID, "订单已超时取消",
		fmt.Sprintf("订单 %s 超过支付时限，已自动取消并释放床位", orderNo),
		fmt.Sprintf(`{"order_id": %d}`, orderID))
	return tx.Commit(ctx)
}
