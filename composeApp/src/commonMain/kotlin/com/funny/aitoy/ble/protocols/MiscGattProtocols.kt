package com.funny.aitoy.ble

import com.funny.aitoy.buttplug.ButtplugConfigRepository
import com.funny.aitoy.buttplug.ButtplugDeviceFingerprint
import com.funny.aitoy.buttplug.ButtplugDeviceMatch
import com.funny.aitoy.buttplug.ButtplugLocalHandlerRegistry
import com.funny.aitoy.buttplug.FlufferProtocolPlans
import com.funny.aitoy.buttplug.HandyProtocolPlans
import com.funny.aitoy.buttplug.HoneyPlayBoxFrameCollector
import com.funny.aitoy.buttplug.HoneyPlayBoxProtocolPlans
import com.funny.aitoy.buttplug.LegacyStpihkalProtocolPlans
import com.funny.aitoy.buttplug.LeloProtocolPlans
import com.funny.aitoy.buttplug.LinearProtocolPlan
import com.funny.aitoy.buttplug.LinearProtocolPlans
import com.funny.aitoy.buttplug.LovenseProtocolPlans
import com.funny.aitoy.buttplug.MixedOutputProtocolPlan
import com.funny.aitoy.buttplug.MixedOutputProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolPlan
import com.funny.aitoy.buttplug.ScalarProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolWrite
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlan
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlans
import com.funny.aitoy.buttplug.SpecializedProtocolPlans
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlan
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlans
import com.funny.aitoy.buttplug.ToyOutputKind
import com.funny.aitoy.buttplug.VibCrafterProtocolPlans
import com.funny.aitoy.buttplug.WeVibeProtocolPlans
import com.funny.aitoy.buttplug.normalizedUuidText
import com.funny.aitoy.buttplug.toToyOutputKind
import kotlin.random.Random
import kotlin.uuid.Uuid

internal object AnkniQd1XhtkjProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ff10-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ff11-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
    private val deviceInfoUuid = Uuid.parse("00002a50-0000-1000-8000-00805f9b34fb")

    override val status = ANKNI_QD1_XHTKJ_STATUS.copy(
        id = "ankni_qd1_xhtkj",
        displayName = "ANKNI QD1",
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniQd1Name()) return false
        if (!fingerprint.manufacturerData.contains("0x642", ignoreCase = true)) return false
        return fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        AnkniQd1XhtkjRouteProtocol(
            status = status,
            writeUuid = writeUuid,
            notifyUuid = notifyUuid,
            deviceInfoUuid = deviceInfoUuid,
        )

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        createInstance(fingerprint).initialize(fingerprint)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        qd1XhtkjCommandsFor(action, status, writeUuid, IntArray(2))
}

internal object AnkniQd1Ff15XhtkjProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ff14-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ff15-0000-1000-8000-00805f9b34fb")

    override val status = ANKNI_QD1_XHTKJ_STATUS.copy(
        id = "ankni_qd1_xhtkj_ff15",
        displayName = "ANKNI QD1",
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniQd1Name()) return false
        if (!fingerprint.manufacturerData.contains("0x642", ignoreCase = true)) return false
        return fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        AnkniQd1XhtkjRouteProtocol(
            status = status,
            writeUuid = writeUuid,
            notifyUuid = notifyUuid,
            deviceInfoUuid = null,
        )

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        createInstance(fingerprint).initialize(fingerprint)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        qd1XhtkjCommandsFor(action, status, writeUuid, IntArray(2))
}

private class AnkniQd1XhtkjRouteProtocol(
    override val status: BleProtocolStatus,
    private val writeUuid: Uuid,
    private val notifyUuid: Uuid,
    private val deviceInfoUuid: Uuid?,
) : BleDeviceProtocol {
    private val channelValues = IntArray(2)

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            if (fingerprint.characteristicUuids.contains(notifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(notifyUuid))
            }
            if (deviceInfoUuid != null && fingerprint.characteristicUuids.contains(deviceInfoUuid)) {
                add(BleProtocolOperation.Read(deviceInfoUuid))
            }
            add(xhtkjWrite(writeUuid, byteArrayOf(0x03, 0x12, 0xf6.toByte(), 0x00)))
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        qd1XhtkjCommandsFor(action, status, writeUuid, channelValues)
}

