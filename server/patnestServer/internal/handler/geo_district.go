package handler

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strings"
	"time"

	"patnestServer/internal/amap"

	"github.com/gin-gonic/gin"
)

// GeoDistricts handles GET /api/v1/geo/districts?city=呼和浩特市
// 也接受 ?adcode=150100（市级编码）。
//
// 「选择位置」页用：列出当前城市下辖的区县。
// 每个区县带中心点坐标，前端据此做到「即使不给定位，选了区县也能按
// 离这个区县最近排序」——不然用户拒绝授权就只剩按评分排。
//
// 行政区划几乎不变，结果在内存里缓存一天，顺带省高德配额。
func GeoDistricts(c *gin.Context) {
	city := strings.TrimSpace(c.Query("city"))
	adcode := strings.TrimSpace(c.Query("adcode"))

	// ⚠️ 逆地理返回的 adcode 是区县级（150105），直接查下级只会得到街道；
	// 这里归到市级（前 4 位 + 00）才是「区县列表」。
	var keywords, cacheKey string
	switch {
	case city != "":
		keywords = city
		cacheKey = "district|city|" + city
	case len(adcode) == 6:
		keywords = adcode[:4] + "00"
		cacheKey = "district|adcode|" + keywords
	default:
		BadRequest(c, "需要 city（城市名）或 6 位 adcode")
		return
	}

	if !geoPublicWithinQuota(c, "districts", districtRatePerMinute) {
		Fail(c, http.StatusTooManyRequests, 42901, "查询过于频繁，请一分钟后再试")
		return
	}
	if !amap.Enabled() {
		Fail(c, http.StatusNotImplemented, 50002, "服务端未配置 AMAP_KEY，无法查询行政区划")
		return
	}

	if body, ok := mapCacheGet(cacheKey); ok {
		c.Data(http.StatusOK, "application/json; charset=utf-8", body)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()

	list, err := amap.Districts(ctx, keywords)
	if err != nil {
		log.Printf("[GeoMap] districts keywords=%s err=%v", keywords, err)
		BadRequest(c, "查询行政区划失败: "+err.Error())
		return
	}

	body, err := json.Marshal(Response{
		Code:    0,
		Message: "ok",
		Data:    gin.H{"keywords": keywords, "items": list},
	})
	if err != nil {
		InternalError(c, "序列化行政区划失败")
		return
	}
	mapCachePut(cacheKey, body, 24*time.Hour)
	c.Data(http.StatusOK, "application/json; charset=utf-8", body)
}
