package com.pulse.app.ui.screens

import com.pulse.app.R
import com.pulse.app.core.Str
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.app.ui.Accents
import com.pulse.app.ui.ColorStyle
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.Palette
import com.pulse.app.ui.ThemeMode
import com.pulse.app.ui.ThemeState
import com.pulse.app.ui.VSpace

/** 设置页的「外观」卡片：模式三选一 + 浅色/深色配色分别设置 */
@Composable
fun AppearanceCard() {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Boolean?>(null) }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Palette.Violet.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Palette, null, tint = Palette.Violet, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Str.s(R.string.appr_000), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    Str.s(R.string.x_theme_summary, Str.data(ThemeState.mode.cn), Str.data(ThemeState.lightStyle.cn), Str.data(ThemeState.darkStyle.cn)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            }
        }
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { m ->
                Chip(
                    text = Str.data(m.cn),
                    selected = ThemeState.mode == m,
                    modifier = Modifier.weight(1f)
                ) { ThemeState.setMode(m, ctx) }
            }
        }
        VSpace(14.dp)
        DividerLine()
        VSpace(12.dp)
        StyleRow(
            title = Str.s(R.string.appr_001),
            style = ThemeState.lightStyle,
            dark = false,
            onClick = { editing = false }
        )
        VSpace(10.dp)
        StyleRow(
            title = Str.s(R.string.appr_002),
            style = ThemeState.darkStyle,
            dark = true,
            onClick = { editing = true }
        )
        VSpace(10.dp)
        Text(
            if (ThemeState.supportsWallpaper) Str.s(R.string.appr_003)
            else Str.s(R.string.appr_004),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextFaint
        )
    }

    editing?.let { dark ->
        ColorStyleDialog(dark = dark, onDismiss = { editing = null })
    }
}

@Composable
private fun StyleRow(title: String, style: ColorStyle, dark: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextDim, modifier = Modifier.weight(1f))
        AccentDots(style.accents(dark, ThemeState.custom(dark)), 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(Str.data(style.cn), style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        Spacer(Modifier.width(8.dp))
        Text(Str.s(R.string.appr_005), style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
    }
}

@Composable
private fun AccentDots(accents: Accents, size: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(accents.primary, accents.secondary, accents.tertiary).forEach { c ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(c)
                    .border(1.dp, Palette.overlay(0.16f), CircleShape)
            )
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.Violet.copy(alpha = 0.18f) else Palette.overlay(0.04f))
            .border(
                1.dp,
                if (selected) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.06f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = if (selected) Palette.Violet else Palette.TextDim
        )
    }
}

/** 自定义色板：18 个手挑的颜色，够覆盖常见审美又不用塞一个取色器 */
private val CUSTOM_SWATCHES = listOf(
    0xFFF06FA0, 0xFFEE5D7E, 0xFFF07A4E, 0xFFE0A32E, 0xFFC7B446, 0xFF74BF5E,
    0xFF3FAE8F, 0xFF3BAFC2, 0xFF3C82E8, 0xFF6A6CE8, 0xFF8B5CF6, 0xFFB36AF0,
    0xFFD946EF, 0xFF9E7B6A, 0xFF7A8CA8, 0xFF8A8A95, 0xFF5B6470, 0xFFB9464E
).map { Color(it) }

@Composable
private fun ColorStyleDialog(dark: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val current = ThemeState.style(dark)
    val wallpaperAccents = remember(dark) {
        if (!ThemeState.supportsWallpaper) null
        else runCatching {
            val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            Accents(s.primary, s.secondary, s.tertiary)
        }.getOrNull()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.DialogBg)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (dark) Str.s(R.string.appr_002) else Str.s(R.string.appr_001),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Text
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.overlay(0.06f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Palette.TextDim)
                }
            }
            VSpace(4.dp)
            Text(
                Str.s(R.string.appr_006),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
            VSpace(16.dp)

            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    ColorStyle.values().filter { it != ColorStyle.WALLPAPER && it != ColorStyle.CUSTOM }
                        .size
                ) { idx ->
                    val st = ColorStyle.values()
                        .filter { it != ColorStyle.WALLPAPER && it != ColorStyle.CUSTOM }[idx]
                    StyleOption(
                        name = Str.data(st.cn),
                        accents = st.accents(dark, ThemeState.custom(dark)),
                        selected = current == st
                    ) { ThemeState.setStyle(dark, st, ctx) }
                }

                item {
                    val supported = ThemeState.supportsWallpaper
                    StyleOption(
                        name = Str.s(R.string.appr_007),
                        accents = wallpaperAccents
                            ?: ThemeState.style(dark).accents(dark, ThemeState.custom(dark)),
                        selected = current == ColorStyle.WALLPAPER,
                        enabled = supported,
                        caption = if (supported) null else Str.s(R.string.appr_008)
                    ) {
                        if (supported) ThemeState.setStyle(dark, ColorStyle.WALLPAPER, ctx)
                    }
                }

                item {
                    StyleOption(
                        name = Str.s(R.string.appr_009),
                        accents = ColorStyle.CUSTOM.accents(dark, ThemeState.custom(dark)),
                        selected = current == ColorStyle.CUSTOM
                    ) { ThemeState.setStyle(dark, ColorStyle.CUSTOM, ctx) }
                }

                if (current == ColorStyle.CUSTOM) {
                    item {
                        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp) {
                            Text(
                                Str.s(R.string.appr_010),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextFaint
                            )
                            VSpace(12.dp)
                            CUSTOM_SWATCHES.chunked(6).forEachIndexed { rowIdx, row ->
                                if (rowIdx > 0) VSpace(10.dp)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { c ->
                                        val on = ThemeState.custom(dark).value == c.value
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    if (on) 2.dp else 1.dp,
                                                    if (on) Palette.Text else Palette.overlay(0.18f),
                                                    CircleShape
                                                )
                                                .clickable { ThemeState.setCustom(dark, c, ctx) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (on) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { VSpace(20.dp) }
            }
        }
    }
}

@Composable
private fun StyleOption(
    name: String,
    accents: Accents,
    selected: Boolean,
    enabled: Boolean = true,
    caption: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.Violet.copy(alpha = 0.12f) else Palette.overlay(0.04f))
            .border(
                1.dp,
                if (selected) Palette.Violet.copy(alpha = 0.45f) else Palette.overlay(0.06f),
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Palette.Text else Palette.TextFaint,
            modifier = Modifier.weight(1f)
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = Palette.TextFaint
            )
            Spacer(Modifier.width(8.dp))
        }
        AccentDots(accents, 16.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = Palette.Violet, modifier = Modifier.size(18.dp))
            }
        }
    }
}
