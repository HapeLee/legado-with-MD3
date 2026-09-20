package io.legado.app.ui.book.read

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.HighlightRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 高亮规则域的状态契约，由 [ReadHighlightRuleDelegate] 独立持有，
 * 不再挂在 [ReadBookUiState] 上。sheet 的开合仍由 [ReadBookUiState.activeSheet] 单一持有。
 */
@Stable
data class HighlightRuleConfigUiState(
    val rules: ImmutableList<HighlightRule> = persistentListOf(),
    val editingRule: HighlightRule? = null,
    val showNewRule: Boolean = false,
    val deleteRule: HighlightRule? = null,
    val importState: BaseImportUiState<HighlightRule> = BaseImportUiState.Idle,
    /** 当前批量弹层来自「预设规则」而非文件/URL 导入，只影响标题与默认勾选。 */
    val presetImport: Boolean = false,
)
