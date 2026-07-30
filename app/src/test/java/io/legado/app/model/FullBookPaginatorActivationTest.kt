package io.legado.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBookPaginatorActivationTest {

    @Test
    fun `direct whole-book page tips enable pagination`() {
        assertTrue(isActive(selection(value = WHOLE_BOOK_PAGE)))
        assertTrue(isActive(selection(value = WHOLE_BOOK_PAGE_AND_PROGRESS)))
    }

    @Test
    fun `inactive historical custom template does not enable pagination`() {
        assertFalse(
            isActive(
                selection(
                    value = ORDINARY_TIP,
                    template = "{BookName} {FullPageIndex}/{FullPageSize}",
                )
            )
        )
    }

    @Test
    fun `active custom template enables pagination only for full-book placeholders`() {
        assertTrue(isActive(selection(CUSTOM_TIP, "{FullPageIndex}")))
        assertTrue(isActive(selection(CUSTOM_TIP, "{FullPageSize}")))
        assertFalse(isActive(selection(CUSTOM_TIP, "{PageIndex}/{PageSize}")))
    }

    private fun isActive(selection: FullBookPageTipSelection) = hasActiveFullBookPageTip(
        selections = listOf(selection),
        customTipValue = CUSTOM_TIP,
        wholeBookPageTipValue = WHOLE_BOOK_PAGE,
        wholeBookPageAndProgressTipValue = WHOLE_BOOK_PAGE_AND_PROGRESS,
    )

    private fun selection(
        value: Int,
        template: String = "",
    ) = FullBookPageTipSelection(value, template)

    private companion object {
        const val ORDINARY_TIP = 1
        const val WHOLE_BOOK_PAGE = 19
        const val WHOLE_BOOK_PAGE_AND_PROGRESS = 20
        const val CUSTOM_TIP = 21
    }
}
