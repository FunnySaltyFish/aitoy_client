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
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.funny.aitoy.core.platform.AndroidPlatformInit
import java.util.ArrayDeque
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleController(
    private val onDevice: (ScannedBleDevice) -> Unit,
    private val onState: (BleConnectionState) -> Unit,
    private val onProtocol: (BleProtocolStatus) -> Unit,
    private val onProtocolAttempt: (ProtocolAttemptStatus) -> Unit,
    private val onLog: (String) -> Unit,
) {
    private val context: Context = AndroidPlatformInit.appContext
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var template: ProtocolTemplate? = null
    private var connectedName = ""
    private var activeProtocol: BleDeviceProtocol? = null
    private var protocolReady = false
    private var protocolCandidates = emptyList<BleDeviceProtocol>()
    private var protocolCandidateIndex = -1
    private val failedProtocolNames = mutableListOf<String>()
    private var disconnectingAfterProtocolFailure = false
    private var resolvedWriteCharacteristic: BluetoothGattCharacteristic? = null
    private val operationQueue = ArrayDeque<BleProtocolOperation>()
    private var operationInProgress = false
    private var operationId = 0L
    private val scanSignatures = mutableMapOf<String, String>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = runCatching { device.name }.getOrNull()
                ?: result.scanRecord?.deviceName
                ?: "未命名设备"
            val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else {
                true
            }
            mainHandler.post {
                onDevice(ScannedBleDevice(name, device.address, result.rssi, connectable))
            }
            val record = result.scanRecord
            val signature = buildString {
                append("name=$name address=${device.address} connectable=$connectable")
                append(" services=${record?.serviceUuids?.joinToString { it.uuid.toString() } ?: "<none>"}")
                append(" manufacturer=")
                val manufacturerData = record?.manufacturerSpecificData
                if (manufacturerData == null || manufacturerData.size() == 0) {
                    append("<none>")
                } else {
                    append(
                        (0 until manufacturerData.size()).joinToString {
                            val id = manufacturerData.keyAt(it)
                            "0x${id.toString(16)}:${manufacturerData.valueAt(it).toHexString()}"
                        },
                    )
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
            trace("连接状态回调 status=$status newState=$newState address=${gatt.device.address}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateState(BleConnectionState.Discovering)
                    val started = gatt.discoverServices()
                    trace("开始发现 GATT 服务 result=$started")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    trace("设备已断开 status=$status")
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
            trace("服务发现完成 status=$status serviceCount=${gatt.services.size}")
            gatt.services.forEach { service ->
                trace("服务 uuid=${service.uuid} characteristicCount=${service.characteristics.size}")
                service.characteristics.forEach { characteristic ->
                    trace(
                        "特征 uuid=${characteristic.uuid} properties=0x" +
                            characteristic.properties.toString(16),
                    )
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState(BleConnectionState.Error)
                return
            }
            val config = template ?: return
            val fingerprint = BleGattFingerprint(
                name = connectedName,
                characteristicUuids = gatt.services
                    .flatMap { it.characteristics }
                    .map { it.uuid }
                    .toSet(),
            )
            protocolCandidates = BleProtocolRegistry.resolveAll(fingerprint)
            protocolCandidateIndex = -1
            failedProtocolNames.clear()
            if (protocolCandidates.isNotEmpty()) {
                trace("找到 ${protocolCandidates.size} 个候选协议，开始依次确认")
                tryNextProtocol(fingerprint, gatt, config)
                return
            }
            finishWithManualOrUnsupported(gatt, config)
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
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            trace("Notify uuid=${characteristic.uuid} bytes=${value.toHexString()}")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            trace("Notify 配置回调 uuid=${descriptor.uuid} status=$status")
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
        disconnect()
        connectedName = device.name
        template = protocolTemplate
        activeProtocol = null
        protocolReady = false
        protocolCandidates = emptyList()
        protocolCandidateIndex = -1
        failedProtocolNames.clear()
        disconnectingAfterProtocolFailure = false
        resolvedWriteCharacteristic = null
        operationQueue.clear()
        operationInProgress = false
        onProtocol(BleProtocolStatus())
        updateProtocolAttempt(ProtocolAttemptStatus())
        operationId++
        trace(
            "连接请求 op=$operationId name=${device.name} address=${device.address} service=${protocolTemplate.serviceUuid} " +
                "write=${protocolTemplate.writeUuid} notify=${protocolTemplate.notifyUuid.ifBlank { "<none>" }}",
        )
        updateState(BleConnectionState.Connecting)
        val remoteDevice = adapter?.getRemoteDevice(device.address)
        gatt = remoteDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            trace("无法创建 GATT 连接", error = true)
            updateState(BleConnectionState.Error)
        }
    }

    fun sendCommand(mode: Int, intensity: Int) {
        val protocol = activeProtocol ?: error("当前设备尚无可用的内置协议")
        enqueue(protocol.setCommands(mode, intensity))
    }

    fun stopDevice() {
        val protocol = activeProtocol ?: error("当前设备尚无可用的内置协议")
        enqueue(protocol.stopCommands())
    }

    private fun write(operation: BleProtocolOperation.Write) {
        val currentGatt = gatt ?: run {
            handleProtocolOperationFailed("设备尚未连接")
            return
        }
        val characteristic = findCharacteristic(operation.characteristicUuid)
            ?: run {
                handleProtocolOperationFailed("找不到写入通道：${operation.characteristicUuid}")
                return
            }
        val supportsNoResponse =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val writeWithResponse = operation.withResponse || !supportsNoResponse
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
            handleProtocolOperationFailed("提交写入失败：$result")
            return
        }
        if (!writeWithResponse) {
            operationInProgress = false
            mainHandler.postDelayed(::executeNextOperation, 40)
        }
    }

    fun disconnect() {
        gatt?.let {
            updateState(BleConnectionState.Disconnecting)
            trace("主动断开 address=${it.device.address}")
            it.disconnect()
        }
    }

    fun close() {
        stopScan()
        gatt?.let(::closeGatt)
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
    ) {
        if (reason.isNotBlank()) trace(reason, error = true)
        protocolCandidateIndex += 1
        val protocol = protocolCandidates.getOrNull(protocolCandidateIndex)
        if (protocol == null) {
            val allFailed = failedProtocolNames.isNotEmpty() && !config.manualControlEnabled
            finishWithManualOrUnsupported(gatt, config, allCandidatesFailed = allFailed)
            return
        }
        activeProtocol = protocol
        protocolReady = false
        operationQueue.clear()
        operationInProgress = false
        trace("确认协议 ${protocolCandidateIndex + 1}/${protocolCandidates.size}：${protocol.status.displayName}")
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
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    success = true,
                    title = "${protocol.status.displayName} 连接成功",
                    message = "已完成协议确认，可以开始测试",
                    protocolName = protocol.status.displayName,
                    currentIndex = protocolCandidateIndex + 1,
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
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
                    message = "已断开连接，可以换一个设备或打开高级工具导入指令",
                    currentIndex = protocolCandidates.size,
                    total = protocolCandidates.size,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace(message, error = true)
            activeProtocol = null
            protocolReady = false
            operationQueue.clear()
            operationInProgress = false
            disconnectingAfterProtocolFailure = true
            updateState(BleConnectionState.Error)
            gatt.disconnect()
            return
        }
        val configuredService = gatt.getService(config.serviceUuid.toUuidOrNull())
        val configuredWrite = configuredService?.getCharacteristic(config.writeUuid.toUuidOrNull())
        trace("协议匹配 service=${configuredService != null} write=${configuredWrite != null}")
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
            activeProtocol = ManualBleProtocol(resolvedWriteCharacteristic!!.uuid, config)
            protocolReady = true
            onProtocol(activeProtocol!!.status)
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    success = true,
                    title = "已使用高级模板连接",
                    message = "可以先轻柔测试，再根据反馈调整指令",
                    protocolName = activeProtocol!!.status.displayName,
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace("未找到可用内置协议，已启用用户确认的高级手动模板")
        } else {
            activeProtocol = null
            protocolReady = false
            onProtocol(
                BleProtocolStatus(),
            )
            updateProtocolAttempt(
                ProtocolAttemptStatus(
                    title = "暂不支持这个设备",
                    message = "已断开连接。可以打开高级工具导入同款设备指令后再试",
                    failedNames = failedProtocolNames.toList(),
                ),
            )
            trace("未找到可用内置协议；可导入同款设备指令或按教程补充适配信息")
            disconnectingAfterProtocolFailure = true
            updateState(BleConnectionState.Error)
            gatt.disconnect()
            return
        }
        updateState(BleConnectionState.Ready)
    }

    private fun closeGatt(target: BluetoothGatt) {
        target.close()
        if (gatt === target) gatt = null
        trace("GATT 资源已释放")
    }

    private fun enqueue(operations: List<BleProtocolOperation>) {
        operationQueue.addAll(operations)
        executeNextOperation()
    }

    private fun executeNextOperation() {
        if (operationInProgress) return
        val operation = if (operationQueue.isEmpty()) null else operationQueue.removeFirst()
        if (operation == null) {
            if (!protocolReady && connectionStateNeedsReady()) {
                protocolReady = true
                activeProtocol?.status?.let { status ->
                    updateProtocolAttempt(
                        ProtocolAttemptStatus(
                            success = true,
                            title = "${status.displayName} 连接成功",
                            message = "已完成协议确认，可以开始测试",
                            protocolName = status.displayName,
                            currentIndex = protocolCandidateIndex + 1,
                            total = protocolCandidates.size,
                            failedNames = failedProtocolNames.toList(),
                        ),
                    )
                }
                updateState(BleConnectionState.Ready)
            }
            return
        }
        operationInProgress = true
        when (operation) {
            is BleProtocolOperation.Read -> {
                val characteristic = findCharacteristic(operation.characteristicUuid)
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
            is BleProtocolOperation.Write -> write(operation)
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, bytes: ByteArray, status: Int) {
        trace("读取回调 uuid=$uuid status=$status bytes=${bytes.toHexString()}")
        operationInProgress = false
        if (status != BluetoothGatt.GATT_SUCCESS) {
            handleProtocolOperationFailed("协议确认读取失败 status=$status")
            return
        }
        runCatching {
            activeProtocol?.onRead(uuid, bytes)?.let(operationQueue::addAll)
        }.onFailure {
            handleProtocolOperationFailed(it.message ?: "协议确认失败")
            return
        }
        executeNextOperation()
    }

    private fun handleProtocolOperationFailed(reason: String) {
        operationQueue.clear()
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
                    characteristicUuids = currentGatt.services
                        .flatMap { it.characteristics }
                        .map { it.uuid }
                        .toSet(),
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

    private fun updateState(state: BleConnectionState) {
        mainHandler.post { onState(state) }
    }

    private fun updateProtocolAttempt(status: ProtocolAttemptStatus) {
        mainHandler.post { onProtocolAttempt(status) }
    }

    private fun trace(message: String, error: Boolean = false) {
        if (error) Log.e(TAG, message) else Log.d(TAG, message)
        mainHandler.post { onLog(message) }
    }

    private fun String.toUuidOrNull(): UUID? = trim().takeIf { it.isNotEmpty() }?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }

    companion object {
        const val TAG = "AiToyBle"
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
