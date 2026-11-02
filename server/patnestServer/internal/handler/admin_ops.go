package handler

import (
	"context"
	"encoding/csv"
	"fmt"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// writeAudit 统一写后台审计日志。
func writeAudit(c *gin.Context, action, targetType string, targetID int64, detail string) {
	if detail == "" {
		detail = "{}"
	}
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, $2, $3, NULLIF($4::bigint, 0::bigint), $5::jsonb)",
		c.GetInt64("user_id"), action, targetType, targetID, detail)
}

func paging(c *gin.Context, maxPerPage int) (int, int, int) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > maxPerPage {
		perPage = 20
	}
	return page, perPage, (page - 1) * perPage
}

// ---------------- 优惠券 ----------------

type couponInput struct {
	Code      string  `json:"code" binding:"required,min=2,max=32"`
	Title     string  `json:"title" binding:"required,max=128"`
	Type      string  `json:"type" binding:"required,oneof=fixed percent"`
	Value     float64 `json:"value" binding:"required,gt=0"`
	MinAmount float64 `json:"min_amount"`
	TotalQty  int     `json:"total_qty"`
	PerUser   int     `json:"per_user"`
	StartsAt  string  `json:"starts_at"`
	EndsAt    string  `json:"ends_at"`
	Enabled   *bool   `json:"enabled"`
}

func AdminListCoupons(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	enabled := c.Query("enabled") // "", "1", "0"
	where := "WHERE ($1 = '' OR c.enabled = ($1 = '1'))"
	var total int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM coupons c "+where, enabled).Scan(&total)

	rows, err := db.Pool.Query(context.Background(), `
		SELECT c.id, c.code, c.title, c.type, c.value, c.min_amount, c.total_qty, c.claimed_qty,
		       c.used_qty, c.per_user, to_char(c.starts_at, 'YYYY-MM-DD'),
		       COALESCE(to_char(c.ends_at, 'YYYY-MM-DD'), ''), c.enabled,
		       to_char(c.created_at, 'YYYY-MM-DD HH24:MI')
		FROM coupons c `+where+`
		ORDER BY c.id DESC LIMIT $2 OFFSET $3`, enabled, perPage, offset)
	if err != nil {
		InternalError(c, "查询优惠券失败: "+err.Error())
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var (
			id, totalQty, claimed, used, perUser int64
			code, title, ctype, starts, ends, at string
			value, minAmount                     float64
			on                                   bool
		)
		if err := rows.Scan(&id, &code, &title, &ctype, &value, &minAmount, &totalQty, &claimed,
			&used, &perUser, &starts, &ends, &on, &at); err != nil {
			continue
		}
		items = append(items, gin.H{
			"id": id, "code": code, "title": title, "type": ctype, "value": value,
			"min_amount": minAmount, "total_qty": totalQty, "claimed_qty": claimed,
			"used_qty": used, "per_user": perUser, "starts_at": starts, "ends_at": ends,
			"enabled": on, "created_at": at,
		})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

func couponTimes(in couponInput) (time.Time, *time.Time, error) {
	start := time.Now()
	if in.StartsAt != "" {
		t, err := time.Parse("2006-01-02", in.StartsAt)
		if err != nil {
			return start, nil, fmt.Errorf("开始日期格式应为 YYYY-MM-DD")
		}
		start = t
	}
	var end *time.Time
	if in.EndsAt != "" {
		t, err := time.Parse("2006-01-02", in.EndsAt)
		if err != nil {
			return start, nil, fmt.Errorf("结束日期格式应为 YYYY-MM-DD")
		}
		t = t.Add(24*time.Hour - time.Second)
		end = &t
	}
	return start, end, nil
}

func AdminCreateCoupon(c *gin.Context) {
	var in couponInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Type == "percent" && (in.Value <= 0 || in.Value > 100) {
		BadRequest(c, "折扣券面值应为 1-100 的百分比")
		return
	}
	start, end, err := couponTimes(in)
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	if in.PerUser <= 0 {
		in.PerUser = 1
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}

	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO coupons (code, title, type, value, min_amount, total_qty, per_user, starts_at, ends_at, enabled)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10) RETURNING id`,
		strings.ToUpper(in.Code), in.Title, in.Type, in.Value, in.MinAmount, in.TotalQty,
		in.PerUser, start, end, enabled).Scan(&id)
	if err != nil {
		if strings.Contains(err.Error(), "uq_coupons_code") || strings.Contains(err.Error(), "coupons_code_key") {
			Conflict(c, "优惠券码已存在")
			return
		}
		InternalError(c, "创建优惠券失败: "+err.Error())
		return
	}
	writeAudit(c, "CREATE_COUPON", "coupon", id, fmt.Sprintf(`{"code": %q}`, in.Code))
	Created(c, "优惠券已创建", gin.H{"id": id})
}

func AdminUpdateCoupon(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in couponInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	start, end, err := couponTimes(in)
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE coupons SET code = $1, title = $2, type = $3, value = $4, min_amount = $5,
		       total_qty = $6, per_user = $7, starts_at = $8, ends_at = $9, enabled = $10, updated_at = now()
		WHERE id = $11`,
		strings.ToUpper(in.Code), in.Title, in.Type, in.Value, in.MinAmount, in.TotalQty,
		in.PerUser, start, end, enabled, id)
	if err != nil {
		Conflict(c, "更新失败，优惠券码可能重复")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "优惠券不存在")
		return
	}
	writeAudit(c, "UPDATE_COUPON", "coupon", id, "{}")
	OK(c, gin.H{"id": id})
}

