package handler

import "regexp"

// 中国大陆手机号：1 开头，第二位 3-9，共 11 位。
var phonePattern = regexp.MustCompile(`^1[3-9]\d{9}$`)

func validPhone(value string) bool {
	return value == "" || phonePattern.MatchString(value)
}
