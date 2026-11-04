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
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
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

	petCount := len(req.Pets)

	// Start transaction
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "failed to begin transaction")
		return
	}
	defer tx.Rollback(context.Background())

	// 与预估接口共用同一套核算逻辑（含机构校验、节假日逐日上浮、加购、优惠券、积分）
	pricing, e := computeOrderPricing(context.Background(), tx, ownerID, req.SitterID,
		startDate, endDate, petCount, req.Addons, req.UserCouponID, req.PointsUsed, true)
	if e != nil {
		respondHTTPErr(c, e)
		return
	}
	totalDays := pricing.TotalDays
	sitterName := pricing.SitterName
	estimatedAmount := pricing.EstimatedAmount
	addonAmount := pricing.AddonAmount
	discountAmount := pricing.DiscountAmount
	pointsDiscount := pricing.PointsDiscount
	totalAmount := pricing.TotalAmount
	req.PointsUsed = pricing.PointsUsed // 可能被抵扣上限截断，落库用实际值

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
		Conflict(c, capacityConflictMsg(err))
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
	for _, a := range pricing.AddonLines {
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
		// 金额里已经扣过这张券。核销失败却继续提交，就会留下"钱已减、券还能再用"的重复用券口子，
		// 所以这里必须判错回滚；UPDATE 也带上校验过的状态条件，和退回逻辑一样按行状态天然幂等。
		tag, err := tx.Exec(context.Background(),
			`UPDATE user_coupons SET status = 'used', order_id = $1, used_at = now()
			 WHERE id = $2 AND user_id = $3 AND status = 'unused'`,
			orderID, req.UserCouponID, ownerID)
		if err != nil {
			InternalError(c, "核销优惠券失败: "+err.Error())
			return
		}
		if tag.RowsAffected() != 1 {
			Conflict(c, "优惠券已被使用，请重新选择")
			return
		}
		if _, err = tx.Exec(context.Background(),
			`UPDATE coupons SET used_qty = used_qty + 1
			 WHERE id = (SELECT coupon_id FROM user_coupons WHERE id = $1)`, req.UserCouponID); err != nil {
			InternalError(c, "更新优惠券用量失败: "+err.Error())
			return
		}
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
		"SELECT fn_notify($1, 'order_created', $2, $3, $4, $5)",
		sitterUserID, "收到新订单",
		fmt.Sprintf("订单 %s：%d 只宠物，%s 至 %s", orderNo, petCount, req.StartDate, req.EndDate),
		fmt.Sprintf(`{"order_id": %d}`, orderID), "business")

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

// validOrderStatus 订单状态白名单，用于 status_filter 多状态筛选校验
var validOrderStatus = map[string]bool{
	"pending_pay": true, "confirmed": true, "accepted": true, "staying": true,
	"completed": true, "evaluated": true, "cancelled": true, "rejected": true,
}

