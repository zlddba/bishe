package handler

// Let's Encrypt 证书管理（后台「HTTPS 证书」页的自动申请部分）。
//
// 验证方式走 DNS-01（Cloudflare），不占用任何端口：acme.sh 调 Cloudflare API
// 写一条 _acme-challenge TXT 记录，Let's Encrypt 从 DNS 里读到就算通过。
// 因此站点只开 9200 一个端口也能签证书，nginx 不需要监听 80。
//
// 和手工上传不是两条独立的链路，它们共用同一个落盘位置：
//
//	acme.sh --install-cert → TLS_DIR/fullchain.pem + privkey.pem
//	后台上传             → TLS_DIR/fullchain.pem + privkey.pem
//
// 所以 nginx 侧「文件一变就 reload」的逻辑、后端握手时现读证书的逻辑，
// 都不需要区分证书是哪来的。

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"patnestServer/internal/acme"
	"patnestServer/internal/cache"
)

// acmeRenewLockKey 续期任务的跨实例互斥锁。
// 蓝绿两个槽位都在跑 worker，谁先抢到谁去续。
const acmeRenewLockKey = "patnest:lock:acme-renew"

// pickDomain 选一个「当前应该用的域名」：
// 配置里填过的优先，其次用现有证书里的第一个域名，都没有就留空让用户自己填。
func pickDomain(s acme.Settings, cert *acme.CertInfo) string {
	if strings.TrimSpace(s.Domain) != "" {
		return s.Domain
	}
	if cert != nil && len(cert.Domains) > 0 {
		return cert.Domains[0]
	}
	return ""
}

// AdminGetACME handles GET /api/v1/admin/system/acme
//
// Token 只回一个「配没配」的布尔值，永远不回明文：
// 这个接口的响应会进浏览器缓存和日志，把凭证带出去不合适。
func AdminGetACME(c *gin.Context) {
	s := acme.LoadSettings()
	cert, certErr := acme.ReadCert()

	info := gin.H{
		"available":  acme.Available(),
		"version":    acme.Version(),
		"script":     acme.ScriptPath(),
		"home":       acme.Home(),
		"provider":   s.DNSProvider,
		"email":      s.Email,
		"domain":     pickDomain(s, cert),
		"auto_renew": s.AutoRenew,
		"renew_days": s.RenewDays,
		"token_set":  s.CFToken != "",
		"account_id": s.CFAccountID,
		"busy":       acme.Busy(),
		"cert":       cert,
		"last_run":   acme.ReadLastLog(),
	}
	if certErr != nil {
		info["cert_error"] = certErr.Error()
	}
	if cert != nil {
		// 前端据此决定是显示「自动续期已接管」还是「这张是手工证书」。
		info["managed"] = cert.LetsEncrypt
	}
	OK(c, info)
}

