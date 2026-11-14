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
- [ ] **批次 7（收尾）**：`DesignApp` 壳（NavigationBar / NavigationRail / Toast）换 Miuix、删除旧设计系统（DComponents 中已被替代的组件）、`docs/README` 与本文件收尾记录

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

## 已知风险

- **无法本地编译**：所有重写靠静态检查 + CI 发版验证，签名类错误（参数名拼错、重载不匹配）可能在发版时才暴露 → 每个组件 API 都从缓存源码 jar 核对过，但不排除遗漏；
- **Miuix TextField 的 label 浮动动画**与旧版式差异较大，逐屏重写后需要真机过一遍观感；
- miuix-blur 的玻璃效果仅 Android 13+ 生效，低版本降级为不透明（已用 tools:overrideLibrary 放行合并）；
- 逐屏重写期间新旧组件混用，过渡期观感不一致属预期，全部完成后再统一调视觉。
