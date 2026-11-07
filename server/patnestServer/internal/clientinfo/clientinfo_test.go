package clientinfo

import "testing"

func TestDetect(t *testing.T) {
	cases := []struct {
		name     string
		explicit string
		ua       string
		role     string
		want     string
		platform string
	}{
		{"显式 app + Ktor", "app", "Ktor client/3.6.1", "owner", ClientDesktop, "unknown"},
		{"显式 app + okhttp", "app", "okhttp/4.12.0", "owner", ClientAndroid, "android"},
		{"显式 app + 微信", "app", "Mozilla/5.0 MicroMessenger/8.0.49", "owner", ClientMiniProgram, "wechat"},
		{"显式小程序", "miniprogram", "", "owner", ClientMiniProgram, "unknown"},
		{"空客户端 + 浏览器 + 管理员", "", "Mozilla/5.0 (Windows NT 10.0) Chrome/120", "admin", ClientAdmin, "windows"},
		{"空客户端 + 浏览器 + 主人", "", "Mozilla/5.0 (Macintosh) Safari/605", "owner", ClientWeb, "macos"},
		{"空客户端 + 无 UA + 管理员", "", "", "admin", ClientAdmin, "unknown"},
		{"空客户端 + 无 UA + 主人", "", "", "owner", ClientUnknown, "unknown"},
		{"显式 web", "web", "Mozilla/5.0", "owner", ClientWeb, "unknown"},
		{"显式 admin", "admin", "okhttp/4.12.0", "admin", ClientAdmin, "android"},
		{"未知显式值按 UA 猜", "terminal", "okhttp/4.12.0", "owner", ClientAndroid, "android"},
		{"curl 视为桌面/脚本", "", "curl/8.4.0", "owner", ClientDesktop, "unknown"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := Detect(tc.explicit, tc.ua, tc.role)
			if got.Client != tc.want {
				t.Errorf("Detect(%q,%q,%q).Client = %q, want %q", tc.explicit, tc.ua, tc.role, got.Client, tc.want)
			}
			if got.Platform != tc.platform {
				t.Errorf("Detect(%q,%q,%q).Platform = %q, want %q", tc.explicit, tc.ua, tc.role, got.Platform, tc.platform)
			}
			if got.LoginAt == 0 {
				t.Error("LoginAt 应该被填成当前时间戳")
			}
		})
	}
}

func TestUAIsTrimmed(t *testing.T) {
	long := ""
	for i := 0; i < 400; i++ {
		long += "a"
	}
	if got := Detect("", long, "owner").UA; len(got) != 255 {
		t.Fatalf("超长 UA 应截断到 255，实际 %d", len(got))
	}
}

func TestHashTokenStable(t *testing.T) {
	a := HashToken("token-abc")
	b := HashToken("token-abc")
	if a != b || len(a) != 64 {
		t.Fatalf("HashToken 应稳定且为 64 位 hex，实际 %q / %q", a, b)
	}
	if a == HashToken("token-abd") {
		t.Fatal("不同令牌不应得到同一哈希")
	}
}
