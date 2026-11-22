# Miuix 逐屏重写迁移计划

> 2026-11-14 启动。目标：把 KMP 客户端（Android）的全部 UI 从自研设计系统迁移到 Miuix（HyperOS 风格 Compose Multiplatform 组件库）。
> 本文档是唯一的进度台账，每完成一批就更新勾选状态。

## 基本决策

| 决策点 | 结论 |
| --- | --- |
| Miuix 版本 | `top.yukonga.miuix.kmp:miuix-*:0.9.4`（Maven Central 最新；ui/preference/icons/blur/squircle/nav 六模块） |
| 玻璃效果 | 用已发布的 **miuix-blur**（背景真实模糊 / ProgressiveBlur / 倾斜反光）。`miuix-glass` / `miuix-glass-icons` / `miuix-material` 官方从未发布（Central 404），不引入 |
| minSdk 冲突 | miuix-blur/shader 声明 minSdk 33，本应用 28 → AndroidManifest 已加 `tools:overrideLibrary`；**玻璃效果仅 Android 13+ 生效**，API 28~32 自动降级为不透明色 |
| 导航引擎 | **保留 androidx.navigation.compose**。Miuix-nav 是另一套导航运行时，替换收益小风险大；如后期需要再评估 |
| 主题 | `DTheme` 内已包 `MiuixTheme(colors = dMiuixColors(palette))`——品牌粉映射进 HyperOS 配色（primary=brandStrong 保证白字可读），MaterialTheme 与 MiuixTheme 双层共存，重写完成后移除 Material 层 |
| 编译验证 | 本机不编译（沿用约定）；每批保持全项目可编译，CI 在打 tag 发版时全量验证。中途想单独验证可手动触发 `workflow_dispatch` |
| 回退策略 | 旧设计系统（DTheme/DComponents/DScreens 的 D* 组件）在全部屏幕重写完成前**不删除**，已重写屏幕直接用 Miuix 组件 |

## 组件映射表（重写时对照）

| 旧组件 | Miuix 替代 | 备注 |
| --- | --- | --- |
| `DFrame` / `DLazyFrame` | `Scaffold` + `TopAppBar` + `LazyColumn`/`Column` | Scaffold content 是 `(PaddingValues) -> Unit`；TopAppBar 自带状态栏避让（defaultWindowInsetsPadding） |
| `DTopBar` | `TopAppBar(title, navigationIcon, actions)` | title 是 String；大标题模式用 largeTitle 参数 |
| `DCard` | `Card(modifier, onClick?, content: ColumnScope)` | 自带按压缩放反馈（pressFeedbackType） |
| `DButton` | `Button(onClick, colors = ButtonDefaults.buttonColorsPrimary())` | 默认 buttonColors 是灰底，主操作必须显式指定 primary |
| `DSmallButton` | `TextButton` 或小型 `Button` | |
| `DFieldInput` | **`DMiuixField`（全局唯一输入组件）** | 普通/密码双分支共用同一套 decorator 外观；密码走 `BasicSecureTextField` 系统混淆（修复退格清空 bug）；40 处调用已全部迁移 |
| `DTabLine` | `TabRow(tabs, selectedTabIndex, onTabSelected)` | |
| `DSwitch` 类开关 | `Switch(checked, onCheckedChange)` | |
| `DOptionRow` / 设置行 | preference 模块 `SwitchPreference` / `ArrowPreference` / `RadioButtonPreference` / `SliderPreference` | 设置页（D17Settings 等）优先用 |
| `DToast`（DesignApp） | Miuix `Snackbar` + Scaffold(snackbarHost) | 放到收尾批次统一换 |
| `DGrid` | `Column + Row + weight` 保留 | Miuix 无栅格组件；宽屏适配逻辑照搬 |
| 下拉刷新 | `PullToRefresh(isRefreshing, onRefresh, refreshTexts)` | 已接入 13 个页面（v2.0.5 起） |
| `AppIcon` 图标 | 保留自研枚举；可选接入 `miuix-icons` | 图标枚举含 Back/Home/Paw 等，无眼睛图标（密码显隐暂用文字） |

## 批次划分（每批一个 DScreensX 文件或一组自包含屏幕）

