package handler

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// CreateOrder handles POST /api/v1/pet-owner/orders
func CreateOrder(c *gin.Context) {
	ownerID := c.GetInt64("user_id")

	var req model.CreateOrderRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	startDate, err := time.Parse("2006-01-02", req.StartDate)
	if err != nil {
		BadRequest(c, "invalid start_date format, use YYYY-MM-DD")
		return
	}
	endDate, err := time.Parse("2006-01-02", req.EndDate)
	if err != nil {
		BadRequest(c, "invalid end_date format, use YYYY-MM-DD")
		return
	}
	if !endDate.After(startDate) {
		BadRequest(c, "end_date must be after start_date")
		return
	}

	totalDays := int(endDate.Sub(startDate).Hours() / 24)
	petCount := len(req.Pets)

	// Verify sitter exists and is active
	var sitterName string
	var priceMode string
	var dailyPrice, visitPrice float64
	err = db.Pool.QueryRow(context.Background(),
		"SELECT name, price_mode, daily_price, visit_price FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL",
		req.SitterID).Scan(&sitterName, &priceMode, &dailyPrice, &visitPrice)
	if err != nil {
		BadRequest(c, "sitter not found or not active")
		return
	}

	// 计算基础费用：按天计费时逐日叠加节假日上浮规则
	var estimatedAmount float64
	if priceMode == "per_day" {
		type rule struct {
			start, end time.Time
			multiplier float64
		}
		rules := make([]rule, 0)
		if rows, err := db.Pool.Query(context.Background(), `
			SELECT start_date, end_date, multiplier FROM sitter_price_rules
			WHERE sitter_id = $1 AND end_date >= $2 AND start_date < $3`,
			req.SitterID, startDate, endDate); err == nil {
			for rows.Next() {
				var r rule
				if rows.Scan(&r.start, &r.end, &r.multiplier) == nil {
					rules = append(rules, r)
				}
			}
			rows.Close()
		}
		for d := startDate; d.Before(endDate); d = d.AddDate(0, 0, 1) {
			multiplier := 1.0
			for _, r := range rules {
				if !d.Before(r.start) && !d.After(r.end) && r.multiplier > multiplier {
					multiplier = r.multiplier
				}
			}
			estimatedAmount += dailyPrice * multiplier
		}
		estimatedAmount *= float64(petCount)
	} else {
		estimatedAmount = visitPrice * float64(petCount)
	}

	// Start transaction
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "failed to begin transaction")
		return
	}
	defer tx.Rollback(context.Background())

	// ---- 加购服务 ----
	type addonLine struct {
		ID     int64
		Name   string
		Price  float64
		Qty    int
		Amount float64
	}
	addonLines := make([]addonLine, 0, len(req.Addons))
	addonAmount := 0.0
	for _, a := range req.Addons {
		qty := a.Qty
		if qty <= 0 {
			qty = 1
		}
		if qty > 20 {
			BadRequest(c, "加购数量不能超过 20")
			return
		}
		var name string
		var price float64
		err = tx.QueryRow(context.Background(),
			`SELECT name, price FROM sitter_addons
			 WHERE id = $1 AND sitter_id = $2 AND enabled`, a.AddonID, req.SitterID).Scan(&name, &price)
		if err != nil {
			BadRequest(c, "加购服务不存在或已下架")
			return
		}
		amount := price * float64(qty)
		addonAmount += amount
		addonLines = append(addonLines, addonLine{ID: a.AddonID, Name: name, Price: price, Qty: qty, Amount: amount})
	}

	// ---- 优惠券 ----
	subtotal := estimatedAmount + addonAmount
	discountAmount := 0.0
	if req.UserCouponID > 0 {
		var ctype string
		var value, minAmount float64
		var ucStatus string
		err = tx.QueryRow(context.Background(), `
			SELECT c.type, c.value, c.min_amount, uc.status
			FROM user_coupons uc JOIN coupons c ON c.id = uc.coupon_id
			WHERE uc.id = $1 AND uc.user_id = $2 AND c.enabled
			  AND (c.ends_at IS NULL OR c.ends_at >= now())
			FOR UPDATE`, req.UserCouponID, ownerID).Scan(&ctype, &value, &minAmount, &ucStatus)
		if err != nil || ucStatus != "unused" {
			BadRequest(c, "优惠券不可用或已使用")
			return
		}
		if subtotal < minAmount {
			BadRequest(c, "订单金额未达到优惠券使用门槛")
			return
		}
		if ctype == "fixed" {
			discountAmount = value
		} else {
			discountAmount = subtotal * value / 100
		}
		if discountAmount > subtotal {
			discountAmount = subtotal
		}
	}

	// ---- 积分抵扣（100 积分 = 1 元） ----
	remaining := subtotal - discountAmount
	if remaining < 0 {
		remaining = 0
	}
	pointsDiscount := 0.0
	if req.PointsUsed > 0 {
		var balance int
		if err = tx.QueryRow(context.Background(),
			"SELECT points FROM users WHERE id = $1 FOR UPDATE", ownerID).Scan(&balance); err != nil {
			InternalError(c, "读取积分失败")
			return
		}
		if req.PointsUsed > balance {
			BadRequest(c, "积分余额不足")
			return
		}
		maxPoints := int(remaining * pointsPerYuan)
		if req.PointsUsed > maxPoints {
			req.PointsUsed = maxPoints
		}
		pointsDiscount = float64(req.PointsUsed) / pointsPerYuan
		if pointsDiscount > remaining {
			pointsDiscount = remaining
		}
	}
	totalAmount := remaining - pointsDiscount
	if totalAmount < 0 {
		totalAmount = 0
	}

	// Verify all pets belong to this owner and are available
	for _, p := range req.Pets {
		var petOwnerID int64
		err = tx.QueryRow(context.Background(),
			"SELECT owner_id FROM pets WHERE id = $1 AND deleted_at IS NULL FOR UPDATE", p.PetID).Scan(&petOwnerID)
		if err != nil {
			BadRequest(c, fmt.Sprintf("pet %d not found", p.PetID))
			return
		}
		if petOwnerID != ownerID {
			Forbidden(c, fmt.Sprintf("pet %d does not belong to you", p.PetID))
			return
		}

		// Check pet availability (no overlapping active orders)
		var conflict int
		_ = tx.QueryRow(context.Background(),
			`SELECT COUNT(*) FROM order_pet op JOIN orders o ON o.id = op.order_id
			 WHERE op.pet_id = $1 AND o.status IN ('pending_pay','confirmed','accepted','staying')
			 AND o.deleted_at IS NULL AND o.start_date < $2 AND o.end_date > $3`,
			p.PetID, endDate, startDate).Scan(&conflict)
		if conflict > 0 {
			Conflict(c, fmt.Sprintf("pet %d is already booked in this date range", p.PetID))
			return
		}
	}

	// Reserve capacity using DB function
	_, err = tx.Exec(context.Background(),
		"SELECT fn_reserve_capacity($1, $2, $3, $4)",
		req.SitterID, startDate, endDate, petCount)
	if err != nil {
		Conflict(c, "capacity exceeded for selected dates")
		return
	}

	// Generate order number
	var orderNo string
	_ = tx.QueryRow(context.Background(), "SELECT fn_generate_order_no()").Scan(&orderNo)

	// Insert order
	var orderID int64
	err = tx.QueryRow(context.Background(),
		`INSERT INTO orders (order_no, owner_id, sitter_id, status, start_date, end_date, total_days, pet_count,
		                     estimated_amount, total_amount, payment_status, remark,
		                     coupon_id, discount_amount, points_used, addon_amount)
		 VALUES ($1, $2, $3, 'pending_pay', $4, $5, $6, $7, $8, $9, 'none', $10,
		         (SELECT coupon_id FROM user_coupons WHERE id = $11), $12, $13, $14)
		 RETURNING id`,
		orderNo, ownerID, req.SitterID, startDate, endDate, totalDays, petCount,
		estimatedAmount, totalAmount, nilIfEmpty(req.Remark),
		req.UserCouponID, discountAmount, req.PointsUsed, addonAmount,
	).Scan(&orderID)
	if err != nil {
		InternalError(c, "failed to create order: "+err.Error())
		return
	}

	// Insert order_pet records
	for _, p := range req.Pets {
		stayDate := startDate
		if p.StayDate != "" {
			stayDate, _ = time.Parse("2006-01-02", p.StayDate)
		}
		_, err = tx.Exec(context.Background(),
			"INSERT INTO order_pet (order_id, pet_id, stay_date) VALUES ($1, $2, $3)",
			orderID, p.PetID, stayDate)
		if err != nil {
			InternalError(c, "failed to link pet to order")
			return
		}
	}

	// 加购明细、优惠券核销、积分扣减
	for _, a := range addonLines {
		_, err = tx.Exec(context.Background(),
			`INSERT INTO order_addons (order_id, addon_id, name, price, qty, amount)
			 VALUES ($1, $2, $3, $4, $5, $6)`,
			orderID, a.ID, a.Name, a.Price, a.Qty, a.Amount)
		if err != nil {
			InternalError(c, "写入加购明细失败")
			return
		}
	}
	if req.UserCouponID > 0 {
		_, _ = tx.Exec(context.Background(),
			`UPDATE user_coupons SET status = 'used', order_id = $1, used_at = now() WHERE id = $2`,
			orderID, req.UserCouponID)
		_, _ = tx.Exec(context.Background(),
			`UPDATE coupons SET used_qty = used_qty + 1
			 WHERE id = (SELECT coupon_id FROM user_coupons WHERE id = $1)`, req.UserCouponID)
	}
	if req.PointsUsed > 0 {
		var balance int
		if err = tx.QueryRow(context.Background(),
			`UPDATE users SET points = points - $1, updated_at = now() WHERE id = $2 RETURNING points`,
			req.PointsUsed, ownerID).Scan(&balance); err != nil {
			InternalError(c, "扣减积分失败")
			return
		}
		_, _ = tx.Exec(context.Background(),
			`INSERT INTO points_ledger (user_id, change_amount, balance, reason, ref_type, ref_id)
			 VALUES ($1, $2, $3, '下单抵扣', 'order', $4)`,
			ownerID, -req.PointsUsed, balance, orderID)
	}

	// Notify sitter
	var sitterUserID int64
	_ = tx.QueryRow(context.Background(), "SELECT user_id FROM sitters WHERE id = $1", req.SitterID).Scan(&sitterUserID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_created', $2, $3, $4)",
		sitterUserID, "收到新订单",
		fmt.Sprintf("订单 %s：%d 只宠物，%s 至 %s", orderNo, petCount, req.StartDate, req.EndDate),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	// Commit
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "failed to commit transaction")
		return
	}

	Created(c, "order created, please pay within 30 minutes", gin.H{
		"order_id":         orderID,
		"order_no":         orderNo,
		"estimated_amount": estimatedAmount,
		"addon_amount":     addonAmount,
		"discount_amount":  discountAmount,
		"points_used":      req.PointsUsed,
		"points_discount":  pointsDiscount,
		"total_amount":     totalAmount,
		"total_days":       totalDays,
		"pet_count":        petCount,
		"sitter_name":      sitterName,
	})
}

