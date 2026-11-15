package com.pulse.app

import com.pulse.app.core.WaveLayer
import com.pulse.app.core.WaveMath
import com.pulse.app.core.WaveOp
import com.pulse.app.core.WavePrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 函数波形合成的回归测试：只看采样结果，不依赖 Android。
 */
class WaveMathTest {

    private fun sine(amp: Float = 1f, cycles: Float = 1f, op: WaveOp = WaveOp.BASE) =
        WaveLayer(
            primitive = WavePrimitive.SINE,
            amplitude = amp,
            cycles = cycles,
            phase = 0f,
            op = op
        )

    @Test
    fun `正弦从 0 起、中点最高、回到 0`() {
        val pts = WaveMath.synthesize(listOf(sine()), samples = 101)
        assertEquals(0f, pts.first(), 0.01f)
        assertEquals(1f, pts[50], 0.01f)
        assertEquals(0f, pts.last(), 0.01f)
    }

    @Test
    fun `幅度 0 就是一条直线`() {
        val pts = WaveMath.synthesize(listOf(sine(amp = 0f)))
        assertTrue(pts.all { it == 0f })
    }

    @Test
    fun `相加顶过 1 时按峰值缩回`() {
        val pts = WaveMath.synthesize(listOf(sine(), sine(op = WaveOp.ADD)))
        assertEquals(1f, pts.maxOrNull()!!, 0.01f)
        assertEquals(0f, pts.minOrNull()!!, 0.01f)
    }

    @Test
    fun `相乘得到调制而不是加倍的包络`() {
        val pts = WaveMath.synthesize(listOf(sine(), sine(amp = 0.5f, op = WaveOp.MUL)))
        assertEquals(0.5f, pts.maxOrNull()!!, 0.02f)
    }

    @Test
    fun `相位 0_5 会让正弦从中间开始往下走`() {
        val pts = WaveMath.synthesize(
            listOf(WaveLayer(WavePrimitive.SINE, 1f, 1f, 0.5f)),
            samples = 101
        )
        assertEquals(1f, pts.first(), 0.01f)
    }

    @Test
    fun `非整数周期会被判为有接缝`() {
        assertFalse(WaveMath.hasSeam(sine(cycles = 2f)))
        assertTrue(WaveMath.hasSeam(sine(cycles = 1.5f)))
        // 斜坡本来就不循环，不算接缝
        assertFalse(WaveMath.hasSeam(WaveLayer(WavePrimitive.RAMP, cycles = 1.5f)))
    }

    @Test
    fun `频率换算按循环时长走`() {
        assertEquals(0.5f, WaveMath.hertz(2f, 4000), 0.001f)
        assertEquals(1f, WaveMath.hertz(5f, 5000), 0.001f)
        assertEquals(0f, WaveMath.hertz(3f, 0), 0.001f)
    }

    @Test
    fun `配方编码解码后参数不变`() {
        val layers = listOf(
            WaveLayer(WavePrimitive.TRIANGLE, 0.6f, 2f, 0.25f, WaveOp.BASE),
            WaveLayer(WavePrimitive.DECAY, 0.4f, 3f, 0f, WaveOp.MUL),
            WaveLayer(WavePrimitive.PULSE, 0.3f, 4.5f, 0.75f, WaveOp.MAX)
        )
        val back = WaveMath.decode(WaveMath.encode(layers))!!
        assertEquals(3, back.size)
        assertEquals(WavePrimitive.TRIANGLE, back[0].primitive)
        assertEquals(0.6f, back[0].amplitude, 0.001f)
        assertEquals(2f, back[0].cycles, 0.001f)
        assertEquals(0.25f, back[0].phase, 0.001f)
        assertEquals(WaveOp.MUL, back[1].op)
        assertEquals(4.5f, back[2].cycles, 0.001f)
        assertEquals(WaveOp.MAX, back[2].op)
    }

    @Test
    fun `空配方或不认识的配方不会崩`() {
        assertEquals(null, WaveMath.decode(null))
        assertEquals(null, WaveMath.decode(""))
        assertEquals(null, WaveMath.decode("not a recipe"))
        // 认不出来的原语整层丢掉，剩下的照常读
        assertEquals(null, WaveMath.decode("NOPE|0.5|1|0|BASE"))
        val back = WaveMath.decode("SINE|1|1|0|BASE;BAD")!!
        assertEquals(1, back.size)
        assertEquals(WavePrimitive.SINE, back[0].primitive)
    }

    @Test
    fun `第一层永远是基底，op 不影响它`() {
        val a = WaveMath.synthesize(listOf(sine(op = WaveOp.MUL)))
        val b = WaveMath.synthesize(listOf(sine(op = WaveOp.BASE)))
        assertArrayEqualsFloat(a, b)
    }

    private fun assertArrayEqualsFloat(a: FloatArray, b: FloatArray) {
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(a[i], b[i], 0.0001f)
    }
}
