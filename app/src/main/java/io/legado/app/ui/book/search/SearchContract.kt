package io.legado.app.ui.book.search

import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.domain.model.BookShelfState
import io.legado.app.ui.main.bookshelf.BookShelfItem

data class SearchResultItemUi(
    val book: SearchBook,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
)

data class SearchUiState(
    val query: String = "",
    val committedQuery: String = "",
    val results: List<SearchResultItemUi> = emptyList(),
    val history: List<SearchKeyword> = emptyList(),
    val bookshelfHints: List<BookShelfItem> = emptyList(),
    val enabledGroups: List<String> = emptyList(),
    val enabledSources: List<BookSourcePart> = emptyList(),
    val scopeDisplay: String = "",
    val scopeDisplayNames: List<String> = emptyList(),
    val selectedScopeSourceUrls: Set<String> = emptySet(),
    val isAllScope: Boolean = true,
    val isSourceScope: Boolean = false,
    val isPrecisionSearch: Boolean = false,
    val isSearching: Boolean = false,
    val isManualStop: Boolean = false,
    val hasMore: Boolean = true,
    val processedSources: Int = 0,
    val totalSources: Int = 0,
    val showScopeSheet: Boolean = false,
    val showClearHistoryDialog: Boolean = false,
    val showSuggestions: Boolean = true,
    val emptyScopeAction: SearchEmptyScopeAction? = null,
)

data class SearchEmptyScopeAction(
    val scopeDisplay: String,
    val wasPrecisionSearch: Boolean,
)

sealed interface SearchIntent {
    data class Initialize(val key: String?, val scopeRaw: String?) : SearchIntent
    data class UpdateQuery(val query: String) : SearchIntent
    data object SubmitSearch : SearchIntent
    data object LoadMore : SearchIntent
    data object StopSearch : SearchIntent
    data object PauseEngine : SearchIntent
    data object ResumeEngine : SearchIntent
    data class UseHistoryKeyword(val keyword: String) : SearchIntent
    data class OpenSearchBook(val book: SearchBook) : SearchIntent
    data class OpenBookshelfBook(val book: BookShelfItem) : SearchIntent
    data class DeleteHistory(val item: SearchKeyword) : SearchIntent
    data class SetClearHistoryDialogVisible(val visible: Boolean) : SearchIntent
    data object ConfirmClearHistory : SearchIntent
    data class SetScopeSheetVisible(val visible: Boolean) : SearchIntent
    data object SelectAllScope : SearchIntent
    data class ToggleScopeGroup(val groupName: String) : SearchIntent
    data class ToggleScopeSource(val source: BookSourcePart) : SearchIntent
    data class RemoveScopeItem(val scopeName: String) : SearchIntent
    data class TogglePrecision(val enabled: Boolean) : SearchIntent
    data object ConfirmEmptyScopeAction : SearchIntent
    data object DismissEmptyScopeAction : SearchIntent
    data object OpenSourceManage : SearchIntent
}

sealed interface SearchEffect {
    data class OpenBookInfo(
        val name: String,
        val author: String,
        val bookUrl: String,
    ) : SearchEffect

    data object OpenSourceManage : SearchEffect

    data class ShowMessage(val message: String) : SearchEffect
}
