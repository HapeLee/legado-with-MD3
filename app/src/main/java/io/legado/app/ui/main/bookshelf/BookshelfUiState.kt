package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.components.list.ListUiState

data class BookshelfGroupSelectorState(
    val groups: List<BookGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    val selectedGroupId: Long = BookGroup.IdAll
)

sealed interface BookshelfOverlay {
    data object AddUrlDialog : BookshelfOverlay
    data object ImportSheet : BookshelfOverlay
    data object ExportSheet : BookshelfOverlay
    data object ConfigSheet : BookshelfOverlay
    data object GroupManageSheet : BookshelfOverlay
    data object LogSheet : BookshelfOverlay
    data object GroupMenu : BookshelfOverlay
    data object GroupSelectSheet : BookshelfOverlay
    data object BatchDownloadConfirmDialog : BookshelfOverlay
}

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
    val updatingBooks: Set<String> = emptySet(),
    val activeOverlay: BookshelfOverlay? = null,
    val isEditMode: Boolean = false,
    val selectedBookUrls: Set<String> = emptySet(),
    val isInFolderRoot: Boolean = false,
    val isRefreshing: Boolean = false,
    val bookGroupStyle: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val currentGroupName: String? = null,
    val draggingBooks: List<BookShelfItem>? = null,
    val pendingSavedBooks: List<BookShelfItem>? = null
) : ListUiState<BookShelfItem>
