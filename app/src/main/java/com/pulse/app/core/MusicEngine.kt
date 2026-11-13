package com.pulse.app.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 麦克风律动：1024 点 FFT，取低频能量做“跟拍”，中高频做“气氛值”。
 * 不做节拍检测的复杂模型，靠自适应底噪 + 指数衰减包络，实测足够跟手。
 */
class MusicEngine(private val scope: CoroutineScope) {

    data class MusicState(
        val running: Boolean = false,
        val intensity: Float = 0f,
        val bass: Float = 0f,
        val mid: Float = 0f,
        val high: Float = 0f,
        val level: Float = 0f
    )

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private val _sensitivity = MutableStateFlow(0.5f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private var job: Job? = null
    private var smoothed = 0f
    private var noiseFloor = 0.02f

    fun setSensitivity(v: Float) {
        _sensitivity.value = v.coerceIn(0.05f, 1f)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (job != null) return
        smoothed = 0f
        noiseFloor = 0.02f
        job = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val n = 1024
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = max(minBuf, n * 2 * 4)
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            } catch (e: Exception) {
                _state.value = MusicState(running = false)
                return@launch
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                _state.value = MusicState(running = false)
                return@launch
            }
            record.startRecording()
            _state.value = MusicState(running = true)
            val re = FloatArray(n)
            val im = FloatArray(n)
            val window = FloatArray(n) { 0.5f - 0.5f * cos(2 * PI * it / (n - 1)).toFloat() }
            val shortBuf = ShortArray(n)
            try {
                while (isActive) {
                    var read = 0
                    while (read < n) {
                        val r = record.read(shortBuf, read, n - read)
                        if (r <= 0) break
                        read += r
                    }
                    if (read < n) {
                        delay(10)
                        continue
                    }
                    var sumSq = 0.0
                    for (i in 0 until n) {
                        val v = shortBuf[i] / 32768f
                        sumSq += (v * v).toDouble()
                        re[i] = v * window[i]
                        im[i] = 0f
                    }
                    val rms = sqrt(sumSq / n).toFloat()

                    fft(re, im)

                    val binHz = sampleRate.toFloat() / n
                    fun band(lo: Float, hi: Float): Float {
                        var s = 0f
                        var count = 0
                        val a = (lo / binHz).toInt().coerceIn(1, n / 2 - 1)
                        val b = (hi / binHz).toInt().coerceIn(a + 1, n / 2 - 1)
                        for (i in a until b) {
                            s += sqrt(re[i] * re[i] + im[i] * im[i])
                            count++
                        }
                        return if (count == 0) 0f else s / count
                    }

                    val bass = band(30f, 160f)
                    val mid = band(160f, 2000f)
                    val high = band(2000f, 8000f)

                    // 自适应底噪
                    noiseFloor = if (rms < noiseFloor) {
                        noiseFloor * 0.92f + rms * 0.08f
                    } else {
                        noiseFloor * 0.999f + rms * 0.001f
                    }
                    // 灵敏度 0 = 需要比环境声大很多才动；1 = 一点动静就响应
                    val s = _sensitivity.value
                    // 灵敏度 0：门限高、增益小、还要过一条平方曲线，基本只有很大声才动
                    val gate = noiseFloor * (6.0f - 4.6f * s)
                    val span = noiseFloor * 8f + 0.012f
                    val target = if (rms < gate) {
                        0f
                    } else {
                        val norm = ((rms - gate) / span).coerceIn(0f, 1f)
                        val curved = norm.pow(1f + (1f - s) * 1.2f)
                        val drive = (curved * (0.15f + 2.2f * s)).coerceIn(0f, 1f)
                        val bassDrive = (bass / (0.10f + 0.45f * s)).coerceIn(0f, 1f) * s
                        (drive * 0.9f + bassDrive * 0.3f).coerceIn(0f, 1f)
                    }
                    smoothed = if (target > smoothed) {
                        smoothed + (target - smoothed) * 0.65f
                    } else {
                        smoothed + (target - smoothed) * 0.12f
                    }

                    _state.value = MusicState(
                        running = true,
                        intensity = smoothed,
                        bass = normDb(bass),
                        mid = normDb(mid),
                        high = normDb(high),
                        level = (rms * 6f).coerceIn(0f, 1f)
                    )
                    delay(35)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
                _state.value = MusicState()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = MusicState()
    }

    private fun normDb(v: Float): Float {
        if (v <= 0f) return 0f
        val db = 20f * (ln(v.toDouble()) / ln(10.0)).toFloat()
        return ((db + 70f) / 70f).coerceIn(0f, 1f)
    }

    /** 原地基 2 FFT */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
