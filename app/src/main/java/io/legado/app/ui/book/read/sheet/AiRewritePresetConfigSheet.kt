package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.AiRewritePresetConfigUiState
import io.legado.app.ui.book.read.AiRewritePresetUi
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun AiRewritePresetConfigSheet(
    show: Boolean,
    state: AiRewritePresetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.ai_rewrite_presets),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = stringResource(R.string.ai_rewrite_presets_summary),
                    modifier = Modifier.weight(1f),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.bodySmall,
                )
                MediumTonalButton(
                    onClick = { onIntent(ReadBookIntent.AddAiRewritePreset) },
                    icon = Icons.Default.Add,
                    text = stringResource(R.string.add),
                )
            }

            if (state.editing) {
                Spacer(Modifier.height(12.dp))
                PresetEditor(state = state, onIntent = onIntent)
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                AppText(
                    text = message,
                    color = LegadoTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(12.dp))
            if (state.presets.isEmpty()) {
                AppText(
                    text = stringResource(R.string.ai_rewrite_no_preset),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.presets, key = { it.id }) { preset ->
                        PresetItem(
                            preset = preset,
                            onEdit = { onIntent(ReadBookIntent.EditAiRewritePreset(preset)) },
                            onDelete = { onIntent(ReadBookIntent.RequestDeleteAiRewritePreset(preset)) },
                        )
                    }
                }
            }
        }
    }

    val deletingPreset = state.deletePreset
    AppAlertDialog(
        show = deletingPreset != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDeleteAiRewritePreset) },
        title = stringResource(R.string.delete),
        text = deletingPreset?.name?.let {
            stringResource(R.string.ai_rewrite_preset_delete_confirm, it)
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmDeleteAiRewritePreset) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDeleteAiRewritePreset) },
    )
}

@Composable
private fun PresetEditor(
    state: AiRewritePresetConfigUiState,
    onIntent: (ReadBookIntent) -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTextField(
                value = state.editingName,
                onValueChange = { onIntent(ReadBookIntent.SetAiRewritePresetName(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ai_rewrite_preset_name),
                singleLine = true,
            )
            AppTextField(
                value = state.editingInstruction,
                onValueChange = { onIntent(ReadBookIntent.SetAiRewritePresetInstruction(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ai_rewrite_preset_instruction),
                minLines = 3,
                maxLines = 6,
            )
            ConfirmDismissButtonsRow(
                onDismiss = { onIntent(ReadBookIntent.CancelAiRewritePresetEdit) },
                onConfirm = { onIntent(ReadBookIntent.SaveAiRewritePreset) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.save),
            )
        }
    }
}

@Composable
private fun PresetItem(
    preset: AiRewritePresetUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.onSheetContent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = preset.name,
                    style = LegadoTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = preset.instruction,
                    modifier = Modifier.padding(top = 2.dp),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MediumTonalButton(
                onClick = onEdit,
                icon = Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit),
            )
            MediumTonalButton(
                onClick = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}
