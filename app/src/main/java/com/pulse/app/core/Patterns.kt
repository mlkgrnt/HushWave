package com.pulse.app.core

import com.pulse.app.core.Str
import com.pulse.app.R
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 波形 = 等间隔采样的 0..1 序列。官方 App 用 100ms 一帧，我们 50ms 一帧更细腻。
 */
data class Pattern(
    val id: String,
    val name: String,
    val durationMs: Int,
    val points: FloatArray,
    val tag: String = "",
    val builtIn: Boolean = false,
    /** 函数波形的配方（JSON）。手绘波形为空；有它才能回头继续调参数 */
    val recipe: String? = null
) {
    fun sample(phase: Float): Float {
        if (points.isEmpty()) return 0f
        val x = ((phase % 1f) + 1f) % 1f * (points.size - 1)
        val i = x.toInt().coerceIn(0, points.size - 2)
        val f = x - i
        return (points[i] * (1 - f) + points[i + 1] * f).coerceIn(0f, 1f)
    }

    companion object {
        fun fromPoints(
            id: String,
            name: String,
            durationMs: Int,
            raw: List<Float>,
            tag: String = "",
            builtIn: Boolean = false,
            samples: Int = 192
        ): Pattern {
            if (raw.isEmpty()) return Pattern(id, name, durationMs, FloatArray(samples) { 0f }, tag, builtIn)
            val src = if (raw.size == 1) listOf(raw[0], raw[0]) else raw
            val out = FloatArray(samples)
            for (i in 0 until samples) {
                val x = i.toFloat() / (samples - 1) * (src.size - 1)
                val a = x.toInt().coerceIn(0, src.size - 2)
                val f = x - a
                out[i] = (src[a] * (1 - f) + src[a + 1] * f).coerceIn(0f, 1f)
            }
            return Pattern(id, name, durationMs, out, tag, builtIn)
        }
    }
}

object PresetLibrary {

    private fun build(
        id: String,
        name: String,
        tag: String,
        durationMs: Int,
        steps: Int = 220,
        f: (Float) -> Float
    ): Pattern {
        val pts = FloatArray(steps) { i -> f(i.toFloat() / (steps - 1)).coerceIn(0f, 1f) }
        return Pattern(id, name, durationMs, pts, tag, builtIn = true)
    }

    val presets: List<Pattern> = listOf(
        build("tide", "潮汐", "舒缓", 7000) { t ->
            val s = 0.5f + 0.5f * sin(2 * PI * t).toFloat()
            0.12f + 0.88f * s.pow(1.6f)
        },
        build("breath", "呼吸", "舒缓", 9000) { t ->
            val s = 0.5f - 0.5f * kotlin.math.cos(2 * PI * t).toFloat()
            0.08f + 0.72f * s * s * (3 - 2 * s)
        },
        build("heart", "心跳", "律动", 1300) { t ->
            fun beat(center: Float, width: Float): Float {
                val d = abs(t - center)
                return if (d < width) (1 - d / width).pow(2f) else 0f
            }
            0.06f + 0.94f * max(beat(0.06f, 0.10f), 0.78f * beat(0.26f, 0.09f))
        },
        build("surge", "涌浪", "律动", 4200) { t ->
            val saw = if (t < 0.55f) t / 0.55f else 1f - (t - 0.55f) / 0.45f * 0.85f
            0.05f + 0.95f * saw.pow(1.8f)
        },
        build("stairs", "阶梯", "节奏", 6000) { t ->
            val step = (t * 10).toInt().coerceIn(0, 9)
            val ladder = if (step < 5) step else 9 - step
            (0.12f + ladder * 0.17f).coerceIn(0f, 1f)
        },
        build("pulse", "电音脉冲", "节奏", 2000, 240) { t ->
            val beat = (t * 8) % 1f
            val env = exp(-beat * 6.5f).toFloat()
            val accent = if ((t * 8).toInt() % 4 == 0) 1f else 0.72f
            (0.10f + 0.90f * env * accent).coerceIn(0f, 1f)
        },
        build("humming", "蜂鸟", "高频", 1500) { t ->
            val flutter = 0.5f + 0.5f * sin(2 * PI * 9 * t).toFloat()
            0.35f + 0.6f * flutter
        },
        build("storm", "暴雨", "随机", 8000, 260) { t ->
            val r = Random(t.hashCode())
            val a = 0.4f + 0.6f * sin(2 * PI * (3 + r.nextFloat()) * t).toFloat()
            val b = 0.3f + 0.7f * sin(2 * PI * (7 + r.nextFloat() * 3) * t).toFloat()
            (0.10f + 0.85f * (a * 0.6f + b * 0.4f) * (0.7f + 0.3f * sin(2 * PI * 0.7 * t).toFloat()))
                .coerceIn(0f, 1f)
        },
        build("ascend", "渐强", "律动", 12000) { t ->
            val ramp = 0.15f + 0.8f * t
            val ripple = 0.85f + 0.15f * sin(2 * PI * 6 * t).toFloat()
            (ramp * ripple).coerceIn(0f, 1f)
        },
        // 渐强的镜像：一样的时长和纹理，方向反过来，凑满 5 行 × 2 张
        build("fade", "渐弱", "舒缓", 12000) { t ->
            val ramp = 0.95f - 0.8f * t
            val ripple = 0.85f + 0.15f * sin(2 * PI * 6 * t).toFloat()
            (ramp * ripple).coerceIn(0f, 1f)
        }
    )

