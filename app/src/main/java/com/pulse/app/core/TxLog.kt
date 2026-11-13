package com.pulse.app.core

data class TxLog(
    val dir: Dir,
    val time: Long,
    val bytes: ByteArray,
    val note: String = "",
    val raw: ByteArray? = null
) {
    enum class Dir { TX, RX, SYS }

    val hex: String get() = if (bytes.isEmpty()) "" else GalakuProtocol.hex(bytes)
    val plainHex: String get() = bytes.joinToString(" ") { "%02X".format(it) }
    val timeLabel: String
        get() {
            val s = (time / 1000) % 86400
            return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
