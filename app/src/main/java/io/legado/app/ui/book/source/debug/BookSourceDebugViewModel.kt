package io.legado.app.ui.book.source.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.Debug
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class BookSourceDebugViewModel(
    private val repository: BookSourceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookSourceDebugUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<BookSourceDebugEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private val nextId = AtomicLong()
    private var source: BookSource? = null
    private var session: Debug.Session? = null

    fun onIntent(intent: BookSourceDebugIntent) {
        when (intent) {
            is BookSourceDebugIntent.Load -> load(intent.sourceUrl)
            is BookSourceDebugIntent.SetQuery -> _uiState.update { it.copy(query = intent.value) }
            is BookSourceDebugIntent.SelectTarget -> _uiState.update { it.copy(target = intent.target) }
            is BookSourceDebugIntent.SelectFilter -> _uiState.update { it.copy(filter = intent.filter) }
            is BookSourceDebugIntent.UseExample -> _uiState.update {
                it.copy(target = intent.example.target, query = intent.example.value)
            }
            is BookSourceDebugIntent.ShowEntry -> _uiState.update { it.copy(selectedEntryId = intent.id) }
            BookSourceDebugIntent.DismissEntry -> _uiState.update { it.copy(selectedEntryId = null) }
            BookSourceDebugIntent.Start -> start()
            BookSourceDebugIntent.Stop -> stop()
            BookSourceDebugIntent.Clear -> _uiState.update { it.copy(entries = persistentListOf()) }
        }
    }

    private fun load(sourceUrl: String?) = viewModelScope.launch {
        val loaded: BookSource? = withContext(Dispatchers.IO) {
            sourceUrl?.let { repository.getBookSource(it) }
        }
        source = loaded
        if (loaded == null) {
            _uiState.update { it.copy(status = BookSourceDebugStatus.Failed) }
            _effects.tryEmit(BookSourceDebugEffect.ShowMessage("未获取到书源"))
            return@launch
        }
        val examples = buildList<BookSourceDebugExampleUi> {
            val configuredKeyword = loaded.ruleSearch?.checkKeyWord
            val keyword = if (configuredKeyword.isNullOrBlank()) "我的" else configuredKeyword
            add(BookSourceDebugExampleUi(keyword, BookSourceDebugTarget.Search, keyword))
            add(BookSourceDebugExampleUi("系统", BookSourceDebugTarget.Search, "系统"))
            val kinds = try { loaded.exploreKinds() } catch (_: Exception) { emptyList() }
            kinds.filter { !it.url.isNullOrBlank() }.forEach { kind ->
                add(BookSourceDebugExampleUi(kind.title, BookSourceDebugTarget.Explore, kind.url.orEmpty()))
            }
        }.distinctBy { it.target to it.value }.toImmutableList()
        _uiState.update {
            it.copy(
                sourceName = loaded.bookSourceName,
                query = examples.firstOrNull()?.value.orEmpty(),
                status = BookSourceDebugStatus.Idle,
                examples = examples,
            )
        }
    }

    private fun start() {
        val currentSource = source
        if (currentSource == null) {
            _effects.tryEmit(BookSourceDebugEffect.ShowMessage("未获取到书源"))
            return
        }
        val state = _uiState.value
        if (state.query.isBlank()) {
            _effects.tryEmit(BookSourceDebugEffect.ShowMessage("调试内容不能为空")); return
        }
        session?.cancel()
        _uiState.update { it.copy(entries = persistentListOf(), status = BookSourceDebugStatus.Running) }
        val key = when (state.target) {
            BookSourceDebugTarget.Search -> state.query
            BookSourceDebugTarget.Explore -> "发现::${state.query}"
            BookSourceDebugTarget.Info -> state.query
            BookSourceDebugTarget.Toc -> "++${state.query.removePrefix("++")}"
            BookSourceDebugTarget.Content -> "--${state.query.removePrefix("--")}"
        }
        session = Debug.startDebug(viewModelScope, currentSource, key).also(::collectSession)
    }

    private fun stop() {
        session?.cancel()
        session = null
        _uiState.update { state ->
            if (state.status == BookSourceDebugStatus.Running) state.copy(status = BookSourceDebugStatus.Cancelled)
            else state
        }
    }

    private fun collectSession(debugSession: Debug.Session) {
        viewModelScope.launch {
            debugSession.events.collect(::onDebugEvent)
            if (session === debugSession) {
                session = null
                _uiState.update { state ->
                    if (state.status == BookSourceDebugStatus.Running) {
                        state.copy(status = BookSourceDebugStatus.Cancelled)
                    } else state
                }
            }
        }
    }

    private fun onDebugEvent(event: Debug.Event) {
        _uiState.update { state ->
            val entry = BookSourceDebugEntryUi(
                id = nextId.incrementAndGet(),
                kind = event.kind,
                message = event.message,
                timestamp = event.timestamp,
                elapsedMillis = event.elapsedMillis,
            )
            state.copy(
                entries = (state.entries + entry).takeLast(MAX_LOG_ENTRIES).toImmutableList(),
                status = when (event.kind) {
                    Debug.EventKind.Error -> BookSourceDebugStatus.Failed
                    Debug.EventKind.Completed -> BookSourceDebugStatus.Success
                    else -> state.status
                },
            )
        }
    }

    override fun onCleared() {
        session?.cancel()
        session = null
        super.onCleared()
    }

    companion object { private const val MAX_LOG_ENTRIES = 1_000 }
}
