package io.legado.app.service

import android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT
import io.legado.app.model.AudioPlay.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioPlayMediaStateTest {
    @Test
    fun lastChapterOffersNextOnlyInRepeatOrRandomModes() {
        for (mode in PlayMode.entries) {
            val expected = mode != PlayMode.LIST_END_STOP
            assertEquals(expected, canSkipToNextAudioChapter(1, 2, mode))
            val actions = audioPlayMediaActions(1, 2, canSeek = true, playMode = mode)
            assertEquals(expected, actions and ACTION_SKIP_TO_NEXT != 0L)
        }
    }

    @Test
    fun noQueueOrStaleChapterNeverOffersNext() {
        assertFalse(canSkipToNextAudioChapter(0, 0, PlayMode.LIST_LOOP))
        assertFalse(canSkipToNextAudioChapter(2, 2, PlayMode.LIST_LOOP))
    }
}
