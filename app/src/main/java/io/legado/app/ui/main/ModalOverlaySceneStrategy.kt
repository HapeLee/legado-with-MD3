package io.legado.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/** Keeps the previous destination composed while an entry renders its own modal window. */
class ModalOverlaySceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        entry.metadata[MetadataKey] ?: return null
        val previousEntries = entries.dropLast(1)
        if (previousEntries.isEmpty()) return null
        return ModalOverlayScene(
            entry = entry,
            previousEntries = previousEntries,
        )
    }

    companion object {
        private object MetadataKey : NavMetadataKey<Unit>

        fun modalOverlay(): Map<String, Any> = metadata { put(MetadataKey, Unit) }
    }
}

private data class ModalOverlayScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)

    // NavDisplay recursively resolves the scene underneath an overlay. Passing only the
    // last entry turns a nested overlay (book info under a reader) into a SinglePane root,
    // changing its composition/lifecycle owner and discarding its parent scene.
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
    override val content: @Composable () -> Unit = {
        // NavDisplay retains existing overlays and appends newly opened ones. Their
        // composition order can therefore differ from stack order for nested overlays.
        // Draw and hit-test by stack depth so a reader always sits above book info.
        Box(Modifier.zIndex(previousEntries.size.toFloat())) {
            entry.Content()
        }
    }
}
