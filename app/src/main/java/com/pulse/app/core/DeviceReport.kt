package com.pulse.app.core

import android.os.Build
import com.pulse.app.BuildConfig

/**
 * 把扫描到的广播信息 + 连接后读到的 GATT 结构整理成一段可以直接贴进 issue 的文本。
 *
 * 只陈述看到的事实：广播里有什么、GATT 里有哪些服务和特征、本机认成了什么。
 * 不猜协议帧，也不假装支持。
 */
object DeviceReport {

    fun build(item: ScanItem, gatt: String?, record: ToyRecord?, note: String? = null): String =
        buildString {
            appendLine("## 设备特征")
            appendLine()
            appendLine("- App: HushWave ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "- 手机: ${Build.MANUFACTURER} ${Build.MODEL} · " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            appendLine()
            appendLine("### 广播")
            appendLine("- 名称: ${item.name}")
            appendLine("- 地址: ${item.address}")
            appendLine("- RSSI: ${item.rssi} dBm")
            appendLine("- 可连接: ${if (item.connectable) "是" else "否"}")
            if (item.services.isEmpty()) {
                appendLine("- 广播服务: 无")
            } else {
                appendLine("- 广播服务:")
                item.services.forEach { appendLine("  - $it") }
            }
            if (item.manufacturer.isNotEmpty()) {
                appendLine("- 厂商数据:")
                item.manufacturer.forEach { (id, hex) ->
                    appendLine("  - 0x%04X: %s".format(id, hex))
                }
            }
            if (item.serviceData.isNotEmpty()) {
                appendLine("- Service Data:")
                item.serviceData.forEach { (uuid, hex) -> appendLine("  - $uuid: $hex") }
            }
            appendLine()
            appendLine("### 识别")
            val match = item.match
            when {
                match == null -> appendLine("- 结果: 未收录")
                match.weak -> appendLine(
                    "- 结果: 未收录（只有共用 UUID 证据，可能是 ${match.protocol.brand} · " +
                        "${match.protocol.display}）"
                )

                else -> appendLine(
                    "- 结果: ${match.protocol.brand} · ${match.protocol.display}（${match.reason}）"
                )
            }
            if (record != null) {
                appendLine(
                    "- 已存记录: ${record.brand} · ${record.protocolId} · " +
                        record.modules.joinToString(", ") { it.label }
                )
            } else {
                appendLine("- 已存记录: 无")
            }
            appendLine()
            appendLine("### GATT 结构")
            if (gatt.isNullOrBlank()) {
                appendLine("- 未读取（${note ?: "设备没有连上"}）")
            } else {
                appendLine("```text")
                gatt.trimEnd().lineSequence().forEach { appendLine(it) }
                appendLine("```")
            }
            appendLine()
            appendLine("> 本机只读取了广播与 GATT 结构，没有下发任何控制帧。")
        }
}