// AdminIssueACME handles POST /api/v1/admin/system/acme/issue
//
// body: { "domain": "www.example.com", "email": "ops@example.com",
//
//	"token": "Cloudflare API Token", "staging": false, "force": false }
//
// token 可以留空，此时用上次保存的那份。
func AdminIssueACME(c *gin.Context) {
	var in struct {
		Domain  string `json:"domain" binding:"required,max=512"`
		Email   string `json:"email" binding:"max=254"`
		Token   string `json:"token" binding:"max=512"`
		Staging bool   `json:"staging"`
		Force   bool   `json:"force"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写要签发证书的域名")
		return
	}

	// 用独立的 context，不挂请求的：DNS-01 要等 TXT 记录生效，最长 5 分钟，
	// 客户端切走标签页、或者代理先超时断开，都不该把已经跑了一半的 acme.sh 杀掉。
	// 断开后重新打开页面查状态，一样能看到结果（日志落在 ACME_HOME 里）。
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	res, err := acme.Issue(ctx, acme.IssueOptions{
		Domain:  in.Domain,
		Email:   in.Email,
		Staging: in.Staging,
		Force:   in.Force,
		CFToken: in.Token,
	})
	if err != nil {
		out := ""
		if res != nil {
			out = res.Output
		}
		writeAudit(c, "ACME_ISSUE_FAIL", "system", 0,
			fmt.Sprintf(`{"domain": %q, "staging": %t}`, in.Domain, in.Staging))
		FailWithDetail(c, http.StatusBadRequest, 10001, err.Error(), gin.H{
			"output": out,
			"hint":   issueHint(out),
		})
		return
	}

	// 签发成功顺手记住邮箱 / 域名 / Token，下次打开页面不用重填。
	s := acme.LoadSettings()
	if strings.TrimSpace(in.Email) != "" {
		s.Email = strings.TrimSpace(in.Email)
	}
	if strings.TrimSpace(in.Token) != "" {
		s.CFToken = strings.TrimSpace(in.Token)
	}
	s.Domain = res.Domain
	if err := acme.SaveSettings(s); err != nil {
		log.Printf("[acme] 保存设置失败: %v", err)
	}

	writeAudit(c, "ACME_ISSUE", "system", 0,
		fmt.Sprintf(`{"domain": %q, "staging": %t, "not_after": %q}`,
			res.Domain, res.Staging, certNotAfter(res)))

	msg := "证书已签发并安装，nginx 会在几秒内自动重载"
	if res.Staging {
		msg = "测试证书已签发（浏览器不信任，仅用于验证流程），确认无误后请用生产环境重签"
	}
	if res.Skipped {
		msg = "证书还在有效期内，无需重新签发"
	}
	OK(c, gin.H{
		"domain":  res.Domain,
		"staging": res.Staging,
		"skipped": res.Skipped,
		"took_ms": res.TookMS,
		"cert":    res.Cert,
		"output":  res.Output,
		"message": msg,
	})
}

// AdminRenewACME handles POST /api/v1/admin/system/acme/renew
// 手动触发一次续期（不填域名就用当前证书的域名）。
func AdminRenewACME(c *gin.Context) {
	var in struct {
		Domain string `json:"domain" binding:"max=255"`
	}
	_ = c.ShouldBindJSON(&in)

	domain := strings.TrimSpace(in.Domain)
	if domain == "" {
		if cert, _ := acme.ReadCert(); cert != nil && len(cert.Domains) > 0 {
			domain = cert.Domains[0]
		}
	}
	if domain == "" {
		BadRequest(c, "还没有证书，请先申请一张")
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()

	res, err := acme.Renew(ctx, domain)
	if err != nil {
		out := ""
		if res != nil {
			out = res.Output
		}
		writeAudit(c, "ACME_RENEW_FAIL", "system", 0, fmt.Sprintf(`{"domain": %q}`, domain))
		FailWithDetail(c, http.StatusBadRequest, 10001, err.Error(), gin.H{
			"output": out,
			"hint":   issueHint(out),
		})
		return
	}

	writeAudit(c, "ACME_RENEW", "system", 0, fmt.Sprintf(`{"domain": %q}`, domain))
	msg := "续期完成，nginx 会在几秒内自动重载"
	if res.Skipped {
		msg = "证书还没到续期时间，本次跳过"
	}
	OK(c, gin.H{"domain": domain, "skipped": res.Skipped, "cert": res.Cert, "output": res.Output, "message": msg})
}

// AdminUpdateACMESettings handles POST /api/v1/admin/system/acme/settings
//
// body: { "auto_renew": true, "email": "...", "renew_days": 30, "token": "..." }
//
// 配置落盘在 ACME_HOME，蓝绿槽位共享，改完立刻生效（worker 每轮都重新读）。
func AdminUpdateACMESettings(c *gin.Context) {
	var in struct {
		AutoRenew *bool  `json:"auto_renew"`
		Email     string `json:"email" binding:"max=254"`
		RenewDays int    `json:"renew_days"`
		Token     string `json:"token" binding:"max=512"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数格式有误")
		return
	}
	if in.Email != "" && !strings.Contains(in.Email, "@") {
		BadRequest(c, "邮箱格式不正确")
		return
	}
	if in.RenewDays != 0 && (in.RenewDays < 7 || in.RenewDays > 60) {
		BadRequest(c, "续期提前天数请填 7 到 60 之间")
		return
	}

	s := acme.LoadSettings()
	if in.AutoRenew != nil {
		s.AutoRenew = *in.AutoRenew
	}
	if in.Email != "" {
		s.Email = strings.TrimSpace(in.Email)
	}
	if in.RenewDays != 0 {
		s.RenewDays = in.RenewDays
	}
	if strings.TrimSpace(in.Token) != "" {
		s.CFToken = strings.TrimSpace(in.Token)
	}
	if err := acme.SaveSettings(s); err != nil {
		InternalError(c, "保存失败: "+err.Error())
		return
	}
	writeAudit(c, "ACME_SETTINGS", "system", 0,
		fmt.Sprintf(`{"auto_renew": %t, "renew_days": %d}`, s.AutoRenew, s.RenewDays))
	OK(c, gin.H{
		"auto_renew": s.AutoRenew,
		"email":      s.Email,
		"renew_days": s.RenewDays,
		"token_set":  s.CFToken != "",
		"message":    "已保存",
	})
}

