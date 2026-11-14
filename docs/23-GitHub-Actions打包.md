# GitHub Actions 打包

> 日期：2026-11-14
> 仓库：`github.com/zlddba/bishe`（私有）
> 工作流：`.github/workflows/build.yml`

---

## 一、能打什么

| 产物 | 架构 | 构建 runner | 说明 |
| --- | --- | --- | --- |
| 后端二进制 + 管理后台 dist + Docker 部署包 | amd64 + arm64 | `ubuntu-latest`（Go 交叉编译） | 复用 `build.sh --arch`，容器镜像由目标架构宿主机本地构建 |
| Android Release APK | 仅 arm64-v8a | `ubuntu-latest` | `ndk.abiFilters` 限定 64 位 ARM；签名、接口地址、高德 Key 走 Secrets |
| 桌面端 Linux deb / rpm / AppImage / tar.gz | amd64 + arm64 | `ubuntu-latest` / `ubuntu-24.04-arm` | jpackage 不能交叉打包，两种架构各跑一次；AppImage 用对应架构的 appimagetool |
| 桌面端 Windows msi | x64 + arm64 | `windows-latest` / `windows-11-arm` | jpackage 只能打当前系统格式 |
| 桌面端 macOS dmg | 仅 arm64 | `macos-latest` | 按约定不出 Intel 版 |
| 微信小程序 | 无架构概念 | `ubuntu-latest` | 只打包工程源码，产出 `patnest-uniapp-<version>.zip` |

工作流只在**推送 tag**（`v*`，例 `v2.0.1`）或**手动指定 tag 重跑**时运行，不跟随 push 分支，避免每次提交都消耗私有仓库的 Actions 分钟数。

**发布硬前提（三者缺一不可）**：

| 条件 | 说明 | 缺失时的报错 |
| --- | --- | --- |
| 发布分支 | 远端存在 `release/vX.Y.Z` | `远端不存在发布分支 release/v2.0.2` |
| 同名 tag | 远端存在 `vX.Y.Z`，且其提交在发布分支上 | 缺 tag 或 tag 不在该分支 |
| 发布文档 | `docs/release/vX.Y.Z.md` 存在且非空 | `缺少发布文档 …` |

`verify` job 负责逐项校验，任何一项不满足都会在构建开始前失败，不会出现「打了一半才发现没文档」。

---

## 二、一次性准备

### 2.1 Android 签名

签名文件 `patnestapp_release.jks` 没有入库（`.gitignore` 排除），需要转成 base64 放进 Secrets：

```bash
# Windows / Git Bash
base64 -w0 app/patnestApp/buildkeys/patnestapp_release.jks > release.jks.b64
```

把 `release.jks.b64` 的内容填到 **Settings → Secrets and variables → Actions → Secrets 标签 → New repository secret**，名字用 `PATNEST_KEYSTORE_BASE64`（**Repository secret，不是 Environment secret**，原因见 2.2）。

### 2.2 Secrets / Variables 清单

**结论：全部填在 Repository 级，不要填 Environment 级。**

路径：仓库页 → **Settings → Secrets and variables → Actions**，页面上有两个标签：

- **Secrets** 标签 → `New repository secret`（敏感值，写入后不可回读）
- **Variables** 标签 → `New repository variable`（明文，日志里可见，便于排查）

| 名称 | 填到哪个标签 | 类型 | 必填 | 用途 |
| --- | --- | --- | --- | --- |
| `PATNEST_KEYSTORE_BASE64` | Secrets | Secret | 是 | release keystore 的 base64 |
| `PATNEST_STORE_PASSWORD` | Secrets | Secret | 是 | keystore 密码 |
| `PATNEST_KEY_PASSWORD` | Secrets | Secret | 是 | key 密码 |
| `PATNEST_KEY_ALIAS` | Secrets | Secret | 否 | 默认 `key0` |
| `PATNEST_API_BASE` | **Variables** | Variable | 是 | 例：`https://www.linkzdsada.dpdns.org:9200/api/v1` |
| `PATNEST_AMAP_ANDROID_KEY` | Secrets | Secret | 否 | 留空时 App 自动退回后端瓦片底图 |

`PATNEST_API_BASE` 放 Variables 即可（不是敏感信息）；工作流写的是 `vars.PATNEST_API_BASE || secrets.PATNEST_API_BASE`，所以放 Secret 也能读到，只是没必要。

`GITHUB_TOKEN` **不用建**：`release` job 里的 `GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}` 是 Actions 内置的临时令牌，由 `permissions: contents: write` 授权。

