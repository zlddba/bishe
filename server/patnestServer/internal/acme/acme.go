// Package acme 把内置的 acme.sh 包一层，供后台「HTTPS 证书」页面申请、续期
// Let's Encrypt 证书。
//
// 为什么用 acme.sh 而不是 certbot：
//   - 它是一个单文件 sh 脚本（vendor/acme.sh，镜像里 COPY 到 /opt/acme），
//     alpine 上补 curl + openssl 就能跑；certbot 要拖一整套 python 运行时。
//   - 没有常驻进程：签发和续期都是同一条 --issue / --renew 命令，
//     状态全在 ACME_HOME 目录里，进程重启不影响。
//
// 为什么用 DNS-01 而不是 HTTP-01：
//
//	站点只对外开 9200 一个端口，80 在有些机器上还要留给别的服务。
//	DNS-01 只在域名解析里加一条 TXT 记录，不占用任何端口，也不需要停机。
//	代价是要有 DNS 服务商的 API 凭证（本项目默认 Cloudflare）。
//
// 证书的最终落盘位置与手工上传完全一致（TLS_DIR 下的 fullchain.pem /
// privkey.pem），所以 nginx 那边「证书变了就 reload」的逻辑不需要任何改动。
package acme

import (
	"bytes"
	"context"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"patnestServer/internal/backendtls"
)

const (
	defaultScript = "/opt/acme/acme.sh"
	defaultHome   = "/var/lib/patnest/acme"

	// ServerLE / ServerStaging 是 acme.sh 的 --server 别名：
	//   letsencrypt       生产环境，签出来的证书浏览器信任，但有速率限制
	//   letsencrypt_test  测试环境，签出来的证书不被信任，但可以随便试
	ServerLE      = "letsencrypt"
	ServerStaging = "letsencrypt_test"

	// DNSProviderCF 是 acme.sh 的 Cloudflare DNS 插件名。
	// 换成别的服务商时，这里要跟着换成 dns_dp(DNSPod) / dns_ali(阿里云) 等，
	// 对应的凭证环境变量也不同（见 acme.sh 的 dnsapi 目录）。
	DNSProviderCF = "dns_cf"

	// logFile 记录最近一次签发 / 续期的完整输出，方便后台排错。
	logFile = "last-run.log"

	// issueTimeout 单次签发的上限。DNS-01 要等 TXT 记录生效，
	// 正常 30~90 秒，慢的时候两三分钟，超过这个时间基本是凭证或 DNS 的问题。
	issueTimeout = 5 * time.Minute
)

// DomainPattern 域名白名单校验。
//
// 这个值会拼进 acme.sh 的命令行参数，所以必须严格限制字符集，
// 否则后台一旦被拿到管理员权限，就等于把 shell 交出去了。
var DomainPattern = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$`)

func envOr(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

// ScriptPath acme.sh 脚本位置。
func ScriptPath() string { return envOr("ACME_SCRIPT", defaultScript) }

// Home acme.sh 的账户目录（含账户密钥、每个域名的证书与配置）。
func Home() string { return envOr("ACME_HOME", defaultHome) }

// Available 判断容器里是否有可用的 acme.sh。
func Available() bool {
	st, err := os.Stat(ScriptPath())
	return err == nil && !st.IsDir()
}

// Version 读脚本头部声明的版本号（脚本里写的是 VER=3.1.7）。
func Version() string {
	f, err := os.Open(ScriptPath())
	if err != nil {
		return ""
	}
	defer f.Close()
	buf := make([]byte, 64*1024)
	n, _ := f.Read(buf)
	for _, line := range strings.Split(string(buf[:n]), "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "VER=") {
			return strings.Trim(strings.TrimPrefix(line, "VER="), `"'`)
		}
	}
	return ""
}

// CertInfo 当前生效证书的关键信息，与手工上传页展示的是同一份数据。
type CertInfo struct {
	Domains  []string `json:"domains"`
	Issuer   string   `json:"issuer"`
	NotAfter string   `json:"not_after"`
	DaysLeft int      `json:"days_left"`
	// LetsEncrypt 表示这张证书是 Let's Encrypt 签的，
	// 只有这种证书才会被自动续期 worker 接管。
	LetsEncrypt bool `json:"lets_encrypt"`
}

