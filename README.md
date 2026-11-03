# PatNest 毕业设计

一个仓库、按目录分模块的毕业设计工程。项目代号 **PatNest**，业务主题尚未最终确定（宠物 / 专利两种方向待确认）。

## 目录结构

```
biyezuopin/
├─ app/patnestApp/               Kotlin Multiplatform + Compose，Android / iOS / Desktop
├─ server/patnestServer/         Go 后端（规划：Gin + SQLite），目前只有 go.mod
├─ uniapp/patnestUniapp/         uni-app 用户端（只做微信小程序，不出 H5 / App）
├─ webadmin/patnestServerAdminWeb/  Vue 3 + Vite 管理后台
├─ docs/                         需求、数据库设计、接口文档、论文素材
├─ .gitignore                    全仓库公共忽略规则
└─ .gitattributes                换行与二进制文件规则
```

采用**单仓库分目录**，没有使用 git submodule：四个模块共享一份提交历史，跨模块改动（比如接口字段调整）可以一次提交完成，拷贝和提交作业时也不会出现子模块拉不下来的问题。模块之间仍保持目录和依赖独立，后续如果确实需要拆库，再按目录迁移即可。

## 各端启动方式

### 管理后台 webadmin

```powershell
cd webadmin/patnestServerAdminWeb
npm install
npm run dev
```

要求 Node ≥ 22.18（`package.json` 里写死的）。当前机器 PATH 里还没有 `node` / `npm`，需要先装 Node 或把已有安装加到 PATH。

### 后端 server

```powershell
cd server/patnestServer
go mod tidy
go run .
```

当前只有 `go.mod`（`module patnestServer`，Go 1.27），还没有 `main.go`，所以暂时跑不起来。

### 移动端 app

```powershell
cd app/patnestApp
./gradlew :desktopApp:run          # 桌面端
./gradlew :androidApp:assembleDebug # Android
```

iOS 用 Xcode 打开 `app/patnestApp/iosApp`。工程目标 JVM 21，本机是 JDK 25，若 Gradle 报工具链不匹配，需要装一个 JDK 21。

### 微信小程序 uniapp

用 HBuilderX 直接导入 `uniapp/patnestUniapp`，只发行微信小程序；该工程没有 `package.json`，不通过 npm 启动。
App 形态（Android / iOS / Desktop）全部走上面的 KMP 工程，不用 uni-app 打包 5+App。

## Git 约定

- 分支：`master`（毕设阶段单人开发，够用）
- 提交信息加模块前缀，便于回溯：
  - `server: 初始化 Gin 路由与数据库连接`
  - `web: 登录页对接 /api/login`
  - `app: 首页商品列表`
  - `uni: 购物车页面`
  - `docs: 补充数据库设计`
- 构建产物、IDE 配置、本地路径（`local.properties`）、运行期数据（`uploads/`、`*.db`、日志）一律不入库，规则见各目录 `.gitignore`。

## 当前进度

| 模块 | 状态 |
|---|---|
| server | 空壳，仅 `go.mod` |
| uniapp | 官方默认模板（Hello 页） |
| webadmin | Vite 默认模板（Home / About） |
| app | KMP 默认模板（Click me 演示） |
| docs | 空目录 |

## 下一步

1. 确认业务主题与范围（宠物商城 / 领养社区 / 专利服务……）。
2. 在 `server` 里搭出 Gin + SQLite 骨架：`main.go`、`models/`、`handlers/`。
3. 定数据库模型和接口清单，写进 `docs/`。
4. 三端按接口清单逐页实现，优先保证一条完整业务链路可演示。
