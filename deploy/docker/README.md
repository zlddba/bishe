# PatNest 后端容器包（Nginx + Redis + API + 管理页面）

本包自带**已构建好的**后端二进制与管理页面静态文件，服务器上只打包镜像，不编译代码。

## 目录

```
patnest-docker/
├── compose.yaml            # 唯一编排模板（app / tools 两组 profile）
├── .env.example            # 复制成 .env 后填写
├── docker/
│   ├── Dockerfile.api      # 直接复制 artifacts 里的二进制
│   ├── Dockerfile.web      # 直接复制 web/dist 静态文件
│   └── nginx/default.conf  # 9200 端口：管理页面 + /api 反代
├── artifacts/              # 已编译的 Linux amd64 二进制
├── web/dist/               # 已构建的管理页面（含 version.json）
├── nginx/upstream.conf     # 蓝绿 active 槽位
├── db/                     # 建库脚本与增量迁移
├── data/updates/           # 运行时生成：tmp / manual / queue / status / logs
└── scripts/                # 安装 / 发布 / 迁移（.sh 与 .ps1 两套等价脚本）
```

## 首次部署（Linux / macOS）

```bash
cp .env.example .env     # 填 DB_PASSWORD、MinIO 密钥、JWT_SECRET、REDIS_PASSWORD
bash scripts/install.sh
```

## 首次部署（Windows PowerShell）

需要 Docker Desktop（Linux 容器模式）或 Podman Desktop，并确认 `docker compose version`
或 `podman compose version` 至少一个可用。

```powershell
cd D:\path\to\patnest-docker-1.0.0
Copy-Item .env.example .env
notepad .env

Set-ExecutionPolicy -Scope Process Bypass
.\scripts\install.ps1
```

如果不想改执行策略，也可以单次绕过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1
```

Windows 脚本与 Bash 脚本一一对应，命令参数也一致。安装脚本会在启动后轮询
`/health`，并打印数据库、MinIO、Redis 的连接结果。

访问 `http://<服务器IP>:9200/` 是管理页面，`http://<服务器IP>:9200/api/v1/...` 是接口。

也可以直接手写 compose 命令：

```bash
podman-compose --profile app up -d --build
podman-compose --profile tools run --rm migrate
```

## 更新

Linux / macOS：

```bash
# 1. 用本机新编译的 artifacts/patnest-api 覆盖包内文件
# 2. 蓝绿发布（接口不中断）
bash scripts/release-api.sh

# 1. 用本机新构建的 web/dist 覆盖包内目录
# 2. 重建镜像并重启 nginx（约 1 秒）
bash scripts/release-web.sh

# 只跑迁移
bash scripts/migrate.sh -status
bash scripts/migrate.sh

# 回滚到本机已有镜像（旧镜像不要急着 prune）
bash scripts/rollback.sh 1.0.0
```

Windows PowerShell：

```powershell
# 后端蓝绿发布
.\scripts\release-api.ps1 -Version 1.1.0

# 管理页面更新
.\scripts\release-web.ps1 -Version 1.1.0

# 只跑迁移 / 查看状态
.\scripts\migrate.ps1 -status
.\scripts\migrate.ps1

# 回滚到本机已有镜像
.\scripts\rollback.ps1 -Version 1.0.0
```

版本号省略时，`release-api.ps1` 使用当前时间生成标签，`release-web.ps1`
使用 `.env` 里的 `APP_VERSION`。发布脚本会自动识别 `podman-compose`、
`podman compose` 或 `docker compose`，不需要手工改脚本。

## 管理端一键更新

管理后台新增“系统更新”页面，支持两种方式：

1. **页面上传**：选择构建脚本生成的 `patnest-docker-<版本>.zip`，文件进入
   `data/updates/tmp`，审核版本和内容后点击“应用更新”。
2. **服务器目录**：把同一个 zip 放到
   `<部署目录>/data/updates/manual`，在页面上点击“检查更新”，识别后点击“应用”。

真正执行更新的是宿主机上的更新代理，不是 API 容器：

```powershell
# Windows，安装脚本会自动启动；手动启动：
.\scripts\update-agent.ps1
```

```bash
# Linux
nohup bash scripts/update-agent.sh >> data/updates/agent.log 2>&1 &
```

代理只消费 `data/updates/queue/*.job`，校验版本、暂存目录和目标文件后，
备份当前文件，再调用 `release-api.ps1` / `release-web.ps1`。API 容器只负责
上传、扫描和排队，不需要挂载 Docker socket。

页面顶部的“更新代理”状态来自 `data/updates/agent.heartbeat`；如果显示离线，
先启动代理。Windows 安装脚本会创建一个当前用户的启动快捷方式，开机登录后自动运行。

## 说明

- 对外只暴露 `PUBLIC_PORT`（默认 9200），API 的 9201 只在容器网络内。
- Redis 用于登录失败限流（同 IP + 用户名 10 分钟内 5 次），AOF 持久化。
- 外部镜像统一使用最新标签：`alpine:latest`、`nginx:latest`、`redis:latest`；构建带 `--pull`，Redis 每次启动按 `pull_policy: always` 检查更新。
- `api-blue` / `api-green` 使用同一个镜像，发布时只替换其中一个，健康检查通过后切 Nginx upstream。
- MySQL/PostgreSQL 与 MinIO 不在本 compose 内，由存储服务器单独提供。
- `data/updates` 由 API 容器和宿主机代理共享，安装脚本会放开该目录的读写权限；不要把该目录暴露到公网。

## Windows 常见问题

| 现象 | 处理 |
| --- | --- |
| `无法加载文件 ... install.ps1，因为在此系统上禁止运行脚本` | 先执行 `Set-ExecutionPolicy -Scope Process Bypass`，或用上面的 `powershell -ExecutionPolicy Bypass -File` |
| `No compose command found` | 启动 Docker Desktop，或在 Podman Desktop 中启用 compose；用 `docker compose version` 自检 |
| `docker: command not found` | Docker Desktop 未启动或未加入 PATH，重启终端后再试 |
| 构建时报 `COPY web/dist ... not found` | 解压不完整或 `.dockerignore` 误排除了 `dist`，重新解压完整发布包 |
| `api-blue health check failed` | 看 `docker logs patnest-api-blue`，重点检查 `.env` 的 `DB_HOST/DB_PASSWORD` 与存储服务器白名单 |
| MinIO 警告 | `/health` 里 `minio=false`，检查 `MINIO_ENDPOINT`、密钥和 9000 端口连通性 |
| 端口 9200 被占用 | 改 `.env` 的 `PUBLIC_PORT`，重新执行安装脚本 |
