package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimerTest {

    @Test
    fun normalize_clampsMinutesToSupportedRange() {
        assertEquals(0, PlaybackTimer.normalize(-1))
        assertEquals(0, PlaybackTimer.normalize(0))
        assertEquals(90, PlaybackTimer.normalize(90))
        assertEquals(180, PlaybackTimer.normalize(180))
        assertEquals(180, PlaybackTimer.normalize(181))
    }

    @Test
    fun addIncrement_recoversInvalidValuesAndCyclesAtMaximum() {
        assertEquals(10, PlaybackTimer.addIncrement(-1))
        assertEquals(10, PlaybackTimer.addIncrement(0))
        assertEquals(180, PlaybackTimer.addIncrement(175))
        assertEquals(0, PlaybackTimer.addIncrement(180))
        assertEquals(0, PlaybackTimer.addIncrement(181))
    }
}
