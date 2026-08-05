package io.legado.app.ui.replace

import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReplaceRuleStateTest {

    @Test
    fun editedRuleContentChangesListItemEquality() {
        val item = ReplaceRuleItemUi(
            id = 1L,
            name = "rule",
            isEnabled = true,
            group = null,
            pattern = "before",
            replacement = "",
            scope = null,
            scopeTitle = false,
            scopeContent = true,
            excludeScope = null,
            isRegex = true,
            timeoutMillisecond = 3000L,
            order = 1
        )

        assertNotEquals(item, item.copy(pattern = "after"))
    }

    @Test
    fun newEditRoutesUseDifferentSessions() {
        assertNotEquals(ReplaceEditRoute(), ReplaceEditRoute())
    }
}
