package team.wd.atnestapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.wd.atnestapp.ui.components.AppIcon

enum class TagTone { Ok, Warn, Danger, Brand, Info, Neutral }

fun tagColors(tone: TagTone): Pair<Color, Color> = when (tone) {
    TagTone.Ok -> MobileColors.OkSoft to MobileColors.Ok
    TagTone.Warn -> MobileColors.WarnSoft to MobileColors.WarnInk
    TagTone.Danger -> MobileColors.DangerSoft to MobileColors.Danger
    TagTone.Brand -> MobileColors.Brand100 to MobileColors.BrandDeep
    TagTone.Info -> MobileColors.InfoSoft to MobileColors.Info
    TagTone.Neutral -> MobileColors.NeutralSoft to MobileColors.Ink2
}

/** 订单状态到语义色的映射，保证两端对同一个状态的观感一致 */
fun statusTone(rawStatus: String): TagTone = when (rawStatus) {
    "pending_pay" -> TagTone.Warn
    "confirmed" -> TagTone.Info
    "accepted" -> TagTone.Brand
    "staying" -> TagTone.Brand
    "completed" -> TagTone.Ok
    "evaluated" -> TagTone.Ok
    "cancelled" -> TagTone.Neutral
    "rejected" -> TagTone.Danger
    "pending" -> TagTone.Warn
    "paid" -> TagTone.Ok
    "approved" -> TagTone.Ok
    "rejected_request" -> TagTone.Danger
    "disabled" -> TagTone.Danger
    else -> TagTone.Neutral
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MobileColors.Ink3, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(text, color = MobileColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, color = MobileColors.Ink2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** 白底卡片：1px 描边 + 12 圆角，与设计稿 .card 对应。列表页里最常用的一层容器。 */
@Composable
fun MsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    background: Color = MobileColors.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MobileShape.Lg
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, MobileColors.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun MsDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MobileColors.Line2))
}

/** 主按钮：品牌粉渐变胶囊，移动端统一 48dp 高，拇指区更好点 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: AppIcon? = null,
) {
    Box(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MobileShape.Pill)
            .background(PrimaryGradient)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (icon != null) AppIcon(icon, Modifier.size(16.dp), Color.White)
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    textColor: Color = MobileColors.Ink,
    icon: AppIcon? = null,
) {
    Box(
        modifier
            .height(height)
            .clip(MobileShape.Pill)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) AppIcon(icon, Modifier.size(15.dp), textColor)
            Text(text, color = textColor, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: TagTone = TagTone.Brand) {
    val (bg, fg) = tagColors(tone)
    Box(
        modifier.height(38.dp).clip(MobileShape.Pill).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MobileColors.BrandDeep) {
    Text(text, modifier.clickable(onClick = onClick).padding(6.dp), color = color, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun StatusTag(text: String, tone: TagTone, modifier: Modifier = Modifier, dot: Boolean = false) {
    val (bg, fg) = tagColors(tone)
    Row(
        modifier.clip(MobileShape.Pill).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(text, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** 单行输入。BasicTextField 自己画外框，避免 material3 TextField 在旧版本上的样式漂移。 */
