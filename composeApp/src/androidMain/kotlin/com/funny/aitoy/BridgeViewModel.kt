package com.funny.aitoy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.os.Handler
import android.os.Looper
import com.funny.aitoy.ble.AndroidBleController
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolTemplate
import com.funny.aitoy.ble.ScannedBleDevice
import com.funny.aitoy.core.prefs.DataSaverUtils
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.funny.aitoy.relay.RelayClient
import com.funny.aitoy.relay.RelayCommand
import com.funny.aitoy.relay.RelayDevice
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt

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

    var serverUrl: String by mutableDataSaverStateOf(
        DataSaverUtils,
        "AITOY_SERVER_URL",
        "http://192.168.1.118:5002",
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
    var mode by mutableStateOf(1)
    var intensity by mutableStateOf(1)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val controller = AndroidBleController(
        onDevice = ::onDeviceFound,
        onState = {
            connectionState = it
            if (it == BleConnectionState.Ready) syncRelayDevice()
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
        controller.connect(device, currentTemplate())
    }

    fun disconnect() {
        controller.disconnect()
    }

    fun sendTest() = runAction {
        controller.sendCommand(mode, intensity)
    }

    fun stopDevice() = runAction {
        controller.stopDevice()
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
        logs += message
        while (logs.size > 200) logs.removeAt(0)
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
                )
            )
        )
    }

    private fun handleRelayCommand(command: RelayCommand) {
        runCatching {
            when (command.action) {
                "set" -> {
                    mainHandler.removeCallbacks(autoStop)
                    val mappedIntensity = (
                        1 + ((command.intensity ?: 0).coerceIn(0, 100) / 100.0) * 4
                        ).roundToInt().coerceIn(1, 5)
                    controller.sendCommand(
                        mode = command.mode?.coerceAtLeast(1) ?: 1,
                        intensity = mappedIntensity.coerceAtMost(protocolStatus.intensityMax),
                    )
                    mainHandler.postDelayed(
                        autoStop,
                        (command.durationSec ?: 8).coerceIn(1, 15) * 1_000L,
                    )
                }
                "stop", "stop_all" -> {
                    mainHandler.removeCallbacks(autoStop)
                    controller.stopDevice()
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

    private inline fun runAction(block: () -> Unit) {
        errorMessage = runCatching(block).exceptionOrNull()?.message
        errorMessage?.let { appendLog("操作失败：$it") }
    }

    override fun onCleared() {
        controller.close()
        mainHandler.removeCallbacks(autoStop)
        relay.close()
    }
}
