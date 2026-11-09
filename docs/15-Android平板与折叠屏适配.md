# Android 平板与折叠屏适配

> 适用端：`app/patnestApp` 的 Android 端，界面代码在
> `shared/src/androidMain/kotlin/team/wd/atnestapp/ui/design`。
> 桌面端（`desktopApp`）是独立实现的一套 UI，不受本文影响。

## 1. 背景

Android 端 61 屏是按手机竖屏设计稿逐屏还原的，尺寸基本都是绝对值：18dp 页面边距、
86dp 封面、单列卡片、底部 4 个 Tab。这套版式在 360~430dp 的手机上正好，换到平板上只有两个结果：

- 一张卡片横贯 1200dp，文字行宽过长，视觉上像被拉伸的网页；
- 底部 4 个 Tab 铺在 1200dp 宽度的两端，手指够不到，也不符合大屏「导航在左」的习惯。

适配目标不是重画一套平板 UI，而是让**同一套界面在任意窗口尺寸下都成立**：
导航换位置、正文限宽、列表分列。手机上的版式保持逐像素不变。

## 2. 断点

口径与 Material 3 window size class 一致，定义在 `DWindow.kt` 的 `DSizeClass`：

| 档位 | 宽度 | 典型设备 | 导航 | 正文最大宽度 | 页面边距 |
| --- | --- | --- | --- | --- | --- |
| Compact | < 600dp | 手机竖屏、小折叠外屏 | 底部 Tab | 不限（`Dp.Infinity`） | 18dp |
| Medium | 600~839dp | 平板竖屏、手机横屏、折叠展开 | 左侧导航栏，88dp | 800dp 居中 | 24dp |
| Expanded | ≥ 840dp | 平板横屏、桌面模式、分屏大窗 | 左侧导航栏，96dp | 1040dp 居中 | 32dp |

限宽不是审美选择。文字行宽超过约 80 个字符后，阅读时眼球回扫距离变长，
表单和控制项也会散得过开。1040dp 这个值取的是「卡片内正文仍在舒适区间」的上限。

列数不按档位写死，而是用「可用宽度 ÷ 最小卡宽」计算，见 `DWindow.columns()`：

```
可用宽度 = 窗口宽 − 左侧导航栏 − 两侧边距
列数     = (可用宽度 / minItemWidth).toInt().coerceIn(1, 3)
```

同样叫「平板」，600dp 的竖屏和 1280dp 的横屏差一倍多，写死列数必然有一边不合适。
上限 3 列是刻意的：4 列会让机构卡片的封面和价格挤在一行里无法阅读。

## 3. 窗口信息怎么来

`DesignApp` 在根节点用 `BoxWithConstraints` 实测窗口宽高，构造 `DWindow` 后通过
`LocalDWindow` 下发：

```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val window = remember(maxWidth, maxHeight) { DWindow.of(maxWidth, maxHeight) }
    CompositionLocalProvider(LocalDWindow provides window) {
        Row {
            if (window.showRail) DNavigationRail(activeTab, nav)
            Box(Modifier.weight(1f)) { NavHost(...) }
        }
    }
}
```

几个设计取舍：

**用 `BoxWithConstraints` 而不是 `LocalConfiguration.screenWidthDp`。**
分屏拖拽、折叠屏开合、桌面模式自由窗口缩放时窗口尺寸会连续变化，
`BoxWithConstraints` 会随新的约束实时重组；`screenWidthDp` 只反映整块屏幕的宽度，
分屏时拿到的是错的。

**用 `staticCompositionLocalOf` 而不是 `compositionLocalOf`。**
窗口尺寸变化时整个子树本来就要重新布局，没必要为读值维护细粒度订阅。

**默认值取手机竖屏**（`DWindow(360.dp, 780.dp, Compact)`）。
任何没被 `DesignApp` 包住的 `@Preview` 或单测都会退化成改动前的版式，
不会抛 `CompositionLocal not present`。

## 4. 改了什么

### 4.1 导航：底部 Tab → 左侧导航栏

- `DBottomTabs` 保留，只在 `win.isCompact` 时渲染；
- 新增 `DNavigationRail`，Medium / Expanded 下常驻在 `NavHost` 左侧；
- 两者共用同一套 `DTab`（按主人端 / 机构端身份过滤）和同一个图标映射
  `DTab.dIcon`，所以两套导航不会出现图标对不上的情况。

二级页（机构详情、收银台等）的 `activeTab` 为 null，导航栏**仍然常驻但不高亮任何一项**。
平板上从详情页直接跳去别的 Tab 是常见操作，隐藏导航栏会逼用户先返回一层。

### 4.2 正文：限宽居中

`DFrame` / `DLazyFrame` 的正文统一经过 `dPageWidth`：

```kotlin
private fun Modifier.dPageWidth(win: DWindow): Modifier =
    if (win.hasContentLimit) this.widthIn(max = win.contentMaxWidth) else this
```

手机下这个修饰符是空操作，与改造前完全一致；宽屏下正文限宽后由外层
`Box(contentAlignment = Alignment.TopCenter)` 居中。

底部结算栏 `DFixBar` 套同样的限宽，否则按钮会被拉成一米长。
顶栏 `DTopBar` 也走同一套限宽与边距，标题才会和正文左边缘对齐，
而不是贴在屏幕最左边、和内容错开几百 dp。

### 4.3 列表：单列 → 多列

`DGrid` 负责「一排多张卡」：

```kotlin
DGrid(store.favorites, minItemWidth = 320.dp) { sitter -> A_SitterCard(...) }
```

