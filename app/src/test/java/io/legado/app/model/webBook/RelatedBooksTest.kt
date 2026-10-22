package io.legado.app.model.webBook

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RelatedBooksTest {
    @Test
    fun `invalid configuration is reported instead of silently disappearing`() = runBlocking {
        val errors = mutableListOf<String>()
        val modules = RelatedBooks.load(Book(), "[{", { error("must not request") }) { message, error ->
            if (error != null) errors += message
        }
        assertTrue(modules.isEmpty())
        assertEquals(listOf("关联书籍 relatedBooks 配置解析失败"), errors)
    }

    @Test
    fun `module failures are diagnosed and do not hide successful modules`() = runBlocking {
        val messages = mutableListOf<String>()
        val modules = RelatedBooks.load(
            Book(bookUrl = "current"),
            """[
                {"title":"missing"},
                {"title":"broken","url":"bad"},
                {"key":"ok","title":"works","url":"good"},
                {"title":"empty","url":"empty"}
            ]""",
            explore = { url ->
                when (url) {
                    "bad" -> error("failed request")
                    "good" -> "resolved" to listOf(SearchBook(bookUrl = "current"), SearchBook(bookUrl = "other"))
                    else -> url to emptyList()
                }
            },
            log = { message, _ -> messages += message },
        )
        assertEquals(listOf("ok"), modules.map { it.key })
        assertEquals("resolved", modules.single().resolvedUrl)
        assertEquals(listOf("other"), modules.single().books.map { it.bookUrl })
        assertTrue(messages.any { "relatedBooks[1] missing: url 为空" in it })
        assertTrue(messages.any { "relatedBooks[2] broken: 请求或解析失败" in it })
        assertTrue(messages.any { "relatedBooks[4] empty: 获取 0 本" in it })
    }

    @Test
    fun `URL JavaScript retains spaces inside code and strings`() = runBlocking {
        val script = """@js:var name = 'a b'; java.log(name); 'https://example.com/'"""
        var requested = ""
        RelatedBooks.load(
            Book(),
            """[{"url":"  $script  "}]""",
            explore = { requested = it; it to emptyList() },
            log = { _, _ -> },
        )
        assertEquals(script, requested)
    }

    @Test
    fun `cancellation propagates without being logged as module failure`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        try {
            RelatedBooks.load(
                Book(), """[{"url":"cancel"}]""",
                explore = { throw CancellationException("cancelled") },
                log = { _, error -> if (error != null) errors += error },
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(errors.isEmpty())
        }
    }
}