- [ ] **批次 0（已完成）**：地基。依赖接入（6 模块）、`DTheme` 加 MiuixTheme 桥、试点屏 **D09Login** 重写（Scaffold/TopAppBar/Card/TextField/Button 全 Miuix 化）
- [x] **批次 1**：认证与表单屏 —— `D40Register`、`D41ProfileEdit`、`D12PetAdd`、`D44AddressAdd`、`D28PublishDynamic`（新增 DMiuix.kt 桥接助手；下拉选择字段暂保留自研 Popup）
- [x] **批次 2**：主人端主页与列表 —— `D01Home`、`D02FindSitters`、`D05Orders`、`D08Pets`、`D13Favorites`、`D14Coupons`、`D15Messages`（新增 Scroll/List 双骨架，Miuix TopAppBar + PullToRefresh + 底部 Tab；页内卡片组件保留待视觉统一批次）
- [ ] **批次 3**：主人端详情与流程 —— `D03SitterDetail`、`D04Booking`、`D10Checkout`、`D11ReviewWrite`、`D19OrderDetail`、`D22PaySuccess`、`D23SitterCompare`
- [x] **批次 4**：机构端 —— `D18SitterWorkspace`、`DSitterOrders`、`DSitterMoments`、`DSitterProfile`、`D26ShopProfile`、`D27Capacity`、`D29/D30/D31`（DScreensB 机构端 9 屏 + D31SitterReviews）
- [x] **批次 5**：详情与二级页 —— `D06Dynamics`、`D16Addresses`、`D17Settings`、`D20Tickets`、`DTicketChat`、`D21Points`、`D24MapPicker`、`D25RoleSwitch`（D24MapPicker 本就是全屏地图无骨架）
- [x] **批次 6**：剩余散屏 —— `D32~D39`、`D42~D45`、`D46~D60` 全部（DScreensC x15、DScreensD x21 处骨架）
- [x] **批次 7（收尾）**：`DCard` 底层换 Miuix Card（100+ 调用点零改动）；旧骨架 `DFrame` / `DLazyFrame` 定义删除（零调用）。
  - **保留自绘的组件及理由**：导航壳（`DNavigationRail` / `DBottomTabs`）与 Toast——`AppIcon` 是 Canvas 自绘（非 ImageVector，Miuix 导航 item 强依赖 ImageVector），且现有选中态（BrandSoft 胶囊 + 动画）为定制设计；两者已走统一 DColor token 体系，随 MiuixTheme 深浅色联动。
  - **统一输入**：`DMiuixField` 为全项目唯一输入组件（40 处调用），普通/密码双分支共用 `DMiuixFieldBox` 外观；骨架 `DMiuixScrollScaffold` / `DMiuixListScaffold` 为唯一页面骨架（67 处调用）。

## 批次验收标准（每批都要过）

1. 全项目括号平衡 / 缺导入静态检查通过；
2. 未重写的屏幕**零改动**（避免连带回归）；
3. 重写屏的业务逻辑（store 调用、导航跳转、校验文案）逐行保留；
4. 亮色与深色两套调色板下视觉 token 全部走 MiuixTheme.colorScheme 或 DColor（不写死颜色）；
5. 提交信息格式：`miuix: 重写批次 N —— <涉及屏幕>`。

## 首次编译排错（批次 0，2026-11-14，l1.log）

4 个错误全部是导入类问题，已修复：

1. `DComponents.kt:1131 Unresolved 'Offset'` —— 清理 DPullRefresh 时误删了 `geometry.Offset` 导入，但 DDropdown 的 `localToWindow(Offset.Zero)` 还在用 → 已恢复；
2. `DScreensA.kt:50/1187 Unresolved 'KeyboardOptions'` —— 新版 state API 的 `KeyboardOptions` 在 **`androidx.compose.foundation.text`** 包（不是 `text.input`，后者只有 KeyboardActionHandler/TextFieldState 等）→ 已修正；
3. `DScreensA.kt:1153 Unresolved 'verticalScroll'` —— DScreensA 原来所有滚动都在 DFrame 里，从未直接用过这个修饰符 → 已补导入。

经验：**导入类错误是本迁移最高频的风险**（不编译只能靠静态扫描），后续每批都跑「正文引用 vs 导入」静态扫描（脚本见工作日志）再交付。

