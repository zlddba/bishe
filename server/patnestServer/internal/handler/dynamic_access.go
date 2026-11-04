package handler

import (
	"context"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// canAccessDynamic 判断某个用户能否读写某条寄养动态。
//
// 动态挂在具体订单上，里面是别人家宠物的照片和近况，所以只有这几方可以看：
// 这一单的主人、承接的机构成员（含作者本人）、以及管理员。
// 之前 Like/Comment/ListComments 只要求"登录了"，换个 dynamic_id 就能读别人的动态内容、
// 留言甚至点赞，属于横向越权。
func canAccessDynamic(dynamicID, userID int64, role string) (bool, error) {
	var ok bool
	err := db.Pool.QueryRow(context.Background(), `
		SELECT EXISTS (
			SELECT 1
			FROM dynamics d
			JOIN orders o ON o.id = d.order_id
			JOIN sitters s ON s.id = d.sitter_id
			WHERE d.id = $1
			  AND ($3 = 'admin' OR o.owner_id = $2 OR s.user_id = $2 OR d.author_id = $2)
		)`, dynamicID, userID, role,
	).Scan(&ok)
	if err != nil {
		return false, err
	}
	return ok, nil
}

// requireDynamicAccess 校验并写好响应；返回 false 时调用方直接 return 即可。
func requireDynamicAccess(c *gin.Context, dynamicID int64) bool {
	if dynamicID == 0 {
		BadRequest(c, "动态编号无效")
		return false
	}
	allowed, err := canAccessDynamic(dynamicID, c.GetInt64("user_id"), c.GetString("role"))
	if err != nil {
		InternalError(c, "校验动态权限失败")
		return false
	}
	if !allowed {
		Forbidden(c, "无权访问该动态")
		return false
	}
	return true
}
