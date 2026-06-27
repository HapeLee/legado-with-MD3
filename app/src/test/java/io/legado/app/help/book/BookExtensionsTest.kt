package io.legado.app.help.book

import io.legado.app.data.entities.HighlightTagRule
import org.junit.Assert.assertEquals
import org.junit.Test

class BookExtensionsTest {

    @Test
    fun parseHighlightedTags_ordersMatchesByRuleOrder() {
        val rules = listOf(
            HighlightTagRule(
                id = 2,
                title = "次要",
                pattern = "动作",
                order = 1,
            ),
            HighlightTagRule(
                id = 1,
                title = "优先",
                pattern = "奇幻",
                order = 0,
            ),
        )

        val (highlighted, regular) = parseHighlightedTags(
            kindLabels = listOf("动作", "普通", "奇幻"),
            rules = rules,
        )

        assertEquals(listOf("优先", "次要"), highlighted.map { it.title })
        assertEquals(listOf(listOf("奇幻"), listOf("动作")), highlighted.map { it.matchedLabels })
        assertEquals(listOf("普通"), regular)
    }
}
