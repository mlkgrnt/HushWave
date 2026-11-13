package com.pulse.app

import com.pulse.app.core.GalakuProtocol
import com.pulse.app.core.ModuleSpec
import com.pulse.app.core.ModuleType
import com.pulse.app.core.drivers.DriverContext
import com.pulse.app.core.drivers.Drivers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议编码回归测试：期望值来自 buttplug 的 protocol_impl 与官方 App 反编译。
 * 只验证“字节怎么拼”，不依赖蓝牙。
 */
class DriverTest {

    private fun ctx(
        modules: List<ModuleSpec>,
        values: Map<ModuleType, Float>,
        name: String? = null
    ): DriverContext {
        val map = modules.associate { spec ->
            spec.key to (values[spec.type] ?: 0f)
        }
        return DriverContext(name, modules, map)
    }

    private fun vib(n: Int, max: Int = 100) =
        (0 until n).map { ModuleSpec(ModuleType.VIBRATE, it, "振动 ${'A' + it}", max) }

    @Test
    fun lovense_single() {
        val modules = vib(1, 20)
        val packets = Drivers.of("lovense").packets(ctx(modules, mapOf(ModuleType.VIBRATE to 0.5f)))
        assertEquals("Vibrate:10;", String(packets.first()))
    }

    @Test
    fun lovense_dual() {
        val modules = vib(2, 20)
        val packets = Drivers.of("lovense").packets(ctx(modules, mapOf(ModuleType.VIBRATE to 1f)))
        assertEquals("Vibrate1:20;", String(packets[0]))
        assertEquals("Vibrate2:20;", String(packets[1]))
    }

    @Test
    fun svakom_v1() {
        val modules = vib(1, 19)
        val p = Drivers.of("svakom-v1").packets(ctx(modules, mapOf(ModuleType.VIBRATE to 10f / 19f)))
        assertArrayEquals(byteArrayOf(0x55, 0x04, 0x03, 0x00, 0x01, 0x0A), p.first())
    }

    @Test
    fun svakom_v1_off() {
        val modules = vib(1, 19)
        val p = Drivers.of("svakom-v1").packets(ctx(modules, mapOf(ModuleType.VIBRATE to 0f)))
        assertArrayEquals(byteArrayOf(0x55, 0x04, 0x03, 0x00, 0x00, 0x00), p.first())
    }

