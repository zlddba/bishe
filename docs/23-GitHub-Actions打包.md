# GitHub Actions 打包

> 日期：2026-11-14
> 仓库：`github.com/zlddba/bishe`（私有）
> 工作流：`.github/workflows/build.yml`

---

## 一、能打什么

| 产物 | 托管 runner | 说明 |
| --- | --- | --- |
| 后端二进制 + 管理后台 dist + Docker 部署包 | 可以（ubuntu） | 直接复用 `build.sh`，产出 `release/patnest-docker-*.zip` |
| Android Release APK | 可以（ubuntu + Android SDK） | 签名、接口地址、高德 Key 全部走 Secrets |
| 桌面端 deb / msi / dmg | 可以（三个系统各跑一份） | Compose Desktop 的 jpackage 只能打当前系统格式 |
| 微信小程序 | 可以（只打包工程，不编译） | 产出 `patnest-uniapp-<version>.zip`，下载后用 HBuilderX 导入发行 |

工作流默认只在**打 tag**（`v1.2.0`）或**手动触发**时运行，避免每次 push 都消耗私有仓库的 Actions 分钟数。需要每次 push 都构建时，把文件里 `push.branches` 的注释取消即可。

---

## 二、一次性准备

### 2.1 Android 签名

签名文件 `patnestapp_release.jks` 没有入库（`.gitignore` 排除），需要转成 base64 放进 Secrets：

```bash
# Windows / Git Bash
base64 -w0 app/patnestApp/buildkeys/patnestapp_release.jks > release.jks.b64
```

把 `release.jks.b64` 的内容填到仓库 **Settings → Secrets and variables → Actions → Secrets**。

### 2.2 Secrets / Variables 清单

| 名称 | 类型 | 必填 | 用途 |
| --- | --- | --- | --- |
| `PATNEST_KEYSTORE_BASE64` | Secret | 是 | release keystore 的 base64 |
| `PATNEST_STORE_PASSWORD` | Secret | 是 | keystore 密码 |
| `PATNEST_KEY_PASSWORD` | Secret | 是 | key 密码 |
| `PATNEST_KEY_ALIAS` | Secret | 否 | 默认 `key0` |
| `PATNEST_API_BASE` | Variable 或 Secret | 是 | 例：`https://www.linkzdsada.dpdns.org:9200/api/v1` |
| `PATNEST_AMAP_ANDROID_KEY` | Secret | 否 | 留空时 App 自动退回后端瓦片底图 |

`PATNEST_API_BASE` 建议放 **Variables**（不是敏感信息，日志里也能看到，方便排查）；工作流优先读 Variables，没有再读 Secrets。

### 2.3 本地开发不受影响

`androidApp/build.gradle.kts` 的签名配置改成：

1. 优先读环境变量 `PATNEST_RELEASE_KEYSTORE` / `PATNEST_DEBUG_KEYSTORE`；
2. 没有环境变量时回落到项目内 `buildkeys/patnestapp_{debug,release}.jks`。

原来的 `D:\schoollearn\...` 绝对路径已删除——它在 Linux runner 上会让 Gradle 配置阶段直接失败。本机只要有 `buildkeys/` 目录，`assembleDebug/assembleRelease` 行为不变。

---

## 三、运行与产物

手动触发：**Actions → Build → Run workflow**，可以单独勾选只打某一份产物。

| Artifact | 内容 |
| --- | --- |
| `patnest-deploy-bundle` | `patnest-docker-<version>.zip`，含 API/migrate 二进制、`web/`、`db/migrations`、compose 与安装脚本 |
| `patnest-android-apk` | `androidApp-release.apk` |
| `patnest-desktop-deb` / `-msi` / `-dmg` | 对应系统的安装包 |
| `patnest-uniapp-project` | `patnest-uniapp-<version>.zip`，小程序 HBuilderX 工程源码包（不含 `unpackage/`） |

保留 14 天。打 tag 时版本号取 tag 去掉 `v`；手动触发时部署包取 `webadmin/.../package.json` 的版本，小程序包取 `manifest.json` 的 `versionName`。

### 两个产物注意事项

- **桌面安装包不包含 `.env`**：桌面端运行时从工作目录向上找 `.env`（或读环境变量 `PATNEST_API_BASE`），分发时要把 `.env` 放在安装目录/启动目录旁边。
- **APK 的接口地址在构建期写死**（`BuildConfig.API_BASE`），换环境要改 Variable 重新构建，App 内没有修改入口。

---

## 四、微信小程序（打包源码工程）

