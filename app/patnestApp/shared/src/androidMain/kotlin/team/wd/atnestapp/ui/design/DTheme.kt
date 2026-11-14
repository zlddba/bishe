package team.wd.atnestapp.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * 一套配色的全部令牌。
 *
 * 浅色值逐条对应 docs/KMP-Android端UI设计稿.html 的 :root 变量；
 * 深色值是同一套语义的暗色映射，只调「底色 / 文字 / 描边 / 浅底强调」，
 * 品牌色、图片占位、渐变这类「素材色」不参与翻转（见 DColor 里不带 @Composable 的常量）。
 */
@Immutable
data class DPalette(
    val brand: Color,
    /** 品牌色做「填充」时用的深一档值（选中胶囊、白字底），深色下不能跟着变亮，否则白字看不见。 */
    val brandStrong: Color,
    val brandSoft: Color,
    /** 品牌色作为「文字 / 图标」时的值，深色下要提亮。 */
    val brandInk: Color,
    val brand900: Color,
    val brand800: Color,

    val bg: Color,
    val card: Color,
    val line: Color,
    val line2: Color,

    val ink: Color,
    val ink2: Color,
    val ink3: Color,

    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val star: Color,

    val neutralSoft: Color,
    /** 浮层气泡底色（toast）。浅色是半透明深色，深色要提亮成实体灰，否则贴在暗底上看不出来。 */
    val toast: Color,

    val heroGradient: Brush,
    /** 地图选点、信息卡这类「浅色水洗底」，深色下要压成暗底，否则上面的浅色文字会糊掉。 */
    val mapWash: Brush,
)

val DLightPalette = DPalette(
    brand = Color(0xFFE0708A),
    brandStrong = Color(0xFFAF4A62),
    brandSoft = Color(0xFFFBE0E6),
    brandInk = Color(0xFFAF4A62),
    brand900 = Color(0xFF6F2A3B),
    brand800 = Color(0xFF8E3B4E),

    bg = Color(0xFFFAF5F6),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFF1EEEF),
    line2 = Color(0xFFECE7E9),

    ink = Color(0xFF2B2628),
    ink2 = Color(0xFF6E6568),
    ink3 = Color(0xFF9C9296),

    ok = Color(0xFF2F9E63),
    okSoft = Color(0xFFE4F4EC),
    warn = Color(0xFFD97706),
    warnSoft = Color(0xFFFDF0DC),
    danger = Color(0xFFC2456A),
    dangerSoft = Color(0xFFFBE7EC),
    info = Color(0xFF3E7BFA),
    infoSoft = Color(0xFFE7F1FD),
    star = Color(0xFFEAB308),

    neutralSoft = Color(0xFFF3F0F1),
    toast = Color(0xE62B2628),

    // 渐变在 data class 外面构造，只能写字面色值，分别等于 brand → brand800（浅）/ 更深的收口（深）。
    heroGradient = Brush.linearGradient(listOf(Color(0xFFE0708A), Color(0xFF8E3B4E))),
    mapWash = Brush.linearGradient(listOf(Color(0xFFE8F0E4), Color(0xFFD8E4E8))),
)

val DDarkPalette = DPalette(
    // 品牌色本身在深色下对比度已经够（#E0708A 对 #121011 ≈ 5:1），保持不变以维持品牌识别。
    brand = Color(0xFFE0708A),
    brandStrong = Color(0xFFA6435B),
    brandSoft = Color(0xFF3C2429),
    brandInk = Color(0xFFF3A5B6),
    brand900 = Color(0xFF2A1119),
    brand800 = Color(0xFF6E2C3C),

    // 底色带一点暖粉调，和品牌色同族，不要用纯灰黑。
    bg = Color(0xFF121011),
    card = Color(0xFF1E1A1B),
    line = Color(0xFF2B2527),
    line2 = Color(0xFF372F31),

    ink = Color(0xFFF4EFF0),
    ink2 = Color(0xFFB7ADB0),
    ink3 = Color(0xFF8B8185),

    ok = Color(0xFF4CBF87),
    okSoft = Color(0xFF17301F),
    warn = Color(0xFFE0A044),
    warnSoft = Color(0xFF33260F),
    danger = Color(0xFFF27C93),
    dangerSoft = Color(0xFF3B1D24),
    info = Color(0xFF6FA3FF),
    infoSoft = Color(0xFF17243A),
    star = Color(0xFFE8C24C),

    neutralSoft = Color(0xFF2A2426),
    toast = Color(0xF03A3436),

    heroGradient = Brush.linearGradient(listOf(Color(0xFFE0708A), Color(0xFF6E2C3C))),
    mapWash = Brush.linearGradient(listOf(Color(0xFF1A2620), Color(0xFF1C2529))),
)

