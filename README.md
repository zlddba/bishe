# PatNest 宠栖

宠物寄养服务预约系统，毕业设计项目。一个仓库按目录分模块，三条角色线：**主人**（找机构、下单寄养、支付、评价）、**机构**（开店、排期与容量、定价、接单、发动态）、**管理员**（审核、订单与资金、内容、系统运维）。

线上环境：`https://www.linkzdsada.dpdns.org:9200`（API 与管理后台同域，`/swagger/index.html` 为接口文档）。

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Go 1.25 + Gin + pgx + PostgreSQL + Redis + MinIO，JWT 鉴权，手写 RESP 客户端 |
| 管理后台 | Vue 3 + Vite + Element Plus |
| 客户端 | Kotlin Multiplatform + Compose：Android 与 Desktop 共用网络层/模型/状态，各自实现 UI（JVM 21） |
| 小程序 | uni-app（Vue 3）+ 微信小程序，HBuilderX 工程，28 个页面 |
| 部署 | Podman / Docker Compose，nginx 单端口 9200 做 http/https 分流，API 蓝绿双槽位 |

## 目录结构

```
biyezuopin/
├─ server/patnestServer/                Go 后端：Gin + PostgreSQL + Redis + MinIO
│  ├─ cmd/{api,migrate,dbprobe}/        常驻服务、在线迁移、连通性排查
│  ├─ internal/                         路由、处理器、模型、中间件、ACME、TOTP、存储…
│  └─ serverdb/                         init.sql 全量基线 + migrations 增量脚本
├─ webadmin/patnestServerAdminWeb/      管理后台：Vue 3 + Vite + Element Plus
├─ app/patnestApp/                      客户端：shared / androidApp / desktopApp
├─ uniapp/patnestUniapp/                微信小程序（HBuilderX 工程，无 package.json）
├─ deploy/                              Dockerfile、compose、nginx、发布与回滚脚本
├─ docs/                                需求、数据库、接口、部署、测试、发布说明
│  └─ release/                          每个 tag 一份发布说明，正文即 Release 页内容
├─ .github/workflows/build.yml          发布流水线：校验 → 构建 → 发布
├─ build.ps1 / build.sh                 一键构建（管理后台 dist + Go 二进制 + 部署包 zip）
└─ .gitignore / .gitattributes          公共忽略与换行规则
```

单仓库分目录，不用 submodule：跨模块改动（比如接口字段调整）能一次提交完成，拷贝作业时也不会出现子模块拉不下来的问题。

## 各端启动

### 后端

```bash
cd server/patnestServer
cp .env.example .env        # 填数据库、Redis、MinIO、JWT_SECRET、高德 Key
psql -U postgres -d patnest_db -f serverdb/init.sql   # 新库：一次建好结构与种子数据
go run ./cmd/migrate -dir serverdb/migrations          # 既有库：按序补增量
go run ./cmd/api                                       # 默认 9200
```

- 接口文档：`/swagger/index.html`，JSON 在 `/swagger.json`。文档由 `internal/router/router.go` 生成，改路由后执行 `python internal/swagger/gen_swagger.py`，`go test ./internal/swagger` 会检查漏掉的路由。`SWAGGER_ENABLED=false` 可关闭。
- `serverdb/init.sql` 是唯一全量基线，新结构变更从 `0022` 起追加脚本并同步进 `init.sql`；`archive/` 与 `0001` 不再改动（老库校验和依赖它）。
- 证书与两步验证：后台「系统更新」页可申请/续期 Let's Encrypt 证书（Cloudflare DNS-01，不占 80 端口），「账号安全」页绑定 TOTP。

### 管理后台

```bash
cd webadmin/patnestServerAdminWeb
npm install
npm run dev        # 需要 Node ^22.18.0 || >=24.12.0
```

