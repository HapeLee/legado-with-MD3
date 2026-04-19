package io.legado.app.ui.book.info

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.group.GroupEditSheet
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
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
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    GlassCard(onClick = { onSelect(file) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
    currentGroupId: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val groups by appDb.bookGroupDao.flowSelect().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedGroupId by remember(currentGroupId) { mutableLongStateOf(currentGroupId) }
    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.group_select),
        endAction = { IconButton(onClick = { editingGroup = BookGroup() }) { Icon(Icons.Default.Add, null) } }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    val isSelected = selectedGroupId and group.groupId > 0
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
            ConfirmDismissButtonsRow(
                onDismiss = onDismissRequest,
                onConfirm = { onConfirm(selectedGroupId) },
                dismissText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.ok),
            )
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
    val items by viewModel.dataFlow.collectAsStateWithLifecycle(initialValue = emptyList())
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
            IconButton(onClick = { viewModel.startOrStopSearch() }) {
                Icon(if (isSearching) Icons.Default.MoreVert else Icons.Default.Refresh, null)
            }
        }
    ) {
        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.bookUrl + it.originName }) { item ->
                GlassCard(onClick = { onSelect(item.coverUrl.orEmpty()) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoilBookCover(name = item.name, author = item.author, path = item.coverUrl, sourceOrigin = item.origin, modifier = Modifier.fillMaxWidth())
                        AppText(text = item.originName, style = LegadoTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ChangeSourceSheet(
    show: Boolean,
    oldBook: Book,
    onDismissRequest: () -> Unit,
    onReplace: (BookSource, Book, List<BookChapter>) -> Unit,
    onAddAsNew: (Book, List<BookChapter>) -> Unit,
    viewModel: ChangeBookSourceComposeViewModel = koinViewModel(key = "source-${oldBook.bookUrl}"),
) {
    val context = LocalContext.current
    val items by viewModel.searchDataFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val progress by viewModel.changeSourceProgress.collectAsStateWithLifecycle()
    val groups by appDb.bookSourceDao.flowEnabledGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedGroup = viewModel.searchGroup
    val checkAuthor = viewModel.checkAuthor
    val loadInfo = viewModel.loadInfo
    val loadToc = viewModel.loadToc
    val loadWordCount = viewModel.loadWordCount
    var actionBook by remember { mutableStateOf<SearchBook?>(null) }
    var mismatchBook by remember { mutableStateOf<SearchBook?>(null) }
    var loadingAction by remember { mutableStateOf(false) }
    var showOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showFilterMenu by rememberSaveable { mutableStateOf(false) }

    val editSourceResult = rememberLauncherForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
        val origin = it.data?.getStringExtra("origin") ?: return@rememberLauncherForActivityResult
        viewModel.startSearch(origin)
    }

    LaunchedEffect(oldBook.bookUrl) {
        viewModel.initData(oldBook.name, oldBook.author, oldBook, false)
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
                            text = "字数对比",
                            isSelected = loadWordCount,
                            onClick = {
                                viewModel.onLoadWordCountChange(!loadWordCount)
                                dismiss()
                            }
                        )
                    }
                }
                MediumIconButton(
                    onClick = { context.startActivity<BookSourceActivity>() },
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
                Box {
                    MediumIconButton(
                        onClick = { showFilterMenu = true },
                        imageVector = Icons.Default.FilterList
                    )
                    RoundDropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) { dismiss ->
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.all_source),
                            isSelected = selectedGroup.isBlank(),
                            onClick = {
                                viewModel.onSearchGroupSelected("")
                                dismiss()
                            }
                        )
                        groups.forEach { group ->
                            RoundDropdownMenuItem(
                                text = group,
                                isSelected = selectedGroup == group,
                                onClick = {
                                    viewModel.onSearchGroupSelected(group)
                                    dismiss()
                                }
                            )
                        }
                    }
                }
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
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            if (!item.sameBookTypeLocal(oldBook.type)) mismatchBook = item else actionBook = item
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
                                        onReplace(source, book, toc)
                                    }
                                }
                                onDismiss()
                            }
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    val performAction: (SearchBook, Boolean) -> Unit = { searchBook, replace ->
        loadingAction = true
        val book = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        viewModel.getToc(book, { toc, source ->
            loadingAction = false
            if (replace) {
                onReplace(source, book, toc)
                onDismissRequest()
            } else {
                onAddAsNew(book, toc)
                context.toastOnUi(context.getString(R.string.book_added_to_shelf))
            }
            actionBook = null
        }, {
            loadingAction = false
            context.toastOnUi(if (replace) "换源失败" else "添加书籍失败")
        })
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
                CircularProgressIndicator()
            }
        }
    )
}
