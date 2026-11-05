# shared/libs — 本地 AAR 目录

## 怎么放

从高德官网下载 Android 地图 SDK（3D 地图），解压后把 `libs/` 里的 `.aar` 直接拷到这里：

```
shared/libs/
├── AMap3DMap_x.y.z.aar      ← 只要这个就能显示地图
├── AMapSearch_x.y.z.aar     ← 可选，本工程没用到
└── AMapLocation_x.y.z.aar   ← 可选，本工程没用到（定位走系统 LocationManager）
```

文件名不用改、不用登记到任何配置文件。`shared/build.gradle.kts` 会自动扫描本目录里的
`*.aar` / `*.jar`：只要有文件就切到本地包，没有就退回 Maven 上的 `com.amap.api:3dmap`。

放好之后 **Sync Project**（或命令行 `--rerun-tasks`），因为"用不用本地包"是在配置阶段判定的。

## 为什么放在 shared 而不是 androidApp

`shared/src/androidMain/.../design/DAmap.kt` 里直接 `import com.amap.api.maps.AMap`，
SDK 必须出现在 shared 的编译类路径上。

## 下载包长什么样

官网给的是一个 zip，解压后通常是：

```
AMap_Android_3DMapSDK/
├── libs/
│   └── AMap3DMap_x.y.z.aar        ← 拷这个
├── assets/                        （旧版包才有，不用拷）
└── 说明文档 / API 文档
```

新版是单个 `.aar`（内含 `classes.jar` + `jni/<abi>/libAMapSDK_MAP_v*.so` + assets 样式表），
直接丢进来即可，不需要再配 `jniLibs.srcDirs`。

**只有旧版**（`AMap3DMap_x.y.z.jar` + `arm64-v8a/`、`armeabi-v7a/` 这样的 so 目录）
才需要额外两步，本工程当前用的是 aar 路线，不用管：

```kotlin
// 旧版 jar + so 的写法示意，仅在下载到 jar 包时需要
android { sourceSets["main"].jniLibs.srcDirs += "libs" }
dependencies { implementation(files("libs/AMap3DMap_x.y.z.jar")) }
```

## 不要和 Maven 依赖并存

同一个类被 aar 和 Maven 包各提供一次，dex 阶段会报：

```
Duplicate class com.amap.api.maps.AMap found in modules ...
```

`shared/build.gradle.kts` 里已经用 if/else 二选一，不要改成两个都加。

## 换版本后要顺手确认的三件事

1. **Key 的 SHA1 不变就不用动**。Key 绑的是包名 + 签名，与 SDK 版本无关。
2. **隐私合规接口还在不在**。`MapsInitializer.updatePrivacyShow / updatePrivacyAgree`
   从 8.1.0 起强制要求，新版仍保留；若某天编译报找不到这两个方法，
   说明 API 名改了，看 `DAmap.kt` 的 `AmapBridge.prepare()`。
3. **白屏 6 秒兜底**。`DAmapView` 里加载超时会自动切回后端瓦片底图，
   看到瓦片图基本就是 Key 不对或签名不匹配，不是 aar 没生效。
