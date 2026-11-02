package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// ListDynamics handles GET /api/v1/pet-owner/orders/:id/dynamics
func ListDynamics(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	var total int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM dynamics WHERE order_id = $1", orderID).Scan(&total)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT id, order_id, sitter_id, author_id, content, photos, tags, created_at, updated_at
		 FROM dynamics WHERE order_id = $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
		orderID, perPage, offset)
	if err != nil {
		InternalError(c, "failed to query dynamics")
		return
	}
	defer rows.Close()

	items := make([]model.Dynamic, 0)
	for rows.Next() {
		var d model.Dynamic
		if err := rows.Scan(&d.ID, &d.OrderID, &d.SitterID, &d.AuthorID, &d.Content,
			&d.Photos, &d.Tags, &d.CreatedAt, &d.UpdatedAt); err != nil {
			continue
		}
		items = append(items, d)
	}

	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// PublishDynamic handles POST /api/v1/sitter/orders/:id/dynamics
func PublishDynamic(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	userID := c.GetInt64("user_id")

	var req model.CreateDynamicRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	photos := req.Photos
	if photos == nil {
		photos = []string{}
	}
	tags := req.Tags
	if tags == nil {
		tags = []string{}
	}

	// Verify sitter owns this order
	var sitterID int64
	err = db.Pool.QueryRow(context.Background(),
		`SELECT s.id FROM sitters s JOIN orders o ON o.sitter_id = s.id
		 WHERE o.id = $1 AND s.user_id = $2 AND o.deleted_at IS NULL`,
		orderID, userID).Scan(&sitterID)
	if err != nil {
		Forbidden(c, "not your order")
		return
	}

	var dynamic model.Dynamic
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO dynamics (order_id, sitter_id, author_id, content, photos, tags)
		 VALUES ($1, $2, $3, $4, $5, $6)
		 RETURNING id, order_id, sitter_id, author_id, content, photos, tags, created_at, updated_at`,
		orderID, sitterID, userID, req.Content, photos, tags,
	).Scan(&dynamic.ID, &dynamic.OrderID, &dynamic.SitterID, &dynamic.AuthorID, &dynamic.Content,
		&dynamic.Photos, &dynamic.Tags, &dynamic.CreatedAt, &dynamic.UpdatedAt)
	if err != nil {
		InternalError(c, "failed to publish dynamic")
		return
	}

	// Notify owner
	var ownerID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT owner_id FROM orders WHERE id = $1", orderID).Scan(&ownerID)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'dynamics_published', '新的寄养动态', $2, $3)",
		ownerID, fmt.Sprintf("您宠物的寄养动态 #%d 已发布。", dynamic.ID),
		fmt.Sprintf(`{"order_id": %d, "dynamic_id": %d}`, orderID, dynamic.ID))

	Created(c, "dynamic published", dynamic)
}

// GetReview handles GET /api/v1/pet-owner/orders/:id/review
func GetReview(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}

	var r model.Review
	err = db.Pool.QueryRow(context.Background(),
		`SELECT id, order_id, sitter_id, owner_id, overall_score, service_score, environment_score,
		 hygiene_score, owner_comment, owner_photos, reply_text, reply_at, is_visible, created_at, updated_at
		 FROM reviews WHERE order_id = $1`,
		orderID,
	).Scan(&r.ID, &r.OrderID, &r.SitterID, &r.OwnerID, &r.OverallScore, &r.ServiceScore,
		&r.EnvironmentScore, &r.HygieneScore, &r.OwnerComment, &r.OwnerPhotos,
		&r.ReplyText, &r.ReplyAt, &r.IsVisible, &r.CreatedAt, &r.UpdatedAt)

	if err != nil {
		NotFound(c, "no review found for this order")
		return
	}

	OK(c, r)
}

// SubmitReview handles POST /api/v1/pet-owner/orders/:id/review
func SubmitReview(c *gin.Context) {
	orderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid order id")
		return
	}
	ownerID := c.GetInt64("user_id")

	var req model.CreateReviewRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "invalid request: "+err.Error())
		return
	}

	photos := req.OwnerPhotos
	if photos == nil {
		photos = []string{}
	}

	// Verify order is completed and belongs to this owner
	var sitterID int64
	var status string
	err = db.Pool.QueryRow(context.Background(),
		"SELECT sitter_id, status FROM orders WHERE id = $1 AND owner_id = $2 AND deleted_at IS NULL",
		orderID, ownerID).Scan(&sitterID, &status)
	if err != nil {
		NotFound(c, "order not found")
		return
	}
	if status != "completed" {
		BadRequest(c, "can only review completed orders")
		return
	}

	var r model.Review
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO reviews (order_id, sitter_id, owner_id, overall_score, service_score, environment_score, hygiene_score, owner_comment, owner_photos)
		 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		 RETURNING id, order_id, sitter_id, owner_id, overall_score, service_score, environment_score, hygiene_score, owner_comment, owner_photos, reply_text, reply_at, is_visible, created_at, updated_at`,
		orderID, sitterID, ownerID, req.OverallScore, req.ServiceScore,
		req.EnvironmentScore, req.HygieneScore, nilIfEmpty(req.OwnerComment), photos,
	).Scan(&r.ID, &r.OrderID, &r.SitterID, &r.OwnerID, &r.OverallScore, &r.ServiceScore,
		&r.EnvironmentScore, &r.HygieneScore, &r.OwnerComment, &r.OwnerPhotos,
		&r.ReplyText, &r.ReplyAt, &r.IsVisible, &r.CreatedAt, &r.UpdatedAt)

	if err != nil {
		if isUniqueViolation(err) {
			Conflict(c, "review already exists for this order")
			return
		}
		InternalError(c, "failed to submit review: "+err.Error())
		return
	}

	// Update order status to evaluated
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE orders SET status = 'evaluated', evaluated_at = now() WHERE id = $1", orderID)

	// Notify sitter
	var sitterUserID int64
	_ = db.Pool.QueryRow(context.Background(), "SELECT user_id FROM sitters WHERE id = $1", sitterID).Scan(&sitterUserID)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'review_received', '收到新评价', $2, $3)",
		sitterUserID, fmt.Sprintf("您收到 %d 星评价（订单 #%d）。", req.OverallScore, orderID),
		fmt.Sprintf(`{"order_id": %d, "review_id": %d}`, orderID, r.ID))

	Created(c, "review submitted", r)
}

