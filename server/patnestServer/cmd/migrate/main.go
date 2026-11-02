package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"time"

	"patnestServer/internal/config"
	"patnestServer/internal/migrate"
)

func main() {
	dir := flag.String("dir", "serverdb/migrations", "迁移文件目录")
	dryRun := flag.Bool("dry-run", false, "只检查待执行的迁移，不写入数据库")
	status := flag.Bool("status", false, "查看迁移状态（等同于 --dry-run）")
	timeout := flag.Duration("timeout", 5*time.Minute, "整体超时时间")
	flag.Parse()

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()

	cfg := config.Load()
	results, err := migrate.Run(ctx, cfg.DBDSN(), *dir, *dryRun || *status)
	printResults(results)
	if err != nil {
		fmt.Fprintf(os.Stderr, "\n[FAIL] %v\n", err)
		os.Exit(1)
	}
	if *dryRun || *status {
		fmt.Println("\n[OK] 状态检查完成，未执行任何变更。")
	} else {
		fmt.Println("\n[OK] 数据库迁移完成。")
	}
}

func printResults(results []migrate.Result) {
	if len(results) == 0 {
		fmt.Println("没有找到迁移文件。")
		return
	}
	fmt.Printf("\n%-8s %-38s %-18s %s\n", "版本", "文件", "状态", "耗时")
	fmt.Println("--------------------------------------------------------------------------")
	for _, r := range results {
		cost := "-"
		if r.Duration > 0 {
			cost = r.Duration.Round(time.Millisecond).String()
		}
		fmt.Printf("%-8s %-38s %-18s %s\n", r.Version, r.Name, stateLabel(r.State), cost)
		if r.Err != nil {
			fmt.Printf("         错误: %v\n", r.Err)
		}
	}
}

func stateLabel(s string) string {
	switch s {
	case "new":
		return "已执行"
	case "applied":
		return "已存在"
	case "pending":
		return "待执行"
	case "failed":
		return "失败"
	case "checksum-mismatch":
		return "校验不一致"
	default:
		return s
	}
}
