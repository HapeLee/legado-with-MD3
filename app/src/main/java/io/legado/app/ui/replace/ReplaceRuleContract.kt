package io.legado.app.ui.replace

import android.net.Uri
import androidx.compose.runtime.Immutable
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem

@Immutable
data class ReplaceRuleItemUi(
    override val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val group: String?,
    val pattern: String,
    val replacement: String,
    val scope: String?,
    val scopeTitle: Boolean,
    val scopeContent: Boolean,
    val excludeScope: String?,
    val isRegex: Boolean,
    val timeoutMillisecond: Long,
    val order: Int
) : SelectableItem<Long> {
    fun toEntity() = ReplaceRule(
        id = id,
        name = name,
        group = group,
        pattern = pattern,
        replacement = replacement,
        scope = scope,
        scopeTitle = scopeTitle,
        scopeContent = scopeContent,
        excludeScope = excludeScope,
        isEnabled = isEnabled,
        isRegex = isRegex,
        timeoutMillisecond = timeoutMillisecond,
        order = order
    )
}

data class ReplaceRuleUiState(
    override val items: List<ReplaceRuleItemUi> = emptyList(),
    override val selectedIds: Set<Long> = emptySet(),
    override val searchKey: String = "",
    val sortMode: String = "desc",
    val selectedGroup: String? = null,
    val interaction: InteractionState = InteractionState()
) : ListUiState<ReplaceRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isUploading
}

sealed interface ReplaceRuleIntent {
    data class SetSearchMode(val active: Boolean) : ReplaceRuleIntent
    data class UpdateSearchQuery(val query: String) : ReplaceRuleIntent
    data object ClearSelection : ReplaceRuleIntent
    data object SelectAll : ReplaceRuleIntent
    data object InvertSelection : ReplaceRuleIntent
    data class SetSelection(val ids: Set<Long>) : ReplaceRuleIntent
    data class ToggleSelection(val id: Long) : ReplaceRuleIntent
    data object EnableSelection : ReplaceRuleIntent
    data object DisableSelection : ReplaceRuleIntent
    data object DeleteSelection : ReplaceRuleIntent
    data object UploadSelection : ReplaceRuleIntent
    data class ExportSelection(val uri: Uri) : ReplaceRuleIntent
    data class MoveItem(val from: Int, val to: Int) : ReplaceRuleIntent
    data object SaveSortOrder : ReplaceRuleIntent
    data class DeleteRule(val rule: ReplaceRule) : ReplaceRuleIntent
    data class SetRuleEnabled(val id: Long, val enabled: Boolean) : ReplaceRuleIntent
    data class CopyRule(val rule: ReplaceRule) : ReplaceRuleIntent
    data class ImportSource(val text: String) : ReplaceRuleIntent
    data object CancelImport : ReplaceRuleIntent
    data class ToggleImportSelection(val index: Int) : ReplaceRuleIntent
    data class ToggleImportAll(val isSelected: Boolean) : ReplaceRuleIntent
    data class UpdateImportItem(val index: Int, val rule: ReplaceRule) : ReplaceRuleIntent
    data object SaveImportedRules : ReplaceRuleIntent
    // ReplaceRule-specific
    data class SetGroup(val groupName: String?) : ReplaceRuleIntent
    data class SetSortMode(val mode: String) : ReplaceRuleIntent
    data class ToTop(val rule: ReplaceRule) : ReplaceRuleIntent
    data class ToBottom(val rule: ReplaceRule) : ReplaceRuleIntent
    data class TopSelectByIds(val ids: Set<Long>) : ReplaceRuleIntent
    data class BottomSelectByIds(val ids: Set<Long>) : ReplaceRuleIntent
    data class AddGroup(val group: String) : ReplaceRuleIntent
    data class DeleteGroup(val group: String) : ReplaceRuleIntent
    data class UpGroup(val oldGroup: String, val newGroup: String?) : ReplaceRuleIntent
}

sealed interface ReplaceRuleEffect
