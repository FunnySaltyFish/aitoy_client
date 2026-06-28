package com.funny.aitoy.buttplug

import ai_toy_bridge.composeapp.generated.resources.Res
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.decodeFromString

object ButtplugConfigRepository {
    private const val INDEX_PATH = "files/buttplug/protocol-index.txt"
    private const val PROTOCOL_DIR = "files/buttplug/protocols"

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
        ),
    )
    private var cached: List<ButtplugProtocolDefinition>? = null

    suspend fun loadAll(): List<ButtplugProtocolDefinition> {
        cached?.let { return it }
        val index = Res.readBytes(INDEX_PATH)
            .decodeToString()
            .lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".yml") }
            .toList()
        val definitions = index.mapNotNull { fileName ->
            runCatching {
                val text = Res.readBytes("$PROTOCOL_DIR/$fileName").decodeToString()
                yaml.decodeFromString<YamlNode>(text)
                    .asMap()
                    ?.toProtocolDefinition(fileName.removeSuffix(".yml"))
            }.getOrNull()
        }
        cached = definitions
        return definitions
    }

    suspend fun matchAll(fingerprint: ButtplugDeviceFingerprint): List<ButtplugDeviceMatch> =
        loadAll().mapNotNull { it.match(fingerprint) }
}

private fun YamlMap.toProtocolDefinition(id: String): ButtplugProtocolDefinition {
    val defaults = mapValue("defaults")?.asMap()
    val defaultFeatures = defaults?.featureNodes().orEmpty()
    val communicationNodes = listValue("communication").mapNotNull { it.asMap() }
    val communicationTypes = communicationNodes
        .flatMap { node -> node.entries.entries.map { (type, _) -> type.content.lowercase() } }
        .toSet()
    val btleNodes = communicationNodes.mapNotNull { it.mapValue(COMMUNICATION_BTLE)?.asMap() }
    val endpoints = btleNodes.flatMap { btle ->
        btle.mapValue("services")
            ?.asMap()
            ?.entries
            ?.entries
            ?.mapNotNull { (service, characteristicsNode) ->
                val characteristics = characteristicsNode.asMap()?.entries?.entries
                    ?.associate { (name, uuid) -> name.content.lowercase() to uuid.scalarText().normalizedUuidText() }
                    .orEmpty()
                ButtplugEndpoint(
                    serviceUuid = service.content.normalizedUuidText(),
                    characteristics = characteristics,
                )
            }
            .orEmpty()
    }
    val defaultDevice = defaults.toDeviceDefinition(defaultFeatures)
    val configurations = listValue("configurations").mapNotNull { node ->
        node.asMap()?.toDeviceDefinition(node.asMap()?.featureNodes() ?: defaultFeatures)
    }
    return ButtplugProtocolDefinition(
        id = id,
        displayName = defaultDevice.name.ifBlank { id },
        communicationTypes = communicationTypes,
        communicationNames = btleNodes.flatMap { it.stringListValue("names") }.distinct(),
        manufacturerDataCompanies = btleNodes
            .flatMap { it.listValue("manufacturer_data") }
            .mapNotNull { it.asMap()?.intValue("company") }
            .toSet(),
        endpoints = endpoints,
        defaultDevice = defaultDevice,
        configurations = configurations,
    )
}

private fun YamlMap?.toDeviceDefinition(
    resolvedFeatures: List<YamlMap>,
): ButtplugDeviceDefinition =
    ButtplugDeviceDefinition(
        identifiers = this?.stringListValue("identifier").orEmpty(),
        name = this?.stringValue("name").orEmpty(),
        features = resolvedFeatures.flatMap { feature ->
            val featureIndex = feature.intValue("index") ?: 0
            val description = feature.stringValue("description").orEmpty()
            feature.mapValue("output")
                ?.asMap()
                ?.entries
                ?.entries
                ?.mapNotNull { (type, outputNode) ->
                    val output = outputNode.asMap()
                    val range = output?.rangeValue("value") ?: output?.rangeValue("duration")
                    range?.let {
                        ButtplugOutputFeature(
                            type = type.content,
                            min = it.first,
                            max = it.last,
                            featureIndex = featureIndex,
                            description = description,
                        )
                    }
                }
                .orEmpty()
        },
    )

private fun YamlMap.featureNodes(): List<YamlMap> =
    listValue("features").mapNotNull { it.asMap() }

private fun YamlMap.rangeValue(key: String): IntRange? {
    val values = mapValue(key)?.asIntList() ?: return null
    return if (values.size >= 2) values.first()..values.last() else null
}

private fun YamlNode.asIntList(): List<Int> =
    when (this) {
        is YamlList -> items.firstOrNull { it is YamlList }?.asIntList()
            ?: items.mapNotNull { it.scalarText().toIntOrNull() }
        else -> emptyList()
    }

private fun YamlMap.stringListValue(key: String): List<String> {
    val node = mapValue(key) ?: return emptyList()
    return when (node) {
        is YamlList -> node.items.mapNotNull { it.scalarText().takeIf(String::isNotBlank) }
        is YamlScalar -> listOf(node.content)
        else -> emptyList()
    }
}

private fun YamlMap.listValue(key: String): List<YamlNode> =
    (mapValue(key) as? YamlList)?.items.orEmpty()

private fun YamlMap.stringValue(key: String): String? =
    mapValue(key)?.scalarText()?.takeIf(String::isNotBlank)

private fun YamlMap.intValue(key: String): Int? =
    mapValue(key)?.scalarText()?.toIntOrNull()

private fun YamlMap.mapValue(key: String): YamlNode? =
    entries.entries.firstOrNull { it.key.content == key }?.value

private fun YamlNode?.asMap(): YamlMap? = this as? YamlMap

private fun YamlNode?.scalarText(): String =
    (this as? YamlScalar)?.content.orEmpty()
