package com.pulse.app

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val library = com.pulse.app.core.MusicLibrary(app)
    private val store = PatternStore(app)

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

    private val _savedToys = MutableStateFlow(memory.all())
    val savedToys: StateFlow<List<ToyRecord>> = _savedToys.asStateFlow()

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
                            "已连接 · 恢复「${rec.name}」的 ${rec.modules.size} 个模块"
                        } else {
                            "已连接 ${st.name}"
                        }
                    }

                    is BleState.Failed -> {
                        engine.setDemo(true)
                        _toast.value = st.reason
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
                    _toast.value = "麦克风不可用，检查权限或是否被其它应用占用"
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
    }

    fun resolveVoiceTargets(): List<String> {
        val all = engine.modules.value.map { it.key }.toSet()
        return _voiceTargets.value.filter { it in all }
    }

    fun setVoiceTargets(keys: Set<String>) {
        _voiceTargets.value = keys
    }

    fun resolveSongTargets(): List<String> {
        val all = engine.modules.value.map { it.key }.toSet()
        return _songTargets.value.filter { it in all }
    }

    fun setSongTargets(keys: Set<String>) {
        _songTargets.value = keys
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

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    fun connect(item: ScanItem, protocolOverride: ProtocolSpec? = null) {
        val match = item.match
        val protocol = protocolOverride ?: match?.protocol
        if (protocol == null) {
            _toast.value = "未识别品牌，请手动指定协议"
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
        _quickTargets.value = defaultQuickTargets(modules)
        _voiceTargets.value = defaultQuickTargets(modules)
        _songTargets.value = defaultQuickTargets(modules)
        engine.setDemo(false)
        ble.connect(item, protocol)
        if (!usable) {
            memory.save(_activeRecord.value!!)
            _savedToys.value = memory.all()
        }
    }

    /** 没设备时载入一台虚拟 Galaku 双电机，用于预览界面与波形 */
    fun loadDemo() {
        val protocol = ToyCatalog.find("galaku")
        android.util.Log.d("PulseDemo", "loadDemo protocol=$protocol")
        if (protocol == null) return
        val modules = listOf(
            ModuleSpec(ModuleType.VIBRATE, 0, "振动 A", 100),
            ModuleSpec(ModuleType.VIBRATE, 1, "振动 B", 100)
        )
        _activeRecord.value = null
        engine.configure(protocol, null, modules, "DEMO", emptyMap())
        _quickTargets.value = defaultQuickTargets(modules)
        _voiceTargets.value = defaultQuickTargets(modules)
        _songTargets.value = defaultQuickTargets(modules)
        engine.setDemo(true)
        _toast.value = "模拟设备已载入（不会真的驱动）"
    }

    fun disconnect() {
        persistNow()
        ble.disconnect()
        player.stop()
        music.stop()
        _musicSync.value = false
        if (_shakeMode.value) setShakeMode(false)
        engine.setDemo(true)
        engine.configure(null, null, emptyList(), null)
        _quickTargets.value = emptySet()
        _activeRecord.value = null
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
        _quickTargets.value = defaultQuickTargets(modules)
        _voiceTargets.value = defaultQuickTargets(modules)
        _songTargets.value = defaultQuickTargets(modules)
        memory.save(newRec)
        _savedToys.value = memory.all()
        _toast.value = "已切换协议：${protocol.brand} · ${protocol.display}"
    }

    /** 手动改模块列表（未知型号 / 检测不全） */
    fun updateModules(modules: List<ModuleSpec>) {
        val rec = _activeRecord.value ?: return
        if (modules.isEmpty()) return
        val newRec = rec.copy(modules = modules, updatedAt = System.currentTimeMillis())
        _activeRecord.value = newRec
        engine.configure(engine.protocol.value, engine.model.value, modules, rec.name, rec.values)
        _quickTargets.value = defaultQuickTargets(modules)
        _voiceTargets.value = defaultQuickTargets(modules)
        _songTargets.value = defaultQuickTargets(modules)
        memory.save(newRec)
        _savedToys.value = memory.all()
        _toast.value = "模块已保存：${modules.size} 个"
    }

    fun deleteToy(address: String) {
        memory.forget(address)
        _savedToys.value = memory.all()
        if (_activeRecord.value?.address == address) _activeRecord.value = null
    }

    fun clearAllToys() {
        memory.clear()
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
        player.play(p, scale, targetKeys, sync)
    }

    fun togglePattern() = player.toggle()
    fun stopPattern() = player.stop()
    fun setPatternScale(v: Float) = player.setScale(v)
    fun setPatternTarget(keys: List<String>, sync: Boolean) = player.setTargets(keys, sync)

    fun saveCustom(name: String, durationMs: Int, trace: List<Pair<Float, Float>>): Pattern {
        val p = PresetLibrary.fromTrace(name, durationMs, trace)
        val list = _customPatterns.value + p
        _customPatterns.value = list
        store.save(list)
        _toast.value = "已保存「$name」"
        return p
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
            _toast.value = "麦克风律动已开启"
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
            _toast.value = "摇一摇：晃起来"
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

    fun importSong(uri: Uri) {
        viewModelScope.launch {
            _songBusy.value = "正在导入…"
            val song = withContext(kotlinx.coroutines.Dispatchers.IO) { library.import(uri) }
            if (song == null) {
                _songBusy.value = null
                _toast.value = "导入失败"
                return@launch
            }
            _songBusy.value = "正在分析音乐…"
            val env = library.analyze(song)
            _songBusy.value = null
            if (env.isEmpty()) {
                library.delete(song)
                _toast.value = "这首歌没法分析，可能是不支持的格式"
            } else {
                _toast.value = "已导入《${song.title}》"
            }
            _songs.value = library.list()
        }
    }

    fun importSongFromPath(path: String) {
        viewModelScope.launch {
            _songBusy.value = "正在导入…"
            val song = withContext(kotlinx.coroutines.Dispatchers.IO) { library.importFromPath(path) }
            if (song == null) {
                _songBusy.value = null
                _toast.value = "找不到文件"
                return@launch
            }
            _songBusy.value = "正在分析音乐…"
            val env = library.analyze(song)
            _songBusy.value = null
            if (env.isEmpty()) library.delete(song)
            _songs.value = library.list()
            _toast.value = if (env.isEmpty()) "分析失败" else "已导入《${song.title}》"
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
            _songBusy.value = "正在准备…"
            var env = library.envelope(song)
            if (env.isEmpty()) env = library.analyze(song)
            envelope = env
            _songBusy.value = null
            if (env.isEmpty()) {
                _toast.value = "这首歌没有可用的分析结果"
                return@launch
            }
            val mp = MediaPlayer()
            try {
                mp.setDataSource(song.path)
                mp.prepare()
            } catch (e: Exception) {
                runCatching { mp.release() }
                _toast.value = "播放失败：${e.message}"
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
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
            _songPosition.value = pos
            val idx = (pos / 50L).toInt()
            val v = if (idx in envelope.indices) envelope[idx] else 0f
            _songIntensity.value = v
            val keys = resolveSongTargets()
            if (keys.isNotEmpty()) engine.setMany(keys.associateWith { v })
            delay(50)
        }
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
