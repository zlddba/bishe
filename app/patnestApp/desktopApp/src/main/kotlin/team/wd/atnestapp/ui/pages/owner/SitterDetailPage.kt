package team.wd.atnestapp.ui.pages.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.AppPage
import team.wd.atnestapp.ui.AppState
import team.wd.atnestapp.ui.components.AppIcon
import team.wd.atnestapp.ui.components.CalDayCard
import team.wd.atnestapp.ui.components.GhostButton
import team.wd.atnestapp.ui.components.GridRows
import team.wd.atnestapp.ui.components.KeyValueRow
import team.wd.atnestapp.ui.components.LinkText
import team.wd.atnestapp.ui.components.Panel
import team.wd.atnestapp.ui.components.PrimaryButton
import team.wd.atnestapp.ui.components.ReviewItem
import team.wd.atnestapp.ui.components.ServiceRow
import team.wd.atnestapp.ui.components.StarRating
import team.wd.atnestapp.ui.components.Tag
import team.wd.atnestapp.ui.components.TagKind
import team.wd.atnestapp.ui.components.ThinDivider
import team.wd.atnestapp.ui.components.sitterGradient
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.ui.data.CalDay
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.patCard
import kotlinx.coroutines.launch

@Composable
fun SitterDetailPage(app: AppState) {
    val sitter = app.sitters.getOrNull(app.sitterIndex)
    if (sitter == null) {
        Column(
            Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (app.sittersError != null) "机构信息加载失败：${app.sittersError}" else "正在加载机构信息…",
                color = PatNestColors.Ink2,
                fontSize = 13.sp,
            )
        }
        return
    }
    val gradient = sitterGradient(sitter.id)

    LaunchedEffect(sitter.id) { app.loadSitterDetailExtras(sitter.id) }
    val goodRate = if (app.detailReviews.isEmpty()) {
        null
    } else {
        app.detailReviews.count { it.overallScore >= 4 } * 100 / app.detailReviews.size
    }
    val isFavorite = app.favorites.any { it.id == sitter.id }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("返回列表", { app.go(AppPage.Sitters) }, height = 34.dp, icon = AppIcon.Back)
            Spacer(Modifier.weight(1f))
            GhostButton(if (isFavorite) "已收藏" else "收藏", {
                if (!app.loggedIn) {
                    app.requestLogin()
                } else {
                    app.scope.launch {
                        if (isFavorite) app.removeFavorite(sitter.id) else app.addFavorite(sitter.id)
                    }
                }
            }, height = 36.dp, icon = if (isFavorite) AppIcon.Check else AppIcon.Heart)
        }

        Spacer(Modifier.height(14.dp))

        Panel(contentPadding = PaddingValues(0.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-64).dp, y = (-36).dp)
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-20).dp, y = 40.dp)
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                AppIcon(AppIcon.Image, Modifier.align(Alignment.Center).size(44.dp), Color.White.copy(alpha = 0.9f))
                Tag(
                    sitter.type,
                    TagKind.Glass,
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(sitter.name, color = PatNestColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Tag(
                        when (sitter.status) {
                            "active" -> "在架"
                            "pending" -> "待审核"
                            "inactive" -> "已下架"
                            "banned" -> "已封禁"
                            else -> sitter.status
                        },
                        when (sitter.status) {
                            "active" -> TagKind.Ok
                            "pending" -> TagKind.Warn
                            else -> TagKind.Danger
                        },
                        dot = sitter.status == "active",
                    )
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StarRating(sitter.score, size = 13.dp)
                    Text(
                        "· ${sitter.score}.0 分" + (goodRate?.let { " · 好评率 $it%" } ?: ""),
                        color = PatNestColors.Ink2, fontSize = 13.sp,
                    )
                }
                Text(
                    "地址：${sitter.addr}　容量 ${sitter.cap}　负责人 ${sitter.owner}　${sitter.tel}",
                    color = PatNestColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "标签：${sitter.tags}",
                    color = PatNestColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(title = "寄养信息", modifier = Modifier.weight(1f)) {
                KeyValueRow("寄养类型", sitter.type)
                KeyValueRow("所在地址", sitter.addr)
                KeyValueRow("可同时容量", sitter.cap)
                KeyValueRow("负责人", sitter.owner)
                KeyValueRow("联系电话", sitter.tel)
                KeyValueRow("环境标签", sitter.tags)
            }
            Panel(title = "机构简介", modifier = Modifier.weight(1f)) {
                Text(sitter.desc, color = PatNestColors.Ink2, fontSize = 13.5.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(14.dp))
                Text("服务项目", color = PatNestColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                if (app.detailAddons.isNotEmpty()) {
                    app.detailAddons.forEachIndexed { i, addon ->
                        ServiceRow(addon.name, "¥${addon.price.toInt()}/${addon.unit}")
                        if (i < app.detailAddons.lastIndex) ThinDivider()
                    }
                } else {
                    Text("暂无加购服务", color = PatNestColors.Ink2, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Panel(
            title = "容量日历",
            subtitle = "近 7 日床位排期",
            trailing = { LinkText("去预约 ›") { app.openBooking(app.sitterIndex) } },
        ) {
            if (app.detailCapacity.isNotEmpty()) {
                GridRows(app.detailCapacity.map { it.toDetailCalDay() }, columns = 7, gap = 10.dp) { day ->
                    CalDayCard(day)
                }
            } else {
                Text(
                    if (app.detailExtrasLoading) "正在加载容量…" else "暂无容量数据",
                    color = PatNestColors.Ink2, fontSize = 12.5.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val reviewCount = app.detailReviews.size
        val avgScore = if (reviewCount == 0) 0.0 else app.detailReviews.map { it.overallScore }.average()
        Panel(
            title = "主人评价",
            subtitle = if (reviewCount == 0) "暂无评价" else "共 $reviewCount 条 · 平均 ${((avgScore * 10).toInt() / 10.0)} 分",
            trailing = { Tag("综合评分", TagKind.Brand) },
        ) {
            if (app.detailReviews.isEmpty()) {
                Text(
                    if (app.detailExtrasLoading) "正在加载评价…" else "还没有主人评价",
                    color = PatNestColors.Ink2, fontSize = 12.5.sp,
                )
            } else {
                app.detailReviews.forEachIndexed { i, review ->
                    ReviewItem(
                        team.wd.atnestapp.ui.data.ReviewItemData(
                            user = "主人",
                            score = review.overallScore,
                            time = formatReviewTime(review.createdAt),
                            txt = review.ownerComment.ifBlank { "（无文字评价）" },
                        ),
                    )
                    if (i < app.detailReviews.lastIndex) ThinDivider()
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .patCard(PatNestShape.Lg, 12.dp)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${sitter.type} · ${if (sitter.unit == "次") "次单价" else "日单价"}",
                    color = PatNestColors.Ink3, fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥${sitter.price}", color = PatNestColors.BrandInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(" /${sitter.unit}", color = PatNestColors.Ink3, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            GhostButton("联系机构", {
                if (!app.loggedIn) {
                    app.requestLogin()
                } else {
                    app.scope.launch { app.contactSitter(sitter.id) }
                }
            })
            Spacer(Modifier.width(10.dp))
            PrimaryButton("立即预约", { app.openBooking(app.sitterIndex) }, height = 44.dp)
        }
    }
}

private fun CapacityItemDto.toDetailCalDay(): CalDay {
    val week = runCatching {
        when (java.time.LocalDate.parse(date).dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }.getOrDefault("")
    val kind = when {
        full -> "full"
        available <= 1 -> "busy"
        else -> "free"
    }
    return CalDay(
        date = if (date.length >= 10) date.substring(5).replace('-', '/') else date,
        week = week,
        slots = listOf("余 ${available}/${capacity}" to kind),
    )
}

private fun formatReviewTime(raw: String): String {
    if (raw.length < 10) return raw
    return raw.substring(5, 10).replace('-', '/')
}
