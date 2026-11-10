// Package amap 封装高德 Web 服务 API。Key 只留在后端进程，绝不下发给客户端。
// 目前只用地理编码（地址 → GCJ-02 坐标），POI/距离计算按需再往这里加。
package amap

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// ErrDisabled 表示服务端没配 AMAP_KEY，调用方应给出可操作的提示而不是 500。
var ErrDisabled = errors.New("AMAP_KEY 未配置")

var (
	apiKey     string
	baseURL    string
	httpClient = &http.Client{Timeout: 6 * time.Second}
)

// Point 是一次地理编码的结果，坐标为 GCJ-02（与库里机构坐标同一基准）。
type Point struct {
	Latitude  float64
	Longitude float64
	Formatted string
	// Province 省/自治区/直辖市，用于「选择地区」页把默认选中项定位到省一级
	Province string
	City     string
	District string
	// Township 街道/乡镇，高德逆地理在 extensions=all 时返回；
	// 直辖市或部分开发区可能为空，此时用 District 兜底。
	Township string
	Adcode   string
	Level    string
}

func Init(key, base string) {
	apiKey = strings.TrimSpace(key)
	base = strings.TrimRight(strings.TrimSpace(base), "/")
	if base == "" {
		base = "https://restapi.amap.com"
	}
	baseURL = base
}

func Enabled() bool { return apiKey != "" }

// flexText 处理高德的一个老毛病：无值的字段返回空数组 []，而不是空字符串。
type flexText struct{ s string }

func (f *flexText) UnmarshalJSON(b []byte) error {
	trimmed := strings.TrimSpace(string(b))
	if trimmed == "" || trimmed == "null" || trimmed == "[]" || trimmed == `""` {
		f.s = ""
		return nil
	}
	var v string
	if err := json.Unmarshal(b, &v); err == nil {
		f.s = v
	}
	// 数字等其它形态直接忽略，不因为副字段格式变化让整个解析失败
	return nil
}

type geoResponse struct {
	Status   string `json:"status"`
	Info     string `json:"info"`
	Infocode string `json:"infocode"`
	Geocodes []struct {
		FormattedAddress flexText `json:"formatted_address"`
		City             flexText `json:"city"`
		District         flexText `json:"district"`
		Adcode           flexText `json:"adcode"`
		Location         flexText `json:"location"`
		Level            flexText `json:"level"`
	} `json:"geocodes"`
}

// Geocode 把"城市 + 详细地址"解析成坐标。address 建议写到区/街道/门牌，
// city 可空（空时由高德按全国范围猜测）。
func Geocode(ctx context.Context, address, city string) (*Point, error) {
	if !Enabled() {
		return nil, ErrDisabled
	}
	address = strings.TrimSpace(address)
	city = strings.TrimSpace(city)
	if address == "" {
		return nil, errors.New("地址为空")
	}

	q := url.Values{}
	q.Set("address", address)
	if city != "" {
		q.Set("city", city)
	}
	q.Set("output", "JSON")
	q.Set("key", apiKey)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, baseURL+"/v3/geocode/geo?"+q.Encode(), nil)
	if err != nil {
		return nil, err
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("请求高德地理编码失败: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, fmt.Errorf("读取高德响应失败: %w", err)
	}
	var parsed geoResponse
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("解析高德响应失败: %w", err)
	}
	if parsed.Status != "1" {
		code := parsed.Infocode
		if code == "" {
			code = "unknown"
		}
		return nil, fmt.Errorf("高德接口返回错误 %s(%s)", parsed.Info, code)
	}
	if len(parsed.Geocodes) == 0 {
		return nil, errors.New("没有解析到该地址，请补上城市或写清楚区/街道")
	}

	first := parsed.Geocodes[0]
	lng, lat, err := parseLocation(first.Location.s)
	if err != nil {
		return nil, err
	}
	return &Point{
		Latitude:  lat,
		Longitude: lng,
		Formatted: first.FormattedAddress.s,
		City:      first.City.s,
		District:  first.District.s,
		Adcode:    first.Adcode.s,
		Level:     first.Level.s,
	}, nil
}

// parseLocation 解析高德 "经度,纬度" 字符串，注意顺序是经度在前。
func parseLocation(s string) (lng, lat float64, err error) {
	parts := strings.Split(strings.TrimSpace(s), ",")
	if len(parts) != 2 {
		return 0, 0, fmt.Errorf("高德返回的坐标格式异常: %q", s)
	}
	lng, err = strconv.ParseFloat(strings.TrimSpace(parts[0]), 64)
	if err != nil {
		return 0, 0, fmt.Errorf("高德返回的经度异常: %q", parts[0])
	}
	lat, err = strconv.ParseFloat(strings.TrimSpace(parts[1]), 64)
	if err != nil {
		return 0, 0, fmt.Errorf("高德返回的纬度异常: %q", parts[1])
	}
	if lng < -180 || lng > 180 || lat < -90 || lat > 90 || (lng == 0 && lat == 0) {
		return 0, 0, fmt.Errorf("高德返回的坐标不在有效范围: %q", s)
	}
	return lng, lat, nil
}
