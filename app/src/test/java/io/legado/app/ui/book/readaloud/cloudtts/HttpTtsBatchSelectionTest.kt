package io.legado.app.ui.book.readaloud.cloudtts

import io.legado.app.domain.model.readaloud.ReadAloudVoice
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTtsBatchSelectionTest {
    private val user = voice("user", ReadAloudVoice.MANAGED_BY_USER)
    private val catalog = voice("catalog", ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG)
    private val configured = voice("configured", ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS)
    private val voices = listOf(user, catalog, configured)

    @Test fun `select all includes removable catalog voices only`() {
        assertEquals(setOf(user.id, catalog.id), toggleAllRemovableVoices(voices, emptySet()))
    }

    @Test fun `same count with different identities is not all selected`() {
        assertEquals(setOf(user.id, catalog.id), toggleAllRemovableVoices(voices, setOf("stale", user.id)))
    }

    @Test fun `all selected toggles to empty`() {
        assertEquals(emptySet<String>(), toggleAllRemovableVoices(voices, setOf(user.id, catalog.id)))
    }

    private fun voice(id: String, owner: String) = ReadAloudVoice(
        id = id, engineType = "http", engineId = "1", speakerId = id, displayName = id, managedBy = owner,
    )
}
