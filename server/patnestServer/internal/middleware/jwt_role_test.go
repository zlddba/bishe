package middleware

import (
	"testing"
	"time"
)

// 角色鉴权的核心回归：机构账号不能因为令牌里的旧角色被挡在机构端之外。
func TestAccountStateEffectiveRole(t *testing.T) {
	cases := []struct {
		name      string
		dbRole    string
		hasShop   bool
		tokenRole string
		want      string
	}{
		{
			name:      "数据库已是 sitter，正常放行",
			dbRole:    "sitter",
			hasShop:   true,
			tokenRole: "sitter",
			want:      "sitter",
		},
		{
			name:      "令牌是登录时的 owner 快照，但名下有在营机构",
			dbRole:    "owner",
			hasShop:   true,
			tokenRole: "owner",
			want:      "sitter",
		},
		{
			name:      "纯主人账号没有机构，不该被提升",
			dbRole:    "owner",
			hasShop:   false,
			tokenRole: "owner",
			want:      "owner",
		},
		{
			name:      "数据库角色为空时退回令牌里的值",
			dbRole:    "",
			hasShop:   false,
			tokenRole: "sitter",
			want:      "sitter",
		},
		{
			name:      "管理员即使名下挂了机构也还是管理员",
			dbRole:    "admin",
			hasShop:   true,
			tokenRole: "admin",
			want:      "admin",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			st := &accountState{Role: tc.dbRole, HasShop: tc.hasShop}
			if got := st.effectiveRole(tc.tokenRole); got != tc.want {
				t.Fatalf("effectiveRole = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestAccountStateUsable(t *testing.T) {
	now := time.Now()
	revoked := now.Add(-10 * time.Minute)
	fresh := now

	cases := []struct {
		name     string
		state    accountState
		issuedAt time.Time
		want     bool
	}{
		{
			name:     "正常账号",
			state:    accountState{Status: 1},
			issuedAt: now.Add(-time.Hour),
			want:     true,
		},
		{
			name:     "被禁用",
			state:    accountState{Status: 0},
			issuedAt: now.Add(-time.Hour),
			want:     false,
		},
		{
			name:     "令牌早于吊销点",
			state:    accountState{Status: 1, InvalidAt: &revoked},
			issuedAt: revoked.Add(-time.Hour),
			want:     false,
		},
		{
			// 登录瞬间会顺手吊销旧会话，时钟毫秒级偏差不该让刚发出的令牌失效
			name:     "吊销后一分钟内签发的令牌放过",
			state:    accountState{Status: 1, InvalidAt: &revoked},
			issuedAt: fresh,
			want:     true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.state.usable(tc.issuedAt); got != tc.want {
				t.Fatalf("usable = %v, want %v", got, tc.want)
			}
		})
	}
}
