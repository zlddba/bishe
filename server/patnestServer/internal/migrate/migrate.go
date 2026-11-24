package migrate

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

const createMigrationsTable = `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version      VARCHAR(32)  PRIMARY KEY,
    name         TEXT         NOT NULL,
    checksum     VARCHAR(64)  NOT NULL,
    applied_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    execution_ms INTEGER      NOT NULL DEFAULT 0
);
COMMENT ON TABLE schema_migrations IS '已应用的数据库增量迁移记录，由 cmd/migrate 维护';
`

type Migration struct {
	Version  string
	Name     string
	SQL      string
	Checksum string

	// ChecksumLF 是 SQL 内容按 LF 归一化后的 sha256。
	// Windows 检出会把 .sql 变成 CRLF，而历史库里记录的是 LF 版校验和；
	// 校验时同时接受「原始字节」与「LF 归一化」两种结果，避免仅因换行符
	// 差异误判为「历史迁移被篡改」。两个值内容一致时才都会匹配。
	ChecksumLF string
}

// checksumPair 返回迁移内容在「原始字节」与「LF 归一化」两种口径下的 sha256 十六进制串。
// 归一化只把 \r\n 变为 \n，不改变任何 SQL 语义，因此两种校验和等价可信。
func checksumPair(body []byte) (raw, lf string) {
	rawSum := sha256.Sum256(body)
	normalized := bytes.ReplaceAll(body, []byte("\r\n"), []byte("\n"))
	lfSum := sha256.Sum256(normalized)
	return hex.EncodeToString(rawSum[:]), hex.EncodeToString(lfSum[:])
}

type Result struct {
	Version  string
	Name     string
	State    string
	Duration time.Duration
	Err      error
}

// Load 读取目录下形如 0001_xxx.sql 的迁移文件，按版本号升序返回。
func Load(dir string) ([]Migration, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("读取迁移目录失败: %w", err)
	}

	list := make([]Migration, 0, len(entries))
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(strings.ToLower(e.Name()), ".sql") {
			continue
		}
		body, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil {
			return nil, fmt.Errorf("读取 %s 失败: %w", e.Name(), err)
		}
		version, _ := splitName(e.Name())
		if version == "" {
			return nil, fmt.Errorf("迁移文件名缺少版本号前缀: %s", e.Name())
		}
		raw, lf := checksumPair(body)
		list = append(list, Migration{
			Version:    version,
			Name:       e.Name(),
			SQL:        string(body),
			Checksum:   raw,
			ChecksumLF: lf,
		})
	}

	sort.Slice(list, func(i, j int) bool { return list[i].Version < list[j].Version })

	for i := 1; i < len(list); i++ {
		if list[i].Version == list[i-1].Version {
			return nil, fmt.Errorf("迁移版本号重复: %s", list[i].Version)
		}
	}
	return list, nil
}

func splitName(file string) (version, name string) {
	base := strings.TrimSuffix(file, filepath.Ext(file))
	idx := strings.Index(base, "_")
	if idx <= 0 {
		return "", base
	}
	prefix := base[:idx]
	for _, r := range prefix {
		if r < '0' || r > '9' {
			return "", base
		}
	}
	return prefix, base[idx+1:]
}

// Run 在单个连接 + 咨询锁下依次应用迁移；dryRun 时只检查不执行。
func Run(ctx context.Context, dsn, dir string, dryRun bool) ([]Result, error) {
	list, err := Load(dir)
	if err != nil {
		return nil, err
	}

	conn, err := pgx.Connect(ctx, dsn)
	if err != nil {
		return nil, fmt.Errorf("连接数据库失败: %w", err)
	}
	defer conn.Close(context.Background())

	if _, err := conn.Exec(ctx, createMigrationsTable); err != nil {
		return nil, fmt.Errorf("初始化 schema_migrations 失败: %w", err)
	}

	// 多个发布任务同时执行时串行化，避免重复应用
	if _, err := conn.Exec(ctx, `SELECT pg_advisory_lock(hashtext('patnest_schema_migrations'))`); err != nil {
		return nil, fmt.Errorf("获取迁移锁失败: %w", err)
	}
	defer conn.Exec(context.Background(), `SELECT pg_advisory_unlock(hashtext('patnest_schema_migrations'))`)

	applied := map[string]string{}
	rows, err := conn.Query(ctx, `SELECT version, checksum FROM schema_migrations`)
	if err != nil {
		return nil, fmt.Errorf("读取已应用迁移失败: %w", err)
	}
	for rows.Next() {
		var v, sum string
		if err := rows.Scan(&v, &sum); err == nil {
			applied[v] = sum
		}
	}
	rows.Close()

	results := make([]Result, 0, len(list))
	for _, m := range list {
		if sum, ok := applied[m.Version]; ok {
			// 原始字节或 LF 归一化任一匹配即视为内容一致，兼容跨平台换行符差异。
			if sum != m.Checksum && sum != m.ChecksumLF {
				results = append(results, Result{Version: m.Version, Name: m.Name, State: "checksum-mismatch",
					Err: fmt.Errorf("已应用迁移内容被修改，请新增迁移文件而不是改历史文件")})
				continue
			}
			results = append(results, Result{Version: m.Version, Name: m.Name, State: "applied"})
			continue
		}
		if dryRun {
			results = append(results, Result{Version: m.Version, Name: m.Name, State: "pending"})
			continue
		}

		start := time.Now()
		err := applyOne(ctx, conn, m)
		cost := time.Since(start)
		if err != nil {
			results = append(results, Result{Version: m.Version, Name: m.Name, State: "failed", Duration: cost, Err: err})
			return results, fmt.Errorf("迁移 %s 失败: %w", m.Name, err)
		}
		results = append(results, Result{Version: m.Version, Name: m.Name, State: "new", Duration: cost})
	}

	for _, r := range results {
		if r.Err != nil {
			return results, r.Err
		}
	}
	return results, nil
}

const insertMigration = `INSERT INTO schema_migrations (version, name, checksum, execution_ms)
	 VALUES ($1, $2, $3, $4)`

// noTransactionMarker 标记必须脱离事务执行的迁移（如 CREATE INDEX CONCURRENTLY）。
const noTransactionMarker = "-- migrate:no-transaction"

func applyOne(ctx context.Context, conn *pgx.Conn, m Migration) error {
	start := time.Now()

	if isNonTransactional(m.SQL) {
		// 这类语句本身不可回滚，要求迁移内部可重复执行（IF NOT EXISTS / OR REPLACE）
		if _, err := conn.Exec(ctx, m.SQL); err != nil {
			return err
		}
		_, err := conn.Exec(ctx, insertMigration,
			m.Version, m.Name, m.ChecksumLF, time.Since(start).Milliseconds())
		return err
	}

	tx, err := conn.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(context.Background())

	if _, err := tx.Exec(ctx, m.SQL); err != nil {
		return err
	}
	ms := time.Since(start).Milliseconds()
	if _, err := tx.Exec(ctx, insertMigration, m.Version, m.Name, m.ChecksumLF, ms); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func isNonTransactional(sql string) bool {
	head := sql
	if len(head) > 2048 {
		head = head[:2048]
	}
	for _, line := range strings.Split(head, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if line == noTransactionMarker {
			return true
		}
		if strings.HasPrefix(line, "--") {
			continue
		}
		return false
	}
	return false
}
