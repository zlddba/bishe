package handler

import (
	"context"
	"strconv"

	"patnestServer/internal/db"
	"patnestServer/internal/swagger"

	"github.com/gin-gonic/gin"
)

const swaggerSettingKey = "swagger_enabled"

// AdminGetSwaggerSetting handles GET /api/v1/admin/settings/swagger
func AdminGetSwaggerSetting(c *gin.Context) {
	OK(c, gin.H{"enabled": swagger.Enabled()})
}

// AdminUpdateSwaggerSetting handles PUT /api/v1/admin/settings/swagger
// 保存到 platform_settings，并立即更新运行期开关，不需要重启。
func AdminUpdateSwaggerSetting(c *gin.Context) {
	var in struct {
		Enabled *bool `json:"enabled" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil || in.Enabled == nil {
		BadRequest(c, "enabled 必须是 true 或 false")
		return
	}

	adminID := c.GetInt64("user_id")
	value := strconv.FormatBool(*in.Enabled)
	_, err := db.Pool.Exec(context.Background(), `
		INSERT INTO platform_settings (key, value, updated_at, updated_by)
		VALUES ($1, $2, now(), NULLIF($3, 0))
		ON CONFLICT (key) DO UPDATE
		SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by`,
		swaggerSettingKey, value, adminID)
	if err != nil {
		InternalError(c, "保存 Swagger 开关失败: "+err.Error())
		return
	}

	swagger.SetEnabled(*in.Enabled)
	writeAudit(c, "UPDATE_SWAGGER", "setting", 0, `{"enabled": `+value+`}`)
	OK(c, gin.H{"enabled": swagger.Enabled()})
}
