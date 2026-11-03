package handler

// 桌面端地图选点：Compose Desktop 没有内嵌浏览器，也不值得为选点引入 JCEF 这种重量级依赖。
// 这里改发一张一次性的选点页：桌面端拿到链接后调用系统浏览器打开，
// 用户在页面里选点，页面把坐标交回后端，桌面端轮询取结果。
// 链接里只有 128 位随机令牌（库里存哈希），5 分钟过期、取走即删。

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"html/template"
	"log"
	"net/http"
	"strings"
	"time"

	"patnestServer/internal/cache"
	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

const (
	geoPickTTL        = 5 * time.Minute
	geoPickRateWindow = time.Minute
	geoPickRateMax    = 6 // 每分钟最多发起 6 次选点，避免刷表
)

var (
	geoJSKey      string
	geoJSSecurity string
	pickerTmpl    = template.Must(template.New("picker").Parse(geoPickerHTML))
)

// InitGeoPicker 注入高德 JS API Key。没配 Key 时创建会话会直接给出可操作提示。
func InitGeoPicker(jsKey, jsSecurity string) {
	geoJSKey = strings.TrimSpace(jsKey)
	geoJSSecurity = strings.TrimSpace(jsSecurity)
}

type createPickRequest struct {
	Latitude  *float64 `json:"latitude"`
	Longitude *float64 `json:"longitude"`
	PlaceName string   `json:"place_name"`
}

// CreateGeoPick handles POST /api/v1/geo/pick  （登录用户，桌面端发起）
func CreateGeoPick(c *gin.Context) {
	if geoJSKey == "" {
		Fail(c, http.StatusNotImplemented, 50002, "服务端未配置 AMAP_JS_KEY，桌面端地图选点暂不可用")
		return
	}
	userID := c.GetInt64("user_id")

	if cache.Enabled() {
		n, err := cache.IncrWindow(fmt.Sprintf("geo:pick:uid:%d", userID), geoPickRateWindow)
		if err == nil && n > geoPickRateMax {
			Fail(c, http.StatusTooManyRequests, 42901, "选点请求过于频繁，请稍后再试")
			return
		}
	}

	var req createPickRequest
	_ = c.ShouldBindJSON(&req) // 全部字段可选：没有已存坐标时从默认城市开始

	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		InternalError(c, "生成选点链接失败")
		return
	}
	token := hex.EncodeToString(buf)

	initLat, initLng := 0.0, 0.0
	if req.Latitude != nil && req.Longitude != nil {
		initLat, initLng = *req.Latitude, *req.Longitude
	}
	name := collapseText(req.PlaceName)
	if len([]rune(name)) > 48 {
		name = string([]rune(name)[:48])
	}

	if _, err := db.Pool.Exec(context.Background(),
		`INSERT INTO geo_pick_sessions (token_hash, user_id, initial_lat, initial_lng, place_name, expires_at)
		 VALUES ($1, $2, $3, $4, $5, now() + $6::interval)`,
		hashPickToken(token), userID, initLat, initLng, name, fmt.Sprintf("%d seconds", int(geoPickTTL.Seconds())),
	); err != nil {
		log.Printf("[GeoPick] create error: %v", err)
		InternalError(c, "创建选点会话失败")
		return
	}
	// 顺手回收过期会话，不额外起定时任务
	_, _ = db.Pool.Exec(context.Background(), `DELETE FROM geo_pick_sessions WHERE expires_at < now()`)

	// path 是相对接口根路径的，桌面端拿它拼自己的 apiBase，避免写死主机端口
	OK(c, gin.H{"token": token, "path": "/geo/pick/" + token, "expires_in": int(geoPickTTL.Seconds())})
}

// GeoPickerPage handles GET /api/v1/geo/pick/:token  （浏览器打开，无需登录，靠一次性令牌）
func GeoPickerPage(c *gin.Context) {
	token := strings.TrimSpace(c.Param("token"))
	if !validPickToken(token) {
		c.String(http.StatusBadRequest, "选点链接无效")
		return
	}

	var (
		initLat, initLng float64
		name             string
		picked           bool
	)
	err := db.Pool.QueryRow(context.Background(),
		`SELECT initial_lat, initial_lng, place_name, picked_at IS NOT NULL
		 FROM geo_pick_sessions WHERE token_hash = $1 AND expires_at > now()`, hashPickToken(token),
	).Scan(&initLat, &initLng, &name, &picked)
	if err != nil {
		c.String(http.StatusGone, "选点链接已过期，请回到桌面端重新发起")
		return
	}

	page := struct {
		Token       string
		JSKey       string
		JSSecurity  string
		InitialLat  float64
		InitialLng  float64
		PlaceName   string
		AlreadyDone bool
	}{token, geoJSKey, geoJSSecurity, initLat, initLng, name, picked}

	c.Header("Content-Type", "text/html; charset=utf-8")
	// 选点结果是一次性数据，别让浏览器缓存住旧页面
	c.Header("Cache-Control", "no-store")
	if err := pickerTmpl.Execute(c.Writer, page); err != nil {
		log.Printf("[GeoPick] render page error: %v", err)
	}
}

