package com.funny.aitoy.buttplug

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ExternalButtplugDevice(
    val index: Int,
    val name: String,
)

class AndroidExternalButtplugClient(
    private val url: String,
    private val onDevices: (List<ExternalButtplugDevice>) -> Unit = {},
    private val onState: (String) -> Unit = {},
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build(),
) {
    private var socket: WebSocket? = null
    private var nextId = 1
    private val devices = linkedMapOf<Int, ExternalButtplugDevice>()

    fun connect(startScanning: Boolean = true) {
        disconnect()
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onState("connected")
                    send(ExternalButtplugTransportPlans.requestServerInfo(nextMessageId()))
                    send(ExternalButtplugTransportPlans.requestDeviceList(nextMessageId()))
                    if (startScanning) send(ExternalButtplugTransportPlans.startScanning(nextMessageId()))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onState("closed")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onState("failed:${t.message.orEmpty()}")
                }
            },
        )
    }

    fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        devices.clear()
    }

    fun startScanning() {
        send(ExternalButtplugTransportPlans.startScanning(nextMessageId()))
    }

    fun stopScanning() {
        send(ExternalButtplugTransportPlans.stopScanning(nextMessageId()))
    }

    fun stopDevice(deviceIndex: Int) {
        send(ExternalButtplugTransportPlans.stopDevice(nextMessageId(), deviceIndex))
    }

    fun sendScalar(deviceIndex: Int, scalars: List<ExternalButtplugScalar>) {
        if (scalars.isNotEmpty()) {
            send(ExternalButtplugTransportPlans.scalarCmd(nextMessageId(), deviceIndex, scalars))
        }
    }

    fun sendLinear(deviceIndex: Int, vectors: List<ExternalButtplugLinear>) {
        if (vectors.isNotEmpty()) {
            send(ExternalButtplugTransportPlans.linearCmd(nextMessageId(), deviceIndex, vectors))
        }
    }

    private fun send(message: String) {
        socket?.send(message)
    }

    private fun nextMessageId(): Int = nextId++

    private fun handleMessage(text: String) {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return
        for (index in 0 until array.length()) {
            val wrapper = array.optJSONObject(index) ?: continue
            when {
                wrapper.has("DeviceList") -> handleDeviceList(wrapper.getJSONObject("DeviceList"))
                wrapper.has("DeviceAdded") -> handleDeviceAdded(wrapper.getJSONObject("DeviceAdded"))
                wrapper.has("DeviceRemoved") -> handleDeviceRemoved(wrapper.getJSONObject("DeviceRemoved"))
            }
        }
    }

    private fun handleDeviceList(message: JSONObject) {
        devices.clear()
        val list = message.optJSONArray("Devices") ?: JSONArray()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val deviceIndex = item.optInt("DeviceIndex", -1)
            if (deviceIndex >= 0) {
                devices[deviceIndex] = ExternalButtplugDevice(
                    index = deviceIndex,
                    name = item.optString("DeviceName", "Buttplug Device"),
                )
            }
        }
        onDevices(devices.values.toList())
    }

    private fun handleDeviceAdded(message: JSONObject) {
        val deviceIndex = message.optInt("DeviceIndex", -1)
        if (deviceIndex < 0) return
        devices[deviceIndex] = ExternalButtplugDevice(
            index = deviceIndex,
            name = message.optString("DeviceName", "Buttplug Device"),
        )
        onDevices(devices.values.toList())
    }

    private fun handleDeviceRemoved(message: JSONObject) {
        val deviceIndex = message.optInt("DeviceIndex", -1)
        if (deviceIndex < 0) return
        devices.remove(deviceIndex)
        onDevices(devices.values.toList())
    }
}
