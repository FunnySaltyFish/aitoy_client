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
                "cachito",
                "cueme",
                "cowgirl",
                "cupido",
                "cowgirl-cone",
                "deepsire",
                "feelingso",
                "fluffer",
                "fleshy-thrust",
                "fox",
                "foreo",
                "fredorch",
                "fredorch-rotary",
                "galaku",
                "galaku-pump",
                "hgod",
                "hismith",
                "hismith-mini",
                "honeyplaybox",
                "htk_bm",
                "itoys",
                "jejoue",
                "joyhub",
                "kiiroo-powershot",
                "kiiroo-prowand",
                "kiiroo-spot",
                "kiiroo-v1",
                "kiiroo-v2",
                "kiiroo-v21",
                "kiiroo-v21-initialized",
                "kiiroo-v2-vibrator",
                "kiiroo-v3",
                "lelo-f1s",
                "lelo-f1sv2",
                "lelo-harmony",
                "libo-elle",
                "libo-shark",
                "libo-vibes",
                "loob",
                "lovense",
                "lovedistance",
                "lovehoney-desire",
                "leten",
                "lioness",
                "luvmazer",
                "magic-motion-1",
                "magic-motion-2",
                "magic-motion-3",
                "magic-motion-4",
                "mannuo",
                "maxpro",
                "meese",
                "mizzzee-v2",
                "mizzzee-v3",
                "monsterpub",
                "motorbunny",
                "muse",
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
                "satisfyer",
                "sayberx",
                "sexverse-lg389",
                "sexverse-v1",
                "sexverse-v2",
                "sexverse-v3",
                "sexverse-v4",
                "sexverse-v5",
                "sensee-v2",
                "serveu",
                "svakom-alex",
                "svakom-alex-v2",
                "svakom-avaneo",
                "svakom-barnard",
                "svakom-dice",
                "svakom-dt250a",
                "svakom-jordan",
                "svakom-pulse",
                "svakom-sam",
                "svakom-sam2",
                "svakom-suitcase",
                "svakom-tarax",
                "svakom-v1",
                "svakom-v2",
                "svakom-v3",
                "svakom-barney",
                "svakom-iker",
                "svakom-v4",
                "svakom-v5",
                "svakom-v6",
                "synchro",
                "tryfun-blackhole",
                "tryfun-meta2",
                "tryfun",
                "thehandy",
                "thehandy-v3",
                "utimi",
                "vibcrafter",
                "vorze-sa",
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
    fun externalButtplugTransportProtocolsAreControllable() {
        for (protocolId in ExternalButtplugTransportPlans.protocolIds) {
            val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
                protocol(
                    id = protocolId,
                    communicationTypes = setOf(COMMUNICATION_SERIAL),
                    outputType = OUTPUT_VIBRATE,
                ),
            )

            assertEquals(ButtplugSupportStatus.Controllable, entry.status, protocolId)
            assertEquals(ButtplugHandlerSource.ExternalButtplugTransport, entry.handler?.source, protocolId)
        }
    }

    @Test
    fun btleProtocolWithoutOutputIsRecognizedAsNoOutput() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-input-only",
                communicationTypes = setOf(COMMUNICATION_BTLE),
                outputType = null,
            ),
        )

        assertEquals(ButtplugSupportStatus.RecognizedNoOutput, entry.status)
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
    fun supportEntryMapsSprayOutput() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-spray",
                communicationTypes = setOf(COMMUNICATION_BTLE),
                outputType = OUTPUT_SPRAY,
            ),
        )

        assertEquals(setOf(ToyOutputKind.Spray), entry.outputKinds)
    }

    @Test
    fun supportEntryMapsTemperatureOutput() {
        val entry = ButtplugLocalHandlerRegistry.supportEntryFor(
            protocol(
                id = "example-temperature",
                communicationTypes = setOf(COMMUNICATION_BTLE),
                outputType = OUTPUT_TEMPERATURE,
            ),
        )

        assertEquals(setOf(ToyOutputKind.Temperature), entry.outputKinds)
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
        outputType: String? = OUTPUT_VIBRATE,
    ): ButtplugProtocolDefinition =
        ButtplugProtocolDefinition(
            id = id,
            displayName = id,
            communicationTypes = communicationTypes,
            communicationNames = emptyList(),
            manufacturerDataCompanies = emptySet(),
            endpoints = emptyList(),
            defaultDevice = ButtplugDeviceDefinition(
                identifiers = emptyList(),
                name = id,
                features = outputType?.let {
                    listOf(
                        ButtplugOutputFeature(
                            type = it,
                            min = 0,
                            max = 100,
                            featureIndex = 0,
                            description = "",
                        ),
                    )
                }.orEmpty(),
            ),
            configurations = emptyList(),
        )
}