// ListMyOrders handles GET /api/v1/pet-owner/orders
func ListMyOrders(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "10"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 10
	}
	offset := (page - 1) * perPage

	where := "WHERE o.owner_id = $1 AND o.deleted_at IS NULL"
	args := []interface{}{ownerID}
	idx := 2

	if status := c.Query("status_filter"); status != "" {
		where += fmt.Sprintf(" AND o.status = $%d", idx)
		args = append(args, status)
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM orders o "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT o.id, o.order_no, o.status, o.payment_status, o.start_date, o.end_date,
		o.total_days, o.pet_count, o.estimated_amount, o.total_amount, o.created_at, s.name
		FROM orders o JOIN sitters s ON s.id = o.sitter_id
		%s ORDER BY o.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "failed to query orders")
		return
	}
	defer rows.Close()

	orders := make([]model.Order, 0)
	for rows.Next() {
		var o model.Order
		if err := rows.Scan(&o.ID, &o.OrderNo, &o.Status, &o.PaymentStatus, &o.StartDate, &o.EndDate,
			&o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount, &o.CreatedAt, &o.SitterName); err != nil {
			continue
		}
		orders = append(orders, o)
	}

	OK(c, gin.H{"items": orders, "total": total, "page": page, "per_page": perPage})
}

// ListReceivedOrders handles GET /api/v1/sitter/orders/manage
func ListReceivedOrders(c *gin.Context) {
	userID := c.GetInt64("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "10"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 10
	}
	offset := (page - 1) * perPage

	var total int
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM orders o JOIN sitters s ON s.id = o.sitter_id
		 WHERE s.user_id = $1 AND o.deleted_at IS NULL`, userID).Scan(&total)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT o.id, o.order_no, o.status, o.payment_status, o.start_date, o.end_date,
		 o.total_days, o.pet_count, o.estimated_amount, o.total_amount, o.created_at,
		 u.username
		 FROM orders o
		 JOIN sitters s ON s.id = o.sitter_id
		 JOIN users u ON u.id = o.owner_id
		 WHERE s.user_id = $1 AND o.deleted_at IS NULL
		 ORDER BY o.created_at DESC LIMIT $2 OFFSET $3`,
		userID, perPage, offset)
	if err != nil {
		InternalError(c, "failed to query orders")
		return
	}
	defer rows.Close()

	orders := make([]model.Order, 0)
	for rows.Next() {
		var o model.Order
		if err := rows.Scan(&o.ID, &o.OrderNo, &o.Status, &o.PaymentStatus, &o.StartDate, &o.EndDate,
			&o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount, &o.CreatedAt, &o.OwnerName); err != nil {
			continue
		}
		orders = append(orders, o)
	}

	OK(c, gin.H{"items": orders, "total": total, "page": page, "per_page": perPage})
}

