# PatNest 毕业设计

宠物寄养服务预约系统，项目代号 **PatNest**（产品名"宠栖"）。一个仓库、按目录分模块，三条角色线：主人（下单预约寄养）、机构（开店、排期、定价、接单）、管理员（后台审核与运营）。

## 目录结构

```
biyezuopin/
├─ server/patnestServer/                Go 后端：Gin + PostgreSQL + Redis + MinIO
├─ webadmin/patnestServerAdminWeb/      管理后台：Vue 3 + Vite + Element Plus
├─ app/patnestApp/                      客户端：Kotlin Multiplatform + Compose（Android / Desktop）
├─ uniapp/patnestUniapp/                uni-app 微信小程序端（HBuilderX 工程，业务尚未接入）
├─ deploy/                              Docker / nginx / systemd / 发布与回滚脚本
├─ release/                             一键构建产出的容器包
├─ docs/                                需求、数据库、接口、部署、合规文档
├─ build.ps1 / build.sh                 一键构建（前端 dist + Go 二进制 + 容器包 zip）
├─ .gitignore                           全仓库公共忽略规则
└─ .gitattributes                       换行与二进制文件规则
```

采用**单仓库分目录**，没有使用 git submodule：各模块共享一份提交历史，跨模块改动（比如接口字段调整）可以一次提交完成，拷贝和提交作业时也不会出现子模块拉不下来的问题。模块之间目录和依赖仍然独立，后续确实需要拆库时按目录迁移即可。

## 各端启动方式

### 后端 server

```powershell
cd server/patnestServer
copy .env.example .env      # 填数据库、MinIO、JWT_SECRET、高德 Key
psql -U postgres -d patnest_db -f serverdb/init.sql   # 全新库：一次建好全部结构 + 种子数据
go run ./cmd/migrate -dir serverdb/migrations           # 幂等确认（init.sql 已预写 0001 记录）
go run ./cmd/migrate -dir serverdb/migrations -status   # 查看已应用版本
go run ./cmd/api                                        # 默认监听 APP_PORT=9200
```

### 接口文档（Swagger UI）

后端启动后访问 `http://localhost:9200/swagger/index.html`，文档 JSON 在 `/swagger.json`。UI 里点右上角 Authorize 填 `Bearer <access_token>` 即可调试需登录的接口。设 `SWAGGER_ENABLED=false` 可关闭。

服务器/容器部署时访问 `http://<host>:9200/swagger/index.html`：nginx 已放行 `/swagger/` 与 `/swagger.json` 到后端。注意 `docker/nginx/locations.conf` 是**打进 nginx 镜像**的（不是挂载），更新后要重建 web 镜像才会生效：`docker compose --profile app up -d --build`。接口清单是公开的，生产环境可用 `SWAGGER_ENABLED=false` 关闭。

文档由 `internal/router/router.go` 自动生成（不是手写注解）：改动路由后执行 `python internal/swagger/gen_swagger.py` 重新生成，`go test ./internal/swagger` 会检查文档是否漏掉新路由。生成的 JSON 通过 `go:embed` 打进二进制，容器部署无需额外文件。

直接依赖只有 Gin、pgx、jwt/v5、minio-go、godotenv、x/crypto、gin-swagger 等几项。Redis 客户端（`internal/cache`）是手写的精简 RESP 实现，只覆盖本项目用到的命令，没有为它引入第三方库。

入口分三个：`cmd/api` 常驻服务、`cmd/migrate` 在线迁移、`cmd/dbprobe` 连通性排查。
`serverdb/init.sql` 是唯一全量基线（已合并原 `0001`~`0021` 的净效果），新库执行它即可；
`serverdb/migrations/` 主目录只留 `0001_baseline.sql` 占位，历史脚本在 `migrations/archive/`。
后续结构变更从 `0022` 起按 `00NN_名字.sql` 往后追加，并同步更新 `init.sql`；
**已归档脚本和 `0001` 的内容一律不再改动**（后者校验和被老库依赖）。

