package io.legado.app.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.legado.app.data.entities.Book
import io.legado.app.feature.reader.core.transition.READER_MORPH_BACK_DURATION_MILLIS
import io.legado.app.feature.reader.core.transition.READER_MORPH_EASING_X1
import io.legado.app.feature.reader.core.transition.READER_MORPH_EASING_X2
import io.legado.app.feature.reader.core.transition.READER_MORPH_EASING_Y1
import io.legado.app.feature.reader.core.transition.READER_MORPH_EASING_Y2
import io.legado.app.feature.reader.core.transition.READER_MORPH_OPEN_DURATION_MILLIS
import io.legado.app.feature.reader.core.transition.backCoverAlphaAt
import io.legado.app.feature.reader.core.transition.coverRectAt
import io.legado.app.feature.reader.core.transition.openCoverAlphaAt
import io.legado.app.feature.reader.core.transition.panelClipAt
import io.legado.app.ui.widget.components.image.cover.BookCoverAnchorStore
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 书架↔阅读页的形变（对标「胶囊→播放页」那套 `PlayerMorphHost`：面板始终按全屏布局，
 * 每帧只改最终坐标下的裁剪轮廓，正文一个像素都不被拉伸）。
 *
 * 为什么不再用 Material3 的 `sharedBounds`：
 * · 它要两端在同一刻都注册同一个 key 才能配对。在书架上停留一会儿再返回时配对失败，
 *   转场就退化成场景级的交叉淡入淡出（就是「返回动画还是旧的」）。这里形变只读**开书那一刻
 *   缓存下来的封面矩形**，返回动画完全由阅读页自己驱动，不依赖后一站还组合着。
 * · sharedBounds 会把整块正文搬进转场 overlay 每帧重画（还要连后一站一起画），这就是打开
 *   动画卡的原因；这里每帧只改一个 graphicsLayer 的裁剪轮廓，正文不重绘。
 * · 遮罩只有一块：封面层是这块被裁剪的面之内的子节点，所以「遮罩同时也遮罩封面」，
 *   不再是各是各的。
 */

/**
 * 一次开合的形变状态。
 *
 * [geometry] 是贝塞尔处理后的进度（0=还在封面那一格，1=铺满），管裁剪轮廓与封面的缩放；
 * [clock] 是本轮动画的线性时间比例，只管封面退场的时机——「封面完全消失的时机」说的是
 * 整体动画时间的百分之几，那是时间轴，不是进度轴，两者不能共用一个值。
 */
class ReaderCoverMorphState internal constructor(
    val startRectPx: Rect?,
    val startCornerRadiusPx: Float,
) {
    internal val geometry = Animatable(0f)
    internal val clock = Animatable(0f)

    /**
     * 本轮是返回：决定封面往哪个方向退场。
     * 是状态而不是普通字段——`LaunchedEffect` 在组合之后才写它，普通字段读不到这一趟的翻转。
     */
    internal var returning by mutableStateOf(false)

    internal var rootLeftPx by mutableFloatStateOf(0f)
    internal var rootTopPx by mutableFloatStateOf(0f)
    internal var canvasWidthPx by mutableFloatStateOf(0f)
    internal var canvasHeightPx by mutableFloatStateOf(0f)
}

/**
 * 取回封面落点并驱动开合。[animatedVisibilityScope] 为空（脱离导航栈、预览）时返回 null，
 * 阅读页按普通全屏出现，不裁剪也不淡入。
 */
@Composable
fun rememberReaderCoverMorph(
    sharedCoverKey: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): ReaderCoverMorphState? {
    val scope = animatedVisibilityScope ?: return null
    // 只在开书那一刻取一次：之后书架那边再怎么重排都不能改起点，否则返回动画会飞向错位。
    val anchor = remember(sharedCoverKey) { BookCoverAnchorStore.peek(sharedCoverKey) }
    val state = remember(sharedCoverKey, anchor) {
        ReaderCoverMorphState(anchor?.rectPx, anchor?.cornerRadiusPx ?: 0f)
    }
    val entering = scope.transition.targetState == EnterExitState.Visible
    LaunchedEffect(state, entering) {
        val durationMillis =
            if (entering) READER_MORPH_OPEN_DURATION_MILLIS else READER_MORPH_BACK_DURATION_MILLIS
        state.returning = !entering
        state.clock.snapTo(0f)
        val easing = CubicBezierEasing(
            READER_MORPH_EASING_X1,
            READER_MORPH_EASING_Y1,
            READER_MORPH_EASING_X2,
            READER_MORPH_EASING_Y2,
        )
        coroutineScope {
            launch {
                state.geometry.animateTo(
                    if (entering) 1f else 0f,
                    tween(durationMillis, easing = easing),
                )
            }
            launch {
                state.clock.animateTo(1f, tween(durationMillis, easing = LinearEasing))
            }
        }
    }
    return state
}

