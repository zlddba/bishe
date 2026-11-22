// Package swagger serves the generated OpenAPI document and the Swagger UI.
//
// The document is produced from internal/router/router.go by gen_swagger.py and
// embedded at compile time, so the API binary needs no generator, no swag CLI
// and no extra files at runtime. Re-generate after changing routes:
//
//	python internal/swagger/gen_swagger.py
package swagger

import (
	_ "embed"
	"net/http"
	"sync/atomic"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

//go:embed swagger.json
var spec []byte

// enabled 是运行期开关：启动时由 SWAGGER_ENABLED 初始化，
// 后台改完后立刻生效，不需要重启进程。
var enabled atomic.Bool

func init() {
	enabled.Store(true)
}

// SetEnabled 打开/关闭 Swagger UI 与 /swagger.json。
func SetEnabled(v bool) {
	enabled.Store(v)
}

// Enabled 返回当前运行期开关状态。
func Enabled() bool {
	return enabled.Load()
}

// guard 在关闭时直接返回 404，而不是把文档暴露成 401/403。
func guard(c *gin.Context) {
	if !enabled.Load() {
		c.AbortWithStatus(http.StatusNotFound)
		return
	}
	c.Next()
}

const (
	// DocPath serves the OpenAPI 2.0 document.
	DocPath = "/swagger.json"
	// UIPath serves the Swagger UI and its static assets.
	UIPath = "/swagger/*any"
	// IndexURL is the human-facing entry point.
	IndexURL = "/swagger/index.html"
)

// Register mounts the document and the UI on r. Call once during startup.
// doc.json is served from DocPath instead of gin-swagger's default relative
// "doc.json" because a /swagger/*any wildcard cannot coexist with a static
// /swagger/doc.json route in gin's router.
func Register(r *gin.Engine) {
	// 路由始终注册，由运行期 guard 决定是否返回 404。
	// 这样后台把开关打开后立即生效，不需要重新挂路由或重启。
	r.GET(DocPath, guard, func(c *gin.Context) {
		c.Header("Cache-Control", "no-cache")
		c.Data(http.StatusOK, "application/json; charset=utf-8", spec)
	})
	r.GET(UIPath, guard, ginSwagger.CustomWrapHandler(&ginSwagger.Config{
		URL:                      DocPath,
		Title:                    "PatNest API",
		DocExpansion:             "none",
		PersistAuthorization:     true,
		DefaultModelsExpandDepth: 1,
	}, swaggerFiles.Handler))
}

// Spec returns a copy of the embedded OpenAPI document.
func Spec() []byte {
	out := make([]byte, len(spec))
	copy(out, spec)
	return out
}
