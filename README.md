# PatNest 宠栖

宠物寄养服务预约系统，毕业设计项目。一个仓库按目录分模块，三条角色线：**主人**（找机构、下单寄养、支付、评价）、**机构**（开店、排期与容量、定价、接单、发动态）、**管理员**（审核、订单与资金、内容、系统运维）。

| 项 | 当前值 |
| --- | --- |
| 最新版本 | **v2.0.4**（2026-11-14 发布） |
| 首个正式版 | v2.0.0 |
| 发布说明 | [docs/release/v2.0.4.md](docs/release/v2.0.4.md) |
| 安装包 / 部署包 | [GitHub Releases](https://github.com/zlddba/bishe/releases) |
| 线上环境 | `https://www.linkzdsada.dpdns.org:9200`（API 与管理后台同域，`/swagger/index.html` 为接口文档） |

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Go 1.25 + Gin + pgx + PostgreSQL + Redis + MinIO，JWT 鉴权，手写 RESP 客户端；170 条路由 / 212 个接口操作 |
| 管理后台 | Vue 3 + Vite + Element Plus（Node `^22.18.0 \|\| >=24.12.0`） |
| 客户端 | Kotlin Multiplatform + Compose（Gradle 9.8.2 / JVM 21）：Android 与 Desktop 共用网络层、模型与状态，各自实现 UI |
| 小程序 | uni-app（Vue 3）+ 微信小程序，HBuilderX 工程，28 个页面 |
| 部署 | Podman / Docker Compose，nginx 单端口 9200 做 http/https 分流，API 蓝绿双槽位 |
| 发布 | GitHub Actions：release 分支 + tag + 发布文档三项校验 → 多架构并行构建 → 自动创建 Release |

## 目录结构

```
biyezuopin/
├─ server/patnestServer/                Go 后端：Gin + PostgreSQL + Redis + MinIO
│  ├─ cmd/{api,migrate,dbprobe}/        常驻服务、在线迁移、连通性排查
│  ├─ internal/                         路由、处理器、模型、中间件、ACME、TOTP、存储…
│  └─ serverdb/                         init.sql 全量基线 + migrations 增量脚本（当前到 0023）
├─ webadmin/patnestServerAdminWeb/      管理后台：Vue 3 + Vite + Element Plus
├─ app/patnestApp/                      客户端：shared / androidApp / desktopApp
├─ uniapp/patnestUniapp/                微信小程序（HBuilderX 工程，无 package.json）
├─ deploy/                              Dockerfile、compose、nginx、发布与回滚脚本
├─ docs/                                需求、数据库、接口、部署、测试、故障修复文档
│  └─ release/                          TEMPLATE.md + 每个 tag 一份发布说明（正文即 Release 内容）
├─ .github/workflows/build.yml          发布流水线：校验 → 构建 → 发布 Release
├─ build.ps1 / build.sh                 一键构建（管理后台 dist + Go 二进制 + 部署包 zip）
├─ release/                             本地构建产物（已忽略，不入库）
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
- `serverdb/init.sql` 是唯一全量基线；新结构变更从 `0022` 起追加脚本并同步进 `init.sql`，当前最新为 `0023_admin_totp.sql`；`archive/` 与 `0001` 不再改动（老库校验和依赖它）。
- 证书与两步验证：后台「系统更新」页可申请/续期 Let's Encrypt 证书（Cloudflare DNS-01，不占 80 端口），「账号安全」页绑定 TOTP。

### 管理后台

```bash
cd webadmin/patnestServerAdminWeb
npm install
npm run dev
```

本地开发靠 `.env.local` 的 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:9200`）代理到后端。地图选点依赖 `VITE_AMAP_JS_KEY` / `VITE_AMAP_SECURITY_CODE`，不配只是降级为地址解析。

### 客户端

```bash
cd app/patnestApp
./gradlew :desktopApp:run             # 桌面端
./gradlew :androidApp:assembleDebug   # Android
./gradlew :shared:jvmTest             # 共享层测试
```

桌面端接口地址在**构建期**由 `PATNEST_API_BASE` 固化进启动器（`-Dpatnest.api.base=...`），运行时按「环境变量 → `.env` → 构建期固化值 → 内置默认地址」取值，因此**不需要 `.env` 就能直接运行**；想临时指向别的后端可用环境变量覆盖。Android 端在登录页填写服务地址并存 `SharedPreferences`（真机联调改成局域网后端 IP:9200）。工程目标 JVM 21，本机 JDK 不一致时装 JDK 21 或在 Gradle 工具链里指过去。iOS target 已移除。

**所有桌面产物通过 `jlink` 自带裁剪后的 JVM 运行时，用户机器不需要安装 Java/JDK**，只需常规原生库（glibc、X11/GTK、字体）；AppImage 在少数发行版还需 `libfuse2`。

| 平台 | 格式 | 架构 | 使用方式 |
| --- | --- | --- | --- |
| Linux | `.deb` / `.rpm` | amd64 + arm64 | 包管理器安装 |
| Linux | `.AppImage` | amd64 + arm64 | `chmod +x` 后直接运行，免安装 |
| Linux | `.tar.gz` | amd64 + arm64 | 解压后运行 `bin/` 下的启动器，免安装 |
| Windows | `.msi` | x64 + arm64 | 安装向导 |
| macOS | `.dmg` | 仅 arm64 | Apple Silicon；按约定不出 Intel 版 |
| Android | `.apk` | 仅 arm64-v8a | 直接安装；32 位 ARM / x86 设备不支持 |

### 小程序

HBuilderX 导入 `uniapp/patnestUniapp`，只发行微信小程序（AppID `wx1366ab99a6b27171`），不出 H5 / App，也不用 npm 启动。宿主域名、`request` 合法域名与图片域名都要在微信公众平台配置，接口地址在 `uniapp/patnestUniapp/config.js`。

## 构建与发布

### 本地打包（服务端部署包）

```powershell
./build.ps1                        # Windows，版本号默认取 webadmin/package.json
./build.ps1 -Version 2.0.2 -Arch amd64
./build.ps1 -Version 2.0.2 -Arch arm64
./build.ps1 -SkipWeb               # 只重打后端
```

```bash
bash build.sh --version 2.0.2 --arch amd64     # Linux / WSL
bash build.sh --version 2.0.2 --arch arm64
```

产物是 `release/patnest-server-<version>-linux-<arch>/` 与同名 zip，内含对应架构的 Go 二进制、Dockerfile / compose 配置与 `update-manifest.json`。**容器镜像不在构建机生成**：`alpine` / `nginx` 基础镜像都是多架构的，服务器按自身架构本地 build 即可。服务器侧的蓝绿发布、热更新、回滚见 `deploy/README.md` 与 `docs/04-部署与热更新.md`。

### 发布流程（GitHub Actions）

**发布前提：发布分支、同名 tag、发布文档，三者缺一不可**，`verify` job 会逐项校验，缺哪个直接在日志里点名。

1. 从 `master` 切出发布分支：`git switch -c release/vX.Y.Z`
2. 写发布说明 `docs/release/vX.Y.Z.md`（复制 `docs/release/TEMPLATE.md`）。**这份文件的正文就是 Release 页的发布说明**，改文档即改 Release 内容。
3. 推送分支：`git push origin release/vX.Y.Z`
4. 在该提交上打 tag 并推送：`git tag vX.Y.Z && git push origin vX.Y.Z`

tag 推送后流水线依次执行：

| 阶段 | 内容 |
| --- | --- |
| `verify` | 校验 tag 在远端存在、`release/vX.Y.Z` 分支存在、tag 提交在该分支上、`docs/release/vX.Y.Z.md` 存在且非空 |
| 构建（并行，9 个 job 实例） | 2 个服务端部署包、1 个小程序包、1 个 arm64 APK、2 组 Linux 四格式、3 个 Windows/macOS 安装包 |
| `release` | 用发布文档作正文创建 Release 并附上全部产物；已存在同名 Release 时覆盖正文与附件 |

手动重跑：Actions → **Release** → Run workflow，填 tag（如 `v2.0.1`），走同一套校验与发布逻辑。

### 发布产物与命名规范

统一格式 `patnest-<版本>-<平台>-<架构>.<扩展名>`，**架构标识必写**（源码包除外）。这是 v2.0.2 起启用的规范，一次发布共 15 个附件（v2.0.0 / v2.0.1 的历史附件名保持不变）：

| Artifact | 内容 |
| --- | --- |
| `patnest-server-linux-amd64` / `-arm64` | `patnest-server-<version>-linux-<arch>.zip`，服务端部署包 |
| `patnest-android-apk` | `patnest-<version>-android-arm64.apk` |
| `patnest-desktop-linux-amd64` / `-arm64` | `patnest-<version>-linux-<arch>.deb` / `.rpm` / `.AppImage` / `.tar.gz` |
| `patnest-desktop-windows-x64` / `-arm64` | `patnest-<version>-windows-<arch>.msi` |
| `patnest-desktop-macos-arm64` | `patnest-<version>-macos-arm64.dmg` |
| `patnest-uniapp-project` | `patnest-uniapp-<version>.zip`，小程序工程源码包（无架构概念） |

| 端 | 支持架构 | 构建 runner |
| --- | --- | --- |
| 服务端 / 容器 | amd64 + arm64 | `ubuntu-latest` 交叉编译 Go 二进制，镜像在目标架构宿主机本地构建 |
| Linux 桌面 | amd64 + arm64 | `ubuntu-latest` / `ubuntu-24.04-arm`（jpackage 不能交叉打包） |
| Windows 桌面 | x64 + arm64 | `windows-latest` / `windows-11-arm` |
| macOS 桌面 | 仅 arm64 | `macos-latest`（Apple Silicon） |
| Android | 仅 arm64-v8a | `ubuntu-latest` + `ndk.abiFilters` |

Artifact 保留 14 天；正式分发走对应 tag 的 Release 页。APK 上传前会校验包内不含 `lib/armeabi-v7a/`。

### 仓库配置

**Settings → Secrets and variables → Actions**，全部填在 **Repository 级**（不是 Environment 级，工作流没有声明 `environment:`，填 Environment 会读不到）。

| 名称 | 填到 | 必填 | 用途 |
| --- | --- | --- | --- |
| `PATNEST_KEYSTORE_BASE64` | Secrets（Repository secret） | 是 | release keystore 的 base64 |
| `PATNEST_STORE_PASSWORD` / `PATNEST_KEY_PASSWORD` | Secrets（Repository secret） | 是 | keystore 与 key 口令 |
| `PATNEST_KEY_ALIAS` | Secrets（Repository secret） | 否 | 默认 `key0` |
| `PATNEST_API_BASE` | Variables（Repository variable） | 是 | Android 与 Desktop 在构建期共用，例：`https://www.linkzdsada.dpdns.org:9200/api/v1` |
| `PATNEST_AMAP_ANDROID_KEY` | Secrets（Repository secret） | 否 | 留空时 App 退回瓦片底图 |

`GITHUB_TOKEN` 不用建，由 Actions 内置并在 `release` job 通过 `permissions: contents: write` 授权。

### 已发布版本

| 版本 | 日期 | 重点 | 发布说明 |
| --- | --- | --- | --- |
| v2.0.0 | 2026-11-14 | 首个正式版：三端全链路、TOTP、Let's Encrypt 自动证书、CI 发布流水线 | [v2.0.0.md](docs/release/v2.0.0.md) |
| v2.0.1 | 2026-11-14 | 桌面端开箱即用（无需 `.env` / 无需安装 Java）、Linux 新增 rpm / AppImage / tar.gz | [v2.0.1.md](docs/release/v2.0.1.md) |
| v2.0.2 | 2026-11-14 | 双架构矩阵：服务端 / Linux / Windows 补 arm64，Android 收窄为仅 arm64-v8a，产物命名统一 | [v2.0.2.md](docs/release/v2.0.2.md) |
| v2.0.3 | 2026-11-14 | KMP 全列表页下拉刷新 + 页面 / 逻辑修复（后端代录评价状态同步） | [v2.0.3.md](docs/release/v2.0.3.md) |
| v2.0.4 | 2026-11-14 | 平板下拉刷新对齐修复、容量日历宽屏适配 | [v2.0.4.md](docs/release/v2.0.4.md) |

私有仓库按分钟计费（Linux 1×、Windows 2×、macOS 10×）：双架构矩阵让 job 数从 8 增至 11，一次完整发布墙钟约 8~12 分钟，累计计费约为原来的 1.5~2 倍，所以只在打 tag 时跑，不跟随每次 push。细节与历次排错记录见 `docs/23-GitHub-Actions打包.md`。

## Git 约定

- 常驻分支 `master`；发布时切 `release/vX.Y.Z`，与 tag `vX.Y.Z` 一一对应。
- 提交信息带模块前缀：`server:` / `web:` / `app:` / `uni:` / `docs:` / `ci:`。
- 不入库：根目录 `/release/`、`/dist/`、`/build/`、`app/patnestApp/out/`、桌面打包中间目录与 `*.AppImage`、IDE 配置、`local.properties`、`.env`、运行期数据（`uploads/`、日志）、签名文件、`.workbuddy/`，以及个人材料 `docs/bswd/`。

## 当前进度

| 模块 | 状态 |
| --- | --- |
| server | 主链路完成：账号与权限分层、机构资质、订单与资金、容量台账、优惠券/积分/邀请码、钱包、评价、动态、工单与售后、结算、审计与限流、地理编码、Swagger 自动生成、TOTP、Let's Encrypt 自动证书 |
| webadmin | 18 个业务页面：经营看板、机构/订单/宠物、内容与评价、优惠券、公告与推送、售后与结算、钱包、用户与权限、审计、系统更新、账号安全（TOTP） |
| app | Desktop 主人端 + 机构端完整；Android 主人/机构双工作台；金额精度、列表分页、加购与积分、接单入口已补齐；桌面端六种分发格式全部自动产出 |
| uniapp | 28 页已接后端：登录注册、首页与筛选、机构详情、下单（含优惠券/积分/加购）、订单全流程、宠物档案、钱包、动态、收藏、通知、工单、退款、设置 |
| 质量 | 接口联通测试 253 个调用点零遗漏、线上 160 项检查、18 步业务闭环，见 `docs/21-系统测试报告.md` |
| CI | tag 驱动发布：三项校验 → 11 个 job 实例 → 15 个多架构 Release 附件 → 正文取自发布文档；v2.0.0 / v2.0.1 已发布 |

## 文档索引

过程文档在 `docs/`，编号有历史断号（合并删过几篇），完整清单见 [docs/README.md](docs/README.md)。

- 入门三份：`02-数据库设计.md`、`03-接口文档.md`、`05-编译打包发布运行.md`
- 排查问题：`21-系统测试报告.md`、`22-客户端逻辑与UI修复-20261114.md`
- 发布相关：`23-GitHub-Actions打包.md`、`docs/release/TEMPLATE.md` 与各版本发布说明

接口以源码为准：路由、字段、错误码有冲突时先改文档，再到 `docs/12-后端权限与限流收紧说明.md` 的遗留表里记一笔。
