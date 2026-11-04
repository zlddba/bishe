package handler

import "regexp"

// 中国大陆手机号：1 开头，第二位 3-9，共 11 位。
var phonePattern = regexp.MustCompile(`^1[3-9]\d{9}$`)

func validPhone(value string) bool {
	return value == "" || phonePattern.MatchString(value)
}

// maskPhone 把手机号中间四位打码（138****2201）。
// 后台列表默认只返回打码号码，完整号码走单独接口并记审计，
// 避免任何有 users.read 权限的管理员批量导走用户手机号。
func maskPhone(value string) string {
	if len(value) != 11 {
		return value
	}
	return value[:3] + "****" + value[7:]
}
