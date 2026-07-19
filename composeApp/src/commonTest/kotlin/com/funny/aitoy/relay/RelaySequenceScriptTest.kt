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

    @Test
    fun parsesFiniteRepeatSequence() {
        val step = parseRelaySequenceScript(
            "repeat(count=3){set(mode=1,intensity=20,duration=5); set(mode=2,intensity=40,duration=8)}",
        ).single()

        assertEquals(
            RelaySequenceStep.Repeat(
                count = 3,
                steps = listOf(
                    RelaySequenceStep.Set(mode = 1, intensity = 20, durationSec = 5),
                    RelaySequenceStep.Set(mode = 2, intensity = 40, durationSec = 8),
                ),
            ),
            step,
        )
    }

    @Test
    fun parsesFiniteRepeatAndKeepsFollowingTopLevelStep() {
        val steps = parseRelaySequenceScript(
            "repeat(count=2){set(mode=1,intensity=20,duration=5); set(mode=1,intensity=40,duration=8)}; stop()",
        )

        assertEquals(
            listOf(
                RelaySequenceStep.Repeat(
                    count = 2,
                    steps = listOf(
                        RelaySequenceStep.Set(mode = 1, intensity = 20, durationSec = 5),
                        RelaySequenceStep.Set(mode = 1, intensity = 40, durationSec = 8),
                    ),
                ),
                RelaySequenceStep.Stop,
            ),
            steps,
        )
    }

    @Test
    fun parsesInfiniteRepeatCount() {
        val step = parseRelaySequenceScript(
            "repeat(count=-1){set(mode=1,intensity=20,duration=5); set(mode=1,intensity=40,duration=8)}",
        ).single()

        assertEquals(
            RelaySequenceStep.Repeat(
                count = UnlimitedSequenceRepeatCount,
                steps = listOf(
                    RelaySequenceStep.Set(mode = 1, intensity = 20, durationSec = 5),
                    RelaySequenceStep.Set(mode = 1, intensity = 40, durationSec = 8),
                ),
            ),
            step,
        )
    }
}
