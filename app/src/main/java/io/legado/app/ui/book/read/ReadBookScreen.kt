package io.legado.app.ui.book.read

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.sheet.AiRewritePresetConfigSheet
import io.legado.app.ui.book.read.sheet.AiTextCleanSheet
import io.legado.app.ui.book.read.sheet.AiTextRewriteSheet
import io.legado.app.ui.book.read.sheet.BgTextConfigSheet
import io.legado.app.ui.book.read.sheet.ChangeChapterSourceSheet
import io.legado.app.ui.book.read.sheet.ChapterSummarySheet
import io.legado.app.ui.book.read.sheet.CharsetConfigSheet
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.book.read.sheet.ContentEditSheet
import io.legado.app.ui.book.read.sheet.ContentProcessesSheet
import io.legado.app.ui.book.read.sheet.DownloadSheet
import io.legado.app.ui.book.read.sheet.EffectiveReplacesSheet
import io.legado.app.ui.book.read.sheet.HighlightRuleConfigSheet
import io.legado.app.ui.book.read.sheet.HttpTtsEditSheet
import io.legado.app.ui.book.read.sheet.MoreConfigSheet
import io.legado.app.ui.book.read.sheet.PageAnimConfigSheet
import io.legado.app.ui.book.read.sheet.PageKeyConfigSheet
import io.legado.app.ui.book.read.sheet.PhotoSheet
import io.legado.app.ui.book.read.sheet.ReadAloudConfigSheet
import io.legado.app.ui.book.read.sheet.ReadAloudNumberConfigSheet
import io.legado.app.ui.book.read.sheet.ShadowSetSheet
import io.legado.app.ui.book.read.sheet.SimulatedReadingSheet
import io.legado.app.ui.book.read.sheet.SpeakEngineConfigSheet
import io.legado.app.ui.book.read.sheet.TitleBarIconSheet
import io.legado.app.ui.book.read.sheet.ToolButtonConfigSheet
import io.legado.app.ui.book.read.sheet.UnderlineConfigSheet
import io.legado.app.ui.config.readConfig.TextSelectMenuFilterSheet
import io.legado.app.ui.dict.DictSheet
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.bookmark.BookmarkEditSheet
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

/**
 * Stateless ReadBook screen — renders BackHandler + dialogs + sheets.
 * ReadView is hosted in the XML layout, not here.
 */
