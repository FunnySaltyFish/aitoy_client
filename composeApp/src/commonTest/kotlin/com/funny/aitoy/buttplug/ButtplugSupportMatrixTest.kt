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
                "bananasome",
                "cowgirl",
                "cupido",
                "cowgirl-cone",
                "deepsire",
                "feelingso",
                "fox",
                "foreo",
                "galaku-pump",
                "hgod",
                "htk_bm",
                "itoys",
                "kiiroo-powershot",
                "kiiroo-prowand",
                "kiiroo-spot",
                "kiiroo-v2-vibrator",
                "lelo-f1s",
                "libo-elle",
                "libo-shark",
                "libo-vibes",
                "lovedistance",
                "lovehoney-desire",
                "leten",
                "lioness",
                "magic-motion-3",
                "magic-motion-4",
                "mannuo",
                "maxpro",
                "meese",
                "mizzzee-v2",
                "mizzzee-v3",
                "motorbunny",
                "mymuselinkplus",
                "mysteryvibe",
                "mysteryvibe-v2",
                "nexus-revo",
                "nobra",
                "omobo",
                "patoo",
                "picobong",
                "prettylove",
                "realov",
                "sakuraneko",
                "sexverse-lg389",
                "sexverse-v2",
                "sexverse-v3",
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
                "svakom-barney",
                "svakom-iker",
                "svakom-v4",
                "svakom-v6",
                "synchro",
                "tryfun-blackhole",
                "tryfun-meta2",
                "utimi",
                "wetoy",
                "xiuxiuda",
                "xibao",
                "xuanhuan",
                "youcups",
                "youou",
                "zalo",
                "vibratissimo",
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
