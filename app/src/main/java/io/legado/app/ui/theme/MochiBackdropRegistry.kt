package io.legado.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import ui.feice.mochi.basic.MochiSheetState
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.MochiBackdrop

/** Routes can open overlays outside their scaffold; sample its content, never the overlay itself. */
@Stable
internal class MochiBackdropRegistry {
    private val sources = mutableStateListOf<Pair<Any, MochiBackdrop?>>()
    private val sheets = mutableStateListOf<MochiSheetState>()
    val current: MochiBackdrop? get() = sources.lastOrNull()?.second
    val currentSheet: MochiSheetState? get() = sheets.lastOrNull()

    fun addSheet(state: MochiSheetState) {
        sheets.add(state)
    }

    fun removeSheet(state: MochiSheetState) {
        sheets.remove(state)
    }

    fun register(owner: Any, backdrop: MochiBackdrop?) {
        sources.removeAll { it.first === owner }
        sources.add(owner to backdrop)
    }

    fun unregister(owner: Any) {
        sources.removeAll { it.first === owner }
    }
}

internal val LocalMochiBackdropRegistry = compositionLocalOf<MochiBackdropRegistry?> { null }

@Composable
internal fun rememberMochiOverlayBackdrop(visible: Boolean): MochiBackdrop? {
    val source = LocalMochiBackdrop.current ?: LocalMochiBackdropRegistry.current?.current
    // Keep the surface below the opening overlay, even if its content contains another scaffold.
    return remember(visible) { source }
}