#### 为什么不能填 Environment secrets

Environment secrets 只有在 job 显式声明 `environment: <环境名>` 时才会被注入。本工作流的 `verify / bundle / miniapp / android / desktop / release` 六个 job **都没有写 `environment:`**，所以：

- 填到 Environment 里的密钥，`${{ secrets.XXX }}` 取到的是**空字符串**；
- 表现是 Android 构建在 `Decode keystore` 一步报 `缺少 Secrets.PATNEST_KEYSTORE_BASE64，release 构建无法签名`，而不是报「secret 不存在」——很容易误判成没填。

仓库级密钥对所有 job、所有分支/tag 生效，这也是托管构建最省事的做法。

#### 如果确实想用 Environment（可选，多一层审批）

适合「发布必须人工点同意」的场景。改法是三处一起改，缺一处就会读不到：

1. **Settings → Environments → New environment**，命名 `release`（建议勾 Required reviewers，并把 Deployment branches 限制为 `release/*` 与 `v*` tag）；
2. 把上面 5 个 Secret 建在 `release` 环境下，同时删掉仓库级的同名 Secret（同名时 Environment 级会覆盖仓库级，留着容易误判到底读的是哪一份）；
3. 给**需要这些密钥的 job**加一行 `environment: release`。当前只有 `android` 真正读密钥，`release` job 用的是内置 token：

```yaml
  android:
    name: Android APK
    needs: verify
    environment: release        # 加了这行，${{ secrets.* }} 才会去 release 环境里找
    runs-on: ubuntu-latest
```

如果 `PATNEST_API_BASE` 放成了 **Environment variable**（而不是仓库变量），`android` 也必须加 `environment: release`，否则 `vars.PATNEST_API_BASE` 取不到，会退回默认的 `http://127.0.0.1:9200/api/v1`——打出来的 APK 连不上后端，而且构建日志一切正常，属于最难查的一类问题。

### 2.3 本地开发不受影响

`androidApp/build.gradle.kts` 的签名配置改成：

1. 优先读环境变量 `PATNEST_RELEASE_KEYSTORE` / `PATNEST_DEBUG_KEYSTORE`；
2. 没有环境变量时回落到项目内 `buildkeys/patnestapp_{debug,release}.jks`。

原来的 `D:\schoollearn\...` 绝对路径已删除——它在 Linux runner 上会让 Gradle 配置阶段直接失败。本机只要有 `buildkeys/` 目录，`assembleDebug/assembleRelease` 行为不变。

---

## 三、发布流程与产物

### 3.1 一张图看懂

```
release/v2.0.2 分支 ──┐
                      ├─→ verify（校验分支/tag/文档）──→ 并行构建 ──→ Release（正文=发布文档）
tag v2.0.2 ───────────┘
docs/release/v2.0.2.md ┘
```

### 3.2 操作步骤

```bash
git switch -c release/v2.0.2              # 1. 从 master 切发布分支
cp docs/release/TEMPLATE.md docs/release/v2.0.2.md   # 2. 写发布说明
$EDITOR docs/release/v2.0.2.md
git add docs/release/v2.0.2.md && git commit -m "docs: v2.0.2 发布说明"
git push origin release/v2.0.2            # 3. 推分支
git tag v2.0.2 && git push origin v2.0.2  # 4. 打 tag 触发流水线
```

手动重跑：**Actions → Release → Run workflow**，在 `tag` 输入框填已存在的 tag（例 `v2.0.1`）。下文示例统一用假设的下一个版本 `v2.0.2`。适用于构建失败后重试、或补发附件；不会重复创建 Release，同名时改为覆盖正文与附件。

### 3.3 发布文档即 Release 正文

`docs/release/vX.Y.Z.md` 的全文会作为 Release 说明提交，流水线不额外拼文案。好处是发布说明跟着代码走、能 review、能回滚，不用在 Actions 日志和网页表单之间来回粘贴。

- 模板：`docs/release/TEMPLATE.md`（版本亮点 / 新增功能 / 问题修复 / 升级说明 / 已知问题 / 校验）
- 文件名必须与 tag 严格对应，`v` 前缀不能省
- 空文件会被 `verify` 拒绝

### 3.4 产物与命名规范

统一格式 `patnest-<版本>-<平台>-<架构>.<扩展名>`，**架构标识必写**（源码包除外）。v2.0.2 起一次发布共 15 个附件、11 个 job 实例。

