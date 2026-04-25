package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.components.list.ListUiState

data class BookshelfUiState(
    override val items: List<BookShelfItem> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val groups: List<BookGroup> = emptyList(),
    val allGroups: List<BookGroup> = emptyList(),
    val groupPreviews: Map<Long, List<BookShelfItem>> = emptyMap(),
    val groupBookCounts: Map<Long, Int> = emptyMap(),
    val currentGroupBookCount: Int = 0,
    val allBooksCount: Int = 0,
    val selectedGroupIndex: Int = 0,
    val selectedGroupId: Long = BookGroup.IdAll,
    val loadingText: String? = null,
    val upBooksCount: Int = 0,
    val updatingBooks: Set<String> = emptySet()
) : ListUiState<BookShelfItem>
