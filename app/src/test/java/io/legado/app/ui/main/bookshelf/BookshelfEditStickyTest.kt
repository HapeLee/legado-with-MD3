package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfEditStickyTest {

    @Test
    fun `hidden when group tabs are visible`() {
        assertNull(
            buildBookshelfEditSticky(
                isEditMode = true,
                bookGroupStyle = 0,
                selectedCount = 2,
                currentGroupTotalCount = 10,
                groupName = "Group",
            )
        )
    }

    @Test
    fun `hidden outside edit mode for every group style`() {
        (0..2).forEach { style ->
            assertNull(
                buildBookshelfEditSticky(
                    isEditMode = false,
                    bookGroupStyle = style,
                    selectedCount = 0,
                    currentGroupTotalCount = 10,
                    groupName = null,
                )
            )
        }
    }

    @Test
    fun `shown when group tabs are hidden`() {
        listOf(1, 2).forEach { style ->
            assertEquals(
                BookshelfEditStickySummary(2, 10, "Group"),
                buildBookshelfEditSticky(
                    isEditMode = true,
                    bookGroupStyle = style,
                    selectedCount = 2,
                    currentGroupTotalCount = 10,
                    groupName = "Group",
                ),
            )
        }
    }

    @Test
    fun `summary follows the latest selection and group state`() {
        val first = buildBookshelfEditSticky(true, 1, 0, 5, "A")
        val second = buildBookshelfEditSticky(true, 1, 3, 8, "B")

        assertEquals(0, first?.selectedCount)
        assertEquals(5, first?.currentGroupTotalCount)
        assertEquals("A", first?.groupName)
        assertEquals(3, second?.selectedCount)
        assertEquals(8, second?.currentGroupTotalCount)
        assertEquals("B", second?.groupName)
    }

    @Test
    fun `edit info stays on subtitle when group tabs are visible`() {
        assertFalse(bookshelfEditInfoUsesSticky(true, 0))
    }

    @Test
    fun `edit info moves to sticky bar when group tabs are hidden`() {
        listOf(1, 2).forEach { style ->
            assertTrue(bookshelfEditInfoUsesSticky(true, style))
        }
    }

    @Test
    fun `no edit info carrier outside edit mode`() {
        (0..2).forEach { style ->
            assertFalse(bookshelfEditInfoUsesSticky(false, style))
        }
    }

    @Test
    fun `sticky visibility always matches the shared carrier rule`() {
        listOf(false, true).forEach { isEditMode ->
            (0..2).forEach { style ->
                val usesSticky = bookshelfEditInfoUsesSticky(isEditMode, style)
                val hasSticky = buildBookshelfEditSticky(isEditMode, style, 1, 5, "Group") != null
                assertEquals(usesSticky, hasSticky)
            }
        }
    }
}
