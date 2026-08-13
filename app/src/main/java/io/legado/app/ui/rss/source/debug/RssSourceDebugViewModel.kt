package io.legado.app.ui.rss.source.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssSourceEditRepository
import io.legado.app.help.source.sortUrls
import io.legado.app.model.Debug
import io.legado.app.ui.book.source.debug.BookSourceDebugEntryUi
import io.legado.app.ui.book.source.debug.BookSourceDebugStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class RssSourceDebugViewModel(
    private val repository: RssSourceEditRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RssSourceDebugUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RssSourceDebugEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var source: RssSource? = null
    private var session: Debug.Session? = null
    private val ids = AtomicLong()

    fun onIntent(intent: RssSourceDebugIntent) = when (intent) {
        is RssSourceDebugIntent.Load -> { load(intent.sourceUrl); Unit }
        is RssSourceDebugIntent.SetQuery -> _uiState.update { it.copy(query = intent.value) }
        is RssSourceDebugIntent.SelectTarget -> _uiState.update { it.copy(target = intent.value) }
        is RssSourceDebugIntent.SelectFilter -> _uiState.update { it.copy(filter = intent.value) }
        is RssSourceDebugIntent.UseExample -> _uiState.update { it.copy(target = intent.value.target, query = intent.value.value) }
        is RssSourceDebugIntent.ShowEntry -> _uiState.update { it.copy(selectedEntryId = intent.id) }
        RssSourceDebugIntent.DismissEntry -> _uiState.update { it.copy(selectedEntryId = null) }
        RssSourceDebugIntent.Start -> start()
        RssSourceDebugIntent.Stop -> stop()
        RssSourceDebugIntent.Clear -> _uiState.update { it.copy(entries = persistentListOf()) }
    }

    private fun load(url: String?) = viewModelScope.launch {
        val loaded = url?.let { repository.findByUrl(it) }
        source = loaded
        if (loaded == null) {
            _uiState.update { it.copy(status = BookSourceDebugStatus.Failed) }
            _effects.tryEmit(RssSourceDebugEffect.ShowMessage("未获取到订阅源"))
            return@launch
        }
        val examples = buildList {
            add(RssSourceDebugExampleUi("我的", RssSourceDebugTarget.Search, "我的"))
            add(RssSourceDebugExampleUi("系统", RssSourceDebugTarget.Search, "系统"))
            runCatching { loaded.sortUrls() }.getOrNull()?.filter { it.second.isNotBlank() }?.forEach {
                add(RssSourceDebugExampleUi(it.first, RssSourceDebugTarget.Sort, it.second))
            }
        }.distinctBy { it.target to it.value }.toImmutableList()
        _uiState.update { it.copy(sourceName = loaded.sourceName, query = "我的", examples = examples, status = BookSourceDebugStatus.Idle) }
    }

    private fun start() {
        val rss = source ?: run {
            _effects.tryEmit(RssSourceDebugEffect.ShowMessage("未获取到订阅源"))
            return
        }
        val state = _uiState.value
        if (state.query.isBlank()) { _effects.tryEmit(RssSourceDebugEffect.ShowMessage("调试内容不能为空")); return }
        session?.cancel()
        _uiState.update { it.copy(entries = persistentListOf(), status = BookSourceDebugStatus.Running) }
        val key = if (state.target == RssSourceDebugTarget.Sort) "分类::${state.query}" else state.query
        session = Debug.startDebug(viewModelScope, rss, key).also(::collectSession)
    }
    private fun stop() {
        session?.cancel()
        session = null
        _uiState.update { state ->
            if (state.status == BookSourceDebugStatus.Running) {
                state.copy(status = BookSourceDebugStatus.Cancelled)
            } else state
        }
    }
    private fun collectSession(debugSession: Debug.Session) {
        viewModelScope.launch {
            debugSession.events.collect(::onDebugEvent)
            if (session === debugSession) {
                session = null
                _uiState.update { state -> if (state.status == BookSourceDebugStatus.Running) state.copy(status = BookSourceDebugStatus.Cancelled) else state }
            }
        }
    }
    private fun onDebugEvent(event: Debug.Event) {
        _uiState.update { state -> state.copy(
            entries = (state.entries + BookSourceDebugEntryUi(ids.incrementAndGet(), event.kind, event.message, event.timestamp, event.elapsedMillis)).takeLast(1000).toImmutableList(),
            status = when (event.kind) { Debug.EventKind.Error -> BookSourceDebugStatus.Failed; Debug.EventKind.Completed -> BookSourceDebugStatus.Success; else -> state.status }
        ) }
    }
    override fun onCleared() { session?.cancel(); session = null; super.onCleared() }
}
