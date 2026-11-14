package com.pulse.app.core.drivers

import com.pulse.app.core.GalakuProtocol
import com.pulse.app.core.ModuleSpec
import com.pulse.app.core.ModuleType

/**
 * 每种品牌/固件一个 Driver，只负责“把模块值翻译成要写进特征值的字节”。
 *
 * 编码规则来自 buttplug（BSD-3-Clause）的 protocol_impl：
 *  lovense / svakom-v1..v6 / wevibe-8bit / magic-motion-1..4 / kiiroo-v21 / satisfyer
 * 伽拉库部分来自官方 App 反编译（比 buttplug 的实现更完整，支持吮吸/抽插/泵/电击）。
 * 杰士邦 jissbon 的小粉饼 / G29D 落在同一套 Galaku OEM 方案里，直接复用 GalakuDriver。
 */
data class DriverContext(
    val deviceName: String?,
    val specs: List<ModuleSpec>,
    val values: Map<String, Float>
) {
    fun specsOf(type: ModuleType): List<ModuleSpec> =
        specs.filter { it.type == type }.sortedBy { it.index }

    fun value(spec: ModuleSpec): Float = (values[spec.key] ?: 0f).coerceIn(0f, 1f)

    fun raw(type: ModuleType, index: Int = 0): Int {
        val spec = specsOf(type).getOrNull(index) ?: return 0
        return spec.raw(value(spec))
    }

    fun on(type: ModuleType, index: Int = 0): Int = if (raw(type, index) > 0) 1 else 0

    fun has(type: ModuleType): Boolean = specs.any { it.type == type }
}

interface ToyDriver {
    val id: String
    fun onConnect(ctx: DriverContext): List<ByteArray> = emptyList()
    fun packets(ctx: DriverContext): List<ByteArray>
    val keepAliveMs: Long get() = 0L
}

object Drivers {

    fun of(protocolId: String): ToyDriver = when {
        protocolId == "galaku" || protocolId == "jissbon" -> GalakuDriver()
        protocolId == "lovense" -> LovenseDriver()
        protocolId.startsWith("svakom-v") ->
            SvakomDriver(protocolId.removePrefix("svakom-v").toIntOrNull() ?: 1)
        protocolId == "wevibe-8bit" -> WeVibe8BitDriver()
        protocolId.startsWith("magic-motion-") ->
            MagicMotionDriver(protocolId.removePrefix("magic-motion-").toIntOrNull() ?: 1)
        protocolId == "kiiroo-v21" -> KiirooV21Driver()
        protocolId == "satisfyer" -> SatisfyerDriver()
        protocolId == "monsterpub" -> MonsterPubDriver()
        protocolId == "tryfun" -> TryFunDriver()
        protocolId == "tryfun-meta2" -> TryFunMeta2Driver()
        protocolId == "tryfun-blackhole" -> TryFunBlackHoleDriver()
        else -> NoopDriver(protocolId)
    }
}

class NoopDriver(override val id: String) : ToyDriver {
    override fun packets(ctx: DriverContext): List<ByteArray> = emptyList()
}

// ---------------------------------------------------------------- Galaku
class GalakuDriver : ToyDriver {
    override val id = "galaku"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val caiping = ctx.deviceName?.equals("AC695X_1(BLE)", ignoreCase = true) == true
        if (caiping) {
            val level = ctx.raw(ModuleType.PUMP).takeIf { it > 0 } ?: ctx.raw(ModuleType.VIBRATE)
            return listOf(GalakuProtocol.caipingPump(level > 0, level))
        }

        val out = mutableListOf<ByteArray>()
        val vibes = ctx.specsOf(ModuleType.VIBRATE)

        when {
            ctx.has(ModuleType.SUCTION) || ctx.has(ModuleType.THRUST) -> {
                if (ctx.has(ModuleType.SUCTION)) out += GalakuProtocol.suction(ctx.raw(ModuleType.SUCTION))
                if (ctx.has(ModuleType.THRUST)) out += GalakuProtocol.thrust(ctx.raw(ModuleType.THRUST))
            }

            ctx.has(ModuleType.PUMP) -> {
                val level = ctx.raw(ModuleType.PUMP)
                out += GalakuProtocol.pump(level > 0, level)
            }

            vibes.size >= 3 -> out += GalakuProtocol.vibe3(
                ctx.raw(ModuleType.VIBRATE, 0),
                ctx.raw(ModuleType.VIBRATE, 1),
                ctx.raw(ModuleType.VIBRATE, 2)
            )

            vibes.size == 2 -> out += GalakuProtocol.vibe2(
                ctx.raw(ModuleType.VIBRATE, 0),
                ctx.raw(ModuleType.VIBRATE, 1)
            )

            vibes.size == 1 -> out += GalakuProtocol.vibe(ctx.raw(ModuleType.VIBRATE, 0))
        }

