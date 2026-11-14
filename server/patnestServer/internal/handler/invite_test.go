package handler

import "testing"

func TestValidInviteCodeFormat(t *testing.T) {
	cases := []struct {
		code string
		want bool
	}{
		{"PET000001", true},
		{"PET000123", true},
		{"PET1234567", true}, // ID 超 6 位时 lpad 不截断
		{"PET12345", false},  // 位数不足
		{"pet000001", false}, // 注册入口会先 ToUpper，这里只认规范形式
		{"ABC000001", false},
		{"PET00000A", false},
		{" PET000001", false},
		{"", false},
	}
	for _, tc := range cases {
		if got := validInviteCodeFormat(tc.code); got != tc.want {
			t.Errorf("validInviteCodeFormat(%q) = %v, want %v", tc.code, got, tc.want)
		}
	}
}
