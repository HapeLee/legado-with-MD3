package io.legado.app.ui.book.changesource

import androidx.compose.runtime.Immutable
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ChangeChapterSourceUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchProgress: Pair<Int, String> = 0 to "",
    val totalSourceCount: Int = 0,
    val searchResults: ImmutableList<SearchBook> = persistentListOf(),
    // Options
    val checkAuthor: Boolean = true,
    val loadInfo: Boolean = true,
    val loadToc: Boolean = true,
    val loadWordCount: Boolean = true,
    // TOC view
    val showToc: Boolean = false,
    val selectedSourceName: String = "",
    val tocItems: ImmutableList<BookChapter> = persistentListOf(),
    val isLoadingToc: Boolean = false,
    val currentTocIndex: Int = -1,
    // Scope filter
    val scopeState: ScopeUiState = ScopeUiState(
        isAll = true,
        isSource = false,
        displayNames = emptyList(),
        sourceUrls = emptyList()
    ),
    val enabledGroups: ImmutableList<String> = persistentListOf(),
    val enabledSources: ImmutableList<BookSourcePart> = persistentListOf(),
    // Source book map (for score flow)
    val bookMap: Map<String, SearchBook> = emptyMap(),
)

@Immutable
data class ScopeUiState(
    val isAll: Boolean,
    val isSource: Boolean,
    val displayNames: List<String>,
    val sourceUrls: List<String>
)

sealed interface ChangeChapterSourceIntent {
    // Search
    data class UpdateQuery(val query: String) : ChangeChapterSourceIntent
    data object StartStopSearch : ChangeChapterSourceIntent
    data object Refresh : ChangeChapterSourceIntent

    // Source selection
    data class SelectSource(val searchBook: SearchBook) : ChangeChapterSourceIntent
    data object BackFromToc : ChangeChapterSourceIntent

    // Chapter selection
    data class SelectChapter(val chapter: BookChapter) : ChangeChapterSourceIntent

    // Options menu
    data class SetCheckAuthor(val enabled: Boolean) : ChangeChapterSourceIntent
    data class SetLoadInfo(val enabled: Boolean) : ChangeChapterSourceIntent
    data class SetLoadToc(val enabled: Boolean) : ChangeChapterSourceIntent
    data class SetLoadWordCount(val enabled: Boolean) : ChangeChapterSourceIntent

    // Source actions
    data class TopSource(val searchBook: SearchBook) : ChangeChapterSourceIntent
    data class BottomSource(val searchBook: SearchBook) : ChangeChapterSourceIntent
    data class DisableSource(val searchBook: SearchBook) : ChangeChapterSourceIntent
    data class DeleteSource(val searchBook: SearchBook) : ChangeChapterSourceIntent

    // Scope filter
    data object ShowFilterSheet : ChangeChapterSourceIntent
    data object DismissFilterSheet : ChangeChapterSourceIntent
    data object SelectAllScope : ChangeChapterSourceIntent
    data class ToggleScopeGroup(val groupName: String) : ChangeChapterSourceIntent
    data class ToggleScopeSource(val source: BookSourcePart) : ChangeChapterSourceIntent
    data object ApplyScope : ChangeChapterSourceIntent
}

sealed interface ChangeChapterSourceEffect {
    data class ReplaceContent(val content: String) : ChangeChapterSourceEffect
    data class ShowToast(val message: String) : ChangeChapterSourceEffect
    data object Dismiss : ChangeChapterSourceEffect
}
