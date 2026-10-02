package io.legado.app.ui.book.readaloud.morph

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect

/**
 * 「胶囊 ↔ 听书播放页」形变的纯几何计算。
 *
 * 形变使用单一 0..1 进度。面板始终按全屏布局，
 * 本实现按最终坐标裁剪面板，不缩放圆角，也不随进度重测面板布局。
 *
 * **面板与封面使用不同的几何曲线**：
 * - 视觉面（板子）：单一线性 `t`；
 * - 封面：位置分量 `e1 = 1-(1-t)²`（先快）+ 尺寸分量 `e2 = t²`（先慢）。
 * 共用一条曲线会变成「一个框在等比放大」，板子和封面没有层次。
 *
 * 只有「面」和「封面」吃几何插值，文字控件只吃透明度（见 [computeMorphVeil]），
 * 否则文字会被非等比缩放拉糊。
 */

/** 遮罩 / 内容开始淡入的下限点。 */
const val MORPH_VEIL_START = 0.20f

/** 遮罩 / 内容完全不透明的上限点：收起时在此点之前完全保持可见，之后在中后期渐变消失。 */
const val MORPH_VEIL_END = 0.65f

/**
 * 封面旋转归零的进度点。
 *
 * 必须早于内容淡入：胶囊展开前期旋转即平滑归零，交接处不产生抖动。
 */
const val MORPH_ROTATION_SETTLE = 0.20f

/**
 * 收起时保留播放页背景，只有进度降到最后 15% 才交还胶囊底色。
 * progress 是展开进度：0 为胶囊，1 为播放页，不能把收起末段误写为 0.85..1。
 */
const val MORPH_FACE_BLEND_START = 0f
const val MORPH_FACE_BLEND_END = 0.15f

/** 视觉面形变帧。 */
@Immutable
data class PlayerPanelMorphFrame(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val shadowAlpha: Float,
    val cornerRadii: PlayerPanelCornerRadii = PlayerPanelCornerRadii.all(cornerRadiusPx),
)

@Immutable
data class PlayerPanelCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    companion object {
        val Zero = all(0f)
        fun all(radius: Float) = PlayerPanelCornerRadii(radius, radius, radius, radius)
    }
}

/** 封面形变帧。 */
@Immutable
data class PlayerCoverMorphFrame(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val imageScale: Float,
    val imageRotationDeg: Float,
    val shadowAlpha: Float,
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** 两端导数为零的缓动；进度类曲线用它接缝更顺。 */
private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 位置曲线：先快后慢。 */
internal fun morphPositionEase(t: Float): Float = 1f - (1f - t) * (1f - t)

/** 尺寸曲线：先慢后快。 */
internal fun morphSizeEase(t: Float): Float = t * t

/**
 * 视觉面形变：从 [start]（胶囊矩形）长到 [end]（全屏矩形）。
 *
 * **几何用单一线性 `t`**，与封面的两段式曲线刻意分开。
 * 两条曲线共用一条的后果是「一个框在等比放大」，板子和封面之间没有层次：
 * 板子匀速长大、封面先慢后快，才有「封面从里面长出来」的观感。
 *
 * 起点或终点未测量时返回 null，调用方退化为不做几何插值。
 */
fun computePanelMorphFrame(
    progress: Float,
    start: Rect,
    end: Rect,
    startCornerRadiusPx: Float,
    endCornerRadiusPx: Float,
    endCornerRadii: PlayerPanelCornerRadii = PlayerPanelCornerRadii.all(endCornerRadiusPx),
): PlayerPanelMorphFrame? {
    if (start.isEmpty || end.isEmpty) return null
    val t = progress.coerceIn(0f, 1f)

    val w = lerp(start.width, end.width, t)
    val h = lerp(start.height, end.height, t)
    val cornerRadiusPx = lerp(
        startCornerRadiusPx,
        endCornerRadiusPx.coerceAtMost(minOf(w, h) * 0.5f),
        t,
    )
    return PlayerPanelMorphFrame(
        bounds = Rect(
            left = lerp(start.left, end.left, t),
            top = lerp(start.top, end.top, t),
            right = lerp(start.right, end.right, t),
            bottom = lerp(start.bottom, end.bottom, t),
        ),
        cornerRadiusPx = cornerRadiusPx,
        // 阴影比尺寸收敛得快一点（收敛在 e2≈0.85）。
        shadowAlpha = lerp(1f, 0f, morphSizeEase(t)),
        cornerRadii = PlayerPanelCornerRadii(
            topLeft = lerp(startCornerRadiusPx, endCornerRadii.topLeft, t).coerceIn(
                0f,
                minOf(w, h) / 2f
            ),
            topRight = lerp(startCornerRadiusPx, endCornerRadii.topRight, t).coerceIn(
                0f,
                minOf(w, h) / 2f
            ),
            bottomRight = lerp(startCornerRadiusPx, endCornerRadii.bottomRight, t).coerceIn(
                0f,
                minOf(w, h) / 2f
            ),
            bottomLeft = lerp(startCornerRadiusPx, endCornerRadii.bottomLeft, t).coerceIn(
                0f,
                minOf(w, h) / 2f
            ),
        ),
    )
}

/**
 * 把累加旋转角折到 `(-180, 180]`。
 *
 * 胶囊封面是「每 12 秒转一圈」的累加动画（`Animatable` 一直在 `+360`），
 * 播放十几分钟后这个值可以到几千度。直接拿它做 `× (1 - e2)` 的插值，
 * 收起时飞行封面会顺着插值把前面攒下的每一圈都转一遍——这正是「返回时封面旋转很多次」的来源。
 *
 * 视觉上旋转角取模 360 完全等价，折到 `(-180, 180]` 还顺带保证走最短路径：
 * 形变期间最多转过半圈。
 */
fun normalizeMorphRotationDeg(deg: Float): Float {
    if (!deg.isFinite()) return 0f
    val wrapped = deg % 360f
    return when {
        wrapped > 180f -> wrapped - 360f
        wrapped <= -180f -> wrapped + 360f
        else -> wrapped
    }
}

/**
 * 封面形变：从胶囊里的圆形小封面长到播放页封面页的大封面。
 *
 * [imageRotationDeg] 是胶囊封面当前的旋转角（累加值），先归一化到半圈以内，
 * 再在 [MORPH_ROTATION_SETTLE] 之前回落到 0 —— 必须早于内容淡入，交接才不会「转着落地」。
 */
fun computeCoverMorphFrame(
    progress: Float,
    start: Rect,
    end: Rect,
    startCornerRadiusPx: Float,
    endCornerRadiusPx: Float,
    imageRotationDeg: Float = 0f,
): PlayerCoverMorphFrame? {
    if (start.isEmpty || end.isEmpty) return null
    val t = progress.coerceIn(0f, 1f)
    val e1 = morphPositionEase(t)
    val e2 = morphSizeEase(t)

    // 中心的 X 与 Y 用**不同**的缓动：X 先快（贴着胶囊横向就位），Y 跟尺寸一样先慢。
    // 这是「从胶囊里长出来」而不是「从胶囊里滑出来」的关键。
    val cx = lerp(start.center.x, end.center.x, e1)
    val cy = lerp(start.center.y, end.center.y, e2)
    val w = lerp(start.width, end.width, e2)
    val h = lerp(start.height, end.height, e2)
    val maxRadius = minOf(w, h) * 0.5f
    return PlayerCoverMorphFrame(
        bounds = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f),
        cornerRadiusPx = lerp(startCornerRadiusPx, endCornerRadiusPx, e2)
            .coerceAtMost(maxRadius),
        // 页面封面不缩放，终点必须与它逐像素一致。
        imageScale = 1f,
        imageRotationDeg = normalizeMorphRotationDeg(imageRotationDeg) *
                (1f - smoothstep(t / MORPH_ROTATION_SETTLE)),
        shadowAlpha = lerp(1f, 0f, e2),
    )
}

