package handler

import (
	"context"
	"fmt"
	"math"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

// 平台佣金比例有两层：
//   - platform_settings.commission_rate：后台设置的全局默认比例；
//   - sitters.commission_rate：单个机构的单独比例，NULL 表示跟随全局。
//
// 收益汇总、提现结算都统一走 sitterCommissionRate()，保证机构端看到的比例
// 和实际扣的佣金是同一个数，不会出现「页面 10%、扣款 15%」这种对不上的情况。

const (
	commissionSettingKey  = "commission_rate"
	defaultCommissionRate = 0.10
)

// rowQuerier 让同一段查询既能走连接池（*pgxpool.Pool），也能走事务（pgx.Tx）。
// 注销流程要在同一个事务里「算区间 → 写结算单 → 软删机构」，
// 如果结算逻辑只认连接池，就没法放进事务。
type rowQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

func clampCommissionRate(rate float64) float64 {
	if math.IsNaN(rate) || rate < 0 {
		return 0
	}
	if rate > 1 {
		return 1
	}
	return rate
}

// globalCommissionRate 读平台默认比例。配置缺失或写坏了都退回 10%，
// 不因为一条配置让收益接口整体报错。
func globalCommissionRate(ctx context.Context) float64 {
	var raw string
	if err := db.Pool.QueryRow(ctx,
		"SELECT value FROM platform_settings WHERE key = $1", commissionSettingKey).Scan(&raw); err != nil {
		return defaultCommissionRate
	}
	rate, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return defaultCommissionRate
	}
	return clampCommissionRate(rate)
}

// sitterCommissionRate 取机构实际生效的佣金：单独设置优先，否则用平台默认。
func sitterCommissionRate(ctx context.Context, sitterID int64) float64 {
	return sitterCommissionRateQ(ctx, db.Pool, sitterID)
}

// sitterCommissionRateQ 同上，但允许传入事务。
func sitterCommissionRateQ(ctx context.Context, q rowQuerier, sitterID int64) float64 {
	var override *float64
	var raw string
	err := q.QueryRow(ctx, `
		SELECT s.commission_rate::float8,
		       COALESCE((SELECT value FROM platform_settings WHERE key = $2), '')
		FROM sitters s WHERE s.id = $1`, sitterID, commissionSettingKey).
		Scan(&override, &raw)
	if err != nil {
		return defaultCommissionRate
	}
	if override != nil {
		return clampCommissionRate(*override)
	}
	if rate, perr := strconv.ParseFloat(raw, 64); perr == nil {
		return clampCommissionRate(rate)
	}
	return defaultCommissionRate
}

// AdminGetCommissionSetting handles GET /api/v1/admin/settings/commission
// 返回全局默认比例，附带更新时间，后台页面据此回显。
func AdminGetCommissionSetting(c *gin.Context) {
	var raw, updatedAt string
	var updatedBy int64
	err := db.Pool.QueryRow(context.Background(), `
		SELECT value,
		       COALESCE(to_char(updated_at, 'YYYY-MM-DD HH24:MI'), ''),
		       COALESCE(updated_by, 0)
		FROM platform_settings WHERE key = $1`, commissionSettingKey).
		Scan(&raw, &updatedAt, &updatedBy)

	rate := defaultCommissionRate
	isDefault := true
	if err == nil {
		if v, perr := strconv.ParseFloat(raw, 64); perr == nil {
			rate = clampCommissionRate(v)
			isDefault = false
		}
	}
	OK(c, gin.H{"rate": rate, "is_default": isDefault, "updated_at": updatedAt, "updated_by": updatedBy})
}

// AdminUpdateCommissionSetting handles PUT /api/v1/admin/settings/commission
// 只影响「没有单独设置比例」的机构；已单独设置的机构保持自己的比例。
func AdminUpdateCommissionSetting(c *gin.Context) {
	var in struct {
		Rate *float64 `json:"rate" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写佣金比例")
		return
	}
	if in.Rate == nil || math.IsNaN(*in.Rate) || *in.Rate < 0 || *in.Rate > 1 {
		BadRequest(c, "佣金比例需在 0 ~ 1 之间，例如 0.1 表示 10%")
		return
	}
	rate := clampCommissionRate(*in.Rate)
	adminID := c.GetInt64("user_id")
	_, err := db.Pool.Exec(context.Background(), `
		INSERT INTO platform_settings (key, value, updated_at, updated_by)
		VALUES ($1, $2, now(), NULLIF($3, 0))
		ON CONFLICT (key) DO UPDATE
		SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by`,
		commissionSettingKey, strconv.FormatFloat(rate, 'f', 4, 64), adminID)
	if err != nil {
		InternalError(c, "保存佣金比例失败: "+err.Error())
		return
	}
	writeAudit(c, "UPDATE_COMMISSION", "setting", 0, fmt.Sprintf(`{"rate": %.4f}`, rate))
	OK(c, gin.H{"rate": rate})
}
