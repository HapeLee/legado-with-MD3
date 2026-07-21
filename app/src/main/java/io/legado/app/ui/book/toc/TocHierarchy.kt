package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter

/** Filters a pre-order flattened TOC while preserving nested collapse boundaries. */
internal fun filterCollapsedToc(
    items: List<TocDomainItem>,
    collapsedIds: Set<Int>,
): List<TocDomainItem> = buildList {
    val collapsedAncestorLevels = ArrayDeque<Int>()
    for (item in items) {
        val level = item.chapter.tocLevel
        while (collapsedAncestorLevels.lastOrNull()?.let { it >= level } == true) {
            collapsedAncestorLevels.removeLast()
        }

        if (collapsedAncestorLevels.isEmpty()) {
            add(item)
        }
        if (item.chapter.isVolume && item.chapter.index in collapsedIds) {
            collapsedAncestorLevels.addLast(level)
        }
    }
}

/** Reverses siblings at every level and keeps every parent before its descendants. */
internal fun List<BookChapter>.reverseTocHierarchy(): List<BookChapter> {
    if (size < 2) return this

    data class TocNode(
        val chapter: BookChapter,
        val children: MutableList<TocNode> = mutableListOf(),
    )

    val roots = mutableListOf<TocNode>()
    val ancestors = ArrayDeque<TocNode>()
    for (chapter in this) {
        while (ancestors.size > chapter.tocLevel) {
            ancestors.removeLast()
        }
        val node = TocNode(chapter)
        ancestors.lastOrNull()?.children?.add(node) ?: roots.add(node)
        ancestors.addLast(node)
    }

    fun MutableList<TocNode>.appendReversedTo(result: MutableList<BookChapter>) {
        asReversed().forEach { node ->
            result.add(node.chapter)
            node.children.appendReversedTo(result)
        }
    }

    return buildList(size) { roots.appendReversedTo(this) }
}