/**
 * 形变图层：整块阅读页按全屏布局，只被一个会长大的圆角矩形裁着。
 * 没有封面落点时退化成整块淡入淡出（从搜索/目录直接开书就是这条）。
 */
fun Modifier.readerCoverMorphLayer(
    state: ReaderCoverMorphState?,
    screenCornerRadiusPx: Float,
): Modifier {
    if (state == null) return this
    return this
        .onGloballyPositioned { coords ->
            val origin = coords.localToWindow(Offset.Zero)
            state.rootLeftPx = origin.x
            state.rootTopPx = origin.y
            state.canvasWidthPx = coords.size.width.toFloat()
            state.canvasHeightPx = coords.size.height.toFloat()
        }
        .graphicsLayer {
            val progress = state.geometry.value
            val start = state.startRectPx
            if (start == null) {
                clip = false
                alpha = progress
                return@graphicsLayer
            }
            if (progress >= 0.999f) {
                // 铺满之后不再裁剪：屏幕物理圆角交给系统，正文边缘不留二次裁切。
                clip = false
                alpha = 1f
                return@graphicsLayer
            }
            val (frame, cornerPx) = panelClipAt(
                start = start.translate(-state.rootLeftPx, -state.rootTopPx),
                canvasWidth = size.width,
                canvasHeight = size.height,
                startRadiusPx = state.startCornerRadiusPx,
                screenRadiusPx = screenCornerRadiusPx,
                progress = progress,
            )
            clip = true
            alpha = 1f
            shape = ReaderCoverMorphClipShape(frame, cornerPx)
        }
}

/**
 * 遮罩内的封面层：面板是从它身上长出去的，所以它跟着面板一起放大（只做等比，绝不压扁），
 * 并在整段走完三成半之前彻底让位给正文。
 *
 * 返回是打开的倒放：面板缩到最后三成半才把封面显出来，缩放同步收到原来那一格，
 * 最后一帧正好落回书架上那张同位置、同尺寸、同圆角的真封面，交接看不出来。
 * 少了这一层，收小的那一路裁出来的就是正文的一条切片，落到封面格上就是「各是各的」。
 */
@Composable
fun ReaderCoverMorphCover(
    state: ReaderCoverMorphState?,
    book: Book?,
    modifier: Modifier = Modifier,
) {
    val rect = state?.startRectPx ?: return
    if (book == null) return
    val density = LocalDensity.current
    val cornerRadiusDp = with(density) { state.startCornerRadiusPx.toDp() }
    val coverWidth = with(density) { rect.width.toDp() }
    val coverHeight = with(density) { rect.height.toDp() }
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (rect.left - state.rootLeftPx).roundToInt(),
                    (rect.top - state.rootTopPx).roundToInt(),
                )
            }
            .size(coverWidth, coverHeight)
            // 缩放全在图层变换里：改尺寸会连着让封面重新测量、重新解码，动画期间闪的就是它。
            .graphicsLayer {
                val progress = state.geometry.value
                val canvasWidth = state.canvasWidthPx
                val canvasHeight = state.canvasHeightPx
                val frame = if (canvasWidth > 0f && canvasHeight > 0f) {
                    coverRectAt(
                        start = rect.translate(-state.rootLeftPx, -state.rootTopPx),
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        progress = progress,
                    )
                } else null
                if (frame != null) {
                    scaleX = frame.width / size.width
                    scaleY = frame.height / size.height
                    translationX = frame.center.x - (rect.center.x - state.rootLeftPx)
                    translationY = frame.center.y - (rect.center.y - state.rootTopPx)
                } else {
                    scaleX = 1f
                    scaleY = 1f
                }
                alpha = when {
                    // 铺满时归零：动画收尾之后这块封面就该让位给正文，不能常驻在正文上。
                    progress >= 0.999f -> 0f
                    state.returning -> backCoverAlphaAt(state.clock.value)
                    else -> openCoverAlphaAt(state.clock.value)
                }
            }
            .clip(RoundedCornerShape(cornerRadiusDp)),
    ) {
        BookCoverImage(
            name = book.name,
            author = book.author,
            path = book.getDisplayCover(),
            sourceOrigin = book.origin,
            bookUrl = book.bookUrl,
            preferCache = true,
            showLoadingPlaceholder = false,
            sharedCoverKey = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 圆角矩形裁剪轮廓，坐标已是宿主图层的坐标（`PlayerMorphHost` 的 MorphPanelClipShape 同口径）。 */
internal class ReaderCoverMorphClipShape(
    private val frame: Rect,
    private val cornerRadiusPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                frame.left,
                frame.top,
                frame.right,
                frame.bottom,
                cornerRadiusPx,
                cornerRadiusPx,
            ),
        )
}
