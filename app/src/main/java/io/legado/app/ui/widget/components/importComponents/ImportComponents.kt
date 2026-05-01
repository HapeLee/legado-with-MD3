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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON

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
                    label = hint,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )

                if (historyValues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText("历史记录:", style = LegadoTheme.typography.labelSmall)
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
                SmallIconButton(
                    onClick = { editingIndex = null },
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        } else {
            null
        },
        endAction = if (!isEditing) {
            {
                Row {
                    topBarActions()
                    SmallIconButton(
                        onClick = { onToggleAll(!allSelected) },
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = if (allSelected) "全不选" else "全选"
                    )
                }
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

        ConfirmDismissButtonsRow(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp),
            onDismiss = onDismissRequest,
            onConfirm = {
                val selectedData = currentState.items.filter { it.isSelected }.map { it.data }
                onConfirm(selectedData)
            },
            dismissText = "取消",
            confirmText = "导入",
            confirmEnabled = selectedCount > 0
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
    val jsonObject = remember(version) { data.toImportJsonObject() }

    if (jsonObject == null) {
        AppText("不支持编辑")
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
            items = jsonObject.entrySet().toList(),
            key = { it.key }
        ) { entry ->
            BatchImportJsonField(
                name = entry.key,
                value = entry.value,
                onValueChange = { value ->
                    val updatedJsonObject = data.toImportJsonObject() ?: return@BatchImportJsonField
                    updatedJsonObject.add(entry.key, value)
                    updatedJsonObject.toImportDataLike(data)?.let(onDataChange)
                }
            )
        }
    }
}

@Composable
private fun BatchImportJsonField(
    name: String,
    value: JsonElement,
    onValueChange: (JsonElement) -> Unit
) {
    val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
    if (primitive?.isBoolean == true) {
        GlassCard(
            containerColor = LegadoTheme.colorScheme.onSheetContent
        ) {
            SwitchSettingItem(
                title = name,
                checked = primitive.asBoolean,
                onCheckedChange = { onValueChange(JsonPrimitive(it)) }
            )
        }

        return
    }

    val isJsonText = value.isJsonObject || value.isJsonArray
    val initialText = value.toImportEditText()
    var text by remember(name, initialText) { mutableStateOf(initialText) }

    AppTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toImportJsonElement(value)?.let(onValueChange)
        },
        label = name,
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
                    ImportStatus.New -> "新增"
                    ImportStatus.Update -> "更新"
                    ImportStatus.Existing -> "已有"
                    ImportStatus.Error -> "错误"
                },
                style = LegadoTheme.typography.labelMedium,
                color = when (status) {
                    ImportStatus.New -> MaterialTheme.colorScheme.primary
                    ImportStatus.Update -> MaterialTheme.colorScheme.secondary
                    ImportStatus.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.padding(end = 4.dp)
            )

            SmallIconButton(
                onClick = onInfoClick,
                imageVector = Icons.Default.Info,
                contentDescription = "详情"
            )
        }
    )
}

private fun Any?.toImportJsonObject(): JsonObject? {
    return GSON.toJsonTree(this).takeIf { it.isJsonObject }?.asJsonObject
}

@Suppress("UNCHECKED_CAST")
private fun <T> JsonObject.toImportDataLike(data: T): T? {
    val clazz = data?.let { it::class.java } ?: return null
    return runCatching { GSON.fromJson(this, clazz) as T }.getOrNull()
}

private fun JsonElement.toImportEditText(): String {
    return when {
        this is JsonNull || isJsonNull -> ""
        isJsonObject || isJsonArray -> GSON.toJson(this)
        isJsonPrimitive -> asJsonPrimitive.asString
        else -> toString()
    }
}

private fun String.toImportJsonElement(oldValue: JsonElement): JsonElement? {
    val text = trim()
    if (oldValue.isJsonNull) {
        return if (text.isEmpty()) JsonNull.INSTANCE else JsonPrimitive(this)
    }

    if (oldValue.isJsonObject || oldValue.isJsonArray) {
        if (text.isEmpty()) return JsonNull.INSTANCE
        return runCatching { JsonParser.parseString(this) }.getOrNull()
    }

    if (!oldValue.isJsonPrimitive) return JsonPrimitive(this)

    val primitive = oldValue.asJsonPrimitive
    return when {
        primitive.isNumber -> {
            if (text.isEmpty()) {
                JsonNull.INSTANCE
            } else {
                text.toLongOrNull()?.let { JsonPrimitive(it) }
                    ?: text.toDoubleOrNull()?.let { JsonPrimitive(it) }
            }
        }

        else -> JsonPrimitive(this)
    }
}
