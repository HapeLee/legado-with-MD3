package io.legado.app.domain.usecase

import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfAutoGroupPlanParserTest {

    private val parser = BookshelfAutoGroupPlanParser()
    private val books = linkedMapOf(
        "b1" to book("url-1", "Book 1"),
        "b2" to book("url-2", "Book 2"),
        "b3" to book("url-3", "Book 3"),
    )

    @Test
    fun `extracts fenced json and ignores braces inside strings`() {
        val plan = parser.parse(
            """prefix ```json
                {"groups":[{"name":"Sci { Fi }","books":[{"id":"b1","reason":"uses braces {}"}]}]}
                ``` suffix
            """.trimIndent(),
            books,
            emptySet(),
        )

        assertEquals("Sci { Fi }", plan.groups.single().name)
        assertEquals("url-1", plan.groups.single().books.single().bookUrl)
        assertEquals(2, plan.ignoredBooks.size)
    }

    @Test
    fun `merges duplicate groups and keeps first book assignment`() {
        val plan = parser.parse(
            """{"groups":[
                {"name":"Fantasy","books":[{"id":"b1"},{"id":"b2"}]},
                {"name":"Fantasy","books":[{"id":"b2"},{"id":"b3"}]},
                {"name":" ","books":[{"id":"b1"}]},
                {"name":"Unknown","books":[{"id":"missing"}]}
            ]}""",
            books,
            setOf("Fantasy"),
        )

        assertEquals(1, plan.groups.size)
        assertEquals(listOf("url-1", "url-2", "url-3"), plan.groups.single().books.map { it.bookUrl })
        assertTrue(plan.groups.single().reuseExisting)
        assertTrue(plan.ignoredBooks.isEmpty())
    }

    @Test
    fun `ignores invalid field types and covers omitted books`() {
        val plan = parser.parse(
            """{"groups":[{"name":42,"books":{}},{"name":"Valid","books":[{"id":7}]}],"ignoredBooks":[{"id":"b1"},{"id":"unknown"}]}""",
            books,
            emptySet(),
        )

        assertTrue(plan.groups.isEmpty())
        assertEquals(listOf("url-1", "url-2", "url-3"), plan.ignoredBooks.map { it.bookUrl })
    }

    @Test
    fun `rejects malformed response`() {
        val error = runCatching { parser.parse("not json {", books, emptySet()) }.exceptionOrNull()

        assertTrue(error is BookshelfAutoGroupException)
        assertEquals(
            BookshelfAutoGroupErrorReason.InvalidResponse,
            (error as BookshelfAutoGroupException).reason,
        )
    }

    @Test
    fun `rejects json without the grouping contract`() {
        val error = runCatching {
            parser.parse("{\"message\":\"request failed\"}", books, emptySet())
        }.exceptionOrNull()

        assertTrue(error is BookshelfAutoGroupException)
        assertEquals(
            BookshelfAutoGroupErrorReason.InvalidResponse,
            (error as BookshelfAutoGroupException).reason,
        )
    }

    @Test
    fun `skips unrelated json before the grouping plan`() {
        val plan = parser.parse(
            """metadata {"message":"ignored"}\n{"groups":[{"name":"Valid","books":[{"id":"b1"}]}]}""",
            books,
            emptySet(),
        )

        assertEquals("Valid", plan.groups.single().name)
        assertEquals("url-1", plan.groups.single().books.single().bookUrl)
    }

    @Test
    fun `keeps grouped and ignored reasons`() {
        val plan = parser.parse(
            response = """{"groups":[{"name":"Valid","books":[{"id":"b1","reason":"Group reason"}]}],"ignoredBooks":[{"id":"b2","reason":"Ignore reason"}]}""",
            booksByPromptId = books,
            existingGroupNames = emptySet(),
        )

        assertEquals("Group reason", plan.groups.single().books.single().reason)
        assertEquals("Ignore reason", plan.ignoredBooks.first { it.bookUrl == "url-2" }.reason)
    }

    @Test
    fun `keeps only the first short reason sentence`() {
        val plan = parser.parse(
            response = """{"groups":[{"name":"Valid","books":[{"id":"b1","reason":"第一句理由。第二句不应保留。"}]}]}""",
            booksByPromptId = books,
            existingGroupNames = emptySet(),
        )

        assertEquals("第一句理由。", plan.groups.single().books.single().reason)
    }

    private fun book(url: String, name: String) = BookshelfAutoGroupBook(
        bookUrl = url,
        name = name,
        author = "Author",
        intro = "Intro",
        kind = "Kind",
        currentGroupNames = emptyList(),
    )
}
