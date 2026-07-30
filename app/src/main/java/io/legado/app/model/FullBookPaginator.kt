package io.legado.app.model

import io.legado.app.constant.ReadTipType
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.help.config.ReadBookConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Optional precision producer for local books; the coordinator remains the only page-count SSOT. */
object FullBookPaginator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var activeBookUrl: String? = null
    private var activeLayoutGeneration: Long = -1L

    fun startIfNeeded(layoutGeneration: Long) {
        val bookUrl = ReadBook.book?.takeIf { it.isLocal }?.bookUrl
        if (bookUrl == null || !isNeeded()) {
            stop()
            return
        }
        if (job?.isActive == true &&
            activeBookUrl == bookUrl &&
            activeLayoutGeneration == layoutGeneration
        ) return
        stop()
        activeBookUrl = bookUrl
        activeLayoutGeneration = layoutGeneration
        job = scope.launch {
            ReadBook.paginateLocalBookPages(layoutGeneration)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activeBookUrl = null
        activeLayoutGeneration = -1L
    }

    fun isNeeded(): Boolean = hasActiveFullBookPageTip(
        selections = listOf(
            FullBookPageTipSelection(
                ReadBookConfig.tipHeaderLeft,
                ReadBookConfig.customTipHeaderLeft,
            ),
            FullBookPageTipSelection(
                ReadBookConfig.tipHeaderMiddle,
                ReadBookConfig.customTipHeaderMiddle,
            ),
            FullBookPageTipSelection(
                ReadBookConfig.tipHeaderRight,
                ReadBookConfig.customTipHeaderRight,
            ),
            FullBookPageTipSelection(
                ReadBookConfig.tipFooterLeft,
                ReadBookConfig.customTipFooterLeft,
            ),
            FullBookPageTipSelection(
                ReadBookConfig.tipFooterMiddle,
                ReadBookConfig.customTipFooterMiddle,
            ),
            FullBookPageTipSelection(
                ReadBookConfig.tipFooterRight,
                ReadBookConfig.customTipFooterRight,
            ),
        ),
        customTipValue = ReadTipType.tipCustom,
        wholeBookPageTipValue = ReadTipType.tipWholeBookPage,
        wholeBookPageAndProgressTipValue = ReadTipType.tipWholeBookPageAndProgress,
    )
}

internal data class FullBookPageTipSelection(
    val value: Int,
    val customTemplate: String,
)

internal fun hasActiveFullBookPageTip(
    selections: List<FullBookPageTipSelection>,
    customTipValue: Int,
    wholeBookPageTipValue: Int,
    wholeBookPageAndProgressTipValue: Int,
): Boolean = selections.any { selection ->
    when (selection.value) {
        wholeBookPageTipValue, wholeBookPageAndProgressTipValue -> true
        customTipValue -> CustomTipPlaceholder.extractPlaceholders(selection.customTemplate).any {
            it == CustomTipPlaceholder.FULL_PAGE_INDEX.key ||
                it == CustomTipPlaceholder.FULL_PAGE_SIZE.key
        }
        else -> false
    }
}
