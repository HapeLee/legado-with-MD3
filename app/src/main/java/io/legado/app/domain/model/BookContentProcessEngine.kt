package io.legado.app.domain.model

import io.legado.app.data.entities.BookContentProcess
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlin.math.abs

object BookContentProcessEngine {

    data class ApplyResult(
        val text: String,
        val effectiveProcesses: List<BookContentProcess>,
    )

    fun apply(
        content: String,
        processes: List<BookContentProcess>,
    ): ApplyResult {
        if (content.isEmpty() || processes.isEmpty()) {
            return ApplyResult(content, emptyList())
        }
        var output = content
        val effectiveProcesses = arrayListOf<BookContentProcess>()
        processes
            .filter {
                it.enabled &&
                    it.status == BookContentProcess.STATUS_ACTIVE &&
                    it.stage == BookContentProcess.STAGE_CONTENT
            }
            .forEach { process ->
                val anchor = GSON.fromJsonObject<TextProcessAnchor>(process.anchorJson)
                    .getOrNull()
                    ?: return@forEach
                val action = GSON.fromJsonObject<TextProcessAction>(process.actionJson)
                    .getOrNull()
                    ?: return@forEach
                val range = findTargetRange(output, anchor) ?: return@forEach
                val next = when (action.type) {
                    TextProcessAction.TYPE_REPLACE -> {
                        output.replaceRange(
                            range,
                            normalizeProcessText(action.replacement.orEmpty())
                        )
                    }

                    TextProcessAction.TYPE_DELETE -> output.removeRange(range)
                    TextProcessAction.TYPE_INSERT_BEFORE -> {
                        output.replaceRange(
                            range.first,
                            range.first,
                            normalizeProcessText(action.text.orEmpty())
                        )
                    }

                    TextProcessAction.TYPE_INSERT_AFTER -> {
                        output.replaceRange(
                            range.last + 1,
                            range.last + 1,
                            normalizeProcessText(action.text.orEmpty())
                        )
                    }

                    else -> output
                }
                if (next != output) {
                    output = next
                    effectiveProcesses.add(process)
                }
            }
        return ApplyResult(output, effectiveProcesses)
    }

    fun normalizeProcessText(text: String): String {
        return text.lines()
            .joinToString("\n") { line ->
                line.trim { it.code <= 0x20 || it == '　' }
            }
            .trim()
    }

    private fun findTargetRange(
        content: String,
        anchor: TextProcessAnchor,
    ): IntRange? {
        val candidates = listOf(
            anchor.selectedText,
            normalizeProcessText(anchor.selectedText),
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val approximatePosition = anchor.chapterPosition ?: 0
        for (candidate in candidates) {
            val start = findClosestOccurrence(content, candidate, approximatePosition)
            if (start >= 0) {
                return start until start + candidate.length
            }
        }
        val normalizedContent = normalizeForMatching(content)
        for (candidate in candidates) {
            val normalizedCandidate = normalizeForMatching(candidate).text
            if (normalizedCandidate.isEmpty()) continue
            val range = findClosestNormalizedOccurrence(
                normalizedContent = normalizedContent,
                normalizedSelectedText = normalizedCandidate,
                approximatePosition = approximatePosition,
            )
            if (range != null) return range
        }
        return null
    }

    private fun normalizeForMatching(text: String): NormalizedText {
        val normalized = StringBuilder(text.length)
        val sourceIndices = ArrayList<Int>(text.length)
        text.forEachIndexed { index, char ->
            if (!char.isProcessWhitespace()) {
                normalized.append(char)
                sourceIndices.add(index)
            }
        }
        return NormalizedText(normalized.toString(), sourceIndices)
    }

    private fun Char.isProcessWhitespace(): Boolean {
        return isWhitespace() || this == '　'
    }

    private fun findClosestNormalizedOccurrence(
        normalizedContent: NormalizedText,
        normalizedSelectedText: String,
        approximatePosition: Int,
    ): IntRange? {
        var match = normalizedContent.text.indexOf(normalizedSelectedText)
        if (match < 0) return null
        var closestStart = normalizedContent.sourceIndices[match]
        var closestEnd = normalizedContent.sourceIndices[match + normalizedSelectedText.length - 1] + 1
        var closestDistance = abs(closestStart - approximatePosition)
        while (match >= 0) {
            val sourceStart = normalizedContent.sourceIndices[match]
            val distance = abs(sourceStart - approximatePosition)
            if (distance < closestDistance) {
                closestStart = sourceStart
                closestEnd = normalizedContent.sourceIndices[match + normalizedSelectedText.length - 1] + 1
                closestDistance = distance
            }
            match = normalizedContent.text.indexOf(normalizedSelectedText, match + 1)
        }
        return closestStart until closestEnd
    }

    private fun findClosestOccurrence(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Int {
        var match = content.indexOf(selectedText)
        if (match < 0) return -1
        var closest = match
        var closestDistance = abs(match - approximatePosition)
        while (match >= 0) {
            val distance = abs(match - approximatePosition)
            if (distance < closestDistance) {
                closest = match
                closestDistance = distance
            }
            match = content.indexOf(selectedText, match + 1)
        }
        return closest
    }

    private data class NormalizedText(
        val text: String,
        val sourceIndices: List<Int>,
    )
}