@Composable
fun ReadBookScreen(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler {
        when {
            state.isShowingSearchResult -> onIntent(ReadBookIntent.ExitSearch)
            state.menuVisible -> onIntent(ReadBookIntent.ReadMenuBack)
            state.isAutoPage -> onIntent(ReadBookIntent.StopAutoPage)
            else -> onIntent(ReadBookIntent.CloseReadBook())
        }
    }

    // Dialogs driven by activeDialog state
    val restoreDialog = state.activeDialog as? ReadBookDialog.ConfirmRestoreProgress
    val syncDialog = state.activeDialog as? ReadBookDialog.SureSyncProgress
    val restoreLastProgressDialog = state.activeDialog as? ReadBookDialog.RestoreLastBookProgress
    val skipDialog = state.activeDialog as? ReadBookDialog.ConfirmSkipToChapter
    val payDialog = state.activeDialog as? ReadBookDialog.ConfirmChapterPay
    val addToBookshelfDialog = state.activeDialog as? ReadBookDialog.ConfirmAddToBookshelf

    AppAlertDialog(
        show = restoreDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.restore_progress),
        text = stringResource(R.string.found_cloud_progress),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            restoreDialog?.let { onIntent(ReadBookIntent.SureNewProgress(it.progress)) }
            onIntent(ReadBookIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = syncDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.sync_progress),
        text = stringResource(R.string.progress_exceeds_cloud),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            syncDialog?.let { onIntent(ReadBookIntent.SureSyncProgress(it.progress)) }
            onIntent(ReadBookIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = restoreLastProgressDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.KeepCurrentBookProgress) },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.restore_last_book_process),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.RestoreLastBookProgress) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.KeepCurrentBookProgress) },
    )
    AppAlertDialog(
        show = skipDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.chapter_list),
        text = stringResource(R.string.confirm_skip_to_chapter),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.DismissDialog) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = payDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
        title = stringResource(R.string.chapter_pay),
        text = payDialog?.chapterTitle ?: "",
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onIntent(ReadBookIntent.DismissDialog)
            onIntent(ReadBookIntent.ConfirmPayAction)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
    )
    AppAlertDialog(
        show = addToBookshelfDialog != null,
        onDismissRequest = { onIntent(ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf) },
        title = stringResource(R.string.add_to_bookshelf),
        text = stringResource(
            R.string.check_add_bookshelf,
            addToBookshelfDialog?.bookName.orEmpty()
        ),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(ReadBookIntent.ConfirmAddCurrentBookToBookshelf) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf) },
    )

    // AppModalBottomSheet-based sheets — always composed, controlled by show flag
    // for proper enter/exit animations
    val dismissSheet = { onIntent(ReadBookIntent.DismissSheet) }

    ShadowSetSheet(
        show = state.activeSheet is ReadBookSheet.ShadowSet,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
    )
    EffectiveReplacesSheet(
        show = state.activeSheet is ReadBookSheet.EffectiveReplaces,
        onDismissRequest = dismissSheet,
        onOpenReplaceEditor = { id, pattern ->
            onIntent(ReadBookIntent.OpenReplaceEditor(id, pattern))
        },
        onReplaceRuleChanged = { onIntent(ReadBookIntent.ReplaceRuleChanged) },
        onNavigateToTextEffects = {
            onIntent(ReadBookIntent.DismissSheet)
            onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.TextTitle))
        },
        onOpenContentProcesses = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ContentProcesses))
        },
    )
    ContentProcessesSheet(
        show = state.activeSheet is ReadBookSheet.ContentProcesses,
        state = state.contentProcessConfig,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    UnderlineConfigSheet(
        show = state.activeSheet is ReadBookSheet.UnderlineConfig,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
    )
    val fontSelectReadSettings: ReadSettingsRepository = org.koin.compose.koinInject()
    val fontSelectPreferences by fontSelectReadSettings.preferences.collectAsStateWithLifecycle(
        initialValue = null
    )
    val fontSelectFolderState = remember(fontSelectPreferences) {
        val pref = fontSelectPreferences
        if (pref == null) {
            FontFolderState.Loading
        } else {
            FontFolderState.Loaded(pref.fontFolder.takeIf { it.isNotEmpty() }?.toUri())
        }
    }
    val fontSelectSystemTypefaces = stringArrayResource(R.array.system_typefaces)
    FontSelectSheet(
        show = state.activeSheet is ReadBookSheet.FontSelect,
        title = stringResource(R.string.select_font),
        folderState = fontSelectFolderState,
        selectedFontPath = ReadBookConfig.textFont,
        onDismissRequest = dismissSheet,
        onSelectFont = { onIntent(ReadBookIntent.SelectFont(it.uri.toString())) },
        onSelectSystemTypeface = { onIntent(ReadBookIntent.SelectSystemTypeface(it)) },
        onOpenFolderPicker = { onIntent(ReadBookIntent.OpenFontFolderPicker) },
        systemTypefaces = fontSelectSystemTypefaces,
    )
    FontSelectSheet(
        show = state.activeSheet is ReadBookSheet.TitleFontSelect,
        title = stringResource(R.string.read_config_title_settings),
        folderState = fontSelectFolderState,
        selectedFontPath = ReadBookConfig.titleFont,
        onDismissRequest = dismissSheet,
        onSelectFont = { onIntent(ReadBookIntent.SelectTitleFont(it.uri.toString())) },
        onSelectSystemTypeface = { onIntent(ReadBookIntent.SelectTitleSystemTypeface(it)) },
        onOpenFolderPicker = { onIntent(ReadBookIntent.OpenFontFolderPicker) },
        systemTypefaces = fontSelectSystemTypefaces,
    )
    ToolButtonConfigSheet(
        show = state.activeSheet is ReadBookSheet.ToolButtonConfig,
        items = state.menuConfig.bottomBarButtons,
        customIcons = state.menuConfig.readMenuCustomIcons,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
    )
    TitleBarIconSheet(
        show = state.activeSheet is ReadBookSheet.TitleBarIconConfig,
        items = state.menuConfig.titleBarButtons,
        customIcons = state.menuConfig.titleBarCustomIcons,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
    )
    HighlightRuleConfigSheet(
        show = state.activeSheet is ReadBookSheet.HighlightRuleConfig,
        state = state.highlightRuleConfig,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
    )
    ContentEditSheet(
        show = state.activeSheet is ReadBookSheet.ContentEdit,
        state = state,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    ChapterSummarySheet(
        show = state.activeSheet is ReadBookSheet.ChapterSummary,
        state = state.chapterSummary,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    AiTextCleanSheet(
        show = state.activeSheet is ReadBookSheet.AiTextClean,
        state = state.aiTextClean,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    AiTextRewriteSheet(
        show = state.activeSheet is ReadBookSheet.AiTextRewrite,
        state = state.aiTextRewrite,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    AiRewritePresetConfigSheet(
        show = state.activeSheet is ReadBookSheet.AiRewritePresetConfig,
        state = state.aiRewritePresetConfig,
        onIntent = onIntent,
        onDismissRequest = { onIntent(ReadBookIntent.CloseAiRewritePresetConfig) },
    )
    MoreConfigSheet(
        show = state.activeSheet is ReadBookSheet.MoreConfig,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
        onOpenClickRegionalConfig = {
            onIntent(ReadBookIntent.DismissSheet)
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ClickActionConfig))
        },
        onOpenPageKeyConfig = {
            onIntent(ReadBookIntent.DismissSheet)
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageKeyConfig))
        },
        onOpenTextSelectMenuFilterConfig = {
            onIntent(ReadBookIntent.DismissSheet)
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextSelectMenuFilterConfig))
        },
    )
    ReadAloudConfigSheet(
        show = state.activeSheet is ReadBookSheet.ReadAloudConfig,
        state = state,
        onIntent = onIntent,
        onDismissRequest = dismissSheet,
    )
    SpeakEngineConfigSheet(
        show = state.activeSheet is ReadBookSheet.SpeakEngineConfig,
        state = state,
        onIntent = onIntent,
        onDismissRequest = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ReadAloudConfig))
        },
    )
    HttpTtsEditSheet(
        show = state.activeSheet is ReadBookSheet.HttpTtsEdit,
        httpTTS = state.editingHttpTts,
        onIntent = onIntent,
        onDismissRequest = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SpeakEngineConfig))
        },
    )
    ReadAloudNumberConfigSheet(
        show = state.activeSheet is ReadBookSheet.PreDownloadConfig,
        title = stringResource(R.string.read_aloud_preload),
        description = stringResource(R.string.read_aloud_preload_summary, state.preDownloadNum),
        value = state.preDownloadNum,
        defaultValue = 10,
        valueRange = 0f..100f,
        onValueChange = { onIntent(ReadBookIntent.ApplyPreDownloadNum(it)) },
        onDismissRequest = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ReadAloudConfig))
        },
    )
    ReadAloudNumberConfigSheet(
        show = state.activeSheet is ReadBookSheet.AudioCacheCleanConfig,
        title = stringResource(R.string.audio_cache_clean_time),
        description = stringResource(
            R.string.audio_cache_clean_time_summary,
            state.audioCacheCleanTime
        ),
        value = state.audioCacheCleanTime,
        defaultValue = 10,
        valueRange = 0f..10080f,
        onValueChange = { onIntent(ReadBookIntent.ApplyAudioCacheCleanTime(it)) },
        onDismissRequest = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ReadAloudConfig))
        },
    )
    ReadAloudNumberConfigSheet(
        show = state.activeSheet is ReadBookSheet.ParagraphIntervalConfig,
        title = stringResource(R.string.tts_paragraph_interval),
        description = stringResource(
            R.string.tts_paragraph_interval_summary,
            state.readAloudParagraphInterval
        ),
        value = state.readAloudParagraphInterval,
        defaultValue = 0,
        valueRange = 0f..5000f,
        onValueChange = { onIntent(ReadBookIntent.ApplyParagraphInterval(it)) },
        onDismissRequest = {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ReadAloudConfig))
        },
    )
    AppLogSheet(
        show = state.activeSheet is ReadBookSheet.AppLog,
        onDismissRequest = dismissSheet,
    )
    BgTextConfigSheet(
        show = state.activeSheet is ReadBookSheet.BgTextConfig,
        onDismissRequest = dismissSheet,
        onIntent = onIntent,
        onSelectImage = { onIntent(ReadBookIntent.OpenReadStyleImagePicker) },
        onSelectImageForMode = { isNight ->
            onIntent(ReadBookIntent.OpenReadStyleImagePickerForMode(isNight))
        },
        onImportConfig = { onIntent(ReadBookIntent.OpenReadStyleImport) },
        onExportConfig = { onIntent(ReadBookIntent.OpenReadStyleExport) },
        styleConfig = state.styleConfig,
    )
    val dictSheet = state.activeSheet as? ReadBookSheet.Dict
    DictSheet(
        show = dictSheet != null,
        word = dictSheet?.word ?: "",
        onDismissRequest = dismissSheet,
    )
    val photoSheet = state.activeSheet as? ReadBookSheet.Photo
    PhotoSheet(
        show = photoSheet != null,
        src = photoSheet?.src ?: "",
        sourceOrigin = photoSheet?.sourceOrigin,
        onDismissRequest = dismissSheet,
    )
    val bookmarkSheet = state.activeSheet as? ReadBookSheet.Bookmark
    bookmarkSheet?.let { sheet ->
        BookmarkEditSheet(
            show = true,
            bookmark = sheet.bookmark,
            onDismiss = dismissSheet,
            onSave = { onIntent(ReadBookIntent.SaveBookmark(it)) },
            onDelete = { onIntent(ReadBookIntent.DeleteBookmark(it)) },
        )
    }

    // AlertDialog-based sheets and special cases — conditionally composed
    when (state.activeSheet) {
        is ReadBookSheet.ClickActionConfig -> {
            ClickActionConfigSheet(
                onDismissRequest = dismissSheet,
            )
        }

        is ReadBookSheet.PageKeyConfig -> {
            PageKeyConfigSheet(
                onDismissRequest = dismissSheet,
            )
        }

        is ReadBookSheet.TextSelectMenuFilterConfig -> {
            TextSelectMenuFilterSheet(
                show = true,
                onDismissRequest = dismissSheet,
                onFilterChanged = {
                    onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.TextSelectMenuFilter(it)))
                }
            )
        }

        is ReadBookSheet.PageAnim -> {
            PageAnimConfigSheet(
                onDismissRequest = dismissSheet,
                onAnimChanged = { onIntent(ReadBookIntent.PageAnimChanged) },
            )
        }

        is ReadBookSheet.Download -> {
            DownloadSheet(
                onDismissRequest = dismissSheet,
                onDownload = { start, end ->
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.DownloadChapters(start, end))
                },
            )
        }

        is ReadBookSheet.Charset -> {
            CharsetConfigSheet(
                onDismissRequest = dismissSheet,
            )
        }

        is ReadBookSheet.SimulatedReading -> {
            SimulatedReadingSheet(
                onDismissRequest = dismissSheet,
                onApply = { onIntent(ReadBookIntent.ApplySimulatedReading) },
            )
        }

        is ReadBookSheet.Bookmark -> Unit

        is ReadBookSheet.InfoConfig -> {
            // Integrated into ReadStyleSheet's HeaderFooterPage
            LaunchedEffect(state.activeSheet) {
                onIntent(ReadBookIntent.DismissSheet)
            }
        }

        is ReadBookSheet.ChangeChapterSource -> {
            val sheet = state.activeSheet
            val book = state.book
            if (book != null) {
                var showSheet by remember { mutableStateOf(true) }
                LaunchedEffect(showSheet) {
                    if (!showSheet) {
                        kotlinx.coroutines.delay(300)
                        onIntent(ReadBookIntent.SetActiveSheet(null))
                    }
                }
                val viewModel = androidx.compose.runtime.key(
                    "chapter-source-${book.bookUrl}-${sheet.chapterIndex}"
                ) {
                    org.koin.androidx.compose.koinViewModel<io.legado.app.ui.book.changesource.ChangeChapterSourceViewModel>()
                }
                androidx.compose.runtime.DisposableEffect(viewModel) {
                    onDispose { viewModel.dispose() }
                }
                LaunchedEffect(book.bookUrl, sheet.chapterIndex) {
                    viewModel.initData(
                        book,
                        sheet.chapterIndex,
                        sheet.chapterTitle
                    )
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                ChangeChapterSourceSheet(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = viewModel::onIntent,
                    show = showSheet,
                    onDismissRequest = { showSheet = false },
                    onAnimationFinish = { onIntent(ReadBookIntent.SetActiveSheet(null)) },
                    bookScoreFlow = viewModel::bookScoreFlow,
                    onBookScoreClick = viewModel::onBookScoreClick,
                    onEditSource = { sourceUrl ->
                        onIntent(ReadBookIntent.OpenSourceEditByUrl(sourceUrl))
                    },
                )
                // Handle ReplaceContent effect
                LaunchedEffect(viewModel) {
                    viewModel.effects.collectLatest { effect ->
                        when (effect) {
                            is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.ReplaceContent -> {
                                showSheet = false
                                onIntent(ReadBookIntent.SaveChapterContent(effect.content, sheet.chapterIndex))
                            }

                            is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.ShowToast -> {
                                context.toastOnUi(effect.message)
                            }

                            is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.Dismiss -> {
                                // Handled by showSheet animation — no-op
                            }
                        }
                    }
                }
            } else {
                LaunchedEffect(sheet) {
                    onIntent(ReadBookIntent.DismissSheet)
                }
            }
        }

        is ReadBookSheet.ChangeBookSource -> {
            val changeSourceSheet = state.activeSheet
            val changeSourceBook = state.book
            if (changeSourceBook == null) {
                LaunchedEffect(changeSourceSheet) {
                    onIntent(ReadBookIntent.DismissSheet)
                }
            }
            if (changeSourceBook != null) {
                ChangeSourceSheet(
                    show = true,
                    oldBook = changeSourceBook,
                    fromReadBookActivity = true,
                    allowAddAsNew = false,
                    dismissOnReplaceStart = true,
                    onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                    onReplace = { _, newBook, toc, _ ->
                        onIntent(ReadBookIntent.DismissSheet)
                        onIntent(ReadBookIntent.ChangeSource(newBook, toc))
                    },
                    onReplaceBook = { newBook ->
                        onIntent(ReadBookIntent.ChangeSourceBook(newBook))
                    },
                    onAddAsNew = { newBook, toc ->
                        onIntent(ReadBookIntent.DismissSheet)
                        onIntent(ReadBookIntent.AddSourceAsNewBook(newBook, toc))
                    },
                )
            }
        }

        null -> {}

        // Sheets using AppModalBottomSheet are composed unconditionally above
        else -> {}
    }

    ActionReminder(
        reminder = if (state.menuVisible) null else state.activeReminder,
        onAction = { reminder -> reminder.actionIntent?.let { onIntent(it) } },
        onDismiss = { onIntent(ReadBookIntent.DismissReminder) },
    )
}
