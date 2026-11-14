# 图片域名统一与 HTTPS 证书

> 对应改动：2026-11-10。图片从「对象存储直链」改为「本站域名 + Nginx 反代」，
> 并在后台「系统更新」页加入 HTTPS 证书上传。

## 一、为什么要把图片链接改到本站域名

改之前，上传接口返回的是对象存储直链：

```
http://zdz1.linkzdsada.dpdns.org:9000/patnest-uploads/uploads/2026/11/xxx.jpg
```

问题有三个：

1. **小程序要配两个合法域名**：业务域名之外，还要把对象存储域名加进
   `downloadFile` 白名单，否则 `<image>` 加载失败；
2. **对象存储必须暴露公网**，还得单独为它申请证书，否则 HTTPS 页面里会
   混入 HTTP 图片被浏览器拦掉；
3. **换存储厂商等于换全部图片链接**，历史数据要跟着迁移。

改完之后统一走：

```
https://<你的域名>/files/uploads/2026/11/xxx.jpg
```

由 Nginx 的 `location /files/` 反代到对象存储的 bucket，路径前缀被剥掉、原样转发。

## 二、图片：需要改的三处

### 1. 后端 .env

```ini
# 图片对外前缀，必须包含 /files
PUBLIC_FILE_BASE=https://api.example.com/files
```

留空则回退到对象存储直链（兼容老部署）。配置后**新上传**的图片返回新域名。

### 2. Nginx 反代

容器部署：`deploy/docker/nginx/locations.conf` 里的 `location /files/`，
目标地址在**构建镜像时**由 `MINIO_SCHEME / MINIO_ENDPOINT / MINIO_BUCKET`
替换写入（compose 从 `.env` 读取，与后端同一份来源）：

```nginx
location /files/ {
    proxy_pass http://对象存储地址/桶名/;   # 末尾的 / 不能省
    ...
}
```

> 为什么是构建时替换而不是启动时 envsubst：Windows Docker 下「把单个模板
> 文件挂进一个不存在的目录」实测不生效（模板没被渲染，nginx 用的还是默认配置）。
> 构建时替换更可控，代价是改对象存储地址要重新 build 镜像。

systemd 部署：直接改 `deploy/nginx/patnest.conf` 里的 `location /files/`，
把地址换成实际的 `MINIO_ENDPOINT` / `MINIO_BUCKET`，然后 `nginx -t && nginx -s reload`。

### 3. 数据库只存相对路径（0008 已处理）

数据库里**不再保存域名**，所有图片字段统一是 `/uploads/YYYY/MM/xxx.jpg`：

| 表 | 字段 |
|---|---|
| `users` | `avatar_url` |
| `pets` | `avatar_url`、`photo_urls[]`、`vaccine_urls[]` |
| `sitters` | `cover_photo_url`、`detail_photos[]`、`license_photo_url`、`id_card_photo_url` |
| `dynamics` | `photos[]` |
| `reviews` | `owner_photos[]` |

迁移 `0008_file_relative_paths.sql` 已把历史绝对地址批量还原成相对路径：
`fn_rel_file()` 剥掉 `http(s)://任意域名/(files|patnest-uploads)/` 前缀，数组逐元素处理，
可重复执行。**换域名、换协议、切 CDN 都不会让存量图片失效。**

发现还有别的旧格式（其他存储来源、导入的数据）时手工补一条即可：

```sql
UPDATE users SET avatar_url = fn_rel_file(avatar_url) WHERE avatar_url ~ '^https?://';
```

拼接在 HTTP 边界完成（`internal/middleware/fileurl.go`），不写进任何一条 SQL：

| 方向 | 变化 |
|---|---|
| 出站 | `"/uploads/2026/11/a.jpg"` → `"https://api.example.com/files/uploads/2026/11/a.jpg"` |
| 入站 | `"https://api.example.com/files/uploads/2026/11/a.jpg"` → `"/uploads/2026/11/a.jpg"` |

只改 JSON 里**整段就是路径**的字段值（匹配 `"` + 前缀），正文中提到链接的普通文本不会被改；
CSV 导出、文件下载等非 JSON 响应直接透传，不缓冲。

> 为什么不写进 SQL：图片字段散在 20 多个接口、几十条 SELECT 里（含数组列），
> 逐列套 `fn_file_url()` 一定会漏；写入侧还要反向剥前缀，等于两处维护。
> 放在边界只改一次，新加接口自动生效。

