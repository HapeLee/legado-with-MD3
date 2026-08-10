package io.legado.app.ui.book.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.R
import io.legado.app.help.coil.CoverExtras
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import kotlin.math.ceil

private val LocalReaderViewportSize = staticCompositionLocalOf { IntSize.Zero }
private val LocalMangaAspectRatios = staticCompositionLocalOf<MutableMap<String, Float>> {
    mutableMapOf()
}

@Composable
fun MangaReaderScreen(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = koinInject(),
) {
    BackHandler { onIntent(MangaReaderIntent.BackPressed) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val aspectRatios = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(
        state.autoReadEnabled,
        state.settings.autoReadSpeed,
        state.menuVisible,
        state.activeSheet,
    ) {
        val isWebtoon = state.settings.scrollMode == MangaScrollMode.WEBTOON ||
                state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null || isWebtoon) {
            return@LaunchedEffect
        }
        while (true) {
            delay(state.settings.autoReadSpeed.coerceAtLeast(1) * 1_000L)
            onIntent(MangaReaderIntent.PageStep(1))
        }
    }

    val context = LocalContext.current
    LaunchedEffect(state.currentItemIndex, state.settings.preDownloadCount, state.pages) {
        val preloadCount = state.settings.preDownloadCount.coerceAtLeast(0)
        val start = (state.currentItemIndex - preloadCount).coerceAtLeast(0)
        val end = (state.currentItemIndex + preloadCount + 1)
            .coerceAtMost(state.pages.size)
        state.pages.subList(start.coerceAtMost(end), end)
            .filterIsInstance<MangaReaderItemUi.Page>()
            .forEach { page ->
                imageLoader.enqueue(page.imageRequest(state.settings, context) { ratio ->
                    aspectRatios[page.key] = ratio
                })
            }
    }

    CompositionLocalProvider(
        LocalReaderViewportSize provides viewportSize,
        LocalMangaAspectRatios provides aspectRatios,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .background(state.settings.backgroundColor)
        ) {
            when (state.settings.scrollMode) {
                MangaScrollMode.PAGE_LEFT_TO_RIGHT,
                MangaScrollMode.PAGE_RIGHT_TO_LEFT -> HorizontalMangaPager(state, onIntent, imageLoader)
                MangaScrollMode.PAGE_TOP_TO_BOTTOM -> VerticalMangaPager(state, onIntent, imageLoader)
                else -> WebtoonMangaList(state, onIntent, imageLoader)
            }

            MangaFooter(state)
            MangaReaderMenu(state, onIntent)
            ReaderStatusOverlay(state, onIntent)
        }
    }
    state.activeSheet?.takeIf { it != MangaReaderSheet.ChangeSource }?.let { sheet ->
        MangaReaderSettingsSheet(sheet, state, onIntent)
    }
    if (state.activeSheet == MangaReaderSheet.ChangeSource) {
        state.changeSourceBook?.let { oldBook ->
            ChangeSourceSheet(
                show = true,
                oldBook = oldBook,
                fromReadBookActivity = true,
                allowAddAsNew = true,
                dismissOnReplaceStart = true,
                onDismissRequest = { onIntent(MangaReaderIntent.DismissSheet) },
                onReplace = { _, book, toc, _ ->
                    onIntent(MangaReaderIntent.DismissSheet)
                    onIntent(MangaReaderIntent.ChangeSourceBook(book, toc))
                },
                onAddAsNew = { book, toc ->
                    onIntent(MangaReaderIntent.AddExternalBookToShelf(book, toc))
                },
            )
        }
    }
    AppAlertDialog(
        show = state.activeDialog is MangaReaderDialog.AddToShelf,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissDialog) },
        title = stringResource(R.string.add_to_bookshelf),
        text = stringResource(R.string.check_add_bookshelf, state.bookName),
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(MangaReaderIntent.AddCurrentBookToShelf) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MangaReaderIntent.DiscardCurrentBookAndExit) },
    )
    val payDialog = state.activeDialog as? MangaReaderDialog.ConfirmPay
    AppAlertDialog(
        show = payDialog != null,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissDialog) },
        title = stringResource(R.string.chapter_pay),
        text = payDialog?.chapterName,
        confirmText = stringResource(R.string.ok),
        onConfirm = { onIntent(MangaReaderIntent.PayCurrentChapter) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MangaReaderIntent.DismissDialog) },
    )
    val progressDialog = state.activeDialog as? MangaReaderDialog.ConfirmProgress
    AppAlertDialog(
        show = progressDialog != null,
        onDismissRequest = { onIntent(MangaReaderIntent.DismissDialog) },
        title = stringResource(R.string.get_book_progress),
        text = stringResource(R.string.cloud_progress_exceeds_current),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            progressDialog?.progress?.let { onIntent(MangaReaderIntent.ApplyReadingProgress(it)) }
            onIntent(MangaReaderIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(MangaReaderIntent.DismissDialog) },
    )
}

