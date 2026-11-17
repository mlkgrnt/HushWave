package com.pulse.app.core

/**
 * 扫描结果的取舍策略。
 *
 * 这里刻意**不用**系统的 `ScanFilter`：它只能按「完整设备名 / 精确服务 UUID / MAC」
 * 过滤，而目录里的名称是 `LVS-*`、`LOVE-*` 这种通配，Galaku、Svakom 很多型号
 * 又只靠型号名识别、广播里根本没有服务 UUID。真把过滤交给系统，这些真设备会被
 * 静默丢掉，而且没有任何回调能告诉我们「漏了」。
 *
 * 所以改成在回调里做一次软过滤：射频层照收（保持 LOW_LATENCY 的发现速度），
 * 但不认识的设备不进缓冲、不进列表。手动添加 / 设备特征需要看见未知设备，
 * 传 onlyRecognized = false 即可。
 */
object ScanFilterPolicy {
    /**
     * 弱匹配（只命中通用串口服务）仍然留下：设备页的品牌筛选会用到它们，
     * 页面本来就默认隐藏。丢掉的只有目录完全认不出来的设备。
     */
    fun keep(match: ToyMatch?, onlyRecognized: Boolean): Boolean =
        !onlyRecognized || match != null
}
