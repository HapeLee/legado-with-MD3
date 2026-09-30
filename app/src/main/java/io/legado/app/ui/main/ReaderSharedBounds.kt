package io.legado.app.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.legado.app.ui.widget.components.image.cover.sharedCoverSourceRadius

/**
 * 阅读页与书架封面之间的 sharedBounds：把正文当成一块**只被裁剪、不缩放**的面，
 * 从封面那一格长到满屏（口径抄 `PlayerMorphHost` 的 `MorphPanelSurface`：
 * 「始终按全屏尺寸布局，只更新最终坐标下的裁剪轮廓」）。
 *
 * 库的默认 `resizeMode` 是 `scaleToBounds(FillWidth)`，会把整页排版等比缩到封面那格——
 * 看着像一张缩小的书页飘过去，不是桌面点开应用那种「板子长出来、边到硬边填满屏幕」。
 * `ContentScale.None` 让它保持最终布局，只有裁剪框在动；文字因此一个像素都不会被拉伸。
 * 起点用封面左上角对齐（桌面也是从图标那一角展开），收尾裁剪交给设备物理圆角。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.readerSharedBounds(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedCoverKey: String?,
    displayCornerRadiusPx: Float,
    density: Float,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null || sharedCoverKey == null) {
        return this
    }
    val targetRadius = displayCornerRadiusPx / density
    val startRadius = sharedCoverSourceRadius(sharedCoverKey)?.value ?: targetRadius
    val radiusState = animatedVisibilityScope.transition.animateFloat(
        label = "reader-clip-corner-radius",
    ) { state ->
        if (state == EnterExitState.Visible) targetRadius else startRadius
    }
    // 裁剪半径只在裁剪轮廓真正被解析的那一刻读（绘制阶段）。原来写成 `radius.dp` 是把每帧都变的
    // 值读进了重组：转场期间整个阅读页正文每帧重组一次，这就是「卡卡的」最直接的一处
    //（`PlayerMorphHost` 里对同一件事有同样的告诫）。
    val clipShape = remember(radiusState) { AnimatedCornerShape(radiusState::value) }
    // 裁剪框的曲线用播放器那一份临界阻尼弹簧：一次到位、不回弹，收尾是硬边而不是「弹一下」。
    return this.then(with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(sharedCoverKey),
            animatedVisibilityScope = animatedVisibilityScope,
            // 进入只留很短的淡入：这块面要读起来是「不透明的板子」，600ms 淡入会一路透出书架，
            // 展开的就成了淡出的一张纸而不是一块板。
            enter = fadeIn(animationSpec = tween(140)),
            // 返回书架时把正文压在封面之上：封面那一侧的 sharedBounds 用的是库默认 fadeIn()（spring），
            // 几百毫秒就满了，而正文是自己的 600ms 淡出。overlay 里谁在上面谁决定观感——封面在上面
            // 就是「先闪出封面、再看着它缩小」，正文在上面才是打开动画的倒放（边缩小边让正文化掉、露出封面）。
            exit = fadeOut(animationSpec = tween(600)),
            boundsTransform = BoundsTransform { _, _ -> readerBoundsSpring },
            resizeMode = ResizeMode.scaleToBounds(ContentScale.None, Alignment.TopStart),
            zIndexInOverlay = 1f,
            clipInOverlayDuringTransition = OverlayClip(clipShape),
        )
    })
}

/**
 * 圆角值延迟读取的 [Shape]：`createOutline` 在绘制阶段被调用，那里读动画状态就只让轮廓重算，
 * 不会把宿主 Composable 拖进每帧重组。
 */
private class AnimatedCornerShape(private val radiusDp: () -> Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        // 裁剪框刚长成封面那一格时比圆角还小，不夹住会画出自交的路径
        val radiusPx = (radiusDp() * density.density)
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx))
    }
}

/** 与 `ReadAloudMorphState.SETTLE_SPEC` 同一条弹簧（那里是 Float，这里要 Rect，只保留同样的阻尼与刚度）。 */
private val readerBoundsSpring: FiniteAnimationSpec<Rect> =
    spring(dampingRatio = 1f, stiffness = 260f)
