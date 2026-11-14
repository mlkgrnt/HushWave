package com.pulse.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- 配色基础

/** 一套强调色：主色 / 次色 / 第三色。整份 UI 的彩色都从这里取。 */
data class Accents(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    /** 素色方案：状态色也走中性灰 */
    val flat: Boolean = false
)

enum class ThemeMode(val cn: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

enum class ColorStyle(val cn: String) {
    CANDY("糖果"),
    AURORA("极光"),
    MINT("薄荷"),
    SUNSET("晚霞"),
    OCEAN("海盐"),
    GRAPE("葡萄"),
    MONO("素色"),
    CUSTOM("自定义"),
    WALLPAPER("壁纸取色");

    fun accents(dark: Boolean, custom: Color = Color.Unspecified): Accents {
        fun a(p: Long, s: Long, t: Long) = Accents(Color(p), Color(s), Color(t))
        return when (this) {
            CANDY -> if (dark) a(0xFFA78BFA, 0xFF22D3EE, 0xFFFB7185)
            else a(0xFFF06FA0, 0xFF3FB6C9, 0xFFEE5D7E)

            AURORA -> if (dark) a(0xFF9B8CFF, 0xFF4FE0E8, 0xFFC79BFF)
            else a(0xFF7C6CF0, 0xFF2FB9CE, 0xFFB36AF0)

            MINT -> if (dark) a(0xFF5FD3B2, 0xFF5FD8E8, 0xFF9BE07F)
            else a(0xFF2FA37E, 0xFF3BAFC2, 0xFF74BF5E)

            SUNSET -> if (dark) a(0xFFFF9A6B, 0xFFFF7AA8, 0xFFFFC15C)
            else a(0xFFEF7B45, 0xFFE85C93, 0xFFE0A32E)

            OCEAN -> if (dark) a(0xFF6BA6FF, 0xFF56D6E6, 0xFF9C9CFF)
            else a(0xFF3C82E8, 0xFF2FAFBF, 0xFF6A6CE8)

            GRAPE -> if (dark) a(0xFFB79CFF, 0xFFF0A8FF, 0xFF9BA8FF)
            else a(0xFF8B5CF6, 0xFFD946EF, 0xFF6366F1)

            MONO -> if (dark) a(0xFFBFBFC9, 0xFF8E8E9A, 0xFFDADAE4)
            else a(0xFF6B6B75, 0xFF8A8A95, 0xFF4F4F58)

            CUSTOM -> {
                val base = if (custom == Color.Unspecified) Color(0xFFF06FA0) else custom
                if (dark) {
                    Accents(
                        primary = base.withHsv(value = base.hsvValue().coerceAtLeast(0.86f)),
                        secondary = base.withHsv(hue = base.hsvHue() + 130f, sat = 0.45f, value = 0.95f),
                        tertiary = base.withHsv(hue = base.hsvHue() + 300f, sat = 0.5f, value = 0.95f)
                    )
                } else {
                    Accents(
                        primary = base.withHsv(value = base.hsvValue().coerceAtMost(0.78f)),
                        secondary = base.withHsv(hue = base.hsvHue() + 130f, sat = 0.52f, value = 0.72f),
                        tertiary = base.withHsv(hue = base.hsvHue() + 300f, sat = 0.55f, value = 0.74f)
                    )
                }
            }

            // 实际取色在 PulseTheme 里调用系统动态配色，这里只作为低版本回退
            WALLPAPER -> CANDY.accents(dark, custom)
        }
    }

    companion object {
        val all: List<ColorStyle> get() = values().toList()
        /** 选择器里列出的方案（壁纸取色单独一行处理） */
        val selectable: List<ColorStyle> get() = values().toList()
    }
}

fun Color.hsvHue(): Float = hsv()[0]

fun Color.hsvValue(): Float = hsv()[2]

private fun Color.hsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

fun Color.withHsv(hue: Float = Float.NaN, sat: Float = Float.NaN, value: Float = Float.NaN): Color {
    val hsv = hsv()
    if (!hue.isNaN()) hsv[0] = (hue + 360f) % 360f
    if (!sat.isNaN()) hsv[1] = sat.coerceIn(0f, 1f)
    if (!value.isNaN()) hsv[2] = value.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun Color.scale(f: Float): Color =
    Color(red * f, green * f, blue * f, alpha)

private fun blend(a: Color, b: Color, t: Float): Color = lerp(a, b, t.coerceIn(0f, 1f))

// ------------------------------------------------------------------ 调色板

object Palette {

    private val _dark = mutableStateOf(true)
    private val _accents = mutableStateOf(ColorStyle.CANDY.accents(true))

    /** 当前是否深色。由 PulseTheme 在组合开始时设置 */
    var dark: Boolean
        get() = _dark.value
        set(value) {
            _dark.value = value
        }

    /** 当前强调色。由 PulseTheme 在组合开始时设置 */
    var accents: Accents
        get() = _accents.value
        set(value) {
            _accents.value = value
        }

    val Ink: Color get() = if (dark) blend(Color(0xFF06070C), accents.primary, 0.03f)
    else blend(Color(0xFFFFF6FA), accents.primary, 0.02f)
    val InkDeep: Color get() = if (dark) Color(0xFF04050A) else Color(0xFFFFEDF5)
    val Surface: Color get() = if (dark) blend(Color(0xFF0E1120), accents.primary, 0.05f)
    else Color(0xFFFFFFFF)
    val SurfaceHi: Color get() = if (dark) blend(Color(0xFF151A2E), accents.primary, 0.08f)
    else blend(Color(0xFFFFF0F6), accents.primary, 0.06f)
    val Line: Color get() = if (dark) blend(Color(0xFF242B47), accents.primary, 0.12f)
    else blend(Color(0xFFF0DCE7), accents.primary, 0.12f)
    val CardSolid: Color get() = if (dark) blend(Color(0xFF111527), accents.primary, 0.05f)
    else Color(0xFFFFFFFF)
    val NavBg: Color get() = if (dark) blend(Color(0xFF0A0C16), accents.primary, 0.05f).copy(alpha = 0.94f)
    else Color(0xF2FFFFFF)
    val NavPill: Color get() = if (dark) blend(Color(0xFF20263F), accents.primary, 0.10f)
    else blend(Color(0xFFFFE3EF), accents.primary, 0.10f)
    val NavPillDim: Color get() = if (dark) blend(Color(0xFF161B2E), accents.primary, 0.06f)
    else blend(Color(0xFFFFF3F8), accents.primary, 0.05f)

    val BgTop: Color get() = if (dark) blend(Color(0xFF0A0C18), accents.primary, 0.09f)
    else blend(Color(0xFFFFFAFD), accents.primary, 0.05f)
    val BgMid: Color get() = if (dark) blend(Color(0xFF07090F), accents.primary, 0.06f)
    else blend(Color(0xFFFFF2F8), accents.primary, 0.08f)
    val BgBottom: Color get() = if (dark) blend(Color(0xFF04050A), accents.primary, 0.03f)
    else blend(Color(0xFFFFE9F3), accents.primary, 0.12f)
    val DialogBg: Color get() = if (dark) blend(Color(0xFF07090F), accents.primary, 0.05f)
    else blend(Color(0xFFFFF7FB), accents.primary, 0.03f)

    val Violet: Color get() = accents.primary
    val VioletDeep: Color get() = if (dark) accents.primary else accents.primary.scale(0.88f)
    val Cyan: Color get() = accents.secondary
    val Rose: Color get() = accents.tertiary
    val Lime: Color
        get() = when {
            accents.flat && dark -> Color(0xFFB9B9C4)
            accents.flat -> Color(0xFF6D6D78)
            dark -> Color(0xFF9AE6B4)
            else -> Color(0xFF4FB477)
        }

    val Text: Color get() = if (dark) Color(0xFFECEEF8) else Color(0xFF3C2A3A)
    val TextDim: Color get() = if (dark) Color(0xFF9AA3C0) else Color(0xFF7A6678)
    val TextFaint: Color get() = if (dark) Color(0xFF5E6784) else Color(0xFFA995A4)

    /** 半透明覆盖色：深色用白、浅色用墨，统一控制卡片/描边的存在感 */
    fun overlay(alpha: Float): Color =
        if (dark) Color.White.copy(alpha = alpha)
        else Color(0xFF5A3A55).copy(alpha = (alpha * 0.85f).coerceAtMost(0.22f))

    val Glow: Brush
        get() = Brush.linearGradient(listOf(VioletDeep, Cyan))
    val GlowSoft: Brush
        get() = Brush.linearGradient(
            listOf(Violet.copy(alpha = 0.85f), Cyan.copy(alpha = 0.75f))
        )
    val Warm: Brush
        get() = Brush.linearGradient(
            listOf(Rose, if (dark) Color(0xFFFFA26B) else blend(Rose, Color(0xFFFFA26B), 0.55f))
        )

    val ActionBrush: Brush
        get() = Brush.horizontalGradient(
            listOf(
                VioletDeep,
                if (dark) blend(accents.secondary, Color(0xFF5B7CFF), 0.45f)
                else blend(Rose, Color(0xFFFFA26B), 0.5f)
            )
        )
}

// ------------------------------------------------------------------ 主题状态

/**
 * 主题设置的唯一真相来源。模式与深浅色配色分别保存，
 * 界面改动后调用 save() 落地，下次启动 load() 恢复。
 */
object ThemeState {

    private const val PREFS = "hushwave_theme"
    private var loaded = false

    var mode by mutableStateOf(ThemeMode.SYSTEM)
    var lightStyle by mutableStateOf(ColorStyle.CANDY)
    var darkStyle by mutableStateOf(ColorStyle.CANDY)
    var lightCustom by mutableStateOf(Color(0xFFF06FA0))
    var darkCustom by mutableStateOf(Color(0xFFA78BFA))

    /** 改动计数器，方便组合层用它做 remember 的 key */
    var revision by mutableStateOf(0)
        private set

    fun load(ctx: Context) {
        if (loaded) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        lightStyle = runCatching { ColorStyle.valueOf(p.getString("light_style", null) ?: "") }
            .getOrDefault(ColorStyle.CANDY)
        darkStyle = runCatching { ColorStyle.valueOf(p.getString("dark_style", null) ?: "") }
            .getOrDefault(ColorStyle.CANDY)
        lightCustom = Color(p.getInt("light_custom", 0xFFF06FA0.toInt()))
        darkCustom = Color(p.getInt("dark_custom", 0xFFA78BFA.toInt()))
        loaded = true
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name)
            .putString("light_style", lightStyle.name)
            .putString("dark_style", darkStyle.name)
            .putInt("light_custom", lightCustom.toArgb())
            .putInt("dark_custom", darkCustom.toArgb())
            .apply()
        revision++
    }

    fun resolveDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    fun style(dark: Boolean): ColorStyle = if (dark) darkStyle else lightStyle

    fun custom(dark: Boolean): Color = if (dark) darkCustom else lightCustom

    fun setStyle(dark: Boolean, style: ColorStyle, ctx: Context) {
        if (dark) darkStyle = style else lightStyle = style
        save(ctx)
    }

    fun setCustom(dark: Boolean, color: Color, ctx: Context) {
        if (dark) darkCustom = color else lightCustom = color
        if (style(dark) != ColorStyle.CUSTOM) setStyle(dark, ColorStyle.CUSTOM, ctx) else save(ctx)
    }

    fun setMode(m: ThemeMode, ctx: Context) {
        mode = m
        save(ctx)
    }

    fun accents(dark: Boolean): Accents = style(dark).accents(dark, custom(dark))

    val supportsWallpaper: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

// -------------------------------------------------------------------- 主题

private val PulseType = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 64.sp,
        letterSpacing = (-2).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 1.4.sp
    )
)