internal object MizzzeeXhtkjProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ff10-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ff11-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
    private val deviceInfoUuid = Uuid.parse("00002a50-0000-1000-8000-00805f9b34fb")

    override val status = XHTKJ_STATUS

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            if (fingerprint.characteristicUuids.contains(notifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(notifyUuid))
            }
            if (fingerprint.characteristicUuids.contains(deviceInfoUuid)) {
                add(BleProtocolOperation.Read(deviceInfoUuid))
            }
            add(xhtkjWrite(writeUuid, byteArrayOf(0x03, 0x12, 0xf6.toByte(), 0x00)))
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        xhtkjCommandsFor(action, status, writeUuid)
}

private val XHTKJ_STATUS = BleProtocolStatus(
    id = "mizzzee_xhtkj",
    displayName = "Mizzzee XHTKJ",
    controllable = true,
    intensityMax = 100,
    supportsMode = true,
    modeMax = 10,
    controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
    modeLabel = "预设节奏",
    modeNames = List(10) { index -> "模式 ${index + 1}" },
    intensityLabel = "滑动强度",
    automatic = true,
    repeatIntervalMs = 200,
)

private val ANKNI_QD1_XHTKJ_STATUS = BleProtocolStatus(
    id = "ankni_qd1_xhtkj",
    displayName = "ANKNI QD1",
    controllable = true,
    intensityMax = 100,
    supportsMode = true,
    modeMax = 10,
    controlStyle = ToyControlStyle.IndependentFunctions,
    modeLabel = "预设节奏",
    modeNames = List(10) { index -> "模式 ${index + 1}" },
    intensityLabel = "强度",
    channelNames = listOf("震动", "吮吸"),
    features = listOf(
        BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 0, label = "震动", functionCode = 1),
        BleProtocolFeature(type = "constrict", min = 0, max = 100, index = 1, label = "吮吸", functionCode = 2),
    ),
    automatic = true,
    repeatIntervalMs = 200,
)

private fun xhtkjCommandsFor(
    action: ToyControlAction,
    status: BleProtocolStatus,
    writeUuid: Uuid,
): List<BleProtocolOperation> =
    when (action) {
        is ToyControlAction.Intensity -> listOf(xhtkjStrengthCommand(writeUuid, action.value.coerceIn(0, status.intensityMax)))
        is ToyControlAction.Combined -> listOf(xhtkjModelCommand(writeUuid, action.mode, status.modeMax))
        is ToyControlAction.DualMotor -> listOf(xhtkjModelCommand(writeUuid, action.mode, status.modeMax))
        is ToyControlAction.Pattern -> listOf(xhtkjModelCommand(writeUuid, action.mode, status.modeMax))
        ToyControlAction.Stop -> listOf(xhtkjClearCommand(writeUuid), xhtkjResetModelCommand(writeUuid))
    }

private fun qd1XhtkjCommandsFor(
    action: ToyControlAction,
    status: BleProtocolStatus,
    writeUuid: Uuid,
    channelValues: IntArray,
): List<BleProtocolOperation> =
    when (action) {
        is ToyControlAction.Pattern -> listOf(xhtkjModelCommand(writeUuid, action.mode, status.modeMax))
        is ToyControlAction.Intensity -> {
            val value = action.value.coerceIn(0, status.intensityMax)
            channelValues[0] = value
            channelValues[1] = value
            listOf(xhtkjStrengthCommand(writeUuid, channelValues[0], channelValues[1]))
        }
        is ToyControlAction.Combined -> {
            updateQd1XhtkjFunction(action, status, channelValues)
            listOf(xhtkjStrengthCommand(writeUuid, channelValues[0], channelValues[1]))
        }
        is ToyControlAction.DualMotor -> {
            channelValues[0] = action.internalIntensity.coerceIn(0, status.intensityMax)
            channelValues[1] = action.externalIntensity.coerceIn(0, status.intensityMax)
            listOf(xhtkjStrengthCommand(writeUuid, channelValues[0], channelValues[1]))
        }
        ToyControlAction.Stop -> {
            channelValues.fill(0)
            listOf(
                xhtkjStrengthCommand(writeUuid, 0, 0),
                xhtkjClearCommand(writeUuid),
                xhtkjResetModelCommand(writeUuid),
            )
        }
    }

private fun updateQd1XhtkjFunction(
    action: ToyControlAction.Combined,
    status: BleProtocolStatus,
    channelValues: IntArray,
) {
    val functionCode = action.mode / 100
    val value = action.intensity.coerceIn(0, status.intensityMax)
    when (functionCode) {
        1 -> channelValues[0] = value
        2 -> channelValues[1] = value
        else -> {
            channelValues[0] = value
            channelValues[1] = value
        }
    }
}