## 液态玻璃架构（2026-11-14 续，首帧崩溃后定型）

首帧 SIGSEGV 的根因是内容流玻璃在 `layerBackdrop` 记录层内部采样自身。修复后把玻璃拆成两层：

| 层 | 记录内容 | 谁用 | 约束 |
| --- | --- | --- | --- |
| `LocalDGlassBackdrop`（页面级） | 整页滚动内容 + 底色 | 顶栏、底部 Tab、结算栏、Toast、登录卡、地图浮层 | 玻璃必须在记录节点**之外**（骨架的浮层兄弟节点） |
| `LocalDGlassAmbient`（窗口级） | 品牌光斑 + 斜向光带，`DGlassAmbientLayer` | 卡片、输入框、搜索条、chip、小按钮、hero 头、左导航栏 | 记录层在 `DesignApp` 根部，是页面内容的**兄弟节点**，永不被玻璃引用 |

统一入口：

- `Modifier.dGlassHero()`：内容流里的品牌渐变 hero，采环境层 + 68% 品牌渐变；
- `Modifier.dGlassBrush(brush, alpha)`：钱包卡 / 订单机构头 / 信息卡这类已有品牌渐变的表面，玻璃在下、渐变在上；
- `Modifier.dGlassSurface()`：内容流里的卡片/输入框/小控件，采环境层 + tint + fallback；
- `DGlassSurface(...)`：浮层用，默认采页面级 backdrop，显式 `backdrop =` 可覆盖；
- `DCard` 已默认玻璃化（API < 33 自动回 Miuix 实体卡），全项目 100+ 卡片零调用点改动。

第二批覆盖：选择行（`DOptionRow` / 宠物 / 支付 / 身份切换 / 分段选择 / 筛选 tag）、聊天气泡（对方侧）、hero 上的铃铛/状态/切换身份胶囊、订单机构头、钱包卡、D 组 hero、信息卡、上传占位、搜索输入、回复引用、空态/成功/宠物图标圆、错误态。小控件统一把 `edgeDp` 收到 4~6dp，避免上下折射带互相覆盖。

第三批：**图片上的浮层按钮**。机构详情封面（返回/收藏）和店铺封面（更换封面）各自 `rememberDGlassBackdrop()`，图片层挂 `dGlassBackdropSource`，按钮作为兄弟浮层用 `DGlassSurface(backdrop = 图片backdrop)`——玻璃模糊的是图片本身，既不自采样，也不串到环境层。机构详情的小地图预览「查看大图」也走同一模式，但用 `AmapBridge.hasKey(context)` 分支：瓦片模式记录地图并开玻璃，SDK 模式（AndroidView 采不到）传 `backdrop = null` 自动退回 Scrim。

收尾补充：邀请码卡、订单机构头图标、身份切换图标块、地址页地图选点卡、宽屏左栏选中项、图片封面按钮全部玻璃化。剩余的实色面只保留语义/内容类：`DTag` 状态标签、容量日历格、进度条、开关轨道、头像/封面/图片渐变、分割线、原生 Picker 与 Popup 菜单。

## 玻璃文字对比度修复（2026-11-14 晚）

玻璃 tint 从早期的 30~60% 统一提到可读档位：

| 表面 | tint |
| --- | --- |
| 底部 Tab / 结算栏 / 输入栏 | 页面底色的 90~92% |
| 顶栏 | 76% |
| 地图信息卡 | 88% |
| 普通卡片 | 66% |
| 输入框 / 搜索 / 选项 / chip / 小按钮 | 72~80% |
| 图片上的浮层按钮 | 暗 Scrim 40~55% + 白字 |
| hero | 76% 品牌渐变 + 顶部 26% 黑色 scrim |

浅色背景从 `#FAF5F6` 调到 `#FBEAF1`（非常浅的品牌粉），卡片遮罩提高并把环境层改成近白，
让白卡在粉底上「浮」出来。`DCard` / `dGlassBrush` / `DGlassPanel` / `dGlassHero` 统一加
`dGlassSheen`：左上白色 bloom + -16° 品牌粉反光带 + 顶部 1px 亮边，画在内容之下，
不影响文字对比度。

