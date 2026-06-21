package io.legado.app.help.readaloud.role

data class ReadAloudRoleRange(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int
)

data class ReadAloudRoleUnit(
    val id: String,
    val kind: String,
    val roleType: String,
    val characterName: String,
    val characterId: Long = 0L,
    val ranges: List<ReadAloudRoleRange>,
    val text: String,
    val speakerHint: String = "",
    val emotionName: String = "",
    val emotionTag: String = "",
    val confidence: Double = 0.0,
    val needsAi: Boolean = false,
    val reason: String = "",
    val cueBefore: String = "",
    val cueAfter: String = ""
) {
    val firstParagraphIndex: Int
        get() = ranges.firstOrNull()?.paragraphIndex ?: -1

    val firstStart: Int
        get() = ranges.firstOrNull()?.start ?: 0

    fun touches(paragraphIndices: Set<Int>): Boolean {
        return ranges.any { it.paragraphIndex in paragraphIndices }
    }
}

data class ReadAloudRolePreprocessResult(
    val version: String,
    val units: List<ReadAloudRoleUnit>
)

object ReadAloudRolePreprocessor {

    const val VERSION = "builtin-dialogue-cluster-v4"

    private val defaultQuotePairs = mapOf(
        '“' to '”',
        '‘' to '’',
        '"' to '"',
        '\'' to '\'',
        '「' to '」',
        '『' to '』'
    )
    private val defaultSentencePunctuation = setOf('。', '！', '？', '…', '!', '?')
    private val defaultThoughtCuePatterns = listOf("心道", "暗道", "想道", "心想")

    private data class RuleConfig(
        val quotePairs: Map<Char, Char> = defaultQuotePairs,
        val sentencePunctuation: Set<Char> = defaultSentencePunctuation,
        val thoughtCuePatterns: List<String> = defaultThoughtCuePatterns,
        val dialogueMinLength: Int = 6,
        val emphasisMaxLength: Int = 8,
        val colonCueMaxLength: Int = 24,
        val mergeCrossParagraphQuote: Boolean = true
    ) {
        val thoughtCueRegex: Regex by lazy {
            val pattern = thoughtCuePatterns
                .filter { it.isNotBlank() }
                .joinToString("|") { Regex.escape(it) }
                .ifBlank { "心道|暗道|想道|心想" }
            Regex("(?:$pattern)\\s*[，,、：:]?\\s*$")
        }
    }

    fun process(
        paragraphs: List<String>,
        paragraphOffset: Int = 0
    ): ReadAloudRolePreprocessResult {
        val config = loadRuleConfig()
        if (paragraphs.isEmpty()) {
            return ReadAloudRolePreprocessResult(VERSION, emptyList())
        }
        val units = mutableListOf<ReadAloudRoleUnit>()
        var openQuote: OpenQuote? = null
        paragraphs.forEachIndexed { localIndex, text ->
            val paragraphIndex = paragraphOffset + localIndex
            var cursor = 0
            var index = 0

            openQuote?.let { open ->
                val closeIndex = text.indexOf(open.closeChar)
                if (closeIndex >= 0) {
                    val ranges = open.ranges + ReadAloudRoleRange(paragraphIndex, 0, closeIndex + 1)
                    units += quoteUnit(paragraphs, paragraphOffset, ranges, open.openChar, open.closeChar, config)
                    cursor = closeIndex + 1
                    index = cursor
                    openQuote = null
                } else {
                    open.ranges += ReadAloudRoleRange(paragraphIndex, 0, text.length)
                    cursor = text.length
                    index = text.length
                }
            }

            while (index < text.length) {
                val closeChar = config.quotePairs[text[index]]
                if (closeChar == null) {
                    index++
                    continue
                }
                val endQuote = findClosingQuote(text, index + 1, closeChar)
                if (endQuote >= 0) {
                    addPlainUnits(units, paragraphIndex, text, cursor, index, config)
                    val range = ReadAloudRoleRange(paragraphIndex, index, endQuote + 1)
                    units += quoteUnit(paragraphs, paragraphOffset, listOf(range), text[index], closeChar, config)
                    cursor = endQuote + 1
                    index = cursor
                } else {
                    addPlainUnits(units, paragraphIndex, text, cursor, index, config)
                    openQuote = OpenQuote(
                        openChar = text[index],
                        closeChar = closeChar,
                        ranges = mutableListOf(ReadAloudRoleRange(paragraphIndex, index, text.length))
                    )
                    cursor = text.length
                    index = text.length
                }
            }
            addPlainUnits(units, paragraphIndex, text, cursor, text.length, config)
        }

        openQuote?.let { open ->
            if (open.ranges.isNotEmpty()) {
                units += quoteUnit(paragraphs, paragraphOffset, open.ranges, open.openChar, open.closeChar, config)
            }
        }

        return ReadAloudRolePreprocessResult(
            version = VERSION,
            units = units
                .filter { it.ranges.isNotEmpty() && it.text.isNotBlank() }
                .sortedWith(compareBy<ReadAloudRoleUnit> { it.firstParagraphIndex }.thenBy { it.firstStart })
        )
    }

