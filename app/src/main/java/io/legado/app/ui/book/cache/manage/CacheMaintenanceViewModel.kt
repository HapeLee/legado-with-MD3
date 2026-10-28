package io.legado.app.ui.book.cache.manage

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

enum class CacheMaintenanceTab { OrphanChapters, WordCount }

/** 当前正在执行的操作；null 表示空闲，此时列表可操作。 */
enum class CacheMaintenanceBusyKind { Scan, Delete, Recount }

@Stable
data class CacheMaintenanceUiState(
    val tab: CacheMaintenanceTab = CacheMaintenanceTab.OrphanChapters,
    /**
     * 当前正在执行的操作。为 null 时列表处于可操作态。
     *
     * 用枚举而不是一个布尔：扫描、删除、重算的进度口径与文案都不同，
     * 只有一个 isBusy 时执行期只能显示"正在扫描"。
     */
    val busyKind: CacheMaintenanceBusyKind? = null,
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
    val isBusy: Boolean get() = busyKind != null

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
            is CacheMaintenanceIntent.SelectTab -> {
                // 在跑的任务属于旧 Tab：不取消的话它会把旧结果写进新 Tab，
                // 还会让新 Tab 上的"立即扫描"被 isBusy 判断静默吞掉。
                runningJob?.cancel()
                _uiState.update {
                    it.copy(tab = intent.tab, hasScanned = false)
                }
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
            _uiState.update {
                it.copy(
                    busyKind = CacheMaintenanceBusyKind.Scan,
                    hasScanned = false,
                    scannedBooks = 0,
                    totalBooks = 0,
                )
            }
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
            } catch (error: CancellationException) {
                // 用户主动取消不是失败，不能弹一条 toast。
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                finishJob(coroutineContext.job)
            }
        }
    }

    private fun deleteSelected() {
        if (_uiState.value.isBusy) return
        val groups = _uiState.value.selectedOrphanBooks
        if (groups.isEmpty()) return
        _uiState.update { it.copy(showDeleteConfirm = false, lastDeletion = null) }
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(busyKind = CacheMaintenanceBusyKind.Delete) }
            try {
                var deletedEntries = 0
                var freedBytes = 0L
                var skippedBooks = 0
                val removedUrls = groups.mapTo(mutableSetOf()) { it.bookUrl }
                groups.forEach { group ->
                    val deleted = maintenanceGateway.deleteOrphanedChapters(
                        bookUrl = group.bookUrl,
                        entryNames = group.entryNames.toSet(),
                    )
                    deletedEntries += deleted.deletedEntries
                    freedBytes += deleted.freedBytes
                    if (deleted.skippedBooks > 0) {
                        skippedBooks += deleted.skippedBooks
                        // 没删成的书留在列表里，否则会显示成"已删除"。
                        removedUrls -= group.bookUrl
                    }
                }
                _uiState.update {
                    it.copy(
                        lastDeletion = DeletedChapterCache(deletedEntries, freedBytes, skippedBooks),
                        orphanGroups = it.orphanGroups.filter { group -> group.bookUrl !in removedUrls },
                        selectedOrphanBookUrls = emptySet(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                finishJob(coroutineContext.job)
            }
        }
    }

    private fun recountSelected() {
        if (_uiState.value.isBusy) return
        val bookUrls = _uiState.value.selectedRecountBookUrls
        if (bookUrls.isEmpty()) return
        _uiState.update { it.copy(lastRecount = null) }
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyKind = CacheMaintenanceBusyKind.Recount,
                    scannedBooks = 0,
                    totalBooks = 0,
                )
            }
            try {
                val progress: (Int, Int) -> Unit = { current, total ->
                    _uiState.update { it.copy(scannedBooks = current, totalBooks = total) }
                }
                val outcome = maintenanceGateway.recountWordCounts(bookUrls, progress)
                _uiState.update {
                    it.copy(
                        lastRecount = outcome,
                        recountTargets = it.recountTargets.filterNot { target -> target.bookUrl in bookUrls },
                        selectedRecountBookUrls = emptySet(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                finishJob(coroutineContext.job)
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

    /**
     * 只有自己仍是当前任务时才收尾。
     *
     * 切 Tab 会先取消旧任务再启动新任务，旧任务的 finally 可能晚于新任务启动执行，
     * 无条件清 busy 会把正在跑的新任务标记成空闲。
     */
    private fun finishJob(job: Job) {
        if (runningJob === job) {
            _uiState.update { it.copy(busyKind = null) }
        }
    }

    private fun reportFailure(error: Throwable) {
        _effects.tryEmit(
            CacheMaintenanceEffect.ShowMessage(error.localizedMessage ?: error.javaClass.simpleName)
        )
    }
}