private fun xhtkjStrengthCommand(writeUuid: Uuid, intensity: Int): BleProtocolOperation.Write =
    xhtkjStrengthCommand(writeUuid, intensity, intensity)

private fun xhtkjStrengthCommand(writeUuid: Uuid, primaryIntensity: Int, secondaryIntensity: Int): BleProtocolOperation.Write {
    val primary = xhtkjEncodeStrength(primaryIntensity)
    val secondary = xhtkjEncodeStrength(secondaryIntensity)
    val primaryLow = (primary and 0xff).toByte()
    val primaryHigh = ((primary ushr 8) and 0xff).toByte()
    val secondaryLow = (secondary and 0xff).toByte()
    val secondaryHigh = ((secondary ushr 8) and 0xff).toByte()
    return xhtkjWrite(
        writeUuid,
        byteArrayOf(
            0x03,
            0x12,
            0xf3.toByte(),
            0x00,
            0xfc.toByte(),
            0x00,
            0xfe.toByte(),
            0x40,
            0x01,
            primaryLow,
            primaryHigh,
            0x00,
            0xfc.toByte(),
            0x00,
            0xfe.toByte(),
            0x40,
            0x01,
            secondaryLow,
            secondaryHigh,
            0x00,
        ),
    )
}

private fun xhtkjModelCommand(writeUuid: Uuid, mode: Int, modeMax: Int): BleProtocolOperation.Write =
    xhtkjWrite(writeUuid, ByteArray(20).also {
        it[0] = 0x03
        it[1] = 0x12
        it[2] = mode.coerceIn(0, modeMax).toByte()
    })

private fun xhtkjClearCommand(writeUuid: Uuid): BleProtocolOperation.Write =
    xhtkjWrite(writeUuid, ByteArray(20).also {
        it[0] = 0x03
        it[1] = 0x12
        it[2] = 0xf0.toByte()
        it[3] = 0x07
    })

private fun xhtkjResetModelCommand(writeUuid: Uuid): BleProtocolOperation.Write =
    xhtkjWrite(writeUuid, ByteArray(20).also {
        it[0] = 0x03
        it[1] = 0x12
        it[2] = 0xf4.toByte()
    })

private fun xhtkjWrite(writeUuid: Uuid, bytes: ByteArray): BleProtocolOperation.Write =
    BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = bytes,
        withResponse = false,
    )

private fun xhtkjEncodeStrength(intensity: Int): Int {
    if (intensity <= 0) return 0x003c
    val scaled = (intensity / 100.0 * 0.7) + 0.3
    return (((scaled * 1023).toInt() shl 6) or 0x3c).coerceIn(0, 0xffff)
}

internal object XiuxiudaOfficialProtocol : BleDeviceProtocol {
    private val routes = listOf(
        XiuxiudaOfficialRoute(
            serviceUuid = Uuid.parse("53300001-0023-4bd4-bbd5-a6920e4c5653"),
            notifyUuid = Uuid.parse("53300002-0023-4bd4-bbd5-a6920e4c5653"),
            writeUuid = Uuid.parse("53300003-0023-4bd4-bbd5-a6920e4c5653"),
        ),
        XiuxiudaOfficialRoute(
            serviceUuid = Uuid.parse("00000001-0000-1000-8000-00805f9b34fb"),
            notifyUuid = Uuid.parse("00000002-0000-1000-8000-00805f9b34fb"),
            writeUuid = Uuid.parse("00000003-0000-1000-8000-00805f9b34fb"),
        ),
    )
    private val highPowerNames = setOf(
        "XXD-Lush12C",
        "XXD-Lush12D",
        "XXD-Lush12E",
        "XXD-Lush12L",
        "XXD-Lush12R",
        "XXD-Lush12S",
        "XXD-Lush12T",
        "XXD-Lush12V",
        "XXD-Lush141",
        "XXD-Lush142",
        "XXD-Lush143",
        "XXD-Lush144",
        "XXD-Lush145",
        "XXD-Lush146",
        "XXD-Lush149",
        "XXD-Lush14E",
    ).map { it.normalizedDeviceName() }.toSet()
    private val dualMotorPrefixes = setOf(
        "XXD-Lush123",
        "XXD-Lush124",
        "XXD-Lush125",
    ).map { it.normalizedDeviceName() }

