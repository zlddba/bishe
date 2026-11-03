package handler

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

// pointsPerYuan 100 积分 = 1 元。
const pointsPerYuan = 100

// platformCommission 平台抽佣比例。
const platformCommission = 0.10

// ---------------- 个人中心 ----------------

// UpdateMyProfile handles PUT /api/v1/auth/profile
func UpdateMyProfile(c *gin.Context) {
	var in struct {
		Nickname     *string `json:"nickname" binding:"omitempty,max=64"`
		Phone        *string `json:"phone" binding:"omitempty,max=20"`
		Email        *string `json:"email" binding:"omitempty,max=128"`
		AvatarURL    *string `json:"avatar_url"`
		Address      *string `json:"address" binding:"omitempty,max=255"`
		ContactName  *string `json:"contact_name" binding:"omitempty,max=64"`
		ContactPhone *string `json:"contact_phone" binding:"omitempty,max=20"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Phone != nil && !validPhone(*in.Phone) {
		BadRequest(c, "手机号格式不正确")
		return
	}
	if in.ContactPhone != nil && !validPhone(*in.ContactPhone) {
		BadRequest(c, "联系人手机号格式不正确")
		return
	}
	_, err := db.Pool.Exec(context.Background(), `
		UPDATE users SET
		  nickname = COALESCE($1, nickname), phone = COALESCE($2, phone),
		  email = COALESCE($3, email), avatar_url = COALESCE($4, avatar_url),
		  address = COALESCE($5, address), contact_name = COALESCE($6, contact_name),
		  contact_phone = COALESCE($7, contact_phone), updated_at = now()
		WHERE id = $8 AND deleted_at IS NULL`,
		in.Nickname, in.Phone, in.Email, in.AvatarURL, in.Address, in.ContactName, in.ContactPhone,
		c.GetInt64("user_id"))
	if err != nil {
		if strings.Contains(err.Error(), "uq_users_phone") {
			Conflict(c, "手机号已被其他账号使用")
			return
		}
		InternalError(c, "更新资料失败: "+err.Error())
		return
	}
	OK(c, gin.H{"updated": true})
}

// UpdatePetVaccine 主人上传/更新宠物免疫证明，提交后进入待核验状态。
func UpdatePetVaccine(c *gin.Context) {
	petID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		VaccineURLs []string `json:"vaccine_urls" binding:"required,min=1,max=6"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请上传 1-6 张免疫证明图片")
		return
	}
	for _, u := range in.VaccineURLs {
		if len(u) == 0 || len(u) > 500 {
			BadRequest(c, "免疫证明图片地址无效")
			return
		}
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE pets SET vaccine_urls = $1, vaccine_status = 'pending', vaccine_note = NULL,
		       vaccine_verified_by = NULL, vaccine_verified_at = NULL, updated_at = now()
		WHERE id = $2 AND owner_id = $3 AND deleted_at IS NULL`,
		in.VaccineURLs, petID, c.GetInt64("user_id"))
	if err != nil {
		InternalError(c, "提交免疫证明失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "宠物不存在")
		return
	}
	OK(c, gin.H{"pet_id": petID, "vaccine_status": "pending"})
}

// ChangeMyPassword handles PUT /api/v1/auth/password
func ChangeMyPassword(c *gin.Context) {
	var in struct {
		OldPassword string `json:"old_password" binding:"required"`
		NewPassword string `json:"new_password" binding:"required,min=8,max=128"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "新密码至少 8 位")
		return
	}
	userID := c.GetInt64("user_id")
	var hash string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT password_hash FROM users WHERE id = $1 AND deleted_at IS NULL", userID).Scan(&hash); err != nil {
		NotFound(c, "用户不存在")
		return
	}
	if bcrypt.CompareHashAndPassword([]byte(hash), []byte(in.OldPassword)) != nil {
		BadRequest(c, "原密码不正确")
		return
	}
	newHash, err := bcrypt.GenerateFromPassword([]byte(in.NewPassword), bcrypt.DefaultCost)
	if err != nil {
		InternalError(c, "密码加密失败")
		return
	}
	_, err = db.Pool.Exec(context.Background(),
		"UPDATE users SET password_hash = $1, updated_at = now() WHERE id = $2", string(newHash), userID)
	if err != nil {
		InternalError(c, "修改密码失败")
		return
	}
	// 改密后其他设备上的刷新令牌全部失效
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL", userID)
	OK(c, gin.H{"updated": true})
}

// ---------------- 收藏 / 对比 ----------------

