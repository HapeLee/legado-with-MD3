package io.legado.app.ui.book.toc.rule.preview

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.TxtTocRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class TxtTocRulePreviewUiState(
    val loading: Boolean = true,
    val rules: ImmutableList<TocRulePreviewItem> = persistentListOf(),
    val currentRule: String = "",
    val selectedRule: String = "",
    val activeSheet: TxtTocRulePreviewSheet? = null,
    val isGridLayout: Boolean = true,
    val editingRule: TxtTocRule? = null,
) {
    val hasSelection: Boolean get() = selectedRule.isNotEmpty()
}

@Stable
data class TocRulePreviewItem(
    val rule: TxtTocRule,
    val chapterCount: Int = 0,
    val totalCount: Int = 0,
    val chapters: ImmutableList<String> = persistentListOf(),
)

sealed interface TxtTocRulePreviewSheet {
    data class ChapterList(val item: TocRulePreviewItem) : TxtTocRulePreviewSheet
}

sealed interface TxtTocRulePreviewIntent {
    data object DismissSheet : TxtTocRulePreviewIntent
    data class ShowChapterList(val item: TocRulePreviewItem) : TxtTocRulePreviewIntent
    data class SelectRule(val rule: String) : TxtTocRulePreviewIntent
    data object ToggleLayout : TxtTocRulePreviewIntent
    data class EditRule(val rule: TxtTocRule) : TxtTocRulePreviewIntent
    data object DismissEditDialog : TxtTocRulePreviewIntent
    data class SaveRule(val rule: TxtTocRule) : TxtTocRulePreviewIntent
}

sealed interface TxtTocRulePreviewEffect {
    data class ApplyRule(val rule: String) : TxtTocRulePreviewEffect
    data class ShowToast(val message: String) : TxtTocRulePreviewEffect
}
