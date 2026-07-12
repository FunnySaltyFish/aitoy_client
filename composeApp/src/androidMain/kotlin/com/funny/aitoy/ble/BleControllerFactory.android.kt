package com.funny.aitoy.ble

import com.funny.aitoy.diagnostics.AiToyTraceEvent

actual object BleControllerFactory {
    actual fun create(
        onDevice: (ScannedBleDevice) -> Unit,
        onState: (BleConnectionState) -> Unit,
        onProtocol: (BleProtocolStatus) -> Unit,
        onProtocolAttempt: (ProtocolAttemptStatus) -> Unit,
        onBatteryPercent: (Int?) -> Unit,
        onLog: (String) -> Unit,
        onTrace: (AiToyTraceEvent) -> Unit,
    ): BleController = AndroidBleController(
        onDevice = onDevice,
        onState = onState,
        onProtocol = onProtocol,
        onProtocolAttempt = onProtocolAttempt,
        onBatteryPercent = onBatteryPercent,
        onLog = onLog,
        onTrace = onTrace,
    )
}
