package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminCreateSitter handles POST /api/v1/admin/sitters
func AdminCreateSitter(c *gin.Context) {
	var req struct {
		UserID       int64    `json:"user_id" binding:"required"`
		Name         string   `json:"name" binding:"required,min=1,max=128"`
		Type         string   `json:"type" binding:"required,oneof=home shop visit other"`
		Description  string   `json:"description"`
		DailyPrice   float64  `json:"daily_price" binding:"min=0"`
		VisitPrice   float64  `json:"visit_price" binding:"min=0"`
		PriceMode    string   `json:"price_mode" binding:"required,oneof=per_day per_visit"`
		Capacity     int      `json:"capacity" binding:"required,min=1"`
		Address      string   `json:"address"`
		Latitude     *float64 `json:"latitude"`
		Longitude    *float64 `json:"longitude"`
		CoverPhoto   string   `json:"cover_photo_url"`
		DetailPhotos []string `json:"detail_photos"`
		Status       string   `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	status := req.Status
	if status == "" {
		status = "pending"
	}

	photos := req.DetailPhotos
	if photos == nil {
		photos = []string{}
	}

	var id int64
	err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode,
		 capacity, address, latitude, longitude, cover_photo_url, detail_photos, status)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
		 RETURNING id`,
		req.UserID, req.Name, req.Type, nilIfEmpty(req.Description), req.DailyPrice, req.VisitPrice,
		req.PriceMode, req.Capacity, nilIfEmpty(req.Address), req.Latitude, req.Longitude,
		nilIfEmpty(req.CoverPhoto), photos, status,
	).Scan(&id)
	if err != nil {
		InternalError(c, "创建机构失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_SITTER', 'sitter', $2, $3)",
		adminID, id, fmt.Sprintf(`{"name": "%s"}`, req.Name))

	Created(c, "机构创建成功", gin.H{"id": id, "name": req.Name})
}

// AdminUpdateSitter handles PUT /api/v1/admin/sitters/:id
func AdminUpdateSitter(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的机构ID")
		return
	}

	var req struct {
		Name         *string   `json:"name"`
		Type         *string   `json:"type" binding:"omitempty,oneof=home shop visit other"`
		Description  *string   `json:"description"`
		DailyPrice   *float64  `json:"daily_price" binding:"omitempty,min=0"`
		VisitPrice   *float64  `json:"visit_price" binding:"omitempty,min=0"`
		PriceMode    *string   `json:"price_mode" binding:"omitempty,oneof=per_day per_visit"`
		Capacity     *int      `json:"capacity" binding:"omitempty,min=1"`
		Address      *string   `json:"address"`
		Latitude     *float64  `json:"latitude"`
		Longitude    *float64  `json:"longitude"`
		CoverPhoto   *string   `json:"cover_photo_url"`
		DetailPhotos *[]string `json:"detail_photos"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	updates := ""
	args := []interface{}{}
	idx := 1

	if req.Name != nil {
		updates += fmt.Sprintf("name = $%d, ", idx)
		args = append(args, *req.Name)
		idx++
	}
	if req.Type != nil {
		updates += fmt.Sprintf("type = $%d, ", idx)
		args = append(args, *req.Type)
		idx++
	}
	if req.Description != nil {
		updates += fmt.Sprintf("description = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.Description))
		idx++
	}
	if req.DailyPrice != nil {
		updates += fmt.Sprintf("daily_price = $%d, ", idx)
		args = append(args, *req.DailyPrice)
		idx++
	}
	if req.VisitPrice != nil {
		updates += fmt.Sprintf("visit_price = $%d, ", idx)
		args = append(args, *req.VisitPrice)
		idx++
	}
	if req.PriceMode != nil {
		updates += fmt.Sprintf("price_mode = $%d, ", idx)
		args = append(args, *req.PriceMode)
		idx++
	}
	if req.Capacity != nil {
		updates += fmt.Sprintf("capacity = $%d, ", idx)
		args = append(args, *req.Capacity)
		idx++
	}
	if req.Address != nil {
		updates += fmt.Sprintf("address = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.Address))
		idx++
	}
	if req.Latitude != nil {
		updates += fmt.Sprintf("latitude = $%d, ", idx)
		args = append(args, *req.Latitude)
		idx++
	}
	if req.Longitude != nil {
		updates += fmt.Sprintf("longitude = $%d, ", idx)
		args = append(args, *req.Longitude)
		idx++
	}
	if req.CoverPhoto != nil {
		updates += fmt.Sprintf("cover_photo_url = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.CoverPhoto))
		idx++
	}
	if req.DetailPhotos != nil {
		updates += fmt.Sprintf("detail_photos = $%d, ", idx)
		args = append(args, *req.DetailPhotos)
		idx++
	}

	if updates == "" {
		BadRequest(c, "没有需要更新的字段")
		return
	}

	updates = updates[:len(updates)-2]
	args = append(args, sitterID)

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE sitters SET "+updates+" WHERE id = $"+strconv.Itoa(idx)+" AND deleted_at IS NULL",
		args...)
	if err != nil {
		InternalError(c, "更新失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "机构不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_SITTER', 'sitter', $2, '{}')",
		adminID, sitterID)

	OK(c, gin.H{"id": sitterID})
}

// AdminDeleteSitter handles DELETE /api/v1/admin/sitters/:id (soft delete)
func AdminDeleteSitter(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的机构ID")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE sitters SET deleted_at = now(), status = 'inactive' WHERE id = $1 AND deleted_at IS NULL",
		sitterID)
	if err != nil {
		InternalError(c, "删除失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "机构不存在或已删除")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_SITTER', 'sitter', $2, '{}')",
		adminID, sitterID)

	OK(c, gin.H{"id": sitterID})
}

// AdminGetSitterDetail handles GET /api/v1/admin/sitters/:id
func AdminGetSitterDetail(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的机构ID")
		return
	}

	type SitterDetail struct {
		ID              int64    `json:"id"`
		UserID          int64    `json:"user_id"`
		Username        string   `json:"username"`
		Name            string   `json:"name"`
		Type            string   `json:"type"`
		Description     *string  `json:"description"`
		DailyPrice      float64  `json:"daily_price"`
		VisitPrice      float64  `json:"visit_price"`
		PriceMode       string   `json:"price_mode"`
		Capacity        int      `json:"capacity"`
		Address         *string  `json:"address"`
		Latitude        *float64 `json:"latitude"`
		Longitude       *float64 `json:"longitude"`
		AvgScore        float64  `json:"avg_score"`
		ReviewCount     int      `json:"review_count"`
		CoverPhoto      *string  `json:"cover_photo_url"`
		DetailPhotos    []string `json:"detail_photos"`
		ContactPerson   *string  `json:"contact_person"`
		LicensePhotoURL *string  `json:"license_photo_url"`
		IDCardPhotoURL  *string  `json:"id_card_photo_url"`
		ReviewNote      *string  `json:"review_note"`
		Status          string   `json:"status"`
		BannedReason    *string  `json:"banned_reason"`
		CreatedAt       string   `json:"created_at"`
	}

	var s SitterDetail
	err = db.Pool.QueryRow(context.Background(),
		`SELECT s.id, s.user_id, u.username, s.name, s.type::text, s.description,
		 s.daily_price, s.visit_price, s.price_mode::text, s.capacity,
		 s.address, s.latitude, s.longitude, s.avg_score, s.review_count,
		 s.cover_photo_url, s.detail_photos,
		 s.contact_person, s.license_photo_url, s.id_card_photo_url, s.review_note,
		 s.status::text, s.banned_reason,
		 to_char(s.created_at, 'YYYY-MM-DD HH24:MI')
		 FROM sitters s JOIN users u ON u.id = s.user_id
		 WHERE s.id = $1 AND s.deleted_at IS NULL`,
		sitterID,
	).Scan(&s.ID, &s.UserID, &s.Username, &s.Name, &s.Type, &s.Description,
		&s.DailyPrice, &s.VisitPrice, &s.PriceMode, &s.Capacity,
		&s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
		&s.CoverPhoto, &s.DetailPhotos,
		&s.ContactPerson, &s.LicensePhotoURL, &s.IDCardPhotoURL, &s.ReviewNote,
		&s.Status, &s.BannedReason,
		&s.CreatedAt)

	if err != nil {
		NotFound(c, "机构不存在")
		return
	}

	OK(c, s)
}

var _ = log.Printf
