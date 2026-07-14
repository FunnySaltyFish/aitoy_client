package com.funny.aitoy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleController
import com.funny.aitoy.ble.BleControllerFactory
import com.funny.aitoy.ble.BleBroadcastProtocolRegistry
import com.funny.aitoy.ble.BleProtocolFeature
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.ble.ToyControlAction
import com.funny.aitoy.ble.ToyControlStyle
import com.funny.aitoy.ble.independentFunctionCode
import com.funny.aitoy.ble.independentFunctionModeMax
import com.funny.aitoy.ble.svakomSl278PairIdentity
import com.funny.aitoy.ble.svakomStableIdentity
import com.funny.aitoy.core.kmp.ToastType
import com.funny.aitoy.core.kmp.toast
import com.funny.aitoy.core.prefs.AiToyPrefs
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.core.utils.safeFromJson
import com.funny.aitoy.core.utils.toJson
import com.funny.aitoy.diagnostics.AiToyTraceEvent
import com.funny.aitoy.diagnostics.AiToyTraceUploadPolicy
import com.funny.aitoy.diagnostics.AiToyTraceUploader
import com.funny.aitoy.model.AppUpdateState
import com.funny.aitoy.model.ManagedToy
import com.funny.aitoy.model.RememberedToy
import com.funny.aitoy.model.ToyRuntimeState
import com.funny.aitoy.model.lastSeenKey
import com.funny.aitoy.network.OkHttpUtils
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.Product
import com.funny.aitoy.relay.MaxSequenceDurationSec
import com.funny.aitoy.relay.RelayClient
import com.funny.aitoy.relay.RelayCommand
import com.funny.aitoy.relay.RelayDevice
import com.funny.aitoy.relay.RelayDeviceFeature
import com.funny.aitoy.relay.RelaySequenceStep
import com.funny.aitoy.relay.UnlimitedSequenceDurationSec
import com.funny.aitoy.relay.parseRelaySequenceScript
import com.funny.aitoy.runtime.DeviceRuntimeStore
import com.funny.aitoy.runtime.toRuntimeState
import com.funny.data_saver.core.mutableDataSaverStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val EXCLUSIVE_INTENSITY_MODE = 0

private const val LegacyDefaultUserToken = "ut_fishfish"
private const val DefaultCommunityUrl = "https://qm.qq.com/q/ytRVIe6O7m"
private const val DefaultTutorialUrl = "https://docs.qq.com/s/4dhBqBSu1u5900Qh7qvYHW/folder/YFZTdLuzaIxv"
private const val ProtocolExhaustedFeedbackMessage =
    "已尝试所有协议，目前仍不支持。请在群里反馈，并附上官方 App 截图或产品说明书，说明设备可用的档位和模式；同时提供你在本 App 内的操作时间，方便查找日志。"

@OptIn(ExperimentalUuidApi::class)
private fun generateDefaultUserToken(): String = "ut_${Uuid.random().toString().replace("-", "")}"

internal fun mapScalarFeatureValue(value: Int, feature: BleProtocolFeature): Int {
    val min = minOf(feature.min, feature.max)
    val max = maxOf(feature.min, feature.max)
    return value.coerceIn(min, max)
}

class BridgeViewModel : ViewModel() {
    val devices = mutableStateListOf<ScannedBleDevice>()
    val cachitoQuickDevices: List<ScannedBleDevice> = BleBroadcastProtocolRegistry.cachitoQuickConnectDevices()
    private val deviceRuntimeStore = DeviceRuntimeStore()

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
    var showGuide by mutableStateOf(false)
    var nearbyDevicesExpanded by mutableStateOf(false)
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
    var crashNotice by mutableStateOf(BridgePlatform.consumeLastCrashNotice())
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
    var accountUser
        get() = AiToyPrefs.user
        set(value) {
            AiToyPrefs.user = value
        }
    var accountLoading by mutableStateOf(false)
        private set
    var accountMessage by mutableStateOf("")
        private set
    var profileNameDraft by mutableStateOf("")
    var profileAvatarDraft by mutableStateOf("")
    var memberProducts by mutableStateOf<List<Product>>(emptyList())
        private set
    var selectedPayType by mutableStateOf("alipay")
    var pendingOrderNo by mutableStateOf("")
        private set

    fun showAccountMessage(message: String) {
        accountMessage = message
    }

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
        get() = rememberedDevicesJson
            .safeFromJson<List<RememberedToy>>(emptyList())
            .filter { it.address.isNotBlank() }
            .map {
                it.copy(
                    name = it.name.ifBlank { "我的小玩具" },
                    protocolName = it.protocolName.ifBlank { "已保存" },
                )
            }

    val managedToys: List<ManagedToy>
        get() {
            val saved = rememberedDevices
            val addresses = linkedSetOf<String>()
            saved.forEach { addresses += it.address }
            controllers.keys.forEach { addresses += it }
            deviceRuntimeStore.keys.forEach { addresses += it }
            if (selectedAddress.isNotBlank()) addresses += selectedAddress
            val rows = saved.map { toy ->
                ManagedToy(
                    name = toy.name,
                    address = toy.address,
                    protocolName = toy.protocolName,
                    runtimeState = deviceRuntimeStore.runtimeState(toy.address),
                    selected = selectedAddress == toy.address,
                    current = selectedAddress == toy.address,
                    saved = true,
                    batteryPercent = batteryPercentForAddress(toy.address),
                )
            }.toMutableList()
            addresses
                .filter { address -> rows.none { it.address == address } }
                .forEach { address ->
                    rows += ManagedToy(
                        name = when {
                            address == selectedAddress -> selectedName
                            deviceRuntimeStore.displayName(address).isNotBlank() -> deviceRuntimeStore.displayName(address)
                            else -> devices.firstOrNull { it.address == address }?.name.orEmpty()
                        }.ifBlank { "蓝牙设备" },
                        address = address,
                        protocolName = protocolStatusFor(address).displayName.ifBlank { "尚未识别" },
                        runtimeState = if (deviceRuntimeStore.runtimeState(address) != ToyRuntimeState.Offline) {
                            deviceRuntimeStore.runtimeState(address)
                        } else if (address == selectedAddress) {
                            connectionState.toRuntimeState()
                        } else {
                            ToyRuntimeState.Offline
                        },
                        selected = selectedAddress == address,
                        current = selectedAddress == address,
                        saved = false,
                        batteryPercent = batteryPercentForAddress(address),
                    )
                }
            return rows.sortedWith(
                compareByDescending<ManagedToy> { it.runtimeState == ToyRuntimeState.Connected }
                    .thenByDescending { it.current }
                    .thenByDescending { it.lastSeenKey(saved) }
            )
        }

