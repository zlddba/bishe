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
   `nginx/upstream.conf.example` 到 `/etc/nginx/patnest/upstream.conf`。
4. 把 `systemd/slot.blue.example`、`slot.green.example` 去掉 `.example` 拷到
   `/opt/patnest/api/shared/`。
5. 复制 `env.example` 为 `deploy/.env` 并填写，脚本执行前 `source` 它。

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