func AdminDeleteCoupon(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE coupons SET enabled = FALSE, updated_at = now() WHERE id = $1", id)
	if err != nil {
		InternalError(c, "停用优惠券失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "优惠券不存在")
		return
	}
	writeAudit(c, "DISABLE_COUPON", "coupon", id, "{}")
	OK(c, gin.H{"id": id, "enabled": false})
}

// ---------------- 通知模板 ----------------

func AdminListTemplates(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, code, name, title, content, channel, enabled, to_char(updated_at, 'YYYY-MM-DD HH24:MI')
		FROM notification_templates ORDER BY id`)
	if err != nil {
		InternalError(c, "查询模板失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var code, name, title, content, channel, at string
		var enabled bool
		if err := rows.Scan(&id, &code, &name, &title, &content, &channel, &enabled, &at); err != nil {
			continue
		}
		items = append(items, gin.H{"id": id, "code": code, "name": name, "title": title,
			"content": content, "channel": channel, "enabled": enabled, "updated_at": at})
	}
	OK(c, gin.H{"items": items})
}

type templateInput struct {
	Code    string `json:"code" binding:"required,max=64"`
	Name    string `json:"name" binding:"required,max=64"`
	Title   string `json:"title" binding:"required,max=128"`
	Content string `json:"content" binding:"required"`
	Channel string `json:"channel" binding:"omitempty,oneof=in_app sms wechat"`
	Enabled *bool  `json:"enabled"`
}

func AdminCreateTemplate(c *gin.Context) {
	var in templateInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Channel == "" {
		in.Channel = "in_app"
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}
	var id int64
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO notification_templates (code, name, title, content, channel, enabled)
		VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`,
		in.Code, in.Name, in.Title, in.Content, in.Channel, enabled).Scan(&id)
	if err != nil {
		Conflict(c, "模板编码已存在")
		return
	}
	writeAudit(c, "CREATE_TEMPLATE", "notification_template", id, fmt.Sprintf(`{"code": %q}`, in.Code))
	Created(c, "模板已创建", gin.H{"id": id})
}