// EstimateOrder handles GET /api/v1/pet-owner/sitters/:id/estimate
// 下单前费用预估：与下单共用 computeOrderPricing，口径一致（含节假日逐日上浮、加购、优惠券、积分）。
// 入参（query）：start_date、end_date（YYYY-MM-DD，必填）、pet_ids（逗号分隔）、
// addons（逗号分隔，元素为 addon_id 或 addon_id:qty，qty 默认 1）、user_coupon_id、points_used。
func EstimateOrder(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || sitterID == 0 {
		BadRequest(c, "invalid sitter id")
		return
	}
	ownerID := c.GetInt64("user_id")

	startDate, err := time.Parse("2006-01-02", c.Query("start_date"))
	if err != nil {
		BadRequest(c, "invalid start_date format, use YYYY-MM-DD")
		return
	}
	endDate, err := time.Parse("2006-01-02", c.Query("end_date"))
	if err != nil {
		BadRequest(c, "invalid end_date format, use YYYY-MM-DD")
		return
	}
	if !endDate.After(startDate) {
		BadRequest(c, "end_date must be after start_date")
		return
	}

	petIDs := make([]int64, 0, 10)
	for _, p := range strings.Split(c.Query("pet_ids"), ",") {
		if id, e := strconv.ParseInt(strings.TrimSpace(p), 10, 64); e == nil && id > 0 {
			petIDs = append(petIDs, id)
		}
	}
	if len(petIDs) == 0 || len(petIDs) > 10 {
		BadRequest(c, "pet_ids 为空或超过 10 只")
		return
	}

	// 宠物归属校验（只读，不做档期冲突检查）
	var owned int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM pets WHERE owner_id = $1 AND id = ANY($2) AND deleted_at IS NULL",
		ownerID, petIDs).Scan(&owned)
	if owned != len(petIDs) {
		BadRequest(c, "宠物不存在或不属于当前账号")
		return
	}

	addons := make([]model.CreateOrderAddon, 0, 4)
	for _, item := range strings.Split(c.Query("addons"), ",") {
		item = strings.TrimSpace(item)
		if item == "" {
			continue
		}
		idStr, qtyStr := item, "1"
		if i := strings.IndexByte(item, ':'); i >= 0 {
			idStr, qtyStr = item[:i], item[i+1:]
		}
		id, e1 := strconv.ParseInt(idStr, 10, 64)
		qty, e2 := strconv.Atoi(qtyStr)
		if e1 != nil || id <= 0 {
			BadRequest(c, "addons 参数格式：id 或 id:qty，逗号分隔")
			return
		}
		if e2 != nil || qty <= 0 {
			qty = 1
		}
		addons = append(addons, model.CreateOrderAddon{AddonID: id, Qty: qty})
	}

	userCouponID, _ := strconv.ParseInt(c.Query("user_coupon_id"), 10, 64)
	pointsUsed, _ := strconv.Atoi(c.Query("points_used"))

	pricing, e := computeOrderPricing(context.Background(), db.Pool, ownerID, sitterID,
		startDate, endDate, len(petIDs), addons, userCouponID, pointsUsed, false)
	if e != nil {
		respondHTTPErr(c, e)
		return
	}

	breakdown := make([]gin.H, 0, len(pricing.AddonLines)+3)
	breakdown = append(breakdown, gin.H{
		"type": "base", "label": "寄养费用", "amount": pricing.EstimatedAmount,
	})
	for _, line := range pricing.AddonLines {
		breakdown = append(breakdown, gin.H{
			"type": "addon", "label": line.Name, "price": line.Price, "qty": line.Qty, "amount": line.Amount,
		})
	}
	if pricing.DiscountAmount > 0 {
		breakdown = append(breakdown, gin.H{
			"type": "discount", "label": "优惠券-" + pricing.CouponTitle, "amount": -pricing.DiscountAmount,
		})
	}
	if pricing.PointsDiscount > 0 {
		breakdown = append(breakdown, gin.H{
			"type": "points", "label": "积分抵扣", "points_used": pricing.PointsUsed,
			"amount": -pricing.PointsDiscount,
		})
	}

	OK(c, gin.H{
		"sitter_id":        sitterID,
		"sitter_name":      pricing.SitterName,
		"total_days":       pricing.TotalDays,
		"pet_count":        len(petIDs),
		"estimated_amount": pricing.EstimatedAmount,
		"addon_amount":     pricing.AddonAmount,
		"subtotal":         pricing.Subtotal,
		"discount_amount":  pricing.DiscountAmount,
		"points_used":      pricing.PointsUsed,
		"points_discount":  pricing.PointsDiscount,
		"total_amount":     pricing.TotalAmount,
		"breakdown":        breakdown,
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
		// 支持逗号分隔的多状态筛选（如 confirmed,accepted,staying），单值同样兼容
		seen := make([]string, 0, 8)
		for _, s := range strings.Split(status, ",") {
			s = strings.TrimSpace(s)
			if s == "" {
				continue
			}
			if !validOrderStatus[s] {
				BadRequest(c, "未知订单状态: "+s)
				return
			}
			seen = append(seen, s)
		}
		if len(seen) > 0 {
			where += fmt.Sprintf(" AND o.status::text = ANY($%d)", idx)
			args = append(args, seen)
			idx++
		}
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM orders o "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT o.id, o.order_no, o.status, o.payment_status, o.start_date::text, o.end_date::text,
		o.total_days, o.pet_count, o.estimated_amount, o.total_amount, o.created_at, s.name, o.sitter_id
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
			&o.TotalDays, &o.PetCount, &o.EstimatedAmount, &o.TotalAmount, &o.CreatedAt, &o.SitterName,
			&o.SitterID); err != nil {
			log.Printf("[ListMyOrders] scan error: %v", err)
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
		`SELECT o.id, o.order_no, o.status, o.payment_status, o.start_date::text, o.end_date::text,
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
			log.Printf("[ListReceivedOrders] scan error: %v", err)
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
			o.start_date::text, o.end_date::text, o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
			o.cancel_reason, o.cancel_time, o.paid_at, o.accepted_at, o.completed_at, o.evaluated_at,
			o.remark, o.created_at, o.updated_at, s.name, u.username
			FROM orders o JOIN sitters s ON s.id = o.sitter_id JOIN users u ON u.id = o.owner_id
			WHERE o.id = $1 AND s.user_id = $2 AND o.deleted_at IS NULL`
	} else {
		query = `SELECT o.id, o.order_no, o.owner_id, o.sitter_id, o.status, o.payment_status,
			o.start_date::text, o.end_date::text, o.total_days, o.pet_count, o.estimated_amount, o.total_amount,
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

	// 类型错误等查询故障不能被当成"订单不存在"，否则问题会被 404 掩盖
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			NotFound(c, "order not found")
		} else {
			log.Printf("[GetOrderDetail] scan error: %v", err)
			InternalError(c, "读取订单失败")
		}
		return
	}

	// Get order pets
	petRows, err := db.Pool.Query(context.Background(),
		`SELECT op.id, op.order_id, op.pet_id, op.stay_date::text, op.status, p.name
		 FROM order_pet op JOIN pets p ON p.id = op.pet_id WHERE op.order_id = $1`, orderID)
	if err != nil {
		log.Printf("[GetOrderDetail] order_pet query error: %v", err)
		petRows = nil
	} else {
		defer petRows.Close()
	}

	pets := make([]model.OrderPet, 0)
	for petRows != nil && petRows.Next() {
		var op model.OrderPet
		if err := petRows.Scan(&op.ID, &op.OrderID, &op.PetID, &op.StayDate, &op.Status, &op.PetName); err != nil {
			log.Printf("[GetOrderDetail] order_pet scan error: %v", err)
			continue
		}
		pets = append(pets, op)
	}

	// 附带动态与评价，省去客户端二次补拉
	dynRows, _ := db.Pool.Query(context.Background(),
		`SELECT id, order_id, sitter_id, author_id, content, photos, tags, created_at, updated_at
		 FROM dynamics WHERE order_id = $1 ORDER BY created_at DESC LIMIT 20`, orderID)
	dynamics := make([]model.Dynamic, 0)
	if dynRows != nil {
		for dynRows.Next() {
			var d model.Dynamic
			if err := dynRows.Scan(&d.ID, &d.OrderID, &d.SitterID, &d.AuthorID, &d.Content,
				&d.Photos, &d.Tags, &d.CreatedAt, &d.UpdatedAt); err == nil {
				dynamics = append(dynamics, d)
			}
		}
		dynRows.Close()
	}

	var review *model.Review
	var r model.Review
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT id, order_id, sitter_id, owner_id, overall_score, service_score, environment_score,
		 hygiene_score, owner_comment, owner_photos, reply_text, reply_at, is_visible, created_at, updated_at
		 FROM reviews WHERE order_id = $1`, orderID,
	).Scan(&r.ID, &r.OrderID, &r.SitterID, &r.OwnerID, &r.OverallScore, &r.ServiceScore,
		&r.EnvironmentScore, &r.HygieneScore, &r.OwnerComment, &r.OwnerPhotos,
		&r.ReplyText, &r.ReplyAt, &r.IsVisible, &r.CreatedAt, &r.UpdatedAt); err == nil {
		review = &r
	}

	// 支付/退款流水仅主人可见
	var payments []model.Payment
	if role != "sitter" {
		payRows, _ := db.Pool.Query(context.Background(),
			`SELECT id, amount, method::text, status, COALESCE(transaction_id, ''),
			        to_char(created_at, 'YYYY-MM-DD HH24:MI'),
			        COALESCE(to_char(paid_at, 'YYYY-MM-DD HH24:MI'), ''),
			        COALESCE(to_char(refunded_at, 'YYYY-MM-DD HH24:MI'), '')
			 FROM payments WHERE order_id = $1 ORDER BY id DESC`, orderID)
		payments = make([]model.Payment, 0)
		if payRows != nil {
			for payRows.Next() {
				var p model.Payment
				if err := payRows.Scan(&p.ID, &p.Amount, &p.Method, &p.Status, &p.TransactionID,
					&p.CreatedAt, &p.PaidAt, &p.RefundedAt); err == nil {
					payments = append(payments, p)
				}
			}
			payRows.Close()
		}
	}

	OK(c, model.OrderDetailResponse{Order: o, Pets: pets, Dynamics: dynamics, Review: review, Payments: payments})
}

