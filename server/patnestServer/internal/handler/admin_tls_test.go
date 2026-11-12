package handler

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
)

// selfSignedPEM 生成一份自签证书与私钥（PEM 文本）。
func selfSignedPEM(t *testing.T) (certPEM, keyPEM string) {
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
	certPEM = string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	keyPEM = string(pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER}))
	return certPEM, keyPEM
}

// call 发一个请求给 handler，返回状态码与响应里的 data 部分。
func call(t *testing.T, h gin.HandlerFunc, method, path, body string) (int, map[string]any) {
	t.Helper()
	gin.SetMode(gin.TestMode)
	w := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(w)
	c.Request = httptest.NewRequest(method, path, strings.NewReader(body))
	c.Request.Header.Set("Content-Type", "application/json")
	h(c)

	var full map[string]any
	_ = json.Unmarshal(w.Body.Bytes(), &full)
	data, _ := full["data"].(map[string]any)
	if data == nil {
		data = full
	}
	return w.Code, data
}

// TestAdminTLSBackendSwitch 覆盖后台页面上的完整链路：
// 上传证书 → 打开后端 HTTPS → 状态能查到 → 关掉。
//
// 注意：TLS 目录是进程级的（backendtls.Default() 首次调用时固定），
// 所以整个测试文件共用一个临时目录。
func TestAdminTLSBackendSwitch(t *testing.T) {
	dir, err := os.MkdirTemp("", "patnest-tls-test")
	if err != nil {
		t.Fatalf("创建临时目录失败: %v", err)
	}
	defer os.RemoveAll(dir)
	t.Setenv("TLS_DIR", dir)

	certPEM, keyPEM := selfSignedPEM(t)

	// 1) 上传前的状态
	code, body := call(t, AdminGetTLS, http.MethodGet, "/api/v1/admin/system/tls", "")
	if code != http.StatusOK {
		t.Fatalf("查询状态失败: %d %v", code, body)
	}
	bt, _ := body["backend_tls"].(map[string]any)
	if bt == nil {
		t.Fatalf("响应里应包含 backend_tls: %v", body)
	}
	if bt["enabled"] == true {
		t.Fatal("初始状态不应是开启")
	}
	if bt["available"] == true {
		t.Fatal("还没上传证书，available 不应为 true")
	}

	// 2) 没证书时直接打开 → 应被拒绝
	payload := `{"enabled":true}`
	code, body = call(t, AdminSetBackendTLS, http.MethodPost, "/api/v1/admin/system/tls/backend", payload)
	if code == http.StatusOK {
		t.Fatalf("没有证书时不应允许开启: %v", body)
	}

	// 3) 上传证书
	upPayload, _ := json.Marshal(map[string]string{"cert": certPEM, "key": keyPEM})
	code, body = call(t, AdminUploadTLS, http.MethodPost, "/api/v1/admin/system/tls", string(upPayload))
	if code != http.StatusOK {
		t.Fatalf("上传证书失败: %d %v", code, body)
	}
	for _, name := range []string{"fullchain.pem", "privkey.pem"} {
		if _, err := os.Stat(filepath.Join(dir, name)); err != nil {
			t.Fatalf("证书未落盘 %s: %v", name, err)
		}
	}

	// 4) 上传后 available 应为 true
	_, body = call(t, AdminGetTLS, http.MethodGet, "/api/v1/admin/system/tls", "")
	bt, _ = body["backend_tls"].(map[string]any)
	if bt["available"] != true {
		t.Fatalf("上传后 available 应为 true: %v", body)
	}

	// 5) 打开后端 HTTPS
	code, body = call(t, AdminSetBackendTLS, http.MethodPost, "/api/v1/admin/system/tls/backend", payload)
	if code != http.StatusOK {
		t.Fatalf("开启失败: %d %v", code, body)
	}
	if msg, _ := body["message"].(string); !strings.Contains(msg, "https") {
		t.Logf("提示文案: %v", body["message"])
	}

	// 6) 状态应变成开启
	_, body = call(t, AdminGetTLS, http.MethodGet, "/api/v1/admin/system/tls", "")
	bt, _ = body["backend_tls"].(map[string]any)
	if bt["enabled"] != true {
		t.Fatalf("开启后状态应为 true: %v", body)
	}
	// 开关应落盘，方便多实例同步
	raw, err := os.ReadFile(filepath.Join(dir, "backend_tls"))
	if err != nil {
		t.Fatalf("开关文件未落盘: %v", err)
	}
	if strings.TrimSpace(string(raw)) != "on" {
		t.Fatalf("开关文件内容应为 on，实际 %q", raw)
	}

	// 7) 关掉
	code, body = call(t, AdminSetBackendTLS, http.MethodPost, "/api/v1/admin/system/tls/backend", `{"enabled":false}`)
	if code != http.StatusOK {
		t.Fatalf("关闭失败: %d %v", code, body)
	}
	_, body = call(t, AdminGetTLS, http.MethodGet, "/api/v1/admin/system/tls", "")
	bt, _ = body["backend_tls"].(map[string]any)
	if bt["enabled"] != false {
		t.Fatalf("关闭后状态应为 false: %v", body)
	}
}

// TestAdminDeleteTLSDisablesBackend 移除证书时，后端的 HTTPS 必须一并关掉：
// 证书没了还显示"已开启"，握手全失败，比直接关掉难查。
func TestAdminDeleteTLSDisablesBackend(t *testing.T) {
	dir, err := os.MkdirTemp("", "patnest-tls-del-test")
	if err != nil {
		t.Fatalf("创建临时目录失败: %v", err)
	}
	defer os.RemoveAll(dir)
	t.Setenv("TLS_DIR", dir)

	certPEM, keyPEM := selfSignedPEM(t)
	upPayload, _ := json.Marshal(map[string]string{"cert": certPEM, "key": keyPEM})
	if code, body := call(t, AdminUploadTLS, http.MethodPost, "/api/v1/admin/system/tls", string(upPayload)); code != http.StatusOK {
		t.Fatalf("上传证书失败: %d %v", code, body)
	}
	if code, body := call(t, AdminSetBackendTLS, http.MethodPost, "/api/v1/admin/system/tls/backend", `{"enabled":true}`); code != http.StatusOK {
		t.Fatalf("开启失败: %d %v", code, body)
	}

	if code, body := call(t, AdminDeleteTLS, http.MethodDelete, "/api/v1/admin/system/tls", ""); code != http.StatusOK {
		t.Fatalf("删除失败: %d %v", code, body)
	}

	_, body := call(t, AdminGetTLS, http.MethodGet, "/api/v1/admin/system/tls", "")
	bt, _ := body["backend_tls"].(map[string]any)
	if bt["enabled"] != false {
		t.Fatalf("移除证书后后端 HTTPS 应自动关闭: %v", body)
	}
}