// ReadCert 解析 TLS_DIR 里的当前证书。文件不存在时返回 nil, nil。
func ReadCert() (*CertInfo, error) {
	raw, err := os.ReadFile(filepath.Join(backendtls.Dir(), backendtls.CertFile))
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil, nil
		}
		return nil, err
	}
	block, _ := pem.Decode(raw)
	if block == nil {
		return nil, fmt.Errorf("证书文件不是合法的 PEM")
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return nil, err
	}

	out := &CertInfo{
		Domains:  cert.DNSNames,
		Issuer:   cert.Issuer.CommonName,
		NotAfter: cert.NotAfter.Format("2006-01-02"),
		DaysLeft: int(time.Until(cert.NotAfter).Hours() / 24),
		LetsEncrypt: strings.Contains(cert.Issuer.CommonName, "Let's Encrypt") ||
			strings.Contains(strings.Join(cert.Issuer.Organization, ","), "Let's Encrypt"),
	}
	if len(out.Domains) == 0 && cert.Subject.CommonName != "" {
		out.Domains = []string{cert.Subject.CommonName}
	}
	return out, nil
}

// IssueOptions 一次签发请求的参数。
type IssueOptions struct {
	// Domain 主域名，可写多个（英文逗号分隔），第一个作为证书主名。
	Domain  string
	Email   string
	Staging bool
	// Force 忽略「域名没变、还没到续期时间」的判断，强制重签。
	// 刚改过 DNS、或者之前用测试环境签过一次，都要用它。
	Force bool
	// Cloudflare 凭证（DNS-01 验证用）。Token 为空时会退回 Settings 里存的那份。
	CFToken     string
	CFAccountID string
}

// Result 签发 / 续期的结果。
type Result struct {
	Domain   string `json:"domain"`
	Staging  bool   `json:"staging"`
	Cert     *CertInfo
	Output   string `json:"output"`
	TookMS   int64  `json:"took_ms"`
	Skipped  bool   `json:"skipped"`
	ExitCode int    `json:"exit_code"`
}

// mutex 保证同一进程内不会有两个签发任务同时跑：
// acme.sh 的账户目录不是并发安全的，两个请求撞在一起会互相覆盖配置。
var mutex sync.Mutex

// Busy 报告当前是否有签发任务在跑，后台用它禁用按钮。
func Busy() bool {
	if mutex.TryLock() {
		mutex.Unlock()
		return false
	}
	return true
}

// splitDomains 把 "a.com, b.com" 拆成去重后的列表。
func splitDomains(raw string) []string {
	seen := map[string]bool{}
	out := make([]string, 0, 2)
	for _, part := range strings.FieldsFunc(raw, func(r rune) bool { return r == ',' || r == ' ' || r == '\n' || r == ';' }) {
		d := strings.ToLower(strings.TrimSpace(part))
		if d == "" || seen[d] {
			continue
		}
		seen[d] = true
		out = append(out, d)
	}
	return out
}