// GetOrderDetail handles GET /api/v1/pet-owner/orders/:id and GET /api/v1/sitter/orders/:id
func GetOrderDetail(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	userID := c.GetInt64("user_id")
	role := c.GetString("role")

	var o model.Order
	var query string
	if role == "sitter" {
		query = `SELECT o.id, o.order_no, o.owner_id, o.sitter_id, o.status, o.payment_status,
			o.start_date, o.end_date, o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
			o.cancel_reason, o.cancel_time, o.paid_at, o.accepted_at, o.completed_at, o.evaluated_at,
			o.remark, o.created_at, o.updated_at, s.name, u.username
			FROM orders o JOIN sitters s ON s.id = o.sitter_id JOIN users u ON u.id = o.owner_id
			WHERE o.id = $1 AND s.user_id = $2 AND o.deleted_at IS NULL`
	} else {
		query = `SELECT o.id, o.order_no, o.owner_id, o.sitter_id, o.status, o.payment_status,
			o.start_date, o.end_date, o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
			o.cancel_reason, o.cancel_time, o.paid_at, o.accepted_at, o.completed_at, o.evaluated_at,
			o.remark, o.created_at, o.updated_at, s.name, u.username
			FROM orders o JOIN sitters s ON s.id = o.sitter_id JOIN users u ON u.id = o.owner_id
			WHERE o.id = $1 AND o.owner_id = $2 AND o.deleted_at IS NULL`
	}

	err = db.Pool.QueryRow(context.Background(), query, orderID, userID).Scan(
		&o.ID, &o.OrderNo, &o.OwnerID, &o.SitterID, &o.Status, &o.PaymentStatus,
		&o.StartDate, &o.EndDate, &o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount,
		&o.CancelReason, &o.CancelTime, &o.PaidAt, &o.AcceptedAt, &o.CompletedAt, &o.EvaluatedAt,
		&o.Remark, &o.CreatedAt, &o.UpdatedAt, &o.SitterName, &o.OwnerName)

	if err != nil {
		NotFound(c, "order not found")
		return
	}

	// Get order pets
	petRows, _ := db.Pool.Query(context.Background(),
		`SELECT op.id, op.order_id, op.pet_id, op.stay_date, op.status, p.name
		 FROM order_pet op JOIN pets p ON p.id = op.pet_id WHERE op.order_id = $1`, orderID)
	defer petRows.Close()

	pets := make([]model.OrderPet, 0)
	for petRows.Next() {
		var op model.OrderPet
		_ = petRows.Scan(&op.ID, &op.OrderID, &op.PetID, &op.StayDate, &op.Status, &op.PetName)
		pets = append(pets, op)
	}

	OK(c, model.OrderDetailResponse{Order: o, Pets: pets})
}

