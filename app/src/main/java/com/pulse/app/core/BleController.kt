package com.pulse.app.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class ScanItem(
    val name: String,
    val address: String,
    val rssi: Int,
    val services: List<String> = emptyList(),
    val match: ToyMatch? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    /** 厂商自定义广播（0xNN -> HEX），给「设备特征收集」用 */
    val manufacturer: Map<Int, String> = emptyMap(),
    val serviceData: Map<String, String> = emptyMap(),
    val connectable: Boolean = true
)

sealed interface BleState {
    data object Idle : BleState
    data class Connecting(val name: String, val address: String) : BleState
    data class Ready(
        val name: String,
        val address: String,
        val mtu: Int,
        val service: String,
        val tx: String
    ) : BleState

    data class Failed(val reason: String) : BleState
}

data class LinkInfo(val service: String, val tx: String, val mtu: Int)

private const val MP_SERVICE = "00006000-0000-1000-8000-00805f9b34fb"
private const val MP_TX = "00006001-0000-1000-8000-00805f9b34fb"
private const val MP_TXMODE = "00006002-0000-1000-8000-00805f9b34fb"
private const val MP_TXVIBRATE = "00006003-0000-1000-8000-00805f9b34fb"
private const val MP_GENERIC0 = "0000600a-0000-1000-8000-00805f9b34fb"
private const val MP_RX_SERVICE = "00008000-0000-1000-8000-00805f9b34fb"
private const val MP_RX = "00008001-0000-1000-8000-00805f9b34fb"

/** 握手用的 4 把 15 字节密钥，来自 buttplug 的 monsterpub.rs */
private val MP_KEYS: List<ByteArray> = listOf("2IPO", "LSBB", "SIS6", "TALK").map { word ->
    ByteArray(15) { i -> word[i % 4].code.toByte() }
}

/**
 * 通用 GATT 客户端：不同品牌只是 service / tx / rx 不同，连接流程一致。
 * 写操作串行化 + 可选的 keepalive 重发（Svakom / Satisfyer 这类固件需要）。
 */
@SuppressLint("MissingPermission")
class BleController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val adapter: BluetoothAdapter? by lazy {
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ScanItem>>(emptyList())
    val devices: StateFlow<List<ScanItem>> = _devices.asStateFlow()

    private val _log = MutableStateFlow<List<TxLog>>(emptyList())
    val log: StateFlow<List<TxLog>> = _log.asStateFlow()

    private val _link = MutableStateFlow<LinkInfo?>(null)
    val link: StateFlow<LinkInfo?> = _link.asStateFlow()

    private val _lastNotify = MutableStateFlow<String?>(null)
    val lastNotify: StateFlow<String?> = _lastNotify.asStateFlow()

    /** 「设备特征收集」读到的 GATT 结构，连上再断开，不下发任何控制帧 */
    private val _dump = MutableStateFlow<String?>(null)
    val dump: StateFlow<String?> = _dump.asStateFlow()

    private var inspecting = false
    private var inspectJob: Job? = null

    /**
     * 只保留“最新一帧组”。拖动滑杆/声控时更新远快于蓝牙写入，
     * 用 CONFLATED 丢掉中间态，避免队列堆积导致停止按钮要等很久。
     */
    private val writeQueue = Channel<List<ByteArray>>(Channel.CONFLATED)
    private var writeAck: CompletableDeferred<Boolean>? = null

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var connectedAddress: String? = null
    private var connectedName: String? = null
    private var preferred: List<GattProfile> = emptyList()
    private var protocolId: String? = null

    // 小怪兽 MonsterPub：需要先读一段随机串、XOR 后写回，才能下发控制帧
    private var mpModeChar: BluetoothGattCharacteristic? = null
    private var mpUsesTx = false
    private var mpGeneric0 = false
    private var handshakeDone = CompletableDeferred<Boolean>().apply { complete(true) }
    private var readAck: CompletableDeferred<ByteArray?>? = null
    private var keepAliveJob: Job? = null
    private var lastPacket: ByteArray? = null
    private var keepAliveMs: Long = 0L
    private var isGalaku = false

    val isBluetoothOn: Boolean get() = adapter?.isEnabled == true
    val isSupported: Boolean get() = adapter != null

    init {
        scope.launch {
            for (frames in writeQueue) {
                for (bytes in frames) {
                    if (writeChar == null) break
                    writeOnce(bytes)
                }
            }
        }
    }

