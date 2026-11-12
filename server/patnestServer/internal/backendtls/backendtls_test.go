package backendtls

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// writeCert 在目录里写一对自签证书。
func writeCert(t *testing.T, dir string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("生成私钥失败: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(time.Now().UnixNano()),
		Subject:               pkix.Name{CommonName: "localhost"},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		DNSNames:              []string{"localhost"},
		IsCA:                  true,
		BasicConstraintsValid: true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("签发证书失败: %v", err)
	}
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatalf("序列化私钥失败: %v", err)
	}
	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER})
	if err := os.WriteFile(filepath.Join(dir, CertFile), certPEM, 0o600); err != nil {
		t.Fatalf("写证书失败: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, KeyFile), keyPEM, 0o600); err != nil {
		t.Fatalf("写私钥失败: %v", err)
	}
}

// TestSetEnabledRequiresCert 没证书时不能开 HTTPS：
// 否则握手全失败，页面还显示"已开启"，比直接报错难查得多。
func TestSetEnabledRequiresCert(t *testing.T) {
	dir := t.TempDir()
	m := New(dir)

	if err := m.SetEnabled(true); err == nil {
		t.Fatal("没有证书时 SetEnabled(true) 应该报错")
	}
	if m.Enabled() {
		t.Fatal("报错后开关不应被打开")
	}
	if m.HasCert() {
		t.Fatal("没有证书时 HasCert 应为 false")
	}
}

// TestEnableDisable 覆盖开关的落盘与读取。
func TestEnableDisable(t *testing.T) {
	dir := t.TempDir()
	writeCert(t, dir)
	m := New(dir)

	if err := m.SetEnabled(true); err != nil {
		t.Fatalf("开启失败: %v", err)
	}
	if !m.Enabled() {
		t.Fatal("开启后 Enabled 应为 true")
	}
	raw, err := os.ReadFile(filepath.Join(dir, SwitchFile))
	if err != nil {
		t.Fatalf("读取开关文件失败: %v", err)
	}
	if string(raw) != "on" {
		t.Fatalf("开关文件内容应为 on，实际 %q", raw)
	}

	if err := m.SetEnabled(false); err != nil {
		t.Fatalf("关闭失败: %v", err)
	}
	if m.Enabled() {
		t.Fatal("关闭后 Enabled 应为 false")
	}
}

// TestInitPrefersFileOnDisk 落盘文件优先于默认值：
// 页面上点过的开关，不该被重启时的默认值覆盖。
func TestInitPrefersFileOnDisk(t *testing.T) {
	dir := t.TempDir()
	writeCert(t, dir)
	if err := os.WriteFile(filepath.Join(dir, SwitchFile), []byte("off"), 0o600); err != nil {
		t.Fatalf("写开关文件失败: %v", err)
	}
	m := New(dir)
	if err := m.Init(true); err != nil {
		t.Fatalf("Init 失败: %v", err)
	}
	if m.Enabled() {
		t.Fatal("落盘是 off 时，Init(true) 不应强制打开")
	}
}

// TestInitFailsWhenCertMissing 默认要 https 但证书不可用时必须失败，
// 不能悄悄退回明文。
func TestInitFailsWhenCertMissing(t *testing.T) {
	m := New(t.TempDir())
	if err := m.Init(true); err == nil {
		t.Fatal("缺证书时 Init(true) 应报错")
	}
	if m.Enabled() {
		t.Fatal("Init 失败后不应开启 HTTPS")
	}
}

// TestInitDefaultOff 没配开关文件且默认 http 时保持关闭。
func TestInitDefaultOff(t *testing.T) {
	m := New(t.TempDir())
	if err := m.Init(false); err != nil {
		t.Fatalf("Init 失败: %v", err)
	}
	if m.Enabled() {
		t.Fatal("默认应为关闭")
	}
}

// TestCertHotReload 重新上传证书后，握手用的证书要跟着换。
func TestCertHotReload(t *testing.T) {
	dir := t.TempDir()
	writeCert(t, dir)
	m := New(dir)

	first, err := m.loadCert()
	if err != nil {
		t.Fatalf("读取证书失败: %v", err)
	}
	firstLeaf := first.Leaf

	// 换一份新证书（重新上传的动作）
	time.Sleep(10 * time.Millisecond)
	writeCert(t, dir)

	second, err := m.loadCert()
	if err != nil {
		t.Fatalf("重新读取证书失败: %v", err)
	}
	if second.Leaf.SerialNumber.Cmp(firstLeaf.SerialNumber) == 0 {
		t.Fatal("证书更新后应读到新证书")
	}
}
