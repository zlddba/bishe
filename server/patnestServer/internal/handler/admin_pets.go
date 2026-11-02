package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminCreatePet handles POST /api/v1/admin/pets
func AdminCreatePet(c *gin.Context) {
	var req struct {
		OwnerID    int64    `json:"owner_id" binding:"required"`
		Name       string   `json:"name" binding:"required,min=1,max=64"`
		Species    string   `json:"species" binding:"required,oneof=cat dog other"`
		Breed      string   `json:"breed"`
		BodySize   string   `json:"body_size" binding:"omitempty,oneof=tiny small medium large xlarge"`
		AgeMonth   *int     `json:"age_month" binding:"omitempty,min=0,max=360"`
		WeightKg   *float64 `json:"weight_kg" binding:"omitempty,min=0"`
		Vaccinated *bool    `json:"vaccinated"`
		Sterilized *bool    `json:"sterilized"`
		PhotoURLs  []string `json:"photo_urls"`
		AvatarURL  string   `json:"avatar_url"`
		Notes      string   `json:"notes"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	if !adminOwnerExists(req.OwnerID) {
		BadRequest(c, "主人不存在或该用户不是宠物主人")
		return
	}

	bodySize := req.BodySize
	if bodySize == "" {
		bodySize = "medium"
	}
	photos := req.PhotoURLs
	if photos == nil {
		photos = []string{}
	}
	vaccinated, sterilized := false, false
	if req.Vaccinated != nil {
		vaccinated = *req.Vaccinated
	}
	if req.Sterilized != nil {
		sterilized = *req.Sterilized
	}

	var id int64
	err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO pets (owner_id, name, species, breed, body_size, age_month, weight_kg,
		 vaccinated, sterilized, photo_urls, avatar_url, notes)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
		 RETURNING id`,
		req.OwnerID, req.Name, req.Species, nilIfEmpty(req.Breed), bodySize,
		req.AgeMonth, req.WeightKg, vaccinated, sterilized, photos,
		nilIfEmpty(req.AvatarURL), nilIfEmpty(req.Notes),
	).Scan(&id)
	if err != nil {
		InternalError(c, "创建宠物失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_PET', 'pet', $2, $3)",
		adminID, id, fmt.Sprintf(`{"name": "%s", "owner_id": %d}`, req.Name, req.OwnerID))

	Created(c, "宠物档案已创建", gin.H{"id": id})
}

// AdminUpdatePet handles PUT /api/v1/admin/pets/:id
func AdminUpdatePet(c *gin.Context) {
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的宠物ID")
		return
	}

	var req struct {
		OwnerID    *int64    `json:"owner_id"`
		Name       *string   `json:"name" binding:"omitempty,min=1,max=64"`
		Species    *string   `json:"species" binding:"omitempty,oneof=cat dog other"`
		Breed      *string   `json:"breed"`
		BodySize   *string   `json:"body_size" binding:"omitempty,oneof=tiny small medium large xlarge"`
		AgeMonth   *int      `json:"age_month" binding:"omitempty,min=0,max=360"`
		WeightKg   *float64  `json:"weight_kg" binding:"omitempty,min=0"`
		Vaccinated *bool     `json:"vaccinated"`
		Sterilized *bool     `json:"sterilized"`
		PhotoURLs  *[]string `json:"photo_urls"`
		AvatarURL  *string   `json:"avatar_url"`
		Notes      *string   `json:"notes"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}

	if req.OwnerID != nil && !adminOwnerExists(*req.OwnerID) {
		BadRequest(c, "主人不存在或该用户不是宠物主人")
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
	if req.OwnerID != nil {
		add("owner_id = $%d", *req.OwnerID)
	}
	if req.Name != nil {
		add("name = $%d", *req.Name)
	}
	if req.Species != nil {
		add("species = $%d", *req.Species)
	}
	if req.Breed != nil {
		add("breed = $%d", nilIfEmpty(*req.Breed))
	}
	if req.BodySize != nil {
		add("body_size = $%d", *req.BodySize)
	}
	if req.AgeMonth != nil {
		add("age_month = $%d", *req.AgeMonth)
	}
	if req.WeightKg != nil {
		add("weight_kg = $%d", *req.WeightKg)
	}
	if req.Vaccinated != nil {
		add("vaccinated = $%d", *req.Vaccinated)
	}
	if req.Sterilized != nil {
		add("sterilized = $%d", *req.Sterilized)
	}
	if req.PhotoURLs != nil {
		add("photo_urls = $%d", *req.PhotoURLs)
	}
	if req.AvatarURL != nil {
		add("avatar_url = $%d", nilIfEmpty(*req.AvatarURL))
	}
	if req.Notes != nil {
		add("notes = $%d", nilIfEmpty(*req.Notes))
	}

	if len(sets) == 0 {
		BadRequest(c, "没有需要更新的字段")
		return
	}

	query := "UPDATE pets SET " + joinComma(sets) + " WHERE id = $" + strconv.Itoa(idx) + " AND deleted_at IS NULL"
	args = append(args, petID)
	tag, err := db.Pool.Exec(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "更新失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "宠物不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_PET', 'pet', $2, '{}')", adminID, petID)

	OK(c, gin.H{"id": petID})
}

// AdminDeletePet handles DELETE /api/v1/admin/pets/:id (soft delete)
func AdminDeletePet(c *gin.Context) {
	petID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的宠物ID")
		return
	}

	var busy int
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM order_pet op JOIN orders o ON o.id = op.order_id
		 WHERE op.pet_id = $1 AND o.deleted_at IS NULL
		 AND o.status IN ('pending_pay','confirmed','accepted','staying')`, petID).Scan(&busy)
	if busy > 0 {
		Conflict(c, "该宠物存在进行中的订单，暂不能删除")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE pets SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL", petID)
	if err != nil {
		InternalError(c, "删除失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "宠物不存在或已删除")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_PET', 'pet', $2, '{}')", adminID, petID)

	OK(c, gin.H{"id": petID})
}

func adminOwnerExists(ownerID int64) bool {
	var exists bool
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM users WHERE id = $1 AND role = 'owner' AND deleted_at IS NULL)",
		ownerID).Scan(&exists)
	return exists
}

func joinComma(items []string) string {
	out := ""
	for i, s := range items {
		if i > 0 {
			out += ", "
		}
		out += s
	}
	return out
}
