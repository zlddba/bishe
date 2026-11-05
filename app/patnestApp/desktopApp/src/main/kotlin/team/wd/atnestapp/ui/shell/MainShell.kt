package team.wd.atnestapp.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppRole
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.ModalKind
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.RoleSwitch
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.pages.live.GoLivePage
import team.wd.atnestapp.ui.pages.live.LiveListPage
import team.wd.atnestapp.ui.pages.live.LiveRoomPage
import team.wd.atnestapp.ui.pages.owner.BookingPage
import team.wd.atnestapp.ui.pages.owner.CouponsPage
import team.wd.atnestapp.ui.pages.owner.DynamicsPage
import team.wd.atnestapp.ui.pages.owner.FavoritesPage
import team.wd.atnestapp.ui.pages.owner.HomePage
import team.wd.atnestapp.ui.pages.owner.MessagesPage
import team.wd.atnestapp.ui.pages.owner.OrdersPage
import team.wd.atnestapp.ui.pages.owner.PaymentPage
import team.wd.atnestapp.ui.pages.owner.PetEditPage
import team.wd.atnestapp.ui.pages.owner.PetsPage
import team.wd.atnestapp.ui.pages.owner.ProfilePage
import team.wd.atnestapp.ui.pages.owner.ReviewPage
import team.wd.atnestapp.ui.pages.owner.SitterDetailPage
import team.wd.atnestapp.ui.pages.owner.SittersPage
import team.wd.atnestapp.ui.pages.owner.WalletPage
import team.wd.atnestapp.ui.pages.sitter.SitterCapacityPage
import team.wd.atnestapp.ui.pages.sitter.SitterCouponsPage
import team.wd.atnestapp.ui.pages.sitter.SitterIncomePage
import team.wd.atnestapp.ui.pages.sitter.SitterOrdersPage
import team.wd.atnestapp.ui.pages.sitter.SitterOverviewPage
import team.wd.atnestapp.ui.pages.sitter.SitterProfilePage
import team.wd.atnestapp.ui.pages.sitter.SitterPublishPage
import team.wd.atnestapp.ui.pages.sitter.SitterReviewsPage
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestPageBackground
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.brandGlow
import team.wd.atnestapp.ui.theme.patShadowSoft

/**
 * 登录后的主界面：左侧浅色导航 + 顶部栏 + 页面内容。
 * 新视觉语言「现代柔和 · 有层次」：浅色侧栏、胶囊选中态、柔和阴影。
 */
@Composable
fun MainShell(app: AppState) {
    Row(Modifier.fillMaxSize()) {
        RailNav(app)
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(PatNestColors.Line),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TopBar(app)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PatNestPageBackground { PageHost(app) }
            }
        }
    }
}

/* ---------- 左侧导航 ---------- */

private data class NavItem(val page: AppPage, val label: String, val icon: AppIcon)

private val ownerNav = listOf(
    "常用" to listOf(
        NavItem(AppPage.Home, "首页", AppIcon.Home),
        NavItem(AppPage.Sitters, "找机构", AppIcon.Shop),
        NavItem(AppPage.Pets, "我的宠物", AppIcon.Paw),
        NavItem(AppPage.Booking, "预约下单", AppIcon.Calendar),
    ),
    "寄养" to listOf(
        NavItem(AppPage.Orders, "我的订单", AppIcon.Orders),
        NavItem(AppPage.Dynamics, "寄养动态", AppIcon.Image),
        NavItem(AppPage.Live, "直播广场", AppIcon.Eye),
    ),
    "账户" to listOf(
        NavItem(AppPage.Profile, "个人中心", AppIcon.User),
        NavItem(AppPage.Favorites, "我的收藏", AppIcon.Heart),
        NavItem(AppPage.Coupons, "优惠券", AppIcon.Ticket),
        NavItem(AppPage.Messages, "消息通知", AppIcon.Bell),
        NavItem(AppPage.Wallet, "我的钱包", AppIcon.Card),
    ),
)

