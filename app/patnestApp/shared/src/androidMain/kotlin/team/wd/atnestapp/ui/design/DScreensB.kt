package team.wd.atnestapp.ui.design

import team.wd.atnestapp.utils.formatScore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.GeocodeDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.SitterCouponPayload
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.app.MobileImage
import team.wd.atnestapp.ui.app.rememberPhotoPicker
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/* ================================================================== *
 * 第 2 组：⑯~㉚ 屏。版式按 docs/KMP-Android端UI设计稿.html 还原，
 * 数据已接真实接口：统一从 DLocalStore.current（MobileState）读取。
 * 本组私有数据与 helper 统一 B_ 前缀，避免与其它分组文件重名。
 * ================================================================== */

/* ⑯ 地址管理（后端没有独立地址簿接口，常用地址随个人资料保存） */
@Composable
fun D16Addresses(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) { store.loadProfile() }
    val profile = store.profile
    val address = profile?.address.orEmpty()
    val contact = profile?.contactName?.takeIf { it.isNotBlank() }
        ?: store.nickname.ifBlank { store.account }
    val phone = dMaskPhone(
        profile?.contactPhone?.takeIf { it.isNotBlank() } ?: profile?.phone,
    )
    DFrame(nav = nav, title = "地址管理") {
        DPad {
            Spacer(Modifier.height(14.dp))
            if (address.isBlank()) {
                DEmpty("还没有填写常用地址", "去填写") { nav.go(DScreen.ProfileEdit) }
            } else {
                DCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(contact, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                        Spacer(Modifier.width(8.dp))
                        Text(phone, fontSize = 12.5.sp, color = DColor.Ink3)
                        Spacer(Modifier.weight(1f))
                        DTag("默认", DTagTone.Ok)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(address, fontSize = 12.5.sp, color = DColor.Ink2)
                    Spacer(Modifier.height(12.dp))
                    B_TextLink("编辑", DColor.BrandInk) { nav.go(DScreen.ProfileEdit) }
                }
            }
            Text(
                "联系人、手机号与地址保存在个人资料里，修改后下单会自动带上。",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
            Spacer(Modifier.height(14.dp))
            DButton(
                "+ 新增地址",
                { nav.go(DScreen.AddressAdd) },
                style = DButtonStyle.Outline,
                height = 44.dp,
            )
        }
    }
}

/* ⑰ 设置 */
@Composable
fun D17Settings(nav: DNav) {
    val store = DLocalStore.current
    DFrame(nav = nav, title = "设置") {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard(padding = 0.dp) {
                DInfoRow("当前账号", store.nickname.ifBlank { store.account }.ifBlank { "未登录" })
                DDivider()
                DMenuRow(
                    "身份",
                    store.identityLabel,
                    onClick = { nav.go(DScreen.RoleSwitch) },
                )
                DDivider()
                DMenuRow("账号与安全", onClick = { nav.go(DScreen.SettingsDetail) })
                DDivider()
                DMenuRow("个人资料", onClick = { nav.go(DScreen.ProfileEdit) })
                DDivider()
                DInfoRow("关于宠栖", "v1.0.0")
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "退出登录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DColor.Danger,
                    modifier = Modifier.clickable {
                        store.logout()
                        nav.tab(DTab.Home)
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("宠栖 PetNest · KMP Android v1.0", fontSize = 11.sp, color = DColor.Ink3)
            }
        }
    }
}

