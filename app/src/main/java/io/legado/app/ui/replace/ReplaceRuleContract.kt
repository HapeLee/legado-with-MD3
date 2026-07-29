package io.legado.app.ui.replace

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.book.read.ContentProcessConfigUiState
import io.legado.app.ui.book.read.ContentProcessItemUi
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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

@Stable
data class ReplaceRuleUiState(
    override val items: List<ReplaceRuleItemUi> = emptyList(),
    override val selectedIds: Set<Long> = emptySet(),
    override val searchKey: String = "",
    val sortMode: String = "desc",
    val selectedGroup: String? = null,
    val interaction: InteractionState = InteractionState(),
    // Book-specific state (only active when bookUrl is provided)
    val bookUrl: String? = null,
    val replaceEnabled: Boolean = false,
    val effectiveRules: ImmutableList<ReplaceRule> = persistentListOf(),
    val chineseConvertActive: Boolean = false,
    val reSegmentActive: Boolean = false,
    val contentProcessState: ContentProcessConfigUiState = ContentProcessConfigUiState(),
    val showEffectiveReplaces: Boolean = false,
    val showContentProcesses: Boolean = false,
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
    // Book-specific intents (only when bookUrl is provided)
    data class InitBookData(val bookUrl: String) : ReplaceRuleIntent
    data object ToggleReplaceEnable : ReplaceRuleIntent
    data object ShowEffectiveReplaces : ReplaceRuleIntent
    data object ShowContentProcesses : ReplaceRuleIntent
    data object DismissEffectiveReplaces : ReplaceRuleIntent
    data object DismissContentProcesses : ReplaceRuleIntent
    data class DisableEffectiveRule(val rule: ReplaceRule) : ReplaceRuleIntent
    data object DisableChineseConverter : ReplaceRuleIntent
    data object DisableReSegment : ReplaceRuleIntent
    data class ToggleContentProcess(val id: String, val enabled: Boolean) : ReplaceRuleIntent
    data class RequestDeleteContentProcess(val item: ContentProcessItemUi) : ReplaceRuleIntent
    data object ConfirmDeleteContentProcess : ReplaceRuleIntent
    data object DismissDeleteContentProcess : ReplaceRuleIntent
}

sealed interface ReplaceRuleEffect {
    data class OpenReplaceEditor(val id: Long, val pattern: String?) : ReplaceRuleEffect
}