private val sitterNav = listOf(
    "经营" to listOf(
        NavItem(AppPage.SbDash, "经营概览", AppIcon.Home),
        NavItem(AppPage.SbCoupons, "优惠券", AppIcon.Ticket),
    ),
    "业务" to listOf(
        NavItem(AppPage.SbOrders, "接单管理", AppIcon.Orders),
        NavItem(AppPage.SbCapacity, "容量日历", AppIcon.Calendar),
        NavItem(AppPage.SbPublish, "发布动态", AppIcon.Plus),
        NavItem(AppPage.SbReviews, "评价管理", AppIcon.Star),
        NavItem(AppPage.Live, "直播广场", AppIcon.Eye),
        NavItem(AppPage.GoLive, "我要开播", AppIcon.Camera),
    ),
    "财务" to listOf(
        NavItem(AppPage.SbIncome, "收益结算", AppIcon.OrdersPlain),
    ),
    "设置" to listOf(
        NavItem(AppPage.SbProfile, "机构资料", AppIcon.User),
    ),
)

@Composable
private fun RailNav(app: AppState) {
    val groups = if (app.role == AppRole.Owner || !app.isSitterAccount) ownerNav else sitterNav
    Column(
        Modifier
            .width(226.dp)
            .fillMaxHeight()
            .background(PatNestColors.Side)
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, top = 4.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .brandGlow(7.dp, PatNestShape.Pill)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk))),
                contentAlignment = Alignment.Center,
            ) {
                Text("宠", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("宠栖", color = PatNestColors.Ink, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                Text("PetNest Desktop", color = PatNestColors.Ink3, fontSize = 10.sp, letterSpacing = 0.4.sp)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            groups.forEach { (section, items) ->
                Text(
                    section,
                    color = PatNestColors.Ink3,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 7.dp),
                )
                items.forEach { item ->
                    val badge = when (item.page) {
                        AppPage.Orders -> app.orders.count { it.status == "待支付" }
                        AppPage.Messages -> app.notificationsCenter.notifications.count { !it.isRead }
                        AppPage.SbOrders -> app.receivedOrders.count { it.status == "待确认" }
                        else -> 0
                    }
                    RailNavItem(app, items = item, badgeCount = badge)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .patShadowSoft(5.dp, PatNestShape.Md)
                .clip(PatNestShape.Md)
                .background(Color.White)
                .clickable {
                    if (!app.loggedIn) {
                        app.requestLogin()
                    } else {
                        app.go(if (app.role == AppRole.Owner) AppPage.Profile else AppPage.SbProfile)
                    }
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val ownerName = app.nickname.ifBlank { app.account.ifBlank { "主人" } }
            val shopName = app.myShop?.name?.takeIf { it.isNotBlank() } ?: "机构账号"
            val displayName = when {
                !app.loggedIn -> "游客"
                app.role == AppRole.Owner -> ownerName
                else -> shopName
            }
            val shopType = when (app.myShop?.type) {
                "home" -> "家庭寄养"
                "shop" -> "门店寄养"
                "visit" -> "上门喂养"
                "other" -> "其他"
                else -> "机构"
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayName.take(1),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = PatNestColors.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !app.loggedIn -> "登录后可预约与查看订单"
                        app.role == AppRole.Owner -> "主人 · ${app.account.ifBlank { "未绑定账号" }}"
                        else -> "$shopType · ${app.myShop?.address?.takeIf { it.isNotBlank() } ?: "机构账号"}"
                    },
                    color = PatNestColors.Ink3, fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (app.loggedIn) {
                // 名称列已经占掉剩余宽度，这里只留一点间距，否则退出按钮会被挤出侧栏
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            app.modal = ModalKind.Confirm("退出登录", "确定退出当前账号吗？") { app.logout() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcon.Logout, Modifier.size(15.dp), PatNestColors.Ink3)
                }
            }
        }
    }
}

@Composable
private fun RailNavItem(app: AppState, items: NavItem, badgeCount: Int) {
    val active = app.page == items.page
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (active) Modifier.patShadowSoft(3.dp, PatNestShape.Md) else Modifier)
            .clip(PatNestShape.Md)
            .background(if (active) PatNestColors.SideActive else Color.Transparent)
            .clickable { app.go(items.page) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) PatNestColors.Brand else Color.Transparent),
        )
        AppIcon(
            items.icon,
            Modifier.size(17.dp),
            if (active) PatNestColors.Brand else PatNestColors.Ink3,
        )
        Text(
            items.label,
            color = if (active) PatNestColors.SideInkActive else PatNestColors.SideInk,
            fontSize = 13.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (badgeCount > 0 && app.loggedIn) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(PatNestShape.Pill)
                    .background(PatNestColors.Brand)
                    .padding(horizontal = 8.dp, vertical = 1.5.dp),
            ) {
                Text("$badgeCount", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ---------- 顶部栏 ---------- */

@Composable
private fun TopBar(app: AppState) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.page.title, color = PatNestColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .clip(PatNestShape.Pill)
                    .background(PatNestColors.PanelSoft)
                    .border(1.dp, PatNestColors.Line, PatNestShape.Pill)
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            ) {
                Text("宠栖 Desktop · ${app.page.title}", color = PatNestColors.Ink3, fontSize = 11.sp)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .width(252.dp)
                    .height(36.dp)
                    .clip(PatNestShape.Pill)
                    .background(PatNestColors.Soft)
                    .border(1.dp, PatNestColors.Line, PatNestShape.Pill)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(AppIcon.Search, Modifier.size(15.dp), PatNestColors.Ink3)
                BasicTextField(
                    value = app.sitterSearch,
                    onValueChange = { app.sitterSearch = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = PatNestColors.Ink, fontSize = 13.sp),
                    cursorBrush = SolidColor(PatNestColors.Brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { app.go(AppPage.Sitters) }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (app.sitterSearch.isEmpty()) {
                                Text("搜索机构 / 宠物", color = PatNestColors.Ink3, fontSize = 13.sp)
                            }
                            inner()
                        }
                    },
                )
            }

            if (app.loggedIn) {
                if (app.isSitterAccount) {
                    RoleSwitch(
                        selected = if (app.role == AppRole.Owner) "owner" else "sitter",
                        onSelect = { app.switchRole(if (it == "owner") AppRole.Owner else AppRole.Sitter) },
                    )
                } else {
                    Tag("主人", TagKind.Brand)
                }

                TopIconButton(app, showDot = true) {
                    if (app.role == AppRole.Owner) app.go(AppPage.Messages) else app.toast("暂无新通知")
                }

                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk)))
                        .clickable {
                            app.go(if (app.role == AppRole.Owner) AppPage.Profile else AppPage.SbProfile)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (app.nickname.isNotBlank()) app.nickname.take(1) else "陈",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                TopIconButton(app, showDot = true) {
                    app.go(AppPage.Messages)
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .brandGlow(8.dp, PatNestShape.Pill)
                        .clip(PatNestShape.Pill)
                        .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk)))
                        .clickable { app.requestLogin() }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("登 录", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        ThinDivider()
    }
}

