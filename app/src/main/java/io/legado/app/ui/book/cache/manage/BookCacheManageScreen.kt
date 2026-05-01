package io.legado.app.ui.book.cache.manage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookCacheManageRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BookCacheManageViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.onIntent(BookCacheManageIntent.Initialize)
    }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookCacheManageEffect.ShowMessage -> context.toastOnUi(effect.message)
            }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookCacheManageScreen(
        state = state,
        onBackClick = onBackClick,
        onIntent = viewModel::onIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookCacheManageScreen(
    state: BookCacheManageUiState,
    onBackClick: () -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var pendingDeleteBook by remember { mutableStateOf<BookCacheBookItem?>(null) }
    var pendingDeleteChapter by remember { mutableStateOf<Pair<BookCacheBookItem, BookCacheChapterItem>?>(null) }
    val allBooks = state.shelfBooks + state.notShelfBooks
    val hasRunningDownload = allBooks.any { it.hasActiveDownload }
    val hasDownloadTarget = allBooks.any { it.cachedCount < it.totalCount }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = "缓存管理",
                subtitle = state.downloadSummary.takeIf { it.isNotBlank() },
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(BookCacheManageIntent.Refresh) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新"
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (hasRunningDownload || hasDownloadTarget) {
                FloatingActionButton(
                    onClick = {
                        if (hasRunningDownload) {
                            onIntent(BookCacheManageIntent.StopAllDownloads)
                        } else {
                            onIntent(BookCacheManageIntent.StartAllDownloads)
                        }
                    }
                ) {
                    AppIcon(
                        imageVector = if (hasRunningDownload) Icons.Default.Stop else Icons.Default.Download,
                        contentDescription = if (hasRunningDownload) "停止下载" else "开始下载"
                    )
                }
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cacheSection(
                    title = "书架书籍",
                    emptyText = "没有书架内书籍缓存或下载任务",
                    books = state.shelfBooks,
                    expandedBookUrls = state.expandedBookUrls,
                    chaptersByBookUrl = state.chaptersByBookUrl,
                    onToggleExpanded = { bookUrl ->
                        onIntent(BookCacheManageIntent.ToggleBookExpanded(bookUrl))
                    },
                    onIntent = onIntent,
                    onDeleteBook = { pendingDeleteBook = it },
                    onDeleteChapter = { book, chapter -> pendingDeleteChapter = book to chapter }
                )
                cacheSection(
                    title = "未在书架",
                    emptyText = "没有未在书架的书籍下载状态",
                    books = state.notShelfBooks,
                    expandedBookUrls = state.expandedBookUrls,
                    chaptersByBookUrl = state.chaptersByBookUrl,
                    onToggleExpanded = { bookUrl ->
                        onIntent(BookCacheManageIntent.ToggleBookExpanded(bookUrl))
                    },
                    onIntent = onIntent,
                    onDeleteBook = { pendingDeleteBook = it },
                    onDeleteChapter = { book, chapter -> pendingDeleteChapter = book to chapter }
                )
            }
        }
    }

    DeleteBookCacheDialog(
        item = pendingDeleteBook,
        onConfirm = { item ->
            onIntent(BookCacheManageIntent.DeleteBookCache(item.bookUrl))
            pendingDeleteBook = null
        },
        onDismiss = { pendingDeleteBook = null }
    )
    DeleteChapterCacheDialog(
        item = pendingDeleteChapter,
        onConfirm = { book, chapter ->
            onIntent(
                BookCacheManageIntent.DeleteChapterCache(
                    book.bookUrl,
                    chapter.chapterUrl,
                    chapter.title,
                    chapter.index,
                )
            )
            pendingDeleteChapter = null
        },
        onDismiss = { pendingDeleteChapter = null }
    )
}