    /** 从手指轨迹生成自定义波形 */
    fun fromTrace(
        name: String,
        durationMs: Int,
        trace: List<Pair<Float, Float>>,
        smooth: Boolean = true
    ): Pattern {
        if (trace.isEmpty()) return Pattern("custom", name, durationMs, FloatArray(64) { 0f })
        val sorted = trace.sortedBy { it.first }
        val n = 192
        val raw = FloatArray(n)
        var idx = 0
        for (i in 0 until n) {
            val x = i.toFloat() / (n - 1)
            while (idx < sorted.size - 2 && sorted[idx + 1].first < x) idx++
            val p0 = sorted[idx]
            val p1 = sorted[min(idx + 1, sorted.size - 1)]
            val span = (p1.first - p0.first).coerceAtLeast(0.0001f)
            val f = ((x - p0.first) / span).coerceIn(0f, 1f)
            raw[i] = (p0.second * (1 - f) + p1.second * f).coerceIn(0f, 1f)
        }
        val out = if (smooth) {
            FloatArray(n) { i ->
                var sum = 0f
                var w = 0f
                for (k in -3..3) {
                    val j = (i + k).coerceIn(0, n - 1)
                    val weight = 1f - abs(k) * 0.22f
                    sum += raw[j] * weight
                    w += weight
                }
                sum / w
            }
        } else raw
        return Pattern("custom_${System.currentTimeMillis()}", name, durationMs, out, "自定义")
    }
}

class PatternStore(context: Context) {

    private val sp = context.getSharedPreferences("patterns", Context.MODE_PRIVATE)

    fun load(): List<Pattern> {
        val text = sp.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pts = o.getString("points").split(",").map { it.toFloat() }
                    add(
                        Pattern(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            durationMs = o.getInt("duration"),
                            points = pts.toFloatArray(),
                            tag = o.optString("tag", "自定义"),
                            recipe = o.optString("recipe").ifBlank { null }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Pattern>) {
        try {
            saveInternal(list)
        } catch (_: Throwable) {
        }
    }

    private fun saveInternal(list: List<Pattern>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("duration", p.durationMs)
                    put("tag", p.tag)
                    put("points", p.points.filter { it.isFinite() }.joinToString(",") { "%.3f".format(it) })
                    p.recipe?.let { put("recipe", it) }
                }
            )
        }
        sp.edit().putString("items", arr.toString()).apply()
    }
}

/** 波形播放器：50ms 一帧，支持循环、整体缩放、双通道交错、目标模块 */
class PatternPlayer(
    private val engine: ToyEngine,
    private val scope: CoroutineScope
) {
    data class PlayState(
        val pattern: Pattern,
        val phase: Float,
        val playing: Boolean,
        val scale: Float,
        val targetKeys: List<String>,
        val sync: Boolean
    )

    private val _state = MutableStateFlow<PlayState?>(null)
    val state: StateFlow<PlayState?> = _state.asStateFlow()

    private var job: Job? = null
    private var startedAt = 0L
    private var offsetMs = 0L

    fun play(
        pattern: Pattern,
        scale: Float = 1f,
        targetKeys: List<String>,
        sync: Boolean = true
    ) {
        if (targetKeys.isEmpty()) return
        job?.cancel()
        startedAt = System.currentTimeMillis()
        offsetMs = 0
        _state.value = PlayState(
            pattern = pattern,
            phase = 0f,
            playing = true,
            scale = scale,
            targetKeys = targetKeys,
            sync = sync
        )
        job = scope.launch { loop() }
    }

    fun setTargets(keys: List<String>, sync: Boolean) {
        val current = _state.value ?: return
        _state.value = current.copy(targetKeys = keys, sync = sync)
        if (keys.isEmpty()) stop()
    }

    fun setScale(v: Float) {
        _state.value = _state.value?.copy(scale = v.coerceIn(0.05f, 1f))
    }

    fun toggle() {
        val s = _state.value ?: return
        if (s.playing) {
            offsetMs += System.currentTimeMillis() - startedAt
            job?.cancel()
            _state.value = s.copy(playing = false)
            zeroTargets(s.targetKeys)
        } else {
            startedAt = System.currentTimeMillis()
            _state.value = s.copy(playing = true)
            job = scope.launch { loop() }
        }
    }

    fun stop() {
        val keys = _state.value?.targetKeys.orEmpty()
        job?.cancel()
        job = null
        _state.value = null
        zeroTargets(keys)
    }

    private fun zeroTargets(keys: List<String>) {
        if (keys.isEmpty()) return
        engine.setMany(keys.associateWith { 0f })
    }

    private suspend fun loop() {
        while (true) {
            val s = _state.value ?: return
            if (!s.playing) return
            val elapsed = offsetMs + (System.currentTimeMillis() - startedAt)
            val phase = (elapsed % s.pattern.durationMs).toFloat() / s.pattern.durationMs
            val keys = s.targetKeys
            val map = HashMap<String, Float>()
            keys.forEachIndexed { i, key ->
                // 同步：所有通道同一相位；不同步：按顺序错开，形成波浪
                val offset = if (s.sync || keys.size <= 1) 0f else i.toFloat() / keys.size
                val p = (phase + offset) % 1f
                map[key] = s.pattern.sample(p) * s.scale
            }
            if (map.isNotEmpty()) engine.setMany(map)
            _state.value = _state.value?.copy(phase = phase)
            delay(100)
        }
    }
}
