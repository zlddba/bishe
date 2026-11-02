package handler

import (
	"context"
	"fmt"
	"strconv"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

// AdminCreateUser handles POST /api/v1/admin/users
func AdminCreateUser(c *gin.Context) {
	var req struct {
		Username string `json:"username" binding:"required,min=3,max=50"`
		Password string `json:"password" binding:"required,min=8,max=128"`
		Nickname string `json:"nickname"`
		Phone    string `json:"phone"`
		Email    string `json:"email"`
		Role     string `json:"role" binding:"required,oneof=owner sitter admin"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}
	if !validPhone(req.Phone) {
		BadRequest(c, "手机号格式不正确")
		return
	}

	// 手机号全表唯一（含软删行），先查后插，给出明确提示
	if req.Phone != "" {
		var phoneTaken bool
		_ = db.Pool.QueryRow(context.Background(),
			"SELECT EXISTS(SELECT 1 FROM users WHERE phone = $1)",
			req.Phone).Scan(&phoneTaken)
		if phoneTaken {
			Conflict(c, "该手机号已被注册")
			return
		}
	}

	var exists bool
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT EXISTS(SELECT 1 FROM users WHERE username = $1 AND deleted_at IS NULL)",
		req.Username).Scan(&exists)
	if exists {
		Conflict(c, "用户名已存在")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		InternalError(c, "密码加密失败")
		return
	}

	var id int64
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO users (username, password_hash, nickname, phone, email, role)
		 VALUES ($1, $2, $3, $4, $5, $6)
		 RETURNING id`,
		req.Username, string(hash), nilIfEmpty(req.Nickname), nilIfEmpty(req.Phone), nilIfEmpty(req.Email), req.Role,
	).Scan(&id)
	if err != nil {
		InternalError(c, "创建用户失败: "+err.Error())
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'CREATE_USER', 'user', $2, $3)",
		adminID, id, fmt.Sprintf(`{"username": "%s", "role": "%s"}`, req.Username, req.Role))

	Created(c, "用户创建成功", gin.H{
		"id":       id,
		"username": req.Username,
		"role":     req.Role,
	})
}

// AdminUpdateUser handles PUT /api/v1/admin/users/:id
func AdminUpdateUser(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的用户ID")
		return
	}

	adminID := c.GetInt64("user_id")

	// Admin cannot change own role
	var req struct {
		Nickname *string `json:"nickname"`
		Phone    *string `json:"phone"`
		Email    *string `json:"email"`
		Role     *string `json:"role" binding:"omitempty,oneof=owner sitter admin"`
		Status   *int16  `json:"status" binding:"omitempty,oneof=0 1"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "参数错误: "+err.Error())
		return
	}
	if req.Phone != nil && !validPhone(*req.Phone) {
		BadRequest(c, "手机号格式不正确")
		return
	}

	// Block self role change
	if req.Role != nil && userID == adminID {
		BadRequest(c, "不能变更自己的角色")
		return
	}

	updates := ""
	args := []interface{}{}
	idx := 1

	if req.Nickname != nil {
		updates += fmt.Sprintf("nickname = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.Nickname))
		idx++
	}
	if req.Phone != nil {
		updates += fmt.Sprintf("phone = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.Phone))
		idx++
	}
	if req.Email != nil {
		updates += fmt.Sprintf("email = $%d, ", idx)
		args = append(args, nilIfEmpty(*req.Email))
		idx++
	}
	if req.Role != nil {
		updates += fmt.Sprintf("role = $%d, ", idx)
		args = append(args, *req.Role)
		idx++
	}
	if req.Status != nil {
		// Admin cannot disable self
		if userID == adminID && *req.Status == 0 {
			BadRequest(c, "不能禁用自己的账号")
			return
		}
		updates += fmt.Sprintf("status = $%d, ", idx)
		args = append(args, *req.Status)
		idx++
	}

	if updates == "" {
		BadRequest(c, "没有需要更新的字段")
		return
	}

	updates = updates[:len(updates)-2]
	args = append(args, userID)

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE users SET "+updates+" WHERE id = $"+strconv.Itoa(idx)+" AND deleted_at IS NULL",
		args...)
	if err != nil {
		InternalError(c, "更新失败: "+err.Error())
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "用户不存在")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_USER', 'user', $2, $3)",
		adminID, userID, fmt.Sprintf(`{"updates": "%s"}`, updates))

	OK(c, gin.H{"id": userID})
}

// AdminDeleteUser handles DELETE /api/v1/admin/users/:id (soft delete)
func AdminDeleteUser(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的用户ID")
		return
	}

	adminID := c.GetInt64("user_id")

	// Cannot delete self
	if userID == adminID {
		BadRequest(c, "不能删除自己的账号")
		return
	}

	// Cannot delete system admin (username='admin')
	var targetUsername string
	err = db.Pool.QueryRow(context.Background(),
		"SELECT username FROM users WHERE id = $1 AND deleted_at IS NULL", userID,
	).Scan(&targetUsername)
	if err != nil {
		NotFound(c, "用户不存在")
		return
	}
	if targetUsername == "admin" {
		BadRequest(c, "系统管理员账号不可删除")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE users SET deleted_at = now(), status = 0 WHERE id = $1 AND deleted_at IS NULL",
		userID)
	if err != nil {
		InternalError(c, "删除失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "用户不存在或已删除")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_USER', 'user', $2, $3)",
		adminID, userID, fmt.Sprintf(`{"deleted_username": "%s"}`, targetUsername))

	OK(c, gin.H{"id": userID})
}

// AdminPurgeUser handles DELETE /api/v1/admin/users/:id/purge（彻底删除，仅限已软删除用户）
func AdminPurgeUser(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的用户ID")
		return
	}

	adminID := c.GetInt64("user_id")
	if userID == adminID {
		BadRequest(c, "不能彻底删除自己的账号")
		return
	}

	// 只允许彻底删除已进入"已注销"（软删除）状态的用户
	var targetUsername string
	err = db.Pool.QueryRow(context.Background(),
		"SELECT username FROM users WHERE id = $1 AND deleted_at IS NOT NULL", userID,
	).Scan(&targetUsername)
	if err != nil {
		NotFound(c, "用户不存在或尚未删除（仅已删除的用户可彻底删除）")
		return
	}
	if targetUsername == "admin" {
		BadRequest(c, "系统管理员账号不可删除")
		return
	}

	if err := purgeUserAccount(context.Background(), userID); err != nil {
		Conflict(c, "该用户存在业务数据（订单、宠物、机构等），无法彻底删除，可保持已删除状态")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'PURGE_USER', 'user', $2, $3)",
		adminID, userID, fmt.Sprintf(`{"purged_username": "%s"}`, targetUsername))

	OK(c, gin.H{"id": userID, "purged": true})
}

// AdminResetPassword handles PUT /api/v1/admin/users/:id/password
func AdminResetPassword(c *gin.Context) {
	userID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的用户ID")
		return
	}

	var req struct {
		Password string `json:"password" binding:"required,min=8,max=128"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		BadRequest(c, "密码至少8位: "+err.Error())
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		InternalError(c, "密码加密失败")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE users SET password_hash = $1, login_fail_count = 0, locked_until = NULL WHERE id = $2 AND deleted_at IS NULL",
		string(hash), userID)
	if err != nil {
		InternalError(c, "重置失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "用户不存在")
		return
	}

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'RESET_PASSWORD', 'user', $2, '{}')",
		adminID, userID)

	OK(c, gin.H{"id": userID})
}