@Composable
private fun TopIconButton(app: AppState, showDot: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(PatNestColors.Soft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcon.Bell, Modifier.size(17.dp), PatNestColors.Ink2)
        if (showDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(PatNestColors.Danger)
                    .border(1.5.dp, Color.White, CircleShape),
            )
        }
    }
}

/* ---------- 页面宿主 ---------- */

@Composable
private fun PageHost(app: AppState) {
    when (app.page) {
        AppPage.Home -> HomePage(app)
        AppPage.Sitters -> SittersPage(app)
        AppPage.SitterDetail -> SitterDetailPage(app)
        AppPage.Pets -> PetsPage(app)
        AppPage.PetEdit -> PetEditPage(app)
        AppPage.Booking -> BookingPage(app)
        AppPage.Pay -> PaymentPage(app)
        AppPage.Orders -> OrdersPage(app)
        AppPage.Dynamics -> DynamicsPage(app)
        AppPage.Review -> ReviewPage(app)
        AppPage.Profile -> ProfilePage(app)
        AppPage.Favorites -> FavoritesPage(app)
        AppPage.Coupons -> CouponsPage(app)
        AppPage.Messages -> MessagesPage(app)
        AppPage.Wallet -> WalletPage(app)
        AppPage.SbDash -> SitterOverviewPage(app)
        AppPage.SbOrders -> SitterOrdersPage(app)
        AppPage.SbCapacity -> SitterCapacityPage(app)
        AppPage.SbPublish -> SitterPublishPage(app)
        AppPage.SbReviews -> SitterReviewsPage(app)
        AppPage.SbIncome -> SitterIncomePage(app)
        AppPage.SbProfile -> SitterProfilePage(app)
        AppPage.SbCoupons -> SitterCouponsPage(app)
        AppPage.Live -> LiveListPage(app)
        AppPage.LiveRoom -> LiveRoomPage(app)
        AppPage.GoLive -> GoLivePage(app)
    }
}
