# 后端 API 未接前端清单

> 审计日期：2026-11-14
> 代码基线：`server/patnestServer/internal/router/router.go` + `app/patnestApp` + `uniapp/patnestUniapp` + `webadmin/patnestServerAdminWeb`
> 审计范围：用户端 Android（KMP）、微信小程序（uni-app）、管理后台（Vue）三个前端

## 0. 结论摘要

本次从 `router.go` 解析出 **212 条路由**，逐条对照三个前端的 API 封装和页面调用。

结论：

1. **管理后台覆盖完整**：`adminApi` 的接口方法都能在页面里找到调用，没有发现“后台有接口、后台页面没用”的情况。
2. **修复前全端未调用 7 条，本轮已接 2 条，剩 5 条地图类**：
   - ✅ `GET /sitter/orders/:id`（2026-11-15 已接，详情进入时刷新）
   - ✅ `POST /sitter/shop`（2026-11-15 已接，保存时发现无店铺则建店）
   - ⏳ `GET /geo/static-map`
   - ⏳ `POST /geo/pick`
   - ⏳ `GET /geo/pick-result/:token`
   - ⏳ `GET /geo/pick/:token`
   - ⏳ `POST /geo/pick/:token`
   - 说明：这 5 条是浏览器选点/静态地图，管理端已用高德 JS，Android 已用高德 SDK；**桌面端仍在用其中 3 条**（`desktopApp` 的 `AppRoot.kt` 调 `fetchStaticMap` / `createGeoPick` / `pollGeoPick`），属桌面端自身功能，不需要补齐到 Android。
3. **Android 单独没接的 8 项，已全部补齐**：
   - ✅ 订单详情 `GET /pet-owner/orders/:id`
   - ✅ 动态详情 `GET /pet-owner/dynamics/:id`
   - ✅ 机构详情 `GET /sitters/:id`
   - ✅ 服务端机构搜索 `GET /sitters/search`
   - ✅ 撤销注销 `POST /pet-owner/account/cancel-withdraw`
   - ✅ 订单 `status_filter` 服务端筛选
   - ✅ 宠物 `species` 服务端筛选
   - ✅ 机构 `price_min` / `price_max` 价格区间筛选
   - ✅ 机构 `sort` 价格排序（推荐 / 价格低到高 / 价格高到低）
4. **5 条 `/pet-owner/sitters...` 是公开 `/sitters...` 的登录态别名**，全端没有直接调用；对应功能已经通过公开路由覆盖，不算功能缺失。
5. **`/health` 和支付回调不是前端功能**：前者是运维/发布检查，后者是微信/支付宝网关回调，不能算“未接”。

---

## 1. 判定标准

本文把“没调用”分成四类，避免把兼容路由和基础设施误报成缺失：

| 类型 | 判定 | 处理建议 |
| --- | --- | --- |
| A. 全端未调用 | Android、小程序、后台都没有任何调用 | 优先确认是漏接还是预留 |
| B. Android 未调用 | 小程序或后台已经调用，Android 没接 | 按 Android 功能缺口处理 |
| C. 参数级未使用 | 接口本身调用了，但后端支持的关键查询参数没用 | 评估是否影响分页/筛选正确性 |
| D. 兼容/基础设施 | 重复别名、健康检查、支付回调 | 不当作功能缺失 |

审计时排除了 `uniapp/unpackage/dist` 编译产物，避免同一处调用被重复统计。

---

## 2. 全端未调用（A 类，修复前）

### 2.1 `DELETE /sitter/addons/:id` —— 实际是“下架”，不是硬删除

**这条不是缺口，已由现有「停用/启用」覆盖。**

| 项 | 内容 |
| --- | --- |
| 后端 handler | `DeleteAddon` |
| 实际行为 | `UPDATE sitter_addons SET enabled = FALSE`，不是物理删除 |
| Android | 现有 `toggleAddon()` 走 `PUT /sitter/addons/:id` 的 `enabled` 字段，效果等价 |
| 小程序 / 后台 | 没有机构加购管理，不涉及 |
| 建议 | 保留后端语义，或把路由改名；前端不需要额外「删除」按钮 |

---

### 2.2 `GET /sitter/orders/:id` —— 机构订单详情

> 状态：✅ 2026-11-15 已接。`getReceivedOrder()` + `refreshReceivedOrder()`，详情页进入时刷新。

