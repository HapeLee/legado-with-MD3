package io.legado.app.data.repository

import io.legado.app.data.entities.Book
import io.legado.app.ui.book.searchContent.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchContentRepositoryTest {

    private class ReadGateway : io.legado.app.domain.gateway.ReadSettingsGateway {
        override var currentSettings = io.legado.app.domain.model.settings.ReadSettings()
        override val settings get() = kotlinx.coroutines.flow.flowOf(currentSettings)
        override suspend fun update(transform: (io.legado.app.domain.model.settings.ReadSettings) -> io.legado.app.domain.model.settings.ReadSettings) {
            currentSettings = transform(currentSettings)
        }
    }

    @Test
    fun semanticStyleChangesInvalidateCacheAndRestorableSession() {
        val gateway = ReadGateway()
        val repository = SearchContentRepository(readSettingsGateway = gateway)
        val book = book()
        repository.beginSearch(book, "A", false, false)
        gateway.currentSettings = gateway.currentSettings.copy(adaptSpecialStyle = false)
        assertNull(repository.getCache(book, "A", false, false))
        assertNull(repository.getLastSession(book.bookUrl))
        repository.beginSearch(book, "A", false, false)
        gateway.currentSettings = gateway.currentSettings.copy(chineseConverterType = 1)
        assertNull(repository.getLastSession(book.bookUrl))
    }

    @Test
    fun ruleOrContentRevisionInvalidatesBothCachePaths() {
        var revision = 0L
        val repository = SearchContentRepository(contentRevisionProvider = { revision })
        val book = book()
        repository.beginSearch(book, "A", false, false)
        revision++
        assertNull(repository.getCache(book, "A", false, false))
        assertNull(repository.getLastSession(book.bookUrl))
    }

    @Test
    fun injectedLegacyIndentationIsPartOfSemanticIdentity() {
        var indent = "　　"
        val repository = SearchContentRepository(paragraphIndentProvider = { indent })
        val book = book()
        repository.beginSearch(book, "A", false, false)
        indent = ""
        assertNull(repository.getCache(book, "A", false, false))
        assertNull(repository.getLastSession(book.bookUrl))
    }

    @Test
    fun presentationOnlyChangesKeepSemanticCache() {
        val gateway = ReadGateway()
        val repository = SearchContentRepository(readSettingsGateway = gateway)
        val book = book()
        repository.beginSearch(book, "A", false, false)
        gateway.currentSettings = gateway.currentSettings.copy(readMenuTextColor = 123, textFullJustify = false)
        assertEquals(emptyList<SearchResult>(), repository.getCache(book, "A", false, false))
    }

    private fun book(url: String = "book-1") = Book(name = "测试书", author = "作者", bookUrl = url)

    @Test
    fun `beginSearch remembers latest search parameters for the book`() {
        val repository = SearchContentRepository()

        repository.beginSearch(
            book = book(),
            query = "keyword",
            replaceEnabled = true,
            regexReplace = false,
        )

        val session = repository.getLastSession("book-1")
        assertEquals("keyword", session?.query)
        assertEquals(true, session?.replaceEnabled)
        assertEquals(false, session?.regexReplace)
        assertEquals(emptyList<SearchResult>(), session?.results)
    }

    @Test
    fun `getLastSession does not return another book search`() {
        val repository = SearchContentRepository()
        repository.beginSearch(
            book = book(),
            query = "keyword",
            replaceEnabled = false,
            regexReplace = false,
        )

        assertNull(repository.getLastSession("book-2"))
    }

    @Test
    fun `getCache only matches the exact search parameters`() {
        val repository = SearchContentRepository()
        val book = book()
        repository.beginSearch(book, "keyword", replaceEnabled = false, regexReplace = false)

        assertEquals(
            emptyList<SearchResult>(),
            repository.getCache(book, "keyword", replaceEnabled = false, regexReplace = false),
        )
        // 替换开关会改变检索文本，缓存不能跨设置复用（否则偏移与片段都是旧的）。
        assertNull(
            repository.getCache(
                book,
                "keyword",
                replaceEnabled = true,
                regexReplace = false
            )
        )
        assertNull(repository.getCache(book, "other", replaceEnabled = false, regexReplace = false))
    }

    @Test
    fun `clearSession drops the cached results of that book`() {
        val repository = SearchContentRepository()
        val book = book()
        repository.beginSearch(book, "keyword", replaceEnabled = false, regexReplace = false)

        repository.clearSession("book-1")

        assertNull(repository.getLastSession("book-1"))
        assertNull(
            repository.getCache(
                book,
                "keyword",
                replaceEnabled = false,
                regexReplace = false
            )
        )
    }
}