    @Test
    fun svakom_v4_two_motors() {
        val modules = vib(2, 10)
        val p = Drivers.of("svakom-v4").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 1f))
        ).first()
        assertEquals(7, p.size)
        assertArrayEquals(
            byteArrayOf(0x55, 0x03, 0x00, 0x00, 0x01, 0x0A, 0x00),
            p
        )
    }

    @Test
    fun svakom_v2_estim() {
        val modules = listOf(
            ModuleSpec(ModuleType.VIBRATE, 0, "振动", 10),
            ModuleSpec(ModuleType.ESTIM, 0, "电击", 10)
        )
        val packets = Drivers.of("svakom-v2").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 0.5f, ModuleType.ESTIM to 0.8f))
        )
        assertTrue(packets.any { it.contentEquals(byteArrayOf(0x55, 0x06, 0x01, 0x00, 0x08, 0x08)) })
    }

    @Test
    fun wevibe_both() {
        val modules = vib(2, 12)
        val p = Drivers.of("wevibe-8bit").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 6f / 12f))
        ).first()
        // 0x0F 0x03 0x00 (6+3) (6+3) status=3
        assertArrayEquals(byteArrayOf(0x0F, 0x03, 0x00, 0x09, 0x09, 0x03, 0x00, 0x00), p)
    }

    @Test
    fun wevibe_off() {
        val modules = vib(1, 12)
        val p = Drivers.of("wevibe-8bit").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 0f))
        ).first()
        assertArrayEquals(ByteArray(8).also { it[0] = 0x0F }, p)
    }

    @Test
    fun magicmotion_v3() {
        val modules = vib(1, 100)
        val p = Drivers.of("magic-motion-3").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 0.5f))
        ).first()
        assertEquals(12, p.size)
        assertEquals(0x0B.toByte(), p[0])
        assertEquals(0x46.toByte(), p[4])
        assertEquals(50.toByte(), p[9])
    }

    @Test
    fun kiiroo_v21() {
        val modules = vib(1, 100)
        val p = Drivers.of("kiiroo-v21").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 1f))
        ).first()
        assertArrayEquals(byteArrayOf(0x01, 0x64), p)
    }

    @Test
    fun satisfyer_two_motors() {
        val modules = vib(2, 100)
        val p = Drivers.of("satisfyer").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 0.5f))
        ).first()
        assertEquals(8, p.size)
        assertArrayEquals(byteArrayOf(50, 50, 50, 50, 50, 50, 50, 50), p)
    }

    @Test
    fun galaku_single_roundtrip() {
        val modules = vib(1, 100)
        val p = Drivers.of("galaku").packets(
            ctx(modules, mapOf(ModuleType.VIBRATE to 0.5f))
        ).first()
        val plain = GalakuProtocol.decrypt(p)
        assertArrayEquals(
            byteArrayOf(0x23, 0x5A, 0x00, 0x00, 0x01, 0x31, 0x32, 0x00, 0x00, 0x00, 0x00),
            plain.copyOfRange(0, 11)
        )
        var sum = 0
        for (i in 0 until 11) sum += plain[i].toInt()
        assertEquals(sum.toByte(), plain[11])
    }

    @Test
    fun galaku_suction_thrust() {
        val modules = listOf(
            ModuleSpec(ModuleType.SUCTION, 0, "吮吸", 100),
            ModuleSpec(ModuleType.THRUST, 0, "抽插", 100)
        )
        val packets = Drivers.of("galaku").packets(
            ctx(modules, mapOf(ModuleType.SUCTION to 0.3f, ModuleType.THRUST to 0.7f))
        )
        assertEquals(2, packets.size)
        val a = GalakuProtocol.decrypt(packets[0])
        val b = GalakuProtocol.decrypt(packets[1])
        assertEquals(0x32.toByte(), a[5])
        assertEquals(30.toByte(), a[6])
        assertEquals(0x71.toByte(), b[5])
        assertEquals(70.toByte(), b[6])
    }

    @Test
    fun galaku_pump_device() {
        val modules = listOf(ModuleSpec(ModuleType.PUMP, 0, "气泵", 100))
        val packets = Drivers.of("galaku").packets(
            ctx(modules, mapOf(ModuleType.PUMP to 0.8f))
        )
        val plain = GalakuProtocol.decrypt(packets.first())
        assertEquals(0x60.toByte(), plain[5])
        assertEquals(3.toByte(), plain[6])
        assertEquals(1.toByte(), plain[7])
        assertEquals(80.toByte(), plain[8])
    }

    @Test
    fun galaku_caiping_pump_raw() {
        val modules = listOf(ModuleSpec(ModuleType.PUMP, 0, "气泵", 100))
        val packets = Drivers.of("galaku").packets(
            ctx(modules, mapOf(ModuleType.PUMP to 1f), name = "AC695X_1(BLE)")
        )
        val p = packets.first()
        assertEquals(16, p.size)
        assertEquals(0xAA.toByte(), p[0])
        assertEquals(1.toByte(), p[5])
        assertEquals(100.toByte(), p[4])
    }

    @Test
    fun all_protocols_have_driver() {
        val ids = listOf(
            "galaku", "lovense", "svakom-v1", "svakom-v2", "svakom-v3",
            "svakom-v4", "svakom-v5", "svakom-v6", "wevibe-8bit",
            "magic-motion-1", "magic-motion-2", "magic-motion-3", "magic-motion-4",
            "kiiroo-v21", "satisfyer"
        )
        ids.forEach { id ->
            val modules = vib(1)
            val out = Drivers.of(id).packets(ctx(modules, mapOf(ModuleType.VIBRATE to 1f)))
            assertTrue("$id 没有输出", out.isNotEmpty())
        }
    }
}
