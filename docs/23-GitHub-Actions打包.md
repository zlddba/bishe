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

工作流只在**推送 tag**（`v1.2.0`）或**手动指定 tag 重跑**时运行，不跟随 push 分支，避免每次提交都消耗私有仓库的 Actions 分钟数。

**发布硬前提（三者缺一不可）**：

| 条件 | 说明 | 缺失时的报错 |
| --- | --- | --- |
| 发布分支 | 远端存在 `release/vX.Y.Z` | `远端不存在发布分支 release/v1.2.0` |
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
release/v1.2.0 分支 ──┐
                      ├─→ verify（校验分支/tag/文档）──→ 并行构建 ──→ Release（正文=发布文档）
tag v1.2.0 ───────────┘
docs/release/v1.2.0.md ┘
```

### 3.2 操作步骤

```bash
git switch -c release/v1.2.0              # 1. 从 master 切发布分支
cp docs/release/TEMPLATE.md docs/release/v1.2.0.md   # 2. 写发布说明
$EDITOR docs/release/v1.2.0.md
git add docs/release/v1.2.0.md && git commit -m "docs: v1.2.0 发布说明"
git push origin release/v1.2.0            # 3. 推分支
git tag v1.2.0 && git push origin v1.2.0  # 4. 打 tag 触发流水线
```

手动重跑：**Actions → Release → Run workflow**，在 `tag` 输入框填 `v1.2.0`。适用于构建失败后重试、或补发附件；不会重复创建 Release，同名时改为覆盖正文与附件。

### 3.3 发布文档即 Release 正文

`docs/release/vX.Y.Z.md` 的全文会作为 Release 说明提交，流水线不额外拼文案。好处是发布说明跟着代码走、能 review、能回滚，不用在 Actions 日志和网页表单之间来回粘贴。

- 模板：`docs/release/TEMPLATE.md`（版本亮点 / 新增功能 / 问题修复 / 升级说明 / 已知问题 / 校验）
- 文件名必须与 tag 严格对应，`v` 前缀不能省
- 空文件会被 `verify` 拒绝

### 3.4 产物

| Artifact | 内容 |
| --- | --- |
| `patnest-deploy-bundle` | `patnest-docker-<version>.zip`，含 API/migrate 二进制、`web/`、`db/migrations`、compose 与安装脚本 |
| `patnest-android-apk` | `androidApp-release.apk` |
| `patnest-desktop-deb` / `-msi` / `-dmg` | 对应系统的安装包 |
| `patnest-uniapp-project` | `patnest-uniapp-<version>.zip`，小程序 HBuilderX 工程源码包（不含 `unpackage/`） |

四类产物同时上传为 Artifact（保留 14 天）与 Release 附件。版本号统一取 tag 去掉 `v`，不再从 `package.json` / `manifest.json` 猜。

### 3.5 校验失败对照表

`verify` 用本地临时仓库跑过六个用例，正常与拦截两条路径都能走通：

| 日志里的报错 | 缺什么 | 怎么补 |
| --- | --- | --- |
| `远端不存在 tag v1.2.0` | tag 没推 | `git push origin v1.2.0` |
| `远端不存在发布分支 release/v1.2.0` | 分支没推或名字不对 | `git switch -c release/v1.2.0 && git push origin release/v1.2.0` |
| `tag v1.2.0（abc12345）不在分支 release/v1.2.0（def67890）上` | tag 打在分支外的提交上 | 把该提交合入发布分支，删 tag 重打：`git tag -d v1.2.0 && git push origin :refs/tags/v1.2.0` |
| `缺少发布文档 docs/release/v1.2.0.md` | 没写发布说明 | 复制 `docs/release/TEMPLATE.md` 改名 |
| `发布文档 … 内容为空` | 文件是空的 | 补内容；Release 正文不能为空 |
| `标签必须以 v 开头` / `标签格式必须是 vX.Y.Z` | tag 命名不合规 | 用 `v1.2.0` 这种三段式 |
| `手动触发请填写要发布的 tag` | 在分支上点 Run workflow 但没填 tag | 填 tag，或改用推 tag 触发 |

### 两个产物注意事项

- **桌面安装包不包含 `.env`**：桌面端运行时从工作目录向上找 `.env`（或读环境变量 `PATNEST_API_BASE`），分发时要把 `.env` 放在安装目录/启动目录旁边。
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
npx miniprogram-ci upload --pp ./dist/build/mp-weixin --pkp ./private.key --appid wx... --uvVersion 1.2.0
```

优点：全云端、可复现；缺点：要调整目录结构，HBuilderX 仍能打开 CLI 工程，但需要重新验证一轮。上传密钥同样要放进 Secrets（`WX_PRIVATE_KEY`）。

### 4.4 只打「体验版」不发布

如果只是想在 CI 里产出可安装的体验包，也可以只做 `uni build -p mp-weixin` 出 dist，再用 `miniprogram-ci` 上传为体验版，不做提审。仍需 AppID + 上传密钥。

---

## 五、成本与限制

- 私有仓库的 Actions 分钟数有限额：Linux 按 1 倍计，Windows 2 倍，macOS **10 倍**。桌面端三系统一次约 30~50 分钟，所以只在打 tag 时跑；日常调试在本地构建，不要为了验证代码去推 tag。
- 首次构建要下载 Gradle 9.8.2、Android SDK 37、Kotlin/Compose 依赖，单次可能 15~25 分钟；`gradle/actions/setup-gradle` 已开启缓存，后续会快很多。
- Android 编译需要 SDK Platform 37；工作流只接受 license，缺失的 platform/build-tools 由 AGP 自动下载。若 AGP 版本更新导致包名变化，在 `sdkmanager` 里显式安装即可。
- 桌面端 macOS runner 是 arm64，打出来的 dmg 只适用于 Apple Silicon；要 Intel 包需要 `macos-13`。
- 流水线只负责**构建 + 发布 Release**，不会自动部署到服务器：部署包下载后仍按 `docs/04-部署与热更新.md` 的蓝绿流程上线。想连部署一起自动化，再加 rsync/scp 步骤与环境审批。

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
