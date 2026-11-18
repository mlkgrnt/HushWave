package com.pulse.app

import com.pulse.app.R
import com.pulse.app.core.Str
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.app.core.BleController
import com.pulse.app.core.BleState
import com.pulse.app.core.ModeMemory
import com.pulse.app.core.ModeTargets
import com.pulse.app.core.ModuleSpec
import com.pulse.app.core.ModuleType
import com.pulse.app.core.MusicEngine
import com.pulse.app.core.Pattern
import com.pulse.app.core.PatternPlayer
import com.pulse.app.core.PatternStore
import com.pulse.app.core.PresetLibrary
import com.pulse.app.core.ProtocolSpec
import com.pulse.app.core.ScanItem
import com.pulse.app.core.ToyCatalog
import com.pulse.app.core.ToyEngine
import com.pulse.app.core.ToyMemory
import com.pulse.app.core.ToyRecord
import com.pulse.app.core.WaveLayer
import com.pulse.app.core.WaveMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

class PulseViewModel(app: Application) : AndroidViewModel(app) {

    val ble = BleController(app)
    val engine = ToyEngine(ble)
    val music = MusicEngine(viewModelScope)
    val player = PatternPlayer(engine, viewModelScope)
    val memory = ToyMemory(app)
    private val modeMemory = ModeMemory(app)
    val library = com.pulse.app.core.MusicLibrary(app)
    private val store = PatternStore(app)

    /** 当前连接的玩具地址，模式目标按它存 */
    private var modeAddress: String? = null

    val presets: List<Pattern> = PresetLibrary.presets

    private val _customPatterns = MutableStateFlow(store.load())
    val customPatterns: StateFlow<List<Pattern>> = _customPatterns.asStateFlow()

    private val _brandFilter = MutableStateFlow<String?>(null)
    val brandFilter: StateFlow<String?> = _brandFilter.asStateFlow()

