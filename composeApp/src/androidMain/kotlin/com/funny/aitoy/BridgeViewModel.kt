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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funny.aitoy.ble.AndroidBleController
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.CachitoBroadcastProtocol
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.chat.ToyTool
import com.funny.aitoy.chat.ToyToolResult
import com.funny.aitoy.core.kmp.ToastType
import com.funny.aitoy.core.kmp.appCtx
import com.funny.aitoy.core.kmp.openUrl
import com.funny.aitoy.core.kmp.toast
import com.funny.aitoy.core.prefs.AiToyPrefs
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.diagnostics.AiToyCrashReporter
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
)

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

    data class Sleep(val durationSec: Int) : RelaySequenceStep

    data object Stop : RelaySequenceStep
}

private class SequenceScriptFormatException : IllegalArgumentException("序列脚本格式错误")

private const val LegacyDefaultUserToken = "ut_fishfish"
private const val DefaultCommunityUrl = "https://qm.qq.com/q/ytRVIe6O7m"
private const val DefaultTutorialUrl = "https://docs.qq.com/s/4dhBqBSu1u5900Qh7qvYHW/folder/YFZTdLuzaIxv"

private fun generateDefaultUserToken(): String = "ut_${UUID.randomUUID().toString().replace("-", "")}"

class BridgeViewModel : ViewModel() {
    val devices = mutableStateListOf<ScannedBleDevice>()
    val logs = mutableStateListOf<String>()

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
        "55 09 00 00 {mode} {intensity} 00",
    )
    var stopTemplate: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "BLE_STOP_TEMPLATE",
        "55 FE 09 00 00 00 00",
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
                )
            }.filter { it.address.isNotBlank() }
        }.getOrDefault(emptyList())

    val protocolPresets = listOf(
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
            description = "设置强度、节奏和持续时间；到时会自动停止。",
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

    private val controller = AndroidBleController(
        onDevice = ::onDeviceFound,
        onState = {
            connectionState = it
            if (it == BleConnectionState.Ready) {
                currentDeviceSaved = rememberedDevices.any { toy -> toy.address == selectedAddress }
                syncRelayDevice()
                autoOnlineSavedDevice()
            }
        },
        onProtocol = { protocolStatus = it },
        onProtocolAttempt = { protocolAttemptStatus = it },
        onLog = ::appendLog,
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        runCatching { controller.stopDevice(mode) }
            .onSuccess { appendLog("安全计时结束，已自动发送停止指令") }
            .onFailure { appendLog("自动停止失败：${it.message}") }
    }
    private val controlTrial = Runnable {
        runCatching {
            controller.sendCommand(mode, intensity)
            syncRelayDevice()
            mainHandler.removeCallbacks(autoStop)
            mainHandler.postDelayed(autoStop, 5_000L)
        }.onFailure {
            busyHint = it.message ?: "控制失败"
        }
    }
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
    private var relaySequenceJob: Job? = null
    private var debugLogTapCount = 0
    private var autoOnlineAddress = ""

    init {
        if (AiToyPrefs.apiPrefix == "aitoy") {
            AiToyPrefs.apiPrefix = OkHttpUtils.DEFAULT_API_PREFIX
        }
        if (AiToyPrefs.serverBaseUrl.isBlank()) {
            OkHttpUtils.useProductionBaseUrl()
        }
        refreshBaseUrlState()
        ensureDefaultUserToken()
        userTokenDraft = userToken
        checkAppConfig()
    }

    private fun ensureDefaultUserToken() {
        if (userToken.isBlank() || userToken == LegacyDefaultUserToken) {
            userToken = generateDefaultUserToken()
        }
    }

    fun toggleScan() {
        errorMessage = null
        if (scanning) {
            controller.stopScan()
            scanning = false
        } else {
            devices.clear()
            controller.startScan()
            scanning = true
        }
    }

    fun connect(device: ScannedBleDevice) = runAction {
        if (!device.controllable) {
            errorMessage = "这个广播暂时无法控制"
            appendLog("跳过不可连接且未识别的广播设备：name=${device.name} address=${device.address}")
            return@runAction
        }
        selectedAddress = device.address
        selectedName = device.name
        protocolAttemptStatus = ProtocolAttemptStatus()
        protocolStatus = BleProtocolStatus()
        controlTrialStarted = false
        currentDeviceSaved = rememberedDevices.any { it.address == device.address }
        showDeviceRemarkDialog = false
        deviceRemarkDraft =
            rememberedDevices.firstOrNull { it.address == device.address }?.name.orEmpty()
        scanning = false
        busyHint = "正在为你连接 ${device.name}"
        controller.connect(device, currentTemplate())
    }

    fun connectRemembered(toy: RememberedToy) {
        connect(
            ScannedBleDevice(
                name = toy.name,
                address = toy.address,
                rssi = 0,
                connectable = true,
            )
        )
    }

    fun disconnect() {
        busyHint = ""
        protocolAttemptStatus = ProtocolAttemptStatus()
        controlTrialStarted = false
        showDeviceRemarkDialog = false
        mainHandler.removeCallbacks(controlTrial)
        controller.disconnect()
    }

    fun updateMode(value: Int) {
        val next = value.coerceIn(1, protocolStatus.modeMax.coerceAtLeast(1))
        if (mode == next) return
        mode = next
        scheduleControlTrial()
    }

    fun updateIntensity(value: Int) {
        val next = value.coerceIn(1, protocolStatus.intensityMax.coerceAtLeast(1))
        if (intensity == next) return
        intensity = next
        scheduleControlTrial()
    }

    fun stopDevice() = runAction {
        busyHint = "已停止"
        mainHandler.removeCallbacks(autoStop)
        mainHandler.removeCallbacks(controlTrial)
        controller.stopDevice(mode)
        syncRelayDevice()
    }

    fun stopAllDevices() = runAction {
        busyHint = "已全部停止"
        mainHandler.removeCallbacks(autoStop)
        mainHandler.removeCallbacks(controlTrial)
        controller.stopDevice(mode)
        syncRelayDevice()
    }

    fun confirmCurrentDeviceWorks() {
        if (selectedAddress.isBlank()) return
        deviceRemarkDraft = rememberedDevices.firstOrNull { it.address == selectedAddress }?.name
            ?: selectedName.ifBlank { "我的小玩具" }
        editingRemarkAddress = selectedAddress
        editingRemarkProtocolName = protocolStatus.displayName.ifBlank { "已保存" }
        showDeviceRemarkDialog = true
    }

    fun reportCurrentDeviceNotWorking() {
        busyHint = "可以继续调整，或到高级工具导入指令"
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
                        .put("lastSeenAt", it.lastSeenAt),
                )
            }
        rememberedDevicesJson = merged.toString()
        if (toy.address == selectedAddress) currentDeviceSaved = false
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
        ToyToolResult(true, "已调节设备，$safeDuration 秒后会自动停止。")
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
        importMessage = "已选择 ${preset.name}，下一步重新连接设备测试"
        busyHint = "手动指令已准备好，请重新连接测试"
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
                importMessage = "已导入，下一步重新连接设备测试"
                busyHint = "手动指令已准备好，请重新连接测试"
                appendLog("已导入分享指令：${result.title}")
            }.onFailure {
                importMessage = it.message ?: "导入失败"
            }
        }
    }

    fun reconnectWithCurrentTemplate() {
        if (selectedAddress.isBlank()) {
            if (!scanning) toggleScan()
            busyHint = "请选择要测试的设备"
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
    }

    private fun appendLog(message: String) {
        mainHandler.post {
            logs += message
            while (logs.size > 200) logs.removeAt(0)
        }
    }

    private fun syncRelayDevice() {
        if (selectedAddress.isBlank()) return
        relay.syncDevices(
            listOf(
                RelayDevice(
                    deviceId = currentDeviceId(),
                    displayName = selectedName.ifBlank { "蓝牙设备" },
                    connected = connectionState == BleConnectionState.Ready,
                    isDefault = true,
                    protocolName = protocolStatus.displayName.ifBlank { "尚未识别" },
                    intensity = intensity,
                    mode = mode,
                    intensityMax = protocolStatus.intensityMax,
                )
            )
        )
    }

    private fun handleRelayCommand(command: RelayCommand) {
        if (command.action != "sequence") {
            relay.commandResult(command.commandId, false, "暂不支持这个操作")
            return
        }
        val steps = runCatching {
            parseRelaySequenceScript(command.script.orEmpty(), command.defaultDurationSec)
        }.getOrElse {
            relay.commandResult(command.commandId, false, "序列脚本格式错误")
            return
        }
        val previousJob = relaySequenceJob
        relaySequenceJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            relay.commandResult(command.commandId, true, "序列已执行")
            runCatching {
                executeRelaySequence(steps, command.defaultDurationSec)
            }.onFailure {
                if (it is CancellationException) return@launch
                appendLog("序列执行失败：${it.message ?: "命令执行失败"}")
            }
        }
    }

    private suspend fun executeRelaySequence(
        steps: List<RelaySequenceStep>,
        defaultDurationSec: Int?,
    ) {
        val safeDefaultDuration = (defaultDurationSec ?: 30).coerceIn(1, 30)
        var stopped = false
        try {
            steps.forEachIndexed { index, step ->
                when (step) {
                    is RelaySequenceStep.Set -> {
                        val durationSec = step.durationSec
                        sendToySet(
                            intensityPercent = step.intensity,
                            requestedMode = step.mode,
                            autoStopSec = durationSec ?: safeDefaultDuration,
                        )
                        stopped = false
                        if (durationSec != null) {
                            delay(durationSec.coerceIn(1, 30) * 1_000L)
                            sendToyStop()
                            stopped = true
                        } else if (index == steps.lastIndex) {
                            delay(safeDefaultDuration * 1_000L)
                        }
                    }
                    is RelaySequenceStep.Sleep -> delay(step.durationSec.coerceIn(0, 300) * 1_000L)
                    RelaySequenceStep.Stop -> {
                        sendToyStop()
                        stopped = true
                    }
                }
            }
        } finally {
            if (!stopped) sendToyStop()
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
                    val intensity = args["intensity"]?.coerceIn(0, 40) ?: throw SequenceScriptFormatException()
                    val duration = args["duration"]?.coerceIn(1, 30)
                    RelaySequenceStep.Set(mode = mode, intensity = intensity, durationSec = duration)
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
    ) {
        mainHandler.removeCallbacks(autoStop)
        val maxIntensity = protocolStatus.intensityMax.coerceAtLeast(1)
        val mappedIntensity = (
            (intensityPercent.coerceIn(0, 100) / 100.0) * maxIntensity
            ).roundToInt().coerceIn(0, maxIntensity)
        val mappedMode = requestedMode.coerceAtLeast(1)
        controller.sendCommand(mode = mappedMode, intensity = mappedIntensity)
        mode = mappedMode
        intensity = mappedIntensity
        busyHint = "设备正在运行，稍后会自动停止"
        syncRelayDevice()
        mainHandler.postDelayed(autoStop, autoStopSec.coerceIn(1, 30) * 1_000L)
    }

    private fun sendToyStop(all: Boolean = false) {
        mainHandler.removeCallbacks(autoStop)
        controller.stopDevice(mode)
        busyHint = if (all) "已全部停止" else "已停止"
        syncRelayDevice()
    }

    private fun currentDeviceId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(selectedAddress.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "dev_$digest"
    }

    fun currentModeLabel(): String {
        if (protocolStatus.id == "cachito_advertise") {
            val name = CachitoBroadcastProtocol.modeNames().getOrNull(mode - 1)
            return if (name.isNullOrBlank()) "模板 ${mode}" else "模板 ${mode}：$name"
        }
        if (protocolStatus.id == "kisstoy_gatt") {
            val name = listOf("主电机", "第二电机", "抽插", "双通道").getOrNull(mode - 1)
            return if (name.isNullOrBlank()) "节奏 ${mode}" else "节奏 ${mode}：$name"
        }
        return "节奏 ${mode}"
    }

    private fun scheduleControlTrial() {
        if (!protocolStatus.controllable || connectionState != BleConnectionState.Ready) return
        controlTrialStarted = true
        busyHint = "正在尝试，稍后会自动停止"
        mainHandler.removeCallbacks(controlTrial)
        mainHandler.postDelayed(controlTrial, 250L)
    }

    private fun rememberDevice(
        address: String,
        remark: String,
        protocolName: String,
    ) {
        if (address.isBlank()) return
        val current = JSONObject()
            .put("name", remark)
            .put("address", address)
            .put("protocolName", protocolName)
            .put("lastSeenAt", System.currentTimeMillis())
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
                        .put("lastSeenAt", it.lastSeenAt),
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
        controller.close()
        mainHandler.removeCallbacks(autoStop)
        relay.close()
    }

    companion object {
        val APP_VERSION_CODE: Int
            get() = currentPackageVersionCode()
        val APP_VERSION_NAME: String
            get() = currentPackageVersionName()

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