func ListFavorites(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(), `
		SELECT s.id, s.name, s.type::text, s.daily_price, s.avg_score, s.review_count,
		       COALESCE(s.address, ''), COALESCE(s.cover_photo_url, ''), s.status::text,
		       to_char(f.created_at, 'YYYY-MM-DD HH24:MI')
		FROM favorites f JOIN sitters s ON s.id = f.sitter_id
		WHERE f.user_id = $1 AND s.deleted_at IS NULL
		ORDER BY f.id DESC`, c.GetInt64("user_id"))
	if err != nil {
		InternalError(c, "查询收藏失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, reviews int64
		var name, stype, address, cover, status, at string
		var price, score float64
		if err := rows.Scan(&id, &name, &stype, &price, &score, &reviews, &address, &cover, &status, &at); err == nil {
			items = append(items, gin.H{"sitter_id": id, "name": name, "type": stype,
				"daily_price": price, "avg_score": score, "review_count": reviews,
				"address": address, "cover_photo_url": cover, "status": status, "created_at": at})
		}
	}
	OK(c, gin.H{"items": items})
}

func AddFavorite(c *gin.Context) {
	var in struct {
		SitterID int64 `json:"sitter_id" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "缺少机构ID")
		return
	}
	_, err := db.Pool.Exec(context.Background(),
		"INSERT INTO favorites (user_id, sitter_id) VALUES ($1, $2) ON CONFLICT DO NOTHING",
		c.GetInt64("user_id"), in.SitterID)
	if err != nil {
		BadRequest(c, "收藏失败，机构可能不存在")
		return
	}
	Created(c, "已收藏", gin.H{"sitter_id": in.SitterID})
}

func RemoveFavorite(c *gin.Context) {
	sitterID, _ := strconv.ParseInt(c.Param("sitter_id"), 10, 64)
	_, err := db.Pool.Exec(context.Background(),
		"DELETE FROM favorites WHERE user_id = $1 AND sitter_id = $2", c.GetInt64("user_id"), sitterID)
	if err != nil {
		InternalError(c, "取消收藏失败")
		return
	}
	OK(c, gin.H{"sitter_id": sitterID})
}

// CompareFavorites 返回最多 4 家机构的横向对比数据。
func CompareFavorites(c *gin.Context) {
	ids := make([]int64, 0, 4)
	for _, part := range strings.Split(c.Query("ids"), ",") {
		if v, err := strconv.ParseInt(strings.TrimSpace(part), 10, 64); err == nil && v > 0 {
			ids = append(ids, v)
			if len(ids) == 4 {
				break
			}
		}
	}
	if len(ids) == 0 {
		BadRequest(c, "请提供要对比的机构ID，例如 ids=1,2")
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, name, type::text, daily_price, visit_price, price_mode::text, capacity,
		       avg_score, review_count, COALESCE(address, ''), status::text
		FROM sitters WHERE id = ANY($1) AND deleted_at IS NULL`, ids)
	if err != nil {
		InternalError(c, "查询对比数据失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0, len(ids))
	for rows.Next() {
		var id int64
		var name, stype, mode, address, status string
		var daily, visit float64
		var capacity, reviews int64
		var score float64
		if err := rows.Scan(&id, &name, &stype, &daily, &visit, &mode, &capacity, &score, &reviews, &address, &status); err == nil {
			items = append(items, gin.H{"sitter_id": id, "name": name, "type": stype,
				"daily_price": daily, "visit_price": visit, "price_mode": mode, "capacity": capacity,
				"avg_score": score, "review_count": reviews, "address": address, "status": status})
		}
	}
	OK(c, gin.H{"items": items})
}

// ---------------- 积分 ----------------

func MyPoints(c *gin.Context) {
	userID := c.GetInt64("user_id")
	var balance int
	_ = db.Pool.QueryRow(context.Background(), "SELECT points FROM users WHERE id = $1", userID).Scan(&balance)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT change_amount, balance, reason, to_char(created_at, 'YYYY-MM-DD HH24:MI')
		FROM points_ledger WHERE user_id = $1 ORDER BY id DESC LIMIT 50`, userID)
	if err != nil {
		InternalError(c, "查询积分失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var change, bal int
		var reason, at string
		if err := rows.Scan(&change, &bal, &reason, &at); err == nil {
			items = append(items, gin.H{"change": change, "balance": bal, "reason": reason, "created_at": at})
		}
	}
	OK(c, gin.H{"balance": balance, "rate": pointsPerYuan, "items": items})
}

// ---------------- 优惠券 ----------------

func MyCoupons(c *gin.Context) {
	userID := c.GetInt64("user_id")
	rows, err := db.Pool.Query(context.Background(), `
		SELECT uc.id, c.id, c.code, c.title, c.type, c.value, c.min_amount,
		       uc.status, COALESCE(to_char(c.ends_at, 'YYYY-MM-DD'), ''),
		       COALESCE(c.sitter_id, 0), COALESCE(s.name, '')
		FROM user_coupons uc
		JOIN coupons c ON c.id = uc.coupon_id
		LEFT JOIN sitters s ON s.id = c.sitter_id
		WHERE uc.user_id = $1 ORDER BY uc.status = 'unused' DESC, uc.id DESC`, userID)
	if err != nil {
		InternalError(c, "查询优惠券失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, couponID int64
		var code, title, ctype, status, ends, issuer string
		var sitID int64
		var value, minAmount float64
		if err := rows.Scan(&id, &couponID, &code, &title, &ctype, &value, &minAmount, &status, &ends,
			&sitID, &issuer); err == nil {
			items = append(items, gin.H{"id": id, "coupon_id": couponID, "code": code, "title": title,
				"type": ctype, "value": value, "min_amount": minAmount, "status": status, "ends_at": ends,
				"sitter_id": sitID, "issuer": issuer})
		}
	}

	// 可领取的券：平台官方券 + 在营机构发布的机构专属券
	avail, _ := db.Pool.Query(context.Background(), `
		SELECT c.id, c.code, c.title, c.type, c.value, c.min_amount, COALESCE(to_char(c.ends_at, 'YYYY-MM-DD'), ''),
		       COALESCE(c.sitter_id, 0), COALESCE(s.name, '')
		FROM coupons c LEFT JOIN sitters s ON s.id = c.sitter_id
		WHERE c.enabled AND c.starts_at <= now() AND (c.ends_at IS NULL OR c.ends_at >= now())
		  AND (c.total_qty = 0 OR c.claimed_qty < c.total_qty)
		  AND (c.sitter_id IS NULL OR s.status = 'active')
		  AND NOT EXISTS (SELECT 1 FROM user_coupons uc WHERE uc.coupon_id = c.id AND uc.user_id = $1)
		ORDER BY c.id DESC LIMIT 20`, userID)
	claimable := make([]gin.H, 0)
	if avail != nil {
		defer avail.Close()
		for avail.Next() {
			var id int64
			var code, title, ctype, ends, issuer string
			var sitID int64
			var value, minAmount float64
			if err := avail.Scan(&id, &code, &title, &ctype, &value, &minAmount, &ends, &sitID, &issuer); err == nil {
				claimable = append(claimable, gin.H{"coupon_id": id, "code": code, "title": title,
					"type": ctype, "value": value, "min_amount": minAmount, "ends_at": ends,
					"sitter_id": sitID, "issuer": issuer})
			}
		}
	}
	OK(c, gin.H{"items": items, "claimable": claimable})
}

// DeleteMyCoupon handles DELETE /api/v1/pet-owner/coupons/mine/:id
// 删除券包中的优惠券（:id 为 user_coupons 记录 id）；已使用的券保留为消费记录，不可删除。
func DeleteMyCoupon(c *gin.Context) {
	userID := c.GetInt64("user_id")
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "优惠券编号无效")
		return
	}

	var status string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT status FROM user_coupons WHERE id = $1 AND user_id = $2", id, userID,
	).Scan(&status); err != nil {
		NotFound(c, "优惠券不存在")
		return
	}
	if status == "used" {
		Conflict(c, "已使用的优惠券保留为消费记录，无法删除")
		return
	}

	if _, err := db.Pool.Exec(context.Background(),
		"DELETE FROM user_coupons WHERE id = $1 AND user_id = $2", id, userID); err != nil {
		InternalError(c, "删除优惠券失败")
		return
	}

	OK(c, gin.H{"deleted": true})
}
func ClaimCoupon(c *gin.Context) {
	couponID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	userID := c.GetInt64("user_id")
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "领取失败")
		return
	}
	defer tx.Rollback(context.Background())

	var total, claimed, perUser int
	var enabled bool
	err = tx.QueryRow(context.Background(), `
		SELECT total_qty, claimed_qty, per_user, enabled FROM coupons
		WHERE id = $1 AND starts_at <= now() AND (ends_at IS NULL OR ends_at >= now())
		FOR UPDATE`, couponID).Scan(&total, &claimed, &perUser, &enabled)
	if err != nil || !enabled {
		BadRequest(c, "优惠券不存在或已过期")
		return
	}
	if total > 0 && claimed >= total {
		Conflict(c, "优惠券已领完")
		return
	}
	var mine int
	_ = tx.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM user_coupons WHERE coupon_id = $1 AND user_id = $2", couponID, userID).Scan(&mine)
	if mine >= perUser {
		Conflict(c, "您已领取过该优惠券")
		return
	}
	if _, err := tx.Exec(context.Background(),
		"INSERT INTO user_coupons (coupon_id, user_id) VALUES ($1, $2)", couponID, userID); err != nil {
		Conflict(c, "领取失败，可能已领取")
		return
	}
	if _, err := tx.Exec(context.Background(),
		"UPDATE coupons SET claimed_qty = claimed_qty + 1 WHERE id = $1", couponID); err != nil {
		InternalError(c, "领取失败")
		return
	}
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "领取失败")
		return
	}
	Created(c, "领取成功", gin.H{"coupon_id": couponID})
}

// ---------------- 容量日历 ----------------

func capacityRange(c *gin.Context) (time.Time, time.Time, bool) {
	fromStr := c.DefaultQuery("from", time.Now().Format("2006-01-02"))
	toStr := c.DefaultQuery("to", time.Now().AddDate(0, 0, 30).Format("2006-01-02"))
	from, err1 := time.Parse("2006-01-02", fromStr)
	to, err2 := time.Parse("2006-01-02", toStr)
	if err1 != nil || err2 != nil || to.Before(from) {
		BadRequest(c, "日期范围无效，格式 YYYY-MM-DD")
		return from, to, false
	}
	if to.Sub(from) > 90*24*time.Hour {
		BadRequest(c, "单次最多查询 90 天")
		return from, to, false
	}
	return from, to, true
}

// SitterCapacityPublic handles GET /api/v1/pet-owner/sitters/:id/capacity
func SitterCapacityPublic(c *gin.Context) {
	sitterID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	if sitterID == 0 {
		BadRequest(c, "无效的机构ID")
		return
	}
	// 封禁/下架机构对主人不可见
	var isActive bool
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL)",
		sitterID).Scan(&isActive); err != nil || !isActive {
		NotFound(c, "机构不存在或已下架")
		return
	}
	respondSitterCapacity(c, sitterID)
}

// MySitterCapacity handles GET /api/v1/sitter/capacity
func MySitterCapacity(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	respondSitterCapacity(c, sitterID)
}

func respondSitterCapacity(c *gin.Context, sitterID int64) {
	from, to, ok := capacityRange(c)
	if !ok {
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT d::date::text, COALESCE(c.used, 0), COALESCE(c.capacity, s.capacity)
		FROM sitters s
		CROSS JOIN generate_series($2::date, $3::date, interval '1 day') d
		LEFT JOIN sitter_daily_capacity c ON c.sitter_id = s.id AND c.cap_date = d::date
		WHERE s.id = $1 AND s.deleted_at IS NULL
		ORDER BY d`, sitterID, from, to)
	if err != nil {
		InternalError(c, "查询容量失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var date string
		var used, capacity int
		if err := rows.Scan(&date, &used, &capacity); err == nil {
			available := capacity - used
			if available < 0 {
				available = 0
			}
			items = append(items, gin.H{"date": date, "used": used, "capacity": capacity,
				"available": available, "full": available == 0})
		}
	}
	OK(c, gin.H{"sitter_id": sitterID, "items": items})
}

// SetSitterCapacity handles PUT /api/v1/sitter/capacity
func SetSitterCapacity(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	var in struct {
		Date     string `json:"date" binding:"required"`
		Capacity int    `json:"capacity" binding:"required,min=1,max=200"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	date, err := time.Parse("2006-01-02", in.Date)
	if err != nil {
		BadRequest(c, "日期格式应为 YYYY-MM-DD")
		return
	}
	// 调整后容量不能低于当日已占用数
	var used int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COALESCE(used, 0) FROM sitter_daily_capacity WHERE sitter_id = $1 AND cap_date = $2",
		sitterID, date).Scan(&used); err != nil {
		used = 0
	}
	if in.Capacity < used {
		BadRequest(c, fmt.Sprintf("当日已有 %d 只在住，容量不能低于该数", used))
		return
	}
	_, err = db.Pool.Exec(context.Background(), `
		INSERT INTO sitter_daily_capacity (sitter_id, cap_date, capacity)
		VALUES ($1, $2, $3)
		ON CONFLICT (sitter_id, cap_date) DO UPDATE SET capacity = EXCLUDED.capacity, updated_at = now()`,
		sitterID, date, in.Capacity)
	if err != nil {
		InternalError(c, "设置容量失败: "+err.Error())
		return
	}
	OK(c, gin.H{"date": in.Date, "capacity": in.Capacity})
}

// ---------------- 节假日定价 / 加购 ----------------

func ListPriceRules(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, name, start_date::text, end_date::text, multiplier
		FROM sitter_price_rules WHERE sitter_id = $1 ORDER BY start_date DESC`, sitterID)
	if err != nil {
		InternalError(c, "查询定价规则失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var name, start, end string
		var multiplier float64
		if err := rows.Scan(&id, &name, &start, &end, &multiplier); err == nil {
			items = append(items, gin.H{"id": id, "name": name, "start_date": start,
				"end_date": end, "multiplier": multiplier})
		}
	}
	OK(c, gin.H{"items": items})
}

func CreatePriceRule(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	var in struct {
		Name       string  `json:"name" binding:"required,max=64"`
		StartDate  string  `json:"start_date" binding:"required"`
		EndDate    string  `json:"end_date" binding:"required"`
		Multiplier float64 `json:"multiplier" binding:"required,min=0.5,max=5"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	start, err1 := time.Parse("2006-01-02", in.StartDate)
	end, err2 := time.Parse("2006-01-02", in.EndDate)
	if err1 != nil || err2 != nil || end.Before(start) {
		BadRequest(c, "日期范围无效")
		return
	}
	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO sitter_price_rules (sitter_id, name, start_date, end_date, multiplier)
		VALUES ($1, $2, $3, $4, $5) RETURNING id`,
		sitterID, in.Name, start, end, in.Multiplier).Scan(&id)
	if err != nil {
		InternalError(c, "创建定价规则失败")
		return
	}
	Created(c, "已创建", gin.H{"id": id})
}

func DeletePriceRule(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	tag, err := db.Pool.Exec(context.Background(),
		"DELETE FROM sitter_price_rules WHERE id = $1 AND sitter_id = $2", id, sitterID)
	if err != nil {
		InternalError(c, "删除失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "规则不存在")
		return
	}
	OK(c, gin.H{"id": id})
}

func ListAddons(c *gin.Context) {
	var sitterID int64
	if idStr := c.Param("id"); idStr != "" {
		sitterID, _ = strconv.ParseInt(idStr, 10, 64)
		// 主人浏览路径：封禁/下架机构的加购服务不对外展示
		var isActive bool
		if err := db.Pool.QueryRow(context.Background(),
			"SELECT EXISTS(SELECT 1 FROM sitters WHERE id = $1 AND status = 'active' AND deleted_at IS NULL)",
			sitterID).Scan(&isActive); err != nil || !isActive {
			NotFound(c, "机构不存在或已下架")
			return
		}
	} else {
		var err error
		sitterID, err = mySitterID(c.GetInt64("user_id"))
		if err != nil {
			NotFound(c, "机构资料不存在")
			return
		}
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, name, price, unit, enabled FROM sitter_addons
		WHERE sitter_id = $1 AND enabled ORDER BY id`, sitterID)
	if err != nil {
		InternalError(c, "查询加购服务失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var name, unit string
		var price float64
		var enabled bool
		if err := rows.Scan(&id, &name, &price, &unit, &enabled); err == nil {
			items = append(items, gin.H{"id": id, "name": name, "price": price, "unit": unit, "enabled": enabled})
		}
	}
	OK(c, gin.H{"sitter_id": sitterID, "items": items})
}

func CreateAddon(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	var in struct {
		Name  string  `json:"name" binding:"required,max=64"`
		Price float64 `json:"price" binding:"required,min=0"`
		Unit  string  `json:"unit" binding:"omitempty,max=16"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Unit == "" {
		in.Unit = "per_order"
	}
	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO sitter_addons (sitter_id, name, price, unit) VALUES ($1, $2, $3, $4) RETURNING id`,
		sitterID, in.Name, in.Price, in.Unit).Scan(&id)
	if err != nil {
		InternalError(c, "创建加购服务失败")
		return
	}
	Created(c, "已创建", gin.H{"id": id})
}

func UpdateAddon(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Name    *string  `json:"name"`
		Price   *float64 `json:"price"`
		Unit    *string  `json:"unit"`
		Enabled *bool    `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效")
		return
	}
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE sitter_addons SET
		  name = COALESCE($1, name), price = COALESCE($2, price),
		  unit = COALESCE($3, unit), enabled = COALESCE($4, enabled)
		WHERE id = $5 AND sitter_id = $6`, in.Name, in.Price, in.Unit, in.Enabled, id, sitterID)
	if err != nil {
		InternalError(c, "更新失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "服务不存在")
		return
	}
	OK(c, gin.H{"id": id})
}

func DeleteAddon(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	if _, err := db.Pool.Exec(context.Background(),
		"UPDATE sitter_addons SET enabled = FALSE WHERE id = $1 AND sitter_id = $2", id, sitterID); err != nil {
		InternalError(c, "删除失败")
		return
	}
	OK(c, gin.H{"id": id, "enabled": false})
}

// ---------------- 机构收益 / 结算 ----------------

func SitterEarnings(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	from, to, ok := capacityRange(c)
	if !ok {
		return
	}
	var orders int
	var gross float64
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*), COALESCE(SUM(total_amount), 0) FROM orders
		WHERE sitter_id = $1 AND status = 'completed' AND deleted_at IS NULL
		  AND completed_at >= $2 AND completed_at < $3 + interval '1 day'`,
		sitterID, from, to).Scan(&orders, &gross)

	rows, err := db.Pool.Query(context.Background(), `
		SELECT o.order_no, o.completed_at::date::text, o.total_amount, u.username
		FROM orders o JOIN users u ON u.id = o.owner_id
		WHERE o.sitter_id = $1 AND o.status = 'completed' AND o.deleted_at IS NULL
		  AND o.completed_at >= $2 AND o.completed_at < $3 + interval '1 day'
		ORDER BY o.completed_at DESC LIMIT 200`, sitterID, from, to)
	if err != nil {
		InternalError(c, "查询收益失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var no, date, owner string
		var amount float64
		if err := rows.Scan(&no, &date, &amount, &owner); err == nil {
			items = append(items, gin.H{"order_no": no, "date": date, "amount": amount, "owner": owner})
		}
	}
	commission := gross * platformCommission
	OK(c, gin.H{
		"period": gin.H{"from": from.Format("2006-01-02"), "to": to.Format("2006-01-02")},
		"summary": gin.H{
			"order_count": orders, "gross_amount": gross,
			"commission_rate": platformCommission, "commission_amount": commission,
			"net_amount": gross - commission,
		},
		"items": items,
	})
}

func ListMySettlements(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, period_start::text, period_end::text, order_count, gross_amount,
		       commission_amount, net_amount, status,
		       to_char(applied_at, 'YYYY-MM-DD HH24:MI'), COALESCE(to_char(paid_at, 'YYYY-MM-DD HH24:MI'), '')
		FROM sitter_settlements WHERE sitter_id = $1 ORDER BY id DESC LIMIT 50`, sitterID)
	if err != nil {
		InternalError(c, "查询结算失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orders int64
		var start, end, status, applied, paid string
		var gross, commission, net float64
		if err := rows.Scan(&id, &start, &end, &orders, &gross, &commission, &net, &status, &applied, &paid); err == nil {
			items = append(items, gin.H{"id": id, "period_start": start, "period_end": end,
				"order_count": orders, "gross_amount": gross, "commission_amount": commission,
				"net_amount": net, "status": status, "applied_at": applied, "paid_at": paid})
		}
	}
	OK(c, gin.H{"items": items})
}

func ApplySettlement(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	var in struct {
		PeriodStart string `json:"period_start" binding:"required"`
		PeriodEnd   string `json:"period_end" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效")
		return
	}
	start, err1 := time.Parse("2006-01-02", in.PeriodStart)
	end, err2 := time.Parse("2006-01-02", in.PeriodEnd)
	if err1 != nil || err2 != nil || end.Before(start) {
		BadRequest(c, "日期范围无效")
		return
	}
	var overlap int
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FROM sitter_settlements
		WHERE sitter_id = $1 AND status IN ('pending','paid')
		  AND period_start <= $3 AND period_end >= $2`, sitterID, start, end).Scan(&overlap)
	if overlap > 0 {
		Conflict(c, "该时间段已申请过结算")
		return
	}
	var orders int
	var gross float64
	_ = db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*), COALESCE(SUM(total_amount), 0) FROM orders
		WHERE sitter_id = $1 AND status = 'completed' AND deleted_at IS NULL
		  AND completed_at >= $2 AND completed_at < $3 + interval '1 day'`,
		sitterID, start, end).Scan(&orders, &gross)
	if orders == 0 {
		BadRequest(c, "该时间段没有可结算的已完成订单")
		return
	}
	commission := gross * platformCommission
	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO sitter_settlements (sitter_id, period_start, period_end, order_count,
		      gross_amount, commission_amount, net_amount)
		VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
		sitterID, start, end, orders, gross, commission, gross-commission).Scan(&id)
	if err != nil {
		InternalError(c, "提交结算申请失败")
		return
	}
	Created(c, "已提交结算申请", gin.H{"id": id, "net_amount": gross - commission})
}

// ---------------- 售后工单（用户侧） ----------------

func CreateTicket(c *gin.Context) {
	var in struct {
		Category string `json:"category" binding:"omitempty,max=32"`
		Title    string `json:"title" binding:"required,max=128"`
		Content  string `json:"content" binding:"required,max=2000"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写标题和内容")
		return
	}
	if in.Category == "" {
		in.Category = "other"
	}
	userID := c.GetInt64("user_id")
	no := fmt.Sprintf("TK%s%04d", time.Now().Format("20060102150405"), time.Now().UnixNano()%10000)
	var id int64
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO support_tickets (ticket_no, user_id, category, title, content)
		VALUES ($1, $2, $3, $4, $5) RETURNING id`, no, userID, in.Category, in.Title, in.Content).Scan(&id)
	if err != nil {
		InternalError(c, "提交工单失败")
		return
	}
	_, _ = db.Pool.Exec(context.Background(), `
		INSERT INTO ticket_messages (ticket_id, sender_id, sender_role, content)
		VALUES ($1, $2, 'user', $3)`, id, userID, in.Content)
	Created(c, "工单已提交", gin.H{"id": id, "ticket_no": no})
}

func ListMyTickets(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(), `
		SELECT id, ticket_no, category, title, status, priority,
		       to_char(created_at, 'YYYY-MM-DD HH24:MI')
		FROM support_tickets WHERE user_id = $1 ORDER BY id DESC LIMIT 50`, c.GetInt64("user_id"))
	if err != nil {
		InternalError(c, "查询工单失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id int64
		var no, category, title, status, priority, at string
		if err := rows.Scan(&id, &no, &category, &title, &status, &priority, &at); err == nil {
			items = append(items, gin.H{"id": id, "ticket_no": no, "category": category,
				"title": title, "status": status, "priority": priority, "created_at": at})
		}
	}
	OK(c, gin.H{"items": items})
}

func GetMyTicket(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	userID := c.GetInt64("user_id")
	var no, title, status string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT ticket_no, title, status FROM support_tickets WHERE id = $1 AND user_id = $2", id, userID,
	).Scan(&no, &title, &status); err != nil {
		NotFound(c, "工单不存在")
		return
	}
	rows, err := db.Pool.Query(context.Background(), `
		SELECT sender_role, content, to_char(created_at, 'YYYY-MM-DD HH24:MI')
		FROM ticket_messages WHERE ticket_id = $1 ORDER BY id`, id)
	if err != nil {
		InternalError(c, "查询消息失败")
		return
	}
	defer rows.Close()
	msgs := make([]gin.H, 0)
	for rows.Next() {
		var role, body, at string
		if err := rows.Scan(&role, &body, &at); err == nil {
			msgs = append(msgs, gin.H{"sender_role": role, "content": body, "created_at": at})
		}
	}
	OK(c, gin.H{"id": id, "ticket_no": no, "title": title, "status": status, "messages": msgs})
}

