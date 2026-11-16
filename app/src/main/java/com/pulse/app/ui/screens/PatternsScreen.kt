package com.pulse.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.pulse.app.core.WaveLayer
import com.pulse.app.core.WaveMath
import com.pulse.app.core.WaveOp
import com.pulse.app.core.WavePrimitive
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.GlowSlider
import com.pulse.app.ui.MiniSparkline
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.SectionHeader
import com.pulse.app.ui.VSpace
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PatternsScreen(vm: PulseViewModel) {
    val presets = vm.presets
    val custom by vm.customPatterns.collectAsState()
    val play by vm.player.state.collectAsState()
    val modules by vm.engine.modules.collectAsState()
    val canControl by vm.canControl.collectAsState()

    var editorOpen by remember { mutableStateOf(false) }
    /** 正在编辑的已有波形；null 表示新建 */
    var editing by remember { mutableStateOf<Pattern?>(null) }
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
                if (!canControl) {
                    Text(
                        "先连接玩具，这里会列出它可以动的通道。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                } else {
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
                                enabled = canControl,
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
                        .clickable { editing = null; editorOpen = true }
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
                            .clickable { editing = null; editorOpen = true },
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
                                .alpha(if (canControl) 1f else 0.4f)
                                .clickable(enabled = canControl) {
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
                                        .clickable {
                                            editing = p
                                            editorOpen = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        null,
                                        tint = Palette.TextFaint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
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
        WaveEditorDialog(vm, selected.toList(), sync, editing) {
            editorOpen = false
            editing = null
        }
    }
}

@Composable
private fun PatternCard(
    pattern: Pattern,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Violet.copy(alpha = 0.13f) else Palette.CardSolid)
            .border(
                1.dp,
                if (active) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.06f),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
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
    initial: Pattern? = null,
    onDismiss: () -> Unit
) {
    // 编辑已有波形时：有配方就进函数页，没有配方（手绘）就把旧的采样点摊回曲线上
    val startLayers = remember(initial) {
        WaveMath.decode(initial?.recipe) ?: listOf(WaveLayer(amplitude = 0.85f))
    }
    var tab by remember(initial) {
        mutableIntStateOf(if (initial?.recipe != null) 1 else 0)
    }
    val points = remember(initial) {
        mutableStateListOf<Pair<Float, Float>>().apply {
            val src = initial?.points
            if (initial?.recipe == null && src != null && src.size >= 2) {
                val last = (src.size - 1).toFloat()
                src.forEachIndexed { i, v -> add(i / last to v) }
            }
        }
    }
    val layers = remember(initial) {
        mutableStateListOf<WaveLayer>().apply { addAll(startLayers) }
    }
    var expanded by remember { mutableIntStateOf(0) }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "我的曲线") }
    var duration by remember(initial) { mutableFloatStateOf((initial?.durationMs ?: 5000) / 1000f) }

    val durationMs = (duration * 1000).toInt()
    val curve = remember(layers.toList()) { WaveMath.synthesize(layers) }
    val tooFast = layers.any { it.primitive.periodic && WaveMath.hertz(it.cycles, durationMs) > 3f }
    val seam = layers.any { WaveMath.hasSeam(it) }

    // 切标签时把默认名跟着换掉；编辑已有波形时保留它原来的名字
    LaunchedEffect(tab) {
        if (initial != null) return@LaunchedEffect
        if (tab == 1 && name == "我的曲线") name = "我的波形"
        if (tab == 0 && name == "我的波形") name = "我的曲线"
    }

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
                    Text(
                        if (initial != null) "编辑波形" else "波形编辑器",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.Text
                    )
                    Text(
                        when {
                            initial != null -> "改完保存会覆盖这一条，不会多出一条"
                            tab == 0 -> "手指在格子里拖动，横轴是时间，纵轴是强度"
                            else -> "几个基础函数叠起来，调幅度、频率、相位"
                        },
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

            VSpace(14.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorTab("手绘", tab == 0, Modifier.weight(1f)) { tab = 0 }
                EditorTab("函数", tab == 1, Modifier.weight(1f)) { tab = 1 }
            }

            VSpace(14.dp)

            if (tab == 0) {
                DrawPad(points, Modifier.fillMaxWidth().height(250.dp))
                VSpace(14.dp)
                NameField(name) { name = it.take(18) }
                VSpace(16.dp)
                DurationSlider(duration) { duration = it }
                Spacer(Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    CurvePreview(curve, Modifier.fillMaxWidth().height(150.dp))
                    VSpace(12.dp)
                    layers.forEachIndexed { i, layer ->
                        LayerCard(
                            index = i,
                            layer = layer,
                            expanded = expanded == i,
                            durationMs = durationMs,
                            canDelete = layers.size > 1,
                            onExpand = { expanded = if (expanded == i) -1 else i },
                            onChange = { layers[i] = it },
                            onDelete = {
                                layers.removeAt(i)
                                if (expanded >= layers.size) expanded = layers.size - 1
                            }
                        )
                        VSpace(10.dp)
                    }
                    if (layers.size < WaveMath.MAX_LAYERS) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Palette.overlay(0.04f))
                                .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                                .clickable {
                                    layers.add(WaveLayer(op = WaveOp.ADD, amplitude = 0.5f))
                                    expanded = layers.size - 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "＋ 加一层（最多 ${WaveMath.MAX_LAYERS} 层）",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.TextDim
                            )
                        }
                        VSpace(12.dp)
                    }
                    if (tooFast) {
                        Text(
                            "超过 3 Hz：玩具每秒只收到 10 帧，再高就是一片抖动，听不出起伏。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Rose.copy(alpha = 0.85f)
                        )
                        VSpace(6.dp)
                    }
                    if (seam) {
                        Text(
                            "周期数不是整数，循环回到起点时会跳一下。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextFaint
                        )
                        VSpace(6.dp)
                    }
                    Text(
                        "多层叠加超过 1.0 时会按峰值整体缩回，不会顶在满速。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                }
                VSpace(12.dp)
                NameField(name) { name = it.take(18) }
                VSpace(14.dp)
                DurationSlider(duration) { duration = it }
            }

            VSpace(12.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == 0) {
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
                }
                val ready = tab == 1 || points.size >= 2
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (ready)
                                Palette.ActionBrush
                            else Brush.horizontalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
                        )
                        .clickable(enabled = ready) {
                            val p = if (tab == 0) {
                                vm.saveCustom(
                                    name.ifBlank { "我的曲线" },
                                    durationMs,
                                    points.toList(),
                                    id = initial?.id
                                )
                            } else {
                                vm.saveSynthesis(
                                    name.ifBlank { "我的波形" },
                                    durationMs,
                                    layers.toList(),
                                    id = initial?.id
                                )
                            }
                            vm.playPattern(p, 1f, targetKeys, sync)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (initial != null) "保存修改" else "保存并播放",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ready) Color.White else Palette.TextFaint
                    )
                }
            }
            VSpace(10.dp)
            Text(
                when {
                    initial != null -> "改完点「保存修改」会覆盖原来那条波形。"
                    tab == 0 -> "画得越密越顺滑；只有 1 个点无法生成曲线。"
                    else -> "第 1 层是基底，后面每层按自己的运算叠上去。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
        }
    }
}

