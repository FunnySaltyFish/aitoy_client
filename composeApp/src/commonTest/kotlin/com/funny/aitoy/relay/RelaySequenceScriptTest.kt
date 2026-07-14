package com.funny.aitoy.relay

import kotlin.test.Test
import kotlin.test.assertEquals

class RelaySequenceScriptTest {
    @Test
    fun supportsTenMinuteControlDuration() {
        val step = parseRelaySequenceScript("scalar(feature=4,mode=0,value=60,duration=600)").single()

        assertEquals(
            RelaySequenceStep.Scalar(featureIndex = 4, mode = 0, value = 60, durationSec = 600),
            step,
        )
    }

    @Test
    fun clampsDurationToTenMinutes() {
        val step = parseRelaySequenceScript("set(mode=1,intensity=20,duration=601)").single()

        assertEquals(
            RelaySequenceStep.Set(mode = 1, intensity = 20, durationSec = 600),
            step,
        )
    }
}
