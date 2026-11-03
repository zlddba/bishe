package model

import "time"

type Dynamic struct {
	ID        int64     `json:"id"`
	OrderID   int64     `json:"order_id"`
	SitterID  int64     `json:"sitter_id"`
	AuthorID  int64     `json:"author_id"`
	Content   string    `json:"content"`
	Photos    []string  `json:"photos"`
	Tags      []string  `json:"tags"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type CreateDynamicRequest struct {
	Content string   `json:"content" binding:"required,min=1,max=2000"`
	Photos  []string `json:"photos"`
	Tags    []string `json:"tags"`
}

type Review struct {
	ID               int64      `json:"id"`
	OrderID          int64      `json:"order_id"`
	SitterID         int64      `json:"sitter_id"`
	OwnerID          int64      `json:"owner_id"`
	OverallScore     int16      `json:"overall_score"`
	ServiceScore     int16      `json:"service_score"`
	EnvironmentScore int16      `json:"environment_score"`
	HygieneScore     int16      `json:"hygiene_score"`
	OwnerComment     *string    `json:"owner_comment"`
	OwnerPhotos      []string   `json:"owner_photos"`
	ReplyText        *string    `json:"reply_text"`
	ReplyAt          *time.Time `json:"reply_at"`
	IsVisible        bool       `json:"is_visible"`
	CreatedAt        time.Time  `json:"created_at"`
	UpdatedAt        time.Time  `json:"updated_at"`
}

// Payment 订单支付/退款流水（主人可见自己的记录）
type Payment struct {
	ID            int64   `json:"id"`
	Amount        float64 `json:"amount"`
	Method        string  `json:"method"`
	Status        string  `json:"status"`
	TransactionID string  `json:"transaction_id"`
	CreatedAt     string  `json:"created_at"`
	PaidAt        string  `json:"paid_at"`
	RefundedAt    string  `json:"refunded_at"`
}
type CreateReviewRequest struct {
	OverallScore     int16    `json:"overall_score" binding:"required,min=1,max=5"`
	ServiceScore     int16    `json:"service_score" binding:"required,min=1,max=5"`
	EnvironmentScore int16    `json:"environment_score" binding:"required,min=1,max=5"`
	HygieneScore     int16    `json:"hygiene_score" binding:"required,min=1,max=5"`
	OwnerComment     string   `json:"owner_comment" binding:"required,min=1,max=1000"`
	OwnerPhotos      []string `json:"owner_photos"`
}

type ReplyReviewRequest struct {
	ReplyText string `json:"reply_text" binding:"required,min=1,max=500"`
}

type Notification struct {
	ID        int64     `json:"id"`
	UserID    int64     `json:"user_id"`
	Type      string    `json:"type"`
	Title     string    `json:"title"`
	Body      string    `json:"body"`
	Extra     string    `json:"extra"`
	IsRead    bool      `json:"is_read"`
	CreatedAt time.Time `json:"created_at"`
}