// Issue 申请（或续期）一张 Let's Encrypt 证书，并安装到 TLS_DIR。
//
// 流程分两步，都是 acme.sh 的标准用法：
//  1. --issue --dns dns_cf：acme.sh 调 Cloudflare API 写一条 _acme-challenge
//     TXT 记录，Let's Encrypt 从 DNS 里读到它就算验证通过，全程不碰任何端口；
//     成功后证书落在 ACME_HOME 里。
//  2. --install-cert：把证书和私钥复制到 TLS_DIR/fullchain.pem、privkey.pem。
//     nginx 容器 5 秒一扫，发现文件变了就 reload，所以装完即生效。
//
// 第 2 步的路径会被 acme.sh 记在 ACME_HOME 的域名配置里，
// 之后 --renew 自动重复同样的安装动作，不用再传一遍。
func Issue(ctx context.Context, opt IssueOptions) (*Result, error) {
	domains := splitDomains(opt.Domain)
	if len(domains) == 0 {
		return nil, fmt.Errorf("请填写要签发证书的域名")
	}
	for _, d := range domains {
		if !DomainPattern.MatchString(d) {
			return nil, fmt.Errorf("域名格式不正确：%s（只支持形如 pet.example.com 的写法）", d)
		}
	}
	email := strings.TrimSpace(opt.Email)
	if email != "" && (!strings.Contains(email, "@") || len(email) > 254) {
		return nil, fmt.Errorf("邮箱格式不正确")
	}

	s := LoadSettings()
	token := strings.TrimSpace(opt.CFToken)
	if token == "" {
		token = s.CFToken
	}
	accountID := strings.TrimSpace(opt.CFAccountID)
	if accountID == "" {
		accountID = s.CFAccountID
	}
	if token == "" {
		return nil, fmt.Errorf("请先填写 Cloudflare API Token：DNS 验证要用它去写 _acme-challenge 记录")
	}
	if !Available() {
		return nil, fmt.Errorf("容器内没有找到 acme.sh（%s），请确认镜像是最新版", ScriptPath())
	}

	mutex.Lock()
	defer mutex.Unlock()

	if err := os.MkdirAll(Home(), 0o700); err != nil {
		return nil, fmt.Errorf("创建 ACME 目录失败: %w", err)
	}

	start := time.Now()
	server := ServerLE
	if opt.Staging {
		server = ServerStaging
	}

	args := []string{
		"--home", Home(),
		"--config-home", Home(),
		"--server", server,
		"--issue",
		"--dns", DNSProviderCF,
		"--keylength", "ec-256",
		// 容器里没有 cron，auto-upgrade 只会白跑一遍网络请求
		"--auto-upgrade", "0",
		"--log-level", "2",
	}
	for _, d := range domains {
		args = append(args, "-d", d)
	}
	if email != "" {
		args = append(args, "--accountemail", email)
	}
	if opt.Force {
		args = append(args, "--force")
	}

	out, err := run(ctx, dnsEnv(token, accountID), args...)
	res := &Result{Domain: domains[0], Staging: opt.Staging}

	if err != nil {
		res.Output = tail(out+"\n"+err.Error(), 60)
		res.ExitCode = exitCode(err)
		saveLog(res, false)
		return res, fmt.Errorf("签发失败：%s", firstMeaningfulLine(out, err))
	}

	// 第二步：安装到 TLS 目录。路径固定，和手工上传保持一致。
	installArgs := []string{
		"--home", Home(),
		"--config-home", Home(),
		"--install-cert",
		"-d", domains[0],
		"--ecc",
		"--key-file", filepath.Join(backendtls.Dir(), backendtls.KeyFile),
		"--fullchain-file", filepath.Join(backendtls.Dir(), backendtls.CertFile),
	}
	installOut, installErr := run(ctx, nil, installArgs...)
	out += installOut
	if installErr != nil {
		res.Output = tail(out+"\n"+installErr.Error(), 60)
		res.ExitCode = exitCode(installErr)
		saveLog(res, false)
		return res, fmt.Errorf("证书已签发但安装失败：%s", firstMeaningfulLine(installOut, installErr))
	}

	// acme.sh 在「域名没变、还没到续期时间」时会打印 Skip 并直接返回 0，
	// 这时证书文件没有变化，前端据此提示「无需续期」而不是「签发成功」。
	res.Skipped = strings.Contains(out, "Skip, Next renewal time") ||
		strings.Contains(out, "Domains not changed")
	res.TookMS = time.Since(start).Milliseconds()
	res.Output = tail(out, 60)
	if ci, cerr := ReadCert(); cerr == nil {
		res.Cert = ci
	}
	saveLog(res, true)
	return res, nil
}

// Renew 续期。
//
// DNS-01 的好处在这里体现得最明显：Cloudflare 的 TXT 记录默认留在 zone 里，
// 所以续期时 acme.sh 只是改一下记录值再验一次，不需要人工介入，
// 也不需要开放任何端口。
//
// acme.sh 的 --renew 只在证书快到期时才真正续，
// 所以这个函数可以放心被 worker 反复调用。
func Renew(ctx context.Context, domain string) (*Result, error) {
	domain = strings.ToLower(strings.TrimSpace(domain))
	if !DomainPattern.MatchString(domain) {
		return nil, fmt.Errorf("域名格式不正确：%s", domain)
	}

	mutex.Lock()
	defer mutex.Unlock()
	if !Available() {
		return nil, fmt.Errorf("容器内没有找到 acme.sh")
	}
	s := LoadSettings()
	if s.CFToken == "" {
		return nil, fmt.Errorf("没有保存 Cloudflare API Token，无法续期")
	}

	start := time.Now()
	args := []string{
		"--home", Home(),
		"--config-home", Home(),
		"--renew",
		"-d", domain,
		"--ecc",
		"--auto-upgrade", "0",
		"--log-level", "2",
	}
	out, err := run(ctx, dnsEnv(s.CFToken, s.CFAccountID), args...)
	res := &Result{Domain: domain, TookMS: time.Since(start).Milliseconds(), Output: tail(out, 60)}
	if err != nil {
		res.ExitCode = exitCode(err)
		saveLog(res, false)
		return res, fmt.Errorf("续期失败：%s", firstMeaningfulLine(out, err))
	}

	res.Skipped = strings.Contains(out, "Skip, Next renewal time") ||
		strings.Contains(out, "Skipping. Next renewal time")
	if ci, cerr := ReadCert(); cerr == nil {
		res.Cert = ci
	}
	saveLog(res, true)
	return res, nil
}