// capacityConflictMsg 把 fn_reserve_capacity 抛出的英文异常翻译成主人看得懂的提示，
// 并带上是哪一天满了：换个日期就能自己解决，不该只看到一句英文。
func capacityConflictMsg(err error) string {
	const key = "Capacity exceeded on "
	msg := err.Error()
	if i := strings.Index(msg, key); i >= 0 {
		rest := msg[i+len(key):]
		if j := strings.IndexByte(rest, ':'); j > 0 {
			return fmt.Sprintf("%s 当天容量已满，请换个日期或选择其他机构", rest[:j])
		}
	}
	return "所选日期容量已满，请换个日期或选择其他机构"
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
	// 0 元订单（全额抵扣）不需要支付流水，直接确认；否则 payments 的 amount > 0 约束会让事务提交失败
	txID := ""
	if totalAmount <= 0 {
		if _, err = tx.Exec(context.Background(),
			"UPDATE orders SET status = 'confirmed', payment_status = 'paid', paid_at = $1 WHERE id = $2",
			now, orderID); err != nil {
			InternalError(c, "failed to update order")
			return
		}
	} else {
		if _, err = tx.Exec(context.Background(),
			"UPDATE orders SET status = 'confirmed', payment_status = 'paid', paid_at = $1 WHERE id = $2",
			now, orderID); err != nil {
			InternalError(c, "failed to update order")
			return
		}

		txID = fmt.Sprintf("PAY-MOCK-%s-%d", now.Format("20060102"), orderID)
		if _, err = tx.Exec(context.Background(),
			`INSERT INTO payments (order_id, amount, method, transaction_id, status, paid_at)
			 VALUES ($1, $2, 'mock', $3, 'paid', $4)`,
			orderID, totalAmount, txID, now); err != nil {
			log.Printf("[PayOrder] insert payment error: %v", err)
			InternalError(c, "写入支付流水失败: "+err.Error())
			return
		}
	}

	// Notify sitter
	var sitterUserID int64
	_ = tx.QueryRow(context.Background(),
		"SELECT user_id FROM sitters WHERE id = (SELECT sitter_id FROM orders WHERE id = $1)", orderID).Scan(&sitterUserID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'order_accepted', $2, $3, $4, $5)",
		sitterUserID, "订单已支付", fmt.Sprintf("订单 %d 已支付，金额 %.2f 元", orderID, totalAmount),
		fmt.Sprintf(`{"order_id": %d}`, orderID), "business")

	if err := tx.Commit(context.Background()); err != nil {
		log.Printf("[PayOrder] commit error: %v", err)
		InternalError(c, "支付提交失败: "+err.Error())
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
		"SELECT fn_notify($1, 'order_accepted', '订单已接单', $2, $3, $4)",
		ownerID, fmt.Sprintf("您的订单 #%d 已被机构接单。", orderID),
		fmt.Sprintf(`{"order_id": %d}`, orderID), "personal")

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
		"SELECT fn_notify($1, 'order_rejected', '订单已被拒绝', $2, $3, $4)",
		ownerID, fmt.Sprintf("您的订单 #%d 被拒绝：%s", orderID, req.Reason),
		fmt.Sprintf(`{"order_id": %d}`, orderID), "personal")

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
		"SELECT fn_notify($1, 'order_completed', '寄养已完成', $2, $3, $4)",
		ownerID, fmt.Sprintf("订单 #%d 已完成，请接回宠物并留下评价。", orderID),
		fmt.Sprintf(`{"order_id": %d}`, orderID), "personal")

	OK(c, gin.H{"status": "completed"})
}