private fun LazyListScope.cacheSection(
    title: String,
    emptyText: String,
    books: List<BookCacheBookItem>,
    expandedBookUrls: Set<String>,
    chaptersByBookUrl: Map<String, List<BookCacheChapterItem>>,
    onToggleExpanded: (String) -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
    onDeleteBook: (BookCacheBookItem) -> Unit,
    onDeleteChapter: (BookCacheBookItem, BookCacheChapterItem) -> Unit,
) {
    item(key = "$title-header") {
        AppText(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = LegadoTheme.typography.titleSmallEmphasized,
            color = LegadoTheme.colorScheme.primary
        )
    }
    if (books.isEmpty()) {
        item(key = "$title-empty") {
            TextCard(
                text = emptyText,
                modifier = Modifier.fillMaxWidth(),
                verticalPadding = 12.dp,
                horizontalPadding = 12.dp
            )
        }
    } else {
        books.forEach { item ->
            val bookUrl = item.bookUrl
            val expanded = expandedBookUrls.contains(bookUrl)
            item(key = "$title-book-$bookUrl") {
                BookCacheBookCard(
                    item = item,
                    expanded = expanded,
                    onToggleExpanded = { onToggleExpanded(bookUrl) },
                    onIntent = onIntent,
                    onDeleteBook = onDeleteBook,
                    modifier = Modifier.animateItem()
                )
            }
            if (expanded) {
                items(
                    items = chaptersByBookUrl[bookUrl].orEmpty(),
                    key = { chapter -> "$title-chapter-$bookUrl-${chapter.chapterUrl}" }
                ) { chapter ->
                    BookCacheChapterRow(
                        item = chapter,
                        modifier = Modifier.animateItem(),
                        onDownload = {
                            onIntent(
                                BookCacheManageIntent.DownloadChapter(
                                    bookUrl,
                                    chapter.index
                                )
                            )
                        },
                        onStop = {
                            onIntent(
                                BookCacheManageIntent.StopChapterDownload(
                                    bookUrl,
                                    chapter.index
                                )
                            )
                        },
                        onDelete = { onDeleteChapter(item, chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCacheBookCard(
    item: BookCacheBookItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onIntent: (BookCacheManageIntent) -> Unit,
    onDeleteBook: (BookCacheBookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "BookCacheExpandArrow"
    )
    NormalCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onToggleExpanded,
        containerColor = LegadoTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer(rotationZ = arrowRotation)
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = item.name,
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AppText(
                        text = item.author,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextCard(
                    text = "${item.cachedCount}/${item.totalCount}",
                    backgroundColor = LegadoTheme.colorScheme.cardContainer,
                )
            }
            AppLinearProgressIndicator(
                progress = item.progress,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = "下载中 ${item.downloadingCount} · 等待 ${item.waitingCount} · 暂停 ${item.pausedCount} · 失败 ${item.errorCount}",
                    modifier = Modifier.weight(1f),
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
                if (item.hasDownloadTask || item.cachedCount < item.totalCount) {
                    SmallTonalIconButton(
                        onClick = {
                            if (item.hasActiveDownload) {
                                onIntent(BookCacheManageIntent.StopBookDownload(item.bookUrl))
                            } else {
                                onIntent(BookCacheManageIntent.StartBookDownload(item.bookUrl))
                            }
                        },
                        imageVector = if (item.hasActiveDownload) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = when {
                            item.hasActiveDownload -> "暂停本书下载"
                            item.isPaused -> "继续本书下载"
                            else -> "开始本书下载"
                        }
                    )
                }
                SmallTonalIconButton(
                    onClick = { onDeleteBook(item) },
                    imageVector = Icons.Default.Delete,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun BookCacheChapterRow(
    item: BookCacheChapterItem,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.titleSmallEmphasized
            )
            AppText(
                text = chapterStatusText(item),
                maxLines = 1,
                style = LegadoTheme.typography.labelSmall,
                color = if (item.isError) {
                    LegadoTheme.colorScheme.error
                } else if (item.isCached) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (item.isWaiting || item.isDownloading) {
            SmallTonalIconButton(
                onClick = onStop,
                imageVector = Icons.Default.Stop,
                contentDescription = "暂停章节下载"
            )
        } else if (item.isPaused || !item.isCached) {
            SmallTonalIconButton(
                onClick = onDownload,
                imageVector = Icons.Default.Download,
                contentDescription = if (item.isPaused) "继续章节下载" else "下载章节"
            )
        }
        SmallTonalIconButton(
            onClick = onDelete,
            imageVector = Icons.Default.Delete,
            contentDescription = null
        )
    }
}

private fun chapterStatusText(item: BookCacheChapterItem): String {
    return when {
        item.isDownloading -> "下载中"
        item.isWaiting -> "等待下载"
        item.isPaused -> "已暂停"
        item.isError -> "下载失败"
        item.isCached -> "已缓存"
        else -> "未缓存"
    }
}

@Composable
private fun DeleteBookCacheDialog(
    item: BookCacheBookItem?,
    onConfirm: (BookCacheBookItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = item != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        text = "删除《${item?.name.orEmpty()}》的全部缓存，并从下载队列移除？",
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { item?.let(onConfirm) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}

@Composable
private fun DeleteChapterCacheDialog(
    item: Pair<BookCacheBookItem, BookCacheChapterItem>?,
    onConfirm: (BookCacheBookItem, BookCacheChapterItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        show = item != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        text = "删除章节缓存：${item?.second?.title.orEmpty()}？",
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { item?.let { onConfirm(it.first, it.second) } },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}
