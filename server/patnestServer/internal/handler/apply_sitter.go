package handler

import (
	"context"

	"patnestServer/internal/db"
	"patnestServer/internal/middleware"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// ApplySitter 主人申请成为机构账号：创建待审核机构并把用户角色升级为 sitter。
func ApplySitter(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var req model.CreateSitterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}

	var exists bool
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM sitters WHERE user_id = $1 AND deleted_at IS NULL)",
		userID).Scan(&exists); err != nil {
		InternalError(c, "数据库错误")
		return
	}
	if exists {
		Conflict(c, "你已提交过机构申请，请等待审核")
		return
	}

	photos := req.DetailPhotos
	if photos == nil {
		photos = []string{}
	}

	var s model.Sitter
	err := db.Pool.QueryRow(context.Background(), `
		INSERT INTO sitters (user_id, name, type, description, daily_price, visit_price, price_mode,
		                     capacity, address, latitude, longitude, cover_photo_url, detail_photos, status)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, 'pending')
		RETURNING id, user_id, name, type, description, daily_price, visit_price, price_mode, capacity,
		          address, latitude, longitude, avg_score, review_count, cover_photo_url, detail_photos,
		          status, created_at, updated_at`,
		userID, req.Name, req.Type, nilIfEmpty(req.Description), req.DailyPrice, req.VisitPrice,
		req.PriceMode, req.Capacity, nilIfEmpty(req.Address), req.Latitude, req.Longitude,
		nilIfEmpty(req.CoverPhotoURL), photos,
	).Scan(&s.ID, &s.UserID, &s.Name, &s.Type, &s.Description, &s.DailyPrice, &s.VisitPrice,
		&s.PriceMode, &s.Capacity, &s.Address, &s.Latitude, &s.Longitude, &s.AvgScore,
		&s.ReviewCount, &s.CoverPhotoURL, &s.DetailPhotos, &s.Status, &s.CreatedAt, &s.UpdatedAt)
	if err != nil {
		InternalError(c, "创建机构申请失败: "+err.Error())
		return
	}

	// 升级用户角色；已有人申请的机构不会再重复创建。
	_, _ = db.Pool.Exec(context.Background(),
		"UPDATE users SET role = 'sitter', updated_at = now() WHERE id = $1", userID)

	var username string
	var nickname *string
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT username, nickname FROM users WHERE id = $1", userID).Scan(&username, &nickname)
	displayName := ""
	if nickname != nil {
		displayName = *nickname
	}

	accessToken, err := middleware.GenerateAccessToken(userID, username, "sitter", "")
	if err != nil {
		InternalError(c, "生成访问令牌失败")
		return
	}
	refreshToken, err := middleware.GenerateRefreshToken(userID, username, "sitter", "")
	if err != nil {
		InternalError(c, "生成刷新令牌失败")
		return
	}
	storeRefreshToken(userID, refreshToken)

	OK(c, model.AuthResponse{
		User: model.UserPublic{
			ID:       userID,
			Username: username,
			Nickname: displayName,
			Role:     "sitter",
		},
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    7200,
	})
}
