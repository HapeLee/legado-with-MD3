package io.legado.app.ui.highlightTagRule

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class HighlightTagRuleItemUi(
    override val id: Long,
    val displayName: String,
    val pattern: String,
    val isEnabled: Boolean,
    val rule: HighlightTagRule
) : SelectableItem<Long>

@Stable
data class HighlightTagRuleUiState(
    override val items: ImmutableList<HighlightTagRuleItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : ListUiState<HighlightTagRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

sealed interface HighlightTagRuleIntent {
    data class SetSearchMode(val active: Boolean) : HighlightTagRuleIntent
    data class UpdateSearchQuery(val query: String) : HighlightTagRuleIntent
    data object ClearSelection : HighlightTagRuleIntent
    data object SelectAll : HighlightTagRuleIntent
    data object InvertSelection : HighlightTagRuleIntent
    data class SetSelection(val ids: Set<Long>) : HighlightTagRuleIntent
    data class ToggleSelection(val id: Long) : HighlightTagRuleIntent
    data object EnableSelection : HighlightTagRuleIntent
    data object DisableSelection : HighlightTagRuleIntent
    data object DeleteSelection : HighlightTagRuleIntent
    data object UploadSelection : HighlightTagRuleIntent
    data class ExportSelection(val uri: android.net.Uri) : HighlightTagRuleIntent
    data class MoveItem(val from: Int, val to: Int) : HighlightTagRuleIntent
    data object SaveSortOrder : HighlightTagRuleIntent
    data class SaveRule(val rule: HighlightTagRule, val isNew: Boolean) : HighlightTagRuleIntent
    data class DeleteRule(val rule: HighlightTagRule) : HighlightTagRuleIntent
    data class SetRuleEnabled(val rule: HighlightTagRule, val enabled: Boolean) : HighlightTagRuleIntent
    data class CopyRule(val rule: HighlightTagRule) : HighlightTagRuleIntent
    data class ImportSource(val text: String) : HighlightTagRuleIntent
    data object CancelImport : HighlightTagRuleIntent
    data class ToggleImportSelection(val index: Int) : HighlightTagRuleIntent
    data class ToggleImportAll(val isSelected: Boolean) : HighlightTagRuleIntent
    data class UpdateImportItem(val index: Int, val rule: HighlightTagRule) : HighlightTagRuleIntent
    data object SaveImportedRules : HighlightTagRuleIntent
}

sealed interface HighlightTagRuleEffect

data class HighlightTagRuleRenderState(
    val uiState: HighlightTagRuleUiState,
    val importState: BaseImportUiState<HighlightTagRule>,
)
