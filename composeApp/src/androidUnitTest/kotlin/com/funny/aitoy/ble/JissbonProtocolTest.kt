package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class JissbonProtocolTest {
    @Test
    fun crushEggResolvesToDualVibrateProfile() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("Crush Vibrating Egg 8F2A"))
            ?: error("Jissbon Crush protocol not resolved")

        assertEquals("jissbon_crush_egg", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("震动 A", "震动 B"), protocol.status.channelNames)
        assertEquals(100, protocol.status.intensityMax)
    }

    @Test
    fun crushEggDualMotorFrameMatchesOfficial() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("Crush Vibrating Egg"))!!
        // functionType [1,2]：mask 5，func1→byte5、func2→byte3。
        val cmd = protocol.commandsFor(ToyControlAction.DualMotor(mode = 0, internalIntensity = 100, externalIntensity = 50))
        assertEquals("550305320064", assertIs<BleProtocolOperation.Write>(cmd[0]).bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("550300000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
    }

    @Test
    fun singleMotorEggUsesByte5() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("JSB26_42"))!!
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        val cmd = protocol.commandsFor(ToyControlAction.Intensity(80))
        assertEquals("550301000050", assertIs<BleProtocolOperation.Write>(cmd[0]).bytes.hexUpper())
    }

    @Test
    fun stormGirlSeUsesEightByteFrameWithMaskOne() {
        val protocol = JissbonProfileProtocol(JISSBON_PRODUCTS.getValue("Jissbon Storm girl SE"))
        val cmd = protocol.commandsFor(ToyControlAction.Intensity(60))
        // 官方 r=[85,3,1,0,0,0,0,0]，func4→byte6。
        assertEquals("5503010000003C00", assertIs<BleProtocolOperation.Write>(cmd[0]).bytes.hexUpper())
    }

    @Test
    fun abyssUsesMaskEightEightByteFrame() {
        val protocol = JissbonProfileProtocol(JISSBON_PRODUCTS.getValue("Jissbon Abyss"))
        val cmd = protocol.commandsFor(ToyControlAction.Intensity(50))
        assertEquals("5503080000003200", assertIs<BleProtocolOperation.Write>(cmd[0]).bytes.hexUpper())
    }

    @Test
    fun dualRegionOneFourFrameMatchesOfficial() {
        val protocol = JissbonProfileProtocol(JISSBON_PRODUCTS.getValue("Jissbon Terminator"))
        // functionType [1,4]：mask 9、8 字节，func1→byte5、func4→byte6。
        val cmd = protocol.commandsFor(ToyControlAction.DualMotor(mode = 0, internalIntensity = 100, externalIntensity = 40))
        assertEquals("5503090000642800", assertIs<BleProtocolOperation.Write>(cmd[0]).bytes.hexUpper())
        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("5503000000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
    }

    @Test
    fun svaModuleDeviceSubscribesFfe2NotifyBeforeControl() {
        // Fancy Remote Vibrator 实机跑在司康沃 SVA V2 模组上，notify 在 FFE2，控制前必须先订阅。
        val fp = BleGattFingerprint(
            name = "Fancy Remote Vibrator",
            address = "FF:25:08:C3:49:AA",
            manufacturerData = "0x27:53 56 41 02 01 FF 25 08 C3 49 AA FF 32 5A 00 3F A2 22 BB",
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = setOf(WRITE_UUID, NOTIFY_FFE2_UUID),
        )
        val protocol = BleProtocolRegistry.resolveNative(fp) ?: error("protocol not resolved")
        assertEquals("jissbon_fancy_remote", protocol.status.id)
        val init = protocol.initialize(fp)
        val subscribe = assertIs<BleProtocolOperation.SubscribeNotify>(init.first())
        assertEquals(NOTIFY_FFE2_UUID, subscribe.characteristicUuid)
    }

    @Test
    fun hm10ModuleFallsBackToFfe1Notify() {
        // 纯 HM-10 模组只有 FFE1，notify 回退到 FFE1。
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("Crush Vibrating Egg"))!!
        val subscribe = assertIs<BleProtocolOperation.SubscribeNotify>(
            protocol.initialize(fingerprint("Crush Vibrating Egg")).first(),
        )
        assertEquals(WRITE_UUID, subscribe.characteristicUuid)
    }

    @Test
    fun unknownJissbonNameFallsBackToDual() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("Jissbon Mystery"))!!
        assertEquals("jissbon_generic_dual", protocol.status.id)
    }

    @Test
    fun genericFfe0DeviceIsNotClaimed() {
        // 非杰士邦的通用 FFE0 透传设备不应被误判成杰士邦。
        assertNull(
            BleProtocolRegistry.resolveNativeAll(fingerprint("M5"))
                .firstOrNull { it.status.id.startsWith("jissbon") },
        )
    }

    @Test
    fun noResponseFallbackOffersOtherCommandShapes() {
        val candidates = BleProtocolRegistry.resolveNativeAll(fingerprint("Crush Vibrating Egg"))
            .filter { it.status.id.startsWith("jissbon") }
        // 自动识别在前，命令形态备选在后，支持"没反应换一个"。
        assertEquals("jissbon_crush_egg", candidates.first().status.id)
        assertEquals(
            listOf("jissbon_variant_single", "jissbon_variant_triple", "jissbon_variant_14", "jissbon_variant_16"),
            candidates.drop(1).map { it.status.id },
        )
    }

    private fun fingerprint(name: String): BleGattFingerprint = BleGattFingerprint(
        name = name,
        address = "AB:CD:EF:01:02:03",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_FFE2_UUID: Uuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    }
}
