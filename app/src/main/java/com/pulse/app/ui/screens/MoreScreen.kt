package com.pulse.app.ui.screens

import com.pulse.app.R
import com.pulse.app.core.ApkUpdater
import com.pulse.app.core.LocaleManager
import com.pulse.app.core.Str
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pulse.app.BuildConfig
import com.pulse.app.PulseViewModel
import com.pulse.app.core.BleState
import com.pulse.app.core.CrashLog
import com.pulse.app.core.DeviceReport
import com.pulse.app.core.ScanItem
import com.pulse.app.core.TxLog
import com.pulse.app.core.UpdateChecker
import com.pulse.app.core.UpdateInfo
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
import kotlinx.coroutines.launch

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
    val canControl by vm.canControl.collectAsState()
    val updateState by vm.updater.state.collectAsState()

    var showLogs by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var langTag by remember { mutableStateOf(LocaleManager.selected(ctx)) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkMsg by remember { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { vm.notify(Str.s(R.string.set_000)) }
    }

    fun diagnostics(): String = buildString {
        appendLine("HushWave ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(Str.s(R.string.x_device_meta, Build.MANUFACTURER, Build.MODEL))
        CrashLog.read(ctx)?.let {
            appendLine()
            appendLine(Str.s(R.string.set_001))
            appendLine(it.take(4000))
        }
        if (logs.isNotEmpty()) {
            appendLine()
            appendLine(Str.s(R.string.set_002))
            logs.takeLast(40).forEach { l ->
                val body = if (l.bytes.isEmpty()) l.note else l.hex
                appendLine("${l.timeLabel} ${l.dir} $body")
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.setMusicSync(true) else vm.notify(Str.s(R.string.set_003))
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
            Text(Str.s(R.string.set_004), style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
            VSpace(2.dp)
            Text(
                Str.s(R.string.set_005),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
        }

        item { AppearanceCard() }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showLang = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Palette.Cyan.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = Palette.Cyan, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Str.s(R.string.lang_title), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            Str.s(R.string.lang_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Text(
                        when (langTag) {
                            "zh" -> Str.s(R.string.lang_zh)
                            "en" -> Str.s(R.string.lang_en)
                            "es" -> Str.s(R.string.lang_es)
                            else -> Str.s(R.string.lang_follow)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Cyan
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
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
                            .background(Palette.Lime.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Memory, null, tint = Palette.Lime, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Str.s(R.string.set_006), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            Str.s(R.string.x_saved_toys, saved.size),
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
                            Text(Str.s(R.string.set_007), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                        }
                    }
                }

                if (saved.isEmpty()) {
                    VSpace(12.dp)
                    Text(
                        Str.s(R.string.set_008),
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
                                    shortName(rec.modelName, Str.data(rec.brand)),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Palette.Text
                                )
                                Text(
                                    Str.s(R.string.x_record_channels, Str.data(rec.brand), rec.modules.size),
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
                        Text(Str.s(R.string.set_009), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            if (music.running) Str.s(R.string.set_010) else Str.s(R.string.set_011),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                    Switch(
                        checked = musicSync,
                        onCheckedChange = { toggleMusic(it) },
                        enabled = canControl,
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
                    MiniMetric(Str.s(R.string.set_012), music.bass, Modifier.weight(1f))
                    MiniMetric(Str.s(R.string.set_013), music.mid, Modifier.weight(1f))
                    MiniMetric(Str.s(R.string.set_014), music.high, Modifier.weight(1f))
                    MiniMetric(Str.s(R.string.set_015), music.intensity, Modifier.weight(1f))
                }
                VSpace(12.dp)
                GlowSlider(
                    label = Str.s(R.string.set_016),
                    value = sensitivity,
                    onChange = { vm.setMusicSensitivity(it) },
                    accent = Palette.Cyan
                )
                VSpace(6.dp)
                Text(
                    Str.s(R.string.set_017),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
                if (!canControl) {
                    VSpace(10.dp)
                    Text(
                        Str.s(R.string.set_018),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                } else if (modules.isNotEmpty()) {
                    VSpace(12.dp)
                    Text(Str.s(R.string.set_019), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
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
                                    Str.data(spec.label),
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
                        Text(Str.s(R.string.set_020), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            Str.s(R.string.set_021),
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
                        Text(Str.s(R.string.set_022), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader(Str.s(R.string.set_023), BuildConfig.VERSION_NAME)
                Text(
                    Str.s(R.string.set_024) +
                        Str.s(R.string.set_025),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim
                )
                VSpace(8.dp)
                Text(
                    Str.s(R.string.set_026),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
                VSpace(14.dp)
                DividerLine()
                VSpace(12.dp)
                ActionRow(
                    title = if (checking) Str.s(R.string.set_027) else Str.s(R.string.set_028),
                    subtitle = checkMsg ?: Str.s(R.string.x_version_current, BuildConfig.VERSION_NAME),
                    icon = Icons.Rounded.SystemUpdate,
                    accent = Palette.Cyan,
                    enabled = !checking,
                    label = if (checking) "…" else Str.s(R.string.set_029)
                ) {
                    if (checking) return@ActionRow
                    checking = true
                    checkMsg = null
                    scope.launch {
                        UpdateChecker.latest()
                            .onSuccess { info ->
                                update = info
                                checkMsg = if (UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                                    Str.s(R.string.x_version_new, info.version)
                                } else {
                                    Str.s(R.string.set_030)
                                }
                                if (!UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                                    vm.notify(Str.s(R.string.set_030))
                                }
                            }
                            .onFailure { e ->
                                checkMsg = Str.s(R.string.x_check_failed, e.message ?: Str.s(R.string.x_network_unavailable))
                                vm.notify(Str.s(R.string.set_031))
                            }
                        checking = false
                    }
                }
                val upd = update
                if (upd != null && UpdateChecker.isNewer(upd.version, BuildConfig.VERSION_NAME)) {
                    VSpace(10.dp)
                    val st = updateState
                    when (st) {
                        is ApkUpdater.State.Downloading -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Palette.overlay(0.05f))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        Str.s(R.string.update_downloading, (st.progress * 100).toInt()),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Palette.Text
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        Str.s(R.string.dev_004),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Palette.Cyan,
                                        modifier = Modifier.clickable { vm.cancelUpdateDownload() }
                                    )
                                }
                                VSpace(10.dp)
                                LinearProgressIndicator(
                                    progress = { st.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Palette.Cyan,
                                    trackColor = Palette.overlay(0.08f)
                                )
                                VSpace(8.dp)
                                Text(
                                    Str.s(
                                        R.string.update_source,
                                        if (st.source == 0) "GitHub"
                                        else Str.s(R.string.update_source_mirror, st.source)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextFaint
                                )
                            }
                        }

                        is ApkUpdater.State.Ready -> {
                            Column(Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Palette.ActionBrush)
                                        .clickable { vm.installUpdate() }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        Str.s(R.string.update_install),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                                VSpace(6.dp)
                                Text(
                                    Str.s(R.string.update_ready),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.TextFaint
                                )
                            }
                        }

                        is ApkUpdater.State.Failed -> {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    Str.s(R.string.update_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextDim
                                )
                                VSpace(8.dp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Palette.ActionBrush)
                                            .clickable { vm.downloadUpdate(upd.version) }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            Str.s(R.string.update_retry),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Palette.overlay(0.06f))
                                            .clickable { openUrl(upd.pageUrl) }
                                            .padding(horizontal = 18.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            "GitHub",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Palette.TextDim
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Palette.ActionBrush)
                                    .clickable { vm.downloadUpdate(upd.version) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Str.s(R.string.update_download) + " " + upd.version,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                VSpace(12.dp)
                ActionRow(
                    title = Str.s(R.string.set_032),
                    subtitle = "github.com/mlkgrnt/HushWave",
                    icon = Icons.Rounded.OpenInNew,
                    accent = Palette.Violet,
                    label = Str.s(R.string.set_022)
                ) {
                    openUrl(UpdateChecker.REPO)
                }
                VSpace(12.dp)
                ActionRow(
                    title = Str.s(R.string.set_033),
                    subtitle = Str.s(R.string.set_034),
                    icon = Icons.Rounded.Description,
                    accent = Palette.Lime,
                    label = Str.s(R.string.set_035)
                ) {
                    openUrl(UpdateChecker.REPO + "/blob/main/LICENSE")
                }
                VSpace(12.dp)
                Text(
                    Str.s(R.string.set_036) +
                        Str.s(R.string.set_037),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                    color = Palette.TextFaint
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Str.s(R.string.set_038), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            Str.s(R.string.set_039),
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
                        Text(Str.s(R.string.set_040), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
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
                            .background(Palette.Rose.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = Palette.Rose, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Str.s(R.string.set_041), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(
                            Str.s(R.string.set_042),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                }
                VSpace(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.ActionBrush)
                            .clickable {
                                clipboard.setText(AnnotatedString(diagnostics()))
                                vm.notify(Str.s(R.string.set_043))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Str.s(R.string.set_044), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Box(
                        Modifier
                            .weight(0.9f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                            .clickable { showLogs = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Str.s(R.string.set_045), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                            .clickable { openUrl(UpdateChecker.ISSUES) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.OpenInNew, null, tint = Palette.TextDim, modifier = Modifier.size(17.dp))
                    }
                }
                VSpace(9.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.overlay(0.05f))
                        .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                        .clickable { showReport = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        Str.s(R.string.set_046),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextDim
                    )
                }
                if (crash != null) {
                    VSpace(10.dp)
                    DividerLine()
                    VSpace(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Str.s(R.string.set_047),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Rose,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            Str.s(R.string.set_007),
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
                Pill(BuildConfig.VERSION_NAME, Palette.TextFaint)
            }
        }
    }

    if (showMusic) {
        MusicDialog(vm) { showMusic = false }
    }

    if (showLang) {
        LanguageDialog(langTag, onPick = { tag ->
            LocaleManager.set(ctx, tag)
            Str.init(ctx)
            langTag = tag
            showLang = false
            (ctx as? Activity)?.recreate()
        }, onDismiss = { showLang = false })
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

    if (showReport) {
        DeviceReportDialog(vm) { showReport = false }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(if (enabled) Palette.overlay(0.05f) else Color.Transparent)
                .border(
                    1.dp,
                    if (enabled) Palette.overlay(0.08f) else Palette.overlay(0.04f),
                    RoundedCornerShape(11.dp)
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Palette.TextDim else Palette.TextFaint
            )
        }
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
                Text(Str.s(R.string.set_045), style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
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
                Str.s(R.string.set_048),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
            VSpace(16.dp)
            LazyColumn(Modifier.fillMaxSize()) {
                if (crash != null) {
                    item {
                        Text(Str.s(R.string.set_049), style = MaterialTheme.typography.labelSmall, color = Palette.Rose)
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
                    Text(Str.s(R.string.set_050), style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                    VSpace(8.dp)
                }
                if (logs.isEmpty()) {
                    item {
                        Text(
                            Str.s(R.string.set_051),
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
                                    TxLog.Dir.TX -> Str.s(R.string.set_052)
                                    TxLog.Dir.RX -> Str.s(R.string.set_053)
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

/**
 * 未收录设备的特征收集：扫到 → 点一台 → 只读 GATT 结构 → 生成一段可贴进 issue 的文本。
 * 全程不下发控制帧，读完就断开。
 */
@Composable
private fun DeviceReportDialog(vm: PulseViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val devices by vm.ble.devices.collectAsState()
    val scanning by vm.ble.scanning.collectAsState()
    val dump by vm.ble.dump.collectAsState()
    val bleState by vm.ble.state.collectAsState()
    val record by vm.activeRecord.collectAsState()

    // 这个页面要看的是没收录的设备，扫描必须是全量的
    LaunchedEffect(Unit) {
        if (vm.ble.scanning.value) vm.startScan(onlyRecognized = false)
    }

    var picked by remember { mutableStateOf<ScanItem?>(null) }
    var reading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var baseline by remember { mutableStateOf<String?>(null) }

    fun finish(item: ScanItem, gatt: String?, note: String?) {
        report = DeviceReport.build(item, gatt, record, note)
        hint = note
        reading = false
    }

    fun openIssue() {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.ISSUES + "/new")))
        }.onFailure { vm.notify(Str.s(R.string.set_000)) }
    }

    LaunchedEffect(picked) {
        val item = picked ?: return@LaunchedEffect
        report = null
        hint = null
        when (bleState) {
            is BleState.Ready -> finish(item, null, Str.s(R.string.set_054))
            is BleState.Connecting -> finish(item, null, Str.s(R.string.set_055))
            else -> {
                baseline = dump
                reading = true
                vm.inspectDevice(item)
            }
        }
    }

    LaunchedEffect(dump, bleState) {
        val item = picked ?: return@LaunchedEffect
        if (!reading) return@LaunchedEffect
        val gatt = dump
        if (gatt != null && gatt != baseline) {
            finish(item, gatt, null)
        } else if (bleState is BleState.Failed) {
            finish(item, null, (bleState as BleState.Failed).reason)
        }
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
                    Text(Str.s(R.string.set_056), style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                    Text(
                        when {
                            report != null -> Str.s(R.string.set_057)
                            picked != null -> Str.s(R.string.set_058)
                            else -> Str.s(R.string.set_059)
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
                        .clickable {
                            vm.stopScan()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Palette.TextDim)
                }
            }
            VSpace(16.dp)

            if (report != null) {
                val text = report ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hint ?: Str.s(R.string.set_060),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hint == null) Palette.TextFaint else Palette.Rose,
                        modifier = Modifier.weight(1f)
                    )
                }
                VSpace(10.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.overlay(0.05f))
                        .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 0.sp
                        ),
                        color = Palette.TextDim
                    )
                }
                VSpace(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.ActionBrush)
                            .clickable {
                                clipboard.setText(AnnotatedString(text))
                                vm.notify(Str.s(R.string.set_061))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Str.s(R.string.set_062), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.overlay(0.05f))
                            .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                            .clickable { openIssue() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(Str.s(R.string.set_063), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                    }
                }
                VSpace(9.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            picked = null
                            report = null
                            hint = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(Str.s(R.string.set_064), style = MaterialTheme.typography.labelLarge, color = Palette.TextFaint)
                }
            } else if (reading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Palette.Violet
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            Str.s(R.string.x_connecting, picked?.name ?: ""),
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Text
                        )
                        Text(
                            Str.s(R.string.set_065),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                }
                VSpace(14.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.overlay(0.05f))
                        .clickable {
                            vm.ble.cancelConnect()
                            picked?.let { finish(it, null, Str.s(R.string.set_066)) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(Str.s(R.string.set_067), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                }
            } else {
                var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res ->
                    if (res.values.all { it }) pendingAction?.invoke()
                    pendingAction = null
                }
                val permissions = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                fun withPermission(action: () -> Unit) {
                    val ok = permissions.all {
                        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (ok) action() else {
                        pendingAction = action
                        launcher.launch(permissions)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (scanning) {
                                    Brush.horizontalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
                                } else {
                                    Palette.ActionBrush
                                }
                            )
                            .clickable {
                                withPermission {
                                    if (scanning) vm.stopScan() else vm.startScan(onlyRecognized = false)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (scanning) Str.s(R.string.set_068) else Str.s(R.string.set_069),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (scanning) Palette.TextDim else Color.White
                        )
                    }
                }
                VSpace(14.dp)
                if (devices.isEmpty()) {
                    Text(
                        Str.s(R.string.set_070),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(devices, key = { "report:${it.address}" }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.overlay(0.04f))
                                .clickable { picked = item }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Palette.Text
                                )
                                Text(
                                    "${item.address} · ${item.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.sp
                                    ),
                                    color = Palette.TextFaint
                                )
                            }
                            val match = item.match
                            if (match != null) {
                                Pill(
                                    if (match.weak) Str.s(R.string.x_maybe_brand, Str.data(match.protocol.brand)) else Str.data(match.protocol.brand),
                                    if (match.weak) Palette.TextDim else Palette.Violet
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint = Palette.TextFaint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        VSpace(8.dp)
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

@Composable
private fun LanguageDialog(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties()) {
        Column(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Palette.DialogBg)
                .padding(20.dp)
        ) {
            Text(
                Str.s(R.string.lang_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Palette.Text
            )
            VSpace(12.dp)
            listOf<Pair<String?, Int>>(
                null to R.string.lang_follow,
                "zh" to R.string.lang_zh,
                "en" to R.string.lang_en,
                "es" to R.string.lang_es
            ).forEach { (tag, res) ->
                val active = current == tag
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) Palette.Violet.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onPick(tag) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Str.s(res), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Spacer(Modifier.weight(1f))
                    if (active) Icon(Icons.Rounded.Check, null, tint = Palette.Violet, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
