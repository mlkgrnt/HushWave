package com.pulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.app.PulseViewModel
import com.pulse.app.core.ModuleType
import com.pulse.app.core.Pattern
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.GlowSlider
import com.pulse.app.ui.MiniSparkline
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.SectionHeader
import com.pulse.app.ui.VSpace
import kotlin.math.abs

@Composable
fun PatternsScreen(vm: PulseViewModel) {
    val presets = vm.presets
    val custom by vm.customPatterns.collectAsState()
    val play by vm.player.state.collectAsState()
    val modules by vm.engine.modules.collectAsState()

    var editorOpen by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    val sync by vm.patternSync.collectAsState()
    val patternTargets by vm.patternTargets.collectAsState()
    val selected = remember { mutableStateListOf<String>() }
    val chipsScroll = rememberScrollState()
    var seededFor by remember { mutableStateOf<List<String>>(emptyList()) }

    // 通道选择跟着设备走：换设备时恢复该设备上次的选择，没有记录才默认选第一路振动
    LaunchedEffect(modules, patternTargets) {
        val keys = modules.map { it.key }
        val valid = patternTargets.filter { it in keys }
        when {
            valid.isNotEmpty() -> {
                if (valid != selected.toList()) {
                    selected.clear()
                    selected.addAll(valid)
                }
                seededFor = keys
            }

            // 用户手动把通道全关了：不要把默认值又加回去
            seededFor == keys -> selected.retainAll { it in keys }

            else -> {
                selected.clear()
                val first = modules.firstOrNull { it.type == ModuleType.VIBRATE }
                    ?: modules.firstOrNull()
                first?.let { selected.add(it.key) }
                seededFor = keys
            }
        }
    }

    fun toggle(key: String) {
        if (selected.contains(key)) selected.remove(key) else selected.add(key)
        vm.setPatternTarget(selected.toList(), sync)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("波形", style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
                    Text(
                        "选好通道，点一下曲线开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }
                Pill(if (play == null) "自动循环" else "播放中", Palette.Violet)
            }
            VSpace(16.dp)
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "作用于",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(chipsScroll),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (modules.isEmpty()) {
                            Text(
                                "先连接玩具",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextFaint
                            )
                        }
                        modules.forEach { spec ->
                            val on = selected.contains(spec.key)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (on) Palette.Violet.copy(alpha = 0.18f)
                                        else Palette.overlay(0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (on) Palette.Violet.copy(alpha = 0.5f)
                                        else Palette.overlay(0.06f),
                                        RoundedCornerShape(11.dp)
                                    )
                                    .clickable { toggle(spec.key) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    spec.label,
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                    color = if (on) Palette.Violet else Palette.TextDim
                                )
                            }
                        }
                    }
                }
                VSpace(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("同步", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                        Text(
                            if (sync) "选中的通道一起动" else "按顺序错开，形成波浪",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Switch(
                        checked = sync,
                        onCheckedChange = { on ->
                            vm.setPatternTarget(selected.toList(), on)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Palette.VioletDeep,
                            uncheckedThumbColor = Palette.TextFaint,
                            uncheckedTrackColor = Palette.overlay(0.08f)
                        )
                    )
                }
            }
            VSpace(18.dp)
        }

        if (play != null) {
            item {
                val p = play!!
                GlassCard(Modifier.fillMaxWidth(), glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("正在播放", style = MaterialTheme.typography.labelSmall, color = Palette.Cyan)
                            VSpace(3.dp)
                            Text(
                                p.pattern.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Palette.Text
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Palette.overlay(0.07f))
                                .clickable { vm.togglePattern() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = Palette.Text
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Palette.Rose.copy(alpha = 0.14f))
                                .clickable { vm.stopPattern() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Stop, null, tint = Palette.Rose)
                        }
                    }
                    VSpace(10.dp)
                    Text(
                        "${p.targetKeys.size} 个通道 · " + if (p.sync) "同步" else "波浪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                    VSpace(12.dp)
                    MiniSparkline(
                        points = p.pattern.points,
                        progress = p.phase,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        strokeWidth = 2.2.dp
                    )
                    VSpace(14.dp)
                    GlowSlider(
                        label = "整体幅度",
                        value = scale,
                        onChange = {
                            scale = it
                            vm.setPatternScale(it)
                        },
                        accent = Palette.Violet
                    )
                }
                VSpace(18.dp)
            }
        }

        item {
            SectionHeader("预设", "点一下直接播放")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { p ->
                            PatternCard(
                                pattern = p,
                                active = play?.pattern?.id == p.id,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (selected.isNotEmpty()) {
                                    vm.playPattern(p, scale, selected.toList(), sync)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            VSpace(22.dp)
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("我的波形", "${custom.size} 条", Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Violet.copy(alpha = 0.15f))
                        .border(1.dp, Palette.Violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { editorOpen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, null, tint = Palette.Violet, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("绘制", style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
                    }
                }
            }
            if (custom.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "还没有自定义波形。画出任意曲线，它会按你设定的时长循环播放。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                    VSpace(12.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Palette.VioletDeep.copy(alpha = 0.9f), Color(0xFF3E7BFF).copy(alpha = 0.9f))
                                )
                            )
                            .clickable { editorOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("打开波形编辑器", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    custom.forEach { p ->
                        GlassCard(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selected.isNotEmpty()) {
                                        vm.playPattern(p, scale, selected.toList(), sync)
                                    }
                                },
                            padding = 15.dp,
                            radius = 20.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                                    VSpace(2.dp)
                                    Text(
                                        "${p.durationMs / 1000f}s · ${p.points.size} 采样",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.sp
                                        ),
                                        color = Palette.TextFaint
                                    )
                                }
                                MiniSparkline(
                                    points = p.points,
                                    modifier = Modifier.width(96.dp).height(30.dp),
                                    color = Palette.Cyan
                                )
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { vm.deleteCustom(p.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        null,
                                        tint = Palette.TextFaint,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        WaveEditorDialog(vm, selected.toList(), sync) { editorOpen = false }
    }
}

@Composable
private fun PatternCard(
    pattern: Pattern,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Violet.copy(alpha = 0.13f) else Palette.CardSolid)
            .border(
                1.dp,
                if (active) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.06f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pattern.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) Palette.Violet else Palette.Text,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${pattern.durationMs / 1000f}s",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.sp
                ),
                color = Palette.TextFaint
            )
        }
        VSpace(8.dp)
        MiniSparkline(
            points = pattern.points,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            color = if (active) Palette.Violet else Palette.Cyan.copy(alpha = 0.75f)
        )
        VSpace(8.dp)
        Text(pattern.tag, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
    }
}

