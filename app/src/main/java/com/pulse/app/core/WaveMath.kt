package com.pulse.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 用初等函数拼波形。
 *
 * 和手绘一样，最后都落成 192 个 0..1 的采样点交给 [PatternPlayer]，
 * 所以播放、存储、蓝牙下发这条链路完全不用改。
 *
 * 频率用「每个循环起伏几次」而不是 Hz：循环时长是另一根滑杆，
 * 两者相乘才是 Hz，直接给 Hz 会让「把循环改长」偷偷改变波形形状。
 */
enum class WavePrimitive(val label: String) {
    SINE("正弦"),
    TRIANGLE("三角"),
    SAW("锯齿"),
    SQUARE("方波"),
    PULSE("脉冲"),
    DECAY("衰减"),
    RAMP("斜坡");

    /**
     * @param t 整个循环的进度 0..1（斜坡用）
     * @param u 单个周期内的相位 0..1
     */
    fun value(t: Float, u: Float): Float = when (this) {
        SINE -> (0.5 - 0.5 * cos(2 * PI * u)).toFloat()
        TRIANGLE -> 1f - abs(2f * u - 1f)
        SAW -> u
        SQUARE -> if (u < 0.5f) 1f else 0f
        PULSE -> if (u < 0.18f) 1f else 0f
        DECAY -> exp(-3.5 * u).toFloat()
        RAMP -> t
    }

    /** 斜坡本身是整段单调的，谈「每个循环几次」没有意义 */
    val periodic: Boolean get() = this != RAMP
}

enum class WaveOp(val label: String) {
    BASE("基础"),
    ADD("相加"),
    MUL("相乘"),
    MAX("取大"),
    MIN("取小"),
    MIX("混合")
}

data class WaveLayer(
    val primitive: WavePrimitive = WavePrimitive.SINE,
    val amplitude: Float = 1f,
    val cycles: Float = 1f,
    val phase: Float = 0f,
    val op: WaveOp = WaveOp.BASE
)

object WaveMath {

    const val SAMPLES = 192
    const val MAX_LAYERS = 3
    const val MIN_CYCLES = 0.25f
    const val MAX_CYCLES = 16f

    /**
     * 第一层当基底，后面的层按各自的 [WaveOp] 叠上去。
     * 结果是 0..1：叠加顶过 1.0 时按峰值整体缩回，
     * 否则玩具会被顶在满速一段不动（听起来像卡住）。
     */
    fun synthesize(layers: List<WaveLayer>, samples: Int = SAMPLES): FloatArray {
        if (layers.isEmpty()) return FloatArray(samples) { 0f }
        val out = FloatArray(samples)
        for (i in 0 until samples) {
            val t = i.toFloat() / (samples - 1)
            var v = 0f
            layers.forEachIndexed { idx, layer ->
                val u = frac(layer.cycles * t + layer.phase)
                val raw = layer.primitive.value(t.coerceIn(0f, 1f), u) * layer.amplitude.coerceIn(0f, 1f)
                v = if (idx == 0) raw else combine(layer.op, v, raw)
            }
            out[i] = v
        }
        val peak = out.maxOrNull() ?: 0f
        if (peak > 1f) {
            for (i in out.indices) out[i] /= peak
        }
        for (i in out.indices) out[i] = out[i].coerceIn(0f, 1f)
        return out
    }

    private fun combine(op: WaveOp, a: Float, b: Float): Float = when (op) {
        WaveOp.ADD, WaveOp.BASE -> a + b
        WaveOp.MUL -> a * b
        WaveOp.MAX -> max(a, b)
        WaveOp.MIN -> min(a, b)
        WaveOp.MIX -> (a + b) * 0.5f
    }

    private fun frac(x: Float): Float = x - floor(x)

    /** 每循环 cycles 次，在 durationMs 的循环里等于多少 Hz */
    fun hertz(cycles: Float, durationMs: Int): Float =
        if (durationMs <= 0) 0f else cycles * 1000f / durationMs

    /** 周期函数如果不是整数个周期，循环回到起点会跳一下 */
    fun hasSeam(layer: WaveLayer): Boolean =
        layer.primitive.periodic && abs(layer.cycles - layer.cycles.toInt()) > 0.02f

    /**
     * 配方就是一个紧凑字符串：层与层用 `;` 隔开，字段用 `|`
     * （`SINE|0.85|1|0|BASE`）。用纯文本而不是 JSON，是因为 unitTest 里的
     * `org.json` 是桩实现，这样内核逻辑能在 JVM 上直接测。
     */
    fun encode(layers: List<WaveLayer>): String = layers.joinToString(";") { l ->
        listOf(
            l.primitive.name,
            fmt(l.amplitude),
            fmt(l.cycles),
            fmt(l.phase),
            l.op.name
        ).joinToString("|")
    }

    fun decode(text: String?): List<WaveLayer>? {
        if (text.isNullOrBlank()) return null
        val out = mutableListOf<WaveLayer>()
        text.split(";").forEach { part ->
            val f = part.split("|")
            if (f.size < 5) return@forEach
            val layer = runCatching {
                WaveLayer(
                    primitive = WavePrimitive.valueOf(f[0]),
                    amplitude = f[1].toFloat().coerceIn(0f, 1f),
                    cycles = f[2].toFloat().coerceIn(MIN_CYCLES, MAX_CYCLES),
                    phase = f[3].toFloat().coerceIn(0f, 1f),
                    op = WaveOp.valueOf(f[4])
                )
            }.getOrNull() ?: return@forEach
            out.add(layer)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun fmt(v: Float): String = ((v * 1000).roundToInt() / 1000f).toString()
}
