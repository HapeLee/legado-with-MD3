package io.legado.app.feature.rss.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.JsonRawEditor
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ActionChip
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun RssSourceImportDialog(
    source: String,
    onClose: () -> Unit,
    viewModel: RssSourceImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, source) {
        viewModel.onIntent(RssSourceImportIntent.Load(source))
    }
    LaunchedEffect(state.phase) {
        if (state.phase == ImportPhase.Complete) {
            close()
            viewModel.onIntent(RssSourceImportIntent.Dismiss)
        }
    }
    RssSourceImportContent(
        source = source,
        state = state,
        onIntent = viewModel::onIntent,
        onDismiss = {
            if (viewModel.state.value.phase != ImportPhase.Saving) {
                viewModel.onIntent(RssSourceImportIntent.Dismiss)
                onClose()
            }
        },
    )
}

@Composable
private fun RssSourceImportContent(
    source: String,
    state: RssSourceImportUiState,
    onIntent: (RssSourceImportIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    var showGroup by rememberSaveable(source) { mutableStateOf(false) }
    val importState = when (state.phase) {
        ImportPhase.Idle, ImportPhase.Complete -> BaseImportUiState.Idle
        ImportPhase.Loading, ImportPhase.Saving -> BaseImportUiState.Loading
        ImportPhase.Error -> BaseImportUiState.Error(state.error.orEmpty())
        ImportPhase.Ready -> remember(source, state.items) {
            BaseImportUiState.Success(
                source = source,
                items = state.items.mapIndexed { index, item ->
                    ImportItemWrapper(
                        data = item,
                        isSelected = item.selected,
                        status = item.status,
                        entryId = index.toString(),
                    )
                }.toImmutableList(),
            )
        }
    }
    BatchImportDialog(
        title = stringResource(R.string.import_rss_source),
        importState = importState,
        onDismissRequest = onDismiss,
        onToggleItem = { onIntent(RssSourceImportIntent.Toggle(it)) },
        onToggleAll = { onIntent(RssSourceImportIntent.SelectAll(it)) },
        onConfirm = { onIntent(RssSourceImportIntent.Confirm) },
        onEditItem = { onIntent(RssSourceImportIntent.Edit(it)) },
        configContent = {
            SwitchSettingItem(
                title = stringResource(R.string.keep_original_name),
                checked = state.keepName,
                onCheckedChange = { onIntent(RssSourceImportIntent.KeepName(it)) },
            )
            SwitchSettingItem(
                title = stringResource(R.string.keep_group),
                checked = state.keepGroup,
                onCheckedChange = { onIntent(RssSourceImportIntent.KeepGroup(it)) },
            )
            SwitchSettingItem(
                title = stringResource(R.string.keep_enable),
                checked = state.keepEnable,
                onCheckedChange = { onIntent(RssSourceImportIntent.KeepEnable(it)) },
            )
            SettingItem(
                title = stringResource(R.string.diy_source_group),
                option = state.customGroup.takeIf { it.isNotBlank() }?.let { group ->
                    val label = stringResource(R.string.diy_edit_source_group_title, group)
                    if (state.appendGroup) "+$label" else label
                },
                onClick = { showGroup = true },
            )
        },
        itemTitle = { it.name },
        itemSubtitle = { it.url },
    )

    if (showGroup && state.phase == ImportPhase.Ready) {
        ImportGroupDialog(state, onDismiss = { showGroup = false }, onConfirm = { group, append ->
            onIntent(RssSourceImportIntent.Group(group, append))
            showGroup = false
        })
    }
    state.editor?.let { editor ->
        val dismiss = { onIntent(RssSourceImportIntent.CancelEdit) }
        AppAlertDialog(
            show = true,
            onDismissRequest = dismiss,
            title = state.items.getOrNull(editor.index)?.name ?: stringResource(R.string.edit),
            content = {
                JsonRawEditor(
                    value = editor.json,
                    onValueChange = { onIntent(RssSourceImportIntent.EditText(it)) },
                    label = "JSON",
                )
                if (editor.invalid) AppText(stringResource(R.string.wrong_format))
            },
            confirmText = stringResource(R.string.save),
            onConfirm = { onIntent(RssSourceImportIntent.SaveEdit) },
            dismissText = stringResource(R.string.cancel),
            onDismiss = dismiss,
        )
    }
    if (state.phase == ImportPhase.Ready && state.error != null) {
        val dismiss = { onIntent(RssSourceImportIntent.AcknowledgeError) }
        AppAlertDialog(
            show = true,
            onDismissRequest = dismiss,
            title = stringResource(R.string.error),
            text = state.error,
            confirmText = stringResource(R.string.ok),
            onConfirm = dismiss,
        )
    }
}

@Composable
private fun ImportGroupDialog(
    state: RssSourceImportUiState,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(state.customGroup) }
    var append by rememberSaveable { mutableStateOf(state.appendGroup) }
    val suggestions = remember(state.groups, group) {
        state.groups.filter { it != group && it.contains(group.trim(), ignoreCase = true) }
    }
    AppAlertDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.diy_edit_source_group),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = stringResource(R.string.group_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions, key = { it }) { suggestion ->
                            ActionChip(
                                label = suggestion,
                                onClick = { group = suggestion },
                            )
                        }
                    }
                }
                SwitchSettingItem(
                    title = stringResource(R.string.add_group),
                    checked = append,
                    onCheckedChange = { append = it },
                )
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onConfirm(group, append) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismiss,
    )
}
