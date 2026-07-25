package io.legado.app.model

import io.legado.app.help.book.isLocal
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.service.FullBookPageService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

data class FullBookPaginationState(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
)

/** Optional precision producer for local books; the coordinator remains the only page-count SSOT. */
object FullBookPaginator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var activeBookUrl: String? = null
    private val _state = MutableStateFlow(FullBookPaginationState())
    val state = _state.asStateFlow()

    fun startIfNeeded() {
        val bookUrl = ReadBook.book?.takeIf { it.isLocal }?.bookUrl
        if (bookUrl == null || !isFullPagePlaceholderActive()) {
            stop()
            return
        }
        if (job?.isActive == true && activeBookUrl == bookUrl) return
        stop()
        activeBookUrl = bookUrl
        job = scope.launch {
            _state.value = FullBookPaginationState(isRunning = true)
            runCatching { FullBookPageService.start(appCtx) }
            try {
                ReadBook.paginateLocalBookPages { completed, total ->
                    _state.update { it.copy(completed = completed, total = total) }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activeBookUrl = null
        _state.value = FullBookPaginationState()
    }

    private fun isFullPagePlaceholderActive(): Boolean {
        val directTips = listOf(
            ReadBookConfig.tipHeaderLeft,
            ReadBookConfig.tipHeaderMiddle,
            ReadBookConfig.tipHeaderRight,
            ReadBookConfig.tipFooterLeft,
            ReadBookConfig.tipFooterMiddle,
            ReadBookConfig.tipFooterRight,
        )
        if (directTips.any {
                it == ReadBookConfig.tipWholeBookPage ||
                    it == ReadBookConfig.tipWholeBookPageAndProgress
            }
        ) return true
        return listOf(
        ReadBookConfig.customTipHeaderLeft,
        ReadBookConfig.customTipHeaderMiddle,
        ReadBookConfig.customTipHeaderRight,
        ReadBookConfig.customTipFooterLeft,
        ReadBookConfig.customTipFooterMiddle,
        ReadBookConfig.customTipFooterRight,
    ).any { template ->
        CustomTipPlaceholder.extractPlaceholders(template).any {
            it == CustomTipPlaceholder.FULL_PAGE_INDEX.key ||
                it == CustomTipPlaceholder.FULL_PAGE_SIZE.key
        }
    }
    }
}
