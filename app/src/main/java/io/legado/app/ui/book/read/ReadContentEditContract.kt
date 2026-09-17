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
    /** 编辑器正文；[bodyOnly] 打开时非正文片段已折叠成 `〔图片1〕` 这类占位标记。 */
    val text: String = "",
    /** 章名编辑框：`chapters.title` 原值。原样回写，避免把转换后的显示名写进库里。 */
    val chapterTitle: String = "",
    /** 仅显示正文：图片/富文本/分页标记以占位符显示，保存时原样写回。 */
    val bodyOnly: Boolean = false,
    val cursorOffset: Int = 0,
    val isLocalTxt: Boolean = false,
    val saveToSource: Boolean = false,
)
