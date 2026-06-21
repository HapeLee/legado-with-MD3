package io.legado.app.help.ai

data class AiReadAloudRolePreviewSegment(
    val paragraphIndex: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
    val text: String = "",
    val roleType: String = "",
    val characterName: String = "",
    val characterId: Long = 0L,
    val matchedCharacter: Boolean = false,
    val emotionName: String = "",
    val emotionTag: String = "",
    val speakerName: String = "",
    val toneID: String = "",
    val groupName: String = "",
    val engineType: String = "",
    val confidence: Double = 0.0,
    val source: String = ""
) {
    val key: String
        get() = "$paragraphIndex:$start:$end"
}

data class AiReadAloudRoleState(
    val bookUrl: String = "",
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val stage: String = STAGE_CURRENT,
    val status: String = STATUS_IDLE,
    val message: String = "",
    val paragraphCount: Int = 0,
    val segmentCount: Int = 0,
    val createdCharacterCount: Int = 0,
    val newCharacterCandidateCount: Int = 0,
    val previewSource: String = SOURCE_NONE,
    val previewSegments: List<AiReadAloudRolePreviewSegment> = emptyList(),
    val elapsedMillis: Long = 0L,
    val requestCount: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedInputTokens: Int = 0,
    val error: String = ""
) {
    val running: Boolean
        get() = status == STATUS_RUNNING

    companion object {
        const val STAGE_CURRENT = "current"
        const val STAGE_NEXT = "next"

        const val STATUS_IDLE = "idle"
        const val STATUS_RUNNING = "running"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FALLBACK = "fallback"
        const val STATUS_FAILED = "failed"
        const val STATUS_SKIPPED = "skipped"

        const val SOURCE_NONE = "none"
        const val SOURCE_AI = "ai"
        const val SOURCE_RULE = "rule"
        const val SOURCE_AI_CONFIRM = "ai_confirm"
        const val SOURCE_CACHE = "cache"
        const val SOURCE_FALLBACK = "fallback"
        const val SOURCE_RESOLVED = "resolved"
    }
}
