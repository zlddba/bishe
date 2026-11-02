package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminOrder is the row shape used by the admin order list.
// created_at is returned as a formatted string so the value is stable across databases.
type AdminOrder struct {
	ID              int64   `json:"id"`
	OrderNo         string  `json:"order_no"`
	Status          string  `json:"status"`
	PaymentStatus   string  `json:"payment_status"`
	StartDate       string  `json:"start_date"`
	EndDate         string  `json:"end_date"`
	TotalDays       int     `json:"total_days"`
	PetCount        int16   `json:"pet_count"`
	EstimatedAmount float64 `json:"estimated_amount"`
	TotalAmount     float64 `json:"total_amount"`
	CreatedAt       string  `json:"created_at"`
	SitterName      string  `json:"sitter_name"`
	OwnerName       string  `json:"owner_name"`
}

// AdminListOrders handles GET /api/v1/admin/orders
func AdminListOrders(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	where := "WHERE o.deleted_at IS NULL"
	args := []interface{}{}
	idx := 1

	if status := c.Query("status"); status != "" {
		where += fmt.Sprintf(" AND o.status::text = $%d", idx)
		args = append(args, status)
		idx++
	}
	if ownerID := c.Query("owner_id"); ownerID != "" {
		where += fmt.Sprintf(" AND o.owner_id = $%d", idx)
		args = append(args, ownerID)
		idx++
	}
	if sitterID := c.Query("sitter_id"); sitterID != "" {
		where += fmt.Sprintf(" AND o.sitter_id = $%d", idx)
		args = append(args, sitterID)
		idx++
	}
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND (o.order_no ILIKE $%d OR u.username ILIKE $%d OR s.name ILIKE $%d)", idx, idx, idx)
		args = append(args, "%"+q+"%")
		idx++
	}

	var total int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM orders o JOIN sitters s ON s.id = o.sitter_id JOIN users u ON u.id = o.owner_id "+where,
		args...).Scan(&total); err != nil {
		InternalError(c, "统计订单失败: "+err.Error())
		return
	}

	query := fmt.Sprintf(`SELECT o.id, o.order_no, o.status::text, o.payment_status::text,
		o.start_date::text, o.end_date::text,
		o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
		to_char(o.created_at, 'YYYY-MM-DD HH24:MI'),
		s.name, u.username
		FROM orders o
		JOIN sitters s ON s.id = o.sitter_id
		JOIN users u ON u.id = o.owner_id
		%s ORDER BY o.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询订单失败: "+err.Error())
		return
	}
	defer rows.Close()

	orders := make([]AdminOrder, 0)
	for rows.Next() {
		var o AdminOrder
		if err := rows.Scan(&o.ID, &o.OrderNo, &o.Status, &o.PaymentStatus, &o.StartDate, &o.EndDate,
			&o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount, &o.CreatedAt,
			&o.SitterName, &o.OwnerName); err != nil {
			log.Printf("[AdminListOrders] scan error: %v", err)
			continue
		}
		orders = append(orders, o)
	}

	OK(c, gin.H{"items": orders, "total": total, "page": page, "per_page": perPage})
}

func isActiveOrderStatus(s string) bool {
	switch s {
	case "pending_pay", "confirmed", "accepted", "staying":
		return true
	}
	return false
}

// AdminCreateOrder handles POST /api/v1/admin/orders
func AdminCreateOrder(c *gin.Context) {
	var req struct {
		OwnerID         int64    `json:"owner_id" binding:"required"`
		SitterID        int64    `json:"sitter_id" binding:"required"`
		StartDate       string   `json:"start_date" binding:"required"`
		EndDate         string   `json:"end_date" binding:"required"`
		PetIDs          []int64  `json:"pet_ids" binding:"required,min=1,max=10"`
		Status          string   `json:"status" binding:"omitempty,oneof=pending_pay confirmed accepted staying completed evaluated cancelled rejected"`
		PaymentStatus   string   `json:"payment_status" binding:"omitempty,oneof=none paid refunded"`
		EstimatedAmount *float64 `json:"estimated_amount"`
		TotalAmount     *float64 `json:"total_amount"`
		Remark          string   `json:"remark"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	startDate, err := time.Parse("2006-01-02", req.StartDate)
	if err != nil {
		BadRequest(c, "开始日期格式应为 YYYY-MM-DD")
		return
	}
	endDate, err := time.Parse("2006-01-02", req.EndDate)
	if err != nil {
		BadRequest(c, "结束日期格式应为 YYYY-MM-DD")
		return
	}
	if !endDate.After(startDate) {
		BadRequest(c, "结束日期必须晚于开始日期")
		return
	}
	if !adminOwnerExists(req.OwnerID) {
		BadRequest(c, "主人不存在或该用户不是宠物主人")
		return
	}

	var priceMode string
	var dailyPrice, visitPrice float64
	err = db.Pool.QueryRow(context.Background(),
		"SELECT price_mode::text, daily_price, visit_price FROM sitters WHERE id = $1 AND deleted_at IS NULL",
		req.SitterID).Scan(&priceMode, &dailyPrice, &visitPrice)
	if err != nil {
		BadRequest(c, "机构不存在")
		return
	}

	// 校验宠物归属与该时间段是否可用
	for _, petID := range req.PetIDs {
		var ownerID int64
		err = db.Pool.QueryRow(context.Background(),
			"SELECT owner_id FROM pets WHERE id = $1 AND deleted_at IS NULL", petID).Scan(&ownerID)
		if err != nil {
			BadRequest(c, fmt.Sprintf("宠物 %d 不存在", petID))
			return
		}
		if ownerID != req.OwnerID {
			BadRequest(c, fmt.Sprintf("宠物 %d 不属于该主人", petID))
			return
		}
		var available bool
		_ = db.Pool.QueryRow(context.Background(),
			"SELECT fn_check_pet_available($1, $2, $3)", petID, startDate, endDate).Scan(&available)
		if !available {
			Conflict(c, fmt.Sprintf("宠物 %d 在该时间段已有订单", petID))
			return
		}
	}

	totalDays := int(endDate.Sub(startDate).Hours() / 24)
	petCount := len(req.PetIDs)
	var estimated float64
	if priceMode == "per_day" {
		estimated = float64(totalDays) * dailyPrice * float64(petCount)
	} else {
		estimated = visitPrice * float64(petCount)
	}
	if req.EstimatedAmount != nil {
		estimated = *req.EstimatedAmount
	}
	totalAmount := estimated
	if req.TotalAmount != nil {
		totalAmount = *req.TotalAmount
	}
	status := req.Status
	if status == "" {
		status = "pending_pay"
	}
	paymentStatus := req.PaymentStatus
	if paymentStatus == "" {
		paymentStatus = "none"
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "开启事务失败")
		return
	}
	defer tx.Rollback(context.Background())

	if isActiveOrderStatus(status) {
		if _, err := tx.Exec(context.Background(),
			"SELECT fn_reserve_capacity($1, $2, $3, $4)",
			req.SitterID, startDate, endDate, petCount); err != nil {
			Conflict(c, "所选日期机构容量不足")
			return
		}
	}

	var orderNo string
	_ = tx.QueryRow(context.Background(), "SELECT fn_generate_order_no()").Scan(&orderNo)

	var orderID int64
	err = tx.QueryRow(context.Background(),
		`INSERT INTO orders (order_no, owner_id, sitter_id, status, start_date, end_date, total_days,
		 pet_count, estimated_amount, total_amount, payment_status, remark, paid_at)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12,
		         CASE WHEN $11::text = 'paid' THEN now() ELSE NULL END)
		 RETURNING id`,
		orderNo, req.OwnerID, req.SitterID, status, startDate, endDate, totalDays,
		petCount, estimated, totalAmount, paymentStatus, nilIfEmpty(req.Remark)).Scan(&orderID)
	if err != nil {
		InternalError(c, "创建订单失败: "+err.Error())
		return
	}

	for _, petID := range req.PetIDs {
		if _, err := tx.Exec(context.Background(),
			"INSERT INTO order_pet (order_id, pet_id, stay_date) VALUES ($1, $2, $3)",
			orderID, petID, startDate); err != nil {
			InternalError(c, "关联宠物失败")
			return
		}
	}

	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交事务失败")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_ORDER', 'order', $2, $3)",
		adminID, orderID, fmt.Sprintf(`{"order_no": "%s"}`, orderNo))

	Created(c, "订单已创建", gin.H{"id": orderID, "order_no": orderNo})
}

