package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyBookshelfAutoGroupPlanUseCaseTest {

    @Test
    fun `normalizes duplicate groups books and ignored entries before gateway`() = runBlocking {
        val gateway = RecordingGateway()
        val useCase = ApplyBookshelfAutoGroupPlanUseCase(gateway)
        val book1 = planBook("url-1")
        val book2 = planBook("url-2")

        useCase.execute(
            BookshelfAutoGroupPlan(
                groups = listOf(
                    group("one", " Fantasy ", listOf(book1, book2)),
                    group("two", "Fantasy", listOf(book2)),
                    group("empty", " ", listOf(book1)),
                ),
                ignoredBooks = listOf(
                    ignored("url-1"),
                    ignored("url-3"),
                    ignored("url-3"),
                ),
            ),
            BookshelfAutoGroupOptions(incrementalOnly = true),
        )

        val applied = gateway.appliedPlan!!
        assertEquals(1, applied.groups.size)
        assertEquals("Fantasy", applied.groups.single().name)
        assertEquals(listOf("url-1", "url-2"), applied.groups.single().books.map { it.bookUrl })
        assertEquals(listOf("url-3"), applied.ignoredBooks.map { it.bookUrl })
        assertEquals(true, gateway.appliedOptions?.incrementalOnly)
    }

    private class RecordingGateway : BookshelfAutoGroupGateway {
        var appliedPlan: BookshelfAutoGroupPlan? = null
        var appliedOptions: BookshelfAutoGroupOptions? = null

        override suspend fun loadSource() = BookshelfAutoGroupSource(emptyList(), emptyList())

        override suspend fun applyPlan(
            plan: BookshelfAutoGroupPlan,
            options: BookshelfAutoGroupOptions,
        ): BookshelfAutoGroupApplyResult {
            appliedPlan = plan
            appliedOptions = options
            return BookshelfAutoGroupApplyResult(0, 0, 0, plan.ignoredBooks.size)
        }
    }

    private fun group(key: String, name: String, books: List<BookshelfAutoGroupPlanBook>) =
        BookshelfAutoGroupPlanGroup(key, name, "", false, books)

    private fun planBook(url: String) = BookshelfAutoGroupPlanBook(url, url, "", emptyList(), "")

    private fun ignored(url: String) = BookshelfAutoGroupIgnoredBook(url, url, "", "")
}
