package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"strings"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
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

	// 地区筛选：机构只在 address 里存自由文本，没有结构化的省市区字段，
	// 所以按「包含」匹配。区县优先——传了区县就不必再比城市，
	// 否则「呼和浩特市」会把该市所有区县都算进来，和用户的意图不符。
	if district := strings.TrimSpace(c.Query("district")); district != "" {
		where += fmt.Sprintf(" AND s.address ILIKE $%d", idx)
		args = append(args, "%"+district+"%")
		idx++
	} else if city := strings.TrimSpace(c.Query("city")); city != "" {
		where += fmt.Sprintf(" AND s.address ILIKE $%d", idx)
		args = append(args, "%"+city+"%")
		idx++
	}

	// 计数只依赖 where 里的筛选参数，必须放在距离参数追加之前，
	// 否则 count 查询会多收到 latitude/longitude 两个参数。
	var total int
	countQ := "SELECT COUNT(*) FROM sitters s " + where
	_ = db.Pool.QueryRow(context.Background(), countQ, args...).Scan(&total)

	orderBy := "s.avg_score DESC, s.review_count DESC"
	switch sort := c.Query("sort"); sort {
	case "price_asc":
		orderBy = "s.daily_price ASC"
	case "price_desc":
		orderBy = "s.daily_price DESC"
	case "newest":
		orderBy = "s.created_at DESC"
	case "distance":
		latRaw := strings.TrimSpace(c.Query("latitude"))
		lngRaw := strings.TrimSpace(c.Query("longitude"))
		lat, errLat := strconv.ParseFloat(latRaw, 64)
		lng, errLng := strconv.ParseFloat(lngRaw, 64)
		if errLat != nil || errLng != nil || lat < -90 || lat > 90 || lng < -180 || lng > 180 {
			BadRequest(c, "sort=distance 需要有效的 latitude/longitude")
			return
		}
		latIdx := idx
		args = append(args, lat)
		idx++
		lngIdx := idx
		args = append(args, lng)
		idx++
		// Haversine 排序；没有坐标的机构排最后。
		// LEAST/GREATEST 防止浮点误差让 acos 超出 [-1,1] 报错。
		orderBy = fmt.Sprintf(`CASE
			WHEN s.latitude IS NULL OR s.longitude IS NULL THEN NULL
			ELSE (
				6371 * acos(LEAST(1.0, GREATEST(-1.0,
					cos(radians($%d)) * cos(radians(s.latitude)) *
					cos(radians(s.longitude) - radians($%d)) +
					sin(radians($%d)) * sin(radians(s.latitude))
				)))
			)
		END NULLS LAST, s.avg_score DESC`, latIdx, lngIdx, latIdx)
	}

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

	// 优先走 PGroonga 中文全文检索；扩展/索引不可用时退回 pg_trgm，
	// 老部署不会因为没装 pgroonga 直接 500。
	if searchSittersWithPgronoga(c, q, page, perPage, offset) {
		return
	}

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

	sitters, err := scanSitterRows(rows)
	if err != nil {
		InternalError(c, "search failed")
		return
	}

	OK(c, gin.H{
		"items":    sitters,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// searchSittersWithPgronoga 用 PGroonga 做中文全文检索。
// 返回 true 表示已经写出响应；false 表示调用方应退回 pg_trgm。
func searchSittersWithPgronoga(c *gin.Context, q string, page, perPage, offset int) bool {
	ctx := context.Background()
	var available bool
	if err := db.Pool.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pgroonga')`,
	).Scan(&available); err != nil || !available {
		return false
	}

	where := `s.status = 'active' AND s.deleted_at IS NULL
		AND ((s.name)::text &@~ pgroonga_query_escape($1::text)
			OR (s.address)::text &@~ pgroonga_query_escape($1::text)
			OR s.description &@~ pgroonga_query_escape($1::text))`

	var total int
	if err := db.Pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM sitters s WHERE `+where, q,
	).Scan(&total); err != nil {
		log.Printf("[SearchSitters] pgroonga count failed, fallback to pg_trgm: %v", err)
		return false
	}

	rows, err := db.Pool.Query(ctx,
		`SELECT s.id, s.user_id, s.name, s.type, s.description, s.daily_price, s.visit_price, s.price_mode, s.capacity,
		 s.address, s.latitude, s.longitude, s.avg_score, s.review_count, s.cover_photo_url, s.detail_photos, s.status, s.created_at, s.updated_at,
		 COALESCE(u.contact_phone, ''), COALESCE(u.contact_name, ''), s.contact_person
		 FROM sitters s LEFT JOIN users u ON u.id = s.user_id
		 WHERE `+where+`
		 ORDER BY s.avg_score DESC, s.review_count DESC
		 LIMIT $2 OFFSET $3`,
		q, perPage, offset)
	if err != nil {
		log.Printf("[SearchSitters] pgroonga query failed, fallback to pg_trgm: %v", err)
		return false
	}
	defer rows.Close()

	sitters, err := scanSitterRows(rows)
	if err != nil {
		log.Printf("[SearchSitters] pgroonga scan failed, fallback to pg_trgm: %v", err)
		return false
	}

	OK(c, gin.H{
		"items":    sitters,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
	return true
}

func scanSitterRows(rows pgx.Rows) ([]model.Sitter, error) {
	sitters := make([]model.Sitter, 0)
	for rows.Next() {
		var s model.Sitter
		if err := rows.Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
			&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
			&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt,
			&s.ContactPhone, &s.ContactName, &s.ContactPerson); err != nil {
			return nil, err
		}
		sitters = append(sitters, s)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return sitters, nil
}

// SimilarSitters handles GET /api/v1/sitters/:id/similar
// 机构特征向量由数据库触发器维护，这里只做余弦最近邻查询。
func SimilarSitters(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || sitterID == 0 {
		BadRequest(c, "invalid sitter id")
		return
	}

	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "6"))
	if limit < 1 {
		limit = 6
	}
	if limit > 20 {
		limit = 20
	}

	ctx := context.Background()
	var seedVector string
	if err := db.Pool.QueryRow(ctx,
		`SELECT COALESCE(embedding::text, '')
		 FROM sitters
		 WHERE id = $1 AND status = 'active' AND deleted_at IS NULL`, sitterID,
	).Scan(&seedVector); err != nil {
		NotFound(c, "机构不存在或已下架")
		return
	}
	if strings.TrimSpace(seedVector) == "" {
		OK(c, gin.H{"items": []gin.H{}})
		return
	}

	rows, err := db.Pool.Query(ctx,
		`SELECT s.id, s.user_id, s.name, s.type, s.description, s.daily_price, s.visit_price, s.price_mode, s.capacity,
		 s.address, s.latitude, s.longitude, s.avg_score, s.review_count, s.cover_photo_url, s.detail_photos, s.status, s.created_at, s.updated_at,
		 COALESCE(u.contact_phone, ''), COALESCE(u.contact_name, ''), s.contact_person,
		 1 - (s.embedding <=> $2::vector) AS similarity
		 FROM sitters s
		 LEFT JOIN users u ON u.id = s.user_id
		 WHERE s.id <> $1
		   AND s.status = 'active' AND s.deleted_at IS NULL
		   AND s.embedding IS NOT NULL
		 ORDER BY s.embedding <=> $2::vector
		 LIMIT $3`, sitterID, seedVector, limit)
	if err != nil {
		InternalError(c, "相似机构查询失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0, limit)
	for rows.Next() {
		var s model.Sitter
		var similarity float64
		if err := rows.Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
			&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore, &s.ReviewCount,
			&s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt,
			&s.ContactPhone, &s.ContactName, &s.ContactPerson, &similarity); err != nil {
			InternalError(c, "相似机构读取失败")
			return
		}
		items = append(items, gin.H{"sitter": s, "similarity": similarity})
	}
	if err := rows.Err(); err != nil {
		InternalError(c, "相似机构读取失败")
		return
	}

	OK(c, gin.H{"items": items})
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
