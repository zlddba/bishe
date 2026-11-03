package handler

import (
	"context"
	"strconv"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// petColumns 主人端宠物查询统一列清单（含免疫核验状态回读，免疫证明核验闭环 P0）
const petColumns = `id, owner_id, name, species, breed, body_size, age_month, weight_kg,
	vaccinated, sterilized, photo_urls, avatar_url, notes,
	vaccine_urls, vaccine_status, vaccine_note, created_at, updated_at`

// scanPetRow 统一扫描宠物行，pgx.Row 与 pgx.Rows 均适用。
func scanPetRow(row interface{ Scan(dest ...any) error }) (model.Pet, error) {
	var p model.Pet
	err := row.Scan(&p.ID, &p.OwnerID, &p.Name, &p.Species, &p.Breed, &p.BodySize,
		&p.AgeMonth, &p.WeightKg, &p.Vaccinated, &p.Sterilized,
		&p.PhotoURLs, &p.AvatarURL, &p.Notes,
		&p.VaccineURLs, &p.VaccineStatus, &p.VaccineNote, &p.CreatedAt, &p.UpdatedAt)
	return p, err
}

// ListPets handles GET /api/v1/pet-owner/pets
func ListPets(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "10"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 10
	}
	offset := (page - 1) * perPage

	species := c.Query("species")
	name := c.Query("name")

	query := "SELECT " + petColumns + " FROM pets WHERE owner_id = $1 AND deleted_at IS NULL"
	countQuery := "SELECT COUNT(*) FROM pets WHERE owner_id = $1 AND deleted_at IS NULL"
	args := []interface{}{ownerID}
	argIdx := 2

	if species != "" {
		query += " AND species = $" + strconv.Itoa(argIdx)
		countQuery += " AND species = $" + strconv.Itoa(argIdx)
		args = append(args, species)
		argIdx++
	}
	if name != "" {
		query += " AND name ILIKE $" + strconv.Itoa(argIdx)
		countQuery += " AND name ILIKE $" + strconv.Itoa(argIdx)
		args = append(args, "%"+name+"%")
		argIdx++
	}

	var total int
	if err := db.Pool.QueryRow(context.Background(), countQuery, args...).Scan(&total); err != nil {
		InternalError(c, "failed to count pets")
		return
	}

	query += " ORDER BY created_at DESC LIMIT $" + strconv.Itoa(argIdx) + " OFFSET $" + strconv.Itoa(argIdx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "failed to query pets")
		return
	}
	defer rows.Close()

	pets := make([]model.Pet, 0)
	for rows.Next() {
		p, err := scanPetRow(rows)
		if err != nil {
			InternalError(c, "failed to scan pet")
			return
		}
		pets = append(pets, p)
	}

	OK(c, gin.H{
		"items":    pets,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// CreatePet handles POST /api/v1/pet-owner/pets
func CreatePet(c *gin.Context) {
	ownerID := c.GetInt64("user_id")

	var req model.CreatePetRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	vaccinated := false
	if req.Vaccinated != nil {
		vaccinated = *req.Vaccinated
	}
	sterilized := false
	if req.Sterilized != nil {
		sterilized = *req.Sterilized
	}
	photoURLs := req.PhotoURLs
	if photoURLs == nil {
		photoURLs = []string{}
	}

	pet, err := scanPetRow(db.Pool.QueryRow(context.Background(),
		`INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
		 RETURNING `+petColumns,
		ownerID, req.Name, req.Species, nilIfEmpty(req.Breed), nilIfEmpty(req.BodySize),
		req.AgeMonth, req.WeightKg, vaccinated, sterilized,
		photoURLs, nilIfEmpty(req.AvatarURL), nilIfEmpty(req.Notes),
	))

	if err != nil {
		InternalError(c, "failed to create pet: "+err.Error())
		return
	}

	Created(c, "pet created", pet)
}

// GetPet handles GET /api/v1/pet-owner/pets/:id
func GetPet(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid pet id")
		return
	}

	pet, err := scanPetRow(db.Pool.QueryRow(context.Background(),
		`SELECT `+petColumns+`
		 FROM pets WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL`,
		petID, ownerID,
	))

	if err != nil {
		NotFound(c, "pet not found")
		return
	}

	OK(c, pet)
}

// UpdatePet handles PUT /api/v1/pet-owner/pets/:id
func UpdatePet(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid pet id")
		return
	}

	var req model.UpdatePetRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	// Build dynamic update
	updates := ""
	args := []interface{}{}
	idx := 1

	if req.Name != nil {
		updates += "name = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.Name)
		idx++
	}
	if req.Species != nil {
		updates += "species = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.Species)
		idx++
	}
	if req.Breed != nil {
		updates += "breed = $" + strconv.Itoa(idx) + ", "
		args = append(args, nilIfEmpty(*req.Breed))
		idx++
	}
	if req.BodySize != nil {
		updates += "body_size = $" + strconv.Itoa(idx) + ", "
		args = append(args, nilIfEmpty(*req.BodySize))
		idx++
	}
	if req.AgeMonth != nil {
		updates += "age_month = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.AgeMonth)
		idx++
	}
	if req.WeightKg != nil {
		updates += "weight_kg = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.WeightKg)
		idx++
	}
	if req.Vaccinated != nil {
		updates += "vaccinated = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.Vaccinated)
		idx++
	}
	if req.Sterilized != nil {
		updates += "sterilized = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.Sterilized)
		idx++
	}
	if req.PhotoURLs != nil {
		updates += "photo_urls = $" + strconv.Itoa(idx) + ", "
		args = append(args, *req.PhotoURLs)
		idx++
	}
	if req.AvatarURL != nil {
		updates += "avatar_url = $" + strconv.Itoa(idx) + ", "
		args = append(args, nilIfEmpty(*req.AvatarURL))
		idx++
	}
	if req.Notes != nil {
		updates += "notes = $" + strconv.Itoa(idx) + ", "
		args = append(args, nilIfEmpty(*req.Notes))
		idx++
	}

	if updates == "" {
		BadRequest(c, "no fields to update")
		return
	}

	// Remove trailing comma
	updates = updates[:len(updates)-2]
	args = append(args, petID, ownerID)

	query := "UPDATE pets SET " + updates + " WHERE id = $" + strconv.Itoa(idx) + " AND owner_id = $" + strconv.Itoa(idx+1) + " AND deleted_at IS NULL RETURNING " + petColumns

	pet, err := scanPetRow(db.Pool.QueryRow(context.Background(), query, args...))

	if err != nil {
		NotFound(c, "pet not found or not owned by you")
		return
	}

	OK(c, pet)
}

