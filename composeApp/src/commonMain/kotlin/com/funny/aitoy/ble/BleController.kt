package com.funny.aitoy.ble

import com.funny.aitoy.diagnostics.AiToyTraceEvent

interface BleController {
    fun startScan()
    fun stopScan()
    fun connect(device: ScannedBleDevice, protocolTemplate: ProtocolTemplate)
    fun disconnect()
    fun close()
    fun sendAction(action: ToyControlAction)
    fun stopDevice()
    fun controlWarmupMs(): Long
    fun reportCurrentProtocolNoResponse(): ProtocolFallbackResult?
}

expect object BleControllerFactory {
    fun create(
        onDevice: (ScannedBleDevice) -> Unit,
        onState: (BleConnectionState) -> Unit,
        onProtocol: (BleProtocolStatus) -> Unit,
        onProtocolAttempt: (ProtocolAttemptStatus) -> Unit,
        onBatteryPercent: (Int?) -> Unit,
        onLog: (String) -> Unit,
        onTrace: (AiToyTraceEvent) -> Unit,
    ): BleController
}
