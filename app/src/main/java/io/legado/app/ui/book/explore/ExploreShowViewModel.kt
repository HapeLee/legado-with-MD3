package io.legado.app.ui.book.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import android.content.res.Configuration
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.utils.stackTraceStr
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx

private data class ExploreShowLoadState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEnd: Boolean = false,
    val errorMsg: String? = null,
)

private data class ExploreShowKindState(
    val kinds: List<ExploreKind> = emptyList(),
    val selectedKindTitle: String? = null,
)

private data class ExploreShowDisplayState(
    val sourceUrl: String? = null,
    val layoutState: Int,
    val gridCount: Int,
    val sheet: ExploreShowSheet = ExploreShowSheet.None,
)

class ExploreShowViewModel(
    private val repository: ExploreRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val saveSearchBooksUseCase: SaveSearchBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val localPreferencesRepository: LocalPreferencesRepository,
) : ViewModel() {

    private val _rawBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val _loadState = MutableStateFlow(ExploreShowLoadState())
    private val _kindState = MutableStateFlow(ExploreShowKindState())
    private val _displayState = MutableStateFlow(
        ExploreShowDisplayState(
            layoutState = 0,
            gridCount = if (appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 7 else 3,
        )
    )

    private var sourceUrl: String? = null
    private var exploreUrl: String? = null
    private var initialExploreUrl: String? = null
    private var initialized = false
    private var page = 1
    private var autoPageCount = 0

    companion object {
        private const val MAX_AUTO_PAGES = 3
        private const val AUTO_PAGE_DELAY_MS = 500L
    }

    private val _uiState = MutableStateFlow(
        ExploreShowUiState(
            layoutState = _displayState.value.layoutState,
            gridCount = _displayState.value.gridCount,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExploreShowEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        observeBookshelf()
        combineUiState()
        loadLayoutMode()
        loadGridCount()
    }

    fun onIntent(intent: ExploreShowIntent) {
        when (intent) {
            is ExploreShowIntent.InitData -> initData(intent.sourceUrl, intent.exploreUrl)
            ExploreShowIntent.LoadMore -> loadMore()
            ExploreShowIntent.ForceLoadNext -> loadMore(forceLoad = true)
            ExploreShowIntent.Refresh -> loadMore(isRefresh = true)
            is ExploreShowIntent.SwitchKind -> switchKind(intent.kind)
            ExploreShowIntent.ToggleLayout -> toggleLayout()
            is ExploreShowIntent.SaveGridCount -> saveGridCount(intent.count)
            is ExploreShowIntent.ShowSheet -> _displayState.update { it.copy(sheet = intent.sheet) }
            ExploreShowIntent.DismissSheet -> _displayState.update { it.copy(sheet = ExploreShowSheet.None) }
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
                _loadState,
                _kindState,
                _displayState,
            ) { rawBooks, bookshelf, loadState, kindState, displayState ->
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
                    sourceUrl = displayState.sourceUrl,
                    books = books.toImmutableList(),
                    kinds = kindState.kinds.toImmutableList(),
                    selectedKindTitle = kindState.selectedKindTitle,
                    layoutState = displayState.layoutState,
                    gridCount = displayState.gridCount,
                    isLoading = loadState.isLoading,
                    isRefreshing = loadState.isRefreshing,
                    isEnd = loadState.isEnd,
                    errorMsg = loadState.errorMsg,
                    sheet = displayState.sheet,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun initData(incomingSourceUrl: String, incomingExploreUrl: String?) {
        if (initialized && sourceUrl == incomingSourceUrl && initialExploreUrl == incomingExploreUrl) {
            return
        }
        initialized = true
        sourceUrl = incomingSourceUrl
        initialExploreUrl = incomingExploreUrl
        exploreUrl = incomingExploreUrl
        page = 1
        autoPageCount = 0
        _rawBooks.value = emptyList()
        _loadState.value = ExploreShowLoadState()
        _kindState.value = ExploreShowKindState()
        _displayState.update {
            it.copy(
                sourceUrl = incomingSourceUrl,
                sheet = ExploreShowSheet.None,
            )
        }

        if (incomingExploreUrl == null) {
            viewModelScope.launch {
                loadKinds(incomingSourceUrl)
            }
        }

        loadMore(isRefresh = true)
    }

    private suspend fun loadKinds(sourceUrl: String) {
        _kindState.update { it.copy(kinds = repository.getSourceExploreKinds(sourceUrl)) }
    }

    private fun switchKind(kind: ExploreKind) {
        _kindState.update { it.copy(selectedKindTitle = kind.title) }
        exploreUrl = kind.url
        _loadState.update { it.copy(isEnd = false) }
        autoPageCount = 0
        loadMore(isRefresh = true)
    }

    private fun toggleLayout() {
        _displayState.update {
            val layoutState = if (it.layoutState == 0) 1 else 0
            viewModelScope.launch {
                localPreferencesRepository.updatePreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, layoutState)
            }
            it.copy(layoutState = layoutState)
        }
    }

    private fun loadLayoutMode() {
        viewModelScope.launch {
            val mode = localPreferencesRepository.getPreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, 0).first()
            _displayState.update { it.copy(layoutState = mode) }
        }
    }

    private fun loadGridCount() {
        viewModelScope.launch {
            val isLandscape = appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val key = if (isLandscape) {
                LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_LANDSCAPE
            } else {
                LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_PORTRAIT
            }
            val default = if (isLandscape) 7 else 3
            val count = localPreferencesRepository.getPreference(key, default).first()
            _displayState.update { it.copy(gridCount = count) }
        }
    }

    private fun saveGridCount(count: Int) {
        val isLandscape = appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key = if (isLandscape) {
            LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_LANDSCAPE
        } else {
            LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_PORTRAIT
        }
        viewModelScope.launch {
            localPreferencesRepository.updatePreference(key, count)
        }
        _displayState.update { it.copy(gridCount = count) }
    }

    private fun loadMore(isRefresh: Boolean = false, forceLoad: Boolean = false) {
        val source = sourceUrl
        val url = exploreUrl
        val loadState = _loadState.value
        if (source == null || loadState.isLoading || (loadState.isEnd && !isRefresh && !forceLoad)) return

        _loadState.update {
            it.copy(
                isLoading = true,
                isRefreshing = isRefresh,
                isEnd = if (isRefresh || forceLoad) false else it.isEnd,
                errorMsg = null,
            )
        }

        viewModelScope.launch {
            if (isRefresh) {
                page = 1
                autoPageCount = 0
                _rawBooks.value = emptyList()
            }

            if (forceLoad) {
                autoPageCount = 0
            }

            fetchPage(source, url)
        }
    }

    private suspend fun fetchPage(sourceUrl: String, url: String?) {
        kotlin.runCatching {
            exploreBooksUseCase.execute(sourceUrl, url, args = null, page)
        }.onSuccess { result ->
            val currentList = _rawBooks.value
            val existingUrls = currentList.map { it.bookUrl }.toSet()
            val uniqueNewBooks = result.books
                .filter { it.bookUrl !in existingUrls }
                .distinctBy { it.bookUrl }

            if (result.books.isNotEmpty()) {
                saveSearchBooksUseCase.save(result.books)
            }

            if (uniqueNewBooks.isEmpty()) {
                fetchNextAutoPageOrFinish(sourceUrl, url)
            } else {
                _rawBooks.value = currentList + uniqueNewBooks
                page++
                autoPageCount = 0
                _loadState.update { it.copy(isEnd = false) }
                finishLoading()
            }
        }.onFailure { throwable ->
            _loadState.update { it.copy(errorMsg = throwable.stackTraceStr) }
            finishLoading()
        }
    }

    private suspend fun fetchNextAutoPageOrFinish(sourceUrl: String, url: String?) {
        page++
        autoPageCount++
        if (autoPageCount >= MAX_AUTO_PAGES) {
            _loadState.update { it.copy(isEnd = true) }
            finishLoading()
        } else {
            delay(AUTO_PAGE_DELAY_MS)
            fetchPage(sourceUrl, url)
        }
    }

    private fun finishLoading() {
        _loadState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
            )
        }
    }

    private fun emitEffect(effect: ExploreShowEffect) {
        _effects.tryEmit(effect)
    }
}