// cfZone Cloudflare 的 zone 精简结构。
type cfZone struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// cfEnvelope Cloudflare API 统一的响应外壳。
type cfEnvelope struct {
	Success bool `json:"success"`
	Errors  []struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	} `json:"errors"`
	Result []cfZone `json:"result"`
}

// cfListZones 用 Token 列出它有权访问的 zone。
//
// 这一步同时验证三件事：Token 是否有效、有没有 Zone:Read 权限、
// 以及这个 Token 到底覆盖了哪些域名（后面签发的域名必须落在其中一个 zone 里）。
func cfListZones(ctx context.Context, token string) ([]cfZone, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		"https://api.cloudflare.com/client/v4/zones?per_page=50", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 15 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("连接 Cloudflare 失败: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))

	var env cfEnvelope
	if err := json.Unmarshal(body, &env); err != nil {
		return nil, fmt.Errorf("Cloudflare 返回了无法解析的内容（HTTP %d）", resp.StatusCode)
	}
	if !env.Success {
		msg := "Token 被拒绝"
		if len(env.Errors) > 0 {
			msg = fmt.Sprintf("%s（错误码 %d）", env.Errors[0].Message, env.Errors[0].Code)
		}
		return nil, fmt.Errorf("%s", msg)
	}
	return env.Result, nil
}

// zoneForDomain 在 zone 列表里找能覆盖该域名的那个（长后缀匹配）。
func zoneForDomain(domain string, zones []cfZone) string {
	best := ""
	for _, z := range zones {
		if domain == z.Name || strings.HasSuffix(domain, "."+z.Name) {
			if len(z.Name) > len(best) {
				best = z.Name
			}
		}
	}
	return best
}

// AdminCheckACME handles POST /api/v1/admin/system/acme/check
//
// body: { "domain": "www.example.com", "token": "..." }
//
// 会真的调一次 Cloudflare API，把「凭证无效 / 权限不够 / zone 不覆盖」
// 这几种最典型的问题区分开——否则用户只会看到 acme.sh 吐一句 Invalid response。
func AdminCheckACME(c *gin.Context) {
	var in struct {
		Domain string `json:"domain" binding:"max=512"`
		Token  string `json:"token" binding:"max=512"`
	}
	_ = c.ShouldBindJSON(&in)

	s := acme.LoadSettings()
	token := strings.TrimSpace(in.Token)
	if token == "" {
		token = s.CFToken
	}
	domain := strings.TrimSpace(in.Domain)
	if domain == "" {
		domain = pickDomain(s, nil)
	}
	if domain == "" {
		BadRequest(c, "请先填写域名")
		return
	}
	if !acme.DomainPattern.MatchString(strings.ToLower(domain)) {
		BadRequest(c, "域名格式不正确")
		return
	}
	if token == "" {
		BadRequest(c, "请先填写 Cloudflare API Token")
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), 20*time.Second)
	defer cancel()

	zones, err := cfListZones(ctx, token)
	if err != nil {
		OK(c, gin.H{
			"ok":     false,
			"detail": err.Error(),
			"hint": "Token 校验没过。请到 Cloudflare 控制台 My Profile → API Tokens 新建一个，" +
				"权限至少给 Zone → Zone → Read 和 Zone → DNS → Edit，" +
				"Zone Resources 要包含 " + strings.ToLower(domain) + " 所在的域。",
		})
		return
	}

	names := make([]string, 0, len(zones))
	for _, z := range zones {
		names = append(names, z.Name)
	}
	match := zoneForDomain(strings.ToLower(domain), zones)
	if match == "" {
		OK(c, gin.H{
			"ok":     false,
			"zones":  names,
			"detail": "Token 可用，但它能访问的域名里没有 " + domain,
			"hint": "请确认 Token 的 Zone Resources 选中了 " + strings.ToLower(domain) +
				" 所在的域（zone），或者换一个权限更大的 Token。",
		})
		return
	}

	OK(c, gin.H{
		"ok":    true,
		"zone":  match,
		"zones": names,
		"hint":  "Token 有效，能管理 " + match + " 的 DNS，可以开始申请证书",
	})
}

