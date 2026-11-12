package tlsmux

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"io"
	"math/big"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// fakeProvider 模拟后台开关。
type fakeProvider struct {
	on  bool
	cfg *tls.Config
}

func (f *fakeProvider) Enabled() bool { return f.on }
func (f *fakeProvider) TLSConfig() *tls.Config {
	if f.cfg == nil {
		return &tls.Config{}
	}
	return f.cfg
}

// writeSelfSignedCert 生成一对自签证书（CN=localhost）。
func writeSelfSignedCert(t *testing.T) (certFile, keyFile string) {
	t.Helper()
	dir := t.TempDir()
	certFile = filepath.Join(dir, "cert.pem")
	keyFile = filepath.Join(dir, "key.pem")

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("生成私钥失败: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
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
	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatalf("序列化私钥失败: %v", err)
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER})

	if err := os.WriteFile(certFile, certPEM, 0o600); err != nil {
		t.Fatalf("写证书失败: %v", err)
	}
	if err := os.WriteFile(keyFile, keyPEM, 0o600); err != nil {
		t.Fatalf("写私钥失败: %v", err)
	}
	return certFile, keyFile
}

// TestSamePortAcceptsHTTPAndTLS 是这个包存在的理由：
// 同一个端口上，明文客户端和 TLS 客户端都要能连上。
func TestSamePortAcceptsHTTPAndTLS(t *testing.T) {
	certFile, keyFile := writeSelfSignedCert(t)
	pair, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		t.Fatalf("加载证书失败: %v", err)
	}

	prov := &fakeProvider{on: true, cfg: &tls.Config{
		MinVersion:   tls.VersionTLS12,
		Certificates: []tls.Certificate{pair},
	}}

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("监听失败: %v", err)
	}
	defer ln.Close()

	srv := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})}
	go srv.Serve(New(ln, prov))
	defer srv.Close()

	addr := ln.Addr().String()

	// 明文客户端
	resp, err := (&http.Client{Timeout: 5 * time.Second}).Get("http://" + addr + "/")
	if err != nil {
		t.Fatalf("明文请求失败: %v", err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if string(body) != "ok" {
		t.Fatalf("明文响应异常: %q", body)
	}

	// TLS 客户端（自签证书，测试里跳过校验）
	httpsClient := &http.Client{
		Timeout: 5 * time.Second,
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
		},
	}
	resp2, err := httpsClient.Get("https://" + addr + "/")
	if err != nil {
		t.Fatalf("HTTPS 请求失败: %v", err)
	}
	body2, _ := io.ReadAll(resp2.Body)
	resp2.Body.Close()
	if string(body2) != "ok" {
		t.Fatalf("HTTPS 响应异常: %q", body2)
	}
}

// TestDisabledFallsBackToPlain 验证开关关掉后不再接受 TLS。
func TestDisabledFallsBackToPlain(t *testing.T) {
	certFile, keyFile := writeSelfSignedCert(t)
	pair, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		t.Fatalf("加载证书失败: %v", err)
	}
	prov := &fakeProvider{on: false, cfg: &tls.Config{
		MinVersion:   tls.VersionTLS12,
		Certificates: []tls.Certificate{pair},
	}}

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("监听失败: %v", err)
	}
	defer ln.Close()
	srv := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})}
	go srv.Serve(New(ln, prov))
	defer srv.Close()

	addr := ln.Addr().String()

	// 明文仍然要通
	resp, err := (&http.Client{Timeout: 5 * time.Second}).Get("http://" + addr + "/")
	if err != nil {
		t.Fatalf("明文请求失败: %v", err)
	}
	resp.Body.Close()

	// TLS 必须失败
	httpsClient := &http.Client{
		Timeout:   5 * time.Second,
		Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}},
	}
	if _, err := httpsClient.Get("https://" + addr + "/"); err == nil {
		t.Fatal("开关关闭时 HTTPS 不应该能连上")
	}
}