// AdminGetOrderDetail handles GET /api/v1/admin/orders/:id
func AdminGetOrderDetail(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的订单ID")
		return
	}

	var o AdminOrder
	var ownerID, sitterID int64
	var remark *string
	err = db.Pool.QueryRow(context.Background(),
		`SELECT o.id, o.order_no, o.status::text, o.payment_status::text, o.start_date::text, o.end_date::text,
		 o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
		 to_char(o.created_at, 'YYYY-MM-DD HH24:MI'), s.name, u.username,
		 o.owner_id, o.sitter_id, o.remark
		 FROM orders o
		 JOIN sitters s ON s.id = o.sitter_id
		 JOIN users u ON u.id = o.owner_id
		 WHERE o.id = $1 AND o.deleted_at IS NULL`, orderID,
	).Scan(&o.ID, &o.OrderNo, &o.Status, &o.PaymentStatus, &o.StartDate, &o.EndDate,
		&o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount, &o.CreatedAt,
		&o.SitterName, &o.OwnerName, &ownerID, &sitterID, &remark)
	if err != nil {
		NotFound(c, "订单不存在")
		return
	}

	petRows, err := db.Pool.Query(context.Background(),
		`SELECT op.pet_id, p.name FROM order_pet op JOIN pets p ON p.id = op.pet_id
		 WHERE op.order_id = $1 ORDER BY op.id`, orderID)
	if err != nil {
		InternalError(c, "查询订单宠物失败")
		return
	}
	defer petRows.Close()

	type orderPetBrief struct {
		PetID   int64  `json:"pet_id"`
		PetName string `json:"pet_name"`
	}
	pets := make([]orderPetBrief, 0)
	petIDs := make([]int64, 0)
	for petRows.Next() {
		var p orderPetBrief
		if err := petRows.Scan(&p.PetID, &p.PetName); err != nil {
			continue
		}
		pets = append(pets, p)
		petIDs = append(petIDs, p.PetID)
	}

	// 关联寄养动态
	type dynamicBrief struct {
		ID        int64    `json:"id"`
		Content   string   `json:"content"`
		Photos    []string `json:"photos"`
		CreatedAt string   `json:"created_at"`
	}
	dynamics := make([]dynamicBrief, 0)
	dynRows, err := db.Pool.Query(context.Background(),
		`SELECT id, content, photos, to_char(created_at, 'YYYY-MM-DD HH24:MI')
		 FROM dynamics WHERE order_id = $1 ORDER BY created_at DESC`, orderID)
	if err == nil {
		for dynRows.Next() {
			var d dynamicBrief
			if err := dynRows.Scan(&d.ID, &d.Content, &d.Photos, &d.CreatedAt); err == nil {
				dynamics = append(dynamics, d)
			}
		}
		dynRows.Close()
	}

	// 关联评价（一单一评，可能没有）
	var review interface{}
	var (
		rID          int64
		rOverall     int16
		rService     int16
		rEnvironment int16
		rHygiene     int16
		rComment     *string
		rPhotos      []string
		rReply       *string
		rCreatedAt   string
		rVisible     bool
	)
	err = db.Pool.QueryRow(context.Background(),
		`SELECT id, overall_score, service_score, environment_score, hygiene_score,
		        owner_comment, owner_photos, reply_text,
		        to_char(created_at, 'YYYY-MM-DD HH24:MI'), is_visible
		 FROM reviews WHERE order_id = $1`, orderID,
	).Scan(&rID, &rOverall, &rService, &rEnvironment, &rHygiene,
		&rComment, &rPhotos, &rReply, &rCreatedAt, &rVisible)
	if err == nil {
		review = gin.H{
			"id":                rID,
			"overall_score":     rOverall,
			"service_score":     rService,
			"environment_score": rEnvironment,
			"hygiene_score":     rHygiene,
			"owner_comment":     rComment,
			"owner_photos":      rPhotos,
			"reply_text":        rReply,
			"created_at":        rCreatedAt,
			"is_visible":        rVisible,
		}
	}

	// 支付/退款流水
	type paymentBrief struct {
		ID            int64   `json:"id"`
		Amount        float64 `json:"amount"`
		Method        string  `json:"method"`
		Status        string  `json:"status"`
		TransactionID string  `json:"transaction_id"`
		CreatedAt     string  `json:"created_at"`
		PaidAt        *string `json:"paid_at"`
		RefundedAt    *string `json:"refunded_at"`
		RawResponse   string  `json:"raw_response"`
	}
	payments := make([]paymentBrief, 0)
	payRows, err := db.Pool.Query(context.Background(),
		`SELECT id, amount, method::text, status, COALESCE(transaction_id, ''),
		        to_char(created_at, 'YYYY-MM-DD HH24:MI'),
		        to_char(paid_at, 'YYYY-MM-DD HH24:MI'),
		        to_char(refunded_at, 'YYYY-MM-DD HH24:MI'),
		        COALESCE(raw_response::text, '')
		 FROM payments WHERE order_id = $1 ORDER BY id DESC`, orderID)
	if err == nil {
		for payRows.Next() {
			var p paymentBrief
			if err := payRows.Scan(&p.ID, &p.Amount, &p.Method, &p.Status, &p.TransactionID,
				&p.CreatedAt, &p.PaidAt, &p.RefundedAt, &p.RawResponse); err == nil {
				payments = append(payments, p)
			}
		}
		payRows.Close()
	}

	OK(c, gin.H{
		"order":     o,
		"owner_id":  ownerID,
		"sitter_id": sitterID,
		"remark":    remark,
		"pet_ids":   petIDs,
		"pets":      pets,
		"dynamics":  dynamics,
		"review":    review,
		"payments":  payments,
	})
}

