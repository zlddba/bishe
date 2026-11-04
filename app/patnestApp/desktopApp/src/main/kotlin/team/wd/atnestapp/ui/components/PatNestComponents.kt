package team.wd.atnestapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.data.CalDay
import team.wd.atnestapp.ui.data.Coupon
import team.wd.atnestapp.ui.data.ReviewItemData
import team.wd.atnestapp.data.model.Pet
import team.wd.atnestapp.data.model.Sitter
import team.wd.atnestapp.ui.theme.PatNestColors
import team.wd.atnestapp.ui.theme.PatNestShape
import team.wd.atnestapp.ui.theme.brandGlow
import team.wd.atnestapp.ui.theme.patCard
import team.wd.atnestapp.ui.theme.patShadowSoft

private val sitterGradients = listOf(
    Color(0xFFE0708A) to Color(0xFF8E3B4E),
    Color(0xFFC084C7) to Color(0xFF6A4A96),
    Color(0xFF7FB3E0) to Color(0xFF4A6C96),
    Color(0xFF6FBF8B) to Color(0xFF2F7A5B),
    Color(0xFFE9A15B) to Color(0xFFB56A2E),
)

/** 机构卡片头图的渐变按机构 ID 固定分配，避免每次重组跳色。 */
fun sitterGradient(seed: Long): Pair<Color, Color> {
    val index = ((seed % sitterGradients.size + sitterGradients.size) % sitterGradients.size).toInt()
    return sitterGradients[index]
}

/* ---------- 标签 ---------- */

enum class TagKind { Brand, Ok, Warn, Soft, Danger, Info, Glass }

@Composable
fun Tag(
    text: String,
    kind: TagKind = TagKind.Soft,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (bg, fg) = when (kind) {
        TagKind.Brand -> PatNestColors.Brand50 to PatNestColors.BrandInk
        TagKind.Ok -> PatNestColors.OkSoft to PatNestColors.Ok
        TagKind.Warn -> PatNestColors.WarnSoft to PatNestColors.Warn
        TagKind.Soft -> PatNestColors.Soft to PatNestColors.Ink2
        TagKind.Danger -> PatNestColors.DangerSoft to PatNestColors.Danger
        TagKind.Info -> PatNestColors.InfoSoft to PatNestColors.Info
        TagKind.Glass -> Color.White.copy(alpha = 0.92f) to PatNestColors.Ink
    }
    Row(
        modifier = modifier
            .clip(PatNestShape.Pill)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        }
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------- 按钮 ---------- */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    icon: AppIcon? = null,
) {
    Box(
        modifier = modifier
            .height(height)
            .brandGlow(8.dp, PatNestShape.Pill)
            .clip(PatNestShape.Pill)
            .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            // 没有左右留白时，文字会直接顶到胶囊边缘，白色字看起来像溢出到按钮外面
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                AppIcon(icon, Modifier.size(15.dp), Color.White)
            }
            Text(text, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    textColor: Color = PatNestColors.Ink,
    icon: AppIcon? = null,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(PatNestShape.Pill)
            .background(Color.White)
            .border(1.dp, PatNestColors.Line, PatNestShape.Pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                AppIcon(icon, Modifier.size(15.dp), textColor)
            }
            Text(text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PatNestColors.BrandInk,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun DangerLinkText(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LinkText(text, modifier = modifier, color = PatNestColors.Danger, onClick = onClick)
}

/* ---------- 面板与页头 ---------- */

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .patCard(PatNestShape.Lg, 9.dp)
            .padding(contentPadding),
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text(title, color = PatNestColors.Ink, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, color = PatNestColors.Ink3, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke(this)
            }
        }
        content()
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(title, color = PatNestColors.Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = PatNestColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/* ---------- 表单控件 ---------- */

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = PatNestColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp,
    password: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = TextStyle(color = PatNestColors.Ink, fontSize = 14.sp),
        singleLine = singleLine,
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        cursorBrush = SolidColor(PatNestColors.Brand),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(minHeight) else Modifier.heightIn(min = minHeight))
                    .background(PatNestColors.PanelSoft, RoundedCornerShape(12.dp))
                    .border(1.dp, PatNestColors.Line, RoundedCornerShape(12.dp))
                    .padding(
                        start = 14.dp,
                        end = if (password) 4.dp else 14.dp,
                        top = if (singleLine) 0.dp else 11.dp,
                        bottom = if (singleLine) 0.dp else 11.dp,
                    ),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(
                    Modifier.weight(1f),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = PatNestColors.Ink3, fontSize = 14.sp)
                    }
                    inner()
                }
                if (password) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { passwordVisible = !passwordVisible },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            if (passwordVisible) AppIcon.EyeOff else AppIcon.Eye,
                            Modifier.size(17.dp),
                            PatNestColors.Ink3,
                        )
                    }
                }
            }
        },
    )
}

