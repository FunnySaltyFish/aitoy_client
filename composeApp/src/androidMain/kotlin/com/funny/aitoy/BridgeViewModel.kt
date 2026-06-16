package com.funny.aitoy

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funny.aitoy.ble.AndroidBleController
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.chat.ToyTool
import com.funny.aitoy.chat.ToyToolResult
import com.funny.aitoy.core.prefs.AiToyPrefs
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.core.kmp.appCtx
import com.funny.aitoy.core.kmp.openUrl
import com.funny.aitoy.diagnostics.AiToyCrashReporter
import com.funny.aitoy.network.api.AiToyServices
import com.funny.aitoy.network.api.apiRequest
import com.funny.aitoy.network.api.service.CreateProtocolShareRequest
import com.funny.aitoy.relay.RelayClient
import com.funny.aitoy.relay.RelayCommand
import com.funny.aitoy.relay.RelayDevice
import com.funny.aitoy.update.AppUpdateInstaller
import com.funny.data_saver.core.mutableDataSaverStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var showAdvanced by mutableStateOf(false)
    var showGuide by mutableStateOf(false)
    var showTemplateLibrary by mutableStateOf(false)
    var shareMessage by mutableStateOf("")
        private set
    var importCode by mutableStateOf("")
    var importMessage by mutableStateOf("")
        private set
    var communityCode by mutableStateOf("")
        private set
    var communityMessage by mutableStateOf("")
        private set
    var updateState by mutableStateOf(AppUpdateState())
        private set
    var updateDownloadProgress by mutableStateOf(0)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var crashNotice by mutableStateOf(AiToyCrashReporter.consumeLastCrashNotice().orEmpty())
        private set

    var serverUrl: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "AITOY_SERVER_URL",
        PRODUCTION_BASE_URL,
    )
    var userToken: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "AITOY_USER_TOKEN",
        "ut_${UUID.randomUUID().toString().replace("-", "")}",
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
            serviceUuid = "0000dddd-0000-1000-8000-00805f9b34fb",
            writeUuid = "0000ddd1-0000-1000-8000-00805f9b34fb",
            notifyUuid = "0000ddd2-0000-1000-8000-00805f9b34fb",
            commandTemplate = "55 09 00 00 {mode} {intensity} 00",
            stopTemplate = "55 FE 09 00 00 00 00",
            writeWithResponse = false,
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
                rememberConnectedDevice()
                syncRelayDevice()
            }
        },
        onProtocol = { protocolStatus = it },
        onLog = ::appendLog,
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        runCatching { controller.stopDevice() }
            .onSuccess { appendLog("安全计时结束，已自动发送停止指令") }
            .onFailure { appendLog("自动停止失败：${it.message}") }
    }
    private val relay = RelayClient(
        onState = {
            relayState = it
            if (it == "已在线") syncRelayDevice()
        },
        onLog = ::appendLog,
        onCommand = ::handleRelayCommand,
    )
    init {
        if (serverUrl.contains("127.0.0.1") || serverUrl.contains("192.168.")) {
            serverUrl = PRODUCTION_BASE_URL
        }
        if (AiToyPrefs.apiPrefix == "aitoy") {
            AiToyPrefs.apiPrefix = "api"
        }
        AiToyPrefs.serverBaseUrl = PRODUCTION_BASE_URL
        checkAppConfig()
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
        selectedAddress = device.address
        selectedName = device.name
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
        controller.disconnect()
    }

    fun sendTest() = runAction {
        busyHint = "已发送轻柔测试，稍后会自动停止"
        controller.sendCommand(mode, intensity)
        syncRelayDevice()
        mainHandler.removeCallbacks(autoStop)
        mainHandler.postDelayed(autoStop, 5_000L)
    }

    fun stopDevice() = runAction {
        busyHint = "已停止"
        mainHandler.removeCallbacks(autoStop)
        controller.stopDevice()
        intensity = 0
        syncRelayDevice()
    }

    fun stopAllDevices() = runAction {
        busyHint = "已全部停止"
        mainHandler.removeCallbacks(autoStop)
        controller.stopDevice()
        intensity = 0
        syncRelayDevice()
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
        applyToyCommand(
            action = "set",
            intensityPercent = intensityPercent.coerceIn(0, 100),
            requestedMode = mode.coerceAtLeast(1),
            durationSec = safeDuration,
        )
        ToyToolResult(true, "已调节设备，$safeDuration 秒后会自动停止。")
    }.getOrElse {
        ToyToolResult(false, it.message ?: "操作没有完成。")
    }

    fun stopToyForChat(all: Boolean = false): ToyToolResult = runCatching {
        applyToyCommand(action = if (all) "stop_all" else "stop")
        ToyToolResult(true, if (all) "已全部停止。" else "已停止。")
    }.getOrElse {
        ToyToolResult(false, it.message ?: "操作没有完成。")
    }

    fun clearLogs() {
        logs.clear()
    }

    fun connectRelay() {
        relay.connect(serverUrl, userToken)
    }

    fun disconnectRelay() {
        relay.disconnect()
    }

    fun dismissCrashNotice() {
        crashNotice = ""
    }

    fun openUpdateInBrowser() {
        val url = updateState.downloadPageUrl.ifBlank { updateState.apkUrl }
        if (url.isBlank()) return
        appCtx.openUrl(url)
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
                    mainHandler.post { updateDownloadProgress = progress }
                }
            }.onSuccess {
                mainHandler.post {
                    updateDownloading = false
                    updateDownloadProgress = 100
                }
            }.onFailure {
                mainHandler.post {
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
                communityCode = config.communityCode
                val update = config.update
                updateState = AppUpdateState(
                    checked = true,
                    updateAvailable = update.updateAvailable,
                    forceUpdate = update.forceUpdate,
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

    fun fetchCommunityCode() {
        communityMessage = "正在获取口令..."
        viewModelScope.launch {
            runCatching {
                apiRequest { AiToyServices.appService.config(APP_VERSION_CODE) }
            }.onSuccess { config ->
                communityCode = config.communityCode
                communityMessage = "口令已准备好，可以复制到小红书打开"
            }.onFailure {
                communityMessage = it.message ?: "获取失败"
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
        importMessage = "已选择 ${preset.name}，请重新连接设备测试"
        appendLog("已应用模板：${preset.name}")
    }

    fun importFromLink(link: String?) {
        val resolved = link?.trim().orEmpty()
        if (resolved.isBlank()) return
        val uri = runCatching { Uri.parse(resolved) }.getOrNull()
        val server = uri?.getQueryParameter("server")?.trim().orEmpty()
        if (server == PRODUCTION_BASE_URL) {
            serverUrl = server
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
                it == PRODUCTION_BASE_URL
            }
        }?.trimEnd('/')
        if (linkBaseUrl != null) serverUrl = linkBaseUrl
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
                importMessage = "已导入，可以重新连接设备试试"
                appendLog("已导入分享指令：${result.title}")
            }.onFailure {
                importMessage = it.message ?: "导入失败"
            }
        }
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
        runCatching {
            applyToyCommand(
                action = command.action,
                intensityPercent = command.intensity,
                requestedMode = command.mode,
                durationSec = command.durationSec,
            )
        }.onSuccess {
            relay.commandResult(command.commandId, true, "命令已写入设备")
        }.onFailure {
            relay.commandResult(command.commandId, false, it.message ?: "命令执行失败")
        }
    }

    private fun applyToyCommand(
        action: String,
        intensityPercent: Int? = null,
        requestedMode: Int? = null,
        durationSec: Int? = null,
    ) {
        when (action) {
            "set" -> {
                mainHandler.removeCallbacks(autoStop)
                val maxIntensity = protocolStatus.intensityMax.coerceAtLeast(1)
                val mappedIntensity = (
                    ((intensityPercent ?: 0).coerceIn(0, 100) / 100.0) * maxIntensity
                    ).roundToInt().coerceIn(0, maxIntensity)
                val mappedMode = requestedMode?.coerceAtLeast(1) ?: 1
                controller.sendCommand(mode = mappedMode, intensity = mappedIntensity)
                mode = mappedMode
                intensity = mappedIntensity
                busyHint = "设备正在运行，稍后会自动停止"
                syncRelayDevice()
                mainHandler.postDelayed(autoStop, (durationSec ?: 8).coerceIn(1, 15) * 1_000L)
            }
            "stop", "stop_all" -> {
                mainHandler.removeCallbacks(autoStop)
                controller.stopDevice()
                intensity = 0
                busyHint = if (action == "stop_all") "已全部停止" else "已停止"
                syncRelayDevice()
            }
            else -> error("暂不支持这个操作")
        }
    }

    private fun currentDeviceId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(selectedAddress.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "dev_$digest"
    }

    private fun rememberConnectedDevice() {
        if (selectedAddress.isBlank()) return
        val current = JSONObject()
            .put("name", selectedName.ifBlank { "我的小玩具" })
            .put("address", selectedAddress)
            .put("protocolName", protocolStatus.displayName.ifBlank { "已保存" })
            .put("lastSeenAt", System.currentTimeMillis())
        val merged = JSONArray()
        merged.put(current)
        rememberedDevices
            .filterNot { it.address == selectedAddress }
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

    private fun publicBaseUrl(): String {
        return PRODUCTION_BASE_URL
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
        const val PRODUCTION_BASE_URL = "https://aitoy.funnysaltyfish.fun"
        const val MCP_URL = "https://aitoy.funnysaltyfish.fun/mcp"
        const val APP_VERSION_CODE = 1
        const val APP_VERSION_NAME = "1.0"
    }
}
