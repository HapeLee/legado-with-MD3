package io.legado.app.feature.reader.core.semantic

import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock

/** Validates existing projections, without parsing, repairing or owning chapter content. */
internal object ReaderSemanticContract {
    const val IMAGE_CHAR: Char = '\uFFFC'
    const val PARAGRAPH_BREAK: Char = '\n'

    data class Violation(val position: Int, val message: String)

    fun validate(source: ReaderChapterSource): List<Violation> = buildList {
        val text = source.semanticContent
        if (source.characterCount != text.length) add(Violation(0, "characterCount differs from semantic length"))
        var previousEnd = 0L
        source.blocks.forEach { block ->
            if (block is ReaderChapterSourceBlock.Text && block.isTitle) return@forEach
            val pos = block.chapterPosition
            if (!ReaderSemanticLocation.isValidOffset(text, pos) || pos < previousEnd) {
                add(Violation(pos, "Invalid or unordered block offset"))
            }
            when (block) {
                is ReaderChapterSourceBlock.Paragraph -> {
                    addAll(validateInlines(text, block.items))
                    if (block.items.any { it.chapterPosition < pos }) {
                        add(Violation(pos, "Inline precedes its paragraph"))
                    }
                }
                is ReaderChapterSourceBlock.Text -> if (!ReaderSemanticLocation.matchesAt(text, pos, block.value)) {
                    add(Violation(pos, "Text differs from semantic content"))
                }
                is ReaderChapterSourceBlock.Image -> if (text.getOrNull(pos) != IMAGE_CHAR) {
                    add(Violation(pos, "Image placeholder missing"))
                }
                is ReaderChapterSourceBlock.Html -> if (block.semanticLength < 0 ||
                    block.semanticLength > text.length - pos ||
                    !ReaderSemanticLocation.isValidOffset(text, pos + block.semanticLength)) {
                    add(Violation(pos, "Invalid HTML extent"))
                }
                is ReaderChapterSourceBlock.PageBreak -> Unit
            }
            previousEnd = when (block) {
                is ReaderChapterSourceBlock.Text -> pos.toLong() + block.value.length
                is ReaderChapterSourceBlock.Image -> pos.toLong() + 1
                is ReaderChapterSourceBlock.Html -> pos.toLong() + block.semanticLength
                is ReaderChapterSourceBlock.Paragraph -> block.items.maxOfOrNull {
                    it.chapterPosition.toLong() + inlineLength(it)
                } ?: pos.toLong()
                is ReaderChapterSourceBlock.PageBreak -> pos.toLong()
            }
        }
    }

    private fun inlineLength(item: ReaderChapterInlineSource): Int = when (item) {
        is ReaderChapterInlineSource.Text -> item.value.length
        is ReaderChapterInlineSource.Image, is ReaderChapterInlineSource.BlankLine -> 1
        is ReaderChapterInlineSource.BgmScene -> 0
    }

    /** [origin] is the chapter offset of an independently projected HTML fragment. */
    fun validateInlines(text: String, items: List<ReaderChapterInlineSource>, origin: Int = 0): List<Violation> = buildList {
        var previousEnd = 0
        items.forEach { item ->
            val pos = item.chapterPosition - origin
            if (!ReaderSemanticLocation.isValidOffset(text, pos) || pos < previousEnd) {
                add(Violation(item.chapterPosition, "Invalid or overlapping inline offset"))
            }
            val expected = when (item) {
                is ReaderChapterInlineSource.Text -> item.value
                is ReaderChapterInlineSource.Image -> IMAGE_CHAR.toString()
                is ReaderChapterInlineSource.BlankLine -> PARAGRAPH_BREAK.toString()
                is ReaderChapterInlineSource.BgmScene -> ""
            }
            if (expected.isNotEmpty() && !ReaderSemanticLocation.matchesAt(text, pos, expected)) {
                add(Violation(item.chapterPosition, "Inline differs from semantic content"))
            }
            previousEnd = pos + expected.length
        }
    }
}
