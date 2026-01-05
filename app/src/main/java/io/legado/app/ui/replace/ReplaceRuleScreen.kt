package io.legado.app.ui.replace

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AnimatedText
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SearchBarSection
import io.legado.app.ui.widget.components.SelectionBottomBar
import io.legado.app.ui.widget.components.exportComponents.FilePickerSheet
import io.legado.app.ui.widget.components.exportComponents.FilePickerSheetMode
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.BatchImportDialog
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReplaceRuleScreen(
    viewModel: ReplaceRuleViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {

    val uploadRepository: UploadRepository = koinInject()

    //TODO: 期望换为Navigation
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
                MediumFlexibleTopAppBar(
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
                FloatingActionButton(
                    modifier = Modifier.animateFloatingActionButton(
                        visible = !inSelectionMode,
                        alignment = Alignment.BottomEnd,
                    ),
                    onClick = {
                        context.startActivity(ReplaceEditActivity.startIntent(context))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
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
                        val isSelected = selectedRuleIds.contains(ui.id)
                        ReorderableItem(
                            state = reorderableState,
                            key = ui.id
                        ) { isDragging ->

                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                            ReplaceRuleItem(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .shadow(
                                        elevation = elevation,
                                        shape = MaterialTheme.shapes.medium,
                                        clip = false
                                    )
                                    .then(
                                        if (canReorder) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate
                                                    )
                                                },
                                                onDragStopped = {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.GestureEnd
                                                    )
                                                },
                                                interactionSource = remember { MutableInteractionSource() }
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .animateItem(),
                                name = ui.name,
                                isEnabled = ui.isEnabled,
                                isSelected = isSelected,
                                inSelectionMode = inSelectionMode,
                                onEnabledChange = { enabled ->
                                    viewModel.update(ui.rule.copy(isEnabled = enabled))
                                },
                                onDelete = { showDeleteRuleDialog = ui.rule },
                                onToTop = { viewModel.toTop(ui.rule) },
                                onToBottom = { viewModel.toBottom(ui.rule) },
                                onToggleSelection = {
                                    viewModel.toggleSelection(ui.id)
                                },
                                onClickEdit = {
                                    context.startActivity(
                                        ReplaceEditActivity.startIntent(context, ui.id)
                                    )
                                }
                            )
                        }
                    }
                }
                if (inSelectionMode) {
                    DraggableSelectionHandler(
                        listState = listState,
                        rules = rules,
                        selectedRuleIds = selectedRuleIds,
                        onSelectionChange = viewModel::setSelection,
                        haptic = haptic,
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

@Composable
fun DraggableSelectionHandler(
    listState: LazyListState,
    rules: List<ReplaceRuleItemUi>,
    selectedRuleIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier
) {
    val latestSelectedRuleIds by rememberUpdatedState(selectedRuleIds)
    var isAddingMode by remember { mutableStateOf(true) }
    var lastProcessedIndex by remember { mutableIntStateOf(-1) }

    fun findRuleAtOffset(offsetY: Float): Pair<Int, ReplaceRuleItemUi>? {
        val itemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offsetY >= item.offset && offsetY <= item.offset + item.size
            }

        return itemInfo?.let { info ->
            rules.getOrNull(info.index)?.let { rule ->
                info.index to rule
            }
        }
    }

    fun applySelection(id: Long, add: Boolean) {
        val current = latestSelectedRuleIds
        onSelectionChange(
            if (add) current + id else current - id
        )
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            coroutineScope {
                launch {
                    detectTapGestures(
                        onTap = { offset ->
                            val result = findRuleAtOffset(offset.y)
                            if (result != null) {
                                val (_, rule) = result
                                val id = rule.id
                                val current = latestSelectedRuleIds
                                onSelectionChange(
                                    if (current.contains(id)) current - id
                                    else current + id
                                )
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove
                                )
                            }
                        }
                    )
                }

                // 拖拽多选
                launch {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val result = findRuleAtOffset(offset.y)
                            if (result != null) {
                                val (index, rule) = result
                                lastProcessedIndex = index

                                val id = rule.id
                                val current = latestSelectedRuleIds
                                isAddingMode = !current.contains(id)
                                applySelection(id, isAddingMode)

                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            val result = findRuleAtOffset(change.position.y)
                            if (result != null) {
                                val (index, rule) = result
                                if (index != lastProcessedIndex) {
                                    lastProcessedIndex = index
                                    applySelection(rule.id, isAddingMode)
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            lastProcessedIndex = -1
                        },
                        onDragCancel = {
                            lastProcessedIndex = -1
                        }
                    )
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupManageBottomSheet(
    groups: List<String>,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    viewModel: ReplaceRuleViewModel
) {
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var updatedGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.group_manage),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { group ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (editingGroup == group) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedTextField(
                                    value = updatedGroupName,
                                    onValueChange = { updatedGroupName = it },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    viewModel.upGroup(group, updatedGroupName)
                                    editingGroup = null
                                }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(id = R.string.ok)
                                    )
                                }
                            }
                        } else {
                            ListItem(
                                headlineContent = { Text(group) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            editingGroup = group
                                            updatedGroupName = group
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(id = R.string.edit)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.delGroup(group) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.delete)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReplaceRuleItem(
    name: String,
    isEnabled: Boolean,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onToTop: () -> Unit,
    onToBottom: () -> Unit,
    onClickEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRuleMenu by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "CardColor"
    )

    Card(
        onClick = { onToggleSelection() },
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        ListItem(
            modifier = Modifier
                .animateContentSize(),
            headlineContent = {
                AnimatedContent(targetState = name, label = "RuleNameAnimation") { targetName ->
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                AnimatedContent(
                    targetState = inSelectionMode,
                    label = "LeadingCheckbox"
                ) { visible ->
                    if (visible) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null
                        )
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onEnabledChange
                    )
                    IconButton(onClick = onClickEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    Box {
                        IconButton(onClick = { showRuleMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
                        }
                        DropdownMenu(
                            expanded = showRuleMenu,
                            onDismissRequest = { showRuleMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("移至顶部") },
                                onClick = {
                                    onToTop()
                                    showRuleMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("移至底部") },
                                onClick = {
                                    onToBottom()
                                    showRuleMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    onDelete()
                                    showRuleMenu = false
                                }
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
