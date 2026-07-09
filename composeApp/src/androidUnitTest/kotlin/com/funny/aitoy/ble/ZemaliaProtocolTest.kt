package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ZemaliaProtocolTest {
    @Test
    fun zx650aResolvesToSuckFlapVibrateProfile() {
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(87))
            ?: error("ZEMALIA ZX650A(87) protocol not resolved")

        assertEquals("zemalia_zx650a", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "拍打", "震动"), protocol.status.channelNames)
    }

    @Test
    fun zx650aWritesOnlyTheTouchedFunctionFrame() {
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(87))
            ?: error("ZEMALIA ZX650A(87) protocol not resolved")

        // 吮吸功能码 3，档位 5，强度 8：只发吮吸单帧 55 09 00 00 05 08 00。
        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 5, intensity = 8))
        assertEquals(1, suck.size)
        assertEquals("55090000050800", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())

        // 拍打功能码 4，档位 3，强度 6：55 07 00 00 03 06 00。
        val flap = protocol.commandsFor(ToyControlAction.Combined(mode = 4 * 100 + 3, intensity = 6))
        assertEquals(1, flap.size)
        assertEquals("55070000030600", assertIs<BleProtocolOperation.Write>(flap[0]).bytes.hexUpper())

        // 震动功能码 2，档位 7，强度 10：55 03 00 00 07 0A 00。
        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 7, intensity = 10))
        assertEquals(1, vibrate.size)
        assertEquals("55030000070A00", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun licklingFunctionUsesCommand0x14() {
        // ZT758A(373)：舌舔 0x14 + 吮吸 + 震动。
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(373))
            ?: error("ZEMALIA ZT758A(373) protocol not resolved")

        assertEquals("zemalia_zt758a", protocol.status.id)
        assertTrue(protocol.status.channelNames.contains("舌舔"))

        // 舌舔功能码 6，档位 4：无强度滑杆，level 恒 0 → 55 14 00 00 04 00 00。
        val lick = protocol.commandsFor(ToyControlAction.Combined(mode = 6 * 100 + 4, intensity = 9))
        assertEquals(1, lick.size)
        assertEquals("55140000040000", assertIs<BleProtocolOperation.Write>(lick[0]).bytes.hexUpper())
    }

    @Test
    fun stretchDeviceUsesCommand0x08() {
        // ZT370A(114)：伸缩 0x08 + 震动。
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(114))
            ?: error("ZEMALIA ZT370A(114) protocol not resolved")

        assertEquals("zemalia_zt370a", protocol.status.id)
        // 伸缩功能码 1，档位 3，强度 7：55 08 00 00 03 07 00。
        val stretch = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 3, intensity = 7))
        assertEquals(1, stretch.size)
        assertEquals("55080000030700", assertIs<BleProtocolOperation.Write>(stretch[0]).bytes.hexUpper())
    }

    @Test
    fun stopSendsAllFunctionStopsAndGlobalFallback() {
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(87))
            ?: error("ZEMALIA ZX650A(87) protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        // 三个功能各发停止帧（吮吸、拍打、震动），末尾补全局兜底。
        assertEquals("55090000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("55070000000000", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
        assertEquals("55030000000000", assertIs<BleProtocolOperation.Write>(stop[2]).bytes.hexUpper())
        assertEquals("550400000000AA", assertIs<BleProtocolOperation.Write>(stop[3]).bytes.hexUpper())
    }

    @Test
    fun singleSuckDeviceExposesOnlySuck() {
        // ZT799A(352)：单吮吸 8 档无强度。
        val protocol = BleProtocolRegistry.resolveNative(zemaliaFingerprint(352))
            ?: error("ZEMALIA ZT799A(352) protocol not resolved")
        assertEquals("zemalia_zt799a", protocol.status.id)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
    }

    @Test
    fun unknownWhitelistCodeIsNotClaimedByZemalia() {
        // 非白名单产品码（如 200）不应被 ZEMALIA 接管。
        assertNull(BleProtocolRegistry.resolveNative(zemaliaFingerprint(200)))
    }

    @Test
    fun requiresSvakomManufacturerDataAndGatt() {
        // 缺司康沃厂商前缀时不匹配。
        assertNull(BleProtocolRegistry.resolveNative(zemaliaFingerprint(87, manufacturerData = "")))
        // 缺 GATT 特征时不匹配。
        assertNull(
            BleProtocolRegistry.resolveNative(
                zemaliaFingerprint(87).copy(characteristicUuids = emptySet()),
            ),
        )
    }

    // 构造司康沃 V2 广播 payload：前缀 53 56 41、byte[3]=0x02、产品码在 byte[13..15] 24-bit 大端。
    private fun zemaliaFingerprint(
        productCode: Int,
        name: String = "ZEMALIA",
        manufacturerData: String? = null,
    ): BleGattFingerprint {
        val data = manufacturerData ?: buildString {
            append("0x5350:53 56 41 02 FF 00 00 00 00 00 00 00 00 ")
            append("%02X ".format((productCode shr 16) and 0xff))
            append("%02X ".format((productCode shr 8) and 0xff))
            append("%02X".format(productCode and 0xff))
        }
        return BleGattFingerprint(
            name = name,
            address = "53:50:00:00:00:01",
            manufacturerData = data,
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
        )
    }

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    }
}