// dnsEnv 把 Cloudflare 凭证拼成 acme.sh 需要的环境变量。
//
// acme.sh 的 dns_cf 插件只认环境变量，不接受命令行参数：
//
//	CF_Token        用户 API Token（推荐，权限给 Zone:DNS:Edit + Zone:Zone:Read）
//	CF_Account_ID   同名 zone 出现在多个账户下时才需要
func dnsEnv(token, accountID string) []string {
	env := []string{"CF_Token=" + token}
	if strings.TrimSpace(accountID) != "" {
		env = append(env, "CF_Account_ID="+strings.TrimSpace(accountID))
	}
	return env
}

// LastRun 最近一次签发 / 续期的执行记录（后台「查看日志」用）。
type LastRun struct {
	At     time.Time `json:"at"`
	Domain string    `json:"domain"`
	OK     bool      `json:"ok"`
	Output string    `json:"output"`
}

// ReadLastLog 读最近一次执行的日志。没有记录时返回 nil。
func ReadLastLog() *LastRun {
	raw, err := os.ReadFile(filepath.Join(Home(), logFile))
	if err != nil {
		return nil
	}
	text := string(raw)
	// 第一行是元信息：2026-11-12T23:50:00+08:00|domain|ok
	header, body, _ := strings.Cut(text, "\n")
	parts := strings.Split(header, "|")
	if len(parts) < 3 {
		return &LastRun{Output: tail(text, 60)}
	}
	at, _ := time.Parse(time.RFC3339, parts[0])
	return &LastRun{
		At:     at,
		Domain: parts[1],
		OK:     parts[2] == "ok",
		Output: body,
	}
}

func saveLog(res *Result, ok bool) {
	if err := os.MkdirAll(Home(), 0o700); err != nil {
		return
	}
	status := "fail"
	if ok {
		status = "ok"
	}
	content := time.Now().Format(time.RFC3339) + "|" + res.Domain + "|" + status + "\n" + res.Output
	_ = os.WriteFile(filepath.Join(Home(), logFile), []byte(content), 0o600)
}

// run 执行 acme.sh，把 stdout 与 stderr 合并捕获（acme.sh 的进度都在 stderr）。
//
// extraEnv 用于传 DNS 服务商的凭证，不写进命令行参数，
// 这样 Token 不会出现在进程列表（ps）里。
func run(ctx context.Context, extraEnv []string, args ...string) (string, error) {
	cctx, cancel := context.WithTimeout(ctx, issueTimeout)
	defer cancel()

	var buf bytes.Buffer
	cmd := exec.CommandContext(cctx, "sh", ScriptPath())
	cmd.Args = append(cmd.Args, args...)
	cmd.Stdout = &buf
	cmd.Stderr = &buf
	// acme.sh 会读 LE_CONFIG_HOME / LE_WORKING_DIR：
	//   LE_CONFIG_HOME  账户密钥与证书的存放位置（挂载卷，重建容器不丢）
	//   LE_WORKING_DIR  脚本自己的目录，dnsapi/ 插件就是从这里加载的。
	// 两个变量必须分开——把 WORKING_DIR 指到配置目录会让它找不到 dns_cf.sh。
	cmd.Env = append(os.Environ(),
		"LE_CONFIG_HOME="+Home(),
		"LE_WORKING_DIR="+filepath.Dir(ScriptPath()),
		"ACME_HOME="+Home(),
	)
	cmd.Env = append(cmd.Env, extraEnv...)
	err := cmd.Run()
	if cctx.Err() == context.DeadlineExceeded {
		return buf.String(), fmt.Errorf("执行超时（超过 %s）", issueTimeout)
	}
	return buf.String(), err
}

