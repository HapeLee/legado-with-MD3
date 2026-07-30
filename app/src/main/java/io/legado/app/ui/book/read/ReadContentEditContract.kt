package io.legado.app.ui.book.read

import androidx.compose.runtime.Stable

/**
 * 正文编辑域的状态契约，由 [ReadContentEditDelegate] 独立持有，
 * 不再以 6 个平铺字段挂在 [ReadBookUiState] 上。
 * sheet 的开合仍由 [ReadBookUiState.activeSheet] 单一持有。
 */
@Stable
data class ContentEditUiState(
    val loading: Boolean = false,
    val text: String = "",
    val title: String = "",
    val cursorOffset: Int = 0,
    val isLocalTxt: Boolean = false,
    val saveToSource: Boolean = false,
)
