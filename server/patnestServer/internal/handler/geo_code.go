package handler

// 地理编码接口：给没有系统定位的客户端（桌面端为主）用"城市 + 地址"换 GCJ-02 坐标。
// 高德 Web 服务 Key 只留在后端，命中 geocode_cache 就不重复请求，避免白烧配额。

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"errors"
	"log"
	"math"
	"net/http"
	"strconv"
	"time"

	"patnestServer/internal/amap"
	"patnestServer/internal/cache"
	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

// 真正打到高德配额的上限：每账号每分钟 20 次（命中缓存不计数）。
const geocodeRatePerMinute = 20

// 行政区划和道路半年内基本不变，超过这个时长就当未命中重新解析。
const geocodeCacheTTL = 180 * 24 * time.Hour

type GeocodeRequest struct {
	Address string `json:"address"`
	City    string `json:"city"`
}

// GeocodeAddress handles POST /api/v1/geo/geocode
func GeocodeAddress(c *gin.Context) {
	var req GeocodeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}

	// 同一地址的不同空格写法要能命中同一条缓存
	address := collapseText(req.Address)
	city := collapseText(req.City)
	if len([]rune(address)) < 4 {
		BadRequest(c, "地址太短，请写到区/街道，例如：赛罕区大学东街8号")
		return
	}
	if !amap.Enabled() {
		Fail(c, http.StatusNotImplemented, 50002, "服务端未配置 AMAP_KEY，暂时无法解析地址坐标")
		return
	}

	key := geocodeCacheKey(city, address)
	if hit, ok := lookupGeocode(key, geocodeCacheTTL); ok {
		OK(c, hit)
		return
	}

	if !geocodeWithinQuota(c.GetInt64("user_id")) {
		Fail(c, http.StatusTooManyRequests, 42901, "地址解析过于频繁，请一分钟后再试")
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	point, err := amap.Geocode(ctx, address, city)
	if err != nil {
		log.Printf("[Geocode] city=%q address=%q err=%v", city, address, err)
		BadRequest(c, "地址解析失败，请补充城市或写清楚区/街道: "+err.Error())
		return
	}

	out := geocodeResult(point, false)
	saveGeocode(key, city, address, point)
	OK(c, out)
}

func geocodeResult(p *amap.Point, fromCache bool) gin.H {
	return gin.H{
		"latitude":          round6(p.Latitude),
		"longitude":         round6(p.Longitude),
		"formatted_address": p.Formatted,
		"resolved_city":     p.City,
		"district":          p.District,
		"adcode":            p.Adcode,
		"level":             p.Level,
		"from_cache":        fromCache,
	}
}

// round6 与机构表里存 6 位小数保持一致，避免前端拿到 39.904200000000004 这种值。
func round6(v float64) float64 {
	return math.Round(v*1e6) / 1e6
}

func geocodeCacheKey(city, address string) string {
	sum := md5.Sum([]byte(city + "|" + address))
	return hex.EncodeToString(sum[:])
}

// lookupGeocode 读 geocode_cache；任何异常都按未命中处理，不影响正常解析。
// 地理编码（地址→坐标）与逆地理编码（坐标→地址）共用这张表，靠 ttl 区分新鲜度。
func lookupGeocode(key string, ttl time.Duration) (gin.H, bool) {
	var (
		lat, lng float64
		point    = &amap.Point{}
		updated  time.Time
	)
	err := db.Pool.QueryRow(context.Background(),
		`SELECT latitude, longitude, formatted_address, resolved_city, district, adcode, level, updated_at
		 FROM geocode_cache WHERE cache_key = $1`, key,
	).Scan(&lat, &lng, &point.Formatted, &point.City, &point.District, &point.Adcode, &point.Level, &updated)
	if err != nil {
		if !errors.Is(err, pgx.ErrNoRows) {
			log.Printf("[Geocode] cache lookup error: %v", err)
		}
		return nil, false
	}
	point.Latitude, point.Longitude = lat, lng
	if time.Since(updated) > ttl {
		return nil, false
	}
	return geocodeResult(point, true), true
}

// saveGeocode 写缓存失败只记日志，本次结果照常返回。
func saveGeocode(key, city, address string, p *amap.Point) {
	_, err := db.Pool.Exec(context.Background(),
		`INSERT INTO geocode_cache (cache_key, city, address, latitude, longitude,
		                            formatted_address, resolved_city, adcode, district, level)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		 ON CONFLICT (cache_key) DO UPDATE SET
		     latitude = EXCLUDED.latitude,
		     longitude = EXCLUDED.longitude,
		     formatted_address = EXCLUDED.formatted_address,
		     resolved_city = EXCLUDED.resolved_city,
		     adcode = EXCLUDED.adcode,
		     district = EXCLUDED.district,
		     level = EXCLUDED.level,
		     hit_count = geocode_cache.hit_count + 1,
		     updated_at = now()`,
		key, city, address, p.Latitude, p.Longitude, p.Formatted, p.City, p.Adcode, p.District, p.Level,
	)
	if err != nil {
		log.Printf("[Geocode] cache upsert error: %v", err)
	}
}

// geocodeWithinQuota 用 Redis 计数，Redis 未启用时直接放行（本地开发常态）。
func geocodeWithinQuota(userID int64) bool {
	if !cache.Enabled() {
		return true
	}
	n, err := cache.IncrWindow("geo:code:uid:"+strconv.FormatInt(userID, 10), time.Minute)
	if err != nil {
		log.Printf("[Geocode] rate limit unavailable: %v", err)
		return true
	}
	return n <= geocodeRatePerMinute
}
