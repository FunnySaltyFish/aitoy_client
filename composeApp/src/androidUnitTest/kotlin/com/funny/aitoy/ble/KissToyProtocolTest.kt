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
    fun tutuIIUsesChallengeAuthAndFixedXorMotorPacket() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint()) ?: error("KissToy Tutu II protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("震动", "伸缩"), protocol.status.channelNames)

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
        assertEquals(2, run.size)
        val write = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals(TUTU_WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals("58FF0102DB8492C6FFCFFFFFFF", write.bytes.hexUpper())

        val vibrateOnly = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 0))
        assertEquals("58FF0102DB84B6C6FFCFFFFFFF", assertIs<BleProtocolOperation.Write>(vibrateOnly[0]).bytes.hexUpper())

        val stretchOnly = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 0, externalIntensity = 50))
        assertEquals("58FF0102DBAD92C6FFCFFFFFFF", assertIs<BleProtocolOperation.Write>(stretchOnly[0]).bytes.hexUpper())

        assertEquals(200L, protocol.keepaliveIntervalMs())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals(5, stop.size)
        val stopWrites = stop.filterIsInstance<BleProtocolOperation.Write>()
        assertEquals(3, stopWrites.size)
        stopWrites.forEach {
            assertEquals(TUTU_WRITE_UUID, it.characteristicUuid)
            assertEquals("58FF0102DBADB6C6FFCFFFFFFF", it.bytes.hexUpper())
        }
    }

    @Test
    fun qcttWithDdddRouteDoesNotFallBackToHistoricalKissToy() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint(name = "QCTT", includeHistoricalKissToyGatt = true))
            ?: error("QCTT protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
    }

    @Test
    fun qcttWithHistoricalKissToyRouteCanTryTutuBeforeHistoricalProtocol() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttHistoricalKissToyFingerprint())

        assertEquals(
            listOf("kisstoy_tutu2_kisstoy_gatt", "kisstoy_gatt"),
            protocols.take(2).map { it.status.id },
        )

        val route = protocols.first { it.status.id == "kisstoy_tutu2_kisstoy_gatt" }
        val init = route.initialize(qcttHistoricalKissToyFingerprint())
        assertEquals(KISSTOY_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)

        val auth = route.onNotify(KISSTOY_NOTIFY_UUID, "58000102030405060708090a0b".hexBytes()).first()
        assertEquals(KISSTOY_WRITE_UUID, assertIs<BleProtocolOperation.Write>(auth).characteristicUuid)
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
            listOf("kisstoy_tutu2_ae3a", "kisstoy_tutu2_kisstoy_gatt", "kisstoy_tutu2_ae00", "kisstoy_gatt"),
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
        name: String = "迷路",
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
