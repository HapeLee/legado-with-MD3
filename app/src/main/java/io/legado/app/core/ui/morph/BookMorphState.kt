package io.legado.app.core.ui.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.legado.app.ui.book.readaloud.morph.PlayerCoverMorphFrame
import io.legado.app.ui.book.readaloud.morph.PlayerPanelCornerRadii
import io.legado.app.ui.book.readaloud.morph.PlayerPanelMorphFrame
import io.legado.app.ui.book.readaloud.morph.computeCoverMorphFrame
import io.legado.app.ui.book.readaloud.morph.computeMorphVeil
import io.legado.app.ui.book.readaloud.morph.computePanelMorphFrame

/**
 * 驱动阅读器、漫画界面的无手势形变状态。
 */
@Stable
class BookMorphState(
    val progress: Animatable<Float, AnimationVector1D>,
    private val density: Density,
    val hasTargetCover: Boolean = false,
) {
    var screenBounds by mutableStateOf(Rect.Zero)
        private set

    var screenCornerRadii by mutableStateOf(PlayerPanelCornerRadii.Zero)
        private set

    fun reportScreenCorners(radii: PlayerPanelCornerRadii) {
        screenCornerRadii = radii
    }

    fun reportScreenBounds(bounds: Rect) {
        screenBounds = bounds
    }

    var startBounds by mutableStateOf(Rect.Zero)
        private set
    var startCornerRadiusPx by mutableFloatStateOf(0f)
        private set

    var bookName by mutableStateOf<String?>(null)
        private set
    var author by mutableStateOf<String?>(null)
        private set
    var coverPath by mutableStateOf<String?>(null)
        private set
    var sourceOrigin by mutableStateOf<String?>(null)
        private set
    var bookUrl by mutableStateOf<String?>(null)
        private set

    var badgeText by mutableStateOf<String?>(null)
        private set
    var showBadgeDot by mutableStateOf(false)
        private set
    var leftBottomText by mutableStateOf<String?>(null)
        private set

    var fadeOnlyOpening by mutableStateOf(false)
        private set

    var isCollapsing by mutableStateOf(false)
        private set

    var coverEndBounds by mutableStateOf(Rect.Zero)
        private set
    var coverEndCornerRadiusPx by mutableFloatStateOf(0f)
        private set

    val hasCoverEnd: Boolean get() = !coverEndBounds.isEmpty

    fun reportCoverEnd(bounds: Rect, cornerRadiusPx: Float) {
        if (bounds.isEmpty) return
        if (coverEndBounds != bounds) coverEndBounds = bounds
        coverEndCornerRadiusPx = cornerRadiusPx
    }

    val expanded: Boolean get() = progress.value >= 1f

    val veil: Float get() = if (fadeOnlyOpening) progress.value else computeMorphVeil(progress.value)

    val coverAlpha: Float
        get() = if (hasTargetCover || hasCoverEnd) {
            if (progress.value <= 0.001f || progress.value >= 1f) 0f else 1f
        } else {
            computeBookCoverAlpha(progress.value, isCollapsing)
        }

    val badgeAlpha: Float
        get() = computeBookBadgeAlpha(progress.value, isCollapsing)

    fun onPredictiveBackStart() {
        isCollapsing = true
    }

    fun onPredictiveBackCancel() {
        isCollapsing = false
    }

    var anchorKey by mutableStateOf<String?>(null)
        private set

    fun setAnchor(anchor: BookCoverMorphAnchor?, key: String? = null) {
        anchorKey = key
        isCollapsing = false
        coverEndBounds = Rect.Zero
        coverEndCornerRadiusPx = 0f
        if (anchor != null && !anchor.bounds.isEmpty) {
            startBounds = anchor.bounds
            startCornerRadiusPx = anchor.cornerRadiusPx
            bookName = anchor.bookName
            author = anchor.author
            coverPath = anchor.coverPath
            sourceOrigin = anchor.sourceOrigin
            bookUrl = anchor.bookUrl
            badgeText = anchor.badgeText
            showBadgeDot = anchor.showBadgeDot
            leftBottomText = anchor.leftBottomText
            fadeOnlyOpening = false
        } else {
            bookUrl = null
            badgeText = null
            showBadgeDot = false
            leftBottomText = null
            fadeOnlyOpening = true
        }
    }

    fun updateAnchor(anchor: BookCoverMorphAnchor?) {
        if (anchor != null && !anchor.bounds.isEmpty) {
            startBounds = anchor.bounds
            startCornerRadiusPx = anchor.cornerRadiusPx
            if (coverPath == null) coverPath = anchor.coverPath
            if (bookUrl == null) bookUrl = anchor.bookUrl
            badgeText = anchor.badgeText
            showBadgeDot = anchor.showBadgeDot
            leftBottomText = anchor.leftBottomText
        }
    }

    fun targetStart(): Pair<Rect, Float> = computeTargetStart()

    private fun computeTargetStart(): Pair<Rect, Float> {
        val currentAnchor = if (isCollapsing) BookCoverMorphAnchors.get(anchorKey) else null
        if (isCollapsing) {
            val candidateAnchorBounds = currentAnchor?.bounds?.takeIf { !it.isEmpty }
            if (candidateAnchorBounds != null && isAnchorVisibleInScreen(
                    candidateAnchorBounds,
                    screenBounds
                )
            ) {
                return candidateAnchorBounds to currentAnchor.cornerRadiusPx
            } else if (!startBounds.isEmpty && isAnchorVisibleInScreen(startBounds, screenBounds)) {
                return startBounds to startCornerRadiusPx
            } else {
                val safeWidth = (120f * density.density).coerceAtLeast(100f)
                val safeHeight = safeWidth * (7f / 5f)
                val center = screenBounds.center
                return Rect(
                    left = center.x - safeWidth / 2f,
                    top = center.y - safeHeight / 2f,
                    right = center.x + safeWidth / 2f,
                    bottom = center.y + safeHeight / 2f,
                ) to (8f * density.density)
            }
        } else {
            return startBounds to startCornerRadiusPx
        }
    }

    fun panelFrame(): PlayerPanelMorphFrame? {
        if (fadeOnlyOpening || screenBounds.isEmpty) return null
        val (targetStartBounds, targetStartRadius) = computeTargetStart()
        if (targetStartBounds.isEmpty) return null
        return computePanelMorphFrame(
            progress = progress.value,
            start = targetStartBounds,
            end = screenBounds,
            startCornerRadiusPx = targetStartRadius,
            endCornerRadiusPx = screenCornerRadii.topLeft,
            endCornerRadii = screenCornerRadii,
        )
    }

    fun coverFrame(): PlayerCoverMorphFrame? {
        if (!hasCoverEnd || fadeOnlyOpening || screenBounds.isEmpty) return null
        val (targetStartBounds, targetStartRadius) = computeTargetStart()
        if (targetStartBounds.isEmpty || coverEndBounds.isEmpty) return null
        val targetEnd = if (isCollapsing) {
            if (isAnchorVisibleInScreen(coverEndBounds, screenBounds)) {
                coverEndBounds
            } else {
                return null
            }
        } else {
            coverEndBounds
        }
        return computeCoverMorphFrame(
            progress = progress.value,
            start = targetStartBounds,
            end = targetEnd,
            startCornerRadiusPx = targetStartRadius,
            endCornerRadiusPx = coverEndCornerRadiusPx,
            imageRotationDeg = 0f,
        )
    }

    /**
     * 等待授权关闭继承的手势结算速度（进度/秒）。
     *
     * 返回手势结束后业务逻辑（是否真的能关）要绕一圈才知道结果，宿主先把速度寄存在这里，
     * 授权后的收起再取走，避免收起丢掉手指的动量。
     */
    var pendingCollapseVelocity by mutableFloatStateOf(0f)
        private set

    fun recordCollapseVelocity(velocity: Float) {
        pendingCollapseVelocity = velocity
    }

    /**
     * 取出并清空待继承的手势速度，只生效一次。
     *
     * 只有当形变仍在收起途中（progress < 1）才继承：进度若已回到 1，
     * 说明这次手势被业务回退过（例如弹出了「加入书架」确认框），寄存的速度已经过期。
     */
    fun consumeCollapseVelocity(): Float {
        val velocity = pendingCollapseVelocity
        pendingCollapseVelocity = 0f
        return if (progress.value < 1f) velocity else 0f
    }

    suspend fun animateTo(target: Float, initialVelocity: Float = 0f) {
        isCollapsing = target < 0.5f
        progress.animateTo(
            targetValue = target.coerceIn(0f, 1f),
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = 260f,
                visibilityThreshold = 0.0001f,
            ),
            initialVelocity = initialVelocity,
        )
    }
}

