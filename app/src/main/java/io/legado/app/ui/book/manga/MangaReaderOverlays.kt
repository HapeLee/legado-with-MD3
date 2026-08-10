package io.legado.app.ui.book.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R

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
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        style = MaterialTheme.typography.labelSmall,
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
    AnimatedVisibility(
        visible = state.menuVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                .combinedClickable(onClick = { onIntent(MangaReaderIntent.HideMenu) }),
        )
    }
    AnimatedVisibility(
        visible = state.menuVisible,
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onIntent(MangaReaderIntent.BackPressed) }) { Text("‹") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.bookName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenBookInfo) },
                        ),
                    )
                    Text(
                        state.chapterName,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.combinedClickable(
                            onClick = { onIntent(MangaReaderIntent.OpenChapterUrl) },
                        ),
                    )
                }
                TextButton(onClick = { onIntent(MangaReaderIntent.ChangeSource) }) { Text(stringResource(R.string.change_origin)) }
                TextButton(onClick = { onIntent(MangaReaderIntent.RefreshChapter) }) { Text(stringResource(R.string.refresh)) }
                TextButton(onClick = { onIntent(MangaReaderIntent.OpenSourceActions) }) { Text(stringResource(R.string.book_source)) }
            }
        }
    }
    AnimatedVisibility(
        visible = state.menuVisible,
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                if (state.pageCount > 1) {
                    Slider(
                        value = state.currentPage.toFloat().coerceIn(0f, (state.pageCount - 1).toFloat()),
                        onValueChange = { onIntent(MangaReaderIntent.SeekToPage(it.toInt())) },
                        valueRange = 0f..(state.pageCount - 1).toFloat(),
                        steps = (state.pageCount - 2).coerceAtLeast(0),
                        modifier = Modifier.semantics { contentDescription = readingPageDescription },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ReaderMenuButton(stringResource(R.string.previous_chapter)) { onIntent(MangaReaderIntent.PreviousChapter) }
                    ReaderMenuButton(stringResource(R.string.chapter_list)) { onIntent(MangaReaderIntent.OpenCatalog) }
                    ReaderMenuButton(if (state.autoReadEnabled) stringResource(R.string.stop) else stringResource(R.string.manga_reader_auto_short)) {
                        onIntent(MangaReaderIntent.ToggleAutoRead)
                    }
                    ReaderMenuButton(stringResource(R.string.next_chapter)) { onIntent(MangaReaderIntent.NextChapter) }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ReaderMenuButton(stringResource(R.string.manga_reader_page_settings)) { onIntent(MangaReaderIntent.OpenAutoReadSettings) }
                    ReaderMenuButton(stringResource(R.string.footer)) { onIntent(MangaReaderIntent.OpenReaderSettings) }
                    ReaderMenuButton(stringResource(R.string.manga_reader_filter_short)) { onIntent(MangaReaderIntent.OpenColorFilter) }
                    ReaderMenuButton(stringResource(R.string.manga_reader_click_area_short)) { onIntent(MangaReaderIntent.OpenClickSettings) }
                    ReaderMenuButton(stringResource(R.string.manga_reader_preload_short)) { onIntent(MangaReaderIntent.OpenPreDownloadSettings) }
                }
            }
        }
    }
}

@Composable
private fun ReaderMenuButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text) }
}

@Composable
internal fun BoxScope.ReaderStatusOverlay(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    if (state.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    state.errorMessage?.let { message ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(message, modifier = Modifier.padding(24.dp))
                Button(onClick = { onIntent(MangaReaderIntent.Retry) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
