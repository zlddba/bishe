package handler

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// 机构优惠券：机构自助发布与管理的券，仅限在本机构下单时核销。
type sitterCouponInput struct {
	Title     string  `json:"title" binding:"required,max=128"`
	Type      string  `json:"type" binding:"required,oneof=fixed percent"`
	Value     float64 `json:"value" binding:"required,gt=0"`
	MinAmount float64 `json:"min_amount"`
	TotalQty  int     `json:"total_qty"`
	PerUser   int     `json:"per_user"`
	StartsAt  string  `json:"starts_at"`
	EndsAt    string  `json:"ends_at"`
	Enabled   *bool   `json:"enabled"`
}

// ListSitterCoupons handles GET /api/v1/sitter/coupons
func ListSitterCoupons(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, code, title, type, value, min_amount, total_qty, claimed_qty, used_qty,
		       per_user, enabled, to_char(starts_at, 'YYYY-MM-DD'),
		       COALESCE(to_char(ends_at, 'YYYY-MM-DD'), '')
		FROM coupons WHERE sitter_id = $1 ORDER BY id DESC`, sitterID)
	if err != nil {
		InternalError(c, "查询优惠券失败")
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var (
			id, totalQty, claimed, used, perUser int64
			code, title, ctype, starts, ends     string
			value, minAmount                     float64
			on                                   bool
		)
		if err := rows.Scan(&id, &code, &title, &ctype, &value, &minAmount, &totalQty, &claimed,
			&used, &perUser, &on, &starts, &ends); err != nil {
			continue
		}
		items = append(items, gin.H{
			"id": id, "code": code, "title": title, "type": ctype, "value": value,
			"min_amount": minAmount, "total_qty": totalQty, "claimed_qty": claimed,
			"used_qty": used, "per_user": perUser, "enabled": on,
			"starts_at": starts, "ends_at": ends,
		})
	}
	OK(c, gin.H{"items": items})
}

// validateSitterCoupon 与平台券一致的字段校验；返回 nil 表示通过，否则返回错误提示。
func validateSitterCoupon(in sitterCouponInput) *string {
	if in.Type == "percent" && in.Value > 100 {
		msg := "折扣券面值应为 1-100 的百分比"
		return &msg
	}
	if in.TotalQty < 0 || in.PerUser < 0 {
		msg := "发放数量不能为负数"
		return &msg
	}
	return nil
}

// CreateSitterCoupon handles POST /api/v1/sitter/coupons
func CreateSitterCoupon(c *gin.Context) {
	userID := c.GetInt64("user_id")
	sitterID, err := mySitterID(userID)
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	var in sitterCouponInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if msg := validateSitterCoupon(in); msg != nil {
		BadRequest(c, *msg)
		return
	}
	start, end, err := couponTimes(couponInput{StartsAt: in.StartsAt, EndsAt: in.EndsAt})
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	if in.PerUser <= 0 {
		in.PerUser = 1
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}

	// 券码自动生成，机构侧不需要关心；唯一性按发放主体分区（见迁移 0009）
	code := fmt.Sprintf("SIT%d-%d", sitterID, time.Now().Unix())

	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO coupons (code, title, type, value, min_amount, total_qty, per_user,
		                     starts_at, ends_at, enabled, sitter_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11) RETURNING id`,
		code, in.Title, in.Type, in.Value, in.MinAmount, in.TotalQty, in.PerUser,
		start, end, enabled, sitterID).Scan(&id)
	if err != nil {
		InternalError(c, "发布优惠券失败")
		return
	}

	writeAudit(c, "SITTER_CREATE_COUPON", "coupon", id, fmt.Sprintf(`{"sitter_id": %d}`, sitterID))
	Created(c, "优惠券已发布", gin.H{"id": id})
}

// UpdateSitterCoupon handles PUT /api/v1/sitter/coupons/:id
func UpdateSitterCoupon(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的优惠券ID")
		return
	}

	var in sitterCouponInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if msg := validateSitterCoupon(in); msg != nil {
		BadRequest(c, *msg)
		return
	}
	start, end, err := couponTimes(couponInput{StartsAt: in.StartsAt, EndsAt: in.EndsAt})
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	if in.PerUser <= 0 {
		in.PerUser = 1
	}
	enabled := true
	if in.Enabled != nil {
		enabled = *in.Enabled
	}

	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE coupons SET title = $1, type = $2, value = $3, min_amount = $4, total_qty = $5,
		       per_user = $6, starts_at = $7, ends_at = $8, enabled = $9, updated_at = now()
		WHERE id = $10 AND sitter_id = $11`,
		in.Title, in.Type, in.Value, in.MinAmount, in.TotalQty,
		in.PerUser, start, end, enabled, id, sitterID)
	if err != nil {
		InternalError(c, "更新优惠券失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "优惠券不存在或不属于本机构")
		return
	}

	writeAudit(c, "SITTER_UPDATE_COUPON", "coupon", id, fmt.Sprintf(`{"sitter_id": %d}`, sitterID))
	OK(c, gin.H{"id": id})
}

// DeleteSitterCoupon handles DELETE /api/v1/sitter/coupons/:id
// 已被用户领取的券拒绝删除（user_coupons 级联删除会让用户券包丢失）；已被订单核销的券也拒绝删除。
func DeleteSitterCoupon(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的优惠券ID")
		return
	}

	var claimed int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM user_coupons WHERE coupon_id = $1", id).Scan(&claimed); err != nil {
		InternalError(c, "查询优惠券领取情况失败")
		return
	}
	if claimed > 0 {
		Conflict(c, "该优惠券已被用户领取，无法删除，请改为停用")
		return
	}

	var usedInOrder bool
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM orders WHERE coupon_id = $1)", id).Scan(&usedInOrder); err != nil {
		InternalError(c, "查询优惠券使用情况失败")
		return
	}
	if usedInOrder {
		Conflict(c, "该优惠券已被订单核销，无法删除，请改为停用")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM coupons WHERE id = $1 AND sitter_id = $2", id, sitterID)
	if err != nil {
		InternalError(c, "删除优惠券失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "优惠券不存在或不属于本机构")
		return
	}

	writeAudit(c, "SITTER_DELETE_COUPON", "coupon", id, fmt.Sprintf(`{"sitter_id": %d}`, sitterID))
	OK(c, gin.H{"id": id, "deleted": true})
}
