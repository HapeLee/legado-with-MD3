package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import io.legado.app.domain.model.readaloud.HttpTtsSpeakerDraft
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHttpTtsCatalogUseCaseTest {

    @Test
    fun `导入目录后按引擎写入音色`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        useCase("1", draftOf("a", "b"))

        assertEquals(2, gateway.voices.size)
        assertTrue(gateway.voices.all { it.engineType == ReadAloudVoice.ENGINE_HTTP })
        assertTrue(gateway.voices.all { it.engineId == "1" })
        assertTrue(gateway.voices.all { it.managedBy == ReadAloudVoice.MANAGED_BY_HTTP_SOURCE_CATALOG })
        assertEquals(listOf("a", "b"), gateway.voices.map { it.speakerId })
    }

    /**
     * 回归：同步按 managedBy 全局回收，不限定引擎的话同步 B 会把 A 的目录
     * 当成"本次没出现"整批删掉。
     */
    @Test
    fun `同步另一个引擎不会删掉已有引擎的目录`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        useCase("1", draftOf("a", "b"))
        useCase("2", draftOf("c"))

        assertEquals(3, gateway.voices.size)
        assertEquals(setOf("1", "2"), gateway.voices.mapTo(mutableSetOf()) { it.engineId })
    }

    @Test
    fun `同一引擎重复导入不翻倍`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        repeat(3) { useCase("1", draftOf("a", "b")) }

        assertEquals(2, gateway.voices.size)
    }

    /**
     * 回归：备份里没有 speakersJson 字段，恢复时每个源都是空草稿。若此时顺势清空，
     * 一次恢复就会把用户已有的目录全删掉。
     */
    @Test
    fun `源不带目录时不动已有目录`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        useCase("1", draftOf("a", "b"))
        useCase("1", HttpTtsCatalogDraft())
        useCase("1", HttpTtsCatalogDraft(emotions = listOf(HttpTtsEmotionDraft("开心", "happy"))))

        assertEquals(2, gateway.voices.size)
    }

    /**
     * 排除标记存在的全部意义：行留着，下次导入同一个源不会再被当成新音色插回来。
     */
    @Test
    fun `被排除的音色重新导入源不复活`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        useCase("1", draftOf("a", "b"))
        val excluded = gateway.voices.first { it.speakerId == "a" }
        gateway.upsertVoice(excluded.copy(excluded = true))

        useCase("1", draftOf("a", "b"))

        assertEquals(2, gateway.voices.size)
        assertTrue(gateway.voices.first { it.speakerId == "a" }.excluded)
        assertTrue(!gateway.voices.first { it.speakerId == "b" }.excluded)
    }

    @Test
    fun `clear 只清指定引擎`() = runTest {
        val gateway = FakeVoiceGateway()
        val useCase = SyncHttpTtsCatalogUseCase(SyncReadAloudVoicesUseCase(gateway))

        useCase("1", draftOf("a"))
        useCase("2", draftOf("b"))
        useCase.clear("1")

        assertEquals(listOf("2"), gateway.voices.map { it.engineId })
    }

    private fun draftOf(vararg toneIds: String) = HttpTtsCatalogDraft(
        speakers = toneIds.map { HttpTtsSpeakerDraft(speakerName = "音色$it", toneID = it) }
    )

    private class FakeVoiceGateway : ReadAloudVoiceGateway {

        val voices = mutableListOf<ReadAloudVoice>()

        override fun observeVoices(): Flow<List<ReadAloudVoice>> = flowOf(voices)

        override suspend fun getVoices(): List<ReadAloudVoice> = voices.toList()

        override suspend fun getEnabledVoices(): List<ReadAloudVoice> =
            voices.filter { it.enabled }

        override suspend fun getVoice(id: String): ReadAloudVoice? =
            voices.firstOrNull { it.id == id }

        override suspend fun upsertVoice(voice: ReadAloudVoice) {
            voices.removeAll { it.id == voice.id }
            voices.add(voice)
        }

        override suspend fun deleteVoice(voice: ReadAloudVoice) {
            voices.removeAll { it.id == voice.id }
        }

        override fun observeBindings(bookUrl: String): Flow<List<BookVoiceBinding>> =
            flowOf(emptyList())

        override suspend fun getBindings(bookUrl: String): List<BookVoiceBinding> = emptyList()

        override suspend fun getBinding(
            bookUrl: String,
            subjectType: String,
            subjectId: String,
        ): BookVoiceBinding? = null

        override suspend fun upsertBinding(binding: BookVoiceBinding) = Unit

        override suspend fun deleteBinding(binding: BookVoiceBinding) = Unit
    }
}
