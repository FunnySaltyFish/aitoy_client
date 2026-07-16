package com.funny.aitoy.runtime

import androidx.compose.runtime.mutableStateMapOf
import com.funny.aitoy.ble.BleConnectionState
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.ProtocolAttemptStatus
import com.funny.aitoy.model.RememberedToy
import com.funny.aitoy.model.ToyRuntimeState

data class DeviceRuntime(
    val connectionState: BleConnectionState = BleConnectionState.Idle,
    val runtimeState: ToyRuntimeState = ToyRuntimeState.Offline,
    val protocolStatus: BleProtocolStatus = BleProtocolStatus(),
    val protocolAttemptStatus: ProtocolAttemptStatus = ProtocolAttemptStatus(),
    val displayName: String = "",
    val mode: Int = 1,
    val intensity: Int = 1,
    val secondaryIntensity: Int = 0,
    val batteryPercent: Int? = null,
)

class DeviceRuntimeStore {
    private val runtimes = mutableStateMapOf<String, DeviceRuntime>()

    val keys: Set<String>
        get() = runtimes.keys

    fun ensure(address: String) {
        if (address.isNotBlank()) runtimes.putIfAbsent(address, DeviceRuntime())
    }

    fun remove(address: String) {
        runtimes.remove(address)
    }

    fun connectionState(address: String): BleConnectionState =
        runtimes[address]?.connectionState ?: BleConnectionState.Idle

    fun runtimeState(address: String): ToyRuntimeState =
        runtimes[address]?.runtimeState ?: ToyRuntimeState.Offline

    fun protocolStatus(address: String): BleProtocolStatus =
        runtimes[address]?.protocolStatus ?: BleProtocolStatus()

    fun protocolAttemptStatus(address: String): ProtocolAttemptStatus =
        runtimes[address]?.protocolAttemptStatus ?: ProtocolAttemptStatus()

    fun displayName(address: String): String =
        runtimes[address]?.displayName.orEmpty()

    fun mode(address: String): Int =
        runtimes[address]?.mode ?: 1

    fun intensity(address: String): Int =
        runtimes[address]?.intensity ?: 0

    fun secondaryIntensity(address: String): Int =
        runtimes[address]?.secondaryIntensity ?: intensity(address)

    fun batteryPercent(address: String): Int? =
        runtimes[address]?.batteryPercent?.takeIf {
            connectionState(address) == BleConnectionState.Ready
        }

    fun setConnectionState(address: String, state: BleConnectionState) {
        update(address) {
            it.copy(
                connectionState = state,
                runtimeState = state.toRuntimeState(),
                batteryPercent = it.batteryPercent.takeUnless {
                    state == BleConnectionState.Error || state == BleConnectionState.Idle
                },
            )
        }
    }

    fun setDisplayName(address: String, displayName: String) {
        update(address) { it.copy(displayName = displayName) }
    }

    fun setProtocolStatus(address: String, status: BleProtocolStatus) {
        update(address) { it.copy(protocolStatus = status) }
    }

    fun setProtocolAttemptStatus(address: String, status: ProtocolAttemptStatus) {
        update(address) { it.copy(protocolAttemptStatus = status) }
    }

    fun setBatteryPercent(address: String, percent: Int?) {
        update(address) { it.copy(batteryPercent = percent?.coerceIn(1, 100)) }
    }

    fun setMode(address: String, mode: Int) {
        update(address) { it.copy(mode = mode) }
    }

    fun setIntensity(address: String, intensity: Int) {
        update(address) { it.copy(intensity = intensity) }
    }

    fun setSecondaryIntensity(address: String, secondaryIntensity: Int) {
        update(address) { it.copy(secondaryIntensity = secondaryIntensity) }
    }

    fun setControlValues(
        address: String,
        mode: Int? = null,
        intensity: Int? = null,
        secondaryIntensity: Int? = null,
    ) {
        update(address) {
            it.copy(
                mode = mode ?: it.mode,
                intensity = intensity ?: it.intensity,
                secondaryIntensity = secondaryIntensity ?: it.secondaryIntensity,
            )
        }
    }

    fun defaultConnectedAddress(preferredAddress: String): String =
        preferredAddress.takeIf { connectionState(it) == BleConnectionState.Ready }
            ?: runtimes.entries.firstOrNull { it.value.connectionState == BleConnectionState.Ready }?.key
            ?: ""

    fun connectedDeviceCount(): Int =
        runtimes.count { it.value.runtimeState == ToyRuntimeState.Connected }

    fun connectedSavedDeviceCount(saved: List<RememberedToy>): Int =
        saved.count { runtimeState(it.address) == ToyRuntimeState.Connected }

    private fun update(address: String, transform: (DeviceRuntime) -> DeviceRuntime) {
        if (address.isBlank()) return
        runtimes[address] = transform(runtimes[address] ?: DeviceRuntime())
    }
}

fun BleConnectionState.toRuntimeState(): ToyRuntimeState =
    when (this) {
        BleConnectionState.Connecting,
        BleConnectionState.Discovering,
        BleConnectionState.Disconnecting -> ToyRuntimeState.Connecting
        BleConnectionState.Ready -> ToyRuntimeState.Connected
        BleConnectionState.Error -> ToyRuntimeState.Failed
        BleConnectionState.Idle -> ToyRuntimeState.Offline
    }
