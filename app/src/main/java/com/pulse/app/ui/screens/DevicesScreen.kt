package com.pulse.app.ui.screens

import com.pulse.app.R
import com.pulse.app.core.Str
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pulse.app.PulseViewModel
import com.pulse.app.core.BleState
import com.pulse.app.core.ModuleSpec
import com.pulse.app.core.ModuleType
import com.pulse.app.core.ScanItem
import com.pulse.app.core.ToyRecord
import com.pulse.app.ui.DividerLine
import com.pulse.app.ui.GlassCard
import com.pulse.app.ui.KeyValue
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.SectionHeader
import com.pulse.app.ui.StatusDot
import com.pulse.app.ui.VSpace

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DevicesScreen(vm: PulseViewModel, onOpenControl: () -> Unit) {
    val ctx = LocalContext.current
    val state by vm.ble.state.collectAsState()
    val scanning by vm.ble.scanning.collectAsState()
    val scanned by vm.visibleDevices.collectAsState()
    val brand by vm.brandFilter.collectAsState()
    // 只凭共用 / 通用 UUID 认出来的设备不上首页：那多半是电子价签、资产标签这类
    // 同样用 BLE 串口模块的东西。用户明确选了品牌时还是放行，有些玩具不广播名字。
    val devices = scanned.filter { it.match != null && (brand != null || it.match?.weak != true) }
    val record by vm.activeRecord.collectAsState()
    val saved by vm.savedToys.collectAsState()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var moduleEditor by remember { mutableStateOf(false) }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) pending?.invoke()
        pending = null
    }

    fun withPermission(action: () -> Unit) {
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) action() else {
            pending = action
            launcher.launch(permissions)
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp)
    ) {
        item {
            Text(
                "HushWave",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 1.sp
                ),
                color = Palette.Text,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { vm.notify(Str.s(R.string.dev_000)); vm.loadDemo(); onOpenControl() }
                )
            )
            VSpace(2.dp)
            Text(
                Str.s(R.string.dev_001),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
            VSpace(18.dp)
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrandChip(Str.s(R.string.dev_002), brand == null) { vm.setBrandFilter(null) }
                vm.brands.forEach { b ->
                    BrandChip(Str.data(b), brand == b) { vm.setBrandFilter(b) }
                }
            }
            VSpace(16.dp)
        }

        item {
            when (val s = state) {
                is BleState.Ready -> ConnectedCard(
                    record = record,
                    address = s.address,
                    onControl = onOpenControl,
                    onDisconnect = vm::disconnect,
                    onModules = { moduleEditor = true }
                )

                is BleState.Connecting -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Palette.Violet
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Str.s(R.string.x_connecting, s.name),
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text
                            )
                            Text(
                                Str.s(R.string.dev_003),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextFaint
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.overlay(0.06f))
                                .border(1.dp, Palette.overlay(0.1f), RoundedCornerShape(12.dp))
                                .clickable(onClick = vm::cancelConnect)
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                Str.s(R.string.dev_004),
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.TextDim
                            )
                        }
                    }
                }

                is BleState.Failed -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Palette.Rose, 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(Str.s(R.string.dev_005), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    }
                    VSpace(8.dp)
                    Text(Str.data(s.reason), style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
                }

                else -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Str.s(R.string.dev_006), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text(
                                Str.s(R.string.dev_007),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextFaint
                            )
                        }
                    }
                }
            }
            VSpace(14.dp)
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (scanning) {
                                Brush.horizontalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
                            } else {
                                Palette.ActionBrush
                            }
                        )
                        .clickable {
                            withPermission {
                                if (scanning) vm.stopScan() else vm.startScan()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (scanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (scanning) Str.s(R.string.dev_008) else Str.s(R.string.dev_009),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.overlay(0.06f))
                        .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(18.dp))
                        .clickable { withPermission { vm.startScan() } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
                }
            }
            VSpace(18.dp)
        }

        if (devices.isNotEmpty()) {
            item { SectionHeader(Str.s(R.string.dev_010), Str.s(R.string.x_devices_count, devices.size)) }
            items(devices, key = { "scan:${it.address}" }) { item ->
                ScanRow(
                    item = item,
                    connectedAddress = (state as? BleState.Ready)?.address,
                    onConnect = { withPermission { vm.connect(item) } }
                )
                VSpace(9.dp)
            }
            item { VSpace(12.dp) }
        }

        // 被判定为「只有共用 UUID」的设备不上首页，但要让人知道它们去哪了
        if (brand == null) {
            val uncertain = scanned.count { it.match?.weak == true }
            if (uncertain > 0) {
                item {
                    Text(
                        Str.s(R.string.x_uncertain_hint, uncertain),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                        color = Palette.TextFaint
                    )
                    VSpace(12.dp)
                }
            }
        }

        if (saved.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Memory,
                        null,
                        tint = Palette.Cyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    SectionHeader(Str.s(R.string.dev_011), Str.s(R.string.dev_012), Modifier.weight(1f))
                }
            }
            items(saved, key = { "mem:${it.address}" }) { rec ->
                MemoryRow(
                    record = rec,
                    onDelete = { vm.deleteToy(rec.address) },
                    onConnect = {
                        val hit = vm.ble.devices.value.firstOrNull { it.address.equals(rec.address, true) }
                        if (hit != null) withPermission { vm.connect(hit) }
                        else vm.notify(Str.s(R.string.dev_013))
                    }
                )
                VSpace(9.dp)
            }
            item { VSpace(12.dp) }
        }

        item {
            Text(
                Str.s(R.string.dev_014),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = Palette.TextFaint,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (moduleEditor) {
        ModuleEditor(
            initial = record?.modules ?: emptyList(),
            known = record?.knownModel == true,
            onDismiss = { moduleEditor = false },
            onSave = {
                vm.updateModules(it)
                moduleEditor = false
            }
        )
    }
}

@Composable
private fun BrandChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Palette.Violet.copy(alpha = 0.18f) else Palette.overlay(0.04f)
            )
            .border(
                1.dp,
                if (selected) Palette.Violet.copy(alpha = 0.5f) else Palette.overlay(0.06f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) Palette.Violet else Palette.TextDim
        )
    }
}

