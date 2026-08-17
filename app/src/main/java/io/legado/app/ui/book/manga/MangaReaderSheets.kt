package io.legado.app.ui.book.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.sheet.ReaderBookHeader
import io.legado.app.ui.book.read.sheet.ReaderBookHeaderState
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reader.ReaderMenuActionSquare

private data class MangaMoreAction(
    val label: String,
    val icon: ImageVector,
    val intent: MangaReaderIntent,
)

@Composable
internal fun MangaReaderSourceActionsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        contentPaddingEnabled = false,
    ) {
        fun dispatch(intent: MangaReaderIntent) {
            onIntent(MangaReaderIntent.DismissSheet)
            onIntent(intent)
        }
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(
                state = ReaderBookHeaderState(
                    bookUrl = state.bookUrl,
                    sourceUrl = state.sourceUrl.orEmpty(),
                    sourceName = state.sourceName,
                    title = state.bookName,
                    author = state.bookAuthor,
                    coverUrl = state.coverUrl,
                    customCoverUrl = state.customCoverUrl,
                    chapterTitle = state.chapterName,
                    chapterIndex = state.chapterIndex,
                    chapterCount = state.chapterCount,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        val actions = listOf(
            MangaMoreAction(stringResource(R.string.change_origin), Icons.Default.SwapHoriz, MangaReaderIntent.ChangeSource),
            MangaMoreAction(stringResource(R.string.refresh), Icons.Default.Refresh, MangaReaderIntent.RefreshChapter),
            MangaMoreAction(stringResource(R.string.login), Icons.AutoMirrored.Filled.Login, MangaReaderIntent.OpenSourceLogin),
            MangaMoreAction(stringResource(R.string.manga_reader_buy_chapter), Icons.Default.ShoppingCart, MangaReaderIntent.RequestPayCurrentChapter),
            MangaMoreAction(stringResource(R.string.edit_source), Icons.Default.Edit, MangaReaderIntent.OpenSourceEdit),
            MangaMoreAction(stringResource(R.string.disable_source), Icons.Default.MoreVert, MangaReaderIntent.DisableCurrentSource),
        )
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)) {
            actions.chunked(4).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        ReaderMenuActionSquare(
                            icon = action.icon,
                            text = action.label,
                            modifier = Modifier.weight(1f),
                            onClick = { dispatch(action.intent) },
                        )
                    }
                    repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun MangaReaderPageActionsSheet(
    hasSpread: Boolean,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        contentPaddingEnabled = false,
    ) {
        val actions = buildList {
            add(
                MangaMoreAction(
                    stringResource(R.string.save_image), Icons.Default.Save,
                    MangaReaderIntent.ExecutePageAction(MangaPageAction.SAVE)
                )
            )
            add(
                MangaMoreAction(
                    stringResource(R.string.share), Icons.Default.Share,
                    MangaReaderIntent.ExecutePageAction(MangaPageAction.SHARE)
                )
            )
            add(
                MangaMoreAction(
                    stringResource(R.string.copy_text), Icons.Default.ContentCopy,
                    MangaReaderIntent.ExecutePageAction(MangaPageAction.COPY)
                )
            )
            add(
                MangaMoreAction(
                    stringResource(R.string.manga_reader_set_cover), Icons.Default.Image,
                    MangaReaderIntent.ExecutePageAction(MangaPageAction.SET_COVER)
                )
            )
            if (hasSpread) {
                add(
                    MangaMoreAction(
                        stringResource(R.string.manga_reader_save_spread), Icons.Default.Save,
                        MangaReaderIntent.ExecutePageAction(MangaPageAction.SAVE_SPREAD)
                    )
                )
                add(
                    MangaMoreAction(
                        stringResource(R.string.manga_reader_share_spread), Icons.Default.Share,
                        MangaReaderIntent.ExecutePageAction(MangaPageAction.SHARE_SPREAD)
                    )
                )
                add(
                    MangaMoreAction(
                        stringResource(R.string.manga_reader_copy_spread),
                        Icons.Default.ContentCopy,
                        MangaReaderIntent.ExecutePageAction(MangaPageAction.COPY_SPREAD)
                    )
                )
            }
        }
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)) {
            actions.chunked(4).forEach { rowActions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowActions.forEach { action ->
                        ReaderMenuActionSquare(
                            action.icon,
                            action.label,
                            Modifier.weight(1f),
                            onClick = { onIntent(action.intent) },
                        )
                    }
                    repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun MangaReaderCacheActionsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
        contentPaddingEnabled = false,
    ) {
        val actions = listOf(
            MangaMoreAction(
                stringResource(R.string.manga_reader_cache_current),
                Icons.Default.Download,
                MangaReaderIntent.CacheChapters(MangaCacheSelection.CURRENT),
            ),
            MangaMoreAction(
                stringResource(R.string.manga_reader_cache_following),
                Icons.Default.CloudDownload,
                MangaReaderIntent.CacheChapters(MangaCacheSelection.FOLLOWING),
            ),
            MangaMoreAction(
                stringResource(R.string.manga_reader_cache_all),
                Icons.Default.DownloadForOffline,
                MangaReaderIntent.CacheChapters(MangaCacheSelection.ALL),
            ),
        )
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(
                state = ReaderBookHeaderState(
                    bookUrl = state.bookUrl,
                    sourceUrl = state.sourceUrl.orEmpty(),
                    sourceName = state.sourceName,
                    title = state.bookName,
                    author = state.bookAuthor,
                    coverUrl = state.coverUrl,
                    customCoverUrl = state.customCoverUrl,
                    chapterTitle = state.chapterName,
                    chapterIndex = state.chapterIndex,
                    chapterCount = state.chapterCount,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                ReaderMenuActionSquare(
                    icon = action.icon,
                    text = action.label,
                    modifier = Modifier.weight(1f),
                    onClick = { onIntent(action.intent) },
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}
