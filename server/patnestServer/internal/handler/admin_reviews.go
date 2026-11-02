package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminCreateReview handles POST /api/v1/admin/reviews
func AdminCreateReview(c *gin.Context) {
	var req struct {
		OrderID          int64    `json:"order_id" binding:"required"`
		OverallScore     int16    `json:"overall_score" binding:"required,min=1,max=5"`
		ServiceScore     int16    `json:"service_score" binding:"required,min=1,max=5"`
		EnvironmentScore int16    `json:"environment_score" binding:"required,min=1,max=5"`
		HygieneScore     int16    `json:"hygiene_score" binding:"required,min=1,max=5"`
		OwnerComment     string   `json:"owner_comment" binding:"max=1000"`
		OwnerPhotos      []string `json:"owner_photos"`
		IsVisible        *bool    `json:"is_visible"`
		ReplyText        string   `json:"reply_text"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	var sitterID, ownerID int64
	err := db.Pool.QueryRow(context.Background(),
		"SELECT sitter_id, owner_id FROM orders WHERE id = $1 AND deleted_at IS NULL",
		req.OrderID).Scan(&sitterID, &ownerID)
	if err != nil {
		BadRequest(c, "订单不存在")
		return
	}
	// 入驻机构被强制下架后，评论展示没有意义，直接提示
	var sitterExists bool
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM sitters WHERE id = $1 AND deleted_at IS NULL)", sitterID).Scan(&sitterExists)
	if !sitterExists {
		BadRequest(c, "该订单对应的机构不存在")
		return
	}

	photos := req.OwnerPhotos
	if photos == nil {
		photos = []string{}
	}
	visible := true
	if req.IsVisible != nil {
		visible = *req.IsVisible
	}

	var id int64
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO reviews (order_id, sitter_id, owner_id, overall_score, service_score,
		 environment_score, hygiene_score, owner_comment, owner_photos, is_visible, reply_text, reply_at)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11,
		         CASE WHEN $11::text IS NULL THEN NULL ELSE now() END)
		 RETURNING id`,
		req.OrderID, sitterID, ownerID, req.OverallScore, req.ServiceScore,
		req.EnvironmentScore, req.HygieneScore, nilIfEmpty(req.OwnerComment), photos,
		visible, nilIfEmpty(req.ReplyText)).Scan(&id)
	if err != nil {
		if isUniqueViolation(err) {
			Conflict(c, "该订单已有评价，不能重复创建")
			return
		}
		InternalError(c, "创建评价失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_REVIEW', 'review', $2, $3)",
		adminID, id, fmt.Sprintf(`{"order_id": %d}`, req.OrderID))

	Created(c, "评价已创建", gin.H{"id": id})
}

// AdminUpdateReview handles PUT /api/v1/admin/reviews/:id
func AdminUpdateReview(c *gin.Context) {
	reviewID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的评价ID")
		return
	}

	var req struct {
		OverallScore     *int16    `json:"overall_score" binding:"omitempty,min=1,max=5"`
		ServiceScore     *int16    `json:"service_score" binding:"omitempty,min=1,max=5"`
		EnvironmentScore *int16    `json:"environment_score" binding:"omitempty,min=1,max=5"`
		HygieneScore     *int16    `json:"hygiene_score" binding:"omitempty,min=1,max=5"`
		OwnerComment     *string   `json:"owner_comment" binding:"omitempty,max=1000"`
		OwnerPhotos      *[]string `json:"owner_photos"`
		IsVisible        *bool     `json:"is_visible"`
		ReplyText        *string   `json:"reply_text" binding:"omitempty,max=1000"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	sets := []string{}
	args := []interface{}{}
	idx := 1
	add := func(expr string, v interface{}) {
		sets = append(sets, fmt.Sprintf(expr, idx))
		args = append(args, v)
		idx++
	}
	if req.OverallScore != nil {
		add("overall_score = $%d", *req.OverallScore)
	}
	if req.ServiceScore != nil {
		add("service_score = $%d", *req.ServiceScore)
	}
	if req.EnvironmentScore != nil {
		add("environment_score = $%d", *req.EnvironmentScore)
	}
	if req.HygieneScore != nil {
		add("hygiene_score = $%d", *req.HygieneScore)
	}
	if req.OwnerComment != nil {
		add("owner_comment = $%d", nilIfEmpty(*req.OwnerComment))
	}
	if req.OwnerPhotos != nil {
		add("owner_photos = $%d", *req.OwnerPhotos)
	}
	if req.IsVisible != nil {
		add("is_visible = $%d", *req.IsVisible)
	}
	if req.ReplyText != nil {
		add("reply_text = $%d", *req.ReplyText)
		sets = append(sets, fmt.Sprintf("reply_at = CASE WHEN $%d::text IS NULL THEN NULL ELSE now() END", idx))
		args = append(args, nilIfEmpty(*req.ReplyText))
		idx++
	}
	if len(sets) == 0 {
		BadRequest(c, "没有需要更新的字段")
		return
	}

	query := "UPDATE reviews SET " + joinComma(sets) + " WHERE id = $" + strconv.Itoa(idx)
	args = append(args, reviewID)
	tag, err := db.Pool.Exec(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "更新失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "评价不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_REVIEW', 'review', $2, '{}')", adminID, reviewID)

	OK(c, gin.H{"id": reviewID})
}

// AdminDeleteReview handles DELETE /api/v1/admin/reviews/:id
func AdminDeleteReview(c *gin.Context) {
	reviewID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的评价ID")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM reviews WHERE id = $1", reviewID)
	if err != nil {
		InternalError(c, "删除失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "评价不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_REVIEW', 'review', $2, '{}')", adminID, reviewID)

	OK(c, gin.H{"id": reviewID})
}
