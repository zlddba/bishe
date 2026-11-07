//go:build integration

// 登录客户端识别回归（连真实数据库）：
//
//	go test -tags integration ./internal/router -run TestLoginClientDetection -v
//
// 验证 login/register 会按「显式 client 字段 + User-Agent」识别客户端，
// 并把结果回显在响应里（Redis 未启用时同样成立，只是不落会话画像）。
package router_test

import (
	"fmt"
	"net/http"
	"testing"
	"time"

	"patnestServer/internal/db"

	"context"
)

type authClientView struct {
	AccessToken    string `json:"access_token"`
	Client         string `json:"client"`
	ClientPlatform string `json:"client_platform"`
	NeedBindPhone  bool   `json:"need_bind_phone"`
	User           struct {
		Username string `json:"username"`
	} `json:"user"`
}

func TestLoginClientDetection(t *testing.T) {
	baseURL := setupServer(t)
	username := fmt.Sprintf("probe_client_%d", time.Now().UnixNano())
	phone := probePhone()

	// 1) 注册：显式 app + Android okhttp UA → android
	androidCl := &apiClient{t: t, base: baseURL, ua: "okhttp/4.12.0"}
	code, env := androidCl.do("POST", "/api/v1/auth/register", map[string]any{
		"username": username, "password": "Abc12345!", "role": "owner", "phone": phone, "client": "app",
	})
	if code != http.StatusOK {
		t.Fatalf("注册失败: %d %s", code, env.Message)
	}
	reg := decode[authClientView](t, env)
	if reg.Client != "android" || reg.ClientPlatform != "android" {
		t.Fatalf("Android 注册识别错误: client=%q platform=%q", reg.Client, reg.ClientPlatform)
	}

	var userID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT id FROM users WHERE username = $1", username).Scan(&userID); err != nil || userID == 0 {
		t.Fatalf("取 user_id 失败: %v", err)
	}
	t.Cleanup(func() { purgeProbeUser(t, userID) })

	// 2) 小程序登录：显式 miniprogram + 微信 UA → miniprogram / wechat
	mpCl := &apiClient{t: t, base: baseURL, ua: "Mozilla/5.0 (iPhone) MicroMessenger/8.0.49"}
	code, env = mpCl.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": "Abc12345!", "client": "miniprogram",
	})
	if code != http.StatusOK {
		t.Fatalf("小程序登录失败: %d %s", code, env.Message)
	}
	mp := decode[authClientView](t, env)
	if mp.Client != "miniprogram" || mp.ClientPlatform != "wechat" {
		t.Fatalf("小程序登录识别错误: client=%q platform=%q", mp.Client, mp.ClientPlatform)
	}
	mpCl.tok = mp.AccessToken

	// 3) 桌面端登录：显式 app + Ktor UA → desktop
	desktopCl := &apiClient{t: t, base: baseURL, ua: "Ktor client/3.6.1"}
	code, env = desktopCl.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": "Abc12345!", "client": "app",
	})
	if code != http.StatusOK {
		t.Fatalf("桌面端登录失败: %d %s", code, env.Message)
	}
	desktop := decode[authClientView](t, env)
	if desktop.Client != "desktop" {
		t.Fatalf("桌面端登录识别错误: client=%q", desktop.Client)
	}

	// 4) 不带 client 字段的浏览器登录：按 UA 识别为 web
	webCl := &apiClient{t: t, base: baseURL, ua: "Mozilla/5.0 (Windows NT 10.0) Chrome/120"}
	code, env = webCl.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": "Abc12345!",
	})
	if code != http.StatusOK {
		t.Fatalf("浏览器登录失败: %d %s", code, env.Message)
	}
	web := decode[authClientView](t, env)
	if web.Client != "web" || web.ClientPlatform != "windows" {
		t.Fatalf("浏览器登录识别错误: client=%q platform=%q", web.Client, web.ClientPlatform)
	}

	// 5) /auth/me 回显当前请求的客户端（中间件按令牌从 Redis/UA 取）
	code, meEnv := mpCl.do("GET", "/api/v1/auth/me", nil)
	if code != http.StatusOK {
		t.Fatalf("/auth/me 失败: %d", code)
	}
	me := decode[struct {
		Client         string `json:"client"`
		ClientPlatform string `json:"client_platform"`
	}](t, meEnv)
	if me.Client != "miniprogram" {
		t.Fatalf("/auth/me 的 client = %q，期望 miniprogram", me.Client)
	}
}
