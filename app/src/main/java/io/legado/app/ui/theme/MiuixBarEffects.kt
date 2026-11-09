package io.legado.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.GlassDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.progressiveBlur
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalMiuixBarBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun Modifier.miuixBarBlur(bottom: Boolean = false, fixedStyle: Boolean = false): Modifier {
    val backdrop = LocalMiuixBarBackdrop.current ?: return this
    val theme = LocalAppUiConfiguration.current.theme
    if (!theme.enableBlur) return this
    val progressive = theme.enableProgressiveBlur
    val radius = when {
        fixedStyle -> 24.dp
        bottom -> theme.bottomBarBlurRadius.dp
        else -> theme.topBarBlurRadius.dp
    }
    val alpha = when {
        fixedStyle -> 0f
        bottom -> theme.bottomBarBlurAlpha / 100f
        else -> theme.topBarBlurAlpha / 100f
    }
    val tint = GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }.copy(alpha = alpha)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            if (progressive) progressiveBlur(radius.toPx(), gradient = if (bottom) ProgressiveBlur.Bottom else ProgressiveBlur.Top)
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