@Composable
fun MsField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    multiline: Boolean = false,
    numeric: Boolean = false,
    secret: Boolean = false,
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    if (label != null) {
        Text(label, color = MobileColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    }
    val shape = RoundedCornerShape(10.dp)
    val boxHeight = if (multiline) 0.dp else 46.dp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(color = MobileColors.Ink, fontSize = 15.sp),
        singleLine = !multiline,
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        cursorBrush = SolidColor(MobileColors.Brand),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (boxHeight > 0.dp) Modifier.height(boxHeight) else Modifier.heightIn(min = (minLines * 26 + 22).dp))
                    .clip(shape)
                    .background(MobileColors.Panel)
                    .border(1.dp, MobileColors.Line, shape)
                    .padding(start = 14.dp, end = if (secret) 6.dp else 14.dp, top = if (multiline) 12.dp else 0.dp, bottom = if (multiline) 12.dp else 0.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = MobileColors.Ink3, fontSize = 15.sp)
                    }
                    inner()
                }
                if (secret) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).clickable { reveal = !reveal }, contentAlignment = Alignment.Center) {
                        AppIcon(if (reveal) AppIcon.EyeOff else AppIcon.Eye, Modifier.size(18.dp), MobileColors.Ink3)
                    }
                }
            }
        },
    )
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(MobileShape.Pill)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            val (bg, fg) = if (active) MobileColors.NavActiveBg to MobileColors.BrandDeep else MobileColors.Panel to MobileColors.Ink2
            Box(
                Modifier
                    .clip(MobileShape.Pill)
                    .background(bg)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 15.dp, vertical = 8.dp),
            ) {
                Text(option, color = fg, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** 多选胶囊，用于宠物选择、加购服务勾选 */
@Composable
fun CheckChips(
    options: List<String>,
    selected: Set<String>,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val active = selected.contains(option)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(MobileShape.Md)
                            .background(if (active) MobileColors.Brand100 else MobileColors.Panel)
                            .border(1.dp, if (active) MobileColors.Brand300 else MobileColors.Line, MobileShape.Md)
                            .clickable { onToggle(option) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (active) AppIcon(AppIcon.Check, Modifier.size(13.dp), MobileColors.BrandDeep)
                            Text(
                                option,
                                color = if (active) MobileColors.Brand900 else MobileColors.Ink,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // 奇数个时补一个占位，保证最后一项不被拉宽
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MsSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(MobileShape.Pill)
            .background(if (checked) MobileColors.Brand else MobileColors.NeutralSoft)
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 3.dp).size(20.dp).clip(CircleShape).background(Color.White),
        )
    }
}

@Composable
fun MsCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) MobileColors.Brand else MobileColors.Panel)
            .border(1.dp, if (checked) MobileColors.Brand else MobileColors.Line, RoundedCornerShape(6.dp))
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) AppIcon(AppIcon.Check, Modifier.size(14.dp), Color.White)
    }
}

