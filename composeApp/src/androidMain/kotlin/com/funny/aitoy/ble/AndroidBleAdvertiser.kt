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
    private val pendingOperations = ArrayDeque<BleAdvertiseOperation>()
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertisingSetCallback: AdvertisingSetCallback? = null
    private var advertisingSet: AdvertisingSet? = null
    private var draining = false

    fun start(operation: BleAdvertiseOperation) {
        pendingOperations.addLast(operation)
        if (!draining) {
            drainNext()
        }
    }

    private fun drainNext() {
        val operation = pendingOperations.removeFirstOrNull()
        if (operation == null) {
            draining = false
            return
        }
        draining = true
        advertise(operation)
    }

    private fun advertise(operation: BleAdvertiseOperation) {
        val currentAdvertiser = advertiser
        if (currentAdvertiser == null) {
            onLog("当前手机不支持 BLE 广播发送")
            scheduleNext()
            return
        }
        val uuid = operation.serviceUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    scheduleNext()
                    return
                }
        }
        val serviceDataUuid = operation.serviceDataUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    scheduleNext()
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
            val currentSet = advertisingSet
            if (currentSet != null) {
                runCatching {
                    currentSet.setAdvertisingData(data)
                }.onSuccess {
                    onLog("广播已更新 $summary")
                    scheduleNext()
                }.onFailure {
                    onLog("广播更新失败：${it.message.orEmpty()}")
                    stopCurrentAdvertising()
                    startAdvertisingSet(currentAdvertiser, data, summary)
                }
            } else {
                startAdvertisingSet(currentAdvertiser, data, summary)
            }
        } else {
            stopCompatAdvertising(currentAdvertiser)
            startCompatAdvertising(currentAdvertiser, data, summary)
        }
    }

    private fun startAdvertisingSet(
        currentAdvertiser: BluetoothLeAdvertiser,
        data: AdvertiseData,
        summary: String,
    ) {
        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int,
            ) {
                this@AndroidBleAdvertiser.advertisingSet = advertisingSet
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    onLog("广播已发送 $summary")
                } else {
                    onLog("广播发送失败 status=$status")
                }
                scheduleNext()
            }
        }
        advertisingSetCallback = callback
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
        data: AdvertiseData,
        summary: String,
    ) {
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                onLog("广播已发送 $summary")
                scheduleNext()
            }

            override fun onStartFailure(errorCode: Int) {
                onLog("广播发送失败 errorCode=$errorCode")
                scheduleNext()
            }
        }
        advertiseCallback = callback
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

    private fun scheduleNext() {
        if (pendingOperations.isEmpty()) {
            draining = false
            return
        }
        handler.postDelayed(::drainNext, COMMAND_HOLD_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        pendingOperations.clear()
        draining = false
        stopCurrentAdvertising()
    }

    private fun stopCurrentAdvertising() {
        val currentAdvertiser = advertiser ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            advertisingSetCallback?.let { callback ->
                runCatching { currentAdvertiser.stopAdvertisingSet(callback) }
            }
            advertisingSet = null
            advertisingSetCallback = null
        }
        stopCompatAdvertising(currentAdvertiser)
    }

    private fun stopCompatAdvertising(currentAdvertiser: BluetoothLeAdvertiser) {
        advertiseCallback?.let { callback ->
            runCatching { currentAdvertiser.stopAdvertising(callback) }
        }
        advertiseCallback = null
    }

    companion object {
        private const val COMMAND_HOLD_MS = 160L
    }
}