| 项 | 内容 |
| --- | --- |
| 后端 handler | `GetOrderDetail` |
| 鉴权 | 机构账号 |
| Android | 只调用 `GET /sitter/orders/manage` 列表，以及 `PUT .../accept`、`reject`、`complete` |
| 小程序 | 主人端，没有机构订单 |
| 管理后台 | 走 `/admin/orders/:id`，不是这条 |
| 证据 | `ApiClient.kt:718-733` 中 `/sitter/orders/` 只有 `manage`、`accept`、`reject`、`complete`，没有 GET 详情；列表点进详情只赋 `selectedReceivedOrder`（`DScreensB.kt:304-305`） |

现状：

- 机构端「接单管理」列表点进详情时，直接把列表里的 `ReceivedOrderDto` 赋给 `selectedReceivedOrder`。
- 详情页展示的是列表 DTO，不是后端详情接口返回的完整对象。

影响：

- 详情页字段受列表 DTO 限制；后端详情里如果有额外字段（宠物明细、备注、联系人等），机构端看不到。
- 列表数据过期时，详情页不会主动刷新。
- 后端详情接口成为死接口。

建议：

- 补 `getReceivedOrder(orderId)`，详情页进入时刷新一次；列表 DTO 只做首屏占位。
- 或者确认列表 DTO 已包含详情所需全部字段，删除后端详情路由。

优先级：**中高**。机构接单是核心流程，详情数据完整性比普通展示页更重要。

---

### 2.3 `POST /sitter/shop` —— 机构创建店铺

> 状态：✅ 2026-11-15 已接。`saveMyShop()` 在 `myShop == null` 时走 `createMyShop()`。

| 项 | 内容 |
| --- | --- |
| 后端 handler | `CreateMyShop` |
| 鉴权 | 机构账号 |
| Android | 只有 `GET /sitter/shop/me` 和 `PUT /sitter/shop/me` |
| 小程序 | 主人端，没有机构店铺 |
| 管理后台 | 通过 `POST /admin/sitters` 创建机构，不是这条 |
| 证据 | `ApiClient.kt:617-620` 只有 `getMyShop()` / `updateMyShop()`；没有 `createMyShop()` |

现状：

- App 的机构资料页假设店铺已经存在，直接 `GET`，保存时直接 `PUT`。
- 如果账号刚升级为机构、后台还没建店铺，`GET` 可能 404，`PUT` 也可能失败。

影响：

- 新机构无法在 App 内完成「建店」这一步。
- 后端创建店铺接口闲置。

建议：

- 先确认 `PUT /sitter/shop/me` 是否带「不存在则创建」的语义。
- 如果不带，补 `createMyShop()`，在 `GET` 404 时进入建店表单。
- 如果业务规定只能后台建店，删除 `POST /sitter/shop` 或标注为管理端专用。

优先级：**中**，取决于机构入驻流程是否允许 App 自助建店。

---

### 2.4 `GET /geo/static-map` —— 静态地图

| 项 | 内容 |
| --- | --- |
| 后端 handler | `GeoStaticMap` |
| 鉴权 | 登录用户 |
| Android | 没有页面调用 |
| 小程序 | 没有调用 |
| 管理后台 | 没有调用，后台用高德 JS API |
| 桌面端 | 地址页地图预览会调 `api.fetchStaticMap()` |
| 证据 | `desktopApp/.../AppRoot.kt` 的 `staticMap()` 包装；Android / 小程序 / 管理后台均无调用 |

现状：

- 后端可以按经纬度/缩放/尺寸返回一张静态地图图片。
- Android、小程序与管理后台都没有使用，桌面端用于地址页的地图预览。

影响：

- 静态地图能力只服务桌面端一个入口。

建议：

- 若要收敛，只能连同桌面端预览一起改；Android 侧不需要补。

优先级：**低**，除非产品明确要做分享图/桌面地图。

---

### 2.5 地图选点整条链路（4 条）

这四条是一个完整流程，单独看每条都“没调用”，合起来是「浏览器地图选点」功能整体未接。

#### `POST /geo/pick` —— 创建一次性选点会话

