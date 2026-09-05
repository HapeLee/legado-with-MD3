package io.legado.app.feature.reader.core.readaloud

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph

/** Android app adapter retained while the read-aloud domain remains app-owned. */
fun ReaderReadAloudChapter.canonicalSpeechParagraphs(): List<CanonicalSpeechParagraph> =
    speechParagraphs().map { paragraph ->
        CanonicalSpeechParagraph(
            index = paragraph.index,
            text = paragraph.text,
            chapterPosition = paragraph.chapterPosition,
        )
    }