func ReplyMyTicket(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Content string `json:"content" binding:"required,min=1,max=2000"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写内容")
		return
	}
	userID := c.GetInt64("user_id")
	var status string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT status FROM support_tickets WHERE id = $1 AND user_id = $2", id, userID).Scan(&status); err != nil {
		NotFound(c, "工单不存在")
		return
	}
	if status == "closed" {
		Conflict(c, "工单已关闭")
		return
	}
	if _, err := db.Pool.Exec(context.Background(), `
		INSERT INTO ticket_messages (ticket_id, sender_id, sender_role, content)
		VALUES ($1, $2, 'user', $3)`, id, userID, in.Content); err != nil {
		InternalError(c, "回复失败")
		return
	}
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE support_tickets SET status = 'open', updated_at = now() WHERE id = $1", id)
	Created(c, "已回复", gin.H{"id": id})
}

// ---------------- 动态点赞 / 留言 ----------------

func LikeDynamic(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	_, err := db.Pool.Exec(context.Background(),
		"INSERT INTO dynamic_likes (dynamic_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING",
		id, c.GetInt64("user_id"))
	if err != nil {
		BadRequest(c, "点赞失败")
		return
	}
	OK(c, gin.H{"dynamic_id": id, "liked": true})
}

func UnlikeDynamic(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	_, _ = db.Pool.Exec(context.Background(),
		"DELETE FROM dynamic_likes WHERE dynamic_id = $1 AND user_id = $2", id, c.GetInt64("user_id"))
	OK(c, gin.H{"dynamic_id": id, "liked": false})
}

func ListDynamicComments(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	rows, err := db.Pool.Query(context.Background(), `
		SELECT m.id, COALESCE(u.nickname, u.username), m.content, to_char(m.created_at, 'YYYY-MM-DD HH24:MI')
		FROM dynamic_comments m JOIN users u ON u.id = m.user_id
		WHERE m.dynamic_id = $1 AND m.status = 'visible' ORDER BY m.id`, id)
	if err != nil {
		InternalError(c, "查询留言失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var mid int64
		var user, content, at string
		if err := rows.Scan(&mid, &user, &content, &at); err == nil {
			items = append(items, gin.H{"id": mid, "user": user, "content": content, "created_at": at})
		}
	}
	var likes int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM dynamic_likes WHERE dynamic_id = $1", id).Scan(&likes)
	OK(c, gin.H{"dynamic_id": id, "likes": likes, "items": items})
}

func CreateDynamicComment(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Content string `json:"content" binding:"required,min=1,max=500"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写留言内容")
		return
	}
	var mid int64
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO dynamic_comments (dynamic_id, user_id, content) VALUES ($1, $2, $3) RETURNING id`,
		id, c.GetInt64("user_id"), in.Content).Scan(&mid)
	if err != nil {
		BadRequest(c, "留言失败")
		return
	}
	Created(c, "已留言", gin.H{"id": mid})
}

// ---------------- 评价追评 ----------------

func FollowUpReview(c *gin.Context) {
	reviewID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Content string `json:"content" binding:"required,min=2,max=1000"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写追评内容")
		return
	}
	userID := c.GetInt64("user_id")
	var ownerID int64
	var createdAt time.Time
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT owner_id, created_at FROM reviews WHERE id = $1", reviewID).Scan(&ownerID, &createdAt); err != nil {
		NotFound(c, "评价不存在")
		return
	}
	if ownerID != userID {
		Forbidden(c, "只能追评自己的评价")
		return
	}
	if time.Since(createdAt) > 24*time.Hour {
		BadRequest(c, "评价超过 24 小时，不能追评")
		return
	}
	var id int64
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO review_followups (review_id, owner_id, content) VALUES ($1, $2, $3) RETURNING id`,
		reviewID, userID, in.Content).Scan(&id)
	if err != nil {
		InternalError(c, "追评失败")
		return
	}
	Created(c, "追评成功", gin.H{"id": id})
}

// ---------------- 退款申请（用户侧） ----------------

func RequestRefund(c *gin.Context) {
	orderID, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	var in struct {
		Reason string   `json:"reason" binding:"required,min=2,max=500"`
		Amount *float64 `json:"amount" binding:"omitempty,min=0.01"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写退款原因")
		return
	}
	userID := c.GetInt64("user_id")
	var total float64
	var paymentStatus string
	if err := db.Pool.QueryRow(context.Background(), `
		SELECT total_amount, payment_status::text FROM orders
		WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL`, orderID, userID).Scan(&total, &paymentStatus); err != nil {
		NotFound(c, "订单不存在")
		return
	}
	if paymentStatus != "paid" {
		BadRequest(c, "只有已支付订单可以申请退款")
		return
	}
	amount := total
	if in.Amount != nil {
		amount = *in.Amount
	}
	if amount <= 0 || amount > total {
		BadRequest(c, "退款金额超出订单金额")
		return
	}
	var pending int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM refund_requests WHERE order_id = $1 AND status = 'pending'", orderID).Scan(&pending)
	if pending > 0 {
		Conflict(c, "该订单已有待处理的退款申请")
		return
	}
	var id int64
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO refund_requests (order_id, owner_id, amount, reason)
		VALUES ($1, $2, $3, $4) RETURNING id`, orderID, userID, amount, in.Reason).Scan(&id)
	if err != nil {
		InternalError(c, "提交退款申请失败")
		return
	}
	Created(c, "退款申请已提交，等待平台审核", gin.H{"id": id, "amount": amount})
}

func ListMyRefundRequests(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(), `
		SELECT r.id, r.order_id, o.order_no, r.amount, r.reason, r.status,
		       COALESCE(r.admin_note, ''), to_char(r.created_at, 'YYYY-MM-DD HH24:MI')
		FROM refund_requests r JOIN orders o ON o.id = r.order_id
		WHERE r.owner_id = $1 ORDER BY r.id DESC LIMIT 50`, c.GetInt64("user_id"))
	if err != nil {
		InternalError(c, "查询退款申请失败")
		return
	}
	defer rows.Close()
	items := make([]gin.H, 0)
	for rows.Next() {
		var id, orderID int64
		var no, reason, status, note, at string
		var amount float64
		if err := rows.Scan(&id, &orderID, &no, &amount, &reason, &status, &note, &at); err == nil {
			items = append(items, gin.H{"id": id, "order_id": orderID, "order_no": no,
				"amount": amount, "reason": reason, "status": status, "admin_note": note, "created_at": at})
		}
	}
	OK(c, gin.H{"items": items})
}

// mySitterID 取当前登录机构账号对应的机构ID。
func mySitterID(userID int64) (int64, error) {
	var id int64
	err := db.Pool.QueryRow(context.Background(),
		"SELECT id FROM sitters WHERE user_id = $1 AND deleted_at IS NULL", userID).Scan(&id)
	return id, err
}