    private val scanController = BleControllerFactory.create(
        onDevice = ::onDeviceFound,
        onState = { },
        onProtocol = { },
        onProtocolAttempt = { },
        onBatteryPercent = { },
        onLog = ::appendLog,
        onTrace = ::appendTrace,
    )
    private val controllers = mutableMapOf<String, BleController>()
    private val keepAliveJobs = mutableMapOf<String, Job>()
    private val autoStopJobs = mutableMapOf<String, Job>()
    private var pendingControlAction: ToyControlAction? = null
    private var controlTrialJob: Job? = null
    private val relay = RelayClient(
        scope = viewModelScope,
        onState = {
            relayState = it
            when (it) {
                "正在同步" -> syncRelayDevice()
                "已在线" -> {
                    syncRelayDevice()
                    toast("手机已上线", ToastType.Success)
                }
            }
        },
        onLog = ::appendLog,
        onCommand = ::handleRelayCommand,
        onTrace = ::appendTrace,
    )
    private val relaySequenceJobs = mutableMapOf<String, Job>()
    private var developerTitleTapCount = 0
    private var autoOnlineAddress = ""

    init {
        OkHttpUtils.ensureDefaultNetworkConfig()
        refreshBaseUrlState()
        ensureDefaultUserToken()
        userTokenDraft = userToken
        bootstrapAccount()
        updateTraceContext()
        checkAppConfig()
    }

    private fun controllerFor(address: String): BleController =
        controllers.getOrPut(address) {
            BleControllerFactory.create(
                onDevice = ::onDeviceFound,
                onState = { onDeviceConnectionState(address, it) },
                onProtocol = { onDeviceProtocol(address, it) },
                onProtocolAttempt = { onDeviceProtocolAttempt(address, it) },
                onBatteryPercent = { onDeviceBatteryPercent(address, it) },
                onLog = ::appendLog,
                onTrace = ::appendTrace,
            )
        }

