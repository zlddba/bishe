package handler

import (
	"context"
	"fmt"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// AdminStats handles GET /api/v1/admin/stats
// Returns aggregated data for dashboard charts
func AdminStats(c *gin.Context) {
	ctx := context.Background()
	result := gin.H{}

	// 1. Overview numbers
	var userCount, sitterCount, orderCount, activeOrderCount int
	_ = db.Pool.QueryRow(ctx, "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL").Scan(&userCount)
	_ = db.Pool.QueryRow(ctx, "SELECT COUNT(*) FROM sitters WHERE deleted_at IS NULL AND status = 'active'").Scan(&sitterCount)
	_ = db.Pool.QueryRow(ctx, "SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL").Scan(&orderCount)
	_ = db.Pool.QueryRow(ctx, "SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND status IN ('confirmed','accepted','staying')").Scan(&activeOrderCount)

	result["overview"] = gin.H{
		"users":         userCount,
		"sitters":       sitterCount,
		"orders":        orderCount,
		"active_orders": activeOrderCount,
	}

	// 2. Order status distribution (pie chart)
	statusRows, _ := db.Pool.Query(ctx,
		`SELECT status::text, COUNT(*) FROM orders WHERE deleted_at IS NULL GROUP BY status ORDER BY COUNT(*) DESC`)
	statusData := make([]gin.H, 0)
	for statusRows.Next() {
		var s string
		var cnt int
		_ = statusRows.Scan(&s, &cnt)
		statusData = append(statusData, gin.H{"name": statusLabelZh(s), "value": cnt})
	}
	statusRows.Close()
	result["order_status_dist"] = statusData

	// 3. Revenue trend last 14 days (line chart)
	now := time.Now()
	startDate := now.AddDate(0, 0, -13)
	revenueRows, _ := db.Pool.Query(ctx,
		`SELECT d::date::text AS date, COALESCE(SUM(o.total_amount), 0) AS revenue, COUNT(o.id) AS orders
		 FROM generate_series($1::date, $2::date, '1 day') AS d
		 LEFT JOIN orders o ON o.created_at::date = d::date AND o.deleted_at IS NULL AND o.payment_status = 'paid'
		 GROUP BY d::date ORDER BY d::date`,
		startDate.Format("2006-01-02"), now.Format("2006-01-02"))
	revenueData := make([]gin.H, 0)
	for revenueRows.Next() {
		var dateStr string
		var revenue float64
		var orders int
		_ = revenueRows.Scan(&dateStr, &revenue, &orders)
		revenueData = append(revenueData, gin.H{
			"date":    dateStr,
			"revenue": revenue,
			"orders":  orders,
		})
	}
	revenueRows.Close()
	result["revenue_trend"] = revenueData

	// 4. Sitter type distribution (pie chart)
	typeRows, _ := db.Pool.Query(ctx,
		`SELECT type::text, COUNT(*) FROM sitters WHERE deleted_at IS NULL AND status = 'active' GROUP BY type`)
	typeData := make([]gin.H, 0)
	for typeRows.Next() {
		var t string
		var cnt int
		_ = typeRows.Scan(&t, &cnt)
		typeData = append(typeData, gin.H{"name": sitterTypeLabelZh(t), "value": cnt})
	}
	typeRows.Close()
	result["sitter_type_dist"] = typeData

	// 5. User role distribution (pie chart)
	roleRows, _ := db.Pool.Query(ctx,
		`SELECT role::text, COUNT(*) FROM users WHERE deleted_at IS NULL GROUP BY role`)
	roleData := make([]gin.H, 0)
	for roleRows.Next() {
		var r string
		var cnt int
		_ = roleRows.Scan(&r, &cnt)
		roleData = append(roleData, gin.H{"name": roleLabelZh(r), "value": cnt})
	}
	roleRows.Close()
	result["user_role_dist"] = roleData

	// 6. Top rated sitters (bar chart)
	topRows, _ := db.Pool.Query(ctx,
		`SELECT name, avg_score FROM sitters WHERE deleted_at IS NULL AND status = 'active' AND review_count > 0
		 ORDER BY avg_score DESC, review_count DESC LIMIT 10`)
	topData := make([]gin.H, 0)
	for topRows.Next() {
		var name string
		var score float64
		_ = topRows.Scan(&name, &score)
		topData = append(topData, gin.H{"name": name, "score": score})
	}
	topRows.Close()
	result["top_sitters"] = topData

	// 7. Capacity alerts: sitters with high utilization in next 7 days
	alertRows, _ := db.Pool.Query(ctx,
		`SELECT s.name, c.cap_date::text, c.used, c.capacity
		 FROM sitter_daily_capacity c
		 JOIN sitters s ON s.id = c.sitter_id
		 WHERE s.status = 'active' AND s.deleted_at IS NULL
		 AND c.cap_date >= CURRENT_DATE AND c.cap_date <= CURRENT_DATE + INTERVAL '7 days'
		 AND c.used::float / c.capacity >= 0.8
		 ORDER BY c.used::float / c.capacity DESC LIMIT 10`)
	alertData := make([]gin.H, 0)
	for alertRows.Next() {
		var name, dateStr string
		var used, capacity int
		_ = alertRows.Scan(&name, &dateStr, &used, &capacity)
		alertData = append(alertData, gin.H{
			"name":        name,
			"date":        dateStr,
			"used":        used,
			"capacity":    capacity,
			"utilization": fmt.Sprintf("%.0f%%", float64(used)/float64(capacity)*100),
		})
	}
	alertRows.Close()
	result["capacity_alerts"] = alertData

	// 8. Low score reviews (risk alerts)
	reviewRows, _ := db.Pool.Query(ctx,
		`SELECT r.id, s.name, u.username, r.overall_score, r.owner_comment, r.created_at::text
		 FROM reviews r
		 JOIN sitters s ON s.id = r.sitter_id
		 JOIN users u ON u.id = r.owner_id
		 WHERE r.overall_score <= 3 AND r.is_visible = TRUE
		 ORDER BY r.created_at DESC LIMIT 5`)
	reviewAlerts := make([]gin.H, 0)
	for reviewRows.Next() {
		var id int64
		var sitterName, userName, createdAt string
		var score int16
		var comment *string
		_ = reviewRows.Scan(&id, &sitterName, &userName, &score, &comment, &createdAt)
		reviewAlerts = append(reviewAlerts, gin.H{
			"id":          id,
			"sitter_name": sitterName,
			"reviewer":    userName,
			"score":       score,
			"comment":     comment,
			"created_at":  createdAt,
		})
	}
	reviewRows.Close()
	result["review_alerts"] = reviewAlerts

	// 9. Capacity calendar: next 14 days for top 8 active sitters
	dates := make([]string, 0, 14)
	for i := 0; i < 14; i++ {
		dates = append(dates, now.AddDate(0, 0, i).Format("01-02"))
	}

	calRows, _ := db.Pool.Query(ctx,
		`WITH top AS (
		   SELECT id, name, capacity FROM sitters
		   WHERE deleted_at IS NULL AND status = 'active'
		   ORDER BY capacity DESC, id ASC LIMIT 8
		 )
		 SELECT t.name, COALESCE(c.used, 0), COALESCE(c.capacity, t.capacity), t.id
		 FROM top t
		 CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + INTERVAL '13 days', '1 day') AS d
		 LEFT JOIN sitter_daily_capacity c ON c.sitter_id = t.id AND c.cap_date = d::date
		 ORDER BY t.capacity DESC, t.id, d`)
	type calSitter struct {
		ID     int64
		Name   string
		Values []int
	}
	calOrder := make([]*calSitter, 0)
	calIndex := map[int64]*calSitter{}
	for calRows.Next() {
		var name string
		var used, capacity int
		var sid int64
		if err := calRows.Scan(&name, &used, &capacity, &sid); err != nil {
			continue
		}
		item, ok := calIndex[sid]
		if !ok {
			item = &calSitter{ID: sid, Name: name, Values: make([]int, 0, 14)}
			calIndex[sid] = item
			calOrder = append(calOrder, item)
		}
		pct := 0
		if capacity > 0 {
			pct = int(float64(used)/float64(capacity)*100 + 0.5)
		}
		if pct > 100 {
			pct = 100
		}
		item.Values = append(item.Values, pct)
	}
	calRows.Close()

	calSitters := make([]gin.H, 0, len(calOrder))
	for _, item := range calOrder {
		calSitters = append(calSitters, gin.H{"name": item.Name, "values": item.Values})
	}
	result["capacity_calendar"] = gin.H{
		"dates":   dates,
		"sitters": calSitters,
	}

	// 10. Sitter revenue ranking (paid orders)
	revRows, _ := db.Pool.Query(ctx,
		`SELECT s.name, COALESCE(SUM(o.total_amount), 0) AS revenue, COUNT(o.id) AS orders
		 FROM sitters s
		 LEFT JOIN orders o ON o.sitter_id = s.id AND o.deleted_at IS NULL AND o.payment_status = 'paid'
		 WHERE s.deleted_at IS NULL
		 GROUP BY s.id, s.name
		 ORDER BY revenue DESC, orders DESC LIMIT 8`)
	revData := make([]gin.H, 0)
	for revRows.Next() {
		var name string
		var revenue float64
		var orders int
		if err := revRows.Scan(&name, &revenue, &orders); err != nil {
			continue
		}
		revData = append(revData, gin.H{"name": name, "revenue": revenue, "orders": orders})
	}
	revRows.Close()
	result["sitter_revenue"] = revData

	OK(c, result)
}

func statusLabelZh(s string) string {
	return map[string]string{
		"pending_pay": "待支付", "confirmed": "已确认", "accepted": "已接单",
		"staying": "寄养中", "completed": "已完成", "evaluated": "已评价",
		"cancelled": "已取消", "rejected": "已拒绝",
	}[s]
}

func sitterTypeLabelZh(t string) string {
	return map[string]string{"home": "家庭寄养", "shop": "门店寄养", "visit": "上门喂养"}[t]
}

func roleLabelZh(r string) string {
	return map[string]string{"owner": "主人", "sitter": "机构", "admin": "管理员"}[r]
}
