package io.legado.app.ui.book.searchContent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.help.IntentData
import io.legado.app.utils.postEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val durChapterIndex: Int = -1,
    val book: Book? = null,
    val error: Throwable? = null
)

sealed interface SearchContentState {
    data object Loading : SearchContentState
    data object History : SearchContentState
    data object EmptyResult : SearchContentState
    data class Error(val throwable: Throwable) : SearchContentState
}


class SearchContentViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val searchContentRepository: SearchContentRepository
) : ViewModel() {

    val bookUrl: String = savedStateHandle.get<String>("bookUrl") ?: ""
    private val initialSearchWord: String? = savedStateHandle.get<String>("searchWord")
    private val searchResultIndex: Int = savedStateHandle.get<Int>("searchResultIndex") ?: 0

    private val _searchQuery = MutableStateFlow(initialSearchWord ?: "")
    val searchQuery = _searchQuery.asStateFlow()

    private val _replaceEnabled = MutableStateFlow(false)
    val replaceEnabled = _replaceEnabled.asStateFlow()

    private val _regexReplace = MutableStateFlow(false)
    val regexReplace = _regexReplace.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _historyOnlyThisBook = MutableStateFlow(true)
    val historyOnlyThisBook = _historyOnlyThisBook.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchHistory = combine(_historyOnlyThisBook, _uiState) { onlyThisBook, uiState ->
        onlyThisBook to uiState.book
    }.flatMapLatest { (onlyThisBook, book) ->
        if (onlyThisBook && book != null) {
            appDb.searchContentHistoryDao.getByBook(book.name, book.author)
        } else {
            appDb.searchContentHistoryDao.getAll()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var hasAutoScrolled = false
    private var searchJob: Job? = null

    init {
        initBook()
    }

    private fun initBook() {
        viewModelScope.launch {
            val book = bookRepository.getBook(bookUrl)
            val cachedResults = searchContentRepository.getCache(bookUrl, _searchQuery.value)

            _uiState.update {
                it.copy(
                book = book,
                    durChapterIndex = book?.durChapterIndex ?: -1,
                    searchResults = cachedResults ?: it.searchResults
                )
            }

            if ((cachedResults == null || cachedResults.isEmpty()) && _searchQuery.value.isNotBlank()) {
                executeSearch()
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        executeSearch()
    }

    fun toggleReplace(enabled: Boolean) {
        _replaceEnabled.value = enabled
        executeSearch()
    }

    fun toggleRegex(enabled: Boolean) {
        _regexReplace.value = enabled
        executeSearch()
    }

    fun toggleHistoryScope() {
        _historyOnlyThisBook.value = !_historyOnlyThisBook.value
    }

    private fun executeSearch() {
        searchJob?.cancel()
        val query = _searchQuery.value
        val replace = _replaceEnabled.value
        val regex = _regexReplace.value

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    error = null
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value.book?.let { book ->
                saveSearchHistory(book, query)
                searchContentRepository
                    .search(book, query, replace, regex)
                    .onStart { _uiState.update { it.copy(isSearching = true, error = null) } }
                    .onCompletion { _uiState.update { it.copy(isSearching = false) } }
                    .catch { e -> _uiState.update { it.copy(isSearching = false, error = e) } }
                    .collect { results ->
                        _uiState.update { it.copy(searchResults = results) }
                    }
            }
        }
    }

    private suspend fun saveSearchHistory(book: Book, query: String) {
        val history = appDb.searchContentHistoryDao.get(book.name, book.author, query)
            ?: SearchContentHistory(bookName = book.name, bookAuthor = book.author, query = query)
        history.time = System.currentTimeMillis()
        appDb.searchContentHistoryDao.insert(history)
    }

    fun deleteHistory(history: SearchContentHistory) {
        viewModelScope.launch {
            appDb.searchContentHistoryDao.delete(history.id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val book = _uiState.value.book
            if (_historyOnlyThisBook.value && book != null) {
                appDb.searchContentHistoryDao.deleteByBook(book.name, book.author)
            } else {
                appDb.searchContentHistoryDao.deleteAll()
            }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = false) }
    }

    fun shouldAutoScroll(): Boolean = searchResultIndex > 0 && !hasAutoScrolled

    fun markScrollDone() {
        hasAutoScrolled = true
    }

    fun onSearchResultClick(searchResult: SearchResult, onSuccess: (key: Long) -> Unit) {
        stopSearch()
        postEvent(EventBus.SEARCH_RESULT, uiState.value.searchResults)
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", uiState.value.searchResults)
        onSuccess(key)
    }
}
