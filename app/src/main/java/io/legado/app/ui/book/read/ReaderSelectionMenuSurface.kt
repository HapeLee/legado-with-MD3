package io.legado.app.ui.book.read

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.card.NormalCard
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.glassSurface

internal val LocalReaderSelectionBackdrop = compositionLocalOf<ReaderSelectionBackdrop?> { null }

// Selection menus retain their Popup windows for focus, IME, and dismissal. Use screen
// coordinates to sample the reader layer across the two Compose roots.
internal class ReaderSelectionBackdrop(private val layer: GraphicsLayer) : Backdrop {
    private var sourceCoordinates: LayoutCoordinates? = null
    var hasRecordedContent by mutableStateOf(false)
        private set

    override val isCoordinatesDependent = true

    fun Modifier.recordReaderWhileSelecting(menuState: State<TextMenuState?>): Modifier = this
        .onGloballyPositioned { sourceCoordinates = it }
        .drawWithContent {
            if (menuState.value != null) {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                if (!hasRecordedContent) hasRecordedContent = true
            } else {
                drawContent()
            }
        }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        val source = sourceCoordinates?.takeIf { it.isAttached } ?: return
        val target = coordinates?.takeIf { it.isAttached } ?: return
        val offset = target.positionOnScreen() - source.positionOnScreen()
        if (!offset.isSpecified) return
        val scaleFactor = 1f / downscaleFactor.coerceAtLeast(1)
        withTransform({
            translate(-offset.x * scaleFactor, -offset.y * scaleFactor)
            scale(scaleFactor, scaleFactor, Offset.Zero)
        }) {
            drawLayer(layer)
        }
    }
}

@Composable
internal fun ReaderSelectionMenuSurface(
    modifier: Modifier,
    cornerRadius: Dp,
    elevation: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LegadoTheme.composeEngine == ComposeEngine.Miuix &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalAppUiConfiguration.current.theme.useHyperOs4Components
    ) {
        val surface = glassSurface(
            backdrop = LocalReaderSelectionBackdrop.current?.takeIf { it.hasRecordedContent },
            visuals = GlassPopupDefaults.visuals(),
        )
        Column(modifier.then(surface(RoundedCornerShape(cornerRadius))), content = content)
    } else {
        NormalCard(
            modifier = modifier,
            cornerRadius = cornerRadius,
            elevation = elevation,
            containerColor = LegadoTheme.colorScheme.surfaceBright,
            content = content,
        )
    }
}
