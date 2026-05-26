package io.legado.app.ui.book.search

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.MatchMode
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class SearchResultItemUi(
    val book: SearchBook,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
)

@Stable
data class SearchUiState(
    val query: String = "",
    val committedQuery: String = "",
    val results: ImmutableList<SearchResultItemUi> = persistentListOf(),
    val history: ImmutableList<SearchKeyword> = persistentListOf(),
    val bookshelfHints: ImmutableList<BookShelfItem> = persistentListOf(),
    val enabledGroups: ImmutableList<String> = persistentListOf(),
    val enabledSources: ImmutableList<BookSourcePart> = persistentListOf(),
    val scopeDisplay: String = "",
    val scopeDisplayNames: ImmutableList<String> = persistentListOf(),
    val selectedScopeSourceUrls: Set<String> = emptySet(),
    val isAllScope: Boolean = true,
    val isSourceScope: Boolean = false,
    val matchMode: MatchMode = MatchMode.DEFAULT,
    val isSearching: Boolean = false,
    val isManualStop: Boolean = false,
    val hasMore: Boolean = true,
    val processedSources: Int = 0,
    val totalSources: Int = 0,
    val selectedSourceTypes: Set<Int> = emptySet(),
    val showScopeSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showClearHistoryDialog: Boolean = false,
    val showSuggestions: Boolean = true,
    val emptyScopeAction: SearchEmptyScopeAction? = null,
    val savedScrollIndex: Int = 0,
    val savedScrollOffset: Int = 0,
    val expandedSourceUrl: String? = null,
    val expandedSourceName: String? = null,
    val expandedSourceBooks: ImmutableList<SearchBook> = persistentListOf(),
    val expandedSourceLoading: Boolean = false,
    val expandedSourceEnd: Boolean = false,
    val expandedSourceError: String? = null,
    val expandedSourcePage: Int = 1,
)

data class SearchEmptyScopeAction(
    val scopeDisplay: String,
    val wasMatchMode: MatchMode,
)

sealed interface SearchIntent {
    data class Initialize(val key: String?, val scopeRaw: String?) : SearchIntent
    data class UpdateQuery(val query: String) : SearchIntent
    data object SubmitSearch : SearchIntent
    data object LoadMore : SearchIntent
    data object StopSearch : SearchIntent
    data object ClearSearchResults : SearchIntent
    data object PauseEngine : SearchIntent
    data object ResumeEngine : SearchIntent
    data class UseHistoryKeyword(val keyword: String) : SearchIntent
    data class OpenSearchBook(val book: SearchBook, val sharedCoverKey: String?) : SearchIntent
    data class OpenBookshelfBook(val book: BookShelfItem) : SearchIntent
    data class ExpandSource(val sourceUrl: String, val sourceName: String) : SearchIntent
    data object DismissExpandedSource : SearchIntent
    data object LoadMoreExpandedSource : SearchIntent
    data class OpenExpandedSourceBook(val book: SearchBook, val sharedCoverKey: String?) :
        SearchIntent
    data class DeleteHistory(val item: SearchKeyword) : SearchIntent
    data class SetClearHistoryDialogVisible(val visible: Boolean) : SearchIntent
    data object ConfirmClearHistory : SearchIntent
    data class SetScopeSheetVisible(val visible: Boolean) : SearchIntent
    data class SetSettingsSheetVisible(val visible: Boolean) : SearchIntent
    data class ToggleSourceType(val type: Int) : SearchIntent
    data object SelectAllScope : SearchIntent
    data class ToggleScopeGroup(val groupName: String) : SearchIntent
    data class ToggleScopeSource(val source: BookSourcePart) : SearchIntent
    data class RemoveScopeItem(val scopeName: String) : SearchIntent
    data class SetMatchMode(val mode: MatchMode) : SearchIntent
    data object ConfirmEmptyScopeAction : SearchIntent
    data object DismissEmptyScopeAction : SearchIntent
    data object OpenSourceManage : SearchIntent
    data class SaveScrollState(val index: Int, val offset: Int) : SearchIntent
}

sealed interface SearchEffect {
    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
        val origin: String? = null,
        val coverPath: String? = null,
        val sharedCoverKey: String?,
    ) : SearchEffect

    data object OpenSourceManage : SearchEffect

    data class ShowMessage(val message: String) : SearchEffect
}
