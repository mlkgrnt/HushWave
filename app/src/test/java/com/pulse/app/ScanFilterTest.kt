package com.pulse.app

import com.pulse.app.core.ProtocolSpec
import com.pulse.app.core.ScanFilterPolicy
import com.pulse.app.core.ToyMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 两档扫描模式的取舍：设备页只留认得的，手动添加收全部。 */
class ScanFilterTest {

    private fun match(weak: Boolean = false): ToyMatch = ToyMatch(
        protocol = ProtocolSpec(
            id = "fake",
            brand = "测试",
            display = "测试",
            profiles = emptyList(),
            names = emptyList(),
            services = emptyList(),
            models = mutableListOf(),
            defaults = emptyList(),
            keepAliveMs = 0,
            writeResponse = false
        ),
        model = null,
        confidence = if (weak) 0.5f else 0.9f,
        reason = "测试",
        weak = weak
    )

    @Test
    fun `设备页丢掉完全认不出来的设备`() {
        assertFalse(ScanFilterPolicy.keep(null, onlyRecognized = true))
    }

    @Test
    fun `设备页保留强匹配和弱匹配`() {
        assertTrue(ScanFilterPolicy.keep(match(), onlyRecognized = true))
        // 弱匹配要留给品牌筛选和「不确定 N 台」提示，不能在这里丢
        assertTrue(ScanFilterPolicy.keep(match(weak = true), onlyRecognized = true))
    }

    @Test
    fun `手动添加与设备特征收全部广播`() {
        assertTrue(ScanFilterPolicy.keep(null, onlyRecognized = false))
        assertTrue(ScanFilterPolicy.keep(match(), onlyRecognized = false))
        assertTrue(ScanFilterPolicy.keep(match(weak = true), onlyRecognized = false))
    }
}
