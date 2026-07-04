package io.legado.app.ui.highlightTagRule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.AdaptiveSwitch
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightTagRuleEditSheet(
    show: Boolean,
    rule: HighlightTagRule?,
    onDismissRequest: () -> Unit,
    onSave: (HighlightTagRule) -> Unit,
    onCopy: (HighlightTagRule) -> Unit,
    onPaste: () -> HighlightTagRule?,
) {
    val scope = rememberCoroutineScope()

    val isNew = rule == null || rule.id == 0L
    val initial = remember(show, rule) {
        rule ?: HighlightTagRule()
    }

    var title by remember(show, rule) { mutableStateOf(initial.title) }
    var pattern by remember(show, rule) { mutableStateOf(initial.pattern) }
    var enabled by remember(show, rule) { mutableStateOf(initial.enabled) }

    var showMenu by remember(show, rule) { mutableStateOf(false) }

    fun getCurrentRule(): HighlightTagRule {
        return initial.copy(
            title = title,
            pattern = pattern,
            enabled = enabled
        )
    }

    AppModalBottomSheet(
        title = if (isNew) {
            stringResource(R.string.highlight_tag_add_rule)
        } else {
            stringResource(R.string.highlight_tag_edit_rule)
        },
        startAction = {
            MediumPlainButton(
                onClick = onDismissRequest,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        },
        endAction = {
            Box {
                MediumPlainButton(
                    onClick = { showMenu = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_rule),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = {
                            onCopy(getCurrentRule())
                            showMenu = false
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.paste_rule),
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = {
                            scope.launch {
                                onPaste()?.let { pasted ->
                                    title = pasted.title
                                    pattern = pasted.pattern
                                    enabled = pasted.enabled
                                }
                            }
                            showMenu = false
                        }
                    )
                }
            }
        },
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.highlight_tag_title),
                    singleLine = true
                )
                AppTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = pattern,
                    onValueChange = { pattern = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.highlight_tag_pattern),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = stringResource(R.string.enabled),
                        style = LegadoTheme.typography.bodyMedium
                    )
                    AdaptiveSwitch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }

            AppFloatingActionButton(
                onClick = { onSave(getCurrentRule()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                tooltipText = stringResource(R.string.action_save),
                icon = Icons.Default.Save
            )
        }
    }
}
