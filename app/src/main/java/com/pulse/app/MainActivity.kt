package com.pulse.app

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.unit.dp
import com.pulse.app.core.LocaleManager
import com.pulse.app.ui.AuroraBackground
import com.pulse.app.ui.Palette
import com.pulse.app.ui.PulseTheme
import com.pulse.app.ui.ThemeState
import com.pulse.app.ui.rememberAccents
import com.pulse.app.ui.rememberDynamicScheme
import com.pulse.app.ui.screens.ControlScreen
import com.pulse.app.ui.screens.DevicesScreen
import com.pulse.app.ui.screens.MoreScreen
import com.pulse.app.ui.screens.PatternsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    /** 语言选择在这里生效：包一层目标语言，Compose 里所有 stringResource 跟着变 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.load(this)
        val vm = ViewModelProvider(this)[PulseViewModel::class.java]
        // 退到后台就停扫：BLE 扫描会一直占着射频并唤醒 CPU，看不见界面时没必要留着。
        // 只停不自动恢复，回来时由用户自己点「扫描设备」，避免隔了半天回来偷偷又开始扫。
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                vm.stopScan()
            }
        })
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = ThemeState.resolveDark(systemDark)
            val accents = rememberAccents(dark)
            val scheme = rememberDynamicScheme(dark)
            val view = LocalView.current
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
            PulseTheme(dark = dark, accents = accents, scheme = scheme) {
                AppRoot(vm)
            }
        }
    }
}

private data class Tab(@StringRes val title: Int, val icon: ImageVector)

@Composable
fun AppRoot(vm: PulseViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) {
        // 仅调试包：am start --ez demo true 可载入预览设备
        val debuggable = activity != null &&
            (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && activity?.intent?.getBooleanExtra("demo", false) == true) {
            vm.loadDemo()
        }
        activity?.intent?.getStringExtra("song")?.let { vm.importSongFromPath(it) }
    }
    val toast by vm.toast.collectAsState()
    var showToast by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        if (toast != null) {
            showToast = true
            delay(2200)
            showToast = false
            vm.clearToast()
        }
    }

    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PulseBottomBar(selected = tab, onSelect = { tab = it })
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (tab) {
                    0 -> DevicesScreen(vm) { tab = 1 }
                    1 -> ControlScreen(vm)
                    2 -> PatternsScreen(vm)
                    else -> MoreScreen(vm)
                }

                AnimatedVisibility(
                    visible = showToast && toast != null,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 84.dp, start = 24.dp, end = 24.dp)
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Palette.NavPill, Palette.NavPillDim)))
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Text(
                            toast ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Text
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        Tab(R.string.tab_devices, Icons.Rounded.Bluetooth),
        Tab(R.string.tab_control, Icons.Rounded.Tune),
        Tab(R.string.tab_wave, Icons.Rounded.GraphicEq),
        Tab(R.string.tab_settings, Icons.Rounded.MoreHoriz)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.NavBg)
            .navigationBarsPadding()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, t ->
            val active = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Palette.Violet.copy(alpha = 0.13f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Icon(
                        t.icon,
                        contentDescription = stringResource(t.title),
                        modifier = Modifier.size(19.dp),
                        tint = if (active) Palette.Violet else Palette.TextFaint
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(t.title),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Palette.Text else Palette.TextFaint
                )
            }
        }
    }
}

