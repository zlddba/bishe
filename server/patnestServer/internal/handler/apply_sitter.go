package handler

import (
	"context"
	"fmt"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// ApplySitter handles POST /api/v1/pet-owner/apply-sitter
// 机构入驻申请：提交营业执照 / 身份证等资质材料，创建 pending 状态的机构记录。
// 账号保持 owner 角色，管理员审核通过（active）后才升级为 sitter；
// 被驳回（rejected）后可修改材料重新提交。
func ApplySitter(c *gin.Context) {
	userID := c.GetInt64("user_id")

	var req model.ApplySitterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "请求参数无效: "+err.Error())
		return
	}

	// 一个用户最多一条机构记录：pending=审核中，rejected=可重新申请，其余=已有资质
	var existingID int64
	var existingStatus string
	if err := db.Pool.QueryRow(context.Background(), `
		SELECT COALESCE((SELECT id FROM sitters WHERE user_id = $1 AND deleted_at IS NULL ORDER BY id DESC LIMIT 1), 0),
		       COALESCE((SELECT status::text FROM sitters WHERE user_id = $1 AND deleted_at IS NULL ORDER BY id DESC LIMIT 1), '')`,
		userID).Scan(&existingID, &existingStatus); err != nil {
		InternalError(c, "数据库错误")
		return
	}

	switch existingStatus {
	case "pending":
		Conflict(c, "机构申请审核中，请等待审核结果")
		return
	case "active", "inactive", "banned":
		Conflict(c, "该账号已拥有机构资质")
		return
	}

	var sitterID int64
	if existingStatus == "rejected" {
		// 驳回后重新申请：更新资质材料并回到待审核
		if _, err := db.Pool.Exec(context.Background(),
			`UPDATE sitters
			 SET name = $2, type = $3, contact_person = $4, license_photo_url = $5,
			     id_card_photo_url = $6, address = $7, description = $8,
			     status = 'pending', review_note = NULL, updated_at = now()
			 WHERE id = $1`,
			existingID, req.Name, req.Type, req.ContactPerson, req.LicensePhotoURL,
			nilIfEmpty(req.IDCardPhotoURL), nilIfEmpty(req.Address), nilIfEmpty(req.Description),
		); err != nil {
			InternalError(c, "提交机构申请失败: "+err.Error())
			return
		}
		sitterID = existingID
	} else {
		// 价格、容量等经营配置留默认值，审核通过后由机构端在"我的店铺"中完善
		if err := db.Pool.QueryRow(context.Background(),
			`INSERT INTO sitters (user_id, name, type, contact_person, license_photo_url, id_card_photo_url,
			                       address, description, status)
			 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'pending')
			 RETURNING id`,
			userID, req.Name, req.Type, req.ContactPerson, req.LicensePhotoURL,
			nilIfEmpty(req.IDCardPhotoURL), nilIfEmpty(req.Address), nilIfEmpty(req.Description),
		).Scan(&sitterID); err != nil {
			InternalError(c, "提交机构申请失败: "+err.Error())
			return
		}
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'APPLY_SITTER', 'sitter', $2, $3)",
		userID, sitterID, fmt.Sprintf(`{"type": "%s"}`, req.Type))

	OK(c, gin.H{"sitter_id": sitterID, "status": "pending"})
}
