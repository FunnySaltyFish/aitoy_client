package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChokerMiyinProtocolTest {
    @Test
    fun matchesOfficialMiyinBtDeviceBeforeGenericFff0Protocols() {
        val matches = BleProtocolRegistry.resolveNativeAll(fingerprint(name = "BT003250"))

        assertTrue(matches.isNotEmpty())
        assertEquals("choker_miyin", matches.first().status.id)
    }

    @Test
    fun rejectsSameGattWithoutOfficialName() {
        assertFalse(ChokerMiyinProtocol.matches(fingerprint(name = "Other Device")))
    }

    @Test
    fun intensityUsesOfficialA55aFrameWithBccAndStopAtZero() {
        assertContentEquals(
            hex("A5 07 5A 00 26 DE 0A"),
            firstWrite(ChokerMiyinProtocol.commandsFor(ToyControlAction.Intensity(1))).bytes,
        )
        assertContentEquals(
            hex("A5 07 5A 00 2B D3 0A"),
            firstWrite(ChokerMiyinProtocol.commandsFor(ToyControlAction.Intensity(50))).bytes,
        )
        assertContentEquals(
            hex("A5 07 3C 00 02 9C 0A"),
            firstWrite(ChokerMiyinProtocol.commandsFor(ToyControlAction.Intensity(0))).bytes,
        )
    }

    @Test
    fun patternAndKeepaliveUseOfficialFrames() {
        assertContentEquals(
            hex("A5 12 4B 0A 3C 2E 3C 2C 3C 2E 3C 2C 3C 2E 3C 2C F4 0A"),
            firstWrite(ChokerMiyinProtocol.commandsFor(ToyControlAction.Pattern(10))).bytes,
        )
        assertContentEquals(
            hex("A5 07 69 00 5A 91 0A"),
            firstWrite(ChokerMiyinProtocol.keepaliveCommands(emptyList())).bytes,
        )
    }

    private fun fingerprint(name: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = "",
            scanRecordHex = "",
            serviceUuids = setOf(Uuid.parse("0000fff0-0000-1000-8000-00805f9b34fb")),
            characteristicUuids = setOf(
                Uuid.parse("0000fff5-0000-1000-8000-00805f9b34fb"),
                Uuid.parse("0000fff6-0000-1000-8000-00805f9b34fb"),
            ),
        )

    private fun firstWrite(commands: List<BleProtocolOperation>): BleProtocolOperation.Write =
        commands.first() as BleProtocolOperation.Write

    private fun hex(text: String): ByteArray =
        text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
}
