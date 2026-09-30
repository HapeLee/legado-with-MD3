package io.legado.app.ui.book.manga

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.help.coil.CoverExtras
import io.legado.app.help.coil.MangaImageFileOwner
import io.legado.app.help.coil.MangaPrefetchRequests
import io.legado.app.help.coil.MangaWebtoonTiles
import io.legado.app.help.coil.asMangaPrefetch
import io.legado.app.help.coil.rememberMangaZoomableImageSource
import io.legado.app.ui.book.manga.config.MangaDoublePageMode
import io.legado.app.ui.book.manga.config.MangaPageScaleType
import io.legado.app.ui.book.manga.config.MangaScrollMode
import io.legado.app.ui.book.manga.config.MangaWidePageMode
import io.legado.app.ui.book.manga.config.MangaZoomStartPosition
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumOutlinedButton
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.roundToInt

private val LocalReaderViewportSize = staticCompositionLocalOf { IntSize.Zero }
private val LocalReaderViewportOrigin = staticCompositionLocalOf { Offset.Zero }
private val LocalMangaRatioUpdate = staticCompositionLocalOf<((String, Float) -> Unit)?> { null }
private val LocalMangaAspectRatios = staticCompositionLocalOf<MutableMap<String, Float>> {
    mutableMapOf()
}
internal data class MangaPageEdgeColors(
    val top: Color,
    val bottom: Color,
)

private val LocalMangaBackgroundColors = staticCompositionLocalOf<MutableMap<String, MangaPageEdgeColors>> {
    mutableMapOf()
}