// issueHint 把 acme.sh 的英文报错翻译成可操作的中文提示。
// 只覆盖最常见的几种，其余原样返回，交给日志。
func issueHint(out string) string {
	low := strings.ToLower(out)
	switch {
	case strings.Contains(low, "invalid domain") || strings.Contains(low, "nxdomain"):
		return "域名解析失败：请确认域名已经能在公网上解析（DNS-01 验证要求域名本身真实存在）。"
	case strings.Contains(low, "invalid_access_key") || strings.Contains(low, "authentication error") ||
		strings.Contains(low, "unauthorized") || strings.Contains(low, "invalid token"):
		return "Cloudflare 拒绝了 API Token：请到控制台确认 Token 未过期、" +
			"权限包含 Zone:Zone:Read 与 Zone:DNS:Edit，并且覆盖了这个域名所在的 zone。"
	case strings.Contains(low, "no zone") || strings.Contains(low, "zone not found"):
		return "在 Token 的权限范围里找不到这个域名所属的 zone，请检查 Zone Resources 的设置。"
	case strings.Contains(low, "too many certificates") || strings.Contains(low, "rate limit"):
		return "触发了 Let's Encrypt 的签发频率限制（同一域名每周 5 张）。" +
			"请先勾选「测试环境」验证流程，或等一周后再试。"
	case strings.Contains(low, "add txt record") || strings.Contains(low, "please add"):
		return "始终读不到 _acme-challenge TXT 记录：多半是 Token 只有读权限、写不进 DNS，" +
			"或者这个域名的 NS 并不在 Cloudflare 上。"
	case strings.Contains(low, "timeout"):
		return "与 Let's Encrypt 或 Cloudflare 的通信超时，请检查服务器出网，稍后重试。"
	}
	return ""
}

func certNotAfter(res *acme.Result) string {
	if res != nil && res.Cert != nil {
		return res.Cert.NotAfter
	}
	return ""
}

// StartACMERenewWorker 定时检查 Let's Encrypt 证书并在临期时续期。
//
// 之所以放在 Go 里而不是给容器装 cron：
//   - 镜像里没有 cron，多装一个守护进程只为跑一条命令不划算；
//   - worker 跑在 API 进程里，能直接读配置、打日志、写审计；
//   - 蓝绿两个槽位都会跑，靠 Redis 锁互斥，谁抢到谁续。
func StartACMERenewWorker(ctx context.Context, interval time.Duration) {
	go func() {
		// 启动后等 2 分钟：让数据库、Redis、nginx 都就位，
		// 也避免蓝绿切换时两个新进程同时开抢。
		select {
		case <-ctx.Done():
			return
		case <-time.After(2 * time.Minute):
		}
		renewACMECert(ctx)
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				renewACMECert(ctx)
			}
		}
	}()
}

func renewACMECert(ctx context.Context) {
	if !acme.Available() {
		return
	}
	s := acme.LoadSettings()
	if !s.AutoRenew {
		return
	}
	cert, err := acme.ReadCert()
	if err != nil || cert == nil {
		return
	}
	// 手工上传的证书（比如 Cloudflare Origin CA）不归 worker 管：
	// 那些证书没有对应的 acme 账户目录，硬续会直接报错。
	if !cert.LetsEncrypt {
		return
	}
	if cert.DaysLeft > s.RenewDays || len(cert.Domains) == 0 {
		return
	}
	// 续期要写 DNS，没有凭证就只能等管理员在页面上补。
	if s.CFToken == "" {
		log.Printf("[acme] 证书剩余 %d 天，但没有 Cloudflare Token，跳过自动续期", cert.DaysLeft)
		return
	}

	if cache.Enabled() {
		n, cerr := cache.IncrWindow(acmeRenewLockKey, 30*time.Minute)
		if cerr == nil && n != 1 {
			return // 另一个槽位已经在续了
		}
	}

	log.Printf("[acme] 证书剩余 %d 天（阈值 %d 天），开始续期 %s", cert.DaysLeft, s.RenewDays, cert.Domains[0])
	res, err := acme.Renew(ctx, cert.Domains[0])
	if err != nil {
		log.Printf("[acme] 续期失败: %v", err)
		return
	}
	if res.Skipped {
		log.Printf("[acme] 续期跳过：证书还没到续期时间")
		return
	}
	log.Printf("[acme] 续期完成，新证书到期 %s", certNotAfter(res))
}
