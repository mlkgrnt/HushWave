package com.pulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.app.PulseViewModel
import com.pulse.app.core.BleState
import com.pulse.app.core.ModuleSpec
import com.pulse.app.core.ModuleType
import com.pulse.app.ui.AuraOrb
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.GlowSlider
import com.pulse.app.ui.MiniSparkline
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.SectionHeader
import com.pulse.app.ui.StatusDot
import com.pulse.app.ui.VSpace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ControlScreen(vm: PulseViewModel) {
    val modules by vm.engine.modules.collectAsState()
    val values by vm.engine.values.collectAsState()
    val protocol by vm.engine.protocol.collectAsState()
    val model by vm.engine.model.collectAsState()
    val demo by vm.engine.demo.collectAsState()
    val bleState by vm.ble.state.collectAsState()
    val shake by vm.shakeMode.collectAsState()
    val musicSync by vm.musicSync.collectAsState()
    val play by vm.player.state.collectAsState()
    val record by vm.activeRecord.collectAsState()
    val quickTargets by vm.quickTargets.collectAsState()
    val masterActive by vm.engine.masterActive.collectAsState()
    val masterValue by vm.engine.masterValue.collectAsState()


    val connected = bleState is BleState.Ready
    val primary = modules.firstOrNull { it.type == ModuleType.VIBRATE } ?: modules.firstOrNull()
    val energy = values.values.maxOrNull() ?: 0f

    var trace by remember { mutableStateOf(List(72) { 0f }) }
    LaunchedEffect(Unit) {
        while (true) {
            trace = (trace + (vm.engine.values.value.values.maxOrNull() ?: 0f)).takeLast(72)
            delay(60)
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model?.shortName ?: record?.modelName?.let { stripBrand(it, record?.brand ?: "") }
                        ?: if (demo) "预览" else "未连接",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.Text
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (connected) Palette.Lime else Palette.TextFaint, 6.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                connected -> record?.brand ?: protocol?.brand ?: ""
                                else -> "等待连接"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                }
                when {
                    musicSync -> Pill("MIC", Palette.Cyan)
                    shake -> Pill("SHAKE", Palette.Rose)
                    play != null -> Pill("PATTERN", Palette.Violet)
                    record != null -> Pill("已保存", Palette.Lime)
                }
            }
            VSpace(4.dp)
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(292.dp),
                contentAlignment = Alignment.Center
            ) {
                AuraOrb(
                    level = energy,
                    active = energy > 0.015f,
                    modifier = Modifier.size(292.dp)
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), glow = true, padding = 18.dp) {
                if (primary != null) {
                    GlowSlider(
                        label = "强度（总）",
                        value = if (masterActive) masterValue else 0f,
                        onChange = { vm.setMaster(it) },
                        accent = Palette.Violet,
                        valueText = if (masterActive) "${(masterValue * 100).roundToInt()}%" else "单通道接管"
                    )
                } else {
                    Text(
                        "还没有选择设备。先在「设备」页连接或指定协议。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }
                VSpace(6.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "输出轨迹",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(energy * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Palette.Cyan
                    )
                }
                MiniSparkline(
                    points = trace.toFloatArray(),
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    color = Palette.Violet.copy(alpha = 0.9f)
                )
            }
            VSpace(12.dp)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ActionTile(
                    title = "全部停止",
                    icon = Icons.Rounded.Stop,
                    tint = Palette.Rose,
                    modifier = Modifier.weight(1f)
                ) { vm.stopAll() }
                ActionTile(
                    title = "脉冲",
                    icon = Icons.Rounded.Bolt,
                    tint = Palette.Cyan,
                    modifier = Modifier.weight(1f)
                ) { vm.pulse() }
                ActionTile(
                    title = if (shake) "摇一摇 开" else "摇一摇",
                    icon = Icons.Rounded.Vibration,
                    tint = if (shake) Palette.Violet else Palette.TextDim,
                    active = shake,
                    modifier = Modifier.weight(1f)
                ) { vm.setShakeMode(!shake) }
            }
            if (modules.isNotEmpty()) {
                VSpace(12.dp)
                Text(
                    "摇一摇 / 脉冲 作用于",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextFaint
                )
                VSpace(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    modules.forEach { spec ->
                        val on = quickTargets.contains(spec.key)
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
                                .clickable {
                                    val next = quickTargets.toMutableSet()
                                    if (on) next.remove(spec.key) else next.add(spec.key)
                                    vm.setQuickTargets(next)
                                }
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
            VSpace(18.dp)
        }

        if (play != null) {
            item {
                val p = play!!
                GlassCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "正在播放 · ${p.pattern.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text
                            )
                            VSpace(6.dp)
                            MiniSparkline(
                                points = p.pattern.points,
                                progress = p.phase,
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        RoundIcon(Icons.Rounded.Pause) { vm.togglePattern() }
                        Spacer(Modifier.width(8.dp))
                        RoundIcon(Icons.Rounded.Stop) { vm.stopPattern() }
                    }
                }
                VSpace(18.dp)
            }
        }

        item {
            SectionHeader(
                "通道",
                if (modules.isEmpty()) "" else "${modules.size} 个可调通道"
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                if (modules.isEmpty()) {
                    Text(
                        "连接设备后这里会出现可调通道。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                } else {
                    modules.forEachIndexed { i, spec ->
                        if (i > 0) VSpace(18.dp)
                        ModuleSlider(spec, values[spec.key] ?: 0f) { v ->
                            vm.setModule(spec.key, v)
                        }
                    }
                }
            }
            VSpace(16.dp)
        }

        if (record != null) {
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                    Text(
                        "这台玩具的通道和上次的设置已保存，下次连接会自动恢复。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }
            }
        }
    }
}

private fun stripBrand(name: String, brand: String): String {
    val prefixes = listOf(brand, "Svakom", "Galaku", "Lovense", "WeVibe", "We-Vibe", "MagicMotion")
    val hit = prefixes.filter { it.isNotBlank() }.firstOrNull { name.startsWith("$it ", true) }
    return if (hit != null) name.substring(hit.length).trim() else name
}

@Composable
private fun ModuleSlider(spec: ModuleSpec, value: Float, onChange: (Float) -> Unit) {
    val accent = when (spec.type) {
        ModuleType.VIBRATE -> Palette.Violet
        ModuleType.SUCTION, ModuleType.ESTIM, ModuleType.CONSTRICT -> Palette.Rose
        ModuleType.THRUST, ModuleType.ROTATE, ModuleType.OSCILLATE, ModuleType.ESTIM_FREQ -> Palette.Cyan
        ModuleType.PUMP -> Color(0xFFB794F4)
        ModuleType.HEAT -> Color(0xFFFFA26B)
        ModuleType.LED -> Color(0xFF9AE6B4)
    }
    val label = if (spec.type == ModuleType.ESTIM_FREQ) spec.label else spec.label
    GlowSlider(
        label = label,
        value = value,
        onChange = onChange,
        accent = accent,
        valueText = when (spec.type) {
            ModuleType.ESTIM_FREQ -> "${spec.raw(value)} Hz"
            else -> "${spec.raw(value)} / ${spec.max}"
        }
    )
}

@Composable
private fun ActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) tint.copy(alpha = 0.14f) else Palette.overlay(0.045f))
            .border(
                1.dp,
                if (active) tint.copy(alpha = 0.45f) else Palette.overlay(0.06f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            VSpace(4.dp)
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = Palette.TextDim
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Palette.overlay(0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Palette.Text, modifier = Modifier.size(17.dp))
    }
}