private const val MIN_WEBTOON_ZOOM = 0.5f
private const val MAX_WEBTOON_ZOOM = 3f
private const val WEBTOON_DOUBLE_TAP_ZOOM = 2.5f
private const val MIN_PAGE_ZOOM = 1f
private const val MAX_PAGE_ZOOM = 3f

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
    hazeState: HazeState? = null,
    canHandleBack: Boolean = true,
) {
    val canMorphBack = state.inBookshelf &&
            state.activeDialog == null &&
            state.activeSheet == null &&
            state.settingsCategory == null &&
            !state.menuVisible
    BackHandler(enabled = canHandleBack && !canMorphBack) { onIntent(MangaReaderIntent.BackPressed) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    val aspectRatios = remember(state.bookUrl) { mutableStateMapOf<String, Float>() }
    var applyPageResize by remember(state.bookUrl) {
        mutableStateOf<((String, () -> Unit) -> Unit)?>(
            null
        )
    }
    val updateAspectRatio: (String, Float) -> Unit = { key, ratio ->
        if (ratio.isFinite() && ratio > 0 && aspectRatios[key] != ratio) {
            val resize = { aspectRatios[key] = ratio; Unit }
            applyPageResize?.invoke(key, resize) ?: resize()
        }
    }
    val automaticBackgrounds =
        remember(state.bookUrl) { mutableStateMapOf<String, MangaPageEdgeColors>() }
    val currentPageKey = state.pages.getOrNull(state.currentItemIndex)?.key
    val readerBackground = state.settings.backgroundColor.copy(alpha = 1f)
    val currentPageColor = automaticBackgrounds[currentPageKey]?.top

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
    MangaChapterImagePrefetch(state, imageLoader, updateAspectRatio)
    CompositionLocalProvider(
        LocalReaderViewportSize provides viewportSize,
        LocalReaderViewportOrigin provides viewportOrigin,
        LocalMangaAspectRatios provides aspectRatios,
        LocalMangaRatioUpdate provides updateAspectRatio,
        LocalMangaBackgroundColors provides automaticBackgrounds,
    ) {
        val menuBackdrop = rememberLayerBackdrop()
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .onGloballyPositioned { viewportOrigin = it.positionInRoot() }
                .background(readerBackground)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeSource(hazeState)
                        } else {
                            Modifier
                        }
                    )
                    .layerBackdrop(menuBackdrop)
                    .background(readerBackground)
            ) {
                when (state.settings.scrollMode) {
                    MangaScrollMode.PAGE_LEFT_TO_RIGHT,
                    MangaScrollMode.PAGE_RIGHT_TO_LEFT -> HorizontalMangaPager(state, onIntent, imageLoader)
                    MangaScrollMode.PAGE_TOP_TO_BOTTOM -> VerticalMangaPager(state, onIntent, imageLoader)
                    else -> WebtoonMangaList(state, onIntent, imageLoader) { applyPageResize = it }
                }
            }

            MangaFooter(state)
            MangaReaderMenu(
                state = state,
                onIntent = onIntent,
                backdrop = menuBackdrop,
                hazeState = hazeState,
                menuSeedColor = when (state.settings.menuColorSource) {
                    1 -> currentPageColor ?: readerBackground
                    2 -> null
                    3 -> state.settings.menuSeedColor
                    else -> readerBackground
                },
            )
            ReaderStatusOverlay(state, onIntent)
        }
    }
    if (state.activeSheet == MangaReaderSheet.SourceActions) {
        MangaReaderSourceActionsSheet(state, onIntent)
    }
    if (state.activeSheet == MangaReaderSheet.CacheActions) {
        MangaReaderCacheActionsSheet(state, onIntent)
    }
    (state.activeSheet as? MangaReaderSheet.PageActions)?.let { sheet ->
        MangaReaderPageActionsSheet(sheet.companionPageKey != null, onIntent)
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
private fun MangaChapterImagePrefetch(
    state: MangaReaderUiState,
    imageLoader: ImageLoader,
    updateAspectRatio: (String, Float) -> Unit,
) {
    val context = LocalContext.current
    val pageDecodeSize = mangaPageDecodeSize(
        paged = state.settings.scrollMode != MangaScrollMode.WEBTOON &&
                state.settings.scrollMode != MangaScrollMode.WEBTOON_WITH_GAP,
        sidePaddingPercent = state.settings.sidePaddingPercent,
        pageScaleType = state.settings.pageScaleType,
        widePageMode = state.settings.widePageMode,
    )
    val pagePrefetchRevision = state.pages.filterIsInstance<MangaReaderItemUi.Page>()
        .map { it.key to it.retryRevision }
    val prefetchItemIndex = mangaImagePrefetchIndex(
        state.settings.scrollMode,
        state.currentItemIndex,
        state.footerItemIndex
    )
    val prefetchScope = rememberCoroutineScope()
    val prefetch = remember(
        imageLoader,
        state.bookUrl,
        state.settings.sourceOrigin,
        state.settings.scrollMode
    ) {
        MangaPrefetchRequests(prefetchScope)
    }
    DisposableEffect(prefetch) { onDispose(prefetch::close) }
    LaunchedEffect(
        prefetch,
        prefetchItemIndex,
        pagePrefetchRevision,
        state.settings.preDownloadCount,
        state.chapterIndex
    ) {
        val pages = if (state.settings.preDownloadCount <= 0) emptyMap() else
            mangaChapterPrefetchPages(state.pages, prefetchItemIndex, state.chapterIndex)
                .associateBy { it.key to it.retryRevision }
        prefetch.update(pages.keys.toList()) { key ->
            val page = pages.getValue(key)
            val owner = MangaImageFileOwner()
            try {
                val result = imageLoader.execute(
                    page.imageRequest(
                        settings = state.settings,
                        context = context,
                        decodeSize = pageDecodeSize,
                        fileOwner = owner,
                        onAspectRatio = { updateAspectRatio(page.key, it) },
                    ).asMangaPrefetch()
                )
                if (result is coil3.request.ErrorResult) throw result.throwable
            } finally {
                // 完成后不持有整章租约或位图；原图受阅读缓存预算管理。
                owner.close()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WebtoonMangaList(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    onResizeHandlerChanged: (((String, () -> Unit) -> Unit)?) -> Unit,
) {
    val prefetchEnabled by rememberUpdatedState(state.settings.preDownloadCount > 0)
    // 按像素距离而非页数预组合；窗口跟随列表真实滚动方向，不经过 ViewModel/重组往返。
    val cacheWindow = remember {
        object : LazyLayoutCacheWindow {
            override fun Density.calculateAheadWindow(viewport: Int): Int =
                if (prefetchEnabled) viewport * 4 else 0

            override fun Density.calculateBehindWindow(viewport: Int): Int =
                if (prefetchEnabled) viewport * 2 else 0
        }
    }
    val listState =
        rememberLazyListState(cacheWindow, initialFirstVisibleItemIndex = state.currentItemIndex)
    // 单张图片的加载状态会更新整个 UiState.pages。可见页监听不能因此重启：重启会
    // 丢失 distinctUntilChanged 的上一条记录，并把 LazyColumn 尚未完成重测时保留的
    // 旧视口重新上报为当前页，进而使章节/页码跳回去。
    val latestReaderState by rememberUpdatedState(state)
    val coroutineScope = rememberCoroutineScope()
    var pendingWebtoonTap by remember { mutableStateOf<Job?>(null) }
    var lastWebtoonTapAt by remember { mutableStateOf(0L) }
    var lastWebtoonTapPosition by remember { mutableStateOf(Offset.Unspecified) }
    val viewportSize = LocalReaderViewportSize.current
    val aspectRatios = LocalMangaAspectRatios.current
    val resizeQueue = remember(state.bookUrl) { MangaWebtoonResizeQueue() }
    var zoomAnchorLayout by remember { mutableStateOf<LazyListLayoutInfo?>(null) }
    val applyResize: (() -> Unit) -> Unit = { resize ->
        val layout = listState.layoutInfo
        val anchor =
            if (zoomAnchorLayout != null || listState.isScrollInProgress || latestReaderState.scrollRequest != null ||
            latestReaderState.bookUrl != state.bookUrl
        ) null else {
            val loadedKeys = layout.visibleItemsInfo.mapNotNull { info ->
                (latestReaderState.pages.getOrNull(info.index) as? MangaReaderItemUi.Page)
                    ?.takeIf { it.key == info.key && it.loadState == MangaPageLoadState.Ready && it.key in aspectRatios }
                    ?.key
            }.toSet()
            mangaWebtoonResizeAnchor(
                layout.visibleItemsInfo.map { Triple(it.key.toString(), it.offset, it.size) },
                (layout.viewportStartOffset + layout.viewportEndOffset) / 2,
                loadedKeys,
            )
        }
        resize()
        anchor?.let { (key, offset) ->
            val index = latestReaderState.pages.indexOfFirst { it.key == key }
            if (index >= 0) listState.requestScrollToItem(index, offset)
        }
    }
    val latestApplyResize by rememberUpdatedState(applyResize)
    DisposableEffect(listState, state.bookUrl, resizeQueue) {
        onResizeHandlerChanged { key, resize ->
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.key == key }
            val index = latestReaderState.pages.indexOfFirst { it.key == key }
            val apply =
                if (visible || index in 0 until listState.firstVisibleItemIndex) latestApplyResize
                else { change: () -> Unit -> change() }
            // 整章远端页的尺寸不会移动当前图片，不为它们反复发起滚动位置请求。
            resizeQueue.update(key, listState.isScrollInProgress, visible, resize, apply)
        }
        onDispose { onResizeHandlerChanged(null) }
    }
    LaunchedEffect(listState, state.bookUrl, resizeQueue) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) resizeQueue.flush(
                latestReaderState.pages.map { it.key }.toSet(),
                latestApplyResize
            )
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { measured ->
            // 高度更新不能用旧布局的像素锚点覆盖尚未生效的缩放锚点。
            if (zoomAnchorLayout != null && measured !== zoomAnchorLayout) zoomAnchorLayout = null
        }
    }
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

            is MangaReaderItemUi.ChapterEdge,
            is MangaReaderItemUi.ChapterTransition -> edgeHeightPx
        }
        acc + itemHeight + if (hasGap) gapPx else 0f
    }
    // transformable 回调是 remember 住的，包一层 always-current 的值
    val latestNaturalContentHeight by rememberUpdatedState(naturalContentHeight)
    val latestItemWidthPx by rememberUpdatedState(itemWidthPx)
    val latestViewportSize by rememberUpdatedState(viewportSize)

    fun preserveWebtoonZoomPosition(targetZoom: Float, focalY: Float, panY: Float = 0f): Boolean {
        val layout = listState.layoutInfo
        val width = latestViewportSize.width
        if (width <= 0 || layout.viewportSize.width <= 0) return false
        // 手势事件可能快于重测；基于当前实际测量宽度，而非尚未布局的 zoom 状态计算倍率。
        val pageWidthFraction = latestItemWidthPx / width
        val measuredImageWidth =
            (layout.viewportSize.width * pageWidthFraction).roundToInt().coerceAtLeast(1)
        val targetImageWidth =
            ((width * targetZoom).roundToInt() * pageWidthFraction).roundToInt().coerceAtLeast(1)
        val ratio = targetImageWidth.toFloat() / measuredImageWidth
        val visible = layout.visibleItemsInfo.filter {
            latestReaderState.pages.getOrNull(it.index)?.key == it.key
        }
        val anchor = mangaWebtoonZoomAnchor(
            visible.map { Triple(it.key.toString(), it.offset, it.size) },
            focalY, aspectRatios.keys, ratio, panY,
        ) ?: return false
        val index = visible.firstOrNull { it.key == anchor.first }?.index ?: return false
        // 与宽度变化在同次重测中生效，不先滚旧高度、下一帧再改图片高度。
        zoomAnchorLayout = layout
        listState.requestScrollToItem(index, anchor.second)
        return true
    }

    fun toggleWebtoonZoom(tap: Offset) {
        val target = if (zoom > 1f) 1f else WEBTOON_DOUBLE_TAP_ZOOM
        preserveWebtoonZoomPosition(target, tap.y)
        pan = if (target <= 1f) Offset.Zero else {
            val ratio = target / zoom
            clampZoomPan(
                target = Offset(
                    pan.x * ratio + (tap.x - latestViewportSize.width / 2f) * (1f - ratio),
                    0f
                ),
                zoom = target,
                itemWidth = latestItemWidthPx,
                contentHeight = latestNaturalContentHeight,
                viewport = latestViewportSize,
            )
        }
        zoom = target
    }

    // 列表使用真实的缩放后宽度测量，避免 LazyColumn 先按未缩放宽度裁掉图片两侧。
    // 外层 viewport 保持固定，负责九宫格命中和横向平移。
    // 手势：多点触控一律吞掉（双指可朝任意方向平移 + 捏合缩放），单指交给 LazyColumn 滚动。
    val gestureModifier = if (state.settings.disableScale) Modifier else Modifier
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
                        val center =
                            Offset(latestViewportSize.width / 2f, latestViewportSize.height / 2f)
                        val effectiveCentroid = centroid.takeIf { it.isSpecified } ?: center
                        val anchored = if (ratio != 1f) preserveWebtoonZoomPosition(
                            newZoom, effectiveCentroid.y, if (newZoom > 1f) panChange.y else 0f,
                        ) else false
                        if (ratio != 1f) {
                            handled = true
                            pan = pan * ratio + (effectiveCentroid - center) * (1f - ratio)
                        }
                        if (newZoom <= 1f) {
                            pan = Offset.Zero
                        } else {
                            if (panChange != Offset.Zero) handled = true
                            pan = clampZoomPan(
                                target = Offset(pan.x + panChange.x, 0f),
                                zoom = newZoom,
                                itemWidth = latestItemWidthPx,
                                contentHeight = latestNaturalContentHeight,
                                viewport = latestViewportSize,
                            )
                            if (!anchored && panChange.y != 0f) listState.dispatchRawDelta(-panChange.y)
                        }
                        if (handled) pressed.forEach { it.consume() }
                        zoom = newZoom
                    } else if (pressed.size == 1) {
                        if (handled) break
                    }
                } while (pressed.isNotEmpty())
            }
        }

    fun reportWebtoonVisibleItem(forcedItemIndex: Int? = null) {
        val currentState = latestReaderState
        // Programmatic scrolling also emits a scroll-idle event. That event must not clear the
        // request before the target's actual layout has been confirmed below.
        if (forcedItemIndex == null && currentState.scrollRequest != null) return
        val visibleItems = listState.layoutInfo.visibleItemsInfo.filter { visible ->
            currentState.pages.getOrNull(visible.index)?.key == visible.key
        }
        val focusedItemIndex = forcedItemIndex ?: mangaWebtoonFocusedPageIndex(
            items = currentState.pages,
            visibleItemIndices = visibleItems.map { it.index },
            currentChapterIndex = currentState.chapterIndex,
        ) ?: return
        if (currentState.pages.getOrNull(focusedItemIndex) !is MangaReaderItemUi.Page) return
        val forcedItemVisible = forcedItemIndex == null ||
                visibleItems.any { it.index == forcedItemIndex }
        if (!forcedItemVisible) return
        val currentChapterVisible = visibleItems.any { visibleItem ->
            (currentState.pages.getOrNull(visibleItem.index) as? MangaReaderItemUi.Page)
                ?.chapterIndex == currentState.chapterIndex
        }
        onIntent(
            MangaReaderIntent.VisibleItemChanged(
                itemIndex = focusedItemIndex,
                firstItemIndex = if (forcedItemVisible) {
                    visibleItems.firstOrNull()?.index ?: focusedItemIndex
                } else {
                    focusedItemIndex
                },
                lastItemIndex = focusedItemIndex,
                currentChapterVisible = currentChapterVisible,
                navigationId = currentState.navigationId,
            )
        )
    }

    // 只在真实滚动结束后上报可见页。图片加载/失败会改变条目的 fallback 高度，如果
    // 依赖 layoutInfo 持续上报，布局收缩或撑开本身就会伪装成“翻过了章节边界”，
    // 从而把已跳转的章节又切回去。程序化跳转另由 scrollRequest 完成后直接上报目标页。
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    wasScrolling = true
                    onIntent(MangaReaderIntent.PagerScrollChanged(true))
                } else if (wasScrolling) {
                    wasScrolling = false
                    onIntent(MangaReaderIntent.PagerScrollChanged(false))
                    reportWebtoonVisibleItem()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to
                    listState.layoutInfo.visibleItemsInfo.map { it.index }
        }.distinctUntilChanged().collect { (scrolling, visibleIndices) ->
            if (!scrolling) return@collect
            val currentState = latestReaderState
            mangaWebtoonFocusedPageIndex(
                items = currentState.pages,
                visibleItemIndices = visibleIndices,
                currentChapterIndex = currentState.chapterIndex,
            )?.let { itemIndex ->
                onIntent(
                    MangaReaderIntent.FooterItemChanged(
                        itemIndex = itemIndex,
                        navigationId = currentState.navigationId,
                    )
                )
            }
        }
    }

    LaunchedEffect(state.scrollRequest?.id) {
        val request = state.scrollRequest ?: return@LaunchedEffect
        val targetKey = state.pages.getOrNull(request.itemIndex)?.key ?: return@LaunchedEffect
        // A placeholder may still be the measured list when the new chapter is composed.
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it == state.pages.size }
        if (request.animated) listState.animateScrollToItem(request.itemIndex)
        else listState.scrollToItem(request.itemIndex)
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any {
                it.index == request.itemIndex && it.key == targetKey
            }
        }.first { it }
        reportWebtoonVisibleItem(request.itemIndex)
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
            } else {
                reportWebtoonVisibleItem()
            }
        }
    }
    val scaledListWidth = with(density) {
        (viewportSize.width.coerceAtLeast(1) * zoom).toDp()
    }

    fun performWebtoonTap(tap: Offset) {
        val action = mangaClickActionAt(
            clickActions = state.settings.clickActions,
            x = tap.x,
            y = tap.y,
            width = viewportSize.width,
            height = viewportSize.height,
        )
        when (action) {
            1, 2 -> if (!state.settings.disableClickScroll) {
                val direction = if (action == 1) 1 else -1
                coroutineScope.launch {
                    val distance = viewportSize.height * direction.toFloat()
                    val consumed = if (state.settings.disableScrollAnimation) {
                        listState.scrollBy(distance)
                    } else {
                        listState.animateScrollBy(distance)
                    }
                    if (kotlin.math.abs(consumed) < 1f) {
                        onIntent(
                            if (direction > 0) MangaReaderIntent.NextChapter
                            else MangaReaderIntent.PreviousChapter
                        )
                    } else {
                        reportWebtoonVisibleItem()
                    }
                }
            }

            else -> performMangaClickAction(action, onIntent)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Navigation belongs to the viewport, not to an individual transformed strip item.
            // This keeps the nine-grid stable while the list scrolls, zooms, or pans.
            .pointerInput(state.settings, viewportSize, state.pages) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var latest = start
                    var pointerCount = 1
                    var upAt = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        pointerCount = maxOf(pointerCount, event.changes.count { it.pressed })
                        event.changes.firstOrNull()?.let {
                            latest = it.position
                            upAt = it.uptimeMillis
                        }
                    } while (event.changes.any { it.pressed })

                    val moved = (latest - start).getDistance() > viewConfiguration.touchSlop
                    if (pointerCount > 1 || moved) return@awaitEachGesture
                    val heldFor = upAt - down.uptimeMillis
                    if (heldFor >= viewConfiguration.longPressTimeoutMillis &&
                        state.settings.longPressEnabled
                    ) {
                        pendingWebtoonTap?.cancel()
                        val visibleItem =
                            listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                latest.y >= info.offset && latest.y < info.offset + info.size
                            }
                        val page = visibleItem?.let { state.pages.getOrNull(it.index) }
                                as? MangaReaderItemUi.Page
                        page?.let { onIntent(MangaReaderIntent.LongPressPage(it.key)) }
                        return@awaitEachGesture
                    }

                    val isDoubleTap = !state.settings.disableScale &&
                            upAt - lastWebtoonTapAt <= viewConfiguration.doubleTapTimeoutMillis &&
                            lastWebtoonTapPosition.isSpecified &&
                            (latest - lastWebtoonTapPosition).getDistance() <= viewConfiguration.touchSlop * 2
                    if (isDoubleTap) {
                        pendingWebtoonTap?.cancel()
                        pendingWebtoonTap = null
                        lastWebtoonTapAt = 0L
                        lastWebtoonTapPosition = Offset.Unspecified
                        toggleWebtoonZoom(latest)
                    } else {
                        lastWebtoonTapAt = upAt
                        lastWebtoonTapPosition = latest
                        pendingWebtoonTap?.cancel()
                        pendingWebtoonTap = coroutineScope.launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            performWebtoonTap(latest)
                            pendingWebtoonTap = null
                        }
                    }
                }
            }
            .then(gestureModifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .requiredWidth(scaledListWidth)
                .fillMaxHeight()
                .graphicsLayer(translationX = pan.x),
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
}

