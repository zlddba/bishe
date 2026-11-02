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
}
