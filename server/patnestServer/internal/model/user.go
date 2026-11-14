package model

import (
	"time"
)

type User struct {
	ID             int64      `json:"id"`
	Username       string     `json:"username"`
	PasswordHash   string     `json:"-"`
	Nickname       *string    `json:"nickname"`
	Phone          *string    `json:"phone"`
	Email          *string    `json:"email"`
	Role           string     `json:"role"`
	AvatarURL      *string    `json:"avatar_url"`
	Status         int16      `json:"status"`
	LoginFailCount int16      `json:"-"`
	LockedUntil    *time.Time `json:"-"`
	// 两步验证（0023）：只在登录校验内部使用，绝不跟随用户对象出站
	TotpEnabled  bool       `json:"-"`
	TotpSecret   string     `json:"-"`
	TotpLastStep int64      `json:"-"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
	DeletedAt    *time.Time `json:"-"`
}

type RegisterRequest struct {
	Username string `json:"username" binding:"required,min=3,max=50"`
	Password string `json:"password" binding:"required,min=8,max=128"`
	Role     string `json:"role" binding:"required,oneof=owner sitter"`
	Nickname string `json:"nickname"`
	// Phone 注册必填：手机号是登录标识、找回账号与订单/寄养提醒的唯一可靠联系方式。
	// 这里不加 binding:"required"，由 handler 统一给出「请填写手机号」这类可读提示。
	Phone string `json:"phone"`
	// InviteCode 选填：好友的邀请码，注册时绑定邀请关系（0022）。
	InviteCode string `json:"invite_code" binding:"omitempty,max=16"`
}

type LoginRequest struct {
	// Username 支持「用户名 或 手机号」两种登录标识。
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
	// Client 标识登录来源：app=桌面/手机客户端（不允许管理员），留空视为管理后台。
	Client string `json:"client"`
	// TotpCode 两步验证口令（选填）。正常流程是密码通过后拿 totp_ticket 再调
	// /auth/login/totp；只有在 Redis 不可用、票据无处安放时才走这个「一步到位」的分支。
	TotpCode string `json:"totp_code"`
}

// TotpLoginRequest 是两步验证第二步的请求体。
type TotpLoginRequest struct {
	Ticket string `json:"ticket" binding:"required"`
	// Code 可以是 6 位动态口令，也可以是 10 位恢复码（XXXXX-XXXXX）。
	Code string `json:"code" binding:"required"`
}

type AuthResponse struct {
	User          UserPublic `json:"user"`
	AccessToken   string     `json:"access_token"`
	RefreshToken  string     `json:"refresh_token"`
	ExpiresIn     int64      `json:"expires_in"`
	SingleSession bool       `json:"single_session"`
	// CancelWithdrawn 登录时自动撤销了注销申请（冷静期内登录即不注销）
	CancelWithdrawn bool `json:"cancel_withdrawn,omitempty"`
	// NeedBindPhone 登录成功但账号没有手机号：客户端应弹窗提示「请绑定手机号」。
	NeedBindPhone bool `json:"need_bind_phone"`
	// Client 本次登录识别出的客户端：admin / web / desktop / android / ios / miniprogram
	Client string `json:"client,omitempty"`
	// ClientPlatform windows / macos / linux / android / ios / wechat / unknown
	ClientPlatform string `json:"client_platform,omitempty"`
	// TotpRequired 密码已通过但账号启用了两步验证：此时不下发令牌，
	// 客户端要拿着 TotpTicket 再调 /auth/login/totp 提交动态口令。
	TotpRequired bool `json:"totp_required,omitempty"`
	// TotpTicket 一次性挑战票据（5 分钟有效，用后即焚）。
	TotpTicket string `json:"totp_ticket,omitempty"`
}

func (u User) NicknameValue() string {
	if u.Nickname == nil {
		return ""
	}
	return *u.Nickname
}

type UserPublic struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
	Nickname string `json:"nickname"`
	Role     string `json:"role"`
	Phone    string `json:"phone,omitempty"`
	// PhoneBound 是否已绑定手机号；未绑定时客户端需引导绑定
	PhoneBound bool `json:"phone_bound"`
}
