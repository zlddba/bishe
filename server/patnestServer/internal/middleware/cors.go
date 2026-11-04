package middleware

import (
	"net/http"
	"os"
	"strings"

	"github.com/gin-gonic/gin"
)

// corsAllowEnvKey 配置可信跨域来源，逗号分隔；填 "*" 表示放开全部来源（不建议）。
const corsAllowEnvKey = "CORS_ALLOW_ORIGINS"

// CORS 只给白名单内的来源回 CORS 头。
//
// 之前是"有 Origin 就回显 + Allow-Credentials: true"，等于任何网站都能带着用户
// 的凭据发跨域请求并读取响应。本项目用 Bearer 令牌而不是 Cookie，所以这里干脆
// 不再声明 Allow-Credentials：同源请求（后台 dist 与 /api 同域）本来就不需要 CORS，
// 桌面端和小程序的请求不带 Origin，也不会被影响。
func CORS() gin.HandlerFunc {
	allow := corsAllowList()
	allowAll := allow == nil // 显式配置 "*" 才放开

	return func(c *gin.Context) {
		origin := strings.TrimSpace(c.GetHeader("Origin"))
		if origin != "" {
			if allowAll || containsOrigin(allow, origin) {
				c.Header("Access-Control-Allow-Origin", origin)
				c.Header("Vary", "Origin")
			}
			// 不在白名单：什么都不加，浏览器自然会把响应拦下
		} else {
			// 无 Origin 的客户端（桌面端、小程序、服务端调用）保持同源可用
			c.Header("Access-Control-Allow-Origin", "*")
		}

		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Content-Length, Accept, Accept-Encoding, X-Request-Id, Authorization, X-Requested-With")
		c.Header("Access-Control-Expose-Headers", "Content-Length, Content-Type, Authorization")
		c.Header("Access-Control-Max-Age", "600")

		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}

// corsAllowList 读取白名单。返回 nil 表示配置了 "*"。
func corsAllowList() []string {
	raw := os.Getenv(corsAllowEnvKey)
	if strings.TrimSpace(raw) == "" {
		// 默认只允许本机开发时常用的两个前端端口，避免开发期一上来就跨域失败
		return []string{"http://127.0.0.1:5173", "http://localhost:5173"}
	}
	if strings.TrimSpace(raw) == "*" {
		return nil
	}
	list := make([]string, 0, 4)
	for _, item := range strings.Split(raw, ",") {
		if v := strings.TrimSpace(item); v != "" {
			list = append(list, strings.TrimRight(v, "/"))
		}
	}
	return list
}

func containsOrigin(list []string, origin string) bool {
	origin = strings.TrimRight(origin, "/")
	for _, item := range list {
		if strings.EqualFold(item, origin) {
			return true
		}
	}
	return false
}
