# 热更新部署脚本

适用于两台服务器：存储机（PostgreSQL 18 + MinIO）与后端机（Nginx + PatNest API）。

## 目录约定（后端机）

```
/opt/patnest/api/
├── shared/.env          # 后端配置（DB / MinIO / JWT）
├── shared/slot.blue     # APP_SLOT=blue  APP_PORT=9200
├── shared/slot.green    # APP_SLOT=green APP_PORT=9201
├── releases/<version>/  # 每次发布的二进制
└── current -> releases/<version>

/opt/patnest/web/
├── releases/<build_id>/ # 每次发布的前端静态文件
└── current -> releases/<build_id>

/etc/nginx/patnest/upstream.conf   # 当前活动槽位，脚本自动改写
```

## 首次准备

1. 安装 Nginx、Go 1.25+、psql 客户端，创建 `patnest` 用户与上述目录。
2. 拷贝 `systemd/patnest-api@.service` 到 `/etc/systemd/system/`，
   `systemctl daemon-reload`。
3. 拷贝 `nginx/patnest.conf` 到 `/etc/nginx/conf.d/`，
   `nginx/upstream.conf.example` 到 `/etc/nginx/patnest/upstream.conf`，
   `nginx/api_proxy.conf.example` 到 `/etc/nginx/patnest/api_proxy.conf`
   （反代协议文件，缺失时 nginx 起不来）。
4. 把 `systemd/slot.blue.example`、`slot.green.example` 去掉 `.example` 拷到
   `/opt/patnest/api/shared/`。
5. 复制 `env.example` 为 `deploy/.env` 并填写，脚本执行前 `source` 它。

## 后端协议（http / https）

证书与开关都在后台管理页面里管（「系统更新 → HTTPS 证书」）：

- 上传证书链与私钥 → 落盘到 `TLS_DIR`（默认 `/var/lib/patnest/tls`）；
- 打开「后端同时接受 HTTPS」→ 后端 9200 端口同时收 http 与 https，
  客户端可直连 `https://域名:9200`，**切换即时生效，不用重启**。

`deploy/.env` 的 `API_SCHEME` 只在你想让 nginx 用 https 连上游时才需要（默认 http）。
因为后端两种协议都收，这个值配错也不会连不上。

## 日常发布

```bash
source deploy/.env

# 后端（自动迁移 + 蓝绿切换 + 健康门禁）
bash deploy/scripts/deploy-backend.sh

# 管理面板（原子切换 + Nginx reload）
bash deploy/scripts/deploy-web.sh

# 查看迁移状态
bash deploy/scripts/migrate.sh -status

# 回滚到上一版本
bash deploy/scripts/rollback.sh
```

详细原理、数据库变更规范和验收步骤见 `docs/04-热更新部署方案.md`。
