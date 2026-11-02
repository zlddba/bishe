package cache

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"time"
)

const (
	LoginFailLimit = 5
	LoginFailTTL   = 10 * time.Minute
)

// LoginLimitKey 按 IP + 用户名 做维度，避免同一 IP 撞库或单账号被爆破。
func LoginLimitKey(ip, username string) string {
	sum := sha256.Sum256([]byte(strings.ToLower(strings.TrimSpace(ip + "|" + username))))
	return "login:fail:" + hex.EncodeToString(sum[:8])
}

func LoginBlocked(key string) bool {
	if !Enabled() {
		return false
	}
	n, ok, err := GetInt(key)
	if err != nil || !ok {
		return false
	}
	return n >= LoginFailLimit
}

func RecordLoginFailure(key string) {
	if !Enabled() {
		return
	}
	_, _ = IncrWindow(key, LoginFailTTL)
}

func ClearLoginFailure(key string) {
	if !Enabled() {
		return
	}
	_ = Del(key)
}
