package com.funny.aitoy.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class KissToyProtocolTest {
    @Test
    fun existingKissToyProtocolKeepsHistoricalControlShape() {
        val protocol = BleProtocolRegistry.resolveNative(kissToyFingerprint()) ?: error("KissToy protocol not resolved")

        assertEquals("kisstoy_gatt", protocol.status.id)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals(listOf("主电机", "第二电机", "抽插", "双通道"), protocol.status.modeNames)

        val operations = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 20, externalIntensity = 40))

        assertEquals(3, operations.size)
        operations.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(KISSTOY_WRITE_UUID, write.characteristicUuid)
            assertEquals(true, write.withResponse)
        }
    }

    @Test
    fun tutuIIUsesChallengeAuthAndOfficialV2MotorPackets() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint()) ?: error("KissToy Tutu II protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("底座伸缩", "头部伸缩"), protocol.status.channelNames)

        val init = protocol.initialize(tutuFingerprint())
        assertIs<BleProtocolOperation.SubscribeNotify>(init[0])
        assertIs<BleProtocolOperation.WaitForProtocolReady>(init[1])

        val challenge = "58000102030405060708090a0b".hexBytes()
        val auth = protocol.onNotify(TUTU_NOTIFY_UUID, challenge).first()
        val authWrite = assertIs<BleProtocolOperation.Write>(auth)
        assertEquals(TUTU_WRITE_UUID, authWrite.characteristicUuid)
        assertFalse(authWrite.withResponse)
        assertEquals("58EA31B9D8A9B3C028C7E0E39D", authWrite.bytes.hexUpper())

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 50))
        assertEquals(4, run.size)
        val baseWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals(TUTU_WRITE_UUID, baseWrite.characteristicUuid)
        assertFalse(baseWrite.withResponse)
        assertEquals("4602A64694D888666676B6D6", baseWrite.bytes.hexUpper())
        val headWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals(TUTU_WRITE_UUID, headWrite.characteristicUuid)
        assertFalse(headWrite.withResponse)
        assertEquals("4602A646945888666676B656", headWrite.bytes.hexUpper())

        val baseOnly = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 0))
        assertEquals("4602A64694D888666676B6D6", assertIs<BleProtocolOperation.Write>(baseOnly[0]).bytes.hexUpper())
        assertEquals("4602A646945876866676B624", assertIs<BleProtocolOperation.Write>(baseOnly[2]).bytes.hexUpper())

        val headOnly = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 0, externalIntensity = 50))
        assertEquals("4602A64694D876866676B6A4", assertIs<BleProtocolOperation.Write>(headOnly[0]).bytes.hexUpper())
        assertEquals("4602A646945888666676B656", assertIs<BleProtocolOperation.Write>(headOnly[2]).bytes.hexUpper())

        assertEquals(500L, protocol.keepaliveIntervalMs())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals(7, stop.size)
        val stopWrites = stop.filterIsInstance<BleProtocolOperation.Write>()
        assertEquals(4, stopWrites.size)
        stopWrites.forEach { assertEquals(TUTU_WRITE_UUID, it.characteristicUuid) }
        assertEquals("4602A64694D876866676B6A4", stopWrites[0].bytes.hexUpper())
        assertEquals("4602A646945876866676B624", stopWrites[1].bytes.hexUpper())
        assertEquals("4602A64694D876866676B6A4", stopWrites[2].bytes.hexUpper())
        assertEquals("4602A646945876866676B624", stopWrites[3].bytes.hexUpper())
    }

    @Test
    fun qcttWithDdddRouteDoesNotFallBackToHistoricalKissToy() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint(name = "QCTT", includeHistoricalKissToyGatt = true))
            ?: error("QCTT protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
    }

    @Test
    fun qcttWithHistoricalKissToyRouteUsesHistoricalProtocolOnly() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttHistoricalKissToyFingerprint())

        assertEquals(
            listOf("kisstoy_gatt"),
            protocols.map { it.status.id },
        )
    }

    @Test
    fun localizedLostWithHistoricalKissToyRouteUsesHistoricalProtocolOnly() {
        val protocols = BleProtocolRegistry.resolveNativeAll(lostHistoricalKissToyFingerprint())

        assertEquals(
            listOf("kisstoy_gatt"),
            protocols.map { it.status.id },
        )
    }

    @Test
    fun qcpwDdddRouteUsesOfficialV2LostProfile() {
        val protocol = BleProtocolRegistry.resolveNative(officialV2Fingerprint("QCPW")) ?: error("QCPW protocol not resolved")

        assertEquals("kisstoy_official_v2_dddd", protocol.status.id)
        assertEquals("KissToy 迷路-入体版", protocol.status.displayName)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("震动", "吮吸"), protocol.status.channelNames)
        assertEquals(emptyList(), protocol.initialize(officialV2Fingerprint("QCPW")))

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertEquals(3, run.size)
        val vibrateWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals(TUTU_WRITE_UUID, vibrateWrite.characteristicUuid)
        assertFalse(vibrateWrite.withResponse)
        assertEquals("4602A64694D888666676B6D6", vibrateWrite.bytes.hexUpper())
        val suckingWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals(TUTU_WRITE_UUID, suckingWrite.characteristicUuid)
        assertFalse(suckingWrite.withResponse)
        assertEquals("4602A64694D29F466676B6BF", suckingWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrites = stop.filterIsInstance<BleProtocolOperation.Write>()
        assertEquals(4, stopWrites.size)
        stopWrites.forEach { assertEquals(TUTU_WRITE_UUID, it.characteristicUuid) }
        assertEquals("4602A64694D876866676B6A4", stopWrites[0].bytes.hexUpper())
        assertEquals("4602A64694D2A6866676B6A6", stopWrites[1].bytes.hexUpper())
        assertEquals("4602A64694D876866676B6A4", stopWrites[2].bytes.hexUpper())
        assertEquals("4602A64694D2A6866676B6A6", stopWrites[3].bytes.hexUpper())
    }

    @Test
    fun qcvwDdddRouteUsesSecondVibrateCommand() {
        val protocol = BleProtocolRegistry.resolveNative(officialV2Fingerprint("QCVW")) ?: error("QCVW protocol not resolved")

        assertEquals("KissToy 迷路-震动版", protocol.status.displayName)
        assertEquals(listOf("震动", "第二震动"), protocol.status.channelNames)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertEquals("4602A64694D888666676B6D6", assertIs<BleProtocolOperation.Write>(run[0]).bytes.hexUpper())
        assertEquals("4602A64458D29F466676B6C1", assertIs<BleProtocolOperation.Write>(run[2]).bytes.hexUpper())
    }

    @Test
    fun qcttA0d7RouteIsOfferedAsTutuCandidate() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttA0d7Fingerprint())

        assertEquals(
            listOf("kisstoy_tutu2_a0d7"),
            protocols.map { it.status.id },
        )
        assertEquals(A0D7_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(protocols[0].initialize(qcttA0d7Fingerprint())[0]).characteristicUuid)
    }

    @Test
    fun qcttLiveGattSummaryPrefersAe3aTutuRoute() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttLiveAe3aFingerprint())

        assertEquals(
            listOf("kisstoy_tutu2_ae3a", "kisstoy_tutu2_ae00", "kisstoy_gatt"),
            protocols.map { it.status.id },
        )

        val route = protocols.first()
        val init = route.initialize(qcttLiveAe3aFingerprint())
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)

        val auth = route.onNotify(AE3A_NOTIFY_UUID, "58000102030405060708090a0b".hexBytes()).first()
        assertEquals(AE3A_WRITE_UUID, assertIs<BleProtocolOperation.Write>(auth).characteristicUuid)
    }

    @Test
    fun qcttNameAloneDoesNotSelectTutuRoute() {
        val protocol = BleProtocolRegistry.resolveNative(qcttNameOnlyFingerprint())

        assertEquals(null, protocol)
    }

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun kissToyFingerprint() = BleGattFingerprint(
        name = "QCPW",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID),
        characteristicUuids = setOf(KISSTOY_WRITE_UUID, KISSTOY_NOTIFY_UUID),
    )

    private fun tutuFingerprint(
        name: String = "QCTT",
        includeHistoricalKissToyGatt: Boolean = false,
    ) = BleGattFingerprint(
        name = name,
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = buildSet {
            add(TUTU_SERVICE_UUID)
            if (includeHistoricalKissToyGatt) add(KISSTOY_SERVICE_UUID)
        },
        characteristicUuids = buildSet {
            add(TUTU_WRITE_UUID)
            add(TUTU_NOTIFY_UUID)
            if (includeHistoricalKissToyGatt) {
                add(KISSTOY_WRITE_UUID)
                add(KISSTOY_NOTIFY_UUID)
            }
        },
    )

    private fun qcttHistoricalKissToyFingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID),
        characteristicUuids = setOf(KISSTOY_WRITE_UUID, KISSTOY_NOTIFY_UUID),
    )

    private fun lostHistoricalKissToyFingerprint() = BleGattFingerprint(
        name = "迷路",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID),
        characteristicUuids = setOf(KISSTOY_WRITE_UUID, KISSTOY_NOTIFY_UUID),
    )

    private fun officialV2Fingerprint(name: String) = BleGattFingerprint(
        name = name,
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(TUTU_SERVICE_UUID),
        characteristicUuids = setOf(TUTU_WRITE_UUID, TUTU_NOTIFY_UUID),
    )

    private fun qcttA0d7Fingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = setOf(A0D7_SERVICE_UUID),
        characteristicUuids = setOf(A0D7_WRITE_UUID, A0D7_NOTIFY_UUID),
    )

    private fun qcttLiveAe3aFingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID, AE3A_SERVICE_UUID, AE00_SERVICE_UUID),
        characteristicUuids = setOf(
            KISSTOY_WRITE_UUID,
            KISSTOY_NOTIFY_UUID,
            AE3A_WRITE_UUID,
            AE3A_NOTIFY_UUID,
            AE00_WRITE_UUID,
            AE00_NOTIFY_UUID,
        ),
    )

    private fun qcttNameOnlyFingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = emptySet(),
        characteristicUuids = emptySet(),
    )

    private companion object {
        val KISSTOY_SERVICE_UUID: UUID = UUID.fromString("00001000-0000-1000-8000-00805f9b34fb")
        val KISSTOY_WRITE_UUID: UUID = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb")
        val KISSTOY_NOTIFY_UUID: UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb")

        val TUTU_SERVICE_UUID: UUID = UUID.fromString("0000dddd-0000-1000-8000-00805f9b34fb")
        val TUTU_WRITE_UUID: UUID = UUID.fromString("0000ddd1-0000-1000-8000-00805f9b34fb")
        val TUTU_NOTIFY_UUID: UUID = UUID.fromString("0000ddd2-0000-1000-8000-00805f9b34fb")

        val A0D7_SERVICE_UUID: UUID = UUID.fromString("a0d70001-4c16-4ba7-977a-d394920e13a3")
        val A0D7_WRITE_UUID: UUID = UUID.fromString("a0d70002-4c16-4ba7-977a-d394920e13a3")
        val A0D7_NOTIFY_UUID: UUID = UUID.fromString("a0d70003-4c16-4ba7-977a-d394920e13a3")

        val AE3A_SERVICE_UUID: UUID = UUID.fromString("0000ae3a-0000-1000-8000-00805f9b34fb")
        val AE3A_WRITE_UUID: UUID = UUID.fromString("0000ae3b-0000-1000-8000-00805f9b34fb")
        val AE3A_NOTIFY_UUID: UUID = UUID.fromString("0000ae3c-0000-1000-8000-00805f9b34fb")

        val AE00_SERVICE_UUID: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
        val AE00_WRITE_UUID: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val AE00_NOTIFY_UUID: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
    }
}
