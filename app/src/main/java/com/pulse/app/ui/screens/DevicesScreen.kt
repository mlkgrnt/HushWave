package com.pulse.app.ui.screens

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
    val scanned by vm.visibleDevices.collectAsState()
    val devices = scanned.filter { it.match != null }
    val brand by vm.brandFilter.collectAsState()
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
                    onLongClick = { vm.notify("预览模式"); vm.loadDemo(); onOpenControl() }
                )
            )
            VSpace(2.dp)
            Text(
                "连接你的玩具",
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
                BrandChip("全部", brand == null) { vm.setBrandFilter(null) }
                vm.brands.forEach { b ->
                    BrandChip(b, brand == b) { vm.setBrandFilter(b) }
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
                        Column {
                            Text(
                                "正在连接 ${s.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Text
                            )
                            Text(
                                "请让玩具保持开机并靠近手机",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.TextFaint
                            )
                        }
                    }
                }

                is BleState.Failed -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Palette.Rose, 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("没连上", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    }
                    VSpace(8.dp)
                    Text(s.reason, style = MaterialTheme.typography.bodyMedium, color = Palette.TextFaint)
                }

                else -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("先选品牌，再扫描", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text(
                                "这里只显示已识别的玩具；其它蓝牙设备不会出现",
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
                            if (state is BleState.Scanning) {
                                Brush.horizontalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
                            } else {
                                Palette.ActionBrush
                            }
                        )
                        .clickable {
                            withPermission {
                                if (state is BleState.Scanning) vm.stopScan() else vm.startScan()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state is BleState.Scanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state is BleState.Scanning) "停止扫描" else "扫描设备",
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
            item { SectionHeader("附近设备", "${devices.size} 台") }
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
                    SectionHeader("我的玩具", "点一下即可连接", Modifier.weight(1f))
                }
            }
            items(saved, key = { "mem:${it.address}" }) { rec ->
                MemoryRow(
                    record = rec,
                    onDelete = { vm.deleteToy(rec.address) },
                    onConnect = {
                        val hit = vm.ble.devices.value.firstOrNull { it.address.equals(rec.address, true) }
                        if (hit != null) withPermission { vm.connect(hit) }
                        else vm.notify("先扫描，让玩具出现在附近设备里")
                    }
                )
                VSpace(9.dp)
            }
            item { VSpace(12.dp) }
        }

        item {
            Text(
                "找不到自己的玩具？到「设置 → 手动添加设备」里指定品牌",
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
                        record?.let { shortModel(it) } ?: "已连接",
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.Text
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusDot(Palette.Lime)
                }
                Text(
                    record?.brand?.plus(" · ")?.plus("${record.modules.size} 个通道") ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            }
        }
        VSpace(14.dp)
        DividerLine()
        VSpace(12.dp)
        Text(
            record?.modules?.joinToString("  ") { it.label } ?: "",
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
                Text("进入控制", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
                    Text("通道", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
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
                Text("断开", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
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
                            match.protocol.brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${match.model?.modules?.size ?: match.protocol.defaults.size} 个通道",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextFaint
                        )
                    }
                }
            }
            if (connected) {
                Pill("已连接", Palette.Lime)
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
                        record.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextFaint
                    )
                }
                VSpace(3.dp)
                Text(
                    record.modules.joinToString(" · ") { it.label },
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
    val prefixes = listOf(record.brand, "Svakom", "Galaku", "Jissbon", "杰士邦", "Lovense", "WeVibe", "We-Vibe", "MagicMotion")
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
            Text("通道设置", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
            Text(
                if (known) "这个型号支持的通道已确定，按需要开关即可" else "没识别出型号，请按玩具实际功能勾选",
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
                                spec.label,
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
                    Text("振动通道", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
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
                                    if (n == 0) "无" else "$n 个",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Palette.Violet else Palette.TextDim
                                )
                            }
                        }
                    }
                    VSpace(14.dp)
                    DividerLine()
                    VSpace(10.dp)
                    Text("其它通道", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    VSpace(4.dp)
                    listOf(
                        ModuleType.SUCTION, ModuleType.THRUST, ModuleType.ROTATE,
                        ModuleType.OSCILLATE, ModuleType.CONSTRICT, ModuleType.ESTIM,
                        ModuleType.HEAT, ModuleType.LED
                    ).forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                type.cn,
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
                    Text("取消", style = MaterialTheme.typography.labelLarge, color = Palette.TextDim)
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
                                val labels = listOf("振动 A", "振动 B", "振动 C")
                                repeat(vibeCount.value) { i ->
                                    out.add(ModuleSpec(ModuleType.VIBRATE, i, labels[i], 100))
                                }
                                var idx = 0
                                extra.forEach { type ->
                                    out.add(
                                        ModuleSpec(type, idx++, type.cn, if (type == ModuleType.ESTIM) 10 else 100)
                                    )
                                }
                                out
                            }
                            if (list.isNotEmpty()) onSave(list)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
