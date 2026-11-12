package handler

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"patnestServer/internal/backendtls"
)

// HTTPS 证书管理。
//
// 现在只有后端自己用这张证书：后端在 9200 端口同时接受 http 与 https
// （首字节嗅探，见 internal/tlsmux），客户端可以直接走 https://域名:9200。
//
// nginx 不再终结 TLS —— 443 已关闭，站点只监听 9200。
// 所以这里不再生成 nginx 的 server 配置，也不需要 reload 信号：
// 证书是握手时现读的（backendtls 的 GetCertificate），重新上传即生效。
//
// 校验仍然严格：证书与私钥必须配对、不能过期，私钥以 0600 落盘。

const (
	tlsCertFile = backendtls.CertFile
	tlsKeyFile  = backendtls.KeyFile
)

// tlsDir 证书落盘目录。
func tlsDir() string {
	return backendtls.Dir()
}

func tlsPath(name string) string { return filepath.Join(tlsDir(), name) }

// backendTLSManager 后端自身 TLS 的运行时状态（开关 + 证书热加载）。
//
// 全进程共用一个实例：main.go 用它做协议嗅探，后台接口用它切开关。
// 注意不能写成包级变量：证书目录依赖 TLS_DIR，而它是 .env 加载后才有的
// （config.Load 里调 godotenv.Load），包级变量初始化时读不到。
func backendTLSManager() *backendtls.Manager { return backendtls.Default() }

// certDomains 提取证书覆盖的域名：SAN 优先，没有 SAN 才退回 CN。
func certDomains(cert *x509.Certificate) []string {
	out := make([]string, 0, len(cert.DNSNames)+1)
	out = append(out, cert.DNSNames...)
	if len(out) == 0 && cert.Subject.CommonName != "" {
		out = append(out, cert.Subject.CommonName)
	}
	return out
}

// AdminGetTLS handles GET /api/v1/admin/system/tls
func AdminGetTLS(c *gin.Context) {
	mgr := backendTLSManager()
	info := gin.H{
		"configured": false,
		"dir":        tlsDir(),
		// 后端自身是否在接收 HTTPS 连接。
		// enabled 是当前生效状态；available 表示证书是否就绪（没证书开不了）。
		"backend_tls": gin.H{
			"enabled":   mgr.Enabled(),
			"available": mgr.HasCert(),
		},
	}

	if raw, err := os.ReadFile(tlsPath(tlsCertFile)); err == nil {
		if block, _ := pem.Decode(raw); block != nil {
			if cert, perr := x509.ParseCertificate(block.Bytes); perr == nil {
				info["configured"] = true
				info["domains"] = certDomains(cert)
				info["issuer"] = cert.Issuer.CommonName
				info["not_before"] = cert.NotBefore.Format("2006-01-02")
				info["not_after"] = cert.NotAfter.Format("2006-01-02")
				info["days_left"] = int(time.Until(cert.NotAfter).Hours() / 24)
			}
		}
	}
	OK(c, info)
}

