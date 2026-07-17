package com.funny.aitoy.relay

import com.funny.aitoy.core.log.Log
import com.funny.aitoy.core.utils.JsonX
import com.funny.aitoy.core.utils.nowMs
import com.funny.aitoy.diagnostics.AiToyTraceEvent
import com.funny.aitoy.diagnostics.AiToyTraceUploadPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class RelayCommand(
    val commandId: String,
    val action: String,
    val deviceId: String?,
    val intensity: Int?,
    val mode: Int?,
    val durationSec: Int?,
    val defaultDurationSec: Int?,
    val script: String?,
    val createdAt: Long?,
)

data class RelayDevice(
    val deviceId: String,
    val displayName: String,
    val bleAddress: String = "",
    val connectable: Boolean = true,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: String = "",
    val scanRecordHex: String = "",
    val broadcastProtocolName: String = "",
    val connected: Boolean,
    val isDefault: Boolean,
    val protocolName: String,
    val intensity: Int,
    val mode: Int,
    val intensityMax: Int,
    val modeMax: Int,
    val modeLabel: String = "模式",
    val modeNames: List<String> = emptyList(),
    val intensityLabel: String = "强度",
    val channelNames: List<String> = emptyList(),
    val controlStyle: String,
    val batteryPercent: Int? = null,
    val features: List<RelayDeviceFeature> = emptyList(),
)

data class RelayDeviceFeature(
    val type: String,
    val min: Int,
    val max: Int,
    val index: Int,
    val label: String = "",
    val modeMax: Int = 0,
)

