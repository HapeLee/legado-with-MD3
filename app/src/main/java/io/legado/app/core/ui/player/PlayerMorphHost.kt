package io.legado.app.core.ui.player

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import io.legado.app.ui.book.readaloud.morph.PlayerPanelCornerRadii
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.book.readaloud.morph.computeMorphFaceBlend
import io.legado.app.ui.book.readaloud.morph.computeMorphFlyingCoverAlpha
import io.legado.app.ui.book.readaloud.morph.computeMorphPanelAlpha
import io.legado.app.ui.book.readaloud.morph.computePredictiveMorphProgress
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideThemeOverride
import io.legado.app.ui.theme.ThemeOverrideState
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.player.PlayerBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 拖动结算时用速度投影的时长：0.1s 后进度落在哪，就按哪边吸附。 */
private const val SETTLE_PROJECTION_SECONDS = 0.1f

/** 飞行封面的固定解码尺寸：够 52% 宽的播放页封面清晰，又不随布局尺寸变化而重发请求。 */
private val FLYING_COVER_DECODE_SIZE = coil3.size.Size(1024, 1024)

/**
 * 两种播放页共用的 morph 宿主：与胶囊同窗口、同 composition，由单一进度驱动。
 *
 * 三层（顺序即叠放顺序）：
 * 1. **视觉面**：始终按全屏尺寸布局，只更新最终坐标下的裁剪轮廓，morph 期间零 measure；
 * 2. **内容**：不做几何缩放，只吃透明度（`progress` 的最后一段），避免文字被拉糊；
 * 3. **飞行封面**：唯一同时吃几何插值的内容元素，从胶囊封面长到播放页封面。
 *
 * 播放页因此不再使用 `ModalBottomSheet`（独立窗口）：跨窗口既拿不到对方坐标，
 * 也无法统一裁剪，做不出「面板从胶囊长出来」的观感。
 * 速度 / 定时 / 朗读设置仍是小弹层，继续用 `AppModalBottomSheet`——独立窗口在这里反而是优点。
 */
@Stable
data class PlayerMorphAppearance(
    val bookName: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val bgMode: Int,
)