### 管理后台 webadmin

```powershell
cd webadmin/patnestServerAdminWeb
npm install
npm run dev
```

要求 Node `^22.18.0 || >=24.12.0`。本地开发靠 `.env.local` 里的 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:9200`）代理到后端，不用为此改服务端 CORS。后台的机构地图选点依赖 `VITE_AMAP_JS_KEY` / `VITE_AMAP_SECURITY_CODE`，不配也能跑，只是地图区域会降级成地址解析。

### 客户端 app

```powershell
cd app/patnestApp
./gradlew :desktopApp:run             # 桌面端
./gradlew :androidApp:assembleDebug   # Android
./gradlew :shared:jvmTest             # 共享层测试
```

`shared` 放网络层、数据模型和业务状态，Android 与 Desktop 各自只负责 UI；桌面端服务地址读 `.env`，Android 端在登录页填写并存 `SharedPreferences`（真机联调时把地址改成局域网内后端 IP:9200）。工程目标 JVM 21，本机 JDK 不一致时装一个 JDK 21 或在 Gradle 工具链里指过去。桌面端已接登录、下单、机构经营、地图选点等主链路；Android 端主人侧与机构侧两套工作台已完成，iOS target 已移除。

### 微信小程序 uniapp

用 HBuilderX 导入 `uniapp/patnestUniapp`，只发行微信小程序，不出 H5 / App；该工程没有 `package.json`，不通过 npm 启动。当前它还是 HBuilderX 默认模板，接口层尚未接入，主人端需要的接口契约整理在 `docs/09-主人端缺失接口清单.md`。

## 一键构建与发布

```powershell
# Windows
./build.ps1                        # 版本号默认取 webadmin/package.json
./build.ps1 -Version 1.0.39 -Arch amd64
./build.ps1 -SkipWeb               # 只重打后端

# Linux / WSL
bash build.sh --version 1.0.39 --arch amd64
```

产物是 `release/patnest-docker-<version>` 目录和同名 zip，内含前后端镜像、compose 配置与校验和。服务器侧的蓝绿双 slot 发布、客户端热更新、回滚流程见 `deploy/README.md` 与 `docs/04-部署与热更新.md`。

## Git 约定

- 分支：`master`（毕设阶段单人开发，够用）
- 提交信息加模块前缀，便于回溯：
  - `server: 初始化 Gin 路由与数据库连接`
  - `web: 登录页对接 /api/login`
  - `app: 首页商品列表`
  - `uni: 购物车页面`
  - `docs: 补充数据库设计`
- 构建产物、IDE 配置、本地路径（`local.properties`）、运行期数据（`uploads/`、日志、`.env`）一律不入库，规则见各目录 `.gitignore`。

## 当前进度

| 模块 | 状态 |
|---|---|
| server | 主链路完成：账号、机构资质、商品、订单、优惠券、支付流水、评价、后台管理，含鉴权分层、限流、审计、地理编码、邀请码闭环 |
| webadmin | 可用：登录、机构与用户管理、订单、内容、优惠券、地图选点、数据看板 |
| app | Desktop 端持续迭代；Android 端主人 + 机构两套工作台界面与交互完成（未在本机跑过 Gradle 构建）；`shared` 层有测试覆盖；Android / Desktop 注册页与邀请页已接入邀请码闭环 |
| uniapp | 登录注册、机构浏览、下单、订单、钱包等页面已接后端；注册页与「我的 → 邀请有礼」已接入邀请码；其余按 `docs/09` 清单补齐 |
| docs | 见 `docs/README.md` 索引 |

## 文档索引

过程文档统一在 `docs/`，编号有历史断号（合并删过几篇），完整清单见 [docs/README.md](docs/README.md)。三份最先该看：`02-数据库设计.md`、`03-接口文档.md`、`05-编译打包发布运行.md`。

接口文档与后端源码为准：路由、字段、错误码有冲突时先改文档，再到 `docs/12-后端权限与限流收紧说明.md` 的遗留表里记一笔。
