package com.pulse.app.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：直接下 release 里的 APK，下完交给系统安装器。
 *
 * 先走 GitHub，失败再依次试两个镜像；下载中断就换下一个源。
 * 只在用户点了「下载」之后才动，不做后台自动下载。
 */
class ApkUpdater(private val ctx: Context) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Float, val source: Int) : State
        data class Ready(val file: File) : State
        data class Failed(val reason: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun download(version: String) {
        if (_state.value is State.Downloading) return
        job?.cancel()
        job = scope.launch {
            val dir = File(ctx.filesDir, "updates").apply { mkdirs() }
            // 只留一份，避免旧包越攒越多
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "HushWave-$version.apk")
            out.delete()
            val urls = sources(version)
            for ((index, url) in urls.withIndex()) {
                if (!currentCoroutineContext().isActive) return@launch
                // 先亮出「下载中 0%」，否则连接阶段的十几秒里界面像没反应
                _state.value = State.Downloading(0f, index)
                val ok = runCatching { fetch(url, out, index) }.getOrDefault(false)
                val pkg = runCatching {
                    ctx.packageManager.getPackageArchiveInfo(out.absolutePath, 0)
                }.getOrNull()
                if (ok && out.length() > 1_000_000 && pkg?.packageName == ctx.packageName) {
                    _state.value = State.Ready(out)
                    return@launch
                }
                out.delete()
            }
            _state.value = State.Failed("all sources failed")
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    /** 交给系统安装器；用户需要允许「安装未知应用」 */
    fun install(): Boolean {
        val file = (_state.value as? State.Ready)?.file ?: return false
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { ctx.startActivity(intent); true }.getOrDefault(false)
    }

    private fun sources(version: String): List<String> {
        val path = "https://github.com/mlkgrnt/HushWave/releases/download/v$version/HushWave-$version.apk"
        return listOf(
            path,
            "https://ghproxy.net/$path",
            "https://gh-proxy.com/$path"
        )
    }

    private suspend fun fetch(url: String, out: File, source: Int): Boolean =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HushWave-Updater")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext false
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            if (!currentCoroutineContext().isActive) return@withContext false
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                _state.value = State.Downloading(
                                    (read.toFloat() / total).coerceIn(0f, 1f),
                                    source
                                )
                            }
                        }
                    }
                }
                true
            } finally {
                conn.disconnect()
            }
        }
}
