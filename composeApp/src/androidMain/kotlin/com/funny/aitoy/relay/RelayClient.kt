package com.funny.aitoy.relay

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.funny.aitoy.diagnostics.AiToyTraceEvent
import com.funny.aitoy.diagnostics.AiToyTraceUploadPolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
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
    val connected: Boolean,
    val isDefault: Boolean,
    val protocolName: String,
    val intensity: Int,
    val mode: Int,
    val intensityMax: Int,
    val modeMax: Int,
    val modeNames: List<String> = emptyList(),
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
)

class RelayClient(
    private val onState: (String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onCommand: (RelayCommand) -> Unit,
    private val onTrace: (AiToyTraceEvent) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var lastServerUrl = ""
    private var lastUserToken = ""
    private var userRequestedOnline = false
    private var connecting = false
    private var retryCount = 0
    private var connectionGeneration = 0
    private val reconnect = Runnable {
        if (userRequestedOnline && socket == null && lastServerUrl.isNotBlank()) {
            connect(lastServerUrl, lastUserToken, resetRetry = false)
        }
    }

    fun connect(serverUrl: String, userToken: String) {
        connect(serverUrl, userToken, resetRetry = true)
    }

    private fun connect(serverUrl: String, userToken: String, resetRetry: Boolean) {
        userRequestedOnline = true
        lastServerUrl = serverUrl
        lastUserToken = userToken
        mainHandler.removeCallbacks(reconnect)
        socket?.close(1000, "Reconnect")
        socket = null
        if (resetRetry) retryCount = 0
        connecting = true
        val generation = ++connectionGeneration
        val wsUrl = serverUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/app?token=$userToken"
        trace("连接 AI 伙伴：${safeWsUrl(wsUrl)}")
        emitState("正在连接")
        val nextSocket = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (generation != connectionGeneration) return
                    connecting = false
                    retryCount = 0
                    trace(
                        "AI 伙伴已在线",
                        type = "relay_online",
                        uploadPolicy = AiToyTraceUploadPolicy.SessionOnce,
                    )
                    emitState("已在线")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (generation != connectionGeneration) return
                    trace(
                        "AI 伙伴收到指令：$text",
                        type = "relay_command_received",
                        uploadPolicy = AiToyTraceUploadPolicy.Always,
                    )
                    val json = JSONObject(text)
                    if (json.optString("type") == "command") {
                        emitCommand(
                            RelayCommand(
                                commandId = json.getString("commandId"),
                                action = json.getString("action"),
                                deviceId = json.optString("deviceId").ifBlank { null },
                                intensity = json.optIntOrNull("intensity"),
                                mode = json.optIntOrNull("mode"),
                                durationSec = json.optIntOrNull("durationSec"),
                                defaultDurationSec = json.optIntOrNull("defaultDurationSec"),
                                script = json.optString("script").ifBlank { null },
                                createdAt = json.optLongOrNull("createdAt"),
                            )
                        )
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration) return
                    trace("AI 伙伴正在断开")
                    emitState("正在断开")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (generation != connectionGeneration) return
                    socket = null
                    connecting = false
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
                    socket = null
                    connecting = false
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
        socket = nextSocket
    }

    fun reconnectNow() {
        if (userRequestedOnline && socket == null && !connecting && lastServerUrl.isNotBlank()) {
            connect(lastServerUrl, lastUserToken, resetRetry = false)
        }
    }

    fun syncDevices(devices: List<RelayDevice>) {
        val payload = JSONObject()
            .put("type", "device_status")
            .put(
                "devices",
                JSONArray().apply {
                    devices.forEach { device ->
                        val item = JSONObject()
                            .put("deviceId", device.deviceId)
                            .put("displayName", device.displayName)
                            .put("connected", device.connected)
                            .put("isDefault", device.isDefault)
                            .put("protocolName", device.protocolName)
                            .put("intensity", device.intensity)
                            .put("mode", device.mode)
                            .put("intensityMax", device.intensityMax)
                            .put("modeMax", device.modeMax)
                            .put("modeNames", JSONArray(device.modeNames))
                            .put("controlStyle", device.controlStyle)
                            .put(
                                "features",
                                JSONArray().apply {
                                    device.features.forEach { feature ->
                                        put(
                                            JSONObject()
                                                .put("type", feature.type)
                                                .put("min", feature.min)
                                                .put("max", feature.max)
                                                .put("index", feature.index)
                                                .put("label", feature.label),
                                        )
                                    }
                                },
                            )
                            .put("adapterType", "template_ble")
                            .put("capabilities", JSONArray(listOf("sequence")))
                        device.batteryPercent
                            ?.coerceIn(1, 100)
                            ?.let { item.put("batteryPercent", it) }
                        put(item)
                    }
                },
            )
        send(payload)
    }

    fun commandResult(commandId: String, success: Boolean, message: String) {
        send(
            JSONObject()
                .put("type", "command_result")
                .put("commandId", commandId)
                .put("success", success)
                .put("message", message),
        )
    }

    fun disconnect() {
        userRequestedOnline = false
        connecting = false
        retryCount = 0
        connectionGeneration += 1
        mainHandler.removeCallbacks(reconnect)
        socket?.close(1000, "App disconnect")
        socket = null
        emitState("未连接")
    }

    fun close() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun send(json: JSONObject) {
        val text = json.toString()
        val accepted = socket?.send(text) == true
        trace("AI 伙伴同步状态 accepted=$accepted payload=$text")
        if (!accepted) {
            onTrace(
                AiToyTraceEvent(
                    message = "AI 伙伴同步状态 accepted=false",
                    error = true,
                    type = "relay_sync_rejected",
                )
            )
        }
        if (!accepted && userRequestedOnline) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        mainHandler.removeCallbacks(reconnect)
        val delayMs = min(30_000L, 1_000L shl retryCount.coerceAtMost(5))
        retryCount += 1
        trace("AI 伙伴将在 ${delayMs / 1_000} 秒后重连")
        mainHandler.postDelayed(reconnect, delayMs)
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
        if (error) Log.e(TAG, message) else Log.d(TAG, message)
        mainHandler.post { onLog(message) }
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

    private fun emitState(state: String) {
        mainHandler.post { onState(state) }
    }

    private fun emitCommand(command: RelayCommand) {
        mainHandler.post { onCommand(command) }
    }

    private fun safeWsUrl(wsUrl: String): String =
        wsUrl.replace(Regex("token=[^&]+")) { match ->
            val token = match.value.removePrefix("token=")
            "token=${token.take(6)}..."
        }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    companion object {
        private const val TAG = "AiToyRelay"
    }
}
