package com.funny.aitoy.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
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
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertisingSetCallback: AdvertisingSetCallback? = null
    private var advertisingSet: AdvertisingSet? = null

    fun start(operation: BleAdvertiseOperation) {
        val uuid = operation.serviceUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    return
                }
        }
        val serviceDataUuid = operation.serviceDataUuid.takeIf { it.isNotBlank() }?.let { serviceUuid ->
            runCatching { ParcelUuid.fromString(serviceUuid) }
                .getOrElse {
                    onLog("广播指令格式不正确：$serviceUuid")
                    return
                }
        }
        val currentAdvertiser = advertiser
        if (currentAdvertiser == null) {
            onLog("当前手机不支持 BLE 广播发送")
            return
        }
        stop()
        handler.postDelayed({
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
                    }
                }
                advertisingSetCallback = callback
                currentAdvertiser.startAdvertisingSet(
                    AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                        .setConnectable(false)
                        .setScannable(false)
                        .build(),
                    data,
                    null,
                    null,
                    null,
                    callback,
                )
            } else {
                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        onLog("广播已发送 $summary")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        onLog("广播发送失败 errorCode=$errorCode")
                    }
                }
                advertiseCallback = callback
                currentAdvertiser.startAdvertising(
                    AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                        .setConnectable(false)
                        .setTimeout(0)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                        .build(),
                    data,
                    callback,
                )
            }
        }, RESTART_DELAY_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val currentAdvertiser = advertiser ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            advertisingSetCallback?.let { callback ->
                runCatching { currentAdvertiser.stopAdvertisingSet(callback) }
            }
            advertisingSet = null
            advertisingSetCallback = null
        }
        advertiseCallback?.let { callback ->
            runCatching { currentAdvertiser.stopAdvertising(callback) }
        }
        advertiseCallback = null
    }

    companion object {
        private const val RESTART_DELAY_MS = 100L
    }
}
