package handler

import (
	"context"
	"errors"
	"fmt"
	"regexp"

	"patnestServer/internal/db"

	"github.com/jackc/pgx/v5"
)

// 中国大陆手机号：1 开头，第二位 3-9，共 11 位。
var phonePattern = regexp.MustCompile(`^1[3-9]\d{9}$`)

// capacityShrinkProblem 拦住"把机构默认容量调到低于某天已占用数"的操作。
// 0015 之后默认容量一变，所有未按日覆盖的容量行会跟着刷新；不拦的话机构会静默进入
// 某天 used > capacity 的状态：新订单全被拒，提示又看不出是自己改坏的。
// 返回空串表示没有冲突。
func capacityShrinkProblem(ctx context.Context, sitterID int64, newCap int) (string, error) {
	var date string
	var used int
	err := db.Pool.QueryRow(ctx, `
		SELECT to_char(c.cap_date, 'YYYY-MM-DD'), c.used
		FROM sitter_daily_capacity c
		WHERE c.sitter_id = $1 AND NOT c.is_override AND c.used > $2
		ORDER BY c.used DESC, c.cap_date
		LIMIT 1`, sitterID, newCap).Scan(&date, &used)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", nil
	}
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%s 已占用 %d 只，默认容量不能低于该数；确有需要请先给这一天单独设置容量", date, used), nil
}

func validPhone(value string) bool {
	return value == "" || phonePattern.MatchString(value)
}

// maskPhone 把手机号中间四位打码（138****2201）。
// 后台列表默认只返回打码号码，完整号码走单独接口并记审计，
// 避免任何有 users.read 权限的管理员批量导走用户手机号。
func maskPhone(value string) string {
	if len(value) != 11 {
		return value
	}
	return value[:3] + "****" + value[7:]
}
