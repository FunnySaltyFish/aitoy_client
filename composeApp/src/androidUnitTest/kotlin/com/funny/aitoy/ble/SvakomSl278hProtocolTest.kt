package com.funny.aitoy.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SvakomSl278hProtocolTest {
    @Test
    fun sl278hVibrationStickUsesSeparateStretchAndVibrateCommands() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 100))
            ?: error("SL278H vibration stick protocol not resolved")

        assertEquals("svakom_sl278_plus_v", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("伸缩", "震动"), protocol.status.channelNames)

        val init = protocol.initialize(svakomFingerprint(productCode = 100))
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init.single()).characteristicUuid)

        val operations = protocol.commandsFor(ToyControlAction.DualMotor(mode = 3, internalIntensity = 6, externalIntensity = 4))
        // 伸缩没有强度滑杆：官方 level 恒为 0，激活由模式驱动，帧形 55 08 00 00 <mode> 00 00。
        assertEquals("55080000030000", assertIs<BleProtocolOperation.Write>(operations[0]).bytes.hexUpper())
        assertEquals("55030000010400", assertIs<BleProtocolOperation.Write>(operations[1]).bytes.hexUpper())
        assertEquals("55050000000000", assertIs<BleProtocolOperation.Write>(operations[2]).bytes.hexUpper())
        operations.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(WRITE_UUID, write.characteristicUuid)
            assertFalse(write.withResponse)
        }
    }

    @Test
    fun sl278hFlapSideUsesOfficialFlapCommandShape() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 101))
            ?: error("SL278H flap side protocol not resolved")

        assertEquals("svakom_sl278_plus_f", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("拍打"), protocol.status.channelNames)

        val operations = protocol.commandsFor(ToyControlAction.Combined(mode = 402, intensity = 7))
        val write = assertIs<BleProtocolOperation.Write>(operations[0])

        assertEquals(WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        // 拍打同样没有强度滑杆：官方 level 恒为 0，激活由模式驱动。
        assertEquals("55070000020000", write.bytes.hexUpper())
    }

    @Test
    fun sl278hMatcherStaysAheadOfSt419AndRequiresSvakomManufacturerData() {
        val protocols = BleProtocolRegistry.resolveNativeAll(svakomFingerprint(productCode = 100))

        assertEquals("svakom_sl278_plus_v", protocols.first().status.id)
        assertEquals(null, BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 100, manufacturerData = "")))
    }

    @Test
    fun sl278kLiveAdvertisementMatchesVibrationStickProfile() {
        val protocol = BleProtocolRegistry.resolveNative(
            svakomFingerprint(
                name = "SL278K",
                manufacturerData = "0x32:53 56 41 02 FF 32 26 01 E8 B0 C9 FF 32 00 00 80 19 04 0A 01 00",
            ),
        ) ?: error("SL278K live advertisement did not resolve")

        assertEquals("svakom_sl278_plus_v", protocol.status.id)
    }

    @Test
    fun sl278kSuctionProductCodeMatchesSuctionProfile() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 0x81, name = "SL278K"))
            ?: error("SL278K suction product code did not resolve")

        assertEquals("svakom_sl278_plus_s", protocol.status.id)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
        val operations = protocol.commandsFor(ToyControlAction.Combined(mode = 303, intensity = 8))
        assertEquals("55090000030800", assertIs<BleProtocolOperation.Write>(operations[0]).bytes.hexUpper())
    }

    @Test
    fun sl278kVibrationStickSupportsTenVibrateModesAndHeat() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 0x80, name = "SL278K"))
            ?: error("SL278K vibration stick protocol not resolved")

        assertEquals(10, protocol.status.modeMax)
        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 210, intensity = 5))
        assertEquals("55080000000000", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
        assertEquals("550300000A0500", assertIs<BleProtocolOperation.Write>(vibrate[1]).bytes.hexUpper())
        assertEquals("55050000000000", assertIs<BleProtocolOperation.Write>(vibrate[2]).bytes.hexUpper())

        val heat = protocol.commandsFor(ToyControlAction.Combined(mode = 501, intensity = 1))
        assertEquals("55050137010000", assertIs<BleProtocolOperation.Write>(heat[2]).bytes.hexUpper())
    }

    @Test
    fun featureCodesExposeIndependentAddressingForScalarControl() {
        // scalar(feature=N,...) 在 ViewModel 里按功能类型解析成 functionCode*100+mode，
        // 这里验证类型到功能码/模式上限的映射稳定，且各功能区互相独立。
        assertEquals(1, svakomV2FunctionCode("oscillate"))
        assertEquals(2, svakomV2FunctionCode("vibrate"))
        assertEquals(3, svakomV2FunctionCode("constrict"))
        assertEquals(4, svakomV2FunctionCode("flap"))
        assertEquals(SvakomV2HeatFunctionCode, svakomV2FunctionCode("heat"))
        assertEquals(10, svakomV2FunctionModeMax("vibrate"))
        assertEquals(5, svakomV2FunctionModeMax("constrict"))

        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 0x80, name = "SL278K"))
            ?: error("SL278K vibration stick protocol not resolved")

        // 只驱动震动通道（功能码 2），伸缩通道应保持停止帧。
        val vibrateOnly = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 4, intensity = 6))
        assertEquals("55080000000000", assertIs<BleProtocolOperation.Write>(vibrateOnly[0]).bytes.hexUpper())
        assertEquals("55030000040600", assertIs<BleProtocolOperation.Write>(vibrateOnly[1]).bytes.hexUpper())
    }

    @Test
    fun stretchActivatesByModeAloneWithZeroLevel() {
        // 伸缩没有强度滑杆：官方 level 恒为 0，激活由“模式>0”决定，模式=0 即停。
        // 回归：0.6.9 曾误发 0xFF、更早误发 0..10 强度，实机都不动；真值是 level=0。
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 0x80, name = "SL278K"))
            ?: error("SL278K vibration stick protocol not resolved")

        // 只驱动伸缩（功能码 1，模式 5），强度参数应被忽略、level 恒为 0。
        val stretch = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 5, intensity = 0))
        assertEquals("55080000050000", assertIs<BleProtocolOperation.Write>(stretch[0]).bytes.hexUpper())

        // 模式=0 停止伸缩。
        val stop = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 0, intensity = 0))
        assertEquals("55080000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
    }

    private fun svakomFingerprint(
        productCode: Int = 100,
        name: String = "SL278H",
        manufacturerData: String = "0xffff:53 56 41 01 ${"%02X".format(productCode)} 00 00 00 00 00 00 00",
    ) = BleGattFingerprint(
        name = name,
        address = "AA:BB:CC:DD:EE:FF",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    }
}
