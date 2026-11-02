package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminListUsers handles GET /api/v1/admin/users
func AdminListUsers(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	// deleted 参数：默认只看未删除；all=含已注销；only=只看已注销（回收站）
	deletedFilter := c.Query("deleted")
	where := "WHERE deleted_at IS NULL"
	switch deletedFilter {
	case "all":
		where = "WHERE TRUE"
	case "only":
		where = "WHERE deleted_at IS NOT NULL"
	}
	args := []interface{}{}
	idx := 1

	if role := c.Query("role"); role != "" {
		where += fmt.Sprintf(" AND role::text = $%d", idx)
		args = append(args, role)
		idx++
	}
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND (username ILIKE $%d OR nickname ILIKE $%d)", idx, idx)
		args = append(args, "%"+q+"%")
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM users "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT id, username, nickname, phone, email, role::text, status,
			to_char(created_at, 'YYYY-MM-DD HH24:MI'),
			to_char(deleted_at, 'YYYY-MM-DD HH24:MI')
		FROM users %s ORDER BY created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询用户列表失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AdminUser struct {
		ID        int64   `json:"id"`
		Username  string  `json:"username"`
		Nickname  *string `json:"nickname"`
		Phone     *string `json:"phone"`
		Email     *string `json:"email"`
		Role      string  `json:"role"`
		Status    int16   `json:"status"`
		CreatedAt string  `json:"created_at"`
		DeletedAt *string `json:"deleted_at"`
	}

	users := make([]AdminUser, 0)
	for rows.Next() {
		var u AdminUser
		if err := rows.Scan(&u.ID, &u.Username, &u.Nickname, &u.Phone, &u.Email, &u.Role, &u.Status, &u.CreatedAt, &u.DeletedAt); err != nil {
			log.Printf("[AdminListUsers] scan error: %v", err)
			continue
		}
		users = append(users, u)
	}

	OK(c, gin.H{"items": users, "total": total, "page": page, "per_page": perPage})
}

// AdminListSitters handles GET /api/v1/admin/sitters
func AdminListSitters(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	where := "WHERE s.deleted_at IS NULL"
	args := []interface{}{}
	idx := 1

	if status := c.Query("status"); status != "" {
		where += fmt.Sprintf(" AND s.status::text = $%d", idx)
		args = append(args, status)
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM sitters s "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT s.id, s.user_id, s.name, s.type::text, s.description, s.daily_price, s.visit_price,
		s.price_mode::text, s.capacity, s.address, s.avg_score, s.review_count, s.status::text, to_char(s.created_at, 'YYYY-MM-DD HH24:MI'),
		u.username
		FROM sitters s JOIN users u ON u.id = s.user_id
		%s ORDER BY s.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询机构列表失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AdminSitter struct {
		ID          int64   `json:"id"`
		UserID      int64   `json:"user_id"`
		Username    string  `json:"username"`
		Name        string  `json:"name"`
		Type        string  `json:"type"`
		Description *string `json:"description"`
		DailyPrice  float64 `json:"daily_price"`
		VisitPrice  float64 `json:"visit_price"`
		PriceMode   string  `json:"price_mode"`
		Capacity    int     `json:"capacity"`
		Address     *string `json:"address"`
		AvgScore    float64 `json:"avg_score"`
		ReviewCount int     `json:"review_count"`
		Status      string  `json:"status"`
		CreatedAt   string  `json:"created_at"`
	}

	sitters := make([]AdminSitter, 0)
	for rows.Next() {
		var s AdminSitter
		if err := rows.Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
			&s.PriceMode, &s.Capacity, &s.Address, &s.AvgScore, &s.ReviewCount, &s.Status, &s.CreatedAt, &s.Username); err != nil {
			log.Printf("[AdminListSitters] scan error: %v", err)
			continue
		}
		sitters = append(sitters, s)
	}

	OK(c, gin.H{"items": sitters, "total": total, "page": page, "per_page": perPage})
}

// AdminUpdateSitterStatus handles PUT /api/v1/admin/sitters/:id/status
func AdminUpdateSitterStatus(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "机构编号无效")
		return
	}
	adminID := c.GetInt64("user_id")

	var req struct {
		Status string `json:"status" binding:"required,oneof=active inactive banned"`
		Reason string `json:"reason"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}

	var rowsAffected int64
	if req.Status == "banned" {
		tag, execErr := db.Pool.Exec(context.Background(),
			"UPDATE sitters SET status = 'banned', banned_at = now(), banned_reason = $1 WHERE id = $2 AND deleted_at IS NULL",
			nilIfEmpty(req.Reason), sitterID)
		if execErr != nil {
			InternalError(c, "更新状态失败")
			return
		}
		rowsAffected = tag.RowsAffected()
	} else {
		tag, execErr := db.Pool.Exec(context.Background(),
			"UPDATE sitters SET status = $1::sitter_status, banned_at = NULL, banned_reason = NULL WHERE id = $2 AND deleted_at IS NULL",
			req.Status, sitterID)
		if execErr != nil {
			InternalError(c, "更新状态失败")
			return
		}
		rowsAffected = tag.RowsAffected()
	}

	if rowsAffected == 0 {
		NotFound(c, "机构不存在")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_SITTER_STATUS', 'sitter', $2, $3)",
		adminID, sitterID, fmt.Sprintf(`{"new_status": "%s", "reason": "%s"}`, req.Status, req.Reason))

	OK(c, gin.H{"id": sitterID, "status": req.Status})
}

// AdminAuditLogs handles GET /api/v1/admin/audit
func AdminAuditLogs(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	where := "WHERE 1=1"
	args := []interface{}{}
	idx := 1

	if action := c.Query("action"); action != "" {
		where += fmt.Sprintf(" AND action = $%d", idx)
		args = append(args, action)
		idx++
	}
	if actorID := c.Query("actor_id"); actorID != "" {
		where += fmt.Sprintf(" AND actor_id = $%d", idx)
		args = append(args, actorID)
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM audit_logs "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT id, actor_id, action, resource_type, resource_id, detail::text, ip_address::text, to_char(created_at, 'YYYY-MM-DD HH24:MI')
		FROM audit_logs %s ORDER BY created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询审计日志失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AuditEntry struct {
		ID           int64   `json:"id"`
		ActorID      *int64  `json:"actor_id"`
		Action       string  `json:"action"`
		ResourceType string  `json:"resource_type"`
		ResourceID   *int64  `json:"resource_id"`
		Detail       *string `json:"detail"`
		IPAddress    *string `json:"ip_address"`
		CreatedAt    string  `json:"created_at"`
	}

	logs := make([]AuditEntry, 0)
	for rows.Next() {
		var a AuditEntry
		if err := rows.Scan(&a.ID, &a.ActorID, &a.Action, &a.ResourceType, &a.ResourceID, &a.Detail, &a.IPAddress, &a.CreatedAt); err != nil {
			log.Printf("[AdminAuditLogs] scan error: %v", err)
			continue
		}
		logs = append(logs, a)
	}

	OK(c, gin.H{"items": logs, "total": total, "page": page, "per_page": perPage})
}
