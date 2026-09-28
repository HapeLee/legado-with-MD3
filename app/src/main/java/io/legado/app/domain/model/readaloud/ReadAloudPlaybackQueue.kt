package io.legado.app.domain.model.readaloud

data class ReadAloudPlaybackCue(
    val text: String,
    val chapterStart: Int,
    val chapterEnd: Int,
    val paragraphIndex: Int,
    val voice: ReadAloudVoice?,
    val fallbackVoices: List<ReadAloudVoice>,
    val roleType: SpeechRoleType,
    val characterId: String?,
    val emotion: String = "",
    val characterPerformance: CharacterPerformanceProfile? = null,
    val isChapterTitle: Boolean = false,
) {
    init {
        require(chapterStart >= 0)
        if (isChapterTitle) {
            require(chapterStart == 0 && chapterEnd == 0)
        } else {
            require(chapterEnd == chapterStart + text.length)
        }
    }
}

data class ReadAloudPlaybackCursor(
    val cueIndex: Int,
    val offset: Int,
)

data class ReadAloudPlaybackInfo(
    val chapterPosition: Int = 0,
    val chapterLength: Int = 0,
    val text: String = "",
    val engineName: String = "",
    val characterName: String = "",
    val roleType: SpeechRoleType = SpeechRoleType.Narrator,
    val chapterTitle: String = "",
    val chapterIndex: Int = -1,
)

/** Position-based playback queue independent from reader pagination. */
class ReadAloudPlaybackQueue private constructor(
    val cues: List<ReadAloudPlaybackCue>,
) {

    val isEmpty: Boolean get() = cues.isEmpty()
    val leadingTitleCueCount: Int
        get() = cues.indexOfFirst { !it.isChapterTitle }
            .let { if (it < 0) cues.size else it }

    fun cursorAt(chapterPosition: Int): ReadAloudPlaybackCursor? {
        val bodyStartIndex = leadingTitleCueCount
        if (bodyStartIndex >= cues.size) return null
        val position = chapterPosition.coerceAtLeast(0)
        val bodyCues = cues.subList(bodyStartIndex, cues.size)
        val containingIndex = bodyCues.binarySearch { cue ->
            when {
                cue.chapterEnd <= position -> -1
                cue.chapterStart > position -> 1
                else -> 0
            }
        }
        val index = if (containingIndex >= 0) {
            containingIndex
        } else {
            (-containingIndex - 1).coerceAtMost(bodyCues.lastIndex)
        }
        val cueIndex = bodyStartIndex + index
        val cue = cues[cueIndex]
        return ReadAloudPlaybackCursor(
            cueIndex = cueIndex,
            offset = (position - cue.chapterStart).coerceIn(0, cue.text.length),
        )
    }

    fun previous(cursor: ReadAloudPlaybackCursor): ReadAloudPlaybackCursor? =
        (cursor.cueIndex - 1).takeIf { it in cues.indices }
            ?.let { ReadAloudPlaybackCursor(it, 0) }

    fun next(cursor: ReadAloudPlaybackCursor): ReadAloudPlaybackCursor? =
        (cursor.cueIndex + 1).takeIf { it in cues.indices }
            ?.let { ReadAloudPlaybackCursor(it, 0) }

    fun withChapterTitle(title: String): ReadAloudPlaybackQueue {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty() || isEmpty || cues.first().isChapterTitle) return this
        val titleCue = ReadAloudPlaybackCue(
            text = normalizedTitle,
            chapterStart = 0,
            chapterEnd = 0,
            paragraphIndex = -1,
            voice = null,
            fallbackVoices = emptyList(),
            roleType = SpeechRoleType.Narrator,
            characterId = null,
            isChapterTitle = true,
        )
        return ReadAloudPlaybackQueue(listOf(titleCue) + cues)
    }

    companion object {
        val Empty = ReadAloudPlaybackQueue(emptyList())

        fun from(plan: List<SpeechPlanItem>): ReadAloudPlaybackQueue {
            if (plan.isEmpty()) return Empty
            val cues = plan.map { item ->
                val segment = item.segment
                ReadAloudPlaybackCue(
                    text = segment.text,
                    chapterStart = segment.chapterPosition,
                    chapterEnd = segment.chapterPosition + segment.text.length,
                    paragraphIndex = segment.paragraphIndex,
                    voice = item.voice,
                    fallbackVoices = item.fallbackVoices,
                    roleType = segment.roleType,
                    characterId = segment.characterId,
                    emotion = segment.emotion,
                    characterPerformance = item.characterPerformance,
                )
            }.sortedWith(compareBy(ReadAloudPlaybackCue::chapterStart, ReadAloudPlaybackCue::chapterEnd))
            require(cues.zipWithNext().none { (left, right) -> left.chapterEnd > right.chapterStart }) {
                "Playback cues must not overlap"
            }
            return ReadAloudPlaybackQueue(cues)
        }
    }
}
