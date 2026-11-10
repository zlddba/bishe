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

### 后台操作

「系统更新」页底部 → **HTTPS 证书**：

1. 「证书链」粘贴 `fullchain.pem` 全文（站点证书 + 中间证书，按顺序拼接）；
2. 「私钥」粘贴 `privkey.pem` 全文；
3. 域名可留空（自动取证书里的第一个域名）；
4. 点「保存并生效」，约 5 秒后看「上次生效」是否显示成功。

保存时会校验：

- PEM 能否解析、**私钥与证书是否配对**（不匹配直接拒绝）；
- 证书是否已过期；
- 私钥会以 `0600` 落盘。

### 生效机制

后端**不直接操作 nginx**，只做三件事：写证书、生成 `https.conf.new`、写一个
`reload.request` 信号。真正的 `nginx -t` + `nginx -s reload` 由 nginx 容器里的
守护脚本（`deploy/docker/nginx/tls-watch.sh`）每 3 秒检查一次并执行。

这样设计的原因：让 API 直接 reload 另一个容器，需要挂 `docker.sock`，
等于把宿主机 root 权限交给一个 Web 进程，为一张证书不值得。

**失败会回滚**：新配置先写成 `.new`，守护脚本校验通过才替换；`nginx -t` 失败时
自动恢复上一版并 reload，站点保持原样，错误信息写进 `reload.status`，
后台「上次生效」会显示失败原因。所以后台填错一行配置不会把线上搞挂。

### 443 端口

`compose.yaml` 已映射 `${HTTPS_PORT:-443}:443`。未配置证书时
`deploy/docker/nginx/tls/https.conf` 只有注释，443 不会监听。

### 限制

- **不自动续期**。Let's Encrypt 需要 ACME 客户端 + HTTP-01 验证 + 定时任务，
  当前实现只支持手动上传；到期前 15 天后台会提示，届时重新上传即可。
- **systemd 部署（非容器）暂不支持自动生效**：API 需要 sudo 权限 reload nginx。
  该场景下把证书放到 `/etc/nginx/patnest/tls/`，手工 `nginx -s reload`。
- 私钥以明文存放于宿主机磁盘（`deploy/docker/nginx/tls/`），已在 `.gitignore`
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

# 4. HTTPS 是否生效
curl -I https://<你的域名>/
openssl s_client -connect <你的域名>:443 -servername <你的域名> </dev/null 2>/dev/null | openssl x509 -noout -subject -dates

# 5. nginx 配置语法（容器内）
docker exec patnest-nginx nginx -t
```
