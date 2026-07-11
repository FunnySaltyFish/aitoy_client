package com.funny.aitoy.ble

internal object LoyoProtocol : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = "loyo_gatt",
        displayName = "LOYO",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = LOYO_ELECTRIC_FREQUENCY_MAX,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "电流频率",
        intensityLabel = "电流强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.loyoProfile() != null && LoyoEncryptedFrameCodec.hasRoute(fingerprint)

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol {
        val profile = fingerprint.loyoProfile() ?: return this
        return Session(profile)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private class Session(
        private val profile: LoyoDeviceProfile,
    ) : BleDeviceProtocol {
        override val status = BleProtocolStatus(
            id = "loyo_${profile.code.lowercase()}",
            displayName = "LOYO ${profile.displayName}",
            controllable = true,
            intensityMax = 100,
            supportsMode = profile.motorModel == LoyoMotorModel.Electric,
            modeMax = LOYO_ELECTRIC_FREQUENCY_MAX,
            controlStyle = profile.motorModel.controlStyle,
            modeLabel = "电流频率",
            intensityLabel = profile.motorModel.intensityLabel,
            channelNames = profile.motorModel.channelNames,
            features = profile.motorModel.features,
            automatic = true,
        )

        override fun matches(fingerprint: BleGattFingerprint): Boolean =
            fingerprint.loyoProfile()?.code == profile.code && LoyoEncryptedFrameCodec.hasRoute(fingerprint)

        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            listOf(BleProtocolOperation.SubscribeNotify(LoyoEncryptedFrameCodec.notifyUuid))

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            when (profile.motorModel) {
                LoyoMotorModel.Electric -> electricCommands(action)
                LoyoMotorModel.Vibrate -> singleMotorCommands(action, command = 0x31)
                LoyoMotorModel.Suction -> singleMotorCommands(action, command = 0x32)
                LoyoMotorModel.VibrateAndSuction -> dualCommands(action)
                LoyoMotorModel.DualVibrate -> dualCommands(action)
                LoyoMotorModel.ElectricAndDualMotor -> electricAndDualCommands(action)
            }

        private fun electricCommands(action: ToyControlAction): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Pattern -> listOf(electric(action.mode, 50))
                is ToyControlAction.Intensity -> listOf(electric(frequency = 1, intensity = action.value))
                is ToyControlAction.Combined -> listOf(electric(action.mode, action.intensity))
                is ToyControlAction.DualMotor -> listOf(electric(action.mode, action.strongestIntensity(status.intensityMax)))
                ToyControlAction.Stop -> stopElectric()
            }

        private fun singleMotorCommands(
            action: ToyControlAction,
            command: Int,
        ): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Pattern -> listOf(motor(command, 50))
                is ToyControlAction.Intensity -> listOf(motor(command, action.value))
                is ToyControlAction.Combined -> listOf(motor(command, action.intensity))
                is ToyControlAction.DualMotor -> listOf(motor(command, action.strongestIntensity(status.intensityMax)))
                ToyControlAction.Stop -> listOf(motor(command, 0))
            }

        private fun dualCommands(action: ToyControlAction): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Pattern -> listOf(dual(50, 50))
                is ToyControlAction.Intensity -> listOf(dual(action.value, action.value))
                is ToyControlAction.Combined -> {
                    val first = if (action.mode == 2) 0 else action.intensity
                    val second = if (action.mode == 1) 0 else action.intensity
                    listOf(dual(first, second))
                }
                is ToyControlAction.DualMotor -> listOf(dual(action.internalIntensity, action.externalIntensity))
                ToyControlAction.Stop -> listOf(dual(0, 0))
            }

        private fun electricAndDualCommands(action: ToyControlAction): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Pattern -> listOf(electric(action.mode, 50))
                is ToyControlAction.Intensity -> listOf(electric(frequency = 1, intensity = action.value))
                is ToyControlAction.Combined -> listOf(electric(action.mode, action.intensity))
                is ToyControlAction.DualMotor -> listOf(dual(action.internalIntensity, action.externalIntensity))
                ToyControlAction.Stop -> listOf(dual(0, 0), BleProtocolOperation.Sleep(100), electric(0, 0))
            }

        private fun stopElectric(): List<BleProtocolOperation> =
            listOf(
                motor(0x31, 0),
                BleProtocolOperation.Sleep(100),
                electric(0, 0),
            )

        private fun motor(command: Int, intensity: Int): BleProtocolOperation.Write =
            LoyoEncryptedFrameCodec.command(command, intensity.coerceIn(0, 100))

        private fun dual(first: Int, second: Int): BleProtocolOperation.Write =
            LoyoEncryptedFrameCodec.command(0x40, first.coerceIn(0, 100), second.coerceIn(0, 100))

        private fun electric(frequency: Int, intensity: Int): BleProtocolOperation.Write =
            LoyoEncryptedFrameCodec.encryptedWrite(
                LoyoEncryptedFrameCodec.electricPayload(
                    frequency = frequency.coerceIn(0, LOYO_ELECTRIC_FREQUENCY_MAX),
                    intensity = intensity.coerceIn(0, 100),
                )
            )
    }
}

