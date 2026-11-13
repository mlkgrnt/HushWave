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
    val lastSeen: Long = System.currentTimeMillis()
)

sealed interface BleState {
    data object Idle : BleState
    data object Scanning : BleState
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
    private var preferred: GattProfile? = null
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
        val ch = writeChar ?: return false
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
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = result.device.name ?: record?.deviceName ?: return
            if (name.isBlank()) return
            val uuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            val match = ToyCatalog.match(name, uuids)
            val item = ScanItem(name, result.device.address, result.rssi, uuids, match)
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
            _state.value = BleState.Failed("扫描失败，错误码 $errorCode")
        }
    }

    fun startScan() {
        val a = adapter ?: run {
            _state.value = BleState.Failed("此设备没有蓝牙适配器")
            return
        }
        if (!a.isEnabled) {
            _state.value = BleState.Failed("蓝牙未开启")
            return
        }
        _devices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            a.bluetoothLeScanner?.startScan(null, settings, scanCallback)
            _state.value = BleState.Scanning
        } catch (e: Exception) {
            _state.value = BleState.Failed(e.message ?: "无法开始扫描")
        }
    }

    fun stopScan() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        if (_state.value is BleState.Scanning) _state.value = BleState.Idle
    }

    // --------------------------------------------------------------- connect
    fun connect(item: ScanItem, protocol: ProtocolSpec?) {
        val a = adapter ?: return
        if (_state.value is BleState.Connecting) return
        stopScan()
        closeGatt()
        _state.value = BleState.Connecting(item.name, item.address)
        connectedAddress = item.address
        connectedName = item.name
        preferred = protocol?.profiles?.firstOrNull()
        keepAliveMs = protocol?.keepAliveMs ?: 0
        isGalaku = protocol?.id == "galaku"
        val device: BluetoothDevice = try {
            a.getRemoteDevice(item.address)
        } catch (e: Exception) {
            _state.value = BleState.Failed("地址无效")
            return
        }
        // 上一次的 GATT 刚 close，等一拍再连，否则部分手机栈会返回一个死连接
        scope.launch {
            delay(180)
            if (_state.value !is BleState.Connecting) return@launch
            try {
                gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(appContext, false, gattCallback)
                }
            } catch (e: Exception) {
                _state.value = BleState.Failed("连接失败：${e.message ?: "未知错误"}")
            }
        }
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
                if (_state.value is BleState.Ready || _state.value is BleState.Connecting) {
                    _state.value = BleState.Failed("连接已断开")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = BleState.Failed("服务发现失败 ($status)")
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

            val pref = preferred
            if (pref != null) {
                val s = all.firstOrNull { it.uuid.toString().equals(pref.service, true) }
                val c = s?.getCharacteristic(UUID.fromString(pref.tx))
                if (s != null && c != null) {
                    service = s
                    tx = c
                    if (pref.rx != null) rx = s.getCharacteristic(UUID.fromString(pref.rx))
                }
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

    private fun handleNotify(value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val plain = if (isGalaku && value.size >= 12) GalakuProtocol.decrypt(value) else value
        _lastNotify.value = if (isGalaku) GalakuProtocol.hex(value) else String(value)
        addLog(TxLog.Dir.RX, plain, "notify", value)
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
        lastPacket = null
        _link.value = null
    }

    fun disconnect() {
        closeGatt()
        connectedAddress = null
        connectedName = null
        _state.value = BleState.Idle
        addLog(TxLog.Dir.SYS, ByteArray(0), "已断开")
    }
}