func AdminUpdateTemplate(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in templateInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Channel == "" {
		in.Channel = "in_app"
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE notification_templates SET code = $1, name = $2, title = $3, content = $4,
		       channel = $5, enabled = $6, updated_at = now() WHERE id = $7`,
		in.Code, in.Name, in.Title, in.Content, in.Channel, enabled, id)
	if err != nil {
		Conflict(c, "更新失败，模板编码可能重复")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "模板不存在")
		return
	}
	writeAudit(c, "UPDATE_TEMPLATE", "notification_template", id, "{}")
	OK(c, gin.H{"id": id})
}

func AdminDeleteTemplate(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	tag, err := db.Pool.Exec(context.Background(), "DELETE FROM notification_templates WHERE id = $1", id)
	if err != nil {
		InternalError(c, "删除模板失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "模板不存在")
		return
	}
	writeAudit(c, "DELETE_TEMPLATE", "notification_template", id, "{}")
	OK(c, gin.H{"id": id})
}

// ---------------- 角色权限点 ----------------

func AdminGetPermissions(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(),
		"SELECT role, permission, allowed FROM role_permissions ORDER BY role, permission")
	if err != nil {
		InternalError(c, "查询权限失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var role, permission string
		var allowed bool
		if err := rows.Scan(&role, &permission, &allowed); err != nil {
			continue
		}
		items = append(items, gin.H{"role": role, "permission": permission, "allowed": allowed})
	}
	OK(c, gin.H{"items": items})
}

func AdminUpdatePermission(c *gin.Context) {
	var in struct {
		Role       string `json:"role" binding:"required,oneof=admin owner sitter"`
		Permission string `json:"permission" binding:"required,max=64"`
		Allowed    bool   `json:"allowed"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	// 内置超级管理员的关键权限不允许关闭，避免把自己锁死
	if in.Role == "admin" && !in.Allowed {
		if in.Permission == "users.read" || in.Permission == "system.settings" {
			BadRequest(c, "管理员核心权限不可关闭")
			return
		}
	}
	_, err := db.Pool.Exec(context.Background(), `
		INSERT INTO role_permissions (role, permission, allowed, updated_at)
		VALUES ($1, $2, $3, now())
		ON CONFLICT (role, permission) DO UPDATE SET allowed = EXCLUDED.allowed, updated_at = now()`,
		in.Role, in.Permission, in.Allowed)
	if err != nil {
		InternalError(c, "更新权限失败")
		return
	}
	writeAudit(c, "UPDATE_PERMISSION", "role", 0,
		fmt.Sprintf(`{"role": %q, "permission": %q, "allowed": %t}`, in.Role, in.Permission, in.Allowed))
	OK(c, in)
}

// ---------------- CSV 导出 ----------------

func AdminExportCSV(c *gin.Context) {
	kind := c.Param("kind")
	filename := fmt.Sprintf("patnest-%s-%s.csv", kind, time.Now().Format("20060102-150405"))
	c.Header("Content-Type", "text/csv; charset=utf-8")
	c.Header("Content-Disposition", `attachment; filename="`+filename+`"`)
	// BOM 让 Excel 正确识别 UTF-8 中文
	_, _ = c.Writer.Write([]byte{0xEF, 0xBB, 0xBF})
	w := csv.NewWriter(c.Writer)
	defer w.Flush()

	switch kind {
	case "orders":
		_ = w.Write([]string{"订单号", "机构", "主人", "状态", "支付状态", "开始", "结束", "宠物数", "金额", "创建时间"})
		rows, err := db.Pool.Query(context.Background(), `
			SELECT o.order_no, s.name, u.username, o.status::text, o.payment_status::text,
			       o.start_date::text, o.end_date::text, o.pet_count, o.total_amount,
			       to_char(o.created_at, 'YYYY-MM-DD HH24:MI')
			FROM orders o JOIN sitters s ON s.id = o.sitter_id JOIN users u ON u.id = o.owner_id
			WHERE o.deleted_at IS NULL ORDER BY o.id DESC LIMIT 20000`)
		if err != nil {
			InternalError(c, "导出订单失败")
			return
		}
		defer rows.Close()
		for rows.Next() {
			var no, sitter, owner, status, pay, start, end, at string
			var pets int64
			var amount float64
			if err := rows.Scan(&no, &sitter, &owner, &status, &pay, &start, &end, &pets, &amount, &at); err == nil {
				_ = w.Write([]string{no, sitter, owner, status, pay, start, end,
					strconv.FormatInt(pets, 10), fmt.Sprintf("%.2f", amount), at})
			}
		}
	case "users":
		_ = w.Write([]string{"ID", "用户名", "昵称", "角色", "手机", "邮箱", "状态", "积分", "注册时间"})
		rows, err := db.Pool.Query(context.Background(), `
			SELECT id, username, COALESCE(nickname, ''), role::text, COALESCE(phone, ''),
			       COALESCE(email, ''), status, points, to_char(created_at, 'YYYY-MM-DD HH24:MI')
			FROM users WHERE deleted_at IS NULL ORDER BY id LIMIT 50000`)
		if err != nil {
			InternalError(c, "导出用户失败")
			return
		}
		defer rows.Close()
		for rows.Next() {
			var id int64
			var username, nickname, role, phone, email, at string
			var status int16
			var points int
			if err := rows.Scan(&id, &username, &nickname, &role, &phone, &email, &status, &points, &at); err == nil {
				_ = w.Write([]string{strconv.FormatInt(id, 10), username, nickname, role, phone, email,
					strconv.Itoa(int(status)), strconv.Itoa(points), at})
			}
		}
	case "sitters":
		_ = w.Write([]string{"ID", "机构名", "类型", "状态", "日单价", "容量", "评分", "评价数", "地址"})
		rows, err := db.Pool.Query(context.Background(), `
			SELECT id, name, type::text, status::text, daily_price, capacity, avg_score, review_count,
			       COALESCE(address, '')
			FROM sitters WHERE deleted_at IS NULL ORDER BY id`)
		if err != nil {
			InternalError(c, "导出机构失败")
			return
		}
		defer rows.Close()
		for rows.Next() {
			var id int64
			var name, stype, status, address string
			var price float64
			var capacity, reviews int64
			var score float64
			if err := rows.Scan(&id, &name, &stype, &status, &price, &capacity, &score, &reviews, &address); err == nil {
				_ = w.Write([]string{strconv.FormatInt(id, 10), name, stype, status,
					fmt.Sprintf("%.2f", price), strconv.FormatInt(capacity, 10),
					fmt.Sprintf("%.2f", score), strconv.FormatInt(reviews, 10), address})
			}
		}
	default:
		BadRequest(c, "不支持的导出类型")
	}
	writeAudit(c, "EXPORT_CSV", "export", 0, fmt.Sprintf(`{"kind": %q}`, kind))
}

// ---------------- 退款申请审批 ----------------

func AdminListRefundRequests(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	where := "WHERE ($1 = '' OR r.status = $1)"
	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM refund_requests r "+where, status).Scan(&total)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT r.id, r.order_id, o.order_no, u.username, s.name, r.amount, r.reason, r.status,
		       COALESCE(r.admin_note, ''), to_char(r.created_at, 'YYYY-MM-DD HH24:MI'),
		       COALESCE(to_char(r.handled_at, 'YYYY-MM-DD HH24:MI'), '')
		FROM refund_requests r
		JOIN orders o ON o.id = r.order_id
		JOIN users u ON u.id = r.owner_id
		JOIN sitters s ON s.id = o.sitter_id `+where+`
		ORDER BY r.id DESC LIMIT $2 OFFSET $3`, status, perPage, offset)
	if err != nil {
		InternalError(c, "查询退款申请失败: "+err.Error())
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orderID int64
		var orderNo, owner, sitter, reason, status, note, at, handled string
		var amount float64
		if err := rows.Scan(&id, &orderID, &orderNo, &owner, &sitter, &amount, &reason, &status,
			&note, &at, &handled); err != nil {
			continue
		}
		items = append(items, gin.H{"id": id, "order_id": orderID, "order_no": orderNo,
			"owner": owner, "sitter": sitter, "amount": amount, "reason": reason,
			"status": status, "admin_note": note, "created_at": at, "handled_at": handled})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

func AdminHandleRefundRequest(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Action string `json:"action" binding:"required,oneof=approve reject"`
		Note   string `json:"note" binding:"max=500"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}

	var (
		orderID, ownerID int64
	)
	var amount float64
	var reason, status string
	err := db.Pool.QueryRow(context.Background(),
		"SELECT order_id, owner_id, amount, reason, status FROM refund_requests WHERE id = $1", id,
	).Scan(&orderID, &ownerID, &amount, &reason, &status)
	if err != nil {
		NotFound(c, "退款申请不存在")
		return
	}
	if status != "pending" {
		Conflict(c, "该申请已处理")
		return
	}

	adminID := c.GetInt64("user_id")
	if in.Action == "reject" {
		_, err = db.Pool.Exec(context.Background(), `
			UPDATE refund_requests SET status = 'rejected', admin_id = $1, admin_note = $2,
			       handled_at = now() WHERE id = $3`, adminID, in.Note, id)
		if err != nil {
			InternalError(c, "更新退款申请失败")
			return
		}
		_, _ = db.Pool.Exec(context.Background(),
			"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
			ownerID, "退款申请被驳回", "您的退款申请未通过："+in.Note,
			fmt.Sprintf(`{"refund_request_id": %d}`, id))
		writeAudit(c, "REJECT_REFUND_REQUEST", "refund_request", id, "{}")
		OK(c, gin.H{"id": id, "status": "rejected"})
		return
	}

	refundReason := reason
	if in.Note != "" {
		refundReason = reason + "；审批备注：" + in.Note
	}
	res, err := performRefund(c.Request.Context(), orderID, &amount, refundReason, adminID)
	if err != nil {
		failOp(c, err)
		return
	}
	_, err = db.Pool.Exec(context.Background(), `
		UPDATE refund_requests SET status = 'approved', admin_id = $1, admin_note = $2,
		       handled_at = now() WHERE id = $3`, adminID, in.Note, id)
	if err != nil {
		InternalError(c, "退款已完成，但申请状态更新失败")
		return
	}
	writeAudit(c, "APPROVE_REFUND_REQUEST", "refund_request", id,
		fmt.Sprintf(`{"order_id": %d, "amount": %.2f}`, orderID, res.RefundAmount))
	OK(c, gin.H{"id": id, "status": "approved", "refund": res})
}