        if (ctx.has(ModuleType.ESTIM)) {
            val freq = ctx.raw(ModuleType.ESTIM_FREQ).coerceIn(1, 10)
            val level = ctx.raw(ModuleType.ESTIM)
            if (vibes.size == 2) {
                out.clear()
                out += GalakuProtocol.combo(
                    ctx.raw(ModuleType.VIBRATE, 0),
                    ctx.raw(ModuleType.VIBRATE, 1),
                    freq,
                    level
                )
            } else {
                out += GalakuProtocol.estim(freq, level)
            }
        }

        if (ctx.has(ModuleType.HEAT)) out += GalakuProtocol.heat(ctx.raw(ModuleType.HEAT))
        if (ctx.has(ModuleType.LED)) out += GalakuProtocol.led(ctx.raw(ModuleType.LED))
        return out
    }
}

// ---------------------------------------------------------------- Lovense
class LovenseDriver : ToyDriver {
    override val id = "lovense"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val vibes = ctx.specsOf(ModuleType.VIBRATE)
        when {
            vibes.size <= 1 -> out += "Vibrate:${ctx.raw(ModuleType.VIBRATE)};".toByteArray()
            else -> vibes.forEachIndexed { i, spec ->
                out += "Vibrate${i + 1}:${spec.raw(ctx.value(spec))};".toByteArray()
            }
        }
        if (ctx.has(ModuleType.ROTATE)) out += "Rotate:${ctx.raw(ModuleType.ROTATE)};".toByteArray()
        if (ctx.has(ModuleType.OSCILLATE)) out += "Vibrate:${ctx.raw(ModuleType.OSCILLATE)};".toByteArray()
        if (ctx.has(ModuleType.CONSTRICT)) out += "Air:Level:${ctx.raw(ModuleType.CONSTRICT)};".toByteArray()
        return out
    }
}

// ---------------------------------------------------------------- Svakom
class SvakomDriver(private val version: Int) : ToyDriver {
    override val id = "svakom-v$version"
    override val keepAliveMs = 800L

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val v0 = ctx.raw(ModuleType.VIBRATE, 0)
        val v1 = ctx.raw(ModuleType.VIBRATE, 1)
        val on0 = if (v0 > 0) 1 else 0
        val on1 = if (v1 > 0) 1 else 0
        val out = mutableListOf<ByteArray>()

        when (version) {
            1 -> out += byteArrayOf(0x55, 0x04, 0x03, 0x00, on0.toByte(), v0.toByte())

            2 -> {
                out += byteArrayOf(0x55, 0x03, 0x03, 0x00, on0.toByte(), v0.toByte())
                if (ctx.specsOf(ModuleType.VIBRATE).size > 1) {
                    out += byteArrayOf(0x55, 0x06, 0x01, 0x00, v1.toByte(), v1.toByte())
                }
            }

            3 -> {
                out += byteArrayOf(0x55, 0x03, 0x03, 0x00, on0.toByte(), v0.toByte())
                if (ctx.specsOf(ModuleType.VIBRATE).size > 1) {
                    out += byteArrayOf(0x55, 0x09, 0x00, 0x00, on1.toByte(), v1.toByte())
                }
                if (ctx.has(ModuleType.ROTATE)) {
                    out += byteArrayOf(
                        0x55, 0x08, 0x00, 0x00,
                        ctx.raw(ModuleType.ROTATE).toByte(),
                        0xFF.toByte()
                    )
                }
            }

            4 -> out += multiPacket(ctx, v0, v1, byteArrayOf(0x55, 0x03), 7)
            5 -> out += multiPacket(ctx, v0, v1, byteArrayOf(0x55, 0x03), 6)
            6 -> {
                out += multiPacket(ctx, v0, v1, byteArrayOf(0x55, 0x03), 7)
                val v2 = ctx.raw(ModuleType.VIBRATE, 2)
                if (ctx.specsOf(ModuleType.VIBRATE).size > 2) {
                    out += byteArrayOf(0x55, 0x07, 0x00, 0x00, (if (v2 > 0) 1 else 0).toByte(), v2.toByte(), 0x00)
                }
            }

            else -> out += multiPacket(ctx, v0, v1, byteArrayOf(0x55, 0x03), 7)
        }

