package com.funny.aitoy.relay

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RelayCommand(
    val commandId: String,
    val action: String,
    val deviceId: String?,
    val intensity: Int?,
    val mode: Int?,
    val durationSec: Int?,
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
)

class RelayClient(
    private val onState: (String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onCommand: (RelayCommand) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    fun connect(serverUrl: String, userToken: String) {
        disconnect()
        val wsUrl = serverUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/app?token=$userToken"
        trace("连接 Relay：$wsUrl")
        onState("正在连接")
        socket = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trace("Relay WebSocket 已连接 code=${response.code}")
                    onState("已在线")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    trace("Relay 收到：$text")
                    val json = JSONObject(text)
                    if (json.optString("type") == "command") {
                        onCommand(
                            RelayCommand(
                                commandId = json.getString("commandId"),
                                action = json.getString("action"),
                                deviceId = json.optString("deviceId").ifBlank { null },
                                intensity = json.optIntOrNull("intensity"),
                                mode = json.optIntOrNull("mode"),
                                durationSec = json.optIntOrNull("durationSec"),
                            )
                        )
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    trace("Relay 正在关闭 code=$code reason=$reason")
                    onState("正在断开")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    trace("Relay 已断开 code=$code reason=$reason")
                    onState("未连接")
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    trace("Relay 连接失败：${throwable.message}", error = true)
                    onState("连接失败")
                }
            },
        )
    }

    fun syncDevices(devices: List<RelayDevice>) {
        val payload = JSONObject()
            .put("type", "device_status")
            .put(
                "devices",
                JSONArray().apply {
                    devices.forEach { device ->
                        put(
                            JSONObject()
                                .put("deviceId", device.deviceId)
                                .put("displayName", device.displayName)
                                .put("connected", device.connected)
                                .put("isDefault", device.isDefault)
                                .put("protocolName", device.protocolName)
                                .put("intensity", device.intensity)
                                .put("mode", device.mode)
                                .put("intensityMax", device.intensityMax)
                                .put("adapterType", "template_ble")
                                .put("capabilities", JSONArray(listOf("vibrate", "stop"))),
                        )
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
        socket?.close(1000, "App disconnect")
        socket = null
    }

    fun close() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun send(json: JSONObject) {
        val text = json.toString()
        val accepted = socket?.send(text) == true
        trace("Relay 发送 accepted=$accepted payload=$text")
    }

    private fun trace(message: String, error: Boolean = false) {
        if (error) Log.e(TAG, message) else Log.d(TAG, message)
        onLog(message)
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    companion object {
        private const val TAG = "AiToyRelay"
    }
}