// ---------------- 售后工单 ----------------

func AdminListTickets(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	where := "WHERE ($1 = '' OR t.status = $1)"
	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM support_tickets t "+where, status).Scan(&total)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT t.id, t.ticket_no, u.username, t.category, t.title, t.status, t.priority,
		       to_char(t.created_at, 'YYYY-MM-DD HH24:MI'),
		       (SELECT COUNT(*) FROM ticket_messages m WHERE m.ticket_id = t.id)
		FROM support_tickets t JOIN users u ON u.id = t.user_id `+where+`
		ORDER BY t.status <> 'closed', t.priority = 'high' DESC, t.id DESC LIMIT $2 OFFSET $3`,
		status, perPage, offset)
	if err != nil {
		InternalError(c, "查询工单失败: "+err.Error())
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, msgs int64
		var no, user, category, title, st, priority, at string
		if err := rows.Scan(&id, &no, &user, &category, &title, &st, &priority, &at, &msgs); err != nil {
			continue
		}
		items = append(items, gin.H{"id": id, "ticket_no": no, "user": user, "category": category,
			"title": title, "status": st, "priority": priority, "created_at": at, "message_count": msgs})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

func AdminGetTicket(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var no, user, category, title, content, status, priority, at string
	err := db.Pool.QueryRow(context.Background(), `
		SELECT t.ticket_no, u.username, t.category, t.title, t.content, t.status, t.priority,
		       to_char(t.created_at, 'YYYY-MM-DD HH24:MI')
		FROM support_tickets t JOIN users u ON u.id = t.user_id WHERE t.id = $1`, id,
	).Scan(&no, &user, &category, &title, &content, &status, &priority, &at)
	if err != nil {
		NotFound(c, "工单不存在")
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT m.id, m.sender_role, COALESCE(u.username, '系统'), m.content,
		       to_char(m.created_at, 'YYYY-MM-DD HH24:MI')
		FROM ticket_messages m LEFT JOIN users u ON u.id = m.sender_id
		WHERE m.ticket_id = $1 ORDER BY m.id`, id)
	if err != nil {
		InternalError(c, "查询工单消息失败")
		return
	}
	defer rows.Close()
	msgs := make([]gin.H, 0)
	for rows.Next() {
		var mid int64
		var role, sender, body, mat string
		if err := rows.Scan(&mid, &role, &sender, &body, &mat); err == nil {
			msgs = append(msgs, gin.H{"id": mid, "sender_role": role, "sender": sender,
				"content": body, "created_at": mat})
		}
	}
	OK(c, gin.H{"id": id, "ticket_no": no, "user": user, "category": category, "title": title,
		"content": content, "status": status, "priority": priority, "created_at": at, "messages": msgs})
}

