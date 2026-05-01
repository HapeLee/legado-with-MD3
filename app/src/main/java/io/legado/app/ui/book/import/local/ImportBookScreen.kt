package io.legado.app.ui.book.import.local

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBookContent(
    state: ImportBookUiState,
    onBackClick: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolder: () -> Unit,
    onScanFolder: () -> Unit,
    onImportFileName: () -> Unit,
    onSortChange: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectInvert: () -> Unit,
    onAddToBookshelf: () -> Unit,
    onItemAddToBookshelf: (ImportBook) -> Unit,
    onDeleteSelection: () -> Unit,
    onItemClick: (ImportBook) -> Unit
) {
    ListScaffold(
        title = stringResource(R.string.local_book),
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = onSearchToggle,
        onSearchQueryChange = onSearchQueryChange,
        searchPlaceholder = stringResource(R.string.screen),
        topBarActions = {
            TopBarActionButton(
                onClick = onSelectFolder,
                imageVector = Icons.Default.FolderOpen,
                contentDescription = stringResource(R.string.select_folder)
            )
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_by_name),
                onClick = {
                    onSortChange(0)
                    dismiss()
                },
                trailingIcon = {
                    if (state.sort == 0) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_by_size),
                onClick = {
                    onSortChange(1)
                    dismiss()
                },
                trailingIcon = {
                    if (state.sort == 1) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.sort_by_time),
                onClick = {
                    onSortChange(2)
                    dismiss()
                },
                trailingIcon = {
                    if (state.sort == 2) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.scan_folder),
                onClick = {
                    onScanFolder()
                    dismiss()
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.import_file_name),
                onClick = {
                    onImportFileName()
                    dismiss()
                }
            )
        },
        bottomContent = {
            ImportPathNavigationBar(
                pathNames = state.pathNames,
                canGoBack = state.canGoBack,
                onNavigateBack = onNavigateBack,
                onNavigateToLevel = onNavigateToLevel
            )
        },
        selectionActions = SelectionActions(
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll,
            onSelectInvert = onSelectInvert,
            primaryAction = ActionItem(
                text = stringResource(R.string.add_to_bookshelf),
                icon = { Icon(Icons.Default.CloudDownload, null) },
                onClick = onAddToBookshelf
            ),
            secondaryActions = listOf(
                ActionItem(
                    text = stringResource(R.string.delete),
                    icon = { Icon(Icons.Default.Delete, null) },
                    onClick = onDeleteSelection
                )
            )
        ),
        onAddClick = null
    ) { paddingValues ->
        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isRefreshing = state.isLoading,
            state = refreshState,
            onRefresh = onScanFolder
        ) {
            when {
                state.items.isEmpty() && state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.items.isEmpty() -> {
                    EmptyMessage(
                        modifier = Modifier.fillMaxSize(),
                        message = stringResource(R.string.empty_msg_import_book)
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.selectionId }) { item ->
                            ImportBookItem(
                                modifier = Modifier.animateItem(),
                                item = item,
                                isSelected = item.selectionId in state.selectedIds,
                                onClick = { onItemClick(item) },
                                onAddToBookshelf = { onItemAddToBookshelf(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportBookScreen(
    onBackClick: () -> Unit,
    viewModel: ImportBookViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    var showImportFileNameDialog by remember { mutableStateOf(false) }
    var pendingSingleAddBook by remember { mutableStateOf<ImportBook?>(null) }
    var fileNameJs by remember { mutableStateOf(ImportBookConfig.bookImportFileName.orEmpty()) }
    var pickerTarget by remember { mutableStateOf(ImportFolderPickTarget.IMPORT_FOLDER) }
    val selectDocTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.dispatch(ImportBookIntent.FolderPicked(uri, pickerTarget))
    }
    val handleBack = {
        when {
            uiState.selectedIds.isNotEmpty() -> viewModel.clearSelection()
            uiState.canGoBack -> viewModel.dispatch(ImportBookIntent.NavigateBack)
            else -> onBackClick()
        }
    }

    BackHandler(enabled = !showFolderPicker && !showImportFileNameDialog) {
        handleBack()
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatch(ImportBookIntent.Initialize)
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportBookEffect.RequestFolderPicker -> {
                    pickerTarget = effect.target
                    selectDocTree.launch(effect.initialUri)
                }

                is ImportBookEffect.OpenBook -> {
                    context.startActivityForBook(effect.book)
                }

                is ImportBookEffect.ShowArchiveEntries -> {
                    context.selector(R.string.start_read, effect.fileNames) { _, name, _ ->
                        viewModel.dispatch(
                            ImportBookIntent.ArchiveEntrySelected(effect.fileDoc, name)
                        )
                    }
                }

                is ImportBookEffect.ShowImportArchiveDialog -> {
                    context.alert(R.string.draw, R.string.no_book_found_bookshelf) {
                        okButton {
                            viewModel.dispatch(
                                ImportBookIntent.ImportArchiveConfirmed(
                                    effect.fileDoc,
                                    effect.fileName
                                )
                            )
                        }
                        noButton()
                    }
                }

                is ImportBookEffect.ShowToastRes -> {
                    context.toastOnUi(effect.resId)
                }
            }
        }
    }

    FilePickerSheet(
        show = showFolderPicker,
        onDismissRequest = { showFolderPicker = false },
        title = stringResource(R.string.select_folder),
        onSelectSysDir = {
            showFolderPicker = false
            viewModel.dispatch(ImportBookIntent.SelectFolderClick)
        }
    )

    AppAlertDialog(
        show = showImportFileNameDialog,
        onDismissRequest = { showImportFileNameDialog = false },
        title = stringResource(R.string.import_file_name),
        content = {
            AppText("Use js to parse file name from src, then assign name/author.")
            OutlinedTextField(
                value = fileNameJs,
                onValueChange = { fileNameJs = it },
                label = { AppText("js") }
            )
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            ImportBookConfig.bookImportFileName = fileNameJs
            showImportFileNameDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showImportFileNameDialog = false }
    )

    pendingSingleAddBook?.let { book ->
        AppAlertDialog(
            show = true,
            onDismissRequest = { pendingSingleAddBook = null },
            title = stringResource(R.string.add_to_bookshelf),
            text = stringResource(R.string.check_add_bookshelf, book.name),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                viewModel.dispatch(ImportBookIntent.AddSingleToBookshelf(book))
                pendingSingleAddBook = null
            },
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { pendingSingleAddBook = null }
        )
    }

    ImportBookContent(
        state = uiState,
        onBackClick = handleBack,
        onSearchToggle = { viewModel.dispatch(ImportBookIntent.SearchToggle(it)) },
        onSearchQueryChange = { viewModel.dispatch(ImportBookIntent.SearchQueryChange(it)) },
        onSelectFolder = { showFolderPicker = true },
        onScanFolder = { viewModel.dispatch(ImportBookIntent.ScanFolder) },
        onImportFileName = {
            fileNameJs = ImportBookConfig.bookImportFileName.orEmpty()
            showImportFileNameDialog = true
        },
        onSortChange = { viewModel.dispatch(ImportBookIntent.SortChange(it)) },
        onNavigateBack = { viewModel.dispatch(ImportBookIntent.NavigateBack) },
        onNavigateToLevel = { viewModel.dispatch(ImportBookIntent.NavigateToLevel(it)) },
        onSelectAll = { viewModel.dispatch(ImportBookIntent.SelectAll) },
        onClearSelection = { viewModel.clearSelection() },
        onSelectInvert = { viewModel.dispatch(ImportBookIntent.SelectInvert) },
        onAddToBookshelf = { viewModel.dispatch(ImportBookIntent.AddToBookshelf) },
        onItemAddToBookshelf = { pendingSingleAddBook = it },
        onDeleteSelection = { viewModel.dispatch(ImportBookIntent.DeleteSelection) },
        onItemClick = { viewModel.dispatch(ImportBookIntent.ItemClick(it)) }
    )
}

@Composable
private fun ImportPathNavigationBar(
    pathNames: List<String>,
    canGoBack: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassCard(
            modifier = Modifier.weight(1f),
            containerColor = LegadoTheme.colorScheme.surfaceContainer
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(pathNames) { index, name ->
                    val isLast = index == pathNames.lastIndex
                    AppText(
                        text = name,
                        style = LegadoTheme.typography.labelSmall,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLast) {
                            LegadoTheme.colorScheme.primary
                        } else {
                            LegadoTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .then(if (!isLast) Modifier.clickable { onNavigateToLevel(index) } else Modifier)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (canGoBack) {
            SmallTonalIconButton(
                onClick = onNavigateBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "back"
            )
        }
    }
}

@Composable
private fun ImportBookItem(
    modifier: Modifier,
    item: ImportBook,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAddToBookshelf: () -> Unit
) {
    val containerColor = if (isSelected) {
        LegadoTheme.colorScheme.secondaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainer
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        containerColor = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = when {
                    item.isDir -> Icons.Default.Folder
                    item.isOnBookShelf -> Icons.Outlined.Book
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.isOnBookShelf) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = item.name,
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!item.isDir) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextCard(
                            text = item.name.substringAfterLast('.', "").uppercase(),
                            textStyle = LegadoTheme.typography.labelSmall,
                            horizontalPadding = 4.dp,
                            verticalPadding = 2.dp,
                            cornerRadius = 4.dp,
                            icon = null,
                            backgroundColor = LegadoTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AppText(
                            text = "${ConvertUtils.formatFileSize(item.size)} - ${AppConst.dateFormat.format(item.lastModified)}",
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!item.isDir && !item.isOnBookShelf) {
                Spacer(modifier = Modifier.width(8.dp))
                SmallIconButton(
                    onClick = onAddToBookshelf,
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.AddCircleOutline
                )
            }
        }
    }
}