// PayOrder handles PUT /api/v1/pet-owner/orders/:id/pay
func PayOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	ownerID := c.GetInt64("user_id")

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "transaction error")
		return
	}
	defer tx.Rollback(context.Background())

	var status, paymentStatus string
	var totalAmount float64
	err = tx.QueryRow(context.Background(),
		"SELECT status, payment_status, total_amount FROM orders WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL FOR UPDATE",
		orderID, ownerID).Scan(&status, &paymentStatus, &totalAmount)
	if err != nil {
		NotFound(c, "order not found")
		return
	}
	if status != "pending_pay" {
		BadRequest(c, "order is not in pending_pay status")
		return
	}
	if paymentStatus == "paid" {
		BadRequest(c, "order already paid")
		return
	}

	now := time.Now()
	_, err = tx.Exec(context.Background(),
		"UPDATE orders SET status = 'confirmed', payment_status = 'paid', paid_at = $1 WHERE id = $2",
		now, orderID)
	if err != nil {
		InternalError(c, "failed to update order")
		return
	}

	txID := fmt.Sprintf("PAY-MOCK-%s-%d", now.Format("20060102"), orderID)
	_, _ = tx.Exec(context.Background(),
		`INSERT INTO payments (order_id, amount, method, transaction_id, status, paid_at)
		 VALUES ($1, $2, 'mock', $3, 'paid', $4)`,
		orderID, totalAmount, txID, now)

	// Notify sitter
	var sitterUserID int64
	_ = tx.QueryRow(context.Background(),
		"SELECT user_id FROM sitters WHERE id = (SELECT sitter_id FROM orders WHERE id = $1)", orderID).Scan(&sitterUserID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_accepted', $2, $3, $4)",
		sitterUserID, "订单已支付", fmt.Sprintf("订单 %d 已支付，金额 %.2f 元", orderID, totalAmount),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "commit failed")
		return
	}

	OK(c, gin.H{"order_id": orderID, "transaction_id": txID, "status": "confirmed"})
}

