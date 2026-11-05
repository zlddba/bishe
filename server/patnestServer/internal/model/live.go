package model

import "time"

// LiveRoom 一场商家直播。status: living=直播中，ended=正常结束，banned=被后台封禁终止。
type LiveRoom struct {
	ID          int64      `json:"id"`
	SitterID    int64      `json:"sitter_id"`
	SitterName  string     `json:"sitter_name,omitempty"`
	SitterCover *string    `json:"sitter_cover,omitempty"`
	Title       string     `json:"title"`
	CoverURL    *string    `json:"cover_url"`
	Status      string     `json:"status"`
	ViewerCount int        `json:"viewer_count"`
	PeakViewers int        `json:"peak_viewers"`
	TotalViews  int64      `json:"total_views"`
	StartedAt   time.Time  `json:"started_at"`
	EndedAt     *time.Time `json:"ended_at,omitempty"`
	EndReason   *string    `json:"end_reason,omitempty"`
	BanReason   *string    `json:"ban_reason,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`

	// 推拉流地址：stream_key / push_url 只下发给开播机构本人（与后台），观看端只拿 play_*。
	StreamKey string `json:"stream_key,omitempty"`
	PushURL   string `json:"push_url,omitempty"`
	PlayHLS   string `json:"play_hls,omitempty"`
	PlayFLV   string `json:"play_flv,omitempty"`
	PlayRTMP  string `json:"play_rtmp,omitempty"`
}

// LiveMessage 直播间弹幕。status=hidden 的只有后台看得到。
type LiveMessage struct {
	ID        int64     `json:"id"`
	RoomID    int64     `json:"room_id"`
	UserID    int64     `json:"user_id"`
	Nickname  string    `json:"nickname"`
	AvatarURL *string   `json:"avatar_url,omitempty"`
	Content   string    `json:"content"`
	Status    string    `json:"status,omitempty"`
	CreatedAt time.Time `json:"created_at"`
}