| Artifact | 文件 |
| --- | --- |
| `patnest-server-linux-amd64` / `-arm64` | `patnest-server-<version>-linux-<arch>.zip` |
| `patnest-android-apk` | `patnest-<version>-android-arm64.apk` |
| `patnest-desktop-linux-amd64` / `-arm64` | `patnest-<version>-linux-<arch>.deb` / `.rpm` / `.AppImage` / `.tar.gz` |
| `patnest-desktop-windows-x64` / `-arm64` | `patnest-<version>-windows-<arch>.msi` |
| `patnest-desktop-macos-arm64` | `patnest-<version>-macos-arm64.dmg` |
| `patnest-uniapp-project` | `patnest-uniapp-<version>.zip` |

所有产物上传到 Artifact（保留 14 天）并挂到 Release。`release` job 用 `find dist -type f` 递归收集文件，避免把目录当附件传给 `gh`。

### 3.4.1 架构矩阵实现要点

- **jpackage 不支持交叉打包**：Linux / Windows 的 arm64 版本必须在 arm64 runner 上跑。GitHub 从 2026-01 起在私有仓库也开放了 `ubuntu-24.04-arm` / `windows-11-arm` 标准托管 runner，无需自托管。
- **Compose Desktop 的原生库**：Skiko 提供 `skiko-awt-runtime-linux-arm64` 与 `skiko-awt-runtime-windows-arm64`，`compose.desktop.currentOs` 会按当前 runner 架构自动解析，不需要改依赖。
- **AppImage 工具也要换架构**：amd64 用 `appimagetool-x86_64.AppImage`（`ARCH=x86_64`），arm64 用 `appimagetool-aarch64.AppImage`（`ARCH=aarch64`）。
- **服务端只交叉编译二进制**：`build.sh --arch arm64` 设 `GOARCH=arm64`，包内 Dockerfile 用 `alpine:latest` / `nginx:latest` 多架构基础镜像，服务器按自身架构本地 build 镜像。CI 不构建、不推送容器镜像。
- **Android**：`androidApp/build.gradle.kts` 的 `defaultConfig.ndk.abiFilters` 只保留 `arm64-v8a`；CI 上传前用 `unzip -l` 检查 APK 里没有 `lib/armeabi-v7a/`。
- **macOS**：按约定只有 `macos-latest`（Apple Silicon），不接 `macos-*-intel`，省掉 10× 计费的 Intel job。

### 3.4.2 命名规范的来源

之前同一 Release 里出现过 4 套产品名（`androidApp` / `PatNest` / `patnest-*` / `team.wd.atnestapp`）和 4 套架构写法（无 / `x64` / `x86_64` / `amd64`）。现在：
- `desktopApp/build.gradle.kts` 的 `packageName` 改成 `patnest`（不再拿 applicationId 当产品名）；
- `build.sh` / `build.ps1` 的部署包名改成 `patnest-server-<version>-linux-<arch>`，amd64 也带架构后缀；
- 各 job 在上传前把 jpackage / AGP / appimagetool 的原始文件名统一复制成规范名；
- APK、msi、dmg、deb、rpm、tar.gz 全部带版本号与架构标识。

### 3.5 校验失败对照表

`verify` 用本地临时仓库跑过六个用例，正常与拦截两条路径都能走通：

| 日志里的报错 | 缺什么 | 怎么补 |
| --- | --- | --- |
| `远端不存在 tag v2.0.2` | tag 没推 | `git push origin v2.0.2` |
| `远端不存在发布分支 release/v2.0.2` | 分支没推或名字不对 | `git switch -c release/v2.0.2 && git push origin release/v2.0.2` |
| `tag v2.0.2（abc12345）不在分支 release/v2.0.2（def67890）上` | tag 打在分支外的提交上 | 把该提交合入发布分支，删 tag 重打：`git tag -d v2.0.2 && git push origin :refs/tags/v2.0.2` |
| `缺少发布文档 docs/release/v2.0.2.md` | 没写发布说明 | 复制 `docs/release/TEMPLATE.md` 改名 |
| `发布文档 … 内容为空` | 文件是空的 | 补内容；Release 正文不能为空 |
| `标签必须以 v 开头` / `标签格式必须是 vX.Y.Z` | tag 命名不合规 | 用 `v2.0.2` 这种三段式 |
| `手动触发请填写要发布的 tag` | 在分支上点 Run workflow 但没填 tag | 填 tag，或改用推 tag 触发 |

### 两个产物注意事项