// CancelOrder handles PUT /api/v1/pet-owner/orders/:id/cancel
func CancelOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	ownerID := c.GetInt64("user_id")

	var req model.CancelOrderRequest
	_ = c.ShouldBindJSON(&req)

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE orders SET status = 'cancelled', cancel_reason = $1, cancel_time = now()
		 WHERE id = $2 AND owner_id = $3 AND status IN ('pending_pay','confirmed','accepted') AND deleted_at IS NULL`,
		nilIfEmpty(req.Reason), orderID, ownerID)
	if err != nil {
		InternalError(c, "failed to cancel order")
		return
	}
	if tag.RowsAffected() == 0 {
		BadRequest(c, "order cannot be cancelled (not found or wrong status)")
		return
	}

	OK(c, gin.H{"message": "order cancelled, capacity released"})
}

// AcceptOrder handles PUT /api/v1/sitter/orders/:id/accept
func AcceptOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	userID := c.GetInt64("user_id")

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE orders SET status = 'accepted', accepted_at = now()
		 WHERE id = $1 AND status = 'confirmed' AND deleted_at IS NULL
		 AND sitter_id = (SELECT id FROM sitters WHERE user_id = $2 AND deleted_at IS NULL)`,
		orderID, userID)
	if err != nil {
		InternalError(c, "failed to accept order")
		return
	}
	if tag.RowsAffected() == 0 {
		BadRequest(c, "order cannot be accepted (not found, wrong status, or not your order)")
		return
	}

	var ownerID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT owner_id FROM orders WHERE id = $1", orderID).Scan(&ownerID)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_accepted', '订单已接单', $2, $3)",
		ownerID, fmt.Sprintf("您的订单 #%d 已被机构接单。", orderID),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	OK(c, gin.H{"status": "accepted"})
}

// RejectOrder handles PUT /api/v1/sitter/orders/:id/reject
func RejectOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	userID := c.GetInt64("user_id")

	var req model.RejectOrderRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "reason is required")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE orders SET status = 'rejected', cancel_reason = $1, cancel_time = now()
		 WHERE id = $2 AND status IN ('pending_pay','confirmed') AND deleted_at IS NULL
		 AND sitter_id = (SELECT id FROM sitters WHERE user_id = $3 AND deleted_at IS NULL)`,
		req.Reason, orderID, userID)
	if err != nil {
		InternalError(c, "failed to reject order")
		return
	}
	if tag.RowsAffected() == 0 {
		BadRequest(c, "order cannot be rejected")
		return
	}

	var ownerID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT owner_id FROM orders WHERE id = $1", orderID).Scan(&ownerID)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_rejected', '订单已被拒绝', $2, $3)",
		ownerID, fmt.Sprintf("您的订单 #%d 被拒绝：%s", orderID, req.Reason),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	OK(c, gin.H{"status": "rejected"})
}

// CompleteOrder handles PUT /api/v1/sitter/orders/:id/complete
func CompleteOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	userID := c.GetInt64("user_id")

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE orders SET status = 'completed', completed_at = now()
		 WHERE id = $1 AND status = 'accepted' AND deleted_at IS NULL
		 AND sitter_id = (SELECT id FROM sitters WHERE user_id = $2 AND deleted_at IS NULL)`,
		orderID, userID)
	if err != nil {
		InternalError(c, "failed to complete order")
		return
	}
	if tag.RowsAffected() == 0 {
		BadRequest(c, "order cannot be completed (not found, wrong status, or not yours)")
		return
	}

	var ownerID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT owner_id FROM orders WHERE id = $1", orderID).Scan(&ownerID)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_completed', '寄养已完成', $2, $3)",
		ownerID, fmt.Sprintf("订单 #%d 已完成，请接回宠物并留下评价。", orderID),
		fmt.Sprintf(`{"order_id": %d}`, orderID))

	OK(c, gin.H{"status": "completed"})
}
