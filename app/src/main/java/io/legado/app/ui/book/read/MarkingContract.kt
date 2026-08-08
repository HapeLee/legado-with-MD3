package io.legado.app.ui.book.read

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.HighlightRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 划线/高亮笔记域的状态契约。
 *
 * 该域无独立屏幕，只承载一次「选中文段 → 配置样式/备注 → 保存」的临时会话：
 * [selection] 是当前选中的文段（与普通书签同构），[highlightRules] 供 Sheet 复用样式预设，
 * [editing] 是同一锚点已有的标记（再次选中划线时预填样式与备注进入编辑模式）。
 */
@Immutable
@Stable
data class MarkingUiState(
    val selection: Bookmark? = null,
    val editing: BookMarking? = null,
    val highlightRules: ImmutableList<HighlightRule> = persistentListOf(),
    /** 编辑模式异步加载标记期间的占位，避免 Sheet 先空再弹内容。 */
    val loading: Boolean = false,
)
