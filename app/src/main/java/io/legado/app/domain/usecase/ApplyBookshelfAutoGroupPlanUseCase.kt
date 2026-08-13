package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup

class ApplyBookshelfAutoGroupPlanUseCase(
    private val gateway: BookshelfAutoGroupGateway,
) {

    suspend fun execute(
        plan: BookshelfAutoGroupPlan,
        options: BookshelfAutoGroupOptions,
    ): BookshelfAutoGroupApplyResult {
        val normalizedPlan = normalize(plan)
        require(normalizedPlan.groups.isNotEmpty()) { "No applicable grouping plan" }
        return gateway.applyPlan(normalizedPlan, options)
    }

    internal fun normalize(
        plan: BookshelfAutoGroupPlan,
        existingGroupNames: Set<String> = emptySet(),
    ): BookshelfAutoGroupPlan {
        val assignedBookUrls = linkedSetOf<String>()
        val groupsByName = linkedMapOf<String, BookshelfAutoGroupPlanGroup>()
        plan.groups.forEach { group ->
            val name = group.name.trim().take(MAX_GROUP_NAME_CHARS)
            if (name.isBlank()) return@forEach
            val uniqueBooks = group.books.filter { assignedBookUrls.add(it.bookUrl) }
            if (uniqueBooks.isEmpty()) return@forEach
            val existing = groupsByName[name]
            groupsByName[name] = if (existing == null) {
                group.copy(
                    name = name,
                    books = uniqueBooks,
                    reuseExisting = name in existingGroupNames,
                )
            } else {
                existing.copy(
                    books = existing.books + uniqueBooks,
                    reuseExisting = name in existingGroupNames,
                )
            }
        }
        val ignoredBookUrls = linkedSetOf<String>()
        val ignoredBooks = plan.ignoredBooks.filter { book ->
            book.bookUrl !in assignedBookUrls && ignoredBookUrls.add(book.bookUrl)
        }
        return BookshelfAutoGroupPlan(
            groups = groupsByName.values.toList(),
            ignoredBooks = ignoredBooks,
        )
    }

    private companion object {
        const val MAX_GROUP_NAME_CHARS = 24
    }
}
