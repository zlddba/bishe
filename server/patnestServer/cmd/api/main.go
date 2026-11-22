package main

import (
	"context"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"patnestServer/internal/amap"
	"patnestServer/internal/backendtls"
	"patnestServer/internal/cache"
	"patnestServer/internal/config"
	"patnestServer/internal/db"
	"patnestServer/internal/handler"
	"patnestServer/internal/middleware"
	"patnestServer/internal/router"
	"patnestServer/internal/storage"
	"patnestServer/internal/swagger"
	"patnestServer/internal/tlsmux"
	"patnestServer/internal/version"
)

func main() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)
	log.Println("=========================================")
	log.Println("  PatNest API Server Starting...")
	log.Println("=========================================")

	// Load configuration
	cfg := config.Load()
	version.Slot = cfg.AppSlot
	info := version.Current()
	log.Printf("[Build] version=%s commit=%s built=%s slot=%s go=%s",
		info.Version, info.Commit, info.BuildTime, info.Slot, info.GoVersion)
	log.Printf("[Config] Port=%d, DB=%s:%d/%s, MinIO=%s",
		cfg.AppPort, cfg.DBHost, cfg.DBPort, cfg.DBName, cfg.MinIOEndpoint)

	// Initialize JWT
	middleware.InitJWT(cfg.JWTSecret, time.Duration(cfg.JWTAccessTTLMinutes)*time.Minute)
	log.Printf("[Auth] access token TTL = %d minutes", cfg.JWTAccessTTLMinutes)

	// 高德 Web 服务：地址转坐标（桌面端等无系统定位的客户端用），Key 只在后端
	amap.Init(cfg.AMapKey, cfg.AMapBaseURL)
	if !amap.Enabled() {
		log.Println("[AMap] SKIPPED: AMAP_KEY not set, /geo/geocode will return 501 until configured")
	}

	// 高德 JS API：桌面端"地图选点"页面的交互地图（Key 是浏览器公开的，靠白名单约束）
	handler.InitGeoPicker(cfg.AMapJSKey, cfg.AMapJSSecurity)
	if cfg.AMapJSKey == "" {
		log.Println("[AMap] JS API key not set, desktop map picker disabled (/geo/pick returns 501)")
	}

	// --- Test Database Connection ---
	log.Println("[DB] Connecting to PostgreSQL...")
	// 发布期间数据库可能短暂切换/迁移，重试而不是立即退出，避免热更新窗口内新实例起不来
	if err := connectDBWithRetry(cfg.DBDSN(), 15, 2*time.Second); err != nil {
		log.Fatalf("[DB] FAILED after retries: %v", err)
	}
	defer db.Close()
	log.Println("[DB] Connected successfully!")

	// Quick DB query test
	var dbVersion string
	if err := db.Pool.QueryRow(context.Background(), "SELECT version()").Scan(&dbVersion); err != nil {
		log.Fatalf("[DB] Query test failed: %v", err)
	}
	log.Printf("[DB] Server: %s", dbVersion[:40])

	// --- Test MinIO Connection ---
	log.Println("[MinIO] Connecting to object storage...")
	if cfg.MinIOAccessKey != "" && cfg.MinIOSecretKey != "" {
		err := storage.InitMinIO(storage.MinIOConfig{
			Endpoint:   cfg.MinIOEndpoint,
			AccessKey:  cfg.MinIOAccessKey,
			SecretKey:  cfg.MinIOSecretKey,
			Bucket:     cfg.MinIOBucket,
			UseSSL:     cfg.MinIOUseSSL,
			PublicBase: cfg.PublicFileBase,
		})
		if err != nil {
			log.Printf("[MinIO] WARNING: %v (file upload will be unavailable)", err)
		} else {
			log.Println("[MinIO] Connected successfully!")
			log.Printf("[MinIO] Bucket: %s", cfg.MinIOBucket)
		}
	} else {
		log.Println("[MinIO] SKIPPED: MINIO_ACCESS_KEY or MINIO_SECRET_KEY not set in .env")
	}

	// --- Redis（登录限流等）---
	log.Println("[Redis] Connecting...")
	if err := cache.Init(cfg.RedisAddr, cfg.RedisPassword, cfg.RedisDB); err != nil {
		log.Printf("[Redis] WARNING: %v (login rate limit disabled)", err)
	} else if cache.Enabled() {
		log.Printf("[Redis] Connected: %s", cfg.RedisAddr)
	}
	defer cache.Close()

	// --- Swagger 运行期开关 ---
	// 默认值来自 SWAGGER_ENABLED；如果后台保存过 swagger_enabled，则以数据库为准。
	swagger.SetEnabled(cfg.SwaggerEnabled)
	var swaggerSetting string
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT value FROM platform_settings WHERE key = 'swagger_enabled'`,
	).Scan(&swaggerSetting); err == nil {
		if v, parseErr := strconv.ParseBool(swaggerSetting); parseErr == nil {
			swagger.SetEnabled(v)
		}
	}

	// --- Setup Router ---
	r := router.Setup()
	// 路由始终注册，guard 负责 404；这样后台开关可以即时生效。
	swagger.Register(r)
	if swagger.Enabled() {
		log.Printf("[Swagger] UI: %s (doc: %s)", swagger.IndexURL, swagger.DocPath)
	} else {
		log.Println("[Swagger] Disabled (set SWAGGER_ENABLED=true or enable it in admin settings)")
	}
	log.Println("[Router] All routes registered.")

	// 待支付超时自动取消（每 60 秒扫一次，超过 30 分钟未支付即取消并释放床位）
	workerCtx, stopWorker := context.WithCancel(context.Background())
	defer stopWorker()
	handler.StartOrderExpiryWorker(workerCtx, time.Minute, 30*time.Minute)
	log.Println("[Worker] Order expiry worker started (30m timeout).")
	handler.StartPushTaskWorker(workerCtx, time.Minute)
	log.Println("[Worker] Push task worker started (1m scan).")
	handler.StartAccountCancelWorker(workerCtx, time.Minute)
	log.Println("[Worker] Account cancel worker started (1m scan, 72h cooling).")
	handler.StartAuditCleanupWorker(workerCtx, 24*time.Hour)
	log.Println("[Worker] Audit log cleanup worker started (daily, 180d retention).")
	// Let's Encrypt 自动续期：每 12 小时看一次，剩余天数小于阈值才真正调用 acme.sh。
	// 手工上传的证书不归它管（见 renewACMECert 里的 LetsEncrypt 判断）。
	handler.StartACMERenewWorker(workerCtx, 12*time.Hour)
	log.Println("[Worker] ACME renew worker started (12h scan).")

	// --- Start HTTP Server ---
	addr := fmt.Sprintf(":%d", cfg.AppPort)

	// 后端 TLS：证书在后台管理页面上传，开关也在页面上切。
	// .env 的 SERVER_SCHEME=https 只决定启动时的初始状态。
	tlsMgr := backendtls.Default()
	if err := tlsMgr.Init(cfg.TLSOn()); err != nil {
		log.Fatalf("[TLS] %v", err)
	}
	log.Printf("[TLS] 后端 HTTPS: %s（证书与开关在后台「系统更新 → HTTPS 证书」里管理）",
		onOff(tlsMgr.Enabled()))

	// 同一个端口同时收 http 与 https：开关打开后客户端可以用 https:// 直连，
	// nginx 的明文反代也不受影响，不需要为了切协议重启后端。
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("[Server] 监听 %s 失败: %v", addr, err)
	}

	srv := &http.Server{
		Addr:              addr,
		Handler:           r,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       90 * time.Second,
	}

	// 蓝绿双槽位各跑一个实例：页面上的开关只会打到其中一个，
	// 这里轮询落盘的开关文件把状态同步过来。
	stopTLSWatch := make(chan struct{})
	go tlsMgr.Watch(stopTLSWatch, func(on bool) {
		log.Printf("[TLS] 后端 HTTPS 已切换为 %s", onOff(on))
	})
	defer close(stopTLSWatch)

	go func() {
		log.Println("=========================================")
		log.Printf("  PatNest API %s (%s) listening on %s", info.Version, info.Slot, addr)
		log.Printf("  Protocol: http（%s 时同时接受 https）", onOff(tlsMgr.Enabled()))
		log.Printf("  Health: http://localhost%s/health", addr)
		log.Printf("  Version: http://localhost%s/api/v1/version", addr)
		if swagger.Enabled() {
			log.Printf("  Swagger: http://localhost%s%s", addr, swagger.IndexURL)
		}
		log.Println("=========================================")

		if err := srv.Serve(tlsmux.New(ln, tlsMgr)); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Server] Failed: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("[Server] Shutting down...")

	// 给 nginx 摘流留出余量：先停止接收新连接，再等待在途请求完成
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("[Server] Forced shutdown: %v", err)
	}
	log.Println("[Server] Exited cleanly.")
}

// onOff 把布尔值转成日志里好读的中文。
func onOff(b bool) string {
	if b {
		return "开启"
	}
	return "关闭"
}

func connectDBWithRetry(dsn string, attempts int, wait time.Duration) error {
	var lastErr error
	for i := 1; i <= attempts; i++ {
		if err := db.Init(dsn); err == nil {
			return nil
		} else {
			lastErr = err
			log.Printf("[DB] attempt %d/%d failed: %v", i, attempts, err)
		}
		if i < attempts {
			time.Sleep(wait)
		}
	}
	return lastErr
}
