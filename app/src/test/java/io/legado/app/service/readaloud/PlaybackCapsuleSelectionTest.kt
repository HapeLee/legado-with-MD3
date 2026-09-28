package io.legado.app.service.readaloud

import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.selectPlaybackCapsule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapsuleSelectionTest {
    private val tts = PlaybackCapsuleState(
        source = PlaybackCapsuleSource.ReadAloud,
        bookUrl = "tts",
        isPaused = false
    )
    private val audio = PlaybackCapsuleState(
        source = PlaybackCapsuleSource.AudioBook,
        bookUrl = "audio",
        isPaused = false
    )

    @Test
    fun `audio starts while tts is paused then remains selected on pause`() {
        val selected = selectPlaybackCapsule(tts.copy(isPaused = true), audio, tts.source)
        assertEquals(audio, selected)
        assertEquals(
            audio.copy(isPaused = true), selectPlaybackCapsule(
                tts.copy(isPaused = true), audio.copy(isPaused = true), selected.source
            )
        )
    }

    @Test
    fun `resuming tts takes over a paused audio capsule`() {
        assertEquals(tts, selectPlaybackCapsule(tts, audio.copy(isPaused = true), audio.source))
    }

    @Test
    fun `stopping selected audio falls back to surviving paused tts`() {
        assertEquals(
            tts.copy(isPaused = true), selectPlaybackCapsule(
                tts.copy(isPaused = true), PlaybackCapsuleState(), audio.source
            )
        )
    }

    @Test
    fun `no live service means no capsule even with old book metadata`() {
        assertEquals(
            PlaybackCapsuleState(), selectPlaybackCapsule(
                PlaybackCapsuleState(bookUrl = "old-tts"),
                PlaybackCapsuleState(bookUrl = "old-audio"),
                audio.source
            )
        )
    }

    @Test
    fun `simultaneous playback keeps current source rather than flickering`() {
        assertEquals(audio, selectPlaybackCapsule(tts, audio, audio.source))
        assertEquals(tts, selectPlaybackCapsule(tts, audio, tts.source))
    }

    @Test
    fun `only selected engine can own the external window`() {
        assertTrue(shouldShowExternalCapsule(true, false, true, audio.source, audio.source))
        assertFalse(shouldShowExternalCapsule(true, false, true, tts.source, audio.source))
        assertFalse(shouldShowExternalCapsule(true, false, true, audio.source, null))
        assertFalse(shouldShowExternalCapsule(true, true, true, audio.source, audio.source))
        assertFalse(shouldShowExternalCapsule(true, false, false, audio.source, audio.source))
    }
}
