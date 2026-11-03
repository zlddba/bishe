package model

import "time"

type Order struct {
	ID              int64      `json:"id"`
	OrderNo         string     `json:"order_no"`
	OwnerID         int64      `json:"owner_id"`
	SitterID        int64      `json:"sitter_id"`
	Status          string     `json:"status"`
	StartDate       string     `json:"start_date"`
	EndDate         string     `json:"end_date"`
	TotalDays       int        `json:"total_days"`
	PetCount        int16      `json:"pet_count"`
	EstimatedAmount float64    `json:"estimated_amount"`
	TotalAmount     float64    `json:"total_amount"`
	PaymentStatus   string     `json:"payment_status"`
	CancelReason    *string    `json:"cancel_reason,omitempty"`
	CancelTime      *time.Time `json:"cancel_time,omitempty"`
	PaidAt          *time.Time `json:"paid_at,omitempty"`
	AcceptedAt      *time.Time `json:"accepted_at,omitempty"`
	CompletedAt     *time.Time `json:"completed_at,omitempty"`
	EvaluatedAt     *time.Time `json:"evaluated_at,omitempty"`
	Remark          *string    `json:"remark,omitempty"`
	CreatedAt       time.Time  `json:"created_at"`
	UpdatedAt       time.Time  `json:"updated_at"`
	DeletedAt       *time.Time `json:"-"`

	// Joined fields
	SitterName string `json:"sitter_name,omitempty"`
	OwnerName  string `json:"owner_name,omitempty"`
}

type OrderPet struct {
	ID           int64   `json:"id"`
	OrderID      int64   `json:"order_id"`
	PetID        int64   `json:"pet_id"`
	StayDate     string  `json:"stay_date"`
	CheckinTime  *string `json:"checkin_time,omitempty"`
	CheckoutTime *string `json:"checkout_time,omitempty"`
	Status       string  `json:"status"`

	// Joined
	PetName string `json:"pet_name,omitempty"`
}

type CreateOrderRequest struct {
	SitterID     int64              `json:"sitter_id" binding:"required"`
	StartDate    string             `json:"start_date" binding:"required"`
	EndDate      string             `json:"end_date" binding:"required"`
	Pets         []CreateOrderPet   `json:"pets" binding:"required,min=1,max=10"`
	Remark       string             `json:"remark"`
	UserCouponID int64              `json:"user_coupon_id"`
	PointsUsed   int                `json:"points_used"`
	Addons       []CreateOrderAddon `json:"addons"`
}

type CreateOrderAddon struct {
	AddonID int64 `json:"addon_id" binding:"required"`
	Qty     int   `json:"qty"`
}

type CreateOrderPet struct {
	PetID    int64  `json:"pet_id" binding:"required"`
	StayDate string `json:"stay_date"`
}

type OrderDetailResponse struct {
	Order    Order      `json:"order"`
	Pets     []OrderPet `json:"pets"`
	Dynamics []Dynamic  `json:"dynamics,omitempty"`
	Review   *Review    `json:"review,omitempty"`
	Payments []Payment  `json:"payments,omitempty"`
}

type CancelOrderRequest struct {
	Reason string `json:"reason"`
}

type RejectOrderRequest struct {
	Reason string `json:"reason" binding:"required"`
}
