package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.HighlightRule
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.config.HighlightRuleStore
import io.legado.app.ui.book.read.config.HighlightRuleStore.configNames
import io.legado.app.ui.widget.components.TinySwitch
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySettingItem

@Composable
fun HighlightRuleConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var rules by remember { mutableStateOf(HighlightRuleStore.load()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteIndex by remember { mutableIntStateOf(-1) }
    var editingRule by remember { mutableStateOf<HighlightRule?>(null) }
    var showNewRule by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.highlight_rule_config),
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
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    HighlightRuleItem(
                        rule = rule,
                        onToggle = { enabled ->
                            rules = rules.toMutableList().also {
                                it[index] = it[index].copy(enabled = enabled)
                            }
                            saveRules(rules, onIntent)
                        },
                        onEditClick = { editingRule = rule },
                        onDeleteClick = {
                            deleteIndex = index
                            showDeleteConfirm = true
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { showNewRule = true },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
            }
        }
    }

    // Edit existing rule
    val editingRuleValue = editingRule
    if (show && editingRuleValue != null) {
        HighlightRuleEditSheet(
            show = true,
            rule = editingRuleValue,
            onDismissRequest = { editingRule = null },
            onSave = { updated ->
                rules = rules.map { if (it.id == updated.id) updated else it }
                saveRules(rules, onIntent)
                editingRule = null
            },
        )
    }

    // Add new rule
    if (show && showNewRule) {
        HighlightRuleEditSheet(
            show = true,
            rule = null,
            onDismissRequest = { showNewRule = false },
            onSave = { newRule ->
                rules = rules + newRule
                saveRules(rules, onIntent)
                showNewRule = false
            },
        )
    }

    // Delete confirmation
    if (showDeleteConfirm && deleteIndex in rules.indices) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                deleteIndex = -1
            },
            containerColor = io.legado.app.ui.theme.LegadoTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.sure_delete) + " \"${rules[deleteIndex].name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    rules = rules.toMutableList().also { it.removeAt(deleteIndex) }
                    saveRules(rules, onIntent)
                    showDeleteConfirm = false
                    deleteIndex = -1
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteIndex = -1
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
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

private fun saveRules(rules: List<HighlightRule>, onIntent: (ReadBookIntent) -> Unit) {
    onIntent(ReadBookIntent.UpdateConfig(io.legado.app.ui.book.read.ConfigUpdate.HighlightRules(rules)))
}
