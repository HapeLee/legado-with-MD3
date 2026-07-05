package io.legado.app.ui.book.toc.rule.preview

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.TxtTocRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    val searchQuery: String = "",
    val showSearch: Boolean = false,
) {
    val hasSelection: Boolean get() = selectedRule.isNotEmpty()
    val filteredRules: ImmutableList<TocRulePreviewItem>
        get() = if (searchQuery.isBlank()) rules
        else rules.filter {
            it.rule.name.contains(searchQuery, ignoreCase = true) ||
                    it.rule.example?.contains(searchQuery, ignoreCase = true) == true ||
                    it.rule.rule.contains(searchQuery, ignoreCase = true)
        }.toImmutableList()
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
    data object OpenManagePage : TxtTocRulePreviewIntent
    data class EditRule(val rule: TxtTocRule) : TxtTocRulePreviewIntent
    data object DismissEditDialog : TxtTocRulePreviewIntent
    data class SaveRule(val rule: TxtTocRule) : TxtTocRulePreviewIntent
    data object ToggleSearch : TxtTocRulePreviewIntent
    data class UpdateSearchQuery(val query: String) : TxtTocRulePreviewIntent
    data object ApplyRule : TxtTocRulePreviewIntent
}

sealed interface TxtTocRulePreviewEffect {
    data class ShowToast(val message: String) : TxtTocRulePreviewEffect
    data object OpenManagePage : TxtTocRulePreviewEffect
    data class ApplyRule(val rule: String) : TxtTocRulePreviewEffect
}
