package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

data class BookshelfGroupSelectorState(
    val groups: ImmutableList<BookGroup> = persistentListOf(),
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
    override val items: ImmutableList<BookShelfItem> = persistentListOf(),
    override val selectedIds: ImmutableSet<Any> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val groups: ImmutableList<BookGroup> = persistentListOf(),
    val allGroups: ImmutableList<BookGroup> = persistentListOf(),
    val groupPreviews: ImmutableMap<Long, ImmutableList<BookShelfItem>> = persistentMapOf(),
    val groupBookCounts: ImmutableMap<Long, Int> = persistentMapOf(),
    val currentGroupBookCount: Int = 0,
    val allBooksCount: Int = 0,
    val selectedGroupIndex: Int = 0,
    val selectedGroupId: Long = BookGroup.IdAll,
    val loadingText: String? = null,
    val upBooksCount: Int = 0,
    val updatingBooks: ImmutableSet<String> = persistentSetOf(),
    val activeOverlay: BookshelfOverlay? = null,
    val isEditMode: Boolean = false,
    val selectedBookUrls: ImmutableSet<String> = persistentSetOf(),
    val isInFolderRoot: Boolean = false,
    val isRefreshing: Boolean = false,
    val bookGroupStyle: Int = 0,
    val bookshelfSort: Int = 0,
    val bookshelfSortOrder: Int = 1,
    val title: String = "",
    val subtitle: String? = null,
    val currentGroupName: String? = null,
    val draggingBooks: ImmutableList<BookShelfItem>? = null,
    val pendingSavedBooks: ImmutableList<BookShelfItem>? = null
) : ListUiState<BookShelfItem>
