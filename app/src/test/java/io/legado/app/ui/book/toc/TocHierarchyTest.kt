package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class TocHierarchyTest {

    @Test
    fun collapse_hidesOnlyDescendantsOfTheCollapsedNode() {
        val items = chapters().map {
            TocDomainItem(it, it.title, DownloadState.LOCAL)
        }

        val visible = filterCollapsedToc(items, setOf(1))

        assertEquals(listOf(0, 1, 5, 6), visible.map { it.chapter.index })
    }

    @Test
    fun nestedCollapse_keepsParentAndHidesNestedDescendants() {
        val items = chapters().map {
            TocDomainItem(it, it.title, DownloadState.LOCAL)
        }

        val visible = filterCollapsedToc(items, setOf(2))

        assertEquals(listOf(0, 1, 2, 4, 5, 6), visible.map { it.chapter.index })
    }

    @Test
    fun reverse_reversesSiblingsAtEveryLevelAndPreservesPreorder() {
        val reversed = chapters().reverseTocHierarchy()

        assertEquals(listOf(6, 5, 1, 4, 2, 3, 0), reversed.map { it.index })
        assertEquals(listOf(0, 0, 0, 1, 1, 2, 0), reversed.map { it.tocLevel })
    }

    private fun chapters() = listOf(
        chapter(0, "序章", level = 0),
        chapter(1, "第一卷", level = 0, isVolume = true),
        chapter(2, "第一章", level = 1, isVolume = true),
        chapter(3, "第一节", level = 2),
        chapter(4, "第二章", level = 1),
        chapter(5, "番外", level = 0),
        chapter(6, "后记", level = 0),
    )

    private fun chapter(
        index: Int,
        title: String,
        level: Int,
        isVolume: Boolean = false,
    ) = BookChapter(
        url = index.toString(),
        title = title,
        index = index,
        isVolume = isVolume,
        tocLevel = level,
    )
}
