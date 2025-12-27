package io.legado.app.ui.book.searchContent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.help.IntentData
import io.legado.app.utils.postEvent
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

data class SearchUiState(
    val isSearching: Boolean = false,
    val searchResults: List<SearchResult> = emptyList(),
    val durChapterIndex: Int = -1,
    val book: Book? = null,
    val error: Throwable? = null
)

sealed interface SearchUiEffect {
    data class OpenSearchResult(
        val result: SearchResult,
        val index: Int,
        val allResults: List<SearchResult>
    ) : SearchUiEffect
}

sealed interface SearchContentState {
    data object Loading : SearchContentState
    data object EmptyQuery : SearchContentState
    data object EmptyResult : SearchContentState
    data class Error(val throwable: Throwable) : SearchContentState
}


class SearchContentViewModel( 
    private val bookRepository: BookRepository,
    private val searchContentRepository: SearchContentRepository
    ) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<SearchUiEffect>()
    val effect = _effect.asSharedFlow()

    private var searchJob: Job? = null

    fun initBook(bookUrl: String) {
        viewModelScope.launch {
            val book = bookRepository.getBook(bookUrl)
            _uiState.value = _uiState.value.copy(
                book = book,
                durChapterIndex = book?.durChapterIndex ?: -1
            )
        }
    }

    fun startSearch(query: String, replaceEnabled: Boolean, regexReplace: Boolean) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(
                isSearching = false,
                searchResults = emptyList(),
                error = null
            )}
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value.book?.let { book ->
                searchContentRepository
                    .search(book, query, replaceEnabled, regexReplace)
                    .onStart {
                        _uiState.update { it.copy(isSearching = true, error = null) }
                    }
                    .onCompletion {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                    .catch { e ->
                        _uiState.update { it.copy(isSearching = false, error = e) }
                    }
                    .collect { results ->
                        _uiState.update { it.copy(searchResults = results) }
                    }
            }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
    }

    fun onSearchResultClick(result: SearchResult, index: Int) {
        searchJob?.cancel()
        viewModelScope.launch {
            _effect.emit(
                SearchUiEffect.OpenSearchResult(
                    result = result,
                    index = index,
                    allResults = _uiState.value.searchResults
                )
            )
        }
    }

    fun onSearchResultClick(searchResult: SearchResult, index: Int, onSuccess: (key: Long) -> Unit) {
        stopSearch()
        postEvent(EventBus.SEARCH_RESULT, uiState.value.searchResults)
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", uiState.value.searchResults)
        onSuccess(key)
    }

}