@Composable
private fun ConnectedCard(
    record: ToyRecord?,
    address: String,
    onControl: () -> Unit,
    onDisconnect: () -> Unit,
    onModules: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Palette.Violet.copy(0.35f), Palette.Cyan.copy(0.22f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Bluetooth, null, tint = Palette.Text, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record?.let { shortModel(it) } ?: Str.s(R.string.dev_015),
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.Text
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusDot(Palette.Lime)
                }
                Text(
                    record?.brand?.plus(" · ")?.plus(Str.s(R.string.x_channels_count, record.modules.size)) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            }
        }
        VSpace(14.dp)
        DividerLine()
        VSpace(12.dp)
        Text(
            record?.modules?.joinToString("  ") { Str.data(it.label) } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim
        )
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .weight(1.2f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.ActionBrush)
                    .clickable(onClick = onControl),
                contentAlignment = Alignment.Center
            ) {
                Text(Str.s(R.string.dev_016), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Box(
                Modifier
                    .weight(0.9f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.overlay(0.05f))
                    .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onModules),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = Palette.TextDim, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(Str.s(R.string.dev_017), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                }
            }
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.overlay(0.05f))
                    .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onDisconnect)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(Str.s(R.string.dev_018), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun ScanRow(
    item: ScanItem,
    connectedAddress: String?,
    onConnect: () -> Unit
) {
    val match = item.match
    val connected = connectedAddress == item.address
    GlassCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        padding = 15.dp,
        radius = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        match?.model?.shortName ?: item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text
                    )
                    if (match?.model != null) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.sp
                            ),
                            color = Palette.TextFaint
                        )
                    }
                }
                VSpace(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match != null) {
                        Text(
                            Str.data(match.protocol.brand),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Str.s(R.string.x_channels_count, match.model?.modules?.size ?: match.protocol.defaults.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                }
            }
            if (connected) {
                Pill(Str.s(R.string.dev_015), Palette.Lime)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${item.rssi}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = signalColor(item.rssi)
                    )
                    Text(
                        "dBm",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.sp
                        ),
                        color = Palette.TextFaint
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = Palette.TextFaint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MemoryRow(record: ToyRecord, onDelete: () -> Unit, onConnect: () -> Unit) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        padding = 14.dp,
        radius = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortModel(record),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        Str.data(record.brand),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                }
                VSpace(3.dp)
                Text(
                    record.modules.joinToString(" · ") { Str.data(it.label) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete),
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

/** 信号越强越亮：-55 以上绿、-75 以上次色、再弱用警示色 */
private fun signalColor(rssi: Int) = when {
    rssi >= -60 -> Palette.Lime
    rssi >= -78 -> Palette.Cyan
    else -> Palette.TextFaint
}

private fun shortModel(record: ToyRecord): String {    val name = record.modelName
    val prefixes = listOf(record.brand, "Svakom", "Galaku", "Jissbon", Str.s(R.string.dev_019), "Lovense", "WeVibe", "We-Vibe", "MagicMotion")
    val hit = prefixes.firstOrNull { name.startsWith("$it ", true) }
    return if (hit != null) name.substring(hit.length).trim() else name
}

@Composable
private fun ModuleEditor(
    initial: List<ModuleSpec>,
    known: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<ModuleSpec>) -> Unit
) {
    // 已知型号：只能开关已有通道
    val enabled = remember {
        mutableStateListOf<ModuleSpec>().apply { initial.forEach { add(it) } }
    }
    // 未知型号：允许从常见通道里挑
    val extra = remember {
        mutableStateListOf<ModuleType>().apply {
            initial.map { it.type }.filter { it != ModuleType.VIBRATE && it != ModuleType.ESTIM_FREQ }
                .distinct().forEach { add(it) }
        }
    }
    val vibeCount = remember {
        mutableStateOf(initial.count { it.type == ModuleType.VIBRATE }.coerceAtLeast(0))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.DialogBg)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Text(Str.s(R.string.dev_020), style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
            Text(
                if (known) Str.s(R.string.dev_021) else Str.s(R.string.dev_022),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextFaint
            )
            VSpace(18.dp)

            if (known) {
                GlassCard(Modifier.fillMaxWidth()) {
                    initial.forEachIndexed { i, spec ->
                        if (i > 0) VSpace(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Str.data(spec.label),
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = enabled.contains(spec),
                                onCheckedChange = { on ->
                                    if (on) {
                                        if (!enabled.contains(spec)) enabled.add(spec)
                                    } else {
                                        enabled.remove(spec)
                                    }
                                },
                                colors = switchColors()
                            )
                        }
                    }
                }
            } else {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(Str.s(R.string.dev_023), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    VSpace(10.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..3).forEach { n ->
                            val selected = vibeCount.value == n
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) Palette.Violet.copy(alpha = 0.2f)
                                        else Palette.overlay(0.05f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Palette.Violet.copy(alpha = 0.5f)
                                        else Palette.overlay(0.06f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { vibeCount.value = n }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    if (n == 0) Str.s(R.string.dev_024) else Str.s(R.string.x_count_unit, n),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Palette.Violet else Palette.TextDim
                                )
                            }
                        }
                    }
                    VSpace(14.dp)
                    DividerLine()
                    VSpace(10.dp)
                    Text(Str.s(R.string.dev_025), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    VSpace(4.dp)
                    listOf(
                        ModuleType.SUCTION, ModuleType.THRUST, ModuleType.ROTATE,
                        ModuleType.OSCILLATE, ModuleType.CONSTRICT, ModuleType.ESTIM,
                        ModuleType.HEAT, ModuleType.LED
                    ).forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Str.data(type.cn),
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.TextDim,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = extra.contains(type),
                                onCheckedChange = { on ->
                                    if (on) extra.add(type) else extra.remove(type)
                                },
                                colors = switchColors()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.overlay(0.05f))
                        .border(1.dp, Palette.overlay(0.08f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Str.s(R.string.dev_004), style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Palette.ActionBrush
                        )
                        .clickable {
                            val list = if (known) {
                                enabled.toList()
                            } else {
                                val out = mutableListOf<ModuleSpec>()
                                val labels = listOf(Str.s(R.string.dev_026), Str.s(R.string.dev_027), Str.s(R.string.dev_028))
                                repeat(vibeCount.value) { i ->
                                    out.add(ModuleSpec(ModuleType.VIBRATE, i, labels[i], 100))
                                }
                                var idx = 0
                                extra.forEach { type ->
                                    out.add(
                                        ModuleSpec(type, idx++, Str.data(type.cn), if (type == ModuleType.ESTIM) 10 else 100)
                                    )
                                }
                                out
                            }
                            if (list.isNotEmpty()) onSave(list)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(Str.s(R.string.dev_029), style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Palette.VioletDeep,
    uncheckedThumbColor = Palette.TextFaint,
    uncheckedTrackColor = Palette.overlay(0.08f)
)