val Mono = FontFamily.Monospace

data class PulseExtra(
    val mono: FontFamily = Mono,
    val cardRadius: Int = 26
)

val LocalPulse = staticCompositionLocalOf { PulseExtra() }

private fun schemeFor(acc: Accents, dark: Boolean): ColorScheme {
    return if (dark) darkColorScheme(
        primary = acc.primary,
        onPrimary = Color(0xFF160B2E),
        primaryContainer = blend(Color(0xFF151A2E), acc.primary, 0.24f),
        onPrimaryContainer = acc.primary,
        secondary = acc.secondary,
        onSecondary = Color(0xFF04222A),
        secondaryContainer = blend(Color(0xFF0E1424), acc.secondary, 0.22f),
        onSecondaryContainer = acc.secondary,
        tertiary = acc.tertiary,
        onTertiary = Color(0xFF2A0A13),
        tertiaryContainer = blend(Color(0xFF0E1424), acc.tertiary, 0.22f),
        onTertiaryContainer = acc.tertiary,
        background = Palette.BgMid,
        onBackground = Color(0xFFECEEF8),
        surface = Palette.Surface,
        onSurface = Color(0xFFECEEF8),
        surfaceVariant = Palette.SurfaceHi,
        onSurfaceVariant = Color(0xFF9AA3C0),
        outline = Palette.Line,
        outlineVariant = blend(Palette.Line, Palette.Surface, 0.45f),
        error = Color(0xFFFF8A9B)
    ) else lightColorScheme(
        primary = acc.primary,
        onPrimary = Color.White,
        primaryContainer = blend(Color.White, acc.primary, 0.14f),
        onPrimaryContainer = acc.primary.scale(0.62f),
        secondary = acc.secondary,
        onSecondary = Color.White,
        secondaryContainer = blend(Color.White, acc.secondary, 0.14f),
        onSecondaryContainer = acc.secondary.scale(0.55f),
        tertiary = acc.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = blend(Color.White, acc.tertiary, 0.14f),
        onTertiaryContainer = acc.tertiary.scale(0.6f),
        background = Palette.BgMid,
        onBackground = Color(0xFF3C2A3A),
        surface = Color.White,
        onSurface = Color(0xFF3C2A3A),
        surfaceVariant = Palette.SurfaceHi,
        onSurfaceVariant = Color(0xFF7A6678),
        outline = Palette.Line,
        outlineVariant = blend(Palette.Line, Color.White, 0.5f),
        error = Color(0xFFD1435F)
    )
}

