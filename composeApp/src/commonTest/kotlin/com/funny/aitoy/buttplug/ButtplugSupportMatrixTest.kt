package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertEquals

class ButtplugSupportMatrixTest {
    @Test
    fun registryKeepsExecutableButtplugHandlersExplicit() {
        assertEquals(
            setOf(
                "wevibe",
                "wevibe-8bit",
                "wevibe-chorus",
                "activejoy",
                "adrienlastic",
                "amorelie-joy",
                "aneros",
                "cupido",
                "deepsire",
                "fox",
                "kiiroo-prowand",
                "kiiroo-spot",
                "lovedistance",
                "magic-motion-3",
                "mannuo",
                "maxpro",
                "meese",
                "mymuselinkplus",
                "nobra",
                "omobo",
                "picobong",
                "prettylove",
                "realov",
                "sexverse-v4",
                "sexverse-v5",
                "svakom-alex",
                "svakom-alex-v2",
                "svakom-dice",
                "svakom-pulse",
                "svakom-suitcase",
                "svakom-tarax",
                "svakom-v1",
                "svakom-v2",
                "wetoy",
                "xiuxiuda",
                "youcups",
            ),
            ButtplugLocalHandlerRegistry.controllableButtplugHandlerIds,
        )
    }

    @Test
    fun btleProtocolWithoutHandlerIsRecognizedAsMissingHandler() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-btle",
                communicationTypes = setOf(COMMUNICATION_BTLE),
            ),
        )

        assertEquals(ButtplugSupportStatus.RecognizedMissingHandler, entry.status)
    }

    @Test
    fun serialOnlyProtocolIsRecognizedAsUnsupportedTransport() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-serial",
                communicationTypes = setOf(COMMUNICATION_SERIAL),
            ),
        )

        assertEquals(ButtplugSupportStatus.RecognizedUnsupportedTransport, entry.status)
    }

    @Test
    fun supportEntryMapsProtocolOutputsToStrongKinds() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-linear",
                communicationTypes = setOf(COMMUNICATION_BTLE),
                outputType = OUTPUT_HW_POSITION_WITH_DURATION,
            ),
        )

        assertEquals(setOf(ToyOutputKind.Linear), entry.outputKinds)
    }

    @Test
    fun outputFeatureConvertsToToyFeature() {
        val feature = ButtplugOutputFeature(
            type = OUTPUT_ROTATE,
            min = -20,
            max = 20,
            featureIndex = 1,
            description = "Rotate",
        ).toToyFeature()

        assertEquals(
            ToyFeature(
                index = 1,
                kind = ToyOutputKind.Rotate,
                label = "Rotate",
                min = -20,
                max = 20,
            ),
            feature,
        )
    }

    private fun protocol(
        id: String,
        communicationTypes: Set<String>,
        outputType: String = OUTPUT_VIBRATE,
    ): ButtplugProtocolDefinition =
        ButtplugProtocolDefinition(
            id = id,
            displayName = id,
            communicationTypes = communicationTypes,
            communicationNames = emptyList(),
            endpoints = emptyList(),
            defaultDevice = ButtplugDeviceDefinition(
                identifiers = emptyList(),
                name = id,
                features = listOf(
                    ButtplugOutputFeature(
                        type = outputType,
                        min = 0,
                        max = 100,
                        featureIndex = 0,
                        description = "",
                    ),
                ),
            ),
            configurations = emptyList(),
        )
}
