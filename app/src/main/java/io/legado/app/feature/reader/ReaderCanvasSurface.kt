package io.legado.app.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.feature.reader.core.accessibility.ReaderAccessibilityPolicy
import io.legado.app.feature.reader.core.gesture.PullBookmarkDefaults
import io.legado.app.feature.reader.core.gesture.PullBookmarkGesture
import io.legado.app.feature.reader.core.gesture.ReaderGestureSettingsPolicy
import io.legado.app.feature.reader.core.gesture.ReaderMainAxisPolicy
import io.legado.app.feature.reader.core.gesture.ReaderPageViewportLayout
import io.legado.app.feature.reader.core.gesture.ReaderTapAction
import io.legado.app.feature.reader.core.gesture.ReaderTapActionGrid
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderlineRun
import io.legado.app.feature.reader.core.model.ReaderImageDrawLayout
import io.legado.app.feature.reader.core.model.ReaderNineSliceLayout
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageTip
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTipAlignment
import io.legado.app.feature.reader.core.model.ReaderTipRow
import io.legado.app.feature.reader.core.model.ReaderTipRowLayout
import io.legado.app.feature.reader.core.model.ReaderTipVisual
import io.legado.app.feature.reader.core.model.contentClipPadPx
import io.legado.app.feature.reader.core.model.emphasisUnderlineRunsFor
import io.legado.app.feature.reader.core.model.textBackgroundRuns
import io.legado.app.feature.reader.core.navigation.ReaderPageNavigator
import io.legado.app.feature.reader.core.readaloud.ReaderVisibleTextPosition
import io.legado.app.feature.reader.core.readaloud.ReaderVisibleTextPositionPolicy
import io.legado.app.feature.reader.core.selection.ReaderPageChangeOrigin
import io.legado.app.feature.reader.core.selection.ReaderSelection
import io.legado.app.feature.reader.core.selection.ReaderSelectionEndpoint
import io.legado.app.feature.reader.core.selection.ReaderSelectionLifecyclePolicy
import io.legado.app.feature.reader.core.selection.ReaderSelectionMenuAnchor
import io.legado.app.feature.reader.core.selection.ReaderSelectionPolicy
import io.legado.app.feature.reader.core.selection.mergeSelectionBounds
import io.legado.app.feature.reader.core.selection.selectionPages
import io.legado.app.feature.reader.core.style.mergeBackgroundBounds
import io.legado.app.feature.reader.core.transition.CurlPoint
import io.legado.app.feature.reader.core.transition.PageCurlFrame
import io.legado.app.feature.reader.core.transition.PageCurlGeometry
import io.legado.app.feature.reader.core.transition.ReaderAutoPagePolicy
import io.legado.app.feature.reader.core.transition.ReaderAutoPageVisualMode
import io.legado.app.feature.reader.core.transition.ReaderCoverShadowPolicy
import io.legado.app.feature.reader.core.transition.ReaderCurlTouchPolicy
import io.legado.app.feature.reader.core.transition.ReaderCurlVisualPolicy
import io.legado.app.feature.reader.core.transition.ReaderHorizontalDrag
import io.legado.app.feature.reader.core.transition.ReaderPageTransform
import io.legado.app.feature.reader.core.transition.ReaderPageTransition
import io.legado.app.feature.reader.core.transition.ReaderPageTransitionPolicy
import io.legado.app.feature.reader.core.transition.ReaderProgrammaticTurnPolicy
import io.legado.app.feature.reader.core.transition.ReaderScrollCrossing
import io.legado.app.feature.reader.core.transition.ReaderScrollPolicy
import io.legado.app.feature.reader.core.transition.ReaderScrollResult
import io.legado.app.feature.reader.core.transition.ReaderTransitionDecision
import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.core.transition.ReaderViewportLayerPolicy
import io.legado.app.feature.reader.core.transition.transforms
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderBookmarkBadgeRenderer
import io.legado.app.feature.reader.platform.ReaderPageDecorationDrawCache
import io.legado.app.feature.reader.platform.ReaderTextBackgroundLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Compose Canvas reader surface. Gesture arbitration and transforms are independent of ReadView. */
// 把手圆挂在行底部下方、顶端圆周与竖线末端相切；圆区域在拖动命中时视作竖线的延伸。
private val SelectionHandleRadius = 7.dp
private val SelectionHandleStrokeWidth = 2.dp
private const val SelectionHandleFadeOutMillis = 90
private const val SelectionHandleFadeInMillis = 140

