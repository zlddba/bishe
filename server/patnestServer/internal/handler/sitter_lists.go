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

	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, overall_score, service_score, environment_score, hygiene_score,
		       COALESCE(owner_comment, ''), COALESCE(reply_text, ''), created_at
		FROM reviews WHERE sitter_id = $1 AND is_visible
		ORDER BY created_at DESC LIMIT 20`, sitterID)
	if err != nil {
		InternalError(c, "查询评价失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var overall, service, environment, hygiene int
		var comment, reply string
		var createdAt time.Time
		if err := rows.Scan(&id, &overall, &service, &environment, &hygiene,
			&comment, &reply, &createdAt); err == nil {
			items = append(items, gin.H{
				"id": id, "overall_score": overall, "service_score": service,
				"environment_score": environment, "hygiene_score": hygiene,
				"owner_comment": comment, "reply_text": reply, "created_at": createdAt,
			})
		}
	}
	OK(c, gin.H{"items": items})
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