/** 飞行封面开始淡出 / 开始渐显的进度点。 */
const val BOOK_COVER_FADE_START = 0.10f

/** 飞行封面完全交给内容 / 完全回到书架卡片的进度点。 */
const val BOOK_COVER_FADE_END = 0.50f

/**
 * 小说/漫画封面过渡透明度：在几何飞行的中段（[BOOK_COVER_FADE_START]..[BOOK_COVER_FADE_END]）完成封面淡出，
 * 避免封面在展开中途与内容双重叠加。
 *
 * 打开时（isCollapsing = false）：封面随卡片展开，在 0.10f..0.50f 平滑淡出交接给内容。
 * 收起时（isCollapsing = true）：内容在中后期（0.65f..0.20f）渐变消失，在中段（0.50f..0.10f）封面平滑渐显，完美回到书架卡片。
 *
 * 窗口换算到 [BookMorphState.animateTo] 的临界阻尼弹簧（stiffness = 260）约对应 33 ms → 104 ms；
 * 预测性返回期间进度由手指驱动，这段渐变随之被手势距离拉长。
 */
fun computeBookCoverAlpha(progress: Float, isCollapsing: Boolean): Float {
    val t = progress.coerceIn(0f, 1f)
    return if (isCollapsing) {
        if (t >= BOOK_COVER_FADE_END) 0f
        else ((BOOK_COVER_FADE_END - t) / (BOOK_COVER_FADE_END - BOOK_COVER_FADE_START))
            .coerceIn(0f, 1f)
    } else {
        if (t <= BOOK_COVER_FADE_START) 1f
        else (1f - (t - BOOK_COVER_FADE_START) / (BOOK_COVER_FADE_END - BOOK_COVER_FADE_START))
            .coerceIn(0f, 1f)
    }
}