        // 电击（EMS）。Svakom 官方没有公开帧；这里沿用 v2 第二路的 0x06 帧，
        // 在 Viviana 上实测有效/无效由真机验证，未验证前不要外推到其它型号。
        if (ctx.has(ModuleType.ESTIM)) {
            val lvl = ctx.raw(ModuleType.ESTIM).coerceIn(0, 10)
            out += byteArrayOf(0x55, 0x06, 0x01, 0x00, lvl.toByte(), lvl.toByte())
        }
        return out
    }

    /** v4/v5/v6 共用的“双马达合一包”：用选择字节表达哪一路在动，档位取两者最大值 */
    private fun multiPacket(
        ctx: DriverContext,
        v0: Int,
        v1: Int,
        head: ByteArray,
        size: Int
    ): ByteArray {
        val single = ctx.specsOf(ModuleType.VIBRATE).size <= 1
        val select = when {
            single || (v0 > 0 && v1 > 0) || v0 == v1 -> 0x00
            v0 > 0 -> 0x01
            else -> 0x02
        }
        val on = if (v0 == 0 && v1 == 0) 0 else 1
        val level = maxOf(v0, v1)
        val data = ByteArray(size)
        data[0] = head[0]
        data[1] = head[1]
        data[2] = select.toByte()
        data[3] = 0x00
        data[4] = on.toByte()
        data[5] = level.toByte()
        return data
    }
}

// ---------------------------------------------------------------- We-Vibe
class WeVibe8BitDriver : ToyDriver {
    override val id = "wevibe-8bit"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val specs = ctx.specsOf(ModuleType.VIBRATE)
        val v0 = ctx.raw(ModuleType.VIBRATE, 0)
        val v1 = if (specs.size > 1) ctx.raw(ModuleType.VIBRATE, 1) else 0
        val data = if (v0 == 0 && v1 == 0) {
            byteArrayOf(0x0F, 0, 0, 0, 0, 0, 0, 0)
        } else {
            val status = ((if (v1 == 0) 0 else 2) or (if (v0 == 0) 0 else 1)).toByte()
            byteArrayOf(
                0x0F, 0x03, 0x00,
                (v1 + 3).toByte(),
                (v0 + 3).toByte(),
                status, 0x00, 0x00
            )
        }
        return listOf(data)
    }
}

// ---------------------------------------------------------------- MagicMotion
class MagicMotionDriver(private val version: Int) : ToyDriver {
    override val id = "magic-motion-$version"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val v0 = ctx.raw(ModuleType.VIBRATE, 0)
        val v1 = ctx.raw(ModuleType.VIBRATE, 1)
        val count = ctx.specsOf(ModuleType.VIBRATE).size

