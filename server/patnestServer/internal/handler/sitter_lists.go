package handler

import (
	"context"
	"strconv"
	"time"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// SitterReviewsPublic 游客/主人可查看某机构的可见评价。
func SitterReviewsPublic(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || sitterID == 0 {
		BadRequest(c, "无效的机构ID")
		return
	}

	// 封禁/下架机构对主人不可见
	var isActive bool
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL)",
		sitterID).Scan(&isActive); err != nil || !isActive {
		NotFound(c, "机构不存在或已下架")
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	var total int
	var avgOverall float64
	var avgService, avgEnvironment, avgHygiene float64
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*), COALESCE(AVG(overall_score), 0), COALESCE(AVG(service_score), 0),
		       COALESCE(AVG(environment_score), 0), COALESCE(AVG(hygiene_score), 0)
		FROM reviews WHERE sitter_id = $1 AND is_visible`, sitterID,
	).Scan(&total, &avgOverall, &avgService, &avgEnvironment, &avgHygiene)

	rows, err := db.Pool.Query(context.Background(), `
		SELECT r.id, r.order_id, r.sitter_id, r.overall_score, r.service_score,
		       r.environment_score, r.hygiene_score,
		       COALESCE(r.owner_comment, ''), COALESCE(r.owner_photos, ARRAY[]::text[]),
		       COALESCE(r.reply_text, ''), r.created_at,
		       COALESCE(NULLIF(u.nickname, ''), u.username)
		FROM reviews r JOIN users u ON u.id = r.owner_id
		WHERE r.sitter_id = $1 AND r.is_visible
		ORDER BY r.created_at DESC LIMIT $2 OFFSET $3`,
		sitterID, perPage, offset)
	if err != nil {
		InternalError(c, "查询评价失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orderID, sid int64
		var overall, service, environment, hygiene int
		var comment, reply, ownerName string
		var photos []string
		var createdAt time.Time
		if err := rows.Scan(&id, &orderID, &sid, &overall, &service, &environment, &hygiene,
			&comment, &photos, &reply, &createdAt, &ownerName); err == nil {
			items = append(items, gin.H{
				"id": id, "order_id": orderID, "sitter_id": sid,
				"overall_score": overall, "service_score": service,
				"environment_score": environment, "hygiene_score": hygiene,
				"owner_comment": comment, "owner_photos": photos,
				"owner_name": ownerName, "reply_text": reply, "created_at": createdAt,
			})
		}
	}
	OK(c, gin.H{
		"items": items, "total": total, "page": page, "per_page": perPage,
		"avg_overall": avgOverall, "avg_service": avgService,
		"avg_environment": avgEnvironment, "avg_hygiene": avgHygiene,
	})
}

// ListSitterReviews 返回本机构的评价列表（含回复状态）。
func ListSitterReviews(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, order_id, sitter_id, owner_id, overall_score, service_score, environment_score,
		       hygiene_score, COALESCE(owner_comment, ''), COALESCE(owner_photos, ARRAY[]::text[]),
		       COALESCE(reply_text, ''), reply_at, is_visible, created_at, updated_at
		FROM reviews WHERE sitter_id = $1 ORDER BY created_at DESC LIMIT 50`, sitterID)
	if err != nil {
		InternalError(c, "查询评价失败")
		return
	}
	defer rows.Close()

	items := make([]model.Review, 0)
	for rows.Next() {
		var r model.Review
		var comment, reply string
		if err := rows.Scan(&r.ID, &r.OrderID, &r.SitterID, &r.OwnerID, &r.OverallScore,
			&r.ServiceScore, &r.EnvironmentScore, &r.HygieneScore, &comment, &r.OwnerPhotos,
			&reply, &r.ReplyAt, &r.IsVisible, &r.CreatedAt, &r.UpdatedAt); err == nil {
			r.OwnerComment = &comment
			r.ReplyText = &reply
			items = append(items, r)
		}
	}
	OK(c, gin.H{"items": items})
}

// ListSitterDynamics 返回本机构发布过的寄养动态。
func ListSitterDynamics(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, order_id, sitter_id, author_id, content,
		       COALESCE(photos, ARRAY[]::text[]), COALESCE(tags, ARRAY[]::text[]),
		       created_at, updated_at
		FROM dynamics WHERE sitter_id = $1 ORDER BY created_at DESC LIMIT 50`, sitterID)
	if err != nil {
		InternalError(c, "查询动态失败")
		return
	}
	defer rows.Close()

	items := make([]model.Dynamic, 0)
	for rows.Next() {
		var d model.Dynamic
		if err := rows.Scan(&d.ID, &d.OrderID, &d.SitterID, &d.AuthorID, &d.Content,
			&d.Photos, &d.Tags, &d.CreatedAt, &d.UpdatedAt); err == nil {
			items = append(items, d)
		}
	}
	OK(c, gin.H{"items": items})
}
