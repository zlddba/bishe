package handler

import (
	"os"
	"strconv"
	"time"

	"patnestServer/internal/cache"

	"github.com/gin-gonic/gin"
)

// envPositiveInt 读取一个正整数配置，取不到或非正数时用默认值。
// 名字不绑定限流：注册/刷新/上传的阈值和审计日志保留天数都靠它做环境变量覆盖。
func envPositiveInt(key string, def int) int {
	if v, err := strconv.Atoi(os.Getenv(key)); err == nil && v > 0 {
		return v
	}
	return def
}

// ipRateLimit 给无需登录的接口做"同一 IP 每窗口最多 N 次"的粗粒度限流。
// 登录接口有它自己的失败计数与锁定逻辑，这里补上注册、刷新令牌这类
// 目前完全敞开的入口。Redis 没启用时直接放行，本地开发不受影响。
func ipRateLimit(c *gin.Context, action string, limit int, window time.Duration) bool {
	if !cache.Enabled() {
		return true
	}
	n, err := cache.IncrWindow("rl:"+action+":"+c.ClientIP(), window)
	if err != nil {
		// Redis 抖动不该让正常请求失败
		return true
	}
	return n <= int64(limit)
}

// subjectRateLimit 与 ipRateLimit 同构，但计数维度由调用方给出（通常是已登录用户 ID）。
// 上传这类需要登录的接口按 IP 限流没有意义：同一 NAT 出口下的正常用户会互相挤占配额，
// 而拿着被盗账号的人反而可以慢慢铺满。
func subjectRateLimit(c *gin.Context, action, subject string, limit int, window time.Duration) bool {
	if !cache.Enabled() {
		return true
	}
	if subject == "" {
		subject = c.ClientIP()
	}
	n, err := cache.IncrWindow("rl:"+action+":"+subject, window)
	if err != nil {
		// Redis 抖动不该让正常请求失败
		return true
	}
	return n <= int64(limit)
}