@Composable
private fun EditorTab(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) Palette.Violet.copy(alpha = 0.18f) else Palette.overlay(0.04f))
            .border(
                1.dp,
                if (active) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.07f),
                RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Palette.Violet else Palette.TextDim
        )
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
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
}

@Composable
private fun DurationSlider(duration: Float, onChange: (Float) -> Unit) {
    GlowSlider(
        label = "循环时长",
        value = (duration - 1f) / 19f,
        onChange = { onChange(1f + it * 19f) },
        accent = Palette.Cyan,
        valueText = "%.1fs".format(duration)
    )
}

/** 手绘区：点一下落一个点，拖动连续画 */
@Composable
private fun DrawPad(points: MutableList<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    Box(
        modifier
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
}

/** 函数波形预览 */
@Composable
private fun CurvePreview(curve: FloatArray, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.SurfaceHi)
            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(22.dp))
    ) {
        Canvas(Modifier.fillMaxSize().padding(1.dp)) {
            val w = size.width
            val h = size.height
            for (i in 1..3) {
                val y = h * i / 4f
                drawLine(
                    color = Palette.overlay(0.05f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 1..7) {
                val x = w * i / 8f
                drawLine(
                    color = Palette.overlay(0.035f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
            if (curve.size >= 2) {
                val path = Path()
                curve.forEachIndexed { i, v ->
                    val x = i.toFloat() / (curve.size - 1) * w
                    val y = (1f - v.coerceIn(0f, 1f)) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(w, h)
                    lineTo(0f, h)
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
            }
        }
    }
}

@Composable
private fun LayerCard(
    index: Int,
    layer: WaveLayer,
    expanded: Boolean,
    durationMs: Int,
    canDelete: Boolean,
    onExpand: () -> Unit,
    onChange: (WaveLayer) -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (index == 0) "基底" else "层 ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "${layer.primitive.label} ${fmtCycles(layer.cycles)}×",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text
            )
            Spacer(Modifier.weight(1f))
            if (index > 0) {
                Pill(layer.op.label, Palette.Violet)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (expanded) "收起" else "调整",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim
            )
        }

        if (!expanded) return@GlassCard

        VSpace(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            WavePrimitive.entries.forEach { p ->
                val on = layer.primitive == p
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (on) Palette.Violet.copy(alpha = 0.18f) else Palette.overlay(0.04f)
                        )
                        .border(
                            1.dp,
                            if (on) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.06f),
                            RoundedCornerShape(11.dp)
                        )
                        .clickable { onChange(layer.copy(primitive = p)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = if (on) Palette.Violet else Palette.TextDim
                    )
                }
            }
        }

        if (index > 0) {
            VSpace(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(WaveOp.ADD, WaveOp.MUL, WaveOp.MAX, WaveOp.MIN, WaveOp.MIX).forEach { op ->
                    val on = layer.op == op
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (on) Palette.Cyan.copy(alpha = 0.16f) else Palette.overlay(0.04f)
                            )
                            .border(
                                1.dp,
                                if (on) Palette.Cyan.copy(alpha = 0.45f) else Palette.overlay(0.06f),
                                RoundedCornerShape(11.dp)
                            )
                            .clickable { onChange(layer.copy(op = op)) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            op.label,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                            color = if (on) Palette.Cyan else Palette.TextDim
                        )
                    }
                }
            }
        }

        VSpace(14.dp)
        GlowSlider(
            label = "幅度",
            value = layer.amplitude,
            onChange = { onChange(layer.copy(amplitude = it)) },
            accent = Palette.Violet,
            valueText = "${(layer.amplitude * 100).roundToInt()}%"
        )

        if (layer.primitive.periodic) {
            VSpace(10.dp)
            GlowSlider(
                label = "每循环起伏",
                value = (layer.cycles - WaveMath.MIN_CYCLES) /
                    (WaveMath.MAX_CYCLES - WaveMath.MIN_CYCLES),
                onChange = { v ->
                    val raw = WaveMath.MIN_CYCLES + v * (WaveMath.MAX_CYCLES - WaveMath.MIN_CYCLES)
                    // 吸附到 1/4 次，整数周期随手就能调到，循环接缝才不会跳
                    onChange(layer.copy(cycles = (raw * 4).roundToInt() / 4f))
                },
                accent = Palette.Cyan,
                valueText = "${fmtCycles(layer.cycles)}× · " +
                    "%.2f Hz".format(WaveMath.hertz(layer.cycles, durationMs))
            )
        }

        VSpace(10.dp)
        GlowSlider(
            label = "相位",
            value = layer.phase,
            onChange = { onChange(layer.copy(phase = it)) },
            accent = Palette.Rose,
            valueText = "${(layer.phase * 100).roundToInt()}%"
        )

        if (canDelete) {
            VSpace(10.dp)
            Box(
                Modifier
                    .clickable(onClick = onDelete)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "删除这一层",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Rose
                )
            }
        }
    }
}

private fun fmtCycles(v: Float): String {
    val s = "%.2f".format(v)
    return when {
        s.endsWith(".00") -> s.dropLast(3)
        s.endsWith("0") -> s.dropLast(1)
        else -> s
    }
}
