// Package clientinfo 识别「这次登录来自哪个客户端」，并把会话信息写进 Redis。
//
// 为什么要单独一包：登录（handler）和中台鉴权（middleware）都要用同一套判定，
// 放在 handler 里会被 middleware 反向依赖。
package clientinfo

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"strings"
	"time"

	"patnestServer/internal/cache"
)

const (
	ClientAdmin       = "admin"       // 后台管理网页
	ClientWeb         = "web"         // 普通网页端
	ClientDesktop     = "desktop"     // KMP 桌面端
	ClientAndroid     = "android"     // Android App
	ClientIOS         = "ios"         // iOS App
	ClientMiniProgram = "miniprogram" // 微信小程序（含开发者工具）
	ClientUnknown     = "unknown"
)

// Info 一次登录的客户端画像。存在 Redis 里，供刷新令牌、退出登录和中台排查使用。
type Info struct {
	Client   string `json:"client"`
	Platform string `json:"platform"`
	UA       string `json:"ua,omitempty"`
	IP       string `json:"ip,omitempty"`
	// LoginAt 登录时间（Unix 秒）
	LoginAt int64 `json:"login_at"`
}

// Detect 识别客户端。
//
// explicit 是请求体里的 client 字段（app / web / miniprogram，留空视为后台）；
// ua 是 User-Agent；role 只用于兜底区分「浏览器 + 管理员」。
func Detect(explicit, ua, role string) Info {
	u := strings.ToLower(ua)
	platform := platformOf(u)

	client := strings.ToLower(strings.TrimSpace(explicit))
	switch client {
	case "mp", "miniprogram", "mini", "wechat", "wx":
		client = ClientMiniProgram
	case "admin", "webadmin", "manage":
		client = ClientAdmin
	case "web", "h5", "browser":
		client = ClientWeb
	case "desktop", "pc":
		client = ClientDesktop
	case "android":
		client = ClientAndroid
	case "ios":
		client = ClientIOS
	case "app", "":
		client = guessByUA(u, platform, role)
	default:
		// 没见过的显式值：不信任，按 UA 兜底
		client = guessByUA(u, platform, role)
	}

	return Info{
		Client:   client,
		Platform: platform,
		UA:       trimUA(ua),
		LoginAt:  time.Now().Unix(),
	}
}

func guessByUA(u, platform, role string) string {
	switch {
	case strings.Contains(u, "micromessenger"), strings.Contains(u, "wechatdevtools"),
		strings.Contains(u, "miniprogram"):
		return ClientMiniProgram
	case strings.Contains(u, "okhttp"), strings.Contains(u, "dalvik"), platform == "android":
		return ClientAndroid
	case strings.Contains(u, "cfnetwork"), platform == "ios":
		return ClientIOS
	case strings.Contains(u, "ktor"), strings.Contains(u, "java/"), strings.Contains(u, "jvm"),
		strings.Contains(u, "curl"):
		return ClientDesktop
	case strings.Contains(u, "mozilla"):
		if role == "admin" {
			return ClientAdmin
		}
		return ClientWeb
	case role == "admin":
		return ClientAdmin
	default:
		return ClientUnknown
	}
}

func platformOf(u string) string {
	switch {
	case strings.Contains(u, "micromessenger"), strings.Contains(u, "wechatdevtools"),
		strings.Contains(u, "miniprogram"):
		return "wechat"
	case strings.Contains(u, "android"), strings.Contains(u, "okhttp"):
		return "android"
	case strings.Contains(u, "iphone"), strings.Contains(u, "ipad"), strings.Contains(u, "cfnetwork"):
		return "ios"
	case strings.Contains(u, "mac os"), strings.Contains(u, "macintosh"):
		return "macos"
	case strings.Contains(u, "windows"), strings.Contains(u, "win64"):
		return "windows"
	case strings.Contains(u, "linux"), strings.Contains(u, "x11"):
		return "linux"
	default:
		return "unknown"
	}
}

func trimUA(ua string) string {
	ua = strings.TrimSpace(ua)
	if len(ua) > 255 {
		return ua[:255]
	}
	return ua
}

// HashToken 会话键用令牌哈希，避免把明文令牌写进 Redis。
func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func accessKey(hash string) string  { return "pn:client:access:" + hash }
func refreshKey(hash string) string { return "pn:client:refresh:" + hash }
func lastKey(userID int64) string   { return "pn:client:last:" + itoa(userID) }

func itoa(v int64) string {
	if v == 0 {
		return "0"
	}
	var buf [20]byte
	i := len(buf)
	neg := v < 0
	if neg {
		v = -v
	}
	for v > 0 {
		i--
		buf[i] = byte('0' + v%10)
		v /= 10
	}
	if neg {
		i--
		buf[i] = '-'
	}
	return string(buf[i:])
}

// Save 写入一次会话（访问令牌与刷新令牌各一条）。
func Save(accessToken, refreshToken string, info Info, accessTTL, refreshTTL time.Duration) {
	body, err := json.Marshal(info)
	if err != nil {
		return
	}
	if !cache.Enabled() {
		return
	}
	if accessToken != "" {
		_ = cache.SetString(accessKey(HashToken(accessToken)), string(body), accessTTL)
	}
	if refreshToken != "" {
		_ = cache.SetString(refreshKey(HashToken(refreshToken)), string(body), refreshTTL)
	}
}

// LoadAccess / LoadRefresh 读回会话；Redis 未启用或没命中时 ok=false。
func LoadAccess(token string) (Info, bool) {
	return load(accessKey(HashToken(token)))
}

func LoadRefresh(token string) (Info, bool) {
	return load(refreshKey(HashToken(token)))
}

func load(key string) (Info, bool) {
	if !cache.Enabled() {
		return Info{}, false
	}
	raw, ok, err := cache.GetString(key)
	if err != nil || !ok {
		return Info{}, false
	}
	var info Info
	if json.Unmarshal([]byte(raw), &info) != nil {
		return Info{}, false
	}
	return info, true
}

// DropAccess / DropRefresh 会话结束时清掉对应记录。
func DropAccess(token string) {
	if cache.Enabled() && token != "" {
		_ = cache.Del(accessKey(HashToken(token)))
	}
}

func DropRefresh(token string) {
	if cache.Enabled() && token != "" {
		_ = cache.Del(refreshKey(HashToken(token)))
	}
}

// SaveLast 记录该账号最近一次登录的客户端（30 天），/auth/me 会回显。
func SaveLast(userID int64, info Info) {
	if !cache.Enabled() || userID <= 0 {
		return
	}
	body, err := json.Marshal(info)
	if err != nil {
		return
	}
	_ = cache.SetString(lastKey(userID), string(body), 30*24*time.Hour)
}

func LoadLast(userID int64) (Info, bool) {
	if !cache.Enabled() || userID <= 0 {
		return Info{}, false
	}
	raw, ok, err := cache.GetString(lastKey(userID))
	if err != nil || !ok {
		return Info{}, false
	}
	var info Info
	if json.Unmarshal([]byte(raw), &info) != nil {
		return Info{}, false
	}
	return info, true
}
