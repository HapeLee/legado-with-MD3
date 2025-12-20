package io.legado.app.ui.widget.components.importComponents

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.AnimatedText
import kotlinx.coroutines.launch

@Composable
fun SourceInputDialog(
    title: String = "网络导入",
    hint: String = "请输入 URL 或 JSON",
    initialValue: String = "",
    historyValues: List<String> = emptyList(), // 历史记录
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(hint) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )

                if (historyValues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("历史记录:", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(historyValues) { history ->
                            AssistChip(
                                onClick = { text = history },
                                label = { Text(history, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> BatchImportDialog(
    title: String,
    importState: BaseImportUiState.Success<T>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<T>) -> Unit, // 返回选中的原始数据列表
    onToggleItem: (index: Int) -> Unit,
    onToggleAll: (isSelected: Boolean) -> Unit,
    onItemInfoClick: (index: Int) -> Unit = {},
    // 插槽：允许调用方自定义顶部菜单
    topBarActions: @Composable RowScope.() -> Unit = {},
    // 插槽：自定义每一行的显示内容
    itemContent: @Composable (data: T, status: ImportStatus) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedCount = importState.items.count { it.isSelected }
    val totalCount = importState.items.size

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedText(
                            if (selectedCount > 0)
                                stringResource(
                                    R.string.select_count,
                                    selectedCount,
                                    totalCount
                                )
                            else
                                title
                        )
                    },
                    actions = topBarActions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                ImportBottomBar(
                    selectedCount = selectedCount,
                    totalCount = totalCount,
                    onToggleSelectAll = { isAll -> onToggleAll(isAll) },
                    onConfirm = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                val selectedData =
                                    importState.items.filter { it.isSelected }.map { it.data }
                                onConfirm(selectedData)
                            }
                        }
                    },
                    onCancel = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(importState.items, key = { _, item -> item.data.hashCode() }) { index, itemWrapper ->
                    ImportItemRow(
                        isSelected = itemWrapper.isSelected,
                        status = itemWrapper.status,
                        onClick = { onToggleItem(index) },
                        onInfoClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("其实还没写桀桀桀")
                            }
                            onItemInfoClick(index)
                        },
                        content = { itemContent(itemWrapper.data, itemWrapper.status) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportItemRow(
    isSelected: Boolean,
    status: ImportStatus,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "CardColor"
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        ListItem(
            modifier = Modifier.animateContentSize(),
            headlineContent = { content() },
            leadingContent = {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            },
            supportingContent = {
                Text(
                    text = when (status) {
                        ImportStatus.New -> "新增"
                        ImportStatus.Update -> "更新"
                        ImportStatus.Existing -> "已有"
                        ImportStatus.Error -> "错误"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (status) {
                        ImportStatus.New -> MaterialTheme.colorScheme.primary
                        ImportStatus.Update -> MaterialTheme.colorScheme.secondary
                        ImportStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            },
            trailingContent = {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "详情"
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val allSelected = selectedCount == totalCount
        OutlinedToggleButton(
            checked = allSelected,
            onCheckedChange = { checked ->
                onToggleSelectAll(checked)
            },
        ) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = if (allSelected) "全不选" else "全选"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("取消") }
            Button(
                enabled = selectedCount > 0,
                onClick = onConfirm
            ) {
                Text("导入")
            }
        }
    }
}
