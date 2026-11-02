package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"patnestServer/internal/cache"
	"patnestServer/internal/config"
	"patnestServer/internal/db"
	"patnestServer/internal/handler"
	"patnestServer/internal/middleware"
	"patnestServer/internal/router"
	"patnestServer/internal/storage"
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
	middleware.InitJWT(cfg.JWTSecret)

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
			Endpoint:  cfg.MinIOEndpoint,
			AccessKey: cfg.MinIOAccessKey,
			SecretKey: cfg.MinIOSecretKey,
			Bucket:    cfg.MinIOBucket,
			UseSSL:    cfg.MinIOUseSSL,
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

	// --- Setup Router ---
	r := router.Setup()
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

	// --- Start HTTP Server ---
	addr := fmt.Sprintf(":%d", cfg.AppPort)
	srv := &http.Server{
		Addr:              addr,
		Handler:           r,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       90 * time.Second,
	}

	go func() {
		log.Println("=========================================")
		log.Printf("  PatNest API %s (%s) listening on %s", info.Version, info.Slot, addr)
		log.Printf("  Health: http://localhost%s/health", addr)
		log.Printf("  Version: http://localhost%s/api/v1/version", addr)
		log.Println("=========================================")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
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
