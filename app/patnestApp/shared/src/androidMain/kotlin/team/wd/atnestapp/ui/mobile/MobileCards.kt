package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.data.api.AddonDto
import team.wd.atnestapp.data.api.CapacityItemDto
import team.wd.atnestapp.data.api.DynamicDto
import team.wd.atnestapp.data.api.EarningsItemDto
import team.wd.atnestapp.data.api.NotificationDto
import team.wd.atnestapp.data.api.PetStayDto
import team.wd.atnestapp.data.api.PointsEntryDto
import team.wd.atnestapp.data.api.PriceRuleDto
import team.wd.atnestapp.data.api.PublicReviewDto
import team.wd.atnestapp.data.api.ReceivedOrderDto
import team.wd.atnestapp.data.api.SettlementDto
import team.wd.atnestapp.data.api.SitterReviewDto
import team.wd.atnestapp.data.api.TicketDto
import team.wd.atnestapp.data.model.Order
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.components.AppIcon

@Composable
fun CoverBox(url: String, modifier: Modifier = Modifier, iconSize: Int = 22) {
    if (url.isNotBlank()) {
        MobileImage(url = url, modifier = modifier, contentDescription = null)
    } else {
        Box(modifier.clip(MobileShape.Md).background(MobileColors.Brand50), contentAlignment = Alignment.Center) {
            AppIcon(AppIcon.Paw, Modifier.size(iconSize.dp), MobileColors.Brand300)
        }
    }
}

/** 机构卡片：封面、名称、评分、地址、价格。列表滚动时靠它承担主要信息密度 */
@Composable
fun SitterCard(sitter: Sitter, onClick: () -> Unit, onFavorite: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoverBox(sitter.coverPhoto, Modifier.size(82.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sitter.name,
                    color = MobileColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                StatusTag(sitter.type, TagTone.Neutral)
            }
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StarRow(sitter.score, size = 11.dp)
                Text(sitter.tags, color = MobileColors.Ink3, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                sitter.addr,
                color = MobileColors.Ink2,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("¥" + sitter.price, color = MobileColors.BrandDeep, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(" / " + sitter.unit, color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
                    }
                    Text("可接待 " + sitter.cap, color = MobileColors.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                if (onFavorite != null) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MobileColors.Brand50).clickable(onClick = onFavorite),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Heart, Modifier.size(16.dp), MobileColors.BrandDeep)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                order.org,
                color = MobileColors.Ink,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusTag(order.status, statusTone(order.rawStatus), dot = true)
        }
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.range, color = MobileColors.Ink2, fontSize = 12.5.sp)
            Text(order.days, color = MobileColors.Ink3, fontSize = 11.5.sp)
            Text(order.pet, color = MobileColors.Ink3, fontSize = 11.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        MsDivider()
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                order.no,
                color = MobileColors.Ink3,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(order.fee, color = MobileColors.BrandDeep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PetCard(pet: Pet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverBox(pet.photos.firstOrNull().orEmpty(), Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pet.name, color = MobileColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Text(pet.type + " · " + pet.size, color = MobileColors.Ink2, fontSize = 12.sp)
            }
            Text(pet.breed + " · " + pet.age, color = MobileColors.Ink3, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
        StatusTag(pet.cert, certTone(pet.cert))
    }
}

fun certTone(cert: String): TagTone = when (cert) {
    "已核验" -> TagTone.Ok
    "未通过" -> TagTone.Danger
    "缺证明" -> TagTone.Neutral
    else -> TagTone.Warn
}

/** 优惠券的通用外观：左侧金额、右侧说明与动作，机构端和主人端共用一张版式 */
@Composable
fun CouponCell(
    title: String,
    amount: String,
    rule: String,
    status: String,
    tone: TagTone,
    action: String = "",
    onAction: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(amount, color = MobileColors.BrandDeep, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(rule, color = MobileColors.Ink3, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.width(1.dp).height(44.dp).background(MobileColors.Line2))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MobileColors.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            StatusTag(status, tone)
        }
        if (onDelete != null) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MobileColors.PanelSoft).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Close, Modifier.size(13.dp), MobileColors.Ink3)
            }
        }
        if (onAction != null) {
            Spacer(Modifier.width(8.dp))
            TonalButton(action, onAction)
        }
    }
}

