# patnestApp

Kotlin Multiplatform + Compose Multiplatform 工程，交付 **Android** 与 **Desktop (JVM)** 两端。

> iOS 端已于 2026-11-04 移除：开发机是 Windows，Kotlin/Native 的 iOS target 需要 macOS + Xcode，
> 出不了包，也没有验证手段。相关目录（`iosApp/`、`shared/src/iosMain/`、`shared/src/iosTest/`）
> 与 Gradle 里的 iOS target、`ktor-client-darwin` 依赖都已删掉。以后有 macOS 环境再按 KMP 常规方式加回。

## 模块

- `shared/` 三端共用的业务核心：API 客户端、数据模型、计费与订单状态纯函数、Compose 界面。
  - `commonMain/` 跨端公共代码；`expect` 声明放这里。
  - `androidMain/`、`jvmMain/` 分别放各自的 `actual` 实现（网络引擎、版本号读取、平台名）。
- `androidApp/` Android 壳，入口只做 `Launch()`。
- `desktopApp/` 桌面端，入口与主要界面在 `ui/AppRoot.kt`。

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
