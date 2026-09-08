package io.legado.app.ui.widget.components.importComponents

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.core.platform.ImportFieldValue
import io.legado.app.core.platform.ImportJsonEditorProvider
import io.legado.app.core.platform.ImportJsonField
import io.legado.app.core.ui.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun SourceInputDialog(
    show: Boolean,
    title: String = "网络导入",
    hint: String = "请输入 URL 或 JSON",
    initialValue: String = "",
    historyValues: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(show) { mutableStateOf(initialValue) }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        content = {
            Column {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    label = hint,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )

                if (historyValues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(stringResource(R.string.history_label), style = LegadoTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(historyValues) { history ->
                            AssistChip(
                                onClick = { text = history },
                                label = { AppText(history, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            // 拦截空输入，非空才执行回调
            if (text.isNotBlank()) onConfirm(text)
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismissRequest
    )
}

//TODO: 动画
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> BatchImportDialog(
    title: String,
    importState: BaseImportUiState<T>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<T>) -> Unit,
    onToggleItem: (index: Int) -> Unit,
    onToggleAll: (isSelected: Boolean) -> Unit,
    onItemInfoClick: (index: Int) -> Unit = {},
    onUpdateItem: (index: Int, data: T) -> Unit = { _, _ -> },
    topBarActions: @Composable RowScope.() -> Unit = {},
    itemTitle: (data: T) -> String,
    itemSubtitle: (data: T) -> String? = { null }
) {
    AppAlertDialog(
        data = importState as? BaseImportUiState.Loading,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.loading),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AppCircularProgressIndicator()
            }
        }
    )

    AppAlertDialog(
        data = importState as? BaseImportUiState.Error,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.error),
        onConfirm = { onDismissRequest() },
        content = { error ->
            AppText(error.msg)
        }
    )

    val show = importState is BaseImportUiState.Success<T>

    var cachedState by remember { mutableStateOf<BaseImportUiState.Success<T>?>(null) }
    if (importState is BaseImportUiState.Success<T>) {
        cachedState = importState
    }

    if (!show && cachedState == null) return

    val currentState = cachedState!!
    var editingIndex by remember(currentState.source) { mutableStateOf<Int?>(null) }
    val editingItem = editingIndex?.let { currentState.items.getOrNull(it) }
    val isEditing = editingItem != null
    val selectedCount = currentState.items.count { it.isSelected }
    val totalCount = currentState.items.size
    val allSelected = selectedCount == totalCount
    val sheetTitle = when {
        isEditing -> itemTitle(editingItem.data)
        selectedCount > 0 -> {
            stringResource(
                R.string.select_count,
                selectedCount,
                totalCount
            )
        }

        else -> title
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f),
        title = sheetTitle,
        startAction = if (isEditing) {
            {
                MediumTonalButton(
                    onClick = { editingIndex = null },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        } else {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topBarActions()
                    MediumTonalButton(
                        onClick = { onToggleAll(!allSelected) },
                        icon = Icons.Default.SelectAll,
                        contentDescription = stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)
                    )
                }
            }
        },
        endAction = if (!isEditing && selectedCount > 0) {
            {
                MediumTonalButton(
                    onClick = {
                        val selectedData = currentState.items.filter { it.isSelected }.map { it.data }
                        onConfirm(selectedData)
                    },
                    icon = Icons.Default.FileDownload,
                    text = stringResource(R.string.import_action)
                )
            }
        } else {
            null
        }
    ) {
        if (isEditing) {
            BatchImportJsonEditContent(
                data = editingItem.data,
                version = currentState.version
            ) { data ->
                editingIndex?.let { onUpdateItem(it, data) }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.58f)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        currentState.items,
                        key = { _, item -> item.data.hashCode() }
                    ) { index, itemWrapper ->
                        ImportItemRow(
                            title = itemTitle(itemWrapper.data),
                            subtitle = itemSubtitle(itemWrapper.data),
                            isSelected = itemWrapper.isSelected,
                            status = itemWrapper.status,
                            onClick = { onToggleItem(index) },
                            onInfoClick = {
                                onItemInfoClick(index)
                                editingIndex = index
                            }
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(8.dp)
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun <T> BatchImportJsonEditContent(
    data: T,
    version: Int,
    onDataChange: (T) -> Unit
) {
    val editor = ImportJsonEditorProvider.current
    val fields = remember(version) { editor.fieldsOf(data) }

    if (fields == null) {
        AppText(stringResource(R.string.edit_not_supported))
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.58f),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = fields,
            key = { it.name }
        ) { field ->
            BatchImportJsonField(
                field = field,
                onBooleanChange = { value ->
                    editor.withBoolean(data, field.name, value)?.let(onDataChange)
                },
                onTextChange = { text ->
                    editor.withEditedText(data, field.name, text)?.let(onDataChange)
                }
            )
        }
    }
}

@Composable
private fun BatchImportJsonField(
    field: ImportJsonField,
    onBooleanChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit
) {
    val value = field.value
    if (value is ImportFieldValue.Bool) {
        GlassCard(
            containerColor = LegadoTheme.colorScheme.onSheetContent
        ) {
            SwitchSettingItem(
                title = field.name,
                checked = value.value,
                onCheckedChange = onBooleanChange
            )
        }

        return
    }

    val isJsonText = value is ImportFieldValue.Json
    val initialText = when (value) {
        is ImportFieldValue.Json -> value.text
        is ImportFieldValue.Text -> value.text
        ImportFieldValue.Null -> ""
        is ImportFieldValue.Bool -> ""
    }
    var text by remember(field.name, initialText) { mutableStateOf(initialText) }

    AppTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            onTextChange(newText)
        },
        label = field.name,
        modifier = Modifier.fillMaxWidth(),
        singleLine = !isJsonText,
        maxLines = if (isJsonText) 8 else 1
    )
}

@Composable
fun ImportItemRow(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    status: ImportStatus,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    SelectionItemCard(
        title = title,
        subtitle = subtitle,
        isSelected = isSelected,
        inSelectionMode = true,
        onToggleSelection = onClick,
        containerColor = LegadoTheme.colorScheme.onSheetContent,
        trailingAction = {
            AppText(
                text = when (status) {
                    ImportStatus.New -> stringResource(R.string.import_status_new)
                    ImportStatus.Update -> stringResource(R.string.import_status_update)
                    ImportStatus.Existing -> stringResource(R.string.import_status_existing)
                    ImportStatus.Error -> stringResource(R.string.import_status_error)
                },
                style = LegadoTheme.typography.labelMedium,
                color = when (status) {
                    ImportStatus.New -> LegadoTheme.colorScheme.primary
                    ImportStatus.Update -> LegadoTheme.colorScheme.secondary
                    ImportStatus.Error -> LegadoTheme.colorScheme.error
                    else -> LegadoTheme.colorScheme.outline
                },
                modifier = Modifier.padding(end = 4.dp)
            )

            SmallPlainButton(
                onClick = onInfoClick,
                icon = Icons.Default.Info,
                contentDescription = stringResource(R.string.details)
            )
        }
    )
}