- **桌面端开箱即用，不需要 `.env`，也不需要用户安装 Java**：接口地址在构建期由 `PATNEST_API_BASE` 写成 `-Dpatnest.api.base=...` 固化进启动器；jlink 裁剪的 JVM 也一并打进产物（deb/rpm/msi/dmg/tar.gz/AppImage 都自带）。运行时若想临时换后端，仍可用环境变量 `PATNEST_API_BASE` 或 `.env` 覆盖；二者都缺失时用固化值，不会抛异常。
- **APK 的接口地址在构建期写死**（`BuildConfig.API_BASE`），换环境要改 Variable 重新构建，App 内没有修改入口。

---

## 四、微信小程序（打包源码工程）

### 4.1 现在的做法

这个项目是 **HBuilderX 工程**（根目录没有 `package.json`，只有 `manifest.json` + `pages.json`），HBuilderX 只有 Windows/macOS 桌面版，托管 runner 装不了，所以 CI **不编译小程序**，只把工程原样打成 zip：

- 产物：`patnest-uniapp-project` → `patnest-uniapp-<version>.zip`
- 排除：`unpackage/`、`.hbuilderx/`、`node_modules/`
- 版本号：tag 去掉 `v`

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
npx miniprogram-ci upload --pp ./dist/build/mp-weixin --pkp ./private.key --appid wx... --uvVersion 2.0.2
```

优点：全云端、可复现；缺点：要调整目录结构，HBuilderX 仍能打开 CLI 工程，但需要重新验证一轮。上传密钥同样要放进 Secrets（`WX_PRIVATE_KEY`）。

### 4.4 只打「体验版」不发布

如果只是想在 CI 里产出可安装的体验包，也可以只做 `uni build -p mp-weixin` 出 dist，再用 `miniprogram-ci` 上传为体验版，不做提审。仍需 AppID + 上传密钥。

---

## 五、成本与限制

- 私有仓库的 Actions 分钟数有限额：Linux 按 1 倍计，Windows 2 倍，macOS **10 倍**。双架构矩阵共 11 个 job 实例（9 个构建 + verify + release），累计计费约为早期 8-job 版本的 1.5~2 倍，所以只在打 tag 时跑；日常调试在本地构建，不要为了验证代码去推 tag。
- `windows-11-arm` 上的 jpackage 依赖 WiX 工具链，若首次运行报 WiX 缺失，需在该镜像上显式安装后再重跑；其余 arm64 runner 的 setup-java / Gradle / Skiko 依赖均已验证可用。
- 首次构建要下载 Gradle 9.8.2、Android SDK 37、Kotlin/Compose 依赖，单次可能 15~25 分钟；`gradle/actions/setup-gradle` 已开启缓存，后续会快很多。
- Android 编译需要 SDK Platform 37；工作流只接受 license，缺失的 platform/build-tools 由 AGP 自动下载。若 AGP 版本更新导致包名变化，在 `sdkmanager` 里显式安装即可。
- 桌面端 macOS runner 是 arm64，打出来的 dmg 只适用于 Apple Silicon；要 Intel 包需要 `macos-13`。
- 流水线只负责**构建 + 发布 Release**，不会自动部署到服务器：部署包下载后仍按 `docs/04-部署与热更新.md` 的蓝绿流程上线。想连部署一起自动化，再加 rsync/scp 步骤与环境审批。

---

## 六、本地对应命令

```bash
# 服务端部署包（与 CI 的 bundle job 相同；两种架构各打一份）
bash build.sh --version 2.0.2 --arch amd64
bash build.sh --version 2.0.2 --arch arm64

# Android Release（只含 arm64-v8a）
cd app/patnestApp && ./gradlew :androidApp:assembleRelease

# 桌面端（当前系统）
cd app/patnestApp && ./gradlew :desktopApp:packageDistributionForCurrentOS

