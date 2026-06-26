package io.legado.app.ui.replace.edit

import androidx.compose.runtime.Stable

@Stable
data class ReplaceEditUiState(
    val id: Long = 0,
    val name: String = "",
    val group: String = "默认",
    val pattern: String = "",
    val replacement: String = "",
    val isRegex: Boolean = false,
    val scope: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = false,
    val excludeScope: String = "",
    val timeout: String = "3000",
    val allGroups: List<String> = emptyList(),
    val showGroupDialog: Boolean = false,
    val activeField: ActiveField = ActiveField.None
)

enum class ActiveField { Name, None, Pattern, Replacement, Scope, Exclude }

sealed interface ReplaceEditIntent {
    data class OnNameChange(val value: String) : ReplaceEditIntent
    data class OnPatternChange(val value: String) : ReplaceEditIntent
    data class OnReplacementChange(val value: String) : ReplaceEditIntent
    data class OnScopeChange(val value: String) : ReplaceEditIntent
    data class OnExcludeScopeChange(val value: String) : ReplaceEditIntent
    data class OnGroupChange(val value: String) : ReplaceEditIntent
    data class OnRegexChange(val value: Boolean) : ReplaceEditIntent
    data class OnScopeTitleChange(val value: Boolean) : ReplaceEditIntent
    data class OnScopeContentChange(val value: Boolean) : ReplaceEditIntent
    data class OnTimeoutChange(val value: String) : ReplaceEditIntent
    data class SetActiveField(val field: ActiveField) : ReplaceEditIntent
    data class InsertTextAtCursor(val text: String) : ReplaceEditIntent
    data class ToggleGroupDialog(val show: Boolean) : ReplaceEditIntent
    data class DeleteGroups(val groups: List<String>) : ReplaceEditIntent
    data object CopyRule : ReplaceEditIntent
    data object PasteRule : ReplaceEditIntent
    data object Save : ReplaceEditIntent
}

sealed interface ReplaceEditEffect {
    data object NavigateBack : ReplaceEditEffect
}
