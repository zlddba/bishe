package handler

import (
	"context"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// ListOwnerDynamics 返回主人所有订单下的寄养动态（跨订单聚合）。
func ListOwnerDynamics(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	rows, err := db.Pool.Query(context.Background(), `
		SELECT d.id, d.order_id, d.content, COALESCE(d.photos, ARRAY[]::text[]),
		       COALESCE(d.tags, ARRAY[]::text[]), d.created_at,
		       o.order_no, s.name
		FROM dynamics d
		JOIN orders o ON o.id = d.order_id
		JOIN sitters s ON s.id = d.sitter_id
		WHERE o.owner_id = $1 AND o.deleted_at IS NULL
		ORDER BY d.created_at DESC LIMIT 50`, ownerID)
	if err != nil {
		InternalError(c, "查询寄养动态失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orderID int64
		var content, orderNo, sitterName string
		var photos, tags []string
		var createdAt interface{}
		if err := rows.Scan(&id, &orderID, &content, &photos, &tags, &createdAt, &orderNo, &sitterName); err == nil {
			items = append(items, gin.H{
				"id": id, "order_id": orderID, "content": content,
				"photos": photos, "tags": tags, "created_at": createdAt,
				"order_no": orderNo, "sitter_name": sitterName,
			})
		}
	}
	OK(c, gin.H{"items": items})
}

// DeleteSitterDynamic 机构删除自己发布的寄养动态。
func DeleteSitterDynamic(c *gin.Context) {
	dynamicID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || dynamicID == 0 {
		BadRequest(c, "无效的动态ID")
		return
	}
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM dynamics WHERE id = $1 AND sitter_id = $2", dynamicID, sitterID)
	if err != nil {
		InternalError(c, "删除动态失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "动态不存在")
		return
	}
	OK(c, gin.H{"deleted": true})
}

// SitterStats 返回机构近 7 天订单趋势与今日概览。
func SitterStats(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	trend := make([]gin.H, 0)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT d::date::text, COUNT(o.id), COALESCE(SUM(o.total_amount), 0)
		FROM generate_series(current_date - interval '6 day', current_date, interval '1 day') d
		LEFT JOIN orders o ON o.sitter_id = $1 AND o.created_at::date = d::date AND o.deleted_at IS NULL
		GROUP BY d ORDER BY d`, sitterID)
	if err == nil {
		for rows.Next() {
			var date string
			var orders int
			var revenue float64
			if err := rows.Scan(&date, &orders, &revenue); err == nil {
				trend = append(trend, gin.H{"date": date, "orders": orders, "revenue": revenue})
			}
		}
		rows.Close()
	}

	var pending, active int
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FILTER (WHERE status = 'confirmed'),
		       COUNT(*) FILTER (WHERE status IN ('accepted','staying'))
		FROM orders WHERE sitter_id = $1 AND deleted_at IS NULL`, sitterID).
		Scan(&pending, &active)

	var todayRevenue float64
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COALESCE(SUM(total_amount), 0) FROM orders
		WHERE sitter_id = $1 AND status IN `+doneOrderStatuses+` AND deleted_at IS NULL
		  AND completed_at::date = current_date`, sitterID).Scan(&todayRevenue)

	OK(c, gin.H{
		"sitter_id":     sitterID,
		"trend":         trend,
		"pending":       pending,
		"active":        active,
		"today_revenue": todayRevenue,
	})
}

// ContactSitter 主人向机构发起联系请求（写入机构消息通知）。
func ContactSitter(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || sitterID == 0 {
		BadRequest(c, "无效的机构ID")
		return
	}
	var in struct {
		Message string `json:"message"`
	}
	_ = c.ShouldBindJSON(&in)
	if in.Message == "" {
		in.Message = "有主人希望与您沟通寄养事宜，请留意消息。"
	}

	var sitterUserID int64
	var sitterName string
	err = db.Pool.QueryRow(context.Background(),
		"SELECT user_id, name FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL",
		sitterID).Scan(&sitterUserID, &sitterName)
	if err != nil {
		NotFound(c, "机构不存在或已下架，暂时无法联系")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4, $5)",
		sitterUserID, "收到新的联系请求", in.Message,
		`{"type": "contact_request"}`, "business")

	OK(c, gin.H{"sitter_id": sitterID, "sent": true})
}
