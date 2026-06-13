package io.legado.app.ui.dict.rule

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class DictRuleItemUi(
    override val id: String,
    val urlRule: String,
    val showRule: String,
    val isEnabled: Boolean,
    val rule: DictRule
) : SelectableItem<String>

@Stable
data class DictRuleUiState(
    override val items: ImmutableList<DictRuleItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<String> = persistentSetOf(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : ListUiState<DictRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

sealed interface DictRuleIntent {
    data class SetSearchMode(val active: Boolean) : DictRuleIntent
    data class UpdateSearchQuery(val query: String) : DictRuleIntent
    data object ClearSelection : DictRuleIntent
    data object SelectAll : DictRuleIntent
    data object InvertSelection : DictRuleIntent
    data class SetSelection(val ids: Set<String>) : DictRuleIntent
    data class ToggleSelection(val id: String) : DictRuleIntent
    data object EnableSelection : DictRuleIntent
    data object DisableSelection : DictRuleIntent
    data object DeleteSelection : DictRuleIntent
    data object UploadSelection : DictRuleIntent
    data class ExportSelection(val uri: Uri) : DictRuleIntent
    data class MoveItem(val from: Int, val to: Int) : DictRuleIntent
    data object SaveSortOrder : DictRuleIntent
    data class SaveRule(val rule: DictRule, val isNew: Boolean) : DictRuleIntent
    data class DeleteRule(val rule: DictRule) : DictRuleIntent
    data class SetRuleEnabled(val rule: DictRule, val enabled: Boolean) : DictRuleIntent
    data class CopyRule(val rule: DictRule) : DictRuleIntent
    data class ImportSource(val text: String) : DictRuleIntent
    data object CancelImport : DictRuleIntent
    data class ToggleImportSelection(val index: Int) : DictRuleIntent
    data class ToggleImportAll(val isSelected: Boolean) : DictRuleIntent
    data class UpdateImportItem(val index: Int, val rule: DictRule) : DictRuleIntent
    data object SaveImportedRules : DictRuleIntent
}

sealed interface DictRuleEffect

data class DictRuleRenderState(
    val uiState: DictRuleUiState,
    val importState: BaseImportUiState<DictRule>,
)
