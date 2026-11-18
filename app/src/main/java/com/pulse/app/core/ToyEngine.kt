package com.pulse.app.core

import com.pulse.app.core.drivers.DriverContext
import com.pulse.app.core.drivers.Drivers
import com.pulse.app.core.drivers.ToyDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * 统一输出层：UI / 波形 / 音乐 / 摇动都只改 [values]，由 Driver 翻译成字节。
 * 45ms 节流 + 去重，避免把 BLE 队列打爆。
 */
class ToyEngine(private val ble: BleController) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _protocol = MutableStateFlow<ProtocolSpec?>(null)
    val protocol: StateFlow<ProtocolSpec?> = _protocol.asStateFlow()

    private val _model = MutableStateFlow<CatalogModel?>(null)
    val model: StateFlow<CatalogModel?> = _model.asStateFlow()

    private val _modules = MutableStateFlow<List<ModuleSpec>>(emptyList())
    val modules: StateFlow<List<ModuleSpec>> = _modules.asStateFlow()

    private val _values = MutableStateFlow<Map<String, Float>>(emptyMap())
    val values: StateFlow<Map<String, Float>> = _values.asStateFlow()

    private val _masterActive = MutableStateFlow(false)
    val masterActive: StateFlow<Boolean> = _masterActive.asStateFlow()

    private val _masterValue = MutableStateFlow(0f)
    val masterValue: StateFlow<Float> = _masterValue.asStateFlow()

    private val _demo = MutableStateFlow(true)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()

    private var driver: ToyDriver = Drivers.of("none")
    private var deviceName: String? = null
    private var lastSentSignatures = mutableMapOf<Int, String>()
    private var lastDispatch = 0L
    private var pending = false

    /** 脉冲期间独占这些通道，其它来源的写入会被丢弃 */
    private val pulseHold = PulseHold()

    val energy: Float
        get() {
            val vibes = _values.value.filterKeys { !it.startsWith(ModuleType.ESTIM_FREQ.name) }
            return vibes.values.maxOrNull() ?: 0f
        }

    fun configure(
        protocol: ProtocolSpec?,
        model: CatalogModel?,
        modules: List<ModuleSpec>,
        deviceName: String?,
        saved: Map<String, Float> = emptyMap()
    ) {
        _protocol.value = protocol
        _model.value = model
        _modules.value = modules
        this.deviceName = deviceName
        driver = Drivers.of(protocol?.id ?: "none")
        lastSentSignatures.clear()
        val init = modules.associate { spec ->
            spec.key to (saved[spec.key] ?: if (spec.type == ModuleType.ESTIM_FREQ) 0.4f else 0f)
        }
        _values.value = init
        _masterActive.value = false
        _masterValue.value = 0f
        pulseHold.clear()
        val ka = max(protocol?.keepAliveMs ?: 0L, driver.keepAliveMs)
        ble.updateKeepAlive(ka)
        if (modules.isNotEmpty()) dispatch(force = true)
    }

    fun setDemo(enabled: Boolean) {
        _demo.value = enabled
        if (enabled) lastSentSignatures.clear()
    }

    fun set(key: String, value: Float) {
        if (!pulseHold.allows(key, System.currentTimeMillis())) return
        val next = _values.value.toMutableMap()
        next[key] = value.coerceIn(0f, 1f)
        _values.value = next
        _masterActive.value = false
        dispatch()
    }

    fun setMany(map: Map<String, Float>) {
        val now = System.currentTimeMillis()
        val allowed = map.filterKeys { pulseHold.allows(it, now) }
        if (allowed.isEmpty()) return
        val next = _values.value.toMutableMap()
        allowed.forEach { (k, v) -> if (next.containsKey(k)) next[k] = v.coerceIn(0f, 1f) }
        _values.value = next
        _masterActive.value = false
        dispatch()
    }

    /** 总强度：一次推所有通道（频率类参数除外） */
    fun setMaster(value: Float) {
        val now = System.currentTimeMillis()
        val v = value.coerceIn(0f, 1f)
        val next = _values.value.mapValues { (k, old) ->
            when {
                k.startsWith(ModuleType.ESTIM_FREQ.name) -> old
                !pulseHold.allows(k, now) -> old
                else -> v
            }
        }
        _values.value = next
        _masterValue.value = v
        _masterActive.value = true
        dispatch()
    }

    fun stopAll() {
        pulseHold.clear()
        _values.value = _values.value.mapValues { 0f }
        _masterActive.value = false
        _masterValue.value = 0f
        lastSentSignatures.clear()
        dispatch(force = true)
    }

    fun pulse(key: String, level: Float = 1f, ms: Long = 380) =
        pulseMany(listOf(key), level, ms)

    /**
     * 脉冲：独占这些通道 [ms] 毫秒，期间摇一摇 / 波形 / 音乐 / 滑杆都写不进来。
     * 已经在脉冲中的通道会忽略这次点击——否则第二次会把恢复基线记成 1.0，回落时卡在最大档。
     */
    fun pulseMany(keys: List<String>, level: Float = 1f, ms: Long = 380) {
        if (keys.isEmpty()) return
        val fresh = pulseHold.begin(keys, System.currentTimeMillis(), ms) {
            _values.value[it] ?: 0f
        }
        if (fresh.isEmpty()) return
        // 首帧强制下发，避免被 100ms 节流吞掉
        apply(fresh.associateWith { level }, force = true)
        scope.launch {
            delay(ms + 20)
            val back = pulseHold.expired(System.currentTimeMillis())
            if (back.isNotEmpty()) apply(back)
        }
    }

    /** 内部写值：绕过脉冲独占，只有脉冲本身和到期恢复会走这里 */
    private fun apply(map: Map<String, Float>, force: Boolean = false) {
        val next = _values.value.toMutableMap()
        map.forEach { (k, v) -> if (next.containsKey(k)) next[k] = v.coerceIn(0f, 1f) }
        _values.value = next
        _masterActive.value = false
        dispatch(force = force)
    }

    fun primaryKey(): String? =
        _modules.value.firstOrNull { it.type == ModuleType.VIBRATE }?.key
            ?: _modules.value.firstOrNull()?.key

    private fun dispatch(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDispatch < 100) {
            if (!pending) {
                pending = true
                scope.launch {
                    delay(110)
                    pending = false
                    dispatch(force = true)
                }
            }
            return
        }
        lastDispatch = now
        if (_modules.value.isEmpty()) return

        val ctx = DriverContext(deviceName, _modules.value, _values.value)
        val packets = driver.packets(ctx)
        if (packets.isEmpty()) return

        val changed = packets.filterIndexed { i, p ->
            val sig = p.joinToString(" ") { "%02X".format(it) }
            lastSentSignatures[i] != sig
        }
        if (changed.isEmpty()) return

        if (_demo.value) {
            changed.forEach { ble.addLog(TxLog.Dir.TX, it, "demo") }
        } else {
            ble.sendSet(changed)
        }
        packets.forEachIndexed { i, p ->
            lastSentSignatures[i] = p.joinToString(" ") { "%02X".format(it) }
        }
    }

    fun ratioOf(spec: ModuleSpec): Float = (_values.value[spec.key] ?: 0f).coerceIn(0f, 1f)

    fun rawOf(spec: ModuleSpec): Int = spec.raw(ratioOf(spec))

    fun clamp01(v: Float): Float = min(1f, max(0f, v))
}
