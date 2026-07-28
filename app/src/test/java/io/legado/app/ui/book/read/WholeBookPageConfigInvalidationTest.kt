package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeBookPageConfigInvalidationTest {

    @Test
    fun `header and footer page selections reevaluate whole-book page demand`() {
        val updates = listOf(
            ConfigUpdate.TipHeaderLeft(19),
            ConfigUpdate.TipHeaderMiddle(19),
            ConfigUpdate.TipHeaderRight(19),
            ConfigUpdate.TipFooterLeft(19),
            ConfigUpdate.TipFooterMiddle(19),
            ConfigUpdate.TipFooterRight(19),
            ConfigUpdate.CustomTipHeaderLeft("{FullPageIndex}"),
            ConfigUpdate.CustomTipFooterRight("{FullPageSize}"),
        )

        updates.forEach { update ->
            assertTrue(
                update.javaClass.simpleName,
                ConfigUpdateAction.UpdateWholeBookPageDemand in update.actions,
            )
        }
    }

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
