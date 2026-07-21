package io.legado.app.ui.book.searchContent

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchContentRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class SearchContentUiState(
    val isSearching: Boolean = false,
    val searchResults: ImmutableList<SearchResult> = persistentListOf(),
    val durChapterIndex: Int = -1,
    val book: Book? = null,
    val error: Throwable? = null,
    val searchQuery: String = "",
    val replaceEnabled: Boolean = false,
    val regexReplace: Boolean = false,
    val searchHistory: ImmutableList<SearchContentHistory> = persistentListOf(),
    val historyOnlyThisBook: Boolean = true,
    val shouldAutoScroll: Boolean = false,
)

sealed interface SearchContentIntent {
    data class UpdateQuery(val value: String) : SearchContentIntent
    data class ToggleReplace(val enabled: Boolean) : SearchContentIntent
    data class ToggleRegex(val enabled: Boolean) : SearchContentIntent
    data object ToggleHistoryScope : SearchContentIntent
    data class DeleteHistory(val history: SearchContentHistory) : SearchContentIntent
    data object ClearHistory : SearchContentIntent
    data object StopSearch : SearchContentIntent
    data object MarkAutoScrollDone : SearchContentIntent
    data class OpenResult(val result: SearchResult) : SearchContentIntent
    data object LeaveSearch : SearchContentIntent
}

sealed interface SearchContentEffect {
    data object NavigateBack : SearchContentEffect
}

sealed interface SearchContentState {
    data object Loading : SearchContentState
    data object History : SearchContentState
    data object EmptyResult : SearchContentState
    data class Error(val throwable: Throwable) : SearchContentState
}

class SearchContentViewModel(
    private val bookUrl: String,
    initialSearchWord: String?,
    private val searchResultIndex: Int,
    private val bookRepository: BookRepository,
    private val searchContentRepository: SearchContentRepository,
) : ViewModel() {
    private val restoredSession = if (initialSearchWord == null) {
        searchContentRepository.getLastSession(bookUrl)
    } else null

    private val _uiState = MutableStateFlow(
        SearchContentUiState(
            searchQuery = initialSearchWord ?: restoredSession?.query.orEmpty(),
            replaceEnabled = restoredSession?.replaceEnabled ?: false,
            regexReplace = restoredSession?.regexReplace ?: false,
            shouldAutoScroll = searchResultIndex > 0,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchContentEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var searchJob: Job? = null
    private var historyJob: Job? = null
    private var resultOpened = false

    init {
        initBook()
    }

    fun onIntent(intent: SearchContentIntent) {
        when (intent) {
            is SearchContentIntent.UpdateQuery -> {
                _uiState.update { it.copy(searchQuery = intent.value) }
                executeSearch()
            }
            is SearchContentIntent.ToggleReplace -> {
                _uiState.update { it.copy(replaceEnabled = intent.enabled) }
                executeSearch()
            }
            is SearchContentIntent.ToggleRegex -> {
                _uiState.update { it.copy(regexReplace = intent.enabled) }
                executeSearch()
            }
            SearchContentIntent.ToggleHistoryScope -> {
                _uiState.update { it.copy(historyOnlyThisBook = !it.historyOnlyThisBook) }
                observeHistory()
            }
            is SearchContentIntent.DeleteHistory -> viewModelScope.launch {
                searchContentRepository.deleteHistory(intent.history.id)
            }
            SearchContentIntent.ClearHistory -> viewModelScope.launch {
                val state = _uiState.value
                searchContentRepository.clearHistory(state.book, state.historyOnlyThisBook)
            }
            SearchContentIntent.StopSearch -> stopSearch()
            SearchContentIntent.MarkAutoScrollDone ->
                _uiState.update { it.copy(shouldAutoScroll = false) }
            is SearchContentIntent.OpenResult -> openResult(intent.result)
            SearchContentIntent.LeaveSearch -> leaveSearch()
        }
    }

    private fun initBook() {
        viewModelScope.launch {
            val state = _uiState.value
            val book = bookRepository.getBook(bookUrl)
            val cachedResults = searchContentRepository.getCache(
                bookUrl,
                state.searchQuery,
                state.replaceEnabled,
                state.regexReplace,
            )
            _uiState.update {
                it.copy(
                    book = book,
                    durChapterIndex = book?.durChapterIndex ?: -1,
                    searchResults = cachedResults?.toImmutableList() ?: it.searchResults,
                )
            }
            observeHistory()
            if (cachedResults.isNullOrEmpty() && state.searchQuery.isNotBlank()) executeSearch()
        }
    }

    private fun observeHistory() {
        historyJob?.cancel()
        val state = _uiState.value
        historyJob = viewModelScope.launch {
            searchContentRepository.observeHistory(state.book, state.historyOnlyThisBook)
                .collect { history ->
                    _uiState.update { it.copy(searchHistory = history.toImmutableList()) }
                }
        }
    }

    private fun executeSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.searchQuery.isBlank()) {
            searchContentRepository.clearSession(bookUrl)
            SearchContentResult.clearResults(bookUrl)
            _uiState.update {
                it.copy(isSearching = false, searchResults = persistentListOf(), error = null)
            }
            return
        }
        searchContentRepository.beginSearch(
            bookUrl,
            state.searchQuery,
            state.replaceEnabled,
            state.regexReplace,
        )
        searchJob = viewModelScope.launch {
            state.book?.let { book ->
                searchContentRepository.saveHistory(book, state.searchQuery)
                searchContentRepository.search(
                    book,
                    state.searchQuery,
                    state.replaceEnabled,
                    state.regexReplace,
                )
                    .onStart { _uiState.update { it.copy(isSearching = true, error = null) } }
                    .onCompletion { _uiState.update { it.copy(isSearching = false) } }
                    .catch { error -> _uiState.update { it.copy(isSearching = false, error = error) } }
                    .collect { results ->
                        _uiState.update { it.copy(searchResults = results.toImmutableList()) }
                    }
            }
        }
    }

    private fun stopSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = false) }
    }

    private fun leaveSearch() {
        searchJob?.cancel()
        if (!resultOpened) SearchContentResult.clearResults(bookUrl)
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearching = false,
                searchResults = persistentListOf(),
                error = null,
            )
        }
    }

    private fun openResult(result: SearchResult) {
        stopSearch()
        val results = _uiState.value.searchResults
        val index = results.indexOf(result)
        if (index < 0) return
        resultOpened = true
        SearchContentResult.emitResult(
            SearchContentResult.Result(
                bookUrl = bookUrl,
                searchResults = results,
                index = index,
                query = result.query,
            )
        )
        _effects.tryEmit(SearchContentEffect.NavigateBack)
    }
}
