package model

import "time"

type Sitter struct {
	ID            int64    `json:"id"`
	UserID        int64    `json:"user_id"`
	Name          string   `json:"name"`
	Type          string   `json:"type"`
	Description   *string  `json:"description"`
	DailyPrice    float64  `json:"daily_price"`
	VisitPrice    float64  `json:"visit_price"`
	PriceMode     string   `json:"price_mode"`
	Capacity      int      `json:"capacity"`
	Address       *string  `json:"address"`
	Latitude      *float64 `json:"latitude"`
	Longitude     *float64 `json:"longitude"`
	AvgScore      float64  `json:"avg_score"`
	ReviewCount   int      `json:"review_count"`
	CoverPhotoURL *string  `json:"cover_photo_url"`
	DetailPhotos  []string `json:"detail_photos"`
	// 资质材料仅后台审核可见，不随公开接口序列化
	ContactPerson   *string    `json:"-"`
	LicensePhotoURL *string    `json:"-"`
	IDCardPhotoURL  *string    `json:"-"`
	ReviewNote      *string    `json:"-"`
	Status          string     `json:"status"`
	BannedAt        *time.Time `json:"-"`
	BannedReason    *string    `json:"-"`
	CreatedAt       time.Time  `json:"created_at"`
	UpdatedAt       time.Time  `json:"updated_at"`
	DeletedAt       *time.Time `json:"-"`
}

type CreateSitterRequest struct {
	Name          string   `json:"name" binding:"required,min=1,max=128"`
	Type          string   `json:"type" binding:"required,oneof=home shop visit other"`
	Description   string   `json:"description"`
	DailyPrice    float64  `json:"daily_price" binding:"min=0"`
	VisitPrice    float64  `json:"visit_price" binding:"min=0"`
	PriceMode     string   `json:"price_mode" binding:"required,oneof=per_day per_visit"`
	Capacity      int      `json:"capacity" binding:"required,min=1"`
	Address       string   `json:"address"`
	Latitude      *float64 `json:"latitude"`
	Longitude     *float64 `json:"longitude"`
	CoverPhotoURL string   `json:"cover_photo_url"`
	DetailPhotos  []string `json:"detail_photos"`
}

// ApplySitterRequest 机构入驻申请：只提交资质材料，经营信息（价格、容量等）审核通过后在机构端配置。
type ApplySitterRequest struct {
	Name            string `json:"name" binding:"required,min=1,max=128"`
	Type            string `json:"type" binding:"required,oneof=home shop visit other"`
	ContactPerson   string `json:"contact_person" binding:"required,min=1,max=64"`
	LicensePhotoURL string `json:"license_photo_url" binding:"required"`
	IDCardPhotoURL  string `json:"id_card_photo_url"`
	Address         string `json:"address"`
	Description     string `json:"description"`
}
type UpdateSitterRequest struct {
	Name          *string   `json:"name" binding:"omitempty,min=1,max=128"`
	Type          *string   `json:"type" binding:"omitempty,oneof=home shop visit other"`
	Description   *string   `json:"description"`
	DailyPrice    *float64  `json:"daily_price" binding:"omitempty,min=0"`
	VisitPrice    *float64  `json:"visit_price" binding:"omitempty,min=0"`
	PriceMode     *string   `json:"price_mode" binding:"omitempty,oneof=per_day per_visit"`
	Capacity      *int      `json:"capacity" binding:"omitempty,min=1"`
	Address       *string   `json:"address"`
	Latitude      *float64  `json:"latitude"`
	Longitude     *float64  `json:"longitude"`
	CoverPhotoURL *string   `json:"cover_photo_url"`
	DetailPhotos  *[]string `json:"detail_photos"`
}
