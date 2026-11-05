package handler

import (
	"context"
	"fmt"
	"strconv"
	"strings"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// ListSitters handles GET /api/v1/pet-owner/sitters
func ListSitters(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "10"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 10
	}
	offset := (page - 1) * perPage

	where := "WHERE s.status = 'active' AND s.deleted_at IS NULL"
	args := []interface{}{}
	idx := 1

	if t := c.Query("type"); t != "" {
		types := strings.Split(t, ",")
		placeholders := make([]string, len(types))
		for i, v := range types {
			placeholders[i] = fmt.Sprintf("$%d", idx)
			args = append(args, strings.TrimSpace(v))
			idx++
		}
		where += " AND s.type IN (" + strings.Join(placeholders, ",") + ")"
	}

	if min := c.Query("price_min"); min != "" {
		where += fmt.Sprintf(" AND s.daily_price >= $%d", idx)
		v, _ := strconv.ParseFloat(min, 64)
		args = append(args, v)
		idx++
	}
	if max := c.Query("price_max"); max != "" {
		where += fmt.Sprintf(" AND s.daily_price <= $%d", idx)
		v, _ := strconv.ParseFloat(max, 64)
		args = append(args, v)
		idx++
	}

	orderBy := "s.avg_score DESC, s.review_count DESC"
	switch c.Query("sort") {
	case "price_asc":
		orderBy = "s.daily_price ASC"
	case "price_desc":
		orderBy = "s.daily_price DESC"
	case "newest":
		orderBy = "s.created_at DESC"
	}

	var total int
	countQ := "SELECT COUNT(*) FROM sitters s " + where
	_ = db.Pool.QueryRow(context.Background(), countQ, args...).Scan(&total)

	// 公开列表带联系方式：小程序端「一键拨号」需要，机构没填时返回空串由前端降级成站内留言。
	// 只取 contact_phone / contact_name（users）与 contact_person（sitters），不碰 email 等其它个人信息。
	query := fmt.Sprintf(`SELECT s.id, s.user_id, s.name, s.type, s.description, s.daily_price, s.visit_price,
		s.price_mode, s.capacity, s.address, s.latitude, s.longitude, s.avg_score, s.review_count,
		s.cover_photo_url, s.detail_photos, s.status, s.created_at, s.updated_at,
		COALESCE(u.contact_phone, ''), COALESCE(u.contact_name, ''), s.contact_person
		FROM sitters s LEFT JOIN users u ON u.id = s.user_id
		%s ORDER BY %s LIMIT $%d OFFSET $%d`, where, orderBy, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "failed to query sitters")
		return
	}
	defer rows.Close()

	sitters := make([]model.Sitter, 0)
	for rows.Next() {
		var s model.Sitter
		if err := rows.Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
			&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
			&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt,
			&s.ContactPhone, &s.ContactName, &s.ContactPerson); err != nil {
			InternalError(c, "failed to scan sitter")
			return
		}
		sitters = append(sitters, s)
	}

	OK(c, gin.H{
		"items":    sitters,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// GetSitter handles GET /api/v1/pet-owner/sitters/:id
func GetSitter(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid sitter id")
		return
	}

	var s model.Sitter
	err = db.Pool.QueryRow(context.Background(),
		`SELECT s.id, s.user_id, s.name, s.type, s.description, s.daily_price, s.visit_price, s.price_mode, s.capacity,
		 s.address, s.latitude, s.longitude, s.avg_score, s.review_count, s.cover_photo_url, s.detail_photos, s.status, s.created_at, s.updated_at,
		 COALESCE(u.contact_phone, ''), COALESCE(u.contact_name, ''), s.contact_person
		 FROM sitters s LEFT JOIN users u ON u.id = s.user_id
		 WHERE s.id = $1 AND s.status = 'active' AND s.deleted_at IS NULL`,
		sitterID,
	).Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
		&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
		&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt,
		&s.ContactPhone, &s.ContactName, &s.ContactPerson)

	if err != nil {
		NotFound(c, "机构不存在或已下架")
		return
	}

	OK(c, s)
}