    override val status = BleProtocolStatus(
        id = "xiuxiuda_official",
        displayName = "羞羞哒",
        controllable = true,
        intensityMax = 19,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        return name.startsWith("xxdlush") &&
                fingerprint.address.xiuxiudaAddressPrefix() != null &&
                findRoute(fingerprint) != null
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        initialize(
            fingerprint = fingerprint,
            route = requireNotNull(findRoute(fingerprint)) { "羞羞哒官方协议缺少可用通道" },
        )

    private fun initialize(
        fingerprint: BleGattFingerprint,
        route: XiuxiudaOfficialRoute,
    ): List<BleProtocolOperation> =
        buildList {
            if (fingerprint.characteristicUuids.contains(route.notifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(route.notifyUuid))
                add(write(fingerprint, route, command(0x65, 0x3D, 0x33, 0x01)))
                add(BleProtocolOperation.Sleep(300))
            }
            add(write(fingerprint, route, controlCommand(fingerprint.name, 0)))
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        Session(
            fingerprint = fingerprint,
            route = requireNotNull(findRoute(fingerprint)) { "羞羞哒官方协议缺少可用通道" },
        )

    private class Session(
        private val fingerprint: BleGattFingerprint,
        private val route: XiuxiudaOfficialRoute,
    ) : BleDeviceProtocol {
        override val status: BleProtocolStatus = XiuxiudaOfficialProtocol.statusFor(fingerprint.name)

        override fun matches(fingerprint: BleGattFingerprint): Boolean =
            XiuxiudaOfficialProtocol.matches(fingerprint)

        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            XiuxiudaOfficialProtocol.initialize(fingerprint, route)

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Intensity -> listOf(write(fingerprint, route, controlCommand(fingerprint.name, action.value)))
                is ToyControlAction.Combined -> listOf(write(fingerprint, route, controlCommand(fingerprint.name, action.intensity)))
                is ToyControlAction.DualMotor -> listOf(
                    write(
                        fingerprint,
                        route,
                        dualMotorCommand(
                            fingerprint.name,
                            suctionIntensity = action.internalIntensity,
                            vibrationIntensity = action.externalIntensity,
                        ),
                    ),
                )
                is ToyControlAction.Pattern -> listOf(write(fingerprint, route, controlCommand(fingerprint.name, status.intensityMax)))
                ToyControlAction.Stop -> listOf(write(fingerprint, route, controlCommand(fingerprint.name, 0)))
            }
    }

    private fun statusFor(name: String): BleProtocolStatus =
        if (name.isXiuxiudaDualMotor()) {
            status.copy(
                controlStyle = ToyControlStyle.DualIntensityOnly,
                channelNames = listOf("吸力", "震动"),
            )
        } else {
            status
        }

    private fun controlCommand(name: String, intensity: Int): IntArray {
        val normalizedName = name.normalizedDeviceName()
        val gear = intensity.coerceIn(0, status.intensityMax)
        return when {
            name.isXiuxiudaDualMotor() -> command(0x96, 0x3C, 0x31, gear)
            normalizedName in highPowerNames -> command(0x95, 0x3C, 0x30, gear)
            else -> command(0x65, 0x3A, 0x30, gear)
        }
    }

    private fun dualMotorCommand(
        name: String,
        suctionIntensity: Int,
        vibrationIntensity: Int,
    ): IntArray {
        if (!name.isXiuxiudaDualMotor()) {
            return controlCommand(name, suctionIntensity.coerceAtLeast(vibrationIntensity))
        }
        val suction = suctionIntensity.coerceIn(0, status.intensityMax)
        val vibration = vibrationIntensity.coerceIn(0, status.intensityMax)
        return when {
            suction > 0 && vibration > 0 -> command(0x96, 0x3C, 0x31, suction.coerceAtLeast(vibration))
            suction > 0 -> command(0x76, 0x3A, 0x31, suction)
            vibration > 0 -> command(0x86, 0x3B, 0x31, vibration)
            else -> command(0x96, 0x3C, 0x31, 0)
        }
    }

    private fun String.isXiuxiudaDualMotor(): Boolean {
        val normalizedName = normalizedDeviceName()
        return dualMotorPrefixes.any { normalizedName.startsWith(it) }
    }

    private fun write(
        fingerprint: BleGattFingerprint,
        route: XiuxiudaOfficialRoute,
        command: IntArray,
    ): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = route.writeUuid,
            bytes = fingerprint.address.xiuxiudaPacket(command),
            withResponse = false,
        )