@Composable
fun PlayerMorphHost(
    appearance: PlayerMorphAppearance,
    playerTheme: ThemeOverrideState?,
    morph: ReadAloudMorphState,
    visible: Boolean,
    awaitCapsuleAnchor: Boolean = false,
    backEnabled: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    verticalDragEnabled: Boolean = true,
    onBeforeCollapse: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable (onCollapse: () -> Unit) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentVisible by rememberUpdatedState(visible)
    val currentBeforeCollapse by rememberUpdatedState(onBeforeCollapse)
    var backSettleJob by remember { mutableStateOf<Job?>(null) }
    // 进度每帧都在变，用 derivedStateOf 收敛成布尔，避免宿主整体重组。
    // 必须带 visible 作为 key：derivedStateOf 的 lambda 只在首次组合捕获 visible，
    // 不带 key 的话 visible 变化不会反映到结果里。
    val hostPresent by remember(visible) {
        derivedStateOf { visible || morph.progress.value > 0.001f }
    }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentBackEnabled by rememberUpdatedState(backEnabled)

    val collapse: (Float) -> Unit = { velocity ->
        scope.launch {
            currentBeforeCollapse?.invoke()
            morph.animateTo(0f, initialVelocity = velocity)
            currentDismiss()
        }
    }

    LaunchedEffect(visible, awaitCapsuleAnchor) {
        if (visible) {
            // 服务刚启动时胶囊还没有布局。等两个稳定帧再冻结起点；
            // 首次会话由胶囊宿主提前布局；关闭胶囊时只淡入，不虚构坐标。
            if (morph.progress.value == 0f) {
                morph.prepareOpeningAnchors()
                val ready = awaitPlayerMorphAnchors(morph, awaitCapsuleAnchor)
                if (!ready) morph.useFadeOnlyOpening()
            }
            morph.animateTo(1f)
        } else if (morph.progress.value > 0f) {
            // 外部直接关闭（切经典控制、朗读停止等）：补一次收起动画。
            morph.animateTo(0f)
        }
    }
    // 播放页打开时重建注册，保持高于后挂载的阅读页返回处理器。
    // 始终用同一个返回处理器；关闭预测性动画时仍拦截返回，仅不预览手势进度。
    key(hostPresent) {
        PredictiveBackHandler(enabled = hostPresent && backEnabled) { events ->
            // AndroidX 的 enabled 更新存在一帧延迟，仍须收集 flow 才能结束此回调。
            if (!currentVisible || !currentBackEnabled) {
                events.collect { }
                return@PredictiveBackHandler
            }
            backSettleJob?.cancel()
            currentBeforeCollapse?.invoke()
            val startProgress = morph.progress.value
            var lastProgress = startProgress
            var lastTimeNanos = 0L
            var releaseVelocity = 0f
            try {
                if (predictiveBackEnabled) {
                    morph.progress.stop()
                    morph.dragging = true
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
                    // 串行更新同一进度源，不为每个系统事件另起协程。
                    morph.progress.snapTo(preview)
                }
                // 关闭预测动画时保持原进度，确认后才停止当前动画并执行统一结算。
                if (!predictiveBackEnabled) morph.progress.stop()
                morph.dragging = false
                // 确认后才真正关闭；键盘/三键返回的 flow 没有进度，也走同一路径。
                backSettleJob = scope.launch {
                    morph.animateTo(0f, initialVelocity = releaseVelocity)
                    currentDismiss()
                }
            } catch (cancelled: CancellationException) {
                // 手势取消时 collector 已被取消，恢复动画要交给宿主 scope。
                // 外部已经关闭播放页时不再恢复，避免与外部收起动画争抢。
                if (predictiveBackEnabled && currentVisible) {
                    backSettleJob = scope.launch { morph.animateTo(1f) }
                }
                throw cancelled
            } finally {
                morph.dragging = false
            }
        }
    }

    if (!hostPresent) return

    CompositionLocalProvider(LocalReadAloudMorph provides morph) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    morph.reportScreenBounds(coordinates.boundsInRoot())
                    // API 31 以下或系统没有提供圆角时使用方角，不反射 OEM 私有 API。
                    val windowBounds = coordinates.boundsInWindow()
                    val insets = view.rootWindowInsets
                    fun radius(position: Int, horizontalInset: Float, verticalInset: Float): Float {
                        if (Build.VERSION.SDK_INT < 31) return 0f
                        val corner = insets?.getRoundedCorner(position) ?: return 0f
                        return (corner.radius - maxOf(
                            horizontalInset,
                            verticalInset
                        )).coerceAtLeast(0f)
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        val rightInset =
                            (view.rootView.width - windowBounds.right).coerceAtLeast(0f)
                        val bottomInset =
                            (view.rootView.height - windowBounds.bottom).coerceAtLeast(0f)
                        morph.reportScreenCorners(
                            PlayerPanelCornerRadii(
                                radius(
                                    RoundedCorner.POSITION_TOP_LEFT,
                                    windowBounds.left,
                                    windowBounds.top
                                ),
                                radius(
                                    RoundedCorner.POSITION_TOP_RIGHT,
                                    rightInset,
                                    windowBounds.top
                                ),
                                radius(
                                    RoundedCorner.POSITION_BOTTOM_RIGHT,
                                    rightInset,
                                    bottomInset
                                ),
                                radius(
                                    RoundedCorner.POSITION_BOTTOM_LEFT,
                                    windowBounds.left,
                                    bottomInset
                                ),
                            )
                        )
                    } else {
                        morph.reportScreenCorners(PlayerPanelCornerRadii.Zero)
                    }
                }
                .then(
                    if (verticalDragEnabled) {
                        Modifier
                            .pointerInput(morph) {
                                val velocityTracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                        morph.dragging = true
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        velocityTracker.addPosition(
                                            change.uptimeMillis,
                                            change.position
                                        )
                                        if (dragAmount < 0f && morph.progress.value >= 1f) {
                                            return@detectVerticalDragGestures
                                        }
                                        change.consume()
                                        scope.launch {
                                            morph.progress.snapTo(
                                                (morph.progress.value - dragAmount / morph.dragRangePx())
                                                    .coerceIn(0f, 1f)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        morph.dragging = false
                                        settleMorph(
                                            morph = morph,
                                            scope = scope,
                                            onCollapse = collapse,
                                            progressVelocity = morphDragVelocity(
                                                velocityTracker,
                                                morph
                                            ),
                                        )
                                    },
                                    onDragCancel = {
                                        morph.dragging = false
                                        settleMorph(morph, scope, collapse, 0f)
                                    },
                                )
                            }
                            .nestedScroll(morphCollapseNestedScroll(morph, scope, collapse))
                    } else Modifier
                ),
        ) {
            MorphPanelSurface(
                morph = morph,
                appearance = appearance,
                playerTheme = playerTheme,
            )
            MorphPlayerContent(
                morph = morph,
                playerTheme = playerTheme,
            ) { content { collapse(0f) } }
            MorphFlyingCover(morph = morph, appearance = appearance)
        }
    }

}