/**
 * 书架封面角标（未读章节数、更新指示、来源标签）透明度渐变：
 * - 打开时 (isCollapsing = false)：在动画极前期 (0.0f..0.15f) 迅速平滑淡出，交接给阅读/详情界面；
 * - 收起时 (isCollapsing = true)：在收拢到书架的末段 (0.20f..0.0f) 平滑渐显，与书架原生角标零时差无缝贴合。
 */
fun computeBookBadgeAlpha(progress: Float, isCollapsing: Boolean): Float {
    val t = progress.coerceIn(0f, 1f)
    return if (isCollapsing) {
        if (t >= 0.20f) 0f
        else ((0.20f - t) / 0.20f).coerceIn(0f, 1f)
    } else {
        if (t <= 0.0f) 1f
        else if (t >= 0.15f) 0f
        else (1f - t / 0.15f).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberBookMorphState(
    anchorKey: String? = null,
    hasTargetCover: Boolean = false,
): BookMorphState {
    val density = LocalDensity.current
    return remember(density, anchorKey, hasTargetCover) {
        BookMorphState(
            progress = Animatable(0f),
            density = density,
            hasTargetCover = hasTargetCover,
        ).apply {
            if (!anchorKey.isNullOrBlank()) {
                val anchor = BookCoverMorphAnchors.get(anchorKey)
                setAnchor(anchor, anchorKey)
            }
        }
    }
}

/**
 * 判断锚点矩形是否在屏幕可视区域内有效可见（至少 25% 面积在屏幕内且坐标在有效边界内）。
 */
fun isAnchorVisibleInScreen(bounds: Rect, screen: Rect): Boolean {
    if (bounds.isEmpty || screen.isEmpty) return false
    val left = maxOf(bounds.left, screen.left)
    val top = maxOf(bounds.top, screen.top)
    val right = minOf(bounds.right, screen.right)
    val bottom = minOf(bounds.bottom, screen.bottom)
    if (right <= left || bottom <= top) return false
    val visibleArea = (right - left) * (bottom - top)
    val totalArea = bounds.width * bounds.height
    return totalArea > 0f && (visibleArea / totalArea) >= 0.25f
}

