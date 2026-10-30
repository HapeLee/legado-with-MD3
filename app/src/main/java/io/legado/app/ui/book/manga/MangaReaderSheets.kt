package io.legado.app.ui.book.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.sheet.ReaderBookHeader
import io.legado.app.ui.book.read.sheet.ReaderBookHeaderState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reader.ReaderMenuActionSquare
import io.legado.app.ui.widget.components.text.AppText

private data class MangaMoreAction(
    val label: String,
    val icon: ImageVector,
    val intent: MangaReaderIntent,
    val longClickIntent: MangaReaderIntent? = null,
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
        val actions = buildList {
            if (state.sourceCustomButtonAvailable) add(
                MangaMoreAction(
                    stringResource(R.string.custom_button),
                    Icons.Default.Extension,
                    MangaReaderIntent.SourceCustomButton(false),
                    MangaReaderIntent.SourceCustomButton(true),
                )
            )
            addAll(
                listOf(
                    MangaMoreAction(
                        stringResource(R.string.change_origin),
                        Icons.Default.SwapHoriz,
                        MangaReaderIntent.ChangeSource
                    ),
                    MangaMoreAction(
                        stringResource(R.string.refresh),
                        Icons.Default.Refresh,
                        MangaReaderIntent.RefreshChapter
                    ),
                    MangaMoreAction(
                        stringResource(R.string.login),
                        Icons.AutoMirrored.Filled.Login,
                        MangaReaderIntent.OpenSourceLogin
                    ),
                    MangaMoreAction(
                        stringResource(R.string.manga_reader_buy_chapter),
                        Icons.Default.ShoppingCart,
                        MangaReaderIntent.RequestPayCurrentChapter
                    ),
                    MangaMoreAction(
                        stringResource(R.string.edit_source),
                        Icons.Default.Edit,
                        MangaReaderIntent.OpenSourceEdit
                    ),
                    MangaMoreAction(
                        stringResource(R.string.disable_source),
                        Icons.Default.MoreVert,
                        MangaReaderIntent.DisableCurrentSource
                    ),
                )
            )
        }
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)) {
            actions.chunked(4).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowActions.forEach { action ->
                        MangaSourceActionSquare(
                            action = action,
                            dispatch = ::dispatch,
                            modifier = Modifier.weight(1f),
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
private fun MangaSourceActionSquare(
    action: MangaMoreAction,
    dispatch: (MangaReaderIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(action.intent) { mutableStateOf(false) }
    Box(modifier) {
        ReaderMenuActionSquare(
            icon = action.icon,
            text = action.label,
            hasMore = action.longClickIntent != null,
            onClick = { dispatch(action.intent) },
            onMoreClick = { expanded = true },
        )
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            action.longClickIntent?.let { intent ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.source_custom_button_long_action),
                    onClick = { dismiss(); dispatch(intent) },
                )
            }
        }
    }
}

@Composable
internal fun MangaReaderPageActionsSheet(
    hasSpread: Boolean,
    onIntent: (MangaReaderIntent) -> Unit,
    onTranslate: ((Boolean) -> Unit)? = null,
    onSaveTranslation: (() -> Unit)? = null,
    onClearTranslation: (() -> Unit)? = null,
    onRecompose: (() -> Unit)? = null,
    hasTranslation: Boolean = false,
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)) {
            actions.chunked(4).forEachIndexed { rowIndex, rowActions ->
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
                if (rowIndex == 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderMenuActionSquare(
                            icon = Icons.Default.Translate,
                            text = stringResource(if (hasTranslation) R.string.feature_manga_retranslate_short else R.string.feature_manga_translate_short),
                            modifier = Modifier.weight(1f),
                            enabled = onTranslate != null,
                            onClick = { onTranslate?.invoke(hasTranslation) },
                        )
                        ReaderMenuActionSquare(
                            icon = Icons.Default.Layers,
                            text = stringResource(R.string.feature_manga_recompose_short),
                            modifier = Modifier.weight(1f),
                            enabled = onRecompose != null,
                            onClick = { onRecompose?.invoke() },
                        )
                        ReaderMenuActionSquare(
                            icon = Icons.Default.Save,
                            text = stringResource(R.string.feature_manga_save_translation),
                            modifier = Modifier.weight(1f),
                            enabled = onSaveTranslation != null,
                            onClick = { onSaveTranslation?.invoke() },
                        )
                        ReaderMenuActionSquare(
                            icon = Icons.Default.Delete,
                            text = stringResource(R.string.feature_manga_translation_clear_page),
                            modifier = Modifier.weight(1f),
                            enabled = onClearTranslation != null,
                            onClick = { onClearTranslation?.invoke() },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
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

/** 九宫格可配置动作，顺序即下拉菜单顺序；与 [resolveMangaClickAction] 的取值一一对应。 */
private val MangaClickActionEntries = listOf(
    -1 to R.string.non_action,
    0 to R.string.manga_reader_menu,
    1 to R.string.manga_reader_next_page,
    2 to R.string.manga_reader_previous_page,
    3 to R.string.next_chapter,
    4 to R.string.previous_chapter,
)

private fun mangaClickActionLabelRes(action: Int): Int =
    MangaClickActionEntries.firstOrNull { it.first == action }?.second ?: R.string.non_action

/**
 * 九宫格点击区域配置：入口在设置面板「全局」页，整屏覆盖、格子与屏幕三宫格一一对应，
 * 点击格子弹出动作下拉（对齐阅读正文的点击区域配置）；关闭后回到原设置分类。
 */
@Composable
internal fun MangaReaderClickActionsSheet(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    val dismiss = { onIntent(MangaReaderIntent.DismissSheet) }
    BackHandler(onBack = dismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LegadoTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        ) {
            repeat(3) { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        MangaClickAreaCell(
                            action = state.settings.clickActions.getOrElse(index) { 0 },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onPick = { action ->
                                onIntent(MangaReaderIntent.UpdateClickAction(index, action))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaClickAreaCell(
    action: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        NormalCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 16.dp,
            containerColor = LegadoTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = stringResource(mangaClickActionLabelRes(action)),
                    style = LegadoTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            MangaClickActionEntries.forEach { (value, labelRes) ->
                RoundDropdownMenuItem(
                    text = stringResource(labelRes),
                    isSelected = value == action,
                    onClick = {
                        onPick(value)
                        dismiss()
                    },
                )
            }
        }
    }
}