// ReplyReview handles PUT /api/v1/sitter/reviews/:id/reply
func ReplyReview(c *gin.Context) {
	reviewID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid review id")
		return
	}
	userID := c.GetInt64("user_id")

	var req model.ReplyReviewRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "reply_text is required")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE reviews SET reply_text = $1, reply_at = now()
		 WHERE id = $2 AND sitter_id = (SELECT id FROM sitters WHERE user_id = $3 AND deleted_at IS NULL)`,
		req.ReplyText, reviewID, userID)
	if err != nil {
		InternalError(c, "failed to reply")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "review not found or not yours")
		return
	}

	OK(c, gin.H{"message": "reply posted"})
}

// ListNotifications handles GET /api/v1/notifications
func ListNotifications(c *gin.Context) {
	userID := c.GetInt64("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "20"))
	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 50 {
		perPage = 20
	}
	offset := (page - 1) * perPage

	where := "WHERE user_id = $1"
	args := []interface{}{userID}
	idx := 2

	if isRead := c.Query("is_read"); isRead != "" {
		where += fmt.Sprintf(" AND is_read = $%d", idx)
		args = append(args, isRead == "true")
		idx++
	}

	var total int
	_ = db.Pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM notifications "+where, args...).Scan(&total)

	query := fmt.Sprintf("SELECT id, user_id, type, title, body, extra::text, is_read, created_at FROM notifications %s ORDER BY created_at DESC LIMIT $%d OFFSET $%d", where, idx, idx+1)
	args = append(args, perPage, offset)

	rows, err := db.Pool.Query(context.Background(), query, args...)
	if err != nil {
		InternalError(c, "failed to query notifications")
		return
	}
	defer rows.Close()

	items := make([]model.Notification, 0)
	for rows.Next() {
		var n model.Notification
		if err := rows.Scan(&n.ID, &n.UserID, &n.Type, &n.Title, &n.Body, &n.Extra, &n.IsRead, &n.CreatedAt); err != nil {
			continue
		}
		items = append(items, n)
	}

	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// MarkNotificationRead handles PUT /api/v1/notifications/:id/read
func MarkNotificationRead(c *gin.Context) {
	userID := c.GetInt64("user_id")
	nid, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "invalid notification id")
		return
	}

	tag, _ := db.Pool.Exec(context.Background(),
		"UPDATE notifications SET is_read = TRUE WHERE id = $1 AND user_id = $2", nid, userID)
	if tag.RowsAffected() == 0 {
		NotFound(c, "notification not found")
		return
	}
	OK(c, nil)
}

// MarkAllNotificationsRead handles PUT /api/v1/notifications/read-all
func MarkAllNotificationsRead(c *gin.Context) {
	userID := c.GetInt64("user_id")
	tag, _ := db.Pool.Exec(context.Background(),
		"UPDATE notifications SET is_read = TRUE WHERE user_id = $1 AND is_read = FALSE", userID)
	OK(c, gin.H{"updated": tag.RowsAffected()})
}

func isUniqueViolation(err error) bool {
	return err != nil && (contains(err.Error(), "duplicate key") || contains(err.Error(), "uq_reviews_order"))
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && searchStr(s, substr)
}

func searchStr(s, sub string) bool {
	for i := 0; i <= len(s)-len(sub); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}
