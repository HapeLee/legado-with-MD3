package io.legado.app.ui.book.searchContent

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.domain.gateway.ThemeSettingsGateway
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
    val isEInkMode: Boolean = false,
)

sealed interface SearchContentIntent {
    data class UpdateQuery(val value: String) : SearchContentIntent

    /** 用户在输入法上按下「搜索」：确认这次查询并把关键词写入搜索历史。 */
    data class SubmitSearch(val value: String) : SearchContentIntent
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
    private val themeSettingsGateway: ThemeSettingsGateway,
) : ViewModel() {
    private val restoredSession = if (initialSearchWord == null) {
        searchContentRepository.getLastSession(bookUrl)
    } else null

    /**
     * 从阅读页选中文字进入时，这次查询是用户主动发起的，允许记入历史；
     * 其余情况（恢复上次会话、输入过程中的自动搜索）只做预览，不污染历史。
     */
    private val initialSearchSubmitted = initialSearchWord != null

    private val _uiState = MutableStateFlow(
        SearchContentUiState(
            searchQuery = initialSearchWord ?: restoredSession?.query.orEmpty(),
            replaceEnabled = restoredSession?.replaceEnabled ?: false,
            regexReplace = restoredSession?.regexReplace ?: false,
            shouldAutoScroll = searchResultIndex > 0,
            isEInkMode = themeSettingsGateway.currentSettings.appTheme == "4",
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SearchContentEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var searchJob: Job? = null
    private var historyJob: Job? = null
    private var resultOpened = false

    /**
     * 每次发起/停止搜索自增。被取消的旧任务仍会执行 `onCompletion` / `catch`，
     * 没有代次校验时它会把新搜索刚置上的 `isSearching` 又改回 false（进度条提前消失）。
     */
    private var searchGeneration = 0

    init {
        initBook()
        viewModelScope.launch {
            themeSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(isEInkMode = settings.appTheme == "4") }
            }
        }
    }

    fun onIntent(intent: SearchContentIntent) {
        when (intent) {
            is SearchContentIntent.UpdateQuery -> {
                _uiState.update { it.copy(searchQuery = intent.value) }
                executeSearch()
            }
            is SearchContentIntent.SubmitSearch -> {
                _uiState.update { it.copy(searchQuery = intent.value) }
                executeSearch(recordHistory = true)
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
                book,
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
            if (cachedResults.isNullOrEmpty() && state.searchQuery.isNotBlank()) {
                executeSearch(recordHistory = initialSearchSubmitted)
            }
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

    /**
     * 执行一次全文搜索。
     *
     * [recordHistory] 只在用户确认搜索（输入法搜索键、从阅读页选中文字发起）时为 true：
     * 输入过程中的自动搜索只用于预览，早期实现会把拼音、半截词的中间态全部写进搜索历史。
     */
    private fun executeSearch(recordHistory: Boolean = false) {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.searchQuery.isBlank()) {
            searchGeneration += 1
            searchContentRepository.clearSession(bookUrl)
            SearchContentResult.clearResults(bookUrl)
            _uiState.update {
                it.copy(isSearching = false, searchResults = persistentListOf(), error = null)
            }
            return
        }
        searchContentRepository.beginSearch(
            state.book,
            state.searchQuery,
            state.replaceEnabled,
            state.regexReplace,
        )
        val book = state.book
        if (book == null) {
            // 书还没就位：initBook 拿到书之后会重新发起，别留下结束不了的搜索态。
            _uiState.update { it.copy(isSearching = false) }
            return
        }
        val generation = ++searchGeneration
        // 新查询/新参数下旧结果是过期数据：先清空，随后由仓库逐章 emit 的增量结果填回。
        _uiState.update {
            it.copy(isSearching = true, searchResults = persistentListOf(), error = null)
        }
        searchJob = viewModelScope.launch {
            if (recordHistory) {
                searchContentRepository.saveHistory(book, state.searchQuery)
            }
            searchContentRepository.search(
                book,
                state.searchQuery,
                state.replaceEnabled,
                state.regexReplace,
            )
                .onStart {
                    if (generation == searchGeneration) {
                        _uiState.update { it.copy(isSearching = true, error = null) }
                    }
                }
                .onCompletion {
                    if (generation == searchGeneration) {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
                .catch { error ->
                    if (generation == searchGeneration) {
                        _uiState.update { it.copy(isSearching = false, error = error) }
                    }
                }
                .collect { results ->
                    if (generation == searchGeneration) {
                        _uiState.update { it.copy(searchResults = results.toImmutableList()) }
                    }
                }
        }
    }

    private fun stopSearch() {
        searchGeneration += 1
        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = false) }
    }

    private fun leaveSearch() {
        searchGeneration += 1
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
