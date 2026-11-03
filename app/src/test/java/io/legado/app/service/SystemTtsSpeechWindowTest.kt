package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemTtsSpeechWindowTest {

    private val narrator = CueSpeechState(
        engineId = "com.engine",
        voiceName = "narrator",
        rate = 1.0f,
        pitch = 1.0f,
    )

    @Test
    fun windowStopsAtDepthAndKeepsInsertionOrder() {
        val window = SystemTtsSpeechWindow(capacity = 3)
        window.reset(session = 1)

        window.add(5)
        window.add(6)
        window.add(7)

        assertEquals(3, window.size)
        assertTrue(window.isFull)
        assertTrue(window.contains(5))
        assertTrue(window.hasPending)
    }

    @Test
    fun completeReportsWhetherTheCueWasStillQueued() {
        val window = SystemTtsSpeechWindow(capacity = 3)
        window.reset(session = 1)
        window.add(5)

        assertTrue(window.complete(5))
        // 引擎重复回调同一句时不能再推进一次
        assertFalse(window.complete(5))
        assertFalse(window.hasPending)
    }

    @Test
    fun resetDropsPreviewsOfThePreviousSession() {
        val window = SystemTtsSpeechWindow(capacity = 3)
        window.reset(session = 1)
        window.add(5)
        window.add(6)

        window.reset(session = 2)

        assertEquals(2, window.session)
        assertEquals(0, window.size)
        assertFalse(window.contains(5))
    }

    @Test
    fun clearInvalidatesSessionSoStaleCallbacksAreRejected() {
        val window = SystemTtsSpeechWindow(capacity = 3)
        window.reset(session = 1)
        window.add(5)

        window.clear()

        assertEquals(Int.MIN_VALUE, window.session)
        assertFalse(window.contains(5))
    }

    @Test
    fun sameVoiceAndPresetCanJoinTheWindow() {
        assertTrue(
            canSpeakInSameEngineState(
                applied = narrator,
                next = narrator.copy(rate = 1.0f + 0.0005f),
            )
        )
    }

    @Test
    fun voiceSwitchBreaksTheWindow() {
        assertFalse(
            canSpeakInSameEngineState(
                applied = narrator,
                next = narrator.copy(voiceName = "hero"),
            )
        )
    }

    @Test
    fun engineSwitchBreaksTheWindow() {
        assertFalse(
            canSpeakInSameEngineState(
                applied = narrator,
                next = narrator.copy(engineId = "other.engine"),
            )
        )
    }

    @Test
    fun speedOrPitchChangeBreaksTheWindow() {
        // 语速/音高是引擎级状态：提前改会把还在排队的句子一起改掉
        assertFalse(
            canSpeakInSameEngineState(applied = narrator, next = narrator.copy(rate = 1.2f))
        )
        assertFalse(
            canSpeakInSameEngineState(applied = narrator, next = narrator.copy(pitch = 0.8f))
        )
    }

    @Test
    fun defaultVoiceIsRepresentedByEmptyName() {
        val engineDefault = narrator.copy(voiceName = "")
        assertTrue(canSpeakInSameEngineState(applied = engineDefault, next = engineDefault))
        assertFalse(canSpeakInSameEngineState(applied = engineDefault, next = narrator))
    }
}