| 项 | 内容 |
| --- | --- |
| 后端 handler | `CreateGeoPick` |
| Android | 无页面调用 |
| 小程序 | 无 |
| 管理后台 | 无 |
| 桌面端 | `AppRoot.kt` 的 `pickOnMap()` 调 `api.createGeoPick()` 打开浏览器选点 |
| 证据 | `desktopApp/.../AppRoot.kt` |

#### `GET /geo/pick-result/:token` —— 轮询选点结果

| 项 | 内容 |
| --- | --- |
| 后端 handler | `PollGeoPick` |
| Android | 无页面调用 |
| 小程序 | 无 |
| 管理后台 | 无 |
| 桌面端 | `AppRoot.kt` 轮询 `api.pollGeoPick(token)` 取回选点结果 |

#### `GET /geo/pick/:token` —— 浏览器选点页

| 项 | 内容 |
| --- | --- |
| 后端 handler | `GeoPickerPage` |
| 说明 | 返回 HTML 页面，凭一次性 token 打开，不带登录态 |
| 前端 | 没有任何前端打开这个 URL |

#### `POST /geo/pick/:token` —— 提交选点结果

| 项 | 内容 |
| --- | --- |
| 后端 handler | `SubmitGeoPick` |
| 说明 | 浏览器选点页提交坐标后，后端写回会话 |
| 前端 | 没有页面会走到这里 |

现状：

- Android 实际使用的是 App 内高德地图选点（`D24MapPicker`），不是这套浏览器选点。
- 桌面端没有内置地图 SDK，走的就是这套浏览器选点：建会话 → 打开浏览器 → 轮询结果。

影响：

- 后端多维护一套带 token 的浏览器选点链路，目前只服务桌面端。
- KMP 侧的 `createGeoPick()` / `pollGeoPick()` 保留给桌面端；Android 不调用。

建议：

- 方案 A：管理后台的地址选点改用这套流程，形成实际使用方。
- 方案 B：确认 App 内选点已覆盖所有端，删除这组路由和 KMP 包装。
- 方案 C：保留为「桌面端预留」，在接口文档标注 `@Reserved`，不要让它看起来像已接入。

优先级：**中**。这是本次审计中最大的一块“整功能未接”。

---

## 3. Android 单独未接（B 类）

以下接口/能力在小程序或后台已经使用，但 Android 没有接。它们不算“全端未调用”，但属于 Android 功能缺口。

> 状态：3.1–3.7 已于 2026-11-15 补齐；3.8 机构价格/排序仍缺页面控件；3.9 `scope=received` 已被 `/sitter/orders/manage` 覆盖。

### 3.1 `GET /pet-owner/orders/:id` —— 订单详情

| 项 | 内容 |
| --- | --- |
| 小程序 | `orderApi.detail(id)` 已调用，见 `uniapp/patnestUniapp/utils/api.js` |
| Android | 没有调用；`listOrdersPage()` 只拉列表，详情页用 `selectedOrder` 或列表刷新后的对象 |
| 证据 | `ApiClient.kt:392-396`；小程序 `uniapp/patnestUniapp/utils/api.js:196-198` |
| 影响 | 后端详情里的额外字段、最新状态无法单独刷新；列表过期时详情也过期 |
| 建议 | 补 `getOrderDetail(orderId)`，详情页进入时刷新；列表对象先做骨架屏 |

### 3.2 `GET /pet-owner/dynamics/:id` —— 动态详情

| 项 | 内容 |
| --- | --- |
| 小程序 | `dynamicApi.detail(id)` 已调用 |
| Android | 没有调用；动态详情用列表 `DynamicDto`，评论另走 `/comments` |
| 证据 | 小程序 `uniapp/patnestUniapp/utils/api.js:241-243`；Android 只有 `listOrderDynamics` / `listOwnerDynamics` |
| 影响 | 动态详情页不能单独刷新正文/点赞数；列表数据过期时详情也过期 |
| 建议 | 补 `getDynamic(dynamicId)`，详情页进入时刷新 |

### 3.3 `GET /sitters/:id` —— 机构详情

| 项 | 内容 |
| --- | --- |
| 小程序 | `sitterApi.detail(id)` 已调用 |
| Android | 没有调用；`selectedSitter` 来自 `/sitters` 列表 |
| 证据 | 小程序 `uniapp/patnestUniapp/utils/api.js:118-120`；Android `ApiClient.kt` 没有 `getSitter(id)` |
| 影响 | 详情页依赖列表 DTO；详情专属字段（如完整图集、完整描述）如果列表没返回，就看不到 |
| 建议 | 进入机构详情时调用一次详情接口；列表对象只做首屏 |

