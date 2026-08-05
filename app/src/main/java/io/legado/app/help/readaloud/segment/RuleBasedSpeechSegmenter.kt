package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.readaloud.SpeechSegmentDraft

/**
 * Performs deterministic, pagination-independent speech segmentation.
 *
 * Every non-empty input paragraph is fully covered by ordered, non-overlapping output ranges.
 * Character identity resolution is intentionally left to a later stage.
 */
object RuleBasedSpeechSegmenter {

    const val VERSION = "rule-segmenter-v2-emotion"

    private val quotePairs = mapOf(
        '“' to '”',
        '‘' to '’',
        '「' to '」',
        '『' to '』',
        '"' to '"',
        '\'' to '\'',
    )
    private val sentencePunctuation = setOf('。', '！', '？', '…', '!', '?')
    private val thoughtCueRegex = Regex("(?:心想|心道|暗道|想道)[，,：:\\s]*$")
    private val speechCueRegex = Regex(
        "(?:说|说道|问|问道|答|答道|喊|喊道|叫|叫道|喝道|笑道|低声道|沉声道|怒道|开口道)$"
    )

    fun segment(paragraphs: List<CanonicalSpeechParagraph>): List<SpeechSegmentDraft> {
        if (paragraphs.isEmpty()) return emptyList()
        val result = mutableListOf<SpeechSegmentDraft>()
        var openQuote: OpenQuote? = null

        paragraphs.forEach { paragraph ->
            val text = paragraph.text
            if (text.isEmpty()) return@forEach
            var cursor = 0

            openQuote?.let { quote ->
                val close = text.indexOf(quote.close)
                if (close < 0) {
                    result += paragraph.segment(
                        0,
                        text.length,
                        quote.roleType,
                        0.68f,
                        quote.emotion,
                    )
                    return@forEach
                }
                result += paragraph.segment(0, close + 1, quote.roleType, 0.68f, quote.emotion)
                cursor = close + 1
                openQuote = null
            }

            while (cursor < text.length) {
                val openIndex = text.indexOfNextQuote(cursor)
                if (openIndex < 0) {
                    addPlainSegments(result, paragraph, cursor, text.length)
                    break
                }
                addPlainSegments(result, paragraph, cursor, openIndex)
                val open = text[openIndex]
                val closeChar = quotePairs.getValue(open)
                val closeIndex = text.indexOf(closeChar, openIndex + 1)
                val roleType = quoteRole(text, openIndex, closeIndex)
                val context = text.substring((openIndex - 32).coerceAtLeast(0), openIndex)
                val spokenText = text.substring(
                    openIndex + 1,
                    if (closeIndex < 0) text.length else closeIndex,
                )
                val emotion = if (roleType == SpeechRoleType.Narrator) {
                    ""
                } else {
                    SpeechEmotionDetector.detect(spokenText, context).storageValue
                }
                if (closeIndex < 0) {
                    result += paragraph.segment(
                        openIndex,
                        text.length,
                        roleType,
                        0.68f,
                        emotion,
                    )
                    openQuote = OpenQuote(closeChar, roleType, emotion)
                    break
                }
                result += paragraph.segment(
                    openIndex,
                    closeIndex + 1,
                    roleType,
                    0.72f,
                    emotion,
                )
                cursor = closeIndex + 1
            }
        }

        return mergeAdjacent(result)
    }

    private fun addPlainSegments(
        output: MutableList<SpeechSegmentDraft>,
        paragraph: CanonicalSpeechParagraph,
        start: Int,
        end: Int,
    ) {
        if (start >= end) return
        val text = paragraph.text
        val colon = (start until end).firstOrNull { text[it] == '：' || text[it] == ':' }
        if (colon != null) {
            val cue = text.substring(start, colon).trimEnd()
            if (cue.length in 1..24 && speechCueRegex.containsMatchIn(cue)) {
                output += paragraph.segment(start, colon + 1, SpeechRoleType.Narrator, 0.9f)
                if (colon + 1 < end) {
                    val emotion = SpeechEmotionDetector.detect(
                        text.substring(colon + 1, end),
                        cue,
                    ).storageValue
                    output += paragraph.segment(
                        colon + 1,
                        end,
                        SpeechRoleType.Character,
                        0.58f,
                        emotion,
                    )
                }
                return
            }
        }
        output += paragraph.segment(start, end, SpeechRoleType.Narrator, 0.95f)
    }

    private fun quoteRole(text: String, openIndex: Int, closeIndex: Int): SpeechRoleType {
        val contextBefore = text.substring((openIndex - 24).coerceAtLeast(0), openIndex)
        if (thoughtCueRegex.containsMatchIn(contextBefore)) return SpeechRoleType.Thought
        if (closeIndex < 0) return SpeechRoleType.Character
        val inner = text.substring(openIndex + 1, closeIndex).trim()
        return if (inner.length >= 6 || inner.any(sentencePunctuation::contains)) {
            SpeechRoleType.Character
        } else {
            SpeechRoleType.Narrator
        }
    }

    private fun String.indexOfNextQuote(start: Int): Int {
        var index = start
        while (index < length) {
            val value = this[index]
            if (value in quotePairs && !isWordApostrophe(index)) return index
            index++
        }
        return -1
    }

    private fun String.isWordApostrophe(index: Int): Boolean {
        if (this[index] != '\'') return false
        return getOrNull(index - 1)?.isLetterOrDigit() == true &&
            getOrNull(index + 1)?.isLetterOrDigit() == true
    }

    private fun CanonicalSpeechParagraph.segment(
        start: Int,
        end: Int,
        roleType: SpeechRoleType,
        confidence: Float,
        emotion: String = "",
    ) = SpeechSegmentDraft(
        paragraphIndex = index,
        start = start,
        end = end,
        chapterPosition = chapterPosition + start,
        text = text.substring(start, end),
        roleType = roleType,
        confidence = confidence,
        emotion = emotion,
    )

    private fun mergeAdjacent(segments: List<SpeechSegmentDraft>): List<SpeechSegmentDraft> {
        if (segments.size < 2) return segments
        val result = mutableListOf<SpeechSegmentDraft>()
        segments.forEach { segment ->
            val previous = result.lastOrNull()
            if (
                previous != null &&
                previous.paragraphIndex == segment.paragraphIndex &&
                previous.end == segment.start &&
                previous.roleType == segment.roleType &&
                previous.source == segment.source &&
                previous.emotion == segment.emotion
            ) {
                result[result.lastIndex] = previous.copy(
                    end = segment.end,
                    text = previous.text + segment.text,
                    confidence = minOf(previous.confidence, segment.confidence),
                )
            } else {
                result += segment
            }
        }
        return result
    }

    private data class OpenQuote(
        val close: Char,
        val roleType: SpeechRoleType,
        val emotion: String,
    )
}
