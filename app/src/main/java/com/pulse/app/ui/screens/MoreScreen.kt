package com.pulse.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pulse.app.PulseViewModel
import com.pulse.app.core.CrashLog
import com.pulse.app.core.TxLog
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.GlowSlider
import com.pulse.app.ui.KeyValue
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.SectionHeader
import com.pulse.app.ui.SpectrumBars
import com.pulse.app.ui.StatusDot
import com.pulse.app.ui.VSpace

@Composable
fun MoreScreen(vm: PulseViewModel) {
    val ctx = LocalContext.current
    val music by vm.music.state.collectAsState()
    val musicSync by vm.musicSync.collectAsState()
    val sensitivity by vm.music.sensitivity.collectAsState()
    val logs by vm.ble.log.collectAsState()
    val saved by vm.savedToys.collectAsState()
    val record by vm.activeRecord.collectAsState()
    val modules by vm.engine.modules.collectAsState()
    val voiceTargets by vm.voiceTargets.collectAsState()

    var showLogs by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setMusicSync(true) else vm.notify("没有麦克风权限，无法使用声控")
    }

    fun toggleMusic(enabled: Boolean) {
        if (!enabled) {
            vm.setMusicSync(false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.setMusicSync(true) else audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
            VSpace(2.dp)
            Text(
                "设备记忆、声控与关于",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Palette.Lime.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Memory, null, tint = Palette.Lime, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("我的玩具", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "${saved.size} 台 · 连接后自动恢复设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    if (saved.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.clearAllToys() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("清空", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                        }
                    }
                }

                if (saved.isEmpty()) {
                    VSpace(12.dp)
                    Text(
                        "连过一次的玩具会出现在这里，下次不用重新识别。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                } else {
                    VSpace(12.dp)
                    DividerLine()
                    saved.forEach { rec ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDot(
                                if (record?.address == rec.address) Palette.Lime else Palette.TextFaint,
                                6.dp
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    shortName(rec.modelName, rec.brand),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Palette.Text
                                )
                                Text(
                                    "${rec.brand} · ${rec.modules.size} 个通道",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { vm.deleteToy(rec.address) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    null,
                                    tint = Palette.TextFaint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Palette.Cyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Mic, null, tint = Palette.Cyan, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("声控", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            if (music.running) "正在听声音" else "跟着音乐或声音起伏",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Switch(
                        checked = musicSync,
                        onCheckedChange = { toggleMusic(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Palette.VioletDeep,
                            uncheckedThumbColor = Palette.TextFaint,
                            uncheckedTrackColor = Palette.overlay(0.08f)
                        )
                    )
                }
                VSpace(14.dp)
                SpectrumBars(
                    bass = music.bass,
                    mid = music.mid,
                    high = music.high,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
                VSpace(10.dp)
                Row(Modifier.fillMaxWidth()) {
                    MiniMetric("低频", music.bass, Modifier.weight(1f))
                    MiniMetric("中频", music.mid, Modifier.weight(1f))
                    MiniMetric("高频", music.high, Modifier.weight(1f))
                    MiniMetric("强度", music.intensity, Modifier.weight(1f))
                }
                VSpace(12.dp)
                GlowSlider(
                    label = "灵敏度",
                    value = sensitivity,
                    onChange = { vm.setMusicSensitivity(it) },
                    accent = Palette.Cyan
                )
                VSpace(6.dp)
                Text(
                    "安静时会自动归零；环境越吵，灵敏度可以往左调。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
                if (modules.isNotEmpty()) {
                    VSpace(12.dp)
                    Text("作用于", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                    VSpace(8.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        modules.forEach { spec ->
                            val on = voiceTargets.contains(spec.key)
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
                                        val next = voiceTargets.toMutableSet()
                                        if (on) next.remove(spec.key) else next.add(spec.key)
                                        vm.setVoiceTargets(next)
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
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Palette.Violet.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = Palette.Violet, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("音乐", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "导入歌曲，按激昂程度自动控制",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(11.dp))
                            .clickable { showMusic = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("打开", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("关于", "HushWave 1.0.1")
                Text(
                    "支持 Galaku、Svakom、Lovense、We-Vibe、MagicMotion、Kiiroo、Satisfyer。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim
                )
                VSpace(8.dp)
                Text(
                    "所有指令都在手机本地生成，通过蓝牙直接发送，不联网、不上传数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
                VSpace(8.dp)
                Text(
                    "部分品牌的指令格式参考了开源项目 Buttplug（BSD-3-Clause）。",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = Palette.TextFaint
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("手动添加设备", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "没识别出来的玩具，自己指定品牌和型号",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(11.dp))
                            .clickable { showManual = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("添加", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("遇到问题", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            "查看连接记录，或把最近一次错误发给我",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(11.dp))
                            .clickable { showLogs = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("查看", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
                if (crash != null) {
                    VSpace(10.dp)
                    DividerLine()
                    VSpace(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "有一次异常退出记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Rose,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "清空",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.TextFaint,
                            modifier = Modifier.clickable {
                                CrashLog.clear(ctx)
                                crash = null
                            }
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "HUSHWAVE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = Palette.TextFaint
                )
                Spacer(Modifier.weight(1f))
                Pill("1.0", Palette.TextFaint)
            }
        }
    }

    if (showMusic) {
        MusicDialog(vm) { showMusic = false }
    }

    if (showManual) {
        ManualAddDialog(vm) { showManual = false }
    }

    if (showLogs) {
        DialogLogs(
            crash = crash,
            logs = logs,
            onDismiss = { showLogs = false }
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
        VSpace(3.dp)
        Text(
            "${(value.coerceIn(0f, 1f) * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = Palette.TextDim
        )
        VSpace(4.dp)
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.overlay(0.07f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Palette.Cyan)
            )
        }
    }
}

@Composable
private fun DialogLogs(crash: String?, logs: List<TxLog>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.DialogBg)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("连接记录", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
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
                "只记录蓝牙收发，不含个人信息。出问题时截图发我即可。",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
            VSpace(16.dp)
            LazyColumn(Modifier.fillMaxSize()) {
                if (crash != null) {
                    item {
                        Text("最近一次异常", style = MaterialTheme.typography.labelSmall, color = Palette.Rose)
                        VSpace(6.dp)
                        Text(
                            crash,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.sp
                            ),
                            color = Palette.TextDim
                        )
                        VSpace(16.dp)
                    }
                }
                item {
                    Text("最近收发", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                    VSpace(8.dp)
                }
                if (logs.isEmpty()) {
                    item {
                        Text(
                            "还没有记录。连接设备后会出现。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                } else {
                    items(logs.takeLast(60).reversed().size) { idx ->
                        val l = logs.takeLast(60).reversed()[idx]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                l.timeLabel + " ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp
                                ),
                                color = Palette.TextFaint
                            )
                            Text(
                                when (l.dir) {
                                    TxLog.Dir.TX -> "发送 "
                                    TxLog.Dir.RX -> "接收 "
                                    TxLog.Dir.SYS -> "-- "
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp
                                ),
                                color = when (l.dir) {
                                    TxLog.Dir.TX -> Palette.Violet
                                    TxLog.Dir.RX -> Palette.Cyan
                                    TxLog.Dir.SYS -> Palette.TextFaint
                                }
                            )
                            Text(
                                if (l.bytes.isEmpty()) l.note else l.hex,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = Palette.TextDim,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shortName(name: String, brand: String): String {
    val prefixes = listOf(brand, "Svakom", "Galaku", "Lovense", "WeVibe", "We-Vibe", "MagicMotion")
    val hit = prefixes.filter { it.isNotBlank() }.firstOrNull { name.startsWith("$it ", true) }
    return if (hit != null) name.substring(hit.length).trim() else name
}