func exitCode(err error) int {
	var ee *exec.ExitError
	if errors.As(err, &ee) {
		return ee.ExitCode()
	}
	return -1
}

// tail 只保留最后 n 行：acme.sh 的输出动辄上百行，全塞给前端没人看。
func tail(s string, n int) string {
	s = strings.TrimRight(s, "\n")
	if s == "" {
		return ""
	}
	lines := strings.Split(s, "\n")
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	return strings.Join(lines, "\n")
}

// firstMeaningfulLine 从输出里挑一行最能说明问题的文本作为错误摘要。
//
// acme.sh 失败时的关键信息通常是这几类：
//
//	Please add TXT record / Invalid response / Error / Can not get token
//
// 找不到就用最后一行兜底。
func firstMeaningfulLine(out string, err error) string {
	keywords := []string{"Please add", "Invalid response", "Error", "error", "Can not", "Cannot", "failed", "Failed", "not found"}
	lines := strings.Split(out, "\n")
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		for _, kw := range keywords {
			if strings.Contains(line, kw) {
				return truncate(line, 300)
			}
		}
	}
	for i := len(lines) - 1; i >= 0; i-- {
		if line := strings.TrimSpace(lines[i]); line != "" {
			return truncate(line, 300)
		}
	}
	if err != nil {
		return truncate(err.Error(), 300)
	}
	return "未知错误"
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}

// Settings 后台可改的 ACME 配置。
//
// 这些值不放在 .env 里，是因为改 .env 要重建容器；
// 而「换个 Token」「关掉自动续期」这类操作应该点一下就好。
// 落盘在 ACME_HOME/settings.json（0600），蓝绿两个槽位挂的是同一个目录，天然同步。
// 首次读取时用环境变量兜底，方便装在裸机/别的编排里也能配置。
type Settings struct {
	Email     string `json:"email"`
	Domain    string `json:"domain"`
	AutoRenew bool   `json:"auto_renew"`
	RenewDays int    `json:"renew_days"`
	// DNSProvider 目前只支持 Cloudflare（dns_cf），
	// 留这个字段是为了以后换服务商时不用动存储格式。
	DNSProvider string `json:"dns_provider"`
	CFToken     string `json:"cf_token"`
	CFAccountID string `json:"cf_account_id"`
}

func settingsPath() string { return filepath.Join(Home(), "settings.json") }

// LoadSettings 读配置；文件不存在或格式坏了都退回环境变量默认值，
// 不能因为一个坏文件让整个证书页面打不开。
func LoadSettings() Settings {
	s := Settings{
		Email:       envOr("ACME_EMAIL", ""),
		Domain:      strings.TrimSpace(os.Getenv("ACME_DOMAIN")),
		AutoRenew:   envOr("ACME_AUTO_RENEW", "true") != "false",
		RenewDays:   30,
		DNSProvider: DNSProviderCF,
		CFToken:     strings.TrimSpace(os.Getenv("CF_Token")),
		CFAccountID: strings.TrimSpace(os.Getenv("CF_Account_ID")),
	}
	if v := envOr("ACME_RENEW_DAYS", ""); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 && n < 90 {
			s.RenewDays = n
		}
	}
	raw, err := os.ReadFile(settingsPath())
	if err != nil {
		return s
	}
	var saved Settings
	if json.Unmarshal(raw, &saved) == nil {
		if saved.Email != "" {
			s.Email = saved.Email
		}
		if saved.Domain != "" {
			s.Domain = saved.Domain
		}
		if saved.RenewDays > 0 && saved.RenewDays < 90 {
			s.RenewDays = saved.RenewDays
		}
		if saved.DNSProvider != "" {
			s.DNSProvider = saved.DNSProvider
		}
		if saved.CFToken != "" {
			s.CFToken = saved.CFToken
		}
		if saved.CFAccountID != "" {
			s.CFAccountID = saved.CFAccountID
		}
		if saved.AutoRenew != s.AutoRenew {
			s.AutoRenew = saved.AutoRenew
		}
	}
	return s
}

// SaveSettings 把配置写回 ACME_HOME。目录不存在时先建。
func SaveSettings(s Settings) error {
	if err := os.MkdirAll(Home(), 0o700); err != nil {
		return err
	}
	if s.DNSProvider == "" {
		s.DNSProvider = DNSProviderCF
	}
	raw, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(settingsPath(), raw, 0o600)
}