/* ⑱ 机构端（手机接单 / 动态） */
@Composable
fun D18SitterWorkspace(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.loadMyShop()
        store.loadReceivedOrders()
        store.loadSitterStats()
    }
    // 待确认 / 进行中两组订单会被首页统计、列表、空态多处读取，
    // 按数据源缓存一次，避免每次重组都重新扫一遍订单列表。
    val pending = remember(store.receivedOrders) {
        store.receivedOrders.filter { it.status == "confirmed" }
    }
    val ongoing = remember(store.receivedOrders) {
        store.receivedOrders.filter { it.status == "accepted" || it.status == "staying" }
    }
    val shop = store.myShop
    // 机构身份下的「首页」：底部导航换成工作台 / 订单 / 动态 / 我的这一套
    DFrame(nav = nav, title = null, activeTab = DTab.SitterHome, backable = false, refreshing = store.shopLoading, onRefresh = { scope.launch { store.loadMyShop(force = true); store.loadSitterStats() } }) {
        Column(
            Modifier.fillMaxWidth().background(DColor.HeroGradient)
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shop?.name?.takeIf { it.isNotBlank() } ?: "机构工作台",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DColor.White,
                    // 店名过长时截断，别把右边的状态和「切换身份」挤出去
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(DShape.Pill).background(DColor.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (shop == null) "未入驻" else if (shop.status == "active") "营业中" else "待审核",
                            fontSize = 10.5.sp,
                            color = DColor.White,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 机构端是独立工作台，没有底部导航；不给入口就出不去了（真实产品也是
                    // 在商家版头像 / 店名旁边放「切换身份」）
                    Box(
                        Modifier.clip(DShape.Pill).background(DColor.White.copy(alpha = 0.2f))
                            .clickable { nav.go(DScreen.RoleSwitch) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text("切换身份", fontSize = 10.5.sp, color = DColor.White)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                B_HeroStat(store.sitterStats?.pending?.toString() ?: pending.size.toString(), "待确认")
                B_HeroStat(store.sitterStats?.active?.toString() ?: ongoing.size.toString(), "寄养中")
                B_HeroStat(store.money(store.sitterStats?.todayRevenue ?: 0.0), "今日收入")
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                B_HeroAction("容量", Modifier.weight(1f)) { nav.go(DScreen.Capacity) }
                B_HeroAction("动态", Modifier.weight(1f)) { nav.go(DScreen.PublishDynamic) }
                B_HeroAction("收益", Modifier.weight(1f)) { nav.go(DScreen.SitterIncome) }
                B_HeroAction("看板", Modifier.weight(1f)) { nav.go(DScreen.SitterDashboard) }
                B_HeroAction("评价", Modifier.weight(1f)) { nav.go(DScreen.SitterReviews) }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("待确认订单", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                Spacer(Modifier.width(8.dp))
                DTag(pending.size.toString(), DTagTone.Warn)
            }
            Spacer(Modifier.height(10.dp))
            when {
                store.receivedOrdersLoading -> DLoading()
                pending.isEmpty() -> DEmpty("暂无待确认订单")
                else -> pending.forEach { order ->
                    B_OrderCard(
                        title = "${order.ownerName.ifBlank { "宠物主人" }} · ${order.petCount} 只",
                        tag = "待确认",
                        tone = DTagTone.Warn,
                        lines = listOf(
                            "${order.startDate} - ${order.endDate} · ${order.totalDays} 天",
                            "${store.money(order.totalAmount)} · 单号 ${order.orderNo}",
                        ),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DButton("拒绝", {
                                scope.launch { store.rejectOrder(order, "") }
                            }, Modifier.weight(1f), DButtonStyle.Ghost, 38.dp)
                            DButton("确认接单", {
                                scope.launch { store.acceptOrder(order) }
                            }, Modifier.weight(1f), height = 38.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("进行中寄养", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Spacer(Modifier.height(10.dp))
            if (ongoing.isEmpty()) {
                DEmpty("暂无进行中的寄养")
            } else {
                ongoing.forEach { order ->
                    B_OrderCard(
                        title = "${order.ownerName.ifBlank { "宠物主人" }} · ${order.petCount} 只",
                        tag = store.sitterStatusText(order.status),
                        tone = DTagTone.Ok,
                        lines = listOf(
                            "${order.startDate} - ${order.endDate} · ${order.totalDays} 天",
                            "${store.money(order.totalAmount)} · 单号 ${order.orderNo}",
                        ),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DButton("查看详情", {
                                store.selectedReceivedOrder = order
                                nav.go(DScreen.SitterOrderDetail)
                            }, Modifier.weight(1f), DButtonStyle.Ghost, 38.dp)
                            DButton("发布动态", {
                                store.selectedOrderIdForDynamic = order.id
                                nav.go(DScreen.PublishDynamic)
                            }, Modifier.weight(1f), height = 38.dp)
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 机构端主流程：四个一级页，只在机构身份下出现。
 *
 * 设计稿只画了主人端（① ② ⑥ ⑦），这四屏按真实商家版的信息结构补：
 * 工作台看今天要做什么、订单管接单履约、动态对外发内容、我的管店铺和钱。
 * 和主人端那四个 Tab 是两套完全独立的页面，不是同一页换文案。
 * ------------------------------------------------------------------ */

/* 机构端 ② 接单管理 */
@Composable
fun DSitterOrders(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.loadReceivedOrders()
        store.loadSitterStats()
    }

    val orders = store.receivedOrders
    val pending = remember(orders) { orders.filter { it.status == "confirmed" } }
    val ongoing = remember(orders) {
        orders.filter { it.status == "accepted" || it.status == "staying" }
    }
    var filter by remember { mutableStateOf("全部") }
    val shown = remember(orders, filter) {
        when (filter) {
            "待确认" -> pending
            "进行中" -> ongoing
            "已完成" -> orders.filter { it.status == "completed" || it.status == "evaluated" }
            else -> orders
        }
    }

    DFrame(nav = nav, title = "接单管理", activeTab = DTab.SitterOrders, refreshing = store.receivedOrdersLoading, onRefresh = { scope.launch { store.loadReceivedOrders(force = true) } }) {
        DPad {
            Spacer(Modifier.height(12.dp))
            DCard {
                Row(Modifier.fillMaxWidth()) {
                    B_MiniStat(pending.size.toString(), "待确认", DColor.Warn, Modifier.weight(1f))
                    B_MiniStat(ongoing.size.toString(), "进行中", DColor.BrandInk, Modifier.weight(1f))
                    B_MiniStat(
                        store.money(store.sitterStats?.todayRevenue ?: 0.0),
                        "今日收入",
                        DColor.Ok,
                        Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "待确认", "进行中", "已完成").forEach { item ->
                    DChip(item, item == filter) { filter = item }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                store.receivedOrdersLoading && orders.isEmpty() -> DLoading("正在加载订单…")
                shown.isEmpty() -> DEmpty(
                    if (filter == "全部") "还没有收到订单" else "没有「$filter」的订单",
                    "刷新",
                ) { scope.launch { store.loadReceivedOrders(force = true) } }

                else -> DGrid(shown, minItemWidth = 360.dp) { order ->
                    B_ReceivedOrderCard(
                        order = order,
                        onAccept = { scope.launch { store.acceptOrder(order) } },
                        onReject = { scope.launch { store.rejectOrder(order, "") } },
                        onDetail = {
                            store.selectedReceivedOrder = order
                            nav.go(DScreen.SitterOrderDetail)
                        },
                        onPublish = {
                            store.selectedOrderIdForDynamic = order.id
                            nav.go(DScreen.PublishDynamic)
                        },
                    )
                }
            }
            if (shown.isNotEmpty() && store.receivedOrdersHasMore) {
                Spacer(Modifier.height(10.dp))
                DButton(
                    if (store.receivedOrdersLoadingMore) "正在加载…" else "加载更多订单",
                    { scope.launch { store.loadMoreReceivedOrders() } },
                    style = DButtonStyle.Outline,
                    height = 40.dp,
                    enabled = !store.receivedOrdersLoadingMore,
                )
            }
        }
    }
}

/* 机构端 ③ 机构动态（对外展示的内容，主人端看得到） */
@Composable
fun DSitterMoments(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadSitterDynamics() }
    DFrame(
        nav = nav,
        title = "机构动态",
        activeTab = DTab.SitterMoments,
        actions = { DSmallButton("+ 发布", { nav.go(DScreen.PublishDynamic) }) },
    ) {
        if (store.sitterDynamics.isEmpty()) {
            DEmpty("还没有发布过动态", "去发布一条") { nav.go(DScreen.PublishDynamic) }
            return@DFrame
        }
        DPad {
            Spacer(Modifier.height(12.dp))
            Text(
                "动态会展示在机构详情页，主人挑选机构时最先看到这些内容。",
                fontSize = 11.5.sp,
                color = DColor.Ink3,
            )
            Spacer(Modifier.height(12.dp))
            store.sitterDynamics.forEach { dynamic ->
                DCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dynamic.createdAt.replace("T", " ").take(16),
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                        )
                        Spacer(Modifier.weight(1f))
                        if (dynamic.orderNo.isNotBlank()) DTag(dynamic.orderNo, DTagTone.Line)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (dynamic.photos.isNotEmpty()) {
                        DImageUrl(dynamic.photos.first(), "", height = 160.dp)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(dynamic.content, fontSize = 13.sp, color = DColor.Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("共 ${dynamic.photos.size} 张图", fontSize = 11.sp, color = DColor.Ink3)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "删除",
                            fontSize = 11.5.sp,
                            color = DColor.Danger,
                            modifier = Modifier.clickable {
                                scope.launch { store.deleteDynamic(dynamic) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/* 机构端 ④ 机构我的（店铺、经营、账号） */
@Composable
fun DSitterProfile(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) {
        store.loadMyShop()
        store.loadSitterStats()
        store.loadReceivedOrders()
    }
    val shop = store.myShop
    val orders = store.receivedOrders
    val pending = remember(orders) { orders.count { it.status == "confirmed" } }
    val ongoing = remember(orders) {
        orders.count { it.status == "accepted" || it.status == "staying" }
    }
    DFrame(nav = nav, title = null, activeTab = DTab.SitterProfile, backable = false) {
        Column(
            Modifier.fillMaxWidth().background(DColor.HeroGradient)
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(DColor.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dInitial(shop?.name ?: store.nickname),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.BrandInk,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        shop?.name?.takeIf { it.isNotBlank() } ?: "机构工作台",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.White,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        shop?.address?.takeIf { it.isNotBlank() } ?: "还没有填写店铺地址",
                        fontSize = 12.sp,
                        color = DColor.White.copy(alpha = 0.9f),
                    )
                }
                Box(
                    Modifier.clip(DShape.Md).background(DColor.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        when {
                            shop == null -> "未入驻"
                            shop.status == "active" -> "营业中"
                            else -> "待审核"
                        },
                        fontSize = 11.5.sp,
                        color = DColor.White,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                B_HeroStat(pending.toString(), "待确认")
                B_HeroStat(ongoing.toString(), "寄养中")
                B_HeroStat(store.money(store.sitterStats?.todayRevenue ?: 0.0), "今日收入")
            }
        }
        DSection("店铺管理")
        DPad {
            DCard(padding = 0.dp) {
                DMenuRow("店铺资料", "封面 · 价格 · 地址", onClick = { nav.go(DScreen.ShopProfile) })
                DDivider()
                DMenuRow("容量日历", onClick = { nav.go(DScreen.Capacity) })
                DDivider()
                DMenuRow("加购服务", onClick = { nav.go(DScreen.Addons) })
                DDivider()
                DMenuRow("节假日定价", onClick = { nav.go(DScreen.HolidayRules) })
            }
        }
        DSection("经营数据")
        DPad {
            DCard(padding = 0.dp) {
                DMenuRow("收益与结算", onClick = { nav.go(DScreen.SitterIncome) })
                DDivider()
                DMenuRow("数据看板", onClick = { nav.go(DScreen.SitterDashboard) })
                DDivider()
                DMenuRow("评价管理", onClick = { nav.go(DScreen.SitterReviews) })
                DDivider()
                DMenuRow("优惠券管理", onClick = { nav.go(DScreen.SitterCoupons) })
            }
        }
        DSection("账号")
        DPad {
            DCard(padding = 0.dp) {
                DMenuRow("当前身份", store.identityLabel, onClick = { nav.go(DScreen.RoleSwitch) })
                DDivider()
                DMenuRow("账号与安全", onClick = { nav.go(DScreen.SettingsDetail) })
                DDivider()
                DMenuRow("联系客服", onClick = { nav.go(DScreen.Support) })
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "退出登录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DColor.Danger,
                    modifier = Modifier.clickable {
                        store.logout()
                        nav.resetStack(DScreen.Home)
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("宠栖 PetNest · 机构端 v1.0", fontSize = 11.sp, color = DColor.Ink3)
            }
        }
    }
}

/** 机构端的三格统计（比首页 hero 里的小一档，放在卡片内） */
@Composable
private fun B_MiniStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = DColor.Ink3)
    }
}

/**
 * 机构收到的一单。按钮按状态给：
 * 待确认只能接 / 拒，进行中看详情和发动态，结束后只剩详情。
 */
@Composable
private fun B_ReceivedOrderCard(
    order: ReceivedOrderDto,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDetail: () -> Unit,
    onPublish: () -> Unit,
) {
    val store = DLocalStore.current
    val open = order.status == "confirmed"
    val ongoing = order.status == "accepted" || order.status == "staying"
    B_OrderCard(
        title = "${order.ownerName.ifBlank { "宠物主人" }} · ${order.petCount} 只",
        tag = when {
            open -> "待确认"
            ongoing -> "寄养中"
            order.status == "completed" || order.status == "evaluated" -> "已完成"
            else -> "已关闭"
        },
        tone = when {
            open -> DTagTone.Warn
            ongoing -> DTagTone.Ok
            else -> DTagTone.Line
        },
        lines = listOf(
            "${order.startDate} - ${order.endDate} · ${order.totalDays} 天",
            "单号 ${order.orderNo}",
            "订单金额 " + store.money(order.totalAmount),
        ),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open) {
                DButton("拒绝", onReject, Modifier.weight(1f), DButtonStyle.Ghost, 38.dp)
                DButton("确认接单", onAccept, Modifier.weight(1f), height = 38.dp)
            } else if (ongoing) {
                DButton("查看详情", onDetail, Modifier.weight(1f), DButtonStyle.Ghost, 38.dp)
                DButton("发布动态", onPublish, Modifier.weight(1f), height = 38.dp)
            } else {
                DButton("查看详情", onDetail, Modifier.weight(1f), DButtonStyle.Ghost, 38.dp)
            }
        }
    }
}

/* ⑲ 订单详情（时间线 + 改期/续费/退款） */
@Composable
fun D19OrderDetail(nav: DNav) {
    val store = DLocalStore.current
    val order = store.selectedOrder
    if (order == null) {
        DFrame(nav = nav, title = "订单详情") {
            DEmpty("还没有选择订单", "查看订单") { nav.go(DScreen.Orders) }
        }
        return
    }
    val finished = order.rawStatus in setOf("completed", "evaluated", "cancelled", "rejected")
    DFrame(
        nav = nav,
        title = "订单详情",
        actions = { DTag(order.status, dOrderTone(order.rawStatus)) },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().clip(DShape.Lg)
                    .background(Brush.linearGradient(listOf(DColor.BrandSoft, DColor.White)))
                    .border(1.dp, DColor.Line, DShape.Lg)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).clip(DShape.Md).background(DColor.CoverGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Shop, Modifier.size(24.dp), DColor.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(order.org, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                        Text("${order.pet} · ${order.range}", fontSize = 11.5.sp, color = DColor.Ink3)
                        Spacer(Modifier.height(2.dp))
                        Text("订单号 ${order.no}", fontSize = 11.5.sp, color = DColor.Ink3)
                    }
                }
            }
        }
        DSection("寄养进度")
        DPad {
            DCard {
                DTimelineNode("已下单", order.made.ifBlank { order.range }, DNodeState.Done)
                DTimelineNode(
                    if (order.paid) "已支付" else "待支付",
                    if (order.paid) "支付完成，等待机构确认" else "请在 30 分钟内完成支付",
                    if (order.paid) DNodeState.Done else DNodeState.Current,
                )
                DTimelineNode(
                    when (order.rawStatus) {
                        "accepted" -> "机构已接单"
                        "staying" -> "寄养中"
                        "completed", "evaluated" -> "寄养结束"
                        "cancelled" -> "订单已取消"
                        "rejected" -> "机构已拒绝"
                        else -> "等待机构确认"
                    },
                    "${order.startDate} - ${order.endDate}",
                    if (finished) DNodeState.Done else DNodeState.Todo,
                    last = true,
                )
            }
        }
        DSection("费用明细")
        DPad {
            DCard {
                DKeyValue("订单金额", order.fee)
                DKeyValue("支付状态", if (order.paid) "已支付" else "未支付")
                DKeyValue("寄养天数", order.days)
                DDivider()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("实付", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
                    Spacer(Modifier.weight(1f))
                    Text(order.fee, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DColor.BrandInk)
                }
            }
        }
        DPad {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.rawStatus == "pending_pay") {
                    DButton("去支付", { nav.go(DScreen.Checkout) }, Modifier.weight(1f), DButtonStyle.Ghost, 40.dp)
                }
                if (order.rawStatus in setOf("pending_pay", "confirmed", "accepted")) {
                    DButton("改期", { nav.go(DScreen.OrderReschedule) }, Modifier.weight(1f), DButtonStyle.Ghost, 40.dp)
                }
                if (order.paid && order.rawStatus in setOf("accepted", "staying")) {
                    DButton("续费", { nav.go(DScreen.OrderExtend) }, Modifier.weight(1f), DButtonStyle.Ghost, 40.dp)
                }
                if (order.rawStatus == "completed") {
                    DButton("去评价", { nav.go(DScreen.ReviewWrite) }, Modifier.weight(1f), DButtonStyle.Ghost, 40.dp)
                }
                DButton("售后", { nav.go(DScreen.Tickets) }, Modifier.weight(1f), DButtonStyle.Ghost, 40.dp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.rawStatus in setOf("pending_pay", "confirmed", "accepted")) {
                    DButton("取消订单", { nav.go(DScreen.OrderCancel) }, Modifier.weight(1f), DButtonStyle.Outline, 40.dp)
                }
                if (order.paid) {
                    DButton("申请退款", { nav.go(DScreen.RefundProgress) }, Modifier.weight(1f), DButtonStyle.Outline, 40.dp)
                }
            }
        }
    }
}

@Composable
fun D20Tickets(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("订单问题") }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { store.loadTickets() }
    DFrame(nav = nav, title = "售后工单") {
        if (store.tickets.isEmpty()) {
            DPad {
                Spacer(Modifier.height(16.dp))
                DEmpty("还没有提交过工单")
            }
        } else {
            DPad {
                Spacer(Modifier.height(14.dp))
                DGrid(store.tickets, minItemWidth = 360.dp) { ticket ->
                    B_OrderCard(
                        title = ticket.title.ifBlank { ticket.category },
                        tag = when (ticket.status) {
                            "open" -> "处理中"
                            "resolved", "closed" -> "已解决"
                            else -> ticket.status
                        },
                        tone = if (ticket.status == "open") DTagTone.Warn else DTagTone.Ok,
                        lines = listOf(
                            "分类：${ticket.category}",
                            "工单号 ${ticket.ticketNo}",
                            ticket.createdAt.replace("T", " ").take(16),
                        ),
                        modifier = Modifier.clickable {
                            store.selectedTicketId = ticket.id
                            nav.go(DScreen.TicketChat)
                        },
                        bottom = {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "查看对话与回复 ›",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = DColor.BrandInk,
                            )
                        },
                    )
                }
            }
        }
        DSection("提交新工单")
        DPad {
            DCard {
                B_SelectField("问题分类", category, listOf("订单问题", "账户问题", "服务投诉", "其他")) {
                    category = it
                }
                DFieldInput("标题", title, { title = it }, placeholder = "例如：寄养期间突发状况")
                DFieldInput(
                    label = "详细描述",
                    value = detail,
                    onValueChange = { detail = it },
                    placeholder = "请描述您遇到的问题，客服会在 24 小时内回复…",
                    multiline = true,
                )
                DButton(
                    if (store.busy) "提交中…" else "提交工单",
                    {
                        if (title.isBlank()) {
                            nav.toast("请填写工单标题")
                        } else {
                            scope.launch {
                                store.submitTicket(title.trim(), detail.trim(), category)
                                store.loadTickets()
                                title = ""
                                detail = ""
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 工单会话（设计稿没有独立一屏，从「售后工单」列表点进来）。
 *
 * 列表只回标题和状态，完整往返消息要单独取，所以进页面才拉详情。
 */
@Composable
fun DTicketChat(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    val ticketId = store.selectedTicketId
    var reply by remember { mutableStateOf("") }

    LaunchedEffect(ticketId) { store.openTicket(ticketId) }

    // 只认当前这张单：从 A 返回立刻点 B 时，数据层里可能还留着 A 的会话，
    // 不过滤会先闪一帧别人的对话。用 id 比对比进页面就清空更稳（退场动画也不会闪成空态）。
    val detail = store.ticketDetail?.takeIf { it.id == ticketId }
    val closed = detail != null && (detail.status == "closed" || detail.status == "resolved")
    val send: () -> Unit = {
        scope.launch {
            if (store.replyTicket(ticketId, reply)) reply = ""
        }
    }

    val bottomBar: (@Composable () -> Unit)? = if (detail != null && !closed) {
        {
            Row(
                Modifier.fillMaxWidth().background(DColor.Card)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    DFieldInput(
                        label = "继续追问",
                        value = reply,
                        onValueChange = { reply = it },
                        placeholder = "补充说明或追问…",
                        multiline = true,
                        enabled = !store.ticketReplying,
                    )
                }
                Spacer(Modifier.width(10.dp))
                DButton(
                    if (store.ticketReplying) "发送中" else "发送",
                    send,
                    Modifier.width(84.dp),
                    DButtonStyle.Primary,
                    44.dp,
                    !store.ticketReplying,
                )
            }
        }
    } else null

    DFrame(
        nav = nav,
        title = "工单会话",
        actions = {
            detail?.let {
                DTag(
                    if (closed) "已解决" else "处理中",
                    if (closed) DTagTone.Ok else DTagTone.Warn,
                )
            }
        },
        bottomBar = bottomBar,
    ) {
        when {
            store.ticketLoading && detail == null -> DLoading("正在加载对话…")

            detail == null -> DPad {
                Spacer(Modifier.height(24.dp))
                DEmpty("工单不存在或已被删除", "返回") { nav.back() }
            }

            else -> {
                DPad {
                    Spacer(Modifier.height(14.dp))
                    DCard {
                        DKeyValue("标题", detail.title.ifBlank { "—" })
                        DKeyValue("工单号", detail.ticketNo.ifBlank { "—" })
                        DKeyValue("状态", if (closed) "已解决" else "处理中")
                    }
                }
                DSection("对话 ${detail.messages.size} 条")
                detail.messages.forEach { message ->
                    B_MessageBubble(
                        text = message.content,
                        time = message.createdAt.replace("T", " ").take(16),
                        fromMe = message.senderRole != "admin",
                    )
                }
                if (closed) {
                    DPad {
                        Spacer(Modifier.height(6.dp))
                        DNotice("工单已解决。如需继续咨询，请重新提交一张新工单。")
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun D21Points(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.loadPoints() }
    val balance = store.pointsBalance
    val gained = store.pointsHistory.filter { it.change > 0 }.sumOf { it.change }
    val spent = store.pointsHistory.filter { it.change < 0 }.sumOf { -it.change }
    DFrame(nav = nav, title = null, backable = false, refreshing = refreshing, onRefresh = { refreshing = true; scope.launch { store.loadPoints(); refreshing = false } }) {
        Column(
            Modifier.fillMaxWidth().clip(B_HeroBottomShape).background(DColor.HeroGradient)
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 22.dp),
        ) {
            Text("我的积分", fontSize = 12.sp, color = DColor.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    balance.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DColor.White,
                )
                Spacer(Modifier.width(6.dp))
                Text("分", fontSize = 13.sp, color = DColor.White.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "100 积分 = 1 元 · 下单时可抵扣 ${store.money(balance / 100.0)}",
                fontSize = 12.sp,
                color = DColor.White.copy(alpha = 0.85f),
            )
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    B_Stat(store.money(balance / 100.0), "可抵扣", DColor.BrandInk)
                    B_Stat("+$gained", "累计获取")
                    B_Stat("-$spent", "累计抵扣")
                }
            }
        }
        DSection("积分明细", more = "查看全部 ›", onMore = { nav.go(DScreen.PointsLedger) })
        DPad {
            DCard {
                if (store.pointsHistory.isEmpty()) {
                    Text("暂无积分流水", fontSize = 12.sp, color = DColor.Ink3)
                } else {
                    store.pointsHistory.take(8).forEachIndexed { index, entry ->
                        if (index > 0) DDivider()
                        B_LedgerRow(
                            title = entry.reason.ifBlank { "积分变动" },
                            sub = entry.createdAt.replace("T", " ").take(16),
                            amount = (if (entry.change > 0) "+" else "") + entry.change,
                            positive = entry.change > 0,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun D22PaySuccess(nav: DNav) {
    val store = DLocalStore.current
    val order = store.selectedOrder
    DFrame(
        nav = nav,
        title = null,
        backable = false,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(DColor.Card)
                    .padding(horizontal = LocalDWindow.current.gutter, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DButton("返回首页", { nav.tab(DTab.Home) }, Modifier.weight(1f), DButtonStyle.Ghost)
                // replace：支付成功页不该留在栈里，否则从订单列表返回又看到「支付成功」
                DButton("查看订单", { nav.replace(DScreen.Orders) }, Modifier.weight(1f))
            }
        },
    ) {
        Column(
            // 固定 410dp 在大字号（fontScale 1.3+）和小屏上会把标题/金额裁掉，
            // 改成最小高度，内容多了由 DFrame 的滚动容器接管
            Modifier.fillMaxWidth().heightIn(min = 410.dp).padding(horizontal = 30.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(DColor.OkSoft),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Check, Modifier.size(42.dp), DColor.Ok)
            }
            Spacer(Modifier.height(20.dp))
            Text("支付成功", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DColor.Ink)
            Spacer(Modifier.height(16.dp))
            Text(
                order?.fee ?: "—",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DColor.BrandInk,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (order != null) "订单 ${order.no} · ${order.org}" else "订单信息已更新",
                fontSize = 12.5.sp,
                color = DColor.Ink3,
            )
            Spacer(Modifier.height(26.dp))
            DCard {
                DKeyValue("寄养机构", order?.org ?: "—")
                DKeyValue("宠物数量", order?.pet ?: "—")
                DKeyValue("寄养日期", order?.range ?: "—")
            }
        }
    }
}

/* ㉓ 机构对比（多机构并排，取列表前 3 家真实数据） */
@Composable
fun D23SitterCompare(nav: DNav) {
    val store = DLocalStore.current
    LaunchedEffect(Unit) { store.loadSitters() }
    val list = store.sitters.take(3)
    if (list.size < 2) {
        DFrame(nav = nav, title = "机构对比") {
            DPad {
                if (store.sittersLoading) DLoading() else DEmpty("至少需要两家机构才能对比", "去找机构") {
                    nav.tab(DTab.Sitters)
                }
            }
        }
        return
    }
    var selected by remember { mutableStateOf(0) }
    val rows: List<Pair<String, List<String>>> = listOf(
        "类型" to list.map { it.type },
        "单价" to list.map { "${store.money(it.price)}/${it.unit}" },
        "评分" to list.map { if (it.score > 0) formatScore(it.score) else "—" },
        "评价" to list.map { it.tags },
        "容量" to list.map { it.cap },
        "区域" to list.map { it.addr.split("区").firstOrNull()?.plus("区") ?: "—" },
    )
    DFrame(
        nav = nav,
        title = "机构对比",
        actions = { DTag("已选 ${list.size} 家", DTagTone.Brand) },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard(padding = 8.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    B_Cell("对比项", Modifier.weight(1.2f), color = DColor.Ink3)
                    list.forEachIndexed { i, sitter ->
                        B_Cell(
                            sitter.name,
                            Modifier.weight(1f),
                            bold = true,
                            color = if (i == selected) DColor.BrandInk else DColor.Ink,
                        ) { selected = i }
                    }
                }
                DDivider()
                rows.forEachIndexed { idx, (label, values) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        B_Cell(label, Modifier.weight(1.2f), color = DColor.Ink3)
                        val priceRow = label == "单价"
                        values.forEachIndexed { i, v ->
                            B_Cell(
                                v,
                                Modifier.weight(1f),
                                bold = priceRow && i == selected,
                                color = if (priceRow && i == selected) DColor.BrandInk else DColor.Ink,
                            )
                        }
                    }
                    if (idx != rows.lastIndex) DDivider()
                }
            }
        }
        DPad {
            Spacer(Modifier.height(18.dp))
            DButton("选择 ${list[selected].name} · 去预约", { nav.startBooking(list[selected]) })
        }
    }
}

/** 还没有任何坐标时的默认视野：呼和浩特市中心（与桌面端选点面板保持一致）。 */
private const val MAP_DEFAULT_LAT = 40.821365
private const val MAP_DEFAULT_LNG = 111.772564

/**
 * 选点页屏幕正中的准心。
 *
 * 语义是「准心指着哪里就选哪里」，所以它不挂在任何地理坐标上，只是一层固定浮层。
 * 用自绘而不是复用 AppIcon：图标集里没有图钉，为这一个按钮加一条路径不划算，
 * 而圆头 + 尖尾的形状用三个绘制指令就够。
 */
@Composable
private fun DMapCenterPin(modifier: Modifier = Modifier) {
    // DColor.Brand 是 @Composable getter（读主题调色板），只能在组合期取值；
    // Canvas 的绘制 lambda 是 DrawScope，不是 @Composable，所以先取到局部变量再画
    val pinColor = DColor.Brand
    Canvas(modifier.size(width = 30.dp, height = 42.dp)) {
        val w = size.width
        val h = size.height
        val r = w / 2f
        val head = Offset(w / 2f, r)
        // 尖尾：从圆心下方收成一点，尖端落在画布底边，也就是地图中心
        val tail = Path().apply {
            moveTo(w / 2f, h)
            lineTo(w / 2f - r * 0.46f, r * 1.42f)
            lineTo(w / 2f + r * 0.46f, r * 1.42f)
            close()
        }
        drawPath(tail, pinColor)
        drawCircle(pinColor, radius = r, center = head)
        drawCircle(Color.White, radius = r * 0.34f, center = head)
    }
}

/* ㉔ 地图选点（全屏地图，拖动准心对准位置，地址由后端逆地理编码回填） */
@Composable
fun D24MapPicker(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var address by remember {
        mutableStateOf(store.pickedAddress.ifBlank { store.myShop?.address.orEmpty() })
    }
    var city by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<GeocodeDto?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(15) }
    // 地图中心：优先这次选的点，其次机构已存坐标，最后退回默认视野
    var centerLat by remember {
        mutableStateOf(store.pickedLatitude ?: store.myShop?.latitude ?: MAP_DEFAULT_LAT)
    }
    var centerLng by remember {
        mutableStateOf(store.pickedLongitude ?: store.myShop?.longitude ?: MAP_DEFAULT_LNG)
    }
    // 已有坐标（这次选过 / 机构资料里存过）就直接可确认；什么都没有时要求用户先动一下地图，
    // 免得把默认视野当成机构位置
    var touched by remember { mutableStateOf(store.pickedLatitude != null) }
    // 地址编辑表单默认收起，把区域全留给地图；需要精确输入时再展开
    var formOpen by remember { mutableStateOf(false) }
    val picked = result

    // 拖动停稳 400ms 再反查地址：拖动中连着请求既慢又吵
    LaunchedEffect(centerLat, centerLng) {
        if (!touched) return@LaunchedEffect
        delay(400.milliseconds)
        val geo = store.reverseAddress(centerLat, centerLng) ?: return@LaunchedEffect
        if (geo.formattedAddress.isNotBlank()) {
            address = geo.formattedAddress
            store.pickedAddress = geo.formattedAddress
        }
        result = geo
    }

    // 全屏地图选点：和「机构位置」页保持同一套交互——地图铺满整屏，
    // 返回与位置说明浮在上方，地址编辑区浮在下方，任何浮层都不占地图面积。
    // 之前是 240dp 的小地图加一整列表单，既看不清位置，拖动也只剩一小块可用。
    Box(Modifier.fillMaxSize().background(DColor.MapWash)) {
        DMapView(
            latitude = centerLat,
            longitude = centerLng,
            modifier = Modifier.fillMaxSize(),
            zoom = zoom,
            interactive = true,
            // 铺满整屏时不能要圆角描边，否则四角会露出页面底色
            rounded = false,
            // 准心自己画：marker 钉的是坐标，红点必须钉在屏幕正中，
            // 否则拖动时标记跟着瓦片滑，看起来像没对准
            showMarker = false,
            onCenterChange = { newLat, newLng ->
                centerLat = newLat
                centerLng = newLng
                store.pickedLatitude = newLat
                store.pickedLongitude = newLng
                touched = true
            },
            // 手势缩放后把级别同步回来，缩放按钮的基准才跟得上真实视野
            onZoomChange = { zoom = it },
            corner = {
                // 控件贴右侧中部，避开顶部信息条和底部面板
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DMapZoomPad(zoom = zoom, onZoomChange = { zoom = it })
                }
            },
        )

        // 屏幕正中的准心：尖端对准地图中心，所以整体上移半个高度
        DMapCenterPin(Modifier.align(Alignment.Center).offset(y = (-21).dp))

        // 顶部浮层：返回 + 标题与当前所在区
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(DColor.Scrim)
                    .clickable { nav.back() },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Back, Modifier.size(19.dp), DColor.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f).clip(DShape.Md).background(DColor.Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "选择地址位置",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DColor.Ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    picked?.district?.takeIf { it.isNotBlank() }
                        ?: picked?.resolvedCity?.takeIf { it.isNotBlank() }
                        ?: "拖动地图把准心对准位置",
                    fontSize = 11.sp,
                    color = DColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 底部浮层：选中的地址 + 编辑区 + 确认
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .widthIn(max = LocalDWindow.current.contentMaxWidth)
                .navigationBarsPadding().padding(12.dp),
        ) {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (resolving) "正在解析地址…" else "选中的位置",
                            fontSize = 10.5.sp,
                            color = DColor.Ink3,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            address.ifBlank { "还没有地址，可展开「改地址」手动填写" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DColor.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${centerLat.formatCoord()}, ${centerLng.formatCoord()} · " +
                                when {
                                    picked == null -> "拖动地图选取"
                                    picked.fromCache -> "后端缓存"
                                    else -> "逆地理编码"
                                },
                            fontSize = 10.5.sp,
                            color = DColor.Ink3,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // DSmallButton 的最后一个参数是 filled，尾随 lambda 会被当成 filled 传进去，
                    // 所以这里必须用命名参数指定 onClick
                    DSmallButton(
                        if (formOpen) "收起" else "改地址",
                        onClick = { formOpen = !formOpen },
                        filled = formOpen,
                    )
                }

                // 表单默认收起：进这一页最常见的动作是拖地图，不是打字。
                // 需要精确输入时再展开，省下的高度全给地图。
                if (formOpen) {
                    Spacer(Modifier.height(10.dp))
                    DFieldInput(
                        "详细地址",
                        address,
                        { address = it },
                        placeholder = "省市区 + 街道门牌，至少 4 个字",
                    )
                    DFieldInput("城市（可选）", city, { city = it }, placeholder = "如：呼和浩特")
                    Spacer(Modifier.height(4.dp))
                    DButton(
                        if (resolving) "解析中…" else "按地址定位",
                        {
                            scope.launch {
                                resolving = true
                                val geo = store.resolveAddress(address, city)
                                resolving = false
                                if (geo != null) {
                                    result = geo
                                    // 解析出来的坐标直接当成地图中心，缩放拉近到街道级
                                    centerLat = geo.latitude
                                    centerLng = geo.longitude
                                    zoom = 16
                                    touched = true
                                    store.pickedLatitude = geo.latitude
                                    store.pickedLongitude = geo.longitude
                                    if (geo.formattedAddress.isNotBlank()) {
                                        address = geo.formattedAddress
                                        store.pickedAddress = geo.formattedAddress
                                    }
                                }
                            }
                        },
                        style = DButtonStyle.Outline,
                        height = 42.dp,
                        enabled = !resolving,
                    )
                }

                Spacer(Modifier.height(10.dp))
                DButton(
                    "确认并回填",
                    {
                        if (store.pickedAddress.isBlank()) store.pickedAddress = address
                        store.pickedLatitude = centerLat
                        store.pickedLongitude = centerLng
                        nav.back()
                    },
                    // 没动过地图就不给确认：默认视野不是用户选的位置，
                    // 直接确认会把呼和浩特市中心写进机构资料
                    enabled = touched,
                )
                if (!touched) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "拖动地图或按地址定位后才能确认",
                        fontSize = 10.5.sp,
                        color = DColor.Ink3,
                    )
                }
            }
        }
    }
}

/** 坐标统一留 6 位小数，避免 39.123456789012345 这种噪点。 */
private fun Double.formatCoord(): String {
    val scaled = kotlin.math.round(this * 1_000_000.0).toLong()
    val intPart = scaled / 1_000_000
    val frac = (scaled % 1_000_000).let { if (it < 0) -it else it }
    return "$intPart." + frac.toString().padStart(6, '0')
}

/* ㉕ 切换身份（主人端 / 机构端） */
@Composable
fun D25RoleSwitch(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        store.loadProfile()
        if (store.canUseSitter) store.loadMyShop()
    }
    val shopName = store.myShop?.name?.takeIf { it.isNotBlank() }
    val switching = store.identitySwitching
    val sitterMode = store.isSitterMode

    // 切完直接落到另一套界面的根页。停在切换页等于「点了没反应」，
    // 而且两套界面各有自己的根：主人端是带四个 Tab 的首页，机构端是工作台。
    // 用 resetStack 而不是 tab()：换身份要把上一套的页面整个清掉，返回键不该穿越回去。
    fun switchTo(target: String) {
        if (switching) return
        scope.launch {
            if (!store.switchIdentity(target)) return@launch
            nav.resetStack(if (store.isSitterMode) DScreen.SitterWorkspace else DScreen.Home)
        }
    }

    DFrame(nav = nav, title = "切换身份") {
        DPad {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(DShape.Lg).background(DColor.Card)
                    .border(1.dp, DColor.Line, DShape.Lg)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(DColor.CoverGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        dInitial(store.nickname.ifBlank { store.account }),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DColor.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        store.nickname.ifBlank { store.account }.ifBlank { "未登录" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DColor.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dMaskPhone(store.profile?.phone).ifBlank { store.account },
                        fontSize = 11.5.sp,
                        color = DColor.Ink3,
                    )
                }
                Spacer(Modifier.width(8.dp))
                DTag(store.identityLabel, if (sitterMode) DTagTone.Ok else DTagTone.Brand)
            }
        }
        DSection("我的身份")
        DPad {
            // 单选语义：一个选择器 + 下拉菜单，而不是并排两行各点各的。
            // 菜单里能标出当前身份，也不会出现「点一下才发现进错了身份」的误触。
            val sitterLabel = if (store.canUseSitter) shopName ?: "寄养机构" else "寄养机构（未开通）"
            DDropdown(
                options = listOf("宠物主人", sitterLabel),
                selectedIndex = if (sitterMode) 1 else 0,
                enabled = !switching,
                modifier = Modifier.fillMaxWidth(),
                onSelect = { index ->
                    if (index == 0) {
                        if (sitterMode) switchTo("owner")
                    } else if (store.canUseSitter) {
                        if (!sitterMode) switchTo("sitter")
                    } else {
                        // 没入驻的账号选这一项不是「切换」，而是去开通 ——
                        // 混为一谈会让人以为已经切过去了
                        nav.go(DScreen.SitterOnboarding)
                    }
                },
            ) { expanded ->
                Row(
                    Modifier.fillMaxWidth().clip(DShape.Lg).background(DColor.Card)
                        .border(1.dp, DColor.Line, DShape.Lg)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(DShape.Md)
                            // 用主题语义色，别写死浅绿：深色模式下浅底 + 白图标几乎看不清
                            .background(if (sitterMode) DColor.Ok else DColor.BrandSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            if (sitterMode) AppIcon.Shop else AppIcon.Paw,
                            Modifier.size(21.dp),
                            if (sitterMode) DColor.White else DColor.BrandInk,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (sitterMode) sitterLabel else "宠物主人",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            Spacer(Modifier.width(6.dp))
                            DTag("当前使用", DTagTone.Brand)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (sitterMode) "接单履约 · 发布动态 · 收益结算"
                            else "浏览机构 · 预约寄养 · 管理宠物",
                            fontSize = 11.5.sp,
                            color = DColor.Ink3,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = DColor.Ink3)
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                switching -> DLoading("正在切换身份…")
                !store.canUseSitter -> {
                    Text(
                        "这个账号还没开通机构身份。开通后同一账号就能接单，" +
                            "订单、余额和优惠券依旧共用。",
                        fontSize = 11.5.sp,
                        color = DColor.Ink3,
                    )
                    Spacer(Modifier.height(10.dp))
                    DSmallButton(text = "去开通机构身份", onClick = { nav.go(DScreen.SitterOnboarding) })
                }

                else -> Text(
                    "两个身份共用同一个账号，余额、优惠券和订单记录都会保留；" +
                        "切换后首页、订单和消息范围会换成对应身份的内容。",
                    fontSize = 11.5.sp,
                    color = DColor.Ink3,
                )
            }
        }
    }
}

/* ㉖ 店铺资料（机构维护） */
@Composable
fun D26ShopProfile(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadMyShop() }
    val shop = store.myShop
    var name by remember(shop) { mutableStateOf(shop?.name.orEmpty()) }
    var typeLabel by remember(shop) { mutableStateOf(B_shopTypeLabel(shop?.type)) }
    var price by remember(shop) { mutableStateOf(B_doubleText(shop?.dailyPrice ?: 0.0)) }
    var visitPrice by remember(shop) { mutableStateOf(B_doubleText(shop?.visitPrice ?: 0.0)) }
    var capacity by remember(shop) { mutableStateOf(shop?.capacity?.takeIf { it > 0 }?.toString() ?: "") }
    var address by remember(shop) { mutableStateOf(store.pickedAddress.ifBlank { shop?.address.orEmpty() }) }
    var intro by remember(shop) { mutableStateOf(shop?.description.orEmpty()) }
    var cover by remember(shop) { mutableStateOf(shop?.coverPhotoUrl.orEmpty()) }
    var uploading by remember { mutableStateOf(false) }
    val pickCover = rememberPhotoPicker { fileName, bytes ->
        if (bytes.isEmpty()) {
            nav.toast("没有读取到图片")
        } else {
            scope.launch {
                uploading = true
                val url = store.uploadPhoto(fileName, bytes)
                uploading = false
                if (url != null) cover = url
            }
        }
    }
    val coordText = when {
        store.pickedLatitude != null && store.pickedLongitude != null ->
            "${store.pickedLatitude}, ${store.pickedLongitude}"
        shop?.latitude != null && shop.longitude != null -> "${shop.latitude}, ${shop.longitude}"
        else -> "未定位"
    }
    DFrame(
        nav = nav,
        title = "店铺资料",
        actions = {
            DTag(
                if (shop == null) "未入驻" else if (shop.status == "active") "已审核" else "待审核",
                if (shop?.status == "active") DTagTone.Ok else DTagTone.Warn,
            )
        },
    ) {
        DPad {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(DShape.Lg)
                    .background(DColor.CoverGradient)
                    .clickable(enabled = !uploading) { pickCover() },
                contentAlignment = Alignment.BottomEnd,
            ) {
                if (cover.isNotBlank()) {
                    MobileImage(
                        url = cover,
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = DShape.Lg,
                        placeholderSize = 26.dp,
                    )
                }
                Box(
                    Modifier.padding(14.dp).clip(DShape.Pill)
                        .background(DColor.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (uploading) "上传中…" else "更换封面",
                        fontSize = 12.sp,
                        color = DColor.White,
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DFieldInput("机构名称", name, { name = it }, placeholder = "例如：萌宠之家")
            B_SelectField("机构类型", typeLabel, B_SHOP_TYPES.map { it.first }) { typeLabel = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DFieldInput(
                    "日单价（¥/天）", price, { price = it },
                    modifier = Modifier.weight(1f), numeric = true, placeholder = "130",
                )
                DFieldInput(
                    "上门价（¥/次）", visitPrice, { visitPrice = it },
                    modifier = Modifier.weight(1f), numeric = true, placeholder = "60",
                )
            }
            DFieldInput(
                "默认容量（只）", capacity, { capacity = it },
                numeric = true, placeholder = "3",
            )
            DFieldInput("地址", address, { address = it }, placeholder = "省市区 + 街道门牌")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("坐标：$coordText", fontSize = 12.sp, color = DColor.Ink3)
                Spacer(Modifier.weight(1f))
                B_TextLink("选择位置", DColor.BrandInk) { nav.go(DScreen.MapPicker) }
            }
            Spacer(Modifier.height(12.dp))
            DFieldInput(
                label = "机构介绍",
                value = intro,
                onValueChange = { intro = it },
                placeholder = "介绍下场地、看护方式、日常作息…",
                multiline = true,
            )
        }
        DPad {
            Spacer(Modifier.height(6.dp))
            DCard(padding = 0.dp) {
                DMenuRow(
                    "加购服务",
                    "${store.myAddons.size} 项",
                    onClick = { nav.go(DScreen.Addons) },
                )
                DDivider()
                DMenuRow("节假日定价规则", onClick = { nav.go(DScreen.HolidayRules) })
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DButton(
                if (store.busy) "保存中…" else "保存资料",
                {
                    scope.launch {
                        store.saveMyShop(
                            name = name,
                            type = B_shopTypeCode(typeLabel),
                            description = intro,
                            dailyPrice = price.toDoubleOrNull() ?: 0.0,
                            visitPrice = visitPrice.toDoubleOrNull() ?: 0.0,
                            priceMode = if (typeLabel == "上门喂养") "per_visit" else "per_day",
                            capacity = capacity.toIntOrNull() ?: 0,
                            address = address,
                            coverPhotoUrl = cover,
                            detailPhotos = shop?.detailPhotos ?: emptyList(),
                            latitude = store.pickedLatitude ?: shop?.latitude,
                            longitude = store.pickedLongitude ?: shop?.longitude,
                        )
                    }
                },
                enabled = !store.busy,
            )
        }
    }
}

private val B_SHOP_TYPES = listOf(
    "家庭寄养" to "home",
    "门店寄养" to "shop",
    "上门喂养" to "visit",
)

private fun B_shopTypeLabel(code: String?): String =
    B_SHOP_TYPES.firstOrNull { it.second == code }?.first ?: "家庭寄养"

private fun B_shopTypeCode(label: String): String =
    B_SHOP_TYPES.firstOrNull { it.first == label }?.second ?: "home"

/** 金额转输入框文本：0 或空值不出 "0.0" 这种碍眼内容。 */
private fun B_doubleText(value: Double): String =
    if (value <= 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/* ㉗ 容量日历（逐日设置，数据来自 /sitter/capacity） */
@Composable
fun D27Capacity(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadCapacity() }
    val items = store.capacityItems
    var selectedDate by remember { mutableStateOf("") }
    val current = items.firstOrNull { it.date == selectedDate } ?: items.firstOrNull()
    var adjust by remember(current?.date, current?.capacity) {
        mutableStateOf(current?.capacity ?: 3)
    }
    val monthLabel = current?.date?.let { B_monthLabel(it) } ?: "容量日历"
    DFrame(
        nav = nav,
        title = "容量日历",
        actions = { Text(monthLabel, fontSize = 12.sp, color = DColor.Ink3) },
    ) {
        when {
            store.capacityLoading && items.isEmpty() -> DPad { DLoading() }
            items.isEmpty() -> DPad {
                DEmpty("还没有容量数据", "重新加载") { scope.launch { store.loadCapacity(force = true) } }
            }
            else -> {
                DPad {
                    Spacer(Modifier.height(14.dp))
                    DCard {
                        Row(Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                                Text(
                                    w,
                                    fontSize = 11.sp,
                                    color = DColor.Ink3,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // 首日不一定落在周一，按真实星期补空格，避免日期与表头错位
                        val lead = items.firstOrNull()?.date?.let { B_weekIndex(it) } ?: 0
                        val cells: List<CapacityItemDto?> =
                            List(lead) { null } + items.map { it }
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { item ->
                                    // 每列仍均分占位（触控区一致），但格子本身限宽居中：
                                    // 平板上 7 列均分会把格子拉成 130dp 宽的色块。
                                    Box(
                                        Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (item != null) {
                                            B_DayCell(
                                                day = B_dayOfMonth(item.date),
                                                tone = B_capacityTone(item.full, item.available),
                                                selected = item.date == current?.date,
                                                modifier = Modifier.widthIn(max = 44.dp).fillMaxWidth(),
                                            ) { selectedDate = item.date }
                                        }
                                    }
                                }
                                // 末行不足 7 个时补齐占位，保持列宽一致
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        B_Legend("可订", DColor.Ok)
                        B_Legend("余位紧张", DColor.Warn)
                        B_Legend("已满", DColor.Danger)
                    }
                }
                DPad {
                    Spacer(Modifier.height(14.dp))
                    DCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${current?.date.orEmpty()} ${store.weekday(current?.date.orEmpty())}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DColor.Ink,
                            )
                            DTag(
                                if (current?.full == true) "已满" else "可订",
                                if (current?.full == true) DTagTone.Danger else DTagTone.Ok,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DKeyValue("当日容量", "${current?.capacity ?: 0} 只")
                        DKeyValue("已占用", "${current?.used ?: 0} 只")
                        DKeyValue("可预约", "${current?.available ?: 0} 只")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("调整当日容量", fontSize = 12.5.sp, color = DColor.Ink2)
                            B_Stepper(
                                adjust,
                                { adjust = (adjust - 1).coerceAtLeast(1) },
                                { adjust = (adjust + 1).coerceAtMost(200) },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        DButton(
                            if (store.busy) "保存中…" else "保存当日容量",
                            {
                                val date = current?.date
                                if (date.isNullOrBlank()) {
                                    nav.toast("请先选择日期")
                                } else {
                                    scope.launch { store.setCapacity(date, adjust) }
                                }
                            },
                            style = DButtonStyle.Ghost,
                            height = 40.dp,
                            enabled = !store.busy,
                        )
                    }
                }
            }
        }
    }
}

private fun B_dayOfMonth(date: String): Int =
    date.substring(8, 10).trimStart('0').toIntOrNull() ?: 0

/** 周一为 0，用于给容量日历首行补空格。 */
private fun B_weekIndex(date: String): Int =
    (runCatching { java.time.LocalDate.parse(date).dayOfWeek.value }.getOrDefault(1) - 1).coerceIn(0, 6)

private fun B_monthLabel(date: String): String =
    if (date.length >= 7) "${date.substring(0, 4)} 年 ${date.substring(5, 7)} 月" else date

private fun B_capacityTone(full: Boolean, available: Int): DTagTone? = when {
    full || available <= 0 -> DTagTone.Danger
    available <= 1 -> DTagTone.Warn
    else -> DTagTone.Ok
}

/* ㉘ 发布动态（关联订单 + 多图） */
@Composable
fun D28PublishDynamic(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store.loadReceivedOrders() }
    // 只有已接单 / 寄养中 / 已完成的订单才能发动态
    val orders = remember(store.receivedOrders) {
        store.receivedOrders.filter {
            it.status == "accepted" || it.status == "staying" || it.status == "completed"
        }
    }
    val labels = orders.map { B_orderLabel(it) }
    var orderLabel by remember(orders) {
        val preferred = orders.firstOrNull { it.id == store.selectedOrderIdForDynamic }
        mutableStateOf(
            preferred?.let { B_orderLabel(it) } ?: labels.firstOrNull().orEmpty(),
        )
    }
    var content by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    val photos = remember { mutableStateListOf<String>() }
    val pickPhoto = rememberPhotoPicker { fileName, bytes ->
        when {
            bytes.isEmpty() -> nav.toast("没有读取到图片")
            photos.size >= 6 -> nav.toast("最多上传 6 张照片")
            else -> scope.launch {
                uploading = true
                val url = store.uploadPhoto(fileName, bytes)
                uploading = false
                if (url != null) photos.add(url)
            }
        }
    }
    val orderId = orders.firstOrNull { B_orderLabel(it) == orderLabel }?.id ?: 0L
    DFrame(nav = nav, title = "发布寄养动态") {
        when {
            orders.isEmpty() -> DPad {
                Spacer(Modifier.height(16.dp))
                DEmpty("还没有可关联的订单", "去接单") { nav.back() }
            }
            else -> {
                DPad {
                    Spacer(Modifier.height(14.dp))
                    B_SelectField("关联订单", orderLabel, labels) { orderLabel = it }
                    DFieldInput(
                        label = "动态内容",
                        value = content,
                        onValueChange = { content = it },
                        placeholder = "说说今天宠物的状态，例如吃饭、遛弯、便便情况…",
                        multiline = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("上传照片（最多 6 张）", fontSize = 12.sp, color = DColor.Ink3)
                        Spacer(Modifier.weight(1f))
                        if (uploading) Text("上传中…", fontSize = 12.sp, color = DColor.BrandInk)
                    }
                    Spacer(Modifier.height(8.dp))
                    val cells = photos.toList() + if (photos.size < 6) listOf("add") else emptyList()
                    cells.chunked(4).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { cell ->
                                if (cell == "add") {
                                    B_AddPhoto { if (!uploading) pickPhoto() }
                                } else {
                                    MobileImage(
                                        url = cell,
                                        modifier = Modifier.size(72.dp),
                                        shape = DShape.Md,
                                        placeholderSize = 16.dp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    DButton(
                        if (store.busy) "发布中…" else "发布动态",
                        {
                            when {
                                orderId <= 0L -> nav.toast("请先选择关联订单")
                                content.isBlank() -> nav.toast("动态内容不能为空")
                                else -> scope.launch {
                                    store.publishDynamic(orderId, content.trim(), photos.toList())
                                    store.selectedOrderIdForDynamic = 0L
                                    nav.back()
                                }
                            }
                        },
                        enabled = !store.busy,
                    )
                }
            }
        }
    }
}

/** 佣金比例按百分比展示，最多保留一位小数：0.1 → 10%，0.125 → 12.5% */
private fun B_ratePct(rate: Double): String {
    val pct = (rate * 1000).roundToInt() / 10.0
    return if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "$pct%"
}

private fun B_orderLabel(order: ReceivedOrderDto): String =
    "${order.ownerName.ifBlank { "主人" }} · ${order.petCount} 只 · ${order.orderNo}"

/* ㉙ 机构收益与结算 */
@Composable
fun D29SitterIncome(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // 进收益页一定要重新取：订单刚结束、主人刚评价都会改变金额，
        // 走缓存会出现「今日收入有值、收益页还是 0」
        store.loadEarnings(force = true)
        store.loadSettlements()
        store.loadSitterStats()
        store.loadSettleWindow()
    }
    val earnings = store.earnings
    val summary = earnings?.summary
    val settled = store.settlements.filter { it.status == "paid" }.sumOf { it.netAmount }
    // 只算 pending：rejected 的结算单平台不会打款，算进「待结算」会永远挂在那里，
    // 用户等不到钱也不知道该重新申请
    val pendingSettle = store.settlements.filter { it.status == "pending" }.sumOf { it.netAmount }

    // 收益区间快捷项：以前固定近 30 天，想查上月/上周看不到
    val today = java.time.LocalDate.now()
    val presets = listOf(
        "近 7 天" to (today.minusDays(6).toString() to today.toString()),
        "近 30 天" to (today.minusDays(29).toString() to today.toString()),
        "本月" to (today.withDayOfMonth(1).toString() to today.toString()),
        "上月" to (
            today.minusMonths(1).withDayOfMonth(1).toString() to
                today.withDayOfMonth(1).minusDays(1).toString()
        ),
    )
    val activeLabel = presets.firstOrNull {
        it.second.first == store.earningsFrom && it.second.second == store.earningsTo
    }?.first ?: if (store.earningsFrom.isBlank()) "近 30 天" else ""
    DFrame(nav = nav, title = null, backable = false, refreshing = store.earningsLoading, onRefresh = { scope.launch { store.loadEarnings(force = true); store.loadSettlements(); store.loadSitterStats(); store.loadSettleWindow() } }) {
        Column(
            Modifier.fillMaxWidth().clip(B_HeroBottomShape).background(DColor.HeroGradient)
                .padding(horizontal = LocalDWindow.current.gutter, vertical = 22.dp),
        ) {
            Text(
                "近 30 天净收入" + (earnings?.period?.let { " · ${it.from} ~ ${it.to}" } ?: ""),
                fontSize = 12.sp,
                color = DColor.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                store.money(summary?.netAmount ?: 0.0),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DColor.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("已结算 ${store.money(settled)}", fontSize = 12.sp, color = DColor.White.copy(alpha = 0.9f))
                Text("待结算 ${store.money(pendingSettle)}", fontSize = 12.sp, color = DColor.White.copy(alpha = 0.9f))
                if (summary != null && summary.commissionRate > 0) {
                    Text(
                        // 比例由后台配置（全局默认或本机构单独设置），这里只做展示
                        "佣金 ${B_ratePct(summary.commissionRate)} · ${store.money(summary.commissionAmount)}",
                        fontSize = 12.sp,
                        color = DColor.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
        DPad {
            Spacer(Modifier.height(14.dp))
            DCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    B_Stat((summary?.orderCount ?: 0).toString(), "结算订单")
                    B_Stat(store.money(summary?.grossAmount ?: 0.0), "流水")
                    // 后端返回 0-1 的小数，直接拼 "%" 会显示成 0.1%，这里统一换算
                    B_Stat(B_ratePct(summary?.commissionRate ?: 0.0), "佣金比例")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    DChip(preset.first, activeLabel == preset.first) {
                        scope.launch {
                            store.loadEarningsRange(preset.second.first, preset.second.second)
                        }
                    }
                }
            }
        }
        DSection("结算记录")
        DPad {
            DCard {
                if (store.settlements.isEmpty()) {
                    Text("还没有结算记录", fontSize = 12.sp, color = DColor.Ink3)
                } else {
                    store.settlements.forEachIndexed { index, item ->
                        if (index > 0) DDivider()
                        B_LedgerRow(
                            title = "${item.periodStart} ~ ${item.periodEnd}",
                            sub = "${item.orderCount} 单 · " + when (item.status) {
                                "paid" -> "已打款 " + item.paidAt.take(10)
                                // 后端状态是 pending，以前这里写成 applied，导致
                                // 已申请的单一直显示「待申请结算」
                                "pending" -> "已申请，等待打款"
                                "rejected" -> "已驳回，可重新申请"
                                else -> item.status
                            },
                            amount = store.money(item.netAmount),
                            positive = item.status == "paid",
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // 可结算区间：起点是上次结算之后，漏掉的月份会自动带上
            val win = store.settleWindow
            if (win != null) {
                Text(
                    if (win.orderCount > 0) {
                        "可结算 ${win.start} ~ ${win.end} · ${win.orderCount} 单 · " +
                            "应得 ${store.money(win.netAmount)}"
                    } else {
                        "暂无待结算收益"
                    },
                    fontSize = 11.5.sp,
                    color = DColor.Ink3,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            DButton(
                "申请结算（待结算 ${store.money(pendingSettle)}）",
                { scope.launch { store.applySettlement() } },
            )
            Spacer(Modifier.height(10.dp))
            DButton(
                "优惠券管理",
                { nav.go(DScreen.SitterCoupons) },
                style = DButtonStyle.Outline,
                height = 42.dp,
            )
        }
        if (!earnings?.items.isNullOrEmpty()) {
            DSection("近期收益明细")
            DPad {
                DCard {
                    earnings.items.take(8).forEachIndexed { index, item ->
                        if (index > 0) DDivider()
                        B_LedgerRow(
                            title = item.owner.ifBlank { item.orderNo },
                            sub = "${item.date} · ${item.orderNo}",
                            amount = store.money(item.amount),
                            positive = true,
                        )
                    }
                }
            }
        }
    }
}

/* ㉚ 机构优惠券管理 */
@Composable
fun D30SitterCoupons(nav: DNav) {
    val store = DLocalStore.current
    val scope = rememberCoroutineScope()
    var couponsRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.loadSitterCoupons() }
    var tab by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var isPercent by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var minAmount by remember { mutableStateOf("") }
    var totalQty by remember { mutableStateOf("") }
    val visible = store.sitterCoupons.filter { coupon ->
        when (tab) {
            1 -> coupon.enabled
            2 -> !coupon.enabled
            else -> true
        }
    }
    DFrame(
        nav = nav,
        title = "我的优惠券",
        actions = {
            DSmallButton(if (creating) "取消" else "+ 发布", { creating = !creating })
        },
        refreshing = couponsRefreshing,
        onRefresh = {
            couponsRefreshing = true
            scope.launch { store.loadSitterCoupons(); couponsRefreshing = false }
        },
    ) {
        DTabLine(listOf("全部", "进行中", "已停用"), tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        if (creating) {
            DPad {
                DCard {
                    DFieldInput("券名称", title, { title = it }, placeholder = "例如：本店寄养立减 20")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        B_ToggleTag("满减金额", !isPercent) { isPercent = false }
                        B_ToggleTag("折扣百分比", isPercent) { isPercent = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DFieldInput(
                            if (isPercent) "减免百分比（%）" else "立减金额（¥）",
                            value,
                            { value = it },
                            modifier = Modifier.weight(1f),
                            numeric = true,
                            placeholder = if (isPercent) "20" else "20",
                        )
                        DFieldInput(
                            "门槛（¥）", minAmount, { minAmount = it },
                            modifier = Modifier.weight(1f), numeric = true, placeholder = "100",
                        )
                    }
                    DFieldInput("发放总量", totalQty, { totalQty = it }, numeric = true, placeholder = "100")
                    DButton(
                        if (store.busy) "发布中…" else "发布优惠券",
                        {
                            val v = value.toDoubleOrNull() ?: 0.0
                            val min = minAmount.toDoubleOrNull() ?: 0.0
                            val qty = totalQty.toIntOrNull() ?: 0
                            when {
                                title.isBlank() -> nav.toast("请填写券名称")
                                v <= 0.0 -> nav.toast(if (isPercent) "减免百分比要大于 0" else "立减金额要大于 0")
                                qty <= 0 -> nav.toast("发放总量要大于 0")
                                else -> scope.launch {
                                    store.saveSitterCoupon(
                                        null,
                                        SitterCouponPayload(
                                            title = title.trim(),
                                            type = if (isPercent) "percent" else "fixed",
                                            value = v,
                                            minAmount = min,
                                            totalQty = qty,
                                            perUser = 1,
                                            enabled = true,
                                        ),
                                    )
                                    creating = false
                                    title = ""
                                    value = ""
                                    minAmount = ""
                                    totalQty = ""
                                }
                            }
                        },
                        enabled = !store.busy,
                    )
                }
            }
        }
        DPad {
            when {
                store.busy && visible.isEmpty() -> DLoading()
                visible.isEmpty() -> DEmpty(
                    if (tab == 0) "还没有发布优惠券" else "这个分类下没有券",
                )
                else -> DGrid(visible, minItemWidth = 360.dp) { coupon ->
                    B_OrderCard(
                        title = coupon.title.ifBlank { coupon.code },
                        tag = if (coupon.enabled) "进行中" else "已停用",
                        tone = if (coupon.enabled) DTagTone.Ok else DTagTone.Line,
                        lines = listOf(
                            "满 ${store.money(coupon.minAmount)} 可用 · " + if (coupon.type == "percent") {
                                "减免 ${coupon.value}%"
                            } else {
                                "减 ${store.money(coupon.value)}"
                            },
                            "已领 ${coupon.claimedQty}/${coupon.totalQty} · 已核销 ${coupon.usedQty}",
                            "有效期 ${coupon.startsAt.take(10).ifBlank { "即日起" }} ~ " +
                                coupon.endsAt.take(10).ifBlank { "长期" },
                        ),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row {
                            B_TextLink("停用 / 启用", DColor.BrandInk) {
                                scope.launch { store.toggleSitterCoupon(coupon) }
                            }
                            Spacer(Modifier.width(16.dp))
                            B_TextLink("删除", DColor.Danger) {
                                scope.launch { store.deleteSitterCoupon(coupon) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 本组私有数据与 helper：统一 B_ 前缀，避免与其它分组文件重名。
 * ------------------------------------------------------------------ */

private val B_HeroBottomShape = DShape.HeroBottom

// 主题令牌是 @Composable 取值（深色下要换一套），所以这几个取色 helper 也得是 @Composable。
@Composable
private fun B_toneBg(tone: DTagTone?): Color = when (tone) {
    DTagTone.Ok -> DColor.OkSoft
    DTagTone.Warn -> DColor.WarnSoft
    DTagTone.Danger -> DColor.DangerSoft
    DTagTone.Brand -> DColor.BrandSoft
    else -> Color.Transparent
}

@Composable
private fun B_toneFg(tone: DTagTone?): Color = when (tone) {
    DTagTone.Ok -> DColor.Ok
    DTagTone.Warn -> DColor.Warn
    DTagTone.Danger -> DColor.Danger
    DTagTone.Brand -> DColor.BrandInk
    else -> DColor.Ink3
}

@Composable
private fun B_TextLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.clickable(onClick = onClick),
    )
}


@Composable
private fun B_OrderCard(
    title: String,
    tag: String,
    tone: DTagTone,
    lines: List<String>,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
) {
    DCard(modifier = modifier.padding(bottom = 12.dp), padding = 13.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            DTag(tag, tone)
        }
        lines.forEach { line ->
            Spacer(Modifier.height(6.dp))
            Text(line, fontSize = 11.5.sp, color = DColor.Ink3)
        }
        bottom()
    }
}

/** 工单会话气泡：自己发的靠右填品牌色，客服的靠左用卡片色。 */
@Composable
private fun B_MessageBubble(text: String, time: String, fromMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LocalDWindow.current.gutter, vertical = 5.dp),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.widthIn(max = 280.dp)
                .clip(DShape.Lg)
                .background(if (fromMe) DColor.Brand else DColor.Card)
                .border(1.dp, if (fromMe) DColor.Brand else DColor.Line, DShape.Lg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text, fontSize = 13.sp, color = if (fromMe) DColor.White else DColor.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                time,
                fontSize = 10.5.sp,
                color = if (fromMe) DColor.White.copy(alpha = 0.75f) else DColor.Ink3,
            )
        }
    }
}

@Composable
private fun B_HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DColor.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = DColor.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun B_HeroAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(DShape.Pill).background(DColor.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick).padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 11.sp, color = DColor.White)
    }
}

@Composable
private fun B_Stat(value: String, label: String, valueColor: Color = DColor.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = DColor.Ink3)
    }
}

@Composable
private fun B_LedgerRow(title: String, sub: String, amount: String, positive: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
            Text(sub, fontSize = 11.5.sp, color = DColor.Ink3)
        }
        Text(
            amount,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (positive) DColor.Ok else DColor.Danger,
        )
    }
}

@Composable
private fun B_Cell(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: Color = DColor.Ink,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier.height(40.dp).clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun B_SelectField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    dropdown: Boolean = true,
    onChange: (String) -> Unit,
) {
    Column(modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 12.sp, color = DColor.Ink2)
        Spacer(Modifier.height(6.dp))
        DDropdown(
            options = options,
            selectedIndex = options.indexOf(value).coerceAtLeast(0),
            onSelect = { onChange(options[it]) },
            modifier = Modifier.fillMaxWidth(),
        ) { expanded ->
            Row(
                Modifier.fillMaxWidth().clip(DShape.Md).background(DColor.Card)
                    .border(1.dp, DColor.Line, DShape.Md)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, fontSize = 13.sp, color = DColor.Ink, modifier = Modifier.weight(1f))
                if (dropdown) {
                    Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = DColor.Ink3)
                }
            }
        }
    }
}

@Composable
private fun B_ToggleTag(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(DShape.Pill)
            .background(if (selected) DColor.BrandSoft else DColor.NeutralSoft)
            .border(1.dp, if (selected) DColor.Brand else DColor.Line, DShape.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 11.5.sp, color = if (selected) DColor.BrandInk else DColor.Ink2)
    }
}

@Composable
private fun B_DayCell(
    day: Int,
    tone: DTagTone?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.padding(3.dp).clip(DShape.Sm)
            .background(B_toneBg(tone))
            .border(if (selected) 1.5.dp else 0.dp,
                if (selected) DColor.Brand else Color.Transparent, DShape.Sm)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = B_toneFg(tone))
    }
}

@Composable
private fun B_Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, color = DColor.Ink3)
    }
}

@Composable
private fun B_Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(DShape.Sm).background(DColor.NeutralSoft)
                .clickable(onClick = onMinus),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", fontSize = 16.sp, color = DColor.Ink2)
        }
        Spacer(Modifier.width(12.dp))
        Text("$value 只", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DColor.Ink)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(28.dp).clip(DShape.Sm).background(DColor.BrandSoft)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 16.sp, color = DColor.BrandInk)
        }
    }
}


@Composable
private fun B_AddPhoto(onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(DShape.Md)
            .border(1.5.dp, DColor.Line2, DShape.Md)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", fontSize = 22.sp, color = DColor.Ink3)
    }
}
