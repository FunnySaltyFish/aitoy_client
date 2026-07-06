package com.funny.aitoy.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.funny.aitoy.core.platform.AndroidPlatformInit
import com.funny.aitoy.diagnostics.AiToyTraceEvent
import com.funny.aitoy.diagnostics.AiToyTraceUploadPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleController(
    private val onDevice: (ScannedBleDevice) -> Unit,
    private val onState: (BleConnectionState) -> Unit,
    private val onProtocol: (BleProtocolStatus) -> Unit,
    private val onProtocolAttempt: (ProtocolAttemptStatus) -> Unit,
    private val onBatteryPercent: (Int?) -> Unit = {},
    private val onLog: (String) -> Unit,
    private val onTrace: (AiToyTraceEvent) -> Unit,
) {
    private val context: Context = AndroidPlatformInit.appContext
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gatt: BluetoothGatt? = null
    private var template: ProtocolTemplate? = null
    private var connectedName = ""
    private var connectedAddress = ""
    private var connectedManufacturerData = ""
    private var connectedScanRecordHex = ""
    private var activeProtocol: BleDeviceProtocol? = null
    private var activeBroadcastProtocol: BleBroadcastProtocol? = null
    private var protocolReady = false
    private var gattReadyAtMs = 0L
    private var protocolCandidates = emptyList<BleDeviceProtocol>()
    private var broadcastProtocolCandidates = emptyList<BleBroadcastProtocol>()
    private var protocolCandidateIndex = -1
    private val failedProtocolNames = mutableListOf<String>()
    private var disconnectingAfterProtocolFailure = false
    private var resolvedWriteCharacteristic: BluetoothGattCharacteristic? = null
    private val operationQueue = ArrayDeque<BleProtocolOperation>()
    private var operationInProgress = false
    private var waitingForProtocolReady = false
    private var protocolReadyTimeoutRunnable: Runnable? = null
    private var operationId = 0L
    // 连接是否曾真正建立(到达 STATE_CONNECTED)。未建立的 pending 连接必须 close() 而不是 disconnect()，
    // 否则 Android 会以 status=22 本地终止并泄漏 GATT client 槽，导致后续 connectGatt 静默挂起。
    private var gattEverConnected = false
    // 连接看门狗：connectGatt 后若在超时内未建立，强制 close() 回收 client 槽并报错，避免 11s+ 静默挂起。
    private var connectWatchdogRunnable: Runnable? = null
    private var keepaliveRunnable: Runnable? = null
    private var keepaliveProtocol: BleDeviceProtocol? = null
    private var keepaliveOperations: List<BleProtocolOperation> = emptyList()
    private var writeBusyRetryCount = 0
    private var batteryReadRequested = false
    private val scanSignatures = mutableMapOf<String, String>()
    private val advertiser = AndroidBleAdvertiser(::trace)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = runCatching { device.name }.getOrNull()
                ?: result.scanRecord?.deviceName
                ?: "未命名设备"
            val record = result.scanRecord
            val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else {
                true
            }
            val scannedDevice = ScannedBleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                connectable = connectable,
                serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
                manufacturerData = record.manufacturerDataText(),
                scanRecordHex = record?.bytes?.toHexString().orEmpty(),
            ).let { it.copy(broadcastProtocolName = BleBroadcastProtocolRegistry.resolveFirstName(it)) }
            mainHandler.post {
                onDevice(scannedDevice)
            }
            val signature = buildString {
                append("name=$name address=${device.address} connectable=$connectable")
                append(" services=${record?.serviceUuids?.joinToString { it.uuid.toString() } ?: "<none>"}")
                append(" manufacturer=")
                val manufacturerData = record?.manufacturerSpecificData
                if (manufacturerData == null || manufacturerData.size() == 0) {
                    append("<none>")
                } else {
                    append(record.manufacturerDataText())
                }
                append(" record=${record?.bytes?.toHexString() ?: "<none>"}")
            }
            if (scanSignatures.put(device.address, signature) != signature) {
                trace("扫描结果 $signature rssi=${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            trace("扫描失败 errorCode=$errorCode", error = true)
            updateState(BleConnectionState.Error)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val isConnected = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
            trace(
                "连接状态回调 status=$status newState=$newState address=${gatt.device.address}",
                // 非正常连接（含 status=133/19/8 等错误码、以及被动断开）也必须上传，
                // 否则排查“连一个后连第二个失败/掉线”时看不到真实 status 码。
                error = !isConnected && newState == BluetoothProfile.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS,
                type = if (isConnected) "ble_gatt_connected" else "ble_gatt_state",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_gatt_state:$operationId:$status:$newState",
            )
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gattEverConnected = true
                    cancelConnectWatchdog()
                    updateState(BleConnectionState.Discovering)
                    val started = gatt.discoverServices()
                    trace("开始发现 GATT 服务 result=$started")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    trace(
                        "设备已断开 status=$status address=${gatt.device.address} " +
                            "afterProtocolFailure=$disconnectingAfterProtocolFailure",
                        // 断开事件必须上传：区分主动断开(GATT_SUCCESS)与被动掉线/错误(133/19/8)，
                        // 是排查多设备“连一个掉一个”的关键证据。
                        error = status != BluetoothGatt.GATT_SUCCESS,
                        type = "ble_gatt_disconnected",
                        uploadPolicy = AiToyTraceUploadPolicy.Always,
                        key = "ble_gatt_disconnected:$operationId:$status",
                    )
                    cancelConnectWatchdog()
                    closeGatt(gatt)
                    val finalState = if (disconnectingAfterProtocolFailure) {
                        BleConnectionState.Error
                    } else if (status == BluetoothGatt.GATT_SUCCESS) {
                        BleConnectionState.Idle
                    } else {
                        BleConnectionState.Error
                    }
                    disconnectingAfterProtocolFailure = false
                    updateState(finalState)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            trace(
                "服务发现完成 status=$status serviceCount=${gatt.services.size}",
                error = status != BluetoothGatt.GATT_SUCCESS,
                type = "ble_services_discovered",
                uploadPolicy = if (status == BluetoothGatt.GATT_SUCCESS) {
                    AiToyTraceUploadPolicy.Always
                } else {
                    AiToyTraceUploadPolicy.Always
                },
                key = "ble_services_discovered:$operationId",
            )
            gatt.services.forEach { service ->
                trace("服务 uuid=${service.uuid} characteristicCount=${service.characteristics.size}")
                service.characteristics.forEach { characteristic ->
                    trace(
                        "特征 uuid=${characteristic.uuid} properties=0x" +
                            characteristic.properties.toString(16) +
                            " ${characteristic.propertiesText()} descriptors=" +
                            characteristic.descriptors.joinToString { descriptor -> descriptor.uuid.toString() }.ifBlank { "<none>" },
                    )
                }
            }
            if (status == BluetoothGatt.GATT_SUCCESS && shouldUploadGattDiscoverySummary()) {
                trace(
                    "GATT 能力摘要 name=$connectedName address=${gatt.device.address} " +
                        "services=${gatt.gattDiscoverySummary()}",
                    type = "ble_gatt_discovery_summary",
                    uploadPolicy = AiToyTraceUploadPolicy.Always,
                    key = "ble_gatt_discovery_summary:$operationId",
                )
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState(BleConnectionState.Error)
                return
            }
            val config = template ?: return
            val fingerprint = BleGattFingerprint(
                name = connectedName,
                address = connectedAddress,
                manufacturerData = connectedManufacturerData,
                scanRecordHex = connectedScanRecordHex,
                serviceUuids = gatt.services.map { it.uuid.toKotlinUuid() }.toSet(),
                characteristicUuids = gatt.services
                    .flatMap { it.characteristics }
                    .map { it.uuid.toKotlinUuid() }
                    .toSet(),
                preferredServiceUuid = config.serviceUuid.toKotlinUuidOrNull(),
                preferredWriteUuid = config.writeUuid.toKotlinUuidOrNull(),
                preferredNotifyUuid = config.notifyUuid.toKotlinUuidOrNull(),
            )
            val discoveryOperationId = operationId
            val discoveredGatt = gatt
            scope.launch {
                if (this@AndroidBleController.gatt !== discoveredGatt || operationId != discoveryOperationId) return@launch
                protocolCandidates = BleProtocolRegistry.resolveAll(fingerprint)
                protocolCandidateIndex = -1
                failedProtocolNames.clear()
                if (protocolCandidates.isNotEmpty()) {
                    trace(
                        "找到 ${protocolCandidates.size} 个候选协议，开始依次确认",
                        type = "ble_protocol_candidates",
                        uploadPolicy = AiToyTraceUploadPolicy.Always,
                        key = "ble_protocol_candidates:$operationId",
                    )
                    tryNextProtocol(fingerprint, discoveredGatt, config)
                    return@launch
                }
                finishWithManualOrUnsupported(discoveredGatt, config)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            trace("写入回调 uuid=${characteristic.uuid} status=$status success=${status == BluetoothGatt.GATT_SUCCESS}")
            operationInProgress = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                executeNextOperation()
            } else {
                handleProtocolOperationFailed("协议确认写入失败 status=$status")
            }
        }

        @Deprecated("Android 13 之前的回调")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handleCharacteristicRead(characteristic.uuid, characteristic.value ?: byteArrayOf(), status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(characteristic.uuid, value, status)
        }

        @Deprecated("Android 13 之前的回调")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            trace("Notify uuid=${characteristic.uuid} bytes=${characteristic.value.toHexString()}")
            handleCharacteristicNotify(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            trace("Notify uuid=${characteristic.uuid} bytes=${value.toHexString()}")
            handleCharacteristicNotify(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            trace("Notify 配置回调 uuid=${descriptor.uuid} status=$status")
            if (operationInProgress) {
                operationInProgress = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    executeNextOperation()
                } else {
                    handleProtocolOperationFailed("Notify 配置失败 status=$status")
                }
            }
        }
    }

    fun startScan() {
        if (adapter == null) {
            trace("当前手机不支持蓝牙", error = true)
            return
        }
        if (!adapter.isEnabled) {
            trace("蓝牙尚未开启", error = true)
            return
        }
        trace("开始扫描 BLE 设备")
        scanSignatures.clear()
        adapter.bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        trace("停止扫描")
    }

    fun connect(device: ScannedBleDevice, protocolTemplate: ProtocolTemplate) {
        stopScan()
        // 关键：发起新连接前，把本控制器上一条 GATT 彻底 close()（而非 disconnect()）。
        // pending 连接只 disconnect() 会被 Android 以 status=22 本地终止并泄漏 client 槽，
        // 后续 connectGatt 会静默挂起，表现为“连一个后连第二个连不上、必须关掉前一个电源”。
        cancelConnectWatchdog()
        releaseActiveConnection()
        gattEverConnected = false
        connectedName = device.name
        connectedAddress = device.address
        connectedManufacturerData = device.manufacturerData
        connectedScanRecordHex = device.scanRecordHex
        template = protocolTemplate
        activeProtocol = null
        activeBroadcastProtocol = null
        protocolReady = false
        gattReadyAtMs = 0L
        protocolCandidates = emptyList()
        broadcastProtocolCandidates = emptyList()
        protocolCandidateIndex = -1
        failedProtocolNames.clear()
        disconnectingAfterProtocolFailure = false
        resolvedWriteCharacteristic = null
        batteryReadRequested = false
        onBatteryPercent(null)
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        operationQueue.clear()
        operationInProgress = false
        onProtocol(BleProtocolStatus())
        updateProtocolAttempt(ProtocolAttemptStatus())
        operationId++
        trace(
            "连接请求 op=$operationId name=${device.name} address=${device.address} service=${protocolTemplate.serviceUuid} " +
                "write=${protocolTemplate.writeUuid} notify=${protocolTemplate.notifyUuid.ifBlank { "<none>" }} " +
                "manufacturer=${device.manufacturerData.ifBlank { "<none>" }}",
            type = "ble_connect_request",
            uploadPolicy = AiToyTraceUploadPolicy.Always,
            key = "ble_connect_request:$operationId",
        )
        broadcastProtocolCandidates = BleBroadcastProtocolRegistry.resolveAll(device)
        val shouldUseBroadcastDirectly = broadcastProtocolCandidates.isNotEmpty() &&
            (!device.connectable || broadcastProtocolCandidates.none { it.preferGattWhenConnectable })
        if (shouldUseBroadcastDirectly) {
            activeBroadcastProtocol = broadcastProtocolCandidates.first()
            protocolReady = true
            activeBroadcastProtocol?.status?.let(onProtocol)
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    success = true,
                    title = "${activeBroadcastProtocol!!.status.displayName} 已准备好",
                    message = "可以调节强度，设备会保持当前状态直到停止",
                    protocolName = activeBroadcastProtocol!!.status.displayName,
                    currentIndex = 1,
                    total = broadcastProtocolCandidates.size,
                ),
            )
            trace(
                "已启用广播协议：${activeBroadcastProtocol!!.status.displayName}",
                type = "ble_broadcast_ready",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_broadcast_ready:$operationId:${activeBroadcastProtocol!!.status.id}",
            )
            updateState(BleConnectionState.Ready)
            return
        } else if (broadcastProtocolCandidates.isNotEmpty()) {
            trace(
                "发现广播协议候选 ${broadcastProtocolCandidates.joinToString { it.status.id }}，" +
                    "设备可连接，优先读取 GATT 能力",
                type = "ble_broadcast_candidates",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_broadcast_candidates:$operationId",
            )
        }
        if (!device.connectable) {
            trace("扫描结果显示不可连接，仍尝试读取设备能力 address=${device.address}")
        }
        updateState(BleConnectionState.Connecting)
        val remoteDevice = adapter?.getRemoteDevice(device.address)
        gatt = remoteDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            trace("无法创建 GATT 连接", error = true)
            updateState(BleConnectionState.Error)
        } else {
            armConnectWatchdog()
        }
    }

    fun sendAction(action: ToyControlAction) {
        activeBroadcastProtocol?.let { protocol ->
            val commands = protocol.commandsFor(action)
            trace(
                "广播控制 action=$action protocol=${protocol.status.id} operations=${commands.size}",
                type = "ble_command_summary",
                uploadPolicy = AiToyTraceUploadPolicy.RateLimited,
                key = "ble_command_summary:broadcast:${protocol.status.id}:$action",
                intervalMs = 2_000L,
            )
            trace(
                "广播载荷 protocol=${protocol.status.id} payload=${commands.joinToString { it.describe() }}",
                type = "ble_broadcast_payload",
                uploadPolicy = AiToyTraceUploadPolicy.RateLimited,
                key = "ble_broadcast_payload:${protocol.status.id}:$action",
                intervalMs = 2_000L,
            )
            commands.forEach(advertiser::start)
            return
        }
        val protocol = activeProtocol ?: error("当前设备尚无可用的内置协议")
        val commands = protocol.commandsFor(action)
        val warmupMs = gattControlWarmupMs()
        if (warmupMs > 0) {
            trace("GATT 通道刚就绪，延后 ${warmupMs}ms 发送控制 action=$action protocol=${protocol.status.id}")
            mainHandler.postDelayed(
                {
                    if (activeProtocol === protocol && protocolReady) {
                        trace(
                            "GATT 控制 action=$action protocol=${protocol.status.id} operations=${commands.size}",
                            type = "ble_command_summary",
                            uploadPolicy = AiToyTraceUploadPolicy.RateLimited,
                            key = "ble_command_summary:gatt:${protocol.status.id}:$action",
                            intervalMs = 2_000L,
                        )
                        enqueueControl(protocol, action, commands)
                    }
                },
                warmupMs,
            )
            return
        }
        trace(
            "GATT 控制 action=$action protocol=${protocol.status.id} operations=${commands.size}",
            type = "ble_command_summary",
            uploadPolicy = AiToyTraceUploadPolicy.RateLimited,
            key = "ble_command_summary:gatt:${protocol.status.id}:$action",
            intervalMs = 2_000L,
        )
        enqueueControl(protocol, action, commands)
    }

    fun stopDevice() = sendAction(ToyControlAction.Stop)

    fun controlWarmupMs(): Long = gattControlWarmupMs()

    fun reportCurrentProtocolNoResponse(): ProtocolFallbackResult? {
        val failed = activeProtocol?.status ?: activeBroadcastProtocol?.status ?: return null
        failed.displayName
            .takeIf { it.isNotBlank() && it !in failedProtocolNames }
            ?.let(failedProtocolNames::add)
        trace(
            "协议适配反馈 event=protocol_adapter_feedback result=no_response " +
                "protocol=${failed.id} name=${failed.displayName}",
            error = true,
        )
        activeBroadcastProtocol?.let {
            val currentIndex = broadcastProtocolCandidates.indexOfFirst { candidate ->
                candidate.status.id == failed.id
            }.coerceAtLeast(0)
            val nextIndex = (currentIndex + 1 until broadcastProtocolCandidates.size)
                .firstOrNull { index ->
                    broadcastProtocolCandidates[index].status.displayName !in failedProtocolNames
                }
            if (nextIndex != null) {
                val next = broadcastProtocolCandidates[nextIndex]
                activeBroadcastProtocol = next
                activeProtocol = null
                protocolReady = true
                next.status.let(onProtocol)
                updateProtocolAttempt(
                    ProtocolAttemptStatus(
                        success = true,
                        title = "${next.status.displayName} 已准备好",
                        message = "已切换协议，请从低强度再试一次",
                        protocolName = next.status.displayName,
                        currentIndex = nextIndex + 1,
                        total = broadcastProtocolCandidates.size,
                        failedNames = failedProtocolNames.toList(),
                    ),
                )
                trace(
                    "协议适配反馈 event=protocol_adapter_feedback result=switch " +
                        "failed=${failed.id} next=${next.status.id} total=${broadcastProtocolCandidates.size}",
                    type = "protocol_adapter_feedback",
                    uploadPolicy = AiToyTraceUploadPolicy.Always,
                )
                updateState(BleConnectionState.Ready)
                return ProtocolFallbackResult(failed, switchedToNext = true)
            }
            protocolReady = false
            activeBroadcastProtocol = null
            activeProtocol = null
            finishUnsupportedAfterUserFeedback()
            return ProtocolFallbackResult(failed, switchedToNext = false)
        }
        val currentGatt = gatt
        val currentTemplate = template
        if (currentGatt == null || currentTemplate == null || protocolCandidates.isEmpty()) {
            protocolReady = false
            activeProtocol = null
            finishUnsupportedAfterUserFeedback()
            return ProtocolFallbackResult(failed, switchedToNext = false)
        }
        val hasNext = protocolCandidateIndex + 1 < protocolCandidates.size
        protocolReady = false
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        operationQueue.clear()
        operationInProgress = false
        val fingerprint = BleGattFingerprint(
            name = connectedName,
            address = connectedAddress,
            manufacturerData = connectedManufacturerData,
            scanRecordHex = connectedScanRecordHex,
            serviceUuids = currentGatt.services.map { it.uuid.toKotlinUuid() }.toSet(),
            characteristicUuids = currentGatt.services
                .flatMap { it.characteristics }
                .map { it.uuid.toKotlinUuid() }
                .toSet(),
            preferredServiceUuid = currentTemplate.serviceUuid.toKotlinUuidOrNull(),
            preferredWriteUuid = currentTemplate.writeUuid.toKotlinUuidOrNull(),
            preferredNotifyUuid = currentTemplate.notifyUuid.toKotlinUuidOrNull(),
        )
        tryNextProtocol(
            fingerprint = fingerprint,
            gatt = currentGatt,
            config = currentTemplate,
            reason = "用户反馈 ${failed.displayName} 没有反应，切换下一个候选协议",
            exhaustedByUserFeedback = true,
        )
        return ProtocolFallbackResult(failed, switchedToNext = hasNext)
    }

    private fun write(operation: BleProtocolOperation.Write) {
        val currentGatt = gatt ?: run {
            handleProtocolOperationFailed("设备尚未连接")
            return
        }
        val characteristic = findCharacteristic(operation.characteristicUuid.toAndroidUuid())
            ?: run {
                handleProtocolOperationFailed("找不到写入通道：${operation.characteristicUuid}")
                return
            }
        val supportsWithResponse =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val supportsNoResponse =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        // 以协议请求的写入方式为首选，但当特征实际不支持该方式时按其属性回退，
        // 避免对仅支持 WRITE_NO_RESPONSE 的特征做 with-response 写入而被回 status=3。
        val writeWithResponse = when {
            operation.withResponse && supportsWithResponse -> true
            operation.withResponse && supportsNoResponse -> false
            !operation.withResponse && supportsNoResponse -> false
            !operation.withResponse && supportsWithResponse -> true
            else -> operation.withResponse
        }
        if (writeWithResponse != operation.withResponse) {
            trace(
                "写入方式按特征属性回退 uuid=${characteristic.uuid} " +
                    "请求=${if (operation.withResponse) "with-response" else "without-response"} " +
                    "实际=${if (writeWithResponse) "with-response" else "without-response"} " +
                    "properties=0x${characteristic.properties.toString(16)}",
            )
        }
        val writeType = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        trace(
            "准备写入 op=$operationId uuid=${characteristic.uuid} mode=" +
                "${if (writeWithResponse) "with-response" else "without-response"} " +
            "length=${operation.bytes.size} bytes=${operation.bytes.toHexString()}",
        )
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(characteristic, operation.bytes, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = operation.bytes
            @Suppress("DEPRECATION")
            if (currentGatt.writeCharacteristic(characteristic)) 0 else -1
        }
        trace("写入已提交 result=$result")
        if (result != 0) {
            operationInProgress = false
            if (result == ANDROID_GATT_WRITE_REQUEST_BUSY && writeBusyRetryCount < ANDROID_GATT_WRITE_BUSY_MAX_RETRIES) {
                writeBusyRetryCount += 1
                trace("GATT 写入繁忙，延后重试 retry=$writeBusyRetryCount/${ANDROID_GATT_WRITE_BUSY_MAX_RETRIES}")
                enqueueFirst(listOf(operation))
                mainHandler.postDelayed(::executeNextOperation, ANDROID_GATT_WRITE_BUSY_RETRY_DELAY_MS)
                return
            }
            writeBusyRetryCount = 0
            handleProtocolOperationFailed("提交写入失败：$result")
            return
        }
        writeBusyRetryCount = 0
        if (!writeWithResponse) {
            operationInProgress = false
            mainHandler.postDelayed(::executeNextOperation, 40)
        }
    }

    fun disconnect() {
        advertiser.stop()
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        cancelConnectWatchdog()
        val current = gatt
        if (current != null) {
            if (gattEverConnected) {
                // 已建立的连接走正常异步断开，靠 STATE_DISCONNECTED 回调 closeGatt。
                updateState(BleConnectionState.Disconnecting)
                trace("主动断开 address=${current.device.address}")
                current.disconnect()
            } else {
                // 尚未建立(pending)的连接必须直接 close()：disconnect() 会触发 status=22 并泄漏 client 槽。
                trace("释放未建立的连接 address=${current.device.address}")
                closeGatt(current)
                updateState(BleConnectionState.Idle)
            }
        } else if (activeBroadcastProtocol != null) {
            activeBroadcastProtocol = null
            updateState(BleConnectionState.Idle)
            trace("已停止广播控制")
        }
    }

    /**
     * 无条件释放当前 GATT：pending 连接直接 close() 回收 client 槽，已建立的也 close()（此处用于重连前清场，
     * 不需要走异步 disconnect 的优雅关闭）。用于 connect() 开头确保不把旧连接泄漏或误发 status=22。
     */
    private fun releaseActiveConnection() {
        val current = gatt ?: run {
            activeBroadcastProtocol = null
            return
        }
        trace("重连前释放旧连接 address=${current.device.address} everConnected=$gattEverConnected")
        closeGatt(current)
    }

    private fun armConnectWatchdog() {
        cancelConnectWatchdog()
        val armedOperationId = operationId
        val runnable = Runnable {
            if (operationId == armedOperationId && !gattEverConnected) {
                trace(
                    "连接超时未建立，强制释放并报错 op=$operationId address=$connectedAddress",
                    error = true,
                    type = "ble_connect_timeout",
                    uploadPolicy = AiToyTraceUploadPolicy.Always,
                    key = "ble_connect_timeout:$operationId",
                )
                gatt?.let(::closeGatt)
                updateState(BleConnectionState.Error)
            }
        }
        connectWatchdogRunnable = runnable
        mainHandler.postDelayed(runnable, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectWatchdog() {
        connectWatchdogRunnable?.let(mainHandler::removeCallbacks)
        connectWatchdogRunnable = null
    }

    fun close() {
        stopScan()
        advertiser.stop()
        cancelProtocolKeepalive()
        cancelConnectWatchdog()
        gatt?.let(::closeGatt)
        scope.cancel()
    }

    private fun subscribeNotify(gatt: BluetoothGatt, config: ProtocolTemplate) {
        val notifyUuid = config.notifyUuid.toUuidOrNull()
        val configured = notifyUuid?.let { uuid ->
            gatt.services.asSequence().mapNotNull { it.getCharacteristic(uuid) }.firstOrNull()
        }
        val characteristic = configured ?: gatt.services.asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull {
                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                    it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            }
        if (characteristic == null) {
            trace("设备没有可订阅的 Notify/Indicate 特征")
            return
        }
        if (configured == null) {
            trace("自动选择 Notify 特征 uuid=${characteristic.uuid}")
        }
        val localEnabled = gatt.setCharacteristicNotification(characteristic, true)
            trace("启用 Notify 本地监听 uuid=${characteristic.uuid} result=$localEnabled")
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, value)
            trace("写入 Notify 描述符 result=$result")
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            trace("写入 Notify 描述符 result=${gatt.writeDescriptor(descriptor)}")
        }
    }

    private fun tryNextProtocol(
        fingerprint: BleGattFingerprint,
        gatt: BluetoothGatt,
        config: ProtocolTemplate,
        reason: String = "",
        exhaustedByUserFeedback: Boolean = false,
    ) {
        if (reason.isNotBlank()) trace(reason, error = true)
        protocolCandidateIndex += 1
        val protocol = protocolCandidates.getOrNull(protocolCandidateIndex)
        if (protocol == null) {
            if (exhaustedByUserFeedback) {
                finishUnsupportedAfterUserFeedback()
                return
            }
            val allFailed = failedProtocolNames.isNotEmpty() && !config.manualControlEnabled
            finishWithManualOrUnsupported(gatt, config, allCandidatesFailed = allFailed)
            return
        }
        activeProtocol = protocol
        protocolReady = false
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        operationQueue.clear()
        operationInProgress = false
        trace(
            "确认协议 ${protocolCandidateIndex + 1}/${protocolCandidates.size}：" +
                "${protocol.status.displayName} id=${protocol.status.id} candidates=" +
                protocolCandidates.joinToString { it.status.id },
            type = "ble_protocol_attempt",
            uploadPolicy = AiToyTraceUploadPolicy.Always,
            key = "ble_protocol_attempt:$operationId:${protocol.status.id}",
        )
        if (!protocol.status.controllable) {
            protocol.status.displayName
                .takeIf { it.isNotBlank() && it !in failedProtocolNames }
                ?.let(failedProtocolNames::add)
            trace("已识别 ${protocol.status.displayName}，但当前版本还没有可执行的本地控制器")
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    active = true,
                    title = "已识别 ${protocol.status.displayName}",
                    message = "当前版本还不能直接控制这台设备，正在检查其它可用协议",
                    protocolName = protocol.status.displayName,
                    currentIndex = protocolCandidateIndex + 1,
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            activeProtocol = null
            tryNextProtocol(fingerprint, gatt, config, exhaustedByUserFeedback = exhaustedByUserFeedback)
            return
        }
        updateProtocolAttempt(
            ProtocolAttemptStatus(
                active = true,
                title = "正在尝试 ${protocol.status.displayName}",
                message = "正在确认第 ${protocolCandidateIndex + 1} 个协议，共 ${protocolCandidates.size} 个",
                protocolName = protocol.status.displayName,
                currentIndex = protocolCandidateIndex + 1,
                total = protocolCandidates.size,
                failedNames = failedProtocolNames.toList(),
            ),
        )
        onProtocol(protocol.status)
        enqueue(protocol.initialize(fingerprint))
        if (operationQueue.isEmpty()) {
            protocolReady = true
            gattReadyAtMs = System.currentTimeMillis()
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    success = true,
                    title = "${protocol.status.displayName} 连接成功",
                    message = "已完成协议确认，可以开始控制",
                    protocolName = protocol.status.displayName,
                    currentIndex = protocolCandidateIndex + 1,
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace(
                "协议就绪 name=${protocol.status.displayName} id=${protocol.status.id} index=${protocolCandidateIndex + 1}/${protocolCandidates.size}",
                type = "ble_protocol_ready",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_protocol_ready:$operationId:${protocol.status.id}",
            )
            updateState(BleConnectionState.Ready)
        }
    }

    private fun finishWithManualOrUnsupported(
        gatt: BluetoothGatt,
        config: ProtocolTemplate,
        allCandidatesFailed: Boolean = false,
    ) {
        if (allCandidatesFailed) {
            val names = failedProtocolNames.joinToString("、").ifBlank { "已知协议" }
            val message = "这些协议都没有连上：$names"
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    title = "自动识别失败",
                    message = "已断开连接，可以换一个设备或加群提交设备信息",
                    currentIndex = protocolCandidates.size,
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace(message, error = true)
            activeProtocol = null
            protocolReady = false
            cancelProtocolKeepalive()
            cancelProtocolReadyWait()
            operationQueue.clear()
            operationInProgress = false
            disconnectingAfterProtocolFailure = true
            updateState(BleConnectionState.Error)
            gatt.disconnect()
            return
        }
        val configuredService = gatt.getService(config.serviceUuid.toUuidOrNull())
        val configuredWrite = configuredService?.getCharacteristic(config.writeUuid.toUuidOrNull())
        trace(
            "协议匹配 service=${configuredService != null} write=${configuredWrite != null}",
            type = "ble_manual_template_match",
            uploadPolicy = AiToyTraceUploadPolicy.Always,
            key = "ble_manual_template_match:$operationId",
        )
        resolvedWriteCharacteristic = configuredWrite ?: gatt.services.asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull {
                it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            }
        if (configuredWrite == null && resolvedWriteCharacteristic != null) {
            trace(
                "自动选择写入特征 service=${resolvedWriteCharacteristic?.service?.uuid} " +
                    "uuid=${resolvedWriteCharacteristic?.uuid} properties=0x" +
                    resolvedWriteCharacteristic?.properties?.toString(16),
                type = "ble_auto_write_characteristic",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_auto_write_characteristic:$operationId",
            )
        }
        if (resolvedWriteCharacteristic == null) {
            updateState(BleConnectionState.Error)
            trace("设备没有可写特征", error = true)
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    title = "连接失败",
                    message = "没有找到可用的写入通道，已停止本次连接",
                    currentIndex = protocolCandidates.size.coerceAtLeast(0),
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            disconnectingAfterProtocolFailure = true
            gatt.disconnect()
            return
        }
        subscribeNotify(gatt, config)
        if (config.manualControlEnabled) {
            activeProtocol = ManualBleProtocol(resolvedWriteCharacteristic!!.uuid.toKotlinUuid(), config)
            protocolReady = true
            gattReadyAtMs = System.currentTimeMillis()
            onProtocol(activeProtocol!!.status)
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    success = true,
                    title = "已使用高级模板连接",
                    message = "可以从低强度开始控制，再根据反应调整指令",
                    protocolName = activeProtocol!!.status.displayName,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace(
                "未找到可用内置协议，已启用用户确认的高级手动模板",
                type = "ble_manual_template_ready",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
                key = "ble_manual_template_ready:$operationId",
            )
        } else {
            activeProtocol = null
            protocolReady = false
            onProtocol(
                BleProtocolStatus(),
            )
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    title = "暂不支持这个设备",
                    message = "已断开连接。可以加群提交设备信息，后续版本会继续适配。",
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace("未找到可用内置协议；可按教程补充适配信息")
            disconnectingAfterProtocolFailure = true
            updateState(BleConnectionState.Error)
            gatt.disconnect()
            return
        }
        updateState(BleConnectionState.Ready)
    }

    private fun finishUnsupportedAfterUserFeedback() {
        val failed = failedProtocolNames.toList()
        val currentGatt = gatt
        val totalCandidates = if (broadcastProtocolCandidates.isNotEmpty()) {
            broadcastProtocolCandidates.size
        } else {
            protocolCandidates.size
        }
        updateProtocolAttempt(
            ProtocolAttemptStatus(
                title = "仍未适配成功",
                message = "已尝试所有协议，目前仍不支持，请加群提交反馈，开发者会尝试支持。",
                currentIndex = totalCandidates.coerceAtLeast(failed.size),
                total = totalCandidates,
                failedNames = failed,
                exhausted = true,
            ),
        )
        trace(
            "协议适配反馈 event=protocol_adapter_feedback result=exhausted " +
                "failed=${failed.joinToString("|")} total=$totalCandidates",
            error = true,
        )
        activeProtocol = null
        activeBroadcastProtocol = null
        protocolReady = false
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        gattReadyAtMs = 0L
        operationQueue.clear()
        operationInProgress = false
        onProtocol(BleProtocolStatus())
        currentGatt?.let {
            disconnectingAfterProtocolFailure = true
            updateState(BleConnectionState.Error)
            it.disconnect()
        } ?: updateState(BleConnectionState.Error)
    }

    private fun closeGatt(target: BluetoothGatt) {
        target.close()
        if (gatt === target) gatt = null
        cancelProtocolKeepalive()
        cancelProtocolReadyWait()
        trace("GATT 资源已释放")
    }

    private fun updateProtocolKeepalive(
        protocol: BleDeviceProtocol,
        action: ToyControlAction,
        operations: List<BleProtocolOperation>,
    ) {
        val intervalMs = protocol.keepaliveIntervalMs()
        if (action == ToyControlAction.Stop || intervalMs <= 0 || operations.isEmpty()) {
            cancelProtocolKeepalive()
            return
        }
        keepaliveProtocol = protocol
        keepaliveOperations = operations
        scheduleProtocolKeepalive(intervalMs)
    }

    private fun scheduleProtocolKeepalive(intervalMs: Long) {
        keepaliveRunnable?.let(mainHandler::removeCallbacks)
        val protocol = keepaliveProtocol ?: return
        val runnable = Runnable {
            if (activeProtocol === protocol && protocolReady && keepaliveOperations.isNotEmpty()) {
                val nextOperations = protocol.keepaliveCommands(keepaliveOperations)
                if (nextOperations.isNotEmpty()) {
                    keepaliveOperations = nextOperations
                    trace("GATT keepalive protocol=${protocol.status.id} operations=${nextOperations.size}")
                    enqueue(nextOperations)
                    scheduleProtocolKeepalive(intervalMs)
                } else {
                    cancelProtocolKeepalive()
                }
            }
        }
        keepaliveRunnable = runnable
        mainHandler.postDelayed(runnable, intervalMs)
    }

    private fun cancelProtocolKeepalive() {
        keepaliveRunnable?.let(mainHandler::removeCallbacks)
        keepaliveRunnable = null
        keepaliveProtocol = null
        keepaliveOperations = emptyList()
    }

    private fun enqueueControl(
        protocol: BleDeviceProtocol,
        action: ToyControlAction,
        operations: List<BleProtocolOperation>,
    ) {
        if (action == ToyControlAction.Stop) {
            cancelProtocolKeepalive()
            operationQueue.clear()
        }
        enqueue(operations)
        updateProtocolKeepalive(protocol, action, operations)
    }

    private fun cancelProtocolReadyWait() {
        protocolReadyTimeoutRunnable?.let(mainHandler::removeCallbacks)
        protocolReadyTimeoutRunnable = null
        waitingForProtocolReady = false
    }

    private fun enqueue(operations: List<BleProtocolOperation>) {
        trace("加入 GATT 操作 count=${operations.size} queueBefore=${operationQueue.size}")
        operations.forEachIndexed { index, operation ->
            trace("操作[$index] ${operation.describe()}")
        }
        operationQueue.addAll(operations)
        executeNextOperation()
    }

    private fun executeNextOperation() {
        if (operationInProgress) return
        val operation = if (operationQueue.isEmpty()) null else operationQueue.removeFirst()
        if (operation == null) {
            if (!protocolReady && connectionStateNeedsReady()) {
                protocolReady = true
                gattReadyAtMs = System.currentTimeMillis()
                activeProtocol?.status?.let { status ->
                    updateProtocolAttempt(
                        ProtocolAttemptStatus(
                            success = true,
                            title = "${status.displayName} 连接成功",
                            message = "已完成协议确认，可以开始控制",
                            protocolName = status.displayName,
                            currentIndex = protocolCandidateIndex + 1,
                            total = protocolCandidates.size,
                            failedNames = failedProtocolNames.toList(),
                        ),
                    )
                    trace(
                        "协议就绪 name=${status.displayName} id=${status.id} index=${protocolCandidateIndex + 1}/${protocolCandidates.size}",
                        type = "ble_protocol_ready",
                        uploadPolicy = AiToyTraceUploadPolicy.Always,
                        key = "ble_protocol_ready:$operationId:${status.id}",
                    )
                }
                updateState(BleConnectionState.Ready)
            }
            return
        }
        operationInProgress = true
        when (operation) {
            is BleProtocolOperation.Read -> {
                val characteristic = findCharacteristic(operation.characteristicUuid.toAndroidUuid())
                    ?: run {
                        operationInProgress = false
                        handleProtocolOperationFailed("找不到读取通道：${operation.characteristicUuid}")
                        return
                    }
                trace("准备读取 uuid=${characteristic.uuid}")
                val started = gatt?.readCharacteristic(characteristic) == true
                trace("读取已提交 result=$started")
                if (!started) {
                    operationInProgress = false
                    handleProtocolOperationFailed("提交读取失败：${characteristic.uuid}")
                }
            }
            is BleProtocolOperation.SubscribeNotify -> {
                val characteristic = findCharacteristic(operation.characteristicUuid.toAndroidUuid())
                    ?: run {
                        operationInProgress = false
                        handleProtocolOperationFailed("找不到通知通道：${operation.characteristicUuid}")
                        return
                    }
                val localEnabled = gatt?.setCharacteristicNotification(characteristic, true) == true
                trace("启用 Notify 本地监听 uuid=${characteristic.uuid} result=$localEnabled")
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor == null) {
                    operationInProgress = false
                    handleProtocolOperationFailed("找不到 Notify 描述符：${characteristic.uuid}")
                    return
                }
                val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                val descriptorResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeDescriptor(descriptor, value) ?: -1
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    if (gatt?.writeDescriptor(descriptor) == true) BluetoothGatt.GATT_SUCCESS else -1
                }
                trace("写入 Notify 描述符 result=$descriptorResult")
                if (descriptorResult != BluetoothGatt.GATT_SUCCESS) {
                    operationInProgress = false
                    handleProtocolOperationFailed("提交 Notify 描述符失败：$descriptorResult")
                    return
                }
            }
            is BleProtocolOperation.UnsubscribeNotify -> {
                val characteristic = findCharacteristic(operation.characteristicUuid.toAndroidUuid())
                    ?: run {
                        operationInProgress = false
                        handleProtocolOperationFailed("找不到通知通道：${operation.characteristicUuid}")
                        return
                    }
                val localEnabled = gatt?.setCharacteristicNotification(characteristic, false) == true
                trace("关闭 Notify 本地监听 uuid=${characteristic.uuid} result=$localEnabled")
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor == null) {
                    operationInProgress = false
                    handleProtocolOperationFailed("找不到 Notify 描述符：${characteristic.uuid}")
                    return
                }
                val descriptorResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeDescriptor(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) ?: -1
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    if (gatt?.writeDescriptor(descriptor) == true) BluetoothGatt.GATT_SUCCESS else -1
                }
                trace("写入 Notify 关闭描述符 result=$descriptorResult")
                if (descriptorResult != BluetoothGatt.GATT_SUCCESS) {
                    operationInProgress = false
                    handleProtocolOperationFailed("提交 Notify 关闭描述符失败：$descriptorResult")
                    return
                }
            }
            is BleProtocolOperation.Sleep -> {
                trace("等待协议初始化 ${operation.millis}ms")
                mainHandler.postDelayed({
                    operationInProgress = false
                    executeNextOperation()
                }, operation.millis.coerceAtLeast(0))
            }
            is BleProtocolOperation.WaitForProtocolReady -> {
                if (activeProtocol?.isProtocolReady() == true) {
                    operationInProgress = false
                    executeNextOperation()
                } else {
                    trace("等待协议通知完成初始化 timeout=${operation.timeoutMs}ms")
                    waitingForProtocolReady = true
                    protocolReadyTimeoutRunnable = Runnable {
                        if (waitingForProtocolReady) {
                            operationInProgress = false
                            handleProtocolOperationFailed("等待协议通知超时")
                        }
                    }.also { mainHandler.postDelayed(it, operation.timeoutMs.coerceAtLeast(1_000L)) }
                }
            }
            is BleProtocolOperation.Write -> write(operation)
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, bytes: ByteArray, status: Int) {
        trace("读取回调 uuid=$uuid status=$status bytes=${bytes.toHexString()}")
        operationInProgress = false
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if (uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                trace("标准电量读取失败 status=$status")
                mainHandler.post { onBatteryPercent(null) }
                executeNextOperation()
                return
            }
            handleProtocolOperationFailed("协议确认读取失败 status=$status")
            return
        }
        if (uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            handleBatteryLevel(bytes)
        }
        runCatching {
            activeProtocol?.onRead(uuid.toKotlinUuid(), bytes)?.let(operationQueue::addAll)
        }.onFailure {
            handleProtocolOperationFailed(it.message ?: "协议确认失败")
            return
        }
        executeNextOperation()
    }

    private fun handleCharacteristicNotify(uuid: UUID, bytes: ByteArray) {
        if (uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            handleBatteryLevel(bytes)
        }
        runCatching {
            val protocol = activeProtocol
            val operations = protocol?.onNotify(uuid.toKotlinUuid(), bytes).orEmpty()
            if (waitingForProtocolReady) {
                when {
                    protocol?.isProtocolReady() == true -> {
                        cancelProtocolReadyWait()
                        operationInProgress = false
                        if (operations.isNotEmpty()) {
                            enqueueFirst(operations)
                        }
                        executeNextOperation()
                    }
                    operations.isNotEmpty() -> {
                        cancelProtocolReadyWait()
                        operationInProgress = false
                        enqueueFirst(operations)
                        executeNextOperation()
                    }
                }
            } else if (operations.isNotEmpty()) {
                operationQueue.addAll(operations)
                executeNextOperation()
            }
        }.onFailure {
            handleProtocolOperationFailed("协议通知处理失败：${it.message}")
        }
    }

    private fun enqueueFirst(operations: List<BleProtocolOperation>) {
        for (operation in operations.asReversed()) {
            operationQueue.addFirst(operation)
        }
    }

    private fun handleProtocolOperationFailed(reason: String) {
        cancelProtocolReadyWait()
        operationQueue.clear()
        if (protocolReady) {
            trace(reason, error = true)
            return
        }
        if (!protocolReady && protocolCandidates.isNotEmpty()) {
            activeProtocol?.status?.displayName
                ?.takeIf { it.isNotBlank() && it !in failedProtocolNames }
                ?.let(failedProtocolNames::add)
            activeProtocol?.status?.displayName?.let { name ->
                updateProtocolAttempt(
                    ProtocolAttemptStatus(
                        active = true,
                        title = "$name 尝试失败",
                        message = "正在切换下一个可用协议",
                        protocolName = name,
                        currentIndex = protocolCandidateIndex + 1,
                        total = protocolCandidates.size,
                        failedNames = failedProtocolNames.toList(),
                    ),
                )
            }
            val currentGatt = gatt
            val currentTemplate = template
            if (currentGatt != null && currentTemplate != null) {
                val fingerprint = BleGattFingerprint(
                    name = connectedName,
                    address = connectedAddress,
                    manufacturerData = connectedManufacturerData,
                    scanRecordHex = connectedScanRecordHex,
                    serviceUuids = currentGatt.services.map { it.uuid.toKotlinUuid() }.toSet(),
                    characteristicUuids = currentGatt.services
                        .flatMap { it.characteristics }
                        .map { it.uuid.toKotlinUuid() }
                        .toSet(),
                    preferredServiceUuid = currentTemplate.serviceUuid.toKotlinUuidOrNull(),
                    preferredWriteUuid = currentTemplate.writeUuid.toKotlinUuidOrNull(),
                    preferredNotifyUuid = currentTemplate.notifyUuid.toKotlinUuidOrNull(),
                )
                tryNextProtocol(fingerprint, currentGatt, currentTemplate, reason)
                return
            }
        }
        trace(reason, error = true)
        updateState(BleConnectionState.Error)
    }

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
        gatt?.services
            ?.asSequence()
            ?.mapNotNull { it.getCharacteristic(uuid) }
            ?.firstOrNull()

    private fun connectionStateNeedsReady(): Boolean =
        activeProtocol != null

    private fun requestStandardBatteryLevel() {
        if (batteryReadRequested) return
        val currentGatt = gatt ?: return
        val batteryService = currentGatt.getService(BATTERY_SERVICE_UUID) ?: run {
            trace("设备没有标准电量服务")
            return
        }
        val batteryLevel = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID) ?: run {
            trace("设备没有标准电量特征")
            return
        }
        val canRead = batteryLevel.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
        val canNotify =
            batteryLevel.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                batteryLevel.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!canRead && !canNotify) {
            trace("标准电量特征不支持读取或通知")
            return
        }
        batteryReadRequested = true
        if (canRead) {
            trace("准备读取标准电量")
            operationQueue.add(BleProtocolOperation.Read(batteryLevel.uuid.toKotlinUuid()))
        } else {
            trace("准备订阅标准电量")
            operationQueue.add(BleProtocolOperation.SubscribeNotify(batteryLevel.uuid.toKotlinUuid()))
        }
        executeNextOperation()
    }

    private fun handleBatteryLevel(bytes: ByteArray) {
        val percent = bytes.firstOrNull()
            ?.toInt()
            ?.and(0xff)
            ?.takeIf { it in 1..100 }
        trace("读取到设备电量 ${percent?.let { "$it%" } ?: "未知"}")
        mainHandler.post { onBatteryPercent(percent) }
    }

    private fun gattControlWarmupMs(): Long {
        if (gattReadyAtMs <= 0L) return 0L
        val elapsedMs = System.currentTimeMillis() - gattReadyAtMs
        return (GATT_CONTROL_WARMUP_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun updateState(state: BleConnectionState) {
        if (state == BleConnectionState.Ready) {
            requestStandardBatteryLevel()
        }
        mainHandler.post { onState(state) }
    }

    private fun updateProtocolAttempt(status: ProtocolAttemptStatus) {
        mainHandler.post { onProtocolAttempt(status) }
    }

    private fun trace(
        message: String,
        error: Boolean = false,
        type: String = "",
        uploadPolicy: AiToyTraceUploadPolicy = if (error) {
            AiToyTraceUploadPolicy.Always
        } else {
            AiToyTraceUploadPolicy.Drop
        },
        key: String = "",
        intervalMs: Long = 0L,
    ) {
        if (error) Log.e(TAG, message) else Log.d(TAG, message)
        mainHandler.post { onLog(message) }
        if (error || uploadPolicy != AiToyTraceUploadPolicy.Drop) {
            onTrace(
                AiToyTraceEvent(
                    message = message,
                    error = error,
                    type = type,
                    uploadPolicy = uploadPolicy,
                    key = key,
                    intervalMs = intervalMs,
                )
            )
        }
    }

    private fun BleProtocolOperation.describe(): String =
        when (this) {
            is BleProtocolOperation.Read -> "Read uuid=$characteristicUuid"
            is BleProtocolOperation.SubscribeNotify -> "SubscribeNotify uuid=$characteristicUuid"
            is BleProtocolOperation.UnsubscribeNotify -> "UnsubscribeNotify uuid=$characteristicUuid"
            is BleProtocolOperation.Sleep -> "Sleep millis=$millis"
            is BleProtocolOperation.WaitForProtocolReady -> "WaitForProtocolReady timeoutMs=$timeoutMs"
            is BleProtocolOperation.Write ->
                "Write uuid=$characteristicUuid withResponse=$withResponse bytes=${bytes.toHexString()}"
        }

    private fun BleAdvertiseOperation.describe(): String =
        when {
            serviceUuid.isNotBlank() -> "serviceUuid=$serviceUuid"
            serviceDataUuid.isNotBlank() -> "serviceDataUuid=$serviceDataUuid bytes=${serviceData.toHexString()}"
            manufacturerId != null -> "manufacturer=0x${manufacturerId.toString(16)} bytes=${manufacturerData.toHexString()}"
            else -> "<empty>"
        }

    private fun BluetoothGattCharacteristic.propertiesText(): String {
        val names = buildList {
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        }
        return names.joinToString(prefix = "[", postfix = "]").ifBlank { "[]" }
    }

    private fun shouldUploadGattDiscoverySummary(): Boolean {
        val probe = "$connectedName $connectedManufacturerData $connectedScanRecordHex".lowercase()
        return listOf("qctt", "kisstoy", "tutu", "tutuii", "突突", "迷路", "4a 4c 41 49 53 44 4b")
            .any(probe::contains)
    }

    private fun BluetoothGatt.gattDiscoverySummary(): String =
        services.joinToString("; ") { service ->
            val characteristics = service.characteristics.joinToString(",") { characteristic ->
                val descriptors = characteristic.descriptors
                    .joinToString(prefix = "{", postfix = "}") { descriptor -> descriptor.uuid.toString() }
                "${characteristic.uuid}@0x${characteristic.instanceId.toString(16)}" +
                    ":0x${characteristic.properties.toString(16)}$descriptors"
            }
            "${service.uuid}[$characteristics]"
        }

    private fun String.toUuidOrNull(): UUID? = trim().takeIf { it.isNotEmpty() }?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }

    private fun String.toKotlinUuidOrNull(): kotlin.uuid.Uuid? = toUuidOrNull()?.toKotlinUuid()

    private fun UUID.toKotlinUuid(): kotlin.uuid.Uuid = kotlin.uuid.Uuid.parse(toString())

    private fun kotlin.uuid.Uuid.toAndroidUuid(): UUID = UUID.fromString(toString())

    private fun ScanRecord?.manufacturerDataText(): String {
        val manufacturerData = this?.manufacturerSpecificData
        if (manufacturerData == null || manufacturerData.size() == 0) return ""
        return (0 until manufacturerData.size()).joinToString {
            val id = manufacturerData.keyAt(it)
            "0x${id.toString(16)}:${manufacturerData.valueAt(it).toHexString()}"
        }
    }

    companion object {
        const val TAG = "AiToyBle"
        private const val GATT_CONTROL_WARMUP_MS = 1_200L
        // 直连(autoConnect=false)通常几秒内建立；10s 未建立即判定挂起，强制回收 client 槽。
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHARACTERISTIC_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
}

private const val ANDROID_GATT_WRITE_REQUEST_BUSY = 201
private const val ANDROID_GATT_WRITE_BUSY_MAX_RETRIES = 8
private const val ANDROID_GATT_WRITE_BUSY_RETRY_DELAY_MS = 180L