    val visibleDevices: StateFlow<List<ScanItem>> =
        combine(ble.devices, _brandFilter) { list, brand ->
            if (brand == null) list else list.filter { it.match?.protocol?.brand == brand }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeRecord = MutableStateFlow<ToyRecord?>(null)
    val activeRecord: StateFlow<ToyRecord?> = _activeRecord.asStateFlow()

    /**
     * 「有东西可下发」才算能控制：已连上，或调试预览里已经装好通道。
     * 断开后 engine 的通道会被清空，于是控制 / 波形 / 声控 / 音乐这几处的
     * 通道选择和按钮统一跟着变灰，不用各页面各自判断连接状态。
     */
    val canControl: StateFlow<Boolean> =
        combine(ble.state, engine.modules, engine.demo) { st, mods, demo ->
            mods.isNotEmpty() && (st is BleState.Ready || demo)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _musicSync = MutableStateFlow(false)
    val musicSync: StateFlow<Boolean> = _musicSync.asStateFlow()

    private val _shakeMode = MutableStateFlow(false)
    val shakeMode: StateFlow<Boolean> = _shakeMode.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _voiceTargets = MutableStateFlow<Set<String>>(emptySet())
    val voiceTargets: StateFlow<Set<String>> = _voiceTargets.asStateFlow()

    private val _songTargets = MutableStateFlow<Set<String>>(emptySet())
    val songTargets: StateFlow<Set<String>> = _songTargets.asStateFlow()

    private val _songs = MutableStateFlow(library.list())
    val songs: StateFlow<List<com.pulse.app.core.Song>> = _songs.asStateFlow()

    private val _song = MutableStateFlow<com.pulse.app.core.Song?>(null)
    val song: StateFlow<com.pulse.app.core.Song?> = _song.asStateFlow()

    private val _songPlaying = MutableStateFlow(false)
    val songPlaying: StateFlow<Boolean> = _songPlaying.asStateFlow()

    private val _songPosition = MutableStateFlow(0L)
    val songPosition: StateFlow<Long> = _songPosition.asStateFlow()

    private val _songIntensity = MutableStateFlow(0f)
    val songIntensity: StateFlow<Float> = _songIntensity.asStateFlow()

    private val _songBusy = MutableStateFlow<String?>(null)
    val songBusy: StateFlow<String?> = _songBusy.asStateFlow()

    private val _quickTargets = MutableStateFlow<Set<String>>(emptySet())
    val quickTargets: StateFlow<Set<String>> = _quickTargets.asStateFlow()

    private val _patternTargets = MutableStateFlow<List<String>>(emptyList())
    val patternTargets: StateFlow<List<String>> = _patternTargets.asStateFlow()

    private val _patternSync = MutableStateFlow(true)
    val patternSync: StateFlow<Boolean> = _patternSync.asStateFlow()

    private val _savedToys = MutableStateFlow(memory.all())
    val savedToys: StateFlow<List<ToyRecord>> = _savedToys.asStateFlow()

    /**
     * 「手动添加」只用得上没认出来的设备：能明确识别的已经在首页「附近设备」里了，
     * 不必再占一遍位置。弱匹配（只有通用串口 UUID）的留在这里，等用户指定品牌。
     *
     * 排序交给 BleController 每秒推一次的稳定快照，这里不再单独采样。
     */
    val manualDevices: StateFlow<List<ScanItem>> = ble.devices
        .map { list -> list.filter { it.match == null || it.match?.weak == true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val brands: List<String> get() = ToyCatalog.brands
    val protocolCount: Int get() = ToyCatalog.protocols.size
    val modelCount: Int get() = ToyCatalog.modelCount()

    init {
        ToyCatalog.load(app)
        engine.setDemo(true)
        observeBle()
        observeMusic()
        viewModelScope.launch { autosaveLoop() }
    }

    private fun observeBle() {
        viewModelScope.launch {
            ble.state.collect { st ->
                when (st) {
                    is BleState.Ready -> {
                        engine.setDemo(false)
                        val rec = _activeRecord.value
                        _toast.value = if (rec != null) {
                            Str.s(R.string.x_connected_restored, rec.name, rec.modules.size)
                        } else {
                            Str.s(R.string.x_connected_to, st.name)
                        }
                    }

                    is BleState.Failed -> {
                        // 连接失败 / 中途掉线：通道已经没有意义了，清掉，
                        // 否则控制页还留着一排推不动的滑杆。
                        if (engine.modules.value.isNotEmpty()) clearLinkState()
                        engine.setDemo(true)
                        _toast.value = st.reason
                    }

                    is BleState.Idle -> {
                        // 手动断开由 disconnect() 自己收尾，这里只兜「不知道谁断的」的情况
                        if (engine.modules.value.isNotEmpty() && !engine.demo.value) clearLinkState()
                    }

                    else -> Unit
                }
            }
        }
        // 扫描不再改动连接状态，失败信息走单独的通道，只弹提示、不把引擎切回演示模式
        viewModelScope.launch {
            ble.scanError.collect { msg ->
                if (msg != null) {
                    _toast.value = msg
                    ble.clearScanError()
                }
            }
        }
    }

    private fun observeMusic() {
        viewModelScope.launch {
            music.state.collect { m ->
                if (_musicSync.value && !m.running && m == MusicEngine.MusicState()) {
                    _toast.value = Str.s(R.string.vm_000)
                }
                if (_musicSync.value && m.running) {
                    val keys = resolveVoiceTargets()
                    if (keys.isNotEmpty()) engine.setMany(keys.associateWith { m.intensity })
                }
            }
        }
    }

    private fun vibrateKeys(): List<String> =
        engine.modules.value.filter { it.type == ModuleType.VIBRATE }.map { it.key }

    private fun defaultQuickTargets(modules: List<ModuleSpec>): Set<String> {
        val vibes = modules.filter { it.type == ModuleType.VIBRATE }
        return (if (vibes.isNotEmpty()) vibes else modules).map { it.key }.toSet()
    }

    /** 摇一摇 / 脉冲 作用于哪些通道 */
    fun resolveQuickTargets(): List<String> {
        val all = engine.modules.value.map { it.key }.toSet()
        return _quickTargets.value.filter { it in all }
    }

    fun setQuickTargets(keys: Set<String>) {
        _quickTargets.value = keys
        persistModes()
    }

    fun resolveVoiceTargets(): List<String> {
        val all = engine.modules.value.map { it.key }.toSet()
        return _voiceTargets.value.filter { it in all }
    }

    fun setVoiceTargets(keys: Set<String>) {
        _voiceTargets.value = keys
        persistModes()
    }

    fun resolveSongTargets(): List<String> {
        val all = engine.modules.value.map { it.key }.toSet()
        return _songTargets.value.filter { it in all }
    }

    fun setSongTargets(keys: Set<String>) {
        _songTargets.value = keys
        persistModes()
    }

    // ------------------------------------------------------- 模式目标记忆
    /** 没有记录时用默认值；有记录就照记录来，空集合代表「用户全关了」 */
    private fun restoreTargets(
        saved: List<String>?,
        modules: List<ModuleSpec>
    ): Set<String> {
        if (saved == null) return defaultQuickTargets(modules)
        val available = modules.map { it.key }.toSet()
        return saved.filter { it in available }.toSet()
    }

    private fun persistModes() {
        val address = modeAddress ?: return
        modeMemory.put(
            address,
            ModeTargets(
                quick = _quickTargets.value.toList(),
                voice = _voiceTargets.value.toList(),
                song = _songTargets.value.toList(),
                pattern = _patternTargets.value,
                patternSync = _patternSync.value
            )
        )
    }

    private fun applyRestoredTargets(address: String?, modules: List<ModuleSpec>) {
        modeAddress = address
        val saved = address?.let { modeMemory.get(it) }
        _quickTargets.value = restoreTargets(saved?.quick, modules)
        _voiceTargets.value = restoreTargets(saved?.voice, modules)
        _songTargets.value = restoreTargets(saved?.song, modules)
        _patternTargets.value = saved?.pattern?.filter { key -> modules.any { it.key == key } }
            ?: emptyList()
        _patternSync.value = saved?.patternSync ?: true
    }

    /** 模块列表变了（换协议 / 手动改通道）后，把已经不存在的通道从选择里剔掉 */
    private fun pruneTargets(modules: List<ModuleSpec>) {
        val available = modules.map { it.key }.toSet()
        val fallback = defaultQuickTargets(modules)
        fun prune(set: Set<String>): Set<String> =
            if (set.isEmpty()) set else set.filter { it in available }.toSet().ifEmpty { fallback }
        _quickTargets.value = prune(_quickTargets.value)
        _voiceTargets.value = prune(_voiceTargets.value)
        _songTargets.value = prune(_songTargets.value)
        _patternTargets.value = _patternTargets.value.filter { it in available }
    }

    private fun autosaveLoop() = viewModelScope.launch {
        while (true) {
            delay(3000)
            persistNow()
        }
    }

    private fun persistNow() {
        val rec = _activeRecord.value ?: return
        val values = engine.values.value
        if (values.isEmpty()) return
        memory.patchValues(
            address = rec.address,
            values = values,
            patternId = player.state.value?.pattern?.id ?: rec.patternId,
            scale = player.state.value?.scale ?: rec.scale
        )
        _activeRecord.value = rec.copy(
            values = values,
            patternId = player.state.value?.pattern?.id ?: rec.patternId,
            scale = player.state.value?.scale ?: rec.scale,
            updatedAt = System.currentTimeMillis()
        )
        _savedToys.value = memory.all()
    }

    // ------------------------------------------------------------- 设备
    fun setBrandFilter(brand: String?) {
        _brandFilter.value = brand
    }

    /** 默认只扫目录认得的设备；手动添加 / 设备特征传 false 拿全量广播 */
    fun startScan(onlyRecognized: Boolean = true) = ble.startScan(onlyRecognized)
    fun stopScan() = ble.stopScan()

    fun connect(item: ScanItem, protocolOverride: ProtocolSpec? = null) {
        val match = item.match
        val protocol = protocolOverride ?: match?.protocol
        if (protocol == null) {
            _toast.value = Str.s(R.string.vm_001)
            return
        }
        val model = protocol.modelFor(item.name)
        val saved = memory.find(item.address)
        val usable = saved != null && saved.protocolId == protocol.id && saved.modules.isNotEmpty()
        val catalogModules = model?.modules ?: emptyList()
        // 已知型号优先用目录里的模块定义（标签/上限随版本更新），
        // 再用记忆里的 key 过滤，保留用户关掉的通道
        val modules = when {
            usable && catalogModules.isNotEmpty() -> {
                val kept = saved!!.modules.mapNotNull { s -> catalogModules.firstOrNull { it.key == s.key } }
                if (kept.isNotEmpty()) kept else catalogModules
            }

            usable -> saved!!.modules
            catalogModules.isNotEmpty() -> catalogModules
            else -> protocol.defaults
        }
        _activeRecord.value = ToyRecord(
            address = item.address,
            name = model?.name ?: item.name,
            brand = protocol.brand,
            protocolId = protocol.id,
            modelName = model?.name ?: item.name,
            modules = modules,
            values = if (usable) saved!!.values else emptyMap(),
            patternId = saved?.patternId,
            scale = saved?.scale ?: 1f,
            updatedAt = System.currentTimeMillis(),
            knownModel = model != null && model.modules.isNotEmpty()
        )
        engine.configure(protocol, model, modules, item.name, if (usable) saved!!.values else emptyMap())
        // 每台玩具各自记住「哪个模式作用于哪些通道」
        applyRestoredTargets(item.address, modules)
        engine.setDemo(false)
        ble.connect(item, protocol)
        if (!usable) {
            memory.save(_activeRecord.value!!)
            _savedToys.value = memory.all()
        }
    }

    /** 只读一遍 GATT 结构，不下发控制帧。给「设备特征收集」用。 */
    fun inspectDevice(item: ScanItem) {
        if (!ble.isSupported) {
            ble.failInspect(Str.s(R.string.vm_002))
            return
        }
        if (!ble.isBluetoothOn) {
            ble.failInspect(Str.s(R.string.vm_003))
            return
        }
        when (ble.state.value) {
            is BleState.Ready -> {
                _toast.value = Str.s(R.string.vm_004)
                return
            }

            is BleState.Connecting -> return
            else -> Unit
        }
        ble.inspect(item)
    }

    /** 没设备时载入一台虚拟 Galaku 双电机，用于预览界面与波形 */
    fun loadDemo() {
        val protocol = ToyCatalog.find("galaku")
        android.util.Log.d("PulseDemo", "loadDemo protocol=$protocol")
        if (protocol == null) return
        val modules = listOf(
            ModuleSpec(ModuleType.VIBRATE, 0, Str.s(R.string.vm_005), 100),
            ModuleSpec(ModuleType.VIBRATE, 1, Str.s(R.string.vm_006), 100)
        )
        _activeRecord.value = null
        engine.configure(protocol, null, modules, "DEMO", emptyMap())
        applyRestoredTargets(null, modules)
        engine.setDemo(true)
        _toast.value = Str.s(R.string.vm_007)
    }

    /** 连接中取消：不用干等十几秒超时 */
    fun cancelConnect() {
        if (ble.state.value !is BleState.Connecting) return
        ble.cancelConnect()
        clearLinkState()
        _toast.value = Str.s(R.string.vm_008)
    }

    /**
     * 回到「什么都没连」的状态：停掉所有正在跑的东西、清空通道与模式目标。
     * 断开 / 掉线 / 取消连接都走这里，避免各条路径漏清某一样。
     */
    private fun clearLinkState() {
        player.stop()
        music.stop()
        _musicSync.value = false
        if (_shakeMode.value) setShakeMode(false)
        engine.configure(null, null, emptyList(), null)
        _quickTargets.value = emptySet()
        _voiceTargets.value = emptySet()
        _songTargets.value = emptySet()
        _patternTargets.value = emptyList()
        modeAddress = null
        _activeRecord.value = null
    }

    fun disconnect() {
        persistNow()
        persistModes()
        ble.disconnect()
        clearLinkState()
    }

    /** 手动换协议（识别错或未知型号时） */
    fun overrideProtocol(protocolId: String) {
        val rec = _activeRecord.value ?: return
        val protocol = ToyCatalog.find(protocolId) ?: return
        val model = protocol.modelFor(rec.name)
        val modules = model?.modules?.takeIf { it.isNotEmpty() } ?: protocol.defaults
        val newRec = rec.copy(
            brand = protocol.brand,
            protocolId = protocol.id,
            modelName = model?.name ?: rec.name,
            modules = modules,
            values = emptyMap(),
            updatedAt = System.currentTimeMillis(),
            knownModel = model != null && model.modules.isNotEmpty()
        )
        _activeRecord.value = newRec
        engine.configure(protocol, model, modules, rec.name, emptyMap())
        pruneTargets(modules)
        persistModes()
        memory.save(newRec)
        _savedToys.value = memory.all()
        _toast.value = Str.s(R.string.x_protocol_switched, Str.data(protocol.brand), Str.data(protocol.display))
    }

    /** 手动改模块列表（未知型号 / 检测不全） */
    fun updateModules(modules: List<ModuleSpec>) {
        val rec = _activeRecord.value ?: return
        if (modules.isEmpty()) return
        val newRec = rec.copy(modules = modules, updatedAt = System.currentTimeMillis())
        _activeRecord.value = newRec
        engine.configure(engine.protocol.value, engine.model.value, modules, rec.name, rec.values)
        pruneTargets(modules)
        persistModes()
        memory.save(newRec)
        _savedToys.value = memory.all()
        _toast.value = Str.s(R.string.x_modules_saved, modules.size)
    }

    fun deleteToy(address: String) {
        memory.forget(address)
        modeMemory.forget(address)
        _savedToys.value = memory.all()
        if (_activeRecord.value?.address == address) _activeRecord.value = null
    }

    fun clearAllToys() {
        memory.clear()
        modeMemory.clear()
        _savedToys.value = memory.all()
    }

    // ------------------------------------------------------------- 通道
    fun setModule(key: String, value: Float) = engine.set(key, value)

    /** 总强度：一次推所有通道；拖动时接管所有自动模式 */
    fun setMaster(value: Float) {
        if (player.state.value?.playing == true) player.stop()
        if (_shakeMode.value) setShakeMode(false)
        if (_musicSync.value) setMusicSync(false)
        if (_songPlaying.value) stopSong()
        engine.setMaster(value)
    }

    fun stopAll() {
        player.stop()
        if (_musicSync.value) setMusicSync(false)
        if (_shakeMode.value) setShakeMode(false)
        engine.stopAll()
    }

    fun pulse() {
        engine.pulseMany(resolveQuickTargets())
    }

    // ------------------------------------------------------------- 波形
    fun playPattern(
        p: Pattern,
        scale: Float,
        targetKeys: List<String>,
        sync: Boolean = true
    ) {
        if (_shakeMode.value) setShakeMode(false)
        if (_musicSync.value) setMusicSync(false)
        if (_songPlaying.value) stopSong()
        _patternTargets.value = targetKeys
        _patternSync.value = sync
        persistModes()
        player.play(p, scale, targetKeys, sync)
    }

    fun togglePattern() = player.toggle()
    fun stopPattern() = player.stop()
    fun setPatternScale(v: Float) = player.setScale(v)

    fun setPatternTarget(keys: List<String>, sync: Boolean) {
        _patternTargets.value = keys
        _patternSync.value = sync
        persistModes()
        player.setTargets(keys, sync)
    }

    /**
     * 手绘波形。传 [id] 表示编辑已有条目：沿用原 id 原地覆盖，顺序不变；
     * 不传就是新增。
     */
    fun saveCustom(
        name: String,
        durationMs: Int,
        trace: List<Pair<Float, Float>>,
        id: String? = null
    ): Pattern {
        val drawn = PresetLibrary.fromTrace(name, durationMs, trace)
        val p = if (id != null) drawn.copy(id = id) else drawn
        upsert(p)
        _toast.value = if (id != null) Str.s(R.string.x_updated, name) else Str.s(R.string.x_saved, name)
        return p
    }

    /** 函数合成：采样成点保存，同时留一份配方，方便以后接着调 */
    fun saveSynthesis(
        name: String,
        durationMs: Int,
        layers: List<WaveLayer>,
        id: String? = null
    ): Pattern {
        val p = Pattern(
            id = id ?: "custom_${System.currentTimeMillis()}",
            name = name,
            durationMs = durationMs.coerceIn(300, 30_000),
            points = WaveMath.synthesize(layers),
            tag = Str.s(R.string.vm_009),
            recipe = WaveMath.encode(layers)
        )
        upsert(p)
        _toast.value = if (id != null) Str.s(R.string.x_updated, name) else Str.s(R.string.x_saved, name)
        return p
    }

    private fun upsert(p: Pattern) {
        val list = _customPatterns.value
        val idx = list.indexOfFirst { it.id == p.id }
        _customPatterns.value = if (idx >= 0) {
            list.toMutableList().also { it[idx] = p }
        } else {
            list + p
        }
        store.save(_customPatterns.value)
    }

    fun deleteCustom(id: String) {
        val list = _customPatterns.value.filterNot { it.id == id }
        _customPatterns.value = list
        store.save(list)
    }

    // ------------------------------------------------------------- 音乐
    fun setMusicSync(enabled: Boolean) {
        _musicSync.value = enabled
        if (enabled) {
            if (_shakeMode.value) setShakeMode(false)
            if (_songPlaying.value) stopSong()
            player.stop()
            music.start()
            _toast.value = Str.s(R.string.vm_010)
        } else {
            music.stop()
            vibrateKeys().forEach { engine.set(it, 0f) }
        }
    }

    fun setMusicSensitivity(v: Float) = music.setSensitivity(v)

    // ------------------------------------------------------------- 摇一摇
    private var sensorListener: SensorEventListener? = null

    fun setShakeMode(enabled: Boolean) {
        _shakeMode.value = enabled
        val sm = getApplication<Application>().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (enabled) {
            if (_musicSync.value) setMusicSync(false)
            if (_songPlaying.value) stopSong()
            player.stop()
            val listener = object : SensorEventListener {
                var last = 0L
                var smooth = 0f
                override fun onSensorChanged(event: SensorEvent) {
                    val now = System.currentTimeMillis()
                    if (now - last < 60) return
                    last = now
                    val g = 9.81f
                    val delta = abs(
                        kotlin.math.sqrt(
                            event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]
                        ) - g
                    )
                    val target = min(1f, delta / 14f)
                    smooth = if (target > smooth) smooth + (target - smooth) * 0.7f
                    else smooth + (target - smooth) * 0.12f
                    val targets = resolveQuickTargets()
                    if (targets.isNotEmpty()) engine.setMany(targets.associateWith { smooth })
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sm.registerListener(
                listener,
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME
            )
            sensorListener = listener
            _toast.value = Str.s(R.string.vm_011)
        } else {
            sensorListener?.let { sm.unregisterListener(it) }
            sensorListener = null
            val targets = resolveQuickTargets()
            if (targets.isNotEmpty()) engine.setMany(targets.associateWith { 0f })
        }
    }

    // ------------------------------------------------------------- 音乐播放
    private var mediaPlayer: MediaPlayer? = null
    private var songJob: kotlinx.coroutines.Job? = null
    private var envelope: FloatArray = FloatArray(0)
    private var seekGuardUntil = 0L

    fun importSong(uri: Uri) {
        viewModelScope.launch {
            _songBusy.value = Str.s(R.string.vm_012)
            val song = withContext(kotlinx.coroutines.Dispatchers.IO) { library.import(uri) }
            if (song == null) {
                _songBusy.value = null
                _toast.value = Str.s(R.string.vm_013)
                return@launch
            }
            _songBusy.value = Str.s(R.string.vm_014)
            val started = System.currentTimeMillis()
            val env = library.analyze(song) { p ->
                _songBusy.value = Str.s(R.string.x_analyzing_pct, (p.coerceIn(0f, 1f) * 100).toInt())
            }
            val secs = (System.currentTimeMillis() - started) / 1000f
            _songBusy.value = null
            if (env.isEmpty()) {
                library.delete(song)
                _toast.value = Str.s(R.string.vm_015)
            } else {
                _toast.value = Str.s(R.string.x_import_done, song.title, "%.1f".format(secs))
            }
            _songs.value = library.list()
        }
    }

    fun importSongFromPath(path: String) {
        viewModelScope.launch {
            _songBusy.value = Str.s(R.string.vm_012)
            val song = withContext(kotlinx.coroutines.Dispatchers.IO) { library.importFromPath(path) }
            if (song == null) {
                _songBusy.value = null
                _toast.value = Str.s(R.string.vm_017)
                return@launch
            }
            _songBusy.value = Str.s(R.string.vm_014)
            val started = System.currentTimeMillis()
            val env = library.analyze(song) { p ->
                _songBusy.value = Str.s(R.string.x_analyzing_pct, (p.coerceIn(0f, 1f) * 100).toInt())
            }
            val secs = (System.currentTimeMillis() - started) / 1000f
            _songBusy.value = null
            if (env.isEmpty()) library.delete(song)
            _songs.value = library.list()
            _toast.value = if (env.isEmpty()) {
                Str.s(R.string.vm_018)
            } else {
                Str.s(R.string.x_import_done, song.title, "%.1f".format(secs))
            }
        }
    }

    fun deleteSong(song: com.pulse.app.core.Song) {
        if (_song.value?.id == song.id) stopSong()
        library.delete(song)
        _songs.value = library.list()
    }

    fun playSong(song: com.pulse.app.core.Song) {
        if (_shakeMode.value) setShakeMode(false)
        if (_musicSync.value) setMusicSync(false)
        player.stop()
        viewModelScope.launch {
            songJob?.cancel()
            releasePlayer()
            _songBusy.value = Str.s(R.string.vm_019)
            var env = library.envelope(song)
            if (env.isEmpty()) env = library.analyze(song)
            envelope = env
            _songBusy.value = null
            if (env.isEmpty()) {
                _toast.value = Str.s(R.string.vm_020)
                return@launch
            }
            val mp = MediaPlayer()
            try {
                mp.setDataSource(song.path)
                mp.prepare()
            } catch (e: Exception) {
                runCatching { mp.release() }
                _toast.value = Str.s(R.string.x_play_failed, e.message ?: "")
                return@launch
            }
            mp.setOnCompletionListener { stopSong() }
            mp.start()
            mediaPlayer = mp
            _song.value = song
            _songPlaying.value = true
            songJob = viewModelScope.launch { followSong(song) }
        }
    }

    private suspend fun followSong(song: com.pulse.app.core.Song) {
        while (_songPlaying.value) {
            val mp = mediaPlayer ?: break
            val now = System.currentTimeMillis()
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
            // seek 之后播放器上报的仍是旧位置，会把手柄弹回去；给一小段保护期
            if (now >= seekGuardUntil) _songPosition.value = pos
            val effective = if (now < seekGuardUntil) _songPosition.value else pos
            val idx = (effective / 50L).toInt()
            val v = if (idx in envelope.indices) envelope[idx] else 0f
            _songIntensity.value = v
            val keys = resolveSongTargets()
            if (keys.isNotEmpty()) engine.setMany(keys.associateWith { v })
            delay(50)
        }
    }

    /** 拖动进度条：ratio 0..1 */
    fun seekSong(ratio: Float) {
        val mp = mediaPlayer ?: return
        val s = _song.value ?: return
        if (s.durationMs <= 0) return
        val ms = (ratio.coerceIn(0f, 1f) * s.durationMs).toLong()
        runCatching { mp.seekTo(ms.toInt()) }
        seekGuardUntil = System.currentTimeMillis() + 400
        _songPosition.value = ms
        val idx = (ms / 50L).toInt()
        val v = if (idx in envelope.indices) envelope[idx] else 0f
        _songIntensity.value = v
        val keys = resolveSongTargets()
        if (keys.isNotEmpty()) engine.setMany(keys.associateWith { v })
    }

    fun toggleSong() {
        val mp = mediaPlayer ?: return
        if (_songPlaying.value) {
            mp.pause()
            _songPlaying.value = false
            _songIntensity.value = 0f
            val keys = resolveSongTargets()
            if (keys.isNotEmpty()) engine.setMany(keys.associateWith { 0f })
        } else {
            mp.start()
            _songPlaying.value = true
            songJob = viewModelScope.launch { followSong(_song.value ?: return@launch) }
        }
    }

    fun stopSong() {
        _songPlaying.value = false
        songJob?.cancel()
        songJob = null
        releasePlayer()
        _song.value = null
        _songPosition.value = 0L
        _songIntensity.value = 0f
        val keys = resolveSongTargets()
        if (keys.isNotEmpty()) engine.setMany(keys.associateWith { 0f })
    }

    private fun releasePlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    // ------------------------------------------------------------- 调试
    fun sendRaw(bytes: ByteArray) = ble.send(bytes)
    fun clearLog() = ble.clearLog()

    fun notify(text: String) {
        _toast.value = text
    }

    fun clearToast() {
        _toast.value = null
    }

    fun moduleOf(type: ModuleType): List<ModuleSpec> = engine.modules.value.filter { it.type == type }
}
