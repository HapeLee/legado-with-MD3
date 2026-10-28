package io.legado.app.ui.book.cache.manage

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CacheMaintenanceTab { OrphanChapters, WordCount }

@Stable
data class CacheMaintenanceUiState(
    val tab: CacheMaintenanceTab = CacheMaintenanceTab.OrphanChapters,
    /** 扫描或执行中。为 true 时列表处于不可操作的只读态。 */
    val isBusy: Boolean = false,
    val scannedBooks: Int = 0,
    val totalBooks: Int = 0,
    /** 是否至少扫描过一次；用于区分"还没扫"和"扫出来是空的"。 */
    val hasScanned: Boolean = false,
    val orphanGroups: List<OrphanedChapterGroup> = emptyList(),
    val selectedOrphanBookUrls: Set<String> = emptySet(),
    val recountTargets: List<WordCountRecountTarget> = emptyList(),
    val selectedRecountBookUrls: Set<String> = emptySet(),
    /** 删除/重算的结果留在 state 里，Effect 在无人收集时会丢，不能承载这类完成信息。 */
    val lastDeletion: DeletedChapterCache? = null,
    val lastRecount: RecountOutcome? = null,
    val showDeleteConfirm: Boolean = false,
) {
    val selectedOrphanBooks: List<OrphanedChapterGroup>
        get() = orphanGroups.filter { it.bookUrl in selectedOrphanBookUrls }
}

sealed interface CacheMaintenanceIntent {
    data class SelectTab(val tab: CacheMaintenanceTab) : CacheMaintenanceIntent
    data object Scan : CacheMaintenanceIntent
    data object Cancel : CacheMaintenanceIntent
    data class ToggleOrphanBook(val bookUrl: String) : CacheMaintenanceIntent
    data object ToggleAllOrphanBooks : CacheMaintenanceIntent
    data class ToggleRecountBook(val bookUrl: String) : CacheMaintenanceIntent
    data object ToggleAllRecountBooks : CacheMaintenanceIntent
    data object RequestDelete : CacheMaintenanceIntent
    data object DismissDeleteConfirm : CacheMaintenanceIntent
    data object ConfirmDelete : CacheMaintenanceIntent
    data object RecountSelected : CacheMaintenanceIntent
}

sealed interface CacheMaintenanceEffect {
    data class ShowMessage(val message: String) : CacheMaintenanceEffect
}