/** 分段选择组：胶囊容器 + 白色浮起的选中项。 */
@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier
            .clip(PatNestShape.Pill)
            .background(PatNestColors.PanelSoft)
            .border(1.dp, PatNestColors.Line, PatNestShape.Pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .then(
                        if (active) Modifier.patShadowSoft(4.dp, PatNestShape.Pill) else Modifier,
                    )
                    .clip(PatNestShape.Pill)
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
            ) {
                Text(
                    option,
                    color = if (active) PatNestColors.BrandInk else PatNestColors.Ink2,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun SoftCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(16.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) PatNestColors.Brand else Color.White)
            .border(1.5.dp, if (checked) PatNestColors.Brand else PatNestColors.Ink3, RoundedCornerShape(5.dp))
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            AppIcon(AppIcon.Check, Modifier.size(10.dp), Color.White)
        }
    }
}

@Composable
fun SoftSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(PatNestShape.Pill)
            .background(if (checked) PatNestColors.Brand else PatNestColors.Line)
            .clickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** 单选圆点，用于支付方式选择 */
@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .border(1.6.dp, if (selected) PatNestColors.Brand else PatNestColors.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(PatNestColors.Brand))
        }
    }
}

/* ---------- 评分 ---------- */

@Composable
fun StarRating(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    interactive: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (interactive) 4.dp else 1.dp)) {
        for (i in 1..5) {
            val filled = i <= score
            StarIcon(
                modifier = Modifier
                    .size(size)
                    .then(if (interactive && onChange != null) Modifier.clickable { onChange(i) } else Modifier),
                filled = filled,
                tint = PatNestColors.Star,
            )
        }
    }
}

/* ---------- 统计卡片 ---------- */

@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String = "",
    trend: String = "",
    trendDown: Boolean = false,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
) {
    Column(
        modifier
            .patCard(PatNestShape.Lg, 9.dp)
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PatNestColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (icon != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(PatNestShape.Pill)
                        .background(PatNestColors.Brand50),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(icon, Modifier.size(15.dp), PatNestColors.Brand)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = PatNestColors.Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(unit, color = PatNestColors.Ink3, fontSize = 13.sp, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
        if (trend.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(
                trend,
                color = if (trendDown) PatNestColors.Danger else PatNestColors.Ink3,
                fontSize = 11.5.sp,
            )
        }
    }
}

/* ---------- 宠物头像 ---------- */

@Composable
fun PetAvatar(size: Dp = 38.dp, modifier: Modifier = Modifier, photoUrl: String = "") {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(PatNestColors.BrandSoft, PatNestColors.Brand50))),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNotBlank()) {
            RemoteImage(
                url = photoUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "宠物照片",
                shape = CircleShape,
            )
        } else {
            AppIcon(AppIcon.Paw, Modifier.size(size * 0.58f), PatNestColors.BrandInk)
        }
    }
}

/* ---------- 机构卡片 ---------- */

