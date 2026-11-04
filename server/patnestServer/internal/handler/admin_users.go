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
		"SELECT fn_audit($1, 'CREATE_USER', 'user', $2, $3, NULLIF($4, '')::inet)",
		adminID, id, fmt.Sprintf(`{"username": "%s", "role": "%s"}`, req.Username, req.Role), auditClientIP(c))

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

	// 涉及角色或启用状态时，先过管理员账号保护
	if req.Role != nil || req.Status != nil {
		target, ok := loadAdminTarget(c, userID)
		if !ok {
			return
		}
		if !guardPrivilegedWrite(c, userID, target, true) {
			return
		}
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

	// 被禁用或被降级的账号，既有会话（刷新令牌和已签发的访问令牌）立刻作废
	if req.Status != nil && *req.Status == 0 {
		revokeAllSessions(userID)
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'UPDATE_USER', 'user', $2, $3, NULLIF($4, '')::inet)",
		adminID, userID, fmt.Sprintf(`{"updates": "%s"}`, updates), auditClientIP(c))

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

	// 内置超级管理员不可删除；其它管理员账号也只有内置管理员能动
	target, ok := loadAdminTarget(c, userID)
	if !ok {
		return
	}
	if target.username == superAdminUsername {
		BadRequest(c, "系统管理员账号不可删除")
		return
	}
	if !guardPrivilegedWrite(c, userID, target, true) {
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

	// 账号已停用，既有会话一并作废
	revokeAllSessions(userID)

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'DELETE_USER', 'user', $2, $3, NULLIF($4, '')::inet)",
		adminID, userID, fmt.Sprintf(`{"deleted_username": "%s"}`, target.username), auditClientIP(c))

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
	var targetUsername, targetRole string
	err = db.Pool.QueryRow(context.Background(),
		"SELECT username, role::text FROM users WHERE id = $1 AND deleted_at IS NOT NULL", userID,
	).Scan(&targetUsername, &targetRole)
	if err != nil {
		NotFound(c, "用户不存在或尚未删除（仅已删除的用户可彻底删除）")
		return
	}
	if targetUsername == superAdminUsername {
		BadRequest(c, "系统管理员账号不可删除")
		return
	}
	// 管理员角色账号（非内置）也只有内置管理员能彻底删除，避免同级互相清除痕迹
	if targetRole == "admin" && !isSuperAdmin(c) {
		Forbidden(c, "只有系统管理员可以彻底删除管理员账号")
		return
	}

	if err := purgeUserAccount(context.Background(), userID); err != nil {
		Conflict(c, "该用户存在业务数据（订单、宠物、机构等），无法彻底删除，可保持已删除状态")
		return
	}

	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'PURGE_USER', 'user', $2, $3, NULLIF($4, '')::inet)",
		adminID, userID, fmt.Sprintf(`{"purged_username": "%s"}`, targetUsername), auditClientIP(c))

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

	// 重置密码等于接管账号，管理员口令不能被"用户管理"权限改掉
	target, ok := loadAdminTarget(c, userID)
	if !ok {
		return
	}
	if !guardPasswordReset(c, target) {
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

	// 重置密码等于接管账号，旧会话必须全部失效
	revokeAllSessions(userID)

	adminID := c.GetInt64("user_id")
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_audit($1, 'RESET_PASSWORD', 'user', $2, '{}', NULLIF($3, '')::inet)",
		adminID, userID, auditClientIP(c))

	OK(c, gin.H{"id": userID})
}