@Composable
private fun WebtoonMangaList(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentItemIndex)
    LaunchedEffect(listState, state.pages) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { visible ->
                state.pages.getOrNull(visible.index) is MangaReaderItemUi.Page
            }?.index
        }
            .distinctUntilChanged()
            .collect { index -> index?.let { onIntent(MangaReaderIntent.VisibleItemChanged(it)) } }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) listState.animateScrollToItem(it.itemIndex)
            else listState.scrollToItem(it.itemIndex)
        }
    }
    LaunchedEffect(
        state.autoReadEnabled,
        state.settings.autoReadSpeed,
        state.menuVisible,
        state.activeSheet,
    ) {
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null) return@LaunchedEffect
        val distance = state.settings.autoReadSpeed.coerceAtLeast(1)
        val duration = ceil(16f / distance * 10_000f).toInt()
        while (true) {
            val consumed = listState.animateScrollBy(
                value = 10_000f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing),
            )
            if (consumed < 1f) {
                onIntent(MangaReaderIntent.NextChapter)
                delay(500L)
            }
        }
    }
    val fraction = (1f - state.settings.sidePaddingPercent.coerceIn(0, 45) * 2f / 100f)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP) {
            Arrangement.spacedBy(8.dp)
        } else Arrangement.Top,
    ) {
        items(
            count = state.pages.size,
            key = { state.pages[it].key },
            contentType = { state.pages[it]::class },
        ) { index ->
            MangaReaderItem(
                item = state.pages[index],
                settings = state.settings,
                onIntent = onIntent,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxWidth(fraction),
                paged = false,
            )
        }
    }
}

@Composable
private fun HorizontalMangaPager(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentItemIndex,
        pageCount = { state.pages.size.coerceAtLeast(1) },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.VisibleItemChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) pagerState.animateScrollToPage(it.itemIndex)
            else pagerState.scrollToPage(it.itemIndex)
        }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        state.pages.getOrNull(page)?.let {
            MangaReaderItem(it, state.settings, onIntent, imageLoader, Modifier.fillMaxSize(), true)
        }
    }
}

@Composable
private fun VerticalMangaPager(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentItemIndex,
        pageCount = { state.pages.size.coerceAtLeast(1) },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.VisibleItemChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) pagerState.animateScrollToPage(it.itemIndex)
            else pagerState.scrollToPage(it.itemIndex)
        }
    }
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        state.pages.getOrNull(page)?.let {
            MangaReaderItem(it, state.settings, onIntent, imageLoader, Modifier.fillMaxSize(), true)
        }
    }
}

