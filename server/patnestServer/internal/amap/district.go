package amap

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// District 行政区划节点（高德 /v3/config/district）。
//
// Center* 是该区划的中心点（GCJ-02），用于「选了区县但没有定位」时
// 计算机构到该区县中心的距离——这样即使拒绝授权也能按「离这个区最近」排序。
type District struct {
	Name      string  `json:"name"`
	Adcode    string  `json:"adcode"`
	Level     string  `json:"level"` // province / city / district / street
	CenterLng float64 `json:"center_lng"`
	CenterLat float64 `json:"center_lat"`
}

// Districts 查询某个行政区划的下一级区划。
//
// keywords 可以是 city 级的 adcode（如 150100）或城市名（如「呼和浩特市」）。
// ⚠️ 不要直接传逆地理返回的 adcode：那是**区县**级（150105），
//
//	拿它查下级只会得到街道列表，而我们要的是区县列表。
func Districts(ctx context.Context, keywords string) ([]District, error) {
	if !Enabled() {
		return nil, ErrDisabled
	}
	keywords = strings.TrimSpace(keywords)
	if keywords == "" {
		return nil, fmt.Errorf("行政区划关键字不能为空")
	}

	q := url.Values{}
	q.Set("keywords", keywords)
	q.Set("subdistrict", "1")
	q.Set("extensions", "base")
	q.Set("output", "JSON")
	q.Set("key", apiKey)

	body, _, err := fetch(ctx, baseURL+"/v3/config/district?"+q.Encode())
	if err != nil {
		return nil, err
	}

	var parsed struct {
		Status    string `json:"status"`
		Info      string `json:"info"`
		Infocode  string `json:"infocode"`
		Districts []struct {
			Name   flexText `json:"name"`
			Adcode flexText `json:"adcode"`
			Level  flexText `json:"level"`
			Center flexText `json:"center"`
			// 子级：查询 city 时这里是区县
			Districts []struct {
				Name   flexText `json:"name"`
				Adcode flexText `json:"adcode"`
				Level  flexText `json:"level"`
				Center flexText `json:"center"`
			} `json:"districts"`
		} `json:"districts"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("解析高德行政区划响应失败: %w", err)
	}
	if parsed.Status != "1" {
		code := parsed.Infocode
		if code == "" {
			code = "unknown"
		}
		return nil, fmt.Errorf("高德接口返回错误 %s(%s)", parsed.Info, code)
	}
	if len(parsed.Districts) == 0 {
		return nil, nil
	}

	// keywords 传 adcode 时，districts[0] 是查询到的那个节点本身，
	// 它下面的 districts 才是「下一级」。查不到子级（如县级市）时返回空列表。
	children := parsed.Districts[0].Districts
	out := make([]District, 0, len(children))
	for _, c := range children {
		lng, lat := parseCenter(c.Center.s)
		out = append(out, District{
			Name:      c.Name.s,
			Adcode:    c.Adcode.s,
			Level:     c.Level.s,
			CenterLng: lng,
			CenterLat: lat,
		})
	}
	return out, nil
}

// parseCenter 把 "111.670801,40.818311" 拆成经纬度；格式不对返回 0,0（调用方按无中心点处理）。
func parseCenter(s string) (lng, lat float64) {
	parts := strings.Split(strings.TrimSpace(s), ",")
	if len(parts) != 2 {
		return 0, 0
	}
	lng, err1 := strconv.ParseFloat(strings.TrimSpace(parts[0]), 64)
	lat, err2 := strconv.ParseFloat(strings.TrimSpace(parts[1]), 64)
	if err1 != nil || err2 != nil {
		return 0, 0
	}
	return lng, lat
}