class CacheMaintenanceViewModel(
    private val maintenanceGateway: BookCacheMaintenanceGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheMaintenanceUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CacheMaintenanceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var runningJob: Job? = null

    fun onIntent(intent: CacheMaintenanceIntent) {
        when (intent) {
            is CacheMaintenanceIntent.SelectTab -> _uiState.update {
                it.copy(tab = intent.tab, hasScanned = false)
            }

            CacheMaintenanceIntent.Scan -> scan()
            CacheMaintenanceIntent.Cancel -> runningJob?.cancel()
            is CacheMaintenanceIntent.ToggleOrphanBook -> _uiState.update { state ->
                val selected = if (intent.bookUrl in state.selectedOrphanBookUrls) {
                    state.selectedOrphanBookUrls - intent.bookUrl
                } else {
                    state.selectedOrphanBookUrls + intent.bookUrl
                }
                state.copy(selectedOrphanBookUrls = selected)
            }

            CacheMaintenanceIntent.ToggleAllOrphanBooks -> _uiState.update { state ->
                val selected = if (state.selectedOrphanBookUrls.size == state.orphanGroups.size) {
                    emptySet()
                } else {
                    state.orphanGroups.mapTo(mutableSetOf()) { it.bookUrl }
                }
                state.copy(selectedOrphanBookUrls = selected)
            }

            is CacheMaintenanceIntent.ToggleRecountBook -> _uiState.update { state ->
                val selected = if (intent.bookUrl in state.selectedRecountBookUrls) {
                    state.selectedRecountBookUrls - intent.bookUrl
                } else {
                    state.selectedRecountBookUrls + intent.bookUrl
                }
                state.copy(selectedRecountBookUrls = selected)
            }

            CacheMaintenanceIntent.ToggleAllRecountBooks -> _uiState.update { state ->
                val selected = if (state.selectedRecountBookUrls.size == state.recountTargets.size) {
                    emptySet()
                } else {
                    state.recountTargets.mapTo(mutableSetOf()) { it.bookUrl }
                }
                state.copy(selectedRecountBookUrls = selected)
            }

            CacheMaintenanceIntent.RequestDelete -> _uiState.update {
                it.copy(showDeleteConfirm = true)
            }

            CacheMaintenanceIntent.DismissDeleteConfirm -> _uiState.update {
                it.copy(showDeleteConfirm = false)
            }

            CacheMaintenanceIntent.ConfirmDelete -> deleteSelected()
            CacheMaintenanceIntent.RecountSelected -> recountSelected()
        }
    }

    private fun scan() {
        if (_uiState.value.isBusy) return
        val tab = _uiState.value.tab
        resetResultFor(tab)
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, hasScanned = false, scannedBooks = 0, totalBooks = 0) }
            try {
                val progress: (Int, Int) -> Unit = { current, total ->
                    _uiState.update { it.copy(scannedBooks = current, totalBooks = total) }
                }
                when (tab) {
                    CacheMaintenanceTab.OrphanChapters -> {
                        val groups = maintenanceGateway.scanOrphanedChapters(progress)
                        _uiState.update {
                            it.copy(
                                orphanGroups = groups,
                                selectedOrphanBookUrls = emptySet(),
                            )
                        }
                    }

                    CacheMaintenanceTab.WordCount -> {
                        val targets = maintenanceGateway.scanRecountTargets(progress)
                        _uiState.update {
                            it.copy(
                                recountTargets = targets,
                                selectedRecountBookUrls = emptySet(),
                            )
                        }
                    }
                }
                _uiState.update { it.copy(hasScanned = true) }
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun deleteSelected() {
        val groups = _uiState.value.selectedOrphanBooks
        if (groups.isEmpty()) return
        _uiState.update { it.copy(showDeleteConfirm = false, lastDeletion = null) }
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                var deletedEntries = 0
                var freedBytes = 0L
                groups.forEach { group ->
                    val deleted = maintenanceGateway.deleteOrphanedChapters(
                        bookUrl = group.bookUrl,
                        entryNames = group.entryNames.toSet(),
                    )
                    deletedEntries += deleted.deletedEntries
                    freedBytes += deleted.freedBytes
                }
                _uiState.update {
                    it.copy(
                        lastDeletion = DeletedChapterCache(deletedEntries, freedBytes),
                        orphanGroups = it.orphanGroups.filterNot { group -> group.bookUrl in it.selectedOrphanBookUrls },
                        selectedOrphanBookUrls = emptySet(),
                    )
                }
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun recountSelected() {
        val bookUrls = _uiState.value.selectedRecountBookUrls
        if (bookUrls.isEmpty()) return
        _uiState.update { it.copy(lastRecount = null) }
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val outcome = maintenanceGateway.recountWordCounts(bookUrls)
                _uiState.update {
                    it.copy(
                        lastRecount = outcome,
                        recountTargets = it.recountTargets.filterNot { target -> target.bookUrl in bookUrls },
                        selectedRecountBookUrls = emptySet(),
                    )
                }
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun resetResultFor(tab: CacheMaintenanceTab) {
        _uiState.update {
            when (tab) {
                CacheMaintenanceTab.OrphanChapters -> it.copy(
                    lastDeletion = null,
                    orphanGroups = emptyList(),
                    selectedOrphanBookUrls = emptySet(),
                )

                CacheMaintenanceTab.WordCount -> it.copy(
                    lastRecount = null,
                    recountTargets = emptyList(),
                    selectedRecountBookUrls = emptySet(),
                )
            }
        }
    }

    private fun reportFailure(error: Throwable) {
        _effects.tryEmit(
            CacheMaintenanceEffect.ShowMessage(error.localizedMessage ?: error.javaClass.simpleName)
        )
    }
}