    private fun onDeviceConnectionState(address: String, state: BleConnectionState) {
        deviceRuntimeStore.setConnectionState(address, state)
        if (address == selectedAddress) {
            connectionState = state
            updateTraceContext()
        }
        when (state) {
            BleConnectionState.Ready -> {
                nearbyDevicesExpanded = false
                if (address == selectedAddress) {
                    currentDeviceSaved = rememberedDevices.any { toy -> toy.address == selectedAddress }
                }
                syncRelayDevice()
                if (address == selectedAddress) autoOnlineSavedDevice()
            }
            BleConnectionState.Error -> {
                if (address == selectedAddress) {
                    busyHint = if (deviceRuntimeStore.protocolAttemptStatus(address).exhausted) {
                        ProtocolExhaustedFeedbackMessage
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

    private fun onDeviceBatteryPercent(address: String, percent: Int?) {
        deviceRuntimeStore.setBatteryPercent(address, percent)
        syncRelayDevice()
    }

    private fun onDeviceProtocol(address: String, status: BleProtocolStatus) {
        deviceRuntimeStore.setProtocolStatus(address, status)
        if (address == selectedAddress) {
            protocolStatus = status
            clampCurrentControlValues()
            updateTraceContext()
        }
    }

    private fun onDeviceProtocolAttempt(address: String, status: ProtocolAttemptStatus) {
        deviceRuntimeStore.setProtocolAttemptStatus(address, status)
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
            nearbyDevicesExpanded = connectedDeviceCount() == 0
            scanController.startScan()
            scanning = true
        }
    }

    fun toggleNearbyDevicesExpanded() {
        nearbyDevicesExpanded = !nearbyDevicesExpanded
    }

    fun connect(device: ScannedBleDevice) = runAction {
        if (!device.controllable) {
            errorMessage = "这个广播暂时无法控制"
            appendLog("跳过不可连接且未识别的广播设备：name=${device.name} address=${device.address}")
            return@runAction
        }
        connectedSvakomSl278PairAddressFor(device.manufacturerData)
            ?.takeIf { it != device.address }
            ?.let { connectedAddress ->
                selectDevice(connectedAddress)
                busyHint = "这套设备已连接，可以直接控制两个部位"
                return@runAction
            }
        if (deviceRuntimeStore.runtimeState(device.address) == ToyRuntimeState.Connected) {
            selectDevice(device.address)
            busyHint = "已切换到 ${displayNameForAddress(device.address, device.name)}"
            return@runAction
        }
        // 防抖：连接进行中(Connecting/Discovering)再次点击会触发第二次 connect()，
        // 其内部 disconnect 会掐掉仍在 pending 的首次连接(status=22)，并泄漏 GATT client 槽。
        val inFlightState = deviceRuntimeStore.connectionState(device.address)
        if (inFlightState == BleConnectionState.Connecting || inFlightState == BleConnectionState.Discovering) {
            val displayName = displayNameForAddress(device.address, device.name)
            appendLog("忽略重复连接请求：$displayName 正在连接中 state=$inFlightState")
            busyHint = "正在连接 $displayName，请稍候"
            return@runAction
        }
        val displayName = displayNameForAddress(device.address, device.name)
        selectedAddress = device.address
        selectedName = displayName
        deviceRuntimeStore.setDisplayName(device.address, displayName)
        deviceRuntimeStore.setConnectionState(device.address, BleConnectionState.Connecting)
        deviceRuntimeStore.setBatteryPercent(device.address, null)
        protocolAttemptStatus = ProtocolAttemptStatus()
        protocolStatus = BleProtocolStatus()
        deviceRuntimeStore.setProtocolAttemptStatus(device.address, protocolAttemptStatus)
        deviceRuntimeStore.setProtocolStatus(device.address, protocolStatus)
        updateTraceContext()
        controlTrialStarted = false
        currentDeviceSaved = rememberedDevices.any { it.address == device.address }
        showDeviceRemarkDialog = false
        deviceRemarkDraft =
            rememberedDevices.firstOrNull { it.address == device.address }?.name.orEmpty()
        scanning = false
        busyHint = "正在为你连接 $displayName"
        scanController.stopScan()
        controllerFor(device.address).connect(device, currentTemplate())
    }

    fun connectRemembered(toy: RememberedToy) {
        if (deviceRuntimeStore.runtimeState(toy.address) == ToyRuntimeState.Connected) {
            selectDevice(toy.address)
            busyHint = "已切换到 ${toy.name}"
            return
        }
        val broadcastProtocolName = toy.protocolName.takeIf { it.startsWith("Cachito ") }.orEmpty()
        // 司康沃等设备每次开机换 MAC，记忆的旧地址已失效。若当前扫描到同一实体（司康沃稳定身份匹配），
        // 改连扫描到的新地址和新 manufacturer 数据，避免连到死 MAC 导致连不上/无法识别产品码。
        val refreshed = resolveRememberedScanTarget(toy)
        if (refreshed != null && refreshed.address != toy.address) {
            appendLog("记忆设备 ${toy.name} 地址已更新：${toy.address} → ${refreshed.address}，改连扫描到的新地址")
            connect(refreshed)
            return
        }
        connect(
            refreshed ?: ScannedBleDevice(
                name = toy.name,
                address = toy.address,
                rssi = 0,
                connectable = broadcastProtocolName.isBlank(),
                manufacturerData = toy.manufacturerData,
                scanRecordHex = toy.scanRecordHex,
                broadcastProtocolName = broadcastProtocolName,
            )
        )
    }

    /**
     * 记忆设备重连时，优先在当前扫描结果里找同一实体：
     * 1) 地址仍能扫到 → 用扫描到的最新数据（manufacturer 可能刷新）；
     * 2) 地址失效但司康沃稳定身份（产品码）匹配到某个扫描结果 → 用那个新地址。
     * 找不到则返回 null，调用方回退到记忆的旧地址。
     */
    private fun resolveRememberedScanTarget(toy: RememberedToy): ScannedBleDevice? {
        devices.firstOrNull { it.address == toy.address }?.let { return it }
        val savedIdentity = svakomStableIdentity(toy.manufacturerData) ?: return null
        return devices.firstOrNull { scanned ->
            scanned.connectable && svakomStableIdentity(scanned.manufacturerData) == savedIdentity
        }
    }

    fun selectDevice(address: String) {
        if (address.isBlank()) return
        selectedAddress = address
        selectedName = rememberedDevices.firstOrNull { it.address == address }?.name
            ?: deviceRuntimeStore.displayName(address).takeIf { it.isNotBlank() }
            ?: devices.firstOrNull { it.address == address }?.name
            ?: "蓝牙设备"
        connectionState = deviceRuntimeStore.connectionState(address)
        protocolStatus = deviceRuntimeStore.protocolStatus(address)
        protocolAttemptStatus = deviceRuntimeStore.protocolAttemptStatus(address)
        mode = deviceRuntimeStore.mode(address)
        intensity = deviceRuntimeStore.intensity(address).takeIf { it > 0 } ?: 1
        secondaryIntensity = deviceRuntimeStore.secondaryIntensity(address).takeIf { it > 0 } ?: intensity
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
        deviceRuntimeStore.setConnectionState(address, BleConnectionState.Idle)
        deviceRuntimeStore.setBatteryPercent(address, null)
        controlTrialJob?.cancel()
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
        selectedAddress.takeIf { it.isNotBlank() }?.let { deviceRuntimeStore.setMode(it, next) }
        scheduleControlTrial(patternAction(next))
    }

    fun updateMode(address: String, value: Int) {
        if (address != selectedAddress) selectDevice(address)
        updateMode(value)
    }

    fun updateIntensity(value: Int) {
        val next = value.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        val usesExclusiveIntensity = protocolStatus.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity
        if (intensity == next && !(usesExclusiveIntensity && mode != EXCLUSIVE_INTENSITY_MODE)) return
        intensity = next
        if (usesExclusiveIntensity) {
            mode = EXCLUSIVE_INTENSITY_MODE
        }
        selectedAddress.takeIf { it.isNotBlank() }?.let {
            deviceRuntimeStore.setIntensity(it, next)
            if (usesExclusiveIntensity) {
                deviceRuntimeStore.setMode(it, EXCLUSIVE_INTENSITY_MODE)
            }
        }
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
                deviceRuntimeStore.setIntensity(targetAddress, next)
            }
            else -> {
                if (secondaryIntensity == next) return
                secondaryIntensity = next
                deviceRuntimeStore.setSecondaryIntensity(targetAddress, next)
            }
        }
        scheduleControlTrial(dualIntensityAction(mode, intensity, secondaryIntensity, protocolStatus))
    }

    fun updateIndependentFunction(address: String, functionCode: Int, mode: Int, intensity: Int) {
        if (address != selectedAddress) selectDevice(address)
        val status = protocolStatus
        val safeMode = mode.coerceIn(0, 99)
        val safeIntensity = intensity.coerceIn(0, status.intensityMax.coerceAtLeast(1))
        scheduleControlTrial(
            ToyControlAction.Combined(
                mode = functionCode.coerceIn(1, 9) * 100 + safeMode,
                intensity = safeIntensity,
            )
        )
    }

    fun stopDevice() = runAction {
        val address = selectedAddress
        if (address.isBlank()) return@runAction
        busyHint = "已停止"
        controlTrialJob?.cancel()
        stopDevice(address)
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
        controlTrialJob?.cancel()
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
            fallback.keptCurrent -> {
                "已保持当前设备类型，请靠近手机并从低强度再试一次"
            }
            else -> {
                ProtocolExhaustedFeedbackMessage
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

    fun deleteManagedDevice(address: String) {
        if (address.isBlank()) return
        rememberedDevicesJson = rememberedDevices
            .filterNot { it.address == address }
            .toJson()
        cancelAutoStop(address)
        cancelKeepAlive(address)
        controllers.remove(address)?.disconnect()
        devices.removeAll { it.address == address }
        deviceRuntimeStore.remove(address)
        if (address == selectedAddress) {
            selectedAddress = ""
            selectedName = ""
            connectionState = BleConnectionState.Idle
            protocolStatus = BleProtocolStatus()
            protocolAttemptStatus = ProtocolAttemptStatus()
            currentDeviceSaved = false
            controlTrialStarted = false
            updateTraceContext()
            syncRelayDevice()
            autoOfflineWhenNoConnectedSavedDevice()
        }
        busyHint = "已移除设备"
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

    fun connectRelay() {
        BridgePlatform.startForegroundService()
        relay.connect(OkHttpUtils.currentBaseUrl, userToken)
    }

    fun resumeRelay() {
        relay.reconnectNow()
    }

    fun disconnectRelay() {
        relay.disconnect()
        BridgePlatform.stopForegroundService()
    }

    fun openBatteryConnectionSettings() {
        BridgePlatform.openBatteryConnectionSettings()
    }

    fun openNotificationSettings() {
        BridgePlatform.openNotificationSettings()
    }

    fun dismissCrashNotice() {
        crashNotice = ""
    }

    fun openUpdateInBrowser() {
        val url = updateState.downloadPageUrl.ifBlank { updateState.apkUrl }
        if (url.isBlank()) return
        BridgePlatform.openUrl(url)
    }

    fun openAppDownloadPage() {
        BridgePlatform.openUrl(OkHttpUtils.externalUrl("download"))
    }

    fun openTutorialDocument() {
        BridgePlatform.openUrl(tutorialUrl.ifBlank { DefaultTutorialUrl })
    }

    fun openCommunityGroup() {
        BridgePlatform.openUrl(communityUrl.ifBlank { DefaultCommunityUrl })
    }

    fun onHelpSettingsTitleClick() {
        if (developerMode) return
        developerTitleTapCount += 1
        baseUrlMessage = if (developerTitleTapCount >= 5) {
            developerMode = true
            "开发者模式已开启"
        } else {
            "再点 ${5 - developerTitleTapCount} 次可打开开发者设置"
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
        bootstrapAccount()
        baseUrlMessage = "Token 已保存；手机已经上线时，请重新上线。"
    }

    fun bootstrapAccount() {
        if (userToken.isBlank()) return
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.authService.bootstrap(
                        userToken = userToken,
                        deviceId = AiToyPrefs.deviceId,
                    )
                }
            }.onSuccess { payload ->
                AiToyPrefs.authToken = payload.token
                AiToyPrefs.user = payload.user
                accountUser = payload.user
                profileNameDraft = payload.user.displayName.ifBlank { payload.user.username }
                profileAvatarDraft = payload.user.avatarUrl
                accountMessage = ""
                refreshProducts()
            }.onFailure {
                accountMessage = "账号同步失败，请稍后重试。"
            }
            accountLoading = false
        }
    }

    fun refreshAccount() {
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.userService.me() }
            }.onSuccess { payload ->
                AiToyPrefs.user = payload.user
                accountUser = payload.user
                profileNameDraft = payload.user.displayName.ifBlank { payload.user.username }
                profileAvatarDraft = payload.user.avatarUrl
                accountMessage = ""
            }.onFailure {
                if (AiToyPrefs.authToken.isBlank()) {
                    bootstrapAccount()
                } else {
                    accountMessage = "刷新失败，请稍后再试。"
                }
            }
            accountLoading = false
        }
    }

