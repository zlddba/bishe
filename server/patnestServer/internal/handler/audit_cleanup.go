package handler

import (
	"context"
	"log"
	"time"

	"patnestServer/internal/db"
)

// auditRetention 审计日志保留时长。
// docs/02 写的是"定期归档清理 6 个月前旧记录"，这里把它落成实际执行：
// 演示期数据量小，清理频率按天即可；阈值可用 AUDIT_RETENTION_DAYS 覆盖。
func auditRetention() time.Duration {
	days := envPositiveInt("AUDIT_RETENTION_DAYS", 180)
	return time.Duration(days) * 24 * time.Hour
}

// StartAuditCleanupWorker 每天清理一次超过保留期的审计日志。
// 启动时先跑一次，避免长期未重启的实例积压。
func StartAuditCleanupWorker(ctx context.Context, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		// 启动后延迟 1 分钟再执行，避开启动瞬间的数据库压力
		select {
		case <-ctx.Done():
			return
		case <-time.After(time.Minute):
		}
		cleanupAuditLogs(ctx, auditRetention())
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				cleanupAuditLogs(ctx, auditRetention())
			}
		}
	}()
}

// cleanupAuditLogs 分批删除过期审计日志，避免一次大 DELETE 长时间持锁。
func cleanupAuditLogs(ctx context.Context, retention time.Duration) {
	const batch = 5000
	cutoff := time.Now().Add(-retention)
	var total int64
	for {
		tag, err := db.Pool.Exec(ctx,
			`DELETE FROM audit_logs
			 WHERE id IN (
			     SELECT id FROM audit_logs WHERE created_at < $1 ORDER BY id LIMIT $2
			 )`, cutoff, batch)
		if err != nil {
			log.Printf("[audit-cleanup] delete failed: %v", err)
			return
		}
		n := tag.RowsAffected()
		total += n
		if n < batch {
			break
		}
		select {
		case <-ctx.Done():
			return
		default:
		}
	}
	if total > 0 {
		log.Printf("[audit-cleanup] removed %d audit logs older than %s", total, cutoff.Format(time.RFC3339))
	}
}
