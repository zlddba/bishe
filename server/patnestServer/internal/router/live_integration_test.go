//go:build integration

// 商家直播回归（连真实数据库）：
//
//	go test -tags integration ./internal/router -run TestLiveFlow -v
//
// 覆盖：机构开播（拿推流地址）→ 观众列表/详情（拿不到推流钥匙）→ 进房心跳/在线人数 →
// 弹幕 → 后台查看/隐藏弹幕/强制下播/封禁与解封。推拉流本身由 SRS 承担，这里只校验契约。
package router_test

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"patnestServer/internal/db"

	"golang.org/x/crypto/bcrypt"
)

type liveRoomDTO struct {
	ID          int64   `json:"id"`
	SitterID    int64   `json:"sitter_id"`
	Title       string  `json:"title"`
	Status      string  `json:"status"`
	StreamKey   string  `json:"stream_key"`
	PushURL     string  `json:"push_url"`
	PlayHLS     string  `json:"play_hls"`
	PlayFLV     string  `json:"play_flv"`
	ViewerCount int     `json:"viewer_count"`
	TotalViews  int64   `json:"total_views"`
	EndReason   *string `json:"end_reason"`
	BanReason   *string `json:"ban_reason"`
}

type liveMessageDTO struct {
	ID       int64  `json:"id"`
	Content  string `json:"content"`
	Nickname string `json:"nickname"`
	Status   string `json:"status"`
}

