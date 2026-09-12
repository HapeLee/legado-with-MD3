package io.legado.app.feature.reader.core.readaloud

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph

data class ReaderReadAloudParagraph(
    val text: String,
    val chapterPosition: Int,
    val isParagraphEnd: Boolean,
) {
    val endPosition: Int get() = chapterPosition + text.length
}

data class ReaderReadAloudChapter(
    val chapterIndex: Int,
    val title: String,
    val pageStarts: List<Int>,
    val paragraphs: List<ReaderReadAloudParagraph>,
    val pageParagraphs: List<ReaderReadAloudParagraph>,
    val chapterLength: Int,
) {
    val pageCount: Int get() = pageStarts.size

    fun pageStart(index: Int): Int = pageStarts.getOrElse(index) { pageStarts.lastOrNull() ?: 0 }

    fun pageIndexAt(position: Int): Int = pageStarts
        .indexOfLast { it <= position }
        .coerceAtLeast(0)
        .coerceAtMost((pageStarts.size - 1).coerceAtLeast(0))

    fun paragraphs(splitByPage: Boolean): List<ReaderReadAloudParagraph> =
        if (splitByPage) pageParagraphs else paragraphs

    fun paragraphIndexAtOrAfter(position: Int, splitByPage: Boolean): Int =
        paragraphs(splitByPage).indexOfFirst { it.endPosition >= position }

    fun canonicalSpeechParagraphs(): List<CanonicalSpeechParagraph> = paragraphs.mapIndexed {
        index, paragraph ->
        CanonicalSpeechParagraph(index, paragraph.text.sanitizeForSpeech(), paragraph.chapterPosition)
    }

    companion object {
        fun create(
            chapterIndex: Int,
            title: String,
            semanticContent: String,
            pageStarts: List<Int>,
        ): ReaderReadAloudChapter {
            val normalizedStarts = pageStarts
                .asSequence()
                .filter { it >= 0 }
                .distinct()
                .sorted()
                .toList()
                .ifEmpty { listOf(0) }
            val paragraphs = semanticContent.lineParagraphs()
            val pageParagraphs = paragraphs.flatMap { paragraph ->
                val cuts = normalizedStarts.filter { it > paragraph.chapterPosition && it < paragraph.endPosition }
                buildList {
                    var start = paragraph.chapterPosition
                    (cuts + paragraph.endPosition).forEach { end ->
                        if (end > start) {
                            add(ReaderReadAloudParagraph(
                                text = semanticContent.substring(start, end),
                                chapterPosition = start,
                                isParagraphEnd = end == paragraph.endPosition,
                            ))
                        }
                        start = end
                    }
                }
            }
            return ReaderReadAloudChapter(
                chapterIndex = chapterIndex,
                title = title,
                pageStarts = normalizedStarts,
                paragraphs = paragraphs,
                pageParagraphs = pageParagraphs,
                chapterLength = paragraphs.lastOrNull()?.endPosition ?: 0,
            )
        }
    }
}

private fun String.lineParagraphs(): List<ReaderReadAloudParagraph> = buildList {
    var start = 0
    this@lineParagraphs.forEachIndexed { index, character ->
        if (character == '\n') {
            if (index > start) add(ReaderReadAloudParagraph(substring(start, index), start, true))
            start = index + 1
        }
    }
    if (start < length) add(ReaderReadAloudParagraph(substring(start), start, true))
}

private fun String.sanitizeForSpeech(): String = replace(Regex("[袮祢꧁\uFFFC]"), " ")
