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

    private fun one(type: ModuleType, max: Int, index: Int = 0) =
        ModuleSpec(type, index, type.cn, max)

    @Test
    fun monsterpub_two_channels() {
        val modules = listOf(
            ModuleSpec(ModuleType.VIBRATE, 0, "振动 A", 100),
            ModuleSpec(ModuleType.VIBRATE, 1, "振动 B", 100)
        )
        val c = DriverContext(
            null, modules, mapOf("VIBRATE:0" to 0.5f, "VIBRATE:1" to 1f)
        )
        val p = Drivers.of("monsterpub").packets(c)
        assertArrayEquals(byteArrayOf(50, 100), p.first())
    }

    @Test
    fun monsterpub_tracys_dog_adds_prefix() {
        val p = Drivers.of("monsterpub").packets(
            ctx(vib(2), mapOf(ModuleType.VIBRATE to 0.5f), name = "TracyDog")
        )
        assertArrayEquals(byteArrayOf(0x03, 50, 50), p.first())
    }

    @Test
    fun tryfun_oscillate_and_rotate_checksum() {
        val modules = listOf(one(ModuleType.OSCILLATE, 9), one(ModuleType.ROTATE, 9))
        val p = Drivers.of("tryfun").packets(
            ctx(
                modules,
                mapOf(ModuleType.OSCILLATE to 1f, ModuleType.ROTATE to 1f)
            )
        )
        assertEquals(2, p.size)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0x02, 0x07, 0x09, 0xF0.toByte()), p[0])
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0x02, 0x08, 0x09, 0xEF.toByte()), p[1])
    }

    @Test
    fun tryfun_spray_vibrate() {
        val p = Drivers.of("tryfun").packets(
            ctx(listOf(one(ModuleType.VIBRATE, 4)), mapOf(ModuleType.VIBRATE to 1f))
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x02, 0x00, 0x05, 0x02, 0x04, 0x01, 0x00, 0xF9.toByte()),
            p.first()
        )
    }

    @Test
    fun tryfun_meta2_packet_id_and_checksum() {
        val modules = listOf(
            one(ModuleType.OSCILLATE, 100), one(ModuleType.VIBRATE, 100)
        )
        val p = Drivers.of("tryfun-meta2").packets(
            ctx(
                modules,
                mapOf(ModuleType.OSCILLATE to 1f, ModuleType.VIBRATE to 1f)
            )
        )
        assertEquals(2, p.size)
        assertArrayEquals(
            byteArrayOf(0x00, 0x02, 0x00, 0x05, 0x21, 0x05, 0x0B, 0x64, 0x6B),
            p[0]
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x00, 0x05, 0x21, 0x05, 0x08, 0x64, 0x6E),
            p[1]
        )
    }

    @Test
    fun tryfun_blackhole_vibrate() {
        val modules = listOf(one(ModuleType.OSCILLATE, 100), one(ModuleType.VIBRATE, 100))
        val p = Drivers.of("tryfun-blackhole").packets(
            ctx(
                modules,
                mapOf(ModuleType.OSCILLATE to 1f, ModuleType.VIBRATE to 1f)
            )
        )
        assertEquals(2, p.size)
        assertArrayEquals(
            byteArrayOf(0x00, 0x02, 0x00, 0x03, 0x0C, 0x64, 0x90.toByte()),
            p[0]
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x00, 0x03, 0x09, 0x64, 0x93.toByte()),
            p[1]
        )
    }

    @Test
    fun jissbon_reuses_galaku_frames() {
        // 杰士邦 小粉饼 / G29D 落在 Galaku OEM 方案里，双振动通道应与 galaku 输出逐字节一致
        val modules = listOf(
            ModuleSpec(ModuleType.VIBRATE, 0, "振动 A", 100),
            ModuleSpec(ModuleType.VIBRATE, 1, "振动 B", 100)
        )
        val c = DriverContext(
            "G29D", modules, mapOf("VIBRATE:0" to 0.4f, "VIBRATE:1" to 0.9f)
        )
        val a = Drivers.of("jissbon").packets(c)
        val b = Drivers.of("galaku").packets(c)
        assertEquals(1, a.size)
        assertEquals(b.size, a.size)
        a.zip(b).forEach { assertArrayEquals("jissbon 与 galaku 帧不一致", it.first, it.second) }
        val plain = GalakuProtocol.decrypt(a.first())
        assertEquals(0x23.toByte(), plain[0])   // 帧头
        assertEquals(3.toByte(), plain[6])      // 双电机标志
        assertEquals(40.toByte(), plain[7])     // 振动 A
        assertEquals(90.toByte(), plain[8])     // 振动 B
    }

    @Test
    fun all_protocols_have_driver() {
        val ids = listOf(
            "galaku", "jissbon", "lovense", "svakom-v1", "svakom-v2", "svakom-v3",
            "svakom-v4", "svakom-v5", "svakom-v6", "wevibe-8bit",
            "magic-motion-1", "magic-motion-2", "magic-motion-3", "magic-motion-4",
            "kiiroo-v21", "satisfyer", "monsterpub",
            "tryfun", "tryfun-meta2", "tryfun-blackhole"
        )
        ids.forEach { id ->
            val modules = vib(1)
            val out = Drivers.of(id).packets(ctx(modules, mapOf(ModuleType.VIBRATE to 1f)))
            assertTrue("$id 没有输出", out.isNotEmpty())
        }
    }
}
