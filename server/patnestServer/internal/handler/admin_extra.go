package handler

import (
	"context"
	"fmt"
	"log"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminListPets handles GET /api/v1/admin/pets
func AdminListPets(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 100 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	where := "WHERE p.deleted_at IS NULL"
	args := []interface{}{}
	idx := 1

	if species := c.Query("species"); species != "" {
		where += fmt.Sprintf(" AND p.species::text = $%d", idx)
		args = append(args, species)
		idx++
	}
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND (p.name ILIKE $%d OR p.breed ILIKE $%d)", idx, idx)
		args = append(args, "%"+q+"%")
		idx++
	}
	if ownerID := c.Query("owner_id"); ownerID != "" {
		where += fmt.Sprintf(" AND p.owner_id = $%d", idx)
		args = append(args, ownerID)
		idx++
	}
	if vs := c.Query("vaccine_status"); vs != "" {
		where += fmt.Sprintf(" AND p.vaccine_status = $%d", idx)
		args = append(args, vs)
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM pets p "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT p.id, p.owner_id, u.username, p.name, p.species::text, p.breed,
		p.body_size::text, p.age_month, p.weight_kg, p.vaccinated, p.sterilized,
		p.photo_urls, p.notes, p.vaccine_status, COALESCE(p.vaccine_urls, '{}'),
		COALESCE(p.vaccine_note, ''), to_char(p.created_at, 'YYYY-MM-DD HH24:MI')
		FROM pets p JOIN users u ON u.id = p.owner_id
		%s ORDER BY p.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询宠物列表失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AdminPet struct {
		ID            int64    `json:"id"`
		OwnerID       int64    `json:"owner_id"`
		OwnerName     string   `json:"owner_name"`
		Name          string   `json:"name"`
		Species       string   `json:"species"`
		Breed         *string  `json:"breed"`
		BodySize      *string  `json:"body_size"`
		AgeMonth      *int     `json:"age_month"`
		WeightKg      *float64 `json:"weight_kg"`
		Vaccinated    bool     `json:"vaccinated"`
		Sterilized    bool     `json:"sterilized"`
		PhotoURLs     []string `json:"photo_urls"`
		Notes         *string  `json:"notes"`
		VaccineStatus string   `json:"vaccine_status"`
		VaccineURLs   []string `json:"vaccine_urls"`
		VaccineNote   string   `json:"vaccine_note"`
		CreatedAt     string   `json:"created_at"`
	}

	pets := make([]AdminPet, 0)
	for rows.Next() {
		var p AdminPet
		if err := rows.Scan(&p.ID, &p.OwnerID, &p.OwnerName, &p.Name, &p.Species, &p.Breed,
			&p.BodySize, &p.AgeMonth, &p.WeightKg, &p.Vaccinated, &p.Sterilized,
			&p.PhotoURLs, &p.Notes, &p.VaccineStatus, &p.VaccineURLs, &p.VaccineNote,
			&p.CreatedAt); err != nil {
			log.Printf("[AdminListPets] scan error: %v", err)
			continue
		}
		pets = append(pets, p)
	}

	OK(c, gin.H{"items": pets, "total": total, "page": page, "per_page": perPage})
}

// AdminListDynamics handles GET /api/v1/admin/dynamics
func AdminListDynamics(c *gin.Context) {
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
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND d.content ILIKE $%d", idx)
		args = append(args, "%"+q+"%")
		idx++
	}
	if orderID := c.Query("order_id"); orderID != "" {
		where += fmt.Sprintf(" AND d.order_id = $%d", idx)
		args = append(args, orderID)
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM dynamics d "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT d.id, d.order_id, d.sitter_id, d.author_id, d.content, d.photos, d.tags,
		 to_char(d.created_at, 'YYYY-MM-DD HH24:MI'), s.name, u.username
		 FROM dynamics d
		 JOIN sitters s ON s.id = d.sitter_id
		 JOIN users u ON u.id = d.author_id
		 %s ORDER BY d.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询寄养动态失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AdminDynamic struct {
		ID         int64    `json:"id"`
		OrderID    int64    `json:"order_id"`
		SitterID   int64    `json:"sitter_id"`
		AuthorID   int64    `json:"author_id"`
		Content    string   `json:"content"`
		Photos     []string `json:"photos"`
		Tags       []string `json:"tags"`
		CreatedAt  string   `json:"created_at"`
		SitterName string   `json:"sitter_name"`
		AuthorName string   `json:"author_name"`
	}

	items := make([]AdminDynamic, 0)
	for rows.Next() {
		var d AdminDynamic
		if err := rows.Scan(&d.ID, &d.OrderID, &d.SitterID, &d.AuthorID, &d.Content,
			&d.Photos, &d.Tags, &d.CreatedAt, &d.SitterName, &d.AuthorName); err != nil {
			log.Printf("[AdminListDynamics] scan error: %v", err)
			continue
		}
		items = append(items, d)
	}

	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminListReviews handles GET /api/v1/admin/reviews
func AdminListReviews(c *gin.Context) {
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
	if sitterID := c.Query("sitter_id"); sitterID != "" {
		where += fmt.Sprintf(" AND r.sitter_id = $%d", idx)
		args = append(args, sitterID)
		idx++
	}
	if visible := c.Query("is_visible"); visible != "" {
		where += fmt.Sprintf(" AND r.is_visible = $%d", idx)
		args = append(args, visible == "true")
		idx++
	}
	if q := c.Query("q"); q != "" {
		where += fmt.Sprintf(" AND (r.owner_comment ILIKE $%d OR s.name ILIKE $%d)", idx, idx)
		args = append(args, "%"+q+"%")
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM reviews r JOIN sitters s ON s.id = r.sitter_id "+where, args...).Scan(&total)

	query := fmt.Sprintf(`SELECT r.id, r.order_id, r.sitter_id, r.owner_id,
		 r.overall_score, r.service_score, r.environment_score, r.hygiene_score,
		 r.owner_comment, r.owner_photos, r.reply_text, to_char(r.reply_at, 'YYYY-MM-DD HH24:MI'),
		 r.is_visible, to_char(r.created_at, 'YYYY-MM-DD HH24:MI'),
		 s.name, u.username
		 FROM reviews r
		 JOIN sitters s ON s.id = r.sitter_id
		 JOIN users u ON u.id = r.owner_id
		 %s ORDER BY r.created_at DESC LIMIT $%d OFFSET $%d`, where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "查询评价列表失败: "+err.Error())
		return
	}
	defer rows.Close()

	type AdminReview struct {
		ID               int64    `json:"id"`
		OrderID          int64    `json:"order_id"`
		SitterID         int64    `json:"sitter_id"`
		OwnerID          int64    `json:"owner_id"`
		OverallScore     int16    `json:"overall_score"`
		ServiceScore     int16    `json:"service_score"`
		EnvironmentScore int16    `json:"environment_score"`
		HygieneScore     int16    `json:"hygiene_score"`
		OwnerComment     *string  `json:"owner_comment"`
		OwnerPhotos      []string `json:"owner_photos"`
		ReplyText        *string  `json:"reply_text"`
		ReplyAt          *string  `json:"reply_at"`
		IsVisible        bool     `json:"is_visible"`
		CreatedAt        string   `json:"created_at"`
		SitterName       string   `json:"sitter_name"`
		OwnerName        string   `json:"owner_name"`
	}

	items := make([]AdminReview, 0)
	for rows.Next() {
		var r AdminReview
		if err := rows.Scan(&r.ID, &r.OrderID, &r.SitterID, &r.OwnerID,
			&r.OverallScore, &r.ServiceScore, &r.EnvironmentScore, &r.HygieneScore,
			&r.OwnerComment, &r.OwnerPhotos, &r.ReplyText, &r.ReplyAt,
			&r.IsVisible, &r.CreatedAt, &r.SitterName, &r.OwnerName); err != nil {
			log.Printf("[AdminListReviews] scan error: %v", err)
			continue
		}
		items = append(items, r)
	}

	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}
