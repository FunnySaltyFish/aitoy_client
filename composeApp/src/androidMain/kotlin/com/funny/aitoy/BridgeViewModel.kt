package com.funny.aitoy

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funny.aitoy.ble.AndroidBleController
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.ble.ToyControlAction
import com.funny.aitoy.ble.ToyControlStyle
import com.funny.aitoy.chat.ToyTool
import com.funny.aitoy.chat.ToyToolResult
import com.funny.aitoy.core.kmp.ToastType
import com.funny.aitoy.core.kmp.appCtx
import com.funny.aitoy.core.kmp.openUrl
import com.funny.aitoy.core.kmp.toast
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.diagnostics.AiToyCrashReporter
import com.funny.aitoy.diagnostics.AiToyTraceUploader
import com.funny.aitoy.network.OkHttpUtils
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.CreateProtocolShareRequest
import com.funny.aitoy.relay.RelayClient
import com.funny.aitoy.relay.RelayCommand
import com.funny.aitoy.relay.RelayDevice
import com.funny.aitoy.update.AppUpdateInstaller
import com.funny.data_saver.core.mutableDataSaverStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt

data class RememberedToy(
    val name: String,
    val address: String,
    val protocolName: String,
    val lastSeenAt: Long,
    val manufacturerData: String = "",
    val scanRecordHex: String = "",
)

enum class ToyRuntimeState(val label: String) {
    Offline("未连接"),
    Connecting("正在连接"),
    Connected("已连接"),
    Failed("连接不上"),
}

data class ManagedToy(
    val name: String,
    val address: String,
    val protocolName: String,
    val runtimeState: ToyRuntimeState,
    val selected: Boolean,
    val current: Boolean,
    val saved: Boolean,
)

private fun ManagedToy.lastSeenKey(saved: List<RememberedToy>): Long =
    saved.firstOrNull { it.address == address }?.lastSeenAt ?: 0L

data class ProtocolPreset(
    val name: String,
    val description: String,
    val serviceUuid: String,
    val writeUuid: String,
    val notifyUuid: String,
    val commandTemplate: String,
    val stopTemplate: String,
    val writeWithResponse: Boolean,
)

data class AppUpdateState(
    val checked: Boolean = false,
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val latestVersionName: String = "",
    val message: String = "",
    val apkUrl: String = "",
    val downloadPageUrl: String = "",
    val fileSizeBytes: Long = 0,
    val updateLog: String = "",
)

private sealed interface RelaySequenceStep {
    data class Set(
        val mode: Int,
        val intensity: Int,
        val durationSec: Int?,
    ) : RelaySequenceStep

    data class Dual(
        val mode: Int,
        val internalIntensity: Int,
        val externalIntensity: Int,
        val durationSec: Int?,
    ) : RelaySequenceStep

    data class Pattern(val mode: Int, val durationSec: Int?) : RelaySequenceStep

    data class Intensity(val value: Int, val durationSec: Int?) : RelaySequenceStep

    data class Sleep(val durationSec: Int) : RelaySequenceStep

    data object Stop : RelaySequenceStep
}

private class SequenceScriptFormatException : IllegalArgumentException("序列脚本格式错误")

private const val LegacyDefaultUserToken = "ut_fishfish"
private const val DefaultCommunityUrl = "https://qm.qq.com/q/ytRVIe6O7m"
private const val DefaultTutorialUrl = "https://docs.qq.com/s/4dhBqBSu1u5900Qh7qvYHW/folder/YFZTdLuzaIxv"
private const val MaxSequenceDurationSec = 300

private fun generateDefaultUserToken(): String = "ut_${UUID.randomUUID().toString().replace("-", "")}"

class BridgeViewModel : ViewModel() {
    val devices = mutableStateListOf<ScannedBleDevice>()
    val logs = mutableStateListOf<String>()
    val toyRuntimeStates = mutableStateMapOf<String, ToyRuntimeState>()
    private val deviceConnectionStates = mutableStateMapOf<String, BleConnectionState>()
    private val deviceProtocolStatuses = mutableStateMapOf<String, BleProtocolStatus>()
    private val deviceProtocolAttempts = mutableStateMapOf<String, ProtocolAttemptStatus>()
    private val deviceDisplayNames = mutableStateMapOf<String, String>()
    private val deviceModes = mutableStateMapOf<String, Int>()
    private val deviceIntensities = mutableStateMapOf<String, Int>()
    private val deviceSecondaryIntensities = mutableStateMapOf<String, Int>()

    var connectionState by mutableStateOf(BleConnectionState.Idle)
        private set
    var scanning by mutableStateOf(false)
        private set
    var selectedAddress by mutableStateOf("")
        private set
    var selectedName by mutableStateOf("")
        private set
    var relayState by mutableStateOf("未连接")
        private set
    var protocolStatus by mutableStateOf(BleProtocolStatus())
        private set
    var protocolAttemptStatus by mutableStateOf(ProtocolAttemptStatus())
        private set
    var showAdvanced by mutableStateOf(false)
    var showGuide by mutableStateOf(false)
    var showTemplateLibrary by mutableStateOf(false)
    var shareMessage by mutableStateOf("")
        private set
    var importCode by mutableStateOf("")
    var importMessage by mutableStateOf("")
        private set
    var communityUrl by mutableStateOf(DefaultCommunityUrl)
        private set
    var tutorialUrl by mutableStateOf(DefaultTutorialUrl)
        private set
    var updateState by mutableStateOf(AppUpdateState())
        private set
    var updateDownloadProgress by mutableStateOf(0)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var crashNotice by mutableStateOf(AiToyCrashReporter.consumeLastCrashNotice().orEmpty())
        private set