### 3.4 `GET /sitters/search?q=` —— 服务端机构搜索

| 项 | 内容 |
| --- | --- |
| 小程序 | `sitterApi.search(q)` 已调用，服务端按名称/地址/描述 ILIKE |
| Android | 没有调用；`D38SearchResults` 只对已加载的 `store.sitters` 做本地 `contains` |
| 证据 | `DScreensC.kt:853-860` 的本地过滤；`ApiClient.kt:353-362` 的 `searchSitters()` 无页面调用 |
| 影响 | `/sitters` 默认只加载 50 家；第 51 家之后搜不到，搜索体验不完整 |
| 建议 | 搜索框提交时调用 `searchSitters(q)`；本地过滤只作为输入联想 |

证据：`DScreensC.kt` 的 `D38SearchResults` 使用 `store.sitters.filter { ... sitter.name.contains(...) }`，没有触发网络请求。

### 3.5 `POST /pet-owner/account/cancel-withdraw` —— 撤销注销

| 项 | 内容 |
| --- | --- |
| 小程序 | `accountApi.withdrawCancel()` 已调用，注销申请可撤销 |
| Android | `withdrawAccountCancel()` 已定义但无页面调用；设置页只有「注销账号」 |
| 证据 | `ApiClient.kt:341`；`DScreensD.kt:1499-1512` 只有 `requestAccountCancel`；小程序 `utils/api.js:399-401` 有撤销方法 |
| 影响 | App 内只能发起注销，不能撤销；文案写「重新登录可撤销」，依赖后端登录逻辑 |
| 建议 | 在设置页增加「撤销注销申请」入口，或确认登录自动撤销后删除死方法 |

### 3.6 订单服务端筛选 `status_filter`

| 项 | 内容 |
| --- | --- |
| 后端 | `GET /pet-owner/orders` 支持 `status_filter`，可逗号分隔多状态 |
| 小程序 | 订单页把 `status_filter` 传给后端 |
| Android | `listOrdersPage()` 只传 `page` / `per_page`，状态在本地 `orders.filter` |
| 证据 | `ApiClient.kt:392-396`；小程序 `pages/order/list.vue:128` 传 `status_filter` |
| 影响 | 只对已加载页有效；超过一页（50 条）后筛选结果不完整 |
| 建议 | 给 `listOrdersPage` 增加 `statusFilter: String?`，状态 Tab 切换时重新请求第 1 页 |

### 3.7 宠物服务端筛选 `species` / `name`

| 项 | 内容 |
| --- | --- |
| 后端 | `GET /pet-owner/pets` 支持 `species`、`name` |
| 小程序 | 宠物列表把 `species` 传给后端 |
| Android | `listPetsPage()` 只传分页，宠物类型在本地过滤 |
| 证据 | `ApiClient.kt:366-371`；小程序 `pages/pet/list.vue:43-45` 传 `species` |
| 影响 | 只对已加载页有效；宠物超过一页后筛选不完整 |
| 建议 | 给 `listPetsPage` 增加 `species` / `name` 参数 |

### 3.8 机构服务端筛选 `price_min` / `price_max` / `sort`

> 状态：价格区间已接（`¥100 以下 / ¥100-200 / ¥200 以上`），排序按钮已接推荐/价格升/价格降。

| 项 | 内容 |
| --- | --- |
| 后端 | `GET /sitters` 支持 `type`、`price_min`、`price_max`、`sort` |
| 小程序 | 首页筛选把价格/排序传给后端 |
| Android | 已传 `price_min` / `price_max` / `sort` |
| 证据 | `ApiClient.kt` 的 `listSitters(type, priceMin, priceMax, sort)`；小程序 `pages/index/index.vue:104-105` 传价格/排序参数 |
| 影响 | 已全量筛；价格排序由服务端返回顺序 |
| 建议 | `listSitters` 增加价格区间和 `sort` 参数；本地排序保留作为兜底 |

### 3.9 `scope=received` 参数

| 项 | 内容 |
| --- | --- |
| 后端 | `GET /pet-owner/orders` 支持 `scope=mine|received`，机构账号可看接单 |
| 前端 | 没有前端使用 `scope=received`；Android 机构端改走 `/sitter/orders/manage` |
| 判定 | 功能已被 `/sitter/orders/manage` 覆盖，参数本身闲置 |
| 建议 | 保留兼容即可，不需要补页面；接口文档标注「旧口径」 |

