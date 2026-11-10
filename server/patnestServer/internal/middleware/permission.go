package middleware

import (
	"context"
	"net/http"
	"strings"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminPermission 按后台模块校验 role_permissions 中的管理员权限点。
// 内置超级管理员 admin 始终放行，避免误配置后无法恢复。
func AdminPermission() gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.GetString("username") == "admin" {
			c.Next()
			return
		}
		permission := adminPermissionFor(c.Request.Method, c.Request.URL.Path)
		if permission == "" {
			c.Next()
			return
		}
		var allowed bool
		err := db.Pool.QueryRow(context.Background(),
			"SELECT allowed FROM role_permissions WHERE role = 'admin' AND permission = $1",
			permission).Scan(&allowed)
		if err != nil {
			// 迁移尚未执行时不阻断后台，避免升级瞬间锁死
			c.Next()
			return
		}
		if !allowed {
			c.JSON(http.StatusForbidden, gin.H{"code": 20002, "message": "当前管理员未被授予该模块权限"})
			c.Abort()
			return
		}
		c.Next()
	}
}

func adminPermissionFor(method, path string) string {
	switch {
	case strings.HasPrefix(path, "/api/v1/admin/users"):
		if method == http.MethodGet {
			return "users.read"
		}
		return "users.write"
	case strings.HasPrefix(path, "/api/v1/admin/sitters"):
		return "sitters.manage"
	case strings.HasPrefix(path, "/api/v1/admin/orders"):
		return "orders.manage"
	case strings.HasPrefix(path, "/api/v1/admin/pets"):
		return "pets.verify"
	case strings.HasPrefix(path, "/api/v1/admin/dynamics"):
		return "dynamics.moderate"
	case strings.HasPrefix(path, "/api/v1/admin/reviews"):
		return "reviews.moderate"
	case strings.HasPrefix(path, "/api/v1/admin/coupons"):
		return "coupons.manage"
	case strings.HasPrefix(path, "/api/v1/admin/notification-templates"),
		strings.HasPrefix(path, "/api/v1/admin/notifications"),
		strings.HasPrefix(path, "/api/v1/admin/push-tasks"):
		return "notifications.manage"
	case strings.HasPrefix(path, "/api/v1/admin/permissions"):
		return "system.settings"
	case strings.HasPrefix(path, "/api/v1/admin/export"):
		return "audit.read"
	case strings.HasPrefix(path, "/api/v1/admin/refunds"):
		return "refunds.approve"
	case strings.HasPrefix(path, "/api/v1/admin/tickets"):
		return "tickets.handle"
	case strings.HasPrefix(path, "/api/v1/admin/settlements"):
		return "settlements.manage"
	case strings.HasPrefix(path, "/api/v1/admin/settings"):
		return "system.settings"
	case strings.HasPrefix(path, "/api/v1/admin/audit"):
		return "audit.read"
	case strings.HasPrefix(path, "/api/v1/admin/system"),
		strings.HasPrefix(path, "/api/v1/admin/updates"):
		return "system.settings"
	}
	return ""
}