@Composable
fun StarRow(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    interactive: Boolean = false,
    onChange: ((Int) -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (interactive) 8.dp else 2.dp)) {
        for (i in 1..5) {
            StarGlyph(
                filled = i <= score,
                size = size,
                onClick = if (interactive && onChange != null) {
                    { onChange(i) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun StarGlyph(filled: Boolean, size: Dp, onClick: (() -> Unit)?) {
    val tint = if (filled) MobileColors.Amber else MobileColors.Line
    Box(
        Modifier
            .size(if (onClick != null) size + 8.dp else size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcon.Star, Modifier.size(size), tint)
    }
}

/** 数值步进器，用来调积分、容量、数量，避免用户在手机上敲键盘 */
@Composable
fun StepperRow(
    label: String,
    hint: String = "",
    value: Int,
    step: Int = 1,
    minValue: Int = 0,
    maxValue: Int = 999,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MobileColors.Ink, fontSize = 14.sp)
            if (hint.isNotBlank()) Text(hint, color = MobileColors.Ink3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        StepperButton(AppIcon.Close, value > minValue) { onChange((value - step).coerceIn(minValue, maxValue)) }
        Box(
            Modifier.width(78.dp).height(36.dp).clip(MobileShape.Md).background(MobileColors.PanelSoft)
                .border(1.dp, MobileColors.Line, MobileShape.Md),
            contentAlignment = Alignment.Center,
        ) {
            Text(value.toString() + suffix, color = MobileColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        StepperButton(AppIcon.Plus, value < maxValue) { onChange((value + step).coerceIn(minValue, maxValue)) }
    }
}

@Composable
private fun StepperButton(icon: AppIcon, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(36.dp)
            .clip(MobileShape.Md)
            .background(if (enabled) MobileColors.Brand100 else MobileColors.NeutralSoft)
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, Modifier.size(14.dp), if (enabled) MobileColors.BrandDeep else MobileColors.Ink3)
    }
}

/** 日期步进：移动端不引入 DatePicker 依赖，用加减一天 + 周历横滑来完成选择 */
@Composable
fun DateField(label: String, value: String, modifier: Modifier = Modifier, minValue: String? = null, onChange: (String) -> Unit) {
    val current = runCatching { java.time.LocalDate.parse(value) }.getOrElse { java.time.LocalDate.now() }
    val floor = runCatching { java.time.LocalDate.parse(minValue ?: value) }.getOrElse { current }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MobileColors.Ink2, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            DayButton("减一天", enabled = current.minusDays(1) >= floor) { onChange(current.minusDays(1).toString()) }
            Box(
                Modifier.padding(horizontal = 4.dp).width(112.dp).height(38.dp).clip(MobileShape.Md)
                    .background(MobileColors.NavActiveBg).border(1.dp, MobileColors.Brand200, MobileShape.Md),
                contentAlignment = Alignment.Center,
            ) {
                Text(value, color = MobileColors.BrandDeep, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
            DayButton("加一天", enabled = true) { onChange(current.plusDays(1).toString()) }
        }
        Text(
            runCatching { java.time.LocalDate.parse(value).dayOfWeek }.getOrNull()?.let {
                when (it.value) {
                    1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"; 5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
                }
            } ?: "",
            color = MobileColors.Ink3,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DayButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .height(38.dp)
            .clip(MobileShape.Md)
            .background(if (enabled) MobileColors.Panel else MobileColors.NeutralSoft)
            .border(1.dp, MobileColors.Line, MobileShape.Md)
            .alpha(if (enabled) 1f else 0.5f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) MobileColors.Ink else MobileColors.Ink3, fontSize = 12.5.sp)
    }
}
@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = MobileColors.Ink, weight: Float = 1f) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(key, color = MobileColors.Ink2, fontSize = 13.sp, modifier = Modifier.width(92.dp))
        Text(value, color = valueColor, fontSize = 13.5.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** 设置项通用行：图标 + 标题 + 副标题 + 右侧内容，我的页面靠它拼出来 */
@Composable
fun CellRow(
    icon: AppIcon,
    title: String,
    subtitle: String = "",
    trailing: String = "",
    badge: Int = 0,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (destructive) MobileColors.Danger else MobileColors.BrandDeep
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (onClick == null) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MobileColors.Brand50),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(17.dp), tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) MobileColors.Danger else MobileColors.Ink, fontSize = 14.5.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = MobileColors.Ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (badge > 0) CountBadge(badge)
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                color = MobileColors.Ink2,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp).widthIn(max = 150.dp),
            )
        }
        if (onClick != null) AppIcon(AppIcon.Back, Modifier.size(15.dp).rotate(180f), MobileColors.Ink3)
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(if (count > 9) 22.dp else 18.dp).clip(CircleShape).background(MobileColors.Danger),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun EmptyState(text: String, hint: String = "", modifier: Modifier = Modifier, action: (() -> Unit)? = null, actionText: String = "重新加载") {
    Column(
        modifier.fillMaxWidth().padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MobileColors.Brand50),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcon.Paw, Modifier.size(26.dp), MobileColors.Brand300)
        }
        Text(text, color = MobileColors.Ink2, fontSize = 14.sp)
        if (hint.isNotBlank()) Text(hint, color = MobileColors.Ink3, fontSize = 12.sp)
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            TonalButton(actionText, action)
        }
    }
}

@Composable
fun InlineError(text: String, onRetry: () -> Unit) {
    MsCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppIcon.Close, Modifier.size(16.dp), MobileColors.Danger)
            Column(Modifier.weight(1f)) {
                Text("加载失败", color = MobileColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(text, color = MobileColors.Ink2, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            TonalButton("重试", onRetry, tone = TagTone.Neutral)
        }
    }
}

