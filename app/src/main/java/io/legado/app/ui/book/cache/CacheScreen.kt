package io.legado.app.ui.book.cache

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.Book
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.tryParesExportFileName
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.service.ExportBookService
import io.legado.app.ui.about.AppLogSheet
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.button.TopBarActionButton
import io.legado.app.ui.widget.components.button.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.ACache
import io.legado.app.utils.FileDoc
import io.legado.app.utils.checkWrite
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.verificationField
import org.koin.androidx.compose.koinViewModel

@Composable
fun CacheRouteScreen(
    groupId: Long,
    onBackClick: () -> Unit,
    viewModel: CacheViewModel = koinViewModel()
) {
    LaunchedEffect(groupId) {
        viewModel.dispatch(CacheIntent.Initialize(groupId))
    }
    CacheScreen(viewModel = viewModel, onBackClick = onBackClick)
}

@Composable
private fun CacheScreen(
    viewModel: CacheViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showDownloadAllConfirmDialog by remember { mutableStateOf(false) }
    var showExportTypeDialog by remember { mutableStateOf(false) }
    var showExportFileNameDialog by remember { mutableStateOf(false) }
    var showCharsetDialog by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }
    var showCustomExportDialog by remember { mutableStateOf(false) }
    var pendingExportBookUrl by remember { mutableStateOf<String?>(null) }
    var pendingExportAll by remember { mutableStateOf(false) }
    var customExportPath by remember { mutableStateOf("") }
    var customExportBook by remember { mutableStateOf<Book?>(null) }
    var customExportAllChapter by remember { mutableStateOf(false) }
    var customEpubScopeInput by remember { mutableStateOf("") }
    var customEpubScopeError by remember { mutableStateOf<String?>(null) }
    var customEpubSizeInput by remember { mutableStateOf("1") }
    var customEpisodeExportNameInput by remember { mutableStateOf(state.exportConfig.episodeExportFileName) }
    var exportFileNameInput by remember { mutableStateOf(state.exportConfig.bookExportFileName.orEmpty()) }
    var exportCharsetInput by remember { mutableStateOf(state.exportConfig.exportCharset) }
    val exportBookPathKey = remember { "exportBookPath" }
    val exportTypes = remember { arrayListOf("txt", "epub") }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val exportFolderText = stringResource(R.string.export_folder)
    val exportAllText = stringResource(R.string.export_all)
    val exportChapterIndexText = stringResource(R.string.export_chapter_index)
    val fileContainsNumberText = stringResource(R.string.file_contains_number)
    val exportFileNameText = stringResource(R.string.export_file_name)
    val resultAnalyzedText = stringResource(R.string.result_analyzed)
    val errorScopeInputText = stringResource(R.string.error_scope_input)
    val exportFileNameHintText = "书名：《{name}》 作者：{author}"
    val exportFileNameHelpText = """
支持变量：{name}（书名）、{author}（作者）、{group}（分组）、{source}（书源）、{remark}（备注）。
可以在字段前后加任意字符。

示例：
书名：《{name}》 作者：{author}
输出：书名：《三体》 作者：刘慈欣

书名：《{name}》-作者：{author}_备注
输出：书名：《三体》-作者：刘慈欣_备注
    """.trimIndent()

    val exportDir = rememberLauncherForActivityResult(HandleFileContract()) { result ->
        var isReadyPath = false
        var dirPath = ""
        result.uri?.let { uri ->
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
        if (pendingExportAll) {
            state.books.forEach { book ->
                startExport(context, dirPath, book, state.exportConfig.exportType)
            }
            return@rememberLauncherForActivityResult
        }
        val bookUrl = pendingExportBookUrl ?: return@rememberLauncherForActivityResult
        val book = state.books.firstOrNull { it.bookUrl == bookUrl } ?: return@rememberLauncherForActivityResult
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
                is CacheEffect.ShowMessage -> context.toastOnUi(effect.message)
                is CacheEffect.NotifyBookChanged -> Unit
            }
        }
    }

    fun selectExportFolder(bookUrl: String? = null, forAll: Boolean = false) {
        pendingExportBookUrl = bookUrl
        pendingExportAll = forAll
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

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = state.groupName ?: stringResource(R.string.offline_cache),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
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
                                    text = "${group.groupName}",
                                    isSelected = group.groupId == state.groupId,
                                    onClick = {
                                        dismiss()
                                        viewModel.dispatch(CacheIntent.ChangeGroup(group.groupId))
                                    }
                                )
                            }
                        }
                    }
                    TopBarActionButton(
                        onClick = { showMenu = true },
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null
                    )
                    RoundDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.download_all),
                            onClick = {
                                showMenu = false
                                if (state.isDownloadRunning) {
                                    viewModel.dispatch(CacheIntent.StopDownload)
                                } else {
                                    showDownloadAllConfirmDialog = true
                                }
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.export_all),
                            onClick = { showMenu = false; exportAll() }
                        )
                        PillDivider()
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.export_folder),
                            onClick = { showMenu = false; selectExportFolder() }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.export_file_name),
                            onClick = {
                                showMenu = false
                                exportFileNameInput = state.exportConfig.bookExportFileName.orEmpty()
                                showExportFileNameDialog = true
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "${stringResource(R.string.export_type)} (${exportTypes.getOrElse(state.exportConfig.exportType) { exportTypes[0] }})",
                            onClick = {
                                showMenu = false
                                showExportTypeDialog = true
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "${stringResource(R.string.export_charset)} (${state.exportConfig.exportCharset})",
                            onClick = {
                                showMenu = false
                                exportCharsetInput = state.exportConfig.exportCharset
                                showCharsetDialog = true
                            }
                        )
                        PillDivider()
                        RoundDropdownMenuItem(
                            text = "替换净化",
                            isSelected = state.exportConfig.exportUseReplace,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetExportUseReplace(!state.exportConfig.exportUseReplace)
                                )
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "自定义导出",
                            isSelected = state.exportConfig.enableCustomExport,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetEnableCustomExport(!state.exportConfig.enableCustomExport)
                                )
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "导出包含章节名",
                            isSelected = !state.exportConfig.exportNoChapterName,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetExportNoChapterName(!state.exportConfig.exportNoChapterName)
                                )
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "导出到WebDav",
                            isSelected = state.exportConfig.exportToWebDav,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetExportToWebDav(!state.exportConfig.exportToWebDav)
                                )
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "导出插图文件",
                            isSelected = state.exportConfig.exportPictureFile,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetExportPictureFile(!state.exportConfig.exportPictureFile)
                                )
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "并行导出",
                            isSelected = state.exportConfig.parallelExportBook,
                            onClick = {
                                showMenu = false
                                viewModel.dispatch(
                                    CacheIntent.SetParallelExportBook(!state.exportConfig.parallelExportBook)
                                )
                            }
                        )
                        PillDivider()
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.log),
                            onClick = {
                                showMenu = false
                                showLogSheet = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = {
                    if (state.isDownloadRunning) {
                        viewModel.dispatch(CacheIntent.StopDownload)
                    } else {
                        showDownloadConfirmDialog = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (state.isDownloadRunning) Icons.Default.Stop else Icons.Default.Download,
                    contentDescription = null
                )
            }
        }
    ) { paddingValues ->
        val renderVersion by rememberUpdatedState(state.cacheVersion)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.books, key = { it.bookUrl }) { book ->
                val cacheCount = remember(renderVersion, book.bookUrl) {
                    viewModel.getCacheChapters(book.bookUrl)?.size ?: 0
                }
                val isDownloading = remember(renderVersion, book.bookUrl) {
                    viewModel.isBookDownloading(book.bookUrl)
                }
                val exportMsg = remember(renderVersion, book.bookUrl) {
                    ExportBookService.exportMsg[book.bookUrl]
                }
                val exportProgress = remember(renderVersion, book.bookUrl) {
                    ExportBookService.exportProgress[book.bookUrl]
                }
                GlassCard(
                    containerColor = LegadoTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AppText(
                                text = book.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextCard(text = stringResource(R.string.download_count, cacheCount, book.totalChapterNum))
                        }
                        AppText(
                            text = stringResource(R.string.author_show, book.getRealAuthor()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AppText(
                            text = if (book.isLocal) {
                                stringResource(R.string.local_book)
                            } else {
                                stringResource(R.string.download_count, cacheCount, book.totalChapterNum)
                            }
                        )
                        if (isDownloading && book.totalChapterNum > 0) {
                            AppLinearProgressIndicator(
                                progress = (cacheCount.toFloat() / book.totalChapterNum.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        }
                        if (exportMsg != null) {
                            AppText(text = exportMsg, modifier = Modifier.padding(top = 6.dp))
                        } else if (exportProgress != null && book.totalChapterNum > 0) {
                            AppLinearProgressIndicator(
                                progress = (exportProgress.toFloat() / book.totalChapterNum.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            SmallTonalIconButton(
                                onClick = {
                                    if (!book.isLocal) {
                                        viewModel.dispatch(CacheIntent.ToggleBookDownload(book))
                                    }
                                },
                                imageVector = if (isDownloading) Icons.Default.Stop else Icons.Default.Download,
                                contentDescription = null
                            )
                            SmallTonalIconButton(
                                onClick = { exportBook(book) },
                                modifier = Modifier.padding(start = 8.dp),
                                imageVector = Icons.Default.Upload,
                                contentDescription = null
                            )
                            SmallTonalIconButton(
                                onClick = {
                                    viewModel.dispatch(CacheIntent.DeleteBookDownload(book.bookUrl))
                                    viewModel.dispatch(CacheIntent.ClearBookCache(book))
                                },
                                modifier = Modifier.padding(start = 8.dp),
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }

    FilePickerSheet(
        show = showFilePickerSheet,
        onDismissRequest = { showFilePickerSheet = false },
        title = exportFolderText,
        onSelectSysDir = {
            showFilePickerSheet = false
            val default = arrayListOf<SelectItem<Int>>()
            val path = ACache.get().getAsString(exportBookPathKey)
            if (!path.isNullOrEmpty()) default.add(SelectItem(path, -1))
            exportDir.launch {
                mode = HandleFileContract.DIR_SYS
                title = exportFolderText
                otherActions = default
                requestCode = 0
            }
        }
    )

    AppAlertDialog(
        show = showDownloadConfirmDialog,
        onDismissRequest = { showDownloadConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDownloadConfirmDialog = false
            viewModel.dispatch(
                CacheIntent.StartDownloadForVisibleBooks(
                    books = state.books,
                    downloadAllChapters = false
                )
            )
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDownloadConfirmDialog = false }
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
                CacheIntent.StartDownloadForVisibleBooks(
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
                    viewModel.dispatch(CacheIntent.SetExportType(index))
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
            viewModel.dispatch(CacheIntent.SetBookExportFileName(exportFileNameInput))
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
                CacheIntent.SetExportCharset(
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
                viewModel.dispatch(CacheIntent.SetEpisodeExportFileName(customEpisodeExportNameInput))
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
