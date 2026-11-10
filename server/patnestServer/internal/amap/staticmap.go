package amap

// 静态地图与逆地理编码：给应用内没有浏览器、也不想引入 JCEF 的客户端（桌面端弹框）用。
// 静态地图返回的是高德渲染好的 PNG，Key 仍然只留在后端。

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
)

// 高德静态地图限制：尺寸最大 1024*1024，缩放级别 1~17
const (
	StaticMapMaxSize = 1024
	StaticMapMinZoom = 3
	StaticMapMaxZoom = 17
)

// StaticMapImage 返回一张以 (lng,lat) 为中心的地图图片。
func StaticMapImage(ctx context.Context, lng, lat float64, zoom, width, height int) ([]byte, error) {
	if !Enabled() {
		return nil, ErrDisabled
	}
	if width < 60 || height < 60 || width > StaticMapMaxSize || height > StaticMapMaxSize {
		return nil, fmt.Errorf("地图尺寸超出范围（60~%d）", StaticMapMaxSize)
	}
	if zoom < StaticMapMinZoom {
		zoom = StaticMapMinZoom
	}
	if zoom > StaticMapMaxZoom {
		zoom = StaticMapMaxZoom
	}

	q := url.Values{}
	q.Set("location", coordString(lng, lat))
	q.Set("zoom", strconv.Itoa(zoom))
	q.Set("size", fmt.Sprintf("%d*%d", width, height))
	q.Set("scale", "1")
	// 不传 markers：高德对 markers 的 style/color/label 组合很挑，
	// 而桌面端弹框会在图片正中央自己画选点标记，中心点本来就是我们要的坐标
	q.Set("key", apiKey)

	body, contentType, err := fetch(ctx, baseURL+"/v3/staticmap?"+q.Encode())
	if err != nil {
		return nil, err
	}
	// 成功时是 image/png，失败时高德会回一段 JSON
	if !strings.HasPrefix(contentType, "image/") {
		return nil, amapError(body)
	}
	return body, nil
}

// Reverse 把坐标转成地址文本（GCJ-02 入，GCJ-02 出，不涉及坐标系转换）。
func Reverse(ctx context.Context, lng, lat float64) (*Point, error) {
	if !Enabled() {
		return nil, ErrDisabled
	}
	q := url.Values{}
	q.Set("location", coordString(lng, lat))
	q.Set("extensions", "all")
	q.Set("output", "JSON")
	q.Set("key", apiKey)

	body, _, err := fetch(ctx, baseURL+"/v3/geocode/regeo?"+q.Encode())
	if err != nil {
		return nil, err
	}
	var parsed struct {
		Status   string `json:"status"`
		Info     string `json:"info"`
		Infocode string `json:"infocode"`
		Regeo    struct {
			FormattedAddress flexText `json:"formatted_address"`
			AddressComponent struct {
				Province flexText `json:"province"`
				City     flexText `json:"city"`
				District flexText `json:"district"`
				// township 只有 extensions=all 才返回，是街道/乡镇一级
				Township flexText `json:"township"`
				Adcode   flexText `json:"adcode"`
			} `json:"addressComponent"`
		} `json:"regeocode"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("解析高德逆地理响应失败: %w", err)
	}
	if parsed.Status != "1" {
		code := parsed.Infocode
		if code == "" {
			code = "unknown"
		}
		return nil, fmt.Errorf("高德接口返回错误 %s(%s)", parsed.Info, code)
	}
	comp := parsed.Regeo.AddressComponent
	return &Point{
		Latitude:  lat,
		Longitude: lng,
		Formatted: parsed.Regeo.FormattedAddress.s,
		Province:  comp.Province.s,
		City:      comp.City.s,
		District:  comp.District.s,
		Township:  comp.Township.s,
		Adcode:    comp.Adcode.s,
	}, nil
}

func coordString(lng, lat float64) string {
	return strconv.FormatFloat(lng, 'f', 6, 64) + "," + strconv.FormatFloat(lat, 'f', 6, 64)
}

func fetch(ctx context.Context, rawURL string) ([]byte, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, "", err
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("请求高德接口失败: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return nil, "", fmt.Errorf("读取高德响应失败: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, resp.Header.Get("Content-Type"), fmt.Errorf("高德接口 HTTP %d: %s", resp.StatusCode, amapError(body))
	}
	return body, resp.Header.Get("Content-Type"), nil
}

// amapError 从一段失败响应里取出可读原因；解析不动就退回原文片段。
func amapError(body []byte) error {
	var parsed struct {
		Info     string `json:"info"`
		Infocode string `json:"infocode"`
	}
	if err := json.Unmarshal(body, &parsed); err == nil && parsed.Info != "" {
		return fmt.Errorf("高德接口返回错误 %s(%s)", parsed.Info, parsed.Infocode)
	}
	text := strings.TrimSpace(string(body))
	if len(text) > 120 {
		text = text[:120]
	}
	if text == "" {
		return errors.New("高德接口返回空响应")
	}
	return fmt.Errorf("高德接口返回异常: %s", text)
}
