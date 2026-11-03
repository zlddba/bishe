package amap

// 在线瓦片：桌面端地图弹框用它做连续拖动。
//
// 说明一下取舍：webrd*.is.autonavi.com 是高德网页版在用的公开瓦片域名，
// 它不吃 Web服务 Key 的配额，但也没有在开放平台协议里明确授权第三方直接调用。
// 因此这里只作为毕设演示的本地渲染方案；正式对外要换成开放平台的 JS API（见 docs/10 §6），
// 那时把 TileURL 换成官方地址即可，客户端的瓦片网格逻辑不用动。

import (
	"context"
	"fmt"
	"strings"
)

// 瓦片边长固定 256 像素，级别 3~18（与高德网页版一致）
const (
	TileEdge    = 256
	TileMinZoom = 3
	TileMaxZoom = 18
)

// 四个 CDN 轮着用，避免单域名并发被限
var tileHosts = []string{
	"webrd01.is.autonavi.com",
	"webrd02.is.autonavi.com",
	"webrd03.is.autonavi.com",
	"webrd04.is.autonavi.com",
}

// Tile 拉取一张 XYZ 瓦片，返回 PNG 字节。
func Tile(ctx context.Context, z, x, y int) ([]byte, error) {
	if z < TileMinZoom || z > TileMaxZoom {
		return nil, fmt.Errorf("缩放级别超出范围（%d~%d）", TileMinZoom, TileMaxZoom)
	}
	max := 1 << uint(z)
	if x < 0 || x >= max || y < 0 || y >= max {
		return nil, fmt.Errorf("瓦片坐标超出范围（z=%d 时 x/y 应在 0~%d）", z, max-1)
	}

	host := tileHosts[(x+y)%len(tileHosts)]
	raw := fmt.Sprintf(
		"https://%s/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=%d&y=%d&z=%d",
		host, x, y, z,
	)

	body, contentType, err := fetch(ctx, raw)
	if err != nil {
		return nil, err
	}
	// 正常是 image/png；被限流或拒绝时通常会回一段 HTML/JSON
	if !strings.HasPrefix(contentType, "image/") && !looksLikePng(body) {
		return nil, amapError(body)
	}
	return body, nil
}

// looksLikePng 按文件头判断，因为部分 CDN 回的 Content-Type 并不总是 image/png
func looksLikePng(b []byte) bool {
	return len(b) > 8 && b[0] == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
}