/**
 * 内容可见度：前 [MORPH_VEIL_START] 段保持全透明，最后一段才淡入。
 *
 * 单独一条曲线是刻意的：内容一旦参与几何缩放就会被拉糊，所以它只吃透明度。
 * 收起时反过来用（进度越小越透明）。
 *
 * 内容可见度使用 smoothstep 平滑窗口，保证收起中后期优雅淡出、展开中前期自然显现。
 */
fun computeMorphVeil(
    progress: Float,
    veilStart: Float = MORPH_VEIL_START,
    veilEnd: Float = MORPH_VEIL_END,
): Float {
    val t = progress.coerceIn(0f, 1f)
    if (t <= veilStart) return 0f
    if (t >= veilEnd) return 1f
    return smoothstep((t - veilStart) / (veilEnd - veilStart))
}

/**
 * 视觉面的交接进度：0 = 完全是起点胶囊的脸，1 = 完全是播放页背景。
 *
 * 面板的起点就是胶囊本身（同矩形、同圆角），所以进度 0 时面板必须长得和胶囊一模一样，
 * 否则一按下去就会「先跳到播放页配色、再从那里缩小回胶囊」——收起时最明显。
 * 单条曲线同时驱动底色与背景透明度，两者不会脱节。
 */
fun computeMorphFaceBlend(
    progress: Float,
    blendStart: Float = MORPH_FACE_BLEND_START,
    blendEnd: Float = MORPH_FACE_BLEND_END,
): Float {
    val t = progress.coerceIn(0f, 1f)
    if (blendEnd <= blendStart) return if (t >= blendEnd) 1f else 0f
    return ((t - blendStart) / (blendEnd - blendStart)).coerceIn(0f, 1f)
}

/** 接近胶囊时交还原始材质，避免进度归零时才把纯色面切换成玻璃。 */
fun computeMorphPanelAlpha(progress: Float): Float = smoothstep(progress / 0.15f)

/** 胶囊封面与播放面使用同一淡入/淡出曲线，收起末段连续交接。 */
fun computeCapsuleCoverAlpha(progress: Float): Float = 1f - computeMorphPanelAlpha(progress)

/** 无封面页面返回时，封面留在胶囊端点，随末段材质交接平滑渐显。 */
fun computeMorphFlyingCoverAlpha(progress: Float, coverPageVisible: Boolean): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    return if (coverPageVisible) 1f else 1f - computeMorphPanelAlpha(progress)
}

/** 临界阻尼弹簧临近终点的速度上限：避免甩动越过端点后切换封面。 */
fun computeMorphSettleVelocity(current: Float, target: Float, velocity: Float): Float {
    val distance = kotlin.math.abs(target - current)
    val limit = minOf(3f, kotlin.math.sqrt(260f) * distance)
    return velocity.coerceIn(-limit, limit)
}


/** 系统返回只预览部分收起，保留剩余运动交给确认后的弹簧；取消时仍能恢复。 */
fun computePredictiveMorphProgress(startProgress: Float, backProgress: Float): Float =
    startProgress.coerceIn(0f, 1f) * (1f - 0.45f * backProgress.coerceIn(0f, 1f))
