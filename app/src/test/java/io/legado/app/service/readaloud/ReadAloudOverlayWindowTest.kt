package io.legado.app.service.readaloud

import io.legado.app.feature.readaloud.overlay.capsuleDockSide
import io.legado.app.feature.readaloud.overlay.capsuleOffsetY
import io.legado.app.feature.readaloud.overlay.capsulePresentationOffsetX
import io.legado.app.feature.readaloud.overlay.capsuleVisibleOffsetX
import io.legado.app.feature.readaloud.overlay.capsuleWindowY
import io.legado.app.feature.readaloud.overlay.snapCapsuleOffsetX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudOverlayWindowTest {
    @Test
    fun `capsule keeps the same screen center across host changes and collapse`() {
        val density = 3f
        val saved = -120f
        val expandedHeight = 240
        val collapsedHeight = 144
        val expandedY = capsuleWindowY(saved, density, expandedHeight)
        val collapsedY = capsuleWindowY(saved, density, collapsedHeight)
        assertEquals(expandedY + expandedHeight / 2, collapsedY + collapsedHeight / 2)
        assertEquals(saved, capsuleOffsetY(expandedY, density, expandedHeight), 0.001f)
        assertEquals(saved, capsuleOffsetY(collapsedY, density, collapsedHeight), 0.001f)
    }

    @Test
    fun `position survives manual drag round trip at different densities`() {
        for (density in listOf(1f, 2.75f, 3f)) {
            val draggedWindowY = 512
            val saved = capsuleOffsetY(draggedWindowY, density, 240)
            assertEquals(draggedWindowY, capsuleWindowY(saved, density, 240))
        }
    }

    @Test
    fun `permission denial and visible app never create an external duplicate`() {
        assertTrue(shouldShowExternalCapsule(true, false, true))
        assertFalse(shouldShowExternalCapsule(true, false, false))
        assertFalse(shouldShowExternalCapsule(true, true, true))
        assertFalse(shouldShowExternalCapsule(false, false, true))
    }

    @Test
    fun `only release near an edge snaps while center positions stay unchanged`() {
        assertEquals(-180f, snapCapsuleOffsetX(-130f, 360f), 0.001f)
        assertEquals(180f, snapCapsuleOffsetX(130f, 360f), 0.001f)
        assertEquals(24f, snapCapsuleOffsetX(24f, 360f), 0.001f)
        assertEquals(180f, snapCapsuleOffsetX(1000f, 360f), 0.001f)
        assertEquals(-1, capsuleDockSide(-180f, 360f))
        assertEquals(1, capsuleDockSide(180f, 360f))
        assertEquals(0, capsuleDockSide(24f, 360f))
    }

    @Test
    fun `full mini cover extends halfway outside the screen while expanded controls fit`() {
        val mini = capsulePresentationOffsetX(180f, 360f, 80f, 0f)
        val expanded = capsulePresentationOffsetX(180f, 360f, 168f, 1f)
        assertEquals(180f, mini, 0.001f)
        assertEquals(96f, expanded, 0.001f)
        // 始终是 48dp 完整封面，右半 24dp 自然落在物理屏幕之外。
        assertEquals(384f, 180f + mini + 24f, 0.001f)
        assertEquals(336f, 180f + mini - 24f, 0.001f)
        assertEquals(-180f, capsulePresentationOffsetX(-180f, 360f, 80f, 0f), 0.001f)
        assertEquals(0f, capsuleVisibleOffsetX(180f, 100f, 168f), 0.001f)
    }

    @Test
    fun `collapse and expansion share a continuous position with no final half width jump`() {
        var previous = Float.POSITIVE_INFINITY
        for (step in 0..100) {
            val expansion = step / 100f
            val width = 80f + 88f * expansion
            val x = capsulePresentationOffsetX(180f, 360f, width, expansion)
            assertTrue(x <= previous)
            previous = x
            assertEquals(-x, capsulePresentationOffsetX(-180f, 360f, width, expansion), 0.001f)
            assertEquals(24f, capsulePresentationOffsetX(24f, 360f, width, expansion), 0.001f)
        }
        assertEquals(180f, capsulePresentationOffsetX(180f, 360f, 80.0088f, 0.0001f), 0.01f)
        assertEquals(96f, capsulePresentationOffsetX(180f, 360f, 168f, 1f), 0.001f)
    }

    @Test
    fun `snap threshold is inclusive and restoration preserves edge anchor`() {
        assertEquals(180f, snapCapsuleOffsetX(108f, 360f), 0.001f)
        assertEquals(107f, snapCapsuleOffsetX(107f, 360f), 0.001f)
        assertEquals(-180f, snapCapsuleOffsetX(-108f, 360f), 0.001f)
        assertEquals(180f, snapCapsuleOffsetX(180f, 360f), 0.001f)
    }
}