/**
 * 视觉面：全屏尺寸 + 最终坐标下的轮廓裁剪，内容就是播放页自己的背景。
 *
 * 几何在延迟读取（`graphicsLayer`）里每帧算，不产生重组；
 * 背景用 [PlayerBackground] 而不是纯色，形变全程看到的都已经是播放页的底色，
 * 落地时背景不需要再淡入一次。
 */
@Composable
private fun MorphPanelSurface(
    morph: ReadAloudMorphState,
    appearance: PlayerMorphAppearance,
    playerTheme: ThemeOverrideState?,
) {
    // 面板是「缩小版的播放页」，必须和播放页共用封面取色主题：
    // 否则形变期间读到的是应用默认配色（一块灰），和落地后的播放页完全两个颜色。
    ProvideThemeOverride(playerTheme) {
        val containerColor = LegadoTheme.colorScheme.surface
        // 进度 0 时面板与胶囊完全重合，底色必须是胶囊的脸。
        // 优先使用胶囊上报的底色；没有起点颜色时沿用播放页颜色，不猜测胶囊主题。
        val collapsedColor = morph.panelStartColor.takeIf { it.isSpecified } ?: containerColor

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val screen = morph.screenBounds
                    val frame = morph.panelFrame()
                    alpha =
                        if (frame == null) morph.veil else computeMorphPanelAlpha(morph.progress.value)
                    // 系统圆角只参与过渡，完全展开后不额外裁剪播放页。
                    clip = !morph.expanded && frame != null && !screen.isEmpty
                    if (!clip || frame == null) return@graphicsLayer
                    // 在最终坐标空间裁剪，圆角不再被 scaleX/scaleY 压成椭圆。
                    // 全屏布局保持不变，背景和内容使用完全相同的轮廓。
                    shape = MorphPanelClipShape(
                        frame.bounds.translate(-screen.left, -screen.top), frame.cornerRadii,
                    )
                    // 不给全屏裁剪图层加 elevation：它会生成巨大的动态投影，
                    // 并与尚未淡出的胶囊阴影叠加。胶囊自身的投影保留。
                    shadowElevation = 0f
                }
                .drawBehind {
                    // 底色：胶囊的脸 → 播放页底色。背景模式选「透明」时播放页自身没有底，
                    // 这一层就是唯一的面。
                    val blend =
                        if (morph.panelFrame() == null) 1f else computeMorphFaceBlend(morph.progress.value)
                    drawRect(color = lerpColor(collapsedColor, containerColor, blend))
                },
        ) {
            // 播放页背景（封面模糊 / 流光）按同一条交接曲线淡入：
            // 起点是胶囊的脸，展开后才是播放页背景，收起时原路退回胶囊。
            PlayerBackground(
                name = appearance.bookName,
                author = appearance.author,
                path = appearance.coverPath,
                sourceOrigin = appearance.sourceOrigin,
                bgMode = appearance.bgMode,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha =
                            if (morph.panelFrame() == null) 1f else computeMorphFaceBlend(morph.progress.value)
                    },
            )
        }
    }
}

/**
 * 内容层：不做几何缩放，只按进度淡入淡出，并按当前面板矩形裁剪。
 *
 * 不缩放是为了不让文字被非等比拉伸；裁剪是为了让淡入发生在面板之内。
 * 未完全展开时清空语义，避免 TalkBack 读到还看不见的播放页内容。
 */
