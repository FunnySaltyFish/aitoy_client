package com.funny.aitoy

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.funny.aitoy.ble.AndroidBleController
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.aitoy.relay.RelayClient
import com.funny.aitoy.relay.RelayCommand
import com.funny.aitoy.relay.RelayDevice
import com.funny.data_saver.core.mutableDataSaverStateOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        if (serverUrl.contains("127.0.0.1") || serverUrl.contains("192.168.")) {
            serverUrl = PRODUCTION_BASE_URL
        }
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

    fun clearLogs() {
        logs.clear()
    }

    fun connectRelay() {
        relay.connect(serverUrl, userToken)
    }

    fun disconnectRelay() {
        relay.disconnect()
    }

    fun checkAppConfig() {
        val request = Request.Builder()
            .url("$PRODUCTION_BASE_URL/api/app/config?versionCode=$APP_VERSION_CODE")
            .get()
            .build()
        httpClient.newCall(request).enqueue(
            SimpleCallback(
                onSuccess = { text ->
                    val json = JSONObject(text)
                    if (!json.optBoolean("ok")) error(
                        json.optString("error").ifBlank { "检查更新失败" })
                    communityCode = json.optString("communityCode")
                    val update = json.getJSONObject("update")
                    updateState = AppUpdateState(
                        checked = true,
                        updateAvailable = update.optBoolean("updateAvailable"),
                        forceUpdate = update.optBoolean("forceUpdate"),
                        latestVersionName = update.optString("latestVersionName"),
                        message = update.optString("message"),
                        apkUrl = update.optString("apkUrl"),
                    )
                },
                onFailure = { message ->
                    updateState = AppUpdateState(checked = true, message = message)
                },
            )
        )
    }

    fun fetchCommunityCode() {
        val request = Request.Builder()
            .url("$PRODUCTION_BASE_URL/api/app/config?versionCode=$APP_VERSION_CODE")
            .get()
            .build()
        communityMessage = "正在获取口令..."
        httpClient.newCall(request).enqueue(
            SimpleCallback(
                onSuccess = { text ->
                    val json = JSONObject(text)
                    if (!json.optBoolean("ok")) error(
                        json.optString("error").ifBlank { "获取失败" })
                    communityCode = json.optString("communityCode")
                    communityMessage = "口令已准备好，可以复制到小红书打开"
                },
                onFailure = { message -> communityMessage = message },
            )
        )
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
        val payload = JSONObject()
            .put("serviceUuid", serviceUuid.trim())
            .put("writeUuid", writeUuid.trim())
            .put("notifyUuid", notifyUuid.trim())
            .put("commandTemplate", commandTemplate.trim())
            .put("stopTemplate", stopTemplate.trim())
            .put("writeWithResponse", writeWithResponse)
            .put("manualControlEnabled", true)
            .put("sourceDeviceName", selectedName.ifBlank { "我的小玩具" })
            .put("protocolName", protocolStatus.displayName.ifBlank { "手动指令" })
        val body = JSONObject()
            .put("title", selectedName.ifBlank { "设备指令" })
            .put("baseUrl", publicBaseUrl())
            .put("payload", payload)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("${serverUrl.trimEnd('/')}/api/protocol-shares")
            .post(body)
            .build()
        httpClient.newCall(request).enqueue(
            SimpleCallback(
                onSuccess = { text ->
                    val json = JSONObject(text)
                    if (!json.optBoolean("ok")) error(
                        json.optString("error").ifBlank { "分享失败" })
                    shareMessage = json.optString("url").ifBlank { json.optString("importCode") }
                    appendLog("已生成指令分享：$shareMessage")
                },
                onFailure = { message -> shareMessage = message },
            )
        )
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
        val request = Request.Builder()
            .url("${(linkBaseUrl ?: serverUrl).trimEnd('/')}/api/protocol-shares/$shareId")
            .get()
            .build()
        httpClient.newCall(request).enqueue(
            SimpleCallback(
                onSuccess = { text ->
                    val json = JSONObject(text)
                    if (!json.optBoolean("ok")) error(
                        json.optString("error").ifBlank { "导入失败" })
                    val payload = json.getJSONObject("payload")
                    serviceUuid = payload.optString("serviceUuid", serviceUuid)
                    writeUuid = payload.optString("writeUuid", writeUuid)
                    notifyUuid = payload.optString("notifyUuid", notifyUuid)
                    commandTemplate = payload.optString("commandTemplate", commandTemplate)
                    stopTemplate = payload.optString("stopTemplate", stopTemplate)
                    writeWithResponse = payload.optBoolean("writeWithResponse", writeWithResponse)
                    manualControlEnabled = true
                    importMessage = "已导入，可以重新连接设备试试"
                    appendLog("已导入分享指令：${json.optString("title")}")
                },
                onFailure = { message -> importMessage = message },
            )
        )
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
            when (command.action) {
                "set" -> {
                    mainHandler.removeCallbacks(autoStop)
                    val maxIntensity = protocolStatus.intensityMax.coerceAtLeast(1)
                    val mappedIntensity = (
                            ((command.intensity ?: 0).coerceIn(0, 100) / 100.0) * maxIntensity
                            ).roundToInt().coerceIn(0, maxIntensity)
                    controller.sendCommand(
                        mode = command.mode?.coerceAtLeast(1) ?: 1,
                        intensity = mappedIntensity,
                    )
                    mode = command.mode?.coerceAtLeast(1) ?: 1
                    intensity = mappedIntensity
                    syncRelayDevice()
                    mainHandler.postDelayed(
                        autoStop,
                        (command.durationSec ?: 8).coerceIn(1, 15) * 1_000L,
                    )
                }
                "stop", "stop_all" -> {
                    mainHandler.removeCallbacks(autoStop)
                    controller.stopDevice()
                    intensity = 0
                    syncRelayDevice()
                }
                else -> error("不支持的命令：${command.action}")
            }
        }.onSuccess {
            relay.commandResult(command.commandId, true, "命令已写入设备")
        }.onFailure {
            relay.commandResult(command.commandId, false, it.message ?: "命令执行失败")
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
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private inner class SimpleCallback(
        private val onSuccess: (String) -> Unit,
        private val onFailure: (String) -> Unit,
    ) : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            mainHandler.post { onFailure(e.message ?: "网络连接失败") }
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            val text = response.body?.string().orEmpty()
            mainHandler.post {
                runCatching {
                    if (!response.isSuccessful) error("服务器暂时不可用：${response.code}")
                    onSuccess(text)
                }.onFailure {
                    onFailure(it.message ?: "请求失败")
                }
            }
        }
    }

    companion object {
        const val PRODUCTION_BASE_URL = "https://aitoy.funnysaltyfish.fun"
        const val MCP_URL = "https://aitoy.funnysaltyfish.fun/mcp"
        const val APP_VERSION_CODE = 1
        const val APP_VERSION_NAME = "1.0"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
