package team.wd.atnestapp.ui.pages.owner

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PetCard
import team.wd.atnestapp.ui.components.PetAvatar
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.SitterCard
import team.wd.atnestapp.ui.components.StatCard
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.DrawerKind
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.brandGlow
import kotlinx.coroutines.launch

@Composable
fun HomePage(app: AppState) {
    LaunchedEffect(Unit) {
        if (app.sitters.isEmpty() && !app.sittersLoading) app.loadSitters()
        if (app.loggedIn && app.pets.isEmpty() && !app.petsLoading) app.loadPets()
        if (app.loggedIn && app.orders.isEmpty() && !app.ordersLoading) app.loadOrders()
    }

    val currentStay = app.orders.firstOrNull { it.status == "寄养中" || it.status == "已接单" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 44.dp),
    ) {
        HeroBanner(app, currentStay)

        Spacer(Modifier.height(20.dp))

        if (app.loggedIn) {
            val activeCount = app.orders.count { it.status == "寄养中" }
            val pendingPay = app.orders.count { it.status == "待支付" }
            val finished = app.orders.count { it.status == "已结束" }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    "进行中寄养", "$activeCount", "单", "来自我的订单",
                    modifier = Modifier.weight(1f), icon = AppIcon.Paw,
                )
                StatCard(
                    "待支付订单", "$pendingPay", "单",
                    if (pendingPay > 0) "请尽快完成支付" else "暂无待支付",
                    trendDown = pendingPay > 0,
                    modifier = Modifier.weight(1f), icon = AppIcon.Card,
                )
                StatCard(
                    "我的宠物", "${app.pets.size}", "只",
                    if (app.pets.isEmpty()) "还未添加宠物" else "已全部建档",
                    modifier = Modifier.weight(1f), icon = AppIcon.Heart,
                )
                StatCard(
                    "待评价", "$finished", "单",
                    if (finished > 0) "寄养已结束" else "暂无待评价",
                    modifier = Modifier.weight(1f), icon = AppIcon.Star,
                )
            }
        } else {
            val avgScore = if (app.sitters.isEmpty()) 0.0 else app.sitters.map { it.score }.average()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(
                    "在架机构", "${app.sitters.size}", "家", "家庭 / 门店 / 上门",
                    modifier = Modifier.weight(1f), icon = AppIcon.Shop,
                )
                StatCard(
                    "平均评分",
                    if (avgScore <= 0.0) "—" else ((avgScore * 10).toInt() / 10.0).toString(),
                    "分", "来自在架机构评分",
                    modifier = Modifier.weight(1f), icon = AppIcon.Star,
                )
                StatCard(
                    "家庭寄养",
                    "${app.sitters.count { it.type == "家庭寄养" }}",
                    "家", "安静居家环境",
                    modifier = Modifier.weight(1f), icon = AppIcon.Home,
                )
                StatCard(
                    "门店寄养",
                    "${app.sitters.count { it.type == "门店寄养" }}",
                    "家", "专业门店照护",
                    modifier = Modifier.weight(1f), icon = AppIcon.Orders,
                )
            }
        }

        if (app.loggedIn) {
            SectionHeader("我的宠物", "预约时快速选择") {
                LinkText("管理宠物") { app.go(AppPage.Pets) }
            }
            when {
                app.petsLoading && app.pets.isEmpty() -> {
                    Text("正在加载宠物档案…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
                }
                app.pets.isNotEmpty() -> {
                    GridRows(app.pets.take(2).withIndex().toList(), columns = 4, gap = 16.dp) { iv ->
                        PetCard(
                            iv.value,
                            onDetail = { app.drawer = DrawerKind.PetDetail(iv.index) },
                            onBook = { app.go(AppPage.Booking) },
                        )
                    }
                }
                else -> {
                    Panel {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            PetAvatar(44.dp)
                            Column(Modifier.weight(1f)) {
                                Text("还没有宠物档案", color = PatNestColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.petsError ?: "先添加宠物，预约时就能一键选择。",
                                    color = PatNestColors.Ink2, fontSize = 12.5.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            PrimaryButton("新增宠物", { app.go(AppPage.PetEdit) }, height = 37.dp)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Panel {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PetAvatar(44.dp)
                    Column(Modifier.weight(1f)) {
                        Text("登录后建立宠物档案", color = PatNestColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "记录疫苗、体型与健康备注，预约时一键选择，机构照料更安心。",
                            color = PatNestColors.Ink2, fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    PrimaryButton("去登录", { app.requestLogin(AppPage.Pets) }, height = 37.dp)
                }
            }
        }

        SectionHeader(
            "为你推荐",
            if (app.loggedIn) "基于你的宠物与偏好智能匹配" else "热门机构与真实评价",
        ) {
            Tag(if (app.loggedIn) "智能推荐" else "高分好评", TagKind.Brand)
        }
        when {
            app.sittersLoading && app.sitters.isEmpty() -> {
                Text("正在加载推荐机构…", color = PatNestColors.Ink3, fontSize = 12.5.sp)
            }
            app.sitters.isEmpty() -> {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.sittersError ?: "暂无可推荐的机构",
                            color = PatNestColors.Ink2, fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton("重新加载", {
                            app.scope.launch { app.loadSitters(force = true) }
                        }, height = 35.dp)
                    }
                }
            }
            else -> {
                GridRows(app.sitters.take(3).withIndex().toList(), columns = 3, gap = 18.dp) { iv ->
                    SitterCard(
                        iv.value,
                        onOpen = { app.openSitterDetail(iv.index) },
                        onBook = { app.openBooking(iv.index) },
                    )
                }
            }
        }
    }
}

/** 首页顶部欢迎横幅：品牌渐变 + 柔光圆斑 + 大标题。 */
@Composable
private fun HeroBanner(app: AppState, currentStay: team.wd.atnestapp.data.model.Order?) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(PatNestShape.Xl)
            .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.Brand900))),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-96).dp, y = (-34).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 24.dp, y = 48.dp)
                .size(170.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-30).dp, y = (-40).dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Paw, Modifier.size(30.dp), Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    // 昵称没设置时退回账号名，别再显示一个写死的假名字
                    if (app.loggedIn) "欢迎回来，${app.nickname.ifBlank { app.account }.ifBlank { "朋友" }}" else "发现靠谱的寄养机构",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (app.loggedIn) {
                        currentStay?.let {
                            "「${it.pet}」的寄养正在进行中 · ${it.org} · ${it.range}。可随时在订单里查看进度。"
                        } ?: "还没有进行中的寄养，去挑一家靠谱的机构吧。"
                    } else {
                        "浏览机构、查看床位与真实评价，登录后即可预约寄养、管理宠物档案与订单。"
                    },
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Box(
                Modifier
                    .brandGlow(10.dp, PatNestShape.Pill)
                    .clip(PatNestShape.Pill)
                    .background(Color.White)
                    .clickable { app.go(AppPage.Booking) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text(
                    if (app.loggedIn) "立即预约" else "登录后预约",
                    color = PatNestColors.BrandInk, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PatNestColors.Brand),
        )
        Text(title, color = PatNestColors.Ink, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = PatNestColors.Ink2, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
