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
	CreatedAt      time.Time  `json:"created_at"`
	UpdatedAt      time.Time  `json:"updated_at"`
	DeletedAt      *time.Time `json:"-"`
}

type RegisterRequest struct {
	Username string `json:"username" binding:"required,min=3,max=50"`
	Password string `json:"password" binding:"required,min=8,max=128"`
	Role     string `json:"role" binding:"required,oneof=owner sitter"`
	Nickname string `json:"nickname"`
	Phone    string `json:"phone"`
}

type LoginRequest struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
	// Client 标识登录来源：app=桌面/手机客户端（不允许管理员），留空视为管理后台。
	Client string `json:"client"`
}

type AuthResponse struct {
	User          UserPublic `json:"user"`
	AccessToken   string     `json:"access_token"`
	RefreshToken  string     `json:"refresh_token"`
	ExpiresIn     int64      `json:"expires_in"`
	SingleSession bool       `json:"single_session"`
	// CancelWithdrawn 登录时自动撤销了注销申请（冷静期内登录即不注销）
	CancelWithdrawn bool `json:"cancel_withdrawn,omitempty"`
}

type UserPublic struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
	Nickname string `json:"nickname"`
	Role     string `json:"role"`
}
