package io.legado.app.ui.book.read

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
enum class ReaderLayoutMode {
    PAGED,
    SCROLL,
    DOUBLE_PAGE,
}

@Immutable
data class ReaderPadding(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom
}

@Immutable
data class ReaderViewport(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val contentPadding: ReaderPadding = ReaderPadding(),
    val mode: ReaderLayoutMode = ReaderLayoutMode.PAGED,
) {
    val contentWidthPx: Int get() = (widthPx - contentPadding.horizontal).coerceAtLeast(0)
    val contentHeightPx: Int get() = (heightPx - contentPadding.vertical).coerceAtLeast(0)
    val isReady: Boolean get() = contentWidthPx > 0 && contentHeightPx > 0 && density > 0f
}

@Immutable
data class ReaderLayoutResult(
    val revision: Long,
    val pageIndex: Int,
    val viewport: ReaderViewport,
)

interface ReaderLayoutController {
    val viewport: StateFlow<ReaderViewport?>
    val layoutResults: Flow<ReaderLayoutResult>

    fun updateViewport(viewport: ReaderViewport)

    fun requestRelayout()

    suspend fun awaitViewport(timeoutMillis: Long = VIEWPORT_WAIT_TIMEOUT_MILLIS): ReaderViewport?

    companion object {
        const val VIEWPORT_WAIT_TIMEOUT_MILLIS = 2_000L
    }
}

/**
 * Track C · C4 的尺寸输入接缝。分页仍由 ChapterProvider 完成；本类只负责把
 * View/Compose 的测量结果统一成正文尺寸，并暴露排版完成 revision。
 */
internal class ReaderLayoutCoordinator(
    private val updateLayoutSize: (width: Int, height: Int) -> Unit,
    private val relayoutContent: () -> Unit,
) : ReaderLayoutController {

    private val _viewport = MutableStateFlow<ReaderViewport?>(null)
    override val viewport: StateFlow<ReaderViewport?> = _viewport.asStateFlow()

    private val _layoutResults = MutableSharedFlow<ReaderLayoutResult>(extraBufferCapacity = 16)
    override val layoutResults: Flow<ReaderLayoutResult> = _layoutResults.asSharedFlow()

    private var appliedContentWidth = 0
    private var appliedContentHeight = 0
    private var layoutRevision = 0L

    override fun updateViewport(viewport: ReaderViewport) {
        if (!viewport.isReady) return
        _viewport.value = viewport
        if (
            viewport.contentWidthPx == appliedContentWidth &&
            viewport.contentHeightPx == appliedContentHeight
        ) return
        appliedContentWidth = viewport.contentWidthPx
        appliedContentHeight = viewport.contentHeightPx
        updateLayoutSize(appliedContentWidth, appliedContentHeight)
    }

    override fun requestRelayout() {
        relayoutContent()
    }

    override suspend fun awaitViewport(timeoutMillis: Long): ReaderViewport? {
        return withTimeoutOrNull(timeoutMillis) {
            viewport.filter { it?.isReady == true }.first()
        }
    }

    fun publishPageLayout(pageIndex: Int) {
        val currentViewport = _viewport.value ?: return
        _layoutResults.tryEmit(
            ReaderLayoutResult(
                revision = ++layoutRevision,
                pageIndex = pageIndex,
                viewport = currentViewport,
            )
        )
    }
}
