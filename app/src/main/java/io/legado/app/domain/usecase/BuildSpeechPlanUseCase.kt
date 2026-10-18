package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ChapterSpeechSegment
import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.readaloud.SpeechVoiceSelection

class BuildSpeechPlanUseCase(
    private val voiceGateway: ReadAloudVoiceGateway,
) {

    /** 标题不经过正文的正则角色覆盖，也不要求正文中必须存在旁白片段。 */
    suspend fun narrationVoice(
        bookUrl: String,
        preferredDefaultVoiceId: String? = null,
        useMultiSpeaker: Boolean = true,
    ): SpeechVoiceSelection {
        val voices = voiceGateway.getEnabledVoices().associateBy(ReadAloudVoice::id)
        val bindings = voiceGateway.getBindings(bookUrl).associateBy { it.subjectType to it.subjectId }
        return narrationVoice(
            bindings.voice(BookVoiceBinding.SUBJECT_NARRATOR, BookVoiceBinding.SUBJECT_NARRATOR, voices),
            preferredDefaultVoiceId?.let(voices::get),
            useMultiSpeaker,
        )
    }

    private fun narrationVoice(
        narrator: ReadAloudVoice?,
        defaultVoice: ReadAloudVoice?,
        useMultiSpeaker: Boolean,
    ): SpeechVoiceSelection {
        val primary = if (useMultiSpeaker) narrator ?: defaultVoice else defaultVoice
        val fallback = if (useMultiSpeaker) {
            listOfNotNull(narrator, defaultVoice).distinctBy(ReadAloudVoice::id)
                .filterNot { it.id == primary?.id }
        } else {
            emptyList()
        }
        return SpeechVoiceSelection(primary, fallback)
    }

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
        // 默认音色只认「朗读引擎与音色」里选的那一个：配音页的旁白绑定属于多角色那一层，
        // 绝不能在这种时候顶替它，否则关掉多角色朗读旁白还是配音页那个人。
        val defaultVoice = preferredDefaultVoiceId?.let(voicesById::get)
        val narration = narrationVoice(narrator, defaultVoice, useMultiSpeaker)

        return segments.map { segment ->
            val performance = segment.characterId?.let(characterPerformances::get)
            // 角色（男主/女主/男配/女配）绑定的音色，优先级低于角色专属绑定、高于性别兜底
            val roleVoice = performance?.roleSubject()?.let { subject ->
                bindings.voice(subject, subject, voicesById)
            }
            val primary = if (!useMultiSpeaker) {
                // 多角色朗读关闭：整章不接管音色，defaultVoice 为空时由 SpeechVoiceRouter 落到引擎默认
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
                    SpeechRoleType.Narrator -> narration.voice
                }
            }
            val fallbackVoices = if (segment.roleType == SpeechRoleType.Narrator) {
                narration.fallbackVoices
            } else if (!useMultiSpeaker) {
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