本地开发靠 `.env.local` 的 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:9200`）代理到后端。地图选点依赖 `VITE_AMAP_JS_KEY` / `VITE_AMAP_SECURITY_CODE`，不配只是降级为地址解析。

### 客户端

```bash
cd app/patnestApp
./gradlew :desktopApp:run             # 桌面端
./gradlew :androidApp:assembleDebug   # Android
./gradlew :shared:jvmTest             # 共享层测试
```

桌面端服务地址读 `.env`；Android 端在登录页填写并存 `SharedPreferences`（真机联调改成局域网后端 IP:9200）。工程目标 JVM 21，本机 JDK 不一致时装 JDK 21 或在 Gradle 工具链里指过去。iOS target 已移除。

### 小程序

HBuilderX 导入 `uniapp/patnestUniapp`，只发行微信小程序（AppID `wx1366ab99a6b27171`），不出 H5 / App，也不用 npm 启动。宿主域名、`request` 合法域名与图片域名都要在微信公众平台配置，接口地址在 `uniapp/patnestUniapp/config.js`。

## 构建与发布

### 本地打包

```powershell
./build.ps1                        # Windows，版本号默认取 webadmin/package.json
./build.ps1 -Version 1.2.0 -Arch amd64
./build.ps1 -SkipWeb               # 只重打后端
```

```bash
bash build.sh --version 1.2.0 --arch amd64     # Linux / WSL
```

产物是 `release/patnest-docker-<version>/` 与同名 zip，内含前后端镜像、compose 配置与校验和。服务器侧的蓝绿发布、热更新、回滚见 `deploy/README.md` 与 `docs/04-部署与热更新.md`。

### 发布流程（GitHub Actions）

**发布前提：发布分支、同名 tag、发布文档，三者缺一不可**，`verify` job 会逐项校验，缺哪个直接在日志里点名。

1. 从 `master` 切出发布分支：`git switch -c release/v1.2.0`
2. 写发布说明 `docs/release/v1.2.0.md`（复制 `docs/release/TEMPLATE.md`）。**这份文件的正文就是 Release 页的发布说明**，改文档即改 Release 内容。
3. 推送分支：`git push origin release/v1.2.0`
4. 在该提交上打 tag 并推送：`git tag v1.2.0 && git push origin v1.2.0`

tag 推送后流水线依次执行：

| 阶段 | 内容 |
| --- | --- |
| `verify` | 校验 tag 存在于远端、`release/v1.2.0` 分支存在、tag 提交在该分支上、`docs/release/v1.2.0.md` 存在且非空 |
| 构建（并行） | 部署包、Android APK、桌面端 deb/msi/dmg、小程序工程 zip |
| `release` | 用发布文档作正文创建 Release，并附上全部产物；已存在同名 Release 时覆盖更新 |

手动重跑：Actions → Release → Run workflow，填 tag（如 `v1.2.0`），走同一套校验与发布逻辑。

四类产物：

| Artifact | 内容 |
| --- | --- |
| `patnest-deploy-bundle` | `patnest-docker-<version>.zip`，服务器部署包 |
| `patnest-android-apk` | `androidApp-release.apk`，已签名 |
| `patnest-desktop-deb / -msi / -dmg` | 对应系统的桌面安装包 |
| `patnest-uniapp-project` | `patnest-uniapp-<version>.zip`，小程序工程源码包（不含 `unpackage/`） |

需要的仓库配置（Settings → Secrets and variables → Actions）：

| 名称 | 类型 | 必填 | 用途 |
| --- | --- | --- | --- |
| `PATNEST_KEYSTORE_BASE64` | Secret | 是 | release keystore 的 base64 |
| `PATNEST_STORE_PASSWORD` / `PATNEST_KEY_PASSWORD` | Secret | 是 | keystore 与 key 口令 |
| `PATNEST_KEY_ALIAS` | Secret | 否 | 默认 `key0` |
| `PATNEST_API_BASE` | Variable | 是 | 例：`https://www.linkzdsada.dpdns.org:9200/api/v1` |
| `PATNEST_AMAP_ANDROID_KEY` | Secret | 否 | 留空时 App 退回瓦片底图 |

私有仓库按分钟计费（Linux 1×、Windows 2×、macOS 10×），一次完整发布约 30~55 分钟，所以只在打 tag 时跑，不跟随每次 push。细节见 `docs/23-GitHub-Actions打包.md`。

## Git 约定

- 常驻分支 `master`；发布时切 `release/vX.Y.Z`，与 tag `vX.Y.Z` 一一对应。
- 提交信息带模块前缀：`server:` / `web:` / `app:` / `uni:` / `docs:` / `ci:`。
- 不入库：构建产物（`release/`、`dist/`、`build/`）、IDE 配置、`local.properties`、`.env`、运行期数据（`uploads/`、日志）、签名文件与个人材料（`docs/bswd/`）。

## 当前进度

| 模块 | 状态 |
| --- | --- |
| server | 主链路完成：账号与权限分层、机构资质、订单与资金、容量台账、优惠券/积分/邀请码、钱包、评价、动态、工单与售后、结算、审计与限流、地理编码、Swagger 自动生成、TOTP、Let's Encrypt 自动证书 |
| webadmin | 19 个页面可用：经营看板、机构/订单/宠物、内容与评价、优惠券、公告与推送、售后与结算、钱包、用户与权限、审计、系统更新、账号安全（TOTP） |
| app | Desktop 主人端 + 机构端完整；Android 主人/机构双工作台；金额精度、列表分页、加购与积分、接单入口已补齐 |
| uniapp | 28 页已接后端：登录注册、首页与筛选、机构详情、下单（含优惠券/积分/加购）、订单全流程、宠物档案、钱包、动态、收藏、通知、工单、退款、设置 |
| 质量 | 接口联通测试 253 个调用点零遗漏、线上 160 项检查、18 步业务闭环，见 `docs/21-系统测试报告.md` |
| CI | tag 驱动发布：校验分支/tag/文档 → 四类产物 → 自动生成 Release |

## 文档索引

过程文档在 `docs/`，编号有历史断号（合并删过几篇），完整清单见 [docs/README.md](docs/README.md)。

先看这三份：`02-数据库设计.md`、`03-接口文档.md`、`05-编译打包发布运行.md`；排查问题看 `21-系统测试报告.md` 与 `22-客户端逻辑与UI修复-20261114.md`。

接口以源码为准：路由、字段、错误码有冲突时先改文档，再到 `docs/12-后端权限与限流收紧说明.md` 的遗留表里记一笔。