# Linux 四种格式（需先 apt install fakeroot rpm）
cd app/patnestApp && ./gradlew :desktopApp:packageDeb :desktopApp:packageRpm :desktopApp:createDistributable
# createDistributable 的产物 build/compose/binaries/main/app/<name>/ 可直接运行，
# 打包成 tar.gz 或用 appimagetool 封成 .AppImage 见工作流 linux job
```

---

## 七、首次发布（v2.0.0）排错记录

首次运行 `43562710529`：`verify` / `bundle` / `miniapp` 通过，Android 与三个桌面 job 失败，`release` job 未执行（所以没有生成 Release）。三个根因与修法：

1. **`./gradlew: Permission denied`**（Linux/macOS）：`gradlew` 在 git 里的文件模式是 `100644`。已 `git update-index --chmod=+x app/patnestApp/gradlew`，同时工作流统一改成 `sh ./gradlew`，不再依赖可执行位。
2. **`Verification of Gradle distribution failed!`**（Windows 先暴露）：`gradle-wrapper.properties` 的 `distributionSha256Sum` 与官方不符，已按 `gradle-9.8.2-bin.zip.sha256` 改为 `21bd5da2…`。**以后升级 Gradle 必须同步改这个校验和**，否则三个系统的构建都会挂在下载后。
3. **`android-actions/setup-android@v3` 失败**：该 action 会去安装已被 Google 下线的 `tools` 包，`sdkmanager` 返回 1。已移除，改为直接用 runner 自带的 `cmdline-tools/latest/bin/sdkmanager` 接受 license 并安装 `platforms;android-37`。

另外确认：五个 Secret 与一个 Variable 都按 2.2 配在 Repository 级，`verify` 8 秒通过说明发布分支、tag、发布文档三项校验也齐全。

4. **第二次运行**：Android APK 在 3m51s 构建成功；三个桌面 job 全部挂在 `:desktopApp:compileKotlin`——`WalletPage.kt:213` 的字符串插值写成了 `"当前可用余额 ¥$moneyText(balance)"`，缺花括号会被解析成「变量 `moneyText` + 字面量 `(balance)`」，编译器报 `Function invocation 'moneyText(...)' expected`。改为 `"…¥${moneyText(balance)}"`。这类错误本地不编译很难发现，也说明 CI 的必要性。

每次修复都是：提交 → 快进 `release/v2.0.0` → 删除并重建同名 tag → 推送触发新一轮。

### 发布结果（v2.0.0，2026-11-14）

第三次运行 `43563726783` 全绿，耗时约 5 分钟：`verify` 7s、`miniapp` 13s、`bundle` 19s、`android` 5m0s、`deb` 3m11s、`msi` 3m57s、`dmg` 4m53s、`release` 14s。

<https://github.com/zlddba/bishe/releases/tag/v2.0.0> 已生成正式 Release（非草稿、非预发布），6 个附件：

| 附件 | 大小 |
| --- | --- |
| `patnest-docker-2.0.0.zip` | 14.9 MB |
| `androidApp-release.apk` | 107 MB |
| `team.wd.atnestapp_2.0.0_amd64.deb` | 110 MB |
| `team.wd.atnestapp-2.0.0.msi` | 117 MB |
| `team.wd.atnestapp-2.0.0.dmg` | 119 MB |
| `patnest-uniapp-2.0.0.zip` | 290 KB |

Release 正文与 `docs/release/v2.0.0.md` 逐字一致（忽略换行符后比对通过，2353 字），验证了「发布文档即 Release 正文」这条约定。

> 经验：Gradle 版本号现在从 `PATNEST_VERSION` 注入（`packageVersion` / `versionName`），`versionCode` 用 `github.run_number` 保证单调递增；升级 Gradle 时务必同步 `gradle-wrapper.properties` 的 `distributionSha256Sum`。

### v2.0.1 发布排错

第四次运行（v2.0.1）七个构建 job 全绿，但 `release` job 失败：`dist/*` 里混进了 `dist/desktopApp/`、`dist/dist/` 这两个**目录**，被当成附件传给 `gh release create`，报 `read dist/dist: is a directory`。原因是 `upload-artifact` 对多个 glob 会保留公共前缀之外的目录层级。

两处修正：

1. `linux` job 把 deb / rpm / tar.gz / AppImage 四种产物统一复制到 `app/patnestApp/out/`，上传路径改为单 glob `app/patnestApp/out/*`；
2. `release` job 改用 `find dist -type f` 递归收集**文件**，用数组传给 `gh release create/upload`，并在附件数为 0 时拒绝发布空 Release。

### v2.0.1 发布结果

第五次运行 `43566275863` 全绿（约 5m50s），<https://github.com/zlddba/bishe/releases/tag/v2.0.1> 生成正式 Release，9 个附件：部署包、APK、小程序工程包、deb、rpm、AppImage、tar.gz、msi、dmg。Release 正文与 `docs/release/v2.0.1.md` 逐字一致（忽略换行符，2446 字）。

Linux 四种格式由同一个 `linux` job 产出，实际文件名：

- `team.wd.atnestapp_2.0.1_amd64.deb`（110 MB）
- `team.wd.atnestapp-2.0.1-1.x86_64.rpm`（111 MB）
- `PatNest-2.0.1-x86_64.AppImage`（121 MB）
- `patnest-desktop-2.0.1-linux-x64.tar.gz`（120 MB）

---

## 八、架构矩阵改造（v2.0.2 起，尚未发版）

按「Android / macOS 仅 arm64；服务端（含容器）、Linux 桌面、Windows 桌面 x64 + arm64」调整：

| 改动 | 文件 |
| --- | --- |
| 部署包名改成 `patnest-server-<版本>-linux-<arch>`，amd64 也带架构后缀；`--skip-go` 复用旧包时仍兼容 `patnest-docker-*` 命名 | `build.sh`、`build.ps1` |
| `defaultConfig.ndk.abiFilters` 只留 `arm64-v8a`；CI 上传前校验 APK 不含 `lib/armeabi-v7a/` | `androidApp/build.gradle.kts`、工作流 |
| `packageName` 由 `team.wd.atnestapp` 改为 `patnest` | `desktopApp/build.gradle.kts` |
| `bundle` 加 amd64/arm64 矩阵；`linux` 加 `ubuntu-latest` / `ubuntu-24.04-arm`；`desktop` 加 `windows-latest` / `windows-11-arm`，macOS 仍只 `macos-latest` | `.github/workflows/build.yml` |
| AppImage 按架构选 `appimagetool-x86_64` / `appimagetool-aarch64` | 工作流 `linux` job |
| 所有 job 上传前统一复制成 `patnest-<版本>-<平台>-<架构>.<格式>` | 工作流 |

可行性核对：GitHub 私有仓库已开放 arm64 标准 runner（2026-01 起）；Skiko 提供 `skiko-awt-runtime-linux-arm64` / `skiko-awt-runtime-windows-arm64`；`appimagetool-aarch64.AppImage` 可下载。jpackage 不能交叉打包，所以每个架构各跑一次。`windows-11-arm` 的 WiX 工具链若缺失需在该镜像单独安装，是首个待实跑验证的点。

**这批改动尚未打 tag、未实跑 CI**；需要发布 v2.0.2 时按第三节流程操作。

### v2.0.2 发布结果（双架构矩阵首次实跑）

运行 `43569944862` 全绿，墙钟约 6 分钟：<https://github.com/zlddba/bishe/releases/tag/v2.0.2>，15 个附件，命名全部为 `patnest-<版本>-<平台>-<架构>.<格式>`。正文与 `docs/release/v2.0.2.md` 逐字一致（2541 字）。

关键验证点：
- `ubuntu-24.04-arm` 上 jpackage 产出 arm64 deb / rpm / AppImage / tar.gz，AppImage 用 `appimagetool-aarch64` 封装成功；
- `windows-11-arm` 上 jpackage + WiX 直接可用，产出 arm64 msi（此前标记的待验证点已确认）；
- `ndk.abiFilters` 生效，APK 内无 32 位 ARM 库（CI 校验通过）；
- 两个服务端部署包 `patnest-server-2.0.2-linux-amd64.zip` / `-arm64.zip` 并列发布。

### v2.0.3 发布结果

运行 `43575818994` 全绿（11 个 job，墙钟约 5.5 分钟），<https://github.com/zlddba/bishe/releases/tag/v2.0.3>，15 个附件，命名全部符合规范。正文与 `docs/release/v2.0.3.md` 逐字一致（1513 字）。KMP 下拉刷新组件随包交付。

### v2.0.4 发布结果

运行 `43577735376` 全绿（11 个 job，墙钟约 6 分钟），<https://github.com/zlddba/bishe/releases/tag/v2.0.4>，15 个附件，命名全部符合规范。正文与 `docs/release/v2.0.4.md` 逐字一致（1159 字）。

本版内容：DPullRefresh 增加 contentAlignment 对齐参数，修复 v2.0.3 引入的「平板上限宽正文贴左」问题；D27Capacity 月历格子宽屏限宽居中。服务端无变更。

### v2.0.5 发布结果

运行 `43579255203` 全绿（11 个 job，墙钟约 5.5 分钟），<https://github.com/zlddba/bishe/releases/tag/v2.0.5>，15 个附件，命名全部符合规范。正文与 `docs/release/v2.0.5.md` 逐字一致。本版内容：下拉刷新指示条收回修复（snapshotFlow 持续观察刷新态 + 触发时先钉住 + 150ms 兜底），转圈图标改为三态纯文字提示。服务端无变更。
