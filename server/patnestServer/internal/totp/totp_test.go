package totp

import (
	"strings"
	"testing"
	"time"
)

// RFC 6238 附录 B 的测试向量：密钥是 ASCII "12345678901234567890"，
// 该 20 字节的 base32 编码如下。标准向量是 8 位，本系统用 6 位，
// 因此取同一个 31 位结果的低 6 位比对（94287082 → 287082）。
const rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

func TestCodeMatchesRFC6238(t *testing.T) {
	cases := []struct {
		unix int64
		want string
	}{
		{59, "287082"},        // 94287082
		{1111111109, "081804"}, // 07081804
		{1234567890, "005924"}, // 89005924
		{2000000000, "279037"}, // 69279037
	}
	for _, c := range cases {
		got, err := Code(rfcSecret, time.Unix(c.unix, 0))
		if err != nil {
			t.Fatalf("t=%d: 生成口令失败: %v", c.unix, err)
		}
		if got != c.want {
			t.Errorf("t=%d: 期望 %s，实际 %s", c.unix, c.want, got)
		}
	}
}

func TestVerifyWindowAndReplay(t *testing.T) {
	now := time.Unix(1700000000, 0)
	code, err := Code(rfcSecret, now)
	if err != nil {
		t.Fatal(err)
	}

	// 当前窗口应通过，并返回所属时间步
	step, ok := Verify(rfcSecret, code, now, 1, 0)
	if !ok {
		t.Fatal("当前口令应校验通过")
	}

	// 同一时间步已用过：必须拒绝（防重放）
	if _, ok := Verify(rfcSecret, code, now, 1, step); ok {
		t.Error("同一时间步的口令不应重复通过")
	}

	// 上一个窗口的口令在 ±1 步内容忍范围内应通过
	prev, err := Code(rfcSecret, now.Add(-Step))
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := Verify(rfcSecret, prev, now, 1, 0); !ok {
		t.Error("相邻时间步的口令应通过（允许 ±30 秒时钟偏差）")
	}

	// 差 3 步就必须拒绝
	far, err := Code(rfcSecret, now.Add(-3*Step))
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := Verify(rfcSecret, far, now, 1, 0); ok {
		t.Error("超出容忍窗口的口令不应通过")
	}
}

func TestVerifyRejectsMalformed(t *testing.T) {
	now := time.Unix(1700000000, 0)
	for _, bad := range []string{"", "12345", "1234567", "abcdef", "12 34 56"} {
		if _, ok := Verify(rfcSecret, bad, now, 1, 0); ok {
			t.Errorf("非法输入 %q 不应通过", bad)
		}
	}
}

func TestRecoveryCodes(t *testing.T) {
	codes, err := GenerateRecoveryCodes(10)
	if err != nil {
		t.Fatal(err)
	}
	if len(codes) != 10 {
		t.Fatalf("期望 10 枚恢复码，实际 %d", len(codes))
	}
	seen := map[string]bool{}
	for _, c := range codes {
		// 形如 XXXXX-XXXXX，去掉分隔符后 10 位
		if len(c) != 11 || c[5] != '-' {
			t.Errorf("恢复码格式不对: %q", c)
			continue
		}
		if !LooksLikeRecoveryCode(c) {
			t.Errorf("LooksLikeRecoveryCode 未识别: %q", c)
		}
		if seen[c] {
			t.Errorf("恢复码重复: %q", c)
		}
		seen[c] = true
		// 用户输小写、带空格也要能对上
		loose := strings.ToLower(strings.ReplaceAll(c, "-", " "))
		if HashRecoveryCode(loose) != HashRecoveryCode(c) {
			t.Errorf("恢复码归一化失败: %q", c)
		}
	}
}

func TestURIContainsParams(t *testing.T) {
	uri := URI("PatNest", "admin", rfcSecret)
	for _, want := range []string{"otpauth://totp/", "secret=", "issuer=PatNest", "digits=6", "period=30"} {
		if !strings.Contains(uri, want) {
			t.Errorf("otpauth 链接缺少 %s: %s", want, uri)
		}
	}
}
