package io.legado.app.domain.model

data class BookshelfAutoGroupBook(
    val bookUrl: String,
    val name: String,
    val author: String,
    val intro: String,
    val kind: String,
    val currentGroupNames: List<String>,
)

data class BookshelfAutoGroupSource(
    val books: List<BookshelfAutoGroupBook>,
    val existingGroupNames: List<String>,
) {
    val bookCount: Int get() = books.size
    val groupedBookCount: Int get() = books.count { it.currentGroupNames.isNotEmpty() }
}

data class BookshelfAutoGroupPreflight(
    val analyzedBookCount: Int,
    val effectiveInputCharLimit: Int,
    val estimatedRequestCount: Int,
)

data class BookshelfAutoGroupOptions(
    val incrementalOnly: Boolean = true,
    val includeBookIntro: Boolean = false,
    val enableDeepThinking: Boolean = false,
)

data class BookshelfAutoGroupProgress(
    val currentBatch: Int,
    val totalBatches: Int,
)

enum class BookshelfAutoGroupErrorReason {
    EmptyBookshelf,
    MissingModel,
    CapacityTooSmall,
    GroupCapacityExceeded,
    InvalidResponse,
}

class BookshelfAutoGroupException(
    val reason: BookshelfAutoGroupErrorReason,
    cause: Throwable? = null,
) : IllegalStateException(cause?.message, cause)

data class BookshelfAutoGroupPlan(
    val groups: List<BookshelfAutoGroupPlanGroup>,
    val ignoredBooks: List<BookshelfAutoGroupIgnoredBook> = emptyList(),
) {
    val assignedBookCount: Int get() = groups.sumOf { it.books.size }
    val newGroupCount: Int get() = groups.count { !it.reuseExisting }
}

data class BookshelfAutoGroupPlanGroup(
    val key: String,
    val name: String,
    val description: String,
    val reuseExisting: Boolean,
    val books: List<BookshelfAutoGroupPlanBook>,
)

data class BookshelfAutoGroupPlanBook(
    val bookUrl: String,
    val name: String,
    val author: String,
    val currentGroupNames: List<String>,
    val reason: String,
)

data class BookshelfAutoGroupIgnoredBook(
    val bookUrl: String,
    val name: String,
    val author: String,
    val reason: String,
)

data class BookshelfAutoGroupApplyResult(
    val createdGroupCount: Int,
    val reusedGroupCount: Int,
    val updatedBookCount: Int,
    val ignoredBookCount: Int,
)
