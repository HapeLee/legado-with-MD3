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
            val performance = segment.characterId?.let(characterPerformances::get)
            // 角色（男主/女主/男配/女配）绑定的音色，优先级低于角色专属绑定、高于性别兜底
            val roleVoice = performance?.roleSubject()?.let { subject ->
                bindings.voice(subject, subject, voicesById)
            }
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
                val genderFallback = when (performance?.resolvedGender()) {
                    "male" -> bindings.voice(
                        BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                        BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                        voicesById,
                    )

                    "female" -> bindings.voice(
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
                        add(roleVoice)
                        add(
                            when (performance?.resolvedGender()) {
                                "male" -> bindings.voice(
                                BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                                BookVoiceBinding.SUBJECT_UNKNOWN_MALE,
                                voicesById,
                            )

                                "female" -> bindings.voice(
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
                characterPerformance = performance,
            )
        }
    }

    private fun Map<Pair<String, String>, BookVoiceBinding>.voice(
        subjectType: String,
        subjectId: String,
        voicesById: Map<String, ReadAloudVoice>,
    ): ReadAloudVoice? = get(subjectType to subjectId)?.voiceId?.let(voicesById::get)

    /** 只有已知的角色标识才对应绑定主体，避免任意角色文本参与查询 */
    private fun CharacterPerformanceProfile.roleSubject(): String? = when (role) {
        BookVoiceBinding.SUBJECT_MALE_LEAD,
        BookVoiceBinding.SUBJECT_FEMALE_LEAD,
        BookVoiceBinding.SUBJECT_MALE_SUPPORTING,
        BookVoiceBinding.SUBJECT_FEMALE_SUPPORTING -> role

        else -> null
    }

    private fun CharacterPerformanceProfile.resolvedGender(): String? = when (voiceGender) {
        "male", "female" -> voiceGender
        else -> when (role) {
            BookVoiceBinding.SUBJECT_MALE_LEAD,
            BookVoiceBinding.SUBJECT_MALE_SUPPORTING -> "male"

            BookVoiceBinding.SUBJECT_FEMALE_LEAD,
            BookVoiceBinding.SUBJECT_FEMALE_SUPPORTING -> "female"

            else -> null
        }
    }
}