val LocalDPalette = staticCompositionLocalOf { DLightPalette }

/**
 * 设计稿配色令牌。
 *
 * 带 @Composable 的取值跟随 DTheme 切换（浅色 / 深色），
 * 不带 @Composable 的是「素材色」—— 图片占位、头像渐变、蒙层、白字，
 * 它们代表的是照片 / 品牌面，深色主题下不翻转，因此可以在
 * 顶层 val、Canvas 绘制块、文件级常量里安全使用。
 */
object DColor {
    val Brand: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brand
    val BrandStrong: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brandStrong
    val BrandSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brandSoft
    val BrandInk: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brandInk
    val Brand900: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brand900
    val Brand800: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.brand800

    val Bg: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.bg
    val Card: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.card
    val Line: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.line
    val Line2: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.line2

    val Ink: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.ink
    val Ink2: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.ink2
    val Ink3: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.ink3

    val Ok: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.ok
    val OkSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.okSoft
    val Warn: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.warn
    val WarnSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.warnSoft
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.danger
    val DangerSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.dangerSoft
    val Info: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.info
    val InfoSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.infoSoft
    val Star: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.star

    val NeutralSoft: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.neutralSoft

    /** toast 气泡底色（DesignApp 底部提示）。 */
    val Toast: Color @Composable @ReadOnlyComposable get() = LocalDPalette.current.toast

    /** 首页 / 个人中心 / 钱包卡的品牌渐变。 */
    val HeroGradient: Brush @Composable @ReadOnlyComposable get() = LocalDPalette.current.heroGradient

    /** 地图选点、浅色信息卡的「水洗」底。 */
    val MapWash: Brush @Composable @ReadOnlyComposable get() = LocalDPalette.current.mapWash

    /* ---------------- 以下是素材色，不随主题变化 ---------------- */

    /** 品牌面 / 渐变上的字与图标。 */
    val White = Color(0xFFFFFFFF)

    /** 压在图片上的半透明暗蒙层（返回键圆钮等）。 */
    val Scrim = Color(0x662B2628)

    /** 封面、头像占位渐变。 */
    val CoverGradient = Brush.linearGradient(listOf(Color(0xFFE0708A), Color(0xFFB85A72)))

    /** 图片占位渐变：图片本身是亮的，深色下依然留亮，才有「这里是一张照片」的观感。 */
    val ImageGradient = Brush.linearGradient(listOf(Color(0xFFF6C9A7), Color(0xFFE89AAF)))
}

/**
 * 设计稿圆角。
 *
 * 全部用 dp 硬值，不要写 `RoundedCornerShape(16)` —— 那个 Int 重载是「百分比」，
 * 16% 在卡片上等于半个屏幕宽度的 8%，折算下来半径能到 50dp 以上，越大的控件越圆。
 *
 * 硬约束：任何令牌都不得超过 [MAX_RADIUS_DP]。圆角超过这个值，
 * 在手机屏上就不再是「圆角」而是「拱形」，比控件本身还抢眼。
 */
object DShape {
    /** 圆角上限（dp），新增令牌时也不许越线。 */
    const val MAX_RADIUS_DP = 35

    /** 小控件 / 输入框 / 图标块。 */
    val Sm = RoundedCornerShape(6.dp)

    /** 控件默认（图片、标签、小卡片）。 */
    val Md = RoundedCornerShape(8.dp)

    /** 卡片默认。 */
    val Lg = RoundedCornerShape(12.dp)

    /** 强调容器（钱包卡、地图水洗底、大图）。 */
    val Xl = RoundedCornerShape(16.dp)

    /** 胶囊类（标签 / 选片 / 开关 / 细条）：20dp 在矮控件上仍等于全圆，高按钮上不再鼓成胶囊。 */
    val Pill = RoundedCornerShape(20.dp)

    /** 底部大圆角（hero 抬头下沿）。 */
    val HeroBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
}

/**
 * 主题入口。
 *
 * @param darkTheme 默认跟随系统；也可以传固定值强制某一种主题（预览时用）。
 */