    var serverUrl by mutableStateOf(OkHttpUtils.currentBaseUrl)
        private set
    var baseUrlDraft by mutableStateOf(OkHttpUtils.currentBaseUrl)
    var userTokenDraft by mutableStateOf("")
    var baseUrlMessage by mutableStateOf("")
        private set
    var developerMode: Boolean by mutableDataSaverStateOf(
        DataSaverUtils,
        "DEVELOPER_MODE",
        false,
    )
    var userToken: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "AITOY_USER_TOKEN",
        "",
    )

    var serviceUuid: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_SERVICE_UUID",
        "0000dddd-0000-1000-8000-00805f9b34fb",
    )
    var writeUuid: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_WRITE_UUID",
        "0000ddd1-0000-1000-8000-00805f9b34fb",
    )
    var notifyUuid: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_NOTIFY_UUID",
        "0000ddd2-0000-1000-8000-00805f9b34fb",
    )
    var commandTemplate: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_COMMAND_TEMPLATE",
        "AA 08 02 {intensity} C8 {checksum}",
    )
    var stopTemplate: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_STOP_TEMPLATE",
        "AA 0F 01 00 BA",
    )
    var writeWithResponse: Boolean by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_WRITE_WITH_RESPONSE",
        false,
    )
    var manualControlEnabled: Boolean by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_MANUAL_CONTROL_ENABLED",
        false,
    )
    var rememberedDevicesJson: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_REMEMBERED_DEVICES",
        "[]",
    )
    var mode by mutableStateOf(1)
    var intensity by mutableStateOf(1)
    var secondaryIntensity by mutableStateOf(1)
    var controlTrialStarted by mutableStateOf(false)
        private set
    var currentDeviceSaved by mutableStateOf(false)
        private set
    var showDeviceRemarkDialog by mutableStateOf(false)
        private set
    var deviceRemarkDraft by mutableStateOf("")
    private var editingRemarkAddress = ""
    private var editingRemarkProtocolName = ""
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var busyHint by mutableStateOf("")
        private set

    val rememberedDevices: List<RememberedToy>
        get() = runCatching {
            val array = JSONArray(rememberedDevicesJson)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RememberedToy(
                    name = item.optString("name").ifBlank { "我的小玩具" },
                    address = item.optString("address"),
                    protocolName = item.optString("protocolName").ifBlank { "已保存" },
                    lastSeenAt = item.optLong("lastSeenAt"),
                    manufacturerData = item.optString("manufacturerData"),
                    scanRecordHex = item.optString("scanRecordHex"),
                )
            }.filter { it.address.isNotBlank() }
        }.getOrDefault(emptyList())

    val highlightedDeviceAddress: String
        get() = selectedAddress.takeIf {
            connectionState == BleConnectionState.Connecting ||
                connectionState == BleConnectionState.Discovering ||
                connectionState == BleConnectionState.Ready ||
                connectionState == BleConnectionState.Disconnecting
        }.orEmpty()

    val managedToys: List<ManagedToy>
        get() {
            val saved = rememberedDevices
            val addresses = linkedSetOf<String>()
            saved.forEach { addresses += it.address }
            controllers.keys.forEach { addresses += it }
            if (selectedAddress.isNotBlank()) addresses += selectedAddress
            val rows = saved.map { toy ->
                ManagedToy(
                    name = toy.name,
                    address = toy.address,
                    protocolName = toy.protocolName,
                    runtimeState = toyRuntimeStates[toy.address] ?: ToyRuntimeState.Offline,
                    selected = selectedAddress == toy.address,
                    current = selectedAddress == toy.address,
                    saved = true,
                )
            }.toMutableList()
            addresses
                .filter { address -> rows.none { it.address == address } }
                .forEach { address ->
                    rows += ManagedToy(
                        name = when {
                            address == selectedAddress -> selectedName
                            deviceDisplayNames[address].orEmpty().isNotBlank() -> deviceDisplayNames[address].orEmpty()
                            else -> devices.firstOrNull { it.address == address }?.name.orEmpty()
                        }.ifBlank { "蓝牙设备" },
                        address = address,
                        protocolName = protocolStatusFor(address).displayName.ifBlank { "尚未识别" },
                        runtimeState = toyRuntimeStates[address] ?: if (address == selectedAddress) {
                            connectionState.toRuntimeState()
                        } else {
                            ToyRuntimeState.Offline
                        },
                        selected = selectedAddress == address,
                        current = selectedAddress == address,
                        saved = false,
                    )
                }
            return rows.sortedWith(
                compareByDescending<ManagedToy> { it.runtimeState == ToyRuntimeState.Connected }
                    .thenByDescending { it.current }
                    .thenByDescending { it.lastSeenKey(saved) }
            )
        }

    val protocolPresets = listOf(
        ProtocolPreset(
            name = "ANKNI / Mizzzee DDDD",
            description = "ANKNI 名称、DDDD/DDD1 服务的谜姬/安可尼设备；自动识别优先，也可手动套用此模板。",
            serviceUuid = "0000dddd-0000-1000-8000-00805f9b34fb",
            writeUuid = "0000ddd1-0000-1000-8000-00805f9b34fb",
            notifyUuid = "0000ddd2-0000-1000-8000-00805f9b34fb",
            commandTemplate = "AA 08 02 {intensity} C8 {checksum}",
            stopTemplate = "AA 0F 01 00 BA",
            writeWithResponse = false,
        ),
        ProtocolPreset(
            name = "Roselex / DSJM",
            description = "已验证的 DSJM/Roselex 系列模板，适合无法自动识别时手动尝试。",
            serviceUuid = "0000fffe-0000-1000-8000-00805f9b34fb",
            writeUuid = "0000fe02-0000-1000-8000-00805f9b34fb",
            notifyUuid = "",
            commandTemplate = "03 12 {intensity} 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
            stopTemplate = "03 12 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
            writeWithResponse = true,
        ),
        ProtocolPreset(
            name = "OhMiBod Esca 2",
            description = "OhMiBod 4.0 广播名设备可自动识别；这里保留手动写入模板。",
            serviceUuid = "a0d70001-4c16-4ba7-977a-d394920e13a3",
            writeUuid = "a0d70002-4c16-4ba7-977a-d394920e13a3",
            notifyUuid = "a0d70003-4c16-4ba7-977a-d394920e13a3",
            commandTemplate = "01 {intensity}",
            stopTemplate = "01 00",
            writeWithResponse = false,
        ),
        ProtocolPreset(
            name = "Pink Punch",
            description = "Pink_Punch 设备可自动识别；常量振动模式使用 09 + 强度。",
            serviceUuid = "0000ffe0-0000-1000-8000-00805f9b34fb",
            writeUuid = "0000ffe1-0000-1000-8000-00805f9b34fb",
            notifyUuid = "0000ffe2-0000-1000-8000-00805f9b34fb",
            commandTemplate = "09 {intensity}",
            stopTemplate = "09 00",
            writeWithResponse = false,
        ),
        ProtocolPreset(
            name = "通用 BLE 写入模板",
            description = "给群友分析后快速填写用，保留占位符和停止指令格式。",
            serviceUuid = "",
            writeUuid = "",
            notifyUuid = "",
            commandTemplate = "{intensity}",
            stopTemplate = "00",
            writeWithResponse = false,
        ),
    )

    val builtInTools = listOf(
        ToyTool(
            name = "get_toy_status",
            title = "查看设备状态",
            description = "读取当前连接状态、设备名称、协议、强度和节奏。",
        ),
        ToyTool(
            name = "set_toy_vibration",
            title = "调节设备",
            description = "按指定时间调节强度和节奏。",
        ),
        ToyTool(
            name = "stop_toy",
            title = "立即停止",
            description = "停止当前已连接的设备。",
        ),
        ToyTool(
            name = "stop_all_toys",
            title = "全部停止",
            description = "停止当前应用管理的所有设备。",
        ),
    )

    private val scanController = AndroidBleController(
        onDevice = ::onDeviceFound,
        onState = { },
        onProtocol = { },
        onProtocolAttempt = { },
        onLog = ::appendLog,
    )
    private val controllers = mutableMapOf<String, AndroidBleController>()
    private val keepAliveTasks = mutableMapOf<String, Runnable>()
    private val autoStopTasks = mutableMapOf<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingControlAction: ToyControlAction? = null
    private val controlTrial = Runnable { pendingControlAction?.let(::sendLocalControlAction) }
    private val relay = RelayClient(
        onState = {
            relayState = it
            if (it == "已在线") {
                syncRelayDevice()
                toast("手机已上线", ToastType.Success)
            }
        },
        onLog = ::appendLog,
        onCommand = ::handleRelayCommand,
    )
    private val relaySequenceJobs = mutableMapOf<String, Job>()
    private var debugLogTapCount = 0
    private var autoOnlineAddress = ""

    init {
        OkHttpUtils.ensureDefaultNetworkConfig()
        refreshBaseUrlState()
        ensureDefaultUserToken()
        userTokenDraft = userToken
        updateTraceContext()
        checkAppConfig()
    }

    private fun controllerFor(address: String): AndroidBleController =
        controllers.getOrPut(address) {
            AndroidBleController(
                onDevice = ::onDeviceFound,
                onState = { onDeviceConnectionState(address, it) },
                onProtocol = { onDeviceProtocol(address, it) },
                onProtocolAttempt = { onDeviceProtocolAttempt(address, it) },
                onLog = ::appendLog,
            )
        }

    private fun onDeviceConnectionState(address: String, state: BleConnectionState) {
        deviceConnectionStates[address] = state
        toyRuntimeStates[address] = state.toRuntimeState()
        if (address == selectedAddress) {
            connectionState = state
            updateTraceContext()
        }
        when (state) {
            BleConnectionState.Ready -> {
                if (address == selectedAddress) {
                    currentDeviceSaved = rememberedDevices.any { toy -> toy.address == selectedAddress }
                }
                syncRelayDevice()
                if (address == selectedAddress) autoOnlineSavedDevice()
            }
            BleConnectionState.Error -> {
                if (address == selectedAddress) {
                    busyHint = if (deviceProtocolAttempts[address]?.exhausted == true) {
                        "已尝试所有协议，目前仍不支持，请加群提交反馈，开发者会尝试支持。"
                    } else {
                        "设备连接不上，请确认设备已开机并靠近手机"
                    }
                }
                syncRelayDevice()
                autoOfflineWhenNoConnectedSavedDevice()
            }
            BleConnectionState.Idle -> {
                syncRelayDevice()
                autoOfflineWhenNoConnectedSavedDevice()
            }
            else -> Unit
        }
    }

    private fun onDeviceProtocol(address: String, status: BleProtocolStatus) {
        deviceProtocolStatuses[address] = status
        if (address == selectedAddress) {
            protocolStatus = status
            clampCurrentControlValues()
            updateTraceContext()
        }
    }

    private fun onDeviceProtocolAttempt(address: String, status: ProtocolAttemptStatus) {
        deviceProtocolAttempts[address] = status
        if (address == selectedAddress) {
            protocolAttemptStatus = status
        }
    }

    private fun ensureDefaultUserToken() {
        if (userToken.isBlank() || (!developerMode && userToken == LegacyDefaultUserToken)) {
            userToken = generateDefaultUserToken()
        }
    }

    fun toggleScan() {
        errorMessage = null
        if (scanning) {
            scanController.stopScan()
            scanning = false
        } else {
            devices.clear()
            scanController.startScan()
            scanning = true
        }
    }

    fun connect(device: ScannedBleDevice) = runAction {
        if (!device.controllable) {
            errorMessage = "这个广播暂时无法控制"
            appendLog("跳过不可连接且未识别的广播设备：name=${device.name} address=${device.address}")
            return@runAction
        }
        if (toyRuntimeStates[device.address] == ToyRuntimeState.Connected) {
            selectDevice(device.address)
            busyHint = "已切换到 ${device.name}"
            return@runAction
        }
        selectedAddress = device.address
        selectedName = device.name
        deviceDisplayNames[device.address] = device.name
        toyRuntimeStates[device.address] = ToyRuntimeState.Connecting
        deviceConnectionStates[device.address] = BleConnectionState.Connecting
        protocolAttemptStatus = ProtocolAttemptStatus()
        protocolStatus = BleProtocolStatus()
        deviceProtocolAttempts[device.address] = protocolAttemptStatus
        deviceProtocolStatuses[device.address] = protocolStatus
        updateTraceContext()
        controlTrialStarted = false
        currentDeviceSaved = rememberedDevices.any { it.address == device.address }
        showDeviceRemarkDialog = false
        deviceRemarkDraft =
            rememberedDevices.firstOrNull { it.address == device.address }?.name.orEmpty()
        scanning = false
        busyHint = "正在为你连接 ${device.name}"
        scanController.stopScan()
        controllerFor(device.address).connect(device, currentTemplate())
    }

    fun connectRemembered(toy: RememberedToy) {
        if (toyRuntimeStates[toy.address] == ToyRuntimeState.Connected) {
            selectDevice(toy.address)
            busyHint = "已切换到 ${toy.name}"
            return
        }
        connect(
            ScannedBleDevice(
                name = toy.name,
                address = toy.address,
                rssi = 0,
                connectable = true,
                manufacturerData = toy.manufacturerData,
                scanRecordHex = toy.scanRecordHex,
            )
        )
    }

    fun selectDevice(address: String) {
        if (address.isBlank()) return
        selectedAddress = address
        selectedName = deviceDisplayNames[address]
            ?: rememberedDevices.firstOrNull { it.address == address }?.name
            ?: devices.firstOrNull { it.address == address }?.name
            ?: "蓝牙设备"
        connectionState = deviceConnectionStates[address] ?: BleConnectionState.Idle
        protocolStatus = deviceProtocolStatuses[address] ?: BleProtocolStatus()
        protocolAttemptStatus = deviceProtocolAttempts[address] ?: ProtocolAttemptStatus()
        mode = deviceModes[address] ?: 1
        intensity = deviceIntensities[address] ?: 1
        secondaryIntensity = deviceSecondaryIntensities[address] ?: intensity
        currentDeviceSaved = rememberedDevices.any { it.address == address }
        controlTrialStarted = false
        showDeviceRemarkDialog = false
        updateTraceContext()
        syncRelayDevice()
    }

    fun disconnect() {
        val address = selectedAddress
        if (address.isBlank()) return
        busyHint = ""
        protocolAttemptStatus = ProtocolAttemptStatus()
        controlTrialStarted = false
        showDeviceRemarkDialog = false
        toyRuntimeStates[address] = ToyRuntimeState.Offline
        deviceConnectionStates[address] = BleConnectionState.Idle
        mainHandler.removeCallbacks(controlTrial)
        cancelAutoStop(address)
        cancelKeepAlive(address)
        controllers[address]?.disconnect()
        connectionState = BleConnectionState.Idle
        syncRelayDevice()
        autoOfflineWhenNoConnectedSavedDevice()
    }

    fun updateMode(value: Int) {
        val next = value.coerceIn(1, protocolStatus.modeMax.coerceAtLeast(1))
        if (mode == next) return
        mode = next
        selectedAddress.takeIf { it.isNotBlank() }?.let { deviceModes[it] = next }
        scheduleControlTrial(patternAction(next))
    }

    fun updateMode(address: String, value: Int) {
        if (address != selectedAddress) selectDevice(address)
        updateMode(value)
    }

    fun updateIntensity(value: Int) {
        val next = value.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        if (intensity == next) return
        intensity = next
        selectedAddress.takeIf { it.isNotBlank() }?.let { deviceIntensities[it] = next }
        scheduleControlTrial(intensityAction(next))
    }

    fun updateIntensity(address: String, value: Int) {
        if (address != selectedAddress) selectDevice(address)
        updateIntensity(value)
    }

    fun updateChannelIntensity(address: String, channelIndex: Int, value: Int) {
        if (address != selectedAddress) selectDevice(address)
        val next = value.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        val targetAddress = selectedAddress.ifBlank { address }
        when (channelIndex) {
            0 -> {
                if (intensity == next) return
                intensity = next
                deviceIntensities[targetAddress] = next
            }
            else -> {
                if (secondaryIntensity == next) return
                secondaryIntensity = next
                deviceSecondaryIntensities[targetAddress] = next
            }
        }
        scheduleControlTrial(dualIntensityAction(mode, intensity, secondaryIntensity, protocolStatus))
    }

    fun stopDevice() = runAction {
        val address = selectedAddress
        if (address.isBlank()) return@runAction
        busyHint = "已停止"
        mainHandler.removeCallbacks(controlTrial)
        stopDevice(address)
        syncRelayDevice()
    }

    fun stopAllDevices() = runAction {
        busyHint = "已全部停止"
        mainHandler.removeCallbacks(controlTrial)
        autoStopTasks.keys.toList().forEach(::cancelAutoStop)
        keepAliveTasks.keys.toList().forEach(::cancelKeepAlive)
        controllers.values.forEach { controller ->
            runCatching { controller.stopDevice() }
        }
        syncRelayDevice()
    }

    fun stopDevice(address: String) = runAction {
        stopToyAtAddress(address)
        if (address == selectedAddress) busyHint = "已停止"
        syncRelayDevice()
    }

    fun confirmCurrentDeviceWorks() {
        if (selectedAddress.isBlank()) return
        recordProtocolAdapterFeedback("success")
        deviceRemarkDraft = rememberedDevices.firstOrNull { it.address == selectedAddress }?.name
            ?: selectedName.ifBlank { "我的小玩具" }
        editingRemarkAddress = selectedAddress
        editingRemarkProtocolName = protocolStatus.displayName.ifBlank { "已保存" }
        showDeviceRemarkDialog = true
    }

    fun reportCurrentDeviceNotWorking() {
        val address = selectedAddress
        if (address.isBlank()) return
        mainHandler.removeCallbacks(controlTrial)
        cancelAutoStop(address)
        cancelKeepAlive(address)
        recordProtocolAdapterFeedback("no_response")
        val fallback = controllers[address]?.reportCurrentProtocolNoResponse()
        controlTrialStarted = false
        busyHint = when {
            fallback == null -> {
                "已记录反馈，请加群提交设备信息"
            }
            fallback.switchedToNext -> {
                "已切换协议，请从低强度再试一次"
            }
            else -> {
                "已尝试所有协议，目前仍不支持，请加群提交反馈，开发者会尝试支持。"
            }
        }
        syncRelayDevice()
    }

    fun editRememberedDevice(toy: RememberedToy) {
        deviceRemarkDraft = toy.name
        editingRemarkAddress = toy.address
        editingRemarkProtocolName = toy.protocolName
        showDeviceRemarkDialog = true
    }

    fun deleteRememberedDevice(toy: RememberedToy) {
        val merged = JSONArray()
        rememberedDevices
            .filterNot { it.address == toy.address }
            .forEach {
                merged.put(
                    JSONObject()
                        .put("name", it.name)
                        .put("address", it.address)
                        .put("protocolName", it.protocolName)
                        .put("lastSeenAt", it.lastSeenAt)
                        .put("manufacturerData", it.manufacturerData)
                        .put("scanRecordHex", it.scanRecordHex),
                )
            }
        rememberedDevicesJson = merged.toString()
        toyRuntimeStates.remove(toy.address)
        if (toy.address == selectedAddress) {
            currentDeviceSaved = false
            syncRelayDevice()
            autoOfflineWhenNoConnectedSavedDevice()
        }
        busyHint = "已从我的设备移除"
    }

    fun dismissDeviceRemarkDialog() {
        showDeviceRemarkDialog = false
        editingRemarkAddress = ""
        editingRemarkProtocolName = ""
    }

    fun saveDeviceRemark() {
        val address = editingRemarkAddress.ifBlank { selectedAddress }
        if (address.isBlank()) return
        val fallbackName = if (address == selectedAddress) selectedName else "我的小玩具"
        rememberDevice(
            address = address,
            remark = deviceRemarkDraft.trim().ifBlank { fallbackName.ifBlank { "我的小玩具" } },
            protocolName = editingRemarkProtocolName.ifBlank { protocolStatus.displayName.ifBlank { "已保存" } },
        )
        if (address == selectedAddress) {
            currentDeviceSaved = true
            controlTrialStarted = false
            autoOnlineAddress = ""
            autoOnlineSavedDevice()
        }
        syncRelayDevice()
        showDeviceRemarkDialog = false
        editingRemarkAddress = ""
        editingRemarkProtocolName = ""
        busyHint = "已保存到我的设备"
        toast("已保存，手机正在上线", ToastType.Success)
    }

    fun getToyStatusForChat(): ToyToolResult = ToyToolResult(
        ok = true,
        message = buildString {
            append("设备状态：${connectionState.label}")
            if (selectedName.isNotBlank()) append("；设备：$selectedName")
            append("；协议：${protocolStatus.displayName}")
            append("；当前强度：$intensity")
            if (protocolStatus.supportsMode) append("；当前节奏：$mode")
            append("；可控制：${if (protocolStatus.controllable) "是" else "否"}")
        },
    )

    fun setToyVibrationForChat(
        intensityPercent: Int,
        mode: Int,
        durationSec: Int,
    ): ToyToolResult = runCatching {
        val safeDuration = durationSec.coerceIn(1, 15)
        sendToySet(
            intensityPercent = intensityPercent.coerceIn(0, 100),
            requestedMode = mode.coerceAtLeast(1),
            autoStopSec = safeDuration,
        )
        ToyToolResult(true, "已调节设备，持续 $safeDuration 秒。")
    }.getOrElse {
        ToyToolResult(false, it.message ?: "操作没有完成。")
    }

    fun stopToyForChat(all: Boolean = false): ToyToolResult = runCatching {
        sendToyStop(all = all)
        ToyToolResult(true, if (all) "已全部停止。" else "已停止。")
    }.getOrElse {
        ToyToolResult(false, it.message ?: "操作没有完成。")
    }

    fun clearLogs() {
        logs.clear()
    }

    fun connectRelay() {
        AiToyForegroundService.start(appCtx)
        relay.connect(OkHttpUtils.currentBaseUrl, userToken)
    }

    fun resumeRelay() {
        relay.reconnectNow()
    }

    fun disconnectRelay() {
        relay.disconnect()
        AiToyForegroundService.stop(appCtx)
    }

    fun openBatteryConnectionSettings() {
        val packageName = appCtx.packageName
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = appCtx.getSystemService(PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        runCatching {
            appCtx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            appCtx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun dismissCrashNotice() {
        crashNotice = ""
    }

    fun openUpdateInBrowser() {
        val url = updateState.downloadPageUrl.ifBlank { updateState.apkUrl }
        if (url.isBlank()) return
        appCtx.openUrl(url)
    }

    fun openAppDownloadPage() {
        appCtx.openUrl(OkHttpUtils.externalUrl("download"))
    }

    fun openTutorialDocument() {
        appCtx.openUrl(tutorialUrl.ifBlank { DefaultTutorialUrl })
    }

    fun openCommunityGroup() {
        appCtx.openUrl(communityUrl.ifBlank { DefaultCommunityUrl })
    }

    fun onDebugLogTitleClick() {
        if (developerMode) return
        debugLogTapCount += 1
        baseUrlMessage = if (debugLogTapCount >= 5) {
            developerMode = true
            "开发者模式已开启"
        } else {
            "再点 ${5 - debugLogTapCount} 次可打开开发者设置"
        }
    }

    fun useProductionBaseUrl() {
        if (!developerMode) return
        OkHttpUtils.useProductionBaseUrl()
        refreshBaseUrlState()
        baseUrlMessage = "已切换到线上地址，新请求会立即使用；已在线的连接请重新上线。"
        checkAppConfig()
    }

    fun useLocalBaseUrl() {
        if (!developerMode) return
        OkHttpUtils.useLocalBaseUrl()
        refreshBaseUrlState()
        baseUrlMessage = "已切换到本地地址，新请求会立即使用；已在线的连接请重新上线。"
        checkAppConfig()
    }

    fun saveBaseUrl() {
        if (!developerMode) return
        if (OkHttpUtils.saveBaseUrl(baseUrlDraft)) {
            refreshBaseUrlState()
            baseUrlMessage = "已保存，新请求会立即使用；已在线的连接请重新上线。"
            checkAppConfig()
        } else {
            baseUrlMessage = "地址格式不正确，请输入 http 或 https 地址。"
        }
    }

    fun saveUserToken() {
        if (!developerMode) return
        val token = userTokenDraft.trim()
        if (token.isBlank()) {
            baseUrlMessage = "Token 不能为空。"
            return
        }
        userToken = token
        userTokenDraft = token
        updateTraceContext()
        baseUrlMessage = "Token 已保存；手机已经上线时，请重新上线。"
    }

    fun downloadAndInstallUpdate() {
        val url = updateState.apkUrl
        if (url.isBlank() || updateDownloading) return
        updateDownloading = true
        updateDownloadProgress = 0
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                AppUpdateInstaller.downloadAndInstall(
                    url = url,
                    versionName = updateState.latestVersionName.ifBlank { "latest" },
                ) { progress ->
                    viewModelScope.launch { updateDownloadProgress = progress }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    updateDownloading = false
                    updateDownloadProgress = 100
                    updateState = AppUpdateState(checked = true)
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    updateDownloading = false
                    updateState = updateState.copy(message = it.message ?: "下载失败")
                }
            }
        }
    }

    fun checkAppConfig() {
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.appService.config(APP_VERSION_CODE) }
            }.onSuccess { config ->
                communityUrl = config.communityUrl.ifBlank { DefaultCommunityUrl }
                tutorialUrl = config.tutorialUrl.ifBlank { DefaultTutorialUrl }
                val update = config.update
                val updateAvailable = update.latestVersionCode > APP_VERSION_CODE
                val forceUpdate = update.forceUpdate &&
                    (update.minSupportedVersionCode > APP_VERSION_CODE || updateAvailable)
                updateState = AppUpdateState(
                    checked = true,
                    updateAvailable = update.updateAvailable && updateAvailable,
                    forceUpdate = forceUpdate,
                    latestVersionName = update.latestVersionName,
                    message = update.msg,
                    apkUrl = update.downloadUrl.ifBlank { update.apkUrl },
                    downloadPageUrl = update.downloadPageUrl,
                    fileSizeBytes = update.fileSizeBytes,
                    updateLog = update.updateLog,
                )
            }.onFailure {
                updateState = AppUpdateState(checked = true, message = it.message ?: "检查更新失败")
            }
        }
    }

    fun applyPreset(preset: ProtocolPreset) {
        serviceUuid = preset.serviceUuid
        writeUuid = preset.writeUuid
        notifyUuid = preset.notifyUuid
        commandTemplate = preset.commandTemplate
        stopTemplate = preset.stopTemplate
        writeWithResponse = preset.writeWithResponse
        manualControlEnabled = true
        showAdvanced = true
        showTemplateLibrary = false
        importMessage = "已选择 ${preset.name}，下一步重新连接设备"
        busyHint = "手动指令已准备好，请重新连接"
        appendLog("已应用模板：${preset.name}")
    }

    fun importFromLink(link: String?) {
        val resolved = link?.trim().orEmpty()
        if (resolved.isBlank()) return
        val uri = runCatching { Uri.parse(resolved) }.getOrNull()
        val server = uri?.getQueryParameter("server")?.trim().orEmpty()
        if (developerMode && server.isNotBlank() && OkHttpUtils.isHttpUrl(OkHttpUtils.normalizeBaseUrl(server))) {
            OkHttpUtils.saveBaseUrl(server)
            refreshBaseUrlState()
        }
        importCode = resolved
        showAdvanced = true
        showTemplateLibrary = false
        importSharedTemplate()
    }

    fun shareCurrentTemplate() {
        shareMessage = "正在生成分享链接..."
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.protocolShareService.create(
                        CreateProtocolShareRequest(
                            title = selectedName.ifBlank { "设备指令" },
                            baseUrl = publicBaseUrl(),
                            payload = buildJsonObject {
                                put("serviceUuid", JsonPrimitive(serviceUuid.trim()))
                                put("writeUuid", JsonPrimitive(writeUuid.trim()))
                                put("notifyUuid", JsonPrimitive(notifyUuid.trim()))
                                put("commandTemplate", JsonPrimitive(commandTemplate.trim()))
                                put("stopTemplate", JsonPrimitive(stopTemplate.trim()))
                                put("writeWithResponse", JsonPrimitive(writeWithResponse))
                                put("manualControlEnabled", JsonPrimitive(true))
                                put("sourceDeviceName", JsonPrimitive(selectedName.ifBlank { "我的小玩具" }))
                                put("protocolName", JsonPrimitive(protocolStatus.displayName.ifBlank { "手动指令" }))
                            },
                        )
                    )
                }
            }.onSuccess { result ->
                shareMessage = result.url.ifBlank { result.importCode }
                appendLog("已生成指令分享：$shareMessage")
            }.onFailure {
                shareMessage = it.message ?: "分享失败"
            }
        }
    }

    fun importSharedTemplate() {
        val code = importCode.trim()
        if (code.isBlank()) {
            importMessage = "请先粘贴分享链接或口令"
            return
        }
        importMessage = "正在导入..."
        val uri = runCatching { Uri.parse(code) }.getOrNull()
        val linkBaseUrl = if (uri?.scheme == "http" || uri?.scheme == "https") {
            "${uri.scheme}://${uri.authority}"
        } else {
            uri?.getQueryParameter("server")?.takeIf {
                it.isNotBlank() && OkHttpUtils.isHttpUrl(OkHttpUtils.normalizeBaseUrl(it))
            }
        }?.trimEnd('/')
        if (developerMode && linkBaseUrl != null) {
            OkHttpUtils.saveBaseUrl(linkBaseUrl)
            refreshBaseUrlState()
        }
        val shareId = code.trimEnd('/')
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('=')
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.protocolShareService.get(shareId) }
            }.onSuccess { result ->
                val payload = result.payload
                serviceUuid = payload.stringValue("serviceUuid", serviceUuid)
                writeUuid = payload.stringValue("writeUuid", writeUuid)
                notifyUuid = payload.stringValue("notifyUuid", notifyUuid)
                commandTemplate = payload.stringValue("commandTemplate", commandTemplate)
                stopTemplate = payload.stringValue("stopTemplate", stopTemplate)
                writeWithResponse = payload.booleanValue("writeWithResponse", writeWithResponse)
                manualControlEnabled = true
                showAdvanced = true
                showTemplateLibrary = false
                importMessage = "已导入，下一步重新连接设备"
                busyHint = "手动指令已准备好，请重新连接"
                appendLog("已导入分享指令：${result.title}")
            }.onFailure {
                importMessage = it.message ?: "导入失败"
            }
        }
    }

    fun reconnectWithCurrentTemplate() {
        if (selectedAddress.isBlank()) {
            if (!scanning) toggleScan()
            busyHint = "请选择要连接的设备"
            return
        }
        val device = devices.firstOrNull { it.address == selectedAddress }
            ?: ScannedBleDevice(
                name = selectedName.ifBlank { "蓝牙设备" },
                address = selectedAddress,
                rssi = 0,
                connectable = true,
            )
        disconnect()
        connect(device)
    }

    private fun JsonObject.stringValue(key: String, default: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull ?: default
    }

    private fun JsonObject.booleanValue(key: String, default: Boolean): Boolean {
        return this[key]?.jsonPrimitive?.booleanOrNull ?: default
    }

    private fun currentTemplate() = ProtocolTemplate(
        serviceUuid = serviceUuid.trim(),
        writeUuid = writeUuid.trim(),
        notifyUuid = notifyUuid.trim(),
        writeWithResponse = writeWithResponse,
        manualControlEnabled = manualControlEnabled,
        commandTemplate = commandTemplate,
        stopTemplate = stopTemplate,
    )

    private fun onDeviceFound(device: ScannedBleDevice) {
        val index = devices.indexOfFirst { it.address == device.address }
        if (index >= 0) devices[index] = device else devices.add(device)
        devices.sortByDescending { it.rssi }
        toyRuntimeStates.putIfAbsent(device.address, ToyRuntimeState.Offline)
    }

    private fun appendLog(message: String) {
        AiToyTraceUploader.recordBle(message)
        mainHandler.post {
            logs += message
            while (logs.size > 200) logs.removeAt(0)
        }
    }

    private fun updateTraceContext() {
        AiToyTraceUploader.updateContext(
            userToken = userToken,
            selectedDeviceName = selectedName,
            selectedDeviceAddress = selectedAddress,
            connectionState = connectionState.name,
            protocolId = protocolStatus.id,
            protocolName = protocolStatus.displayName,
        )
    }

    private fun syncRelayDevice() {
        val saved = rememberedDevices
        val addresses = linkedSetOf<String>()
        saved.forEach { addresses += it.address }
        controllers.keys.forEach { addresses += it }
        if (selectedAddress.isNotBlank()) addresses += selectedAddress
        if (addresses.isEmpty()) return
        val defaultAddress = defaultConnectedAddress().ifBlank { saved.firstOrNull()?.address.orEmpty() }
        relay.syncDevices(
            addresses.map { address ->
                val remembered = saved.firstOrNull { it.address == address }
                val isCurrent = address == selectedAddress
                val status = protocolStatusFor(address)
                RelayDevice(
                    deviceId = deviceIdForAddress(address),
                    displayName = when {
                        isCurrent -> selectedName.ifBlank { remembered?.name.orEmpty() }.ifBlank { "蓝牙设备" }
                        deviceDisplayNames[address].orEmpty().isNotBlank() -> deviceDisplayNames[address].orEmpty()
                        else -> remembered?.name.orEmpty().ifBlank { "蓝牙设备" }
                    },
                    connected = deviceConnectionStates[address] == BleConnectionState.Ready,
                    isDefault = address == defaultAddress,
                    protocolName = when {
                        status.displayName.isNotBlank() && status.displayName != "尚未识别" -> status.displayName
                        isCurrent -> protocolStatus.displayName.ifBlank { remembered?.protocolName.orEmpty() }
                        else -> remembered?.protocolName.orEmpty()
                    }.ifBlank { "尚未识别" },
                    intensity = if (isCurrent) intensity else deviceIntensities[address] ?: 0,
                    mode = if (isCurrent) mode else deviceModes[address] ?: 1,
                    intensityMax = status.intensityMax,
                    modeMax = status.modeMax,
                    modeNames = status.modeNames,
                    controlStyle = status.controlStyle.name,
                )
            }
        )
    }

    private fun handleRelayCommand(command: RelayCommand) {
        if (command.action != "sequence") {
            relay.commandResult(command.commandId, false, "暂不支持这个操作")
            return
        }
        val targetAddress = if (command.deviceId != null) {
            addressForDeviceId(command.deviceId) ?: run {
                relay.commandResult(command.commandId, false, "请先在 App 中连接这台设备")
                return
            }
        } else {
            defaultConnectedAddress()
        }
        if (targetAddress.isBlank() || deviceConnectionStates[targetAddress] != BleConnectionState.Ready) {
            relay.commandResult(command.commandId, false, "设备还没有连接")
            return
        }
        val steps = runCatching {
            parseRelaySequenceScript(command.script.orEmpty(), command.defaultDurationSec)
        }.getOrElse {
            relay.commandResult(command.commandId, false, "序列脚本格式错误")
            return
        }
        val previousJob = relaySequenceJobs[targetAddress]
        val job = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            relay.commandResult(command.commandId, true, "序列已执行")
            try {
                runCatching {
                    executeRelaySequence(targetAddress, steps, command.defaultDurationSec)
                }.onFailure {
                    if (it is CancellationException) return@launch
                    appendLog("序列执行失败：${it.message ?: "命令执行失败"}")
                }
            } finally {
                if (relaySequenceJobs[targetAddress] == coroutineContext[Job]) {
                    relaySequenceJobs.remove(targetAddress)
                }
            }
        }
        relaySequenceJobs[targetAddress] = job
    }

    private suspend fun executeRelaySequence(
        address: String,
        steps: List<RelaySequenceStep>,
        defaultDurationSec: Int?,
    ) {
        val safeDefaultDuration = (defaultDurationSec ?: 30).coerceIn(1, MaxSequenceDurationSec)
        var running = false
        try {
            steps.forEach { step ->
                when (step) {
                    is RelaySequenceStep.Set -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        delayControlWarmup(address)
                        val action = sendToySet(
                            intensityPercent = step.intensity,
                            requestedMode = step.mode,
                            autoStopSec = durationSec,
                            address = address,
                            scheduleAutoStop = false,
                        )
                        running = actionKeepsDeviceRunning(action)
                        delay(durationSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
                    }
                    is RelaySequenceStep.Dual -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val status = protocolStatusFor(address)
                        val mappedMode = step.mode.coerceIn(1, status.modeMax.coerceAtLeast(1))
                        val mappedInternalIntensity = mapIntensityPercent(step.internalIntensity, address)
                        val mappedExternalIntensity = mapIntensityPercent(step.externalIntensity, address)
                        val action = dualIntensityAction(
                            currentMode = mappedMode,
                            primaryIntensity = mappedInternalIntensity,
                            secondaryIntensity = mappedExternalIntensity,
                            status = status,
                        )
                        delayControlWarmup(address)
                        sendToyAction(action, durationSec, address, scheduleAutoStop = false)
                        deviceModes[address] = mappedMode
                        deviceIntensities[address] = mappedInternalIntensity
                        deviceSecondaryIntensities[address] = mappedExternalIntensity
                        if (address == selectedAddress) {
                            mode = mappedMode
                            intensity = mappedInternalIntensity
                            secondaryIntensity = mappedExternalIntensity
                        }
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        delay(durationSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
                    }
                    is RelaySequenceStep.Pattern -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val status = protocolStatusFor(address)
                        val mappedMode = step.mode.coerceIn(1, status.modeMax.coerceAtLeast(1))
                        val action = patternAction(
                            nextMode = mappedMode,
                            currentIntensity = intensityForAddress(address),
                            currentSecondaryIntensity = secondaryIntensityForAddress(address),
                            status = status,
                        )
                        delayControlWarmup(address)
                        sendToyAction(action, durationSec, address, scheduleAutoStop = false)
                        deviceModes[address] = mappedMode
                        if (address == selectedAddress) mode = mappedMode
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        delay(durationSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
                    }
                    is RelaySequenceStep.Intensity -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val mappedIntensity = mapIntensityPercent(step.value, address)
                        val status = protocolStatusFor(address)
                        val action = intensityAction(
                            currentMode = modeForAddress(address),
                            nextIntensity = mappedIntensity,
                            nextSecondaryIntensity = mappedIntensity,
                            status = status,
                        )
                        delayControlWarmup(address)
                        sendToyAction(action, durationSec, address, scheduleAutoStop = false)
                        deviceIntensities[address] = mappedIntensity
                        if (status.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
                            deviceSecondaryIntensities[address] = mappedIntensity
                        }
                        if (address == selectedAddress) intensity = mappedIntensity
                        if (address == selectedAddress && status.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
                            secondaryIntensity = mappedIntensity
                        }
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        delay(durationSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
                    }
                    is RelaySequenceStep.Sleep -> delay(step.durationSec.coerceIn(0, 300) * 1_000L)
                    RelaySequenceStep.Stop -> {
                        sendToyStop(address)
                        running = false
                    }
                }
            }
        } finally {
            if (running) sendToyStop(address)
        }
    }

    private fun parseRelaySequenceScript(
        script: String,
        defaultDurationSec: Int?,
    ): List<RelaySequenceStep> {
        val parts = script.split(';').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) throw SequenceScriptFormatException()
        return parts.map { part ->
            when {
                part == "stop()" -> RelaySequenceStep.Stop
                part.startsWith("sleep(") && part.endsWith(")") -> {
                    val seconds = part.removePrefix("sleep(")
                        .removeSuffix(")")
                        .trim()
                        .toIntOrNull()
                        ?: throw SequenceScriptFormatException()
                    RelaySequenceStep.Sleep(seconds.coerceIn(0, 300))
                }
                part.startsWith("set(") && part.endsWith(")") -> {
                    val args = parseSequenceArguments(part.removePrefix("set(").removeSuffix(")"))
                    val mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException()
                    val intensity = args["intensity"]?.coerceIn(0, 100) ?: throw SequenceScriptFormatException()
                    val duration = args["duration"]?.coerceIn(1, MaxSequenceDurationSec)
                    RelaySequenceStep.Set(mode = mode, intensity = intensity, durationSec = duration)
                }
                part.startsWith("dual(") && part.endsWith(")") -> {
                    val args = parseSequenceArguments(part.removePrefix("dual(").removeSuffix(")"))
                    RelaySequenceStep.Dual(
                        mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException(),
                        internalIntensity = args["internal"]?.coerceIn(0, 100)
                            ?: throw SequenceScriptFormatException(),
                        externalIntensity = args["external"]?.coerceIn(0, 100)
                            ?: throw SequenceScriptFormatException(),
                        durationSec = args["duration"]?.coerceIn(1, MaxSequenceDurationSec),
                    )
                }
                part.startsWith("pattern(") && part.endsWith(")") -> {
                    val args = parseSequenceArguments(part.removePrefix("pattern(").removeSuffix(")"))
                    RelaySequenceStep.Pattern(
                        mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException(),
                        durationSec = args["duration"]?.coerceIn(1, MaxSequenceDurationSec),
                    )
                }
                part.startsWith("intensity(") && part.endsWith(")") -> {
                    val args = parseSequenceArguments(part.removePrefix("intensity(").removeSuffix(")"))
                    RelaySequenceStep.Intensity(
                        value = args["value"]?.coerceIn(0, 100)
                            ?: args["intensity"]?.coerceIn(0, 100)
                            ?: throw SequenceScriptFormatException(),
                        durationSec = args["duration"]?.coerceIn(1, MaxSequenceDurationSec),
                    )
                }
                else -> throw SequenceScriptFormatException()
            }
        }
    }

    private fun parseSequenceArguments(text: String): Map<String, Int> {
        if (text.isBlank()) throw SequenceScriptFormatException()
        return text.split(',')
            .associate { entry ->
                val key = entry.substringBefore('=', missingDelimiterValue = "").trim()
                val value = entry.substringAfter('=', missingDelimiterValue = "").trim().toIntOrNull()
                if (key.isBlank() || value == null) throw SequenceScriptFormatException()
                key to value
            }
    }

    private fun sendToySet(
        intensityPercent: Int,
        requestedMode: Int,
        autoStopSec: Int,
        address: String = selectedAddress,
        scheduleAutoStop: Boolean = true,
    ): ToyControlAction {
        val status = protocolStatusFor(address)
        val mappedIntensity = mapIntensityPercent(intensityPercent, address)
        val mappedMode = requestedMode.coerceIn(1, status.modeMax.coerceAtLeast(1))
        val action = when (status.controlStyle) {
            ToyControlStyle.PatternOnly -> ToyControlAction.Pattern(mappedMode)
            ToyControlStyle.IntensityOnly -> ToyControlAction.Intensity(mappedIntensity)
            ToyControlStyle.ExclusivePatternOrIntensity -> {
                if (mappedIntensity > 0) ToyControlAction.Intensity(mappedIntensity)
                else ToyControlAction.Pattern(mappedMode)
            }
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(mappedMode, mappedIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                ToyControlAction.DualMotor(mappedMode, mappedIntensity, mappedIntensity)
        }
        sendToyAction(action, autoStopSec, address, scheduleAutoStop)
        deviceModes[address] = mappedMode
        deviceIntensities[address] = mappedIntensity
        if (status.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
            deviceSecondaryIntensities[address] = mappedIntensity
        }
        if (address == selectedAddress) {
            mode = mappedMode
            intensity = mappedIntensity
            if (status.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
                secondaryIntensity = mappedIntensity
            }
        }
        syncRelayDevice()
        return action
    }

    private fun mapIntensityPercent(intensityPercent: Int, address: String = selectedAddress): Int {
        val maxIntensity = protocolStatusFor(address).intensityMax.coerceAtLeast(1)
        return ((intensityPercent.coerceIn(0, 100) / 100.0) * maxIntensity)
            .roundToInt()
            .coerceIn(0, maxIntensity)
    }

    private fun sendToyAction(
        action: ToyControlAction,
        autoStopSec: Int,
        address: String = selectedAddress,
        scheduleAutoStop: Boolean = true,
    ) {
        val status = protocolStatusFor(address)
        appendLog("准备发送控制 address=$address action=$action autoStopSec=$autoStopSec protocol=${status.id.ifBlank { "<none>" }}")
        cancelAutoStop(address)
        controllerFor(address).sendAction(action)
        startKeepAlive(action, address, autoStopSec)
        if (address == selectedAddress) busyHint = "设备正在运行"
        if (!scheduleAutoStop) return
        val task = Runnable {
            cancelKeepAlive(address)
            runCatching { controllers[address]?.stopDevice() }
                .onSuccess { appendLog("计时结束，已停止设备 address=$address") }
                .onFailure { appendLog("停止失败：${it.message}") }
        }
        autoStopTasks[address] = task
        mainHandler.postDelayed(task, autoStopSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
    }

    private fun sendToyStop(all: Boolean = false) {
        val targets = if (all) controllers.keys.toList() else listOf(selectedAddress).filter { it.isNotBlank() }
        appendLog("准备发送停止 all=$all targets=${targets.joinToString()}")
        targets.forEach { address ->
            stopToyAtAddress(address)
        }
        busyHint = if (all) "已全部停止" else "已停止"
        syncRelayDevice()
    }

    private fun sendToyStop(address: String) {
        appendLog("准备发送停止 address=$address")
        stopToyAtAddress(address)
        if (address == selectedAddress) busyHint = "已停止"
        syncRelayDevice()
    }

    private fun stopToyAtAddress(address: String) {
        cancelAutoStop(address)
        cancelKeepAlive(address)
        controllers[address]?.stopDevice()
    }

    private suspend fun delayControlWarmup(address: String) {
        val warmupMs = controllers[address]?.controlWarmupMs() ?: 0L
        if (warmupMs <= 0L) return
        appendLog("等待 BLE 通道稳定 ${warmupMs}ms address=$address")
        delay(warmupMs)
    }

    private fun startKeepAlive(action: ToyControlAction, address: String, autoStopSec: Int? = null) {
        cancelKeepAlive(address)
        val interval = protocolStatusFor(address).repeatIntervalMs.coerceAtLeast(0)
        if (interval <= 0) return
        val deadline = autoStopSec?.let {
            System.currentTimeMillis() + it.coerceIn(1, MaxSequenceDurationSec) * 1_000L
        }
        keepAliveTasks[address] = object : Runnable {
            override fun run() {
                if (deadline != null && System.currentTimeMillis() >= deadline) return
                runCatching {
                    controllers[address]?.sendAction(action)
                }.onFailure {
                    appendLog("保活写入失败：${it.message ?: "命令发送失败"}")
                }
                mainHandler.postDelayed(this, interval.toLong())
            }
        }.also {
            mainHandler.postDelayed(it, interval.toLong())
        }
    }

    private fun cancelKeepAlive(address: String) {
        keepAliveTasks.remove(address)?.let(mainHandler::removeCallbacks)
    }

    private fun cancelAutoStop(address: String) {
        autoStopTasks.remove(address)?.let(mainHandler::removeCallbacks)
    }

    private fun currentDeviceId(): String {
        return deviceIdForAddress(selectedAddress)
    }

    private fun addressForDeviceId(deviceId: String): String? {
        val addresses = linkedSetOf<String>()
        rememberedDevices.forEach { addresses += it.address }
        controllers.keys.forEach { addresses += it }
        devices.forEach { addresses += it.address }
        selectedAddress.takeIf { it.isNotBlank() }?.let { addresses += it }
        return addresses.firstOrNull { deviceIdForAddress(it) == deviceId }
    }

    private fun defaultConnectedAddress(): String =
        selectedAddress.takeIf { deviceConnectionStates[it] == BleConnectionState.Ready }
            ?: deviceConnectionStates.entries.firstOrNull { it.value == BleConnectionState.Ready }?.key
            ?: ""

    private fun deviceIdForAddress(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(address.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "dev_$digest"
    }

    private fun BleConnectionState.toRuntimeState(): ToyRuntimeState =
        when (this) {
            BleConnectionState.Connecting,
            BleConnectionState.Discovering,
            BleConnectionState.Disconnecting -> ToyRuntimeState.Connecting
            BleConnectionState.Ready -> ToyRuntimeState.Connected
            BleConnectionState.Error -> ToyRuntimeState.Failed
            BleConnectionState.Idle -> ToyRuntimeState.Offline
        }

    fun protocolStatusForAddress(address: String): BleProtocolStatus = protocolStatusFor(address)

    fun modeForAddress(address: String): Int =
        if (address == selectedAddress) mode else deviceModes[address] ?: 1

    fun intensityForAddress(address: String): Int =
        if (address == selectedAddress) intensity else deviceIntensities[address] ?: 0

    fun secondaryIntensityForAddress(address: String): Int =
        if (address == selectedAddress) secondaryIntensity else deviceSecondaryIntensities[address] ?: intensityForAddress(address)

    fun modeLabelForAddress(address: String): String {
        val status = protocolStatusFor(address)
        val targetMode = modeForAddress(address)
        return modeLabel(status, targetMode)
    }

    private fun protocolStatusFor(address: String): BleProtocolStatus =
        if (address == selectedAddress) protocolStatus else deviceProtocolStatuses[address] ?: BleProtocolStatus()

    private fun clampCurrentControlValues() {
        mode = mode.coerceIn(1, protocolStatus.modeMax.coerceAtLeast(1))
        intensity = intensity.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        secondaryIntensity = secondaryIntensity.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        if (selectedAddress.isNotBlank()) {
            deviceModes[selectedAddress] = mode
            deviceIntensities[selectedAddress] = intensity
            if (protocolStatus.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
                deviceSecondaryIntensities[selectedAddress] = secondaryIntensity
            }
        }
    }

    private fun connectedSavedDeviceCount(): Int =
        rememberedDevices.count { toyRuntimeStates[it.address] == ToyRuntimeState.Connected }

    private fun autoOfflineWhenNoConnectedSavedDevice() {
        if (relayState == "已在线" && connectedSavedDeviceCount() == 0) {
            disconnectRelay()
        }
    }

    fun currentModeLabel(): String {
        return modeLabel(protocolStatus, mode)
    }

    private fun modeLabel(status: BleProtocolStatus, targetMode: Int): String {
        val base = status.modeLabel.ifBlank { "模式" }
        val name = status.modeNames.getOrNull(targetMode - 1)
        return if (name.isNullOrBlank()) "$base $targetMode" else "$base $targetMode：$name"
    }

    private fun patternAction(nextMode: Int): ToyControlAction =
        patternAction(nextMode, intensity, secondaryIntensity, protocolStatus)

    private fun patternAction(
        nextMode: Int,
        currentIntensity: Int,
        currentSecondaryIntensity: Int,
        status: BleProtocolStatus,
    ): ToyControlAction =
        when (status.controlStyle) {
            ToyControlStyle.IntensityOnly -> ToyControlAction.Intensity(currentIntensity)
            ToyControlStyle.PatternOnly,
            ToyControlStyle.ExclusivePatternOrIntensity -> ToyControlAction.Pattern(nextMode)
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(nextMode, currentIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                dualIntensityAction(nextMode, currentIntensity, currentSecondaryIntensity, status)
        }

    private fun intensityAction(nextIntensity: Int): ToyControlAction =
        intensityAction(
            currentMode = mode,
            nextIntensity = nextIntensity,
            nextSecondaryIntensity = nextIntensity,
            status = protocolStatus,
        )

    private fun intensityAction(
        currentMode: Int,
        nextIntensity: Int,
        nextSecondaryIntensity: Int,
        status: BleProtocolStatus,
    ): ToyControlAction =
        when (status.controlStyle) {
            ToyControlStyle.PatternOnly -> ToyControlAction.Pattern(currentMode)
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(currentMode, nextIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                dualIntensityAction(currentMode, nextIntensity, nextSecondaryIntensity, status)
            ToyControlStyle.IntensityOnly,
            ToyControlStyle.ExclusivePatternOrIntensity -> ToyControlAction.Intensity(nextIntensity)
        }

    private fun dualIntensityAction(
        currentMode: Int,
        primaryIntensity: Int,
        secondaryIntensity: Int,
        status: BleProtocolStatus,
    ): ToyControlAction =
        ToyControlAction.DualMotor(
            mode = currentMode.coerceIn(1, status.modeMax.coerceAtLeast(1)),
            internalIntensity = primaryIntensity.coerceIn(0, status.intensityMax.coerceAtLeast(1)),
            externalIntensity = secondaryIntensity.coerceIn(0, status.intensityMax.coerceAtLeast(1)),
        )

    private fun actionKeepsDeviceRunning(action: ToyControlAction): Boolean =
        when (action) {
            is ToyControlAction.Intensity -> action.value > 0
            is ToyControlAction.Combined -> action.intensity > 0
            is ToyControlAction.DualMotor -> action.internalIntensity > 0 || action.externalIntensity > 0
            is ToyControlAction.Pattern -> true
            ToyControlAction.Stop -> false
        }

    private fun scheduleControlTrial(action: ToyControlAction) {
        if (!protocolStatus.controllable || connectionState != BleConnectionState.Ready) return
        pendingControlAction = action
        controlTrialStarted = true
        busyHint = "设备正在运行"
        mainHandler.removeCallbacks(controlTrial)
        mainHandler.postDelayed(controlTrial, 250L)
    }

    private fun sendLocalControlAction(action: ToyControlAction) {
        runCatching {
            val address = selectedAddress.ifBlank { error("请选择设备") }
            appendLog("准备发送本地控制 action=$action protocol=${protocolStatus.id.ifBlank { "<none>" }}")
            controllerFor(address).sendAction(action)
            deviceModes[address] = mode
            deviceIntensities[address] = intensity
            if (protocolStatus.controlStyle == ToyControlStyle.PatternAndDualIntensity) {
                deviceSecondaryIntensities[address] = secondaryIntensity
            }
            syncRelayDevice()
            cancelAutoStop(address)
            startKeepAlive(action, address)
        }.onFailure {
            busyHint = it.message ?: "控制失败"
        }
    }

    private fun recordProtocolAdapterFeedback(result: String) {
        appendLog(
            "协议适配反馈 event=protocol_adapter_feedback result=$result " +
                "deviceName=${selectedName.ifBlank { "<none>" }} address=${selectedAddress.ifBlank { "<none>" }} " +
                "protocol=${protocolStatus.id.ifBlank { "<none>" }} name=${protocolStatus.displayName}",
        )
    }

    private fun rememberDevice(
        address: String,
        remark: String,
        protocolName: String,
    ) {
        if (address.isBlank()) return
        val scanned = devices.firstOrNull { it.address == address }
        val existing = rememberedDevices.firstOrNull { it.address == address }
        val current = JSONObject()
            .put("name", remark)
            .put("address", address)
            .put("protocolName", protocolName)
            .put("lastSeenAt", System.currentTimeMillis())
            .put("manufacturerData", scanned?.manufacturerData ?: existing?.manufacturerData.orEmpty())
            .put("scanRecordHex", scanned?.scanRecordHex ?: existing?.scanRecordHex.orEmpty())
        val merged = JSONArray()
        merged.put(current)
        rememberedDevices
            .filterNot { it.address == address }
            .take(7)
            .forEach {
                merged.put(
                    JSONObject()
                        .put("name", it.name)
                        .put("address", it.address)
                        .put("protocolName", it.protocolName)
                        .put("lastSeenAt", it.lastSeenAt)
                        .put("manufacturerData", it.manufacturerData)
                        .put("scanRecordHex", it.scanRecordHex),
                )
            }
        rememberedDevicesJson = merged.toString()
    }

    private fun autoOnlineSavedDevice() {
        if (connectionState != BleConnectionState.Ready) return
        if (!currentDeviceSaved || selectedAddress.isBlank()) return
        if (relayState == "已在线") {
            syncRelayDevice()
            return
        }
        if (autoOnlineAddress == selectedAddress) return
        autoOnlineAddress = selectedAddress
        busyHint = "设备已就绪，手机正在上线"
        toast("设备已就绪，正在上线", ToastType.Info)
        connectRelay()
    }

    private fun publicBaseUrl(): String {
        return OkHttpUtils.currentBaseUrl
    }

    private fun refreshBaseUrlState() {
        serverUrl = OkHttpUtils.currentBaseUrl
        baseUrlDraft = serverUrl
    }

    private inline fun runAction(block: () -> Unit) {
        errorMessage = runCatching(block).exceptionOrNull()?.message
        errorMessage?.let { appendLog("操作失败：$it") }
    }

    override fun onCleared() {
        scanController.close()
        controllers.values.forEach { it.close() }
        relaySequenceJobs.values.forEach { it.cancel() }
        relaySequenceJobs.clear()
        autoStopTasks.keys.toList().forEach(::cancelAutoStop)
        keepAliveTasks.keys.toList().forEach(::cancelKeepAlive)
        relay.close()
    }

    companion object {
        val APP_VERSION_CODE: Int by lazy {
            currentPackageVersionCode()
        }
        val APP_VERSION_NAME: String by lazy {
            currentPackageVersionName()
        }

        private fun currentPackageVersionCode(): Int {
            return runCatching {
                val info = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode
                }
            }.getOrDefault(100)
        }

        private fun currentPackageVersionName(): String {
            return runCatching {
                appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName.orEmpty()
            }.getOrDefault("0.1.0")
        }
    }
}
