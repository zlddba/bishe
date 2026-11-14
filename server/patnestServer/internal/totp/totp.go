// Package totp 实现 RFC 6238 的 6 位动态口令（TOTP），供管理后台做两步验证。
//
// 只依赖标准库：后端镜像里不需要额外组件，算法与 Google Authenticator、
// Microsoft Authenticator、1Password、Authy 等主流验证器兼容（SHA1 / 6 位 / 30 秒）。
//
// 安全约定：
//   - 密钥 20 字节（160 bit），base32 无填充编码，这是验证器公认的长度；
//   - 校验窗口前后各 1 个步长，容忍手机与服务器最多 30 秒时钟偏差；
//   - 校验通过后调用方必须落库 totp_last_step，同一个步长不允许二次使用（防重放）。
package totp

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base32"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"net/url"
	"strings"
	"time"
)

const (
	// Step 是口令的有效期，也是验证器默认的时间步长。
	Step = 30 * time.Second
	// Digits 是验证码位数。
	Digits = 6
	// SecretBytes 是随机密钥长度（字节）。
	SecretBytes = 20
	// DefaultIssuer 显示在验证器列表里，用来区分同一个人不同系统的条目。
	DefaultIssuer = "PatNest"
)

var b32 = base32.StdEncoding.WithPadding(base32.NoPadding)

// GenerateSecret 生成一个新的 base32 密钥（32 个字符，无需填充）。
func GenerateSecret() (string, error) {
	buf := make([]byte, SecretBytes)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return b32.EncodeToString(buf), nil
}

// Normalize 把用户可能带进来的空格、连字符、小写统一成标准 base32。
func Normalize(secret string) string {
	s := strings.ToUpper(strings.TrimSpace(secret))
	s = strings.NewReplacer(" ", "", "-", "", "_", "").Replace(s)
	return strings.TrimRight(s, "=")
}

// stepAt 返回某一时刻所属的时间步。
func stepAt(t time.Time) int64 {
	return t.Unix() / int64(Step/time.Second)
}

// hotp 计算指定步长的原始口令（RFC 4226）。
func hotp(key []byte, step int64) ([]byte, error) {
	var counter [8]byte
	binary.BigEndian.PutUint64(counter[:], uint64(step))

	mac := hmac.New(sha1.New, key)
	if _, err := mac.Write(counter[:]); err != nil {
		return nil, err
	}
	sum := mac.Sum(nil)

	// 动态截断：取最后一个字节低 4 位做偏移，读出 4 字节再抹掉符号位。
	offset := sum[len(sum)-1] & 0x0f
	value := binary.BigEndian.Uint32(sum[offset:offset+4]) & 0x7fffffff
	return []byte(fmt.Sprintf("%0*d", Digits, value%1_000_000)), nil
}

// Code 返回指定时刻的 6 位口令，主要供测试与排障使用。
func Code(secret string, t time.Time) (string, error) {
	key, err := b32.DecodeString(Normalize(secret))
	if err != nil {
		return "", fmt.Errorf("密钥格式不正确: %w", err)
	}
	raw, err := hotp(key, stepAt(t))
	if err != nil {
		return "", err
	}
	return string(raw), nil
}

// Verify 校验口令，skew 为允许的前后步数（传 1 即 ±30 秒）。
// 命中时返回该口令所属的时间步，调用方应记录它来拒绝同一口令重复使用；
// lastStep 为上一次成功校验的步长，传 0 表示不检查重放。
func Verify(secret, code string, now time.Time, skew int, lastStep int64) (int64, bool) {
	code = strings.TrimSpace(code)
	if len(code) != Digits {
		return 0, false
	}
	for _, r := range code {
		if r < '0' || r > '9' {
			return 0, false
		}
	}

	key, err := b32.DecodeString(Normalize(secret))
	if err != nil || len(key) == 0 {
		return 0, false
	}

	base := stepAt(now)
	if skew < 0 {
		skew = 0
	}
	for delta := -skew; delta <= skew; delta++ {
		step := base + int64(delta)
		// 只接受比上次成功校验更新的步长，同一窗口内的第二次提交直接拒绝。
		if lastStep > 0 && step <= lastStep {
			continue
		}
		raw, err := hotp(key, step)
		if err != nil {
			continue
		}
		if subtle.ConstantTimeCompare(raw, []byte(code)) == 1 {
			return step, true
		}
	}
	return 0, false
}

// URI 生成验证器扫码用的 otpauth:// 链接。
// account 一般是用户名，issuer 显示在验证器分组标题上。
func URI(issuer, account, secret string) string {
	if issuer == "" {
		issuer = DefaultIssuer
	}
	label := url.PathEscape(issuer + ":" + account)
	q := url.Values{}
	q.Set("secret", Normalize(secret))
	q.Set("issuer", issuer)
	q.Set("algorithm", "SHA1")
	q.Set("digits", fmt.Sprintf("%d", Digits))
	q.Set("period", fmt.Sprintf("%d", int(Step/time.Second)))
	return "otpauth://totp/" + label + "?" + q.Encode()
}

// recoveryAlphabet 去掉了容易看错的 0/O、1/I，方便用户手抄。
const recoveryAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// GenerateRecoveryCodes 生成 n 个一次性恢复码，格式 XXXXX-XXXXX。
// 恢复码用完后才能在后台重新生成一批。
func GenerateRecoveryCodes(n int) ([]string, error) {
	codes := make([]string, 0, n)
	buf := make([]byte, 10)
	for i := 0; i < n; i++ {
		if _, err := rand.Read(buf); err != nil {
			return nil, err
		}
		var sb strings.Builder
		for j, b := range buf {
			if j == 5 {
				sb.WriteByte('-')
			}
			sb.WriteByte(recoveryAlphabet[int(b)%len(recoveryAlphabet)])
		}
		codes = append(codes, sb.String())
	}
	return codes, nil
}

// NormalizeRecoveryCode 统一大小写与分隔符，用户输入 "abcde fghij" 也能对上。
func NormalizeRecoveryCode(code string) string {
	s := strings.ToUpper(strings.TrimSpace(code))
	s = strings.NewReplacer(" ", "", "-", "", "_", "").Replace(s)
	return s
}

// HashRecoveryCode 返回恢复码的 sha256 十六进制串。
// 库里只存哈希：即使数据库被拖走，也拿不到可用的恢复码。
// 恢复码本身有 32^10 的空间，且一次性、有失败限流，不需要再加慢哈希。
func HashRecoveryCode(code string) string {
	sum := sha256.Sum256([]byte(NormalizeRecoveryCode(code)))
	return hex.EncodeToString(sum[:])
}

// LooksLikeRecoveryCode 判断输入是恢复码还是 6 位口令。
func LooksLikeRecoveryCode(code string) bool {
	s := NormalizeRecoveryCode(code)
	if len(s) != 10 {
		return false
	}
	for _, r := range s {
		if !strings.ContainsRune(recoveryAlphabet, r) {
			return false
		}
	}
	return true
}