@Composable
private fun HorizontalMangaPager(
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
) {
    val viewport = LocalReaderViewportSize.current
    val coroutineScope = rememberCoroutineScope()
    var pendingTap by remember { mutableStateOf<Job?>(null) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Unspecified) }
    val aspectRatios = LocalMangaAspectRatios.current
    val useDoublePage = isDoublePageActive(state.settings.doublePageMode, viewport)
    val aspectRatioSnapshot = aspectRatios.toMap()
    var spreadRatios by remember(state.bookUrl) { mutableStateOf(aspectRatioSnapshot) }
    var pendingSpreadKey by remember(state.bookUrl) { mutableStateOf<String?>(null) }
    val spreads = remember(state.pages, useDoublePage, spreadRatios, state.settings) {
        buildMangaSpreads(
            state.pages,
            useDoublePage,
            spreadRatios,
            coverSingle = state.settings.doublePageCoverSingle,
            shiftPairing = state.settings.doublePageShift,
            splitWidePages = state.settings.widePageMode == MangaWidePageMode.SPLIT,
            splitRightToLeft =
                (state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT) xor
                        state.settings.doublePageInvert,
        )
    }
    val initialSpread = spreads.indexOfFirst { state.currentItemIndex in it }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialSpread,
        pageCount = { spreads.size.coerceAtLeast(1) },
    )
    val latestReaderState by rememberUpdatedState(state)
    val latestSpreads by rememberUpdatedState(spreads)
    LaunchedEffect(pagerState, aspectRatioSnapshot) {
        // 后台整章尺寸到达不能中断 fling；布局重排保留拆分页的 LEFT/RIGHT 身份。
        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        if (spreadRatios != aspectRatioSnapshot) {
            pendingSpreadKey = latestSpreads.getOrNull(pagerState.currentPage)?.key
            spreadRatios = aspectRatioSnapshot
        }
    }
    var spreadLayoutReady by remember(state.bookUrl) { mutableStateOf(false) }
    var reconciledSpreads by remember(state.bookUrl) { mutableStateOf(emptyList<MangaPageSpread>()) }
    LaunchedEffect(spreads, state.currentItemIndex) {
        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        spreadLayoutReady = false
        val target = mangaSpreadReconcileTarget(
            spreads, state.currentItemIndex,
            pendingSpreadKey.takeIf { state.scrollRequest == null })
        pendingSpreadKey = null
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
        reconciledSpreads = spreads
        spreadLayoutReady = true
    }
    LaunchedEffect(pagerState, state.bookUrl, state.navigationId) {
        snapshotFlow {
            val reader = latestReaderState
            val spread = latestSpreads.getOrNull(pagerState.currentPage)
            val indices = spread?.itemIndices.orEmpty()
            val itemIndex = reader.scrollRequest?.itemIndex?.takeIf { it in indices }
                ?: indices.firstOrNull()
            // 新布局提交与 Pager 按 key 重测之间，不能把旧页码映射到新 spread 上报。
            if (!spreadLayoutReady || latestSpreads != reconciledSpreads) null
            // 滚动中不切章：随 fling 越过章节边界时延迟到落定再上报，避免窗口重建期误切。
            // 把 isScrollInProgress 并入快照，滚动结束会产生新值从而重发聚焦页。
            else Triple(pagerState.isScrollInProgress, indices, itemIndex)
        }.distinctUntilChanged()
            .collect { entry ->
                val (isScrolling, indices, itemIndex) = entry ?: return@collect
                val reader = latestReaderState
                if (reader.navigationId != state.navigationId) return@collect
                itemIndex?.let { index ->
                    when (val item = reader.pages.getOrNull(index)) {
                        is MangaReaderItemUi.Page -> if (isScrolling) {
                            onIntent(MangaReaderIntent.FooterItemChanged(index, state.navigationId))
                        } else {
                            onIntent(
                                MangaReaderIntent.VisibleItemChanged(
                                    itemIndex = index,
                                    firstItemIndex = indices.first(),
                                    lastItemIndex = indices.last(),
                                    currentChapterVisible = indices.any { visibleIndex ->
                                        (reader.pages.getOrNull(visibleIndex) as? MangaReaderItemUi.Page)
                                            ?.chapterIndex == reader.chapterIndex
                                    },
                                    navigationId = state.navigationId,
                                )
                            )
                        }
                        is MangaReaderItemUi.ChapterTransition -> Unit
                        is MangaReaderItemUi.ChapterEdge, null -> Unit
                    }
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.PagerScrollChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id, spreads) {
        state.scrollRequest?.let {
            val spreadIndex = spreads.indexOfFirst { spread -> it.itemIndex in spread }
            if (spreadIndex >= 0) {
                if (it.animated) pagerState.animateScrollToPage(spreadIndex)
                else pagerState.scrollToPage(spreadIndex)
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = if (state.settings.preDownloadCount > 0 &&
            state.settings.pageScaleType != MangaPageScaleType.ORIGINAL
        ) 2 else 0,
        key = { spreads.getOrNull(it)?.key ?: "empty:$it" },
        reverseLayout = state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.settings, viewport) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var latest = start
                    var pointerCount = 1
                    var upAt = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        pointerCount = maxOf(pointerCount, event.changes.count { it.pressed })
                        event.changes.firstOrNull()?.let {
                            latest = it.position
                            upAt = it.uptimeMillis
                        }
                    } while (event.changes.any { it.pressed })
                    val moved = (latest - start).getDistance() > viewConfiguration.touchSlop
                    val heldFor = upAt - down.uptimeMillis
                    if (pointerCount > 1 || moved ||
                        heldFor >= viewConfiguration.longPressTimeoutMillis
                    ) return@awaitEachGesture

                    val isDoubleTap = !state.settings.disableScale &&
                            upAt - lastTapAt <= viewConfiguration.doubleTapTimeoutMillis &&
                            lastTapPosition.isSpecified &&
                            (latest - lastTapPosition).getDistance() <= viewConfiguration.touchSlop * 2
                    if (isDoubleTap) {
                        pendingTap?.cancel()
                        pendingTap = null
                        lastTapAt = 0L
                        lastTapPosition = Offset.Unspecified
                    } else {
                        lastTapAt = upAt
                        lastTapPosition = latest
                        pendingTap?.cancel()
                        pendingTap = coroutineScope.launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            clickAction(
                                settings = state.settings,
                                onIntent = onIntent,
                                offset = latest,
                                width = viewport.width,
                                height = viewport.height,
                            )
                            pendingTap = null
                        }
                    }
                }
            },
    ) { page ->
        val slots = spreads.getOrNull(page)?.slots.orEmpty()
        val reverseSpread =
            (state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT) xor
                    state.settings.doublePageInvert
        val displaySlots = if (slots.size == 2 && reverseSpread) slots.reversed() else slots
        MangaHorizontalSpread(
            slots = displaySlots,
            state = state,
            onIntent = onIntent,
            imageLoader = imageLoader,
            viewportHandlesClicks = true,
        )
    }
}