@Composable
fun NotificationCell(item: NotificationDto, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(if (item.isRead) MobileColors.Panel else MobileColors.Brand50)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MobileColors.PanelSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcon.Bell, Modifier.size(16.dp), if (item.isRead) MobileColors.Ink3 else MobileColors.BrandDeep)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, color = MobileColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!item.isRead) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MobileColors.Danger))
                }
            }
            Text(item.body, color = MobileColors.Ink2, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun DynamicCard(dynamic: DynamicDto, liked: Boolean, onLike: () -> Unit, onPhoto: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dynamic.sitterName, color = MobileColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(dynamic.orderNo, color = MobileColors.Ink3, fontSize = 11.sp)
        }
        Text(dynamic.content, color = MobileColors.Ink2, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
        if (dynamic.photos.isNotEmpty()) {
            PhotoGrid(dynamic.photos, Modifier.padding(top = 10.dp), size = 82.dp, onClick = onPhoto)
        }
        Row(
            Modifier.padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dynamic.tags.joinToString("   "), color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(MobileShape.Pill)
                    .background(if (liked) MobileColors.Brand100 else MobileColors.PanelSoft)
                    .clickable(onClick = onLike)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcon.Heart, Modifier.size(13.dp), if (liked) MobileColors.BrandDeep else MobileColors.Ink3)
                    Text(if (liked) "已点赞" else "点赞", color = if (liked) MobileColors.BrandDeep else MobileColors.Ink2, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CapacityCell(item: CapacityItemDto, onClick: () -> Unit) {
    val tone = when {
        item.available <= 0 -> MobileColors.Danger
        item.available <= 1 -> MobileColors.Warn
        else -> MobileColors.Ok
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(item.date.takeLast(5), color = MobileColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(weekdayLabel(item.date), color = MobileColors.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(MobileShape.Pill).background(MobileColors.NeutralSoft)) {
            val ratio = if (item.capacity <= 0) 0f else (item.used.toFloat() / item.capacity.toFloat()).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(ratio).height(8.dp).clip(MobileShape.Pill).background(MobileColors.Brand))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
            Text("余 " + item.available, color = tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("共 " + item.capacity + " 位", color = MobileColors.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

fun weekdayLabel(date: String): String = when (runCatching { java.time.LocalDate.parse(date).dayOfWeek.value }.getOrNull()) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> ""
}

/** 机构端收到的订单：状态文案由调用方给，这里只负责版式与动作按钮 */
@Composable
fun ReceivedOrderCell(
    order: ReceivedOrderDto,
    statusText: String,
    money: (Double) -> String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                order.ownerName.ifBlank { "主人" },
                color = MobileColors.Ink,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusTag(statusText, statusTone(order.status), dot = true)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(order.orderNo, color = MobileColors.Ink3, fontSize = 11.5.sp)
            Text(order.petCount.toString() + " 只", color = MobileColors.Ink2, fontSize = 12.sp)
            Text(money(order.totalAmount), color = MobileColors.BrandDeep, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            shortRange(order.startDate, order.endDate) + " 共 " + order.totalDays + " 天",
            color = MobileColors.Ink2,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (order.status == "confirmed") {
                GhostButton("暂时接不了", onReject, height = 38.dp, textColor = MobileColors.Danger, modifier = Modifier.weight(1f))
                PrimaryButton("确认接单", onAccept, modifier = Modifier.weight(1.4f))
            } else if (order.status == "accepted" || order.status == "staying") {
                PrimaryButton("办理离店", onComplete, modifier = Modifier.fillMaxWidth())
            } else {
                Text("该状态无需机构操作", color = MobileColors.Ink3, fontSize = 12.sp)
            }
        }
    }
}

fun shortRange(start: String, end: String): String {
    val s = if (start.length >= 10) start.substring(5, 10) else start
    val e = if (end.length >= 10) end.substring(5, 10) else end
    return s + " - " + e
}

@Composable
fun ReviewCell(review: SitterReviewDto, onReply: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarRow(review.overallScore, size = 13.dp)
            Spacer(Modifier.width(10.dp))
            Text(review.createdAt.take(10), color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Text(
            review.ownerComment?.takeIf { it.isNotBlank() } ?: "主人没有留下文字评价",
            color = MobileColors.Ink,
            fontSize = 13.5.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniScore("服务", review.serviceScore)
            MiniScore("环境", review.environmentScore)
            MiniScore("卫生", review.hygieneScore)
        }
        val reply = review.replyText
        if (!reply.isNullOrBlank()) {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(MobileShape.Md).background(MobileColors.Brand50).padding(11.dp),
            ) {
                Text("机构回复：" + reply, color = MobileColors.Brand900, fontSize = 12.5.sp)
            }
        } else {
            Spacer(Modifier.height(11.dp))
            TonalButton("回复评价", onReply)
        }
    }
}

@Composable
private fun MiniScore(label: String, value: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MobileColors.Ink3, fontSize = 11.5.sp)
        Text(value.toString(), color = MobileColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PublicReviewCell(review: PublicReviewDto, onPhoto: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MobileColors.Brand50),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.User, Modifier.size(14.dp), MobileColors.Brand300)
            }
            Spacer(Modifier.width(9.dp))
            Text(review.ownerName.ifBlank { "匿名用户" }, color = MobileColors.Ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            Text(review.createdAt.take(10), color = MobileColors.Ink3, fontSize = 11.sp)
        }
        Row(Modifier.padding(top = 8.dp)) {
            StarRow(review.overallScore, size = 12.dp)
        }
        Text(
            review.ownerComment.ifBlank { "主人没有留下文字评价" },
            color = MobileColors.Ink2,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (review.ownerPhotos.isNotEmpty()) {
            PhotoGrid(review.ownerPhotos, Modifier.padding(top = 9.dp), size = 72.dp, onClick = onPhoto)
        }
        if (review.replyText.isNotBlank()) {
            Box(
                Modifier.fillMaxWidth().padding(top = 9.dp).clip(MobileShape.Md).background(MobileColors.Brand50).padding(10.dp),
            ) {
                Text("机构回复：" + review.replyText, color = MobileColors.Brand900, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SimpleRow(
    title: String,
    subtitle: String = "",
    trailing: String = "",
    tone: TagTone = TagTone.Neutral,
    trailingTag: String = "",
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MobileColors.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        if (trailing.isNotBlank()) {
            Text(trailing, color = MobileColors.BrandDeep, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (trailingTag.isNotBlank()) {
            StatusTag(trailingTag, tone)
        }
    }
}

@Composable
fun SettlementCell(item: SettlementDto, money: (Double) -> String) {
    val label = when (item.status) {
        "pending" -> "待审核"
        "approved" -> "待打款"
        "paid" -> "已打款"
        "rejected" -> "已驳回"
        else -> item.status
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.periodStart.take(10) + " 至 " + item.periodEnd.take(10), color = MobileColors.Ink, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            StatusTag(label, statusTone(item.status))
        }
        Row(Modifier.padding(top = 9.dp)) {
            SmallMetric("流水", money(item.grossAmount))
            SmallMetric("服务费", money(item.commissionAmount))
            SmallMetric("到账", money(item.netAmount), highlight = true)
        }
    }
}

@Composable
fun RowScope.SmallMetric(label: String, value: String, highlight: Boolean = false) {
    Column(Modifier.weight(1f)) {
        Text(label, color = MobileColors.Ink3, fontSize = 11.sp)
        Text(
            value,
            color = if (highlight) MobileColors.BrandDeep else MobileColors.Ink,
            fontSize = 13.5.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun TicketCell(item: TicketDto) {
    val label = when (item.status) {
        "open" -> "处理中"
        "replied" -> "已回复"
        "closed" -> "已关闭"
        else -> item.status
    }
    SimpleRow(
        title = item.title,
        subtitle = item.ticketNo + " · " + item.createdAt.take(10),
        trailingTag = label,
        tone = statusTone(item.status),
    )
}

@Composable
fun AddonCell(addon: AddonDto, price: String, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(addon.name, color = MobileColors.Ink, fontSize = 14.sp)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(price, color = MobileColors.BrandDeep, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(addonUnitText(addon.unit), color = MobileColors.Ink3, fontSize = 11.5.sp)
            }
        }
        LinkButton("编辑", onEdit, color = MobileColors.Ink2)
        Spacer(Modifier.width(6.dp))
        MsSwitch(addon.enabled, { onToggle() })
    }
}

fun addonUnitText(unit: String): String = when (unit) {
    "per_order" -> "按订单收取"
    "per_day" -> "按天收取"
    "per_time" -> "按次收取"
    else -> unit
}

@Composable
fun PriceRuleCell(rule: PriceRuleDto, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rule.name, color = MobileColors.Ink, fontSize = 14.sp)
            Text(rule.startDate.take(10) + " 至 " + rule.endDate.take(10), color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(
            "×" + rule.multiplier,
            color = MobileColors.Warn,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 10.dp),
        )
        LinkButton("删除", onDelete, color = MobileColors.Danger)
    }
}

@Composable
fun PointsCell(entry: PointsEntryDto) {
    SimpleRow(
        title = entry.reason.ifBlank { "积分变动" },
        subtitle = entry.createdAt.take(16),
        trailing = (if (entry.change > 0) "+" else "") + entry.change.toString(),
    )
}

@Composable
fun PetStayCell(stay: PetStayDto) {
    SimpleRow(
        title = stay.sitterName.ifBlank { "机构" },
        subtitle = stay.orderNo + " · " + shortRange(stay.startDate, stay.endDate),
        trailingTag = stay.status,
    )
}

@Composable
fun EarningsCell(item: EarningsItemDto, money: (Double) -> String) {
    SimpleRow(
        title = item.orderNo,
        subtitle = item.date.take(10) + " · " + item.owner,
        trailing = money(item.amount),
    )
}