@Composable
private fun MangaReaderItem(
    item: MangaReaderItemUi,
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier,
    paged: Boolean,
) {
    when (item) {
        is MangaReaderItemUi.Page -> MangaPageImage(item, settings, onIntent, imageLoader, modifier, paged)
        is MangaReaderItemUi.ChapterEdge -> Box(
            modifier = modifier.then(if (paged) Modifier.fillMaxHeight() else Modifier.height(96.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MangaPageImage(
    page: MangaReaderItemUi.Page,
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier,
    paged: Boolean,
) {
    var scale by remember(page.key) { mutableFloatStateOf(1f) }
    var offset by remember(page.key) { mutableStateOf(Offset.Zero) }
    var imageSize by remember(page.key) { mutableStateOf(IntSize.Zero) }
    var positionInRoot by remember(page.key) { mutableStateOf(Offset.Zero) }
    val viewportSize = LocalReaderViewportSize.current
    val aspectRatios = LocalMangaAspectRatios.current
    val context = LocalContext.current
    val fallbackHeight = LocalConfiguration.current.screenHeightDp.dp
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale == 1f) {
            offset = Offset.Zero
        } else {
            val maxX = imageSize.width * (newScale - 1f) / 2f
            val maxY = imageSize.height * (newScale - 1f) / 2f
            val center = Offset(imageSize.width / 2f, imageSize.height / 2f)
            val effectiveCentroid = centroid.takeIf { it.isSpecified } ?: center
            val transformedOffset = offset * zoomChange +
                    (effectiveCentroid - center) * (1f - zoomChange) + panChange
            offset = Offset(
                x = transformedOffset.x.coerceIn(-maxX, maxX),
                y = transformedOffset.y.coerceIn(-maxY, maxY),
            )
        }
        scale = newScale
    }
    val transformModifier = if (settings.disableScale) Modifier else Modifier
        .clipToBounds()
        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        .transformable(
            state = transformState,
            canPan = { scale > 1f },
            lockRotationOnZoomPan = true,
        )

    val webtoonSizeModifier = if (paged) Modifier else {
        aspectRatios[page.key]?.takeIf { it > 0f }?.let { Modifier.aspectRatio(it) }
            ?: Modifier.height(fallbackHeight)
    }
    val request = remember(page.key, settings) {
        page.imageRequest(settings, context = context) { ratio ->
            aspectRatios[page.key] = ratio
        }
    }

    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = stringResource(
            R.string.manga_reader_page_description,
            page.chapterName,
            page.pageIndex + 1,
        ),
        contentScale = if (paged) ContentScale.Fit else ContentScale.FillWidth,
        colorFilter = mangaColorFilter(settings),
        modifier = modifier
            .then(webtoonSizeModifier)
            .onSizeChanged { imageSize = it }
            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
            .then(transformModifier)
            .pointerInput(page.key, settings) {
                detectTapGestures(
                    onTap = { tap ->
                        clickAction(
                            settings = settings,
                            onIntent = onIntent,
                            offset = positionInRoot + tap,
                            width = viewportSize.width,
                            height = viewportSize.height,
                        )
                    },
                    onDoubleTap = if (settings.disableScale) null else { _ ->
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                    },
                    onLongPress = if (settings.longPressEnabled) { _ ->
                        onIntent(MangaReaderIntent.LongPressPage(page.imageUrl))
                    } else null,
                )
            },
    )
}

private fun MangaReaderItemUi.Page.imageRequest(
    settings: MangaReaderSettings,
    context: android.content.Context,
    onAspectRatio: (Float) -> Unit = {},
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .apply {
            extras[CoverExtras.Manga] = true
            extras[CoverExtras.SourceOrigin] = settings.sourceOrigin
        }
        .apply {
            when {
                settings.enableEInk -> transformations(MangaEInkTransformation(settings.eInkThreshold))
                settings.enableGray -> transformations(MangaGrayscaleTransformation)
            }
            crossfade(!settings.disableCrossFade)
        }
        .listener(onSuccess = { _, result ->
            val image = result.image
            if (image.width > 0 && image.height > 0) {
                onAspectRatio(image.width.toFloat() / image.height)
            }
        })
        .build()
}

private fun mangaColorFilter(settings: MangaReaderSettings): ColorFilter? {
    if (settings.filterRed == 0 && settings.filterGreen == 0 &&
        settings.filterBlue == 0 && settings.filterAlpha == 0
    ) return null
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        (255 - settings.filterRed) / 255f, 0f, 0f, 0f, 0f,
        0f, (255 - settings.filterGreen) / 255f, 0f, 0f, 0f,
        0f, 0f, (255 - settings.filterBlue) / 255f, 0f, 0f,
        0f, 0f, 0f, (255 - settings.filterAlpha) / 255f, 0f,
    )))
}

private fun clickAction(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    offset: Offset,
    width: Int,
    height: Int,
) {
    val regionIndex = mangaClickRegionIndex(offset.x, offset.y, width, height)
    when (settings.clickActions.getOrNull(regionIndex) ?: 0) {
        -1 -> Unit
        0 -> onIntent(MangaReaderIntent.ToggleMenu)
        1 -> if (!settings.disableClickScroll) onIntent(MangaReaderIntent.PageStep(1))
        2 -> if (!settings.disableClickScroll) onIntent(MangaReaderIntent.PageStep(-1))
        3 -> onIntent(MangaReaderIntent.NextChapter)
        4 -> onIntent(MangaReaderIntent.PreviousChapter)
    }
}

