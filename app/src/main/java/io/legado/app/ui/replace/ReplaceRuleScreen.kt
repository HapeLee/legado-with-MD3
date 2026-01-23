package io.legado.app.ui.replace

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.DraggableSelectionHandler
import io.legado.app.ui.widget.components.EmptyMessageView
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReplaceRuleScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: ReplaceRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    onNavigateToEdit: (ReplaceEditRoute) -> Unit,
) {

    val uploadRepository: UploadRepository = koinInject()

    //TODO: 期望换为Navigation
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rules = uiState.rules
    val groups = uiState.groups

    var isSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteRuleDialog by remember { mutableStateOf<ReplaceRule?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()
    var showGroupManageSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val selectedRuleIds by viewModel.selectedRuleIds.collectAsState()
    val inSelectionMode = selectedRuleIds.isNotEmpty()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabItems = listOf(stringResource(R.string.all)) + groups

    val importState by viewModel.importState.collectAsStateWithLifecycle()
    var showUrlInput by remember { mutableStateOf(false) }

    var showFilePickerSheet by remember { mutableStateOf(false) }
    var filePickerMode by remember { mutableStateOf(FilePickerSheetMode.EXPORT) }
    var isUploading by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboard.current

    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveItemInList(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val canReorder = remember(uiState.sortMode) {
        uiState.sortMode == "asc" || uiState.sortMode == "desc"
    }

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
                        .filter { selectedRuleIds.contains(it.id) }
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
                exportDoc.launch("exportReplaceRule.json")
            },
            onSelectSysFile = {},
            onUpload = {
                showFilePickerSheet = false
                scope.launch {
                    val rulesToExport = rules
                        .filter { selectedRuleIds.contains(it.id) }
                        .map { it.rule }
                    val json = Gson().toJson(rulesToExport)

                    isUploading = true

                    try {
                        runCatching {
                            uploadRepository.upload(
                                fileName = "exportReplaceRule.json",
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

    (importState as? BaseImportUiState.Success<ReplaceRule>)?.let { state ->
        BatchImportDialog(
            title = stringResource(R.string.import_replace_rule),
            importState = state,
            onDismissRequest = { viewModel.cancelImport() },
            onToggleItem = { viewModel.toggleImportSelection(it) },
            onToggleAll = { viewModel.toggleImportAll(it) },
            onConfirm = { viewModel.saveImportedRules() },
            topBarActions = {},
            itemContent = { rule, _ ->
                Column {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    if (!rule.group.isNullOrBlank()) {
                        Text(rule.group!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        )
    }

    if (importState is BaseImportUiState.Loading) {
        Dialog(onDismissRequest = { viewModel.cancelImport() }) { LoadingIndicator() }
    }

    LaunchedEffect(importState) {
        (importState as? BaseImportUiState.Error)?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it.msg)
            }
            viewModel.cancelImport()
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            viewModel.saveSortOrder()
        }
    }

    LaunchedEffect(groups) {
        val maxIndex = groups.size
        if (selectedTabIndex > maxIndex) {
            selectedTabIndex = 0
            viewModel.setSearchKey("")
        }
    }

    if (showGroupManageSheet) {
        GroupManageBottomSheet(
            groups = groups,
            onDismissRequest = { showGroupManageSheet = false },
            sheetState = sheetState,
            viewModel = viewModel
        )
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
                        viewModel.delSelectionByIds(selectedRuleIds)
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = {
                        val titleText = remember(isUploading, inSelectionMode, selectedRuleIds, rules) {
                            when {
                                isUploading -> "正在上传..."
                                inSelectionMode -> "已选择 ${rules.count { it.id in selectedRuleIds }}/${rules.size}"
                                else -> "替换规则"
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
                                        showUrlInput = true // 触发输入框
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("本地导入") },
                                    onClick = { importDoc.launch(arrayOf("text/plain", "application/json")); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("分组管理") },
                                    onClick = { showGroupManageSheet = true; showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("帮助") },
                                    onClick = { /*TODO*/ showMenu = false }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("旧的在前") },
                                    onClick = { viewModel.setSortMode("asc"); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("新的在前") },
                                    onClick = { viewModel.setSortMode("desc"); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("名称升序") },
                                    onClick = {
                                        viewModel.setSortMode("name_asc")
                                        showMenu = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("非时间排序模式下将禁用拖动")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("名称降序") },
                                    onClick = {
                                        viewModel.setSortMode("name_desc")
                                        showMenu = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar("非时间排序模式下将禁用拖动")
                                        }
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
                            selectedTabIndex = 0
                        },
                        placeholder = stringResource(id = R.string.replace_purify_search)
                    )
                }

                val allString = stringResource(R.string.all)
                val tabItems = remember(groups, allString) { listOf(allString) + groups }
                AnimatedVisibility(visible = groups.isNotEmpty()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex.coerceAtMost(tabItems.size - 1)
                            .coerceAtLeast(0),
                        edgePadding = 0.dp,
                        divider = {},
                    ) {
                        tabItems.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = {
                                    selectedTabIndex = index
                                    val group = tabItems.getOrNull(index)
                                    if (group == allString) {
                                        viewModel.setSearchKey("")
                                    } else if (group != null) {
                                        viewModel.setSearchKey("group:$group")
                                    }
                                },
                                modifier = Modifier.wrapContentWidth(),
                                text = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
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
                with(sharedTransitionScope) {
                    FloatingActionButton(
                        modifier = Modifier
                            .animateFloatingActionButton(
                                visible = !inSelectionMode,
                                alignment = Alignment.BottomEnd,
                            )
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "fab_add"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                            ),
                        onClick = {
                            onNavigateToEdit(ReplaceEditRoute(id = -1))
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (rules.isEmpty()) {
                EmptyMessageView(
                    modifier = Modifier.fillMaxSize(),
                    message = "没有替换规则！"
                )
            } else {
                FastScrollLazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules, key = { it.id }) { ui ->
                        with(sharedTransitionScope) {
                            ReorderableSelectionItem(
                                state = reorderableState,
                                key = ui.id,
                                title = ui.name,
                                isEnabled = ui.isEnabled,
                                isSelected = selectedRuleIds.contains(ui.id),
                                inSelectionMode = inSelectionMode,
                                canReorder = canReorder,
                                onToggleSelection = {
                                    viewModel.toggleSelection(ui.id)
                                },
                                onEnabledChange = { enabled ->
                                    viewModel.update(ui.rule.copy(isEnabled = enabled))
                                },
                                onClickEdit = {
                                    onNavigateToEdit(
                                        ReplaceEditRoute(
                                            id = ui.id,
                                            pattern = ui.rule.pattern
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "rule_${ui.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        //clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
                                    ),
                                dropdownContent = { dismiss ->
                                    DropdownMenuItem(
                                        text = { Text("移至顶部") },
                                        onClick = { viewModel.toTop(ui.rule); dismiss() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移至底部") },
                                        onClick = { viewModel.toBottom(ui.rule); dismiss() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = { showDeleteRuleDialog = ui.rule; dismiss() }
                                    )
                                }
                            )
                        }
                    }
                }
                if (inSelectionMode) {
                    DraggableSelectionHandler(
                        listState = listState,
                        items = rules,
                        selectedIds = selectedRuleIds,
                        onSelectionChange = viewModel::setSelection,
                        idProvider = { it.id },
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
                            .offset(y = -ScreenOffset)
                            .zIndex(1f),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    SelectionBottomBar(
                        onSelectAll = {
                            viewModel.setSelection(rules.map { it.id }.toSet())
                        },
                        onSelectInvert = {
                            val allIds = rules.map { it.id }.toSet()
                            viewModel.setSelection(allIds - selectedRuleIds)
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
                                    viewModel.enableSelectionByIds(selectedRuleIds)
                                    viewModel.setSelection(emptySet())
                                }
                            ),
                            ActionItem(
                                text = stringResource(R.string.disable_selection),
                                onClick = {
                                    viewModel.disableSelectionByIds(selectedRuleIds)
                                    viewModel.setSelection(emptySet())
                                }
                            ),
                            ActionItem(
                                text = stringResource(R.string.to_top),
                                onClick = {
                                    viewModel.topSelectByIds(selectedRuleIds)
                                    viewModel.setSelection(emptySet())
                                }
                            ),
                            ActionItem(
                                text = stringResource(R.string.to_bottom),
                                onClick = {
                                    viewModel.bottomSelectByIds(selectedRuleIds)
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
}
