package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.repository.configNames
import io.legado.app.ui.book.read.HighlightRuleConfigUiState
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySettingItem

@Composable
fun HighlightRuleConfigSheet(
    show: Boolean,
    state: HighlightRuleConfigUiState,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.highlight_rule_config),
        endAction = {
            SmallTonalButton(
                onClick = { onIntent(ReadBookIntent.AddHighlightRule) },
                icon = Icons.Default.Add
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(state.rules, key = { _, rule -> rule.id }) { _, rule ->
                    HighlightRuleItem(
                        rule = rule,
                        onToggle = { enabled ->
                            onIntent(ReadBookIntent.ToggleHighlightRule(rule, enabled))
                        },
                        onEditClick = { onIntent(ReadBookIntent.EditHighlightRule(rule)) },
                        onDeleteClick = {
                            onIntent(ReadBookIntent.RequestDeleteHighlightRule(rule))
                        },
                    )
                }
            }
        }
    }

    // Edit existing rule
    val editingRuleValue = state.editingRule

    HighlightRuleEditSheet(
        show = show && editingRuleValue != null,
        rule = editingRuleValue,
        onDismissRequest = { onIntent(ReadBookIntent.DismissHighlightRuleEdit) },
        onSave = { updated ->
            onIntent(ReadBookIntent.SaveHighlightRule(updated))
        },
    )

    // Add new rule
    HighlightRuleEditSheet(
        show = show && state.showNewRule,
        rule = null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissHighlightRuleEdit) },
        onSave = { newRule ->
            onIntent(ReadBookIntent.SaveHighlightRule(newRule))
        },
    )

    // Delete confirmation
    val deletingRule = state.deleteRule
    val sureDeleteText = stringResource(R.string.sure_delete)
    AppAlertDialog(
        show = show && deletingRule != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDeleteHighlightRule) },
        title = stringResource(R.string.delete),
        text = deletingRule?.let { "$sureDeleteText \"${it.name}\"?" },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmDeleteHighlightRule) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDeleteHighlightRule) },
    )

}

@Composable
private fun HighlightRuleItem(
    rule: HighlightRule,
    onToggle: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val configLabel = rule.configName?.configNames()?.joinToString("、") ?: "全局"
    TinySettingItem(
        title = rule.name.ifBlank { rule.displayPattern() },
        description = "${rule.styleSummary()} · $configLabel",
        onClick = onEditClick,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TinySwitch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.size(36.dp),
                )
                SmallTonalButton(
                    onClick = onEditClick,
                    icon = Icons.Default.Edit
                )
                SmallTonalButton(
                    onClick = onDeleteClick,
                    icon = Icons.Default.Delete
                )
            }
        },
    )
}
