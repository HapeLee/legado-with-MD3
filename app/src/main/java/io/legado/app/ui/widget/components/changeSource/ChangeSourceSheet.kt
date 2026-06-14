package io.legado.app.ui.widget.components.changeSource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeSourceConfig
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.book.search.ScopeSelectSheet
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
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
fun ChangeSourceSheet(
    show: Boolean,
    oldBook: Book,
    fromReadBookActivity: Boolean = false,
    allowAddAsNew: Boolean = true,
    dismissOnReplaceStart: Boolean = false,
    onDismissRequest: () -> Unit,
    onReplace: (BookSource, Book, List<BookChapter>, ChangeSourceMigrationOptions) -> Unit,
    onReplaceBook: ((Book) -> Unit)? = null,
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

    LaunchedEffect(oldBook.bookUrl, fromReadBookActivity) {
        viewModel.initData(oldBook.name, oldBook.author, oldBook, fromReadBookActivity)
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

    val performAction = fun(searchBook: SearchBook, replace: Boolean) {
        val book = viewModel.getBookFromMap(searchBook.primaryStr()) ?: searchBook.toBook()
        if (replace && dismissOnReplaceStart && onReplaceBook != null) {
            onDismissRequest()
            onReplaceBook(book)
            actionBook = null
            return
        }
        val dismissBeforeLoading = replace && dismissOnReplaceStart
        if (dismissBeforeLoading) {
            onDismissRequest()
        } else {
            loadingAction = true
        }
        viewModel.getToc(
            book,
            onSuccess = { toc, source ->
                loadingAction = false
                if (replace) {
                    onReplace(source, book, toc, ChangeSourceConfig.getMigrationOptions())
                    if (!dismissBeforeLoading) {
                        onDismissRequest()
                    }
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

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.book_source),
        startAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    MediumPlainButton(
                        onClick = { showOptionsMenu = true },
                        icon = Icons.Default.MoreVert
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
                MediumPlainButton(
                    onClick = { showMigrationOptions = true },
                    icon = Icons.Outlined.Settings
                )
            }
        },
        endAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MediumPlainButton(
                    onClick = { viewModel.startOrStopSearch() },
                    icon = if (isSearching) Icons.Default.PauseCircleOutline else Icons.Default.Refresh,
                )
                MediumPlainButton(
                    onClick = { showFilterSheet = true },
                    icon = Icons.Default.FilterList
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
                            MediumPlainButton(
                                onClick = {
                                    viewModel.onBookScoreClick(item)
                                },
                                icon = Icons.Default.PushPin,
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
                                if (!item.sameBookTypeLocal(oldBook.type)) {
                                    mismatchBook = item
                                } else if (allowAddAsNew) {
                                    actionBook = item
                                } else {
                                    performAction(item, true)
                                }
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

    AppAlertDialog(
        data = mismatchBook,
        onDismissRequest = { mismatchBook = null },
        title = stringResource(R.string.book_type_different),
        text = stringResource(R.string.soure_change_source),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { searchBook ->
            mismatchBook = null
            if (allowAddAsNew) {
                actionBook = searchBook
            } else {
                performAction(searchBook, true)
            }
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { mismatchBook = null }
    )
    if (allowAddAsNew) {
        AppAlertDialog(
            data = actionBook,
            onDismissRequest = { actionBook = null },
            title = stringResource(R.string.change_source_option_title),
            dismissText = stringResource(R.string.add_as_new_book),
            onDismiss = { actionBook?.let { performAction(it, false) } },
            confirmText = stringResource(R.string.replace_current_book),
            onConfirm = { performAction(it, true) }
        )
    }
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
        onApplyScope = { selection ->
            viewModel.applyScopeSelection(selection)
            showFilterSheet = false
        }
    )
}