    private suspend fun writeOnce(bytes: ByteArray): Boolean {
        // 小怪兽的握手没做完之前，别把控制帧塞进去
        if (protocolId == "monsterpub") withTimeoutOrNull(3000) { handshakeDone.await() }
        val allZero = run {
            val from = if (mpGeneric0 && bytes.isNotEmpty() && bytes[0] == 0x03.toByte()) 1 else 0
            (from until bytes.size).all { bytes[it] == 0.toByte() }
        }
        val ch = (if (mpUsesTx && allZero && mpModeChar != null) mpModeChar else writeChar)
            ?: return false
        val g = gatt ?: return false
        val needsAck = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val writeType = if (needsAck) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val ack = CompletableDeferred<Boolean>()
        writeAck = ack
        val sent = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, writeType) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = writeType
                    ch.value = bytes
                    g.writeCharacteristic(ch)
                }
            }
        } catch (e: Exception) {
            false
        }
        val ok = sent && if (needsAck) {
            withTimeoutOrNull(320) { ack.await() } == true
        } else {
            true
        }
        addLog(TxLog.Dir.TX, bytes, if (ok) "" else "写失败")
        if (ok) lastPacket = bytes
        return ok
    }

    fun addLog(dir: TxLog.Dir, bytes: ByteArray, note: String = "", raw: ByteArray? = null) {
        val entry = TxLog(dir, System.currentTimeMillis(), bytes, note, raw)
        _log.value = (_log.value + entry).takeLast(400)
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    // ------------------------------------------------------------------ scan
    // 扫描与连接是两件独立的事：以前共用 _state，一点「重新扫描」就会把 Ready
    // 覆盖成 Scanning，控制页立刻变「未连接」；扫描失败再变 Failed，引擎还会被切回演示模式。
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    fun clearScanError() {
        _scanError.value = null
    }

    private var scanRetryUsed = false
    private var scanRetryJob: Job? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scanRetryUsed = false
            _scanError.value = null
            val record = result.scanRecord
            val name = result.device.name ?: record?.deviceName ?: return
            if (name.isBlank()) return
            val uuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            val match = ToyCatalog.match(name, uuids)
            val manufacturer = HashMap<Int, String>()
            record?.manufacturerSpecificData?.let { arr ->
                for (i in 0 until arr.size()) manufacturer[arr.keyAt(i)] = hexBytes(arr.valueAt(i))
            }
            val serviceData = record?.serviceData
                ?.entries
                ?.associate { it.key.uuid.toString() to hexBytes(it.value) }
                ?: emptyMap()
            val item = ScanItem(
                name = name,
                address = result.device.address,
                rssi = result.rssi,
                services = uuids,
                match = match,
                manufacturer = manufacturer,
                serviceData = serviceData,
                connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.isConnectable
                } else {
                    true
                }
            )
            val list = _devices.value.toMutableList()
            val idx = list.indexOfFirst { it.address == item.address }
            if (idx >= 0) list[idx] = item else list.add(item)
            list.sortWith(
                compareByDescending<ScanItem> { it.match != null }
                    .thenByDescending { it.rssi }
            )
            _devices.value = list
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            addLog(TxLog.Dir.SYS, ByteArray(0), "扫描启动失败 errorCode=$errorCode")
            // 1 = SCAN_FAILED_ALREADY_STARTED：系统里还留着上一次的扫描注册。
            // 彻底停一次再重启，避免注册残留导致以后每次扫描都失败、只能杀进程重开。
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED && !scanRetryUsed) {
                scanRetryUsed = true
                scanRetryJob?.cancel()
                scanRetryJob = scope.launch {
                    delay(200)
                    if (!isActive) return@launch
                    stopScanInternal()
                    startScanInternal()
                    addLog(TxLog.Dir.SYS, ByteArray(0), "扫描已自动重启")
                }
                return
            }
            _scanError.value = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描没能重启，再点一次试试"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "蓝牙扫描没注册上，开关一次蓝牙再试"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "蓝牙出了点问题，开关一次蓝牙再试"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "这台手机不支持蓝牙扫描"
                ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙太忙了，稍等几秒再试"
                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "扫描太频繁，等几秒再试"
                else -> "扫描没启动成功，再点一次试试"
            }
        }
    }

    fun startScan() {
        // 已经在扫描时，「重新扫描」只要清空列表重新收集结果即可，别去停掉再重新注册。
        // 每次重新注册都会占用 Android 的扫描配额，短时间内多点几次就会被判为扫描过频。
        // 连接时 connect() 会 stopScan() 把 _scanning 置 false，
        // 所以「连接成功后点重新扫描」走的是下面的完整重启路径，不受影响。
        if (_scanning.value) {
            _devices.value = emptyList()
            return
        }
        scanRetryJob?.cancel()
        scanRetryJob = null
        scanRetryUsed = false
        startScanInternal()
    }

    private fun startScanInternal() {
        val a = adapter ?: run {
            _scanning.value = false
            _scanError.value = "这台设备没有蓝牙"
            return
        }
        // 关键：每次启动前无条件停一次，清掉系统侧可能残留的扫描注册。
        // 否则重复 startScan 会回调 SCAN_FAILED_ALREADY_STARTED(1) 并且一直卡住。
        stopScanInternal()
        if (!a.isEnabled) {
            _scanError.value = "蓝牙还没打开"
            return
        }
        _devices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            val scanner = a.bluetoothLeScanner
            if (scanner == null) {
                _scanError.value = "蓝牙还没准备好，稍后再试"
                return
            }
            scanner.startScan(null, settings, scanCallback)
            _scanning.value = true
            _scanError.value = null
        } catch (e: Exception) {
            _scanning.value = false
            _scanError.value = "扫描没启动成功，再点一次试试"
        }
    }

    fun stopScan() {
        scanRetryJob?.cancel()
        scanRetryJob = null
        scanRetryUsed = false
        stopScanInternal()
    }

    private fun stopScanInternal() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _scanning.value = false
    }

    // --------------------------------------------------------------- connect

    /** 每次发起连接自增。取消/重连后，上一轮的 connectGatt 回调据此作废。 */
    private var connectGen = 0

    fun connect(item: ScanItem, protocol: ProtocolSpec?, inspectOnly: Boolean = false) {
        val a = adapter ?: return
        if (_state.value is BleState.Connecting) return
        stopScan()
        closeGatt()
        inspecting = inspectOnly
        _dump.value = null
        val gen = ++connectGen
        _state.value = BleState.Connecting(item.name, item.address)
        connectedAddress = item.address
        connectedName = item.name
        preferred = protocol?.profiles.orEmpty()
        protocolId = protocol?.id
        keepAliveMs = protocol?.keepAliveMs ?: 0
        isGalaku = protocol?.id == "galaku"
        mpModeChar = null
        mpUsesTx = false
        mpGeneric0 = false
        handshakeDone = CompletableDeferred()
        if (inspectOnly) {
            // 只读结构，连不上也别干等
            inspectJob?.cancel()
            inspectJob = scope.launch {
                delay(12_000)
                if (gen == connectGen && inspecting) {
                    finishInspect()
                    _state.value = BleState.Failed("读取超时：设备没有回应")
                }
            }
        }
        val device: BluetoothDevice = try {
            a.getRemoteDevice(item.address)
        } catch (e: Exception) {
            _state.value = BleState.Failed("地址无效")
            return
        }
        // 上一次的 GATT 刚 close，等一拍再连，否则部分手机栈会返回一个死连接
        scope.launch {
            delay(180)
            if (gen != connectGen || _state.value !is BleState.Connecting) return@launch
            val created = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(appContext, false, gattCallback)
                }
            } catch (e: Exception) {
                if (gen == connectGen) _state.value = BleState.Failed("连接失败：${e.message ?: "未知错误"}")
                return@launch
            }
            // 这 180ms 里用户可能已经点了取消
            if (gen != connectGen) {
                runCatching {
                    created.disconnect()
                    created.close()
                }
                return@launch
            }
            gatt = created
        }
    }

    /** 连接中手动取消：不用干等超时 */
    fun cancelConnect() {
        if (_state.value !is BleState.Connecting) return
        connectGen++
        inspectJob?.cancel()
        inspectJob = null
        inspecting = false
        closeGatt()
        connectedAddress = null
        connectedName = null
        _state.value = BleState.Idle
        addLog(TxLog.Dir.SYS, ByteArray(0), "已取消连接")
    }

    /**
     * 只连接、读一遍 service / characteristic 结构就断开。
     * 给「设备特征收集」用：未收录的设备靠这份结构判断协议，全程不发控制帧。
     */
    fun inspect(item: ScanItem) {
        connect(item, null, inspectOnly = true)
    }

    private fun finishInspect() {
        inspecting = false
        inspectJob?.cancel()
        inspectJob = null
    }

    /** 读不了 GATT（没蓝牙 / 没开蓝牙）时给 UI 一个明确失败，好退回广播信息 */
    fun failInspect(reason: String) {
        finishInspect()
        _state.value = BleState.Failed(reason)
    }

    private fun describeGatt(g: BluetoothGatt): String = buildString {
        val services = g.services ?: emptyList()
        appendLine("services=${services.size}")
        services.forEach { s ->
            appendLine("service ${s.uuid}")
            s.characteristics.forEach { c ->
                appendLine("  char ${c.uuid} [${propertyNames(c.properties)}]")
                c.descriptors?.forEach { d -> appendLine("    desc ${d.uuid}") }
            }
        }
    }

    private fun propertyNames(props: Int): String {
        val out = mutableListOf<String>()
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) out += "READ"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out += "WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out += "WRITE_NO_RSP"
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out += "NOTIFY"
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out += "INDICATE"
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) out += "SIGNED_WRITE"
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) out += "BROADCAST"
        return if (out.isEmpty()) "NONE" else out.joinToString("|")
    }

    fun updateKeepAlive(ms: Long) {
        keepAliveMs = ms
        startKeepAlive()
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeChar = null
                notifyChar = null
                val wasInspecting = inspecting
                if (wasInspecting) finishInspect()
                if (_state.value is BleState.Ready || _state.value is BleState.Connecting) {
                    _state.value = BleState.Failed(
                        if (wasInspecting) "设备拒绝连接，只能靠广播信息判断" else "连接已断开"
                    )
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (inspecting) finishInspect()
                _state.value = BleState.Failed("服务发现失败 ($status)")
                return
            }
            // 特征收集：拿到结构就够了，不发任何控制帧，直接断开
            if (inspecting) {
                _dump.value = describeGatt(g)
                addLog(TxLog.Dir.SYS, ByteArray(0), "已读取 GATT 结构")
                scope.launch {
                    delay(150)
                    if (!inspecting) return@launch
                    finishInspect()
                    closeGatt()
                    _state.value = BleState.Idle
                }
                return
            }
            try {
                g.requestMtu(517)
            } catch (e: Exception) {
                _state.value = BleState.Failed("请求 MTU 失败")
                return
            }
            scope.launch {
                delay(1600)
                if (_state.value is BleState.Connecting) bind(g, 23)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            bind(g, mtu)
        }

        private fun bind(g: BluetoothGatt, mtu: Int) {
            if (g !== gatt || _state.value !is BleState.Connecting) return
            try {
                bindInternal(g, mtu)
            } catch (e: Exception) {
                _state.value = BleState.Failed("初始化失败：${e.message ?: "未知错误"}")
            }
        }

        private fun bindInternal(g: BluetoothGatt, mtu: Int) {
            val all = g.services ?: emptyList()
            var service: android.bluetooth.BluetoothGattService? = null
            var tx: BluetoothGattCharacteristic? = null
            var rx: BluetoothGattCharacteristic? = null

            for (pref in preferred) {
                val s = all.firstOrNull { it.uuid.toString().equals(pref.service, true) }
                    ?: continue
                val c = s.getCharacteristic(UUID.fromString(pref.tx)) ?: continue
                service = s
                tx = c
                if (pref.rx != null) rx = s.getCharacteristic(UUID.fromString(pref.rx))
                break
            }
            if (tx == null) {
                outer@ for (s in all) {
                    for (c in s.characteristics) {
                        val p = c.properties
                        if (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                        ) {
                            service = s
                            tx = c
                            rx = s.characteristics.firstOrNull {
                                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                            }
                            break@outer
                        }
                    }
                }
            }

            // 小怪兽的写端点不止一个：优先 txvibrate，其次 tx（全停时改写到 txmode），
            // 都没有时用 generic0（Tracy's Dog 这类机型）
            if (protocolId == "monsterpub") {
                val mp = all.firstOrNull {
                    it.uuid.toString().equals(MP_SERVICE, ignoreCase = true)
                }
                if (mp != null) {
                    val vib = mp.getCharacteristic(UUID.fromString(MP_TXVIBRATE))
                    val main = mp.getCharacteristic(UUID.fromString(MP_TX))
                    val gen = mp.getCharacteristic(UUID.fromString(MP_GENERIC0))
                    mpModeChar = mp.getCharacteristic(UUID.fromString(MP_TXMODE))
                    when {
                        vib != null -> { service = mp; tx = vib }
                        main != null -> { service = mp; tx = main; mpUsesTx = true }
                        gen != null -> { service = mp; tx = gen; mpGeneric0 = true }
                    }
                }
            }

            if (service == null || tx == null) {
                _state.value = BleState.Failed("找到设备，但没有可写特征值")
                return
            }

            writeChar = tx
            notifyChar = rx ?: service.characteristics.firstOrNull {
                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            }
            notifyChar?.let { nc ->
                g.setCharacteristicNotification(nc, true)
                nc.descriptors?.forEach { d ->
                    d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(d)
                }
            }
            _link.value = LinkInfo(service.uuid.toString(), tx.uuid.toString(), mtu)
            _state.value = BleState.Ready(
                name = connectedName ?: "device",
                address = connectedAddress ?: "",
                mtu = mtu,
                service = service.uuid.toString(),
                tx = tx.uuid.toString()
            )
            addLog(TxLog.Dir.SYS, ByteArray(0), "已就绪 MTU=$mtu  tx=${tx.uuid}")
            startKeepAlive()
            if (protocolId == "monsterpub") {
                scope.launch { runMonsterPubHandshake(g) }
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
            writeAck = null
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            handleRead(if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleRead(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleNotify(characteristic.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotify(value)
        }
    }

    private fun handleRead(value: ByteArray?) {
        readAck?.complete(value)
    }

    private fun handleNotify(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val plain = if (isGalaku && value.size >= 12) GalakuProtocol.decrypt(value) else value
        _lastNotify.value = if (isGalaku) GalakuProtocol.hex(value) else String(value)
        addLog(TxLog.Dir.RX, plain, "notify", value)
    }

    /**
     * 小怪兽的认证：读 00008001 拿到 [keyIndex, 15 字节挑战]，
     * 用对应密钥逐字节 XOR 后写回，之后设备才接受振动指令。
     */
    private suspend fun runMonsterPubHandshake(g: BluetoothGatt): Boolean = try {
        val ch = g.getService(UUID.fromString(MP_RX_SERVICE))
            ?.getCharacteristic(UUID.fromString(MP_RX))
        val value = if (ch == null) null else readValue(g, ch, 1800)
        val idx = value?.firstOrNull()?.toInt()?.and(0xFF)
        val key = idx?.let { MP_KEYS.getOrNull(it) }
        if (value != null && value.size >= 16 && key != null) {
            val auth = ByteArray(15) { i ->
                ((value[i + 1].toInt() xor key[i].toInt()) and 0xFF).toByte()
            }
            val ok = writeTo(g, ch!!, auth)
            addLog(TxLog.Dir.SYS, ByteArray(0), "MonsterPub 握手 key=$idx ${if (ok) "成功" else "写入失败"}")
            ok
        } else {
            addLog(TxLog.Dir.SYS, ByteArray(0), "MonsterPub 握手跳过（读不到挑战串）")
            false
        }
    } catch (e: Exception) {
        addLog(TxLog.Dir.SYS, ByteArray(0), "MonsterPub 握手异常：${e.message}")
        false
    } finally {
        // 无论成败都放行，避免把控制卡死
        if (!handshakeDone.isCompleted) handshakeDone.complete(true)
    }

    private suspend fun readValue(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        timeoutMs: Long
    ): ByteArray? {
        val ack = CompletableDeferred<ByteArray?>()
        readAck = ack
        return try {
            val started = try {
                g.readCharacteristic(ch)
            } catch (e: Exception) {
                false
            }
            if (started) withTimeoutOrNull(timeoutMs) { ack.await() } else null
        } finally {
            readAck = null
        }
    }

    /** 握手要写到另一个特征值，所以不走 writeOnce 的队列 */
    private suspend fun writeTo(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        bytes: ByteArray
    ): Boolean {
        val ack = CompletableDeferred<Boolean>()
        writeAck = ack
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = bytes
                    g.writeCharacteristic(ch)
                }
            }
        } catch (e: Exception) {
            false
        }
        addLog(TxLog.Dir.TX, bytes, "auth")
        return started && withTimeoutOrNull(700) { ack.await() } == true
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        if (keepAliveMs <= 0) return
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(keepAliveMs)
                val p = lastPacket ?: continue
                if (writeChar != null) writeOnce(p)
            }
        }
    }

    fun send(bytes: ByteArray) = sendSet(listOf(bytes))

    /** 一次提交一组帧（同一状态对应的多帧），只保留最新一组 */
    fun sendSet(frames: List<ByteArray>) {
        if (frames.isEmpty()) return
        if (writeChar == null) {
            frames.forEach { addLog(TxLog.Dir.TX, it, "未连接 · 仅记录") }
            return
        }
        writeQueue.trySend(frames)
    }

    private fun closeGatt() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        writeChar = null
        notifyChar = null
        mpModeChar = null
        mpUsesTx = false
        mpGeneric0 = false
        readAck = null
        if (!handshakeDone.isCompleted) handshakeDone.complete(false)
        lastPacket = null
        _link.value = null
    }

    fun disconnect() {
        connectGen++
        finishInspect()
        closeGatt()
        connectedAddress = null
        connectedName = null
        _state.value = BleState.Idle
        addLog(TxLog.Dir.SYS, ByteArray(0), "已断开")
    }
}

private fun hexBytes(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
