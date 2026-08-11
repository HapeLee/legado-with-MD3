package io.legado.app.ui.book.manga

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import io.legado.app.R
import io.legado.app.help.coil.CoverExtras
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.roundToInt

private val LocalReaderViewportSize = staticCompositionLocalOf { IntSize.Zero }
private val LocalMangaAspectRatios = staticCompositionLocalOf<MutableMap<String, Float>> {
    mutableMapOf()
}

private const val MIN_WEBTOON_ZOOM = 0.5f
private const val MAX_WEBTOON_ZOOM = 3f
private const val WEBTOON_DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_PAGE_ZOOM = 0.5f
private const val MAX_PAGE_ZOOM = 3f

/**
 * 条漫内容缩放：把布局尺寸按 zoom 缩放（渲染缩放由 [Modifier.graphicsLayer] 完成），
 * 使 LazyColumn 的滚动范围随缩放变化，缩小后仍能滚动到全部图片；放大时横向超出部分被裁剪。
 */
private fun Modifier.zoomStripItem(zoom: Float): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width * zoom).roundToInt()
        .coerceIn(constraints.minWidth, constraints.maxWidth)
        .coerceAtLeast(1)
    val height = (placeable.height * zoom).roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
        .coerceAtLeast(1)
    layout(width, height) {
        placeable.placeRelative(0, 0)
    }
}

/**
 * 放大后的平移边界：横向限制在单页宽度溢出的范围内，纵向限制在放大后的内容高度内。
 */
