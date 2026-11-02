package handler

import (
	"context"
	"strconv"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

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

	query := "SELECT id, owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes, created_at, updated_at FROM pets WHERE owner_id = $1 AND deleted_at IS NULL"
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
		var p model.Pet
		if err := rows.Scan(&p.ID, &p.OwnerID, &p.Name, &p.Species, &p.Breed, &p.BodySize,
			&p.AgeMonth, &p.WeightKg, &p.Vaccinated, &p.Sterilized,
			&p.PhotoURLs, &p.AvatarURL, &p.Notes, &p.CreatedAt, &p.UpdatedAt); err != nil {
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

	var pet model.Pet
	err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
		 RETURNING id, owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes, created_at, updated_at`,
		ownerID, req.Name, req.Species, nilIfEmpty(req.Breed), nilIfEmpty(req.BodySize),
		req.AgeMonth, req.WeightKg, vaccinated, sterilized,
		photoURLs, nilIfEmpty(req.AvatarURL), nilIfEmpty(req.Notes),
	).Scan(&pet.ID, &pet.OwnerID, &pet.Name, &pet.Species, &pet.Breed, &pet.BodySize,
		&pet.AgeMonth, &pet.WeightKg, &pet.Vaccinated, &pet.Sterilized,
		&pet.PhotoURLs, &pet.AvatarURL, &pet.Notes, &pet.CreatedAt, &pet.UpdatedAt)

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

	var pet model.Pet
	err = db.Pool.QueryRow(context.Background(),
		`SELECT id, owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes, created_at, updated_at
		 FROM pets WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL`,
		petID, ownerID,
	).Scan(&pet.ID, &pet.OwnerID, &pet.Name, &pet.Species, &pet.Breed, &pet.BodySize,
		&pet.AgeMonth, &pet.WeightKg, &pet.Vaccinated, &pet.Sterilized,
		&pet.PhotoURLs, &pet.AvatarURL, &pet.Notes, &pet.CreatedAt, &pet.UpdatedAt)

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

	var pet model.Pet
	query := "UPDATE pets SET " + updates + " WHERE id = $" + strconv.Itoa(idx) + " AND owner_id = $" + strconv.Itoa(idx+1) + " AND deleted_at IS NULL RETURNING id, owner_id, name, species, breed, body_size, age_month, weight_kg, vaccinated, sterilized, photo_urls, avatar_url, notes, created_at, updated_at"

	err = db.Pool.QueryRow(context.Background(), query, args...).Scan(
		&pet.ID, &pet.OwnerID, &pet.Name, &pet.Species, &pet.Breed, &pet.BodySize,
		&pet.AgeMonth, &pet.WeightKg, &pet.Vaccinated, &pet.Sterilized,
		&pet.PhotoURLs, &pet.AvatarURL, &pet.Notes, &pet.CreatedAt, &pet.UpdatedAt)

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
