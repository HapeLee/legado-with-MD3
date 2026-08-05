package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.ui.book.read.page.entities.TextChapter

fun TextChapter.toCanonicalSpeechParagraphs(): List<CanonicalSpeechParagraph> {
    if (!isCompleted) return emptyList()
    return paragraphs
        .asSequence()
        .filter { it.textLines.isNotEmpty() }
        .mapIndexed { fallbackIndex, paragraph ->
            CanonicalSpeechParagraph(
                index = (paragraph.realNum - 1).takeIf { it >= 0 } ?: fallbackIndex,
                text = paragraph.text,
                chapterPosition = paragraph.chapterPosition,
            )
        }
        .toList()
}
