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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.pulse.app.PulseViewModel
import com.pulse.app.core.ProtocolSpec
import com.pulse.app.core.ScanItem
import com.pulse.app.core.ToyCatalog
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.VSpace

/**
 * 手动添加：只放在设置里。
 * 首页只显示识别得到的玩具，避免一堆耳机、手环混进来。
 */
@Composable
fun ManualAddDialog(vm: PulseViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // 采样过的列表：扫描回调太密，直接渲染会一直重排，点不中想点的那台
    val devices by vm.manualDevices.collectAsState()
    val scanning by vm.ble.scanning.collectAsState()
    // 从设备页带着「只扫已知」的扫描进来时，直接切成全量，不用用户先点一次停止。
    // 已经在扫描就说明权限齐了，这里重新注册是安全的。
    LaunchedEffect(Unit) {
        if (vm.ble.scanning.value) vm.startScan(onlyRecognized = false)
    }
    var selected by remember { mutableStateOf<ScanItem?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    Dialog(
        onDismissRequest = {
            vm.stopScan()
            onDismiss()
        },
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
                if (selected != null) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.overlay(0.06f))
                            .clickable { selected = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, null, tint = Palette.TextDim)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected == null) Str.s(R.string.man_000) else Str.s(R.string.man_001),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.Text
                    )
                    Text(
                        if (selected == null) {
                            Str.s(R.string.man_002)
                        } else {
                            Str.s(R.string.x_unknown_selected, selected!!.name)
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

            if (selected == null) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (scanning) Icons.Rounded.BluetoothSearching
                                else Icons.Rounded.Bluetooth,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (scanning) Str.s(R.string.man_003) else Str.s(R.string.man_004),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
                VSpace(14.dp)

                if (devices.isEmpty()) {
                    Text(
                        Str.s(R.string.man_005),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    items(devices, key = { "manual:${it.address}" }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Palette.overlay(0.04f))
                                .clickable { selected = item }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
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
                            if (item.match != null) {
                                val match = item.match!!
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
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    ToyCatalog.brands.forEach { brandName ->
                        item {
                            Text(
                                brandName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextFaint,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                            )
                        }
                        items(ToyCatalog.ofBrand(brandName), key = { "p:${it.id}" }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Palette.overlay(0.04f))
                                    .clickable {
                                        vm.stopScan()
                                        vm.connect(selected!!, p)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        friendlyProtocolName(p),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Palette.Text
                                    )
                                    Text(
                                        Str.s(R.string.x_models_channels, p.models.size, p.defaults.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Palette.TextFaint
                                    )
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
}

private fun friendlyProtocolName(p: ProtocolSpec): String {
    val v = p.id.substringAfterLast("-v", "")
    return when {
        p.id == "galaku" -> "Galaku"
        p.id == "lovense" -> "Lovense"
        p.id == "wevibe-8bit" -> "We-Vibe"
        p.id == "kiiroo-v21" -> "Kiiroo Pearl / Cliona"
        p.id == "satisfyer" -> "Satisfyer"
        p.brand == "Svakom" && v.isNotEmpty() -> Str.s(R.string.x_svakom_gen, v)
        p.brand == "MagicMotion" -> Str.s(R.string.x_magicmotion_gen, v)
        p.brand == "Svakom" -> "Svakom"
        else -> p.brand
    }
}