## 三、HTTPS 证书

> **站点只监听 9200，443 已关闭。**
> 同一个端口同时收 http 与 https：nginx 用 `stream` + `ssl_preread` 看首字节分流，
> TLS 交给本机 8443（nginx 终结 TLS），明文交给本机 8080。
> （早期版本设想让后端进程自己终结 9200 的 TLS，但容器部署里 9200 由 nginx 占用、
> API 只在内网 9201，客户端根本到不了后端，该设想不成立，已改为 nginx 终结。）

### 后台操作：自动申请（推荐）

「系统更新」页底部 → **HTTPS 证书 → Let's Encrypt 自动证书**：

1. **域名**：要签的域名，多个用英文逗号分隔，第一个作为主名；
2. **CF Token**：Cloudflare API Token，权限给 `Zone → Zone → Read` 与
   `Zone → DNS → Edit`，Zone Resources 选中该域名所在的域；
3. 点 **验证 Token**——后端会真的调一次 Cloudflare API，告诉你这个 Token 能管哪些域名、
   目标域名是否落在里面（比事后从 acme.sh 的报错里猜快得多）；
4. 点 **申请 / 更新证书**，10~60 秒出结果。

验证方式是 **DNS-01**：acme.sh 调 Cloudflare API 写一条
`_acme-challenge.<域名>` TXT 记录，Let's Encrypt 从 DNS 里读到就算通过。
所以**不需要 80 端口、不需要 443、也不需要停机**，站点只开 9200 一样能签。

签发成功后证书直接写进 `nginx/tls/fullchain.pem` + `privkey.pem`，与手工上传的位置完全一致；
nginx 入口脚本 5 秒内发现文件变化并 `nginx -s reload`，不用重启容器。

到期前 `ACME_RENEW_DAYS`（默认 30）天时，后端 worker（每 12 小时一次）会自动续期——
续期复用同一条 TXT 记录机制，同样不需要人工介入。蓝绿两个槽位都会跑这个 worker，
靠 Redis 锁互斥，只有一个真正执行。

几个细节：

- **Token 只写不读**：接口只回 `token_set: true/false`，永远不回明文；Token 存在
  `data/acme/settings.json`（0600），不在 `.env` 里；
- **测试环境开关**：打开后签的是 Let's Encrypt 测试证书（浏览器不信任），
  用来验证 Token、DNS 权限、域名归属，且不消耗正式环境每周 5 张的配额；
- **手工上传的证书不会被接管**：worker 只续 `Issuer` 含 "Let's Encrypt" 的证书，
  Cloudflare Origin CA 之类的证书仍然走手工上传那条路。

### 后台操作：手工上传

「系统更新」页底部 → **HTTPS 证书**：

1. 「证书链」粘贴 `fullchain.pem` 全文（站点证书 + 中间证书，按顺序拼接）；
2. 「私钥」粘贴 `privkey.pem` 全文；
3. 域名可留空（自动取证书里的第一个域名）；
4. 点「保存并生效」，**立即生效**，不需要重启；
5. 打开「**后端同时接受 HTTPS**」开关——后端的 9200 端口开始接受 https 请求。

保存时会校验：

- PEM 能否解析、**私钥与证书是否配对**（不匹配直接拒绝）；
- 证书是否已过期；
- 私钥会以 `0600` 落盘。

### 生效机制

证书是**握手时现读**的（`tls.Config.GetCertificate`），带 mtime 缓存：

- 重新上传证书 → 新的连接立刻用新证书，不用重启后端；
- 开启/关闭开关 → 即时生效（内存状态直接改）；
- 开关落盘在 `TLS_DIR/backend_tls`，蓝绿双槽位的另一个实例每 5 秒同步一次。

nginx 的角色：它是**对外唯一**的 TLS 终结者。默认配置里 9200 由 `stream` 块监听并分流，
8443 那个 server 用挂载进来的 `fullchain.pem` / `privkey.pem` 做握手；后端 9201 只在
容器网络内，nginx 用明文（或 `SERVER_SCHEME=https` 时的 https）反代过去。
`deploy/docker/nginx/entrypoint.sh` 会盯着证书文件的变化，用户重新上传后 5 秒内自动
`nginx -s reload`，所以换证书仍然不需要重启容器。

### 同一端口两种协议

