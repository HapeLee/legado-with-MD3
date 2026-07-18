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
import androidx.compose.runtime.Stable
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
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceEffect
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
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Stable
private data class PendingShelfConflict(
    val existingBook: Book,
    val source: BookSource,
    val newBook: Book,
    val toc: List<BookChapter>,
)

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
    onReplaceConflict: ((
        Book,
        BookSource,
        Book,
        List<BookChapter>,
        ChangeSourceMigrationOptions,
    ) -> Unit)? = null,
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
    val emptyScopeName by viewModel.emptyScopeName.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val checkAuthor = settings.checkAuthor
    val loadInfo = settings.loadInfo
    val loadToc = settings.loadToc
    val loadWordCount = settings.loadWordCount
    var actionBook by remember { mutableStateOf<SearchBook?>(null) }
    var mismatchBook by remember { mutableStateOf<SearchBook?>(null) }
    var shelfConflict by remember { mutableStateOf<PendingShelfConflict?>(null) }
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

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ChangeBookSourceEffect.ShowMessage -> context.toastOnUi(effect.message)
                is ChangeBookSourceEffect.ShowMessageResource ->
                    context.toastOnUi(context.getString(effect.messageRes))
            }
        }
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
                if (replace) {
                    loadingAction = false
                    onReplace(source, book, toc, settings.migrationOptions())
                    if (!dismissBeforeLoading) {
                        onDismissRequest()
                    }
                } else if (onReplaceConflict != null) {
                    viewModel.findShelfConflict(book) { existingBook ->
                        loadingAction = false
                        if (existingBook == null) {
                            onAddAsNew(book, toc)
                            context.toastOnUi(bookAddedToShelfText)
                        } else {
                            shelfConflict = PendingShelfConflict(
                                existingBook = existingBook,
                                source = source,
                                newBook = book,
                                toc = toc,
                            )
                        }
                        actionBook = null
                    }
                } else {
                    loadingAction = false
                    onAddAsNew(book, toc)
                    context.toastOnUi(bookAddedToShelfText)
                    actionBook = null
                }
            },
            onError = {
                loadingAction = false
                AppLog.put("${if (replace) "换源" else "添加书籍"}获取目录出错\n${it.localizedMessage}", it, true)
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
                icon = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_menu)
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
                    icon = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.setting)
                )
            }
        },
        endAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MediumPlainButton(
                    onClick = { viewModel.startOrStopSearch() },
                    icon = if (isSearching) Icons.Default.PauseCircleOutline else Icons.Default.Refresh,
                    contentDescription = stringResource(if (isSearching) R.string.pause else R.string.refresh),
                )
                MediumPlainButton(
                    onClick = { showFilterSheet = true },
                    icon = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.screen)
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
                                contentDescription = stringResource(R.string.a11y_pin_source)
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
                                                settings.migrationOptions()
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
        data = shelfConflict,
        onDismissRequest = { shelfConflict = null },
        title = stringResource(R.string.bookshelf_book_conflict_title),
        text = stringResource(R.string.bookshelf_book_conflict_message),
        confirmText = stringResource(R.string.replace_current_book),
        onConfirm = { conflict ->
            shelfConflict = null
            onReplaceConflict?.invoke(
                conflict.existingBook,
                conflict.source,
                conflict.newBook,
                conflict.toc,
                settings.migrationOptions(),
            )
            onDismissRequest()
        },
        dismissText = stringResource(R.string.add_as_new_book),
        onDismiss = {
            shelfConflict?.let { conflict ->
                onAddAsNew(conflict.newBook, conflict.toc)
                context.toastOnUi(bookAddedToShelfText)
            }
            shelfConflict = null
        },
        content = { conflict ->
            val existingSource = conflict.existingBook.originName.ifBlank {
                conflict.existingBook.origin
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(
                    text = "${stringResource(R.string.book_name)}：${conflict.existingBook.name}",
                    style = LegadoTheme.typography.bodyMedium,
                )
                AppText(
                    text = "${stringResource(R.string.author)}：${conflict.existingBook.author}",
                    style = LegadoTheme.typography.bodyMedium,
                )
                AppText(
                    text = stringResource(R.string.existing_book_source, existingSource),
                    style = LegadoTheme.typography.bodyMedium,
                )
                AppText(
                    text = stringResource(
                        R.string.all_chapter_num,
                        conflict.existingBook.totalChapterNum,
                    ),
                    style = LegadoTheme.typography.bodyMedium,
                )
                AppText(
                    text = stringResource(
                        R.string.latest_chapter_info,
                        conflict.existingBook.latestChapterTitle
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.no_last_chapter),
                    ),
                    style = LegadoTheme.typography.bodyMedium,
                )
                AppText(
                    text = stringResource(
                        R.string.new_book_source,
                        conflict.source.bookSourceName.ifBlank {
                            conflict.source.bookSourceUrl
                        },
                    ),
                    style = LegadoTheme.typography.bodyMedium,
                    color = LegadoTheme.colorScheme.primary,
                )
            }
        },
    )
    AppAlertDialog(
        data = emptyScopeName,
        onDismissRequest = viewModel::dismissEmptyScopeSearch,
        title = stringResource(R.string.draw),
        textProvider = {
            stringResource(
                R.string.search_empty_scope_switch_all,
                emptyScopeName.orEmpty(),
            )
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = { viewModel.confirmEmptyScopeSearch() },
        dismissText = stringResource(R.string.cancel),
        onDismiss = viewModel::dismissEmptyScopeSearch,
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
        initialOptions = settings.migrationOptions(),
        onDismissRequest = { showMigrationOptions = false },
        onConfirm = { options ->
            viewModel.setMigrationOptions(options)
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
