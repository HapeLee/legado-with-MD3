package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import android.net.Uri
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class BookshelfGroupSelectorState(
    val isInitialLoading: Boolean = true,
    val groups: ImmutableList<BookGroupUi> = persistentListOf(),
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
    data class GroupEditSheet(val groupId: Long) : BookshelfOverlay
    data object BatchDownloadConfirmDialog : BookshelfOverlay
}

sealed interface BookshelfIntent {
    data class ChangeGroup(val groupId: Long) : BookshelfIntent
    data class SetSearchKey(val value: String) : BookshelfIntent
    data class SetSearchMode(val active: Boolean) : BookshelfIntent
    data class ShowOverlay(val overlay: BookshelfOverlay) : BookshelfIntent
    data object DismissOverlay : BookshelfIntent
    data object ToggleEditMode : BookshelfIntent
    data object ExitEditMode : BookshelfIntent
    data object ClearSelection : BookshelfIntent
    data object SelectAllVisible : BookshelfIntent
    data object InvertVisibleSelection : BookshelfIntent
    data class ToggleBookSelection(val bookUrl: String) : BookshelfIntent
    data class SetInFolderRoot(val value: Boolean) : BookshelfIntent
    data class MoveBooksToGroup(val bookUrls: Set<String>, val groupId: Long) : BookshelfIntent
    data class DownloadBooks(val bookUrls: Set<String>, val allChapters: Boolean = false) : BookshelfIntent
    data class RefreshBooks(val books: List<BookUiItem>) : BookshelfIntent
    data class StartDragging(val books: List<BookUiItem>) : BookshelfIntent
    data class MoveDragging(val from: Int, val to: Int, val books: List<BookUiItem>) : BookshelfIntent
    data object FinishDragging : BookshelfIntent
    data object ScrollToTop : BookshelfIntent
    data object RefreshAll : BookshelfIntent
    data class RefreshToc(val books: List<BookUiItem>) : BookshelfIntent
    data class AddBookByUrl(val urls: String) : BookshelfIntent
    data class ExportToUri(val uri: Uri, val books: List<BookUiItem>) : BookshelfIntent
    data class UploadBookshelf(val books: List<BookUiItem>) : BookshelfIntent
    data class ImportFromUri(val uri: Uri, val groupId: Long) : BookshelfIntent
    data class UpdateSetting(
        val transform: (BookshelfSettings) -> BookshelfSettings,
    ) : BookshelfIntent
    data class SetCustomTagColorsEnabled(val enabled: Boolean) : BookshelfIntent
    data class SetCustomTagColors(val colors: List<TagColorPair>) : BookshelfIntent
    data object UploadResultConsumed : BookshelfIntent
}

sealed interface BookshelfEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val url: String? = null,
    ) : BookshelfEffect
}

@Stable
data class BookshelfUiState(
    override val items: ImmutableList<BookUiItem> = persistentListOf(),
    override val selectedIds: ImmutableSet<Any> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val isInitialLoading: Boolean = true,
    val groups: ImmutableList<BookGroupUi> = persistentListOf(),
    val allGroups: ImmutableList<BookGroupUi> = persistentListOf(),
    val groupPreviews: ImmutableMap<Long, ImmutableList<BookUiItem>> = persistentMapOf(),
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
    val draggingBooks: ImmutableList<BookUiItem>? = null,
    val pendingSavedBooks: ImmutableList<BookUiItem>? = null,
    val visibleGroupBooks: ImmutableMap<Long, ImmutableList<BookUiItem>> = persistentMapOf(),
    val settings: BookshelfSettings = BookshelfSettings(),
    val useRaisedBottomInset: Boolean = false,
    val enableCustomTagColors: Boolean = false,
    val customTagColors: ImmutableList<TagColorPair> = persistentListOf(),
    val themeColor: Int = 0,
    val pendingUploadUrl: String? = null,
) : ListUiState<BookUiItem>