// DeletePet handles DELETE /api/v1/pet-owner/pets/:id (soft delete)
func DeletePet(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid pet id")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE pets SET deleted_at = now() WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL",
		petID, ownerID)
	if err != nil {
		InternalError(c, "failed to delete pet")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "pet not found")
		return
	}

	OK(c, nil)
}

// GetPetStays handles GET /api/v1/pet-owner/pets/:id/stays
// 宠物维度的历史寄养记录：该宠物参与过的全部订单（按开始日期倒序）。
func GetPetStays(c *gin.Context) {
	ownerID := c.GetInt64("user_id")
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || petID == 0 {
		BadRequest(c, "invalid pet id")
		return
	}

	var owned bool
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM pets WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL)",
		petID, ownerID).Scan(&owned); err != nil || !owned {
		NotFound(c, "宠物不存在")
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT o.id, o.order_no, o.sitter_id, s.name, o.status::text,
		       to_char(o.start_date, 'YYYY-MM-DD'), to_char(o.end_date, 'YYYY-MM-DD'),
		       o.total_days, COALESCE(to_char(op.stay_date, 'YYYY-MM-DD'), ''), o.total_amount
		FROM orders o
		JOIN order_pet op ON op.order_id = o.id
		JOIN sitters s ON s.id = o.sitter_id
		WHERE op.pet_id = $1 AND o.owner_id = $2 AND o.deleted_at IS NULL AND o.status <> 'cancelled'
		ORDER BY o.start_date DESC, o.id DESC`, petID, ownerID)
	if err != nil {
		InternalError(c, "查询寄养记录失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var (
			orderID, sitterID   int64
			orderNo, sitterName string
			status              string
			start, end, stay    string
			totalDays           int
			totalAmount         float64
		)
		if err := rows.Scan(&orderID, &orderNo, &sitterID, &sitterName, &status,
			&start, &end, &totalDays, &stay, &totalAmount); err == nil {
			items = append(items, gin.H{
				"order_id": orderID, "order_no": orderNo,
				"sitter_id": sitterID, "sitter_name": sitterName,
				"status": status, "start_date": start, "end_date": end,
				"total_days": totalDays, "stay_date": stay, "total_amount": totalAmount,
			})
		}
	}

	OK(c, gin.H{"pet_id": petID, "total": len(items), "items": items})
}
