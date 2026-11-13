package com.pulse.app.core

/**
 * 伽拉库（galaku）小玩具的私有 BLE 协议实现。
 *
 * 全部结论来自对官方 App `com.wensheng.galakuplay` v1.0.92 的反编译：
 *  - `bluecontroller/bluetooth/BleControl.java`
 *  - `bluecontroller/bluetooth/EnDesCommand.java`
 *
 * 链路：
 *   Service        00001000-0000-1000-8000-00805f9b34fb
 *   Write char     00001001-0000-1000-8000-00805f9b34fb
 *   Notify char    00001002-0000-1000-8000-00805f9b34fb
 *
 * 帧格式（明文 10 字节）：
 *   [0] 0x5A 帧头  [1..3] 0,0,1  [4] 命令字  [5..9] 负载
 *
 * 发送前再包成 12 字节：
 *   [0] 0x23      [1..10] 明文 10 字节      [11] 前 11 字节之和（低 8 位）
 * 然后按查表异或加密（见 [encrypt]）。
 */
object GalakuProtocol {

    const val SERVICE_UUID = "00001000-0000-1000-8000-00805f9b34fb"
    const val WRITE_CHAR_UUID = "00001001-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_CHAR_UUID = "00001002-0000-1000-8000-00805f9b34fb"

    const val HEADER: Byte = 0x5A
    const val ENVELOPE: Byte = 0x23

    // ---------------------------------------------------------------- 命令字
    const val CMD_VIBE = 0x31        // 单电机强度, [5]=0..100
    const val CMD_MULTI = 0x40       // [5]=3 双电机 [6]=A [7]=B / [5]=7 三电机 [6][7][8]
    const val CMD_SUCTION = 0x32     // 吮吸, [5]=0..100
    const val CMD_THRUST = 0x71      // 抽插, [5]=0..100
    const val CMD_HEAT = 0x80        // 加热, [5]=档位
    const val CMD_LED = 0x15         // 灯效, [5]=档位
    const val CMD_ESTIM = 0x90       // 电击 [5]=频率 [6]=强度
    const val CMD_COMBO = 0x93       // [5]=0x0F, [6]=A [7]=B [8]=频率 [9]=强度
    const val CMD_PUMP = 0x60        // [5]=3, [6]=启停 [7]=强度
    const val CMD_PUMP_SIMPLE = 0x50 // [5]=启停

    /**
     * 厂家使用的 4 张 12 字节密钥表。行号由「前一密文字节 & 0x03」决定。
     * 表内容本身就是厂商在固件里写死的字符串碎片，原样保留。
     */
    private val KEY_TAB: Array<ByteArray> = arrayOf(
        byteArrayOf(0, 24, -104, -9, -91, 61, 13, 41, 37, 80, 68, 70),
        byteArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
        byteArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, -114, -99, -93),
        byteArrayOf(0, -59, -42, -25, -8, 10, 50, 32, 111, 98, 13, 10)
    )

    private fun key(prevCipher: Byte, index: Int): Byte =
        KEY_TAB[prevCipher.toInt() and 0x03][index]

    /** 12 字节明文 -> 12 字节密文 */
    fun encrypt(plain: ByteArray): ByteArray {
        val out = ByteArray(12)
        val head = plain[0]
        out[0] = head
        for (i in 1 until 12) {
            val k = key(out[i - 1], i)
            out[i] = ((k.toInt() xor head.toInt()) xor plain[i].toInt()).toByte()
            out[i] = (out[i] + k).toByte()
        }
        return out
    }

    /** 12 字节密文 -> 12 字节明文 */
    fun decrypt(cipher: ByteArray): ByteArray {
        val out = ByteArray(minOf(12, cipher.size))
        if (cipher.isEmpty()) return out
        val head = cipher[0]
        out[0] = head
        for (i in 1 until out.size) {
            val k = key(cipher[i - 1], i)
            out[i] = (k.toInt() xor ((cipher[i] - k).toInt() xor head.toInt())).toByte()
        }
        return out
    }

    /** 把 10 字节明文命令封装成可直接写入特征值的 12 字节密文 */
    fun frame(plain: ByteArray): ByteArray {
        require(plain.size == 10) { "command payload must be 10 bytes" }
        val buf = ByteArray(12)
        buf[0] = ENVELOPE
        plain.copyInto(buf, 1, 0, 10)
        var sum = 0
        for (i in 0 until 11) sum += buf[i].toInt()
        buf[11] = sum.toByte()
        return encrypt(buf)
    }

    // ------------------------------------------------------------- 命令构造
    private fun cmd(
        code: Int,
        p1: Int,
        p2: Int = 0,
        p3: Int = 0,
        p4: Int = 0,
        p5: Int = 0
    ): ByteArray = byteArrayOf(
        HEADER, 0, 0, 1,
        code.toByte(),
        p1.toByte(), p2.toByte(), p3.toByte(), p4.toByte(), p5.toByte()
    )

    /** 单电机振动 0..100 */
    fun vibe(level: Int): ByteArray = frame(cmd(CMD_VIBE, level, 0, 0, 0))

    /** 双电机振动（A / B 各 0..100） */
    fun vibe2(a: Int, b: Int): ByteArray = frame(cmd(CMD_MULTI, 3, a, b, 0))

    /** 三电机振动 */
    fun vibe3(a: Int, b: Int, c: Int): ByteArray = frame(cmd(CMD_MULTI, 7, a, b, c))

    /** 吮吸 0..100 */
    fun suction(level: Int): ByteArray = frame(cmd(CMD_SUCTION, level, 0, 0, 0))

    /** 抽插 0..100 */
    fun thrust(level: Int): ByteArray = frame(cmd(CMD_THRUST, level, 0, 0, 0))

    /** 加热档位 */
    fun heat(level: Int): ByteArray = frame(cmd(CMD_HEAT, level, 0, 0, 0))

    /** 灯效档位 */
    fun led(level: Int): ByteArray = frame(cmd(CMD_LED, level, 0, 0, 0))

    /** 电击：频率 + 强度 */
    fun estim(freq: Int, level: Int): ByteArray = frame(cmd(CMD_ESTIM, freq, level, 0, 0))

    /** 组合帧：A、B、电击频率、电击强度（设备类型 5 使用） */
    fun combo(a: Int, b: Int, freq: Int, level: Int): ByteArray =
        frame(cmd(CMD_COMBO, 15, a, b, freq, level))

    /** 泵：启停 + 强度（设备类型 2 使用） */
    fun pump(on: Boolean, level: Int): ByteArray =
        frame(cmd(CMD_PUMP, 3, if (on) 1 else 0, level, 0))

    /** 泵：仅启停 */
    fun pumpSimple(on: Boolean): ByteArray =
        frame(cmd(CMD_PUMP_SIMPLE, if (on) 1 else 0, 0, 0, 0))

    /**
     * 彩屏泵（AC695X 方案）使用 16 字节裸帧，不走加密：
     *   [0]=0xAA [1]=0x01 [2]=0x0A [3]=0x03 [4]=强度 [5]=启停
     */
    fun caipingPump(on: Boolean, level: Int): ByteArray {
        val b = ByteArray(16)
        b[0] = 0xAA.toByte()
        b[1] = 0x01
        b[2] = 0x0A
        b[3] = 0x03
        b[4] = level.toByte()
        b[5] = (if (on) 1 else 0).toByte()
        return b
    }

    /** 调试日志用：AA 01 0A ... */
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun toInts(bytes: ByteArray): String = bytes.joinToString(",") { it.toString() }
}