    fun refreshProducts() {
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.payService.products() }
            }.onSuccess { payload ->
                memberProducts = payload.products
            }
        }
    }

    fun saveProfile() {
        val name = profileNameDraft.trim()
        if (name.isBlank()) {
            accountMessage = "请填写昵称。"
            return
        }
        if (name.length > 12) {
            accountMessage = "昵称最多 12 个字。"
            return
        }
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.userService.updateProfile(
                        displayName = name,
                        avatarUrl = profileAvatarDraft.trim().ifBlank { null },
                    )
                }
            }.onSuccess { payload ->
                AiToyPrefs.user = payload.user
                accountUser = payload.user
                accountMessage = "资料已保存。"
            }.onFailure {
                accountMessage = "保存失败，请稍后再试。"
            }
            accountLoading = false
        }
    }

    fun uploadAvatar(fileName: String, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            accountMessage = "请选择可用的头像图片。"
            return
        }
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                val safeName = fileName.ifBlank { "avatar.jpg" }
                val contentType = when (safeName.substringAfterLast('.', "").lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val part = MultipartBody.Part.createFormData(
                    name = "file",
                    filename = safeName,
                    body = bytes.toRequestBody(contentType.toMediaType()),
                )
                apiRequest { AiToyServices.userService.uploadAvatar(part) }
            }.onSuccess { payload ->
                AiToyPrefs.user = payload.user
                accountUser = payload.user
                profileAvatarDraft = payload.user.avatarUrl
                accountMessage = "头像已更新。"
            }.onFailure {
                accountMessage = "头像更新失败，请稍后再试。"
            }
            accountLoading = false
        }
    }

    fun openMembershipAgreement() {
        val prefix = AiToyPrefs.apiPrefix.trim().trim('/')
        BridgePlatform.openUrl(OkHttpUtils.externalUrl("$prefix/pay/membership-agreement"))
    }

    fun startMembershipPurchase(productId: String, months: Int = 1, quantity: Int = 1) {
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                apiRequest {
                    AiToyServices.payService.createOrder(
                        productId = productId,
                        payType = selectedPayType,
                        months = months.coerceIn(1, 12),
                        quantity = quantity.coerceIn(1, 99),
                    )
                }
            }.onSuccess { payload ->
                pendingOrderNo = payload.orderNo
                accountMessage = "订单已创建，完成支付后回到 App 刷新。"
                if (payload.payUrl.isNotBlank()) {
                    BridgePlatform.openUrl(payload.payUrl)
                }
            }.onFailure {
                accountMessage = "暂时无法发起支付，请稍后再试。"
            }
            accountLoading = false
        }
    }

    fun refreshPendingOrder() {
        val orderNo = pendingOrderNo
        if (orderNo.isBlank()) {
            refreshAccount()
            return
        }
        accountLoading = true
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.payService.queryOrder(orderNo) }
            }.onSuccess { payload ->
                payload.user?.let {
                    AiToyPrefs.user = it
                    accountUser = it
                }
                accountMessage = if (payload.status == "paid") "会员已生效。" else "订单还未完成。"
            }.onFailure {
                accountMessage = "订单刷新失败，请稍后再试。"
            }
            accountLoading = false
        }
    }

    fun downloadAndInstallUpdate() {
        val url = updateState.apkUrl
        if (url.isBlank() || updateDownloading) return
        updateDownloading = true
        updateDownloadProgress = 0
        viewModelScope.launch {
            runCatching {
                BridgePlatform.downloadAndInstallUpdate(
                    url = url,
                    versionName = updateState.latestVersionName.ifBlank { "latest" },
                ) { progress ->
                    viewModelScope.launch { updateDownloadProgress = progress }
                }
            }.onSuccess {
                updateDownloading = false
                updateDownloadProgress = 100
                updateState = AppUpdateState(checked = true)
            }.onFailure {
                updateDownloading = false
                updateState = updateState.copy(message = it.message ?: "下载失败")
            }
        }
    }

    fun checkAppConfig() {
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.appService.config(BridgePlatform.appVersionCode) }
            }.onSuccess { config ->
                communityUrl = config.communityUrl.ifBlank { DefaultCommunityUrl }
                tutorialUrl = config.tutorialUrl.ifBlank { DefaultTutorialUrl }
                val update = config.update
                val updateAvailable = update.latestVersionCode > BridgePlatform.appVersionCode
                val forceUpdate = update.forceUpdate &&
                    (update.minSupportedVersionCode > BridgePlatform.appVersionCode || updateAvailable)
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

    private fun currentTemplate() = ProtocolTemplate(
        serviceUuid = "",
        writeUuid = "",
        notifyUuid = "",
        writeWithResponse = false,
        manualControlEnabled = false,
        commandTemplate = "",
        stopTemplate = "",
    )

    private fun onDeviceFound(device: ScannedBleDevice) {
        val deviceKey = device.scanIdentityKey()
        val index = devices.indexOfFirst { it.scanIdentityKey() == deviceKey }
        val resolvedDevice = if (index >= 0) devices[index].mergeScanUpdate(device) else device
        if (index >= 0) devices[index] = resolvedDevice else devices.add(resolvedDevice)
        devices.sortByDescending { it.rssi }
        deviceRuntimeStore.ensure(resolvedDevice.address)
    }

    private fun ScannedBleDevice.scanIdentityKey(): String {
        if (!connectable && broadcastProtocolName.startsWith("Cachito ")) {
            return "broadcast:$broadcastProtocolName"
        }
        return "address:$address"
    }

    private fun displayNameForAddress(address: String, fallback: String): String =
        rememberedDevices.firstOrNull { it.address == address }?.name
            ?: fallback.ifBlank { "蓝牙设备" }

    private fun connectedSvakomSl278PairAddressFor(manufacturerData: String): String? {
        val pairIdentity = svakomSl278PairIdentity(manufacturerData) ?: return null
        return deviceRuntimeStore.keys.firstOrNull { address ->
            deviceRuntimeStore.connectionState(address) == BleConnectionState.Ready &&
                protocolStatusFor(address).id in SVAKOM_SL278_PAIR_PROTOCOL_IDS &&
                pairIdentity == svakomSl278PairIdentity(manufacturerDataForAddress(address))
        }
    }

    private val SVAKOM_SL278_PAIR_PROTOCOL_IDS = setOf(
        "svakom_sl278_plus_pair",
        "svakom_sl278_ai_pair",
        "svakom_sl278_app_pair",
    )

    private fun manufacturerDataForAddress(address: String): String =
        devices.firstOrNull { it.address == address }?.manufacturerData
            ?: rememberedDevices.firstOrNull { it.address == address }?.manufacturerData
            ?: ""

    private fun ScannedBleDevice.mergeScanUpdate(latest: ScannedBleDevice): ScannedBleDevice =
        copy(
            name = latest.name.takeUnless { it == "未命名设备" } ?: name,
            rssi = maxOf(rssi, latest.rssi),
            serviceUuids = latest.serviceUuids.ifEmpty { serviceUuids },
            manufacturerData = latest.manufacturerData.ifBlank { manufacturerData },
            scanRecordHex = latest.scanRecordHex.ifBlank { scanRecordHex },
            broadcastProtocolName = latest.broadcastProtocolName.ifBlank { broadcastProtocolName },
        )

    @Suppress("UNUSED_PARAMETER")
    private fun appendLog(message: String) = Unit

    private fun appendTrace(event: AiToyTraceEvent) {
        AiToyTraceUploader.recordBle(event)
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
        deviceRuntimeStore.keys.forEach { addresses += it }
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
                        deviceRuntimeStore.displayName(address).isNotBlank() -> deviceRuntimeStore.displayName(address)
                        else -> remembered?.name.orEmpty().ifBlank { "蓝牙设备" }
                    },
                    connected = deviceRuntimeStore.connectionState(address) == BleConnectionState.Ready,
                    isDefault = address == defaultAddress,
                    protocolName = when {
                        status.displayName.isNotBlank() && status.displayName != "尚未识别" -> status.displayName
                        isCurrent -> protocolStatus.displayName.ifBlank { remembered?.protocolName.orEmpty() }
                        else -> remembered?.protocolName.orEmpty()
                    }.ifBlank { "尚未识别" },
                    intensity = if (isCurrent) intensity else deviceRuntimeStore.intensity(address),
                    mode = if (isCurrent) mode else deviceRuntimeStore.mode(address),
                    intensityMax = status.intensityMax,
                    modeMax = status.modeMax,
                    modeLabel = status.modeLabel,
                    modeNames = status.modeNames,
                    intensityLabel = status.intensityLabel,
                    channelNames = status.channelNames,
                    controlStyle = status.controlStyle.name,
                    batteryPercent = if (deviceRuntimeStore.connectionState(address) == BleConnectionState.Ready) {
                        deviceRuntimeStore.batteryPercent(address)
                    } else {
                        null
                    },
                    features = status.features.map { feature ->
                        RelayDeviceFeature(
                            type = feature.type,
                            min = feature.min,
                            max = feature.max,
                            index = feature.index,
                            label = feature.label,
                        )
                    },
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
        if (targetAddress.isBlank() || deviceRuntimeStore.connectionState(targetAddress) != BleConnectionState.Ready) {
            relay.commandResult(command.commandId, false, "设备还没有连接")
            return
        }
        val steps = runCatching {
            parseRelaySequenceScript(command.script.orEmpty())
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
        var shouldStopRunning = true
        try {
            steps.forEach { step ->
                when (step) {
                    is RelaySequenceStep.Set -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val unlimited = durationSec == UnlimitedSequenceDurationSec
                        val actionDurationSec = if (unlimited) safeDefaultDuration else durationSec
                        delayControlWarmup(address)
                        val action = sendToySet(
                            intensityPercent = step.intensity,
                            requestedMode = step.mode,
                            autoStopSec = actionDurationSec,
                            address = address,
                            scheduleAutoStop = false,
                            keepAliveDurationSec = if (unlimited) null else actionDurationSec,
                        )
                        running = actionKeepsDeviceRunning(action)
                        if (unlimited) {
                            shouldStopRunning = false
                            return
                        }
                        delay(actionDurationSec * 1_000L)
                    }
                    is RelaySequenceStep.Dual -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val unlimited = durationSec == UnlimitedSequenceDurationSec
                        val actionDurationSec = if (unlimited) safeDefaultDuration else durationSec
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
                        sendToyAction(
                            action,
                            actionDurationSec,
                            address,
                            scheduleAutoStop = false,
                            keepAliveDurationSec = if (unlimited) null else actionDurationSec,
                        )
                        deviceRuntimeStore.setControlValues(
                            address,
                            mode = mappedMode,
                            intensity = mappedInternalIntensity,
                            secondaryIntensity = mappedExternalIntensity,
                        )
                        if (address == selectedAddress) {
                            mode = mappedMode
                            intensity = mappedInternalIntensity
                            secondaryIntensity = mappedExternalIntensity
                        }
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        if (unlimited) {
                            shouldStopRunning = false
                            return
                        }
                        delay(actionDurationSec * 1_000L)
                    }
                    is RelaySequenceStep.Pattern -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val unlimited = durationSec == UnlimitedSequenceDurationSec
                        val actionDurationSec = if (unlimited) safeDefaultDuration else durationSec
                        val status = protocolStatusFor(address)
                        val mappedMode = step.mode.coerceIn(1, status.modeMax.coerceAtLeast(1))
                        val action = patternAction(
                            nextMode = mappedMode,
                            currentIntensity = intensityForAddress(address),
                            currentSecondaryIntensity = secondaryIntensityForAddress(address),
                            status = status,
                        )
                        delayControlWarmup(address)
                        sendToyAction(
                            action,
                            actionDurationSec,
                            address,
                            scheduleAutoStop = false,
                            keepAliveDurationSec = if (unlimited) null else actionDurationSec,
                        )
                        deviceRuntimeStore.setMode(address, mappedMode)
                        if (address == selectedAddress) mode = mappedMode
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        if (unlimited) {
                            shouldStopRunning = false
                            return
                        }
                        delay(actionDurationSec * 1_000L)
                    }
                    is RelaySequenceStep.Intensity -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val unlimited = durationSec == UnlimitedSequenceDurationSec
                        val actionDurationSec = if (unlimited) safeDefaultDuration else durationSec
                        val mappedIntensity = mapIntensityPercent(step.value, address)
                        val status = protocolStatusFor(address)
                        val action = intensityAction(
                            currentMode = modeForAddress(address),
                            nextIntensity = mappedIntensity,
                            nextSecondaryIntensity = mappedIntensity,
                            status = status,
                        )
                        delayControlWarmup(address)
                        sendToyAction(
                            action,
                            actionDurationSec,
                            address,
                            scheduleAutoStop = false,
                            keepAliveDurationSec = if (unlimited) null else actionDurationSec,
                        )
                        deviceRuntimeStore.setIntensity(address, mappedIntensity)
                        if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
                            deviceRuntimeStore.setMode(address, EXCLUSIVE_INTENSITY_MODE)
                        }
                        if (status.supportsDualIntensity()) {
                            deviceRuntimeStore.setSecondaryIntensity(address, mappedIntensity)
                        }
                        if (address == selectedAddress) {
                            intensity = mappedIntensity
                            if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
                                mode = EXCLUSIVE_INTENSITY_MODE
                            }
                        }
                        if (address == selectedAddress && status.supportsDualIntensity()) {
                            secondaryIntensity = mappedIntensity
                        }
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        if (unlimited) {
                            shouldStopRunning = false
                            return
                        }
                        delay(actionDurationSec * 1_000L)
                    }
                    is RelaySequenceStep.Scalar -> {
                        val durationSec = step.durationSec ?: safeDefaultDuration
                        val unlimited = durationSec == UnlimitedSequenceDurationSec
                        val actionDurationSec = if (unlimited) safeDefaultDuration else durationSec
                        val status = protocolStatusFor(address)
                        val action = scalarFeatureAction(step, address, status)
                        delayControlWarmup(address)
                        sendToyAction(
                            action,
                            actionDurationSec,
                            address,
                            scheduleAutoStop = false,
                            keepAliveDurationSec = if (unlimited) null else actionDurationSec,
                        )
                        syncRelayDevice()
                        running = actionKeepsDeviceRunning(action)
                        if (unlimited) {
                            shouldStopRunning = false
                            return
                        }
                        delay(actionDurationSec * 1_000L)
                    }
                    is RelaySequenceStep.Sleep -> delay(step.durationSec.coerceIn(0, 300) * 1_000L)
                    RelaySequenceStep.Stop -> {
                        sendToyStop(address)
                        running = false
                    }
                }
            }
        } finally {
            if (running && shouldStopRunning) sendToyStop(address)
        }
    }

    private fun sendToySet(
        intensityPercent: Int,
        requestedMode: Int,
        autoStopSec: Int,
        address: String = selectedAddress,
        scheduleAutoStop: Boolean = true,
        keepAliveDurationSec: Int? = autoStopSec,
    ): ToyControlAction {
        val status = protocolStatusFor(address)
        val mappedIntensity = mapIntensityPercent(intensityPercent, address)
        val mappedMode = requestedMode.coerceIn(1, status.modeMax.coerceAtLeast(1))
        val action = when (status.controlStyle) {
            ToyControlStyle.PatternOnly -> ToyControlAction.Pattern(mappedMode)
            ToyControlStyle.IntensityOnly -> ToyControlAction.Intensity(mappedIntensity)
            ToyControlStyle.DualIntensityOnly -> ToyControlAction.DualMotor(1, mappedIntensity, mappedIntensity)
            ToyControlStyle.ExclusivePatternOrIntensity -> ToyControlAction.Intensity(mappedIntensity)
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(mappedMode, mappedIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                ToyControlAction.DualMotor(mappedMode, mappedIntensity, mappedIntensity)
            ToyControlStyle.IndependentFunctions -> ToyControlAction.Combined(mappedMode, mappedIntensity)
        }
        sendToyAction(action, autoStopSec, address, scheduleAutoStop, keepAliveDurationSec)
        deviceRuntimeStore.setControlValues(
            address,
            mode = if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
                EXCLUSIVE_INTENSITY_MODE
            } else {
                mappedMode
            },
            intensity = mappedIntensity,
            secondaryIntensity = mappedIntensity.takeIf { status.supportsDualIntensity() },
        )
        if (address == selectedAddress) {
            mode = if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
                EXCLUSIVE_INTENSITY_MODE
            } else {
                mappedMode
            }
            intensity = mappedIntensity
            if (status.supportsDualIntensity()) {
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
        keepAliveDurationSec: Int? = autoStopSec,
    ) {
        val status = protocolStatusFor(address)
        appendLog("准备发送控制 address=$address action=$action autoStopSec=$autoStopSec protocol=${status.id.ifBlank { "<none>" }}")
        cancelAutoStop(address)
        controllerFor(address).sendAction(action)
        startKeepAlive(action, address, keepAliveDurationSec)
        if (address == selectedAddress) busyHint = "设备正在运行"
        if (!scheduleAutoStop) return
        autoStopJobs[address] = viewModelScope.launch {
            delay(autoStopSec.coerceIn(1, MaxSequenceDurationSec) * 1_000L)
            cancelKeepAlive(address)
            runCatching { controllers[address]?.stopDevice() }
                .onSuccess { appendLog("计时结束，已停止设备 address=$address") }
                .onFailure { appendLog("停止失败：${it.message}") }
        }
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
        val status = protocolStatusFor(address)
        val interval = status.repeatIntervalMs.coerceAtLeast(0)
        if (interval <= 0) return
        if (!actionKeepsDeviceRunning(action)) return
        if (action is ToyControlAction.Pattern && status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity) {
            return
        }
        val deadline = autoStopSec?.let {
            nowMs() + it.coerceIn(1, MaxSequenceDurationSec) * 1_000L
        }
        keepAliveJobs[address] = viewModelScope.launch {
            while (isActive) {
                delay(interval.toLong())
                if (deadline != null && nowMs() >= deadline) return@launch
                runCatching {
                    controllers[address]?.sendAction(action)
                }.onFailure {
                    appendLog("保活写入失败：${it.message ?: "命令发送失败"}")
                }
            }
        }
    }

    private fun cancelKeepAlive(address: String) {
        keepAliveJobs.remove(address)?.cancel()
    }

    private fun cancelAutoStop(address: String) {
        autoStopJobs.remove(address)?.cancel()
    }

    private fun addressForDeviceId(deviceId: String): String? {
        val addresses = linkedSetOf<String>()
        rememberedDevices.forEach { addresses += it.address }
        controllers.keys.forEach { addresses += it }
        devices.forEach { addresses += it.address }
        deviceRuntimeStore.keys.forEach { addresses += it }
        selectedAddress.takeIf { it.isNotBlank() }?.let { addresses += it }
        return addresses.firstOrNull { deviceIdForAddress(it) == deviceId }
    }

    private fun defaultConnectedAddress(): String =
        deviceRuntimeStore.defaultConnectedAddress(selectedAddress)

    private fun deviceIdForAddress(address: String): String {
        return BridgePlatform.stableDeviceId(address)
    }

    fun protocolStatusForAddress(address: String): BleProtocolStatus = protocolStatusFor(address)

    fun runtimeStateForAddress(address: String): ToyRuntimeState =
        deviceRuntimeStore.runtimeState(address)

    fun modeForAddress(address: String): Int =
        if (address == selectedAddress) mode else deviceRuntimeStore.mode(address)

    fun intensityForAddress(address: String): Int =
        if (address == selectedAddress) intensity else deviceRuntimeStore.intensity(address)

    fun secondaryIntensityForAddress(address: String): Int =
        if (address == selectedAddress) secondaryIntensity else deviceRuntimeStore.secondaryIntensity(address)

    fun batteryPercentForAddress(address: String): Int? =
        deviceRuntimeStore.batteryPercent(address)

    fun modeLabelForAddress(address: String): String {
        val status = protocolStatusFor(address)
        val targetMode = modeForAddress(address)
        return modeLabel(status, targetMode)
    }

    private fun protocolStatusFor(address: String): BleProtocolStatus =
        if (address == selectedAddress) protocolStatus else deviceRuntimeStore.protocolStatus(address)

    private fun clampCurrentControlValues() {
        mode = mode.coerceIn(1, protocolStatus.modeMax.coerceAtLeast(1))
        intensity = intensity.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        secondaryIntensity = secondaryIntensity.coerceIn(0, protocolStatus.intensityMax.coerceAtLeast(1))
        if (selectedAddress.isNotBlank()) {
            deviceRuntimeStore.setControlValues(
                selectedAddress,
                mode = mode,
                intensity = intensity,
                secondaryIntensity = secondaryIntensity.takeIf { protocolStatus.supportsDualIntensity() },
            )
            if (protocolStatus.supportsDualIntensity()) {
                deviceRuntimeStore.setSecondaryIntensity(selectedAddress, secondaryIntensity)
            }
        }
    }

    private fun connectedSavedDeviceCount(): Int =
        deviceRuntimeStore.connectedSavedDeviceCount(rememberedDevices)

    private fun connectedDeviceCount(): Int =
        deviceRuntimeStore.connectedDeviceCount()

    private fun autoOfflineWhenNoConnectedSavedDevice() {
        if (relayState == "已在线" && connectedSavedDeviceCount() == 0) {
            disconnectRelay()
        }
    }

    fun currentModeLabel(): String {
        return modeLabel(protocolStatus, mode)
    }

    private fun modeLabel(status: BleProtocolStatus, targetMode: Int): String {
        if (status.controlStyle == ToyControlStyle.ExclusivePatternOrIntensity && targetMode == EXCLUSIVE_INTENSITY_MODE) {
            return if (status.id == "jiuai_jellyfish_stroker") "当前为匀速伸缩" else "当前为${status.intensityLabel}"
        }
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
            ToyControlStyle.DualIntensityOnly ->
                dualIntensityAction(1, currentIntensity, currentSecondaryIntensity, status)
            ToyControlStyle.PatternOnly,
            ToyControlStyle.ExclusivePatternOrIntensity -> ToyControlAction.Pattern(nextMode)
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(nextMode, currentIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                dualIntensityAction(nextMode, currentIntensity, currentSecondaryIntensity, status)
            ToyControlStyle.IndependentFunctions -> ToyControlAction.Combined(nextMode, currentIntensity)
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
            ToyControlStyle.DualIntensityOnly ->
                dualIntensityAction(1, nextIntensity, nextSecondaryIntensity, status)
            ToyControlStyle.CombinedPatternAndIntensity -> ToyControlAction.Combined(currentMode, nextIntensity)
            ToyControlStyle.PatternAndDualIntensity ->
                dualIntensityAction(currentMode, nextIntensity, nextSecondaryIntensity, status)
            ToyControlStyle.IndependentFunctions -> ToyControlAction.Combined(currentMode, nextIntensity)
            ToyControlStyle.IntensityOnly,
            ToyControlStyle.ExclusivePatternOrIntensity -> ToyControlAction.Intensity(nextIntensity)
        }

    /**
     * 把 MCP 的 scalar(feature=N,...) 步骤映射为具体动作。
     * 多功能设备按 feature 下标定位功能区，独立控制伸缩/震动/吮吸/拍打/加热等；
     * 其它协议按下标退化为通用强度或组合控制，保持脚本兼容。
     */
    private fun scalarFeatureAction(
        step: RelaySequenceStep.Scalar,
        address: String,
        status: BleProtocolStatus,
    ): ToyControlAction {
        if (status.controlStyle != ToyControlStyle.IndependentFunctions) {
            val mappedIntensity = mapIntensityPercent(step.value, address)
            return intensityAction(
                currentMode = step.mode.coerceIn(1, status.modeMax.coerceAtLeast(1)),
                nextIntensity = mappedIntensity,
                nextSecondaryIntensity = mappedIntensity,
                status = status,
            )
        }
        val feature = status.features.getOrNull(step.featureIndex)
            ?: status.features.firstOrNull()
            ?: return ToyControlAction.Intensity(mapIntensityPercent(step.value, address))
        val functionCode = status.independentFunctionCode(feature).coerceIn(1, 9)
        val safeMode = step.mode.coerceIn(0, status.independentFunctionModeMax(feature).coerceAtLeast(1))
        return ToyControlAction.Combined(
            mode = functionCode * 100 + safeMode,
            intensity = mapScalarFeatureValue(step.value, feature),
        )
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
        controlTrialJob?.cancel()
        controlTrialJob = viewModelScope.launch {
            delay(250L)
            pendingControlAction?.let(::sendLocalControlAction)
        }
    }

    private fun sendLocalControlAction(action: ToyControlAction) {
        runCatching {
            val address = selectedAddress.ifBlank { error("请选择设备") }
            appendLog("准备发送本地控制 action=$action protocol=${protocolStatus.id.ifBlank { "<none>" }}")
            controllerFor(address).sendAction(action)
            deviceRuntimeStore.setControlValues(
                address,
                mode = mode,
                intensity = intensity,
                secondaryIntensity = secondaryIntensity.takeIf { protocolStatus.supportsDualIntensity() },
            )
            syncRelayDevice()
            cancelAutoStop(address)
            startKeepAlive(action, address)
        }.onFailure {
            busyHint = it.message ?: "控制失败"
        }
    }

    private fun recordProtocolAdapterFeedback(result: String) {
        val message = "协议适配反馈 event=protocol_adapter_feedback result=$result " +
            "deviceName=${selectedName.ifBlank { "<none>" }} address=${selectedAddress.ifBlank { "<none>" }} " +
            "protocol=${protocolStatus.id.ifBlank { "<none>" }} name=${protocolStatus.displayName}"
        appendLog(message)
        appendTrace(
            AiToyTraceEvent(
                message = message,
                error = result != "success",
                type = "protocol_adapter_feedback",
                uploadPolicy = AiToyTraceUploadPolicy.Always,
            )
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
        val current = RememberedToy(
            name = remark,
            address = address,
            protocolName = protocolName,
            lastSeenAt = nowMs(),
            manufacturerData = scanned?.manufacturerData ?: existing?.manufacturerData.orEmpty(),
            scanRecordHex = scanned?.scanRecordHex ?: existing?.scanRecordHex.orEmpty(),
        )
        rememberedDevicesJson = (listOf(current) + rememberedDevices
            .filterNot { it.address == address }
            .take(7))
            .toJson()
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
        autoStopJobs.keys.toList().forEach(::cancelAutoStop)
        keepAliveJobs.keys.toList().forEach(::cancelKeepAlive)
        relay.close()
    }

}
