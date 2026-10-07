package io.legado.app.core.ui.morph

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.readaloud.morph.PlayerPanelCornerRadii
import io.legado.app.ui.book.readaloud.morph.computeMorphPanelAlpha
import io.legado.app.ui.book.readaloud.morph.computePredictiveMorphProgress
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalBookMorph = compositionLocalOf<BookMorphState?> { null }

/**
 * 详情页等含有目标封面的页面：上报页面封面终点，并在过渡期间让位给宿主唯一的飞行封面。
 */
@Composable
fun Modifier.trackBookMorphCover(
    radius: Dp = 4.dp,
): Modifier {
    val morph = LocalBookMorph.current ?: return this
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    return this
        .onGloballyPositioned { coordinates ->
            morph.reportCoverEnd(
                bounds = coordinates.boundsInRoot(),
                cornerRadiusPx = radiusPx,
            )
        }
        .graphicsLayer {
            alpha = if (morph.expanded || morph.fadeOnlyOpening) 1f else 0f
        }
}

/**
 * 通用的阅读器/漫画形变宿主：从封面平滑展开到全屏，且不包含下拉收起手势。
 */
@Composable
fun BookMorphHost(
    anchorKey: String?,
    backgroundColor: Color = LegadoTheme.colorScheme.background,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    hasTargetCover: Boolean = false,
    onDismiss: () -> Boolean,
    onBackRequested: (() -> Unit)? = null,
    content: @Composable (onCollapse: () -> Unit) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val morph = rememberBookMorphState(anchorKey, hasTargetCover)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentBackRequested by rememberUpdatedState(onBackRequested)
    val currentBackEnabled by rememberUpdatedState(backEnabled)
    var backSettleJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(anchorKey, morph) {
        if (!anchorKey.isNullOrBlank()) {
            BookCoverMorphAnchors.setActiveMorph(anchorKey, morph)
        }
        onDispose {
            BookCoverMorphAnchors.clearActiveMorph(anchorKey)
        }
    }

    val refreshAnchor = {
        val anchor = BookCoverMorphAnchors.get(anchorKey)
        if (anchor != null && !anchor.bounds.isEmpty) {
            morph.updateAnchor(anchor)
        }
    }

    val collapse: () -> Unit = remember(scope, morph, anchorKey) {
        {
            backSettleJob?.cancel()
            backSettleJob = scope.launch {
                if (!anchorKey.isNullOrBlank()) {
                    BookCoverMorphAnchors.setActiveMorph(anchorKey, morph)
                }
                BookCoverMorphAnchors.get(anchorKey)?.let(morph::updateAnchor)
                morph.animateTo(0f, initialVelocity = morph.consumeCollapseVelocity())
                if (!currentDismiss()) morph.animateTo(1f)
            }
        }
    }

    LaunchedEffect(anchorKey) {
        val anchor = BookCoverMorphAnchors.get(anchorKey)
        morph.setAnchor(anchor, anchorKey)
        if (!anchorKey.isNullOrBlank()) {
            BookCoverMorphAnchors.setActiveMorph(anchorKey, morph)
        }
        morph.animateTo(1f)
    }

    // 拦截返回手势，支持预测性返回跟随手指缩回原封面
    PredictiveBackHandler(enabled = backEnabled) { events ->
        if (!currentBackEnabled) {
            events.collect { }
            return@PredictiveBackHandler
        }
        backSettleJob?.cancel()
        if (!anchorKey.isNullOrBlank()) {
            BookCoverMorphAnchors.setActiveMorph(anchorKey, morph)
        }
        refreshAnchor()
        morph.onPredictiveBackStart()
        val startProgress = morph.progress.value
        var lastProgress = startProgress
        var lastTimeNanos = 0L
        var releaseVelocity = 0f
        try {
            if (predictiveBackEnabled) {
                morph.progress.stop()
            }
            events.collect { event ->
                if (!predictiveBackEnabled) return@collect
                val preview = computePredictiveMorphProgress(startProgress, event.progress)
                val now = System.nanoTime()
                if (lastTimeNanos != 0L && now > lastTimeNanos) {
                    releaseVelocity = (preview - lastProgress) /
                            ((now - lastTimeNanos) / 1_000_000_000f)
                }
                lastProgress = preview
                lastTimeNanos = now
                morph.progress.snapTo(preview)
            }
            if (!predictiveBackEnabled) morph.progress.stop()
            val requestBack = currentBackRequested
            // 手势速度先寄存：业务授权后的收起由 collapse 取走，未授权（弹确认框）则由过期的
            // 进度判断丢弃，不会把旧动量带到下一次收起。
            morph.recordCollapseVelocity(releaseVelocity)
            if (requestBack != null) {
                // Reader business logic authorizes the exit through Finish. It then calls
                // collapse; the animation completion must never issue another close request.
                requestBack()
            } else {
                backSettleJob = scope.launch {
                    morph.animateTo(0f, initialVelocity = releaseVelocity)
                    if (!currentDismiss()) morph.animateTo(1f)
                }
            }
        } catch (cancelled: CancellationException) {
            morph.onPredictiveBackCancel()
            if (predictiveBackEnabled) {
                backSettleJob = scope.launch { morph.animateTo(1f) }
            }
            throw cancelled
        }
    }

    CompositionLocalProvider(LocalBookMorph provides morph) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    morph.reportScreenBounds(coordinates.boundsInRoot())
                    if (Build.VERSION.SDK_INT >= 31) {
                        val insets = view.rootWindowInsets
                        fun radius(position: Int): Float =
                            insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f

                        val r = maxOf(
                            radius(RoundedCorner.POSITION_TOP_LEFT),
                            radius(RoundedCorner.POSITION_TOP_RIGHT),
                            radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                            radius(RoundedCorner.POSITION_BOTTOM_LEFT),
                        )
                        morph.reportScreenCorners(PlayerPanelCornerRadii.all(r))
                    } else {
                        morph.reportScreenCorners(PlayerPanelCornerRadii.Zero)
                    }
                }
        ) {
            // 1. 动态裁剪背景层
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val screen = morph.screenBounds
                        val frame = morph.panelFrame()
                        alpha =
                            if (frame == null) morph.veil else computeMorphPanelAlpha(morph.progress.value)
                        clip = !morph.expanded && frame != null && !screen.isEmpty
                        if (!clip || frame == null) return@graphicsLayer
                        shape = MorphPanelClipShape(
                            frame.bounds.translate(-screen.left, -screen.top),
                            frame.cornerRadii,
                        )
                        shadowElevation = 0f
                    }
                    .background(backgroundColor)
            )

            // 2. 内容层：无几何缩放，只吃透明度与轮廓裁剪，内容还没完全显形前清空语义和屏蔽点击
            val contentSettled by remember {
                derivedStateOf { morphContentSettled(morph.veil, morph.progress.value) }
            }
            val contentSemantics = if (contentSettled) Modifier else Modifier.clearAndSetSemantics {}
            val inputGate = if (contentSettled) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentSemantics)
                    .then(inputGate)
                    .graphicsLayer {
                        clip = !morph.expanded
                        val frame = if (clip) morph.panelFrame() else null
                        if (frame != null) {
                            shape = MorphPanelClipShape(
                                frame.bounds.translate(
                                    -morph.screenBounds.left,
                                    -morph.screenBounds.top
                                ),
                                frame.cornerRadii,
                            )
                        } else {
                            clip = false
                        }
                        alpha = morph.veil
                    }
            ) {
                content(collapse)
            }

            // 3. 飞行封面：
            // 若页面有封面终点（如详情页），封面从起点连续飞向页面封面位置，全程保持连续；
            // 若页面无封面终点（如小说/漫画阅读），封面随卡片展开并在中段（0.10f..0.50f）平滑淡出。
            val coverAlpha by remember { derivedStateOf { morph.coverAlpha } }
            val flyingCoverVisible by remember {
                derivedStateOf { !morph.expanded && !morph.fadeOnlyOpening && coverAlpha > 0.001f }
            }
            if (flyingCoverVisible) {
                val density = LocalDensity.current
                val bounds: Rect?
                val cornerRadiusPx: Float
                if (morph.hasTargetCover) {
                    if (morph.hasCoverEnd) {
                        val frame = morph.coverFrame()
                        bounds = frame?.bounds
                        cornerRadiusPx = frame?.cornerRadiusPx ?: 0f
                    } else {
                        // 目标页面封面终点尚在测量（第 0 帧），在终点就位前稳妥锚定在起点，绝不跳变到全屏 panel
                        val (startBounds, startRadius) = morph.targetStart()
                        bounds = startBounds
                        cornerRadiusPx = startRadius
                    }
                } else {
                    val frame = morph.panelFrame()
                    bounds = frame?.bounds
                    cornerRadiusPx = frame?.cornerRadiusPx ?: 0f
                }
                if (bounds != null && !bounds.isEmpty) {
                    val screen = morph.screenBounds
                    val localBounds = bounds.translate(-screen.left, -screen.top)
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    localBounds.left.roundToInt(),
                                    localBounds.top.roundToInt()
                                )
                            }
                            .size(
                                with(density) { localBounds.width.toDp() },
                                with(density) { localBounds.height.toDp() }
                            )
                            .graphicsLayer {
                                alpha = coverAlpha
                                clip = true
                                shape =
                                    RoundedCornerShape(with(density) { cornerRadiusPx.toDp() })
                            }
                    ) {
                        BookCoverImage(
                            name = morph.bookName,
                            author = morph.author,
                            path = morph.coverPath,
                            sourceOrigin = morph.sourceOrigin,
                            bookUrl = morph.bookUrl,
                            preferCache = true,
                            showLoadingPlaceholder = false,
                            sharedCoverKey = morph.anchorKey,
                            modifier = Modifier.fillMaxSize(),
                        )

                        val badgeAlpha = morph.badgeAlpha
                        val currentBadgeText = morph.badgeText
                        val currentLeftBottomText = morph.leftBottomText
                        if (badgeAlpha > 0.001f && !currentBadgeText.isNullOrEmpty()) {
                            TextCard(
                                text = currentBadgeText,
                                icon = if (morph.showBadgeDot) Icons.Default.Update else null,
                                iconSize = 12.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .graphicsLayer { alpha = badgeAlpha },
                                cornerRadius = 4.dp,
                                horizontalPadding = 4.dp,
                                verticalPadding = 2.dp,
                            )
                        }

                        if (badgeAlpha > 0.001f && !currentLeftBottomText.isNullOrEmpty()) {
                            TextCard(
                                text = currentLeftBottomText,
                                backgroundColor = LegadoTheme.colorScheme.cardContainer,
                                contentColor = LegadoTheme.colorScheme.onCardContainer,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(2.dp)
                                    .graphicsLayer { alpha = badgeAlpha },
                                cornerRadius = 4.dp,
                                horizontalPadding = 4.dp,
                                verticalPadding = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MorphPanelClipShape(
    private val bounds: Rect,
    private val radii: PlayerPanelCornerRadii,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(
            rect = bounds,
            topLeft = CornerRadius(radii.topLeft, radii.topLeft),
            topRight = CornerRadius(radii.topRight, radii.topRight),
            bottomRight = CornerRadius(radii.bottomRight, radii.bottomRight),
            bottomLeft = CornerRadius(radii.bottomLeft, radii.bottomLeft),
        ),
    )
}

/**
 * 内容层什么时候该放行触点与读屏。
 *
 * 判据与内容透明度同源（[BookMorphState.veil]，即本文件内容层 `graphicsLayer` 里的那个 alpha）：
 * 透明度在 `MORPH_VEIL_END` 就已经满格，而 `progress` 要等弹簧收敛才正好等于 1f。
 * 拿 `progress` 当闸门会留出一段「画面看着早停稳了、手指底下还是死的」的窗口，
 * 动画被取消或重播时这段窗口还会永久化。
 */
internal fun morphContentSettled(veil: Float, progress: Float): Boolean =
    veil >= 1f || progress <= 0.001f
