package io.legado.app.ui.dict.rule

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.ReorderableSelectionItem
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.exportComponents.FilePickerSheet
import io.legado.app.ui.widget.components.exportComponents.FilePickerSheetMode
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.utils.GSON
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictRuleScreen(
    viewModel: DictRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {

    val uploadRepository: UploadRepository = koinInject()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val rules = uiState.items
    val listState = rememberLazyListState()
    var isSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val selectedIds by viewModel.selectedIds.collectAsState()
    val inSelectionMode = selectedIds.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hapticFeedback = LocalHapticFeedback.current
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DictRule?>(null) }
    var showDeleteRuleDialog by remember { mutableStateOf<DictRule?>(null) }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showUrlInput by remember { mutableStateOf(false) }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var filePickerMode by remember { mutableStateOf(FilePickerSheetMode.EXPORT) }
    var isUploading by remember { mutableStateOf(false) }
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    val importDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.reader().readText()
                    viewModel.importSource(text)
                }
            }
        }
    )

    val exportDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let { it ->
                scope.launch {
                    val rulesToExport = rules
                        .filter { selectedIds.contains(it.name) }
                        .map { it.rule }

                    val json = Gson().toJson(rulesToExport)
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.writer().write(json)
                    }
                }
            }
        }
    )

    if (showUrlInput) {
        SourceInputDialog(
            title = stringResource(R.string.import_on_line),
            onDismissRequest = { showUrlInput = false },
            onConfirm = {
                showUrlInput = false
                viewModel.importSource(it)
            }
        )
    }

    if (showFilePickerSheet) {
        FilePickerSheet(
            sheetState = sheetState,
            onDismissRequest = { showFilePickerSheet = false },
            mode = filePickerMode,
            onSelectSysDir = {
                showFilePickerSheet = false
                exportDoc.launch("exportDictRule.json")
            },
            onSelectSysFile = {},
            onUpload = {
                showFilePickerSheet = false
                scope.launch {
                    val selectedRules = viewModel.getSelectedRules()
                    val json = GSON.toJson(selectedRules)

                    isUploading = true

                    try {
                        runCatching {
                            uploadRepository.upload(
                                fileName = "exportDictRule.json",
                                file = json,
                                contentType = "application/json"
                            )
                        }.onSuccess { url ->
                            isUploading = false
                            val result = snackbarHostState.showSnackbar(
                                message = "上传成功: $url",
                                actionLabel = "复制链接",
                                withDismissAction = true
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                clipboardManager.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("export url", url))
                                )
                            }
                        }.onFailure { e ->
                            isUploading = false
                            snackbarHostState.showSnackbar("上传失败: ${e.localizedMessage}")
                        }
                    } finally {
                        isUploading = false
                    }
                }
            },
            allowExtensions = arrayOf("json")
        )
    }

    (importState as? BaseImportUiState.Success<DictRule>)?.let { state ->
        BatchImportDialog(
            title = "导入词典规则",
            importState = state,
            onDismissRequest = { viewModel.cancelImport() },
            onToggleItem = { viewModel.toggleImportSelection(it) },
            onToggleAll = { viewModel.toggleImportAll(it) },
            onConfirm = { viewModel.saveImportedRules() },
            itemContent = { rule, _ ->
                Column {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    Text(rule.urlRule, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        )
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            viewModel.saveSortOrder()
        }
    }

    if (showDeleteRuleDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteRuleDialog = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.sure_del) + showDeleteRuleDialog!!.name) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.delete(showDeleteRuleDialog!!)
                        showDeleteRuleDialog = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRuleDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.del_msg)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.delSelectionByIds(selectedIds)
                        viewModel.setSelection(emptySet())
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEditSheet) {
        DictRuleEditSheet(
            rule = editingRule,
            onDismissRequest = { showEditSheet = false },
            onSave = {
                if (editingRule == null) {
                    viewModel.insert(it)
                } else {
                    viewModel.update(it)
                }
                showEditSheet = false
            },
            onCopy = { viewModel.copyRule(it) },
            onPaste = { viewModel.pasteRule() }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = {
                        val titleText = remember(isUploading, inSelectionMode, selectedIds, rules) {
                            when {
                                isUploading -> "正在上传..."
                                inSelectionMode -> "已选择 ${selectedIds.size}/${rules.size}"
                                else -> "字典规则"
                            }
                        }
                        AnimatedText(
                            text = titleText
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (inSelectionMode) {
                                    viewModel.setSelection(emptySet())
                                } else {
                                    onBackClick()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (inSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (inSelectionMode) "Cancel" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (!inSelectionMode) {
                            IconButton(onClick = { isSearch = !isSearch }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("在线导入") },
                                    onClick = {
                                        showMenu = false
                                        showUrlInput = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("本地导入") },
                                    onClick = {
                                        importDoc.launch(
                                            arrayOf(
                                                "text/plain",
                                                "application/json"
                                            )
                                        ); showMenu = false
                                    }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(visible = isSearch && !inSelectionMode) {
                    SearchBarSection(
                        query = uiState.searchKey ?: "",
                        onQueryChange = {
                            viewModel.setSearchKey(it)
                        },
                        placeholder = stringResource(id = R.string.search)
                    )
                }
            }
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Localized description") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = !inSelectionMode,
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = {
                        editingRule = null
                        showEditSheet = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FastScrollLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.name }) { item ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = item.name,
                        title = item.name,
                        isEnabled = item.isEnabled,
                        isSelected = selectedIds.contains(item.name),
                        inSelectionMode = inSelectionMode,
                        onToggleSelection = {
                            viewModel.toggleSelection(item.name)
                        },
                        onEnabledChange = { enabled ->
                            viewModel.update(item.rule.copy(enabled = enabled))
                        },
                        onClickEdit = {
                            editingRule = item.rule
                            showEditSheet = true
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        trailingAction = {
                            IconButton(
                                onClick = {
                                    showDeleteRuleDialog = item.rule
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    )
                }
            }
            if (inSelectionMode) {
                DraggableSelectionHandler(
                    listState = listState,
                    items = rules,
                    selectedIds = selectedIds,
                    onSelectionChange = viewModel::setSelection,
                    idProvider = { it.name },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .align(Alignment.TopStart)
                )
            }
            AnimatedVisibility(
                visible = inSelectionMode,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                        .zIndex(1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SelectionBottomBar(
                    onSelectAll = {
                        viewModel.setSelection(rules.map { it.name }.toSet())
                    },
                    onSelectInvert = {
                        val allIds = rules.map { it.name }.toSet()
                        viewModel.setSelection(allIds - selectedIds)
                    },
                    primaryAction = ActionItem(
                        text = stringResource(R.string.delete),
                        icon = { Icon(Icons.Default.Delete, null) },
                        onClick = { showDeleteSelectedDialog = true }
                    ),
                    secondaryActions = listOf(
                        ActionItem(
                            text = stringResource(R.string.enable),
                            onClick = {
                                viewModel.enableSelectionByIds(selectedIds)
                                viewModel.setSelection(emptySet())
                            }
                        ),
                        ActionItem(
                            text = stringResource(R.string.disable_selection),
                            onClick = {
                                viewModel.disableSelectionByIds(selectedIds)
                                viewModel.setSelection(emptySet())
                            }
                        ),
                        ActionItem(
                            text = stringResource(R.string.export),
                            onClick = { showFilePickerSheet = true }
                        )
                    )
                )
            }

        }
    }
}