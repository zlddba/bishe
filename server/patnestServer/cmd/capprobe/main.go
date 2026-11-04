package main

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"time"

	"patnestServer/internal/config"

	"github.com/jackc/pgx/v5"
)

// capprobe 只读对账：把 sitter_daily_capacity 记录的逐日占用与按有效订单重算的占用做比较，
// 用来判断 capacity exceeded 是真满了还是账目泄漏。全程只有 SELECT。
func main() {
	dump := len(os.Args) > 1 && os.Args[1] == "-dump"

	cfg := config.Load()
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	conn, err := pgx.Connect(ctx, cfg.DBDSN())
	if err != nil {
		fmt.Println("[FAIL] 连接数据库失败:", err)
		os.Exit(1)
	}
	defer conn.Close(ctx)
	// -trigtest 在事务里模拟"订单完成"和"后台软删进行中订单"，观察该夜的 used 是否归还，
	// 结束后整笔事务回滚，不留任何数据。用来验证 0015 的两个触发器真的在线上生效。
	if len(os.Args) > 1 && os.Args[1] == "-trigtest" {
		tx, err := conn.Begin(ctx)
		if err != nil {
			fmt.Fprintln(os.Stderr, "[FAIL] 开启事务失败:", err)
			os.Exit(1)
		}
		defer tx.Rollback(context.Background())

		cands, err := tx.Query(ctx, `
			SELECT o.id, o.sitter_id, GREATEST(current_date, o.start_date)::text, o.end_date::text, o.pet_count
			FROM orders o
			WHERE o.deleted_at IS NULL
			  AND o.status IN ('confirmed','accepted','staying')
			  AND o.end_date > current_date
			  AND EXISTS (SELECT 1 FROM sitter_daily_capacity c
			              WHERE c.sitter_id = o.sitter_id
			                AND c.cap_date = GREATEST(current_date, o.start_date)
			                AND c.used >= o.pet_count)
			ORDER BY o.id DESC
			LIMIT 2`,
		)
		if err != nil {
			fmt.Fprintln(os.Stderr, "[FAIL] 选取订单失败:", err)
			os.Exit(1)
		}
		type cand struct {
			oid, sid int64
			d, end   string
			pets     int
		}
		var got []cand
		for cands.Next() {
			var c cand
			if err := cands.Scan(&c.oid, &c.sid, &c.d, &c.end, &c.pets); err == nil {
				got = append(got, c)
			}
		}
		cands.Close()
		if len(got) == 0 {
			fmt.Println("没有可测试的进行中订单，跳过触发器验证")
			return
		}

		used := func(c cand) int {
			var v int
			if err := tx.QueryRow(ctx,
				"SELECT used FROM sitter_daily_capacity WHERE sitter_id = $1 AND cap_date = $2::date",
				c.sid, c.d).Scan(&v); err != nil {
				fmt.Fprintln(os.Stderr, "[FAIL] 读取 used 失败:", err)
				os.Exit(1)
			}
			return v
		}

		q := string(rune(39))
		if _, err := tx.Exec(ctx, "SAVEPOINT trigtest"); err != nil {
			fmt.Fprintln(os.Stderr, "  [FAIL] 建立存档点失败:", err)
			return
		}
		fmt.Println("== 完成/评价应归还未消费的未来夜间 ==")
		c := got[0]
		before := used(c)
		if _, err := tx.Exec(ctx, "UPDATE orders SET status = "+q+"completed"+q+" WHERE id = $1", c.oid); err != nil {
			fmt.Fprintln(os.Stderr, "  [FAIL] UPDATE 失败:", err)
			return
		}
		after := used(c)
		fmt.Println("  订单", c.oid, "机构", c.sid, "夜间", c.d, "：used", before, "->", after, "（该单", c.pets, "只）")
		if after == before-c.pets {
			fmt.Println("  结论：已归还")
		} else {
			fmt.Println("  结论：未归还，账目仍有问题")
		}
		if _, err := tx.Exec(ctx, "ROLLBACK TO SAVEPOINT trigtest"); err != nil {
			fmt.Fprintln(os.Stderr, "  [WARN] 回滚到存档点失败:", err)
		}

		// 没有第二笔进行中订单时复用同一笔：两段各走一次存档点，互不影响。
		{
			fmt.Println("== 软删进行中订单应归还容量 ==")
			c2 := got[0]
			if len(got) > 1 {
				c2 = got[1]
			}
			if _, err := tx.Exec(ctx, "SAVEPOINT trigtest"); err != nil {
				fmt.Fprintln(os.Stderr, "  [FAIL] 建立存档点失败:", err)
				return
			}
			b2 := used(c2)
			if _, err := tx.Exec(ctx, "UPDATE orders SET deleted_at = now() WHERE id = $1", c2.oid); err != nil {
				fmt.Fprintln(os.Stderr, "  [FAIL] UPDATE 失败:", err)
				return
			}
			a2 := used(c2)
			fmt.Println("  订单", c2.oid, "机构", c2.sid, "夜间", c2.d, "：used", b2, "->", a2, "（该单", c2.pets, "只）")
			if a2 == b2-c2.pets {
				fmt.Println("  结论：已归还")
			} else {
				fmt.Println("  结论：未归还，账目仍有问题")
			}
		}
		return
	}
	// -reserve <机构ID> <开始> <结束> <宠物数>：在事务里真调一次 fn_reserve_capacity 再回滚，
	// 用来复现"下单返回容量已满"，确认新函数是否按当前上限校验，且不会留下任何数据。
	if len(os.Args) > 1 && os.Args[1] == "-reserve" {
		if len(os.Args) != 6 {
			fmt.Fprintln(os.Stderr, "用法: go run ./cmd/capprobe -reserve <sitter_id> <start> <end> <pet_count>")
			os.Exit(2)
		}
		sid, _ := strconv.ParseInt(os.Args[2], 10, 64)
		pets := os.Args[5]
		span := "机构 " + os.Args[2] + "  " + os.Args[3] + " ~ " + os.Args[4] + "  " + pets + " 只"
		tx, err := conn.Begin(ctx)
		if err != nil {
			fmt.Fprintln(os.Stderr, "[FAIL] 开启事务失败:", err)
			os.Exit(1)
		}
		defer tx.Rollback(context.Background())
		_, err = tx.Exec(ctx, "SELECT fn_reserve_capacity($1, $2::date, $3::date, $4)",
			sid, os.Args[3], os.Args[4], mustInt(pets))
		if err != nil {
			fmt.Println(span, "=> 仍被拒绝:", err)
		} else {
			fmt.Println(span, "=> 可以占用（事务已回滚，未落库）")
		}
		return
	}

	if dump {
		// -dump 把整张容量表打印成可重放的 INSERT，用于在修复账目前留一份回滚点。
		fmt.Println("-- sitter_daily_capacity 备份，生成时间见会话开始时刻")
		fmt.Println("BEGIN;")
		fmt.Println("TRUNCATE sitter_daily_capacity;")
		raws, err := conn.Query(ctx, `
			SELECT sitter_id, cap_date::text, used, capacity
			FROM sitter_daily_capacity ORDER BY sitter_id, cap_date`)
		if err != nil {
			fmt.Fprintln(os.Stderr, "[FAIL] 导出失败:", err)
			os.Exit(1)
		}
		defer raws.Close()
		for raws.Next() {
			var sid int64
			var used, cap int
			var date string
			if err := raws.Scan(&sid, &date, &used, &cap); err != nil {
				fmt.Fprintln(os.Stderr, "[FAIL] 读取失败:", err)
				os.Exit(1)
			}
			q := string(rune(39))
			fmt.Println("INSERT INTO sitter_daily_capacity (sitter_id, cap_date, used, capacity) VALUES (" +
				strconv.FormatInt(sid, 10) + ", " + q + date + q + ", " +
				strconv.Itoa(used) + ", " + strconv.Itoa(cap) + ");")
		}
		fmt.Println("COMMIT;")
		return
	}

	queries := []struct{ title, sql string }{
		{"0 迁移版本", `SELECT version FROM schema_migrations ORDER BY version`},
		{"1 每个机构账目不一致的日期数", `WITH live AS (
  SELECT o.sitter_id,
         (generate_series(o.start_date::timestamp, (o.end_date - 1)::timestamp,
                          interval '1 day'))::date AS cap_date,
         SUM(o.pet_count)::int AS real_used
  FROM orders o
  WHERE o.deleted_at IS NULL
    AND o.status IN ('pending_pay','confirmed','accepted','staying')
  GROUP BY 1, 2
)
SELECT s.id, s.name, s.capacity AS sitter_cap,
       COUNT(c.*) FILTER (WHERE c.used IS DISTINCT FROM COALESCE(l.real_used,0)) AS mismatch_days,
       COALESCE(MAX(c.used), 0) AS max_recorded
FROM sitters s
LEFT JOIN sitter_daily_capacity c ON c.sitter_id = s.id
LEFT JOIN live l ON l.sitter_id = c.sitter_id AND l.cap_date = c.cap_date
WHERE s.deleted_at IS NULL
GROUP BY s.id, s.name, s.capacity
ORDER BY mismatch_days DESC, s.id`},
		{"2 明细（最多 40 行）记录 vs 重算", `WITH live AS (
  SELECT o.sitter_id,
         (generate_series(o.start_date::timestamp, (o.end_date - 1)::timestamp,
                          interval '1 day'))::date AS cap_date,
         SUM(o.pet_count)::int AS real_used
  FROM orders o
  WHERE o.deleted_at IS NULL
    AND o.status IN ('pending_pay','confirmed','accepted','staying')
  GROUP BY 1, 2
)
SELECT c.sitter_id, c.cap_date, c.used AS recorded, COALESCE(l.real_used,0) AS actual,
       c.capacity AS row_cap, s.capacity AS sitter_cap
FROM sitter_daily_capacity c
JOIN sitters s ON s.id = c.sitter_id
LEFT JOIN live l ON l.sitter_id = c.sitter_id AND l.cap_date = c.cap_date
WHERE c.used IS DISTINCT FROM COALESCE(l.real_used,0)
ORDER BY c.sitter_id, c.cap_date
LIMIT 40`},
		{"3 软删但仍在进行中的订单", `SELECT id, order_no, sitter_id, status::text, start_date, end_date, pet_count, deleted_at::text
FROM orders
WHERE deleted_at IS NOT NULL
  AND status IN ('pending_pay','confirmed','accepted','staying')
ORDER BY deleted_at DESC
LIMIT 20`},
		{"4 行内容量与机构容量不一致", `SELECT COUNT(*) AS rows_bad, COUNT(DISTINCT c.sitter_id) AS sitters_bad
FROM sitter_daily_capacity c JOIN sitters s ON s.id = c.sitter_id
WHERE c.capacity IS DISTINCT FROM s.capacity`},
		{"5 未来 14 天逐日占用合计", `SELECT cap_date, COUNT(*) AS sitter_rows, SUM(used) AS used_sum, MIN(capacity) AS min_cap
FROM sitter_daily_capacity
WHERE cap_date >= current_date AND cap_date < current_date + 14
GROUP BY cap_date ORDER BY cap_date`},
		{"7 各状态订单占用的未来日期数", `SELECT status::text, COUNT(*) AS orders,
       COUNT(*) FILTER (WHERE end_date > current_date) AS 未来仍有日期,
       MIN(start_date::text) AS 最早开始, MAX(end_date::text) AS 最晚结束
FROM orders WHERE deleted_at IS NULL
GROUP BY 1 ORDER BY 2 DESC`},
		{"8 机构 7 的全部订单（含软删）", `SELECT id, order_no, status::text, payment_status::text, start_date::text, end_date::text,
       pet_count, (deleted_at IS NOT NULL) AS is_deleted, created_at::date
FROM orders WHERE sitter_id = 7 ORDER BY id`},
		{"9 未来日期上状态非活跃但仍有容量行的订单（泄漏嫌疑）", `SELECT o.id, o.order_no, o.sitter_id, o.status::text, o.start_date::text, o.end_date::text, o.pet_count,
       (o.deleted_at IS NOT NULL) AS is_deleted
FROM orders o
WHERE o.status NOT IN ('pending_pay','confirmed','accepted','staying')
  AND o.end_date > current_date
ORDER BY o.end_date DESC
LIMIT 20`},
		{"10 机构 7 逐日真值", `SELECT d::date AS cap_date,
       COALESCE(c.used, -1) AS recorded,
       COALESCE(c.capacity, -1) AS row_cap,
       COALESCE(SUM(o.pet_count), 0) AS live_sum,
       STRING_AGG(o.id::text || ":" || o.status::text || "/p" || o.pet_count::text, ",") AS live_orders
FROM generate_series(current_date - 1, current_date + 9, interval '1 day') AS d
LEFT JOIN sitter_daily_capacity c ON c.sitter_id = 7 AND c.cap_date = d::date
LEFT JOIN orders o ON o.sitter_id = 7
     AND o.deleted_at IS NULL
     AND o.status IN ('pending_pay','confirmed','accepted','staying')
     AND o.start_date <= d::date AND o.end_date > d::date
GROUP BY 1, 2, 3
ORDER BY 1`},
		{"6 最近 12 条订单", `SELECT id, order_no, sitter_id, status::text, payment_status::text, start_date, end_date, pet_count,
       (deleted_at IS NOT NULL) AS is_deleted
FROM orders ORDER BY id DESC LIMIT 12`},
	}

	for _, q := range queries {
		fmt.Println()
		fmt.Println("== " + q.title + " ==")
		rows, err := conn.Query(ctx, q.sql)
		if err != nil {
			fmt.Println("  [WARN] 查询失败:", err)
			continue
		}
		for _, f := range rows.FieldDescriptions() {
			fmt.Printf("%-22s ", f.Name)
		}
		fmt.Println()
		n := 0
		for rows.Next() {
			vals, err := rows.Values()
			if err != nil {
				break
			}
			for _, v := range vals {
				fmt.Printf("%-22v ", v)
			}
			fmt.Println()
			n++
		}
		rows.Close()
		if n == 0 {
			fmt.Println("  （无记录）")
		}
	}
}

// mustInt 把命令行动参转成 int，失败即退出，避免把 0 只宠物送进容量函数。
func mustInt(v string) int {
	n, err := strconv.Atoi(v)
	if err != nil {
		fmt.Fprintln(os.Stderr, "宠物数必须是整数:", err)
		os.Exit(2)
	}
	return n
}