---

## 4. 参数级未使用汇总

| 接口 | 后端支持 | 小程序 | Android | 影响 |
| --- | --- | --- | --- | --- |
| `GET /pet-owner/orders` | `status_filter`、`scope` | 用 `status_filter` | 只用分页 | 状态筛选只对已加载页有效 |
| `GET /pet-owner/pets` | `species`、`name` | 用 `species` | 只用分页 | 宠物筛选只对已加载页有效 |
| `GET /sitters` | `price_min`、`price_max`、`sort` | 用价格/排序 | 只用 `type` | 价格/排序只对前 50 家有效 |
| `GET /sitters/search` | `q` | 已用 | 未用 | 搜索只覆盖已加载数据 |
| `GET /pet-owner/orders` | `scope=received` | 未用 | 未用（走 sitter 路由） | 旧口径，被新路由覆盖 |
| `GET /sitter/orders/manage` | `page`、`per_page` | 无机构端 | 已用 | 正常 |

---

## 5. 重复别名（C 类，不算功能缺失）

以下 5 条是公开 `/sitters...` 的登录态别名，三个前端都没有直接调用；实际功能通过公开路由完成：

| 别名 | 对应公开路由 | 使用方 |
| --- | --- | --- |
| `GET /pet-owner/sitters` | `GET /sitters` | Android、小程序 |
| `GET /pet-owner/sitters/search` | `GET /sitters/search` | 小程序 |
| `GET /pet-owner/sitters/:id` | `GET /sitters/:id` | 小程序 |
| `GET /pet-owner/sitters/:id/capacity` | `GET /sitters/:id/capacity` | Android、小程序 |
| `GET /pet-owner/sitters/:id/addons` | `GET /sitters/:id/addons` | Android、小程序 |

注意：`GET /pet-owner/sitters/:id/estimate` **不是别名**，它是下单前费用预估的独立接口，Android 和小程序都在用，不能删除。

建议：

- 保留别名做兼容即可。
- 如果确定没有旧客户端，可删除，减少路由表噪音。
- 接口文档里标注「公开路由的登录态别名，新客户端优先用公开路由」。

---

## 6. 基础设施路由（D 类，不是功能缺失）

| 路由 | 用途 | 为什么不算缺口 |
| --- | --- | --- |
| `GET /health` | 发布门禁、负载均衡健康检查、后台版本提示 | 由运维/网关调用，不是用户页面 |
| `POST /payments/recharge/notify/:channel` | 微信/支付宝充值异步回调 | 由支付网关调用，前端不能也不应该调用 |

---

## 7. 已定义但没页面调用的包装（死代码）

### 7.1 Android

| 方法 | 对应接口 | 说明 |
| --- | --- | --- |
| `withdrawAccountCancel()` | `POST /pet-owner/account/cancel-withdraw` | 设置页没有撤销入口 |
| `searchSitters()` | `GET /sitters/search` | 搜索页只做本地过滤 |
| `listPets()` | `GET /pet-owner/pets` | 旧非分页包装，UI 已改用 `listPetsPage()` |
| `listOrders()` | `GET /pet-owner/orders` | 旧非分页包装，UI 已改用 `listOrdersPage()` |
| `listReceivedOrders()` | `GET /sitter/orders/manage` | 旧非分页包装，UI 已改用 `listReceivedOrdersPage()` |

### 7.2 小程序 / 后台

- 小程序 `authApi.refresh()` 是请求层内部使用，不算死代码。
- 后台 `adminApi` 未发现无页面调用的方法。

---

## 8. 覆盖统计

| 模块 | 路由数 | 前端覆盖情况 |
| --- | ---: | --- |
| 管理后台 `/admin` | 92 | `adminApi` 方法均有页面调用，未发现缺口 |
| 主人端 `/pet-owner` | 52 | 大部分覆盖；缺口见第 3 节 |
| 机构端 `/sitter` | 34 | 已覆盖：订单详情、建店已补；加购删除实际是下架，由 toggle 覆盖 |
| 公开机构 `/sitters` | 6 | 覆盖 |
| 地理 `/geo` | 9 | 静态地图 + 浏览器选点 5 条 Android 未接（桌面端在用其中 3 条） |
| 认证 `/auth` | 8 | 覆盖 |
| 工单 `/support` | 4 | 覆盖 |
| 其他（健康检查、版本、支付回调、上传、旧通知） | 7 | 健康检查/回调为基础设施；旧通知仍用于未读角标 |

