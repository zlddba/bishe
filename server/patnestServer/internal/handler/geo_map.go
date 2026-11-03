package handler

// 静态地图与逆地理编码代理：桌面端在应用内弹框里显示真高德底图，
// 但不引入浏览器控件，也不需要把任何 Key 下发到客户端。

import (
	"container/list"
	"context"
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"sync"
	"time"

	"patnestServer/internal/amap"
	"patnestServer/internal/cache"

	"github.com/gin-gonic/gin"
)

const (
	staticMapRatePerMinute = 120 // 拖动/缩放会连续请求，这里比地理编码宽松
	staticMapTTL           = 10 * time.Minute
	staticMapMaxBytes      = 24 << 20 // 24MB 内存上限，超了按最久未用淘汰

	// 瓦片比整张静态图碎得多、也几乎不变化：限得更宽，缓存得更久
	tileRatePerMinute = 600
	tileTTL           = 24 * time.Hour
)

// 逆地理缓存有效期：同一次选点会连续问几次相邻坐标，这里主要防重复请求；
// 比地理编码短，因为点是用户随手点的，区划/门牌变动后不必等半年才刷新
const reverseCacheTTL = 30 * 24 * time.Hour

type mapCacheEntry struct {
	key    string
	body   []byte
	expire time.Time
}

var (
	mapCacheMu    sync.Mutex
	mapCache      = map[string]*list.Element{}
	mapCacheLRU   = list.New()
	mapCacheBytes int
)

// GeoStaticMap handles GET /api/v1/geo/static-map
func GeoStaticMap(c *gin.Context) {
	lat, err := strconv.ParseFloat(c.Query("latitude"), 64)
	if err != nil {
		BadRequest(c, "latitude 无效")
		return
	}
	lng, err := strconv.ParseFloat(c.Query("longitude"), 64)
	if err != nil {
		BadRequest(c, "longitude 无效")
		return
	}
	zoom, _ := strconv.Atoi(c.DefaultQuery("zoom", "15"))
	width, _ := strconv.Atoi(c.DefaultQuery("width", "640"))
	height, _ := strconv.Atoi(c.DefaultQuery("height", "420"))
	// 尺寸/缩放统一夹到高德允许的区间，避免客户端算错像素就把 500 抛回去
	zoom = clampZoom(zoom)
	width = clampMapSize(width)
	height = clampMapSize(height)

	if lat < -90 || lat > 90 || lng < -180 || lng > 180 {
		BadRequest(c, "坐标超出范围")
		return
	}
	if !amap.Enabled() {
		Fail(c, http.StatusNotImplemented, 50002, "服务端未配置 AMAP_KEY，无法出地图图片")
		return
	}
	if !imageWithinQuota("map", c.GetInt64("user_id"), staticMapRatePerMinute) {
		Fail(c, http.StatusTooManyRequests, 42901, "地图请求过于频繁，请放慢拖动")
		return
	}

	// 坐标量化到 5 位小数（约 1 米），让同一位置的重复请求命中缓存
	key := fmt.Sprintf("map|%.5f|%.5f|%d|%dx%d", lat, lng, zoom, width, height)
	if body, ok := mapCacheGet(key); ok {
		c.Data(http.StatusOK, "image/png", body)
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), 8*time.Second)
	defer cancel()

	body, err := amap.StaticMapImage(ctx, lng, lat, zoom, width, height)
	if err != nil {
		log.Printf("[GeoMap] static map error: %v (lat=%.6f lng=%.6f zoom=%d size=%dx%d)", err, lat, lng, zoom, width, height)
		InternalError(c, "获取地图失败: "+err.Error())
		return
	}
	mapCachePut(key, body, staticMapTTL)
	c.Data(http.StatusOK, "image/png", body)
}

// GeoTile handles GET /api/v1/geo/tile —— 桌面端地图弹框的连续底图。
// 客户端只在露出新格子时请求，所以这里以缓存命中为主。
func GeoTile(c *gin.Context) {
	z, err := strconv.Atoi(c.Query("z"))
	if err != nil {
		BadRequest(c, "z 无效")
		return
	}
	x, err := strconv.Atoi(c.Query("x"))
	if err != nil {
		BadRequest(c, "x 无效")
		return
	}
	y, err := strconv.Atoi(c.Query("y"))
	if err != nil {
		BadRequest(c, "y 无效")
		return
	}
	if !imageWithinQuota("tile", c.GetInt64("user_id"), tileRatePerMinute) {
		Fail(c, http.StatusTooManyRequests, 42901, "地图瓦片请求过于频繁")
		return
	}

	key := fmt.Sprintf("tile|%d|%d|%d", z, x, y)
	if body, ok := mapCacheGet(key); ok {
		c.Data(http.StatusOK, "image/png", body)
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), 6*time.Second)
	defer cancel()

	body, err := amap.Tile(ctx, z, x, y)
	if err != nil {
		log.Printf("[GeoMap] tile z=%d x=%d y=%d err=%v", z, x, y, err)
		InternalError(c, "获取瓦片失败: "+err.Error())
		return
	}
	mapCachePut(key, body, tileTTL)
	// 瓦片内容不随用户变化，允许浏览器与中间层缓存一天
	c.Header("Cache-Control", "public, max-age=86400")
	c.Data(http.StatusOK, "image/png", body)
}

