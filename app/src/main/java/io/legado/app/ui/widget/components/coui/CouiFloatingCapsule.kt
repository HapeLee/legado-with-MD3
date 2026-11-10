package io.legado.app.ui.widget.components.coui

import android.graphics.BlendMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import timber.log.Timber

internal val LocalCouiFloatingNavigation = staticCompositionLocalOf<CouiFloatingNavigation?> { null }

internal class CouiFloatingNavigation(val backdrop: CouiBackdrop, val renderer: CouiFloatingRenderer)

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun rememberCouiCapsuleRenderer(): CouiFloatingRenderer? = remember {
    try {
        CouiFloatingRenderer.standalone()
    } catch (error: IllegalArgumentException) {
        Timber.w(error, "COUI floating material is unavailable")
        null
    } catch (error: UnsupportedOperationException) {
        Timber.w(error, "COUI floating material is unavailable")
        null
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun couiFloatingCapsule(
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    shadowAlpha: Float = 1f,
    scale: (Size) -> Float = { 1f },
): Modifier {
    val dark = CouiTheme.isDark
    val tint = CouiTheme.materialTintFor(dark)
    val contour = LocalCouiContourGlowEnabled.current
    val density = LocalDensity.current.density
    val material = remember(dark, tint) {
        CouiFloatingMaterial(
            firstColor = if (dark) Color(0x80666666) else Color(0xA3666666),
            firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
            secondColor = if (dark) Color(0xB3262626) else Color(0x80F0F0F0),
            edgeAlpha = if (dark) 0.8f else 0.25f,
            innerGlowAlpha = if (dark) 0.03f else 0.1f,
            themeTint = tint,
        )
    }
    val shadow = remember(dark, density, contour, shadowAlpha) {
        val preset = CouiTopAppBarButtonDefaults.shadow(dark = dark, capsule = true, density = density, contour = contour)
        preset.copy(color = preset.color.copy(alpha = preset.color.alpha * shadowAlpha.coerceIn(0f, 1f)))
    }
    val contentLayer = rememberGraphicsLayer()
    var size by remember { mutableStateOf(Size.Zero) }
    return Modifier
        .onSizeChanged { size = it.toSize() }
        .couiFloatingSurface(
            backdrop = backdrop,
            renderer = renderer,
            material = material,
            shadow = shadow,
            shape = CouiCapsuleShape,
            contentLayer = contentLayer,
            contourGlowEnabled = contour,
            foregroundComposition = true,
        ) {
            val factor = scale(size)
            CouiFloatingSurface(bounds = Rect(Offset.Zero, size), scaleX = factor, scaleY = factor)
        }
        .drawWithContent {
            // COUI composites this foreground after the material, keeping icons and text clear.
            contentLayer.record { this@drawWithContent.drawContent() }
        }
}
