package migrate

import (
	"context"
	"fmt"
	"os"

	"github.com/jackc/pgx/v5"
)

// 基线判定：只要 public.users 存在，就认为库结构已经建立过。
// 这里刻意不复用 schema_migrations：老库可能被人工初始化过而没有任何版本记录，
// 用业务表判断才符合「有表就跳过、没表才灌基线」的语义。
const baselineProbe = `SELECT to_regclass('public.users') IS NOT NULL`

// EnsureDatabase 检查 DSN 指向的库是否存在，不存在则连维护库创建。
// 返回 true 表示本次新建了数据库。
func EnsureDatabase(ctx context.Context, dsn string) (bool, error) {
	cfg, err := pgx.ParseConfig(dsn)
	if err != nil {
		return false, fmt.Errorf("解析数据库连接串失败: %w", err)
	}
	target := cfg.Database
	if target == "" {
		return false, fmt.Errorf("连接串里没有数据库名")
	}

	conn, err := connectMaintenance(ctx, cfg)
	if err != nil {
		return false, fmt.Errorf("连接维护库失败（无法自动建库）: %w", err)
	}
	defer conn.Close(context.Background())

	var exists bool
	if err := conn.QueryRow(ctx,
		`SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1)`, target).Scan(&exists); err != nil {
		return false, fmt.Errorf("检查数据库是否存在失败: %w", err)
	}
	if exists {
		return false, nil
	}

	// CREATE DATABASE 不支持参数占位符，库名必须按标识符转义后再拼接。
	stmt := "CREATE DATABASE " + pgx.Identifier{target}.Sanitize()
	if _, err := conn.Exec(ctx, stmt); err != nil {
		return false, fmt.Errorf("创建数据库 %s 失败: %w", target, err)
	}
	return true, nil
}

// connectMaintenance 依次尝试 postgres / template1，避开目标库本身。
func connectMaintenance(ctx context.Context, cfg *pgx.ConnConfig) (*pgx.Conn, error) {
	var lastErr error
	for _, name := range []string{"postgres", "template1"} {
		if name == cfg.Database {
			continue
		}
		clone := *cfg
		clone.Database = name
		conn, err := pgx.ConnectConfig(ctx, &clone)
		if err == nil {
			return conn, nil
		}
		lastErr = err
	}
	if lastErr == nil {
		lastErr = fmt.Errorf("没有可用的维护库")
	}
	return nil, lastErr
}

// BootstrapSchema 在业务表不存在时执行基线 SQL（通常是 init.sql）。
// 返回 true 表示本次执行了基线。
//
// init.sql 自身带 BEGIN/COMMIT，所以这里刻意不包外层事务：再套一层会让
// 文件内的 COMMIT 提前结束事务，后续语句就失去原子性。
func BootstrapSchema(ctx context.Context, dsn, initFile string) (bool, error) {
	if initFile == "" {
		return false, nil
	}

	body, err := os.ReadFile(initFile)
	if err != nil {
		return false, fmt.Errorf("读取基线 SQL %s 失败: %w", initFile, err)
	}
	if len(body) == 0 {
		return false, fmt.Errorf("基线 SQL %s 是空文件", initFile)
	}

	conn, err := pgx.Connect(ctx, dsn)
	if err != nil {
		return false, fmt.Errorf("连接数据库失败: %w", err)
	}
	defer conn.Close(context.Background())

	var exists bool
	if err := conn.QueryRow(ctx, baselineProbe).Scan(&exists); err != nil {
		return false, fmt.Errorf("检查基线状态失败: %w", err)
	}
	if exists {
		return false, nil
	}

	// 无参数调用走 simple query protocol，可以一次性执行多语句脚本，
	// 与 applyOne 执行迁移文件的方式保持一致。
	if _, err := conn.Exec(ctx, string(body)); err != nil {
		return false, fmt.Errorf("执行基线 SQL %s 失败: %w", initFile, err)
	}

	if err := conn.QueryRow(ctx, baselineProbe).Scan(&exists); err != nil {
		return false, fmt.Errorf("基线执行后校验失败: %w", err)
	}
	if !exists {
		return false, fmt.Errorf("基线 SQL 执行完毕但缺少 users 表，请检查 %s 的内容", initFile)
	}
	return true, nil
}
