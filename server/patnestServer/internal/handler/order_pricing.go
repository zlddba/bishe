package handler

import (
	"context"
	"fmt"
	"time"

	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

// sqlQuerier 让计费核心同时服务于下单事务（pgx.Tx，FOR UPDATE 锁行为）
// 与预估接口（db.Pool，只读不加锁），保证两者口径完全一致。
type sqlQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

// httpErr 计费过程中产生的、可直接映射为 HTTP 响应的业务错误。
type httpErr struct {
	Status int
	Msg    string
}

func (e *httpErr) Error() string { return e.Msg }

func badRequestErr(format string, a ...any) *httpErr {
	return &httpErr{Status: 400, Msg: fmt.Sprintf(format, a...)}
}

// respondHTTPErr 把 *httpErr 转成对应状态码的统一响应。
func respondHTTPErr(c *gin.Context, e *httpErr) {
	switch e.Status {
	case 403:
		Forbidden(c, e.Msg)
	case 409:
		Conflict(c, e.Msg)
	case 404:
		NotFound(c, e.Msg)
	case 500:
		InternalError(c, e.Msg)
	default:
		BadRequest(c, e.Msg)
	}
}

type pricingAddonLine struct {
	ID     int64   `json:"id"`
	Name   string  `json:"name"`
	Price  float64 `json:"price"`
	Qty    int     `json:"qty"`
	Amount float64 `json:"amount"`
}

// orderPricing 是下单/预估共用的核算结果。
type orderPricing struct {
	SitterName      string
	TotalDays       int
	EstimatedAmount float64
	AddonLines      []pricingAddonLine
	AddonAmount     float64
	Subtotal        float64
	CouponTitle     string
	DiscountAmount  float64
	PointsUsed      int
	PointsDiscount  float64
	TotalAmount     float64
}

// computeOrderPricing 复算订单金额：机构房价（含节假日逐日上浮）+ 加购服务
// - 优惠券 - 积分抵扣。lock=true 时对优惠券与积分余额加行锁（下单事务内）。
func computeOrderPricing(
	ctx context.Context,
	q sqlQuerier,
	ownerID, sitterID int64,
	startDate, endDate time.Time,
	petCount int,
	addons []model.CreateOrderAddon,
	userCouponID int64,
	pointsUsed int,
	lock bool,
) (*orderPricing, *httpErr) {

	totalDays := int(endDate.Sub(startDate).Hours() / 24)

	var sitterName, priceMode string
	var dailyPrice, visitPrice float64
	err := q.QueryRow(ctx,
		"SELECT name, price_mode::text, daily_price, visit_price FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL",
		sitterID).Scan(&sitterName, &priceMode, &dailyPrice, &visitPrice)
	if err != nil {
		return nil, badRequestErr("sitter not found or not active")
	}
	if (priceMode == "per_day" && dailyPrice <= 0) || (priceMode == "per_visit" && visitPrice <= 0) {
		return nil, badRequestErr("该机构未设置有效价格，暂时无法下单")
	}

	// 基础费用：按天计费时逐日叠加节假日上浮规则
	var estimatedAmount float64
	if priceMode == "per_day" {
		type rule struct {
			start, end time.Time
			multiplier float64
		}
		rules := make([]rule, 0)
		if rows, err := q.Query(ctx, `
			SELECT start_date, end_date, multiplier FROM sitter_price_rules
			WHERE sitter_id = $1 AND end_date >= $2 AND start_date < $3`,
			sitterID, startDate, endDate); err == nil {
			for rows.Next() {
				var r rule
				if rows.Scan(&r.start, &r.end, &r.multiplier) == nil {
					rules = append(rules, r)
				}
			}
			rows.Close()
		}
		for d := startDate; d.Before(endDate); d = d.AddDate(0, 0, 1) {
			multiplier := 1.0
			for _, r := range rules {
				if !d.Before(r.start) && !d.After(r.end) && r.multiplier > multiplier {
					multiplier = r.multiplier
				}
			}
			estimatedAmount += dailyPrice * multiplier
		}
		estimatedAmount *= float64(petCount)
	} else {
		estimatedAmount = visitPrice * float64(petCount)
	}

	// 加购服务
	lines := make([]pricingAddonLine, 0, len(addons))
	addonAmount := 0.0
	for _, a := range addons {
		qty := a.Qty
		if qty <= 0 {
			qty = 1
		}
		if qty > 20 {
			return nil, badRequestErr("加购数量不能超过 20")
		}
		var name string
		var price float64
		err := q.QueryRow(ctx,
			`SELECT name, price FROM sitter_addons WHERE id = $1 AND sitter_id = $2 AND enabled`,
			a.AddonID, sitterID).Scan(&name, &price)
		if err != nil {
			return nil, badRequestErr("加购服务不存在或已下架")
		}
		amount := price * float64(qty)
		addonAmount += amount
		lines = append(lines, pricingAddonLine{ID: a.AddonID, Name: name, Price: price, Qty: qty, Amount: amount})
	}

	// 优惠券
	subtotal := estimatedAmount + addonAmount
	discountAmount := 0.0
	couponTitle := ""
	if userCouponID > 0 {
		var ctype string
		var value, minAmount float64
		var ucStatus string
		var couponSitterID int64
		forSQL := `
			SELECT c.type::text, c.value, c.min_amount, uc.status, COALESCE(c.sitter_id, 0), c.title
			FROM user_coupons uc JOIN coupons c ON c.id = uc.coupon_id
			WHERE uc.id = $1 AND uc.user_id = $2 AND c.enabled
			  AND (c.ends_at IS NULL OR c.ends_at >= now())`
		if lock {
			forSQL += " FOR UPDATE"
		}
		err := q.QueryRow(ctx, forSQL, userCouponID, ownerID).
			Scan(&ctype, &value, &minAmount, &ucStatus, &couponSitterID, &couponTitle)
		if err != nil || ucStatus != "unused" {
			return nil, badRequestErr("优惠券不可用或已使用")
		}
		// 机构专属券只能用于发券机构的订单
		if couponSitterID != 0 && couponSitterID != sitterID {
			return nil, badRequestErr("该优惠券仅限发券机构使用，不能用于当前订单")
		}
		if subtotal < minAmount {
			return nil, badRequestErr("订单金额未达到优惠券使用门槛")
		}
		if ctype == "fixed" {
			discountAmount = value
		} else {
			discountAmount = subtotal * value / 100
		}
		if discountAmount > subtotal {
			discountAmount = subtotal
		}
	}

	// 积分抵扣（100 积分 = 1 元）
	remaining := subtotal - discountAmount
	if remaining < 0 {
		remaining = 0
	}
	pointsDiscount := 0.0
	if pointsUsed > 0 {
		var balance int
		sql := "SELECT points FROM users WHERE id = $1"
		if lock {
			sql += " FOR UPDATE"
		}
		if err := q.QueryRow(ctx, sql, ownerID).Scan(&balance); err != nil {
			return nil, &httpErr{Status: 500, Msg: "读取积分失败"}
		}
		if pointsUsed > balance {
			return nil, badRequestErr("积分余额不足")
		}
		maxPoints := int(remaining * pointsPerYuan)
		if pointsUsed > maxPoints {
			pointsUsed = maxPoints
		}
		pointsDiscount = float64(pointsUsed) / pointsPerYuan
		if pointsDiscount > remaining {
			pointsDiscount = remaining
		}
	}
	totalAmount := remaining - pointsDiscount
	if totalAmount < 0 {
		totalAmount = 0
	}

	return &orderPricing{
		SitterName:      sitterName,
		TotalDays:       totalDays,
		EstimatedAmount: estimatedAmount,
		AddonLines:      lines,
		AddonAmount:     addonAmount,
		Subtotal:        subtotal,
		CouponTitle:     couponTitle,
		DiscountAmount:  discountAmount,
		PointsUsed:      pointsUsed,
		PointsDiscount:  pointsDiscount,
		TotalAmount:     totalAmount,
	}, nil
}