> 注：一条路由可能同时被多个前端使用，上表是路由归属统计，不是调用次数。

---

## 9. 建议处理顺序

| 优先级 | 事项 | 理由 |
| --- | --- | --- |
| P1 | 机构订单详情 | 接单核心流程，避免详情只靠列表 DTO |
| P1 | Android 服务端搜索/筛选 | 修掉“超过 50 条搜不到/筛不全”的实际问题 |
| P2 | 机构建店 | 取决于是否允许 App 自助入驻 |
| P2 | 撤销注销入口 | 小程序已有，Android 缺一个入口 |
| P3 | 地图选点/静态地图 | 整条链路未接，先确认产品是否还需要 |
| P3 | 清理兼容别名/死包装 | 不影响功能，减少维护面 |

---

## 10. 复现方法

1. 从 `router.go` 提取所有 `GET/POST/PUT/DELETE` 路由，展开 `v1`、`owner`、`sitter`、`admin` 分组前缀。
2. 从以下文件提取方法 + 路径：
   - `app/patnestApp/shared/src/commonMain/.../ApiClient.kt`
   - `uniapp/patnestUniapp/utils/api.js`
   - `webadmin/patnestServerAdminWeb/src/api/index.js`
3. 归一化动态段：`:id`、`$id`、`${id}` → `*`。
4. 排除 `uniapp/unpackage/dist`、`node_modules`。
5. 对每条路由检查：
   - 是否有前端路径匹配；
   - 方法是否一致；
   - 动态段是否能被字符串拼接覆盖；
   - 页面是否真的调用对应 API 方法，而不只是 API 文件里定义了。
6. 对“接口调用了但参数没传”的情况，单独检查 handler 的 `c.Query(...)`。

---

## 11. 不作为缺口的路由（避免误删）

以下路由容易被误判为“没调用”，实际有使用或属于基础设施：

- `GET /notifications`、`PUT /notifications/:id/read`、`PUT /notifications/read-all`
  - Android `NotificationCenter` 仍用它们维护未读角标；按视角拆分的 `/pet-owner/notifications`、`/sitter/notifications` 也在用。
- `GET /pet-owner/sitters/:id/estimate`
  - 下单前费用预估，Android 和小程序都在用，不是别名。
- `GET /sitters/:id/capacity`、`GET /sitters/:id/addons`、`GET /sitters/:id/reviews`
  - 公开详情页在用。
- `POST /upload/image`
  - Android、小程序、后台都在上传图片。
- `GET /geo/tile`
  - Android `MapTiles` 在用，虽然它是通过 `getImage()` 而不是普通 `getData()` 调用的。
- `GET /geo/reverse`、`GET /geo/districts`、`POST /geo/geocode`
  - 定位/地址解析链路在用。

---

## 12. 2026-11-15 首页定位推荐

本轮把 Android 首页补成和 uniapp 首页一致的「定位推荐」：

- 后端 `GET /sitters` 新增 `latitude` / `longitude`，`sort=distance` 时按 Haversine 距离排序，没有坐标的机构排最后
- Android 首页先出综合推荐；已授权定位时静默取当前位置，反查城市/区县后重新按距离拉推荐
- 首页增加定位胶囊：显示「城市 · 区县 · 附近」，点击可重新定位
- 推荐卡片显示距离（1 公里内报米，以上报公里）
- 未授权/定位失败继续按综合推荐，不阻塞首页
- 浏览器选点/静态地图仍按预留处理，管理端用高德 JS、Android 用高德 SDK

## 13. 最终结论

- **本轮已补**：机构订单详情、机构建店、Android 订单/动态/机构详情刷新、服务端机构搜索、撤销注销、订单状态筛选、宠物物种筛选。
- **仍需产品确认**：浏览器地图选点、静态地图。
- **仍需补**：无（地图类按预留处理）。
- **只需要清理**：`/pet-owner/sitters...` 别名、Android 死包装、旧非分页方法。
- **管理后台没有发现接口未接**。
