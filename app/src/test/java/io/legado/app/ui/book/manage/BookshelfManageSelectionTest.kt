package io.legado.app.ui.book.manage

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfManageSelectionTest {

    @Test
    fun selectVisible_keepsBooksSelectedInOtherGroups() {
        // 分组 A 里选了 a1，切到分组 B 后全选 B：a1 必须还在
        val result = selectionOfSelectVisible(
            selected = setOf("a1"),
            visibleBookUrls = setOf("b1", "b2")
        )
        assertEquals(setOf("a1", "b1", "b2"), result)
    }

    @Test
    fun selectVisible_isIdempotent() {
        val visible = setOf("b1", "b2")
        val first = selectionOfSelectVisible(emptySet(), visible)
        assertEquals(first, selectionOfSelectVisible(first, visible))
    }

    @Test
    fun invertVisible_onlyFlipsVisibleBooks() {
        // a1 属于另一个分组（不可见），反选当前分组时不能被翻掉
        val result = selectionOfInvertVisible(
            selected = setOf("a1", "b1"),
            visibleBookUrls = setOf("b1", "b2")
        )
        assertEquals(setOf("a1", "b2"), result)
    }

    @Test
    fun invertVisible_addsAllVisibleWhenNothingSelected() {
        val visible = setOf("b1", "b2")
        assertEquals(visible, selectionOfInvertVisible(setOf("a1"), visible) - setOf("a1"))
    }
}