internal fun clampZoomPan(
    target: Offset,
    zoom: Float,
    itemWidth: Float,
    contentHeight: Float,
    viewport: IntSize,
): Offset {
    if (contentHeight <= 0f) return Offset.Zero
    val width = viewport.width.coerceAtLeast(1).toFloat()
    val height = viewport.height.coerceAtLeast(1).toFloat()
    val maxX = ((itemWidth * zoom - width) / 2f).coerceAtLeast(0f)
    val maxY = (contentHeight * zoom - height).coerceAtLeast(0f)
    return Offset(
        x = target.x.coerceIn(-maxX, maxX),
        y = target.y.coerceIn(-maxY, 0f),
    )
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
        state.settingsCategory,
    ) {
        val isWebtoon = state.settings.scrollMode == MangaScrollMode.WEBTOON ||
                state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null ||
            state.settingsCategory != null || isWebtoon
        ) {
            return@LaunchedEffect
        }
        while (true) {
            delay(state.settings.autoReadSpeed.coerceAtLeast(1) * 1_000L)
            onIntent(MangaReaderIntent.PageStep(1))
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingMessage = state.pendingMessages.firstOrNull()
    LaunchedEffect(pendingMessage?.id, context, lifecycleOwner) {
        val message = pendingMessage ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Toast.makeText(
                context,
                message.content.resolve(context),
                Toast.LENGTH_SHORT,
            ).show()
            onIntent(MangaReaderIntent.MessageShown(message.id))
        }
    }
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
    if (state.activeSheet == MangaReaderSheet.SourceActions) {
        MangaReaderSourceActionsSheet(state, onIntent)
    }
    if (state.activeSheet == MangaReaderSheet.ChangeSource) {
        val oldBook = remember(state.changeSourceBook) {
            state.changeSourceBook?.toBook()
        }
        oldBook?.let {
            ChangeSourceSheet(
                show = true,
                oldBook = it,
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

private fun MangaReaderText.resolve(context: android.content.Context): String = when (this) {
    is MangaReaderText.Dynamic -> value
    is MangaReaderText.Resource -> context.getString(resId, *args.toTypedArray())
}

@Composable
private fun WebtoonMangaList(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentItemIndex)
    val viewportSize = LocalReaderViewportSize.current
    val aspectRatios = LocalMangaAspectRatios.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val fraction = 1f - state.settings.sidePaddingPercent.coerceIn(0, 45) * 2f / 100f

    // 估算自然内容高度（放大后用于限制平移范围）；未加载的图片用整屏高度兜底
    val density = LocalDensity.current
    val fallbackPageHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val edgeHeightPx = with(density) { 96.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val itemWidthPx = viewportSize.width * fraction
    val hasGap = state.settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
    val naturalContentHeight = state.pages.fold(0f) { acc, item ->
        val itemHeight = when (item) {
            is MangaReaderItemUi.Page -> {
                val ratio = aspectRatios[item.key]
                if (ratio != null && ratio > 0f) itemWidthPx / ratio else fallbackPageHeightPx
            }

            is MangaReaderItemUi.ChapterEdge -> edgeHeightPx
        }
        acc + itemHeight + if (hasGap) gapPx else 0f
    }
    // transformable 回调是 remember 住的，包一层 always-current 的值
    val latestNaturalContentHeight by rememberUpdatedState(naturalContentHeight)
    val latestItemWidthPx by rememberUpdatedState(itemWidthPx)
    val latestViewportSize by rememberUpdatedState(viewportSize)

    fun toggleWebtoonZoom() {
        if (zoom > 1f) {
            zoom = 1f
            pan = Offset.Zero
        } else {
            zoom = WEBTOON_DOUBLE_TAP_ZOOM
        }
    }

    // 内容缩放模型：graphicsLayer 只负责平移，缩放由每一项的 zoomStripItem 完成，
    // 这样视口固定、滚动范围随缩放变化，缩小后仍能滚到全部图片。
    // 手势：多点触控一律吞掉（双指可朝任意方向平移 + 捏合缩放），单指交给 LazyColumn 滚动。
    val zoomModifier = if (state.settings.disableScale) Modifier else Modifier
        .graphicsLayer(
            translationX = pan.x,
            translationY = pan.y,
        )
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var handled = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val centroid = event.calculateCentroid()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val currentZoom = zoom
                        val newZoom =
                            (currentZoom * zoomChange).coerceIn(MIN_WEBTOON_ZOOM, MAX_WEBTOON_ZOOM)
                        val ratio = if (currentZoom > 0f) newZoom / currentZoom else 1f
                        if (ratio != 1f) {
                            handled = true
                            val width = latestViewportSize.width.coerceAtLeast(1).toFloat()
                            val height = latestViewportSize.height.coerceAtLeast(1).toFloat()
                            val center = Offset(width / 2f, height / 2f)
                            val effectiveCentroid = centroid.takeIf { it.isSpecified } ?: center
                            pan = pan * ratio + (effectiveCentroid - center) * (1f - ratio)
                        }
                        if (newZoom <= 1f) {
                            pan = Offset.Zero
                        } else if (panChange != Offset.Zero) {
                            handled = true
                            pan = clampZoomPan(
                                target = pan + panChange,
                                zoom = newZoom,
                                itemWidth = latestItemWidthPx,
                                contentHeight = latestNaturalContentHeight,
                                viewport = latestViewportSize,
                            )
                        }
                        if (handled) pressed.forEach { it.consume() }
                        zoom = newZoom
                    } else if (pressed.size == 1) {
                        if (handled) break
                    }
                } while (pressed.isNotEmpty())
            }
        }

    LaunchedEffect(listState, state.pages) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val currentChapterVisible = visibleItems.any { item ->
                (state.pages.getOrNull(item.index) as? MangaReaderItemUi.Page)?.chapterIndex == state.chapterIndex
            }
            val firstPageIndex = visibleItems.firstOrNull { item ->
                state.pages.getOrNull(item.index) is MangaReaderItemUi.Page
            }?.index
            if (firstPageIndex == null) null else firstPageIndex to currentChapterVisible
        }
            .distinctUntilChanged()
            .collect { entry ->
                entry?.let { (index, stillVisible) ->
                    onIntent(MangaReaderIntent.VisibleItemChanged(index, stillVisible))
                }
            }
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
        state.settingsCategory,
    ) {
        if (!state.autoReadEnabled || state.menuVisible || state.activeSheet != null ||
            state.settingsCategory != null
        ) return@LaunchedEffect
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(zoomModifier),
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
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .zoomStripItem(zoom)
                    .graphicsLayer(scaleX = zoom, scaleY = zoom),
                paged = false,
                onDoubleTap = if (state.settings.disableScale) {
                    null
                } else {
                    { toggleWebtoonZoom() }
                },
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
    LaunchedEffect(pagerState, state.pages) {
        snapshotFlow {
            val page = state.pages.getOrNull(pagerState.currentPage) as? MangaReaderItemUi.Page
            pagerState.currentPage to (page?.chapterIndex == state.chapterIndex)
        }.distinctUntilChanged()
            .collect { (page, stillVisible) ->
                onIntent(MangaReaderIntent.VisibleItemChanged(page, stillVisible))
            }
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
    LaunchedEffect(pagerState, state.pages) {
        snapshotFlow {
            val page = state.pages.getOrNull(pagerState.currentPage) as? MangaReaderItemUi.Page
            pagerState.currentPage to (page?.chapterIndex == state.chapterIndex)
        }.distinctUntilChanged()
            .collect { (page, stillVisible) ->
                onIntent(MangaReaderIntent.VisibleItemChanged(page, stillVisible))
            }
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
    onDoubleTap: (() -> Unit)? = null,
) {
    when (item) {
        is MangaReaderItemUi.Page -> MangaPageImage(
            page = item,
            settings = settings,
            onIntent = onIntent,
            imageLoader = imageLoader,
            modifier = modifier,
            paged = paged,
            onDoubleTap = onDoubleTap,
        )
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
    onDoubleTap: (() -> Unit)? = null,
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
        val newScale = (scale * zoomChange).coerceIn(MIN_PAGE_ZOOM, MAX_PAGE_ZOOM)
        if (newScale <= 1f) {
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
    // 翻页模式单页缩放；条漫模式由 WebtoonMangaList 整体缩放，不在此处理单张图
    val transformModifier = if (paged && !settings.disableScale) Modifier
        .clipToBounds()
        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        .transformable(
            state = transformState,
            canPan = { scale > 1f },
            lockRotationOnZoomPan = true,
        )
    else Modifier

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
                        if (onDoubleTap != null) {
                            onDoubleTap()
                        } else {
                            scale = if (scale > 1f) 1f else 2.5f
                            if (scale == 1f) offset = Offset.Zero
                        }
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

