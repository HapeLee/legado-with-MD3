package io.legado.app.feature.explore

import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreMainSelectionTest {
    @Test
    fun `saved source falls back when it is removed`() {
        val sources = listOf(
            ExploreMainSourceItem("source-a", "A"),
            ExploreMainSourceItem("source-b", "B"),
        )
        assertEquals("source-b", resolveExploreMainSource(sources, "source-b"))
        assertEquals("source-a", resolveExploreMainSource(sources, "removed"))
        assertNull(resolveExploreMainSource(emptyList(), "removed"))
    }

    @Test
    fun `explicit selection wins over saved source and stale preference`() {
        val sources = listOf(
            ExploreMainSourceItem("source-a", "A"),
            ExploreMainSourceItem("source-b", "B"),
        )
        // 偏好写入是异步的，回读旧值时不能把已选书源退回旧源。
        assertEquals("source-b", resolveExploreMainSource(sources, "source-a", "source-b"))
        // 已选书源被删除后，才回退到已保存的书源，再回退到列表首个。
        assertEquals("source-a", resolveExploreMainSource(sources, "source-a", "removed"))
        assertEquals(
            "source-b",
            resolveExploreMainSource(listOf(sources[1]), "removed", "source-a")
        )
        assertNull(resolveExploreMainSource(emptyList(), "removed", "source-a"))
    }

    @Test
    fun `category key distinguishes same title with different url`() {
        val books = ExploreKind(title = "books", url = "https://a/books")
        val other = ExploreKind(title = "books", url = "https://b/books")
        assertEquals(exploreKindKey(books), exploreKindKey(books.copy()))
        assertNotEquals(exploreKindKey(books), exploreKindKey(other))
    }

    @Test
    fun `category memory round-trips and ignores malformed entries`() {
        val selections = mapOf(
            "https://source-a" to "1:a:https://a/books",
            "https://source-b" to "1:b:https://b/books",
        )
        assertEquals(
            selections,
            parseExploreKindSelections(encodeExploreKindSelections(selections))
        )
        assertTrue(parseExploreKindSelections("").isEmpty())
        assertTrue(parseExploreKindSelections("no-separator").isEmpty())
        assertEquals(
            mapOf("https://source-a" to "1:a:x"),
            parseExploreKindSelections("\u0001https://source-a\u00001:a:x\u0001malformed"),
        )
    }

    @Test
    fun `pager contains only loadable unique categories`() {
        val kinds = listOf(
            ExploreKind(title = "filter", url = null, type = ExploreKind.Type.select),
            ExploreKind(title = "books", url = "https://example.com", type = ExploreKind.Type.url),
        )
        assertEquals(
            listOf(kinds[1]), loadableExploreMainKinds(
                kinds + kinds[1] +
                        ExploreKind(title = "blank", url = "", type = ExploreKind.Type.url)
            )
        )
    }
}
