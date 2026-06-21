package io.legado.app.ui.book.read.page.entities

data class ReadAloudCue(
    val index: Int,
    val text: String,
    val chapterPosition: Int,
    val pageIndex: Int,
    val pageStartPos: Int,
    val key: String
)

fun TextChapter.buildReadAloudCues(pageSplit: Boolean): List<ReadAloudCue> {
    if (!isCompleted) return emptyList()
    val paragraphs = getParagraphs(pageSplit)
    if (paragraphs.isEmpty()) return emptyList()
    val cues = ArrayList<ReadAloudCue>(paragraphs.size)
    paragraphs.forEach { paragraph ->
        val text = ReadAloudTextCleaner.cleanVisibleText(paragraph.text, keepLineBreaks = false)
        if (text.isBlank()) return@forEach
        val chapterPosition = paragraph.chapterPosition.coerceAtLeast(0)
        val pageIndex = getPageIndexByCharIndex(chapterPosition)
        if (pageIndex < 0) return@forEach
        val pageStartPos = (chapterPosition - getReadLength(pageIndex)).coerceAtLeast(0)
        cues.add(
            ReadAloudCue(
                index = cues.size,
                text = text,
                chapterPosition = chapterPosition,
                pageIndex = pageIndex,
                pageStartPos = pageStartPos,
                key = "${chapter?.index ?: 0}:$chapterPosition:${paragraph.realNum}:${text.hashCode()}"
            )
        )
    }
    return cues
}

fun List<ReadAloudCue>.indexForChapterPosition(chapterPosition: Int): Int {
    if (isEmpty()) return -1
    val exact = indexOfFirst { it.chapterPosition == chapterPosition }
    if (exact >= 0) return exact
    return indexOfLast { it.chapterPosition <= chapterPosition }
        .coerceIn(0, lastIndex)
}
