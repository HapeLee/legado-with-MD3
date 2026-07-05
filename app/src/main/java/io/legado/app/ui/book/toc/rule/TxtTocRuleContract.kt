package io.legado.app.ui.book.toc.rule

import android.net.Uri
import androidx.compose.runtime.Immutable
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem

@Immutable
data class TxtTocRuleItemUi(
    override val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val rule: TxtTocRule,
    val example: String = ""
) : SelectableItem<Long>

data class TxtTocRuleUiState(
    override val items: List<TxtTocRuleItemUi> = emptyList(),
    override val selectedIds: Set<Long> = emptySet(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : ListUiState<TxtTocRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

sealed interface TxtTocRuleIntent {
    data class SetSearchMode(val active: Boolean) : TxtTocRuleIntent
    data class UpdateSearchQuery(val query: String) : TxtTocRuleIntent
    data object ClearSelection : TxtTocRuleIntent
    data object SelectAll : TxtTocRuleIntent
    data object InvertSelection : TxtTocRuleIntent
    data class SetSelection(val ids: Set<Long>) : TxtTocRuleIntent
    data class ToggleSelection(val id: Long) : TxtTocRuleIntent
    data object EnableSelection : TxtTocRuleIntent
    data object DisableSelection : TxtTocRuleIntent
    data object DeleteSelection : TxtTocRuleIntent
    data object UploadSelection : TxtTocRuleIntent
    data class ExportSelection(val uri: Uri) : TxtTocRuleIntent
    data class MoveItem(val from: Int, val to: Int) : TxtTocRuleIntent
    data object SaveSortOrder : TxtTocRuleIntent
    data class SaveRule(val rule: TxtTocRule, val isNew: Boolean) : TxtTocRuleIntent
    data class DeleteRule(val rule: TxtTocRule) : TxtTocRuleIntent
    data class SetRuleEnabled(val rule: TxtTocRule, val enabled: Boolean) : TxtTocRuleIntent
    data class CopyRule(val rule: TxtTocRule) : TxtTocRuleIntent
    data class ImportSource(val text: String) : TxtTocRuleIntent
    data object CancelImport : TxtTocRuleIntent
    data class ToggleImportSelection(val index: Int) : TxtTocRuleIntent
    data class ToggleImportAll(val isSelected: Boolean) : TxtTocRuleIntent
    data class UpdateImportItem(val index: Int, val rule: TxtTocRule) : TxtTocRuleIntent
    data object SaveImportedRules : TxtTocRuleIntent
    data object ImportBuiltInRules : TxtTocRuleIntent
}

sealed interface TxtTocRuleEffect

data class TxtTocRuleRenderState(
    val uiState: TxtTocRuleUiState,
    val importState: BaseImportUiState<TxtTocRule>,
)
