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

	// 资料还在 pending 时不允许开播（小程序端就是靠这个提示「审核中」的）
	code, env = sitter.do("POST", "/api/v1/sitter/live", map[string]any{"title": "审核中开播"})
	if code != http.StatusForbidden {
		t.Fatalf("pending 机构开播应 403，实际 %d %s", code, env.Message)
	}
	if !strings.Contains(env.Message, "审核") {
		t.Fatalf("pending 开播提示应说明审核中，实际 %q", env.Message)
	}
	// 模拟后台审核通过
	if _, err := db.Pool.Exec(context.Background(),
		`UPDATE sitters SET status = 'active' WHERE user_id = $1`, sitterUserID); err != nil {
		t.Fatalf("激活机构失败: %v", err)
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
	// 演示库里可能还有其他人在播，这里只认我们刚开的那一场
	var listed *liveRoomDTO
	for i := range list.Items {
		if list.Items[i].ID == room.ID {
			listed = &list.Items[i]
			break
		}
	}
	if listed == nil {
		t.Fatalf("直播列表里找不到刚开的场次 %d（total=%d）", room.ID, list.Total)
	}
	if listed.StreamKey != "" || listed.PushURL != "" {
		t.Fatalf("列表不能下发推流钥匙: %+v", *listed)
	}
	if listed.PlayHLS == "" {
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
		ViewerCount int         `json:"viewer_count"`
		Room        liveRoomDTO `json:"room"`
	}](t, env)
	if joined.ViewerCount != 1 {
		t.Fatalf("进房后在线人数 = %d，期望 1", joined.ViewerCount)
	}
	// 回归：join 兼心跳，返回的 room 必须仍带播放地址，但绝不能带推流钥匙。
	// 曾经这里先 hide 再 fill，结果心跳一次客户端就把播放器拆了（画面闪没/错乱）。
	if joined.Room.PlayHLS == "" || joined.Room.PlayFLV == "" {
		t.Fatalf("join 返回的 room 缺少播放地址: %+v", joined.Room)
	}
	if joined.Room.StreamKey != "" || joined.Room.PushURL != "" {
		t.Fatalf("join 返回的 room 不应带推流钥匙: %+v", joined.Room)
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
	var adminRoom *liveRoomDTO
	for i := range adminList.Items {
		if adminList.Items[i].ID == room.ID {
			adminRoom = &adminList.Items[i]
			break
		}
	}
	if adminRoom == nil || adminRoom.PushURL == "" {
		t.Fatalf("后台应能看到这场直播的推流地址: %+v", adminList.Items)
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

	// ---------- 回归：审核通过后旧 token 的 role 还是 owner，也必须能开播 ----------
	// 小程序端常见场景：登录时是 owner → 后台审核把 users.role 改成 sitter，
	// 手里那张 token 仍写着 owner。开播接口不能按 JWT role 卡，否则用户只看到"无法直播"。
	if _, err := db.Pool.Exec(context.Background(),
		`UPDATE users SET role = 'owner' WHERE id = $1`, sitterUserID); err != nil {
		t.Fatalf("改回 owner 失败: %v", err)
	}
	ownerToken, _, _ := loginProbe(t, baseURL, usernameOf(t, sitterUserID), "Abc12345!")
	code, env = ownerToken.do("POST", "/api/v1/sitter/live", map[string]any{"title": "owner token 开播"})
	if code != http.StatusOK {
		t.Fatalf("机构资料 active 时，owner 角色 token 也应能开播，实际 %d %s", code, env.Message)
	}
	reopened := decode[liveRoomDTO](t, env)
	_, _ = ownerToken.do("PUT", fmt.Sprintf("/api/v1/sitter/live/%d/stop", reopened.ID), map[string]any{})
}

// usernameOf 取 user_id 对应的登录名，用于换角色后重新登录。
func usernameOf(t *testing.T, userID int64) string {
	t.Helper()
	var name string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT username FROM users WHERE id = $1", userID).Scan(&name); err != nil {
		t.Fatalf("取用户名失败: %v", err)
	}
	return name
}

// loginProbe 用已有账号登录，返回带令牌的客户端。
func loginProbe(t *testing.T, baseURL, username, password string) (*apiClient, int64, string) {
	t.Helper()
	cl := &apiClient{t: t, base: baseURL}
	code, env := cl.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": password, "client": "app",
	})
	if code != http.StatusOK {
		t.Fatalf("登录失败: %d %s", code, env.Message)
	}
	cl.tok = decode[struct {
		AccessToken string `json:"access_token"`
	}](t, env).AccessToken
	var userID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT id FROM users WHERE username = $1", username).Scan(&userID)
	return cl, userID, username
}
