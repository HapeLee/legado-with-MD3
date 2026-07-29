package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter

/**
 * 过滤折叠后的目录列表，保留被折叠卷的标题，隐藏其子项。
 *
 * 支持两种场景：
 * 1. 扁平 TXT 分卷（所有 tocLevel 均为 0）：通过 isVolume 标志界定卷边界，
 *    折叠某卷后隐藏该卷到下一卷之间的所有非卷章节。
 * 2. 多层级目录（存在 tocLevel > 0 的章节）：用栈记录被折叠的祖先层级，
 *    栈非空时跳过所有更深层的子项。
 */
internal fun filterCollapsedToc(
    items: List<TocDomainItem>,
    collapsedIds: Set<Int>,
): List<TocDomainItem> = buildList {
    // 扁平 TXT 分卷场景：无层级信息，仅靠 isVolume 判定卷边界
    if (items.none { it.chapter.tocLevel > 0 }) {
        var collapsed = false
        for (item in items) {
            if (item.chapter.isVolume) {
                // 遇到卷名：始终显示，并更新折叠状态
                add(item)
                collapsed = item.chapter.index in collapsedIds
            } else if (!collapsed) {
                // 非卷名章节：仅当未被折叠时显示
                add(item)
            }
        }
        return@buildList
    }
    // 多层级目录场景：用栈追踪被折叠的祖先层级
    val collapsedAncestorLevels = ArrayDeque<Int>()
    for (item in items) {
        val level = item.chapter.tocLevel
        // 弹出层级 >= 当前的祖先，因为当前项不属于它们的子树
        while (collapsedAncestorLevels.lastOrNull()?.let { it >= level } == true) {
            collapsedAncestorLevels.removeLast()
        }
        // 栈为空说明不在任何被折叠的卷内，显示该项
        if (collapsedAncestorLevels.isEmpty()) {
            add(item)
        }
        // 当前项是被折叠的卷：将其层级入栈，后续更深层级的子项将被隐藏
        if (item.chapter.isVolume && item.chapter.index in collapsedIds) {
            collapsedAncestorLevels.addLast(level)
        }
    }
}

/**
 * 反转目录层级：在每个层级内反转兄弟节点顺序，保持父节点在子节点之前。
 * 根据 tocLevel 重建树结构，递归反转后扁平化输出。
 */
internal fun List<BookChapter>.reverseTocHierarchy(): List<BookChapter> {
    if (size < 2) return this

    data class TocNode(
        val chapter: BookChapter,
        val children: MutableList<TocNode> = mutableListOf(),
    )

    // 根据 tocLevel 重建树：ancestors 栈深度对应当前层级
    val roots = mutableListOf<TocNode>()
    val ancestors = ArrayDeque<TocNode>()
    for (chapter in this) {
        // 弹出栈直到栈深度 == 当前 tocLevel，使当前节点挂到正确的父节点下
        while (ancestors.size > chapter.tocLevel) {
            ancestors.removeLast()
        }
        val node = TocNode(chapter)
        ancestors.lastOrNull()?.children?.add(node) ?: roots.add(node)
        ancestors.addLast(node)
    }

    // 递归反转：每个层级的 children 逆序输出，保持父在子前
    fun MutableList<TocNode>.appendReversedTo(result: MutableList<BookChapter>) {
        asReversed().forEach { node ->
            result.add(node.chapter)
            node.children.appendReversedTo(result)
        }
    }

    return buildList(size) { roots.appendReversedTo(this) }
}
