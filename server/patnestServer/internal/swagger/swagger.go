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

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

//go:embed swagger.json
var spec []byte

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
	r.GET(DocPath, func(c *gin.Context) {
		c.Header("Cache-Control", "no-cache")
		c.Data(http.StatusOK, "application/json; charset=utf-8", spec)
	})
	r.GET(UIPath, ginSwagger.CustomWrapHandler(&ginSwagger.Config{
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
