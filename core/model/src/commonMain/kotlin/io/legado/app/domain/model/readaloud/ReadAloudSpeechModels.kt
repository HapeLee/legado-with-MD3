package io.legado.app.domain.model.readaloud

data class ReadAloudVoice(
    val id: String,
    val engineType: String,
    val engineId: String,
    val speakerId: String,
    val displayName: String,
    val traitsJson: String = "[]",
    val emotionCatalogJson: String = "[]",
    val managedBy: String = MANAGED_BY_USER,
    val enabled: Boolean = true,
    val available: Boolean = true,
    val revision: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ENGINE_SYSTEM = "system"
        const val ENGINE_HTTP = "http"
        const val ENGINE_CLOUD = "cloud_tts"
        const val MANAGED_BY_USER = "user"
        const val MANAGED_BY_CONFIGURED_TTS = "configured_tts"
    }
}

data class ReadAloudEngineSelection(
    val engineType: String,
    val engineId: String,
    val speakerId: String = "",
    val displayName: String = "",
)

data class VoiceCatalogEntry(
    val engineType: String,
    val engineId: String,
    val speakerId: String = "",
    val displayName: String,
    val traitsJson: String = "[]",
    val emotionCatalogJson: String = "[]",
    val sourceRevision: Long = 0L,
    val managedBy: String = ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS,
)

data class BookVoiceBinding(
    val bookUrl: String,
    val subjectType: String,
    val subjectId: String,
    val voiceId: String,
    val locked: Boolean = false,
    val source: String = SOURCE_USER,
    val confidence: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SUBJECT_CHARACTER = "character"
        const val SUBJECT_NARRATOR = "narrator"
        const val SUBJECT_UNKNOWN_MALE = "unknown_male"
        const val SUBJECT_UNKNOWN_FEMALE = "unknown_female"
        const val SUBJECT_MALE_LEAD = "male_lead"
        const val SUBJECT_FEMALE_LEAD = "female_lead"
        const val SUBJECT_MALE_SUPPORTING = "male_supporting"
        const val SUBJECT_FEMALE_SUPPORTING = "female_supporting"
        const val SUBJECT_UNKNOWN = "unknown"

        const val SOURCE_USER = "user"
        const val SOURCE_AUTO = "auto"
        const val SOURCE_IMPORT = "import"
    }
}

data class ChapterSpeechAnalysis(
    val id: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val contentHash: String,
    val resolverVersion: String,
    val characterRevision: String,
    val status: SpeechAnalysisStatus,
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ChapterSpeechSegment(
    val id: String,
    val analysisId: String,
    val bookUrl: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val chapterPosition: Int,
    val text: String,
    val roleType: SpeechRoleType,
    val characterId: String? = null,
    val characterName: String = "",
    val emotion: String = "",
    val confidence: Float = 0f,
    val source: SpeechResolutionSource,
    val userLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class CanonicalSpeechParagraph(
    val index: Int,
    val text: String,
    val chapterPosition: Int,
)

data class SpeechSegmentDraft(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val chapterPosition: Int,
    val text: String,
    val roleType: SpeechRoleType,
    val confidence: Float,
    val emotion: String = "",
    val source: SpeechResolutionSource = SpeechResolutionSource.Rule,
)

data class SpeechPlanItem(
    val segment: ChapterSpeechSegment,
    val voice: ReadAloudVoice?,
    val fallbackVoices: List<ReadAloudVoice>,
    val characterPerformance: CharacterPerformanceProfile? = null,
)

data class ChapterSpeechAnalysisResult(
    val analysis: ChapterSpeechAnalysis,
    val segments: List<ChapterSpeechSegment>,
    val fromCache: Boolean,
    val characterPerformances: List<CharacterPerformanceProfile> = emptyList(),
)

data class CharacterPerformanceProfile(
    val characterId: String,
    val role: String = "",
    val voiceGender: String = "unknown",
    val voiceAgeBand: String = "unknown",
    val personality: String = "",
    val updatedAt: Long = 0L,
)

enum class SpeechAnalysisMode(val storageValue: String) {
    Rule("rule"),
    RuleWithAi("rule_with_ai"),
    AiUnderstanding("ai_understanding");

    companion object {
        fun fromStorage(value: String): SpeechAnalysisMode =
            entries.firstOrNull { it.storageValue == value } ?: Rule
    }
}

data class SpeakerCharacter(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val role: String = "",
    val voiceGender: String = "unknown",
    val voiceAgeBand: String = "unknown",
    val updatedAt: Long = 0L,
)

enum class SpeechRoleType(val storageValue: String) {
    Narrator("narrator"),
    Character("character"),
    Thought("thought"),
    Unknown("unknown");

    companion object {
        fun fromStorage(value: String): SpeechRoleType =
            entries.firstOrNull { it.storageValue == value } ?: Unknown
    }
}

enum class SpeechResolutionSource(val storageValue: String) {
    Rule("rule"),
    Local("local"),
    Ai("ai"),
    User("user"),
    Fallback("fallback");

    companion object {
        fun fromStorage(value: String): SpeechResolutionSource =
            entries.firstOrNull { it.storageValue == value } ?: Fallback
    }
}

enum class SpeechAnalysisStatus(val storageValue: String) {
    Pending("pending"),
    Running("running"),
    Success("success"),
    Partial("partial"),
    Failed("failed");

    companion object {
        fun fromStorage(value: String): SpeechAnalysisStatus =
            entries.firstOrNull { it.storageValue == value } ?: Failed
    }
}
