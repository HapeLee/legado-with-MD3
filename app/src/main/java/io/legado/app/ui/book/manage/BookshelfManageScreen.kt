package io.legado.app.ui.book.manage

import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.domain.usecase.BatchChangeSourcePreviewItem
import io.legado.app.domain.usecase.BatchChangeSourcePreviewStatus
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.tryParesExportFileName
import io.legado.app.service.ExportBookService
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.book.changesource.ChangeSourceMigrationOptionsSheet
import io.legado.app.ui.book.info.ChangeSourceSheet
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.button.SmallTonalTextButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.ACache
import io.legado.app.utils.FileDoc
import io.legado.app.utils.checkWrite
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.move
import io.legado.app.utils.startActivity
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.verificationField
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.button.MediumIconButton
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class BookshelfManageFabAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val action: () -> Unit
)

private data class BookshelfManageListState(
    override val items: List<Book> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false
) : ListUiState<Book>

@Composable
fun BookshelfManageRouteScreen(
    groupId: Long,
    onBackClick: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit,
    viewModel: BookshelfManageScreenViewModel = koinViewModel()
) {
    LaunchedEffect(groupId) {
        viewModel.dispatch(BookshelfManageScreenIntent.Initialize(groupId))
    }
    BookshelfManageScreen(
        viewModel = viewModel,
        onBackClick = onBackClick,
        onOpenBookInfo = onOpenBookInfo
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookshelfManageScreen(
    viewModel: BookshelfManageScreenViewModel,
    onBackClick: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showGroupMenu by remember { mutableStateOf(false) }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showDownloadAllConfirmDialog by remember { mutableStateOf(false) }
    var showBatchDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showExportTypeDialog by remember { mutableStateOf(false) }
    var showExportFileNameDialog by remember { mutableStateOf(false) }
    var showCharsetDialog by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }
    var showGroupSelectSheet by remember { mutableStateOf(false) }
    var showDeleteBookConfirmDialog by remember { mutableStateOf(false) }
    var showCustomExportDialog by remember { mutableStateOf(false) }
    var showBatchSourcePickerSheet by remember { mutableStateOf(false) }
    var pendingBatchSources by remember { mutableStateOf<List<BookSource>>(emptyList()) }
    var singleChangeSourceBook by remember { mutableStateOf<Book?>(null) }
    var manualSearchPreviewBook by remember { mutableStateOf<Book?>(null) }
    var otherSourcePreviewItem by remember { mutableStateOf<BatchChangeSourcePreviewItem?>(null) }
    var pendingMoveGroupBookUrl by remember { mutableStateOf<String?>(null) }
    var groupPickerCurrentGroupId by remember { mutableLongStateOf(0L) }
    var moreMenuBookUrl by remember { mutableStateOf<String?>(null) }
    var pendingDeleteBookUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingExportBookUrl by remember { mutableStateOf<String?>(null) }
    var pendingExportAll by remember { mutableStateOf(false) }
    var pendingExportSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customExportPath by remember { mutableStateOf("") }
    var customExportBook by remember { mutableStateOf<Book?>(null) }
    var customExportAllChapter by remember { mutableStateOf(false) }
    var customEpubScopeInput by remember { mutableStateOf("") }
    var customEpubScopeError by remember { mutableStateOf<String?>(null) }
    var customEpubSizeInput by remember { mutableStateOf("1") }
    var customEpisodeExportNameInput by remember { mutableStateOf(state.exportConfig.episodeExportFileName) }
    var exportFileNameInput by remember { mutableStateOf(state.exportConfig.bookExportFileName.orEmpty()) }
    var exportCharsetInput by remember { mutableStateOf(state.exportConfig.exportCharset) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchKey by remember { mutableStateOf("") }
    var selectedBookUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteOriginalBookFile by remember { mutableStateOf(state.deleteBookOriginal) }
    val exportBookPathKey = remember { "exportBookPath" }
    val exportTypes = remember { arrayListOf("txt", "epub") }
    val focusRequester = remember { FocusRequester() }
    val exportFolderText = stringResource(R.string.export_folder)
    val exportAllText = stringResource(R.string.export_all)
    val exportChapterIndexText = stringResource(R.string.export_chapter_index)
    val fileContainsNumberText = stringResource(R.string.file_contains_number)
    val exportFileNameText = stringResource(R.string.export_file_name)
    val resultAnalyzedText = stringResource(R.string.result_analyzed)
    val errorScopeInputText = stringResource(R.string.error_scope_input)
    val noGroupText = stringResource(R.string.no_group)
    val exportFileNameHintText = stringResource(R.string.export_file_name_template_hint)
    val exportFileNameHelpText = stringResource(R.string.export_file_name_template_help)
    val booksByUrl = remember(state.books) { state.books.associateBy { it.bookUrl } }
    val userGroups = remember(state.groupList) { state.groupList.filter { it.groupId > 0L } }

    val groupNameResolver: (Book) -> String = remember(userGroups, noGroupText) {
        { book ->
            if (book.group <= 0L) {
                noGroupText
            } else {
                val groups = userGroups.filter {
                    (book.group and it.groupId) > 0L
                }
                if (groups.isEmpty()) noGroupText
                else groups.joinToString("、") { it.groupName }
            }
        }
    }
    val filteredBooks = remember(state.books, searchKey, isSearchMode, groupNameResolver) {
        if (!isSearchMode || searchKey.isBlank()) {
            state.books
        } else {
            val key = searchKey.trim()
            state.books.filter { book ->
                book.name.contains(key, true) ||
                        book.getRealAuthor().contains(key, true) ||
                        book.originName.contains(key, true) ||
                        groupNameResolver(book).contains(key, true)
            }
        }
    }
    val listUiState = remember(filteredBooks, selectedBookUrls, searchKey, isSearchMode) {
        BookshelfManageListState(
            items = filteredBooks,
            selectedIds = selectedBookUrls.mapTo(linkedSetOf()) { it as Any },
            searchKey = searchKey,
            isSearch = isSearchMode,
            isLoading = false
        )
    }
    val inSelectionMode = selectedBookUrls.isNotEmpty()
    val hasLocalBookInDeleteTarget = remember(state.books, pendingDeleteBookUrls) {
        state.books.any { pendingDeleteBookUrls.contains(it.bookUrl) && it.isLocal }
    }
    val clearSelection = {
        selectedBookUrls = emptySet()
    }
    val toggleBookSelection: (Book) -> Unit = { book ->
        selectedBookUrls = if (selectedBookUrls.contains(book.bookUrl)) {
            selectedBookUrls - book.bookUrl
        } else {
            selectedBookUrls + book.bookUrl
        }
    }

    BackHandler(enabled = selectedBookUrls.isNotEmpty()) {
        clearSelection()
    }

    LaunchedEffect(state.books) {
        val visibleBookUrls = booksByUrl.keys
        selectedBookUrls = selectedBookUrls.intersect(visibleBookUrls)
    }

    val exportDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        var isReadyPath = false
        var dirPath = ""
        uri?.let {
            if (uri.isContentScheme()) {
                ACache.get().put(exportBookPathKey, uri.toString())
                dirPath = uri.toString()
                isReadyPath = true
            } else {
                uri.path?.let { path ->
                    ACache.get().put(exportBookPathKey, path)
                    dirPath = path
                    isReadyPath = true
                }
            }
        }
        if (!isReadyPath) return@rememberLauncherForActivityResult
        if (pendingExportSelection.isNotEmpty()) {
            pendingExportSelection.forEach { bookUrl ->
                booksByUrl[bookUrl]?.let { book ->
                    startExport(context, dirPath, book, state.exportConfig.exportType)
                }
            }
            return@rememberLauncherForActivityResult
        }
        if (pendingExportAll) {
            state.books.forEach { book ->
                startExport(context, dirPath, book, state.exportConfig.exportType)
            }
            return@rememberLauncherForActivityResult
        }
        val bookUrl = pendingExportBookUrl ?: return@rememberLauncherForActivityResult
        val book = booksByUrl[bookUrl] ?: return@rememberLauncherForActivityResult
        if (state.exportConfig.enableCustomExport) {
            customExportPath = dirPath
            customExportBook = book
            customExportAllChapter = false
            customEpubScopeInput = ""
            customEpubScopeError = null
            customEpubSizeInput = "1"
            customEpisodeExportNameInput = state.exportConfig.episodeExportFileName
            showCustomExportDialog = true
        } else {
            startExport(context, dirPath, book, state.exportConfig.exportType)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookshelfManageScreenEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BookshelfManageScreenEffect.NotifyBookChanged -> Unit
                is BookshelfManageScreenEffect.OpenBookInfo -> {
                    onOpenBookInfo(effect.name, effect.author, effect.bookUrl)
                }
            }
        }
    }

    fun selectExportFolder(
        bookUrl: String? = null,
        forAll: Boolean = false,
        selection: Set<String> = emptySet()
    ) {
        pendingExportBookUrl = bookUrl
        pendingExportAll = forAll
        pendingExportSelection = selection
        showFilePickerSheet = true
    }

    fun exportBook(book: Book) {
        val path = ACache.get().getAsString(exportBookPathKey)
        if (path.isNullOrEmpty() || !FileDoc.fromDir(path).checkWrite()) {
            selectExportFolder(book.bookUrl)
        } else if (state.exportConfig.enableCustomExport) {
            customExportPath = path
            customExportBook = book
            customExportAllChapter = false
            customEpubScopeInput = ""
            customEpubScopeError = null
            customEpubSizeInput = "1"
            customEpisodeExportNameInput = state.exportConfig.episodeExportFileName
            showCustomExportDialog = true
        } else {
            startExport(context, path, book, state.exportConfig.exportType)
        }
    }

    fun exportAll() {
        val path = ACache.get().getAsString(exportBookPathKey)
        if (path.isNullOrEmpty()) {
            selectExportFolder(forAll = true)
        } else {
            state.books.forEach { book ->
                startExport(context, path, book, state.exportConfig.exportType)
            }
        }
    }

    fun exportSelected() {
        if (selectedBookUrls.isEmpty()) return
        val path = ACache.get().getAsString(exportBookPathKey)
        if (path.isNullOrEmpty() || !FileDoc.fromDir(path).checkWrite()) {
            selectExportFolder(selection = selectedBookUrls)
        } else {
            selectedBookUrls.forEach { bookUrl ->
                booksByUrl[bookUrl]?.let { book ->
                    startExport(context, path, book, state.exportConfig.exportType)
                }
            }
        }
    }
    fun resolveSelectionGroupMask(): Long {
        val targetBooks = selectedBookUrls.mapNotNull { booksByUrl[it] }
        if (targetBooks.isEmpty()) return 0L
        val firstGroup = targetBooks.first().group.coerceAtLeast(0L)
        return if (targetBooks.all { it.group == firstGroup }) firstGroup else 0L
    }
    val fabItems = listOf(
        BookshelfManageFabAction(
            Icons.Default.SelectAll,
            stringResource(R.string.select_all)
        ) {
            selectedBookUrls = state.books.mapTo(hashSetOf()) { it.bookUrl }
        },
        BookshelfManageFabAction(
            Icons.Default.Refresh,
            stringResource(R.string.revert_selection)
        ) {
            val visibleBookUrls = booksByUrl.keys
            selectedBookUrls = visibleBookUrls - selectedBookUrls
        },
        BookshelfManageFabAction(
            Icons.Default.Download,
            "缓存选中"
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                showBatchDownloadConfirmDialog = true
            }
        },
        BookshelfManageFabAction(
            Icons.Default.Refresh,
            "批量换源"
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                showBatchSourcePickerSheet = true
            }
        },
        BookshelfManageFabAction(
            Icons.Default.Bookmarks,
            stringResource(R.string.move_to_group)
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                groupPickerCurrentGroupId = resolveSelectionGroupMask()
                pendingMoveGroupBookUrl = null
                showGroupSelectSheet = true
            }
        },
        BookshelfManageFabAction(
            Icons.Default.Upload,
            "导出选中"
        ) {
            exportSelected()
        },
        BookshelfManageFabAction(
            Icons.Default.Delete,
            stringResource(R.string.clear_cache)
        ) {
            viewModel.dispatch(BookshelfManageScreenIntent.ClearCachesForBooks(selectedBookUrls))
            clearSelection()
        },
        BookshelfManageFabAction(
            Icons.Default.Delete,
            stringResource(R.string.delete)
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                pendingDeleteBookUrls = selectedBookUrls
            deleteOriginalBookFile = state.deleteBookOriginal
                showDeleteBookConfirmDialog = true
            }
        }
    )
    val listState = rememberLazyListState()
    val canReorderBooks = state.bookSort == 3 && !isSearchMode && selectedBookUrls.isEmpty()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (canReorderBooks) {
            viewModel.dispatch(
                BookshelfManageScreenIntent.MoveBookOrder(
                    fromIndex = from.index,
                    toIndex = to.index
                )
            )
        }
    }
    ListScaffold(
        title = if (inSelectionMode) {
            "已选 ${selectedBookUrls.size}/${filteredBooks.size}"
        } else {
            state.groupName ?: stringResource(R.string.offline_cache)
        },
        state = listUiState,
        onBackClick = onBackClick,
        onSearchToggle = { active ->
            isSearchMode = active
            if (!active) {
                searchKey = ""
            }
        },
        onSearchQueryChange = { searchKey = it },
        searchPlaceholder = "筛选书名/作者/书源/分组",
        topBarActions = {
            if (state.groupList.isNotEmpty()) {
                TopBarActionButton(
                    onClick = { showGroupMenu = true },
                    imageVector = AppIcons.Filter,
                    contentDescription = null
                )
                RoundDropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false }
                ) { dismiss ->
                    state.groupList.forEach { group ->
                        RoundDropdownMenuItem(
                            text = group.groupName,
                            isSelected = group.groupId == state.groupId,
                            onClick = {
                                dismiss()
                                viewModel.dispatch(BookshelfManageScreenIntent.ChangeGroup(group.groupId))
                            }
                        )
                    }
                }
            }
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.download_all),
                onClick = {
                    dismiss()
                    if (state.isDownloadRunning) {
                        viewModel.dispatch(BookshelfManageScreenIntent.StopDownload)
                    } else {
                        showDownloadAllConfirmDialog = true
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.export_all),
                onClick = { dismiss(); exportAll() }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.export_folder),
                onClick = { dismiss(); selectExportFolder() }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.export_file_name),
                onClick = {
                    dismiss()
                    exportFileNameInput = state.exportConfig.bookExportFileName.orEmpty()
                    showExportFileNameDialog = true
                }
            )
            RoundDropdownMenuItem(
                text = "${stringResource(R.string.export_type)} (${exportTypes.getOrElse(state.exportConfig.exportType) { exportTypes[0] }})",
                onClick = {
                    dismiss()
                    showExportTypeDialog = true
                }
            )
            RoundDropdownMenuItem(
                text = "${stringResource(R.string.export_charset)} (${state.exportConfig.exportCharset})",
                onClick = {
                    dismiss()
                    exportCharsetInput = state.exportConfig.exportCharset
                    showCharsetDialog = true
                }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = "替换净化",
                isSelected = state.exportConfig.exportUseReplace,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetExportUseReplace(!state.exportConfig.exportUseReplace)
                    )
                }
            )
            RoundDropdownMenuItem(
                text = "自定义导出",
                isSelected = state.exportConfig.enableCustomExport,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetEnableCustomExport(!state.exportConfig.enableCustomExport)
                    )
                }
            )
            RoundDropdownMenuItem(
                text = "导出包含章节名",
                isSelected = !state.exportConfig.exportNoChapterName,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetExportNoChapterName(!state.exportConfig.exportNoChapterName)
                    )
                }
            )
            RoundDropdownMenuItem(
                text = "导出到WebDav",
                isSelected = state.exportConfig.exportToWebDav,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetExportToWebDav(!state.exportConfig.exportToWebDav)
                    )
                }
            )
            RoundDropdownMenuItem(
                text = "导出插图文件",
                isSelected = state.exportConfig.exportPictureFile,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetExportPictureFile(!state.exportConfig.exportPictureFile)
                    )
                }
            )
            RoundDropdownMenuItem(
                text = "并行导出",
                isSelected = state.exportConfig.parallelExportBook,
                onClick = {
                    dismiss()
                    viewModel.dispatch(
                        BookshelfManageScreenIntent.SetParallelExportBook(!state.exportConfig.parallelExportBook)
                    )
                }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.log),
                onClick = {
                    dismiss()
                    showLogSheet = true
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                modifier = Modifier.offset(x = 16.dp, y = 16.dp),
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier = Modifier
                            .animateFloatingActionButton(
                                visible = true,
                                alignment = Alignment.BottomEnd,
                            )
                            .focusRequester(focusRequester),
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.AutoMirrored.Filled.MenuOpen
                            }
                        }
                        Icon(
                            imageVector = imageVector,
                            contentDescription = "Menu",
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                }
            ) {
                fabItems.forEach { (icon, label, action) ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            action()
                            fabMenuExpanded = false
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        text = { Text(text = label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        val renderVersion by rememberUpdatedState(state.cacheVersion)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredBooks, key = { it.bookUrl }) { book ->
                val cacheCount = remember(renderVersion, book.bookUrl) {
                    viewModel.getCacheCount(book.bookUrl) ?: 0
                }
                val isPreparingDownload = viewModel.isBookPreparingDownload(book.bookUrl)
                val isDownloadingInCacheModel = viewModel.isBookDownloading(book.bookUrl)
                val isDownloading = isPreparingDownload || isDownloadingInCacheModel
                val downloadFailureText = viewModel.getDownloadFailureMessage(book.bookUrl)?.let {
                    stringResource(R.string.cache_download_failed, it)
                }
                val isSelected = selectedBookUrls.contains(book.bookUrl)
                val exportMsg = remember(renderVersion, book.bookUrl) {
                    ExportBookService.exportMsg[book.bookUrl]
                }
                ReorderableItem(
                    state = reorderableState,
                    key = book.bookUrl,
                    enabled = canReorderBooks
                ) {
                    NormalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canReorderBooks) {
                                    Modifier.longPressDraggableHandle()
                                } else {
                                    Modifier
                                }
                            ),
                        onClick = { toggleBookSelection(book) },
                        onLongClick = { toggleBookSelection(book) },
                        containerColor = if (isSelected) {
                            LegadoTheme.colorScheme.surfaceContainerHigh
                        } else {
                            LegadoTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .adaptiveHorizontalPadding(vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    AppText(
                                        text = book.name,
                                        style = LegadoTheme.typography.titleSmallEmphasized,
                                        maxLines = 1
                                    )
                                    AppText(
                                        text = book.getRealAuthor(),
                                        style = LegadoTheme.typography.bodySmall
                                    )
                                    AppText(
                                        text = "${groupNameResolver(book)} | ${book.originName.ifBlank { book.origin }}",
                                        style = LegadoTheme.typography.labelSmallEmphasized.copy(color = LegadoTheme.colorScheme.primary)
                                    )
                                    if (exportMsg != null) {
                                        AppText(text = exportMsg, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                TextCard(
                                    text = if (book.isLocal) {
                                        stringResource(R.string.local_book)
                                    } else {
                                        stringResource(
                                            R.string.download_count,
                                            cacheCount,
                                            book.totalChapterNum
                                        )
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (downloadFailureText != null) {
                                    AppText(
                                        text = downloadFailureText,
                                        modifier = Modifier.weight(1f),
                                        style = LegadoTheme.typography.labelSmall,
                                        color = LegadoTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RoundDropdownMenu(
                                        expanded = moreMenuBookUrl == book.bookUrl,
                                        onDismissRequest = { moreMenuBookUrl = null }
                                    ) { dismiss ->
                                        RoundDropdownMenuItem(
                                            text = "换源",
                                            onClick = {
                                                singleChangeSourceBook = book
                                                dismiss()
                                            }
                                        )
                                        RoundDropdownMenuItem(
                                            text = "删除书籍",
                                            onClick = {
                                                pendingDeleteBookUrls = setOf(book.bookUrl)
                                                deleteOriginalBookFile = state.deleteBookOriginal
                                                showDeleteBookConfirmDialog = true
                                                dismiss()
                                            }
                                        )
                                        RoundDropdownMenuItem(
                                            text = "删除缓存",
                                            onClick = {
                                                viewModel.dispatch(
                                                    BookshelfManageScreenIntent.ClearCachesForBooks(
                                                        setOf(book.bookUrl)
                                                    )
                                                )
                                                dismiss()
                                            }
                                        )
                                    }
                                    SmallTonalIconButton(
                                        onClick = {
                                            if (!book.isLocal) {
                                                viewModel.dispatch(BookshelfManageScreenIntent.ToggleBookDownload(book))
                                            }
                                        },
                                        imageVector = if (isDownloading) Icons.Default.Stop else Icons.Default.Download,
                                        contentDescription = "download"
                                    )
                                    SmallTonalIconButton(
                                        onClick = { exportBook(book) },
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = "upload"
                                    )
                                    SmallTonalIconButton(
                                        onClick = {
                                            pendingMoveGroupBookUrl = book.bookUrl
                                            groupPickerCurrentGroupId = book.group.coerceAtLeast(0L)
                                            showGroupSelectSheet = true
                                        },
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = "group"
                                    )
                                    SmallTonalIconButton(
                                        onClick = { moreMenuBookUrl = book.bookUrl },
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "more"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    singleChangeSourceBook?.let { book ->
        ChangeSourceSheet(
            show = true,
            oldBook = book,
            onDismissRequest = { singleChangeSourceBook = null },
            onReplace = { source, newBook, toc, options ->
                viewModel.dispatch(
                    BookshelfManageScreenIntent.ChangeBookSource(
                        oldBookUrl = book.bookUrl,
                        source = source,
                        book = newBook,
                        chapters = toc,
                        options = options,
                    )
                )
                singleChangeSourceBook = null
            },
            onAddAsNew = { _, _ ->
                context.toastOnUi("请在书籍详情页添加为新书")
            },
        )
    }

    BookSourcePickerSheet(
        show = showBatchSourcePickerSheet,
        title = "选择目标书源",
        onDismissRequest = { showBatchSourcePickerSheet = false },
        onConfirm = { sources ->
            pendingBatchSources = sources
            showBatchSourcePickerSheet = false
        }
    )

    ChangeSourceMigrationOptionsSheet(
        show = pendingBatchSources.isNotEmpty(),
        title = "批量换源选项",
        subtitle = "将对已选 ${selectedBookUrls.size} 本书执行换源；选项只对本次操作生效。",
        onDismissRequest = { pendingBatchSources = emptyList() },
        onConfirm = { options ->
            viewModel.dispatch(
                BookshelfManageScreenIntent.BatchChangeBookSource(
                    bookUrls = selectedBookUrls,
                    sources = pendingBatchSources,
                    options = options,
                )
            )
            pendingBatchSources = emptyList()
            clearSelection()
        }
    )

    BatchChangePreviewSheet(
        show = state.batchChangePreviewItems.isNotEmpty(),
        items = state.batchChangePreviewItems,
        onDismissRequest = { viewModel.dispatch(BookshelfManageScreenIntent.DismissBatchChangePreview) },
        onOpenBook = { book, inBookshelf ->
            viewModel.dispatch(BookshelfManageScreenIntent.OpenBookInfoPreview(book, inBookshelf))
        },
        onManualSearch = { book -> manualSearchPreviewBook = book },
        onSkip = { bookUrl -> viewModel.dispatch(BookshelfManageScreenIntent.SkipPreviewItem(bookUrl)) },
        onMigrate = { bookUrl -> viewModel.dispatch(BookshelfManageScreenIntent.MigratePreviewItem(bookUrl)) },
        onAddToShelf = { bookUrl ->
            viewModel.dispatch(BookshelfManageScreenIntent.AddPreviewItemToShelf(bookUrl))
        },
        onShowOtherSources = { item -> otherSourcePreviewItem = item },
        onMigrateAll = { viewModel.dispatch(BookshelfManageScreenIntent.MigrateAllPreviewItems) },
    )

    manualSearchPreviewBook?.let { book ->
        ChangeSourceSheet(
            show = true,
            oldBook = book,
            onDismissRequest = { manualSearchPreviewBook = null },
            onReplace = { source, newBook, toc, _ ->
                viewModel.dispatch(
                    BookshelfManageScreenIntent.UpdatePreviewItem(
                        oldBookUrl = book.bookUrl,
                        source = source,
                        book = newBook,
                        chapterCount = toc.size,
                    )
                )
                manualSearchPreviewBook = null
            },
            onAddAsNew = { _, _ ->
                context.toastOnUi("请先选择替换候选后再新增至书架")
            },
        )
    }

    OtherSourceOptionsSheet(
        item = otherSourcePreviewItem,
        onDismissRequest = { otherSourcePreviewItem = null },
        onSelect = { oldBookUrl, index ->
            viewModel.dispatch(BookshelfManageScreenIntent.SelectPreviewCandidate(oldBookUrl, index))
            otherSourcePreviewItem = null
        },
        onOpenBook = { book ->
            viewModel.dispatch(BookshelfManageScreenIntent.OpenBookInfoPreview(book, false))
        }
    )

    AppAlertDialog(
        show = state.isChangingSource || state.changeSourceError != null,
        onDismissRequest = {
            if (!state.isChangingSource) {
                viewModel.dispatch(BookshelfManageScreenIntent.DismissChangeSourceStatus)
            }
        },
        title = stringResource(R.string.change_source_batch),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isChangingSource) {
                    CircularProgressIndicator()
                }
                AppText(
                    text = state.changeSourceError ?: state.changeSourceProgress ?: "准备中",
                    style = LegadoTheme.typography.bodyMedium,
                    color = if (state.changeSourceError == null) {
                        LegadoTheme.colorScheme.onSurface
                    } else {
                        LegadoTheme.colorScheme.error
                    },
                )
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = if (state.isChangingSource) {
            null
        } else {
            { viewModel.dispatch(BookshelfManageScreenIntent.DismissChangeSourceStatus) }
        },
    )

    FilePickerSheet(
        show = showFilePickerSheet,
        onDismissRequest = { showFilePickerSheet = false },
        title = exportFolderText,
        onSelectSysDir = {
            showFilePickerSheet = false
            exportDir.launch(null)
        }
    )

    AppAlertDialog(
        show = showBatchDownloadConfirmDialog,
        onDismissRequest = { showBatchDownloadConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showBatchDownloadConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.DownloadBooks(
                    bookUrls = selectedBookUrls,
                    downloadAllChapters = false
                )
            )
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showBatchDownloadConfirmDialog = false }
    )

    AppAlertDialog(
        show = showDeleteBookConfirmDialog,
        onDismissRequest = { showDeleteBookConfirmDialog = false },
        title = stringResource(R.string.draw),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(text = stringResource(R.string.sure_del))
                if (hasLocalBookInDeleteTarget) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = deleteOriginalBookFile,
                            onCheckedChange = { checked ->
                                deleteOriginalBookFile = checked
                            }
                        )
                        AppText(
                            text = stringResource(R.string.delete_book_file),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDeleteBookConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.DeleteBooks(
                    bookUrls = pendingDeleteBookUrls,
                    deleteOriginal = deleteOriginalBookFile
                )
            )
            pendingDeleteBookUrls = emptySet()
            clearSelection()
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDeleteBookConfirmDialog = false }
    )

    GroupSelectSheet(
        show = showGroupSelectSheet,
        currentGroupId = groupPickerCurrentGroupId,
        onDismissRequest = { showGroupSelectSheet = false },
        onConfirm = { groupId ->
            val moveSet = pendingMoveGroupBookUrl?.let { setOf(it) } ?: selectedBookUrls
            val targetGroupId = groupId.coerceAtLeast(0L)
            viewModel.dispatch(
                BookshelfManageScreenIntent.MoveBooksToGroup(
                    bookUrls = moveSet,
                    groupId = targetGroupId
                )
            )
            pendingMoveGroupBookUrl = null
            groupPickerCurrentGroupId = 0L
            showGroupSelectSheet = false
            clearSelection()
        }
    )

    AppAlertDialog(
        show = showDownloadAllConfirmDialog,
        onDismissRequest = { showDownloadAllConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDownloadAllConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.StartDownloadForVisibleBooks(
                    books = state.books,
                    downloadAllChapters = true
                )
            )
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDownloadAllConfirmDialog = false }
    )

    OptionSheet(
        show = showExportTypeDialog,
        onDismissRequest = { showExportTypeDialog = false },
        title = stringResource(R.string.export_type)
    ) {
        exportTypes.forEachIndexed { index, type ->
            OptionCard(
                icon = if (type == "epub") Icons.Default.Upload else Icons.Default.Download,
                text = type,
                onClick = {
                    viewModel.dispatch(BookshelfManageScreenIntent.SetExportType(index))
                    showExportTypeDialog = false
                }
            )
        }
    }

    AppAlertDialog(
        show = showExportFileNameDialog,
        onDismissRequest = { showExportFileNameDialog = false },
        title = stringResource(R.string.export_file_name),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(text = exportFileNameHelpText)
                AppTextField(
                    value = exportFileNameInput,
                    onValueChange = { exportFileNameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = exportFileNameText,
                    placeholder = { AppText(exportFileNameHintText) }
                )
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            viewModel.dispatch(BookshelfManageScreenIntent.SetBookExportFileName(exportFileNameInput))
            showExportFileNameDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showExportFileNameDialog = false }
    )

    AppAlertDialog(
        show = showCharsetDialog,
        onDismissRequest = { showCharsetDialog = false },
        title = stringResource(R.string.set_charset),
        content = {
            AppTextField(
                value = exportCharsetInput,
                onValueChange = { exportCharsetInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.set_charset)
            )
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            viewModel.dispatch(
                BookshelfManageScreenIntent.SetExportCharset(
                    exportCharsetInput.ifBlank { "UTF-8" }
                )
            )
            showCharsetDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showCharsetDialog = false }
    )

    AppLogSheet(
        show = showLogSheet,
        onDismissRequest = { showLogSheet = false }
    )

    val currentCustomBook = customExportBook
    AppAlertDialog(
        show = showCustomExportDialog && currentCustomBook != null,
        onDismissRequest = {
            showCustomExportDialog = false
            customEpubScopeError = null
        },
        title = stringResource(R.string.select_section_export),
        content = {
            val episodeTemplateValid = customEpisodeExportNameInput.isNotBlank()
                && tryParesExportFileName(customEpisodeExportNameInput)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = customExportAllChapter,
                        onClick = { customExportAllChapter = true }
                    )
                    AppText(
                        text = exportAllText,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = !customExportAllChapter,
                        onClick = { customExportAllChapter = false }
                    )
                    AppText(
                        text = exportChapterIndexText,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (!customExportAllChapter) {
                    AppTextField(
                        value = customEpubScopeInput,
                        onValueChange = {
                            customEpubScopeInput = it
                            customEpubScopeError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = exportChapterIndexText,
                        placeholder = { AppText("1-5,8,10-18") },
                        supportingText = {
                            customEpubScopeError?.let { msg ->
                                AppText(text = msg)
                            }
                        },
                        isError = customEpubScopeError != null
                    )
                    AppTextField(
                        value = customEpubSizeInput,
                        onValueChange = { customEpubSizeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = fileContainsNumberText
                    )
                    AppTextField(
                        value = customEpisodeExportNameInput,
                        onValueChange = { customEpisodeExportNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = exportFileNameText,
                        placeholder = { AppText(exportFileNameHintText) }
                    )
                    if (episodeTemplateValid) {
                        AppText(
                            text = "$resultAnalyzedText: ${
                                currentCustomBook?.getExportFileName(
                                    "epub",
                                    1,
                                    customEpisodeExportNameInput
                                ).orEmpty()
                            }"
                        )
                    } else if (customEpisodeExportNameInput.isNotBlank()) {
                        AppText(text = "Error")
                    }
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            val book = customExportBook ?: return@AppAlertDialog
            if (customExportAllChapter) {
                context.startService<ExportBookService> {
                    action = IntentAction.start
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("exportType", "epub")
                    putExtra("exportPath", customExportPath)
                }
                showCustomExportDialog = false
                return@AppAlertDialog
            }
            if (!verificationField(customEpubScopeInput)) {
                customEpubScopeError = errorScopeInputText
                return@AppAlertDialog
            }
            customEpubScopeError = null
            if (customEpisodeExportNameInput.isNotBlank() && tryParesExportFileName(
                    customEpisodeExportNameInput
                )
            ) {
                viewModel.dispatch(BookshelfManageScreenIntent.SetEpisodeExportFileName(customEpisodeExportNameInput))
            }
            val epubSize = customEpubSizeInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
            context.startService<ExportBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("exportType", "epub")
                putExtra("exportPath", customExportPath)
                putExtra("epubSize", epubSize)
                putExtra("epubScope", customEpubScopeInput)
            }
            showCustomExportDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = {
            showCustomExportDialog = false
            customEpubScopeError = null
        }
    )
}

@Composable
private fun BookSourcePickerSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: (List<BookSource>) -> Unit,
) {
    val sources by appDb.bookSourceDao.flowEnabled()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var searchKey by rememberSaveable(show) { mutableStateOf("") }
    var selectedSources by remember(show) { mutableStateOf<List<BookSourcePart>>(emptyList()) }
    val selectedUrls = remember(selectedSources) {
        selectedSources.mapTo(hashSetOf()) { it.bookSourceUrl }
    }
    val selectedListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(selectedListState) { from, to ->
        selectedSources = selectedSources.toMutableList().apply {
            move(from.index, to.index)
        }
    }
    val filteredSources = remember(sources, searchKey) {
        if (searchKey.isBlank()) {
            sources
        } else {
            val key = searchKey.trim()
            sources.filter { source ->
                source.bookSourceName.contains(key, true) ||
                        source.bookSourceGroup.orEmpty().contains(key, true) ||
                        source.bookSourceUrl.contains(key, true)
            }
        }
    }
    val availableSources = remember(filteredSources, selectedUrls) {
        filteredSources.filterNot { selectedUrls.contains(it.bookSourceUrl) }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        endAction = {
            SmallTonalTextButton(
                text = stringResource(android.R.string.ok),
                imageVector = Icons.Default.PlayArrow,
                onClick = {
                    onConfirm(selectedSources.mapNotNull { it.getBookSource() })
                }
            )
        }
    ) {
        AppTextField(
            value = searchKey,
            onValueChange = { searchKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.screen),
            backgroundColor = LegadoTheme.colorScheme.surface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (selectedSources.isNotEmpty()) {
            AppText(
                text = "已选书源（长按拖拽排序）",
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                state = selectedListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(selectedSources, key = { it.bookSourceUrl }) { source ->
                    ReorderableSelectionItem(
                        state = reorderableState,
                        key = source.bookSourceUrl,
                        title = source.bookSourceName,
                        subtitle = source.bookSourceGroup,
                        isSelected = true,
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                        onToggleSelection = {
                            selectedSources = selectedSources.filterNot {
                                it.bookSourceUrl == source.bookSourceUrl
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        AppText(
            text = "可选书源",
            style = LegadoTheme.typography.labelMedium,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(availableSources, key = { it.bookSourceUrl }) { source ->
                SourcePickerItem(
                    source = source,
                    isSelected = false,
                    onClick = {
                        selectedSources = selectedSources + source
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SourcePickerItem(
    source: BookSourcePart,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    SelectionItemCard(
        title = source.bookSourceName,
        supportingContent = {
            AppText(
                text = source.bookSourceGroup.orEmpty().ifBlank { source.bookSourceUrl },
                style = LegadoTheme.typography.bodySmall,
                maxLines = 2,
            )
        },
        isSelected = isSelected,
        onToggleSelection = onClick,
        containerColor = LegadoTheme.colorScheme.onSheetContent,
    )
}

@Composable
private fun BatchChangePreviewSheet(
    show: Boolean,
    items: List<BatchChangeSourcePreviewItem>,
    onDismissRequest: () -> Unit,
    onOpenBook: (Book, Boolean) -> Unit,
    onManualSearch: (Book) -> Unit,
    onSkip: (String) -> Unit,
    onMigrate: (String) -> Unit,
    onAddToShelf: (String) -> Unit,
    onShowOtherSources: (BatchChangeSourcePreviewItem) -> Unit,
    onMigrateAll: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = "批量换源预览",
        endAction = {
            SmallTonalTextButton(
                text = "迁移全部",
                imageVector = Icons.Default.PlayArrow,
                onClick = onMigrateAll,
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.oldBook.bookUrl }) { item ->
                BatchChangePreviewRow(
                    item = item,
                    onOpenBook = onOpenBook,
                    onManualSearch = onManualSearch,
                    onSkip = onSkip,
                    onMigrate = onMigrate,
                    onAddToShelf = onAddToShelf,
                    onShowOtherSources = onShowOtherSources,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchChangePreviewRow(
    item: BatchChangeSourcePreviewItem,
    onOpenBook: (Book, Boolean) -> Unit,
    onManualSearch: (Book) -> Unit,
    onSkip: (String) -> Unit,
    onMigrate: (String) -> Unit,
    onAddToShelf: (String) -> Unit,
    onShowOtherSources: (BatchChangeSourcePreviewItem) -> Unit,
) {
    val candidate = item.selectedCandidate
    NormalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PreviewBookInfo(
                    title = "原书籍",
                    book = item.oldBook,
                    chapterCount = item.oldBook.totalChapterNum,
                    onClick = { onOpenBook(item.oldBook, true) },
                    modifier = Modifier.weight(1f),
                )
                PreviewBookInfo(
                    title = if (item.status == BatchChangeSourcePreviewStatus.Skipped) {
                        statusText(item.status)
                    } else {
                        candidate?.source?.bookSourceName ?: statusText(item.status)
                    },
                    book = if (item.status == BatchChangeSourcePreviewStatus.Skipped) {
                        null
                    } else {
                        candidate?.book
                    },
                    chapterCount = if (item.status == BatchChangeSourcePreviewStatus.Skipped) {
                        null
                    } else {
                        candidate?.chapterCount
                    },
                    onClick = {
                        candidate?.book?.let { onOpenBook(it, false) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediumIconButton(
                    imageVector = Icons.Default.Search,
                    onClick = { onManualSearch(item.oldBook) },
                )
                SmallTonalTextButton(
                    text = "不迁移",
                    imageVector = Icons.Default.SkipNext,
                    onClick = { onSkip(item.oldBook.bookUrl) },
                )
                SmallTonalTextButton(
                    text = "开始迁移",
                    imageVector = Icons.Default.PlayArrow,
                    onClick = { onMigrate(item.oldBook.bookUrl) },
                )
                SmallTonalTextButton(
                    text = "新增至书架",
                    imageVector = Icons.Default.Add,
                    onClick = { onAddToShelf(item.oldBook.bookUrl) },
                )
                if (item.candidates.size > 1) {
                    SmallTonalTextButton(
                        text = "查看其他源信息",
                        imageVector = Icons.Default.Info,
                        onClick = { onShowOtherSources(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBookInfo(
    title: String,
    book: Book?,
    chapterCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(enabled = book != null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (book != null) {
            CoilBookCover(
                name = book.name,
                author = book.author,
                path = book.getDisplayCover(),
                sourceOrigin = book.origin,
                modifier = Modifier.width(54.dp),
            )
            AppText(
                text = book.name,
                style = LegadoTheme.typography.labelMedium,
                maxLines = 1,
            )
            AppText(
                text = "${book.getRealAuthor()} · ${chapterCount ?: 0}章",
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        } else {
            Spacer(modifier = Modifier.size(54.dp))
            AppText(
                text = title,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.error,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun OtherSourceOptionsSheet(
    item: BatchChangeSourcePreviewItem?,
    onDismissRequest: () -> Unit,
    onSelect: (String, Int) -> Unit,
    onOpenBook: (Book) -> Unit,
) {
    AppModalBottomSheet(
        show = item != null,
        onDismissRequest = onDismissRequest,
        title = "其他源信息",
    ) {
        val currentItem = item
        if (currentItem != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(currentItem.candidates.indices.toList(), key = { it }) { index ->
                    val candidate = currentItem.candidates[index]
                    SelectionItemCard(
                        title = candidate.source.bookSourceName,
                        subtitle = "${candidate.book.name} · ${candidate.chapterCount}章",
                        supportingContent = {
                            AppText(
                                text = candidate.book.getRealAuthor(),
                                style = LegadoTheme.typography.bodySmall,
                            )
                        },
                        isSelected = index == currentItem.selectedCandidateIndex,
                        onToggleSelection = { onSelect(currentItem.oldBook.bookUrl, index) },
                        trailingAction = {
                            SmallTonalIconButton(
                                onClick = { onOpenBook(candidate.book) },
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                            )
                        },
                        containerColor = LegadoTheme.colorScheme.onSheetContent,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun statusText(status: BatchChangeSourcePreviewStatus): String {
    return when (status) {
        BatchChangeSourcePreviewStatus.Matched -> "已匹配"
        BatchChangeSourcePreviewStatus.NotFound -> "未找到"
        BatchChangeSourcePreviewStatus.Skipped -> "不迁移"
    }
}

private fun startExport(
    context: android.content.Context,
    path: String,
    book: Book,
    exportTypeIndex: Int
) {
    val exportType = if (exportTypeIndex == 1) "epub" else "txt"
    context.startService<ExportBookService> {
        action = IntentAction.start
        putExtra("bookUrl", book.bookUrl)
        putExtra("exportType", exportType)
        putExtra("exportPath", path)
    }
}