// AdminUpdateOrder handles PUT /api/v1/admin/orders/:id
func AdminUpdateOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的订单ID")
		return
	}

	var req struct {
		StartDate       *string  `json:"start_date"`
		EndDate         *string  `json:"end_date"`
		PetIDs          *[]int64 `json:"pet_ids"`
		Status          *string  `json:"status" binding:"omitempty,oneof=pending_pay confirmed accepted staying completed evaluated cancelled rejected"`
		PaymentStatus   *string  `json:"payment_status" binding:"omitempty,oneof=none paid refunded"`
		EstimatedAmount *float64 `json:"estimated_amount" binding:"omitempty,min=0"`
		TotalAmount     *float64 `json:"total_amount" binding:"omitempty,min=0"`
		Remark          *string  `json:"remark"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "开启事务失败")
		return
	}
	defer tx.Rollback(context.Background())

	var (
		oldStatus            string
		oldStart, oldEnd     time.Time
		oldPetCount, ownerID int
		sitterID             int64
	)
	err = tx.QueryRow(context.Background(),
		`SELECT status::text, start_date, end_date, pet_count, owner_id, sitter_id
		 FROM orders WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`, orderID,
	).Scan(&oldStatus, &oldStart, &oldEnd, &oldPetCount, &ownerID, &sitterID)
	if err != nil {
		NotFound(c, "订单不存在")
		return
	}

	startDate, endDate := oldStart, oldEnd
	if req.StartDate != nil {
		startDate, err = time.Parse("2006-01-02", *req.StartDate)
		if err != nil {
			BadRequest(c, "开始日期格式应为 YYYY-MM-DD")
			return
		}
	}
	if req.EndDate != nil {
		endDate, err = time.Parse("2006-01-02", *req.EndDate)
		if err != nil {
			BadRequest(c, "结束日期格式应为 YYYY-MM-DD")
			return
		}
	}
	if !endDate.After(startDate) {
		BadRequest(c, "结束日期必须晚于开始日期")
		return
	}

	petCount := oldPetCount
	if req.PetIDs != nil {
		if len(*req.PetIDs) == 0 || len(*req.PetIDs) > 10 {
			BadRequest(c, "宠物数量需在 1 到 10 之间")
			return
		}
		for _, petID := range *req.PetIDs {
			var petOwnerID int64
			err = tx.QueryRow(context.Background(),
				"SELECT owner_id FROM pets WHERE id = $1 AND deleted_at IS NULL", petID).Scan(&petOwnerID)
			if err != nil {
				BadRequest(c, fmt.Sprintf("宠物 %d 不存在", petID))
				return
			}
			if petOwnerID != int64(ownerID) {
				BadRequest(c, fmt.Sprintf("宠物 %d 不属于该订单的主人", petID))
				return
			}
			var conflict int
			_ = tx.QueryRow(context.Background(),
				`SELECT COUNT(*) FROM order_pet op JOIN orders o ON o.id = op.order_id
				 WHERE op.pet_id = $1 AND o.id <> $2 AND o.status IN ('pending_pay','confirmed','accepted','staying')
				 AND o.deleted_at IS NULL AND o.start_date < $3 AND o.end_date > $4`,
				petID, orderID, endDate, startDate).Scan(&conflict)
			if conflict > 0 {
				Conflict(c, fmt.Sprintf("宠物 %d 在该时间段已有其他订单", petID))
				return
			}
		}
		petCount = len(*req.PetIDs)
	}

	newStatus := oldStatus
	if req.Status != nil {
		newStatus = *req.Status
	}
	oldActive := isActiveOrderStatus(oldStatus)
	newActive := isActiveOrderStatus(newStatus)
	rangeChanged := !startDate.Equal(oldStart) || !endDate.Equal(oldEnd) || petCount != oldPetCount

	// 先处理状态迁移：取消/拒绝由触发器释放容量（staying 不在触发器覆盖范围内，单独释放）
	if oldActive && !newActive {
		if _, err := tx.Exec(context.Background(),
			"UPDATE orders SET status = $1 WHERE id = $2", newStatus, orderID); err != nil {
			InternalError(c, "更新订单状态失败: "+err.Error())
			return
		}
		if oldStatus == "staying" {
			_, _ = tx.Exec(context.Background(),
				"SELECT fn_release_capacity($1, $2, $3, $4)",
				sitterID, oldStart, oldEnd, oldPetCount)
		}
	}

	// 仍在占用容量且时间/宠物发生变化：先释放旧占用
	if oldActive && newActive && rangeChanged {
		if _, err := tx.Exec(context.Background(),
			"SELECT fn_release_capacity($1, $2, $3, $4)",
			sitterID, oldStart, oldEnd, oldPetCount); err != nil {
			InternalError(c, "释放原容量失败: "+err.Error())
			return
		}
	}

	totalDays := int(endDate.Sub(startDate).Hours() / 24)
	sets := []string{
		"start_date = $1", "end_date = $2", "total_days = $3", "pet_count = $4",
	}
	args := []interface{}{startDate, endDate, totalDays, petCount}
	idx := 5
	add := func(expr string, v interface{}) {
		sets = append(sets, fmt.Sprintf(expr, idx))
		args = append(args, v)
		idx++
	}
	if req.PaymentStatus != nil {
		add("payment_status = $%d", *req.PaymentStatus)
		if *req.PaymentStatus == "paid" {
			sets = append(sets, "paid_at = COALESCE(paid_at, now())")
		}
	}
	if req.EstimatedAmount != nil {
		add("estimated_amount = $%d", *req.EstimatedAmount)
	}
	if req.TotalAmount != nil {
		add("total_amount = $%d", *req.TotalAmount)
	}
	if req.Remark != nil {
		add("remark = $%d", nilIfEmpty(*req.Remark))
	}
	if newStatus != oldStatus && !(oldActive && !newActive) {
		add("status = $%d", newStatus)
	}

	query := "UPDATE orders SET " + joinComma(sets) + " WHERE id = $" + strconv.Itoa(idx)
	args = append(args, orderID)
	if _, err := tx.Exec(context.Background(), query, args...); err != nil {
		InternalError(c, "更新订单失败: "+err.Error())
		return
	}

	if req.PetIDs != nil {
		if _, err := tx.Exec(context.Background(),
			"DELETE FROM order_pet WHERE order_id = $1", orderID); err != nil {
			InternalError(c, "更新订单宠物失败: "+err.Error())
			return
		}
		for _, petID := range *req.PetIDs {
			if _, err := tx.Exec(context.Background(),
				"INSERT INTO order_pet (order_id, pet_id, stay_date) VALUES ($1, $2, $3)",
				orderID, petID, startDate); err != nil {
				InternalError(c, "更新订单宠物失败: "+err.Error())
				return
			}
		}
	}

	// 管理员手动标记已支付时补一条支付流水，保证财务记录完整
	if req.PaymentStatus != nil && *req.PaymentStatus == "paid" {
		var paidCount int
		_ = tx.QueryRow(context.Background(),
			"SELECT COUNT(*) FROM payments WHERE order_id = $1 AND status = 'paid'", orderID).Scan(&paidCount)
		if paidCount == 0 {
			var paidAmount float64
			if req.TotalAmount != nil {
				paidAmount = *req.TotalAmount
			} else {
				_ = tx.QueryRow(context.Background(),
					"SELECT total_amount FROM orders WHERE id = $1", orderID).Scan(&paidAmount)
			}
			_, _ = tx.Exec(context.Background(),
				`INSERT INTO payments (order_id, amount, method, transaction_id, status, paid_at)
				 VALUES ($1, $2, 'mock', $3, 'paid', now())`,
				orderID, paidAmount, fmt.Sprintf("PAY-ADMIN-%d-%d", orderID, time.Now().Unix()))
		}
	}

	// 订单仍有效且新占用尚未登记：重新占用容量
	if newActive && (!oldActive || rangeChanged) {
		if _, err := tx.Exec(context.Background(),
			"SELECT fn_reserve_capacity($1, $2, $3, $4)",
			sitterID, startDate, endDate, petCount); err != nil {
			Conflict(c, "所选日期机构容量不足")
			return
		}
	}

	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交事务失败")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_ORDER', 'order', $2, $3)",
		adminID, orderID, fmt.Sprintf(`{"status": "%s"}`, newStatus))

	OK(c, gin.H{"id": orderID})
}

// AdminDeleteOrder handles DELETE /api/v1/admin/orders/:id (soft delete + 释放容量)
func AdminDeleteOrder(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的订单ID")
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "开启事务失败")
		return
	}
	defer tx.Rollback(context.Background())

	var (
		status             string
		startDate, endDate time.Time
		petCount           int
		sitterID           int64
	)
	err = tx.QueryRow(context.Background(),
		`SELECT status::text, start_date, end_date, pet_count, sitter_id
		 FROM orders WHERE id = $1 AND deleted_at IS NULL FOR UPDATE`, orderID,
	).Scan(&status, &startDate, &endDate, &petCount, &sitterID)
	if err != nil {
		NotFound(c, "订单不存在或已删除")
		return
	}

	if isActiveOrderStatus(status) {
		if _, err := tx.Exec(context.Background(),
			"SELECT fn_release_capacity($1, $2, $3, $4)",
			sitterID, startDate, endDate, petCount); err != nil {
			InternalError(c, "释放容量失败: "+err.Error())
			return
		}
	}

	if _, err := tx.Exec(context.Background(),
		"UPDATE orders SET deleted_at = now() WHERE id = $1", orderID); err != nil {
		InternalError(c, "删除订单失败: "+err.Error())
		return
	}

	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交事务失败")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_ORDER', 'order', $2, '{}')", adminID, orderID)

	OK(c, gin.H{"id": orderID})
}