/** 根据当前设置算出一次主题（壁纸取色需要 Context，所以只能在这里做） */
@Composable
fun rememberAccents(dark: Boolean): Accents {
    val ctx = LocalContext.current
    val style = ThemeState.style(dark)
    val custom = ThemeState.custom(dark)
    val revision = ThemeState.revision
    return androidx.compose.runtime.remember(dark, style, custom, revision) {
        if (style == ColorStyle.WALLPAPER && ThemeState.supportsWallpaper) {
            val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            Accents(s.primary, s.secondary, s.tertiary)
        } else {
            style.accents(dark, custom)
        }
    }
}

/** 壁纸取色时的完整 M3 配色，其余方案返回 null 交给 schemeFor 生成 */
@Composable
fun rememberDynamicScheme(dark: Boolean): ColorScheme? {
    val ctx = LocalContext.current
    val style = ThemeState.style(dark)
    val revision = ThemeState.revision
    return androidx.compose.runtime.remember(dark, style, revision) {
        if (style == ColorStyle.WALLPAPER && ThemeState.supportsWallpaper) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            null
        }
    }
}

@Composable
fun PulseTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accents: Accents? = null,
    scheme: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val acc = accents ?: ThemeState.accents(dark)
    Palette.dark = dark
    Palette.accents = acc
    MaterialTheme(
        colorScheme = scheme ?: schemeFor(acc, dark),
        typography = PulseType,
        content = content
    )
}