func AdminReplyTicket(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Content string `json:"content" binding:"required,min=1,max=2000"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写回复内容")
		return
	}
	var userID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT user_id FROM support_tickets WHERE id = $1", id).Scan(&userID); err != nil {
		NotFound(c, "工单不存在")
		return
	}
	adminID := c.GetInt64("user_id")
	_, err := db.Pool.Exec(context.Background(), `
		INSERT INTO ticket_messages (ticket_id, sender_id, sender_role, content)
		VALUES ($1, $2, 'admin', $3)`, id, adminID, in.Content)
	if err != nil {
		InternalError(c, "回复失败")
		return
	}
	_, _ = db.Pool.Exec(context.Background(), `
		UPDATE support_tickets SET status = CASE WHEN status = 'open' THEN 'processing' ELSE status END,
		       assigned_admin_id = $1, updated_at = now() WHERE id = $2`, adminID, id)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		userID, "客服回复了您的工单", in.Content, fmt.Sprintf(`{"ticket_id": %d}`, id))
	writeAudit(c, "REPLY_TICKET", "ticket", id, "{}")
	Created(c, "已回复", gin.H{"id": id})
}

func AdminUpdateTicket(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Status   string `json:"status" binding:"omitempty,oneof=open processing closed"`
		Priority string `json:"priority" binding:"omitempty,oneof=low normal high"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	_, err := db.Pool.Exec(context.Background(), `
		UPDATE support_tickets SET
		  status = COALESCE(NULLIF($1, ''), status),
		  priority = COALESCE(NULLIF($2, ''), priority),
		  updated_at = now()
		WHERE id = $3`, in.Status, in.Priority, id)
	if err != nil {
		InternalError(c, "更新工单失败")
		return
	}
	writeAudit(c, "UPDATE_TICKET", "ticket", id, fmt.Sprintf(`{"status": %q, "priority": %q}`, in.Status, in.Priority))
	OK(c, gin.H{"id": id})
}

