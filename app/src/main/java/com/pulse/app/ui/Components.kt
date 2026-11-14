package com.pulse.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(modifier.fillMaxSize().background(Palette.InkDeep)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Palette.BgTop,
                    0.45f to Palette.BgMid,
                    1f to Palette.BgBottom
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.VioletDeep.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * (0.18f + drift * 0.12f), size.height * 0.12f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * (0.18f + drift * 0.12f), size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.Cyan.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * (0.92f - drift * 0.10f), size.height * 0.38f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * (0.92f - drift * 0.10f), size.height * 0.38f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    radius: Dp = 26.dp,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (glow) Palette.SurfaceHi else Palette.CardSolid,
                        Palette.Surface
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Palette.overlay(0.13f), Palette.overlay(0.02f))
                ),
                shape = RoundedCornerShape(radius)
            )
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Palette.TextFaint) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

/** 会呼吸、会随强度膨胀的光球 */
@Composable
fun AuraOrb(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Violet,
    secondary: Color = Palette.Cyan
) {
    val smooth by animateFloatAsState(level.coerceIn(0f, 1f), tween(160), label = "lvl")
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "spin"
    )
    val ripple by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "ripple"
    )
    val breathe by transition.animateFloat(
        0.985f, 1.015f,
        infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension / 2
            val core = base * 0.60f * (0.97f + 0.06f * smooth) * breathe

            // 外层光晕：中间透明、外圈亮，像悬浮的能量场
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.52f to accent.copy(alpha = 0.10f + 0.20f * smooth),
                    0.72f to accent.copy(alpha = 0.16f + 0.30f * smooth),
                    1f to Color.Transparent
                ),
                radius = base * 1.02f,
                center = c
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to secondary.copy(alpha = 0.05f + 0.10f * smooth),
                    1f to Color.Transparent
                ),
                radius = base * 0.95f,
                center = c
            )

            // 扩散涟漪
            if (active) {
                for (k in 0..2) {
                    val p = ((ripple + k * 0.34f) % 1f)
                    drawCircle(
                        color = accent.copy(alpha = (1f - p) * (0.10f + 0.14f * smooth)),
                        radius = core * (1.05f + p * 0.85f),
                        center = c,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // 全环轨道
            drawCircle(
                color = Palette.overlay(0.045f),
                radius = base * 0.86f,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )

            // 三条旋转弧
            repeat(3) { i ->
                val r = base * (0.86f - i * 0.055f)
                val sweep = 46f + 54f * smooth + i * 10f
                rotate(angle * (1f + i * 0.6f) * (if (i % 2 == 0) 1f else -1f), c) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                (if (i == 1) secondary else accent).copy(alpha = 0.20f + 0.55f * smooth),
                                Color.Transparent
                            ),
                            center = c
                        ),
                        startAngle = i * 118f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = (1f + i * 0.4f).dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 玻璃球体：深色是深空玻璃，浅色是磨砂白，避免浅色主题里出现一个黑圆
            val orbTop = if (Palette.dark) Color(0xFF1A2138) else Color.White
            val orbMid =
                if (Palette.dark) Color(0xFF101427) else lerp(Color.White, accent, 0.06f)
            val orbBottom =
                if (Palette.dark) Color(0xFF070A13) else lerp(Color.White, accent, 0.13f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to orbTop,
                    0.55f to orbMid,
                    1f to orbBottom
                ),
                radius = core,
                center = c
            )
            // 球体边缘的冷光
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        accent.copy(alpha = 0.55f + 0.35f * smooth),
                        secondary.copy(alpha = 0.25f),
                        accent.copy(alpha = 0.55f + 0.35f * smooth)
                    ),
                    center = c
                ),
                radius = core,
                center = c,
                style = Stroke(width = 1.4.dp.toPx())
            )
            // 内部核心光
            drawCircle(
                brush = Brush.radialGradient(
                    0f to (if (Palette.dark) Color.White else accent)
                        .copy(alpha = 0.02f + 0.34f * smooth),
                    0.35f to accent.copy(alpha = 0.03f + 0.26f * smooth),
                    0.75f to accent.copy(alpha = 0.01f + 0.09f * smooth),
                    1f to Color.Transparent
                ),
                radius = core * (0.55f + 0.45f * smooth),
                center = c
            )
            // 左上高光
            drawArc(
                color = if (Palette.dark) Color.White.copy(alpha = 0.10f + 0.10f * smooth)
                else accent.copy(alpha = 0.16f + 0.14f * smooth),
                startAngle = 195f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(c.x - core * 0.94f, c.y - core * 0.94f),
                size = Size(core * 1.88f, core * 1.88f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )

            // 环绕粒子
            val dots = 22
            for (i in 0 until dots) {
                val t = i.toFloat() / dots * 2f * PI.toFloat() + angle / 180f * PI.toFloat()
                val rr = base * (0.80f + 0.05f * sin(angle / 70f + i * 0.8f))
                val x = c.x + cos(t) * rr
                val y = c.y + sin(t) * rr * 0.94f
                drawCircle(
                    color = (if (Palette.dark) Color.White else accent)
                        .copy(alpha = 0.06f + 0.20f * smooth * (0.4f + 0.6f * sin(t * 3f))),
                    radius = (0.6f + 1.2f * smooth).dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(smooth * 100).roundToInt()}",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Light,
                    fontSize = 60.sp,
                    letterSpacing = (-2).sp
                ),
                color = Palette.Text
            )
            Text(
                if (active) "LIVE OUTPUT" else "STANDBY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = if (active) secondary.copy(alpha = 0.9f) else Palette.TextFaint
            )
        }
    }
}

