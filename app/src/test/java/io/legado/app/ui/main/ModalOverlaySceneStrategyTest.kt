package io.legado.app.ui.main

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModalOverlaySceneStrategyTest {
    private val strategy = ModalOverlaySceneStrategy()
    private val scope = SceneStrategyScope<NavKey>()

    @Test
    fun `reader keeps the same book info overlay and home parent`() {
        val home = entry(MainRouteHome)
        val info = entry(MainRouteBookInfo("Book", "Author", "book-url"), overlay = true)
        val reader = entry(MainRouteReadBook(bookUrl = "book-url"), overlay = true)
        val infoBeforeReading = calculate(listOf(home, info)) as OverlayScene<NavKey>
        val readerScene = calculate(listOf(home, info, reader)) as OverlayScene<NavKey>

        // NavDisplay recursively calculates the background from overlaidEntries.
        // Keeping only info turns it into a SinglePane root and loses its overlay owner.
        val infoUnderReader = calculate(readerScene.overlaidEntries)
        assertNotNull("Book info must remain an overlay while reading", infoUnderReader)
        assertEquals(infoBeforeReading, infoUnderReader)
        assertEquals(listOf(home), (infoUnderReader as OverlayScene<NavKey>).previousEntries)

        val infoAfterReading = calculate(readerScene.previousEntries)
        assertEquals(infoBeforeReading, infoAfterReading)
        assertEquals(listOf(home), infoAfterReading!!.previousEntries)
    }

    @Test
    fun `root destination is never an overlay even with overlay metadata`() {
        val info = entry(MainRouteBookInfo("Book", "Author", "book-url"), overlay = true)
        assertNull(calculate(listOf(info)))
        assertNull(calculate(listOf(entry(MainRouteHome))))
    }

    private fun calculate(entries: List<NavEntry<NavKey>>): Scene<NavKey>? =
        with(strategy) { scope.calculateScene(entries) }

    private fun entry(key: NavKey, overlay: Boolean = false): NavEntry<NavKey> =
        NavEntry(
            key = key,
            metadata = if (overlay) ModalOverlaySceneStrategy.modalOverlay() else emptyMap(),
        ) {}
}
