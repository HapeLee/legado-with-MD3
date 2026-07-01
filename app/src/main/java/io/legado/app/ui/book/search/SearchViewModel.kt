package io.legado.app.ui.book.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.MatchMode
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookSearchControl
import io.legado.app.domain.usecase.BookSearchRequest
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SearchRunEvent
import io.legado.app.ui.config.otherConfig.OtherConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val searchBooksUseCase: SearchBooksUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val localPreferencesRepository: LocalPreferencesRepository,
) : ViewModel() {

    val searchLayoutMode = localPreferencesRepository
        .getPreference(LocalPreferencesKeys.SEARCH_LAYOUT_MODE, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun toggleSearchLayout() {
        viewModelScope.launch {
            val newMode = if (searchLayoutMode.value == 0) 1 else 0
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.SEARCH_LAYOUT_MODE, newMode
            )
        }
    }

    private val matchModeFlow = localPreferencesRepository
        .getPreference(LocalPreferencesKeys.MATCH_MODE, MatchMode.DEFAULT.value)
        .distinctUntilChanged()
        .map { MatchMode.of(it) }

    private val _uiState = MutableStateFlow(
        SearchUiState(
            scopeDisplay = SearchScope("").display,
            scopeDisplayNames = SearchScope("").displayNames.toImmutableList(),
            isAllScope = SearchScope("").isAll(),
            isSourceScope = SearchScope("").isSource(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val queryFlow = MutableStateFlow("")
    private val bookshelfKeys = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private var persistedSearchScopeRaw = ""
    private var hasTemporaryScope = false
    private val searchScope = SearchScope("")
    private val searchScopeReady = CompletableDeferred<Unit>()
    private val preferenceWriteScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val searchControl = BookSearchControl()
    private val searchResultBooks = LinkedHashMap<SearchResultKey, SearchBook>()

    private var initializeJob: Job? = null
    private var lastInitializedKey: String? = null
    private var lastInitializedScopeRaw: String? = null
    private var hasInitialized = false
    private var searchJob: Job? = null
    private var currentSearchPage = 1
    private var resultCountBeforeCurrentPage = 0
    private var wasSearching = false

    init {
        syncScopeState()
        observeSearchScope()
        observeEnabledGroups()
        observeEnabledSources()
        observeBookshelf()
        observeQueryHistory()
        observeQueryBookshelfHints()
        observeMatchMode()
    }

    fun onAddToShelf(book: SearchBook) {
        viewModelScope.launch {
            addToBookshelfUseCase.execute(book)
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Initialize -> initialize(intent.key, intent.scopeRaw)
            is SearchIntent.UpdateQuery -> updateQuery(intent.query, intent.showSuggestions)
            SearchIntent.SubmitSearch -> submitSearch()
            SearchIntent.LoadMore -> loadMore()
            SearchIntent.StopSearch -> stopSearch()
            SearchIntent.ClearSearchResults -> clearSearchResults()
            SearchIntent.PauseEngine -> {
                wasSearching = wasSearching || (searchJob?.isActive == true)
                searchControl.pause()
            }

            SearchIntent.ResumeEngine -> {
                searchControl.resume()
                if (wasSearching) {
                    val state = _uiState.value
                    if (state.committedQuery.isNotBlank() && searchJob?.isActive != true) {
                        startSearch(state.committedQuery, currentSearchPage)
                    }
                    wasSearching = false
                }
            }
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
                        origin = intent.book.origin,
                        coverPath = intent.book.coverUrl,
                        sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }

            is SearchIntent.OpenBookshelfBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                        origin = intent.book.origin,
                        coverPath = intent.book.getDisplayCover(),
                        sharedCoverKey = null,
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

            is SearchIntent.SetSettingsSheetVisible -> {
                _uiState.update { it.copy(showSettingsSheet = intent.visible) }
            }

            is SearchIntent.ToggleSourceType -> {
                _uiState.update { state ->
                    val current = state.selectedSourceTypes
                    val next = if (current.contains(intent.type)) {
                        (current - intent.type).toImmutableSet()
                    } else {
                        (current + intent.type).toImmutableSet()
                    }
                    state.copy(selectedSourceTypes = next)
                }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.ClearAllSourceTypes -> {
                _uiState.update { it.copy(selectedSourceTypes = persistentSetOf()) }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.SelectAllScope -> {
                val oldScope = searchScope.toString()
                searchScope.update("")
                persistSearchScope()
                syncScopeState(restartSearch = true, oldScope = oldScope)
            }

            is SearchIntent.ApplyScopeSelection -> applyScopeSelection(intent)
            is SearchIntent.ToggleScopeGroup -> toggleScopeGroup(intent.groupName)
            is SearchIntent.ToggleScopeSource -> toggleScopeSource(intent.source)
            is SearchIntent.RemoveScopeItem -> {
                val oldScope = searchScope.toString()
                searchScope.remove(intent.scopeName)
                persistSearchScope()
                syncScopeState(restartSearch = true, oldScope = oldScope)
            }

            is SearchIntent.SetMatchMode -> {
                _uiState.update { it.copy(matchMode = intent.mode) }
                viewModelScope.launch {
                    localPreferencesRepository.updatePreference(
                        LocalPreferencesKeys.MATCH_MODE, intent.mode.value
                    )
                }
                restartCommittedSearchIfNeeded()
            }

            SearchIntent.ConfirmEmptyScopeAction -> handleEmptyScopeActionConfirmed()
            SearchIntent.DismissEmptyScopeAction -> {
                _uiState.update { it.copy(emptyScopeAction = null) }
            }

            SearchIntent.OpenSourceManage -> emitEffect(SearchEffect.OpenSourceManage)

            is SearchIntent.ExpandSource -> {
                val state = _uiState.value
                if (state.expandedSourceUrl == intent.sourceUrl) {
                    _uiState.update { it.copy(showExpandedSource = true) }
                    return
                }
                _uiState.update {
                    it.copy(
                        expandedSourceUrl = intent.sourceUrl,
                        expandedSourceName = intent.sourceName,
                        expandedSourceBooks = persistentListOf(),
                        expandedSourceLoading = true,
                        expandedSourceEnd = false,
                        expandedSourceError = null,
                        expandedSourcePage = 1,
                        showExpandedSource = true,
                        expandedSourceSavedScrollIndex = 0,
                        expandedSourceSavedScrollOffset = 0,
                    )
                }
                loadExpandedSourcePage(intent.sourceUrl, page = 1)
            }

            SearchIntent.DismissExpandedSource -> {
                _uiState.update {
                    it.copy(showExpandedSource = false)
                }
            }

            SearchIntent.LoadMoreExpandedSource -> {
                val state = _uiState.value
                val sourceUrl = state.expandedSourceUrl ?: return
                if (state.expandedSourceLoading || state.expandedSourceEnd) return
                _uiState.update {
                    it.copy(
                        expandedSourceLoading = true,
                        expandedSourceError = null
                    )
                }
                loadExpandedSourcePage(sourceUrl, page = state.expandedSourcePage)
            }

            is SearchIntent.OpenExpandedSourceBook -> {
                emitEffect(
                    SearchEffect.OpenBookInfo(
                        name = intent.book.name,
                        author = intent.book.author,
                        bookUrl = intent.book.bookUrl,
                        origin = intent.book.origin,
                        coverPath = intent.book.coverUrl,
                        sharedCoverKey = intent.sharedCoverKey,
                    )
                )
            }

            is SearchIntent.SaveScrollState -> {
                _uiState.update {
                    it.copy(
                        savedScrollIndex = intent.index,
                        savedScrollOffset = intent.offset,
                    )
                }
            }

            is SearchIntent.SaveExpandedSourceScrollState -> {
                _uiState.update {
                    it.copy(
                        expandedSourceSavedScrollIndex = intent.index,
                        expandedSourceSavedScrollOffset = intent.offset,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        stopSearch(manualStop = false)
        super.onCleared()
    }

    private fun initialize(key: String?, scopeRaw: String?) {
        initializeJob?.cancel()
        initializeJob = viewModelScope.launch {
            searchScopeReady.await()
            initializeAfterScopeLoaded(key, scopeRaw)
        }
    }

    private fun initializeAfterScopeLoaded(key: String?, scopeRaw: String?) {
        val normalizedKey = key?.trim()
        val normalizedScopeRaw = scopeRaw?.takeIf { it.isNotBlank() }
        val isSameRequest = hasInitialized &&
                lastInitializedKey == normalizedKey &&
                lastInitializedScopeRaw == normalizedScopeRaw
        lastInitializedKey = normalizedKey
        lastInitializedScopeRaw = normalizedScopeRaw
        hasInitialized = true

        val temporaryScope = scopeRaw?.takeIf { it.isNotBlank() }
        if (temporaryScope != null) {
            hasTemporaryScope = true
            searchScope.update(temporaryScope, postValue = false)
            syncScopeState()
        } else if (hasTemporaryScope) {
            hasTemporaryScope = false
            searchScope.update(persistedSearchScopeRaw, postValue = false)
            syncScopeState()
        }

        // When the ViewModel already holds a non-empty committed query,
        // it means a search session is in progress or completed.
        // This happens when returning from BookInfo — the LaunchedEffect
        // re-fires but we must not wipe the existing results.
        val hasActiveSearch = _uiState.value.committedQuery.isNotEmpty()
        if (isSameRequest && hasActiveSearch) return

        clearSearchResults()

        val initKey = normalizedKey.orEmpty()
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
                    _uiState.update { it.copy(enabledGroups = groups.toImmutableList()) }
                }
        }
    }

    private fun observeEnabledSources() {
        viewModelScope.launch {
            repository.enabledSources
                .catch { emit(emptyList()) }
                .collect { sources ->
                    _uiState.update { it.copy(enabledSources = sources.toImmutableList()) }
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
                        state.copy(results = state.results.withShelfState(keys).toImmutableList())
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
                    _uiState.update { it.copy(history = history.toImmutableList()) }
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
                    _uiState.update { it.copy(bookshelfHints = books.toImmutableList()) }
                }
        }
    }

    private fun observeMatchMode() {
        viewModelScope.launch {
            matchModeFlow.collect { mode ->
                _uiState.update { it.copy(matchMode = mode) }
            }
        }
    }

    private fun updateQuery(query: String, showSuggestions: Boolean) {
        val currentState = _uiState.value
        val isSameQuery = currentState.query == query
        val sameUiFlag = currentState.showSuggestions == showSuggestions
        if (isSameQuery && sameUiFlag) {
            return
        }
        if (showSuggestions && currentState.isSearching && !isSameQuery) {
            stopSearch(manualStop = false)
        }
        queryFlow.value = query
        _uiState.update {
            it.copy(
                query = query,
                showSuggestions = showSuggestions,
                emptyScopeAction = null,
            )
        }
    }

    private fun submitSearch(keyOverride: String? = null) {
        val keyword = keyOverride?.trim() ?: queryFlow.value.trim()
        if (keyword.isBlank()) return

        updateQuery(keyword, showSuggestions = false)

        // Cancel the old search job BEFORE clearing results to prevent
        // stale Progress events from re-inserting books into the map.
        searchJob?.cancel()
        searchJob = null

        currentSearchPage = 1
        searchResultBooks.clear()
        _uiState.update {
            it.copy(
                committedQuery = keyword,
                results = persistentListOf(),
                isManualStop = false,
                hasMore = true,
                processedSources = 0,
                totalSources = 0,
                emptyScopeAction = null,
                expandedSourceUrl = null,
                expandedSourceName = null,
                expandedSourceBooks = persistentListOf(),
                expandedSourceLoading = false,
                expandedSourceEnd = false,
                expandedSourceError = null,
                expandedSourcePage = 1,
                showExpandedSource = false,
                expandedSourceSavedScrollIndex = 0,
                expandedSourceSavedScrollOffset = 0,
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
        resultCountBeforeCurrentPage = searchResultBooks.size
        wasSearching = true
        searchJob = viewModelScope.launch {
            try {
                searchBooksUseCase
                    .execute(
                        BookSearchRequest(
                            keyword = keyword,
                            page = page,
                            scope = BookSearchScope(searchScope.toString()),
                            matchMode = _uiState.value.matchMode,
                            concurrency = OtherConfig.threadCount,
                            types = _uiState.value.selectedSourceTypes.takeIf { it.isNotEmpty() },
                        ),
                        searchControl
                    )
                    .collect { event -> handleSearchEvent(event) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                wasSearching = false
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
                removeSearchResults(event.removedBookUrls)
                mergeSearchResults(event.upsertBooks)
                _uiState.update {
                    it.copy(
                        results = buildSearchResultItems(
                            shelf = bookshelfKeys.value,
                        ).toImmutableList(),
                        processedSources = event.processedSources,
                        totalSources = event.totalSources,
                    )
                }
            }

            is SearchRunEvent.Finished -> {
                wasSearching = false
                _uiState.update { state ->
                    val emptyAction = if (searchResultBooks.isEmpty() && event.isEmpty && !searchScope.isAll()) {
                        SearchEmptyScopeAction(
                            scopeDisplay = searchScope.display,
                            wasMatchMode = state.matchMode,
                        )
                    } else {
                        null
                    }
                    val hasNewPageResults = searchResultBooks.size > resultCountBeforeCurrentPage
                    val exactSearchShouldStopAfterFirstPage =
                        state.matchMode == MatchMode.EXACT &&
                            currentSearchPage == 1 &&
                            searchResultBooks.size <= EXACT_SEARCH_SINGLE_PAGE_RESULT_THRESHOLD
                    state.copy(
                        isSearching = false,
                        hasMore = event.hasMore &&
                            hasNewPageResults &&
                            !exactSearchShouldStopAfterFirstPage,
                        emptyScopeAction = emptyAction,
                    )
                }
            }
        }
    }

    private fun stopSearch(manualStop: Boolean = true) {
        searchJob?.cancel()
        searchJob = null
        wasSearching = false
        _uiState.update {
            it.copy(
                isSearching = false,
                isManualStop = manualStop || it.isManualStop,
            )
        }
    }

    private fun clearSearchResults() {
        stopSearch(manualStop = true)
        searchResultBooks.clear()
        queryFlow.value = ""
        _uiState.update {
            it.copy(
                query = "",
                committedQuery = "",
                results = persistentListOf(),
                processedSources = 0,
                totalSources = 0,
                isSearching = false,
                isManualStop = false,
                hasMore = true,
                showSuggestions = true,
                emptyScopeAction = null,
                expandedSourceUrl = null,
                expandedSourceName = null,
                expandedSourceBooks = persistentListOf(),
                expandedSourceLoading = false,
                expandedSourceEnd = false,
                expandedSourceError = null,
                expandedSourcePage = 1,
                showExpandedSource = false,
                expandedSourceSavedScrollIndex = 0,
                expandedSourceSavedScrollOffset = 0,
            )
        }
    }

    private fun toggleScopeGroup(groupName: String) {
        val oldScope = searchScope.toString()
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
        persistSearchScope()
        syncScopeState(restartSearch = true, oldScope = oldScope)
    }

    private fun toggleScopeSource(source: BookSourcePart) {
        val oldScope = searchScope.toString()
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
        persistSearchScope()
        syncScopeState(restartSearch = true, oldScope = oldScope)
    }

    private fun handleEmptyScopeActionConfirmed() {
        val action = _uiState.value.emptyScopeAction ?: return
        _uiState.update { it.copy(emptyScopeAction = null) }

        if (action.wasMatchMode == MatchMode.EXACT) {
            _uiState.update { it.copy(matchMode = MatchMode.DEFAULT) }
            viewModelScope.launch {
                localPreferencesRepository.updatePreference(
                    LocalPreferencesKeys.MATCH_MODE, MatchMode.DEFAULT.value
                )
            }
        } else {
            searchScope.update("")
            persistSearchScope()
            syncScopeState()
        }

        restartCommittedSearchIfNeeded()
    }

    private fun restartCommittedSearchIfNeeded() {
        val state = _uiState.value
        val committed = state.committedQuery
        if (
            committed.isNotBlank() &&
            state.query.trim() == committed &&
            !state.showSuggestions &&
            !state.isManualStop
        ) {
            submitSearch(committed)
        }
    }

    private fun applyScopeSelection(intent: SearchIntent.ApplyScopeSelection) {
        val oldScope = searchScope.toString()
        when {
            intent.isSourceScope -> searchScope.updateSources(intent.sources)
            intent.groupNames.isNotEmpty() -> searchScope.update(intent.groupNames)
            else -> searchScope.update("")
        }
        persistSearchScope()
        syncScopeState(restartSearch = true, oldScope = oldScope)
    }

    private fun syncScopeState(
        restartSearch: Boolean = false,
        oldScope: String? = null,
    ) {
        val scopeChanged = oldScope == null || oldScope != searchScope.toString()
        _uiState.update {
            it.copy(
                scopeDisplay = searchScope.display,
                scopeDisplayNames = searchScope.displayNames.toImmutableList(),
                selectedScopeSourceUrls = searchScope.sourceUrls.toImmutableSet(),
                isAllScope = searchScope.isAll(),
                isSourceScope = searchScope.isSource(),
            )
        }
        if (restartSearch && scopeChanged) {
            restartCommittedSearchIfNeeded()
        }
    }

    private fun observeSearchScope() {
        viewModelScope.launch {
            localPreferencesRepository
                .getPreference(LocalPreferencesKeys.SEARCH_SCOPE, "")
                .distinctUntilChanged()
                .collect { scopeRaw ->
                    persistedSearchScopeRaw = scopeRaw
                    if (!hasTemporaryScope && scopeRaw != searchScope.toString()) {
                        searchScope.update(scopeRaw, postValue = false)
                        syncScopeState()
                    }
                    searchScopeReady.complete(Unit)
                }
        }
    }

    private fun persistSearchScope() {
        hasTemporaryScope = false
        persistedSearchScopeRaw = searchScope.toString()
        val scopeRaw = persistedSearchScopeRaw
        preferenceWriteScope.launch {
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.SEARCH_SCOPE,
                scopeRaw
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
        return searchResultBooks.values
            .sortedWithSearchPriority(_uiState.value.committedQuery, _uiState.value.matchMode)
            .toSearchResultItems(shelf)
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

    private fun loadExpandedSourcePage(sourceUrl: String, page: Int) {
        viewModelScope.launch {
            val keyword = _uiState.value.committedQuery
            try {
                val result = exploreBooksUseCase.execute(
                    sourceUrl = sourceUrl,
                    moduleUrl = null,
                    args = null,
                    page = page,
                    key = keyword,
                )
                val newBooks = result.books
                _uiState.update {
                    it.copy(
                        expandedSourceBooks = (it.expandedSourceBooks + newBooks).toImmutableList(),
                        expandedSourceLoading = false,
                        expandedSourceEnd = newBooks.isEmpty(),
                        expandedSourcePage = page + 1,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        expandedSourceLoading = false,
                        expandedSourceError = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    private fun emitEffect(effect: SearchEffect) {
        _effects.tryEmit(effect)
    }

    private fun mergeSearchResults(books: List<SearchBook>) {
        books.forEach { book ->
            val key = SearchResultKey(book.name, book.author)
            val currentBook = searchResultBooks[key]
            if (currentBook == null) {
                searchResultBooks[key] = book
            } else {
                book.origins.forEach { origin -> currentBook.addOrigin(origin) }
            }
        }
    }

    private fun removeSearchResults(bookUrls: List<String>) {
        if (bookUrls.isEmpty()) return
        val removedUrls = bookUrls.toHashSet()
        searchResultBooks.entries.removeAll { (_, book) -> book.bookUrl in removedUrls }
    }

    private fun Collection<SearchBook>.sortedWithSearchPriority(
        keyword: String,
        matchMode: MatchMode,
    ): List<SearchBook> {
        val equalBooks = arrayListOf<SearchBook>()
        val tagsBooks = arrayListOf<SearchBook>()
        val containsBooks = arrayListOf<SearchBook>()
        val otherBooks = arrayListOf<SearchBook>()
        forEach { book ->
            when {
                book.name.equals(keyword, ignoreCase = true) ||
                    book.author.equals(keyword, ignoreCase = true) -> equalBooks.add(book)
                book.kind?.contains(keyword, ignoreCase = true) == true -> tagsBooks.add(book)
                book.name.contains(keyword, ignoreCase = true) ||
                    book.author.contains(keyword, ignoreCase = true) -> containsBooks.add(book)
                matchMode == MatchMode.DEFAULT -> otherBooks.add(book)
            }
        }
        return buildList(size) {
            addAll(equalBooks.sortedByDescending { it.origins.size })
            addAll(tagsBooks.sortedByDescending { it.origins.size })
            addAll(containsBooks.sortedByDescending { it.origins.size })
            addAll(otherBooks)
        }
    }

    private data class SearchResultKey(
        val name: String,
        val author: String,
    )

    private companion object {
        const val EXACT_SEARCH_SINGLE_PAGE_RESULT_THRESHOLD = 3
    }
}