        return when (version) {
            1 -> listOf(
                byteArrayOf(
                    0x0B, 0xFF.toByte(), 0x04, 0x0A, 0x32, 0x32, 0x00, 0x04, 0x08,
                    v0.toByte(), 0x64, 0x00
                )
            )

            3 -> listOf(
                byteArrayOf(
                    0x0B, 0xFF.toByte(), 0x04, 0x0A, 0x46, 0x46, 0x00, 0x04, 0x08,
                    v0.toByte(), 0x64, 0x00
                )
            )

            else -> {
                val first = if (count > 1) v0 else v0
                val second = if (count > 1) v1 else v0
                listOf(
                    byteArrayOf(
                        0x10, 0xFF.toByte(), 0x04, 0x0A, 0x32, 0x32, 0x00, 0x04, 0x08,
                        first.toByte(), 0x64, 0x00,
                        0x04, 0x08,
                        second.toByte(), 0x64, 0x01
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Kiiroo v2.1
class KiirooV21Driver : ToyDriver {
    override val id = "kiiroo-v21"

    override fun packets(ctx: DriverContext): List<ByteArray> =
        listOf(byteArrayOf(0x01, ctx.raw(ModuleType.VIBRATE).toByte()))
}

// ---------------------------------------------------------------- Satisfyer
class SatisfyerDriver : ToyDriver {
    override val id = "satisfyer"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val out = ArrayList<Byte>()
        ctx.specsOf(ModuleType.VIBRATE).forEach { spec ->
            val v = spec.raw(ctx.value(spec)).toByte()
            repeat(4) { out.add(v) }
        }
        if (out.isEmpty()) out.add(0)
        return listOf(out.toByteArray())
    }
}

// ------------------------------------------------------- 小怪兽 Sistalk MonsterPub
/**
 * 帧格式：直接把每个通道的 0..100 依次写进去。
 * 小部分机型（Tracy's Dog）需要在前面补一个 0x03。
 * 连接时还要做一次 XOR 握手，见 BleController.monsterPubHandshake()。
 */
class MonsterPubDriver : ToyDriver {
    override val id = "monsterpub"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val specs = ctx.specsOf(ModuleType.VIBRATE).ifEmpty { ctx.specsOf(ModuleType.OSCILLATE) }
        if (specs.isEmpty()) return emptyList()
        val type = specs.first().type
        val data = ByteArray(specs.size) { i -> ctx.raw(type, i).toByte() }
        val tracys = ctx.deviceName?.startsWith("Tracy", ignoreCase = true) == true
        return listOf(if (tracys) byteArrayOf(0x03) + data else data)
    }
}

// ------------------------------------------------------------ 网易春风 TryFun 系列
/**
 * 校验和：sum 从 0xFF 开始减去除首字节外的所有字节，再加一个计数。
 * 经典 TryFun 的计数从 0 开始，Meta2 / BlackHole 把包头号也算进去，从 1 开始。
 */
private fun tfChecksum(head: ByteArray, countStart: Int): ByteArray {
    var sum = 0xFF
    var count = countStart
    for (i in 1 until head.size) {
        sum -= head[i].toInt() and 0xFF
        count++
    }
    val out = ByteArray(head.size + 1)
    head.copyInto(out)
    out[head.size] = ((sum + count) and 0xFF).toByte()
    return out
}

/** 经典 TryFun：AA 02 07/08 + 速度；少数型号（TF-SPRAY）用 9 字节振动帧 */
class TryFunDriver : ToyDriver {
    override val id = "tryfun"

    override fun packets(ctx: DriverContext): List<ByteArray> {
        if (ctx.has(ModuleType.VIBRATE)) {
            val s = ctx.raw(ModuleType.VIBRATE)
            return listOf(
                byteArrayOf(
                    0x00, 0x02, 0x00, 0x05,
                    if (s == 0) 1 else 2,
                    (if (s == 0) 2 else s).toByte(),
                    0x01,
                    if (s == 0) 1 else 0,
                    (0xFD - s.coerceAtLeast(1)).toByte()
                )
            )
        }
        val out = mutableListOf<ByteArray>()
        if (ctx.has(ModuleType.OSCILLATE)) {
            out += tfChecksum(
                byteArrayOf(0xAA.toByte(), 0x02, 0x07, ctx.raw(ModuleType.OSCILLATE).toByte()),
                0
            )
        }
        if (ctx.has(ModuleType.ROTATE)) {
            out += tfChecksum(
                byteArrayOf(0xAA.toByte(), 0x02, 0x08, ctx.raw(ModuleType.ROTATE).toByte()),
                0
            )
        }
        return out
    }
}

/** 网易春风 Meta 2：包头号 + 21 05 08/0B/0E + 速度 */
class TryFunMeta2Driver : ToyDriver {
    override val id = "tryfun-meta2"
    private var packetId = 0

    private fun nextId(): Byte {
        val v = packetId
        packetId = (packetId + 1) and 0xFF
        return v.toByte()
    }

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        if (ctx.has(ModuleType.OSCILLATE)) {
            out += tfChecksum(
                byteArrayOf(
                    nextId(), 0x02, 0x00, 0x05, 0x21, 0x05, 0x0B,
                    ctx.raw(ModuleType.OSCILLATE).toByte()
                ),
                1
            )
        }
        if (ctx.has(ModuleType.VIBRATE)) {
            out += tfChecksum(
                byteArrayOf(
                    nextId(), 0x02, 0x00, 0x05, 0x21, 0x05, 0x08,
                    ctx.raw(ModuleType.VIBRATE).toByte()
                ),
                1
            )
        }
        if (ctx.has(ModuleType.ROTATE)) {
            var s = ctx.raw(ModuleType.ROTATE).toByte()
            if (s >= 0) s++
            s = (-s).toByte()
            out += tfChecksum(
                byteArrayOf(nextId(), 0x02, 0x00, 0x05, 0x21, 0x05, 0x0E, s),
                1
            )
        }
        return out
    }
}

/** 网易春风 Black Hole Plus / Rock：包头号 + 03 0C/09 + 速度 */
class TryFunBlackHoleDriver : ToyDriver {
    override val id = "tryfun-blackhole"
    private var packetId = 0

    private fun nextId(): Byte {
        val v = packetId
        packetId = (packetId + 1) and 0xFF
        return v.toByte()
    }

    override fun packets(ctx: DriverContext): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        if (ctx.has(ModuleType.OSCILLATE)) {
            out += tfChecksum(
                byteArrayOf(nextId(), 0x02, 0x00, 0x03, 0x0C, ctx.raw(ModuleType.OSCILLATE).toByte()),
                1
            )
        }
        if (ctx.has(ModuleType.VIBRATE)) {
            out += tfChecksum(
                byteArrayOf(nextId(), 0x02, 0x00, 0x03, 0x09, ctx.raw(ModuleType.VIBRATE).toByte()),
                1
            )
        }
        return out
    }
}
