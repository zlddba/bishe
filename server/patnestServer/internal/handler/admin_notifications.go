package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminListNotifications handles GET /api/v1/admin/notifications
func AdminListNotifications(c *gin.Context) {
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
	if userID := c.Query("user_id"); userID != "" {
		where += fmt.Sprintf(" AND n.user_id = $%d", idx)
		args = append(args, userID)
		idx++
	}
	if typ := c.Query("type"); typ != "" {
		where += fmt.Sprintf(" AND n.type::text = $%d", idx)
		args = append(args, typ)
		idx++
	}
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND (n.title ILIKE $%d OR n.body ILIKE $%d)", idx, idx)
		args = append(args, "%"+q+"%")
		idx++
	}

	var total int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM notifications n "+where, args...).Scan(&total); err != nil {
		InternalError(c, "统计通知失败: "+err.Error())
		return
	}

	query := fmt.Sprintf(`SELECT n.id, n.user_id, u.username, n.type::text, n.title, n.body,
		COALESCE(n.extra::text, ''), n.is_read, to_char(n.created_at, 'YYYY-MM-DD HH24:MI')
		FROM notifications n JOIN users u ON u.id = n.user_id
		%s ORDER BY n.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询通知失败: "+err.Error())
		return
	}
	defer rows.Close()

	type adminNotification struct {
		ID        int64  `json:"id"`
		UserID    int64  `json:"user_id"`
		Username  string `json:"username"`
		Type      string `json:"type"`
		Title     string `json:"title"`
		Body      string `json:"body"`
		Extra     string `json:"extra"`
		IsRead    bool   `json:"is_read"`
		CreatedAt string `json:"created_at"`
	}

	items := make([]adminNotification, 0)
	for rows.Next() {
		var n adminNotification
		if err := rows.Scan(&n.ID, &n.UserID, &n.Username, &n.Type, &n.Title,
			&n.Body, &n.Extra, &n.IsRead, &n.CreatedAt); err != nil {
			continue
		}
		items = append(items, n)
	}

	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminBroadcastNotification handles POST /api/v1/admin/notifications/broadcast
func AdminBroadcastNotification(c *gin.Context) {
	var req struct {
		Title  string `json:"title" binding:"required,min=1,max=128"`
		Body   string `json:"body" binding:"required,min=1,max=2000"`
		Target string `json:"target" binding:"omitempty,oneof=all owner sitter"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	target := req.Target
	if target == "" || target == "all" {
		tag, err := db.Pool.Exec(context.Background(),
			`INSERT INTO notifications (user_id, type, title, body)
			 SELECT id, 'system_announcement', $1, $2 FROM users
			 WHERE deleted_at IS NULL AND status = 1`, req.Title, req.Body)
		if err != nil {
			InternalError(c, "推送公告失败: "+err.Error())
			return
		}
		adminID := c.GetInt64("user_id")
		_, _ = db.Pool.Exec(context.Background(),
			"SELECT fn_audit($1, 'BROADCAST_NOTIFICATION', 'notification', NULL, $2)",
			adminID, fmt.Sprintf(`{"title": %q, "target": "all", "count": %d}`, req.Title, tag.RowsAffected()))
		Created(c, fmt.Sprintf("已推送给 %d 位用户", tag.RowsAffected()), gin.H{"sent": tag.RowsAffected()})
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		`INSERT INTO notifications (user_id, type, title, body)
		 SELECT id, 'system_announcement', $1, $2 FROM users
		 WHERE deleted_at IS NULL AND status = 1 AND role::text = $3`, req.Title, req.Body, target)
	if err != nil {
		InternalError(c, "推送公告失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'BROADCAST_NOTIFICATION', 'notification', NULL, $2)",
		adminID, fmt.Sprintf(`{"title": %q, "target": %q, "count": %d}`, req.Title, target, tag.RowsAffected()))

	Created(c, fmt.Sprintf("已推送给 %d 位用户", tag.RowsAffected()), gin.H{"sent": tag.RowsAffected()})
}

// AdminDeleteNotification handles DELETE /api/v1/admin/notifications/:id
func AdminDeleteNotification(c *gin.Context) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的通知ID")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM notifications WHERE id = $1", id)
	if err != nil {
		InternalError(c, "删除失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "通知不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_NOTIFICATION', 'notification', $2, '{}')", adminID, id)

	OK(c, gin.H{"id": id})
}