// ---------------- 机构结算 ----------------

func AdminListSettlements(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	where := "WHERE ($1 = '' OR st.status = $1)"
	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM sitter_settlements st "+where, status).Scan(&total)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT st.id, s.name, st.period_start::text, st.period_end::text, st.order_count,
		       st.gross_amount, st.commission_amount, st.net_amount, st.status,
		       to_char(st.applied_at, 'YYYY-MM-DD HH24:MI'),
		       COALESCE(to_char(st.paid_at, 'YYYY-MM-DD HH24:MI'), ''), COALESCE(st.note, '')
		FROM sitter_settlements st JOIN sitters s ON s.id = st.sitter_id `+where+`
		ORDER BY st.status = 'pending' DESC, st.id DESC LIMIT $2 OFFSET $3`, status, perPage, offset)
	if err != nil {
		InternalError(c, "查询结算失败: "+err.Error())
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orders int64
		var sitter, start, end, st, applied, paid, note string
		var gross, commission, net float64
		if err := rows.Scan(&id, &sitter, &start, &end, &orders, &gross, &commission, &net, &st,
			&applied, &paid, &note); err != nil {
			continue
		}
		items = append(items, gin.H{"id": id, "sitter": sitter, "period_start": start, "period_end": end,
			"order_count": orders, "gross_amount": gross, "commission_amount": commission,
			"net_amount": net, "status": st, "applied_at": applied, "paid_at": paid, "note": note})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

func AdminPaySettlement(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Action string `json:"action" binding:"required,oneof=pay reject"`
		Note   string `json:"note" binding:"max=500"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	var sitterID int64
	var status string
	var net float64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT sitter_id, status, net_amount FROM sitter_settlements WHERE id = $1", id,
	).Scan(&sitterID, &status, &net); err != nil {
		NotFound(c, "结算单不存在")
		return
	}
	if status != "pending" {
		Conflict(c, "该结算单已处理")
		return
	}
	newStatus := "paid"
	if in.Action == "reject" {
		newStatus = "rejected"
	}
	_, err := db.Pool.Exec(context.Background(), `
		UPDATE sitter_settlements SET status = $1, admin_id = $2, note = $3,
		       paid_at = CASE WHEN $1 = 'paid' THEN now() ELSE NULL END
		WHERE id = $4`, newStatus, c.GetInt64("user_id"), in.Note, id)
	if err != nil {
		InternalError(c, "更新结算失败")
		return
	}
	var sitterUserID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT user_id FROM sitters WHERE id = $1", sitterID).Scan(&sitterUserID)
	title := "结算已打款"
	body := fmt.Sprintf("您的结算单 #%d 已完成，金额 %.2f 元", id, net)
	if newStatus == "rejected" {
		title = "结算被驳回"
		body = "您的结算单 #" + strconv.FormatInt(id, 10) + " 被驳回：" + in.Note
	}
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4)",
		sitterUserID, title, body, fmt.Sprintf(`{"settlement_id": %d}`, id))
	writeAudit(c, "SETTLE_SITTER", "settlement", id, fmt.Sprintf(`{"status": %q}`, newStatus))
	OK(c, gin.H{"id": id, "status": newStatus})
}

// ---------------- 宠物免疫核验 ----------------

func AdminVerifyPetVaccine(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Status string `json:"status" binding:"required,oneof=missing pending verified rejected"`
		Note   string `json:"note" binding:"max=255"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE pets SET vaccine_status = $1, vaccine_note = $2, vaccine_verified_by = $3,
		       vaccine_verified_at = now(), updated_at = now()
		WHERE id = $4 AND deleted_at IS NULL`, in.Status, nilIfEmpty(in.Note), c.GetInt64("user_id"), id)
	if err != nil {
		InternalError(c, "更新免疫核验失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "宠物不存在")
		return
	}
	writeAudit(c, "VERIFY_PET_VACCINE", "pet", id, fmt.Sprintf(`{"status": %q}`, in.Status))
	OK(c, gin.H{"id": id, "status": in.Status})
}