/** 一排可点击的缩略图；点哪张就放大哪张，超过 max 的部分折叠成 +N */
@Composable
fun PhotoStrip(
    urls: List<String>,
    modifier: Modifier = Modifier,
    max: Int = 6,
    thumbWidth: Dp = 96.dp,
    thumbHeight: Dp = 70.dp,
    onClick: (String) -> Unit,
) {
    val shown = urls.take(max)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { url ->
            RemoteImage(
                url = url,
                modifier = Modifier.size(thumbWidth, thumbHeight).clickable { onClick(url) },
                contentDescription = "照片缩略图",
            )
        }
        if (urls.size > shown.size) {
            Box(Modifier.size(thumbWidth, thumbHeight).clip(RoundedCornerShape(10.dp)).background(PatNestColors.Soft), contentAlignment = Alignment.Center) {
                Text("+${urls.size - shown.size}", color = PatNestColors.Ink2, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SitterCard(
    sitter: Sitter,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    onOpen: () -> Unit,
    onBook: () -> Unit,
) {
    val gradient = sitterGradient(sitter.id)
    Column(
        modifier
            .patCard(PatNestShape.Lg, 10.dp)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
        ) {
            if (sitter.coverPhoto.isNotBlank()) {
                RemoteImage(
                    url = sitter.coverPhoto,
                    modifier = Modifier.matchParentSize(),
                    contentDescription = "${sitter.name} 封面",
                    shape = RoundedCornerShape(0.dp),
                )
            } else {
                AppIcon(
                    AppIcon.Image,
                    Modifier.align(Alignment.Center).size(34.dp),
                    Color.White.copy(alpha = 0.9f),
                )
            }
            Tag(
                sitter.type,
                TagKind.Glass,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppIcon(AppIcon.Shop, Modifier.size(14.dp), PatNestColors.Brand)
                Text(sitter.name, color = PatNestColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StarRating(sitter.score, size = 12.dp)
                Text("· ${sitter.score}.0", color = PatNestColors.Ink2, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${sitter.addr} · 容量 ${sitter.cap}",
                color = PatNestColors.Ink2, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sitter.tags,
                color = PatNestColors.Ink3, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            ThinDivider()
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥${sitter.price}", color = PatNestColors.BrandInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(" /${sitter.unit}", color = PatNestColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selectable) {
                    Text("选择 ›", color = PatNestColors.BrandInk, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    PrimaryButton("立即预约", onBook, height = 33.dp)
                }
            }
        }
    }
}

/* ---------- 宠物卡片 ---------- */

@Composable
fun PetCard(
    pet: Pet,
    modifier: Modifier = Modifier,
    onDetail: () -> Unit,
    onBook: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier
            .patCard(PatNestShape.Lg, 9.dp)
            .clickable(onClick = onDetail)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PetAvatar(photoUrl = pet.photos.firstOrNull().orEmpty())
            Column {
                Text(pet.name, color = PatNestColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(pet.breed, color = PatNestColors.Ink3, fontSize = 11.5.sp)
            }
            if (onDelete != null) {
                Spacer(Modifier.weight(1f))
                DangerLinkText("删除", onClick = onDelete)
            }
        }
        Spacer(Modifier.height(12.dp))
        PetInfoLine("体型", pet.size)
        PetInfoLine("年龄", pet.age)
        PetInfoLine("疫苗", pet.vaccine)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("详情", onDetail, Modifier.weight(1f), height = 33.dp)
            PrimaryButton("预约", onBook, Modifier.weight(1f), height = 33.dp)
        }
    }
}

@Composable
private fun PetInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = PatNestColors.Ink3, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = PatNestColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/* ---------- 表格 ---------- */

@Composable
fun RowScope.HCell(text: String, weight: Float) {
    Text(
        text,
        color = PatNestColors.Ink3,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(weight),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RowScope.TCell(text: String, weight: Float, dim: Boolean = false, mono: Boolean = false) {
    Text(
        text,
        color = if (dim) PatNestColors.Ink3 else if (mono) PatNestColors.Ink2 else PatNestColors.Ink,
        fontSize = 12.5.sp,
        fontWeight = if (dim) FontWeight.Normal else FontWeight.Medium,
        modifier = Modifier.weight(weight),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PatNestColors.Line2))
}

/* ---------- 键值行 ---------- */

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = PatNestColors.Ink, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, color = PatNestColors.Ink2, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/* ---------- 日历卡片 ---------- */

@Composable
fun SlotTag(text: String, kind: String, fill: Boolean = true) {
    val (bg, fg) = when (kind) {
        "free" -> PatNestColors.OkSoft to PatNestColors.Ok
        "busy" -> PatNestColors.WarnSoft to PatNestColors.Warn
        else -> PatNestColors.DangerSoft to PatNestColors.Danger
    }
    Box(
        Modifier
            // 卡片里要铺满一行；放进横向 Row 时必须包内容，否则会把兄弟节点挤成一个字一行
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(PatNestShape.Pill)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CalDayCard(day: CalDay, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (day.holiday) PatNestColors.Brand50 else Color.White)
            .border(1.dp, if (day.holiday) PatNestColors.Brand200 else PatNestColors.Line, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.date,
            color = if (day.holiday) PatNestColors.BrandInk else PatNestColors.Ink,
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (day.holiday) "${day.week} · 假期" else day.week,
            color = PatNestColors.Ink3, fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        day.slots.forEach { (text, kind) ->
            SlotTag(text, kind)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/* ---------- 优惠券 ---------- */

@Composable
fun CouponCard(coupon: Coupon, onUse: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .patCard(PatNestShape.Lg, 9.dp),
    ) {
        Column(
            Modifier
                .width(118.dp)
                .background(Brush.linearGradient(listOf(PatNestColors.Brand, PatNestColors.BrandInk)))
                .padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(coupon.value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(coupon.cond, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(coupon.title, color = PatNestColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (coupon.issuer.isNotBlank()) {
                Text(
                    "发券方：${coupon.issuer}",
                    color = PatNestColors.BrandInk, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                if (coupon.from.isBlank()) "有效期至 ${coupon.to}" else "${coupon.from} - ${coupon.to}",
                color = PatNestColors.Ink3, fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (coupon.state) {
                        "可用" -> "点击去使用 ›"
                        "可领取" -> "领取后下单自动抵扣"
                        else -> coupon.state
                    },
                    color = PatNestColors.Ink3, fontSize = 11.5.sp,
                )
                Spacer(Modifier.weight(1f))
                when (coupon.state) {
                    "可用" -> PrimaryButton("立即使用", onUse, height = 30.dp)
                    "可领取" -> PrimaryButton("领取", onUse, height = 30.dp)
                    else -> Tag(coupon.state, TagKind.Soft)
                }
                if (onDelete != null) {
                    Spacer(Modifier.width(8.dp))
                    GhostButton("删除", onDelete, height = 30.dp, textColor = PatNestColors.Danger)
                }
            }
        }
    }
}

/* ---------- 评价条目 ---------- */

@Composable
fun ReviewItem(review: ReviewItemData, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarRating(review.score, size = 12.dp)
            Text(review.user, color = PatNestColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(review.time, color = PatNestColors.Ink3, fontSize = 11.5.sp)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing(this)
            }
        }
        Text(
            review.txt,
            color = PatNestColors.Ink2, fontSize = 12.5.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/* ---------- 服务价格行 ---------- */

@Composable
fun ServiceRow(name: String, price: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = PatNestColors.Ink2, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        Text(price, color = PatNestColors.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 加购服务的计价单位：后端存的是 per_order / per_day 这类代码，
 * 直接拼进界面会变成"¥30/per_order"，这里统一翻译成中文。
 */
fun addonUnitLabel(unit: String): String = when (unit) {
    "per_order" -> "次"
    "per_day" -> "天"
    else -> unit.ifBlank { "次" }
}

/* ---------- 虚线上传框 ---------- */

@Composable
fun DashedUploadBox(
    title: String,
    sub: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lineColor = PatNestColors.Brand200
    Column(
        modifier
            .fillMaxWidth()
            .background(PatNestColors.Brand50, RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset.Zero,
                    size = size,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(AppIcon.Image, Modifier.size(30.dp), PatNestColors.BrandInk)
        Spacer(Modifier.height(8.dp))
        Text(title, color = PatNestColors.BrandInk, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
        if (sub.isNotEmpty()) {
            Text(sub, color = PatNestColors.Brand300, fontSize = 11.sp)
        }
    }
}

/* ---------- 角色切换 ---------- */

@Composable
fun RoleSwitch(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(PatNestShape.Pill)
            .background(PatNestColors.Soft)
            .border(1.dp, PatNestColors.Line, PatNestShape.Pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("owner" to "主人", "sitter" to "机构").forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .then(if (active) Modifier.patShadowSoft(3.dp, PatNestShape.Pill) else Modifier)
                    .clip(PatNestShape.Pill)
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    color = if (active) PatNestColors.BrandInk else PatNestColors.Ink2,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/* ---------- 网格布局 ---------- */

@Composable
fun <T> GridRows(
    items: List<T>,
    columns: Int,
    gap: Dp,
    itemContent: @Composable (T) -> Unit,
) {
    items.chunked(columns).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            rowItems.forEach { item ->
                Box(Modifier.weight(1f)) { itemContent(item) }
            }
            repeat(columns - rowItems.size) {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(gap))
    }
}

/* ---------- 宠物档案小头像 ---------- */

@Composable
fun EmptyPawPlaceholder(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    AppIcon(AppIcon.Image, modifier.size(size), Color.White.copy(alpha = 0.9f))
}