// AdminUploadTLS handles POST /api/v1/admin/system/tls
//
// body: { "cert": "-----BEGIN CERTIFICATE-----...", "key": "-----BEGIN PRIVATE KEY-----...",
//
//	"server_name": "api.example.com" }
//
// cert 要传完整证书链（站点证书 + 中间证书），否则部分安卓机/小程序会报证书链不完整。
//
// 保存后立即可用：证书是握手时现读的，新连接立刻用新证书，不需要重启。
func AdminUploadTLS(c *gin.Context) {
	var in struct {
		Cert       string `json:"cert" binding:"required"`
		Key        string `json:"key" binding:"required"`
		ServerName string `json:"server_name" binding:"max=255"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请同时提供证书与私钥")
		return
	}

	certPEM := []byte(strings.TrimSpace(in.Cert) + "\n")
	keyPEM := []byte(strings.TrimSpace(in.Key) + "\n")

	// X509KeyPair 会同时校验「是不是有效 PEM」和「私钥与证书是否配对」
	pair, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		BadRequest(c, "证书与私钥不匹配或格式有误: "+err.Error())
		return
	}
	cert, err := x509.ParseCertificate(pair.Certificate[0])
	if err != nil {
		BadRequest(c, "无法解析证书: "+err.Error())
		return
	}
	if time.Now().After(cert.NotAfter) {
		BadRequest(c, "证书已过期（有效期至 "+cert.NotAfter.Format("2006-01-02")+"）")
		return
	}

	domain := strings.TrimSpace(in.ServerName)
	if domain == "" {
		if doms := certDomains(cert); len(doms) > 0 {
			domain = doms[0]
		}
	}

	dir := tlsDir()
	if err := os.MkdirAll(dir, 0o700); err != nil {
		InternalError(c, "创建证书目录失败: "+err.Error())
		return
	}
	if err := os.WriteFile(tlsPath(tlsCertFile), certPEM, 0o600); err != nil {
		InternalError(c, "写入证书失败: "+err.Error())
		return
	}
	if err := os.WriteFile(tlsPath(tlsKeyFile), keyPEM, 0o600); err != nil {
		InternalError(c, "写入私钥失败: "+err.Error())
		return
	}

	writeAudit(c, "TLS_CERT_UPLOAD", "system", 0,
		fmt.Sprintf(`{"domain": %q, "not_after": %q}`, domain, cert.NotAfter.Format("2006-01-02")))
	OK(c, gin.H{
		"domains":   certDomains(cert),
		"not_after": cert.NotAfter.Format("2006-01-02"),
		"message":   "证书已保存，立即生效（新连接会使用新证书）",
	})
}

// AdminDeleteTLS handles DELETE /api/v1/admin/system/tls
// 移除证书，并把后端的 HTTPS 开关一并关掉。
func AdminDeleteTLS(c *gin.Context) {
	dir := tlsDir()
	// 目录不存在说明这台机器从没上传过证书，本来就是纯 HTTP。
	// 直接返回，别往下走：否则写文件会因目录缺失而报错，
	// 用户点「移除」看到一个莫名其妙的失败。
	if _, err := os.Stat(dir); err != nil {
		if os.IsNotExist(err) {
			OK(c, gin.H{"message": "当前未配置 HTTPS 证书"})
			return
		}
		InternalError(c, "读取证书目录失败: "+err.Error())
		return
	}
	_ = os.Remove(tlsPath(tlsCertFile))
	_ = os.Remove(tlsPath(tlsKeyFile))

	// 证书没了，后端自己的 HTTPS 也开不下去了：顺手关掉，
	// 否则页面显示"已开启"但握手全失败，比直接关掉更难排查。
	backendWasOn := backendTLSManager().Enabled()
	if backendWasOn {
		_ = backendTLSManager().SetEnabled(false)
	}

	writeAudit(c, "TLS_CERT_REMOVE", "system", 0, `{}`)
	msg := "证书已移除，后端不再接受 HTTPS 连接"
	if backendWasOn {
		msg += "（HTTPS 开关已一并关闭）"
	}
	OK(c, gin.H{"message": msg})
}

// AdminSetBackendTLS handles POST /api/v1/admin/system/tls/backend
//
// body: { "enabled": true }
//
// 控制后端自己的 9200 端口是否同时接受 HTTPS 连接。
//
// 打开后同一个端口既收明文也收 TLS（靠首字节嗅探判断），
// 所以：客户端可以直连 https://域名:9200，明文客户端也不受影响，
// 而且切换是即时的，不用重启后端。
//
// 打开前必须已有可用证书：没有证书却开着 HTTPS，握手必然失败，
// 不如在这里直接报错，让页面上提示先上传证书。
func AdminSetBackendTLS(c *gin.Context) {
	var in struct {
		Enabled bool `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数格式有误")
		return
	}

	mgr := backendTLSManager()
	if err := mgr.SetEnabled(in.Enabled); err != nil {
		BadRequest(c, "开启失败: "+err.Error())
		return
	}

	writeAudit(c, "TLS_BACKEND_TOGGLE", "system", 0, fmt.Sprintf(`{"enabled": %t}`, in.Enabled))
	msg := "后端已停止接受 HTTPS 连接"
	if in.Enabled {
		msg = "后端已开启 HTTPS，同一端口（9200）现在同时接受 http 与 https"
	}
	OK(c, gin.H{"enabled": in.Enabled, "message": msg})
}
