package io.legado.app.ui.book.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.exploreLayoutGrid
import io.legado.app.utils.stackTraceStr
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class ExploreShowViewModel(
    private val repository: ExploreRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val saveSearchBooksUseCase: SaveSearchBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
) : ViewModel() {

    private val _rawBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val _isLoading = MutableStateFlow(false)
    private val _isEnd = MutableStateFlow(false)
    private val _errorMsg = MutableStateFlow<String?>(null)
    private val _kinds = MutableStateFlow<List<ExploreKind>>(emptyList())
    private val _selectedKindTitle = MutableStateFlow<String?>(null)

    private var bookSource: BookSource? = null
    private var sourceUrl: String? = null
    private var exploreUrl: String? = null
    private var page = 1

    private val _uiState = MutableStateFlow(
        ExploreShowUiState(
            layoutState = AppConfig.exploreLayoutState,
            gridCount = appCtx.exploreLayoutGrid,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExploreShowEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        observeBookshelf()
        combineUiState()
    }

    fun onIntent(intent: ExploreShowIntent) {
        when (intent) {
            is ExploreShowIntent.InitData -> initData(intent.sourceUrl, intent.exploreUrl)
            ExploreShowIntent.LoadMore -> loadMore()
            ExploreShowIntent.Refresh -> loadMore(isRefresh = true)
            is ExploreShowIntent.SwitchKind -> switchKind(intent.kind)
            ExploreShowIntent.ToggleLayout -> toggleLayout()
            is ExploreShowIntent.SaveGridCount -> saveGridCount(intent.count)
            is ExploreShowIntent.ShowSheet -> _uiState.update { it.copy(sheet = intent.sheet) }
            ExploreShowIntent.DismissSheet -> _uiState.update { it.copy(sheet = ExploreShowSheet.None) }
            is ExploreShowIntent.OpenBook -> emitEffect(
                ExploreShowEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey,
                )
            )

            is ExploreShowIntent.AddToShelf -> viewModelScope.launch {
                addToBookshelfUseCase.execute(intent.book)
            }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            repository.getBookshelfItems().collect { list ->
                _bookshelf.value = list.map {
                    BookShelfKey(it.name, it.author, it.bookUrl)
                }.toSet()
            }
        }
    }

    private fun combineUiState() {
        viewModelScope.launch {
            combine(
                _rawBooks,
                _bookshelf,
                _isLoading,
                _isEnd,
                _errorMsg,
                _kinds,
                _selectedKindTitle,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val rawBooks = values[0] as List<SearchBook>
                val bookshelf = values[1] as Set<BookShelfKey>
                val isLoading = values[2] as Boolean
                val isEnd = values[3] as Boolean
                val errorMsg = values[4] as String?
                val kinds = values[5] as List<ExploreKind>
                val selectedKindTitle = values[6] as String?

                val books = rawBooks.map { item ->
                    ExploreBookItemUi(
                        book = item,
                        shelfState = resolveBookShelfStateUseCase.execute(
                            name = item.name,
                            author = item.author,
                            url = item.bookUrl,
                            shelf = bookshelf,
                        )
                    )
                }

                ExploreShowUiState(
                    sourceUrl = sourceUrl,
                    books = books.toImmutableList(),
                    kinds = kinds.toImmutableList(),
                    selectedKindTitle = selectedKindTitle,
                    layoutState = _uiState.value.layoutState,
                    gridCount = _uiState.value.gridCount,
                    isLoading = isLoading,
                    isRefreshing = isLoading && page == 1,
                    isEnd = isEnd,
                    errorMsg = errorMsg,
                    sheet = _uiState.value.sheet,
                )
            }.collect { newState ->
                val oldState = _uiState.value
                _uiState.value = newState

                if (newState.books.isEmpty() && _rawBooks.value.isNotEmpty() && !newState.isEnd && !newState.isLoading) {
                    loadMore()
                }
            }
        }
    }

    private fun initData(incomingSourceUrl: String?, incomingExploreUrl: String?) {
        if (sourceUrl == incomingSourceUrl && exploreUrl == incomingExploreUrl && bookSource != null) {
            return
        }
        sourceUrl = incomingSourceUrl
        exploreUrl = incomingExploreUrl
        page = 1
        bookSource = null
        _rawBooks.value = emptyList()
        _isEnd.value = false
        _errorMsg.value = null
        _selectedKindTitle.value = null
        _kinds.value = emptyList()

        viewModelScope.launch {
            if (bookSource == null && incomingSourceUrl != null) {
                bookSource = repository.getBookSource(incomingSourceUrl)
            }

            if (exploreUrl == null && bookSource != null) {
                loadKinds(incomingSourceUrl!!)
            }

            loadMore(isRefresh = true)
        }
    }

    private fun loadKinds(sourceUrl: String) {
        viewModelScope.launch {
            _kinds.value = repository.getSourceExploreKinds(sourceUrl)
        }
    }

    private fun switchKind(kind: ExploreKind) {
        _selectedKindTitle.value = kind.title
        exploreUrl = kind.url
        _isEnd.value = false
        loadMore(isRefresh = true)
    }

    private fun toggleLayout() {
        _uiState.update {
            val newState = if (it.layoutState == 0) 1 else 0
            AppConfig.exploreLayoutState = newState
            it.copy(layoutState = newState)
        }
    }

    private fun saveGridCount(count: Int) {
        appCtx.exploreLayoutGrid = count
        _uiState.update { it.copy(gridCount = count) }
    }

    private fun loadMore(isRefresh: Boolean = false) {
        val source = bookSource
        val url = exploreUrl ?: source?.exploreUrl
        if (source == null || url == null || _isLoading.value || (_isEnd.value && !isRefresh)) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMsg.value = null

            if (isRefresh) {
                page = 1
                _isEnd.value = false
                _rawBooks.value = emptyList()
            }

            kotlin.runCatching {
                exploreBooksUseCase.execute(source.bookSourceUrl, url, args = null, page)
            }.onSuccess { result ->
                if (result.books.isEmpty()) {
                    _isEnd.value = true
                } else {
                    saveSearchBooksUseCase.save(result.books)

                    val currentList = _rawBooks.value
                    val existingUrls = currentList.map { it.bookUrl }.toSet()

                    val uniqueNewBooks = result.books
                        .filter { it.bookUrl !in existingUrls }
                        .distinctBy { it.bookUrl }

                    if (uniqueNewBooks.isEmpty()) {
                        _isEnd.value = true
                    } else {
                        _rawBooks.value = currentList + uniqueNewBooks
                        page++
                        _isEnd.value = false
                    }
                }
            }.onFailure {
                _errorMsg.value = it.stackTraceStr
            }

            _isLoading.value = false
        }
    }

    private fun emitEffect(effect: ExploreShowEffect) {
        _effects.tryEmit(effect)
    }
}
