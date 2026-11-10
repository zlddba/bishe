# shared/amap-repo — 高德 SDK 的本地 Maven 仓库

这里放的是从高德开放平台官网下载的 Android SDK，按 **Maven 仓库目录布局** 存放。
`shared/build.gradle.kts` 通过坐标 `com.amap.api:amap-bundle:11.3.100` 引用它，
`settings.gradle.kts` 里把本目录注册成了仓库（名字 `localAmapRepo`，只接管 `com.amap.api` 组）。

## 为什么不用 `shared/libs/xxx.aar` 了

以前是 `implementation(files("libs/xxx.aar"))`，AGP 9 会直接拒绝：

```
Direct local .aar file dependencies are not supported when building an AAR.
The following direct local .aar file dependencies of the :shared project caused this error:
...AMap3DMap_11.3.100_...aar
```

`shared` 用的是 `com.android.kotlin.multiplatform.library` 插件，它必须把 shared 打成
AAR 交给 `androidApp`，而 AGP 不允许 AAR 里再挂一个本地 `.aar` 文件依赖
（那样打出来的 AAR 会缺类、缺资源）。换成 Maven 坐标后走的是标准 AAR 依赖管线，
类、`res/`、`jni/`、`AndroidManifest.xml` 都会被正常合并，限制也就不存在了。

## 目录结构

```
shared/amap-repo/
└── com/amap/api/amap-bundle/11.3.100/
    ├── amap-bundle-11.3.100.aar   ← 官网那个合并包，改了名
    └── amap-bundle-11.3.100.pom   ← 给 Gradle 认坐标用的最小 POM
```

文件名必须和 `<artifactId>-<version>.aar` 完全对应，否则 Gradle 找不到。

## 换版本怎么做

1. 把新 aar 按同样的规则放到新目录，例如
   `com/amap/api/amap-bundle/11.4.0/amap-bundle-11.4.0.aar` + 同名 `.pom`（改 `<version>`）；
2. 改 `shared/build.gradle.kts` 里的 `val amapLocalVersion = "11.4.0"`；
3. 删掉旧版本目录，**Sync Project**（这个判断在配置阶段执行）。

如果只是想让工程回到 Maven Central 的老版本（10.0.600），把 `amap-repo` 里的
`amap-bundle` 目录删掉即可，`useLocalAmapAar` 会自动变 false。

## 不要和 Maven 依赖并存

同一个类被本地 aar 和 Maven 包各提供一次，dex 阶段会报：

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

## 版权

aar 是高德开放平台的发布物，仅按官方 SDK 许可用于本项目，不要提交到公开仓库。