type submitPickRequest struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Address   string  `json:"address"`
}

// SubmitGeoPick handles POST /api/v1/geo/pick/:token  （选点页回写结果）
func SubmitGeoPick(c *gin.Context) {
	token := strings.TrimSpace(c.Param("token"))
	if !validPickToken(token) {
		BadRequest(c, "选点链接无效")
		return
	}

	var req submitPickRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}
	if req.Latitude < -90 || req.Latitude > 90 || req.Longitude < -180 || req.Longitude > 180 {
		BadRequest(c, "坐标超出范围")
		return
	}

	address := collapseText(req.Address)
	if len([]rune(address)) > 200 {
		address = string([]rune(address)[:200])
	}

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE geo_pick_sessions
		 SET latitude = $2, longitude = $3, address = $4, picked_at = now()
		 WHERE token_hash = $1 AND picked_at IS NULL AND expires_at > now()`,
		hashPickToken(token), req.Latitude, req.Longitude, address,
	)
	if err != nil {
		log.Printf("[GeoPick] submit error: %v", err)
		InternalError(c, "提交选点结果失败")
		return
	}
	if tag.RowsAffected() == 0 {
		Fail(c, http.StatusGone, 30001, "选点链接已过期或已使用")
		return
	}
	OK(c, gin.H{"message": "已收到，可关闭页面回到桌面端"})
}

// PollGeoPick handles GET /api/v1/geo/pick-result/:token  （桌面端轮询，取走即销毁）
func PollGeoPick(c *gin.Context) {
	token := strings.TrimSpace(c.Param("token"))
	userID := c.GetInt64("user_id")
	if !validPickToken(token) {
		BadRequest(c, "选点链接无效")
		return
	}

	var (
		lat, lng  float64
		address   string
		pickedAt  *time.Time
		expiresAt time.Time
	)
	err := db.Pool.QueryRow(context.Background(),
		`SELECT COALESCE(latitude, 0), COALESCE(longitude, 0), address, picked_at, expires_at
		 FROM geo_pick_sessions WHERE token_hash = $1 AND user_id = $2`, hashPickToken(token), userID,
	).Scan(&lat, &lng, &address, &pickedAt, &expiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		OK(c, gin.H{"status": "expired"})
		return
	}
	if err != nil {
		log.Printf("[GeoPick] poll error: %v", err)
		InternalError(c, "读取选点结果失败")
		return
	}

	if pickedAt == nil {
		if time.Now().After(expiresAt) {
			_, _ = db.Pool.Exec(context.Background(), `DELETE FROM geo_pick_sessions WHERE token_hash = $1`, hashPickToken(token))
			OK(c, gin.H{"status": "expired"})
			return
		}
		OK(c, gin.H{"status": "pending"})
		return
	}

	// 一次性：取到结果就删掉会话
	_, _ = db.Pool.Exec(context.Background(), `DELETE FROM geo_pick_sessions WHERE token_hash = $1`, hashPickToken(token))
	OK(c, gin.H{
		"status":            "done",
		"latitude":          round6(lat),
		"longitude":         round6(lng),
		"formatted_address": address,
	})
}

func validPickToken(token string) bool {
	if len(token) != 32 {
		return false
	}
	// 令牌是 16 字节的十六进制串，逐字符判断；不能用 ParseUint（32 个十六进制位会溢出 uint64）
	for _, r := range token {
		isDigit := r >= '0' && r <= '9'
		isHexLetter := (r >= 'a' && r <= 'f') || (r >= 'A' && r <= 'F')
		if !isDigit && !isHexLetter {
			return false
		}
	}
	return true
}

func hashPickToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func collapseText(s string) string {
	return strings.Join(strings.Fields(s), " ")
}
