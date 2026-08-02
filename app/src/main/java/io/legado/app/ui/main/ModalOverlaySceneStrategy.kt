package io.legado.app.ui.main

import androidx.compose.runtime.Composable
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
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries.takeLast(1)
    override val content: @Composable () -> Unit = { entry.Content() }
}
