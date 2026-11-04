package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminCreateDynamic handles POST /api/v1/admin/dynamics
func AdminCreateDynamic(c *gin.Context) {
	var req struct {
		OrderID int64    `json:"order_id" binding:"required"`
		Content string   `json:"content" binding:"required,min=1,max=2000"`
		Photos  []string `json:"photos"`
		Tags    []string `json:"tags"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	var sitterID int64
	err := db.Pool.QueryRow(context.Background(),
		"SELECT sitter_id FROM orders WHERE id = $1 AND deleted_at IS NULL", req.OrderID).Scan(&sitterID)
	if err != nil {
		BadRequest(c, "订单不存在")
		return
	}
	var authorID int64
	err = db.Pool.QueryRow(context.Background(),
		"SELECT user_id FROM sitters WHERE id = $1", sitterID).Scan(&authorID)
	if err != nil {
		BadRequest(c, "该订单对应的机构不存在")
		return
	}

	photos := req.Photos
	if photos == nil {
		photos = []string{}
	}
	tags := req.Tags
	if tags == nil {
		tags = []string{}
	}

	var id int64
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO dynamics (order_id, sitter_id, author_id, content, photos, tags)
		 VALUES ($1, $2, $3, $4, $5, $6) RETURNING id`,
		req.OrderID, sitterID, authorID, req.Content, photos, tags).Scan(&id)
	if err != nil {
		InternalError(c, "发布动态失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_DYNAMIC', 'dynamic', $2, $3, NULLIF($4, '')::inet)",
		adminID, id, fmt.Sprintf(`{"order_id": %d}`, req.OrderID), auditClientIP(c))

	Created(c, "动态已发布", gin.H{"id": id})
}

// AdminUpdateDynamic handles PUT /api/v1/admin/dynamics/:id
func AdminUpdateDynamic(c *gin.Context) {
	dynamicID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的动态ID")
		return
	}

	var req struct {
		Content *string   `json:"content" binding:"omitempty,min=1,max=2000"`
		Photos  *[]string `json:"photos"`
		Tags    *[]string `json:"tags"`
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
	if req.Content != nil {
		add("content = $%d", *req.Content)
	}
	if req.Photos != nil {
		add("photos = $%d", *req.Photos)
	}
	if req.Tags != nil {
		add("tags = $%d", *req.Tags)
	}
	if len(sets) == 0 {
		BadRequest(c, "没有需要更新的字段")
		return
	}

	query := "UPDATE dynamics SET " + joinComma(sets) + " WHERE id = $" + strconv.Itoa(idx)
	args = append(args, dynamicID)
	tag, err := db.Pool.Exec(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "更新失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "动态不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_DYNAMIC', 'dynamic', $2, '{}', NULLIF($3, '')::inet)", adminID, dynamicID, auditClientIP(c))

	OK(c, gin.H{"id": dynamicID})
}

// AdminDeleteDynamic handles DELETE /api/v1/admin/dynamics/:id
func AdminDeleteDynamic(c *gin.Context) {
	dynamicID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的动态ID")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM dynamics WHERE id = $1", dynamicID)
	if err != nil {
		InternalError(c, "删除失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "动态不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_DYNAMIC', 'dynamic', $2, '{}', NULLIF($3, '')::inet)", adminID, dynamicID, auditClientIP(c))

	OK(c, gin.H{"id": dynamicID})
}