class RelayClient(
    private val scope: CoroutineScope,
    private val onState: (String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onCommand: (RelayCommand) -> Unit,
    private val onTrace: (AiToyTraceEvent) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private var lastServerUrl = ""
    private var lastUserToken = ""
    private var userRequestedOnline = false
    private var connecting = false
    private var onlineConfirmed = false
    private var retryCount = 0
    private var connectionGeneration = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    fun connect(serverUrl: String, userToken: String) {
        connect(serverUrl, userToken, resetRetry = true)
    }

    private fun connect(serverUrl: String, userToken: String, resetRetry: Boolean) {
        userRequestedOnline = true
        lastServerUrl = serverUrl
        lastUserToken = userToken
        reconnectJob?.cancel()
        stopHeartbeat()
        socket?.close(1000, "Reconnect")
        socket = null
        onlineConfirmed = false
        if (resetRetry) retryCount = 0
        connecting = true
        val generation = ++connectionGeneration
        val wsUrl = serverUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/app?token=$userToken"
        trace("连接 AI 伙伴：${safeWsUrl(wsUrl)}")
        emitState("正在连接")
        socket = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (generation != connectionGeneration) return
                    connecting = false
                    onlineConfirmed = false
                    retryCount = 0
                    trace(
                        "AI 伙伴连接已建立，正在同步设备",
                        type = "relay_socket_open",
                        uploadPolicy = AiToyTraceUploadPolicy.SessionOnce,
                    )
                    emitState("正在同步")
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (generation != connectionGeneration) return
                    val json = runCatching { JsonX.json.parseToJsonElement(text).jsonObject }.getOrNull()
                    val messageType = json.stringOrBlank("type")
                    when (messageType) {
                        "command" -> {
                            trace(
                                "AI 伙伴收到指令：$text",
                                type = "relay_command_received",
                                uploadPolicy = AiToyTraceUploadPolicy.Always,
                            )
                            if (json != null) emitCommand(json.toRelayCommand())
                        }
                        "heartbeat_ack" -> Unit
                        "device_status_ack" -> {
                            if (onlineConfirmed) return
                            onlineConfirmed = true
                            trace(
                                "AI 伙伴已在线",
                                type = "relay_online",
                                uploadPolicy = AiToyTraceUploadPolicy.SessionOnce,
                            )
                            retryCount = 0
                            emitState("已在线")
                        }
                        else -> trace("AI 伙伴收到消息 type=$messageType")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration) return
                    trace("AI 伙伴正在断开")
                    emitState("正在断开")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration) return
                    stopHeartbeat()
                    socket = null
                    connecting = false
                    onlineConfirmed = false
                    if (code == SERVER_COMMAND_IDLE_CLOSE_CODE) {
                        userRequestedOnline = false
                        retryCount = 0
                        reconnectJob?.cancel()
                        trace(
                            "AI 伙伴已空闲，下次使用时再上线",
                            type = "relay_idle_offline",
                            uploadPolicy = AiToyTraceUploadPolicy.Always,
                        )
                        emitState("未连接")
                        return
                    }
                    if (userRequestedOnline) {
                        trace(
                            "AI 伙伴连接已断开，正在准备重连",
                            type = "relay_disconnected",
                            uploadPolicy = AiToyTraceUploadPolicy.Always,
                        )
                        emitState("等待重连")
                        scheduleReconnect()
                    } else {
                        trace(
                            "AI 伙伴已下线",
                            type = "relay_offline",
                            uploadPolicy = AiToyTraceUploadPolicy.Always,
                        )
                        emitState("未连接")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (generation != connectionGeneration) return
                    stopHeartbeat()
                    socket = null
                    connecting = false
                    onlineConfirmed = false
                    trace("AI 伙伴连接中断：${t.message ?: "网络不可用"}", error = true)
                    if (userRequestedOnline) {
                        emitState("等待重连")
                        scheduleReconnect()
                    } else {
                        emitState("连接失败")
                    }
                }
            },
        )
    }

    fun reconnectNow() {
        if (userRequestedOnline && socket == null && !connecting && lastServerUrl.isNotBlank()) {
            connect(lastServerUrl, lastUserToken, resetRetry = false)
        }
    }

    fun syncDevices(devices: List<RelayDevice>) {
        send(
            buildJsonObject {
                put("type", "device_status")
                put("devices", relayDevicesJsonArray(devices))
            }
        )
    }

    fun commandResult(commandId: String, success: Boolean, message: String) {
        send(
            buildJsonObject {
                put("type", "command_result")
                put("commandId", commandId)
                put("success", success)
                put("message", message)
            }
        )
    }

    fun disconnect() {
        userRequestedOnline = false
        connecting = false
        onlineConfirmed = false
        retryCount = 0
        connectionGeneration += 1
        reconnectJob?.cancel()
        stopHeartbeat()
        socket?.close(1000, "App disconnect")
        socket = null
        emitState("未连接")
    }

    fun close() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun send(json: JsonObject) {
        val currentSocket = socket
        val text = json.toString()
        val accepted = currentSocket?.send(text) == true
        trace("AI 伙伴同步状态 accepted=$accepted payload=$text")
        if (!accepted) {
            onlineConfirmed = false
            emitState(if (userRequestedOnline) "等待重连" else "未连接")
            onTrace(
                AiToyTraceEvent(
                    message = "AI 伙伴同步状态 accepted=false",
                    error = true,
                    type = "relay_sync_rejected",
                )
            )
        }
        if (!accepted && userRequestedOnline) {
            currentSocket?.cancel()
            if (socket === currentSocket) socket = null
            scheduleReconnect()
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val currentSocket = socket ?: return@launch
                if (!userRequestedOnline) return@launch
                val accepted = currentSocket.send(
                    buildJsonObject {
                        put("type", "heartbeat")
                        put("clientTime", nowMs())
                    }.toString()
                )
                if (!accepted) {
                    trace("AI 伙伴保活失败，正在准备重连", error = true, type = "relay_heartbeat_failed")
                    onlineConfirmed = false
                    emitState("等待重连")
                    currentSocket.cancel()
                    if (socket === currentSocket) socket = null
                    scheduleReconnect()
                    return@launch
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val delayMs = min(30_000L, 1_000L shl retryCount.coerceAtMost(5))
        retryCount += 1
        trace("AI 伙伴将在 ${delayMs / 1_000} 秒后重连")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (userRequestedOnline && socket == null && lastServerUrl.isNotBlank()) {
                connect(lastServerUrl, lastUserToken, resetRetry = false)
            }
        }
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
    ) {
        if (error) Log.e(TAG) { message } else Log.d(TAG) { message }
        emitLog(message)
        if (error || uploadPolicy != AiToyTraceUploadPolicy.Drop) {
            onTrace(
                AiToyTraceEvent(
                    message = message,
                    error = error,
                    type = type,
                    uploadPolicy = uploadPolicy,
                )
            )
        }
    }

    private fun emitLog(message: String) {
        scope.launch { onLog(message) }
    }

    private fun emitState(state: String) {
        scope.launch { onState(state) }
    }

    private fun emitCommand(command: RelayCommand) {
        scope.launch { onCommand(command) }
    }

    private fun safeWsUrl(wsUrl: String): String =
        wsUrl.replace(Regex("token=[^&]+")) { match ->
            val token = match.value.removePrefix("token=")
            "token=${token.take(6)}..."
        }

    private fun JsonObject.toRelayCommand(): RelayCommand =
        RelayCommand(
            commandId = stringOrBlank("commandId"),
            action = stringOrBlank("action"),
            deviceId = stringOrBlank("deviceId").ifBlank { null },
            intensity = intOrNull("intensity"),
            mode = intOrNull("mode"),
            durationSec = intOrNull("durationSec"),
            defaultDurationSec = intOrNull("defaultDurationSec"),
            script = stringOrBlank("script").ifBlank { null },
            createdAt = longOrNull("createdAt"),
        )

    private fun relayDevicesJsonArray(devices: List<RelayDevice>): JsonArray =
        buildJsonArray {
            devices.forEach { device ->
                add(
                    buildJsonObject {
                        put("deviceId", device.deviceId)
                        put("displayName", device.displayName)
                        if (device.bleAddress.isNotBlank()) put("bleAddress", device.bleAddress.take(80))
                        put("connectable", device.connectable)
                        if (device.serviceUuids.isNotEmpty()) put("serviceUuids", stringJsonArray(device.serviceUuids.take(16)))
                        if (device.manufacturerData.isNotBlank()) put("manufacturerData", device.manufacturerData.take(512))
                        if (device.scanRecordHex.isNotBlank()) put("scanRecordHex", device.scanRecordHex.take(512))
                        if (device.broadcastProtocolName.isNotBlank()) put("broadcastProtocolName", device.broadcastProtocolName.take(160))
                        put("connected", device.connected)
                        put("isDefault", device.isDefault)
                        put("protocolName", device.protocolName)
                        put("intensity", device.intensity)
                        put("mode", device.mode)
                        put("intensityMax", device.intensityMax)
                        put("modeMax", device.modeMax)
                        put("modeLabel", device.modeLabel)
                        put("modeNames", stringJsonArray(device.modeNames))
                        put("intensityLabel", device.intensityLabel)
                        put("channelNames", stringJsonArray(device.channelNames))
                        put("controlStyle", device.controlStyle)
                        put("features", relayDeviceFeaturesJsonArray(device.features))
                        put("adapterType", "template_ble")
                        put("capabilities", stringJsonArray(listOf("sequence")))
                        device.batteryPercent?.coerceIn(1, 100)?.let { put("batteryPercent", it) }
                    }
                )
            }
        }

    private fun relayDeviceFeaturesJsonArray(features: List<RelayDeviceFeature>): JsonArray =
        buildJsonArray {
            features.forEach { feature ->
                add(
                    buildJsonObject {
                        put("type", feature.type)
                        put("min", feature.min)
                        put("max", feature.max)
                        put("index", feature.index)
                        put("label", feature.label)
                        if (feature.modeMax > 0) put("modeMax", feature.modeMax)
                    }
                )
            }
        }

    private fun stringJsonArray(values: List<String>): JsonArray =
        buildJsonArray {
            values.forEach { add(JsonPrimitive(it)) }
        }

    private fun JsonObject?.stringOrBlank(name: String): String =
        this?.get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.jsonPrimitive?.intOrNull

    private fun JsonObject.longOrNull(name: String): Long? =
        get(name)?.jsonPrimitive?.longOrNull

    companion object {
        private const val TAG = "AiToyRelay"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val SERVER_COMMAND_IDLE_CLOSE_CODE = 4001
    }
}
