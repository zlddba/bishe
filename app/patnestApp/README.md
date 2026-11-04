# patnestApp

Kotlin Multiplatform + Compose Multiplatform 工程，交付 **Android** 与 **Desktop (JVM)** 两端。

> iOS 端已于 2026-11-04 移除：开发机是 Windows，Kotlin/Native 的 iOS target 需要 macOS + Xcode，
> 出不了包，也没有验证手段。相关目录（`iosApp/`、`shared/src/iosMain/`、`shared/src/iosTest/`）
> 与 Gradle 里的 iOS target、`ktor-client-darwin` 依赖都已删掉。以后有 macOS 环境再按 KMP 常规方式加回。

## 模块

- `shared/` 三端共用的业务核心：API 客户端、数据模型、计费与订单状态纯函数、Compose 界面。
  - `commonMain/` 跨端公共代码；`expect` 声明放这里。
  - `androidMain/`、`jvmMain/` 分别放各自的 `actual` 实现（网络引擎、版本号读取、平台名）。
- `androidApp/` Android 壳，只有一个 `MainActivity`，`setContent { PatNestMobileApp(applicationContext) }`。
- `shared/src/androidMain/.../ui/mobile/` Android 端全部界面与业务状态，见下方「Android 端」。
- `desktopApp/` 桌面端，入口与主要界面在 `ui/AppRoot.kt`。

## Android 端

代码都在 `shared/src/androidMain/kotlin/team/wd/atnestapp/ui/mobile/`，包名 `team.wd.atnestapp.ui.mobile`。
放在 `shared` 而不是 `androidApp` 是为了直接复用 `commonMain` 的 `ApiClient`、数据模型和 `AppIcon`，不必在壳模块里重复声明 Compose 依赖。

| 文件 | 职责 |
|---|---|
| `MobileTheme.kt` | 配色与圆角令牌，取值对齐 `docs/宠栖后台-UI配色设计图.html` |
| `MobileSession.kt` | 接口地址、令牌、账号角色存 `SharedPreferences` |
| `MobileState.kt` | 路由表、返回栈、所有页面数据与业务动作 |
| `MobileComponents.kt` / `MobileCards.kt` | 原子控件与业务卡片 |
| `MobileImage.kt` / `MobilePicker.kt` / `MobileNavigator.kt` | 图片采样缓存、相册选图、叫起地图导航 |
| `LoginScreen.kt` / `OwnerScreens.kt` / `SitterScreens.kt` / `Sheets.kt` | 登录注册、主人端页面、机构端页面、底部弹层 |
| `PatNestMobileApp.kt` | 根组件：启动页、页面分发、底部导航、Toast 与弹层遮罩 |

几个约定：

- 导航不引入 navigation 依赖，`MobileScreen` 枚举加手写返回栈，系统返回键先关弹层再退页；
- 确认、短表单、选券、资质上传统一做成底部弹层（`MobileSheet`），不用 `Dialog`，避开输入法焦点和返回键的兼容坑；
- 机构账号登录后在「机构资料 - 账号」里切回主人端，主人端「我的」里有「机构工作台」入口；
- 每 15 秒轮询一次订单与消息，页面进入时按 `loadFor(screen)` 补数据；
- 导航按高德地图 App、系统地图 `geo:` 顺序回退，`AndroidManifest.xml` 里已声明包可见性；
- 后端是局域网 `http`，必须保留 `android:usesCleartextTraffic="true"`；
- 图片上传走相册选择器（`PickVisualMedia`），需要 `shared/build.gradle.kts` 的 androidMain 里有 `androidx.activity:activity-compose`。

## 运行

```powershell
./gradlew :desktopApp:run             # 桌面端
./gradlew :desktopApp:hotRun --auto   # 桌面端热重载
./gradlew :androidApp:assembleDebug   # Android
```

## 测试

```powershell
./gradlew :shared:testAndroidHostTest   # Android
./gradlew :shared:jvmTest               # Desktop
```

工程目标 JVM 21；本机若是更高版本的 JDK，Gradle 报工具链不匹配时补一个 JDK 21。

了解更多：[Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
