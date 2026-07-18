package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechRoleType

class BuildSpeechPlanUseCase(
    private val voiceGateway: ReadAloudVoiceGateway,
) {

    suspend operator fun invoke(
        bookUrl: String,
        segments: List<ChapterSpeechSegment>,
        preferredDefaultVoiceId: String? = null,
        characterPerformances: Map<String, CharacterPerformanceProfile> = emptyMap(),
        useMultiSpeaker: Boolean = true,
    ): List<SpeechPlanItem> {
        val voices = voiceGateway.getEnabledVoices()
        val voicesById = voices.associateBy(ReadAloudVoice::id)
        val bindings = voiceGateway.getBindings(bookUrl)
            .associateBy { it.subjectType to it.subjectId }
        val narrator = bindings.voice(
            BookVoiceBinding.SUBJECT_NARRATOR,
            BookVoiceBinding.SUBJECT_NARRATOR,
            voicesById,
        )
        val unknown = bindings.voice(
            BookVoiceBinding.SUBJECT_UNKNOWN,
            BookVoiceBinding.SUBJECT_UNKNOWN,
            voicesById,
        )
        val defaultVoice = preferredDefaultVoiceId?.let(voicesById::get)
            ?: narrator

        return segments.map { segment ->
            val primary = if (!useMultiSpeaker) {
                // Multi-speaker disabled — every segment uses the default voice
                defaultVoice
            } else {
                val characterVoice = segment.characterId?.let { characterId ->
                    bindings.voice(
                        BookVoiceBinding.SUBJECT_CHARACTER,
                        characterId,
                        voicesById,
                    )
                }
                val characterRole = segment.characterId?.let(characterPerformances::get)?.role
                val roleVoice = characterRole?.let { role ->
                    bindings.voice(role, role, voicesById)
                }
                val genderFallback = when (characterRole) {
                    BookVoiceBinding.SUBJECT_MALE_LEAD,
                    BookVoiceBinding.SUBJECT_MALE_SUPPORTING -> bindings.voice(
                        BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                        BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                        voicesById,
                    )
                    BookVoiceBinding.SUBJECT_FEMALE_LEAD,
                    BookVoiceBinding.SUBJECT_FEMALE_SUPPORTING -> bindings.voice(
                        BookVoiceBinding.SUBJECT_UNKNOWN_FEMALE,
                        BookVoiceBinding.SUBJECT_UNKNOWN_FEMALE,
                        voicesById,
                    )
                    else -> null
                }
                when (segment.roleType) {
                    SpeechRoleType.Character,
                    SpeechRoleType.Thought -> characterVoice ?: roleVoice ?: genderFallback
                        ?: unknown ?: narrator ?: defaultVoice
                    SpeechRoleType.Unknown -> unknown ?: narrator ?: defaultVoice
                    SpeechRoleType.Narrator -> narrator ?: defaultVoice
                }
            }
            val fallbackVoices = if (!useMultiSpeaker) {
                emptyList()
            } else {
                buildList {
                    if (segment.roleType != SpeechRoleType.Narrator) {
                        val characterRole = segment.characterId
                            ?.let(characterPerformances::get)?.role
                        add(characterRole?.let { bindings.voice(it, it, voicesById) })
                        add(when (characterRole) {
                            BookVoiceBinding.SUBJECT_MALE_LEAD,
                            BookVoiceBinding.SUBJECT_MALE_SUPPORTING -> bindings.voice(
                                BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                                BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                                voicesById,
                            )
                            BookVoiceBinding.SUBJECT_FEMALE_LEAD,
                            BookVoiceBinding.SUBJECT_FEMALE_SUPPORTING -> bindings.voice(
                                BookVoiceBinding.SUBJECT_UNKNOWN_FEMALE,
                                BookVoiceBinding.SUBJECT_UNKNOWN_FEMALE,
                                voicesById,
                            )
                            else -> null
                        })
                        add(unknown)
                    }
                    add(narrator)
                    add(defaultVoice)
                }.filterNotNull()
                    .distinctBy(ReadAloudVoice::id)
                    .filterNot { it.id == primary?.id }
            }
            SpeechPlanItem(
                segment = segment,
                voice = primary,
                fallbackVoices = fallbackVoices,
                characterPerformance = segment.characterId?.let(characterPerformances::get),
            )
        }
    }

    private fun Map<Pair<String, String>, BookVoiceBinding>.voice(
        subjectType: String,
        subjectId: String,
        voicesById: Map<String, ReadAloudVoice>,
    ): ReadAloudVoice? = get(subjectType to subjectId)?.voiceId?.let(voicesById::get)
}
