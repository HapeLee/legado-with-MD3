package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBooksUseCaseTest {

    @Test
    fun `search url without dynamic page does not support next page`() {
        val source = BookSource(
            searchUrl = "https://example.com/search?keyword={{key}}&page=1"
        )

        assertFalse(source.supportsSearchPage())
    }

    @Test
    fun `search url with page group supports next page`() {
        val source = BookSource(
            searchUrl = "https://example.com/search/{{key}}/<1,2,3>"
        )

        assertTrue(source.supportsSearchPage())
    }

    @Test
    fun `search url with page inner rule supports next page`() {
        val source = BookSource(
            searchUrl = "https://example.com/search?keyword={{key}}&page={{page}}"
        )

        assertTrue(source.supportsSearchPage())
    }

    @Test
    fun `search url with page js supports next page`() {
        val source = BookSource(
            searchUrl = "<js>baseUrl + '/search?keyword=' + key + '&page=' + page</js>"
        )

        assertTrue(source.supportsSearchPage())
    }

    @Test
    fun `search url with js but without page does not support next page`() {
        val source = BookSource(
            searchUrl = "<js>baseUrl + '/search?keyword=' + key</js>"
        )

        assertFalse(source.supportsSearchPage())
    }
}
