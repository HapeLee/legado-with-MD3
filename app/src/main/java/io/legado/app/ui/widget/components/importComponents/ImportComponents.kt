package io.legado.app.ui.widget.components.importComponents

import android.annotation.SuppressLint
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AnimatedText
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch

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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentState = cachedState!!
    val selectedCount = currentState.items.count { it.isSelected }
    val totalCount = currentState.items.size

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer
    ) {
        AppScaffold(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f),
            topBar = {
                CenterAlignedTopAppBar(
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
                    onToggleSelectAll = onToggleAll,
                    onConfirm = {
                        val selectedData =
                            currentState.items.filter { it.isSelected }.map { it.data }
                        onConfirm(selectedData)
                    },
                    onCancel = onDismissRequest
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    currentState.items,
                    key = { _, item -> item.data.hashCode() }) { index, itemWrapper ->
                    ImportItemRow(
                        title = itemTitle(itemWrapper.data),
                        subtitle = itemSubtitle(itemWrapper.data),
                        isSelected = itemWrapper.isSelected,
                        status = itemWrapper.status,
                        onClick = { onToggleItem(index) },
                        onInfoClick = {
                            scope.launch { snackbarHostState.showSnackbar("其实还没写桀桀桀") }
                            onItemInfoClick(index)
                        }
                    )
                }
            }
        }
    }
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
        modifier = Modifier.padding(vertical = 4.dp),
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
                icon = Icons.Default.Info,
                contentDescription = "详情"
            )
        }
    )
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
            OutlinedButton(onClick = onCancel) { AppText("取消") }
            Button(
                enabled = selectedCount > 0,
                onClick = onConfirm
            ) {
                AppText("导入")
            }
        }
    }
}