internal fun BleGattFingerprint.loyoProfile(): LoyoDeviceProfile? {
    val normalizedName = name.normalizedDeviceName()
    return LOYO_DEVICE_PROFILES.firstOrNull { profile ->
        normalizedName == profile.code.normalizedDeviceName()
    }
}

internal data class LoyoDeviceProfile(
    val code: String,
    val displayName: String,
    val motorModel: LoyoMotorModel,
)

internal enum class LoyoMotorModel(
    val controlStyle: ToyControlStyle,
    val intensityLabel: String,
    val channelNames: List<String> = emptyList(),
    val features: List<BleProtocolFeature> = emptyList(),
) {
    Electric(
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        intensityLabel = "电流强度",
        features = listOf(BleProtocolFeature(type = "shock", min = 0, max = 100, index = 0, label = "电流")),
    ),
    Vibrate(
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "震动强度",
        channelNames = listOf("震动"),
        features = listOf(BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 0, label = "震动")),
    ),
    Suction(
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "吮吸强度",
        channelNames = listOf("吮吸"),
        features = listOf(BleProtocolFeature(type = "constrict", min = 0, max = 100, index = 0, label = "吮吸")),
    ),
    VibrateAndSuction(
        controlStyle = ToyControlStyle.DualIntensityOnly,
        intensityLabel = "强度",
        channelNames = listOf("震动", "吮吸"),
        features = listOf(
            BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 0, label = "震动"),
            BleProtocolFeature(type = "constrict", min = 0, max = 100, index = 1, label = "吮吸"),
        ),
    ),
    DualVibrate(
        controlStyle = ToyControlStyle.DualIntensityOnly,
        intensityLabel = "强度",
        channelNames = listOf("外震", "内震"),
        features = listOf(
            BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 0, label = "外震"),
            BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 1, label = "内震"),
        ),
    ),
    ElectricAndDualMotor(
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        intensityLabel = "电流强度",
        features = listOf(BleProtocolFeature(type = "shock", min = 0, max = 100, index = 0, label = "电流")),
    ),
}

internal const val LOYO_ELECTRIC_FREQUENCY_MAX = 8

internal val LOYO_DEVICE_PROFILES = listOf(
    LoyoDeviceProfile("DJ01", "电击精灵", LoyoMotorModel.Electric),
    LoyoDeviceProfile("PKQ1", "一颗青杏-萌奇", LoyoMotorModel.Electric),
    LoyoDeviceProfile("DJ02", "一颗青杏-电击精灵", LoyoMotorModel.Electric),
    LoyoDeviceProfile("DJXM", "电击小萌", LoyoMotorModel.Electric),
    LoyoDeviceProfile("XMJR", "电击小萌2代", LoyoMotorModel.Electric),
    LoyoDeviceProfile("MA13", "云里", LoyoMotorModel.Electric),
    LoyoDeviceProfile("XW49", "千羽", LoyoMotorModel.Electric),
    LoyoDeviceProfile("WMBN", "小香蕉", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("MQH8", "小萌", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("MQH9", "小萌", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("MQH6", "暖小萌", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("KYLS", "小猫腻", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("YM57", "初芽", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("WMTD", "望悦跳蛋", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("WY01", "小洛莉", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("YM23", "小波蛋", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("KY01", "觅吻", LoyoMotorModel.Vibrate),
    LoyoDeviceProfile("WMXB", "小白", LoyoMotorModel.Suction),
    LoyoDeviceProfile("XW44", "LOYO S30", LoyoMotorModel.Suction),
    LoyoDeviceProfile("KYEJ", "猫爪小耳机", LoyoMotorModel.Suction),
    LoyoDeviceProfile("WMTY", "天羿", LoyoMotorModel.VibrateAndSuction),
    LoyoDeviceProfile("KYSX", "白鹿鸣", LoyoMotorModel.VibrateAndSuction),
    LoyoDeviceProfile("KYZS", "战神", LoyoMotorModel.DualVibrate),
    LoyoDeviceProfile("KYYM", "小洛神", LoyoMotorModel.DualVibrate),
    LoyoDeviceProfile("XW54", "射手", LoyoMotorModel.DualVibrate),
    LoyoDeviceProfile("MA17", "雪婵", LoyoMotorModel.DualVibrate),
    LoyoDeviceProfile("KYMA", "雷震芷", LoyoMotorModel.ElectricAndDualMotor),
    LoyoDeviceProfile("XW51", "偷潮", LoyoMotorModel.ElectricAndDualMotor),
)