@Composable
fun DTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DDarkPalette else DLightPalette
    DSystemBars(darkTheme)
    // ColorScheme 只在深浅色切换时构造一次：每次重组都 new 一个会让 MaterialTheme 失去可跳过性。
    val colorScheme = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = DDarkPalette.brand,
                onPrimary = DColor.White,
                primaryContainer = DDarkPalette.brandSoft,
                onPrimaryContainer = DDarkPalette.brandInk,
                background = DDarkPalette.bg,
                onBackground = DDarkPalette.ink,
                surface = DDarkPalette.card,
                onSurface = DDarkPalette.ink,
                surfaceVariant = DDarkPalette.neutralSoft,
                onSurfaceVariant = DDarkPalette.ink2,
                outline = DDarkPalette.line2,
                error = DDarkPalette.danger,
            )
        } else {
            lightColorScheme(
                primary = DLightPalette.brand,
                onPrimary = DColor.White,
                primaryContainer = DLightPalette.brandSoft,
                onPrimaryContainer = DLightPalette.brandInk,
                background = DLightPalette.bg,
                onBackground = DLightPalette.ink,
                surface = DLightPalette.card,
                onSurface = DLightPalette.ink,
                surfaceVariant = DLightPalette.neutralSoft,
                onSurfaceVariant = DLightPalette.ink2,
                outline = DLightPalette.line2,
                error = DLightPalette.danger,
            )
        }
    }
    CompositionLocalProvider(LocalDPalette provides palette) {
        // Miuix 主题桥：逐屏重写期间新旧组件并存，两边共享同一套品牌配色。
        // Miuix 的颜色只在这里构造一次（remember 挂 darkTheme），重写完成后
        // MaterialTheme 一层会被移除，现在先双层主题共存。
        MiuixTheme(colors = remember(darkTheme) { dMiuixColors(palette, darkTheme) }) {
            MaterialTheme(colorScheme = colorScheme, content = content)
        }
    }
}

/**
 * 把 [DPalette] 语义令牌映射进 Miuix 的 HyperOS 配色。
 *
 * 映射原则与 Material 版一致：
 * - `primary` 用「填充级」的 brandStrong（选中胶囊 / 白字底），深浅色都不跟随变亮，
 *   保证白字始终可读；
 * - 文字 / 图标级的 brandInk 不进 Miuix（Miuix 组件里的强调色一律走 primary），
 *   现有屏幕的重音仍走 DColor.BrandInk，等逐屏重写时再逐个收敛；
 * - 表面色（bg / card / line / ink / neutralSoft）一一对应，深浅色自动翻转。
 *
 * 只传 Miuix lightColorScheme / darkColorScheme 签名里确认存在的字段；
 * 未覆盖的字段（dividerLine、tertiary 等）落在官方深浅色默认值上。
 */
private fun dMiuixColors(p: DPalette, dark: Boolean): MiuixColors {
    val base = if (dark) miuixDarkColorScheme() else miuixLightColorScheme()
    return base.copy(
        primary = p.brandStrong,
        onPrimary = DColor.White,
        primaryVariant = p.brand,
        onPrimaryVariant = p.brandInk,
        primaryContainer = p.brandSoft,
        onPrimaryContainer = p.brandInk,
        secondary = p.neutralSoft,
        onSecondary = p.ink,
        secondaryVariant = p.line,
        onSecondaryVariant = p.ink2,
        error = p.danger,
        onError = DColor.White,
        errorContainer = p.dangerSoft,
        onErrorContainer = p.danger,
        disabledPrimary = p.line2,
        disabledOnPrimary = p.ink3,
        disabledPrimaryButton = p.line2,
        disabledOnPrimaryButton = p.ink3,
        disabledPrimarySlider = p.line2,
    )
}

/**
 * 沉浸式系统栏的图标明暗。
 *
 * 系统栏本身是透明的（MainActivity 里 enableEdgeToEdge），图标必须跟主题反过来：
 * 浅色主题要深色图标，深色主题要浅色图标。只在主题切换时重设一次，
 * 不会和 DPaleStatusBar（hero 页临时强制白图标）互相打架。
 */
@Composable
private fun DSystemBars(dark: Boolean) {
    val context = LocalContext.current
    DisposableEffect(dark) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        // 不还原：主题切换时会重新走一遍，退出页面时窗口也就销毁了。
        onDispose { }
    }
}
