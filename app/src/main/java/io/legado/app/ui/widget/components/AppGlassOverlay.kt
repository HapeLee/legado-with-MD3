package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * The in-place glass menu layer and recorded page backgrounds for one Compose window.
 */
@Stable
internal class AppGlassOverlayState {

    internal class Entry(
        val key: Any,
        val content: @Composable BoxScope.() -> Unit,
    )

    private val entries = mutableStateListOf<Entry>()
    private val sources = mutableStateListOf<Pair<Any, LayerBackdrop?>>()

    /** Overlay origin in root coordinates. Root-space anchors are translated by it. */
    internal var origin: Offset? by mutableStateOf(null)

    internal val contents: List<Entry> get() = entries
    internal val backdrop: LayerBackdrop? get() = sources.lastOrNull()?.second

    internal fun registerBackdrop(owner: Any, backdrop: LayerBackdrop?) {
        val index = sources.indexOfFirst { it.first === owner }
        val source = owner to backdrop
        if (index >= 0) sources[index] = source else sources.add(source)
    }

    internal fun unregisterBackdrop(owner: Any) {
        sources.removeAll { it.first === owner }
    }

    internal fun register(key: Any, content: @Composable BoxScope.() -> Unit) {
        entries.add(Entry(key, content))
    }

    internal fun remove(key: Any) {
        entries.removeAll { it.key === key }
    }
}

internal val LocalAppGlassOverlay = compositionLocalOf<AppGlassOverlayState?> { null }

@Composable
internal fun AppGlassOverlayLayer(state: AppGlassOverlayState) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { state.origin = it.positionInRoot() }) {
        state.contents.forEach { entry ->
            key(entry.key) { entry.content(this) }
        }
    }
}
