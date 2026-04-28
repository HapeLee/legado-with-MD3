package io.legado.app.ui.book.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.help.config.AppConfig
import io.legado.app.domain.model.BookShelfState
import io.legado.app.utils.exploreLayoutGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import splitties.init.appCtx

sealed class BookFilterState(val id: Int) {
    data object SHOW_ALL : BookFilterState(0)
    data object HIDE_IN_SHELF : BookFilterState(1)
    data object HIDE_SAME_NAME_AUTHOR : BookFilterState(2)
    data object SHOW_NOT_IN_SHELF_ONLY : BookFilterState(3)

    companion object {
        fun fromId(id: Int): BookFilterState = when (id) {
            1 -> HIDE_IN_SHELF
            2 -> HIDE_SAME_NAME_AUTHOR
            3 -> SHOW_NOT_IN_SHELF_ONLY
            else -> SHOW_ALL
        }
    }
}

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    data object Empty : UiState<Nothing>()
}

data class ExploreBookItemUi(
    val book: SearchBook,
    val shelfState: BookShelfState = BookShelfState.NOT_IN_SHELF,
)

class ExploreShowViewModel(
    private val repository: ExploreRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase
) : ViewModel() {

    private val _rawBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    private val _filterState = MutableStateFlow(BookFilterState.fromId(AppConfig.exploreFilterState))
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMsg = MutableStateFlow<String?>(null)
    private var bookSource: BookSource? = null
    private var sourceUrl: String? = null
    private var exploreUrl: String? = null
    private var page = 1
    private val _isEndStateFlow = MutableStateFlow(false)
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val _kinds = MutableStateFlow<List<ExploreKind>>(emptyList())
    val kinds = _kinds.asStateFlow()
    private val _selectedKindTitle = MutableStateFlow<String?>(null)
    val selectedKindTitle = _selectedKindTitle.asStateFlow()
    private val _layoutState = MutableStateFlow(AppConfig.exploreLayoutState) // 0=列表, 1=网格
    val layoutState: StateFlow<Int> = _layoutState.asStateFlow()
    private val _gridCount = MutableStateFlow(appCtx.exploreLayoutGrid)
    val gridCount = _gridCount.asStateFlow()
    val isEnd: StateFlow<Boolean> = _isEndStateFlow.asStateFlow()

    val isRefreshing = _isRefreshing.asStateFlow()

    fun saveGridCount(value: Int) {
        appCtx.exploreLayoutGrid = value
        _gridCount.value = value
    }

    val uiBooks = combine(
        _rawBooks,
        _filterState,
        _bookshelf
    ) { books, filter, bookshelf ->
        books.filter { item ->
            isBookValid(item, filter, bookshelf)
        }.map { item ->
            ExploreBookItemUi(
                book = item,
                shelfState = resolveBookShelfStateUseCase.execute(
                    name = item.name,
                    author = item.author,
                    url = item.bookUrl,
                    shelf = bookshelf
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shouldTriggerAutoLoad = combine(
        _isLoading,
        uiBooks,
        _rawBooks,
        _isEndStateFlow
    ) { loading, uiBooks, rawBooks, isEnd ->
        !loading && uiBooks.isEmpty() && rawBooks.isNotEmpty() && !isEnd
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLoading = _isLoading.asStateFlow()
    val errorMsg = _errorMsg.asStateFlow()
    val filterState = _filterState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getBookshelfItems().collect { list ->
                val keys = list.map { BookShelfKey(it.name, it.author, it.bookUrl) }.toSet()
                _bookshelf.value = keys
            }
        }
    }

    fun initData(incomingSourceUrl: String?, incomingExploreUrl: String?) {
        if (sourceUrl == incomingSourceUrl && exploreUrl == incomingExploreUrl && bookSource != null) {
            return
        }
        sourceUrl = incomingSourceUrl
        exploreUrl = incomingExploreUrl
        page = 1
        bookSource = null
        _rawBooks.value = emptyList()
        _isEndStateFlow.value = false
        _errorMsg.value = null
        _selectedKindTitle.value = null

        viewModelScope.launch {
            if (bookSource == null && incomingSourceUrl != null) {
                bookSource = repository.getBookSource(incomingSourceUrl)
                loadKinds(incomingSourceUrl)
            }
            loadMore(isRefresh = true)
        }
    }

    fun loadKinds(sourceUrl: String) {
        viewModelScope.launch {
            _kinds.value = repository.getSourceExploreKinds(sourceUrl)
        }
    }

    fun refreshKinds() {
        sourceUrl?.let { loadKinds(it) }
    }

    fun switchExploreUrl(kind: ExploreKind) {
        _selectedKindTitle.value = kind.title
        exploreUrl = kind.url
        _isEndStateFlow.value = false
        loadMore(isRefresh = true)
    }

    fun setFilterState(state: BookFilterState) {
        _filterState.value = state
        AppConfig.exploreFilterState = state.id
    }

    fun setLayout() {
        val newState = if (_layoutState.value == 0) 1 else 0
        _layoutState.value = newState
        AppConfig.exploreLayoutState = newState
    }

    fun loadMore(isRefresh: Boolean = false) {
        val source = bookSource
        val url = exploreUrl
        if (source == null || url == null || _isLoading.value || (_isEndStateFlow.value && !isRefresh)) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null

            if (isRefresh) {
                page = 1
                _isEndStateFlow.value = false
                _rawBooks.value = emptyList()
            }

            repository.exploreBook(source, url, page)
                .onSuccess { newBooks ->
                    if (newBooks.isEmpty()) {
                        _isEndStateFlow.value = true
                    } else {
                        repository.saveSearchBooks(newBooks)

                        val currentList = _rawBooks.value
                        val existingUrls = currentList.map { it.bookUrl }.toSet()

                        val uniqueNewBooks = newBooks
                            .filter { it.bookUrl !in existingUrls }
                            .distinctBy { it.bookUrl }

                        if (uniqueNewBooks.isEmpty()) {
                            _isEndStateFlow.value = true
                        } else {
                            _rawBooks.value = currentList + uniqueNewBooks
                            page++
                            _isEndStateFlow.value = false
                        }
                    }
                }
                .onFailure {
                    _errorMsg.value = it.localizedMessage
                }

            _isLoading.value = false
        }
    }

    fun getCurrentBookShelfState(item: SearchBook): BookShelfState {
        return resolveBookShelfStateUseCase.execute(
            name = item.name,
            author = item.author,
            url = item.bookUrl,
            shelf = _bookshelf.value
        )
    }

    private fun isBookValid(
        book: SearchBook,
        filter: BookFilterState,
        shelf: Set<BookShelfKey>
    ): Boolean {
        val state = resolveBookShelfStateUseCase.execute(
            name = book.name,
            author = book.author,
            url = book.bookUrl,
            shelf = shelf
        )
        return when (filter) {
            BookFilterState.SHOW_ALL -> true
            BookFilterState.HIDE_IN_SHELF -> state != BookShelfState.IN_SHELF
            BookFilterState.HIDE_SAME_NAME_AUTHOR -> state != BookShelfState.SAME_NAME_AUTHOR
            BookFilterState.SHOW_NOT_IN_SHELF_ONLY -> state == BookShelfState.NOT_IN_SHELF
        }
    }
}
