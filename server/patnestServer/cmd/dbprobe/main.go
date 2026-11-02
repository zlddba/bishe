package main

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"patnestServer/internal/config"
)

// 临时诊断工具：定位"注册提示创建用户失败"的真实原因。
// 默认只读 + 事务内冲突测试（自动回滚，不落库）；-apply 执行 0005 迁移后复测。
func main() {
	apply := false
	for _, a := range os.Args[1:] {
		if a == "-apply" {
			apply = true
		}
	}

	cfg := config.Load()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	conn, err := pgx.Connect(ctx, cfg.DBDSN())
	if err != nil {
		fmt.Println("[FAIL] 连接数据库失败:", err)
		os.Exit(1)
	}
	defer conn.Close(ctx)
	fmt.Printf("[OK] 已连接 %s/%s\n", cfg.DBHost, cfg.DBName)

	// 1. 迁移状态
	fmt.Println("\n== schema_migrations ==")
	rows, err := conn.Query(ctx, "SELECT version, name FROM schema_migrations ORDER BY version")
	if err != nil {
		fmt.Println("[WARN] 读取失败:", err)
	} else {
		for rows.Next() {
			var v, n string
			if err := rows.Scan(&v, &n); err == nil {
				fmt.Printf("  %s  %s\n", v, n)
			}
		}
		rows.Close()
	}

	// 2. users 表唯一约束
	fmt.Println("\n== users 表唯一约束 ==")
	crows, err := conn.Query(ctx,
		"SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'u'")
	if err != nil {
		fmt.Println("[WARN] 查询失败:", err)
	} else {
		any := false
		for crows.Next() {
			var name, def string
			if err := crows.Scan(&name, &def); err == nil {
				fmt.Printf("  %s: %s\n", name, def)
				any = true
			}
		}
		crows.Close()
		if !any {
			fmt.Println("  （无唯一约束）")
		}
	}

	// 3. 软删除用户（潜在的用户名占用者）
	fmt.Println("\n== 软删除用户（deleted_at 非空）==")
	drows, err := conn.Query(ctx,
		"SELECT id, username, deleted_at::text FROM users WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 20")
	if err != nil {
		fmt.Println("[WARN] 查询失败:", err)
	}
	var collideNames []string
	if drows != nil {
		any := false
		for drows.Next() {
			var id int64
			var username, deletedAt string
			if err := drows.Scan(&id, &username, &deletedAt); err == nil {
				fmt.Printf("  id=%d username=%s deleted_at=%s\n", id, username, deletedAt)
				collideNames = append(collideNames, username)
				any = true
			}
		}
		drows.Close()
		if !any {
			fmt.Println("  （没有软删除用户）")
		}
	}

	// 4. 最近注册的用户
	fmt.Println("\n== 最近 15 个用户 ==")
	lrows, err := conn.Query(ctx,
		"SELECT id, username, (deleted_at IS NOT NULL), created_at::text FROM users ORDER BY id DESC LIMIT 15")
	if err != nil {
		fmt.Println("[WARN] 查询失败:", err)
	} else {
		for lrows.Next() {
			var id int64
			var username, createdAt string
			var deleted bool
			if err := lrows.Scan(&id, &username, &deleted, &createdAt); err == nil {
				mark := ""
				if deleted {
					mark = " [已软删]"
				}
				fmt.Printf("  id=%d %s%s  %s\n", id, username, mark, createdAt)
			}
		}
		lrows.Close()
	}

	// 5. INSERT 冲突测试（事务内，自动回滚）
	testName := ""
	if len(collideNames) > 0 {
		testName = collideNames[0]
	} else {
		fmt.Println("\n[结论候选] 没有软删除用户，无法做同名冲突测试。")
	}
	if testName != "" {
		fmt.Printf("\n== 同名 INSERT 测试（事务回滚，不落库）username=%s ==\n", testName)
		testInsert(ctx, conn, testName)
	}

	// 6. 执行 0005 迁移
	if apply {
		fmt.Println("\n== 执行 0005 迁移 ==")
		sqlBytes, err := os.ReadFile("serverdb/migrations/0005_soft_deleted_username_reuse.sql")
		if err != nil {
			fmt.Println("[FAIL] 读取迁移文件失败:", err)
			os.Exit(1)
		}
		if _, err := conn.Exec(ctx, string(sqlBytes)); err != nil {
			fmt.Println("[FAIL] 迁移执行失败:", err)
			os.Exit(1)
		}
		fmt.Println("[OK] 迁移已执行")
		if testName != "" {
			fmt.Println("\n== 迁移后同名 INSERT 复测（事务回滚）==")
			testInsert(ctx, conn, testName)
		}
		fmt.Println("\n== 迁移后的 users 索引 ==")
		irows, err := conn.Query(ctx,
			"SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'users'")
		if err == nil {
			for irows.Next() {
				var n, d string
				if err := irows.Scan(&n, &d); err == nil && strings.Contains(d, "username") {
					fmt.Printf("  %s\n    %s\n", n, d)
				}
			}
			irows.Close()
		}
	}
}

func testInsert(ctx context.Context, conn *pgx.Conn, username string) {
	tx, err := conn.Begin(ctx)
	if err != nil {
		fmt.Println("[FAIL] 开启事务失败:", err)
		return
	}
	defer tx.Rollback(context.Background())

	_, err = tx.Exec(ctx,
		"INSERT INTO users (username, password_hash, nickname, phone, role) VALUES ($1, $2, $3, NULL, $4)",
		username, "probe-hash-not-a-real-user", "同名冲突测试", "owner")
	if err != nil {
		fmt.Println("  ✗ INSERT 被拒绝（当前仍复现故障）:", err)
	} else {
		fmt.Println("  ✓ INSERT 成功（同名可注册，故障已消除）")
	}
}