### 4.1 现在的做法

这个项目是 **HBuilderX 工程**（根目录没有 `package.json`，只有 `manifest.json` + `pages.json`），HBuilderX 只有 Windows/macOS 桌面版，托管 runner 装不了，所以 CI **不编译小程序**，只把工程原样打成 zip：

- 产物：`patnest-uniapp-project` → `patnest-uniapp-<version>.zip`
- 排除：`unpackage/`、`.hbuilderx/`、`node_modules/`
- 版本号：tag 去掉 `v`；手动触发时取 `manifest.json` 的 `versionName`

拿到 zip 之后：

1. 解压得到 `patnestUniapp/`；
2. HBuilderX → 文件 → 导入 → 从本地目录导入；
3. 「运行到微信开发者工具」调试，或「发行 → 小程序-微信」上传。

`manifest.json` 里的小程序 AppID 是 `wx1366ab99a6b27171`，导入后确认微信开发者工具登录的是同一个小程序账号。

### 4.2 如果以后想自动发行（可选）

下面两种方案留档，需要「CI 直接出 mp-weixin 包并上传」时再启用。

#### 方案 A：自托管 Windows runner

1. 在一台常开的 Windows 机器上装 HBuilderX、微信开发者工具，并登录；
2. 在该机器上注册 GitHub self-hosted runner，加标签 `[self-hosted, windows, hbuilderx]`；
3. 加一个 job（示例，未默认启用）：

```yaml
  miniprogram:
    runs-on: [self-hosted, windows, hbuilderx]
    steps:
      - uses: actions/checkout@v4
      - name: 发行小程序
        run: |
          # 具体子命令以本机 HBuilderX 版本的 CLI 为准，先 `cli.exe --help` 确认；
          # 目标是产出 dist/build/mp-weixin
          & "C:\Program Files\HBuilderX\cli.exe" --help
          # 再用微信开发者工具 CLI 或 miniprogram-ci 上传 dist
```

优点：和现在的 HBuilderX 工作流完全一致；缺点：需要一台常开的 Windows 机器。

#### 方案 B：迁成 uni-app CLI 工程（纯云端）

把 `uniapp/patnestUniapp` 改成 CLI 结构：

```
uniapp/patnestUniapp/
  package.json        # @dcloudio/uni-app / vite / vue
  vite.config.js
  src/
    pages.json  manifest.json  App.vue  main.js  pages/ ...
```

之后就能在 ubuntu runner 上跑：

```bash
npm ci
npx uni build -p mp-weixin
npx miniprogram-ci upload --pp ./dist/build/mp-weixin --pkp ./private.key --appid wx... --uvVersion 1.2.0
```

优点：全云端、可复现；缺点：要调整目录结构，HBuilderX 仍能打开 CLI 工程，但需要重新验证一轮。上传密钥同样要放进 Secrets（`WX_PRIVATE_KEY`）。

### 4.4 只打「体验版」不发布

如果只是想在 CI 里产出可安装的体验包，也可以只做 `uni build -p mp-weixin` 出 dist，再用 `miniprogram-ci` 上传为体验版，不做提审。仍需 AppID + 上传密钥。

---

## 五、成本与限制

- 私有仓库的 Actions 分钟数有限额：Linux 按 1 倍计，Windows 2 倍，macOS **10 倍**。桌面端三系统一次约 30~50 分钟，建议只在发版时跑；日常调试用 `workflow_dispatch` 单独勾选。
- 首次构建要下载 Gradle 9.8.2、Android SDK 37、Kotlin/Compose 依赖，单次可能 15~25 分钟；`gradle/actions/setup-gradle` 已开启缓存，后续会快很多。
- Android 编译需要 SDK Platform 37；工作流只接受 license，缺失的 platform/build-tools 由 AGP 自动下载。若 AGP 版本更新导致包名变化，在 `sdkmanager` 里显式安装即可。
- 桌面端 macOS runner 是 arm64，打出来的 dmg 只适用于 Apple Silicon；要 Intel 包需要 `macos-13`。
- 工作流没有做「发布 Release / 上传到服务器」，只产出 Artifact。需要自动发布可以再加 `softprops/action-gh-release` 或 rsync/scp 步骤。

---

## 六、本地对应命令

```bash
# 部署包（与 CI 的 bundle job 相同）
bash build.sh --version 2.0.0

# Android Release
cd app/patnestApp && ./gradlew :androidApp:assembleRelease

# 桌面端（当前系统）
cd app/patnestApp && ./gradlew :desktopApp:packageDistributionForCurrentOS
```