`stream` 块用 `ssl_preread` 看连接首字节：

| 首字节 | 判定 | 去向 |
|---|---|---|
| `0x16` | TLS 握手记录 | 本机 `8443`，由 nginx 终结 TLS |
| ASCII 字母 / 其他 | 明文 HTTP | 本机 `8080` |

两个 server 共用同一份 `locations.conf`，所以管理页面、`/api`、`/files` 在两种协议下
行为完全一致。转发时带 PROXY protocol，HTTP 层用 `real_ip` 还原真实客户端 IP——
否则所有请求的 `$remote_addr` 都是 `127.0.0.1`，登录限流会把全网算成同一个人。

所以 `http://域名:9200` 和 `https://域名:9200` 都能用，浏览器地址栏换协议不影响登录态。

> 后端进程自身也实现了「首字节嗅探」（`0x16` 交给 `tls.Server`，ASCII 走明文），
> 那是给 systemd 部署下客户端直连后端用的；容器部署里客户端只到 nginx，用不到这条路径。
> 代价是这类连接的 `http.Request.TLS` 为 nil，仓里没有任何地方依赖它（协议判断一律走
> `X-Forwarded-Proto`），所以无实际影响。

几个细节：

- **没有证书时开关打不开**：接口会返回 400 提示先上传证书，不会出现
  "显示已开启但握手全失败"；
- **移除证书会一并关掉开关**：否则后端会显示"已开启"但所有握手都失败。

### 客户端怎么连

| 客户端 | 地址 |
|---|---|
| 管理后台（浏览器） | `https://域名:9200`（`http://域名:9200` 同样可用） |
| 小程序 / 桌面端 | `https://域名:9200/api/v1` |
| 对象存储图片 | `https://域名:9200/files/...`（nginx 反代，与证书无关） |

> 小程序要求合法域名 + HTTPS 证书，域名必须与证书 SAN 对得上。
> **注意证书签发方**：Cloudflare Origin CA 的证书只有 Cloudflare 边缘节点信任，
> 浏览器/`curl` 直连会报 `SEC_E_UNTRUSTED_ROOT`（证书不受信任）。
> 用后台的「Let's Encrypt 自动证书」签一张公共 CA 的证书即可解决（走 DNS-01，不占端口）；
> 只有确认走 Cloudflare 代理时，Origin CA 证书才有意义（且 9200 不在 CF 免费版代理端口
> 列表里，需要改用 8443）。

### 限制

- **手工上传的证书不自动续期**：到期前 15 天后台会提示，届时重新上传即可。
  想让续期自动化，改用「Let's Encrypt 自动证书」——它由后端 worker 负责续期。
- **自动续期依赖 Cloudflare 托管 DNS**：DNS-01 要求域名的 NS 在 Cloudflare 上；
  如果换成别的 DNS 服务商，需要同步替换 `vendor/dnsapi/` 里的插件与凭证环境变量
  （acme.sh 的插件名如 `dns_dp` / `dns_ali`，对应凭证变量各不相同）。
- **未启用 HTTP/2**：`stream` 分流后的 TLS server 没开 h2，连接按 HTTP/1.1 协商。
- 私钥以明文存放于宿主机磁盘（Docker 部署为 `deploy/docker/nginx/tls/`，
  systemd 部署为 `TLS_DIR`，默认 `/var/lib/patnest/tls`），已在 `.gitignore`
  中排除。宿主机权限收紧即可，不要提交到仓库。

## 四、验证清单

```bash
# 1. 图片反代是否生效（应返回 200 和 image/*）
curl -I https://<你的域名>/files/uploads/2026/11/xxx.jpg

# 2. 后端返回的新链接是否已是本站域名
#    上传一张图，看接口返回的 url 字段

# 3. 数据库里存的应当是相对路径（不应该再出现 http://）
#    期望 0
psql "$DSN" -c "SELECT count(*) FROM sitters WHERE cover_photo_url ~ '^https?://';"

# 4. HTTPS 是否生效（后端 9200 同时收 http 与 https）
curl -I http://<你的域名>:9200/health
curl -I https://<你的域名>:9200/health
openssl s_client -connect <你的域名>:9200 -servername <你的域名> </dev/null 2>/dev/null | openssl x509 -noout -subject -dates

# 5. nginx 配置语法（容器内）
docker exec patnest-nginx nginx -t
```
