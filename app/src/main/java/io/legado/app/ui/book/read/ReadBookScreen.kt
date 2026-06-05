package io.legado.app.ui.book.read

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.info.ChangeSourceSheet
import io.legado.app.ui.book.read.sheet.AppLogSheet
import io.legado.app.ui.book.read.sheet.BgTextConfigSheet
import io.legado.app.ui.book.read.sheet.ChangeChapterSourceSheet
import io.legado.app.ui.book.read.sheet.CharsetConfigSheet
import io.legado.app.ui.book.read.sheet.ClickActionConfigSheet
import io.legado.app.ui.book.read.sheet.ContentEditSheet
import io.legado.app.ui.book.read.sheet.DictSheet
import io.legado.app.ui.book.read.sheet.DownloadSheet
import io.legado.app.ui.book.read.sheet.EffectiveReplacesSheet
import io.legado.app.ui.book.read.sheet.FontSelectSheet
import io.legado.app.ui.book.read.sheet.MoreConfigSheet
import io.legado.app.ui.book.read.sheet.PageAnimConfigSheet
import io.legado.app.ui.book.read.sheet.PageKeyConfigSheet
import io.legado.app.ui.book.read.sheet.PhotoSheet
import io.legado.app.ui.book.read.sheet.ReadAloudNumberConfigSheet
import io.legado.app.ui.book.read.sheet.ReadAloudConfigSheet
import io.legado.app.ui.book.read.sheet.RegexColorConfigSheet
import io.legado.app.ui.book.read.sheet.ShadowSetSheet
import io.legado.app.ui.book.read.sheet.SimulatedReadingSheet
import io.legado.app.ui.book.read.sheet.SpeakEngineConfigSheet
import io.legado.app.ui.book.read.sheet.TitleBarIconSheet
import io.legado.app.ui.book.read.sheet.ToolButtonConfigSheet
import io.legado.app.ui.book.read.sheet.UnderlineConfigSheet
import io.legado.app.ui.widget.components.alert.AppAlertDialog
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
            else -> onBack()
        }
    }

    // Dialogs driven by activeDialog state
    when (val dialog = state.activeDialog) {
        is ReadBookDialog.ConfirmRestoreProgress -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                title = stringResource(R.string.restore_progress),
                text = stringResource(R.string.found_cloud_progress),
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    onIntent(ReadBookIntent.SureNewProgress(dialog.progress))
                    onIntent(ReadBookIntent.DismissDialog)
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
            )
        }

        is ReadBookDialog.SureSyncProgress -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                title = stringResource(R.string.sync_progress),
                text = stringResource(R.string.progress_exceeds_cloud),
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    onIntent(ReadBookIntent.SureSyncProgress(dialog.progress))
                    onIntent(ReadBookIntent.DismissDialog)
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
            )
        }

        is ReadBookDialog.ConfirmSkipToChapter -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                title = stringResource(R.string.chapter_list),
                text = stringResource(R.string.confirm_skip_to_chapter),
                confirmText = stringResource(R.string.ok),
                onConfirm = { onIntent(ReadBookIntent.DismissDialog) },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
            )
        }

        is ReadBookDialog.ConfirmChapterPay -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                title = stringResource(R.string.chapter_pay),
                text = dialog.chapterTitle,
                confirmText = stringResource(R.string.ok),
                onConfirm = {
                    onIntent(ReadBookIntent.DismissDialog)
                    onIntent(ReadBookIntent.ConfirmPayAction)
                },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(ReadBookIntent.DismissDialog) },
            )
        }

        is ReadBookDialog.StackTrace -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                title = stringResource(R.string.log),
                text = dialog.text,
                confirmText = stringResource(R.string.ok),
                onConfirm = { onIntent(ReadBookIntent.DismissDialog) },
            )
        }

        null -> {}
    }

    // Sheets driven by activeSheet state
    when (state.activeSheet) {
        is ReadBookSheet.ShadowSet -> {
            ShadowSetSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onIntent = onIntent,
            )
        }

        is ReadBookSheet.EffectiveReplaces -> {
            EffectiveReplacesSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onOpenReplaceEditor = { id, pattern ->
                    onIntent(ReadBookIntent.OpenReplaceEditor(id, pattern))
                },
                onReplaceRuleChanged = { onIntent(ReadBookIntent.ReplaceRuleChanged) },
            )
        }

        is ReadBookSheet.UnderlineConfig -> {
            UnderlineConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onIntent = onIntent,
            )
        }

        is ReadBookSheet.FontSelect -> {
            FontSelectSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onSelectFont = { onIntent(ReadBookIntent.SelectFont(it)) },
                onSelectSystemTypeface = { onIntent(ReadBookIntent.SelectSystemTypeface(it)) },
                onOpenFolderPicker = { onIntent(ReadBookIntent.OpenFontFolderPicker) },
            )
        }

        is ReadBookSheet.ToolButtonConfig -> {
            ToolButtonConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onSaved = { onIntent(ReadBookIntent.RefreshToolButtons) },
            )
        }

        is ReadBookSheet.TitleBarIconConfig -> {
            TitleBarIconSheet(
                customIcons = state.menuConfig.titleBarCustomIcons,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onSaved = { onIntent(ReadBookIntent.RefreshTitleBarIcons) },
                onIntent = onIntent,
            )
        }

        is ReadBookSheet.RegexColorConfig -> {
            RegexColorConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onIntent = onIntent,
                onOpenFontSelect = { index ->
                    onIntent(ReadBookIntent.SelectRegexColorFont(index))
                },
            )
        }

        is ReadBookSheet.ContentEdit -> {
            ContentEditSheet(
                state = state,
                onIntent = onIntent,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.MoreConfig -> {
            MoreConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onIntent = onIntent,
                onOpenClickRegionalConfig = {
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ClickActionConfig))
                },
                onOpenPageKeyConfig = {
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageKeyConfig))
                },
            )
        }

        is ReadBookSheet.ReadAloudConfig -> {
            ReadAloudConfigSheet(
                state = state,
                onIntent = onIntent,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.SpeakEngineConfig -> {
            SpeakEngineConfigSheet(
                items = state.ttsEngineItems,
                selectedValue = state.selectedTtsEngine,
                onSelect = { onIntent(ReadBookIntent.ApplySpeakEngine(it)) },
                onDismissRequest = {
                    onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ReadAloudConfig))
                },
            )
        }

        is ReadBookSheet.PreDownloadConfig -> {
            ReadAloudNumberConfigSheet(
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
        }

        is ReadBookSheet.AudioCacheCleanConfig -> {
            ReadAloudNumberConfigSheet(
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
        }

        is ReadBookSheet.ClickActionConfig -> {
            ClickActionConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.PageKeyConfig -> {
            PageKeyConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.PageAnim -> {
            PageAnimConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onAnimChanged = { onIntent(ReadBookIntent.PageAnimChanged) },
            )
        }

        is ReadBookSheet.Download -> {
            DownloadSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onDownload = { start, end ->
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.DownloadChapters(start, end))
                },
            )
        }

        is ReadBookSheet.Charset -> {
            CharsetConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.SimulatedReading -> {
            SimulatedReadingSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onApply = { onIntent(ReadBookIntent.ApplySimulatedReading) },
            )
        }

        is ReadBookSheet.AppLog -> {
            AppLogSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onShowStackTrace = { text ->
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.ShowStackTrace(text))
                },
            )
        }

        is ReadBookSheet.BgTextConfig -> {
            BgTextConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onIntent = onIntent,
                onSelectImage = { onIntent(ReadBookIntent.OpenReadStyleImagePicker) },
                onImportConfig = { onIntent(ReadBookIntent.OpenReadStyleImport) },
                onExportConfig = { onIntent(ReadBookIntent.OpenReadStyleExport) },
            )
        }

        is ReadBookSheet.Dict -> {
            DictSheet(
                word = state.activeSheet.word,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.Bookmark -> {
            // Handled by ViewModel — redirects to menu route
        }

        is ReadBookSheet.Photo -> {
            PhotoSheet(
                src = state.activeSheet.src,
                sourceOrigin = state.activeSheet.sourceOrigin,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

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
            val sheet = state.activeSheet
            val book = state.book
            if (book != null) {
                ChangeSourceSheet(
                    show = true,
                    oldBook = book,
                    onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                    onReplace = { _, newBook, toc, _ ->
                        onIntent(ReadBookIntent.DismissSheet)
                        onIntent(ReadBookIntent.ChangeSource(newBook, toc))
                    },
                    onAddAsNew = { newBook, toc ->
                        onIntent(ReadBookIntent.DismissSheet)
                        onIntent(ReadBookIntent.AddSourceAsNewBook(newBook, toc))
                    },
                )
            } else {
                LaunchedEffect(sheet) {
                    onIntent(ReadBookIntent.DismissSheet)
                }
            }
        }

        null -> {}
    }
}