type reverseRequest struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

// ReverseGeocode handles POST /api/v1/geo/reverse
func ReverseGeocode(c *gin.Context) {
	var req reverseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}
	if req.Latitude < -90 || req.Latitude > 90 || req.Longitude < -180 || req.Longitude > 180 {
		BadRequest(c, "坐标超出范围")
		return
	}
	if !amap.Enabled() {
		Fail(c, http.StatusNotImplemented, 50002, "服务端未配置 AMAP_KEY，无法解析坐标")
		return
	}

	key := reverseCacheKey(req.Longitude, req.Latitude)
	if hit, ok := lookupGeocode(key, reverseCacheTTL); ok {
		OK(c, hit)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	point, err := amap.Reverse(ctx, req.Longitude, req.Latitude)
	if err != nil {
		log.Printf("[GeoMap] reverse error: %v", err)
		BadRequest(c, "坐标解析地址失败: "+err.Error())
		return
	}
	// 与地理编码共用一张缓存表，用 regeo| 前缀区分方向，省掉一次迁移
	saveGeocode(key, point.City, fmt.Sprintf("%.5f,%.5f", req.Longitude, req.Latitude), point)
	OK(c, geocodeResult(point, false))
}

func clampZoom(z int) int {
	if z < amap.StaticMapMinZoom {
		return amap.StaticMapMinZoom
	}
	if z > amap.StaticMapMaxZoom {
		return amap.StaticMapMaxZoom
	}
	return z
}

// clampMapSize 把边长夹进 [60, 1024]，与高德静态地图的尺寸限制一致
func clampMapSize(v int) int {
	if v < 60 {
		return 60
	}
	if v > amap.StaticMapMaxSize {
		return amap.StaticMapMaxSize
	}
	return v
}

func reverseCacheKey(lng, lat float64) string {
	raw := fmt.Sprintf("regeo|%.5f|%.5f", lng, lat)
	sum := md5.Sum([]byte(raw))
	return hex.EncodeToString(sum[:])
}

// imageWithinQuota 给地图类接口做每账号每分钟计数；Redis 未启用时直接放行。
func imageWithinQuota(kind string, userID int64, limit int) bool {
	if !cache.Enabled() {
		return true
	}
	n, err := cache.IncrWindow("geo:"+kind+":uid:"+strconv.FormatInt(userID, 10), time.Minute)
	if err != nil {
		log.Printf("[GeoMap] rate limit unavailable: %v", err)
		return true
	}
	return n <= int64(limit)
}

func mapCacheGet(key string) ([]byte, bool) {
	mapCacheMu.Lock()
	defer mapCacheMu.Unlock()

	el, ok := mapCache[key]
	if !ok {
		return nil, false
	}
	entry := el.Value.(*mapCacheEntry)
	if time.Now().After(entry.expire) {
		mapCacheLRU.Remove(el)
		delete(mapCache, key)
		mapCacheBytes -= len(entry.body)
		return nil, false
	}
	mapCacheLRU.MoveToFront(el)
	return entry.body, true
}

func mapCachePut(key string, body []byte, ttl time.Duration) {
	mapCacheMu.Lock()
	defer mapCacheMu.Unlock()

	if el, ok := mapCache[key]; ok {
		old := el.Value.(*mapCacheEntry)
		mapCacheBytes -= len(old.body)
		mapCacheLRU.Remove(el)
		delete(mapCache, key)
	}

	entry := &mapCacheEntry{key: key, body: body, expire: time.Now().Add(ttl)}
	mapCache[key] = mapCacheLRU.PushFront(entry)
	mapCacheBytes += len(body)

	// 超预算就从最久未用的一端丢，直到回到上限内
	for mapCacheBytes > staticMapMaxBytes {
		back := mapCacheLRU.Back()
		if back == nil {
			return
		}
		old := back.Value.(*mapCacheEntry)
		mapCacheLRU.Remove(back)
		delete(mapCache, old.key)
		mapCacheBytes -= len(old.body)
	}
}
