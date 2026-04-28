package io.legado.app.ui.book.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val searchBooksUseCase: SearchBooksUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(
            isPrecisionSearch = appCtx.getPrefBoolean(PreferKey.precisionSearch),
            scopeDisplay = SearchScope(AppConfig.searchScope).display,
            scopeDisplayNames = SearchScope(AppConfig.searchScope).displayNames,
            isAllScope = SearchScope(AppConfig.searchScope).isAll(),
            isSourceScope = SearchScope(AppConfig.searchScope).isSource(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private val bookshelfKeys = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val searchScope = SearchScope(AppConfig.searchScope)
    private val searchControl = BookSearchControl()
    private val searchResultBooks = LinkedHashMap<String, SearchBook>()

    private var searchJob: Job? = null
    private var currentSearchPage = 1

    init {
        syncScopeState()
        observeEnabledGroups()
        observeEnabledSources()
        observeBookshelf()
        observeQueryHistory()
        observeQueryBookshelfHints()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Initialize -> initialize(intent.key, intent.scopeRaw)
            is SearchIntent.UpdateQuery -> updateQuery(intent.query, showSuggestions = true)
            SearchIntent.SubmitSearch -> submitSearch()
            SearchIntent.LoadMore -> loadMore()
            SearchIntent.StopSearch -> stopSearch()
            SearchIntent.PauseEngine -> searchControl.pause()
            SearchIntent.ResumeEngine -> searchControl.resume()
            is SearchIntent.UseHistoryKeyword -> {
                updateQuery(intent.keyword, showSuggestions = false)
                submitSearch(intent.keyword)
            }

            is SearchIntent.OpenSearchBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                    )
                )
            }

            is SearchIntent.OpenBookshelfBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                    )
                )
            }

            is SearchIntent.DeleteHistory -> viewModelScope.launch {
                repository.deleteSearchKeyword(intent.item)
            }

            is SearchIntent.SetClearHistoryDialogVisible -> {
                _uiState.update { it.copy(showClearHistoryDialog = intent.visible) }
            }

            SearchIntent.ConfirmClearHistory -> {
                _uiState.update { it.copy(showClearHistoryDialog = false) }
                viewModelScope.launch {
                    repository.clearSearchKeywords()
                }
            }

            is SearchIntent.SetScopeSheetVisible -> {
                _uiState.update { it.copy(showScopeSheet = intent.visible) }
            }

            SearchIntent.SelectAllScope -> {
                searchScope.update("")
                syncScopeState()
            }

            is SearchIntent.ToggleScopeGroup -> toggleScopeGroup(intent.groupName)
            is SearchIntent.ToggleScopeSource -> toggleScopeSource(intent.source)
            is SearchIntent.RemoveScopeItem -> {
                searchScope.remove(intent.scopeName)
                syncScopeState()
            }

            is SearchIntent.TogglePrecision -> {
                appCtx.putPrefBoolean(PreferKey.precisionSearch, intent.enabled)
                _uiState.update { it.copy(isPrecisionSearch = intent.enabled) }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.ConfirmEmptyScopeAction -> handleEmptyScopeActionConfirmed()
            SearchIntent.DismissEmptyScopeAction -> {
                _uiState.update { it.copy(emptyScopeAction = null) }
            }

            SearchIntent.OpenSourceManage -> emitEffect(SearchEffect.OpenSourceManage)
        }
    }

    override fun onCleared() {
        stopSearch(manualStop = false)
        super.onCleared()
    }

    private fun initialize(key: String?, scopeRaw: String?) {
        scopeRaw?.let {
            searchScope.update(it, postValue = false)
        }
        syncScopeState()

        val initKey = key?.trim().orEmpty()
        if (initKey.isNotEmpty()) {
            updateQuery(initKey, showSuggestions = false)
            submitSearch(initKey)
        } else if (key != null) {
            updateQuery(initKey, showSuggestions = true)
        }
    }

    private fun observeEnabledGroups() {
        viewModelScope.launch {
            repository.enabledGroups
                .catch { emit(emptyList()) }
                .collect { groups ->
                    _uiState.update { it.copy(enabledGroups = groups) }
                }
        }
    }

    private fun observeEnabledSources() {
        viewModelScope.launch {
            repository.enabledSources
                .catch { emit(emptyList()) }
                .collect { sources ->
                    _uiState.update { it.copy(enabledSources = sources) }
                }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            repository.bookshelfKeys
                .catch { emit(emptySet()) }
                .collect { keys ->
                    bookshelfKeys.value = keys
                    _uiState.update { state ->
                        state.copy(results = state.results.withShelfState(keys))
                    }
                }
        }
    }

    private fun observeQueryHistory() {
        viewModelScope.launch {
            queryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { repository.searchHistory(it) }
                .catch { emit(emptyList()) }
                .collect { history ->
                    _uiState.update { it.copy(history = history) }
                }
        }
    }

    private fun observeQueryBookshelfHints() {
        viewModelScope.launch {
            queryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { repository.searchBookshelf(it) }
                .catch { emit(emptyList()) }
                .collect { books ->
                    _uiState.update { it.copy(bookshelfHints = books) }
                }
        }
    }

    private fun updateQuery(query: String, showSuggestions: Boolean) {
        if (showSuggestions && _uiState.value.isSearching) {
            stopSearch(manualStop = false)
        }
        queryFlow.value = query
        _uiState.update {
            it.copy(
                query = query,
                showSuggestions = showSuggestions,
                isManualStop = false,
                emptyScopeAction = null,
            )
        }
    }

    private fun submitSearch(keyOverride: String? = null) {
        val keyword = keyOverride?.trim() ?: queryFlow.value.trim()
        if (keyword.isBlank()) return

        updateQuery(keyword, showSuggestions = false)

        currentSearchPage = 1
        searchResultBooks.clear()
        _uiState.update {
            it.copy(
                committedQuery = keyword,
                results = emptyList(),
                isManualStop = false,
                hasMore = true,
                processedSources = 0,
                totalSources = 0,
                emptyScopeAction = null,
            )
        }

        viewModelScope.launch {
            repository.saveSearchKeyword(keyword)
        }
        startSearch(keyword, currentSearchPage)
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isSearching) return
        if (state.committedQuery.isBlank()) return
        if (!state.hasMore) return

        currentSearchPage += 1
        _uiState.update {
            it.copy(
                isManualStop = false,
                showSuggestions = false,
            )
        }
        startSearch(state.committedQuery, currentSearchPage)
    }

    private fun startSearch(keyword: String, page: Int) {
        searchJob?.cancel()
        searchControl.resume()
        searchJob = viewModelScope.launch {
            try {
                searchBooksUseCase
                    .execute(
                        BookSearchRequest(
                            keyword = keyword,
                            page = page,
                            scope = BookSearchScope(searchScope.toString()),
                            precision = _uiState.value.isPrecisionSearch,
                            concurrency = OtherConfig.threadCount,
                        ),
                        searchControl
                    )
                    .collect { event -> handleSearchEvent(event) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _uiState.update { it.copy(isSearching = false) }
                exception.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                    ?.let { emitEffect(SearchEffect.ShowMessage(it)) }
            }
        }
    }

    private fun handleSearchEvent(event: SearchRunEvent) {
        when (event) {
            SearchRunEvent.Started -> {
                _uiState.update { it.copy(isSearching = true) }
            }

            is SearchRunEvent.Progress -> {
                event.removedBookUrls.forEach { searchResultBooks.remove(it) }
                event.upsertBooks.forEach { book ->
                    searchResultBooks[book.bookUrl] = book
                }
                _uiState.update {
                    it.copy(
                        results = buildSearchResultItems(
                            shelf = bookshelfKeys.value,
                        ),
                        processedSources = event.processedSources,
                        totalSources = event.totalSources,
                    )
                }
            }

            is SearchRunEvent.Finished -> {
                _uiState.update { state ->
                    val emptyAction = if (searchResultBooks.isEmpty() && event.isEmpty && !searchScope.isAll()) {
                        SearchEmptyScopeAction(
                            scopeDisplay = searchScope.display,
                            wasPrecisionSearch = state.isPrecisionSearch,
                        )
                    } else {
                        null
                    }
                    state.copy(
                        isSearching = false,
                        hasMore = event.hasMore,
                        emptyScopeAction = emptyAction,
                    )
                }
            }
        }
    }

    private fun stopSearch(manualStop: Boolean = true) {
        searchJob?.cancel()
        searchJob = null
        _uiState.update {
            it.copy(
                isSearching = false,
                isManualStop = manualStop || it.isManualStop,
            )
        }
    }

    private fun toggleScopeGroup(groupName: String) {
        if (searchScope.isSource()) {
            searchScope.update("")
        }
        val selected = searchScope.displayNames.toMutableSet()
        if (selected.contains(groupName)) {
            selected.remove(groupName)
        } else {
            selected.add(groupName)
        }
        searchScope.update(selected.toList())
        syncScopeState()
    }

    private fun toggleScopeSource(source: BookSourcePart) {
        val selectedUrls = if (searchScope.isSource()) {
            searchScope.sourceUrls.toMutableSet()
        } else {
            mutableSetOf()
        }

        if (selectedUrls.contains(source.bookSourceUrl)) {
            selectedUrls.remove(source.bookSourceUrl)
        } else {
            selectedUrls.add(source.bookSourceUrl)
        }

        if (selectedUrls.isEmpty()) {
            searchScope.update("")
        } else {
            val selectedSources = _uiState.value.enabledSources.filter {
                selectedUrls.contains(it.bookSourceUrl)
            }
            searchScope.updateSources(selectedSources)
        }
        syncScopeState()
    }

    private fun handleEmptyScopeActionConfirmed() {
        val action = _uiState.value.emptyScopeAction ?: return
        _uiState.update { it.copy(emptyScopeAction = null) }

        if (action.wasPrecisionSearch) {
            appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
            _uiState.update { it.copy(isPrecisionSearch = false) }
        } else {
            searchScope.update("")
            syncScopeState()
        }

        restartCommittedSearchIfNeeded()
    }

    private fun restartCommittedSearchIfNeeded() {
        val committed = _uiState.value.committedQuery
        if (committed.isNotBlank()) {
            submitSearch(committed)
        }
    }

    private fun syncScopeState() {
        _uiState.update {
            it.copy(
                scopeDisplay = searchScope.display,
                scopeDisplayNames = searchScope.displayNames,
                selectedScopeSourceUrls = searchScope.sourceUrls.toSet(),
                isAllScope = searchScope.isAll(),
                isSourceScope = searchScope.isSource(),
            )
        }
    }

    private fun List<SearchBook>.toSearchResultItems(
        shelf: Set<BookShelfKey>
    ): List<SearchResultItemUi> {
        return map { book ->
            SearchResultItemUi(
                book = book,
                shelfState = resolveBookShelfStateUseCase.execute(
                    name = book.name,
                    author = book.author,
                    url = book.bookUrl,
                    shelf = shelf
                )
            )
        }
    }

    private fun buildSearchResultItems(
        shelf: Set<BookShelfKey>,
    ): List<SearchResultItemUi> {
        return searchResultBooks.values.toList().toSearchResultItems(shelf)
    }

    private fun List<SearchResultItemUi>.withShelfState(
        shelf: Set<BookShelfKey>
    ): List<SearchResultItemUi> {
        return map { item ->
            item.copy(
                shelfState = resolveBookShelfStateUseCase.execute(
                    name = item.book.name,
                    author = item.book.author,
                    url = item.book.bookUrl,
                    shelf = shelf
                )
            )
        }
    }

    private fun emitEffect(effect: SearchEffect) {
        _effects.tryEmit(effect)
    }
}
