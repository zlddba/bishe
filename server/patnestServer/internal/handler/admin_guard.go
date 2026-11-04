package handler

import (
	"context"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// superAdminUsername 是 init.sql 里种下的内置超级管理员账号，与 role_permissions 中间件
// 里"不受模块权限限制"的那个账号保持一致。
const superAdminUsername = "admin"

func isSuperAdmin(c *gin.Context) bool {
	return c.GetString("username") == superAdminUsername
}

type adminTarget struct {
	username string
	role     string
}

// loadAdminTarget 读取被操作账号的关键信息；不存在时已经写好响应，返回 false。
func loadAdminTarget(c *gin.Context, userID int64) (adminTarget, bool) {
	var t adminTarget
	err := db.Pool.QueryRow(context.Background(),
		"SELECT username, role::text FROM users WHERE id = $1 AND deleted_at IS NULL", userID,
	).Scan(&t.username, &t.role)
	if err != nil {
		NotFound(c, "用户不存在")
		return t, false
	}
	return t, true
}

// guardPrivilegedWrite 管住"改角色 / 改状态 / 删除"这类会影响管理员体系的操作：
//  1. 只有内置超级管理员能改动 admin 角色的账号，否则一个被授权"用户管理"的普通管理员
//     就能把上级或其他管理员降级、禁用，属于横向提权；
//  2. 不能把系统里最后一个可用管理员关掉，不然后台再也进不去。
func guardPrivilegedWrite(c *gin.Context, targetUserID int64, target adminTarget, touchesPrivilege bool) bool {
	if !touchesPrivilege || target.role != "admin" {
		return true
	}
	if !isSuperAdmin(c) {
		Forbidden(c, "只有系统管理员可以修改管理员账号的角色或状态")
		return false
	}
	// 目标本身已经是禁用状态时不动"最后一名管理员"这条，允许清理已停用的账号
	if target.role == "admin" {
		var active int
		if err := db.Pool.QueryRow(context.Background(),
			"SELECT status FROM users WHERE id = $1 AND deleted_at IS NULL", targetUserID,
		).Scan(&active); err == nil && active != 1 {
			return true
		}
	}
	var remaining int
	if err := db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FROM users
		WHERE role = 'admin' AND status = 1 AND deleted_at IS NULL AND id <> $1`, targetUserID,
	).Scan(&remaining); err == nil && remaining == 0 {
		BadRequest(c, "这是系统里最后一个可用管理员，不能禁用或降级")
		return false
	}
	return true
}

// guardPasswordReset 管住重置密码：内置管理员的口令只能由它自己改，
// 其它 admin 角色账号也只有内置管理员能重置。否则任意"用户管理"权限
// 都能改掉超级管理员的密码，直接接管整个后台。
func guardPasswordReset(c *gin.Context, target adminTarget) bool {
	if target.username == superAdminUsername && c.GetInt64("user_id") != 0 {
		// 内置账号自己可以在"账号与安全"里改密码，但走的是 /auth/password
		if !isSuperAdmin(c) {
			Forbidden(c, "系统管理员的密码只能由该账号本人修改")
			return false
		}
	}
	if target.role == "admin" && !isSuperAdmin(c) {
		Forbidden(c, "只有系统管理员可以重置管理员账号的密码")
		return false
	}
	return true
}