/** 自绘滑杆：比 Material Slider 更贴合这套视觉 */
@Composable
fun GlowSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Violet,
    enabled: Boolean = true,
    valueText: String? = null
) {
    val density = LocalDensity.current
    val v = value.coerceIn(0f, 1f)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Palette.TextDim else Palette.TextFaint,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText ?: "${(v * 100).roundToInt()}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (enabled) accent else Palette.TextFaint
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { o ->
                        onChange((o.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        onChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
        ) {
            val h = 12.dp.toPx()
            val y = size.height / 2 - h / 2
            val r = h / 2
            drawRoundRect(
                color = Palette.overlay(0.06f),
                topLeft = Offset(0f, y),
                size = Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            val fillW = (size.width * v).coerceAtLeast(if (v > 0f) h else 0f)
            if (fillW > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.55f), accent)
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(fillW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            val thumbX = (size.width * v).coerceIn(r, size.width - r)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(thumbX, size.height / 2),
                    radius = 22.dp.toPx()
                ),
                radius = 22.dp.toPx(),
                center = Offset(thumbX, size.height / 2)
            )
            drawCircle(
                color = Palette.Surface,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, size.height / 2)
            )
            drawCircle(
                color = accent,
                radius = 6.4f.dp.toPx(),
                center = Offset(thumbX, size.height / 2)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 2.2f.dp.toPx(),
                center = Offset(thumbX - 1.6f.dp.toPx(), size.height / 2 - 1.6f.dp.toPx())
            )
        }
    }
}

@Composable
fun MiniSparkline(
    points: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = Palette.Violet,
    progress: Float? = null,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxV = points.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
        val w = size.width
        val h = size.height
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i.toFloat() / (points.size - 1) * w
            val y = h - (p / maxV) * (h - 4.dp.toPx()) - 2.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(color.copy(alpha = 0.45f), color, Palette.Cyan.copy(alpha = 0.9f))
            ),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        if (progress != null) {
            val x = progress.coerceIn(0f, 1f) * w
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 7.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun KeyValue(text: String, value: String, valueColor: Color = Palette.Text) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint, modifier = Modifier.weight(1f))
        Text(
            value,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SpectrumBars(bass: Float, mid: Float, high: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = 28
        val gap = 3.dp.toPx()
        val idle = bass < 0.01f && mid < 0.01f && high < 0.01f
        if (idle) {
            val y = size.height * 0.74f
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Palette.overlay(0.1f), Color.Transparent)
                ),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.2.dp.toPx()
            )
            return@Canvas
        }
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val t = i.toFloat() / bars
            val base = when {
                t < 0.34f -> bass
                t < 0.72f -> mid
                else -> high
            }
            val mod = 0.45f + 0.55f * sin((i * 0.9f + base * 6f)).toFloat().let { (it + 1f) / 2f }
            val h = (size.height * (base * mod + 0.10f))
                .coerceIn((if (idle) 7f else 4f).dp.toPx(), size.height)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Palette.Cyan.copy(alpha = if (idle) 0.16f else 0.30f + 0.65f * base),
                        Palette.Violet.copy(alpha = if (idle) 0.06f else 0.10f + 0.35f * base)
                    )
                ),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2, bw / 2)
            )
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Palette.overlay(0.1f), Color.Transparent)
                )
            )
    )
}

@Composable
fun RowSpacer(w: Dp = 10.dp) {
    Spacer(Modifier.width(w))
}

@Composable
fun Pill(
    text: String,
    color: Color = Palette.Violet,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun CenterRow(horizontal: Arrangement.Horizontal = Arrangement.Start, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = horizontal, content = content)
}

@Composable
fun DensityAwareSize(dp: Dp): Float = with(LocalDensity.current) { dp.toPx() }

@Composable
fun muted(size: Int = 12) = TextStyle(fontSize = size.sp, color = Palette.TextFaint)

@Composable
fun accentText(text: String, color: Color = Palette.Cyan) = Text(
    text,
    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    color = color
)

@Composable
fun padded18(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(18.dp), content = content)
}