    private fun command(logo: Int, category1: Int, category2: Int, gear: Int): IntArray =
        intArrayOf(logo, category1, category2, gear)

    private fun findRoute(fingerprint: BleGattFingerprint): XiuxiudaOfficialRoute? =
        routes.firstOrNull { route ->
            fingerprint.serviceUuids.contains(route.serviceUuid) &&
                    fingerprint.characteristicUuids.contains(route.writeUuid)
        }

    private data class XiuxiudaOfficialRoute(
        val serviceUuid: Uuid,
        val notifyUuid: Uuid,
        val writeUuid: Uuid,
    )
}

internal object SenseeCcpa10S2Protocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000fff0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000fff5-0000-1000-8000-00805f9b34fb")
    private val suctionCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf1, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf2, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf3, 0x00, 0x00),
    )
    private val vibrationCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0d, 0x12, 0x66, 0xf9, 0xf1),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0e, 0x12, 0x66, 0xf9, 0xf2),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0f, 0x12, 0x66, 0xf9, 0xf3),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x11, 0x12, 0x66, 0xf9, 0xfa),
    )
    private val suctionOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf0, 0x00, 0x00)
    private val vibrationOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x10, 0x12, 0x66, 0xf9, 0xf0)

    override val status = BleProtocolStatus(
        id = "sensee_ccpa10s2",
        displayName = "Sensee CCPA10S2",
        controllable = true,
        intensityMax = 3,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "功能",
        modeNames = listOf("吸力", "震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("ccpa") || name.contains("sensee")
        val hasSenseeGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return knownName && hasSenseeGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode, action.intensity))
            is ToyControlAction.Intensity -> listOf(command(2, action.value))
            is ToyControlAction.DualMotor -> listOf(command(action.mode, action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(write(suctionOff), write(vibrationOff))
        }

    private fun command(mode: Int, intensity: Int): BleProtocolOperation.Write {
        val normalizedMode = mode.coerceIn(1, status.modeMax)
        val level = intensity.coerceIn(0, status.intensityMax)
        val payload = when {
            level == 0 && normalizedMode == 1 -> suctionOff
            level == 0 -> vibrationOff
            normalizedMode == 1 -> suctionCommands[level - 1]
            else -> vibrationCommands[level - 1]
        }
        return write(payload)
    }

    private fun write(payload: ByteArray) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = payload,
        withResponse = false,
    )
}

internal object CachitoMbProGattProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val aliases = listOf("MB08", "MB Pro", "MBPro", "漫步者Pro", "漫步Pro")

    override val status = BleProtocolStatus(
        id = "cachito_mb_pro_gatt",
        displayName = "Cachito MB Pro",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "吮吸强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasKnownName = aliases.any { alias -> name.contains(alias.normalizedDeviceName()) }
        return hasKnownName &&
            fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        if (fingerprint.characteristicUuids.contains(notifyUuid)) {
            listOf(BleProtocolOperation.SubscribeNotify(notifyUuid))
        } else {
            emptyList()
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(command(action.mode.coerceIn(0, status.intensityMax))))
            is ToyControlAction.Intensity ->
                listOf(write(command(action.value)))
            is ToyControlAction.Combined ->
                listOf(write(command(action.intensity)))
            is ToyControlAction.DualMotor ->
                listOf(write(command(action.strongestIntensity(status.intensityMax))))
            ToyControlAction.Stop ->
                listOf(write(stopCommand()))
        }

    private fun command(intensity: Int): ByteArray {
        val scaled = (intensity.coerceIn(0, status.intensityMax) * 0.75 + 25).toInt()
        return commandPayload(scaled)
    }

    private fun stopCommand(): ByteArray =
        commandPayload(0)

    private fun commandPayload(level: Int): ByteArray {
        val commandId = Random.nextInt(0x64, 0x100)
        val bytes = bytes(
            0x71,
            0x00,
            0x06,
            commandId,
            0x00,
            0x00,
            0x04,
            0x31,
            0x03,
            0x02,
            level.coerceIn(0, 0xff),
            0x00,
            0x00,
            0x00,
            0x00,
        )
        return bytes + ((bytes.sumOf { it.toInt() and 0xff } and 0xff).toByte())
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

