package io.legado.app.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import io.legado.app.domain.model.settings.NavigationAnimationStyle
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import ui.feice.mochi.anim.MochiMotions
import ui.feice.mochi.shape.MochiRoundedRectangle
import kotlin.math.abs
import kotlin.math.roundToInt

private const val UnderPageTravel = 0.25f
private const val SidePushResponseSeconds = 0.46f
private const val ScaleDurationMillis = 320
private const val ScaleFadeMillis = 100
private const val PageCornerSteps = 56
private val PageCornerShapes = List(PageCornerSteps + 1) { step ->
    if (step == 0) RectangleShape else MochiRoundedRectangle((step / 2f).dp)
}

internal fun AnimatedContentTransitionScope<Scene<NavKey>>.navigationPreset(
    style: NavigationAnimationStyle,
    forward: Boolean,
    layoutDirection: LayoutDirection,
    swipeEdge: Int? = null,
): ContentTransform? {
    if (initialState !is NavigationPageScene || targetState !is NavigationPageScene) return null
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    return when (style) {
        NavigationAnimationStyle.Default -> null
        NavigationAnimationStyle.Miuix -> sidePushTransform(forward, direction, swipeEdge != null)
        NavigationAnimationStyle.Scale -> if (forward) sidePushTransform(true, direction, false)
            else scaleTransform(direction, swipeEdge)
    }
}

private fun sidePushTransform(forward: Boolean, direction: Int, predictive: Boolean): ContentTransform {
    // NavDisplay seeks the transition by gesture progress. Linear motion keeps that mapping direct.
    val motion = if (predictive) {
        tween<IntOffset>(durationMillis = 300, easing = LinearEasing)
    } else {
        spring(
            dampingRatio = 1f,
            stiffness = MochiMotions.stiffnessFor(SidePushResponseSeconds),
            visibilityThreshold = IntOffset(1, 1),
        )
    }
    return if (forward) {
        slideInHorizontally(motion) { width -> direction * width } togetherWith
            slideOutHorizontally(motion) { width -> (-direction * width * UnderPageTravel).roundToInt() }
    } else {
        slideInHorizontally(motion) { width -> (-direction * width * UnderPageTravel).roundToInt() } togetherWith
            slideOutHorizontally(motion) { width -> direction * width }
    }
}

// Gesture and exit curves from InstallerX Revived's Scale preset.
private val GestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val QuickExitEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun scaleTransform(direction: Int, swipeEdge: Int?): ContentTransform {
    val pivot = TransformOrigin(if (swipeEdge == NavigationEvent.EDGE_LEFT) 0.8f else 0.2f, 0.5f)
    val exit = if (swipeEdge != null) {
        scaleOut(tween(ScaleDurationMillis, easing = GestureEasing), targetScale = 0.85f, transformOrigin = pivot) +
            fadeOut(tween(
                durationMillis = ScaleFadeMillis,
                delayMillis = ScaleDurationMillis - ScaleFadeMillis,
                easing = FastOutSlowInEasing,
            ))
    } else {
        scaleOut(tween(ScaleDurationMillis, easing = QuickExitEasing), targetScale = 0.85f, transformOrigin = pivot) +
            slideOutHorizontally(tween(ScaleDurationMillis, easing = QuickExitEasing)) { direction * it }
    }
    return EnterTransition.None togetherWith exit
}

internal class NavigationPageSceneDecorator(private val displayLeft: FloatState) : SceneDecoratorStrategy<NavKey> {
    override fun SceneDecoratorStrategyScope<NavKey>.decorateScene(scene: Scene<NavKey>): Scene<NavKey> {
        if (scene is ListDetailScene || scene.entries.size != 1 ||
            scene.metadata[NavDisplay.TransitionKey] != null ||
            scene.metadata[NavDisplay.PopTransitionKey] != null ||
            scene.metadata[NavDisplay.PredictivePopTransitionKey] != null
        ) return scene
        return NavigationPageScene(scene, displayLeft)
    }
}

internal data class NavigationPageScene(
    private val scene: Scene<NavKey>,
    private val displayLeft: FloatState,
) : Scene<NavKey> by scene {
    override val content: @Composable () -> Unit = {
        val style = LocalAppUiConfiguration.current.appShell.navigationAnimationStyle
        val modifier = when (style) {
            NavigationAnimationStyle.Default -> Modifier
            NavigationAnimationStyle.Miuix -> Modifier.fillMaxSize().sidePushSurface(displayLeft)
            NavigationAnimationStyle.Scale -> Modifier.fillMaxSize().scalePageCorners()
        }
        Box(modifier, propagateMinConstraints = true) { scene.content() }
    }
}

@Composable
private fun Modifier.scalePageCorners(): Modifier {
    val corner = LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { tween(ScaleDurationMillis, easing = LinearEasing) },
        label = "page-corner-progress",
    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
    return graphicsLayer { applyPageCorner(corner.value) }
}

private fun GraphicsLayerScope.applyPageCorner(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val step = (p * p * (3f - 2f * p) * PageCornerSteps).roundToInt()
    shape = PageCornerShapes[step]
    clip = step > 0
}

@Composable
private fun Modifier.sidePushSurface(displayLeft: FloatState): Modifier {
    val position = remember { mutableFloatStateOf(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val scrimAlpha = if (LegadoTheme.isDark) 0.4f else 0.18f
    // The slide is applied to an ancestor by AnimatedContent. Compare positions in the same root.
    return onGloballyPositioned { position.floatValue = it.positionInRoot().x }
        .drawWithCache {
            val shadowWidth = 32.dp.toPx()
            // Cubic falloff keeps the outer edge from reading as a second vertical seam.
            val shadowStops = Array(9) { index ->
                val strength = index / 8f
                strength to Color.Black.copy(alpha = strength * strength * strength)
            }
            val shadow = Brush.horizontalGradient(
                *shadowStops,
                startX = if (rtl) size.width + shadowWidth else -shadowWidth,
                endX = if (rtl) size.width else 0f,
            )
            onDrawWithContent {
                val x = (position.floatValue - displayLeft.floatValue) * direction
                if (x > 0f && size.width > 0f) {
                    drawRect(
                        brush = shadow,
                        topLeft = Offset(if (rtl) size.width else -shadowWidth, 0f),
                        size = Size(shadowWidth, size.height),
                        alpha = 0.14f * (1f - x / size.width).coerceIn(0f, 1f),
                    )
                }
                drawContent()
            }
        }
        .graphicsLayer {
            applyPageCorner(abs(position.floatValue - displayLeft.floatValue) / 14.dp.toPx())
        }
        .drawWithContent {
            drawContent()
            val x = (position.floatValue - displayLeft.floatValue) * direction
            if (x < 0f && size.width > 0f) {
                val covered = (-x / (size.width * UnderPageTravel)).coerceIn(0f, 1f)
                val easedCoverage = covered * covered * (3f - 2f * covered)
                drawRect(Color.Black, alpha = scrimAlpha * easedCoverage)
            }
        }
}