地图页上下信息栏从 88% 卡底回退到 **26% 主题底 + 42% 玻璃**，并给栏内文字加 `dGlassTextShadow()`
（浅色白晕 / 深色黑晕），既透出地图又保证文字可读。同类回退还包括：
结算栏 `DFixBar` 30%/45%、工单输入栏与支付成功底栏 30%/45%、普通 `DCard` 从 80% 降到 68%、
添加照片方块 55%。这些透明浮层上的金额/地址/标签统一走 `dGlassTextShadow()`；输入框、
选项行、状态卡仍保持 70~80%，因为它们是文字控件，不是展示卡。

hero 上的统计数字（寄养中 / 累计订单 / 我的宠物）改成主题酒红玻璃卡：`Brand800` 82% + 40% 玻璃 + 白色描边 + sheen，浅色/深色下都不再出现灰黑色块。

底部 Tab 的当前页增加主题色圆角玻璃卡：选中项在 14dp 圆角卡上，`DColor.Brand` 20% + 42% 玻璃 + sheen；
未选中项保持透明 + 文字阴影。浅色模式是淡粉玻璃，深色模式自动变成深酒红玻璃，BrandInk 在两种主题下都可读。

导航栏改为透明玻璃：顶栏/底栏/宽屏左栏的 tint 降到 8~16%，`surfaceAlpha` 降到 0.55~0.60，
内容直接透出；顶栏和底栏各加一层很淡的渐变 scrim（顶栏 50%→透明、底栏透明→30%），
底栏文字再加 6dp 黑色阴影，保证透明后小字号仍然可读。系统状态栏/导航栏本身已由
`MainActivity.enableEdgeToEdge` 设为透明。

同时修了三处会导致暗色模式文字发糊的根因：

1. 订单机构头渐变用了字面量 `DColor.White`，深色下右半段变白、浅色 Ink 文字看不清 → 改为 `DColor.Card`；
2. `DGlassPanel` 登录卡 tint 只有 10% → 提到 68%，label/输入框/按钮有稳定底；
3. 浅色 `Ink3 #9C9296` 在玻璃上约 2.5:1 → 改为 `#6F6669`；深色 `Ink3` 改为 `#A99FA3`；浅色 `BrandInk` 从 `#AF4A62` 加深到 `#9C3F56`。按近似合成计算，玻璃上 `Ink3` / `BrandInk` 均已到 4.8:1 左右。

这样既恢复了「大量玻璃」的视觉，又保证任何内容流玻璃都不会进入 GPU 反馈环路。

当前光学配方（`DGlass.kt`）：边缘折射带 10dp、玻璃厚度 12dp、IOR 1.45、模糊 12~20dp；颜色 `saturation 1.45 / brightness +0.04 / contrast 1.08`；Screen 白 6% + Overlay 黑 3%；固定方向高光（法线点乘，24 次幂），只点亮受光侧。内容玻璃 tint 按表面分级：卡片 74~80%、输入框 75%、选项/chip 75~82%、hero 76% 品牌渐变 + 26% 顶部 scrim。

### 参考 Kyant0/AndroidLiquidGlass

它的透镜管线核心是：`roundedRectSDF` + `circleMap` 边缘压缩 + 逐通道色散 + 独立的 highlight stroke shader + 背景亮度自适应染色 + 重力感应光源。我们已吸收**逐通道色散**：红/蓝通道沿 SDF 法线反向偏移、绿通道居中，只在边缘带内、强度 0.8dp，避免整圈彩虹。没有移植的部分：per-corner SDF（我们仍是单圆角）、sensor highlight（需要重力传感器）、adaptive tint（需要背景亮度回读），这几项会引入额外依赖或每帧读回，暂不纳入。

## 已知风险

- **无法本地编译**：所有重写靠静态检查 + CI 发版验证，签名类错误（参数名拼错、重载不匹配）可能在发版时才暴露 → 每个组件 API 都从缓存源码 jar 核对过，但不排除遗漏；
- **Miuix TextField 的 label 浮动动画**与旧版式差异较大，逐屏重写后需要真机过一遍观感；
- miuix-blur 的玻璃效果仅 Android 13+ 生效，低版本降级为不透明（已用 tools:overrideLibrary 放行合并）；
- 逐屏重写期间新旧组件混用，过渡期观感不一致属预期，全部完成后再统一调视觉。