@Composable
fun ReaderCanvasSurface(
    hostPages: ReaderPageWindow,
    transitionMode: ReaderTransitionMode,
    backgroundColor: Color,
    backgroundImage: Drawable?,
    backgroundRevision: Long,
    backgroundImageAlpha: Float,
    selectionColor: Color,
    textAccentColor: Color,
    autoPageIndicatorColor: Color,
    modifier: Modifier = Modifier,
    onPreviousPage: () -> ReaderPageWindow?,
    onNextPage: () -> ReaderPageWindow?,
    onPageBoundaryReached: (ReaderTurnDirection) -> Unit,
    /**
     * 书中业务上是否存在邻章（对照旧 View `ReadView.hasNextChapter()` / `hasPrevChapter()`）。
     * 邻章排版可能滞后于阅读进度，此时窗口里还没有邻页，但翻页必须照常放行，
     * 由宿主决定"预置加载占位页"还是"直接启动该章排版"。
     */
    hasNextChapter: () -> Boolean,
    hasPreviousChapter: () -> Boolean,
    onToggleMenu: () -> Unit,
    onToggleBookmark: () -> Unit,
    swipeToBookmarkEnabled: Boolean,
    hasBookmarkOnCurrentPage: () -> Boolean,
    cachedImage: (ReaderElement.Image) -> Bitmap?,
    loadImage: suspend (ReaderElement.Image) -> Bitmap?,
    autoPageActive: Boolean,
    autoPagePaused: Boolean,
    autoReadSpeedSeconds: Int,
    isEInkMode: Boolean,
    onAutoPageStop: () -> Unit,
    onShowSelectionMenu: (ReaderSelection, String, ReaderSelectionMenuAnchor) -> Unit,
    onDismissSelectionMenu: () -> Unit,
    onElementClick: (ReaderElement) -> Boolean,
    onElementLongPress: (ReaderElement, Float, Float) -> Boolean,
    selectionEnabled: Boolean,
    selectionHapticsEnabled: Boolean,
    tapActionGrid: ReaderTapActionGrid,
    onTapAction: (ReaderTapAction) -> Unit,
    onReaderInteraction: () -> Unit,
    configuredTouchSlopPx: Int,
    noAnimationScrollPage: Boolean,
    externalPageTurns: Flow<ReaderTurnDirection>,
    externalSelectionCancels: Flow<Unit>,
    onVisibleBodyTextPositionProvider: ((() -> ReaderVisibleTextPosition?)?) -> Unit,
) {
    // 滚动跨页同步换窗：跨页帧内宿主回调直接返回新窗口，先写入 pending 供绘制与
    // 手势立即使用；宿主 StateFlow 回声（同一实例）或外部窗口变化会将其清除。
    // 对照旧 View 版 ContentTextView.scroll 的同步折算语义。
    var scrollPendingWindow by remember { mutableStateOf<ReaderPageWindow?>(null) }
    var scrollPendingBase by remember { mutableStateOf<ReaderPageWindow?>(null) }
    val pendingWindow = scrollPendingWindow
    val pages = when {
        pendingWindow == null -> hostPages
        hostPages === scrollPendingBase || hostPages === pendingWindow -> pendingWindow
        else -> hostPages
    }
    // 手势协程长驻（pointerInput 只在 key 变化时重启），闭包捕获的组合期值会过期；
    // 热路径窗口必须经 rememberUpdatedState 现读。对照旧 View 版每次事件现读
    // curPage 字段、shutiao 版向长驻协程注入最新页源的语义。
    val latestPages by rememberUpdatedState(pages)
    /** 输入/绘制热路径读取的窗口：pending 未清时优先（含跨页当帧）。 */
    fun currentPageWindow(): ReaderPageWindow = scrollPendingWindow ?: latestPages
    val current = pages.current ?: return
    val pageBackgroundImage = remember(backgroundImage, backgroundRevision) {
        backgroundImage?.isolatedCopy()
    }
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val scrollDecay = rememberSplineBasedDecay<Float>()
    var displayOffset by remember { mutableFloatStateOf(0f) }
    var transition by remember { mutableStateOf(ReaderPageTransition()) }
    var pageMotionJob by remember { mutableStateOf<Job?>(null) }
    // The first curl frame starts at a corner. Animate it into the safe fold position so
    // the entering page is revealed instead of popping in when horizontal capture begins.
    var curlRevealProgress by remember { mutableFloatStateOf(1f) }
    var curlRevealJob by remember { mutableStateOf<Job?>(null) }
    var pendingTurn by remember { mutableStateOf<ReaderTurnDirection?>(null) }
    var pendingTurnOrigin by remember { mutableStateOf<ReaderPageId?>(null) }
    val latestPreviousPage by rememberUpdatedState(onPreviousPage)
    val latestNextPage by rememberUpdatedState(onNextPage)
    val latestPageBoundaryReached by rememberUpdatedState(onPageBoundaryReached)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapter)
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapter)

    /**
     * 翻页放行：窗口里有邻页，或书中业务上存在邻章。手势协程长驻，必须现读最新值，
     * 否则读到的是协程启动时的窗口（对照旧 View 每次事件现读 pageSource 的语义）。
     */
    fun canTurn(direction: ReaderTurnDirection, window: ReaderPageWindow = latestPages): Boolean =
        when (direction) {
            ReaderTurnDirection.NEXT -> ReaderPageNavigator.canTurnNext(
                window,
                latestHasNextChapter()
            )

            ReaderTurnDirection.PREVIOUS ->
                ReaderPageNavigator.canTurnPrevious(window, latestHasPreviousChapter())
        }
    val latestAutoPageStop by rememberUpdatedState(onAutoPageStop)
    val latestAutoPagePaused by rememberUpdatedState(autoPagePaused)
    val latestAutoPageActive by rememberUpdatedState(autoPageActive)
    val latestShowSelectionMenu by rememberUpdatedState(onShowSelectionMenu)
    val latestDismissSelectionMenu by rememberUpdatedState(onDismissSelectionMenu)
    val latestSelectionHapticsEnabled by rememberUpdatedState(selectionHapticsEnabled)
    val latestSelectionEnabled by rememberUpdatedState(selectionEnabled)
    val latestTapActionGrid by rememberUpdatedState(tapActionGrid)
    val latestTapAction by rememberUpdatedState(onTapAction)
    val latestReaderInteraction by rememberUpdatedState(onReaderInteraction)
    val latestNoAnimationScrollPage by rememberUpdatedState(noAnimationScrollPage)
    var bookmarkOffset by remember { mutableFloatStateOf(0f) }
    var bookmarkArmed by remember { mutableStateOf(false) }
    var bookmarkWillRemove by remember { mutableStateOf(false) }
    var bookmarkReturnJob by remember { mutableStateOf<Job?>(null) }
    val latestSwipeToBookmarkEnabled by rememberUpdatedState(swipeToBookmarkEnabled)
    val latestHasBookmark by rememberUpdatedState(hasBookmarkOnCurrentPage)
    val latestToggleBookmark by rememberUpdatedState(onToggleBookmark)
    DisposableEffect(transitionMode) {
        onDispose {
            // 模式切换时滚动惯性对换页模式已无意义，必须中止所有页运动动画并复位。
            pageMotionJob?.cancel()
            pendingTurn = null
            pendingTurnOrigin = null
            displayOffset = 0f
            transition = ReaderPageTransition()
            bookmarkReturnJob?.cancel()
            curlRevealJob?.cancel()
            bookmarkOffset = 0f
            bookmarkArmed = false
        }
    }
    DisposableEffect(current.widthPx, current.heightPx, current.layoutRevision) {
        val wasScrollMode = transitionMode == ReaderTransitionMode.SCROLL
        onDispose {
            // 滚动模式的跨页换窗也会更换 current（含跨章 layoutRevision 变化）；
            // fling/点击步进动画每帧重读窗口尺寸，必须跨页延续。只有换页类动画
            // 才在换页/视口变化时中止。
            if (!wasScrollMode) pageMotionJob?.cancel()
        }
    }
    var curlTouchY by remember { mutableFloatStateOf(1f) }
    var curlTouchX by remember { mutableFloatStateOf(0f) }
    var curlCornerY by remember { mutableStateOf(0f) }
    // 滚动偏移只在 graphicsLayer 块（layer 属性期）读取：拖拽/fling 帧只更新层变换、
    // 零重组零重绘（对照 shutiao 的 contentOffset 语义）。
    val scrollOffsetState = remember { mutableFloatStateOf(0f) }
    var scrollOffset by scrollOffsetState
    val latestVisibleBodyTextPosition by rememberUpdatedState {
        if (transitionMode == ReaderTransitionMode.SCROLL) {
            ReaderVisibleTextPositionPolicy.firstVisibleBodyText(currentPageWindow(), scrollOffset)
        } else {
            null
        }
    }
    DisposableEffect(onVisibleBodyTextPositionProvider) {
        onVisibleBodyTextPositionProvider { latestVisibleBodyTextPosition() }
        onDispose { onVisibleBodyTextPositionProvider(null) }
    }
    // 滚动跨页折算标志：applyScrollResult 完成一次同步换窗后置位，由 current.id
    // 效应消费——据此区分"自己跨页"与"外部换窗"，后者才把滚动偏移归零。
    var scrollOwnCrossing by remember { mutableStateOf(false) }
    var autoRevealPx by remember { mutableFloatStateOf(0f) }
    var autoPageRemainingMillis by remember(current.id, autoReadSpeedSeconds) {
        mutableLongStateOf(ReaderAutoPagePolicy.pageDurationMillis(autoReadSpeedSeconds))
    }
    // 连续滚动动画运行标志（fling / 点击滚动步距）。旧 View 的
    // `ScrollPageDelegate.onAnimStart/Stop` 会 `autoPager.pause()/resume()`：两者同时写
    // scrollOffset 会叠加成双倍速度，动画期间必须把自动滚屏挂起。
    var scrollMotionActive by remember { mutableStateOf(false) }
    var textSelection by remember { mutableStateOf<ReaderSelection?>(null) }
    // API 28+ 的平台放大镜直接采样当前 Compose View；未指定 sourceCenter 时会立即隐藏。
    // 采样点始终是正在移动的文字端点，而不是被把手遮挡的手指位置。
    var selectionMagnifierSource by remember { mutableStateOf<Offset?>(null) }
    // 拖动中的圆柄属于指针反馈，不属于吸附后的文字布局。二者分离后，端点跨行
    // 可以更新竖线和选区，而圆柄仍沿手指的连续轨迹移动。
    var selectionDragHandleCenter by remember { mutableStateOf<Offset?>(null) }
    var selectionDragEndpoint by remember { mutableStateOf<ReaderSelectionEndpoint?>(null) }
    val selectionHandleRadiusPx = with(LocalDensity.current) { SelectionHandleRadius.toPx() }
    val latestSelectionPausesAutoPage by rememberUpdatedState(textSelection != null)
    var selectionMenuVisible by remember { mutableStateOf(false) }
    var selectionLayoutRevision by remember { mutableLongStateOf(current.layoutRevision) }
    LaunchedEffect(
        pages.previous?.id,
        pages.previous?.revision,
        pages.current.id,
        pages.current.revision,
        pages.next?.id,
        pages.next?.revision,
        pages.nextPlus?.id,
        pages.nextPlus?.revision,
    ) {
        // Paged modes do not compose the adjacent page until a gesture starts. Warm its images
        // while the window is idle so the first animation frame never falls back to placeholders.
        val prefetchSemaphore = Semaphore(2)
        listOfNotNull(pages.current, pages.next, pages.previous, pages.nextPlus)
            .asSequence()
            .flatMap { page -> page.elements.asSequence().filterIsInstance<ReaderElement.Image>() }
            .distinctBy { element -> element.source to element.bounds }
            .forEach { element -> launch { prefetchSemaphore.withPermit { loadImage(element) } } }
    }
    val transforms = transition.copy(offsetPx = displayOffset).transforms(transitionMode)
    fun pageViewportLayout(window: ReaderPageWindow = currentPageWindow()): ReaderPageViewportLayout =
        if (transitionMode == ReaderTransitionMode.SCROLL) {
            ReaderPageViewportLayout.scroll(window, scrollOffset)
        } else {
            ReaderPageViewportLayout.paged(window)
        }
    fun selectionEndpointBound(
        selection: ReaderSelection,
        endpoint: ReaderSelectionEndpoint,
        window: ReaderPageWindow = currentPageWindow(),
    ) = run {
        val bounds = pageViewportLayout(window).selectionBounds(selection).map { it.bounds }
        val visualStart = endpoint == selection.visualStartEndpoint()
        if (visualStart) bounds.firstOrNull() else bounds.lastOrNull()
    }

    fun selectionCursorCenter(
        selection: ReaderSelection,
        endpoint: ReaderSelectionEndpoint,
        window: ReaderPageWindow = currentPageWindow(),
        draggedHandleCenter: Offset? = null,
    ): Offset? {
        val bound = selectionEndpointBound(selection, endpoint, window) ?: return null
        val visualStart = endpoint == selection.visualStartEndpoint()
        val logicalLineCenter = Offset(
            x = if (visualStart) bound.left else bound.right,
            y = (bound.top + bound.bottom) / 2f,
        )
        val logicalHandleCenter = Offset(
            x = logicalLineCenter.x,
            y = bound.bottom + selectionHandleRadiusPx,
        )
        // 拖动时竖线与圆柄作为一个整体平移，放大镜也采样平移后的竖线中段。
        return logicalLineCenter + ((draggedHandleCenter
            ?: logicalHandleCenter) - logicalHandleCenter)
    }
    fun dismissSelectionMenu() {
        selectionMenuVisible = false
        latestDismissSelectionMenu()
    }
    fun showSelectionMenu(selection: ReaderSelection, window: ReaderPageWindow): Boolean {
        val bounds = pageViewportLayout(window).selectionBounds(selection).map { it.bounds }
        val text = selection.selectedText(window.selectionPages())
        val anchor = ReaderSelectionMenuAnchor.from(bounds) ?: return false
        if (text.isEmpty()) return false
        selectionMenuVisible = true
        latestShowSelectionMenu(selection, text, anchor)
        return true
    }
    fun clearSelectionForPageChange(origin: ReaderPageChangeOrigin) {
        if (textSelection != null && ReaderSelectionLifecyclePolicy.shouldClearForPageChange(origin)) {
            textSelection = null
            selectionMagnifierSource = null
            selectionDragHandleCenter = null
            selectionDragEndpoint = null
            dismissSelectionMenu()
        }
    }
    fun completePendingTurn(): ReaderPageWindow? {
        val direction = pendingTurn.takeIf { pendingTurnOrigin == latestPages.current?.id }
        pendingTurn = null
        pendingTurnOrigin = null
        return when (direction) {
            ReaderTurnDirection.PREVIOUS -> latestPreviousPage()
            ReaderTurnDirection.NEXT -> latestNextPage()
            null -> null
        }
    }
    fun settlePageTurn(decision: ReaderTransitionDecision) {
        pageMotionJob?.cancel()
        curlRevealJob?.cancel()
        if (transitionMode == ReaderTransitionMode.SIMULATION) {
            transition.direction?.let { direction ->
                curlTouchX = ReaderCurlTouchPolicy.revealX(
                    direction,
                    curlTouchX,
                    transition.pageExtentPx,
                    curlRevealProgress,
                )
            }
            curlRevealProgress = 1f
        }
        pendingTurn = transition.direction.takeIf { decision.commit }
        pendingTurnOrigin = latestPages.current?.id
        if (transitionMode == ReaderTransitionMode.NONE) {
            completePendingTurn()
            displayOffset = 0f
            transition = ReaderPageTransition()
            return
        }
        val targetCurlX = transition.direction?.let {
            ReaderCurlTouchPolicy.settledX(it, decision.commit, transition.pageExtentPx)
        } ?: curlTouchX
        val durationMillis = if (transitionMode == ReaderTransitionMode.SIMULATION) {
            ReaderCurlTouchPolicy.settleDurationMillis(
                curlTouchX,
                targetCurlX,
                transition.pageExtentPx,
            )
        } else {
            ReaderPageTransitionPolicy.settleDurationMillis(
                transitionMode, displayOffset, decision.targetOffsetPx, transition.pageExtentPx,
            )
        }
        if (durationMillis == 0) {
            displayOffset = decision.targetOffsetPx
            completePendingTurn()
            displayOffset = 0f
            transition = ReaderPageTransition()
            return
        }
        val startOffset = displayOffset
        val startCurlX = curlTouchX
        val startCurlY = curlTouchY
        val targetCurlY = ReaderCurlTouchPolicy.settledY(
            curlCornerY,
            latestPages.current?.heightPx?.toFloat() ?: 0f,
        )
        // 仿真收尾用匀速：折页滑出屏幕是收尾的主体动作，减速收尾会让它
        // 在结束帧前停滞（对照原版 delegate 的 LinearEasing）。
        val settleEasing = if (transitionMode == ReaderTransitionMode.SIMULATION) {
            LinearEasing
        } else {
            FastOutSlowInEasing
        }
        pageMotionJob = animationScope.launch {
            Animatable(startOffset).animateTo(
                decision.targetOffsetPx,
                tween(durationMillis, easing = settleEasing),
            ) {
                displayOffset = value
                if (transitionMode == ReaderTransitionMode.SIMULATION) {
                    val distance = decision.targetOffsetPx - startOffset
                    val fraction = if (distance == 0f) 1f else ((value - startOffset) / distance).coerceIn(0f, 1f)
                    curlTouchX = startCurlX + (targetCurlX - startCurlX) * fraction
                    curlTouchY = startCurlY + (targetCurlY - startCurlY) * fraction
                }
            }
            completePendingTurn()
            displayOffset = 0f
            transition = ReaderPageTransition()
        }
    }
    fun tapPageTurn(direction: ReaderTurnDirection) {
        val window = latestPages
        // 放行以"书中是否还有邻章"为准（对照旧 View TextPageFactory.hasNext/hasPrev）：
        // 邻章排版滞后于阅读进度时窗口里还没有邻页，但仍必须把翻页交给宿主，
        // 由宿主预置加载占位页或启动该章排版；否则停在末页只弹"没有下一页"。
        if (!canTurn(direction, window)) {
            latestPageBoundaryReached(direction)
            return
        }
        val width = window.current?.widthPx?.toFloat() ?: return
        if (transitionMode == ReaderTransitionMode.SIMULATION) {
            curlRevealProgress = 1f
            curlTouchX = ReaderCurlTouchPolicy.programmaticX(direction, width)
            curlTouchY = ReaderCurlTouchPolicy.programmaticY(
                direction, curlTouchY, window.current.heightPx.toFloat(),
            )
            curlCornerY = ReaderCurlTouchPolicy.cornerY(
                direction, curlTouchY, window.current.heightPx.toFloat(),
            )
        }
        val target = if (direction == ReaderTurnDirection.PREVIOUS) width else -width
        transition = ReaderPageTransition(direction, 0f, width, dragging = true)
        displayOffset = 0f
        settlePageTurn(ReaderTransitionDecision(target, commit = true))
    }

    fun startCurlRevealSnap() {
        curlRevealJob?.cancel()
        curlRevealProgress = 0f
        curlRevealJob = animationScope.launch {
            Animatable(0f).animateTo(
                1f,
                tween(100, easing = FastOutSlowInEasing),
            ) { curlRevealProgress = value }
        }
    }
    fun applyScrollResult(result: ReaderScrollResult, window: ReaderPageWindow) {
        val crossing = result.crossing
        if (crossing == null) {
            scrollOffset = result.offsetPx
            return
        }
        // 跨页换窗同步完成：宿主回调当帧返回新窗口，写入 pending 供本帧之后的
        // 绘制与手势直接使用（宿主 StateFlow 回声随后到达，仅确认不等待）。
        // 对照旧 View 版 ContentTextView.scroll 的同步折算语义。
        val newWindow = when (crossing) {
            ReaderScrollCrossing.PREVIOUS -> latestPreviousPage()
            ReaderScrollCrossing.NEXT -> latestNextPage()
        }
        if (newWindow == null) {
            // 邻章业务上存在但排版未就绪（旧 View 的"章节未加载"态）：这次越界偏移是
            // 相对邻页坐标系的，直接写入会把当前页画到页外。停在当前页边界（等同于
            // ReaderScrollPolicy 的 bottom 语义），装载完成后 current.id 变化会归零偏移。
            val current = window.current
            scrollOffset = if (crossing == ReaderScrollCrossing.NEXT && current != null) {
                minOf(0f, current.scrollViewportExtentPx() - current.scrollExtentPx)
            } else {
                0f
            }
            return
        }
        scrollOffset = result.offsetPx
        scrollOwnCrossing = true
        scrollPendingBase = window
        scrollPendingWindow = newWindow
    }

    /**
     * 滚动路径触边界一律静默。
     *
     * 旧 View 的 `ScrollPageDelegate` 触边界时只把偏移钳回边界并重绘，没有任何提示；
     * "没有下一页"的 Toast 属于分页模式（[tapPageTurn] / `PageDelegate`）的行为。滚动
     * 模式保留它会变成连续滑动时的反复弹窗，因此这里显式吞掉，只留出可读的落点。
     */
    fun reportScrollBoundary(@Suppress("UNUSED_PARAMETER") direction: ReaderTurnDirection) {
        if (transitionMode != ReaderTransitionMode.SCROLL) latestPageBoundaryReached(direction)
    }

    fun tapScrollPage(direction: ReaderTurnDirection) {
        val window = currentPageWindow()
        val page = window.current ?: return
        // “保留一行”步距基于连续相邻页合成内容：短页叠加时，下下页的行也可能已露出。
        val distance = ReaderScrollPolicy.pageStep(
            page = page,
            offsetPx = scrollOffset,
            direction = direction,
            previous = window.previous,
            next = window.next,
            nextPlus = window.nextPlus,
        )
        pageMotionJob?.cancel()
        if (!ReaderGestureSettingsPolicy.animatesScrollPage(latestNoAnimationScrollPage)) {
            val window = currentPageWindow()
            val currentPage = window.current ?: return
            val result = ReaderScrollPolicy.apply(
                scrollOffset,
                distance,
                window.previous?.scrollExtentPx ?: 0f,
                currentPage.scrollExtentPx,
                currentPage.scrollViewportExtentPx(),
                ReaderPageNavigator.canTurnPrevious(window, latestHasPreviousChapter()),
                ReaderPageNavigator.canTurnNext(window, latestHasNextChapter()),
            )
            applyScrollResult(result, window)
            if (result.hitBoundary) reportScrollBoundary(direction)
            return
        }
        pageMotionJob = animationScope.launch {
            scrollMotionActive = true
            try {
                // 时长随步距缩放（旧 PageDelegate.startScroll：animationSpeed * |dy| / viewHeight），
                // 不再固定 18 帧——固定帧数会让"保留一行"的短步距走成整屏的时长。
                val durationMillis = ReaderScrollPolicy.stepDurationMillis(
                    distance,
                    page.scrollViewportExtentPx(),
                )
                var lastValue = 0f
                Animatable(0f).animateTo(
                    distance,
                    tween(durationMillis = durationMillis, easing = LinearEasing),
                ) {
                    val delta = value - lastValue
                    lastValue = value
                    val window = currentPageWindow()
                    val currentPage = window.current ?: return@animateTo
                    val result = ReaderScrollPolicy.apply(
                        scrollOffset,
                        delta,
                        window.previous?.scrollExtentPx ?: 0f,
                        currentPage.scrollExtentPx,
                        currentPage.scrollViewportExtentPx(),
                        ReaderPageNavigator.canTurnPrevious(window, latestHasPreviousChapter()),
                        ReaderPageNavigator.canTurnNext(window, latestHasNextChapter()),
                    )
                    applyScrollResult(result, window)
                    // 触边界即停：与 fling 同一条中断路径（animateTo 的块里不能调用
                    // 挂起的 stop()，用异常跳出后在外层收尾）。
                    if (result.hitBoundary) throw ReaderScrollBoundaryReached()
                }
            } catch (_: ReaderScrollBoundaryReached) {
                reportScrollBoundary(direction)
            } finally {
                scrollMotionActive = false
            }
        }
    }
    fun dispatchTapAction(action: ReaderTapAction) {
        when (action) {
            ReaderTapAction.MENU -> onToggleMenu()
            ReaderTapAction.NEXT_PAGE -> if (transitionMode == ReaderTransitionMode.SCROLL) {
                tapScrollPage(ReaderTurnDirection.NEXT)
            } else tapPageTurn(ReaderTurnDirection.NEXT)
            ReaderTapAction.PREVIOUS_PAGE -> if (transitionMode == ReaderTransitionMode.SCROLL) {
                tapScrollPage(ReaderTurnDirection.PREVIOUS)
            } else tapPageTurn(ReaderTurnDirection.PREVIOUS)
            else -> latestTapAction(action)
        }
    }
    fun accessibilityPageTurn(direction: ReaderTurnDirection) {
        if (!ReaderProgrammaticTurnPolicy.shouldAccept(pageMotionJob?.isActive == true)) return
        clearSelectionForPageChange(ReaderPageChangeOrigin.PROGRAMMATIC)
        dispatchTapAction(
            if (direction == ReaderTurnDirection.PREVIOUS) {
                ReaderTapAction.PREVIOUS_PAGE
            } else {
                ReaderTapAction.NEXT_PAGE
            }
        )
    }
    fun showComposeAccessibilityMenu() {
        dispatchTapAction(ReaderTapAction.MENU)
    }
    LaunchedEffect(externalPageTurns) {
        externalPageTurns.collect { direction ->
            if (!ReaderProgrammaticTurnPolicy.shouldAccept(pageMotionJob?.isActive == true)) {
                return@collect
            }
            clearSelectionForPageChange(ReaderPageChangeOrigin.PROGRAMMATIC)
            displayOffset = 0f
            transition = ReaderPageTransition()
            dispatchTapAction(
                if (direction == ReaderTurnDirection.PREVIOUS) {
                    ReaderTapAction.PREVIOUS_PAGE
                } else {
                    ReaderTapAction.NEXT_PAGE
                }
            )
        }
    }
    LaunchedEffect(externalSelectionCancels) {
        externalSelectionCancels.collect {
            textSelection = null
            selectionMagnifierSource = null
            selectionDragHandleCenter = null
            selectionDragEndpoint = null
            selectionMenuVisible = false
        }
    }
    LaunchedEffect(hostPages) {
        // pending 窗口的收尾：宿主回声（与 pending 同实例）到达后解除；外部换窗
        // （其他实例，如跳转/重排）直接丢弃 pending。偏移归零由下方 current.id
        // 效应依据 scrollOwnCrossing 决定，避免两个效应间执行顺序影响结果。
        if (scrollPendingWindow != null && hostPages !== scrollPendingBase) {
            scrollPendingWindow = null
            scrollPendingBase = null
        }
    }
    LaunchedEffect(current.id, current.layoutRevision, transitionMode) {
        val ownCrossing = scrollOwnCrossing
        scrollOwnCrossing = false
        if (transitionMode != ReaderTransitionMode.SCROLL || !ownCrossing) {
            scrollOffset = 0f
        }
        if (pendingTurnOrigin != null && pendingTurnOrigin != current.id) {
            pageMotionJob?.cancel()
            pendingTurn = null
            pendingTurnOrigin = null
            displayOffset = 0f
            transition = ReaderPageTransition()
        }
    }
    LaunchedEffect(current.layoutRevision) {
        val previousRevision = selectionLayoutRevision
        selectionLayoutRevision = current.layoutRevision
        val selection = textSelection
        if (ReaderSelectionLifecyclePolicy.shouldReanchorMenuAfterLayoutChange(
                hasSelection = selection != null,
                menuVisible = selectionMenuVisible,
                previousLayoutRevision = previousRevision,
                currentLayoutRevision = current.layoutRevision,
            ) && selection != null && !showSelectionMenu(selection, latestPages)
        ) {
            textSelection = null
            selectionMagnifierSource = null
            selectionDragHandleCenter = null
            selectionDragEndpoint = null
            dismissSelectionMenu()
        }
    }
    LaunchedEffect(
        autoPageActive,
        autoPagePaused,
        autoReadSpeedSeconds,
        transitionMode,
        isEInkMode,
        scrollMotionActive,
        current.id,
        textSelection,
    ) {
        if (!autoPageActive) {
            autoRevealPx = 0f
            autoPageRemainingMillis = ReaderAutoPagePolicy.pageDurationMillis(autoReadSpeedSeconds)
            return@LaunchedEffect
        }
        if (ReaderSelectionLifecyclePolicy.shouldPauseAutoPage(textSelection != null)) {
            return@LaunchedEffect
        }
        if (autoPagePaused) return@LaunchedEffect
        // 滚动动画期间挂起：对照旧 View 的 ReadView.onScrollAnimStart/Stop →
        // autoPager.pause()/resume()，避免 fling / 点击步距与自动滚屏叠加。
        if (scrollMotionActive) return@LaunchedEffect
        if (ReaderAutoPagePolicy.visualMode(isEInkMode) == ReaderAutoPageVisualMode.DISCRETE) {
            autoRevealPx = 0f
            val plannedMillis = autoPageRemainingMillis
            val startedAt = SystemClock.uptimeMillis()
            try {
                delay(plannedMillis)
                autoPageRemainingMillis = ReaderAutoPagePolicy.pageDurationMillis(autoReadSpeedSeconds)
                if (!canTurn(ReaderTurnDirection.NEXT)) latestAutoPageStop() else latestNextPage()
            } finally {
                if (ReaderAutoPagePolicy.shouldPreserveRemainingTime(
                        menuPaused = latestAutoPagePaused,
                        selectionPaused = latestSelectionPausesAutoPage,
                    )
                ) {
                    autoPageRemainingMillis = ReaderAutoPagePolicy.remainingAfterPause(
                        plannedMillis,
                        SystemClock.uptimeMillis() - startedAt,
                    )
                }
            }
            return@LaunchedEffect
        }
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val elapsedMs = (frame - previousFrame) / 1_000_000f
            previousFrame = frame
            if (transitionMode == ReaderTransitionMode.SCROLL) {
                val window = currentPageWindow()
                val page = window.current ?: continue
                val viewport = page.scrollViewportExtentPx()
                val delta = viewport /
                    ReaderAutoPagePolicy.pageDurationMillis(autoReadSpeedSeconds).toFloat() * elapsedMs
                val result = ReaderScrollPolicy.apply(
                    scrollOffset,
                    -delta,
                    window.previous?.scrollExtentPx ?: 0f,
                    page.scrollExtentPx,
                    page.scrollViewportExtentPx(),
                    ReaderPageNavigator.canTurnPrevious(window, latestHasPreviousChapter()),
                    ReaderPageNavigator.canTurnNext(window, latestHasNextChapter())
                )
                applyScrollResult(result, window)
                // 滚动模式到书末不自动关闭自动翻页：旧 View 的 AutoPager.computeOffset 在
                // isScroll 分支只把累计偏移交给 curPage.scroll() 钳制，"失败即 stop"这条
                // 路径只存在于分页模式的 progress >= height 分支。这里同样保持开关开启态，
                // 由用户停止，避免翻到书末自动阅读被悄悄关掉。
            } else {
                val viewport = current.heightPx.toFloat().coerceAtLeast(1f)
                val delta = viewport /
                    ReaderAutoPagePolicy.pageDurationMillis(autoReadSpeedSeconds).toFloat() * elapsedMs
                autoRevealPx += delta
                if (autoRevealPx >= viewport) {
                    if (!canTurn(ReaderTurnDirection.NEXT)) {
                        latestAutoPageStop()
                        break
                    }
                    latestNextPage()
                    autoRevealPx = 0f
                }
            }
        }
    }
    val bookmarkDescription = stringResource(io.legado.app.R.string.a11y_page_bookmarked)
    val previousPageDescription = stringResource(io.legado.app.R.string.prev_page)
    val nextPageDescription = stringResource(io.legado.app.R.string.next_page)
    val menuDescription = stringResource(io.legado.app.R.string.menu)
    val accessibilityPage = ReaderAccessibilityPolicy.snapshot(pages)
    Box(modifier
        .clearAndSetSemantics {
            accessibilityPage?.let { page ->
                text = AnnotatedString(page.text)
                if (page.isBookmarked) stateDescription = bookmarkDescription
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { if (page.canGoPrevious) 1f else 0f },
                    maxValue = {
                        (if (page.canGoPrevious) 1f else 0f) +
                                (if (page.canGoNext) 1f else 0f)
                    },
                )
                onClick(label = menuDescription) {
                    showComposeAccessibilityMenu()
                    true
                }
                scrollBy { x, y ->
                    val amount = if (abs(y) >= abs(x)) y else x
                    when {
                        amount > 0f && page.canGoNext -> {
                            accessibilityPageTurn(ReaderTurnDirection.NEXT)
                            true
                        }

                        amount < 0f && page.canGoPrevious -> {
                            accessibilityPageTurn(ReaderTurnDirection.PREVIOUS)
                            true
                        }

                        else -> false
                    }
                }
                customActions = buildList {
                    if (page.canGoPrevious) add(CustomAccessibilityAction(previousPageDescription) {
                        accessibilityPageTurn(ReaderTurnDirection.PREVIOUS)
                        true
                    })
                    if (page.canGoNext) add(CustomAccessibilityAction(nextPageDescription) {
                        accessibilityPageTurn(ReaderTurnDirection.NEXT)
                        true
                    })
                }
            }
        }
        .clipToBounds()
        .background(backgroundColor)
        // Foundation 在 API 28 以下将其降级为 no-op；这里无需另建低版本的昂贵位图快照。
        .magnifier(sourceCenter = { selectionMagnifierSource ?: Offset.Unspecified }, zoom = 1.2f)
        .pointerInput(transitionMode, current.widthPx, current.heightPx, configuredTouchSlopPx) {
            val pageTouchSlop = ReaderGestureSettingsPolicy.touchSlopPx(
                viewConfiguration.touchSlop,
                configuredTouchSlopPx,
            )
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                latestReaderInteraction()
                pageMotionJob?.cancel()
                curlRevealJob?.cancel()
                curlRevealProgress = 1f
                // 翻页收尾被打断时在此同步提交；宿主当帧返回新窗口，但组合要等下一帧，
                // 手势必须改用返回的窗口命中，否则长按会选中已不在屏幕上的旧页。
                val turnedWindow = completePendingTurn()
                displayOffset = 0f
                transition = ReaderPageTransition()
                bookmarkReturnJob?.cancel()
                bookmarkOffset = 0f
                bookmarkArmed = false
                bookmarkWillRemove = latestHasBookmark()
                val bookmarkEnabled = latestSwipeToBookmarkEnabled && textSelection == null
                curlTouchY = down.position.y
                val velocityTracker =
                    VelocityTracker().also { it.addPosition(down.uptimeMillis, down.position) }
                var total = Offset.Zero
                var lastHorizontalDelta = 0f
                var horizontalTurn = false
                var horizontalDrag: ReaderHorizontalDrag? = null
                var horizontalCapturedY = down.position.y
                var bookmarkDrag = false
                var bookmarkReleased = false
                var scrollDrag = false
                var scrollHitBoundary: ReaderTurnDirection? = null
                var movedPastSlop = false
                var longPressed = false
                var grabbingStart = false
                var grabbingEnd = false
                var grabbedEndpoint: ReaderSelectionEndpoint? = null
                var handleGrabOffset = Offset.Zero
                var handleHasMoved = false
                var suppressTap = false
                var pointerPosition = down.position
                val downWindow = turnedWindow ?: currentPageWindow()
                val downSelectionLayout = pageViewportLayout(downWindow)
                val downPlacement = downSelectionLayout.pageAt(down.position.x, down.position.y)
                val downPage = downPlacement?.page ?: downWindow.current
                val downPageY = downPlacement?.localY(down.position.y) ?: down.position.y
                textSelection?.let { selection ->
                    val bounds = downSelectionLayout.selectionBounds(selection).map { it.bounds }
                    // 命中点必须是实际绘制出来的圆心，不能仍以文字行底为中心。保留较大的
                    // 28dp 热区，让圆下方的正文也能作为把手的触控区域，但坐标仍以圆心换算。
                    val handleHitRadius = 28f * density
                    val start = bounds.firstOrNull()
                    val end = bounds.lastOrNull()
                    val startCenter = start?.let {
                        Offset(
                            it.left,
                            it.bottom + selectionHandleRadiusPx,
                        )
                    }
                    val endCenter = end?.let {
                        Offset(
                            it.right,
                            it.bottom + selectionHandleRadiusPx,
                        )
                    }

                    fun handleDistance(x: Float, top: Float, center: Offset): Float {
                        // 竖线与圆视作同一个胶囊形手柄：求手指到“竖线顶端—圆心”
                        // 中轴线的最短距离，再用统一热区判断。
                        val nearestY = down.position.y.coerceIn(top, center.y)
                        return Offset(x, nearestY).minus(down.position).getDistance()
                    }

                    val startDistance = if (start != null && startCenter != null) {
                        handleDistance(start.left, start.top, startCenter)
                    } else {
                        Float.POSITIVE_INFINITY
                    }
                    val endDistance = if (end != null && endCenter != null) {
                        handleDistance(end.right, end.top, endCenter)
                    } else {
                        Float.POSITIVE_INFINITY
                    }
                    when {
                        startDistance <= endDistance && startDistance <= handleHitRadius -> {
                            grabbingStart = true
                            grabbedEndpoint = selection.visualStartEndpoint()
                            handleGrabOffset = down.position - checkNotNull(startCenter)
                        }

                        endDistance <= handleHitRadius -> {
                            grabbingEnd = true
                            grabbedEndpoint = selection.visualEndEndpoint()
                            handleGrabOffset = down.position - checkNotNull(endCenter)
                        }
                    }
                    selectionDragEndpoint = grabbedEndpoint
                    selectionDragHandleCenter = null
                    if (!grabbingStart && !grabbingEnd) {
                        textSelection = null
                        selectionMagnifierSource = null
                        selectionDragEndpoint = null
                        dismissSelectionMenu()
                        suppressTap = true
                    } else dismissSelectionMenu()
                }
                val longPressJob = animationScope.launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (!movedPastSlop && !grabbingStart && !grabbingEnd) {
                        downPage?.let { page ->
                            val element = page.elementAt(down.position.x, downPageY)
                            if (element != null && onElementLongPress(
                                    element,
                                    down.position.x,
                                    down.position.y
                                )
                            ) {
                                longPressed = true
                            } else if (latestSelectionEnabled && !latestAutoPageActive) {
                                ReaderSelectionPolicy.startWord(page, down.position.x, downPageY)
                                    ?.let {
                                        textSelection = it
                                        selectionMagnifierSource = selectionCursorCenter(
                                            it,
                                            ReaderSelectionEndpoint.FOCUS,
                                        )
                                        if (latestSelectionHapticsEnabled) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        longPressed = true
                                    }
                            }
                        }
                    }
                }
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        pointerPosition = change.position
                        if (!change.pressed) {
                            released = true; break
                        }
                        total += change.positionChange()
                        if (change.positionChange().x != 0f) lastHorizontalDelta =
                            change.positionChange().x
                        curlTouchY = change.position.y
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (total.getDistance() >= pageTouchSlop) {
                            movedPastSlop = true
                            if (!longPressed && !grabbingStart && !grabbingEnd) longPressJob.cancel()
                        }
                        if (longPressed || grabbingStart || grabbingEnd) {
                            val selection = textSelection
                            val movingEndpoint = grabbedEndpoint ?: ReaderSelectionEndpoint.FOCUS
                            // PointerInput 会先派发一个与 DOWN 位置相同的事件。把手尚未移动时
                            // 不能再用行底去 hit-test，否则该边界可能直接吸附到下一行。
                            if (grabbedEndpoint != null && !handleHasMoved) {
                                handleHasMoved = change.position != down.position
                                if (!handleHasMoved) {
                                    selectionMagnifierSource = selection?.let {
                                        selectionCursorCenter(it, movingEndpoint)
                                    }
                                    change.consume()
                                    continue
                                }
                            }
                            if (grabbedEndpoint != null) {
                                // 固定 DOWN 时手指相对圆心的二维偏移。跨字符、跨行后都不能
                                // 用新的文字 bounds 重算，否则手指会从圆上滑到竖线上。
                                selectionDragHandleCenter = change.position - handleGrabOffset
                            }
                            val draggedHandleCenter = selectionDragHandleCenter
                            val cursorViewportX = draggedHandleCenter?.x ?: change.position.x
                            val cursorViewportY = draggedHandleCenter?.let {
                                it.y - selectionHandleRadiusPx
                            } ?: change.position.y
                            val placement = pageViewportLayout()
                                .pageAt(cursorViewportX, cursorViewportY)
                            if (placement != null && selection != null) {
                                val page = placement.page
                                val pageY = placement.localY(cursorViewportY)
                                val hit =
                                    ReaderSelectionPolicy.start(page, cursorViewportX, pageY)
                                        ?: if (grabbedEndpoint != null) {
                                            ReaderSelectionPolicy.snapToText(
                                                page,
                                                cursorViewportX,
                                                pageY
                                            )?.let {
                                                ReaderSelection(
                                                    page.id.chapterIndex,
                                                    it.chapterPosition,
                                                    it.chapterPosition,
                                                    it.emphasized
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                // 滚动模式堆叠的下邻页属于下一章，允许选区跨过去（旧 View 的
                                // 选区分词同样覆盖 relativePage 0..2）；分页模式保持单章。
                                val canCrossChapter =
                                    transitionMode == ReaderTransitionMode.SCROLL
                                if (hit != null && (canCrossChapter ||
                                            hit.chapterIndex == selection.chapterIndex)
                                ) {
                                    val updatedSelection = when {
                                        grabbedEndpoint != null -> selection.moveEndpoint(
                                            grabbedEndpoint,
                                            hit.anchor,
                                            hit.anchorIsTitle,
                                            chapter = hit.chapterIndex,
                                        )

                                        else -> ReaderSelectionPolicy.extend(
                                            selection,
                                            page,
                                            cursorViewportX,
                                            pageY,
                                            allowChapterCrossing = canCrossChapter,
                                        )
                                    }
                                    if (updatedSelection != selection) {
                                        textSelection = updatedSelection
                                        if (latestSelectionHapticsEnabled) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    selectionMagnifierSource = selectionCursorCenter(
                                        updatedSelection,
                                        movingEndpoint,
                                        draggedHandleCenter = draggedHandleCenter,
                                    )
                                } else {
                                    selectionMagnifierSource = selectionCursorCenter(
                                        selection,
                                        movingEndpoint,
                                        draggedHandleCenter = draggedHandleCenter,
                                    )
                                }
                                change.consume()
                            }
                            continue
                        }
                        if (!horizontalTurn && !bookmarkDrag && !scrollDrag && total.getDistance() >= pageTouchSlop) {
                            val pull = pullBookmark(
                                total,
                                size.height.toFloat(),
                                density,
                                transitionMode,
                                bookmarkEnabled
                            )
                            if (!bookmarkReleased) {
                                val claim = PullBookmarkGesture.claim(bookmarkReleased, pull)
                                bookmarkDrag = claim.isDragging
                                // Match the View reader: once the first post-slop sample is not a
                                // pull candidate, this gesture belongs to page turning/scrolling and
                                // must not be reclaimed by a later diagonal direction change.
                                bookmarkReleased = claim.isReleased
                            }
                            scrollDrag = transitionMode == ReaderTransitionMode.SCROLL &&
                                    ReaderMainAxisPolicy.isVerticalDominant(total.x, total.y)
                            horizontalTurn = !bookmarkDrag && !scrollDrag &&
                                    ReaderMainAxisPolicy.isHorizontalDominant(total.x, total.y)
                            if (horizontalTurn) {
                                horizontalDrag = ReaderHorizontalDrag.capture(total.x)
                                horizontalCapturedY = change.position.y
                                if (transitionMode == ReaderTransitionMode.SIMULATION) startCurlRevealSnap()
                            }
                        }
                        if (bookmarkDrag) {
                            val pull = pullBookmark(
                                total,
                                size.height.toFloat(),
                                density,
                                transitionMode,
                                bookmarkEnabled
                            )
                            bookmarkOffset = pull.pageOffsetPx
                            bookmarkArmed = pull.isArmed
                            if (pull.isCandidate) {
                                change.consume()
                            } else {
                                // Once handed to page turning, this gesture must never reclaim the pull.
                                bookmarkDrag = false
                                bookmarkReleased = true
                                // The legacy reader restores curPage before forwarding MOVE to its
                                // page delegate. Leaving this translation in place makes cover/slide
                                // turns travel diagonally and can persist until the next DOWN.
                                bookmarkArmed = false
                                bookmarkOffset = 0f
                                horizontalTurn =
                                    ReaderMainAxisPolicy.isHorizontalDominant(total.x, total.y)
                                if (horizontalTurn) {
                                    horizontalDrag = ReaderHorizontalDrag.capture(total.x)
                                    horizontalCapturedY = change.position.y
                                    if (transitionMode == ReaderTransitionMode.SIMULATION) startCurlRevealSnap()
                                }
                            }
                        }
                        if (horizontalTurn && transitionMode != ReaderTransitionMode.SCROLL) {
                            transition = horizontalDrag?.transition(
                                total.x, size.width.toFloat(),
                                ReaderPageNavigator.canTurnPrevious(
                                    latestPages,
                                    latestHasPreviousChapter(),
                                ),
                                ReaderPageNavigator.canTurnNext(
                                    latestPages,
                                    latestHasNextChapter(),
                                ),
                            ) ?: ReaderPageTransition(pageExtentPx = size.width.toFloat())
                            transition.direction?.takeIf { transitionMode == ReaderTransitionMode.SIMULATION }
                                ?.let {
                                    curlTouchX = ReaderCurlTouchPolicy.dragX(
                                        it,
                                        change.position.x,
                                        size.width.toFloat(),
                                    )
                                    curlCornerY = ReaderCurlTouchPolicy.cornerY(
                                        it, horizontalCapturedY, size.height.toFloat(),
                                    )
                                    curlTouchY = ReaderCurlTouchPolicy.dragY(
                                        it,
                                        horizontalCapturedY,
                                        change.position.y,
                                        size.height.toFloat(),
                                    )
                                }
                            displayOffset = transition.offsetPx
                            change.consume()
                        } else if (scrollDrag) {
                            val window = currentPageWindow()
                            val page = window.current
                            if (page != null) {
                                val result = ReaderScrollPolicy.apply(
                                    scrollOffset,
                                    change.positionChange().y,
                                    window.previous?.scrollExtentPx ?: 0f,
                                    page.scrollExtentPx,
                                    page.scrollViewportExtentPx(),
                                    ReaderPageNavigator.canTurnPrevious(
                                        window,
                                        latestHasPreviousChapter(),
                                    ),
                                    ReaderPageNavigator.canTurnNext(window, latestHasNextChapter())
                                )
                                applyScrollResult(result, window)
                                if (result.hitBoundary) {
                                    scrollHitBoundary = if (change.positionChange().y > 0f) {
                                        ReaderTurnDirection.PREVIOUS
                                    } else {
                                        ReaderTurnDirection.NEXT
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                } finally {
                    longPressJob.cancel()
                    selectionDragHandleCenter = null
                    selectionDragEndpoint = null
                    if (!released) {
                        bookmarkArmed = false
                        bookmarkOffset = 0f
                        // A competing gesture or pointer cancellation does not deliver UP.
                        // Do not discard an in-progress horizontal curl here: it has the same
                        // visual contract as a released-but-uncommitted turn and must return
                        // through the curl's natural cancel path (previous-page curls go left).
                        if (horizontalTurn && transition.dragging) {
                            settlePageTurn(ReaderTransitionDecision(0f, commit = false))
                        } else {
                            displayOffset = 0f
                            transition = ReaderPageTransition()
                        }
                    }
                }
                if (released) latestReaderInteraction()
                if (longPressed || grabbingStart || grabbingEnd) {
                    val selection = textSelection
                    selectionMagnifierSource = null
                    if (released && selection != null) {
                        val window = latestPages
                        showSelectionMenu(selection, window)
                    }
                    return@awaitEachGesture
                }
                if (bookmarkDrag) {
                    val releasePull = pullBookmark(
                        pointerPosition - down.position,
                        size.height.toFloat(),
                        density,
                        transitionMode,
                        bookmarkEnabled,
                    )
                    if (released && releasePull.isCandidate) {
                        bookmarkOffset = releasePull.pageOffsetPx
                    }
                    if (released && PullBookmarkGesture.shouldToggleOnRelease(
                            bookmarkDrag,
                            releasePull
                        )
                    ) {
                        latestToggleBookmark()
                    }
                    bookmarkArmed = false
                    bookmarkReturnJob = animationScope.launch {
                        Animatable(bookmarkOffset).animateTo(
                            0f, tween(PullBookmarkDefaults.RETURN_DURATION_MILLIS),
                        ) { bookmarkOffset = value }
                    }
                } else if (scrollDrag) {
                    if (scrollHitBoundary == null) {
                        // 触边界已在拖拽期间把偏移钳住，松手不再启动 fling（也不提示，
                        // 见 [reportScrollBoundary]）。
                        val velocity = if (released) velocityTracker.calculateVelocity().y else 0f
                        pageMotionJob = animationScope.launch {
                            scrollMotionActive = true
                            try {
                                var lastValue = 0f
                                Animatable(0f).animateDecay(velocity, scrollDecay) {
                                    val delta = value - lastValue
                                    lastValue = value
                                    val window = currentPageWindow()
                                    val page = window.current ?: return@animateDecay
                                    val result = ReaderScrollPolicy.apply(
                                        scrollOffset,
                                        delta,
                                        window.previous?.scrollExtentPx ?: 0f,
                                        page.scrollExtentPx,
                                        page.scrollViewportExtentPx(),
                                        ReaderPageNavigator.canTurnPrevious(
                                            window,
                                            latestHasPreviousChapter(),
                                        ),
                                        ReaderPageNavigator.canTurnNext(
                                            window,
                                            latestHasNextChapter(),
                                        ),
                                    )
                                    applyScrollResult(result, window)
                                    if (result.hitBoundary) throw ReaderScrollBoundaryReached()
                                }
                            } catch (_: ReaderScrollBoundaryReached) {
                                // Reaching the first/last content boundary ends the fling immediately.
                                // 旧 View 的 ScrollPageDelegate 同样只是停住，不弹提示。
                            } finally {
                                scrollMotionActive = false
                            }
                        }
                    }
                } else if (horizontalTurn && transition.dragging) {
                    val fade = transitionMode == ReaderTransitionMode.FADE
                    settlePageTurn(
                        ReaderPageTransitionPolicy.release(
                            transition,
                            velocityPxPerSecond = if (fade) 0f else velocityTracker.calculateVelocity().x,
                            commitProgress = if (fade) 0.1f else 0.35f,
                            cancelled = !released,
                            lastDragDeltaPx = if (fade) null else lastHorizontalDelta,
                        )
                    )
                } else if (released && horizontalTurn) {
                    transition.direction?.let(latestPageBoundaryReached)
                } else if (released && !suppressTap && total.getDistance() < pageTouchSlop) {
                    // 元素命中复用 DOWN 时刻的布局：与长按同一坐标系，且不被
                    // 松手前可能发生的窗口替换干扰。
                    val elementHandled = downPlacement?.page
                        ?.elementAt(down.position.x, downPageY)
                        ?.let(onElementClick) == true
                    if (elementHandled) {
                        // Element actions take precedence over reader tap zones.
                    } else dispatchTapAction(
                        latestTapActionGrid.actionAt(
                            down.position.x,
                            down.position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    )
                }
            }
        }) {
        if (ReaderViewportLayerPolicy.usesFixedBackground(transitionMode)) {
            ReaderBackgroundSurface(
                pageBackgroundImage,
                backgroundImageAlpha,
                Modifier.fillMaxSize(),
            )
        }
        if (transitionMode == ReaderTransitionMode.SCROLL) {
            val contentClipPad = current.contentClipPadPx
            Box(Modifier
                .fillMaxSize()
                .drawWithContent {
                    // 外扩阴影/斜体溢出，对照旧 View 的 ChapterProvider.visibleRect。
                    clipRect(
                        top = current.contentTopPx - contentClipPad,
                        bottom = current.contentBottomPx + contentClipPad,
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }) {
                ScrollPageStack(
                    windowProvider = { currentPageWindow() },
                    offsetYState = scrollOffsetState,
                    selection = selectionColor,
                    readAloud = textAccentColor,
                    selectionProvider = { textSelection },
                    cachedImage = cachedImage,
                    loadImage = loadImage,
                )
            }
        } else if (transitionMode == ReaderTransitionMode.SIMULATION && transition.dragging) {
            SimulationPageStack(
                pages,
                transition.direction!!,
                displayOffset,
                ReaderCurlTouchPolicy.revealX(
                    transition.direction!!,
                    curlTouchX,
                    current.widthPx.toFloat(),
                    curlRevealProgress
                ),
                curlTouchY,
                curlCornerY,
                backgroundColor,
                pageBackgroundImage,
                backgroundImageAlpha,
                selectionColor,
                textAccentColor,
                textSelection,
                cachedImage,
                loadImage
            )
        } else {
            @Composable
            fun PageLayer(page: ReaderPage, transform: ReaderPageTransform, offsetY: Float = 0f) {
                ReaderPageCanvas(
                    page, backgroundColor, pageBackgroundImage, backgroundImageAlpha, selectionColor, textAccentColor,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = transform.translationX
                            translationY = offsetY + transform.translationY
                            alpha = transform.alpha
                        },
                    textSelection,
                    cachedImage,
                    loadImage,
                )
            }
            if (transforms.currentOnTop) {
                transforms.next?.let { t -> pages.next?.let { PageLayer(it, t) } }
            }
            PageLayer(current, transforms.current, bookmarkOffset)
            transforms.previous?.let { t -> pages.previous?.let { PageLayer(it, t) } }
            if (!transforms.currentOnTop) {
                transforms.next?.let { t -> pages.next?.let { PageLayer(it, t) } }
            }
        }
        if (transitionMode == ReaderTransitionMode.COVER && transition.direction != null && displayOffset != 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val direction = transition.direction ?: return@Canvas
                val edge = ReaderCoverShadowPolicy.edgePx(direction, displayOffset, size.width)
                val shadowWidth = ReaderCoverShadowPolicy.widthDp * density
                val dark = Color(ReaderCoverShadowPolicy.colorArgb)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(dark, Color.Transparent),
                        edge,
                        edge + shadowWidth,
                    ),
                    Offset(edge, 0f),
                    Size(shadowWidth, size.height),
                )
            }
        }
        if (transitionMode != ReaderTransitionMode.SCROLL && autoPageActive && autoRevealPx > 0f) {
            pages.next?.let { page ->
                Box(Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(bottom = autoRevealPx.coerceAtMost(size.height)) { this@drawWithContent.drawContent() }
                    }) {
                    ReaderPageCanvas(page, backgroundColor, pageBackgroundImage, backgroundImageAlpha, selectionColor, textAccentColor, Modifier.fillMaxSize(), textSelection, cachedImage, loadImage)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = autoPageIndicatorColor,
                    topLeft = Offset(
                        0f,
                        ReaderAutoPagePolicy.indicatorTopPx(autoRevealPx, size.height),
                    ),
                    size = Size(size.width, 1f),
                )
            }
        }
        if (ReaderViewportLayerPolicy.usesFixedPageChrome(transitionMode)) {
            ReaderPageDecorationOverlay(current, Modifier.fillMaxSize())
        }
        val hiddenHandleEndpoint = selectionDragEndpoint.takeIf {
            selectionDragHandleCenter != null
        }
        val anchorHandleTargetAlpha =
            if (hiddenHandleEndpoint == ReaderSelectionEndpoint.ANCHOR) 0f else 1f
        val focusHandleTargetAlpha =
            if (hiddenHandleEndpoint == ReaderSelectionEndpoint.FOCUS) 0f else 1f
        val anchorHandleAlpha by animateFloatAsState(
            targetValue = anchorHandleTargetAlpha,
            animationSpec = tween(
                if (anchorHandleTargetAlpha == 0f) {
                    SelectionHandleFadeOutMillis
                } else {
                    SelectionHandleFadeInMillis
                }
            ),
            label = "readerSelectionAnchorHandleAlpha",
        )
        val focusHandleAlpha by animateFloatAsState(
            targetValue = focusHandleTargetAlpha,
            animationSpec = tween(
                if (focusHandleTargetAlpha == 0f) {
                    SelectionHandleFadeOutMillis
                } else {
                    SelectionHandleFadeInMillis
                }
            ),
            label = "readerSelectionFocusHandleAlpha",
        )
        textSelection?.let { selection ->
            val bounds = pageViewportLayout(pages).selectionBounds(selection).map { it.bounds }
            val handleColor = selectionColor.copy(alpha = 1f)
            val screenDensity = LocalDensity.current.density
            val handleShadowPaint = remember(screenDensity) {
                Paint().apply {
                    isAntiAlias = true
                    color = Color.White.toArgb()
                    setShadowLayer(4f * screenDensity, 0f, 2f * screenDensity, 0x40000000)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                clipRect(
                    top = if (transitionMode == ReaderTransitionMode.SCROLL) current.contentTopPx else 0f,
                    bottom = if (transitionMode == ReaderTransitionMode.SCROLL) current.contentBottomPx else size.height,
                ) {
                    fun drawPinHandle(
                        x: Float,
                        top: Float,
                        bottom: Float,
                        endpoint: ReaderSelectionEndpoint,
                    ) {
                        val handleAlpha = when (endpoint) {
                            ReaderSelectionEndpoint.ANCHOR -> anchorHandleAlpha
                            ReaderSelectionEndpoint.FOCUS -> focusHandleAlpha
                        }
                        if (handleAlpha <= 0.001f) return
                        val lineWidth = SelectionHandleStrokeWidth.toPx()
                        val radius = SelectionHandleRadius.toPx()
                        val logicalCenter = Offset(x, bottom + radius)
                        val center = if (selectionDragEndpoint == endpoint) {
                            selectionDragHandleCenter
                        } else {
                            null
                        } ?: logicalCenter
                        // 竖线和圆柄是同一个手柄：拖动时按相同向量整体平移，保持 DOWN
                        // 时手指落在圆上的相对位置，而不是让竖线吸附后圆柄单独跳动。
                        val dragDelta = center - logicalCenter
                        val lineX = x + dragDelta.x
                        val lineTop = top + dragDelta.y
                        val lineBottom = bottom + dragDelta.y
                        val animatedHandleColor = handleColor.copy(alpha = handleAlpha)
                        drawLine(
                            animatedHandleColor,
                            Offset(lineX, lineTop),
                            Offset(lineX, lineBottom),
                            lineWidth,
                            StrokeCap.Round,
                        )
                        drawIntoCanvas {
                            handleShadowPaint.alpha = (handleAlpha * 255).roundToInt()
                            it.nativeCanvas.drawCircle(
                                center.x,
                                center.y,
                                radius,
                                handleShadowPaint
                            )
                        }
                        drawCircle(
                            animatedHandleColor,
                            radius,
                            center,
                            style = Stroke(SelectionHandleStrokeWidth.toPx()),
                        )
                    }
                    bounds.firstOrNull()?.let { rect ->
                        drawPinHandle(
                            rect.left,
                            rect.top,
                            rect.bottom,
                            selection.visualStartEndpoint(),
                        )
                    }
                    bounds.lastOrNull()?.let { rect ->
                        drawPinHandle(
                            rect.right,
                            rect.top,
                            rect.bottom,
                            selection.visualEndEndpoint(),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = bookmarkArmed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(0, current.contentTopPx.roundToInt())
                }
                .padding(top = PullBookmarkDefaults.HINT_CONTENT_TOP_MARGIN_DP.dp),
            enter = fadeIn(tween(PullBookmarkDefaults.HINT_FADE_MILLIS)),
            exit = fadeOut(tween(0)),
        ) {
            Text(
                text = stringResource(if (bookmarkWillRemove) {
                    R.string.bookmark_swipe_release_to_remove
                } else R.string.bookmark_swipe_release_to_add),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(
                        Color(0xA0000000),
                        RoundedCornerShape(PullBookmarkDefaults.HINT_CORNER_RADIUS_DP.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun pullBookmark(offset: Offset, height: Float, density: Float, mode: ReaderTransitionMode, enabled: Boolean) =
    PullBookmarkGesture.drag(
        offset.x,
        offset.y,
        height,
        enabled,
        mode != ReaderTransitionMode.SCROLL,
        false,
        PullBookmarkDefaults.config(PullBookmarkDefaults.ACTIVATION_DISTANCE_DP * density),
    )

private class ReaderScrollBoundaryReached : CancellationException()

private fun ReaderPage.scrollViewportExtentPx(): Float =
    (contentBottomPx - contentTopPx).coerceAtLeast(1f)

@Composable
private fun ScrollPageStack(
    windowProvider: () -> ReaderPageWindow,
    offsetYState: androidx.compose.runtime.MutableFloatState,
    selection: Color,
    readAloud: Color,
    selectionProvider: () -> ReaderSelection?,
    cachedImage: (ReaderElement.Image) -> Bitmap?,
    loadImage: suspend (ReaderElement.Image) -> Bitmap?,
) {
    val cache = remember { ScrollPageDrawCache() }
    // 窗口变化（含跨页同步换窗）：effect 期为三页构建绘制数据并加载位图，正常情况下
    // 跨页时新进入窗口的页在此处预热；draw 期 miss 时同步兜底，保正确性不缺字
    // （对照 shutiao 的组合期 ensureTextLayoutCache + 绘制期兜底）。
    LaunchedEffect(windowProvider()) {
        val pageWindow = windowProvider()
        // 预热含下下页：跨页后新进入窗口的 next 页就是上一窗口的 nextPlus，绘制数据
        // 早已就绪。数据构建放 Default 线程——跨页帧主线程对页数据零构建，只有重绘
        // （对照 shutiao 的四页流预热；miss 时的 draw 期同步构建仍是兜底）。
        listOfNotNull(
            pageWindow.previous,
            pageWindow.current,
            pageWindow.next,
            pageWindow.nextPlus,
        ).forEach { page ->
            val data = cache.peek(page)
                ?: withContext(Dispatchers.Default) { ScrollPageDrawData(page) }
                    .also { cache.put(page, it) }
            val sources = data.textBackgrounds.map { it.image.source }.distinct()
            val cachedSources = sources.mapNotNull { source ->
                ReaderTextBackgroundLoader.cached(source)?.let { source to it }
            }.toMap()
            if (cachedSources.isNotEmpty()) data.textBackgroundRevision.value++
            val loadedSources = withContext(Dispatchers.IO) {
                sources.mapNotNull { source ->
                    ReaderTextBackgroundLoader.load(source)?.let { source to it }
                }.toMap()
            }
            if (loadedSources.isNotEmpty()) data.textBackgroundRevision.value++
            page.elements.filterIsInstance<ReaderElement.Image>().forEach { element ->
                // The controller owns inline bitmaps in a byte-bounded LRU. Do not retain
                // another strong reference for every cached draw page.
                if (cachedImage(element) == null) loadImage(element)
            }
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = offsetYState.floatValue },
    ) {
        // draw 期快照读：页窗口（同步换窗的 pending）变化只重绘不重组；拖拽平移只更新
        // graphicsLayer 变换，draw 块不执行。相邻页的 y 是栈坐标，整画布随层平移偏移。
        val window = windowProvider()
        val current = window.current ?: return@Canvas
        val activeSelection = selectionProvider()
        fun drawOne(page: ReaderPage, stackOffsetY: Float) {
            val data = cache.ensure(page)
            val selectedBounds = if (activeSelection != null || page.searchStart != null) {
                data.textElements
                    .filter {
                        page.isSearchResult(it) ||
                                activeSelection?.contains(it, page.id.chapterIndex) == true
                    }
                    .map(ReaderElement.Text::bounds)
                    .mergeSelectionBounds()
            } else emptyList()
            withTransform({ translate(0f, stackOffsetY) }) {
                drawScrollPageContent(
                    page,
                    data,
                    selection,
                    readAloud,
                    activeSelection,
                    selectedBounds,
                    cachedImage
                )
            }
        }
        window.previous?.let { drawOne(it, -it.scrollExtentPx) }
        drawOne(current, 0f)
        window.next?.let { next ->
            drawOne(next, current.scrollExtentPx)
            window.nextPlus?.let { following ->
                drawOne(following, current.scrollExtentPx + next.scrollExtentPx)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScrollPageContent(
    page: ReaderPage,
    data: ScrollPageDrawData,
    selection: Color,
    readAloud: Color,
    activeSelection: ReaderSelection?,
    selectedBounds: List<ReaderRect>,
    cachedImage: (ReaderElement.Image) -> Bitmap?,
) {
    val native = drawContext.canvas.nativeCanvas
    data.textBackgroundRevision.value
    data.textBackgrounds.forEach { run ->
        ReaderTextBackgroundLoader.cached(run.image.source)?.let { bitmap ->
            drawTextBackground(native, bitmap, run, data.textBackgroundPaint)
        }
    }
    data.textBackgroundBands.forEach { band ->
        drawRect(
            Color(band.colorArgb),
            Offset(band.bounds.left, band.bounds.top),
            Size(band.bounds.width, band.bounds.height),
        )
    }
    selectedBounds.forEach { rect ->
        drawRect(selection, Offset(rect.left, rect.top), Size(rect.width, rect.height))
    }
    page.elements.forEach { e -> when (e) {
        is ReaderElement.Text -> {
            val paint = data.paints.getValue(e.style)
            paint.color = page.resolvedColorArgb(e, readAloud.toArgb())
            paint.isUnderlineText = e.style.nativeUnderline || e.drawsLinkUnderline
            native.drawText(e.value, e.bounds.left, e.baselinePx, paint)
        }

        is ReaderElement.Image -> cachedImage(e)?.let { bitmap ->
            ReaderImageDrawLayout.fitCenter(e.bounds, bitmap.width, bitmap.height)?.let { layout ->
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(layout.leftPx.roundToInt(), layout.topPx.roundToInt()),
                    dstSize = IntSize(
                        layout.widthPx.roundToInt().coerceAtLeast(1),
                        layout.heightPx.roundToInt().coerceAtLeast(1),
                    ),
                )
            }
        } ?: drawRect(Color.Gray.copy(alpha = .18f), Offset(e.bounds.left, e.bounds.top), Size(e.bounds.width, e.bounds.height))
        is ReaderElement.Review -> if (e.count > 0) drawReview(native, e, data.paints.values.firstOrNull()?.color ?: android.graphics.Color.GRAY)
        is ReaderElement.Action -> Unit
        is ReaderElement.Spacer -> Unit
        is ReaderElement.ParagraphMarker -> {
            if (e.circular) {
                drawCircle(Color(e.colorArgb), e.strokeWidthPx / 2f, Offset(e.bounds.left, e.bounds.top))
            } else {
                drawLine(
                    Color(e.colorArgb),
                    Offset(e.bounds.left, e.bounds.top),
                    Offset(e.bounds.right, e.bounds.bottom),
                    e.strokeWidthPx,
                )
            }
        }
        is ReaderElement.Rule -> Unit
    } }
    page.dynamicEmphasisUnderlineRuns().forEach { run ->
        drawLine(
            color = Color(run.style.colorArgb),
            start = Offset(run.startPx, run.yPx),
            end = Offset(run.endPx, run.yPx),
            strokeWidth = run.style.widthPx,
        )
    }
    data.decorationDrawCache.contentRules.forEach { it.draw(native) }
    data.decorationDrawCache.styledUnderlines.forEach { it.draw(native) }
    data.decorationDrawCache.overlayRules.forEach { it.draw(native) }
}

@Composable
private fun SimulationPageStack(
    pages: ReaderPageWindow,
    direction: ReaderTurnDirection,
    pageOffsetPx: Float,
    touchX: Float,
    touchY: Float,
    cornerY: Float,
    background: Color,
    backgroundImage: Drawable?,
    backgroundImageAlpha: Float,
    selection: Color,
    readAloud: Color,
    activeSelection: ReaderSelection?,
    cachedImage: (ReaderElement.Image) -> Bitmap?,
    loadImage: suspend (ReaderElement.Image) -> Bitmap?,
) {
    val basePage = if (direction == ReaderTurnDirection.NEXT) pages.current else pages.previous
    val revealPage = if (direction == ReaderTurnDirection.NEXT) pages.next else pages.current
    if (basePage == null || revealPage == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val frame = remember(width, height, touchX, touchY, cornerY) {
            PageCurlGeometry.calculate(
                width,
                height,
                touchX,
                touchY.coerceIn(.1f, height - .1f),
                lockedCorner = CurlPoint(width, cornerY),
            )
        }
        if (frame == null) {
            // A degenerate Bezier frame used to draw only the base page, which made the
            // entering page disappear for an entire drag frame. Keep the destination visible
            // with a cheap horizontal fallback until the next valid curl frame arrives.
            ReaderPageCanvas(
                revealPage,
                background,
                backgroundImage,
                backgroundImageAlpha,
                selection,
                readAloud,
                Modifier.fillMaxSize(),
                activeSelection,
                cachedImage,
                loadImage
            )
            val baseTranslation = when (direction) {
                ReaderTurnDirection.NEXT -> pageOffsetPx
                ReaderTurnDirection.PREVIOUS -> pageOffsetPx - width
            }
            ReaderPageCanvas(
                basePage,
                background,
                backgroundImage,
                backgroundImageAlpha,
                selection,
                readAloud,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = baseTranslation },
                activeSelection,
                cachedImage,
                loadImage,
            )
            return@BoxWithConstraints
        }
        val paths = remember(frame, width, height) { frame.renderPaths(width, height) }
        val path0 = paths.front
        val pathNext = paths.reveal
        val pathBack = paths.back
        val baseLayer = rememberGraphicsLayer()
        Box(Modifier
            .fillMaxSize()
            .drawWithContent {
                baseLayer.record { this@drawWithContent.drawContent() }
                clipPath(path0, ClipOp.Difference) { drawLayer(baseLayer) }
            }) {
            ReaderPageCanvas(basePage, background, backgroundImage, backgroundImageAlpha, selection, readAloud, Modifier.fillMaxSize(), activeSelection, cachedImage, loadImage)
        }
        Box(Modifier
            .fillMaxSize()
            .drawWithContent {
                clipPath(path0) {
                    clipPath(pathNext) {
                        this@drawWithContent.drawContent()
                        drawCurlBackShadow(frame)
                    }
                }
            }) {
            ReaderPageCanvas(revealPage, background, backgroundImage, backgroundImageAlpha, selection, readAloud, Modifier.fillMaxSize(), activeSelection, cachedImage, loadImage)
        }
        Canvas(Modifier.fillMaxSize()) {
            clipPath(path0) { clipPath(pathBack) {
                drawRect(background)
                val matrix = Matrix().apply {
                    values[Matrix.ScaleX] = frame.mirror.scaleX
                    values[Matrix.SkewX] = frame.mirror.skewX
                    values[Matrix.SkewY] = frame.mirror.skewY
                    values[Matrix.ScaleY] = frame.mirror.scaleY
                    values[Matrix.TranslateX] = frame.mirror.translateX
                    values[Matrix.TranslateY] = frame.mirror.translateY
                }
                // Canvas.drawBitmap() in the View implementation naturally kept sampling
                // within the screenshot's bounds. A transformed GraphicsLayer otherwise
                // samples beyond its recorded page surface as opaque black on some devices,
                // producing a dark wedge between the two sides of a curl. Clip in source
                // coordinates first so uncovered back-page pixels retain the mean background
                // color drawn above, while the complete background image remains mirrored.
                withTransform({
                    transform(matrix)
                    clipRect(0f, 0f, size.width, size.height)
                }) {
                    drawLayer(baseLayer)
                }
                drawCurlFolderShadow(frame)
            } }
            drawCurlFrontShadows(frame, paths)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurlFrontShadows(
    frame: PageCurlFrame,
    paths: ReaderCurlRenderPaths,
) {
    val curlPath = paths.front
    val reverse = frame.corner.x == 0f && frame.corner.y == size.height || frame.corner.x == size.width && frame.corner.y == 0f
    clipPath(curlPath, ClipOp.Difference) { clipPath(paths.frontShadowHorizontal) {
        val left = if (reverse) frame.control1.x else frame.control1.x - 25f
        val right = if (reverse) frame.control1.x + 25f else frame.control1.x + 1f
        val rotation = (atan2((frame.touch.x - frame.control1.x).toDouble(), (frame.control1.y - frame.touch.y).toDouble()) * 180.0 / PI).toFloat()
        withTransform({ rotate(rotation, Offset(frame.control1.x, frame.control1.y)) }) {
            drawRect(Brush.horizontalGradient(if (reverse) listOf(Color(ReaderCurlVisualPolicy.frontShadowDarkArgb), Color.Transparent) else listOf(Color.Transparent, Color(ReaderCurlVisualPolicy.frontShadowDarkArgb)), left, right), Offset(left, frame.control1.y - hypot(size.width.toDouble(), size.height.toDouble()).toFloat()), Size(right - left, hypot(size.width.toDouble(), size.height.toDouble()).toFloat()))
        }
    } }
    clipPath(curlPath, ClipOp.Difference) { clipPath(paths.frontShadowVertical) {
        val top = if (reverse) frame.control2.y else frame.control2.y - 25f
        val bottom = if (reverse) frame.control2.y + 25f else frame.control2.y + 1f
        val rotation = (atan2((frame.control2.y - frame.touch.y).toDouble(), (frame.control2.x - frame.touch.x).toDouble()) * 180.0 / PI).toFloat()
        val diagonal = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
        val adjustedY = if (frame.control2.y < 0f) frame.control2.y - size.height else frame.control2.y
        val hmg = hypot(frame.control2.x.toDouble(), adjustedY.toDouble()).toFloat()
        val left = if (hmg > diagonal) frame.control2.x - 25f - hmg else frame.control2.x - diagonal
        val right = if (hmg > diagonal) frame.control2.x + diagonal - hmg else frame.control2.x
        withTransform({ rotate(rotation, Offset(frame.control2.x, frame.control2.y)) }) {
            drawRect(Brush.verticalGradient(if (reverse) listOf(Color(ReaderCurlVisualPolicy.frontShadowDarkArgb), Color.Transparent) else listOf(Color.Transparent, Color(ReaderCurlVisualPolicy.frontShadowDarkArgb)), top, bottom), Offset(left, top), Size(right - left, bottom - top))
        }
    } }
}

private data class ReaderCurlRenderPaths(
    val front: Path,
    val reveal: Path,
    val back: Path,
    val frontShadowHorizontal: Path,
    val frontShadowVertical: Path,
)

private fun PageCurlFrame.renderPaths(width: Float, height: Float): ReaderCurlRenderPaths {
    val reverse = corner.x == 0f && corner.y == height || corner.x == width && corner.y == 0f
    val angle = if (reverse) {
        PI / 4 - atan2((control1.y - touch.y).toDouble(), (touch.x - control1.x).toDouble())
    } else {
        PI / 4 - atan2((touch.y - control1.y).toDouble(), (touch.x - control1.x).toDouble())
    }
    val shadowX = (touch.x + 25f * 1.414f * cos(angle)).toFloat()
    val shadowY = (touch.y + (if (reverse) 1 else -1) * 25f * 1.414f * sin(angle)).toFloat()
    return ReaderCurlRenderPaths(
        front = Path().apply {
            moveTo(start1.x, start1.y); quadraticTo(control1.x, control1.y, end1.x, end1.y)
            lineTo(touch.x, touch.y); lineTo(end2.x, end2.y)
            quadraticTo(control2.x, control2.y, start2.x, start2.y); lineTo(corner.x, corner.y); close()
        },
        reveal = Path().apply {
            moveTo(start1.x, start1.y); lineTo(vertex1.x, vertex1.y); lineTo(vertex2.x, vertex2.y)
            lineTo(start2.x, start2.y); lineTo(corner.x, corner.y); close()
        },
        back = Path().apply {
            moveTo(vertex2.x, vertex2.y); lineTo(vertex1.x, vertex1.y); lineTo(end1.x, end1.y)
            lineTo(touch.x, touch.y); lineTo(end2.x, end2.y); close()
        },
        frontShadowHorizontal = Path().apply {
            moveTo(shadowX, shadowY); lineTo(touch.x, touch.y); lineTo(control1.x, control1.y)
            lineTo(start1.x, start1.y); close()
        },
        frontShadowVertical = Path().apply {
            moveTo(shadowX, shadowY); lineTo(touch.x, touch.y); lineTo(control2.x, control2.y)
            lineTo(start2.x, start2.y); close()
        },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurlBackShadow(frame: PageCurlFrame) {
    val reverse = frame.corner.x == 0f && frame.corner.y == size.height || frame.corner.x == size.width && frame.corner.y == 0f
    val shadowWidth = frame.touchToCornerDistance / 4f
    val left = if (reverse) frame.start1.x else frame.start1.x - shadowWidth
    val right = if (reverse) frame.start1.x + shadowWidth else frame.start1.x
    if (right <= left) return
    withTransform({ rotate(frame.degrees, Offset(frame.start1.x, frame.start1.y)) }) {
        drawRect(Brush.horizontalGradient(if (reverse) listOf(Color(ReaderCurlVisualPolicy.backShadowDarkArgb), Color.Transparent) else listOf(Color.Transparent, Color(ReaderCurlVisualPolicy.backShadowDarkArgb)), left, right), Offset(left, frame.start1.y), Size(right - left, hypot(size.width.toDouble(), size.height.toDouble()).toFloat()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurlFolderShadow(frame: PageCurlFrame) {
    val width = minOf(abs((frame.start1.x - frame.control1.x) / 2f), abs((frame.start2.y - frame.control2.y) / 2f)).coerceAtLeast(1f)
    val reverse = frame.corner.x == 0f && frame.corner.y == size.height || frame.corner.x == size.width && frame.corner.y == 0f
    val left = if (reverse) frame.start1.x - 1f else frame.start1.x - width - 1f
    val right = if (reverse) frame.start1.x + width + 1f else frame.start1.x + 1f
    withTransform({ rotate(frame.degrees, Offset(frame.start1.x, frame.start1.y)) }) {
        drawRect(Brush.horizontalGradient(if (reverse) listOf(Color.Transparent, Color(ReaderCurlVisualPolicy.folderShadowDarkArgb)) else listOf(Color(ReaderCurlVisualPolicy.folderShadowDarkArgb), Color.Transparent), left, right), Offset(left, frame.start1.y), Size(right - left, hypot(size.width.toDouble(), size.height.toDouble()).toFloat()))
    }
}

@Composable
private fun ReaderPageCanvas(
    page: ReaderPage,
    background: Color,
    backgroundImage: Drawable?,
    backgroundImageAlpha: Float,
    selection: Color,
    readAloud: Color,
    modifier: Modifier,
    activeSelection: ReaderSelection?,
    cachedImage: (ReaderElement.Image) -> Bitmap?,
    loadImage: suspend (ReaderElement.Image) -> Bitmap?,
    drawBackground: Boolean = true,
    drawDecoration: Boolean = true,
) {
    val isolatedBackgroundImage = remember(backgroundImage) { backgroundImage?.isolatedCopy() }
    val textElements = remember(page.elements) {
        page.elements.filterIsInstance<ReaderElement.Text>()
    }
    val paints = remember(textElements) {
        textElements.map { it.style }.distinct()
            .associateWith(ReaderAndroidPaintFactory::create)
    }
    val badge = page.decoration.bookmarkBadge.takeIf { drawDecoration }
    val cachedBadgeImage = badge?.let(ReaderBookmarkBadgeRenderer::cachedResult)
    val badgeImage by produceState(cachedBadgeImage, badge) {
        value = badge?.let { it to withContext(Dispatchers.IO) { ReaderBookmarkBadgeRenderer.load(it) } }
    }
    val pageImages = page.elements.filterIsInstance<ReaderElement.Image>()
    val cachedImages = pageImages.mapNotNull { element ->
        cachedImage(element)?.takeUnless(Bitmap::isRecycled)?.let { element to it }
    }.toMap()
    var images by remember(page.id, page.layoutRevision, page.revision, pageImages) {
        mutableStateOf(cachedImages)
    }
    LaunchedEffect(page.id, page.layoutRevision, page.revision, pageImages) {
        pageImages.forEach { element ->
            val bitmap = (cachedImage(element) ?: loadImage(element))
                ?.takeUnless(Bitmap::isRecycled)
                ?: return@forEach
            if (images[element] !== bitmap) {
                images = images + (element to bitmap)
            }
        }
    }
    val textBackgrounds = remember(page.elements) { page.textBackgroundRuns() }
    val textBackgroundPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }
    val decorationDrawCache = remember(page.elements) {
        ReaderPageDecorationDrawCache.create(page)
    }
    val textBackgroundBands = remember(textElements) { textElements.mergeBackgroundBounds() }
    val selectedTextBounds = remember(
        textElements,
        activeSelection,
        page.searchStart,
        page.searchEndInclusive,
    ) {
        textElements
            .filter {
                page.isSearchResult(it) ||
                        activeSelection?.contains(it, page.id.chapterIndex) == true
            }
            .map(ReaderElement.Text::bounds)
            .mergeSelectionBounds()
    }
    val textBackgroundSources = remember(textBackgrounds) {
        textBackgrounds.map { it.image.source }.distinct()
    }
    val cachedTextBackgrounds = textBackgroundSources.mapNotNull { source ->
        ReaderTextBackgroundLoader.cached(source)?.let { source to it }
    }.toMap()
    val textBackgroundBitmaps by produceState(cachedTextBackgrounds, textBackgroundSources) {
        value = withContext(Dispatchers.IO) {
            textBackgroundSources.mapNotNull { source ->
                ReaderTextBackgroundLoader.load(source)?.let { source to it }
            }.toMap()
        }
    }
    val tipPaints = remember(
        page.decoration.header,
        page.decoration.footer,
        drawDecoration,
    ) {
        if (!drawDecoration) emptyMap() else listOfNotNull(page.decoration.header, page.decoration.footer).associateWith { row ->
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = row.colorArgb
                textSize = row.fontSizePx
                typeface = ReaderAndroidPaintFactory.loadTypeface(row.fontPath, 400, false)
            }
        }
    }
    Canvas(if (drawBackground) modifier.background(background) else modifier) {
        val native = drawContext.canvas.nativeCanvas
        isolatedBackgroundImage?.takeIf { drawBackground }?.let { drawable ->
            drawable.bounds = android.graphics.Rect(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.alpha = (backgroundImageAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            drawable.draw(native)
        }
        textBackgrounds.forEach { run ->
            textBackgroundBitmaps[run.image.source]?.let { bitmap ->
                drawTextBackground(native, bitmap, run, textBackgroundPaint)
            }
        }
        textBackgroundBands.forEach { band ->
            drawRect(
                Color(band.colorArgb),
                Offset(band.bounds.left, band.bounds.top),
                Size(band.bounds.width, band.bounds.height),
            )
        }
        selectedTextBounds.forEach { rect ->
            drawRect(selection, Offset(rect.left, rect.top), Size(rect.width, rect.height))
        }
        page.elements.forEach { e -> when (e) {
            is ReaderElement.Text -> {
                val paint = paints.getValue(e.style)
                paint.color = page.resolvedColorArgb(e, readAloud.toArgb())
                paint.isUnderlineText = e.style.nativeUnderline || e.drawsLinkUnderline
                native.drawText(e.value, e.bounds.left, e.baselinePx, paint)
            }
            is ReaderElement.Image -> images[e]?.let { bitmap ->
                ReaderImageDrawLayout.fitCenter(e.bounds, bitmap.width, bitmap.height)?.let { layout ->
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(layout.leftPx.roundToInt(), layout.topPx.roundToInt()),
                        dstSize = IntSize(
                            layout.widthPx.roundToInt().coerceAtLeast(1),
                            layout.heightPx.roundToInt().coerceAtLeast(1),
                        ),
                    )
                }
            } ?: drawRect(Color.Gray.copy(alpha = .18f), Offset(e.bounds.left, e.bounds.top), Size(e.bounds.width, e.bounds.height))
            is ReaderElement.Review -> if (e.count > 0) drawReview(native, e, paints.values.firstOrNull()?.color ?: android.graphics.Color.GRAY)
            is ReaderElement.Action -> Unit
            is ReaderElement.Spacer -> Unit
            is ReaderElement.ParagraphMarker -> {
                if (e.circular) {
                    drawCircle(Color(e.colorArgb), e.strokeWidthPx / 2f, Offset(e.bounds.left, e.bounds.top))
                } else {
                    drawLine(
                        Color(e.colorArgb),
                        Offset(e.bounds.left, e.bounds.top),
                        Offset(e.bounds.right, e.bounds.bottom),
                        e.strokeWidthPx,
                    )
                }
            }
            is ReaderElement.Rule -> Unit
        } }
        page.dynamicEmphasisUnderlineRuns().forEach { run ->
            drawLine(
                color = Color(run.style.colorArgb),
                start = Offset(run.startPx, run.yPx),
                end = Offset(run.endPx, run.yPx),
                strokeWidth = run.style.widthPx,
            )
        }
        decorationDrawCache.contentRules.forEach { it.draw(native) }
        decorationDrawCache.styledUnderlines.forEach { it.draw(native) }
        decorationDrawCache.overlayRules.forEach { it.draw(native) }
        if (drawDecoration) drawPageDecoration(native, page, tipPaints, badgeImage)
    }
}

@Composable
fun ReaderBackgroundSurface(
    backgroundImage: Drawable?,
    backgroundImageAlpha: Float,
    modifier: Modifier,
    animateAppearance: Boolean = false,
) {
    val isolatedBackgroundImage = remember(backgroundImage) { backgroundImage?.isolatedCopy() }
    val targetAlpha = if (isolatedBackgroundImage == null) 0f else backgroundImageAlpha
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (animateAppearance) tween(400) else snap(),
        label = "readerBackgroundAlpha",
    )
    Canvas(modifier) {
        isolatedBackgroundImage?.let { drawable ->
            drawable.bounds = android.graphics.Rect(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.alpha = (animatedAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}

private fun Drawable.isolatedCopy(): Drawable =
    constantState?.newDrawable()?.mutate() ?: mutate()

private fun ReaderPage.isSearchResult(text: ReaderElement.Text): Boolean {
    val start = searchStart ?: return false
    val end = searchEndInclusive ?: return false
    if (text.emphasized != searchIsTitle) return false
    val textEnd = text.chapterPosition + text.value.length - 1
    return text.chapterPosition <= maxOf(start, end) && textEnd >= minOf(start, end)
}

private fun ReaderPage.isReadAloud(text: ReaderElement.Text): Boolean =
    !text.emphasized && readAloudParagraphIndex != null && text.paragraphIndex == readAloudParagraphIndex

private fun ReaderPage.resolvedColorArgb(text: ReaderElement.Text, accentColorArgb: Int): Int =
    if (text.link != null || isSearchResult(text) || isReadAloud(text)) accentColorArgb else text.style.colorArgb

private fun ReaderPage.dynamicEmphasisUnderlineRuns(): List<ReaderEmphasisUnderlineRun> {
    val style = emphasisUnderlineStyle ?: return emptyList()
    // 命中位置只决定哪一行划线，线仍是整行（对照旧 View TextLine.drawTextLine）。
    return emphasisUnderlineRunsFor(style) { isSearchResult(it) || isReadAloud(it) }
}

@Composable
private fun ReaderPageDecorationOverlay(page: ReaderPage, modifier: Modifier) {
    val badge = page.decoration.bookmarkBadge
    val cachedBadgeImage = badge?.let(ReaderBookmarkBadgeRenderer::cachedResult)
    val badgeImage by produceState(cachedBadgeImage, badge) {
        value = badge?.let { it to withContext(Dispatchers.IO) { ReaderBookmarkBadgeRenderer.load(it) } }
    }
    val tipPaints = remember(page.decoration.header, page.decoration.footer) {
        listOfNotNull(page.decoration.header, page.decoration.footer).associateWith(::createTipPaint)
    }
    Canvas(modifier) {
        drawPageDecoration(drawContext.canvas.nativeCanvas, page, tipPaints, badgeImage)
    }
}

private fun createTipPaint(row: ReaderTipRow) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    color = row.colorArgb
    textSize = row.fontSizePx
    typeface = ReaderAndroidPaintFactory.loadTypeface(row.fontPath, 400, false)
}

internal fun <T> resolveReaderTipResource(
    row: ReaderTipRow,
    cached: Map<ReaderTipRow, T>,
    create: (ReaderTipRow) -> T,
): T = cached[row] ?: create(row)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageDecoration(
    canvas: android.graphics.Canvas,
    page: ReaderPage,
    tipPaints: Map<ReaderTipRow, Paint>,
    badgeImage: Pair<io.legado.app.feature.reader.core.model.ReaderBookmarkBadge, Bitmap?>?,
) {
    page.decoration.header?.takeIf { it.visible }?.let { row ->
        val paint = resolveReaderTipResource(row, tipPaints, ::createTipPaint)
        drawTipRow(
            canvas,
            row,
            paint,
            ReaderTipRowLayout.headerBaseline(row.paddingTopPx, paint.fontMetrics.top),
        )
        row.dividerColorArgb?.let {
            val metrics = paint.fontMetrics
            val dividerY = ReaderTipRowLayout.extent(
                row.paddingTopPx, metrics.top, metrics.bottom, row.paddingBottomPx,
            )
            drawLine(Color(it), Offset(0f, dividerY), Offset(size.width, dividerY), 1f)
        }
    }
    page.decoration.footer?.takeIf { it.visible }?.let { row ->
        val paint = resolveReaderTipResource(row, tipPaints, ::createTipPaint)
        drawTipRow(
            canvas,
            row,
            paint,
            ReaderTipRowLayout.footerBaseline(
                size.height, row.paddingBottomPx, paint.fontMetrics.bottom,
            ),
        )
        row.dividerColorArgb?.let {
            val metrics = paint.fontMetrics
            val dividerY = size.height - ReaderTipRowLayout.extent(
                row.paddingTopPx, metrics.top, metrics.bottom, row.paddingBottomPx,
            )
            drawLine(Color(it), Offset(0f, dividerY), Offset(size.width, dividerY), 1f)
        }
    }
    page.decoration.bookmarkBadge?.let { badge ->
        if (shouldDrawReaderBookmarkBadge(badge, badgeImage)) {
            ReaderBookmarkBadgeRenderer.draw(
                canvas,
                badge,
                badgeImage?.takeIf { it.first == badge }?.second,
            )
        }
    }
}

internal fun shouldDrawReaderBookmarkBadge(
    badge: io.legado.app.feature.reader.core.model.ReaderBookmarkBadge,
    loaded: Pair<io.legado.app.feature.reader.core.model.ReaderBookmarkBadge, Bitmap?>?,
): Boolean = badge.imageSource.isBlank() || loaded?.first == badge

private fun drawTextBackground(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    run: io.legado.app.feature.reader.core.model.ReaderTextBackgroundRun,
    paint: Paint,
) {
    val bounds = run.contentBounds
    val image = run.image
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val destination = android.graphics.RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    // One paint is retained per page layer so page-turn frames do not allocate per styled run.
    // A tiled predecessor leaves a shader behind, therefore always clear it before reusing it.
    paint.shader = null
    val scale = image.scale.coerceIn(0.1f, 5f)
    when (image.fit) {
        1 -> {
            val width = bounds.width * scale
            val height = bounds.height * scale
            val rect = android.graphics.RectF(
                bounds.left + (bounds.width - width) / 2f,
                bounds.top + (bounds.height - height) / 2f,
                bounds.left + (bounds.width + width) / 2f,
                bounds.top + (bounds.height + height) / 2f,
            )
            canvas.save()
            canvas.clipRect(destination)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        }
        2 -> {
            val fit = maxOf(bounds.width / bitmap.width, bounds.height / bitmap.height) * scale
            val width = bitmap.width * fit
            val height = bitmap.height * fit
            val rect = android.graphics.RectF(
                bounds.left + (bounds.width - width) / 2f,
                bounds.top + (bounds.height - height) / 2f,
                bounds.left + (bounds.width + width) / 2f,
                bounds.top + (bounds.height + height) / 2f,
            )
            canvas.save()
            canvas.clipRect(destination)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        }
        3 -> drawNineSliceBackground(
            canvas = canvas,
            bitmap = bitmap,
            content = destination,
            frame = android.graphics.RectF(run.bounds.left, run.bounds.top, run.bounds.right, run.bounds.bottom),
            image = image,
            paint = paint,
        )
        else -> {
            val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val matrix = android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(bounds.left, bounds.top)
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawRect(destination, paint)
        }
    }
}

private fun drawNineSliceBackground(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    content: android.graphics.RectF,
    frame: android.graphics.RectF,
    image: ReaderTextBackgroundImage,
    paint: Paint,
) {
    ReaderNineSliceLayout.cells(
        bitmap.width,
        bitmap.height,
        ReaderRect(content.left, content.top, content.right, content.bottom),
        ReaderRect(frame.left, frame.top, frame.right, frame.bottom),
        image,
    ).forEach { cell ->
        canvas.drawBitmap(
            bitmap,
            android.graphics.Rect(cell.source.left, cell.source.top, cell.source.right, cell.source.bottom),
            android.graphics.RectF(
                cell.destination.left,
                cell.destination.top,
                cell.destination.right,
                cell.destination.bottom,
            ),
            paint,
        )
    }
}

private fun drawReview(canvas: android.graphics.Canvas, review: ReaderElement.Review, colorArgb: Int) {
    val start = review.bounds.left
    val end = review.bounds.right
    val baseline = review.baselinePx
    val height = review.textSizePx
    val path = android.graphics.Path().apply {
        moveTo(start + 1f, baseline - height * 2f / 5f)
        lineTo(start + height / 6f, baseline - height * .55f)
        lineTo(start + height / 6f, baseline - height * .8f)
        lineTo(end - 1f, baseline - height * .8f)
        lineTo(end - 1f, baseline)
        lineTo(start + height / 6f, baseline)
        lineTo(start + height / 6f, baseline - height / 4f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        textSize = height * .55f
        textAlign = Paint.Align.CENTER
    }
    paint.style = Paint.Style.STROKE
    canvas.drawPath(path, paint)
    paint.style = Paint.Style.FILL
    canvas.drawText(review.count.coerceAtMost(999).toString(), (start + height / 9f + end) / 2f, baseline - height * .23f, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTipRow(
    canvas: android.graphics.Canvas,
    row: ReaderTipRow,
    paint: Paint,
    baseline: Float,
) {
    row.tips.forEach { tip ->
        if (tip.visual == ReaderTipVisual.TEXT) {
            val x = when (tip.alignment) {
                ReaderTipAlignment.START -> { paint.textAlign = Paint.Align.LEFT; row.paddingLeftPx }
                ReaderTipAlignment.CENTER -> { paint.textAlign = Paint.Align.CENTER; size.width / 2f }
                ReaderTipAlignment.END -> { paint.textAlign = Paint.Align.RIGHT; size.width - row.paddingRightPx }
            }
            canvas.drawText(tip.text, x, baseline, paint)
        } else {
            drawVisualTip(canvas, row, tip, paint, baseline)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisualTip(
    canvas: android.graphics.Canvas,
    row: ReaderTipRow,
    tip: ReaderPageTip,
    paint: Paint,
    baseline: Float,
) {
    val unit = density
    val gap = 4f * unit
    val batteryWidth = 28f * unit
    val number = tip.batteryPercent.coerceIn(0, 100).toString()
    val numberWidth = paint.measureText(number)
    val textWidth = paint.measureText(tip.text)
    val visualWidth = when (tip.visual) {
        ReaderTipVisual.BATTERY_OUTER -> batteryWidth + 2f * unit + numberWidth
        ReaderTipVisual.BATTERY_INNER -> (if (tip.text.isEmpty()) 0f else textWidth + gap) + batteryWidth
        ReaderTipVisual.BATTERY_ICON -> batteryWidth
        ReaderTipVisual.BATTERY_CLASSIC -> (if (tip.text.isEmpty()) 0f else textWidth + gap) + numberWidth + 10f * unit
        ReaderTipVisual.ARROW -> 12f * unit + 8f * unit + textWidth
        ReaderTipVisual.TEXT -> textWidth
    }
    val left = when (tip.alignment) {
        ReaderTipAlignment.START -> row.paddingLeftPx
        ReaderTipAlignment.CENTER -> (size.width - visualWidth) / 2f
        ReaderTipAlignment.END -> size.width - row.paddingRightPx - visualWidth
    }
    paint.textAlign = Paint.Align.LEFT
    when (tip.visual) {
        ReaderTipVisual.BATTERY_OUTER -> {
            drawBatteryGlyph(canvas, left, baseline, tip.batteryPercent, paint, drawNumberInside = false)
            canvas.drawText(number, left + batteryWidth + 2f * unit, baseline, paint)
        }
        ReaderTipVisual.BATTERY_INNER -> {
            val batteryLeft = if (tip.text.isEmpty()) left else {
                canvas.drawText(tip.text, left, baseline, paint)
                left + textWidth + gap
            }
            drawBatteryGlyph(canvas, batteryLeft, baseline, tip.batteryPercent, paint, drawNumberInside = true)
        }
        ReaderTipVisual.BATTERY_ICON ->
            drawBatteryGlyph(canvas, left, baseline, tip.batteryPercent, paint, drawNumberInside = false)
        ReaderTipVisual.BATTERY_CLASSIC -> {
            val numberLeft = if (tip.text.isEmpty()) left + 4f * unit else {
                canvas.drawText(tip.text, left, baseline, paint)
                left + textWidth + gap + 4f * unit
            }
            canvas.drawText(number, numberLeft, baseline, paint)
            val top = baseline + paint.fontMetrics.ascent - 2f * unit
            val bottom = baseline + paint.fontMetrics.descent + 2f * unit
            val frame = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = unit.coerceAtLeast(1f) }
            canvas.drawRect(numberLeft - 2f * unit, top, numberLeft + numberWidth + 2f * unit, bottom, frame)
            canvas.drawRect(
                numberLeft + numberWidth + 2f * unit,
                top + (bottom - top) / 3f,
                numberLeft + numberWidth + 4f * unit,
                bottom - (bottom - top) / 3f,
                Paint(paint).apply { style = Paint.Style.FILL },
            )
        }
        ReaderTipVisual.ARROW -> {
            val centerY = baseline + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            val arrow = android.graphics.Path().apply {
                moveTo(left + 8f * unit, centerY - 5f * unit)
                lineTo(left + 3f * unit, centerY)
                lineTo(left + 8f * unit, centerY + 5f * unit)
            }
            canvas.drawPath(arrow, Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * unit
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.MITER
            })
            canvas.drawText(tip.text, left + 20f * unit, baseline, paint)
        }
        ReaderTipVisual.TEXT -> Unit
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBatteryGlyph(
    canvas: android.graphics.Canvas,
    left: Float,
    baseline: Float,
    batteryPercent: Int,
    paint: Paint,
    drawNumberInside: Boolean,
) {
    val unit = density
    val bodyWidth = 22f * unit
    val bodyHeight = 10f * unit
    val top = baseline + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f - bodyHeight / 2f
    val bodyLeft = left + 2f * unit
    val outline = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = unit.coerceAtLeast(1f)
        alpha = 194
    }
    val fill = Paint(paint).apply { style = Paint.Style.FILL; alpha = 194 }
    canvas.drawRoundRect(bodyLeft, top, bodyLeft + bodyWidth, top + bodyHeight, unit, unit, outline)
    canvas.drawRect(
        bodyLeft + bodyWidth,
        top + bodyHeight / 3f,
        bodyLeft + bodyWidth + 2f * unit,
        top + bodyHeight * 2f / 3f,
        fill,
    )
    val innerWidth = (bodyWidth - 4f * unit) * batteryPercent.coerceIn(0, 100) / 100f
    if (innerWidth > 0f) {
        canvas.drawRoundRect(
            bodyLeft + 2f * unit,
            top + 2f * unit,
            bodyLeft + 2f * unit + innerWidth,
            top + bodyHeight - 2f * unit,
            unit,
            unit,
            fill,
        )
    }
    if (drawNumberInside) {
        val numberPaint = Paint(paint).apply {
            textSize = minOf(textSize * .72f, 8f * unit)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val centerY = top + bodyHeight / 2f - (numberPaint.fontMetrics.ascent + numberPaint.fontMetrics.descent) / 2f
        canvas.drawText(
            batteryPercent.coerceIn(0, 100).toString(),
            bodyLeft + bodyWidth / 2f,
            centerY,
            numberPaint,
        )
    }
}
