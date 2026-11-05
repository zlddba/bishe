package model

import "time"

// Wallet 用户钱包（真实货币）。机构复用同一张表。
type Wallet struct {
	UserID        int64     `json:"user_id"`
	Balance       float64   `json:"balance"`
	FrozenBalance float64   `json:"frozen_balance"`
	TotalRecharge float64   `json:"total_recharge"`
	TotalConsume  float64   `json:"total_consume"`
	UpdatedAt     time.Time `json:"updated_at"`
}

// WalletTransaction 资金流水
type WalletTransaction struct {
	ID            int64   `json:"id"`
	UserID        int64   `json:"user_id"`
	Type          string  `json:"type"`
	Amount        float64 `json:"amount"`
	BalanceAfter  float64 `json:"balance_after"`
	Status        string  `json:"status"`
	Channel       string  `json:"channel"`
	RefType       string  `json:"ref_type"`
	RefID         int64   `json:"ref_id"`
	TransactionID string  `json:"transaction_id"`
	Remark        string  `json:"remark"`
	CreatedAt     string  `json:"created_at"`
}

// RechargeOrder 充值单
type RechargeOrder struct {
	ID            int64   `json:"id"`
	UserID        int64   `json:"user_id"`
	Amount        float64 `json:"amount"`
	Channel       string  `json:"channel"`
	Status        string  `json:"status"`
	TransactionID string  `json:"transaction_id"`
	PaidAt        string  `json:"paid_at"`
	CreatedAt     string  `json:"created_at"`
}

// RechargeRequest 发起充值请求
type RechargeRequest struct {
	Amount  float64 `json:"amount" binding:"required,gt=0"`
	Channel string  `json:"channel"`
}

// WalletAdjustRequest 后台人工调账请求（amount 可正可负）
type WalletAdjustRequest struct {
	Amount float64 `json:"amount" binding:"required"`
	Reason string  `json:"reason" binding:"required,min=1,max=200"`
}

// PayOrderRequest 支付订单请求（在原有订单支付上扩展支付方式）
type PayOrderRequest struct {
	Method string `json:"method"` // balance | wechat | alipay | mock
}
