package router

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

// TestSetupRoutes 确保所有路由能注册成功（gin 遇到冲突路由会 panic）。
func TestSetupRoutes(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := Setup()
	if r == nil {
		t.Fatal("router is nil")
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/v1/pet-owner/favorites", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("protected route without token = %d, want 401", w.Code)
	}

	// 逆地理编码是公开接口：首页顶部地名在游客（未登录）浏览时也要显示。
	// 不带 body 应当返回参数错误，而不是 401/404。
	w = httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/api/v1/geo/reverse", nil))
	if w.Code == http.StatusUnauthorized || w.Code == http.StatusNotFound {
		t.Errorf("POST /api/v1/geo/reverse without token = %d, 期望公开可访问（4xx 参数错误）", w.Code)
	}

	// 行政区划查询同为公开接口（选择位置页要列区县）
	w = httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/geo/districts?adcode=150100", nil))
	if w.Code == http.StatusUnauthorized || w.Code == http.StatusNotFound {
		t.Errorf("GET /api/v1/geo/districts without token = %d, 期望公开可访问", w.Code)
	}

	// 同为 geo 模块的 geocode 仍需要登录，避免顺手把整套都放开
	w = httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/api/v1/geo/geocode", nil))
	if w.Code != http.StatusUnauthorized {
		t.Errorf("POST /api/v1/geo/geocode without token = %d, want 401", w.Code)
	}
}