// SearchSitters handles GET /api/v1/pet-owner/sitters/search
func SearchSitters(c *gin.Context) {
	q := c.Query("q")
	if q == "" {
		BadRequest(c, "search query required")
		return
	}

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
		"SELECT COUNT(*) FROM sitters WHERE status = 'active' AND deleted_at IS NULL AND (name ILIKE $1 OR address ILIKE $1 OR description ILIKE $1)",
		"%"+q+"%").Scan(&total)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT s.id, s.user_id, s.name, s.type, s.description, s.daily_price, s.visit_price, s.price_mode, s.capacity,
		 s.address, s.latitude, s.longitude, s.avg_score, s.review_count, s.cover_photo_url, s.detail_photos, s.status, s.created_at, s.updated_at,
		 COALESCE(u.contact_phone, ''), COALESCE(u.contact_name, ''), s.contact_person
		 FROM sitters s LEFT JOIN users u ON u.id = s.user_id
		 WHERE s.status = 'active' AND s.deleted_at IS NULL
		   AND (s.name ILIKE $1 OR s.address ILIKE $1 OR s.description ILIKE $1)
		 ORDER BY similarity(s.name, $2) DESC, s.avg_score DESC
		 LIMIT $3 OFFSET $4`,
		"%"+q+"%", q, perPage, offset)
	if err != nil {
		InternalError(c, "search failed")
		return
	}
	defer rows.Close()

	sitters := make([]model.Sitter, 0)
	for rows.Next() {
		var s model.Sitter
		if err := rows.Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
			&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
			&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt,
			&s.ContactPhone, &s.ContactName, &s.ContactPerson); err != nil {
			continue
		}
		sitters = append(sitters, s)
	}

	OK(c, gin.H{
		"items":    sitters,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// GetMyShop handles GET /api/v1/sitter/shop/me
func GetMyShop(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var s model.Sitter
	err := db.Pool.QueryRow(context.Background(),
		`SELECT id, user_id, name, type, description, daily_price, visit_price, price_mode, capacity,
		 address, latitude, longitude, avg_score, review_count, cover_photo_url, detail_photos, status, created_at, updated_at
		 FROM sitters WHERE user_id = $1 AND deleted_at IS NULL`,
		userID,
	).Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
		&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
		&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt)

	if err != nil {
		NotFound(c, "no shop found for current sitter")
		return
	}

	OK(c, s)
}

// CreateMyShop handles POST /api/v1/sitter/shop
func CreateMyShop(c *gin.Context) {
	userID := c.GetInt64("user_id")

	// 一个账号只该有一条机构记录，否则 mySitterID 取到哪条全靠运气
	var existing int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COALESCE(MAX(id), 0) FROM sitters WHERE user_id = $1 AND deleted_at IS NULL", userID,
	).Scan(&existing); err == nil && existing > 0 {
		Conflict(c, "该账号已有机构资料，请直接编辑")
		return
	}

	var req model.CreateSitterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	photos := req.DetailPhotos
	if photos == nil {
		photos = []string{}
	}

	var s model.Sitter
	err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, cover_photo_url, detail_photos, status)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, 'pending')
		 RETURNING id, user_id, name, type, description, daily_price, visit_price, price_mode, capacity, address, latitude, longitude, avg_score, review_count, cover_photo_url, detail_photos, status, created_at, updated_at`,
		userID, req.Name, req.Type, nilIfEmpty(req.Description), req.DailyPrice, req.VisitPrice,
		req.PriceMode, req.Capacity, nilIfEmpty(req.Address), req.Latitude, req.Longitude,
		nilIfEmpty(req.CoverPhotoURL), photos,
	).Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
		&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
		&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt)

	if err != nil {
		InternalError(c, "failed to create shop: "+err.Error())
		return
	}

	Created(c, "shop created, pending admin approval", s)
}

// UpdateMyShop handles PUT /api/v1/sitter/shop/me
func UpdateMyShop(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var req model.UpdateSitterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
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
	if req.CoverPhotoURL != nil {
		updates += fmt.Sprintf("cover_photo_url = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.CoverPhotoURL))
		idx++
	}
	if req.DetailPhotos != nil {
		updates += fmt.Sprintf("detail_photos = $%d, ", idx)
		args = append(args, *req.DetailPhotos)
		idx++
	}

	if req.Capacity != nil {
		if sid, err := mySitterID(userID); err == nil {
			msg, cerr := capacityShrinkProblem(context.Background(), sid, *req.Capacity)
			if cerr != nil {
				InternalError(c, "校验容量失败: "+cerr.Error())
				return
			}
			if msg != "" {
				BadRequest(c, msg)
				return
			}
		}
	}
	if updates == "" {
		BadRequest(c, "no fields to update")
		return
	}

	changed := changedColumns(updates)
	updates = updates[:len(updates)-2]
	args = append(args, userID)

	var s model.Sitter
	query := fmt.Sprintf(`UPDATE sitters SET %s WHERE user_id = $%d AND deleted_at IS NULL
		RETURNING id, user_id, name, type, description, daily_price, visit_price, price_mode, capacity,
		address, latitude, longitude, avg_score, review_count, cover_photo_url, detail_photos, status, created_at, updated_at`,
		updates, idx)

	err := db.Pool.QueryRow(context.Background(), query, args...).Scan(
		&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
		&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
		&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt)

	if err != nil {
		NotFound(c, "shop not found")
		return
	}

	writeAudit(c, "SITTER_UPDATE_SHOP", "sitter", s.ID,
		fmt.Sprintf(`{"fields":[%s]}`, strings.Join(quotedNames(changed), ",")))

	OK(c, s)
}

// changedColumns 从拼好的 SET 子句里取出本次真正改动的列名。
// 审计关心的是"动了哪几栏"，不是新值本身：这些字段在业务表里查得到，
// 日志里再存一份快照反而容易和后来的修改对不上。
func changedColumns(setClause string) []string {
	parts := strings.Split(setClause, ", ")
	names := make([]string, 0, len(parts))
	for _, p := range parts {
		if i := strings.Index(p, " = $"); i > 0 {
			names = append(names, p[:i])
		}
	}
	return names
}

func quotedNames(names []string) []string {
	out := make([]string, len(names))
	for i, n := range names {
		out[i] = fmt.Sprintf("%q", n)
	}
	return out
}
