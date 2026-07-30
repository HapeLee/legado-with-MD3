package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph

data class AiSpeechAtom(
    val id: String,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

object AiSpeechAtomizer {
    fun atomize(paragraph: CanonicalSpeechParagraph): List<AiSpeechAtom> {
        if (paragraph.text.isEmpty()) return emptyList()
        val boundaries = sortedSetOf(0, paragraph.text.length)
        var straightDoubleOpen = false
        var straightSingleOpen = false
        var index = 0
        while (index < paragraph.text.length) {
            val char = paragraph.text[index]
            when {
                char == '"' -> {
                    if (straightDoubleOpen) boundaries += index + 1 else boundaries += index
                    straightDoubleOpen = !straightDoubleOpen
                }
                char == '\'' && !paragraph.text.isWordApostrophe(index) -> {
                    if (straightSingleOpen) boundaries += index + 1 else boundaries += index
                    straightSingleOpen = !straightSingleOpen
                }
                char in OPEN_QUOTES -> boundaries += index
                char in CLOSE_QUOTES -> boundaries += index + 1
            }
            if (char in SENTENCE_ENDS) {
                var end = index + 1
                while (end < paragraph.text.length && paragraph.text[end] in ALL_CLOSE_QUOTES) {
                    if (paragraph.text[end] == '"') straightDoubleOpen = false
                    if (paragraph.text[end] == '\'') straightSingleOpen = false
                    end++
                }
                boundaries += end
                index = end - 1
            }
            index++
        }
        return boundaries.zipWithNext().mapNotNull { (start, end) ->
            if (start >= end) null else AiSpeechAtom(
                id = "p${paragraph.index}:$start:$end",
                paragraphIndex = paragraph.index,
                start = start,
                end = end,
                text = paragraph.text.substring(start, end),
            )
        }
    }

    private fun String.isWordApostrophe(index: Int): Boolean =
        getOrNull(index - 1)?.isLetterOrDigit() == true &&
            getOrNull(index + 1)?.isLetterOrDigit() == true

    private val OPEN_QUOTES = setOf('“', '‘', '「', '『')
    private val CLOSE_QUOTES = setOf('”', '’', '」', '』')
    private val ALL_CLOSE_QUOTES = CLOSE_QUOTES + setOf('"', '\'')
    private val SENTENCE_ENDS = setOf('。', '！', '？', '!', '?', '；', ';')
}
