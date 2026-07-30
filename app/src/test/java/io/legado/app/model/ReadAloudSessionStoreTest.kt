package io.legado.app.model

import io.legado.app.domain.model.readaloud.ReadAloudPlaybackInfo
import io.legado.app.domain.model.readaloud.ReadAloudSessionState
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudSessionStoreTest {

    @Test
    fun `updates session fields without losing unrelated state`() {
        val store = ReadAloudSessionStore()
        val playback = ReadAloudPlaybackInfo(
            chapterPosition = 42,
            chapterLength = 100,
            text = "current paragraph",
        )

        store.setStatus(ReadAloudSessionStatus.Playing)
        store.updatePlayback(playback)
        store.updateTimer(15)

        assertEquals(
            ReadAloudSessionState(
                status = ReadAloudSessionStatus.Playing,
                playback = playback,
                timerMinutes = 15,
            ),
            store.state.value,
        )
    }

    @Test
    fun `stop clears the complete session`() {
        val store = ReadAloudSessionStore()
        store.setStatus(ReadAloudSessionStatus.Paused)
        store.updatePlayback(ReadAloudPlaybackInfo(chapterPosition = 12))
        store.updateTimer(5)

        store.stop()

        assertEquals(ReadAloudSessionState(), store.state.value)
    }
}
