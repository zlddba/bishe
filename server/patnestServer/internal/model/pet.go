package model

import "time"

type Pet struct {
	ID         int64    `json:"id"`
	OwnerID    int64    `json:"owner_id"`
	Name       string   `json:"name"`
	Species    string   `json:"species"`
	Breed      *string  `json:"breed"`
	BodySize   *string  `json:"body_size"`
	AgeMonth   *int     `json:"age_month"`
	WeightKg   *float64 `json:"weight_kg"`
	Vaccinated bool     `json:"vaccinated"`
	Sterilized bool     `json:"sterilized"`
	PhotoURLs  []string `json:"photo_urls"`
	AvatarURL  *string  `json:"avatar_url"`
	Notes      *string  `json:"notes"`
	// 免疫证明核验状态：missing=未上传 pending=待核验 verified=已通过 rejected=被驳回
	VaccineURLs   []string   `json:"vaccine_urls"`
	VaccineStatus string     `json:"vaccine_status"`
	VaccineNote   *string    `json:"vaccine_note"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	DeletedAt     *time.Time `json:"-"`
}

type CreatePetRequest struct {
	Name       string   `json:"name" binding:"required,min=1,max=64"`
	Species    string   `json:"species" binding:"required,oneof=cat dog other"`
	Breed      string   `json:"breed"`
	BodySize   string   `json:"body_size" binding:"omitempty,oneof=tiny small medium large xlarge"`
	AgeMonth   *int     `json:"age_month" binding:"omitempty,min=0,max=360"`
	WeightKg   *float64 `json:"weight_kg"`
	Vaccinated *bool    `json:"vaccinated"`
	Sterilized *bool    `json:"sterilized"`
	PhotoURLs  []string `json:"photo_urls"`
	AvatarURL  string   `json:"avatar_url"`
	Notes      string   `json:"notes"`
}

type UpdatePetRequest struct {
	Name       *string   `json:"name" binding:"omitempty,min=1,max=64"`
	Species    *string   `json:"species" binding:"omitempty,oneof=cat dog other"`
	Breed      *string   `json:"breed"`
	BodySize   *string   `json:"body_size" binding:"omitempty,oneof=tiny small medium large xlarge"`
	AgeMonth   *int      `json:"age_month" binding:"omitempty,min=0,max=360"`
	WeightKg   *float64  `json:"weight_kg"`
	Vaccinated *bool     `json:"vaccinated"`
	Sterilized *bool     `json:"sterilized"`
	PhotoURLs  *[]string `json:"photo_urls"`
	AvatarURL  *string   `json:"avatar_url"`
	Notes      *string   `json:"notes"`
}
