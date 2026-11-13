package com.pulse.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.app.PulseViewModel
import com.pulse.app.core.Song
import com.pulse.app.ui.Palette
import com.pulse.app.ui.Pill
import com.pulse.app.ui.VSpace
import kotlin.math.roundToInt

@Composable
fun MusicDialog(vm: PulseViewModel, onDismiss: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val song by vm.song.collectAsState()
    val playing by vm.songPlaying.collectAsState()
    val position by vm.songPosition.collectAsState()
    val intensity by vm.songIntensity.collectAsState()
    val busy by vm.songBusy.collectAsState()
    val targets by vm.songTargets.collectAsState()
    val modules by vm.engine.modules.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importSong(uri)
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
                    Text("音乐", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                    Text(
                        "按歌曲的激昂程度自动控制强度",
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

            VSpace(16.dp)

            if (busy != null) {
                Text(
                    busy ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Cyan
                )
                VSpace(10.dp)
            }

            if (song != null) {
                NowPlaying(
                    song = song!!,
                    playing = playing,
                    position = position,
                    intensity = intensity,
                    onToggle = { vm.toggleSong() },
                    onStop = { vm.stopSong() }
                )
                VSpace(14.dp)
            }

            if (modules.isNotEmpty()) {
                Text("作用于", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                VSpace(8.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    modules.forEach { spec ->
                        val on = targets.contains(spec.key)
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
                                    val next = targets.toMutableSet()
                                    if (on) next.remove(spec.key) else next.add(spec.key)
                                    vm.setSongTargets(next)
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
                VSpace(16.dp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("曲库", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.Violet.copy(alpha = 0.15f))
                        .border(1.dp, Palette.Violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { picker.launch(arrayOf("audio/*")) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = Palette.Violet, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导入", style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
                    }
                }
            }
            VSpace(10.dp)

            if (songs.isEmpty()) {
                Text(
                    "还没有歌曲。点「导入」从手机里选一首，App 会在本地分析它的起伏，不联网。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(songs, key = { it.id }) { s ->
                        SongRow(
                            song = s,
                            active = song?.id == s.id,
                            playing = playing && song?.id == s.id,
                            onPlay = { vm.playSong(s) },
                            onDelete = { vm.deleteSong(s) }
                        )
                        VSpace(8.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(
    song: Song,
    playing: Boolean,
    position: Long,
    intensity: Float,
    onToggle: () -> Unit,
    onStop: () -> Unit
) {
    val frac = if (song.durationMs > 0) (position.toFloat() / song.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(listOf(Palette.SurfaceHi, Palette.Surface))
            )
            .border(1.dp, Palette.overlay(0.1f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${mmss(position)} / ${mmss(song.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Palette.TextFaint
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.overlay(0.07f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = Palette.Text
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Rose.copy(alpha = 0.14f))
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Stop, null, tint = Palette.Rose)
            }
        }
        VSpace(12.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.overlay(0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(4.dp)
                    .background(Brush.horizontalGradient(listOf(Palette.Violet, Palette.Cyan)))
            )
        }
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前强度", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Palette.overlay(0.07f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(intensity.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(Brush.horizontalGradient(listOf(Palette.Violet, Palette.Cyan)))
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${(intensity.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Palette.Cyan
            )
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    active: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Palette.Violet.copy(alpha = 0.12f) else Palette.overlay(0.04f))
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) Palette.Violet else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                mmss(song.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Palette.TextFaint
            )
        }
        if (playing) {
            Pill("播放中", Palette.Cyan)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onDelete),
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

private fun mmss(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