@Composable
private fun MorphPlayerContent(
    morph: ReadAloudMorphState,
    playerTheme: ThemeOverrideState?,
    content: @Composable () -> Unit,
) {
    val expanded by remember { derivedStateOf { morph.expanded } }
    val dragging by remember { derivedStateOf { morph.dragging } }
    val contentSemantics = if (expanded) Modifier else Modifier.clearAndSetSemantics {}
    // 内容只吃透明度、始终按全屏尺寸布局，所以未展开时虽然看不见却仍可命中，必须显式屏蔽。
    // 但拖动期间要放行：Initial 阶段吞事件会把宿主自己的拖拽也吞掉。
    val inputGate = if (expanded || dragging) {
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
                // 内容层不做几何缩放（文字会被拉糊），但必须裁在面板范围内：
                // 否则淡入的头几帧里，播放页背景会溢到面板圆角之外的上一站上。
                // 与背景层一致：完全展开后取消裁剪，开始收起时重新启用。
                clip = !morph.expanded
                val frame = if (clip) morph.panelFrame() else null
                if (frame != null) {
                    shape = MorphPanelClipShape(
                        frame.bounds.translate(-morph.screenBounds.left, -morph.screenBounds.top),
                        frame.cornerRadii,
                    )
                } else {
                    clip = false
                }
                alpha = morph.veil
            },
    ) {
        ProvideThemeOverride(playerTheme) {
            content()
        }
    }
}

/**
 * 飞行封面：唯一同时吃几何插值与内容绘制的元素。
 *
 * 三条刻意的取舍：
 * 1. **封面页不交叉淡出**：alpha 常驻 1，进度到 1 才让位给页面封面；目录/歌词页返回时，
 *    封面留在胶囊端点并在收起末段渐显。此前按 `1 - veil` 淡出，
 *    最后 15% 会与页面封面交叉溶解 —— 两者只要有一点尺寸/圆角差异就会露出来，
 *    观感是「封面飞到一半就散了」。
 * 2. **帧只在延迟读取里算**：帧每帧都变，写在组合体里会让整个封面每帧重组，
 *    而 `AsyncImage` 每次重组都可能重发请求（带 crossfade）——封面自己就会闪。
 * 3. **固定解码尺寸**：请求里写死尺寸后，布局尺寸变化不会改请求，也就不重发。
 *    两端宽高比不同（正圆 ↔ 5:7），必须按帧的真实宽高重新布局；
 *    像面板那样用非等比 scale 硬缩会把封面压扁。
 */
@Composable
private fun MorphFlyingCover(
    morph: ReadAloudMorphState,
    appearance: PlayerMorphAppearance,
) {
    val density = LocalDensity.current
    BookCoverImage(
        name = appearance.bookName,
        author = appearance.author,
        path = appearance.coverPath,
        sourceOrigin = appearance.sourceOrigin,
        // 听书是书维度场景，本地优先不跑书源脚本
        bookUrl = null,
        preferCache = true,
        // 飞行封面不需要占位图和交叉淡入：它一出现就该是封面本身。
        showLoadingPlaceholder = false,
        requestBuilder = { size(FLYING_COVER_DECODE_SIZE) },
        modifier = Modifier
            .layout { measurable, _ ->
                val frame = morph.coverFrame() ?: return@layout layout(0, 0) {}
                val width = frame.bounds.width.roundToInt().coerceAtLeast(0)
                val height = frame.bounds.height.roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val frame = morph.coverFrame() ?: return@graphicsLayer
                // 进度到 1 时播放页自己的封面就在同一位置，形变层让位（是让位，不是淡出）。
                alpha = computeMorphFlyingCoverAlpha(morph.progress.value, morph.coverPageVisible)
                rotationZ = frame.imageRotationDeg
                // layout 必须取整，但画面位置和大小保留浮点精度。
                // 默认中心变换原点下补偿半个尺寸差，保证两端边界完全对齐。
                translationX =
                    frame.bounds.left - morph.screenBounds.left + (frame.bounds.width - size.width) / 2f
                translationY =
                    frame.bounds.top - morph.screenBounds.top + (frame.bounds.height - size.height) / 2f
                scaleX =
                    if (size.width > 0f) frame.bounds.width / size.width * frame.imageScale else 1f
                scaleY =
                    if (size.height > 0f) frame.bounds.height / size.height * frame.imageScale else 1f
                shape = RoundedCornerShape(with(density) { frame.cornerRadiusPx.toDp() })
                clip = true
            }
            .drawWithContent {
                drawContent()
                val ring = morph.capsuleProgressRing ?: return@drawWithContent
                val alpha = 1f - computeMorphPanelAlpha(morph.progress.value)
                if (alpha <= 0f) return@drawWithContent
                val inset = ring.strokeWidthPx / 2f
                drawArc(
                    color = ring.color.copy(alpha = ring.color.alpha * alpha),
                    startAngle = -90f,
                    sweepAngle = 360f * ring.progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - ring.strokeWidthPx).coerceAtLeast(0f),
                        (size.height - ring.strokeWidthPx).coerceAtLeast(0f)
                    ),
                    style = Stroke(ring.strokeWidthPx, cap = StrokeCap.Round),
                )
            },
    )
}