// createAdminProbe 直接建一个管理员账号并登录（不走注册接口：注册接口禁止 admin）。
func createAdminProbe(t *testing.T, baseURL string) *apiClient {
	t.Helper()
	hash, err := bcrypt.GenerateFromPassword([]byte("Abc12345!"), bcrypt.DefaultCost)
	if err != nil {
		t.Fatalf("bcrypt: %v", err)
	}
	username := fmt.Sprintf("probe_admin_%d", time.Now().UnixNano())
	var userID int64
	if err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO users (username, password_hash, nickname, role) VALUES ($1, $2, '探针管理员', 'admin')
		 RETURNING id`, username, string(hash)).Scan(&userID); err != nil {
		t.Fatalf("创建管理员失败: %v", err)
	}
	t.Cleanup(func() {
		_, _ = db.Pool.Exec(context.Background(), `DELETE FROM users WHERE id = $1`, userID)
	})

	cl := &apiClient{t: t, base: baseURL}
	code, env := cl.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": "Abc12345!",
	})
	if code != http.StatusOK {
		t.Fatalf("管理员登录失败: %d %s", code, env.Message)
	}
	cl.tok = decode[struct {
		AccessToken string `json:"access_token"`
	}](t, env).AccessToken
	return cl
}

func TestLiveFlow(t *testing.T) {
	baseURL := setupServer(t)

	// ---------- 机构：建店 + 开播 ----------
	sitter, sitterUserID, _ := registerProbe(t, baseURL, "sitter")
	t.Cleanup(func() { purgeProbeUser(t, sitterUserID) })
	code, env := sitter.do("POST", "/api/v1/sitter/shop", map[string]any{
		"name": "直播测试机构", "type": "home", "price_mode": "per_day", "capacity": 3,
	})
	if code != http.StatusOK {
		t.Fatalf("建店失败: %d %s", code, env.Message)
	}

	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "探针直播"})
	if code != http.StatusOK {
		t.Fatalf("开播失败: %d %s", code, env.Message)
	}
	room := decode[liveRoomDTO](t, env)
	if room.ID == 0 || room.StreamKey == "" || !strings.HasPrefix(room.PushURL, "rtmp://") {
		t.Fatalf("开播响应缺少推流信息: %+v", room)
	}
	if !strings.HasSuffix(room.PlayHLS, ".m3u8") || !strings.HasSuffix(room.PlayFLV, ".flv") {
		t.Fatalf("播放地址不对: hls=%q flv=%q", room.PlayHLS, room.PlayFLV)
	}
	// 同一机构不能同时开两场
	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "第二场"})
	if code != http.StatusConflict {
		t.Fatalf("重复开播应返回 409，实际 %d %s", code, env.Message)
	}

	// ---------- 观众：列表 / 详情 / 进房 / 弹幕 ----------
	owner, ownerID, _ := registerProbe(t, baseURL, "owner")
	t.Cleanup(func() { purgeProbeUser(t, ownerID) })

	code, env = owner.do("GET", "/api/v1/live", nil)
	if code != http.StatusOK {
		t.Fatalf("直播列表失败: %d %s", code, env.Message)
	}
	list := decode[struct {
		Items []liveRoomDTO `json:"items"`
		Total int           `json:"total"`
	}](t, env)
	if list.Total != 1 || len(list.Items) != 1 {
		t.Fatalf("直播列表应有 1 场，实际 %d", list.Total)
	}
	if list.Items[0].StreamKey != "" || list.Items[0].PushURL != "" {
		t.Fatalf("列表不能下发推流钥匙: %+v", list.Items[0])
	}
	if list.Items[0].PlayHLS == "" {
		t.Fatal("列表缺少 play_hls")
	}

	code, env = owner.do("GET", fmt.Sprintf("/api/v1/live/%d", room.ID), nil)
	if code != http.StatusOK {
		t.Fatalf("直播详情失败: %d %s", code, env.Message)
	}
	detail := decode[struct {
		Room liveRoomDTO `json:"room"`
	}](t, env)
	if detail.Room.StreamKey != "" || detail.Room.PushURL != "" {
		t.Fatalf("详情不能下发推流钥匙: %+v", detail.Room)
	}
	if detail.Room.PlayHLS == "" {
		t.Fatal("详情缺少 play_hls")
	}

	roomPath := fmt.Sprintf("/api/v1/live/%d", room.ID)
	code, env = owner.do("POST", roomPath+"/join", map[string]any{})
	if code != http.StatusOK {
		t.Fatalf("进房失败: %d %s", code, env.Message)
	}
	joined := decode[struct {
		ViewerCount int `json:"viewer_count"`
	}](t, env)
	if joined.ViewerCount != 1 {
		t.Fatalf("进房后在线人数 = %d，期望 1", joined.ViewerCount)
	}
	// 心跳（再次 join）不应把人数算成 2
	code, env = owner.do("POST", roomPath+"/join", map[string]any{})
	if code != http.StatusOK {
		t.Fatalf("心跳失败: %d %s", code, env.Message)
	}
	if got := decode[struct {
		ViewerCount int `json:"viewer_count"`
	}](t, env); got.ViewerCount != 1 {
		t.Fatalf("心跳重复计数: %d", got.ViewerCount)
	}

	code, env = owner.do("POST", roomPath+"/messages", map[string]any{"content": "主播好"})
	if code != http.StatusOK {
		t.Fatalf("发弹幕失败: %d %s", code, env.Message)
	}
	msg := decode[liveMessageDTO](t, env)
	if msg.ID == 0 || msg.Nickname == "" {
		t.Fatalf("弹幕响应不完整: %+v", msg)
	}
	code, env = owner.do("GET", roomPath+"/messages", nil)
	if code != http.StatusOK {
		t.Fatalf("拉弹幕失败: %d %s", code, env.Message)
	}
	if got := decode[struct {
		Items []liveMessageDTO `json:"items"`
	}](t, env); len(got.Items) != 1 || got.Items[0].Content != "主播好" {
		t.Fatalf("弹幕列表不对: %+v", got.Items)
	}

	// ---------- 后台：查看 / 隐藏弹幕 / 强制下播 ----------
	admin := createAdminProbe(t, baseURL)
	code, env = admin.do("GET", "/api/v1/admin/live", nil)
	if code != http.StatusOK {
		t.Fatalf("后台直播列表失败: %d %s", code, env.Message)
	}
	adminList := decode[struct {
		Items []liveRoomDTO `json:"items"`
	}](t, env)
	if len(adminList.Items) != 1 || adminList.Items[0].PushURL == "" {
		t.Fatalf("后台应能看到推流地址: %+v", adminList.Items)
	}

	code, env = admin.do("PUT", fmt.Sprintf("/api/v1/admin/live-messages/%d/hide", msg.ID),
		map[string]any{"hidden": true})
	if code != http.StatusOK {
		t.Fatalf("隐藏弹幕失败: %d %s", code, env.Message)
	}
	code, env = owner.do("GET", roomPath+"/messages", nil)
	if code != http.StatusOK {
		t.Fatalf("拉弹幕失败: %d %s", code, env.Message)
	}
	if got := decode[struct {
		Items []liveMessageDTO `json:"items"`
	}](t, env); len(got.Items) != 0 {
		t.Fatalf("隐藏后的弹幕不应再可见: %+v", got.Items)
	}

	code, env = admin.do("PUT", fmt.Sprintf("/api/v1/admin/live/%d/stop", room.ID),
		map[string]any{"reason": "集成测试收尾"})
	if code != http.StatusOK {
		t.Fatalf("强制下播失败: %d %s", code, env.Message)
	}
	code, env = owner.do("POST", roomPath+"/join", map[string]any{})
	if code != http.StatusConflict {
		t.Fatalf("已结束直播进房应 409，实际 %d %s", code, env.Message)
	}

	// ---------- 封禁与解封 ----------
	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "被封禁前的直播"})
	if code != http.StatusOK {
		t.Fatalf("二次开播失败: %d %s", code, env.Message)
	}
	room2 := decode[liveRoomDTO](t, env)

	code, env = admin.do("PUT", fmt.Sprintf("/api/v1/admin/live/%d/ban", room2.ID),
		map[string]any{"reason": "违规内容"})
	if code != http.StatusOK {
		t.Fatalf("封禁失败: %d %s", code, env.Message)
	}
	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "封禁后开播"})
	if code != http.StatusForbidden {
		t.Fatalf("封禁后开播应 403，实际 %d %s", code, env.Message)
	}
	if !strings.Contains(env.Message, "违规内容") {
		t.Fatalf("封禁提示应带原因，实际 %q", env.Message)
	}
	// 机构端能看到自己的封禁状态
	code, env = sitter.do("GET", "/api/v1/sitter/live", nil)
	if code != http.StatusOK {
		t.Fatalf("查询我的直播失败: %d %s", code, env.Message)
	}
	mine := decode[struct {
		LiveBanned bool    `json:"live_banned"`
		BanReason  *string `json:"ban_reason"`
	}](t, env)
	if !mine.LiveBanned || mine.BanReason == nil || *mine.BanReason != "违规内容" {
		t.Fatalf("机构端封禁状态不对: %+v", mine)
	}

	var sitterID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT id FROM sitters WHERE user_id = $1", sitterUserID).Scan(&sitterID); err != nil {
		t.Fatalf("取机构 id 失败: %v", err)
	}
	code, env = admin.do("PUT", fmt.Sprintf("/api/v1/admin/sitters/%d/live-ban", sitterID),
		map[string]any{"banned": false})
	if code != http.StatusOK {
		t.Fatalf("解封失败: %d %s", code, env.Message)
	}
	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "解封后直播"})
	if code != http.StatusOK {
		t.Fatalf("解封后应能开播，实际 %d %s", code, env.Message)
	}
	last := decode[liveRoomDTO](t, env)
	_, _ = sitter.do("PUT", fmt.Sprintf("/api/v1/sitter/live/%d/stop", last.ID), map[string]any{})
}