@Composable
private fun MangaHorizontalSpread(
    slots: List<MangaPageSlot>,
    state: MangaReaderUiState,
    onIntent: (MangaReaderIntent) -> Unit,
    imageLoader: ImageLoader,
    viewportHandlesClicks: Boolean,
) {
    val viewport = LocalReaderViewportSize.current
    if (slots.size < 2) {
        val slot = slots.firstOrNull()
        state.pages.getOrNull(slot?.itemIndex ?: -1)?.let {
            MangaReaderItem(
                it,
                state.settings,
                onIntent,
                imageLoader,
                Modifier
                    .fillMaxSize()
                    .pageSlice(slot?.slice ?: MangaPageSlice.FULL),
                paged = true,
                viewportHandlesClicks = viewportHandlesClicks,
            )
        }
        return
    }
    val zoomableState = rememberZoomableState(
        ZoomSpec(
            maxZoomFactor = MAX_PAGE_ZOOM,
            minZoomFactor = MIN_PAGE_ZOOM,
        )
    )
    val spreadSize = remember { mutableStateOf(IntSize.Zero) }
    val doubleClickToZoom = remember(state.settings.zoomStartPosition, state.settings.scrollMode, spreadSize.value) {
        DoubleClickToZoomListener { zoomState, tappedPosition ->
            if ((zoomState.zoomFraction ?: 0f) > 0.05f) {
                zoomState.resetZoom()
            } else {
                val focalX = when (state.settings.zoomStartPosition) {
                    MangaZoomStartPosition.LEFT -> 0f
                    MangaZoomStartPosition.RIGHT -> spreadSize.value.width.toFloat()
                    MangaZoomStartPosition.AUTOMATIC -> if (
                        state.settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
                    ) spreadSize.value.width.toFloat() else 0f
                    else -> tappedPosition.x
                }
                zoomState.zoomTo(WEBTOON_DOUBLE_TAP_ZOOM, Offset(focalX, tappedPosition.y))
            }
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .onSizeChanged { spreadSize.value = it }
            .zoomable(
                state = zoomableState,
                gestures = if (state.settings.disableScale) {
                    EnabledZoomGestures.None
                } else EnabledZoomGestures.ZoomAndPan,
                onClick = if (viewportHandlesClicks) null else { tap ->
                    clickAction(
                        settings = state.settings,
                        onIntent = onIntent,
                        offset = tap,
                        width = viewport.width,
                        height = viewport.height,
                    )
                },
                onLongClick = if (state.settings.longPressEnabled) { tap ->
                    val slot = if (tap.x < spreadSize.value.width / 2f) 0 else 1
                    val page = slots.getOrNull(slot)?.itemIndex?.let(state.pages::getOrNull)
                            as? MangaReaderItemUi.Page
                    page?.let {
                        val companion = slots.getOrNull(if (slot == 0) 1 else 0)
                            ?.itemIndex?.let(state.pages::getOrNull) as? MangaReaderItemUi.Page
                        onIntent(
                            MangaReaderIntent.LongPressPage(
                                it.key,
                                companion?.takeIf { other -> other.key != it.key }?.key,
                                companionBeforePage = slot == 1,
                            )
                        )
                    }
                } else null,
                onDoubleClick = doubleClickToZoom,
            ),
    ) {
        slots.forEach { slot ->
            state.pages.getOrNull(slot.itemIndex)?.let {
                    MangaReaderItem(
                        it,
                        state.settings,
                        onIntent,
                        imageLoader,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pageSlice(slot.slice),
                        paged = true,
                        pageInteractions = false,
                    )
                }
            }
    }
}

private fun Modifier.pageSlice(slice: MangaPageSlice): Modifier = when (slice) {
    MangaPageSlice.FULL -> this
    MangaPageSlice.LEFT, MangaPageSlice.RIGHT -> clipToBounds().layout { measurable, constraints ->
        val width = constraints.maxWidth
        val placeable = measurable.measure(
            constraints.copy(minWidth = width * 2, maxWidth = width * 2),
        )
        layout(width, placeable.height) {
            placeable.placeRelative(if (slice == MangaPageSlice.LEFT) 0 else -width, 0)
        }
    }
}

private fun Modifier.rotateWidePage(enabled: Boolean): Modifier = if (!enabled) this else {
    layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        val width = constraints.constrainWidth(placeable.height)
        val height = constraints.constrainHeight(placeable.width)
        layout(width, height) {
            placeable.placeWithLayer(
                x = (width - placeable.width) / 2,
                y = (height - placeable.height) / 2,
            ) { rotationZ = 90f }
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
    val latestReaderState by rememberUpdatedState(state)
    LaunchedEffect(pagerState, state.bookUrl, state.navigationId) {
        snapshotFlow {
            val item = latestReaderState.pages.getOrNull(pagerState.currentPage)
            // 滚动中不切章：把 isScrollInProgress 并入快照，滚动结束产生新值从而重发聚焦页。
            Triple(pagerState.isScrollInProgress, pagerState.currentPage, item?.key)
        }.distinctUntilChanged()
            .collect { (isScrolling, page, key) ->
                val reader = latestReaderState
                if (reader.navigationId != state.navigationId) return@collect
                val item = reader.pages.getOrNull(page)?.takeIf { it.key == key }
                when (item) {
                    is MangaReaderItemUi.Page -> if (isScrolling) {
                        onIntent(MangaReaderIntent.FooterItemChanged(page, state.navigationId))
                    } else {
                        onIntent(
                            MangaReaderIntent.VisibleItemChanged(
                                itemIndex = page,
                                currentChapterVisible = item.chapterIndex == reader.chapterIndex,
                                navigationId = state.navigationId,
                            )
                        )
                    }
                    is MangaReaderItemUi.ChapterTransition -> Unit
                    is MangaReaderItemUi.ChapterEdge, null -> Unit
                }
            }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onIntent(MangaReaderIntent.PagerScrollChanged(it)) }
    }
    LaunchedEffect(state.scrollRequest?.id) {
        state.scrollRequest?.let {
            if (it.animated) pagerState.animateScrollToPage(it.itemIndex)
            else pagerState.scrollToPage(it.itemIndex)
        }
    }
    VerticalPager(
        state = pagerState,
        key = { state.pages.getOrNull(it)?.key ?: "empty:$it" },
        beyondViewportPageCount = if (state.settings.preDownloadCount > 0 &&
            state.settings.pageScaleType != MangaPageScaleType.ORIGINAL
        ) 2 else 0,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
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
    pageInteractions: Boolean = true,
    viewportHandlesClicks: Boolean = false,
) {
    when (item) {
        is MangaReaderItemUi.Page -> MangaPageImage(
            page = item,
            settings = settings,
            onIntent = onIntent,
            imageLoader = imageLoader,
            modifier = modifier,
            paged = paged,
            interactionsEnabled = pageInteractions,
            viewportHandlesClicks = viewportHandlesClicks,
        )
        is MangaReaderItemUi.ChapterEdge -> Box(
            modifier = modifier
                .then(
                    if (paged) Modifier.fillMaxHeight()
                    else if (item.fullScreen) {
                        Modifier.height(LocalConfiguration.current.screenHeightDp.dp)
                    } else Modifier.height(96.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.loading) CircularProgressIndicator()
                Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.retryChapterIndex?.let { chapterIndex ->
                    MediumOutlinedButton(
                        onClick = { onIntent(MangaReaderIntent.RetryChapter(chapterIndex)) },
                        text = stringResource(R.string.retry),
                    )
                }
            }
        }
        is MangaReaderItemUi.ChapterTransition -> Box(
            modifier = modifier
                .then(if (paged) Modifier.fillMaxHeight() else Modifier.heightIn(min = 160.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val topLabel = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    stringResource(R.string.manga_reader_transition_previous)
                } else {
                    stringResource(R.string.manga_reader_transition_current)
                }
                val bottomLabel = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    stringResource(R.string.manga_reader_transition_current)
                } else {
                    stringResource(R.string.manga_reader_transition_next)
                }
                val topChapter = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    item.targetChapterName
                } else {
                    item.currentChapterName
                }
                val bottomChapter = if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                    item.currentChapterName
                } else {
                    item.targetChapterName
                }
                Text(
                    "$topLabel：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    topChapter ?: stringResource(
                        if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                            R.string.manga_reader_no_previous_chapter
                        } else {
                            R.string.manga_reader_no_next_chapter
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$bottomLabel：",
                    modifier = Modifier.padding(top = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    bottomChapter ?: stringResource(
                        if (item.direction == MangaChapterTransitionDirection.PREVIOUS) {
                            R.string.manga_reader_no_previous_chapter
                        } else {
                            R.string.manga_reader_no_next_chapter
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.targetStatus == MangaChapterTransitionStatus.LOADING) {
                    CircularProgressIndicator()
                }
                item.statusMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.retryChapterIndex?.let { chapterIndex ->
                    MediumOutlinedButton(
                        onClick = { onIntent(MangaReaderIntent.RetryChapter(chapterIndex)) },
                        text = stringResource(R.string.retry),
                    )
                }
            }
        }
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
    interactionsEnabled: Boolean,
    viewportHandlesClicks: Boolean = false,
) {
    var positionInRoot by remember(page.key) { mutableStateOf(Offset.Zero) }
    var imageViewportSize by remember(page.key) { mutableStateOf(IntSize.Zero) }
    val viewportSize = LocalReaderViewportSize.current
    val viewportOrigin = LocalReaderViewportOrigin.current
    val aspectRatios = LocalMangaAspectRatios.current
    val updateRatio = LocalMangaRatioUpdate.current
    val automaticBackgrounds = LocalMangaBackgroundColors.current
    val context = LocalContext.current
    val fallbackHeight = LocalConfiguration.current.screenHeightDp.dp
    val decodeSize = mangaPageDecodeSize(
        paged = paged,
        sidePaddingPercent = settings.sidePaddingPercent,
        pageScaleType = settings.pageScaleType,
        widePageMode = settings.widePageMode,
    )
    val webtoonSizeModifier = if (paged) Modifier else {
        aspectRatios[page.key]?.takeIf { it > 0f }?.let { Modifier.aspectRatio(it) }
            ?: Modifier.height(fallbackHeight)
    }
    val imagePipelineKey = remember(
        page.key,
        page.bookUrl,
        paged,
        settings.sourceOrigin,
        settings.enableEInk,
        settings.eInkThreshold,
        settings.enableGray,
        settings.disableCrossFade,
        page.retryRevision,
        decodeSize,
    ) {
        listOf(
            page.key,
            page.bookUrl,
            paged,
            settings.sourceOrigin,
            settings.enableEInk,
            settings.eInkThreshold,
            settings.enableGray,
            settings.disableCrossFade,
            page.retryRevision,
            decodeSize,
        )
    }
    val fileOwner = remember(imagePipelineKey) { MangaImageFileOwner() }
    var webtoonImageDisplayed by remember(imagePipelineKey) { mutableStateOf(false) }
    var webtoonResult by remember(imagePipelineKey) { mutableStateOf<SuccessResult?>(null) }
    val request = remember(imagePipelineKey) {
        page.imageRequest(
            settings = settings,
            context = context,
            decodeSize = decodeSize,
            fileOwner = fileOwner,
            onFileTransfer = {
                onIntent(
                    MangaReaderIntent.PageLoadStarted(
                        page.requestId,
                        force = true
                    )
                )
            },
            onAspectRatio = { ratio -> updateRatio?.invoke(page.key, ratio) },
            onStart = { onIntent(MangaReaderIntent.PageLoadStarted(page.requestId)) },
            onSuccess = { onIntent(MangaReaderIntent.PageLoadSucceeded(page.requestId)) },
            onError = { message ->
                onIntent(MangaReaderIntent.PageLoadFailed(page.requestId, message))
            },
        )
    }
    val imageRatio = aspectRatios[page.key]
    val isHorizontalPager = settings.scrollMode == MangaScrollMode.PAGE_LEFT_TO_RIGHT ||
        settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
    DisposableEffect(
        page.key,
        imagePipelineKey,
        settings.autoBackground,
        settings.menuColorSource,
        settings.sourceOrigin,
        isHorizontalPager,
        imageRatio,
    ) {
        if (!isHorizontalPager || imageRatio == null ||
            (!settings.autoBackground && settings.menuColorSource != 1) ||
            automaticBackgrounds.containsKey(page.key)
        ) {
            onDispose { }
        } else {
            val disposable = imageLoader.enqueue(
                page.backgroundColorRequest(
                    context = context,
                    sourceOrigin = settings.sourceOrigin,
                    fallbackColor = settings.backgroundColor,
                    aspectRatio = imageRatio,
                    fileOwner = fileOwner,
                ) { colors ->
                    automaticBackgrounds[page.key] = colors
                },
            )
            onDispose(disposable::dispose)
        }
    }

    val isWidePage = imageRatio != null && imageRatio > 1f
    val contentScale = when {
        isWidePage && settings.widePageMode == MangaWidePageMode.FIT_WIDTH -> ContentScale.FillWidth
        settings.pageScaleType == MangaPageScaleType.STRETCH -> ContentScale.FillBounds
        settings.pageScaleType == MangaPageScaleType.FIT_WIDTH -> ContentScale.FillWidth
        settings.pageScaleType == MangaPageScaleType.FIT_HEIGHT -> ContentScale.FillHeight
        settings.pageScaleType == MangaPageScaleType.ORIGINAL -> ContentScale.None
        settings.pageScaleType == MangaPageScaleType.SMART_FIT && isWidePage -> ContentScale.FillWidth
        else -> ContentScale.Fit
    }
    val rotateWidePage = paged && isWidePage &&
        settings.widePageMode == MangaWidePageMode.ROTATE_TO_FIT
    val pageBackground = if (isHorizontalPager && settings.autoBackground) {
        automaticBackgrounds[page.key]?.let { colors ->
            Brush.verticalGradient(colors = listOf(colors.top, colors.bottom))
        }
    } else null

    val contentDescription = stringResource(
        R.string.manga_reader_page_description,
        page.chapterName,
        page.pageIndex + 1,
    )
    val imageModifier = modifier
        .then(webtoonSizeModifier)
        .rotateWidePage(rotateWidePage)
        .then(pageBackground?.let { Modifier.background(it) } ?: Modifier)
        .onSizeChanged { imageViewportSize = it }
        .onGloballyPositioned { positionInRoot = it.positionInRoot() }

    // 仅分页模式使用；条漫的缩放/平移在 WebtoonMangaList 外层统一处理，见下方分支说明。
    val zoomableImageState = rememberZoomableImageState(
        rememberZoomableState(
            ZoomSpec(
                maxZoomFactor = MAX_PAGE_ZOOM,
                minZoomFactor = MIN_PAGE_ZOOM,
            )
        ),
    )
    if (paged) {
        val doubleClickToZoom = remember(settings.zoomStartPosition, settings.scrollMode, imageViewportSize) {
            DoubleClickToZoomListener { zoomableState, tappedPosition ->
                if ((zoomableState.zoomFraction ?: 0f) > 0.05f) {
                    zoomableState.resetZoom()
                } else {
                    val focalX = when (settings.zoomStartPosition) {
                        MangaZoomStartPosition.LEFT -> 0f
                        MangaZoomStartPosition.RIGHT -> imageViewportSize.width.toFloat()
                        MangaZoomStartPosition.AUTOMATIC -> if (
                            settings.scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
                        ) imageViewportSize.width.toFloat() else 0f
                        else -> tappedPosition.x
                    }
                    zoomableState.zoomTo(
                        zoomFactor = WEBTOON_DOUBLE_TAP_ZOOM,
                        centroid = Offset(focalX, tappedPosition.y),
                    )
                }
            }
        }
        Box(imageModifier) {
            ZoomableImage(
                image = rememberMangaZoomableImageSource(
                    request, imageLoader, fileOwner,
                    wholeImage = settings.enableEInk,
                ),
                contentDescription = contentDescription,
                state = zoomableImageState,
                gestures = if (settings.disableScale || !interactionsEnabled) {
                    EnabledZoomGestures.None
                } else {
                    EnabledZoomGestures.ZoomAndPan
                },
                contentScale = contentScale,
                colorFilter = mangaColorFilter(settings),
                onDoubleClick = doubleClickToZoom,
                onClick = if (interactionsEnabled && !viewportHandlesClicks) { tap ->
                    clickAction(
                        settings = settings,
                        onIntent = onIntent,
                        offset = positionInRoot + tap - viewportOrigin,
                        width = viewportSize.width,
                        height = viewportSize.height,
                    )
                } else null,
                onLongClick = if (interactionsEnabled && settings.longPressEnabled) { _ ->
                    onIntent(MangaReaderIntent.LongPressPage(page.key))
                } else null,
                modifier = Modifier.fillMaxSize(),
            )
            MangaImageLoadOverlay(
                page.loadState,
                zoomableImageState.isImageDisplayed || zoomableImageState.isPlaceholderDisplayed,
                { onIntent(MangaReaderIntent.RetryPage(page.key)) },
                { onIntent(MangaReaderIntent.RetryFailedPagesInChapter(page.chapterIndex)) },
            )
        }
        return
    }

    Box(imageModifier) {
        // Coil 整图作为预览，原图区域解码补充可见部分的高清细节。
        // 缩放/平移仍由 WebtoonMangaList 统一处理。
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            colorFilter = mangaColorFilter(settings),
            modifier = Modifier.fillMaxSize(),
            onLoading = {
                webtoonImageDisplayed = it.painter != null
                webtoonResult = null
            },
            onSuccess = {
                webtoonImageDisplayed = true
                webtoonResult = it.result
            },
            onError = {
                webtoonImageDisplayed = false
                webtoonResult = null
            },
        )
        MangaWebtoonTiles(
            result = webtoonResult,
            owner = fileOwner,
            layoutSize = imageViewportSize,
            position = positionInRoot,
            viewport = Rect(
                viewportOrigin, androidx.compose.ui.geometry.Size(
                    viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                )
            ),
            colorFilter = mangaColorFilter(settings),
            transformations = remember(request) { request.transformations.toImmutableList() },
            backgroundColor = settings.backgroundColor.copy(alpha = 1f),
        )
        MangaImageLoadOverlay(
            page.loadState,
            webtoonImageDisplayed,
            { onIntent(MangaReaderIntent.RetryPage(page.key)) },
            { onIntent(MangaReaderIntent.RetryFailedPagesInChapter(page.chapterIndex)) },
        )
    }
}

@Composable
private fun MangaImageLoadOverlay(
    loadState: MangaPageLoadState,
    imageDisplayed: Boolean,
    onRetry: () -> Unit,
    onRetryChapter: () -> Unit,
) {
    // 原图获取、预览解码与实际绘制不同步。已有图片时不叠加转圈，失败仍可重试。
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(imageDisplayed, loadState is MangaPageLoadState.Failed) {
        showLoading = false
        if (!imageDisplayed && loadState !is MangaPageLoadState.Failed) {
            delay(150)
            showLoading = true
        }
    }
    if (loadState !is MangaPageLoadState.Failed && (imageDisplayed || !showLoading)) return
    if (loadState is MangaPageLoadState.Failed) {
        // 失败态保留深色底便于重试按钮可读；加载/排队态不再整页压黑罩，
        // 避免已解码淡入的图被一层「阴影」盖住（#2082）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MediumOutlinedButton(onClick = onRetry, text = stringResource(R.string.retry))
                MediumOutlinedButton(
                    onClick = onRetryChapter,
                    text = stringResource(R.string.manga_reader_retry_failed_chapter),
                )
            }
        }
    } else {
        // 加载中不再整页压黑罩（#2082），因此百分比必须自带底色，
        // 否则在纯白/纯黑的漫画页上都读不出来。
        val progress = (loadState as? MangaPageLoadState.Loading)?.progress
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppCircularProgressIndicator(progress = progress?.let { it / 100f })
                if (progress != null) {
                    Text(
                        text = "$progress%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * 漫画正文的目标解码尺寸。
 *
 * 使用稳定的窗口尺寸，不读单页布局约束：条漫页的高度在拿到宽高比后会变化。
 * 预取与展示共用此规则，分页按显示模式选择需要满足的宽度或高度。
 */
@Composable
private fun mangaPageDecodeSize(
    paged: Boolean,
    sidePaddingPercent: Int,
    pageScaleType: Int,
    widePageMode: Int,
): Size {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    return mangaPageDecodeSize(
        viewport = with(density) {
            IntSize(
                configuration.screenWidthDp.dp.roundToPx(),
                configuration.screenHeightDp.dp.roundToPx(),
            )
        },
        paged = paged,
        sidePaddingPercent = sidePaddingPercent,
        pageScaleType = pageScaleType,
        widePageMode = widePageMode,
    )
}

internal fun mangaPageDecodeSize(
    viewport: IntSize,
    paged: Boolean,
    sidePaddingPercent: Int,
    pageScaleType: Int,
    widePageMode: Int,
): Size {
    // ContentScale.None 必须保留原图固有尺寸。
    if (paged && pageScaleType == MangaPageScaleType.ORIGINAL) return Size.ORIGINAL
    val width = viewport.width.coerceAtLeast(1)
    val height = viewport.height.coerceAtLeast(1)
    val rotated = paged && widePageMode == MangaWidePageMode.ROTATE_TO_FIT
    // 宽图才旋转，但预取时尚无宽高比；取两种方向的上界，避免加载后重发请求。
    val targetWidth = if (rotated) maxOf(width, height) else width
    val targetHeight = if (rotated) maxOf(width, height) else height
    if (paged && pageScaleType == MangaPageScaleType.FIT_HEIGHT) {
        // 宽图的 FIT_WIDTH 优先于普通页缩放。未知宽高比时同时满足两条显示路径。
        if (widePageMode == MangaWidePageMode.FIT_WIDTH) {
            return Size(Dimension(targetWidth), Dimension(targetHeight))
        }
        return Size(Dimension.Undefined, Dimension(targetHeight))
    }
    if (paged && pageScaleType == MangaPageScaleType.STRETCH) {
        return Size(Dimension(targetWidth), Dimension(targetHeight))
    }
    val fraction = if (paged) {
        1f
    } else {
        1f - sidePaddingPercent.coerceIn(0, 45) * 2f / 100f
    }
    return Size(
        width = Dimension((targetWidth * fraction).roundToInt().coerceAtLeast(1)),
        height = Dimension.Undefined,
    )
}

private fun MangaReaderItemUi.Page.imageRequest(
    settings: MangaReaderSettings,
    context: android.content.Context,
    decodeSize: Size,
    fileOwner: MangaImageFileOwner? = null,
    onFileTransfer: () -> Unit = {},
    onAspectRatio: (Float) -> Unit = {},
    onStart: () -> Unit = {},
    onSuccess: () -> Unit = {},
    onError: (String?) -> Unit = {},
): ImageRequest {
    val memoryCacheKey = "manga-page-v2:$bookUrl:$imageUrl:${settings.sourceOrigin}:" +
        "${settings.enableEInk}:${settings.eInkThreshold}:${settings.enableGray}:${fileOwner != null}:$retryRevision"
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(true)
        // 稳定解码目标避免条漫首次占位高度参与降采样。
        // Coil 检查缓存预览是否满足目标尺寸；分页放大清晰度由原图区域解码补足。
        .size(decodeSize)
        // STRETCH 的宽、高都必须满足，否则 FIT 降采样后会在另一个轴上被放大。
        // 只有一个轴有目标值时（适应宽/高、条漫），Coil 按该轴计算缩放。
        .scale(Scale.FILL)
        // 展示、背景取色与回退共享稳定身份；仅数据预取不读写位图缓存。
        .memoryCacheKey(memoryCacheKey)
        .placeholderMemoryCacheKey(memoryCacheKey)
        .apply {
            extras[CoverExtras.Manga] = true
            extras[CoverExtras.SourceOrigin] = settings.sourceOrigin
            extras[CoverExtras.MangaBookUrl] = bookUrl
            extras[CoverExtras.MangaFileOwner] = fileOwner
            extras[CoverExtras.MangaFileTransferStarted] = onFileTransfer
            extras[CoverExtras.MangaAspectRatio] = onAspectRatio
            extras[CoverExtras.MangaWebtoon] = settings.scrollMode == MangaScrollMode.WEBTOON ||
                    settings.scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
        }
        .apply {
            if (settings.enableEInk) transformations(MangaEInkTransformation(settings.eInkThreshold))
            crossfade(!settings.disableCrossFade)
        }
        .listener(onStart = { _ -> onStart() }, onError = { _, result ->
            onError(result.throwable.localizedMessage)
        }, onSuccess = { _, result ->
            val image = result.image
            // 在线页使用原图/EXIF 尺寸，不用降采样后的近似比例反复改变布局。
            if (fileOwner?.aspectRatio() == null && image.width > 0 && image.height > 0) {
                onAspectRatio(image.width.toFloat() / image.height)
            }
            onSuccess()
        })
        .build()
}

private fun MangaReaderItemUi.Page.backgroundColorRequest(
    context: android.content.Context,
    sourceOrigin: String?,
    fallbackColor: Color,
    aspectRatio: Float,
    fileOwner: MangaImageFileOwner? = null,
    onColors: (MangaPageEdgeColors) -> Unit,
): ImageRequest = ImageRequest.Builder(context)
    .data(imageUrl)
    .let { builder ->
        backgroundDecodeSize(aspectRatio).let { size ->
            builder.size(size.width, size.height)
        }
    }
    .allowHardware(false)
    .apply {
        extras[CoverExtras.Manga] = true
        extras[CoverExtras.SourceOrigin] = sourceOrigin
        extras[CoverExtras.MangaBookUrl] = bookUrl
        extras[CoverExtras.MangaFileOwner] = fileOwner
    }
    .listener(onSuccess = { _, result ->
        onColors(extractMangaEdgeColors(result.image.toBitmap(), fallbackColor))
    })
    .build()

private fun backgroundDecodeSize(aspectRatio: Float): IntSize {
    val shortEdge = 256
    val longEdge = 1024
    val ratio = aspectRatio.coerceIn(0.05f, 20f)
    return if (ratio <= 1f) {
        IntSize(shortEdge, (shortEdge / ratio).roundToInt().coerceAtMost(longEdge))
    } else {
        IntSize((shortEdge * ratio).roundToInt().coerceAtMost(longEdge), shortEdge)
    }
}

/**
 * Extracts the two page-edge colors from a small decode of the page.
 *
 * Sampling several inset rows avoids a one-pixel scan line or a page border deciding the result.
 * The most common quantized color bucket preserves a large color block while rejecting sparse
 * line art and text. Four vertical edge tracks are also scanned in 25 segments, following the
 * Komikku approach, to recognize a reliably white or dark page edge.
 */
internal fun extractMangaEdgeColors(
    bitmap: android.graphics.Bitmap,
    fallbackColor: Color = Color.Black,
): MangaPageEdgeColors {
    fun edgeColor(top: Boolean): Color {
        if (bitmap.width == 0 || bitmap.height == 0) return fallbackColor

        val horizontalInset = (bitmap.width * 0.0275f).toInt().coerceAtMost(bitmap.width / 3)
        val verticalInset = (bitmap.height * 0.0125f).toInt()
        val stripHeight = (bitmap.height * 0.06f).toInt().coerceIn(2, 16)
        val startY = if (top) {
            verticalInset
        } else {
            (bitmap.height - verticalInset - stripHeight).coerceAtLeast(0)
        }
        val endY = (startY + stripHeight).coerceAtMost(bitmap.height)
        val startX = horizontalInset
        val endX = (bitmap.width - horizontalInset).coerceAtLeast(startX + 1)
        val stepX = ((endX - startX) / 96).coerceAtLeast(1)
        val stepY = ((endY - startY) / 8).coerceAtLeast(1)
        val buckets = mutableMapOf<Int, MutableList<Int>>()

        for (y in startY until endY step stepY) {
            for (x in startX until endX step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 128) continue
                val bucket =
                    (android.graphics.Color.red(pixel) / 24 shl 8) or
                        (android.graphics.Color.green(pixel) / 24 shl 4) or
                        (android.graphics.Color.blue(pixel) / 24)
                buckets.getOrPut(bucket) { mutableListOf() }.add(pixel)
            }
        }

        val dominant = buckets.maxByOrNull { it.value.size }?.value ?: return fallbackColor
        val dominantColor = Color(
            red = dominant.sumOf(android.graphics.Color::red) / dominant.size,
            green = dominant.sumOf(android.graphics.Color::green) / dominant.size,
            blue = dominant.sumOf(android.graphics.Color::blue) / dominant.size,
        )
        val edgeRun = mangaEdgeRun(bitmap, top)
        return when {
            edgeRun.white >= 6 -> Color.White
            edgeRun.dark >= 6 -> dominantColor.takeIf { it.isMangaDark() } ?: Color.Black
            else -> dominantColor
        }
    }

    return MangaPageEdgeColors(
        top = edgeColor(top = true),
        bottom = edgeColor(top = false),
    )
}

private data class MangaEdgeRun(val white: Int, val dark: Int)

private fun mangaEdgeRun(bitmap: android.graphics.Bitmap, top: Boolean): MangaEdgeRun {
    val left = (bitmap.width * 0.0275f).toInt().coerceIn(0, bitmap.width - 1)
    val right = (bitmap.width - left - 1).coerceAtLeast(left)
    val offset = (bitmap.width * 0.01f).toInt().coerceAtLeast(1)
    val tracks = intArrayOf(
        left,
        right,
        (left + offset).coerceAtMost(bitmap.width - 1),
        (right - offset).coerceAtLeast(0),
    )
    val classes = (0 until 25).map { index ->
        val y = if (top) {
            index * (bitmap.height - 1) / 24
        } else {
            (24 - index) * (bitmap.height - 1) / 24
        }
        val pixels = tracks.map { x -> bitmap.getPixel(x, y) }
        when {
            pixels.count { it.isMangaWhite() } >= 3 -> 1
            pixels.count { it.isMangaDark() } >= 3 -> -1
            else -> 0
        }
    }
    return MangaEdgeRun(
        white = classes.takeWhile { it == 1 }.size,
        dark = classes.takeWhile { it == -1 }.size,
    )
}

private fun Int.isMangaDark(): Boolean =
    android.graphics.Color.red(this) < 40 &&
        android.graphics.Color.green(this) < 40 &&
        android.graphics.Color.blue(this) < 40 &&
        android.graphics.Color.alpha(this) > 200

private fun Int.isMangaWhite(): Boolean =
    android.graphics.Color.red(this) + android.graphics.Color.green(this) +
        android.graphics.Color.blue(this) > 740 && android.graphics.Color.alpha(this) > 200

private fun Color.isMangaDark(): Boolean =
    red * 255f < 40f && green * 255f < 40f && blue * 255f < 40f

internal fun zoomStartOffset(
    position: Int,
    size: IntSize,
    scale: Float,
    rightToLeft: Boolean,
): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    return when (position) {
        MangaZoomStartPosition.LEFT -> Offset(maxX, 0f)
        MangaZoomStartPosition.RIGHT -> Offset(-maxX, 0f)
        MangaZoomStartPosition.AUTOMATIC -> Offset(if (rightToLeft) -maxX else maxX, 0f)
        else -> Offset.Zero
    }
}

private fun mangaColorFilter(settings: MangaReaderSettings): ColorFilter? =
    mangaColorMatrix(settings)?.let(ColorFilter::colorMatrix)

/** 灰度作用于最终绘制，预览、瓦片、动画和整图回退共用相同滤镜。 */
internal fun mangaColorMatrix(settings: MangaReaderSettings): ColorMatrix? {
    val grayscale = settings.enableGray && !settings.enableEInk
    if (!grayscale && settings.filterRed == 0 && settings.filterGreen == 0 &&
        settings.filterBlue == 0 && settings.filterAlpha == 0
    ) return null
    val matrix = ColorMatrix()
    if (grayscale) matrix.setToSaturation(0f)
    val scales = floatArrayOf(
        (255 - settings.filterRed) / 255f,
        (255 - settings.filterGreen) / 255f,
        (255 - settings.filterBlue) / 255f,
        (255 - settings.filterAlpha) / 255f,
    )
    for (row in 0..3) for (column in 0..4) matrix[row, column] *= scales[row]
    return matrix
}

private fun clickAction(
    settings: MangaReaderSettings,
    onIntent: (MangaReaderIntent) -> Unit,
    offset: Offset,
    width: Int,
    height: Int,
) {
    val action = mangaClickActionAt(settings.clickActions, offset.x, offset.y, width, height)
    if ((action == 1 || action == 2) && settings.disableClickScroll) return
    performMangaClickAction(action, onIntent)
}

private fun performMangaClickAction(
    action: Int,
    onIntent: (MangaReaderIntent) -> Unit,
) {
    when (action) {
        -1 -> Unit
        0 -> onIntent(MangaReaderIntent.ToggleMenu)
        1 -> onIntent(MangaReaderIntent.PageStep(1))
        2 -> onIntent(MangaReaderIntent.PageStep(-1))
        3 -> onIntent(MangaReaderIntent.NextChapter)
        4 -> onIntent(MangaReaderIntent.PreviousChapter)
    }
}

internal fun isDoublePageActive(mode: Int, viewport: IntSize): Boolean =
    mode == MangaDoublePageMode.ALWAYS ||
        mode == MangaDoublePageMode.LANDSCAPE && viewport.width > viewport.height
