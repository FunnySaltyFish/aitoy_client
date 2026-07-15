package com.funny.aitoy.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.funny.aitoy.core.platform.AndroidPlatformInit

@SuppressLint("MissingPermission")
internal class AndroidBleAdvertiser(
    private val onLog: (String) -> Unit,
) {
    private val context = AndroidPlatformInit.appContext
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val advertiser get() = adapter?.bluetoothLeAdvertiser
    private val handler = Handler(Looper.getMainLooper())
    private val channels = mutableMapOf<String, AdvertiserChannelState>()

    fun start(operation: BleAdvertiseOperation) {
        val channel = channelFor(operation.channelKey)
        channel.pendingOperations.addLast(operation)
        if (!channel.draining) {
            drainNext(channel)
        }
    }

    private fun channelFor(channelKey: String): AdvertiserChannelState =
        channels.getOrPut(channelKey.ifBlank { DEFAULT_CHANNEL_KEY }) {
            AdvertiserChannelState(channelKey.ifBlank { DEFAULT_CHANNEL_KEY })
        }

    private fun drainNext(channel: AdvertiserChannelState) {
        val operation = channel.pendingOperations.removeFirstOrNull()
        if (operation == null) {
            channel.draining = false
            return
        }
        channel.draining = true
        advertise(channel, operation)
    }

    private fun advertise(channel: AdvertiserChannelState, operation: BleAdvertiseOperation) {
        val currentAdvertiser = advertiser
        if (currentAdvertiser == null) {
            onLog("当前手机不支持 BLE 广播发送")
            scheduleNext(channel)
            return
        }
        val uuid = operation.serviceUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    scheduleNext(channel)
                    return
                }
        }
        val serviceDataUuid = operation.serviceDataUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    scheduleNext(channel)
                    return
                }
        }
        val dataBuilder = AdvertiseData.Builder().setIncludeDeviceName(false)
        uuid?.let(dataBuilder::addServiceUuid)
        serviceDataUuid?.let { dataBuilder.addServiceData(it, operation.serviceData) }
        operation.manufacturerId?.let { id ->
            dataBuilder.addManufacturerData(id, operation.manufacturerData)
        }
        val data = dataBuilder.build()
        val summary = uuid?.uuid?.toString()
            ?: serviceDataUuid?.let { "serviceData=${it.uuid} bytes=${operation.serviceData.toHexString()}" }
            ?: operation.manufacturerId?.let { "manufacturer=0x${it.toString(16)}" }
            ?: "<empty>"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentSet = channel.advertisingSet
            if (currentSet != null) {
                runCatching {
                    currentSet.setAdvertisingData(data)
                }.onSuccess {
                    onLog("广播已更新 $summary")
                    scheduleNext(channel)
                }.onFailure {
                    onLog("广播更新失败：${it.message.orEmpty()}")
                    stopCurrentAdvertising(currentAdvertiser, channel)
                    startAdvertisingSet(currentAdvertiser, channel, operation, data, summary)
                }
            } else {
                startAdvertisingSet(currentAdvertiser, channel, operation, data, summary)
            }
        } else {
            stopCompatAdvertising(currentAdvertiser, channel)
            startCompatAdvertising(currentAdvertiser, channel, operation, data, summary)
        }
    }

    private fun startAdvertisingSet(
        currentAdvertiser: BluetoothLeAdvertiser,
        channel: AdvertiserChannelState,
        operation: BleAdvertiseOperation,
        data: AdvertiseData,
        summary: String,
    ) {
        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int,
            ) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    channel.advertisingSet = advertisingSet
                    onLog("广播已发送 $summary")
                } else {
                    channel.advertisingSet = null
                    onLog("广播发送失败 status=$status")
                    retryOnDefaultChannel(channel, operation)
                }
                scheduleNext(channel)
            }
        }
        channel.advertisingSetCallback = callback
        currentAdvertiser.startAdvertisingSet(
            AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .setConnectable(false)
                .setScannable(false)
                .build(),
            data,
            null,
            null,
            null,
            callback,
        )
    }

    private fun startCompatAdvertising(
        currentAdvertiser: BluetoothLeAdvertiser,
        channel: AdvertiserChannelState,
        operation: BleAdvertiseOperation,
        data: AdvertiseData,
        summary: String,
    ) {
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onLog("广播已发送 $summary")
                scheduleNext(channel)
            }

            override fun onStartFailure(errorCode: Int) {
                onLog("广播发送失败 errorCode=$errorCode")
                retryOnDefaultChannel(channel, operation)
                scheduleNext(channel)
            }
        }
        channel.advertiseCallback = callback
        currentAdvertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build(),
            data,
            callback,
        )
    }

    private fun retryOnDefaultChannel(
        channel: AdvertiserChannelState,
        operation: BleAdvertiseOperation,
    ) {
        if (channel.key == DEFAULT_CHANNEL_KEY) return
        start(operation.copy(channelKey = DEFAULT_CHANNEL_KEY))
    }

    private fun scheduleNext(channel: AdvertiserChannelState) {
        if (channel.pendingOperations.isEmpty()) {
            channel.draining = false
            return
        }
        handler.postDelayed({ drainNext(channel) }, COMMAND_HOLD_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val currentAdvertiser = advertiser
        channels.values.forEach { channel ->
            channel.pendingOperations.clear()
            channel.draining = false
            if (currentAdvertiser != null) {
                stopCurrentAdvertising(currentAdvertiser, channel)
            }
        }
        channels.clear()
    }

    private fun stopCurrentAdvertising(
        currentAdvertiser: BluetoothLeAdvertiser,
        channel: AdvertiserChannelState,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel.advertisingSetCallback?.let { callback ->
                runCatching { currentAdvertiser.stopAdvertisingSet(callback) }
            }
            channel.advertisingSet = null
            channel.advertisingSetCallback = null
        }
        stopCompatAdvertising(currentAdvertiser, channel)
    }

    private fun stopCompatAdvertising(
        currentAdvertiser: BluetoothLeAdvertiser,
        channel: AdvertiserChannelState,
    ) {
        channel.advertiseCallback?.let { callback ->
            runCatching { currentAdvertiser.stopAdvertising(callback) }
        }
        channel.advertiseCallback = null
    }

    private data class AdvertiserChannelState(
        val key: String,
        val pendingOperations: ArrayDeque<BleAdvertiseOperation> = ArrayDeque(),
        var advertiseCallback: AdvertiseCallback? = null,
        var advertisingSetCallback: AdvertisingSetCallback? = null,
        var advertisingSet: AdvertisingSet? = null,
        var draining: Boolean = false,
    )

    companion object {
        private const val DEFAULT_CHANNEL_KEY = "default"
        private const val COMMAND_HOLD_MS = 160L
    }
}
