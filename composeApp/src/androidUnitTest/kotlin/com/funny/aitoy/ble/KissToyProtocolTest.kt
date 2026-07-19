package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class KissToyProtocolTest {
    @Test
    fun existingKissToyProtocolKeepsHistoricalControlShape() {
        // 非迷路历史设备（JY11）保持原有无脉冲控制形状：清零其它通道 + 写目标通道 = 3 帧。
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
    fun miluKissToyGattColdStartInsertsKickStartPulse() {
        // 迷路系列（QCPW）从静止启动到低强度：补满档脉冲 + Sleep + 目标帧，克服马达静摩擦。
        val protocol = BleProtocolRegistry.resolveNative(kissToyFingerprint(name = "QCPW"))
            ?: error("KissToy 迷路 protocol not resolved")
        assertEquals("kisstoy_gatt", protocol.status.id)

        // mode=1 主电机从 0 拉到 15（< 阈值 50）：clear 2 帧 + 脉冲 100 + Sleep + 目标 15 = 5 帧。
        val coldStart = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 15))
        assertEquals(5, coldStart.size)
        assertIs<BleProtocolOperation.Write>(coldStart[0])
        assertIs<BleProtocolOperation.Write>(coldStart[1])
        val pulse = assertIs<BleProtocolOperation.Write>(coldStart[2])
        assertEquals(KISSTOY_WRITE_UUID, pulse.characteristicUuid)
        assertEquals(kissToyHistoricalCommand(0x31, 100).bytes.toList(), pulse.bytes.toList())
        assertEquals(180L, assertIs<BleProtocolOperation.Sleep>(coldStart[3]).millis)
        val target = assertIs<BleProtocolOperation.Write>(coldStart[4])
        assertEquals(kissToyHistoricalCommand(0x31, 15).bytes.toList(), target.bytes.toList())
    }

    @Test
    fun miluKissToyGattSkipsPulseWhenAlreadyRunningOrHighTarget() {
        val protocol = BleProtocolRegistry.resolveNative(kissToyFingerprint(name = "QCPW"))
            ?: error("KissToy 迷路 protocol not resolved")

        // 目标 >= 阈值 50：不插脉冲，clear 2 帧 + 目标 1 帧 = 3。
        val highTarget = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 80))
        assertEquals(3, highTarget.size)

        // 马达已在转（上一步 80），再微调到 15：不插脉冲，仍为 3 帧。
        val fineTune = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 15))
        assertEquals(3, fineTune.size)

        // 停止后回到静止，再从 0 拉到 15：重新踢转，5 帧含脉冲。
        protocol.commandsFor(ToyControlAction.Stop)
        val restart = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 15))
        assertEquals(5, restart.size)
        assertIs<BleProtocolOperation.Sleep>(restart[3])
    }

    @Test
    fun tutuIIUsesChallengeAuthAndBtsnoopMotorFrame() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint()) ?: error("KissToy Tutu II protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("底座伸缩", "头部伸缩"), protocol.status.channelNames)

        // 订阅 AE3C 后等待挑战，认证完成才就绪（对齐真机 btsnoop 会话流程）。
        val init = protocol.initialize(tutuFingerprint())
        assertIs<BleProtocolOperation.SubscribeNotify>(init[0])
        assertIs<BleProtocolOperation.WaitForProtocolReady>(init[1])
        assertFalse(protocol.isProtocolReady())

        // 认证响应 = 58 + XOR(challenge[1:13], K_FIXED)。挑战全零 payload 时响应即 58 + K_FIXED。
        val auth = protocol.onNotify(TUTU_NOTIFY_UUID, "58000000000000000000000000".hexBytes()).first()
        val authWrite = assertIs<BleProtocolOperation.Write>(auth)
        assertEquals(TUTU_WRITE_UUID, authWrite.characteristicUuid)
        assertFalse(authWrite.withResponse)
        assertEquals("58EA30BBDBADB6C62FCFE9E996", authWrite.bytes.hexUpper())
        assertTrue(protocol.isProtocolReady())

        // 电机帧：明文 15 31 b9 00 <M1> <M2> 00 d0 00 16 16 69，整包 XOR K_FIXED 加 0x58 头。
        // DualMotor 强度 0..100 线性映射到 0..255；M1=头部(external)、M2=底座(internal)。
        // 算法与 spec btsnoop Max 示例一致（明文 …24 52 49 17… → 密文 …ff ff ff d1…）。
        val full = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 100, externalIntensity = 100))
        val fullWrite = assertIs<BleProtocolOperation.Write>(full.single())
        assertEquals(TUTU_WRITE_UUID, fullWrite.characteristicUuid)
        assertFalse(fullWrite.withResponse)
        assertEquals("58FF0102DB5249C6FFCFFFFFFF", fullWrite.bytes.hexUpper())

        assertEquals(500L, protocol.keepaliveIntervalMs())

        // 停止：M1=M2=0，明文 15 31 b9 00 00 00 00 d0 00 16 16 69。
        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals(TUTU_WRITE_UUID, stopWrite.characteristicUuid)
        assertEquals("58FF0102DBADB6C6FFCFFFFFFF", stopWrite.bytes.hexUpper())
    }

    @Test
    fun qcttWithoutOfficialAe3aRouteDoesNotFallBackToHistoricalKissToy() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttHistoricalKissToyFingerprint())

        assertEquals(
            emptyList(),
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

        assertEquals("kisstoy_official_v2", protocol.status.id)
        assertEquals("KissToy 迷路-入体版", protocol.status.displayName)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("震动", "吮吸"), protocol.status.channelNames)
        assertEquals(emptyList(), protocol.initialize(officialV2Fingerprint("QCPW")))

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(run, DDDD_WRITE_UUID).bytes,
            content = listOf(153, 0, 102, 0, 0),
        )

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertKissToyBasicControlWrites(
            stop,
            DDDD_WRITE_UUID,
            contents = listOf(
                listOf(0, 0, 102, 0, 0),
                listOf(0, 0, 0, 0, 0),
            ),
        )

        val freshProtocol = BleProtocolRegistry.resolveNative(officialV2Fingerprint("QCPW")) ?: error("QCPW protocol not resolved")
        val primaryOnly = freshProtocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 64, externalIntensity = 0))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(primaryOnly, DDDD_WRITE_UUID).bytes,
            content = listOf(181, 0, 0, 0, 0),
        )
    }

    @Test
    fun qcpwFfe0RouteUsesOfficialV2LostProfile() {
        val fingerprint = officialV2Fingerprint(
            name = "QCPW",
            serviceUuid = FFE0_SERVICE_UUID,
            writeUuid = FFE0_WRITE_UUID,
            notifyUuid = FFE0_NOTIFY_UUID,
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("QCPW FFE0 protocol not resolved")

        assertEquals("kisstoy_official_v2", protocol.status.id)
        assertEquals("KissToy 迷路-入体版", protocol.status.displayName)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(run, FFE0_WRITE_UUID).bytes,
            content = listOf(153, 0, 102, 0, 0),
        )
    }

    @Test
    fun qckhBoboUsesSingleSuckingMotorSlider() {
        val protocol = BleProtocolRegistry.resolveNative(officialV2Fingerprint("QCKH")) ?: error("QCKH protocol not resolved")

        assertEquals("kisstoy_official_v2", protocol.status.id)
        assertEquals("KissToy BOBO", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals(100, protocol.status.intensityMax)
        assertEquals("强度", protocol.status.intensityLabel)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
        assertFalse(protocol.status.supportsMode)

        val low = protocol.commandsFor(ToyControlAction.Intensity(25)).single()
        assertKissToyBasicControlData(
            assertIs<BleProtocolOperation.Write>(low).bytes,
            content = listOf(0, 0, 102, 0, 0),
        )
        val medium = protocol.commandsFor(ToyControlAction.Intensity(50)).single()
        assertKissToyBasicControlData(
            assertIs<BleProtocolOperation.Write>(medium).bytes,
            content = listOf(0, 0, 153, 0, 0),
        )
        val high = protocol.commandsFor(ToyControlAction.Intensity(100)).single()
        assertKissToyBasicControlData(
            assertIs<BleProtocolOperation.Write>(high).bytes,
            content = listOf(0, 0, 255, 0, 0),
        )
    }

    @Test
    fun qcbbt25KissSugarUsesOfficialV2SingleSuckingRoute() {
        val fingerprint = qcbbt25LiveAe3aFingerprint()
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("QCBBT25 protocol not resolved")

        assertEquals("kisstoy_official_v2", protocol.status.id)
        assertEquals("KissToy 嘟嘟糖", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
        assertFalse(protocol.status.supportsMode)

        val init = protocol.initialize(fingerprint)
        assertEquals(9, init.size)
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        val macRequest = assertIs<BleProtocolOperation.Write>(init[1])
        assertEquals(AE3A_WRITE_UUID, macRequest.characteristicUuid)
        assertFalse(macRequest.withResponse)
        assertKissToyAesCcmPacket(
            macRequest.bytes,
            cmdId = 0x00,
            subId = 0x02,
            plaintext = emptyList(),
        )
        assertEquals(500L, assertIs<BleProtocolOperation.Sleep>(init[2]).millis)
        assertKissToyAesCcmDeviceInfoInit(init[3], subId = 0x00)
        assertEquals(200L, assertIs<BleProtocolOperation.Sleep>(init[4]).millis)
        assertKissToyAesCcmDeviceInfoInit(init[5], subId = 0xf1)
        assertEquals(200L, assertIs<BleProtocolOperation.Sleep>(init[6]).millis)
        assertKissToyAesCcmDeviceInfoInit(init[7], subId = 0xf2)
        assertEquals(200L, assertIs<BleProtocolOperation.Sleep>(init[8]).millis)

        val run = protocol.commandsFor(ToyControlAction.Intensity(50))
        val write = assertSingleKissToyAesCcmBasicControlWrite(run, AE3A_WRITE_UUID)
        assertKissToyAesCcmBasicControlData(
            write.bytes,
            content = listOf(0, 0, 153, 0, 0),
        )

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertKissToyAesCcmBasicControlWrites(
            stop,
            AE3A_WRITE_UUID,
            contents = listOf(listOf(0, 0, 0, 0, 0)),
        )
    }

    @Test
    fun kissToyAesCcmMatchesStandardCcmVector() {
        val encrypted = kissToyAesCcmEncrypt(
            key = KISS_TOY_AES_CCM_KEY,
            nonce = bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff),
            plaintext = bytes(0x00, 0x00, 0x99, 0x00, 0x00),
            tagSize = 8,
        )

        assertEquals("c091a2daf02ba2cd0dfdc9be02", encrypted.toHexForTest())
    }

    @Test
    fun kissToyAesCcmNonceMatchesOfficialTimestampShape() {
        val nonce = kissToyAesCcmNonce(timestampMs = 0x0102030405060708L)

        assertEquals("030405060708ffffffffffff", nonce.toHexForTest())
    }

    @Test
    fun kissToyAesCcmGetDeviceMacAddressUsesOfficialInitShape() {
        val packet = kissToyAesCcmGetDeviceMacAddressPacket(
            nonce = bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff),
        )

        assertKissToyAesCcmPacket(
            packet,
            cmdId = 0x00,
            subId = 0x02,
            plaintext = emptyList(),
        )
    }

    @Test
    fun qcpwLiveAe3aGattSummaryPrefersOfficialV2BeforeHistoricalRoute() {
        val fingerprint = qcpwLiveAe3aFingerprint()
        val protocols = BleProtocolRegistry.resolveNativeAll(fingerprint)

        assertEquals("kisstoy_official_v2", protocols.first().status.id)
        assertEquals("KissToy 迷路-入体版", protocols.first().status.displayName)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocols.first().status.controlStyle)
        assertEquals(listOf("震动", "吮吸"), protocols.first().status.channelNames)

        val init = protocols.first().initialize(fingerprint)
        assertEquals(1, init.size)
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)

        val run = protocols.first().commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(run, AE3A_WRITE_UUID).bytes,
            content = listOf(153, 0, 102, 0, 0),
        )
    }

    @Test
    fun qcpwAe3aRouteWinsOverAe00PreferredTemplate() {
        val fingerprint = qcpwLiveAe3aFingerprint().copy(
            preferredServiceUuid = AE00_SERVICE_UUID,
            preferredWriteUuid = AE00_WRITE_UUID,
            preferredNotifyUuid = AE00_NOTIFY_UUID,
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("QCPW AE3A protocol not resolved")

        assertEquals("kisstoy_official_v2", protocol.status.id)

        val init = protocol.initialize(fingerprint)
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(run, AE3A_WRITE_UUID).bytes,
            content = listOf(153, 0, 102, 0, 0),
        )
    }

    @Test
    fun qcpwHistoricalKissToyRouteDoesNotUseOfficialV2() {
        val protocols = BleProtocolRegistry.resolveNativeAll(
            officialV2Fingerprint(
                name = "QCPW",
                serviceUuid = KISSTOY_SERVICE_UUID,
                writeUuid = KISSTOY_WRITE_UUID,
                notifyUuid = KISSTOY_NOTIFY_UUID,
            )
        )

        assertEquals(
            listOf("kisstoy_gatt"),
            protocols.map { it.status.id },
        )
    }

    @Test
    fun qckhHistoricalKissToyRouteKeepsOldFourModeProtocol() {
        val protocol = BleProtocolRegistry.resolveNative(
            officialV2Fingerprint(
                name = "QCKH",
                serviceUuid = KISSTOY_SERVICE_UUID,
                writeUuid = KISSTOY_WRITE_UUID,
                notifyUuid = KISSTOY_NOTIFY_UUID,
            )
        ) ?: error("QCKH historical protocol not resolved")

        assertEquals("kisstoy_gatt", protocol.status.id)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals(listOf("主电机", "第二电机", "抽插", "双通道"), protocol.status.modeNames)

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 2, intensity = 50))
        assertEquals(3, suction.size)
        suction.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(KISSTOY_WRITE_UUID, write.characteristicUuid)
            assertTrue(write.withResponse)
        }

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals(4, stop.size)
        stop.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(KISSTOY_WRITE_UUID, write.characteristicUuid)
            assertTrue(write.withResponse)
        }
    }

    @Test
    fun jiuaiJellyfishUsesWxMiniProgramAe3aPlainCommands() {
        val fingerprint = jiuaiJellyfishFingerprint()
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Jiuai jellyfish protocol not resolved")

        assertEquals("jiuai_jellyfish_stroker", protocol.status.id)
        assertEquals("久爱水母跳蛋", protocol.status.displayName)
        assertEquals(ToyControlStyle.ExclusivePatternOrIntensity, protocol.status.controlStyle)
        assertEquals("伸缩模式", protocol.status.modeLabel)
        assertEquals("伸缩速度", protocol.status.intensityLabel)
        assertEquals(listOf("微风", "心跳", "冲击", "拍打", "深度", "宇宙", "海啸", "瀑布", "火山", "神风"), protocol.status.modeNames)

        val init = protocol.initialize(fingerprint)
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals("AAAAAAAAAA", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())

        val mode1 = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Pattern(1)).single())
        assertEquals(AE3A_WRITE_UUID, mode1.characteristicUuid)
        assertFalse(mode1.withResponse)
        assertEquals("AA0102AD", mode1.bytes.hexUpper())

        val mode10 = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Pattern(10)).single())
        assertEquals("AA0111BC", mode10.bytes.hexUpper())

        val speedMin = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Intensity(1)).single())
        assertEquals("AA021223E1", speedMin.bytes.hexUpper())
        val speedMax = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Intensity(100)).single())
        assertEquals("AA02126422", speedMax.bytes.hexUpper())

        val stop = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Stop).single())
        assertEquals("AA0100AB", stop.bytes.hexUpper())
    }

    @Test
    fun jiuaiJellyfishDoesNotMatchNameAloneOrOtherAe3aDevices() {
        assertEquals(null, BleProtocolRegistry.resolveNative(jiuaiJellyfishFingerprint().copy(serviceUuids = emptySet())))

        val qcpwProtocols = BleProtocolRegistry.resolveNativeAll(qcpwLiveAe3aFingerprint())
        assertEquals("kisstoy_official_v2", qcpwProtocols.first().status.id)
        assertTrue(qcpwProtocols.none { it.status.id == "jiuai_jellyfish_stroker" })
    }

    @Test
    fun qcvwDdddRouteUsesSecondVibrateCommand() {
        val protocol = BleProtocolRegistry.resolveNative(officialV2Fingerprint("QCVW")) ?: error("QCVW protocol not resolved")

        assertEquals("KissToy 迷路-震动版", protocol.status.displayName)
        assertEquals(listOf("震动", "第二震动"), protocol.status.channelNames)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 25))
        assertKissToyBasicControlData(
            assertSingleKissToyBasicControlWrite(run, DDDD_WRITE_UUID).bytes,
            content = listOf(153, 0, 102, 0, 0),
        )
    }

    @Test
    fun qcttLiveGattSummaryUsesOfficialAe3aTutuRouteOnly() {
        val protocols = BleProtocolRegistry.resolveNativeAll(qcttLiveAe3aFingerprint())

        assertEquals(
            listOf("kisstoy_tutu2"),
            protocols.map { it.status.id },
        )

        val route = protocols.first()
        val init = route.initialize(qcttLiveAe3aFingerprint())
        assertEquals(2, init.size)
        assertEquals(AE3A_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertIs<BleProtocolOperation.WaitForProtocolReady>(init[1])
        assertFalse(route.isProtocolReady())

        // 收到挑战后回认证帧到 AE3B，认证完成才就绪。
        val auth = route.onNotify(AE3A_NOTIFY_UUID, "58000102030405060708090a0b".hexBytes()).first()
        assertEquals(AE3A_WRITE_UUID, assertIs<BleProtocolOperation.Write>(auth).characteristicUuid)
        assertTrue(route.isProtocolReady())
        val run = route.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 50))
        assertEquals(AE3A_WRITE_UUID, assertIs<BleProtocolOperation.Write>(run[0]).characteristicUuid)
    }

    @Test
    fun qcttNameAloneDoesNotSelectTutuRoute() {
        val protocol = BleProtocolRegistry.resolveNative(qcttNameOnlyFingerprint())

        assertEquals(null, protocol)
    }

    @Test
    fun cachitoMb08UsesOfficialFfe0GattRoute() {
        val fingerprint = cachitoMb08Fingerprint()
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Cachito MB08 protocol not resolved")

        assertEquals("cachito_mb_pro_gatt", protocol.status.id)
        assertEquals("Cachito MB Pro", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals("吮吸强度", protocol.status.intensityLabel)

        val init = protocol.initialize(fingerprint)
        assertEquals(1, init.size)
        assertEquals(FFE0_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)

        val run = protocol.commandsFor(ToyControlAction.Intensity(70))
        val write = assertIs<BleProtocolOperation.Write>(run.single())
        assertEquals(FFE0_WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals(16, write.bytes.size)
        assertEquals("710006", write.bytes.copyOfRange(0, 3).hexUpper())
        assertEquals("0000043103024D00000000", write.bytes.copyOfRange(4, 15).hexUpper())
        assertEquals(
            write.bytes.take(15).sumOf { it.toInt() and 0xff } and 0xff,
            write.bytes.last().toInt() and 0xff,
        )

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals(FFE0_WRITE_UUID, stopWrite.characteristicUuid)
        assertEquals("0000043103020000000000", stopWrite.bytes.copyOfRange(4, 15).hexUpper())
        assertEquals(
            stopWrite.bytes.take(15).sumOf { it.toInt() and 0xff } and 0xff,
            stopWrite.bytes.last().toInt() and 0xff,
        )

        val broadcastMatches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "MB08-V30-469C",
                address = "EF:32:DF:D8:46:9C",
                rssi = -45,
                connectable = true,
            ),
        )
        assertTrue(broadcastMatches.none { it.status.id == "cachito_mb_pro_advertise" })
    }

    @Test
    fun cachitoTouhuanMiniTraceUsesFed5GattRoute() {
        val fingerprint = cachitoTouhuanMiniFingerprint()
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Cachito 偷欢 Mini protocol not resolved")

        assertEquals("cachito_touhuan_mini_gatt", protocol.status.id)
        assertEquals("Cachito 偷欢 Mini", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals("吮吸强度", protocol.status.intensityLabel)

        val init = protocol.initialize(fingerprint)
        assertEquals(1, init.size)
        assertEquals(FED6_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init.single()).characteristicUuid)

        val run = protocol.commandsFor(ToyControlAction.Intensity(70))
        val write = assertIs<BleProtocolOperation.Write>(run.single())
        assertEquals(FED5_WRITE_UUID, write.characteristicUuid)
        assertTrue(write.withResponse)
        assertEquals(16, write.bytes.size)
        assertEquals("71000A", write.bytes.copyOfRange(0, 3).hexUpper())
        assertEquals("8200000001006400005502", write.bytes.copyOfRange(4, 15).hexUpper())
        assertEquals(
            write.bytes.take(15).sumOf { it.toInt() and 0xff } and 0xff,
            write.bytes.last().toInt() and 0xff,
        )

        val stopWrite = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Stop).single())
        assertEquals(FED5_WRITE_UUID, stopWrite.characteristicUuid)
        assertTrue(stopWrite.withResponse)
        assertEquals("0F00000001000000000000", stopWrite.bytes.copyOfRange(4, 15).hexUpper())

        val zeroWrite = assertIs<BleProtocolOperation.Write>(protocol.commandsFor(ToyControlAction.Intensity(0)).single())
        assertEquals("0F00000001000000000000", zeroWrite.bytes.copyOfRange(4, 15).hexUpper())
    }

    @Test
    fun cachitoToHuanConnectableScanUsesTouhuanBroadcastRoute() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "ToHuan",
                address = "C6:B6:E8:1D:81:2B",
                rssi = -52,
                manufacturerData = "0x5357:38 33 30 30",
                connectable = true,
            ),
        )

        assertEquals(listOf("cachito_touhuan_advertise"), matches.map { it.status.id })
        assertEquals("Cachito 套环", matches.first().status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, matches.first().status.controlStyle)
        assertFalse(matches.first().preferGattWhenConnectable)
    }

    @Test
    fun unnamedManufacturerOnlyBroadcastDoesNotMatchShikong3() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "",
                address = "09:66:F7:99:E6:D8",
                rssi = -55,
                manufacturerData = "0x6:01 09 20 22 9C 99 BA B2 D3 9B 8C 44 9D A4 FA 2D 3B 73 2C 83 48 9A E5 93 55 94 1A",
                connectable = false,
            ),
        )

        assertEquals(emptyList(), matches.map { it.status.id })
    }

    @Test
    fun shikong3ExplicitNameStillMatchesBroadcastRoute() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "Cachito 失控 3.0",
                address = "09:66:F7:99:E6:D8",
                rssi = -55,
                connectable = false,
            ),
        )

        assertEquals("cachito_shikong3_advertise", matches.first().status.id)
        assertTrue(matches.any { it.status.id == "cachito_shikong2_advertise" })
        assertTrue(matches.any { it.status.id == "cachito_shikong25_advertise" })
        assertTrue(matches.any { it.status.id == "cachito_shikong4_advertise" })
    }

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun kissToyFingerprint(name: String = "JY11") = BleGattFingerprint(
        name = name,
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

    private fun officialV2Fingerprint(
        name: String,
        serviceUuid: Uuid = DDDD_SERVICE_UUID,
        writeUuid: Uuid = DDDD_WRITE_UUID,
        notifyUuid: Uuid = DDDD_NOTIFY_UUID,
    ) = BleGattFingerprint(
        name = name,
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(serviceUuid),
        characteristicUuids = setOf(writeUuid, notifyUuid),
        preferredServiceUuid = serviceUuid,
        preferredWriteUuid = writeUuid,
        preferredNotifyUuid = notifyUuid,
    )

    private fun qcttLiveAe3aFingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID, AE3A_SERVICE_UUID),
        characteristicUuids = setOf(
            KISSTOY_WRITE_UUID,
            KISSTOY_NOTIFY_UUID,
            AE3A_WRITE_UUID,
            AE3A_NOTIFY_UUID,
        ),
    )

    private fun qcttNameOnlyFingerprint() = BleGattFingerprint(
        name = "QCTT",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = emptySet(),
        characteristicUuids = emptySet(),
    )

    private fun qcpwLiveAe3aFingerprint() = BleGattFingerprint(
        name = "QCPW",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B, 0x5756:E9 AD FE FC F7 35",
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

    private fun qcbbt25LiveAe3aFingerprint() = BleGattFingerprint(
        name = "QCBBT25",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B, 0xffff:60 00 F1 01 FF FF FF FF FF FF 01 01 64",
        scanRecordHex = "",
        serviceUuids = setOf(AE3A_SERVICE_UUID, AE00_SERVICE_UUID),
        characteristicUuids = setOf(
            AE3A_WRITE_UUID,
            AE3A_NOTIFY_UUID,
            AE00_WRITE_UUID,
            AE00_NOTIFY_UUID,
        ),
    )

    private fun jiuaiJellyfishFingerprint() = BleGattFingerprint(
        name = "YCM-BL001",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(AE3A_SERVICE_UUID),
        characteristicUuids = setOf(AE3A_WRITE_UUID, AE3A_NOTIFY_UUID),
    )

    private fun cachitoMb08Fingerprint() = BleGattFingerprint(
        name = "MB08-V30-469C",
        manufacturerData = "0x5d6:08 00 4A 4C 41 49 53 44 4B",
        scanRecordHex = "",
        serviceUuids = setOf(FFE0_SERVICE_UUID, AE3A_SERVICE_UUID, AE00_SERVICE_UUID),
        characteristicUuids = setOf(
            FFE0_WRITE_UUID,
            FFE0_NOTIFY_UUID,
            AE3A_WRITE_UUID,
            AE3A_NOTIFY_UUID,
            AE00_WRITE_UUID,
            AE00_NOTIFY_UUID,
        ),
    )

    private fun cachitoTouhuanMiniFingerprint() = BleGattFingerprint(
        name = "SN-9AQ1TR02789",
        address = "C1:19:32:00:03:D0",
        manufacturerData = "0x504:C1 19 32 00 03 D0",
        scanRecordHex = "02 01 05 03 02 B0 FF 09 FF 04 05 C1 19 32 00 03 D0 0F 09 53 4E 2D 39 41 51 31 54 52 30 32 37 38 39 02 0A 00",
        serviceUuids = setOf(
            FFB0_SERVICE_UUID,
            FEB3_SERVICE_UUID,
        ),
        characteristicUuids = setOf(
            FED4_READ_UUID,
            FED5_WRITE_UUID,
            FED6_NOTIFY_UUID,
            FED7_UUID,
            FED8_UUID,
            FFB2_UUID,
        ),
    )

    private fun assertSingleKissToyBasicControlWrite(
        operations: List<BleProtocolOperation>,
        writeUuid: Uuid,
        header: Int = 0x58,
    ): BleProtocolOperation.Write {
        assertEquals(1, operations.size)
        val write = assertIs<BleProtocolOperation.Write>(operations.single())
        assertEquals(writeUuid, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals(13, write.bytes.size)
        assertEquals(header, write.bytes[0].toInt() and 0xff)
        return write
    }

    private fun assertSingleKissToyAesCcmBasicControlWrite(
        operations: List<BleProtocolOperation>,
        writeUuid: Uuid,
    ): BleProtocolOperation.Write {
        assertEquals(1, operations.size)
        val write = assertIs<BleProtocolOperation.Write>(operations.single())
        assertEquals(writeUuid, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals(29, write.bytes.size)
        assertEquals(listOf(0x60, 0x02, 0x01, 0x19), write.bytes.take(4).map { it.toInt() and 0xff })
        return write
    }

    private fun assertKissToyAesCcmDeviceInfoInit(
        operation: BleProtocolOperation,
        subId: Int,
    ) {
        val write = assertIs<BleProtocolOperation.Write>(operation)
        assertEquals(AE3A_WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertKissToyAesCcmPacket(
            packet = write.bytes,
            cmdId = 0x00,
            subId = subId,
            plaintext = emptyList(),
        )
    }

    private fun assertKissToyBasicControlWrites(
        operations: List<BleProtocolOperation>,
        writeUuid: Uuid,
        contents: List<List<Int>>,
        header: Int = 0x58,
    ) {
        assertEquals(contents.size, operations.size)
        operations.zip(contents).forEach { (operation, content) ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(writeUuid, write.characteristicUuid)
            assertFalse(write.withResponse)
            assertEquals(13, write.bytes.size)
            assertKissToyBasicControlData(write.bytes, content, header)
        }
    }

    private fun assertKissToyAesCcmBasicControlWrites(
        operations: List<BleProtocolOperation>,
        writeUuid: Uuid,
        contents: List<List<Int>>,
    ) {
        assertEquals(contents.size, operations.size)
        operations.zip(contents).forEach { (operation, content) ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(writeUuid, write.characteristicUuid)
            assertFalse(write.withResponse)
            assertEquals(29, write.bytes.size)
            assertKissToyAesCcmBasicControlData(write.bytes, content)
        }
    }

    private fun assertKissToyBasicControlData(packet: ByteArray, content: List<Int>, header: Int = 0x58) {
        assertEquals(5, content.size)
        assertEquals(header, packet[0].toInt() and 0xff)
        val decrypted = decryptKissToyBleEncryption(packet.drop(1).toByteArray())
        val expectedData = listOf(
            0x02,
            0x0a,
            if (header == 0x60) 0x01 else 0x00,
        ) + content + listOf((header + 0x02 + 0x0a + (if (header == 0x60) 0x01 else 0x00) + content.sum()) and 0xff)
        assertEquals(expectedData, decrypted.drop(1).take(expectedData.size))
        assertEquals(listOf(0, 0), decrypted.takeLast(2))
    }

    private fun assertKissToyAesCcmBasicControlData(packet: ByteArray, content: List<Int>) {
        assertEquals(5, content.size)
        assertKissToyAesCcmPacket(
            packet = packet,
            cmdId = 0x02,
            subId = 0x01,
            plaintext = content,
        )
    }

    private fun assertKissToyAesCcmPacket(
        packet: ByteArray,
        cmdId: Int,
        subId: Int,
        plaintext: List<Int>,
    ) {
        val expectedPayloadLength = 12 + plaintext.size + 8
        assertEquals(4 + expectedPayloadLength, packet.size)
        assertEquals(0x60, packet[0].toInt() and 0xff)
        assertEquals(cmdId, packet[1].toInt() and 0xff)
        assertEquals(subId, packet[2].toInt() and 0xff)
        assertEquals(expectedPayloadLength, packet[3].toInt() and 0xff)
        val nonce = packet.copyOfRange(4, 16)
        val encrypted = packet.copyOfRange(16, packet.size)
        val decrypted = kissToyAesCcmDecrypt(
            key = KISS_TOY_AES_CCM_KEY,
            nonce = nonce,
            encrypted = encrypted,
            tagSize = 8,
        )
        assertEquals(plaintext, decrypted.map { it.toInt() and 0xff })
    }

    private fun decryptKissToyBleEncryption(encryptedBytes: ByteArray): List<Int> {
        assertEquals(12, encryptedBytes.size)
        val encrypted = encryptedBytes.map { it.toInt() and 0xff }
        val raw = MutableList(12) { 0 }
        raw[0] = encrypted[0]
        val seed = raw[0]
        for (index in 1 until raw.size) {
            val key = KISS_TOY_BLE_ENCRYPTION_KEY_TAB_FOR_TEST[encrypted[index - 1] and 0x03][index]
            raw[index] = (((encrypted[index] - key) and 0xff) xor seed xor key) and 0xff
        }
        return raw
    }

    private fun ByteArray.toHexForTest(): String =
        joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        val KISSTOY_SERVICE_UUID: Uuid = Uuid.parse("00001000-0000-1000-8000-00805f9b34fb")
        val KISSTOY_WRITE_UUID: Uuid = Uuid.parse("00001001-0000-1000-8000-00805f9b34fb")
        val KISSTOY_NOTIFY_UUID: Uuid = Uuid.parse("00001002-0000-1000-8000-00805f9b34fb")

        val DDDD_SERVICE_UUID: Uuid = Uuid.parse("0000dddd-0000-1000-8000-00805f9b34fb")
        val DDDD_WRITE_UUID: Uuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")
        val DDDD_NOTIFY_UUID: Uuid = Uuid.parse("0000ddd2-0000-1000-8000-00805f9b34fb")

        val TUTU_SERVICE_UUID: Uuid = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb")
        val TUTU_WRITE_UUID: Uuid = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb")
        val TUTU_NOTIFY_UUID: Uuid = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb")

        val FFE0_SERVICE_UUID: Uuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
        val FFE0_WRITE_UUID: Uuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
        val FFE0_NOTIFY_UUID: Uuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

        val FFB0_SERVICE_UUID: Uuid = Uuid.parse("0000ffb0-0000-1000-8000-00805f9b34fb")
        val FFB2_UUID: Uuid = Uuid.parse("0000ffb2-0000-1000-8000-00805f9b34fb")
        val FEB3_SERVICE_UUID: Uuid = Uuid.parse("0000feb3-0000-1000-8000-00805f9b34fb")
        val FED4_READ_UUID: Uuid = Uuid.parse("0000fed4-0000-1000-8000-00805f9b34fb")
        val FED5_WRITE_UUID: Uuid = Uuid.parse("0000fed5-0000-1000-8000-00805f9b34fb")
        val FED6_NOTIFY_UUID: Uuid = Uuid.parse("0000fed6-0000-1000-8000-00805f9b34fb")
        val FED7_UUID: Uuid = Uuid.parse("0000fed7-0000-1000-8000-00805f9b34fb")
        val FED8_UUID: Uuid = Uuid.parse("0000fed8-0000-1000-8000-00805f9b34fb")

        val AE3A_SERVICE_UUID: Uuid = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb")
        val AE3A_WRITE_UUID: Uuid = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb")
        val AE3A_NOTIFY_UUID: Uuid = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb")

        val AE00_SERVICE_UUID: Uuid = Uuid.parse("0000ae00-0000-1000-8000-00805f9b34fb")
        val AE00_WRITE_UUID: Uuid = Uuid.parse("0000ae01-0000-1000-8000-00805f9b34fb")
        val AE00_NOTIFY_UUID: Uuid = Uuid.parse("0000ae02-0000-1000-8000-00805f9b34fb")

        val KISS_TOY_BLE_ENCRYPTION_KEY_TAB_FOR_TEST = arrayOf(
            intArrayOf(0x18, 0x5a, 0x64, 0x42, 0x10, 0xc6, 0xf6, 0x86, 0xb2, 0xd4, 0xfe, 0x92),
            intArrayOf(0xec, 0xb0, 0xbc, 0x0e, 0xc8, 0x7a, 0x54, 0xee, 0x00, 0x9a, 0x5a, 0x7a),
            intArrayOf(0x6e, 0x02, 0x06, 0xbc, 0xa6, 0x24, 0x4c, 0x2e, 0xca, 0xfc, 0x60, 0x72),
            intArrayOf(0xfa, 0x84, 0x26, 0x50, 0x6c, 0x4a, 0xe8, 0xd2, 0x88, 0x3a, 0x98, 0x9e),
        )
    }
}