@Composable
fun LoadingBars(text: String = "正在加载") {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse = remember { mutableStateOf(0.35f) }
        LaunchedEffect(Unit) {
            while (true) {
                pulse.value = if (pulse.value > 0.8f) 0.35f else pulse.value + 0.15f
                delay(180)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(MobileColors.Brand300.copy(alpha = pulse.value)))
            }
        }
        Text(text, color = MobileColors.Ink3, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun PageTopBar(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MobileColors.Panel).border(1.dp, MobileColors.Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcon.Back, Modifier.size(17.dp), MobileColors.Ink)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = MobileColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = MobileColors.Ink3, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PhotoGrid(urls: List<String>, modifier: Modifier = Modifier, size: Dp = 84.dp, onClick: ((String) -> Unit)? = null) {
    if (urls.isEmpty()) return
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        urls.forEach { url ->
            MobileImage(
                url = url,
                modifier = Modifier.size(size).then(if (onClick != null) Modifier.clickable { onClick(url) } else Modifier),
                contentDescription = null,
            )
        }
    }
}

@Composable
fun AmountBlock(label: String, amount: String, modifier: Modifier = Modifier, tone: Color = MobileColors.BrandDeep) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, color = MobileColors.Ink2, fontSize = 12.sp)
        Text(amount, color = tone, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 明细行：标签在左、金额在右，加购/优惠/积分都用它 */
@Composable
fun LineRow(label: String, value: String, tone: Color = MobileColors.Ink2, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (bold) MobileColors.Ink else MobileColors.Ink2,
            fontSize = if (bold) 14.5.sp else 13.sp,
            modifier = Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            value,
            color = tone,
            fontSize = if (bold) 15.sp else 13.5.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
/** 底部导航：选中态用设计稿的 .navi.on（浅粉底 + 深粉字），不用大面积饱和色 */
@Composable
fun BottomBar(
    items: List<Pair<AppIcon, String>>,
    selected: Int,
    badges: List<Int> = emptyList(),
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(MobileColors.Panel).height(64.dp),
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (active) MobileColors.NavActiveBg else Color.Transparent)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    AppIcon(item.first, Modifier.size(21.dp), if (active) MobileColors.BrandDeep else MobileColors.Ink3)
                    val badge = badges.getOrElse(index) { 0 }
                    if (badge > 0) CountBadge(badge, modifier = Modifier.offset(x = 11.dp, y = (-4).dp))
                }
                Text(
                    item.second,
                    color = if (active) MobileColors.BrandDeep else MobileColors.Ink3,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 底部弹层：遮罩 + 从下方升起的白色面板。
 * 不用 Dialog，少一层返回键和输入法焦点的兼容坑；空白区点一下就收起，面板内部不吃点击。
 */
@Composable
fun SheetOverlay(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(MobileColors.Scrim)) {
        Column(Modifier.fillMaxSize()) {
            // 上半段留白即遮罩，点一下就收起面板
            Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onClose))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MobileColors.Panel)
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 15.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        color = MobileColors.Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MobileColors.PanelSoft).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(AppIcon.Close, Modifier.size(14.dp), MobileColors.Ink2)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

/** 顶部轻提示，不阻塞操作 */
@Composable
fun ToastOverlay(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.padding(top = 70.dp).padding(horizontal = 28.dp).clip(MobileShape.Pill).background(MobileColors.Brand900).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(message, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    hint: String = "",
    tone: Color = MobileColors.BrandDeep,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(MobileShape.Lg)
            .background(MobileColors.Panel)
            .border(1.dp, MobileColors.Line, MobileShape.Lg)
            .padding(14.dp),
    ) {
        Text(label, color = MobileColors.Ink2, fontSize = 12.sp)
        Text(value, color = tone, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        if (hint.isNotBlank()) Text(hint, color = MobileColors.Ink3, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}