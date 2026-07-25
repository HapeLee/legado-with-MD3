package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeBookPageConfigInvalidationTest {

    @Test
    fun `pagination-affecting updates rebuild whole-book page index`() {
        val updates = listOf(
            ConfigUpdate.TitleMode(2),
            ConfigUpdate.PageAnim(1),
            ConfigUpdate.TextFullJustify(true),
            ConfigUpdate.ChineseConverterType(1),
        )

        updates.forEach { update ->
            assertTrue(
                update.javaClass.simpleName,
                ConfigUpdateAction.RebuildWholeBookPageIndex in update.actions,
            )
        }
    }

    @Test
    fun `color-only updates do not rebuild whole-book page index`() {
        val update = ConfigUpdate.TextColor(0x123456)

        assertFalse(ConfigUpdateAction.RebuildWholeBookPageIndex in update.actions)
    }
}
