package io.legado.app.ui.book.cache.manage

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.OrphanDeleteRequest
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
    /** 执行期间请求返回时的确认闸门；破坏性任务不能因一次误触返回被静默腰斩。 */
    val showLeaveConfirm: Boolean = false,
    /** 上一次任务被取消：删除/重算是逐本提交的，部分结果已落盘，提示用户重新扫描。 */
    val wasCancelled: Boolean = false,
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
    data object RequestLeave : CacheMaintenanceIntent
    data object DismissLeaveConfirm : CacheMaintenanceIntent
    data object ConfirmLeave : CacheMaintenanceIntent
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
                // 旧任务的 finally 是异步的，只 cancel 不清 busyKind 的话，
                // 新 Tab 上头一次点「立即扫描」会被 scan() 的 isBusy 守卫静默吞掉；
                // 置空 runningJob 还能让旧任务的 finishJob 不再把结果写回新 Tab。
                runningJob?.cancel()
                runningJob = null
                resetResultFor(intent.tab)
                _uiState.update {
                    it.copy(
                        tab = intent.tab,
                        busyKind = null,
                        hasScanned = false,
                        showDeleteConfirm = false,
                        showLeaveConfirm = false,
                        wasCancelled = false,
                        lastDeletion = null,
                        lastRecount = null,
                    )
                }
            }

            CacheMaintenanceIntent.Scan -> scan()
            CacheMaintenanceIntent.Cancel -> cancelRunning()
            CacheMaintenanceIntent.RequestLeave -> _uiState.update {
                it.copy(showLeaveConfirm = true)
            }

            CacheMaintenanceIntent.DismissLeaveConfirm -> _uiState.update {
                it.copy(showLeaveConfirm = false)
            }

            CacheMaintenanceIntent.ConfirmLeave -> cancelRunning()
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
                    state.orphanGroups.map { it.bookUrl }.toSet()
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
                    state.recountTargets.map { it.bookUrl }.toSet()
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
            var groups: List<OrphanedChapterGroup>? = null
            var targets: List<WordCountRecountTarget>? = null
            try {
                val progress = throttledProgress()
                when (tab) {
                    CacheMaintenanceTab.OrphanChapters ->
                        groups = maintenanceGateway.scanOrphanedChapters(progress)

                    CacheMaintenanceTab.WordCount ->
                        targets = maintenanceGateway.scanRecountTargets(progress)
                }
            } catch (error: CancellationException) {
                // 用户主动取消不是失败，不能弹一条 toast。
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                // 结果只在自己仍是当前任务时写回。Gateway 可能恰好在被取消前返回，
                // 此时若直接写 UiState，旧 Tab 的结果会污染刚切过去的新 Tab。
                val scannedGroups = groups
                val scannedTargets = targets
                finishJob(coroutineContext.job) { state ->
                    when {
                        scannedGroups != null -> state.copy(
                            orphanGroups = scannedGroups,
                            selectedOrphanBookUrls = emptySet(),
                            hasScanned = true,
                        )

                        scannedTargets != null -> state.copy(
                            recountTargets = scannedTargets,
                            selectedRecountBookUrls = emptySet(),
                            hasScanned = true,
                        )

                        // 取消或失败：没有结果就不该标成「扫描完成」。
                        else -> state
                    }
                }
            }
        }
    }

    private fun deleteSelected() {
        if (_uiState.value.isBusy) return
        val groups = _uiState.value.selectedOrphanBooks
        if (groups.isEmpty()) return
        val requests = groups.map {
            OrphanDeleteRequest(it.bookUrl, it.entryNames.toSet())
        }
        _uiState.update {
            it.copy(showDeleteConfirm = false, lastDeletion = null, wasCancelled = false)
        }
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyKind = CacheMaintenanceBusyKind.Delete,
                    scannedBooks = 0,
                    totalBooks = requests.size,
                )
            }
            var outcome = DeletedChapterCache(0, 0L)
            val removedUrls = mutableSetOf<String>()
            val progress = throttledProgress()
            try {
                // 批量接口：章节表只查一次，逐本回报累计结果供取消时写回。
                outcome = maintenanceGateway.deleteOrphanedChapters(requests) { report ->
                    outcome = report.accumulated
                    report.removedBookUrl?.let { removedUrls += it }
                    progress(report.handled, report.total)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                // 取消时也要写回已累计的部分结果：删除是逐本落盘的，不能假装什么都没发生。
                val result = outcome
                val done = removedUrls.toSet()
                finishJob(coroutineContext.job) { state ->
                    state.copy(
                        lastDeletion = result,
                        orphanGroups = state.orphanGroups.filter { it.bookUrl !in done },
                        selectedOrphanBookUrls = emptySet(),
                    )
                }
            }
        }
    }

    private fun recountSelected() {
        if (_uiState.value.isBusy) return
        val bookUrls = _uiState.value.selectedRecountBookUrls
        if (bookUrls.isEmpty()) return
        _uiState.update { it.copy(lastRecount = null, wasCancelled = false) }
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyKind = CacheMaintenanceBusyKind.Recount,
                    scannedBooks = 0,
                    totalBooks = bookUrls.size,
                )
            }
            var outcome: RecountOutcome? = null
            try {
                val progress = throttledProgress()
                outcome = maintenanceGateway.recountWordCounts(bookUrls, progress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportFailure(error)
            } finally {
                // 取消时拿不到汇总结果，但已写入数据库的章节字数是真的；
                // 由 wasCancelled 提示用户重新扫描，而不是伪造一份「已更新 0 章」。
                val result = outcome
                finishJob(coroutineContext.job) { state ->
                    if (result == null) {
                        state
                    } else {
                        val handled = result.handledBookUrls
                        state.copy(
                            lastRecount = result,
                            // 只移除真正处理过的书；被跳过的书留在列表且保持勾选，
                            // 等下载/阅读结束后可以直接再执行一次，不会静默「消失」。
                            recountTargets = state.recountTargets.filterNot {
                                it.bookUrl in handled
                            },
                            selectedRecountBookUrls = state.selectedRecountBookUrls - handled,
                        )
                    }
                }
            }
        }
    }

    /** 主动取消或确认离开：语义相同，都要标记「已取消」并关掉确认弹窗。 */
    private fun cancelRunning() {
        runningJob?.cancel()
        _uiState.update { it.copy(wasCancelled = true, showLeaveConfirm = false) }
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
    private fun finishJob(
        job: Job,
        result: (CacheMaintenanceUiState) -> CacheMaintenanceUiState = { it },
    ) {
        // 切 Tab 会把 runningJob 置空，旧任务不能再把结果与 busyKind 写回新 Tab。
        if (runningJob !== job) return
        _uiState.update { result(it).copy(busyKind = null) }
    }

    private fun reportFailure(error: Throwable) {
        _effects.tryEmit(
            CacheMaintenanceEffect.ShowMessage(error.localizedMessage ?: error.javaClass.simpleName)
        )
    }

    /**
     * 进度节流：几千本书逐本上报会生成几千个 UiState 并触发同等数量的重组。
     * 首尾必须上报（首帧给出总数、末帧给出 100%），中间按时间窗口合并。
     */
    private fun throttledProgress(): (Int, Int) -> Unit {
        var lastEmit = 0L
        return { current, total ->
            // 单调时钟：currentTimeMillis 会被系统时间调整/NTP 回拨带偏，
            // 回拨会让节流窗口长时间不上报。nanoTime 纯 JVM 可用，测试无需 Robolectric。
            val now = System.nanoTime()
            if (current >= total || now - lastEmit >= PROGRESS_INTERVAL_NS) {
                lastEmit = now
                _uiState.update { it.copy(scannedBooks = current, totalBooks = total) }
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_NS = 100_000_000L
    }
}
