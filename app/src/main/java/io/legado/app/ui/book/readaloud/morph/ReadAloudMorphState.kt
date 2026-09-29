package io.legado.app.ui.book.readaloud.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

enum class CapsuleAnchorKind { HomeBar, Global }

@Immutable
data class CapsuleProgressRing(val progress: Float, val color: Color, val strokeWidthPx: Float)

/**
 * 「胶囊 ↔ 听书播放页」形变的唯一进度源与锚点仓库。
 *
 * 单一进度同时驱动视觉面、封面、内容透明度与阴影；两端（胶囊 / 播放页封面）
 * 通过布局回调上报自己的矩形；旋转角独立同步，不依赖重新布局。
 *
 * 这是纯动画状态：不进 ViewModel、不进 UiState、不持久化。
 */
@Stable
class ReadAloudMorphState(
    val progress: Animatable<Float, AnimationVector1D>,
    private val density: Density,
) {

    /** 播放面展开后的目标矩形（宿主容器，即整窗）。 */
    var screenBounds by mutableStateOf(Rect.Zero)
        private set

    var screenCornerRadii by mutableStateOf(PlayerPanelCornerRadii.Zero)
        private set

    fun reportScreenCorners(radii: PlayerPanelCornerRadii) {
        screenCornerRadii = radii
    }

    var capsuleProgressRing by mutableStateOf<CapsuleProgressRing?>(null)
        private set

    fun reportProgressRing(ring: CapsuleProgressRing?) {
        capsuleProgressRing = ring
    }

    /** 胶囊整体矩形与圆角。 */
    var panelStartBounds by mutableStateOf(Rect.Zero)
        private set
    var panelStartCornerRadiusPx by mutableFloatStateOf(0f)
        private set

    /**
     * 起点胶囊的脸色。
     *
     * 面板在进度 0 时要与胶囊完全重合，所以底色必须由胶囊上报：阅读界面的可拖拽胶囊是
     * 黑白纯色，底栏式胶囊是主题容器色，两者不同，宿主猜不出来。
     */
    var panelStartColor by mutableStateOf(Color.Unspecified)
        private set

    /** 胶囊封面矩形与圆角。 */
    var coverStartBounds by mutableStateOf(Rect.Zero)
        private set
    var coverStartCornerRadiusPx by mutableFloatStateOf(0f)
        private set

    /** 播放页封面页的封面矩形与圆角。 */
    var coverEndBounds by mutableStateOf(Rect.Zero)
        private set
    var coverEndCornerRadiusPx by mutableFloatStateOf(0f)
        private set

    /** 关闭前是否位于封面页；动画中冻结，目录/歌词返回时在胶囊位置渐显封面。 */
    var coverPageVisible by mutableStateOf(true)
        private set
    private var latestCoverEndBounds = Rect.Zero
    private var latestCoverEndRadiusPx = 0f

    fun reportCoverPageVisible(visible: Boolean) {
        if (progress.value > 0f && !expanded) return
        coverPageVisible = visible
        // Pager 的最终布局可能先于可见性事件到达；回到封面页时补上最新布局。
        if (visible && !latestCoverEndBounds.isEmpty) {
            coverEndBounds = latestCoverEndBounds
            coverEndCornerRadiusPx = latestCoverEndRadiusPx
        }
    }

    /** 胶囊封面的旋转角，形变期间回落到 0。 */
    var coverRotationDeg by mutableFloatStateOf(0f)
        private set

    /**
     * 用户是否正在拖动进度（胶囊 ↔ 播放页）。
     *
     * 内容层在未展开时会吞掉触摸事件（避免点到看不见的播放页）；但拖动期间必须放行，
     * 否则闸门会把宿主自身的拖拽也吞掉，手势直接失效。
     */
    var dragging by mutableStateOf(false)

    /** 播放面是否已完全展开（也是「内容可交互」的判据）。 */
    val expanded: Boolean get() = progress.value >= 1f

    private var fadeOnlyOpening by mutableStateOf(false)

    var isCoverAnchor by mutableStateOf(false)
        private set

    fun prepareOpeningAnchors() {
        fadeOnlyOpening = false
    }

    /** 上报来自书架封面的形变锚点。不受胶囊类型限制，直接作为有效起点。 */
    fun reportBookCoverAnchor(bounds: Rect, cornerRadiusPx: Float) {
        if (progress.value > 0f && !expanded) return
        isCoverAnchor = true
        fadeOnlyOpening = false
        panelStartBounds = bounds
        panelStartCornerRadiusPx = cornerRadiusPx
        coverStartBounds = bounds
        coverStartCornerRadiusPx = cornerRadiusPx
        panelStartColor = Color.Unspecified
        capsuleProgressRing = null
    }

    /** 锚点等待失败后固定本次展开为淡入，迟到的测量不能在中途改变路径。 */
    fun useFadeOnlyOpening() {
        clearStartAnchors()
        fadeOnlyOpening = true
    }

    /** 路由/朗读会话改变后不沿用上一种胶囊的起点。 */
    fun clearStartAnchors() {
        if (progress.value > 0f && !expanded) return
        isCoverAnchor = false
        panelStartBounds = Rect.Zero
        coverStartBounds = Rect.Zero
        panelStartCornerRadiusPx = 0f
        coverStartCornerRadiusPx = 0f
        panelStartColor = Color.Unspecified
        capsuleProgressRing = null
    }

    private var expectedAnchorKind by mutableStateOf<CapsuleAnchorKind?>(null)
    private var panelAnchorKind by mutableStateOf<CapsuleAnchorKind?>(null)
    private var coverAnchorKind by mutableStateOf<CapsuleAnchorKind?>(null)
    private val panelAnchorMatches get() = expectedAnchorKind == null || panelAnchorKind == expectedAnchorKind
    private val coverAnchorMatches get() = expectedAnchorKind == null || coverAnchorKind == expectedAnchorKind
    val hasCapsuleAnchors: Boolean
        get() = !panelStartBounds.isEmpty && !coverStartBounds.isEmpty &&
                (isCoverAnchor || (panelAnchorMatches && coverAnchorMatches))

    /** 路由交接不能用上一种胶囊的残留测量开始动画。 */
    fun expectCapsuleAnchors(kind: CapsuleAnchorKind?) {
        if (progress.value <= 0f || expanded) expectedAnchorKind = kind
    }

    /** 不同播放会话可以共用胶囊位置，但不能把封面当作同一张图片飞行。 */
    var capsuleCoverLinked by mutableStateOf(true)
        private set

    fun reportCapsuleCoverLinked(linked: Boolean) {
        if (progress.value <= 0f || expanded) capsuleCoverLinked = linked
    }

    /** 内容可见度：前 85% 保持全透明。 */
    val veil: Float get() = computeMorphVeil(progress.value)

    fun reportScreenBounds(bounds: Rect) {
        if (screenBounds != bounds) screenBounds = bounds
    }

    /**
     * 上报胶囊端点。收起和完全展开时可更新，只有过渡期间冻结。
     *
     * 过渡开始后冻结，完全展开后重新接收真实位置：胶囊可能在启动播放后才出现，
     * 或发生折叠改变尺寸、
     * 用户点击时折叠态在展开、路由切换让阅读界面的可拖拽胶囊退出而底栏式胶囊进场。
     * 不冻结的话面板起点会中途跳位、面色跟着换，收起时尤其明显。
     */
    fun reportPanelStart(
        bounds: Rect,
        cornerRadiusPx: Float,
        surfaceColor: Color = Color.Unspecified,
        anchorKind: CapsuleAnchorKind? = null,
    ) {
        if (fadeOnlyOpening || (progress.value > 0f && !expanded)) return
        panelAnchorKind = anchorKind
        if (panelStartBounds != bounds) panelStartBounds = bounds
        panelStartCornerRadiusPx = cornerRadiusPx
        if (surfaceColor.isSpecified) panelStartColor = surfaceColor
    }

    /**
     * 上报封面起点。矩形同样只在进度 0 时冻结；旋转角**每次都写**。
     *
     * 旋转角是例外：胶囊封面在形变全程仍在转，飞行封面必须跟着它，落地那一刻才接得上
     * （胶囊自己的封面是同一角度）。冻结它反而会在收尾时看到一次跳角。
     */
    fun reportCoverStart(
        bounds: Rect,
        cornerRadiusPx: Float,
        rotationDeg: Float = 0f,
        anchorKind: CapsuleAnchorKind? = null,
    ) {
        if (!fadeOnlyOpening && (progress.value <= 0f || expanded)) {
            coverAnchorKind = anchorKind
            if (coverStartBounds != bounds) coverStartBounds = bounds
            coverStartCornerRadiusPx = cornerRadiusPx
        }
        reportCoverRotation(rotationDeg)
    }

    /** 旋转不触发布局回调，独立同步当前角度，保留已冻结的几何起点。 */
    fun reportCoverRotation(rotationDeg: Float) {
        coverRotationDeg = rotationDeg
    }

    fun reportCoverEnd(bounds: Rect, cornerRadiusPx: Float) {
        // Pager 会把不可见的封面放到屏幕外，不能用它覆盖封面页的真实终点。
        if (bounds.isEmpty) return
        latestCoverEndBounds = bounds
        latestCoverEndRadiusPx = cornerRadiusPx
        if (!coverPageVisible) return
        if (coverEndBounds != bounds) coverEndBounds = bounds
        coverEndCornerRadiusPx = cornerRadiusPx
    }

    /** 视觉面形变帧；两端未测量时退化为不做几何插值。 */
    fun panelFrame(): PlayerPanelMorphFrame? = computePanelMorphFrame(
        progress = progress.value,
        start = if (panelAnchorMatches) panelStartBounds else Rect.Zero,
        end = screenBounds,
        startCornerRadiusPx = panelStartCornerRadiusPx,
        endCornerRadiusPx = 0f,
        endCornerRadii = screenCornerRadii,
    )

    /** 封面形变帧；缺任一端就没有飞行封面。 */
    fun coverFrame(): PlayerCoverMorphFrame? {
        if (!capsuleCoverLinked || !coverAnchorMatches || coverStartBounds.isEmpty) return null
        val start = coverStartBounds
        if (coverPageVisible && coverEndBounds.isEmpty) return null
        return computeCoverMorphFrame(
            progress = if (coverPageVisible) progress.value else 0f,
            start = start,
            end = coverEndBounds.takeIf { !it.isEmpty } ?: start,
            startCornerRadiusPx = coverStartCornerRadiusPx,
            endCornerRadiusPx = coverEndCornerRadiusPx,
            imageRotationDeg = coverRotationDeg,
        )
    }

    /** 手势拖动一整段收起所需的像素距离，用于把位移换算成进度。 */
    fun dragRangePx(): Float =
        (screenBounds.height * 0.45f).coerceAtLeast(with(density) { 120.dp.toPx() })

    /**
     * 展开/收起动画。收起时会先把进度归零，由调用方在动画结束后真正关闭播放页。
     *
     * [initialVelocity] 是拖动手势结束时的进度速度（进度/秒），交给弹簧承接。
     * 不传就会在手指抬起处「顿一下」再重新起步，这是拖动收起最明显的生硬感来源。
     */
    suspend fun animateTo(target: Float, initialVelocity: Float = 0f) {
        // 兜底：万一拖动手势没走到 end/cancel（例如中途被取消），别让内容层一直放行触摸。
        dragging = false
        val clamped = target.coerceIn(0f, 1f)
        val velocity =
            if (initialVelocity == 0f && progress.isRunning) progress.velocity else initialVelocity
        progress.animateTo(
            targetValue = clamped,
            // 所有入口共用同一动力学，避免 tween 与弹簧切换时节奏断裂。
            // 两端高精度收敛，沿用当前速度处理动画中断与手势接管。
            animationSpec = SETTLE_SPEC,
            initialVelocity = computeMorphSettleVelocity(progress.value, clamped, velocity),
        )
        // 弹簧可能有微小残差，收尾对齐，保证 [expanded] 与内容可见度判定稳定。
        progress.snapTo(clamped)
        fadeOnlyOpening = false
    }

    private companion object {
        /** 临界阻尼，手势结算两端保持相同手感。 */
        // 默认 0.01 的残差在封面横向路径上仍可达数像素，不能直接当作到位。
        val SETTLE_SPEC =
            spring<Float>(dampingRatio = 1f, stiffness = 260f, visibilityThreshold = 0.0001f)

    }
}

/** 胶囊/播放页两侧通过它上报锚点、读取形变进度。 */
val LocalReadAloudMorph = staticCompositionLocalOf<ReadAloudMorphState?> { null }

@Composable
fun rememberReadAloudMorphState(
    initialProgress: Float = 0f,
): ReadAloudMorphState {
    val density = LocalDensity.current
    return remember(density) {
        ReadAloudMorphState(
            progress = Animatable(initialProgress),
            density = density,
        )
    }
}
