package io.legado.app.ui.tagGroupRule

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class TagGroupRuleItemUi(
    override val id: Long,
    val displayName: String,
    val pattern: String,
    val groupName: String,
    val rule: TagGroupRule
) : SelectableItem<Long>

@Stable
data class TagGroupRuleUiState(
    override val items: ImmutableList<TagGroupRuleItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : ListUiState<TagGroupRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

sealed interface TagGroupRuleIntent {
    data class SetSearchMode(val active: Boolean) : TagGroupRuleIntent
    data class UpdateSearchQuery(val query: String) : TagGroupRuleIntent
    data object ClearSelection : TagGroupRuleIntent
    data object SelectAll : TagGroupRuleIntent
    data object InvertSelection : TagGroupRuleIntent
    data class SetSelection(val ids: Set<Long>) : TagGroupRuleIntent
    data class ToggleSelection(val id: Long) : TagGroupRuleIntent
    data object DeleteSelection : TagGroupRuleIntent
    data object UploadSelection : TagGroupRuleIntent
    data class ExportSelection(val uri: android.net.Uri) : TagGroupRuleIntent
    data class MoveItem(val from: Int, val to: Int) : TagGroupRuleIntent
    data object SaveSortOrder : TagGroupRuleIntent
    data class SaveRule(val rule: TagGroupRule, val isNew: Boolean) : TagGroupRuleIntent
    data class DeleteRule(val rule: TagGroupRule) : TagGroupRuleIntent
    data class CopyRule(val rule: TagGroupRule) : TagGroupRuleIntent
    data class ImportSource(val text: String) : TagGroupRuleIntent
    data object CancelImport : TagGroupRuleIntent
    data class ToggleImportSelection(val index: Int) : TagGroupRuleIntent
    data class ToggleImportAll(val isSelected: Boolean) : TagGroupRuleIntent
    data class UpdateImportItem(val index: Int, val rule: TagGroupRule) : TagGroupRuleIntent
    data object SaveImportedRules : TagGroupRuleIntent
    data object SyncGroups : TagGroupRuleIntent
}

sealed interface TagGroupRuleEffect

data class TagGroupRuleRenderState(
    val uiState: TagGroupRuleUiState,
    val importState: BaseImportUiState<TagGroupRule>,
)