    private fun addPlainUnits(
        units: MutableList<ReadAloudRoleUnit>,
        paragraphIndex: Int,
        text: String,
        start: Int,
        end: Int,
        config: RuleConfig
    ) {
        if (start >= end) return
        val raw = text.substring(start, end)
        if (raw.isBlank()) return
        val colonIndex = raw.indexOfFirst { it == '：' || it == ':' }
        if (colonIndex in 1..config.colonCueMaxLength) {
            val prefix = raw.substring(0, colonIndex).trim()
            val speechStart = start + colonIndex + 1
            if (speechStart < end && prefix.isNotBlank() && isSimpleCuePrefix(prefix)) {
                units += unit(
                    kind = "dialogue",
                    roleType = "character",
                    characterName = "",
                    ranges = listOf(ReadAloudRoleRange(paragraphIndex, start, end)),
                    text = raw,
                    confidence = 0.46,
                    needsAi = true,
                    reason = "colon_dialogue"
                )
                return
            }
        }
        units += unit(
            kind = "narrator",
            roleType = "narrator",
            characterName = "旁白",
            ranges = listOf(ReadAloudRoleRange(paragraphIndex, start, end)),
            text = raw,
            confidence = 0.82,
            reason = "plain_text"
        )
    }

    private fun quoteUnit(
        paragraphs: List<String>,
        paragraphOffset: Int,
        ranges: List<ReadAloudRoleRange>,
        openChar: Char,
        closeChar: Char,
        config: RuleConfig
    ): ReadAloudRoleUnit {
        val text = ranges.joinToString("\n") { range ->
            val paragraph = paragraphs.getOrNull(range.paragraphIndex - paragraphOffset).orEmpty()
            paragraph.substring(range.start.coerceIn(0, paragraph.length), range.end.coerceIn(0, paragraph.length))
        }
        val prefix = contextBefore(paragraphs, paragraphOffset, ranges.first())
        val suffix = contextAfter(paragraphs, paragraphOffset, ranges.last())
        val inner = text
            .trim()
            .trim(openChar, closeChar, '“', '”', '‘', '’', '"', '\'', '「', '」', '『', '』')
            .trim()
        val isCrossParagraph = ranges.map { it.paragraphIndex }.distinct().size > 1
        val looksLikeDialogue = (isCrossParagraph && config.mergeCrossParagraphQuote) ||
            inner.length >= config.dialogueMinLength ||
            inner.any { it in config.sentencePunctuation }
        val thought = config.thoughtCueRegex.containsMatchIn(prefix.takeLast(40)) ||
            suffix.take(40).contains("心想") ||
            suffix.take(40).contains("心道") ||
            suffix.take(40).contains("暗道")

        if (!looksLikeDialogue) {
            val kind = if (inner.length <= config.emphasisMaxLength) "emphasis" else "citation"
            return unit(
                kind = kind,
                roleType = "narrator",
                characterName = "旁白",
                ranges = ranges,
                text = text,
                confidence = 0.72,
                needsAi = false,
                reason = "quoted_$kind",
                cueBefore = prefix,
                cueAfter = suffix
            )
        }

        val roleType = if (thought) "thought" else "character"
        return unit(
            kind = if (thought) "thought" else "dialogue",
            roleType = roleType,
            characterName = "",
            ranges = ranges,
            text = text,
            confidence = 0.46,
            needsAi = true,
            reason = "quoted_dialogue",
            cueBefore = prefix,
            cueAfter = suffix
        )
    }

