package io.legado.app.help.readaloud.role

data class ReadAloudSoundEffectCandidate(
    val id: String,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val cue: String,
    val text: String,
    val context: String,
    val reason: String
)

data class ReadAloudSoundEffectPreprocessResult(
    val version: String,
    val candidates: List<ReadAloudSoundEffectCandidate>
)

object ReadAloudSoundEffectPreprocessor {

    const val VERSION = "builtin-sfx-candidate-v1"

    fun process(
        paragraphs: List<String>,
        paragraphOffset: Int = 0,
        config: ReadAloudPreprocessRuleConfig = ReadAloudPreprocessRuleConfig.current()
    ): ReadAloudSoundEffectPreprocessResult {
        if (paragraphs.isEmpty()) {
            return ReadAloudSoundEffectPreprocessResult(VERSION, emptyList())
        }
        val cuePatterns = config.soundEffectCuePatterns
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cuePatterns.isEmpty()) {
            return ReadAloudSoundEffectPreprocessResult(VERSION, emptyList())
        }
        val excludes = config.soundEffectExcludePatterns
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val contextChars = config.soundEffectContextChars.coerceIn(8, 120)
        val candidates = mutableListOf<ReadAloudSoundEffectCandidate>()
        paragraphs.forEachIndexed { localIndex, paragraph ->
            val paragraphIndex = paragraphOffset + localIndex
            cuePatterns.forEach { cue ->
                var start = paragraph.indexOf(cue)
                while (start >= 0) {
                    val end = start + cue.length
                    val contextStart = (start - contextChars).coerceAtLeast(0)
                    val contextEnd = (end + contextChars).coerceAtMost(paragraph.length)
                    val context = paragraph.substring(contextStart, contextEnd)
                    if (excludes.none { context.contains(it) }) {
                        val hash = Integer.toHexString("$paragraphIndex|$start|$cue|$context".hashCode())
                        candidates += ReadAloudSoundEffectCandidate(
                            id = "sfx_${paragraphIndex}_${start}_$hash",
                            paragraphIndex = paragraphIndex,
                            start = start,
                            end = end,
                            cue = cue,
                            text = paragraph.substring(start, end),
                            context = context,
                            reason = "cue_pattern"
                        )
                    }
                    start = paragraph.indexOf(cue, end)
                }
            }
        }
        return ReadAloudSoundEffectPreprocessResult(
            version = VERSION,
            candidates = candidates
                .distinctBy { it.paragraphIndex to it.start }
                .sortedWith(compareBy<ReadAloudSoundEffectCandidate> { it.paragraphIndex }.thenBy { it.start })
        )
    }
}
