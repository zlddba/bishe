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
)

// HTTPS 证书管理。
//
// 分工是有意的：后端只负责「校验证书 → 落盘 → 生成 443 的 server 配置 →
// 丢一个 reload 信号」，真正的 nginx -t / reload 由 nginx 容器里的守护脚本执行
// （deploy/docker/nginx/tls-watch.sh）。
//
// 为什么不让 API 自己 reload：那需要把 docker.sock 挂进 API 容器，
// 等于把宿主机 root 权限交给一个 Web 进程，为了一张证书不值当。
//
// 为什么先写 .new 再换：配置写错会让 nginx 起不来，整站直接挂。
// 守护脚本先 nginx -t 校验，通过才替换并 reload，失败自动回滚旧配置。

const (
	tlsCertFile    = "fullchain.pem"
	tlsKeyFile     = "privkey.pem"
	tlsConfFile    = "https.conf"
	tlsConfNewFile = "https.conf.new"
	tlsReloadFlag  = "reload.request"
	tlsReloadState = "reload.status"
)

// tlsDir 证书与配置落盘目录：nginx 容器只读挂载同一目录。
func tlsDir() string {
	if d := strings.TrimSpace(os.Getenv("TLS_DIR")); d != "" {
		return d
	}
	return "/var/lib/patnest/tls"
}

func tlsPath(name string) string { return filepath.Join(tlsDir(), name) }

// certDomains 提取证书覆盖的域名：SAN 优先，没有 SAN 才退回 CN。
func certDomains(cert *x509.Certificate) []string {
	out := make([]string, 0, len(cert.DNSNames)+1)
	out = append(out, cert.DNSNames...)
	if len(out) == 0 && cert.Subject.CommonName != "" {
		out = append(out, cert.Subject.CommonName)
	}
	return out
}

// renderHTTPSConf 生成 443 的 server 块。
//
// location 规则抽在 locations.conf 里，9200 和 443 两个 server 共用，
// 避免以后改一处漏一处。
func renderHTTPSConf(domain string) string {
	if domain == "" {
		domain = "_"
	}
	return fmt.Sprintf(`# 由后台「系统更新 → HTTPS 证书」生成，请勿手改。
# 校验不通过时守护脚本会回滚到上一版，所以这里写错了也不会把站点搞挂。
server {
    listen 443 ssl;
    http2 on;
    server_name %s;

    ssl_certificate     %s;
    ssl_certificate_key %s;

    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_stapling        off;

    include /etc/nginx/patnest/locations.conf;
}
`, domain, filepath.Join("/etc/nginx/patnest/tls", tlsCertFile),
		filepath.Join("/etc/nginx/patnest/tls", tlsKeyFile))
}

// AdminGetTLS handles GET /api/v1/admin/system/tls
func AdminGetTLS(c *gin.Context) {
	dir := tlsDir()
	info := gin.H{
		"configured": false,
		"dir":        dir,
		"pending":    fileExists(tlsPath(tlsReloadFlag)),
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
	if raw, err := os.ReadFile(tlsPath(tlsReloadState)); err == nil {
		info["last_reload"] = strings.TrimSpace(string(raw))
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
	if err := os.WriteFile(tlsPath(tlsConfNewFile), []byte(renderHTTPSConf(domain)), 0o600); err != nil {
		InternalError(c, "生成 HTTPS 配置失败: "+err.Error())
		return
	}
	if err := os.WriteFile(tlsPath(tlsReloadFlag), []byte(time.Now().Format(time.RFC3339)), 0o600); err != nil {
		InternalError(c, "写入生效请求失败: "+err.Error())
		return
	}

	writeAudit(c, "TLS_CERT_UPLOAD", "system", 0,
		fmt.Sprintf(`{"domain": %q, "not_after": %q}`, domain, cert.NotAfter.Format("2006-01-02")))
	OK(c, gin.H{
		"pending":   true,
		"domains":   certDomains(cert),
		"not_after": cert.NotAfter.Format("2006-01-02"),
		"message":   "证书已写入，正在校验并重启 HTTPS 监听，约 5 秒后刷新查看结果",
	})
}

// AdminDeleteTLS handles DELETE /api/v1/admin/system/tls
// 移除证书并回退到纯 HTTP（443 的 server 块被清空）。
func AdminDeleteTLS(c *gin.Context) {
	dir := tlsDir()
	// 目录不存在说明这台机器从没上传过证书，本来就是纯 HTTP。
	// 直接返回，别往下走：否则写 https.conf.new 会因目录缺失而报错，
	// 用户点「移除」看到一个莫名其妙的失败。
	if _, err := os.Stat(dir); err != nil {
		if os.IsNotExist(err) {
			OK(c, gin.H{"pending": false, "message": "当前未配置 HTTPS 证书"})
			return
		}
		InternalError(c, "读取证书目录失败: "+err.Error())
		return
	}
	_ = os.Remove(tlsPath(tlsCertFile))
	_ = os.Remove(tlsPath(tlsKeyFile))

	empty := "# 未配置 HTTPS 证书。后台「系统更新 → HTTPS 证书」上传后会重写本文件。\n"
	if err := os.WriteFile(tlsPath(tlsConfNewFile), []byte(empty), 0o600); err != nil {
		InternalError(c, "清理 HTTPS 配置失败: "+err.Error())
		return
	}
	if err := os.WriteFile(tlsPath(tlsReloadFlag), []byte(time.Now().Format(time.RFC3339)), 0o600); err != nil {
		InternalError(c, "写入生效请求失败: "+err.Error())
		return
	}
	writeAudit(c, "TLS_CERT_REMOVE", "system", 0, `{}`)
	OK(c, gin.H{"pending": true, "message": "证书已移除，443 监听将在数秒内关闭"})
}

func fileExists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}
