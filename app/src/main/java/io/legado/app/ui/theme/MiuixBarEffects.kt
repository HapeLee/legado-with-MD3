package io.legado.app.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.GlassDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.progressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun isMiuixHyperOs4Enabled(): Boolean =
    LegadoTheme.composeEngine == ComposeEngine.Miuix &&
        LocalAppUiConfiguration.current.theme.useHyperOs4Components &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal val LocalMiuixBarBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * Whether this composition can render into a Miuix Scaffold's popup host.
 *
 * `AppScaffold` provides it for the Miuix tree; window-level sheets/dialogs must override it to
 * `false` because their child composition otherwise inherits `true` without a host in that window.
 * Host-based glass overlays keep the inherited `true`.
 */
internal val LocalHasMiuixPopupHost = compositionLocalOf { false }

@Composable
internal fun MiuixBlurredTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    fixedStyle: Boolean = false,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalMiuixBarBackdrop.current
    val theme = LocalAppUiConfiguration.current.theme
    Box(modifier) {
        if (backdrop != null && theme.enableBlur) {
            val progressive = theme.enableProgressiveBlur
            val radius = if (fixedStyle) 24f else theme.topBarBlurRadius.toFloat()
            val colors = BlurDefaults.blurColors(
                blendColors = if (fixedStyle) emptyList() else listOf(
                    BlendColorEntry(
                        GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }
                            .copy(alpha = theme.topBarBlurAlpha.coerceIn(0, 100) / 100f),
                    ),
                ),
            )
            val background = Modifier.matchParentSize()
            Box(
                if (progressive) {
                    background
                        // Fade the sampled background while leaving title and actions fully opaque.
                        .graphicsLayer {
                            alpha = scrollBehavior?.state?.let {
                                (-it.contentOffset / 48.dp.toPx()).coerceIn(0f, 1f)
                            } ?: 1f
                        }
                        .progressiveTextureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = radius,
                            gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                            colors = colors,
                        )
                } else {
                    background.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = radius,
                        colors = colors,
                    )
                },
            )
        }
        content()
    }
}

@Composable
internal fun Modifier.miuixBottomBarBlur(): Modifier {
    val backdrop = LocalMiuixBarBackdrop.current ?: return this
    val theme = LocalAppUiConfiguration.current.theme
    if (!theme.enableBlur) return this
    val radius = theme.bottomBarBlurRadius.dp
    val alpha = theme.bottomBarBlurAlpha / 100f
    val tint = GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }.copy(alpha = alpha)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            if (theme.enableProgressiveBlur) progressiveBlur(radius.toPx(), gradient = ProgressiveBlur.Bottom)
            else blur(radius.toPx())
        },
        onDrawSurface = { drawRect(tint) },
    )
}

@Composable
internal fun Modifier.miuixCapsuleGlass(
    tint: Color,
    blurRadius: Dp = 12.dp,
    lensRadius: Dp = 8.dp,
): Modifier {
    val backdrop = LocalMiuixBarBackdrop.current ?: return this
    val highlight = if (LegadoTheme.isDark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(50) },
        effects = {
            val refraction = lensRadius.toPx().coerceAtMost(size.minDimension / 2f)
            padding = maxOf(padding, refraction)
            blur(blurRadius.toPx())
            if (refraction > 0f) {
                val scale = downscaleFactor.coerceAtLeast(1).toFloat()
                runtimeShaderEffect("LegadoCapsuleLens", CapsuleLensShader, "source") {
                    setFloatUniform("halfSize", size.width / (2f * scale), size.height / (2f * scale))
                    setFloatUniform("inset", padding / scale)
                    setFloatUniform("band", refraction / scale)
                }
            }
        },
        highlight = { highlight },
        onDrawSurface = { drawRect(tint) },
    )
}

// Refract the rim towards the capsule's center line. Uniforms use the recorded layer's pixels.
private const val CapsuleLensShader = """
    uniform shader source;
    uniform float2 halfSize;
    uniform float inset;
    uniform float band;

    half4 main(float2 xy) {
        float radius = min(halfSize.x, halfSize.y);
        float2 centerLine = halfSize - radius;
        float2 position = xy - inset - halfSize;
        float2 outward = position - clamp(position, -centerLine, centerLine);
        float distance = length(outward);
        float rim = clamp(1.0 - (radius - distance) / band, 0.0, 1.0);
        return source.eval(xy - outward / max(distance, 0.001) * band * rim * rim);
    }
"""