    private fun loadRuleConfig(): RuleConfig {
        return runCatching {
            val config = ReadAloudPreprocessRuleConfig.current()
            RuleConfig(
                quotePairs = config.quotePairs
                    .mapNotNull { pair ->
                        val open = pair.open.firstOrNull() ?: return@mapNotNull null
                        val close = pair.close.firstOrNull() ?: return@mapNotNull null
                        open to close
                    }
                    .toMap()
                    .ifEmpty { defaultQuotePairs },
                sentencePunctuation = config.sentencePunctuation
                    .takeIf { it.isNotBlank() }
                    ?.toSet()
                    ?: defaultSentencePunctuation,
                thoughtCuePatterns = config.thoughtCuePatterns.ifEmpty { defaultThoughtCuePatterns },
                dialogueMinLength = config.dialogueMinLength,
                emphasisMaxLength = config.emphasisMaxLength,
                colonCueMaxLength = config.colonCueMaxLength,
                mergeCrossParagraphQuote = config.mergeCrossParagraphQuote
            )
        }.getOrElse {
            RuleConfig()
        }
    }

    private fun unit(
        kind: String,
        roleType: String,
        characterName: String,
        characterId: Long = 0L,
        ranges: List<ReadAloudRoleRange>,
        text: String,
        speakerHint: String = "",
        emotionName: String = "",
        emotionTag: String = "",
        confidence: Double = 0.0,
        needsAi: Boolean = false,
        reason: String = "",
        cueBefore: String = "",
        cueAfter: String = ""
    ): ReadAloudRoleUnit {
        val first = ranges.first()
        val last = ranges.last()
        val hash = Integer.toHexString(text.hashCode())
        val id = "u_${first.paragraphIndex}_${first.start}_${last.paragraphIndex}_${last.end}_${kind}_$hash"
        return ReadAloudRoleUnit(
            id = id,
            kind = kind,
            roleType = roleType,
            characterName = characterName,
            characterId = characterId,
            ranges = ranges,
            text = text,
            speakerHint = speakerHint,
            emotionName = emotionName,
            emotionTag = emotionTag,
            confidence = confidence.coerceIn(0.0, 1.0),
            needsAi = needsAi,
            reason = reason,
            cueBefore = cueBefore.take(160),
            cueAfter = cueAfter.take(160)
        )
    }

    private fun findClosingQuote(text: String, start: Int, closeChar: Char): Int {
        if (start >= text.length) return -1
        return text.indexOf(closeChar, start)
    }

    private fun contextBefore(
        paragraphs: List<String>,
        paragraphOffset: Int,
        range: ReadAloudRoleRange
    ): String {
        val localIndex = range.paragraphIndex - paragraphOffset
        val current = paragraphs.getOrNull(localIndex).orEmpty()
            .substring(0, range.start.coerceIn(0, paragraphs.getOrNull(localIndex).orEmpty().length))
        val previous = paragraphs.getOrNull(localIndex - 1).orEmpty().takeLast(40)
        return (previous + "\n" + current).takeLast(100)
    }

    private fun contextAfter(
        paragraphs: List<String>,
        paragraphOffset: Int,
        range: ReadAloudRoleRange
    ): String {
        val localIndex = range.paragraphIndex - paragraphOffset
        val currentParagraph = paragraphs.getOrNull(localIndex).orEmpty()
        val current = currentParagraph.substring(range.end.coerceIn(0, currentParagraph.length))
        val next = paragraphs.getOrNull(localIndex + 1).orEmpty().take(40)
        return (current + "\n" + next).take(100)
    }

    private fun isSimpleCuePrefix(value: String): Boolean {
        val text = value.trim().trim('“', '”', '‘', '’', '"', '\'', '，', ',', '。', '：', ':')
        if (text.length !in 1..24) return false
        if (text.any { it == '\n' || it == '\r' || it == '\t' }) return false
        return text.none { it in "！？；!?;（）()《》<>[]【】" }
    }

    private data class OpenQuote(
        val openChar: Char,
        val closeChar: Char,
        val ranges: MutableList<ReadAloudRoleRange>
    )
}