/**
 * 可滚动页面的下拉收起：只在内容已经滚到顶部、仍有下滑余量时接管。
 *
 * 用 `onPostScroll`（子节点消费之后剩下的位移）而不是 `onPreScroll`，
 * 这样正文 / 目录滚动优先，两者不打架。
 */
private fun morphCollapseNestedScroll(
    morph: ReadAloudMorphState,
    scope: CoroutineScope,
    onCollapse: (Float) -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y <= 0f || morph.progress.value <= 0f) return Offset.Zero
        morph.dragging = true
        val delta = available.y / morph.dragRangePx()
        scope.launch {
            morph.progress.snapTo((morph.progress.value - delta).coerceIn(0f, 1f))
        }
        return Offset(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // 只有真的被这条通道驱动过才结算，普通列表惯性不受影响。
        if (morph.dragging) {
            morph.dragging = false
            settleMorph(
                morph = morph,
                scope = scope,
                onCollapse = onCollapse,
                progressVelocity = -available.y / morph.dragRangePx(),
            )
        }
        return Velocity.Zero
    }
}

/**
 * 把手势速度换算成进度速度（进度/秒）。
 *
 * 手指向上 = 进度变大，而 [VelocityTracker] 的 y 轴向上为负，所以取反。
 */
private fun morphDragVelocity(
    tracker: VelocityTracker,
    morph: ReadAloudMorphState,
): Float = -tracker.calculateVelocity().y / morph.dragRangePx()

/**
 * 拖动结束后的结算：按「当前进度 + 速度投影」判断落点，决定吸附回展开还是真正收起。
 *
 * 光看当前进度会在轻甩时判错方向；光看速度又会让慢速拖过一半时弹回去。
 *
 * 收起必须走 [onCollapse]（动画归零 + 关闭播放页），否则会停在进度 0 的空面板、
 * 而两个胶囊按 `1 - progress` 已经淡到看不见。
 */
private fun settleMorph(
    morph: ReadAloudMorphState,
    scope: CoroutineScope,
    onCollapse: (Float) -> Unit,
    progressVelocity: Float = 0f,
) {
    scope.launch {
        val projected = morph.progress.value + progressVelocity * SETTLE_PROJECTION_SECONDS
        if (projected >= 0.5f) {
            morph.animateTo(1f, initialVelocity = progressVelocity)
        } else {
            onCollapse(progressVelocity)
        }
    }
}

/**
 * 按形变矩形裁剪。
 *
 * 用值类型而不是可变对象：`graphicsLayer` 只在 `shape` 变化时重算轮廓，
 * 同一个实例会被判定为「没变」而沿用旧轮廓，需要用值相等性来表达「这一帧的几何」。
 */
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

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)

/** 保留下层布局用于形变背景，但模态播放浮层存在时不向辅助服务暴露下层控件。 */
internal fun Modifier.playerUnderlaySemantics(playerPresent: Boolean): Modifier =
    if (playerPresent) clearAndSetSemantics {} else this
