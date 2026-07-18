package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KnowledgeEditFieldCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogValue by remember(value) { mutableStateOf(value) }

    GlassCard(
        onClick = {
            dialogValue = value
            showDialog = true
        },
        modifier = modifier,
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppText(
                text = label,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedTextLine(
                text = value.ifBlank { "—" },
                style = LegadoTheme.typography.bodyMedium,
                color = if (value.isBlank()) LegadoTheme.colorScheme.onSurfaceVariant else LegadoTheme.colorScheme.onSurface,
            )
        }
    }

    if (showDialog) {
        AppAlertDialog(
            show = showDialog,
            onDismissRequest = { showDialog = false },
            title = label,
            confirmText = stringResource(R.string.apply),
            onConfirm = {
                onValueChange(dialogValue)
                showDialog = false
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = { showDialog = false },
            content = {
                AppTextField(
                    value = dialogValue,
                    onValueChange = { dialogValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = !multiline,
                    minLines = if (multiline) 4 else 1,
                    maxLines = if (multiline) 12 else 1,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KnowledgeFlowTagEditor(
    title: String,
    tags: ImmutableList<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Int) -> Unit,
    showCloseIcon: Boolean = true,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTagValue by remember { mutableStateOf("") }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var removeTagIndex by remember { mutableStateOf(-1) }
    var removeTagName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(
            text = title,
            style = LegadoTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEachIndexed { index, tag ->
                TagChip(
                    text = tag,
                    showCloseIcon = showCloseIcon,
                    onClick = {
                        if (showCloseIcon) {
                            removeTagIndex = index
                            removeTagName = tag
                            showRemoveDialog = true
                        } else {
                            onRemoveTag(index)
                        }
                    },
                )
            }
            GlassCard(
                onClick = {
                    newTagValue = ""
                    showAddDialog = true
                },
                containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
            ) {
                Box(
                    modifier = Modifier.padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    )
                ) {
                    AppIcon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        modifier = Modifier.size(14.dp),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = showAddDialog,
        onDismissRequest = { showAddDialog = false },
        title = title,
        confirmText = stringResource(R.string.apply),
        onConfirm = {
            if (newTagValue.isNotBlank()) {
                onAddTag(newTagValue)
            }
            showAddDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showAddDialog = false },
        content = {
            AppTextField(
                value = newTagValue,
                onValueChange = { newTagValue = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
    )

    AppAlertDialog(
        show = showRemoveDialog,
        onDismissRequest = { showRemoveDialog = false },
        title = title,
        text = removeTagName,
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            onRemoveTag(removeTagIndex)
            showRemoveDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showRemoveDialog = false },
    )

}

@Composable
fun TagChip(
    text: String,
    showCloseIcon: Boolean = false,
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = 10.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedTextLine(
                text = text,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSecondaryContainer,
            )
            if (showCloseIcon) {
                Spacer(modifier = Modifier.width(2.dp))
                AppIcon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = LegadoTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
