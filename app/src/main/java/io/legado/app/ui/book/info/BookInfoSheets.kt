package io.legado.app.ui.book.info

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeSourceConfig
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.search.ScopeSelectSheet
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun WebFileSheet(
    show: Boolean,
    files: List<BookInfoWebFile>,
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (BookInfoWebFile) -> Unit,
) {
    AppModalBottomSheet(show = show, onDismissRequest = onDismissRequest, title = title) {
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    GlassCard(onClick = { onSelect(file) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (file.name.endsWith("zip") || file.name.endsWith("rar") || file.name.endsWith("7z")) Icons.Outlined.FolderZip else Icons.Outlined.Image, null)
                            Text(text = file.name, modifier = Modifier.weight(1f), style = LegadoTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GroupSelectSheet(
    show: Boolean,
    groups: List<BookGroup>,
    currentGroupId: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selectedGroupId by remember(currentGroupId, show) { mutableLongStateOf(currentGroupId) }
    var editingGroup by remember(show) { mutableStateOf<BookGroup?>(null) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.group_select),
        startAction = {
            MediumIconButton(
                onClick = { editingGroup = BookGroup() },
                imageVector = Icons.Default.Add
            )
        },
        endAction = {
            MediumIconButton(
                onClick = { onConfirm(selectedGroupId) },
                imageVector = Icons.Default.Check
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    val isSelected = (selectedGroupId and group.groupId) != 0L
                    SelectionItemCard(
                        title = group.groupName,
                        isSelected = isSelected,
                        onToggleSelection = {
                            selectedGroupId = if (isSelected) {
                                selectedGroupId - group.groupId
                            } else {
                                selectedGroupId + group.groupId
                            }
                        },
                        leadingContent = {
                            AppCheckbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selectedGroupId = if (it) {
                                        selectedGroupId + group.groupId
                                    } else {
                                        selectedGroupId - group.groupId
                                    }
                                }
                            )
                        },
                        trailingAction = {
                            SmallIconButton(
                                onClick = { editingGroup = group },
                                imageVector = Icons.Default.Edit
                            )
                        },
                        containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    GroupEditSheet(show = editingGroup != null, group = editingGroup, onDismissRequest = { editingGroup = null })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChangeCoverSheet(
    show: Boolean,
    name: String,
    author: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: ChangeCoverViewModel = koinViewModel(key = "cover-$name-$author"),
) {
    val items by viewModel.dataFlow.collectAsStateWithLifecycle(initialValue = emptyList<SearchBook>())
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    LaunchedEffect(name, author) {
        viewModel.initData(name, author)
    }
    DisposableEffect(show) {
        onDispose {
            viewModel.stopSearch()
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_cover_source),
        endAction = {
            MediumIconButton(
                onClick = { viewModel.startOrStopSearch() },
                imageVector = if (isSearching) Icons.Default.MoreVert else Icons.Default.Refresh
            )
        }
    ) {
        if (isSearching) {
            AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.bookUrl + it.originName }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onSelect(item.coverUrl.orEmpty())
                        }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoilBookCover(
                        name = item.name,
                        author = item.author,
                        path = item.coverUrl,
                        sourceOrigin = item.origin,
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(5f / 7f),
                    )
                    AppText(
                        text = item.originName,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        maxLines = 2
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ChangeSourceSheet(
    data: Book?,
    onDismissRequest: () -> Unit,
    onReplace: (BookSource, Book, List<BookChapter>, ChangeSourceMigrationOptions) -> Unit,
    onAddAsNew: (Book, List<BookChapter>) -> Unit,
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    if (currentData != null) {
        ChangeSourceSheet(
            show = data != null,
            oldBook = currentData,
            onDismissRequest = onDismissRequest,
            onReplace = onReplace,
            onAddAsNew = onAddAsNew,
        )
    }
}

@Composable
fun ChangeSourceSheet(
    show: Boolean,
    oldBook: Book,
    onDismissRequest: () -> Unit,
    onReplace: (BookSource, Book, List<BookChapter>, ChangeSourceMigrationOptions) -> Unit,
    onAddAsNew: (Book, List<BookChapter>) -> Unit,
    viewModel: ChangeBookSourceComposeViewModel = koinViewModel(key = "source-${oldBook.bookUrl}"),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val items by viewModel.searchDataFlow.collectAsStateWithLifecycle(initialValue = emptyList<SearchBook>())
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val progress by viewModel.changeSourceProgress.collectAsStateWithLifecycle()
    val groups by viewModel.enabledGroups.collectAsStateWithLifecycle(initialValue = emptyList<String>())
    val enabledSources by viewModel.enabledSources.collectAsStateWithLifecycle(initialValue = emptyList<io.legado.app.data.entities.BookSourcePart>())
    val scopeState by viewModel.scopeUiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val checkAuthor = viewModel.checkAuthor
    val loadInfo = viewModel.loadInfo
    val loadToc = viewModel.loadToc
    val loadWordCount = viewModel.loadWordCount
    var actionBook by remember { mutableStateOf<SearchBook?>(null) }
    var mismatchBook by remember { mutableStateOf<SearchBook?>(null) }
    var showMigrationOptions by remember { mutableStateOf(false) }
    var loadingAction by remember { mutableStateOf(false) }
    var showOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val bookAddedToShelfText = stringResource(R.string.book_added_to_shelf)

    val editSourceResult = rememberLauncherForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
        val origin = it.data?.getStringExtra("origin") ?: return@rememberLauncherForActivityResult
        viewModel.startSearch(origin)
    }

    LaunchedEffect(oldBook.bookUrl) {
        viewModel.initData(oldBook.name, oldBook.author, oldBook, false)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.resume()
                Lifecycle.Event.ON_PAUSE -> viewModel.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(oldBook.bookUrl) {
        onDispose {
            viewModel.stopSearch()
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.book_source),
        startAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    MediumIconButton(
                        onClick = { showOptionsMenu = true },
                        imageVector = Icons.Default.MoreVert
                    )
                    RoundDropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) { dismiss ->
                        RoundDropdownMenuItem(
                            text = "校验作者",
                            isSelected = checkAuthor,
                            onClick = {
                                viewModel.onCheckAuthorChange(!checkAuthor)
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "加载详情",
                            isSelected = loadInfo,
                            onClick = {
                                viewModel.onLoadInfoChange(!loadInfo)
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "加载目录",
                            isSelected = loadToc,
                            onClick = {
                                viewModel.onLoadTocChange(!loadToc)
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "显示更多信息",
                            isSelected = loadWordCount,
                            onClick = {
                                viewModel.onLoadWordCountChange(!loadWordCount)
                                dismiss()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.book_source_manage),
                            onClick = {
                                context.startActivity<BookSourceActivity>()
                                dismiss()
                            }
                        )
                    }
                }
                MediumIconButton(
                    onClick = { showMigrationOptions = true },
                    imageVector = Icons.Outlined.Settings
                )
            }
        },
        endAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MediumIconButton(
                    onClick = { viewModel.startOrStopSearch() },
                    imageVector = if (isSearching) Icons.Default.PauseCircleOutline else Icons.Default.Refresh,
                )
                MediumIconButton(
                    onClick = { showFilterSheet = true },
                    imageVector = Icons.Default.FilterList
                )
            }
        }
    ) {
        AppTextField(
            value = searchQuery,
            backgroundColor = LegadoTheme.colorScheme.surface,
            onValueChange = {
                searchQuery = it
                viewModel.screen(it)
            },
            label = stringResource(R.string.screen),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (isSearching) {
            AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = "${progress.first} / ${viewModel.totalSourceCount} · ${items.size}",
                style = LegadoTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyMessage(
                    message = stringResource(R.string.search_empty)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.bookUrl + it.origin }) { item ->
                    val bookScore by remember(item.origin, item.name, item.author) {
                        viewModel.bookScoreFlow(item)
                    }.collectAsStateWithLifecycle()
                    SelectionItemCard(
                        title = item.originName,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        selectedContainerColor = LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                        leadingContent = {
                            MediumIconButton(
                                onClick = {
                                    viewModel.onBookScoreClick(item)
                                },
                                imageVector = Icons.Default.PushPin,
                                tint = if (bookScore > 0) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.outline,
                                contentDescription = null
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(
                                    text = item.author,
                                    style = LegadoTheme.typography.labelLargeEmphasized
                                )
                                AppText(
                                    text = item.getDisplayLastChapterTitle(),
                                    style = LegadoTheme.typography.labelMediumEmphasized
                                )
                                item.chapterWordCountText?.takeIf { loadWordCount }?.let {
                                    AppText(
                                        text = it,
                                        style = LegadoTheme.typography.labelSmallEmphasized,
                                        color = LegadoTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        isSelected = item.bookUrl == oldBook.bookUrl,
                        onToggleSelection = {
                            if (item.bookUrl != oldBook.bookUrl) {
                                if (!item.sameBookTypeLocal(oldBook.type)) mismatchBook = item else actionBook =
                                    item
                            }
                        },
                        dropdownContent = { onDismiss: () -> Unit ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.to_top),
                                onClick = {
                                    viewModel.topSource(item)
                                    onDismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = "置底",
                                onClick = {
                                    viewModel.bottomSource(item)
                                    onDismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.edit),
                                onClick = {
                                    onDismiss()
                                    editSourceResult.launch { putExtra("sourceUrl", item.origin) }
                                }
                            )
                            RoundDropdownMenuItem(
                                text = "禁用",
                                onClick = {
                                    viewModel.disableSource(item)
                                    onDismiss()
                                }
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.delete),
                                color = LegadoTheme.colorScheme.error,
                                onClick = {
                                    viewModel.del(item)
                                    if (oldBook.bookUrl == item.bookUrl) {
                                        viewModel.autoChangeSource(oldBook.type) { book, toc, source ->
                                            onReplace(
                                                source,
                                                book,
                                                toc,
                                                ChangeSourceConfig.getMigrationOptions()
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    val performAction: (SearchBook, Boolean) -> Unit = { searchBook, replace ->
        loadingAction = true
        val book = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        viewModel.getToc(
            book,
            onSuccess = { toc, source ->
                loadingAction = false
                if (replace) {
                    onReplace(source, book, toc, ChangeSourceConfig.getMigrationOptions())
                    onDismissRequest()
                } else {
                    onAddAsNew(book, toc)
                    context.toastOnUi(bookAddedToShelfText)
                }
                actionBook = null
            },
            onError = {
                loadingAction = false
                context.toastOnUi(if (replace) "换源失败" else "添加书籍失败")
            }
        )
    }

    AppAlertDialog(
        data = mismatchBook,
        onDismissRequest = { mismatchBook = null },
        title = stringResource(R.string.book_type_different),
        text = stringResource(R.string.soure_change_source),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { searchBook ->
            actionBook = searchBook
            mismatchBook = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { mismatchBook = null }
    )
    AppAlertDialog(
        data = actionBook,
        onDismissRequest = { actionBook = null },
        title = stringResource(R.string.change_source_option_title),
        dismissText = stringResource(R.string.add_as_new_book),
        onDismiss = { actionBook?.let { performAction(it, false) } },
        confirmText = stringResource(R.string.replace_current_book),
        onConfirm = { performAction(it, true) }
    )
    AppAlertDialog(
        show = loadingAction,
        onDismissRequest = {},
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AppCircularProgressIndicator()
            }
        }
    )
    ChangeSourceMigrationOptionsSheet(
        show = showMigrationOptions,
        title = "换源选项",
        onDismissRequest = { showMigrationOptions = false },
        onConfirm = { options ->
            ChangeSourceConfig.setMigrationOptions(options)
            showMigrationOptions = false
        }
    )

    ScopeSelectSheet(
        show = showFilterSheet,
        onDismissRequest = { showFilterSheet = false },
        isAll = scopeState.isAll,
        onSelectAll = { viewModel.selectAllScope() },
        groups = groups,
        selectedGroups = scopeState.displayNames,
        onToggleGroup = { viewModel.toggleScopeGroup(it) },
        sources = enabledSources,
        selectedSources = scopeState.sourceUrls,
        onToggleSource = { viewModel.toggleScopeSource(it) },
        isSourceScope = scopeState.isSource,
        onConfirm = {
            viewModel.startSearch()
            showFilterSheet = false
        }
    )
}

