package com.pulse.app

import com.pulse.app.core.PulseHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 脉冲独占：重点是「连点两次不能把恢复值记成 1.0」。
 */
class PulseHoldTest {

    @Test
    fun `脉冲期间其它来源写不进来`() {
        val hold = PulseHold()
        hold.begin(listOf("a"), now = 1000, ms = 380) { 0.2f }
        assertFalse(hold.allows("a", 1200))
        assertTrue(hold.allows("b", 1200))
        assertTrue(hold.allows("a", 1400))
    }

    @Test
    fun `连点两次第二次被忽略_恢复值仍是第一次之前的值`() {
        val hold = PulseHold()
        val first = hold.begin(listOf("a"), now = 1000, ms = 380) { 0.2f }
        assertEquals(listOf("a"), first)

        // 200ms 后再点一次：此时通道值是脉冲峰值 1.0，绝不能把它记成恢复值
        val second = hold.begin(listOf("a"), now = 1200, ms = 380) { 1f }
        assertTrue(second.isEmpty())

        val back = hold.expired(1400)
        assertEquals(0.2f, back["a"]!!, 0.0001f)
    }

    @Test
    fun `到期才恢复_没到期不返回`() {
        val hold = PulseHold()
        hold.begin(listOf("a", "b"), now = 1000, ms = 380) { 0.1f }
        assertTrue(hold.expired(1100).isEmpty())
        val back = hold.expired(1500)
        assertEquals(2, back.size)
        assertTrue(hold.expired(1600).isEmpty())
    }

    @Test
    fun `stopAll 之后脉冲记录清空`() {
        val hold = PulseHold()
        hold.begin(listOf("a"), now = 1000, ms = 380) { 0.3f }
        hold.clear()
        assertTrue(hold.allows("a", 1001))
        assertTrue(hold.expired(2000).isEmpty())
        assertFalse(hold.active)
    }
}