它按可用宽度算列数，用 `weight` 均分，最后一行不足列数时补 `Spacer`。
补空位这一步是必要的：两张卡各占 1/3 和一张卡占满整行，视觉上会跳。

**惰性列表没有换成 `LazyVerticalGrid`**，而是按行 `chunked`：

```kotlin
items(shown.chunked(columns), key = { row -> row.first().id }) { row ->
    DPad { Row { row.forEach { Box(Modifier.weight(1f)) { Card(it) } } } }
}
```

两个原因：外层 `DFrame` 已经是滚动容器，嵌套一个可滚动网格会和外层抢滚动权；
按行分组能保持 `LazyColumn`「只组合可见行」的特性，首屏开销不变。

已适配的页面：

| 页面 | 最小卡宽 | 说明 |
| --- | --- | --- |
| 找机构 `D02FindSitters` | 320dp | 惰性列表，按行分列 |
| 我的订单 `D05Orders` | 360dp | 右侧有金额和按钮，更吃宽度 |
| 寄养动态 `D06Dynamics` | 380dp | 卡片带大图，太窄正文会碎 |
| 我的宠物 `D08Pets` | 340dp | |
| 我的收藏 `D13Favorites` | 320dp | |
| 优惠券 `D14Coupons` | 320dp | |
| 消息通知 `D15Messages` | 340dp | |
| 机构接单 `DScreensB` | 360dp | |
| 售后工单 `DScreensB` | 360dp | |
| 机构优惠券管理 `DScreensB` | 360dp | |
| 机构评价 `DScreensC` | 400dp | 评价卡文字最多，列宽给足 |
| 搜索结果 `DScreensC` | 340dp | |
| 平台公告 `DScreensC` | 360dp | |
| 领券中心 `DScreensC` | 320dp | |

**没有改的页面**：详情页、表单页、收银台、聊天、地图。这些是单列垂直流，
分列没有意义；它们只需要限宽和居中，由 `DFrame` 统一承担，页面代码一行没动。

### 4.4 边距

页面级的 `padding(horizontal = 18.dp)` 已全部替换为 `LocalDWindow.current.gutter`
（涉及 `DComponents.kt`、`DScreensA~D`、`DesignApp.kt`）。
组件内部的固定间距（卡片内 14dp、标签间 6dp 等）保持原值——它们是卡片的内部节奏，
不随窗口放大而放大。

### 4.5 清单配置

`androidApp/src/main/AndroidManifest.xml` 的 `MainActivity` 增加了两项：

```xml
android:resizeableActivity="true"
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"
```

`resizeableActivity` 显式打开，允许分屏、自由窗口、桌面模式。
`configChanges` 覆盖旋转与窗口尺寸变化：页面状态（导航栈、已加载列表、滚动位置）
都在 Compose 里，不让系统重建 Activity 就不会丢。

代价是这些配置变化时不再触发 `onConfigurationChanged` 以外的生命周期回调。
本项目没有依赖重建来刷新资源的逻辑，所以这个代价可以接受；
如果以后要按方向切换布局资源，需要显式处理。

## 5. 兼容性

| 场景 | 行为 |
| --- | --- |
| 手机竖屏（< 600dp） | 与改造前完全一致：底部 Tab、18dp 边距、单列 |
| 手机横屏（~800dp 宽） | 左侧导航、800dp 限宽；横向铺开的列表可两列 |
| 平板竖屏（600~839dp） | 左侧导航、24dp 边距、两列 |
| 平板横屏（≥ 840dp） | 左侧导航、32dp 边距、最多三列 |
| 折叠屏展开 | 展开瞬间窗口变宽，`BoxWithConstraints` 重组，状态不丢 |
| 分屏拖拽 | 同上，连续跟随 |
| 桌面模式自由窗口 | 同上，最小宽度下退化为 Compact 布局 |

## 6. 已知限制

1. **没有覆盖横屏的沉浸式头部。** 首页 hero 区在宽屏下是限宽的，
   但渐变背景只铺到内容宽度，两侧露出的页面底色与手机观感略有差异。
2. **地图页未做双栏。** `DSitterMap` 在大屏下仍是「地图 + 列表」上下结构，
   没有改成左地图右列表。这是因为它依赖的定位与标记逻辑是按整屏坐标写的，
   改成双栏要重算标记点击的命中区域，属于较大改动。
3. **列数上限 3。** 超宽屏（如 4K 桌面模式）会留白较多。
   如需 4 列以上，改 `DWindow.columns()` 里的 `coerceIn(1, 3)` 即可，
   但要同步检查各卡片的 `minItemWidth` 是否还成立。
4. **`configChanges` 屏蔽了 Activity 重建。** 见 4.5 的说明。

## 7. 验证方式

改动只涉及布局层，没有新增依赖、没有改数据流。建议按以下清单手工过一遍
（`adb` 连平板或模拟器，用开发者选项里的「最小宽度」切换档位）：

1. 手机 360dp：确认底部 Tab 存在、边距仍是 18dp、列表单列，与改造前逐屏对比；
2. 600dp：Tab 消失、左侧出现导航栏，正文居中且两侧有留白；
3. 840dp 以上：三列列表，最后一行不足时卡片不拉伸；
4. 分屏拖拽改变窗口宽度：布局连续跟随，不回退到首屏、不丢已加载数据；
5. 二级页在宽屏下：导航栏在、但没有任何 Tab 高亮；
6. 主人端与机构端各走一遍，确认导航栏图标与身份匹配。
