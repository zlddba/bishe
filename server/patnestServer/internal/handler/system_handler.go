package handler

import (
	"context"
	"net/http"
	"time"

	"patnestServer/internal/cache"
	"patnestServer/internal/db"
	"patnestServer/internal/storage"
	"patnestServer/internal/version"

	"github.com/gin-gonic/gin"
)

var startedAt = time.Now()

// Version 返回当前 API 实例的版本信息，供发布校验与管理面板提示更新。
func Version(c *gin.Context) {
	info := version.Current()
	OK(c, gin.H{
		"version":          info.Version,
		"commit":           info.Commit,
		"build_time":       info.BuildTime,
		"go_version":       info.GoVersion,
		"slot":             info.Slot,
		"uptime_seconds":   int64(time.Since(startedAt).Seconds()),
		"started_at":       startedAt.Format(time.RFC3339),
		"server_time":      time.Now().Format(time.RFC3339),
		"maintenance_mode": false,
	})
}

// Health 返回可直接用于蓝绿发布健康门禁的探测结果。
func Health(c *gin.Context) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	dbOK := false
	if db.Pool != nil {
		dbOK = db.Pool.Ping(ctx) == nil
	}
	minioOK := storage.Ping() == nil
	redisOK := cache.Ping() == nil

	status := "ok"
	httpStatus := http.StatusOK
	if !dbOK {
		status = "degraded"
		httpStatus = http.StatusServiceUnavailable
	}

	c.JSON(httpStatus, gin.H{
		"status":         status,
		"service":        "patnest-api",
		"version":        version.Version,
		"commit":         version.Commit,
		"build_time":     version.BuildTime,
		"slot":           version.Slot,
		"db":             dbOK,
		"minio":          minioOK,
		"redis":          redisOK,
		"uptime_seconds": int64(time.Since(startedAt).Seconds()),
		"time":           time.Now().Format(time.RFC3339),
	})
}

type migrationState struct {
	Ready      bool   `json:"ready"`
	Applied    int    `json:"applied"`
	Latest     string `json:"latest"`
	LatestTime string `json:"latest_at"`
}

// AdminSystemInfo 汇总后端、数据库、对象存储与发布记录，供后台“系统信息”展示。
func AdminSystemInfo(c *gin.Context) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	dbVersion := ""
	if db.Pool != nil {
		_ = db.Pool.QueryRow(ctx,
			`SELECT substring(version() from 'PostgreSQL [0-9.]+')`).Scan(&dbVersion)
	}

	var state migrationState
	var hasTable bool
	_ = db.Pool.QueryRow(ctx,
		`SELECT to_regclass('public.schema_migrations') IS NOT NULL`).Scan(&hasTable)
	state.Ready = hasTable
	if hasTable {
		_ = db.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM schema_migrations`).Scan(&state.Applied)
		_ = db.Pool.QueryRow(ctx,
			`SELECT version, to_char(applied_at, 'YYYY-MM-DD HH24:MI')
			 FROM schema_migrations ORDER BY version DESC LIMIT 1`).
			Scan(&state.Latest, &state.LatestTime)
	}

	releases := make([]gin.H, 0, 5)
	var hasReleaseTable bool
	_ = db.Pool.QueryRow(ctx,
		`SELECT to_regclass('public.system_release') IS NOT NULL`).Scan(&hasReleaseTable)
	if hasReleaseTable {
		rows, err := db.Pool.Query(ctx,
			`SELECT app, version, COALESCE(commit_sha, ''), COALESCE(slot, ''),
			        COALESCE(note, ''), to_char(deployed_at, 'YYYY-MM-DD HH24:MI')
			 FROM system_release ORDER BY id DESC LIMIT 5`)
		if err == nil {
			for rows.Next() {
				var app, ver, commit, slot, note, at string
				if err := rows.Scan(&app, &ver, &commit, &slot, &note, &at); err == nil {
					releases = append(releases, gin.H{
						"app": app, "version": ver, "commit": commit,
						"slot": slot, "note": note, "deployed_at": at,
					})
				}
			}
			rows.Close()
		}
	}

	info := version.Current()
	OK(c, gin.H{
		"api": gin.H{
			"version":        info.Version,
			"commit":         info.Commit,
			"build_time":     info.BuildTime,
			"go_version":     info.GoVersion,
			"slot":           info.Slot,
			"uptime_seconds": int64(time.Since(startedAt).Seconds()),
			"started_at":     startedAt.Format(time.RFC3339),
		},
		"database": gin.H{
			"server_version": dbVersion,
			"migrations":     state,
		},
		"storage": gin.H{
			"bucket":   storage.Bucket(),
			"ready":    storage.Ping() == nil,
			"endpoint": storage.Endpoint(),
		},
		"cache": gin.H{
			"enabled": cache.Enabled(),
			"ready":   cache.Ping() == nil,
			"addr":    cache.Addr(),
		},
		"releases": releases,
	})
}
