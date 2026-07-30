package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReadAloudVoicesUseCaseTest {

    @Test
    fun `sync is stable and idempotent`() = runBlocking {
        val gateway = MutableVoiceGateway()
        val useCase = SyncReadAloudVoicesUseCase(gateway)
        val entry = VoiceCatalogEntry(
            engineType = ReadAloudVoice.ENGINE_HTTP,
            engineId = "7",
            displayName = "HTTP voice",
            sourceRevision = 10,
        )

        val first = useCase(
            entries = listOf(entry),
            managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
            now = 100,
        )
        val second = useCase(
            entries = listOf(entry),
            managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
            now = 200,
        )

        assertEquals(1, first.inserted)
        assertEquals(SpeechIdentity.voiceId("http", "7", ""), gateway.voices.single().id)
        assertEquals(SyncReadAloudVoicesResult(0, 0, 0), second)
        assertEquals(100, gateway.voices.single().updatedAt)
    }

    @Test
    fun `missing managed voice becomes unavailable without changing preference`() = runBlocking {
        val old = ReadAloudVoice(
            id = SpeechIdentity.voiceId("http", "7", ""),
            engineType = "http",
            engineId = "7",
            speakerId = "",
            displayName = "old",
            managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
            enabled = false,
        )
        val gateway = MutableVoiceGateway(mutableListOf(old))

        val result = SyncReadAloudVoicesUseCase(gateway)(
            entries = emptyList(),
            managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
            now = 200,
        )

        assertEquals(1, result.unavailable)
        assertFalse(gateway.voices.single().available)
        assertFalse(gateway.voices.single().enabled)
    }

    @Test
    fun `missing configured HTTP voice is removed with deleted TTS`() = runBlocking {
        val old = ReadAloudVoice(
            id = SpeechIdentity.voiceId("http", "7", ""),
            engineType = ReadAloudVoice.ENGINE_HTTP,
            engineId = "7",
            speakerId = "",
            displayName = "deleted TTS",
            managedBy = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
            available = false,
        )
        val gateway = MutableVoiceGateway(mutableListOf(old))

        val result = SyncReadAloudVoicesUseCase(gateway)(
            entries = emptyList(),
            managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
            removeMissingEngineTypes = setOf(ReadAloudVoice.ENGINE_HTTP),
            now = 200,
        )

        assertEquals(1, result.unavailable)
        assertTrue(gateway.voices.isEmpty())
    }
}

private class MutableVoiceGateway(
    val voices: MutableList<ReadAloudVoice> = mutableListOf(),
) : ReadAloudVoiceGateway {
    override fun observeVoices(): Flow<List<ReadAloudVoice>> = flowOf(voices)
    override suspend fun getVoices(): List<ReadAloudVoice> = voices.toList()
    override suspend fun getEnabledVoices(): List<ReadAloudVoice> =
        voices.filter { it.enabled && it.available }
    override suspend fun getVoice(id: String): ReadAloudVoice? = voices.firstOrNull { it.id == id }
    override suspend fun upsertVoice(voice: ReadAloudVoice) {
        voices.removeAll { it.id == voice.id }
        voices += voice
    }
    override suspend fun deleteVoice(voice: ReadAloudVoice) {
        voices.removeAll { it.id == voice.id }
    }
    override fun observeBindings(bookUrl: String): Flow<List<BookVoiceBinding>> = flowOf(emptyList())
    override suspend fun getBindings(bookUrl: String): List<BookVoiceBinding> = emptyList()
    override suspend fun getBinding(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
    ): BookVoiceBinding? = null
    override suspend fun upsertBinding(binding: BookVoiceBinding) = Unit
    override suspend fun deleteBinding(binding: BookVoiceBinding) = Unit
}
