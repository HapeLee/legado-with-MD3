package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.RegexColorRule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySettingItem
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent

@Composable
fun RegexColorConfigSheet(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenFontSelect: (Int) -> Unit,
) {
    var rules by remember { mutableStateOf(ReadBookConfig.regexColorRules.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var editingColorIndex by remember { mutableIntStateOf(-1) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.regex_color_config),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                itemsIndexed(rules, key = { _, rule -> rule.pattern }) { index, rule ->
                    RegexColorRuleItem(
                        rule = rule,
                        onColorClick = {
                            editingColorIndex = index
                            showColorPicker = true
                        },
                        onFontClick = { onOpenFontSelect(index) },
                        onDeleteClick = {
                            rules = rules.toMutableList().also { it.removeAt(index) }
                            saveRules(rules, onIntent)
                        },
                    )
                }
            }

            TextButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.add),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    // Add rule dialog
    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, pattern ->
                val rule = RegexColorRule(
                    name = name,
                    pattern = pattern,
                    color = ReadBookConfig.durConfig.curTextAccentColor(),
                )
                rules = rules + rule
                saveRules(rules, onIntent)
                showAddDialog = false
            },
        )
    }

    // Color picker
    if (showColorPicker && editingColorIndex in rules.indices) {
        ColorPickerSheet(
            show = true,
            initialColor = rules[editingColorIndex].color or 0xFF000000.toInt(),
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                rules = rules.toMutableList().also {
                    it[editingColorIndex] = it[editingColorIndex].copy(color = color)
                }
                saveRules(rules, onIntent)
                showColorPicker = false
            },
        )
    }
}

@Composable
private fun RegexColorRuleItem(
    rule: RegexColorRule,
    onColorClick: () -> Unit,
    onFontClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    TinySettingItem(
        title = rule.name,
        description = rule.pattern,
        onClick = onColorClick,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onColorClick,
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(rule.color or 0xFF000000.toInt()),
                    border = BorderStroke(1.dp, LegadoTheme.colorScheme.outlineVariant),
                ) {}
                IconButton(
                    onClick = onFontClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, pattern: String) -> Unit,
) {
    val defaultPatterns = listOf(
        "“匹配内容”" to "“.+?”",
        "《匹配内容》" to "《.+?》",
        "\"匹配内容\"" to "\".+?\"",
    )
    var showCustomInput by remember { mutableStateOf(false) }
    var customPattern by remember { mutableStateOf("") }

    if (showCustomInput) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.custom_regex_rule)) },
            text = {
                OutlinedTextField(
                    value = customPattern,
                    onValueChange = { customPattern = it },
                    placeholder = { Text(stringResource(R.string.input_regex_pattern)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pattern = customPattern.trim()
                        if (pattern.isNotEmpty()) {
                            onAdd(pattern, pattern)
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.add_regex_rule)) },
            text = {
                Column {
                    defaultPatterns.forEach { (name, pattern) ->
                        TextButton(
                            onClick = { onAdd(name, pattern) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.custom_regex),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun saveRules(rules: List<RegexColorRule>, onIntent: (ReadBookIntent) -> Unit) {
    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.RegexColorRules(rules)))
}
