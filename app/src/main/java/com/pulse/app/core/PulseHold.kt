package com.pulse.app.core

/**
 * 脉冲期间对通道的短期独占。
 *
 * 背景：脉冲只是往共享的 values 里写一下，摇一摇（60ms 一次）、波形 / 音乐（100ms 一帧）
 * 会立刻把它盖掉，所以看起来“失效”或“只闪一下”。这里给点过的通道加一个到期时间，
 * 期间其它来源的写入被丢弃，到期再把脉冲前的值还原。
 *
 * 连续点两次的问题：第二次会把「恢复值」记成第一次脉冲的峰值 1.0，回落时就卡在最大档。
 * 所以**已经在脉冲中的通道直接忽略第二次脉冲**，恢复基线永远取第一次之前的值。
 *
 * 纯逻辑，不碰 Android，可直接单测。
 */
class PulseHold {

    private val until = HashMap<String, Long>()
    private val restore = HashMap<String, Float>()

    /**
     * 开始一次脉冲。
     * @param current 取通道当前值，只对「不在脉冲中」的通道调用
     * @return 这次真正需要脉冲的通道；重复点击时为空
     */
    fun begin(keys: List<String>, now: Long, ms: Long, current: (String) -> Float): List<String> {
        val fresh = keys.distinct().filter { (until[it] ?: 0L) <= now }
        fresh.forEach {
            restore[it] = current(it)
            until[it] = now + ms
        }
        return fresh
    }

    fun allows(key: String, now: Long): Boolean = (until[key] ?: 0L) <= now

    /** 取出已经到期的通道和它们的恢复值，并清掉记录 */
    fun expired(now: Long): Map<String, Float> {
        if (until.isEmpty()) return emptyMap()
        val out = HashMap<String, Float>()
        val it = until.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value <= now) {
                out[e.key] = restore[e.key] ?: 0f
                it.remove()
                restore.remove(e.key)
            }
        }
        return out
    }

    fun clear() {
        until.clear()
        restore.clear()
    }

    val active: Boolean get() = until.isNotEmpty()
}
