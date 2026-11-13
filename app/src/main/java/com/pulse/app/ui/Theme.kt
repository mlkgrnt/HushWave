package com.pulse.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {

    private val _dark = mutableStateOf(true)

    /** 当前是否深色。由 PulseTheme 在组合开始时设置 */
    var dark: Boolean
        get() = _dark.value
        set(value) {
            _dark.value = value
        }

    val Ink: Color get() = if (dark) Color(0xFF06070C) else Color(0xFFFFF6FA)
    val InkDeep: Color get() = if (dark) Color(0xFF04050A) else Color(0xFFFFEDF5)
    val Surface: Color get() = if (dark) Color(0xFF0E1120) else Color(0xFFFFFFFF)
    val SurfaceHi: Color get() = if (dark) Color(0xFF151A2E) else Color(0xFFFFF0F6)
    val Line: Color get() = if (dark) Color(0xFF242B47) else Color(0xFFF0DCE7)
    val CardSolid: Color get() = if (dark) Palette.CardSolid else Color(0xFFFFFFFF)
    val NavBg: Color get() = if (dark) Color(0xF00A0C16) else Color(0xF2FFFFFF)
    val NavPill: Color get() = if (dark) Color(0xFF20263F) else Color(0xFFFFE3EF)
    val NavPillDim: Color get() = if (dark) Color(0xFF161B2E) else Color(0xFFFFF3F8)

    val BgTop: Color get() = if (dark) Color(0xFF0A0C18) else Color(0xFFFFFAFD)
    val BgMid: Color get() = if (dark) Palette.DialogBg else Color(0xFFFFF2F8)
    val BgBottom: Color get() = if (dark) Color(0xFF04050A) else Color(0xFFFFE9F3)
    val DialogBg: Color get() = if (dark) Palette.DialogBg else Color(0xFFFFF7FB)

    val Violet: Color get() = if (dark) Color(0xFFA78BFA) else Color(0xFFF06FA0)
    val VioletDeep: Color get() = if (dark) Color(0xFF7C5CFF) else Color(0xFFE85C93)
    val Cyan: Color get() = if (dark) Color(0xFF22D3EE) else Color(0xFF3FB6C9)
    val Rose: Color get() = if (dark) Color(0xFFFB7185) else Color(0xFFEE5D7E)
    val Lime: Color get() = if (dark) Color(0xFF9AE6B4) else Color(0xFF4FB477)

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
        get() = Brush.linearGradient(listOf(Rose, Color(0xFFFFA26B)))

    val ActionBrush: Brush
        get() = Brush.horizontalGradient(
            listOf(VioletDeep, if (dark) Color(0xFF5B7CFF) else Color(0xFFFFA26B))
        )
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF160B2E),
    primaryContainer = Color(0xFF2A1F52),
    onPrimaryContainer = Color(0xFFA78BFA),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF04222A),
    secondaryContainer = Color(0xFF0C2F38),
    onSecondaryContainer = Color(0xFF22D3EE),
    tertiary = Color(0xFFFB7185),
    onTertiary = Color(0xFF2A0A13),
    background = Color(0xFF06070C),
    onBackground = Color(0xFFECEEF8),
    surface = Color(0xFF0E1120),
    onSurface = Color(0xFFECEEF8),
    surfaceVariant = Color(0xFF151A2E),
    onSurfaceVariant = Color(0xFF9AA3C0),
    outline = Color(0xFF242B47),
    outlineVariant = Color(0xFF1A2038),
    error = Color(0xFFFF8A9B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFF06FA0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2EE),
    onPrimaryContainer = Color(0xFF8A2C55),
    secondary = Color(0xFF3FB6C9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6FA),
    onSecondaryContainer = Color(0xFF15515C),
    tertiary = Color(0xFFEE5D7E),
    onTertiary = Color.White,
    background = Color(0xFFFFF6FA),
    onBackground = Color(0xFF3C2A3A),
    surface = Color.White,
    onSurface = Color(0xFF3C2A3A),
    surfaceVariant = Color(0xFFFFF0F6),
    onSurfaceVariant = Color(0xFF7A6678),
    outline = Color(0xFFF0DCE7),
    outlineVariant = Color(0xFFF7E8F0),
    error = Color(0xFFD1435F)
)
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

@Composable
fun PulseTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    Palette.dark = dark
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PulseType,
        content = content
    )
}
