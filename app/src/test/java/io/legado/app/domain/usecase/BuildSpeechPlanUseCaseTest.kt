package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechResolutionSource
import io.legado.app.domain.model.readaloud.SpeechRoleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSpeechPlanUseCaseTest {

    @Test
    fun `uses character voice and deterministic fallbacks`() = runBlocking {
        val characterVoice = voice("character")
        val unknownVoice = voice("unknown")
        val narratorVoice = voice("narrator")
        val gateway = FakeVoiceGateway(
            voices = listOf(characterVoice, unknownVoice, narratorVoice),
            bindings = listOf(
                binding(BookVoiceBinding.SUBJECT_CHARACTER, "character-1", characterVoice.id),
                binding(BookVoiceBinding.SUBJECT_UNKNOWN, BookVoiceBinding.SUBJECT_UNKNOWN, unknownVoice.id),
                binding(BookVoiceBinding.SUBJECT_NARRATOR, BookVoiceBinding.SUBJECT_NARRATOR, narratorVoice.id),
            ),
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, "character-1")),
        ).single()

        assertEquals(characterVoice, plan.voice)
        assertEquals(listOf(unknownVoice, narratorVoice), plan.fallbackVoices)
    }

    @Test
    fun `never falls back to another character voice`() = runBlocking {
        val otherCharacterVoice = voice("other-character")
        val narratorVoice = voice("narrator")
        val gateway = FakeVoiceGateway(
            voices = listOf(otherCharacterVoice, narratorVoice),
            bindings = listOf(
                binding(BookVoiceBinding.SUBJECT_CHARACTER, "other", otherCharacterVoice.id),
                binding(BookVoiceBinding.SUBJECT_NARRATOR, BookVoiceBinding.SUBJECT_NARRATOR, narratorVoice.id),
            ),
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, "missing")),
        ).single()

        assertEquals(narratorVoice, plan.voice)
        assertEquals(emptyList<ReadAloudVoice>(), plan.fallbackVoices)
    }

    @Test
    fun `leaves route empty when only another character voice exists`() = runBlocking {
        val otherCharacterVoice = voice("other-character")
        val gateway = FakeVoiceGateway(
            voices = listOf(otherCharacterVoice),
            bindings = listOf(
                binding(BookVoiceBinding.SUBJECT_CHARACTER, "other", otherCharacterVoice.id),
            ),
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, "missing")),
        ).single()

        assertEquals(null, plan.voice)
        assertEquals(emptyList<ReadAloudVoice>(), plan.fallbackVoices)
    }

    @Test
    fun `attaches performance only to resolved character cue`() = runBlocking {
        val narratorVoice = voice("narrator")
        val gateway = FakeVoiceGateway(
            voices = listOf(narratorVoice),
            bindings = listOf(
                binding(
                    BookVoiceBinding.SUBJECT_NARRATOR,
                    BookVoiceBinding.SUBJECT_NARRATOR,
                    narratorVoice.id,
                ),
            ),
        )
        val performance = CharacterPerformanceProfile(
            characterId = "character-1",
            personality = "冷静",
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, "character-1")),
            characterPerformances = mapOf(performance.characterId to performance),
        ).single()

        assertEquals(performance, plan.characterPerformance)
    }

    @Test
    fun `uses role voice when character has no individual binding`() = runBlocking {
        val roleVoice = voice("male-lead")
        val gateway = FakeVoiceGateway(
            voices = listOf(roleVoice),
            bindings = listOf(binding(
                BookVoiceBinding.SUBJECT_MALE_LEAD,
                BookVoiceBinding.SUBJECT_MALE_LEAD,
                roleVoice.id,
            )),
        )
        val performance = CharacterPerformanceProfile(
            characterId = "character-1",
            role = BookVoiceBinding.SUBJECT_MALE_LEAD,
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, performance.characterId)),
            characterPerformances = mapOf(performance.characterId to performance),
        ).single()

        assertEquals(roleVoice, plan.voice)
    }

    @Test
    fun `individual character binding overrides role voice`() = runBlocking {
        val characterVoice = voice("character")
        val roleVoice = voice("female-lead")
        val gateway = FakeVoiceGateway(
            voices = listOf(characterVoice, roleVoice),
            bindings = listOf(
                binding(BookVoiceBinding.SUBJECT_CHARACTER, "character-1", characterVoice.id),
                binding(
                    BookVoiceBinding.SUBJECT_FEMALE_LEAD,
                    BookVoiceBinding.SUBJECT_FEMALE_LEAD,
                    roleVoice.id,
                ),
            ),
        )
        val performance = CharacterPerformanceProfile(
            characterId = "character-1",
            role = BookVoiceBinding.SUBJECT_FEMALE_LEAD,
        )

        val plan = BuildSpeechPlanUseCase(gateway)(
            bookUrl = "book",
            segments = listOf(segment(SpeechRoleType.Character, performance.characterId)),
            characterPerformances = mapOf(performance.characterId to performance),
        ).single()

        assertEquals(characterVoice, plan.voice)
    }


    private fun voice(id: String) = ReadAloudVoice(
        id = id,
        engineType = "http",
        engineId = id,
        speakerId = "",
        displayName = id,
    )

    private fun binding(type: String, subjectId: String, voiceId: String) = BookVoiceBinding(
        bookUrl = "book",
        subjectType = type,
        subjectId = subjectId,
        voiceId = voiceId,
    )

    private fun segment(roleType: SpeechRoleType, characterId: String?) = ChapterSpeechSegment(
        id = "segment",
        analysisId = "analysis",
        bookUrl = "book",
        chapterIndex = 0,
        paragraphIndex = 0,
        start = 0,
        end = 2,
        chapterPosition = 0,
        text = "台词",
        roleType = roleType,
        characterId = characterId,
        source = SpeechResolutionSource.Rule,
    )
}

private class FakeVoiceGateway(
    private val voices: List<ReadAloudVoice>,
    private val bindings: List<BookVoiceBinding>,
) : ReadAloudVoiceGateway {
    override fun observeVoices(): Flow<List<ReadAloudVoice>> = flowOf(voices)
    override suspend fun getVoices(): List<ReadAloudVoice> = voices
    override suspend fun getEnabledVoices(): List<ReadAloudVoice> = voices
    override suspend fun getVoice(id: String): ReadAloudVoice? = voices.firstOrNull { it.id == id }
    override suspend fun upsertVoice(voice: ReadAloudVoice) = Unit
    override suspend fun deleteVoice(voice: ReadAloudVoice) = Unit
    override fun observeBindings(bookUrl: String): Flow<List<BookVoiceBinding>> = flowOf(bindings)
    override suspend fun getBindings(bookUrl: String): List<BookVoiceBinding> = bindings
    override suspend fun getBinding(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
    ): BookVoiceBinding? = bindings.firstOrNull {
        it.bookUrl == bookUrl && it.subjectType == subjectType && it.subjectId == subjectId
    }
    override suspend fun upsertBinding(binding: BookVoiceBinding) = Unit
    override suspend fun deleteBinding(binding: BookVoiceBinding) = Unit
}