@Composable
private fun WaveEditorDialog(
    vm: PulseViewModel,
    targetKeys: List<String>,
    sync: Boolean,
    onDismiss: () -> Unit
) {
    val points = remember { mutableStateListOf<Pair<Float, Float>>() }
    var name by remember { mutableStateOf("我的曲线") }
    var duration by remember { mutableFloatStateOf(5f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.DialogBg)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("波形编辑器", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                    Text(
                        "手指在格子里拖动，横轴是时间，纵轴是强度",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }
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

            VSpace(18.dp)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Palette.SurfaceHi)
                    .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(22.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            points.add(
                                (o.x / size.width).coerceIn(0f, 1f) to
                                    (1f - o.y / size.height).coerceIn(0f, 1f)
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o ->
                                points.add(
                                    (o.x / size.width).coerceIn(0f, 1f) to
                                        (1f - o.y / size.height).coerceIn(0f, 1f)
                                )
                            },
                            onDrag = { change, _ ->
                                points.add(
                                    (change.position.x / size.width).coerceIn(0f, 1f) to
                                        (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                )
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize().padding(1.dp)) {
                    val w = size.width
                    val h = size.height
                    for (i in 1..7) {
                        val y = h * i / 8f
                        drawLine(
                            color = Palette.overlay(0.045f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    for (i in 1..11) {
                        val x = w * i / 12f
                        drawLine(
                            color = Palette.overlay(0.035f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    if (points.size >= 2) {
                        val sorted = points.sortedBy { it.first }
                        val path = Path()
                        sorted.forEachIndexed { i, p ->
                            val x = p.first * w
                            val y = (1f - p.second) * h
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(sorted.last().first * w, h)
                            lineTo(sorted.first().first * w, h)
                            close()
                        }
                        drawPath(
                            fill,
                            brush = Brush.verticalGradient(
                                listOf(Palette.Violet.copy(alpha = 0.30f), Color.Transparent)
                            )
                        )
                        drawPath(
                            path,
                            brush = Brush.horizontalGradient(listOf(Palette.Violet, Palette.Cyan)),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                        sorted.forEach { p ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.75f),
                                radius = 2.dp.toPx(),
                                center = Offset(p.first * w, (1f - p.second) * h)
                            )
                        }
                    } else {
                        drawLine(
                            color = Palette.TextFaint.copy(alpha = 0.5f),
                            start = Offset(0f, h * 0.7f),
                            end = Offset(w, h * 0.7f),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
                if (points.isEmpty()) {
                    Text(
                        "在这里画",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            VSpace(14.dp)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(18) },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Violet,
                    unfocusedBorderColor = Palette.overlay(0.12f),
                    focusedLabelColor = Palette.Violet,
                    unfocusedLabelColor = Palette.TextFaint,
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text
                )
            )

            VSpace(16.dp)
            GlowSlider(
                label = "循环时长",
                value = (duration - 1f) / 19f,
                onChange = { duration = 1f + it * 19f },
                accent = Palette.Cyan,
                valueText = "%.1fs".format(duration)
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.overlay(0.05f))
                        .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(16.dp))
                        .clickable { points.clear() }
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("清空", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (points.size >= 2)
                                Palette.ActionBrush
                            else Brush.horizontalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
                        )
                        .clickable(enabled = points.size >= 2) {
                            val p = vm.saveCustom(
                                name.ifBlank { "我的曲线" },
                                (duration * 1000).toInt(),
                                points.toList()
                            )
                            vm.playPattern(p, 1f, targetKeys, sync)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "保存并播放",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (points.size >= 2) Color.White else Palette.TextFaint
                    )
                }
            }
            VSpace(10.dp)
            Text(
                "画得越密越顺滑；只有 1 个点无法生成曲线。",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
        }
    }
}
