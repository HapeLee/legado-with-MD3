package io.legado.app.ui.book.manga

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.reader.DefaultReaderMenuBottomSurface
import io.legado.app.ui.widget.components.reader.DefaultReaderMenuTopSurface
import io.legado.app.ui.widget.components.reader.ReaderMenuAction
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedBottom
import io.legado.app.ui.widget.components.reader.ReaderMenuAnimatedTop
import io.legado.app.ui.widget.components.reader.ReaderMenuDismissLayer
import io.legado.app.ui.widget.components.reader.ReaderMenuIconButton
import io.legado.app.ui.widget.components.reader.ReaderMenuSlider
import io.legado.app.ui.widget.components.reader.ReaderMenuToolRow

@Composable
internal fun BoxScope.MangaFooter(state: MangaReaderUiState) {
    val page = state.pages.getOrNull(state.currentItemIndex) as? MangaReaderItemUi.Page ?: return
    val settings = state.settings
    if (settings.hideFooter) return
    val progress = if (page.chapterCount <= 0 || page.pageCount <= 0) 0.0 else {
        (page.chapterIndex.toDouble() + (page.pageIndex + 1.0) / page.pageCount) / page.chapterCount
    }
    val pageLabel = stringResource(R.string.manga_reader_page_label)
    val chapterLabel = stringResource(R.string.manga_reader_chapter_label)
    val progressLabel = stringResource(R.string.manga_reader_progress_label)
    val text = buildString {
        if (!settings.hideChapterName) append(page.chapterName).append(' ')
        if (!settings.hidePageNumber) {
            if (!settings.hidePageNumberLabel) append(pageLabel).append(' ')
            append("${page.pageIndex + 1}/${page.pageCount} ")
        }
        if (!settings.hideChapter) {
            if (!settings.hideChapterLabel) append(chapterLabel).append(' ')
            append("${page.chapterIndex + 1}/${page.chapterCount} ")
        }
        if (!settings.hideProgress) {
            if (!settings.hideProgressLabel) append(progressLabel).append(' ')
            append("%.1f%%".format((progress * 100).coerceAtMost(100.0)))
        }
    }.trim()
    val alignment = when (settings.footerAlignment) {
        1 -> Alignment.BottomCenter
        2 -> Alignment.BottomEnd
        else -> Alignment.BottomStart
    }
    Text(
        text = text,
        color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        style = LegadoTheme.typography.labelSmall,
        modifier = Modifier
            .align(alignment)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
internal fun BoxScope.MangaReaderMenu(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val readingPageDescription = stringResource(R.string.manga_reader_page_semantics)
    ReaderMenuDismissLayer(
        visible = state.menuVisible,
        onDismiss = { onIntent(MangaReaderIntent.HideMenu) },
    )
    ReaderMenuAnimatedTop(
        visible = state.menuVisible,
    ) {
        DefaultReaderMenuTopSurface {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReaderMenuIconButton(
                    icon = AppIcons.Back,
                    description = stringResource(R.string.back),
                    onClick = { onIntent(MangaReaderIntent.BackPressed) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.bookName,
                        maxLines = 1,
                        style = LegadoTheme.typography.titleMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenBookInfo) },
                        ),
                    )
                    Text(
                        state.chapterName,
                        maxLines = 1,
                        style = LegadoTheme.typography.labelMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenChapterUrl) },
                        ),
                    )
                }
                ReaderMenuIconButton(Icons.Filled.SwapHoriz, stringResource(R.string.change_origin)) {
                    onIntent(MangaReaderIntent.ChangeSource)
                }
                ReaderMenuIconButton(Icons.Filled.Refresh, stringResource(R.string.refresh)) {
                    onIntent(MangaReaderIntent.RefreshChapter)
                }
                ReaderMenuIconButton(Icons.Filled.MoreVert, stringResource(R.string.book_source)) {
                    onIntent(MangaReaderIntent.OpenSourceActions)
                }
            }
        }
    }
    ReaderMenuAnimatedBottom(
        visible = state.menuVisible,
    ) {
        DefaultReaderMenuBottomSurface {
            AnimatedContent(
                targetState = state.settingsCategory != null,
                transitionSpec = {
                    (expandVertically(expandFrom = Alignment.Bottom) + fadeIn())
                        .togetherWith(shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut())
                },
                label = "mangaMenuExpand",
            ) { isSettings ->
                if (isSettings) {
                    MangaSettingsPanel(state, onIntent)
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        if (state.pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReaderMenuIconButton(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.previous_chapter)
                                ) {
                                    onIntent(MangaReaderIntent.PreviousChapter)
                                }
                                ReaderMenuSlider(
                                    value = state.currentPage.toFloat()
                                        .coerceIn(0f, (state.pageCount - 1).toFloat()),
                                    onValueChange = { onIntent(MangaReaderIntent.SeekToPage(it.toInt())) },
                                    valueRange = 0f..(state.pageCount - 1).toFloat(),
                                    steps = (state.pageCount - 2).coerceAtLeast(0),
                                    accessibilityLabel = readingPageDescription,
                                    modifier = Modifier.weight(1f),
                                )
                                ReaderMenuIconButton(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    stringResource(R.string.next_chapter)
                                ) {
                                    onIntent(MangaReaderIntent.NextChapter)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ReaderMenuToolRow(
                            listOf(
                                ReaderMenuAction(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    stringResource(R.string.chapter_list)
                                ) {
                                    onIntent(MangaReaderIntent.OpenCatalog)
                                },
                                ReaderMenuAction(
                                    icon = Icons.Filled.AutoMode,
                                    description = if (state.autoReadEnabled) stringResource(R.string.stop) else stringResource(
                                        R.string.manga_reader_auto_short
                                    ),
                                    onClick = { onIntent(MangaReaderIntent.ToggleAutoRead) },
                                    onLongClick = {
                                        onIntent(
                                            MangaReaderIntent.OpenSettings(
                                                MangaReaderSettingsCategory.AUTO_READ
                                            )
                                        )
                                    },
                                ),
                                ReaderMenuAction(
                                    Icons.Filled.Tune,
                                    stringResource(R.string.manga_reader_page_settings)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.READER
                                        )
                                    )
                                },
                                ReaderMenuAction(
                                    Icons.Filled.FilterAlt,
                                    stringResource(R.string.manga_reader_filter_short)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.FILTER
                                        )
                                    )
                                },
                                ReaderMenuAction(
                                    Icons.Filled.TouchApp,
                                    stringResource(R.string.manga_reader_click_area_short)
                                ) {
                                    onIntent(
                                        MangaReaderIntent.OpenSettings(
                                            MangaReaderSettingsCategory.CLICK_ACTIONS
                                        )
                                    )
                                },
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ReaderStatusOverlay(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    if (state.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LegadoTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.errorMessage?.let { message ->
        val errorText = when (message) {
            is MangaReaderText.Dynamic -> message.value
            is MangaReaderText.Resource -> stringResource(
                message.resId,
                *message.args.toTypedArray(),
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = LegadoTheme.colorScheme.surface) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorText, modifier = Modifier.padding(24.dp))
                Button(onClick = { onIntent(MangaReaderIntent.Retry) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
