package io.legado.app.ui.book.read

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.info.ChangeSourceSheet
import io.legado.app.ui.book.read.sheet.AppLogSheet
import io.legado.app.ui.book.read.sheet.AutoReadSheet
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
import io.legado.app.ui.book.read.sheet.ReadAloudConfigSheet
import io.legado.app.ui.book.read.sheet.RegexColorConfigSheet
import io.legado.app.ui.book.read.sheet.ShadowSetSheet
import io.legado.app.ui.book.read.sheet.SimulatedReadingSheet
import io.legado.app.ui.book.read.sheet.ToolButtonConfigSheet
import io.legado.app.ui.book.read.sheet.UnderlineConfigSheet
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
    when (state.activeDialog) {
        is ReadBookDialog.ConfirmRestoreProgress -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onIntent(ReadBookIntent.SureNewProgress(state.activeDialog.progress))
                            onIntent(ReadBookIntent.DismissDialog)
                        }
                    ) {
                        androidx.compose.material3.Text("确定")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { onIntent(ReadBookIntent.DismissDialog) }
                    ) {
                        androidx.compose.material3.Text("取消")
                    }
                },
                title = { androidx.compose.material3.Text("恢复进度") },
                text = { androidx.compose.material3.Text("发现云端进度，是否恢复？") },
            )
        }

        is ReadBookDialog.SureSyncProgress -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onIntent(ReadBookIntent.SureSyncProgress(state.activeDialog.progress))
                            onIntent(ReadBookIntent.DismissDialog)
                        }
                    ) {
                        androidx.compose.material3.Text("确定")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { onIntent(ReadBookIntent.DismissDialog) }
                    ) {
                        androidx.compose.material3.Text("取消")
                    }
                },
                title = { androidx.compose.material3.Text("同步进度") },
                text = { androidx.compose.material3.Text("当前阅读进度已超过云端，是否覆盖？") },
            )
        }

        is ReadBookDialog.ConfirmSkipToChapter -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { onIntent(ReadBookIntent.DismissDialog) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onIntent(ReadBookIntent.DismissDialog)
                            // The caller should handle the actual skip via a separate intent
                        }
                    ) {
                        androidx.compose.material3.Text("确定")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { onIntent(ReadBookIntent.DismissDialog) }
                    ) {
                        androidx.compose.material3.Text("取消")
                    }
                },
                title = { androidx.compose.material3.Text("跳转章节") },
                text = { androidx.compose.material3.Text(stringResource(R.string.confirm_skip_to_chapter)) },
            )
        }

        null -> {}
    }

    // Sheets driven by activeSheet state
    when (state.activeSheet) {
        is ReadBookSheet.ShadowSet -> {
            ShadowSetSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.AutoRead -> {
            AutoReadSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onOpenChapterList = { onIntent(ReadBookIntent.OpenChapterList) },
                onShowMainMenu = { onIntent(ReadBookIntent.ShowMenu) },
                onStopAutoPage = { onIntent(ReadBookIntent.StopAutoPage) },
                onShowPageAnimConfig = { onIntent(ReadBookIntent.ShowPageAnimConfig) },
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

        is ReadBookSheet.RegexColorConfig -> {
            RegexColorConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onOpenFontSelect = { index ->
                    onIntent(ReadBookIntent.SelectRegexColorFont(index))
                },
            )
        }

        is ReadBookSheet.ContentEdit -> {
            ContentEditSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.MoreConfig -> {
            MoreConfigSheet(
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
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
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                onSelectSpeakEngine = {
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.SelectSpeakEngine)
                },
                onOpenPreDownloadNumPicker = {
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.OpenPreDownloadNumPicker)
                },
                onOpenCacheCleanTimePicker = {
                    onIntent(ReadBookIntent.DismissSheet)
                    onIntent(ReadBookIntent.OpenCacheCleanTimePicker)
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
                onSelectImage = { /* TODO: needs Activity for file picker */ },
                onImportConfig = { /* TODO: needs Activity for file picker */ },
                onExportConfig = { /* TODO: needs Activity for file picker */ },
                onSelectColor = { /* TODO: needs Activity for color picker */ },
            )
        }

        is ReadBookSheet.Dict -> {
            DictSheet(
                word = state.activeSheet.word,
                onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
            )
        }

        is ReadBookSheet.Bookmark -> {
            val bookmark = state.activeSheet.bookmark
            LaunchedEffect(bookmark) {
                onIntent(ReadBookIntent.DismissSheet)
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.Bookmark(bookmark)))
            }
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
            onIntent(ReadBookIntent.DismissSheet)
        }

        is ReadBookSheet.ChangeChapterSource -> {
            state.book?.let { book ->
                val viewModel = androidx.compose.runtime.key(
                    "chapter-source-${book.bookUrl}-${state.activeSheet.chapterIndex}"
                ) {
                    org.koin.androidx.compose.koinViewModel<io.legado.app.ui.book.changesource.ChangeChapterSourceViewModel>()
                }
                LaunchedEffect(book.bookUrl, state.activeSheet.chapterIndex) {
                    viewModel.initData(
                        book,
                        state.activeSheet.chapterIndex,
                        state.activeSheet.chapterTitle
                    )
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                ChangeChapterSourceSheet(
                    state = viewModel.uiState.collectAsStateWithLifecycle().value,
                    onIntent = viewModel::onIntent,
                    effects = viewModel.effects,
                    onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                    bookScoreFlow = viewModel::bookScoreFlow,
                    onBookScoreClick = viewModel::onBookScoreClick,
                    onEditSource = { sourceUrl ->
                        val intent = android.content.Intent(
                            context,
                            io.legado.app.ui.book.source.edit.BookSourceEditActivity::class.java
                        )
                        intent.putExtra("sourceUrl", sourceUrl)
                        context.startActivity(intent)
                    },
                )
                // Handle ReplaceContent effect
                LaunchedEffect(Unit) {
                    viewModel.effects.collectLatest { effect ->
                        when (effect) {
                            is io.legado.app.ui.book.changesource.ChangeChapterSourceEffect.ReplaceContent -> {
                                onIntent(ReadBookIntent.DismissSheet)
                                onIntent(ReadBookIntent.SaveChapterContent(effect.content))
                            }

                            else -> {}
                        }
                    }
                }
            } ?: onIntent(ReadBookIntent.DismissSheet)
        }

        is ReadBookSheet.ChangeBookSource -> {
            state.book?.let { book ->
                ChangeSourceSheet(
                    show = true,
                    oldBook = book,
                    onDismissRequest = { onIntent(ReadBookIntent.DismissSheet) },
                    onReplace = { _, newBook, toc, _ ->
                        onIntent(ReadBookIntent.DismissSheet)
                        onIntent(ReadBookIntent.ChangeSource(newBook, toc))
                    },
                    onAddAsNew = { newBook, toc ->
                        // Add to bookshelf
                        onIntent(ReadBookIntent.DismissSheet)
                    },
                )
            } ?: onIntent(ReadBookIntent.DismissSheet)
        }

        null -> {}
    }
}
