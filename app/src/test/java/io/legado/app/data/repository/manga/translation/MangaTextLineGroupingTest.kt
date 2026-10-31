package io.legado.app.data.repository.manga.translation

import android.app.Application
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaTextLineGroupingTest {
    @Test fun `parallel rotated rows use de rotated distances instead of overlapping outer rectangles`() {
        fun tilted(id: String, offset: Float): MangaTranslatedRegion {
            val angle = .6f; val c = kotlin.math.cos(angle); val s = kotlin.math.sin(angle)
            fun point(x: Float, y: Float) = MangaTextPoint(150 + x * c - (y + offset) * s, 100 + x * s + (y + offset) * c)
            val quad = MangaTextQuad(listOf(point(0f, 0f), point(200f, 0f), point(200f, 20f), point(0f, 20f)))
            val box = MangaTextBox(quad.points.minOf { it.x }, quad.points.minOf { it.y }, quad.points.maxOf { it.x }, quad.points.maxOf { it.y })
            return MangaTranslatedRegion(id, id, id, box, sourceLines = listOf(MangaSourceLine(id, box, quad)))
        }
        val result = MangaTextLineGrouping.split(listOf(tilted("a", 0f), tilted("b", 60f)), 1000, 1000)
        assertEquals(2, result.size)
    }
    private fun row(id: String, l: Float, t: Float, r: Float = l + 200, h: Float = 20f, vertical: Boolean = false): MangaTranslatedRegion {
        val box = MangaTextBox(l, t, r, t + h)
        return MangaTranslatedRegion(id, id, id, box, vertical = vertical, sourceLines = listOf(MangaSourceLine(id, box)))
    }

    @Test fun `unassigned paddle rows form paragraphs without joining distant text`() {
        val a = row("a", 100f, 100f); val b = row("b", 100f, 130f); val c = row("c", 100f, 300f)
        val result = MangaContainerGeometry.regroup(1000, 1000, listOf(a, b, c), emptyList(), mergeLooseLines = true)
        assertEquals(listOf("a\nb", "c"), result.map { it.original })
        assertEquals(3, result.flatMap { it.sourceLines }.size)
    }

    @Test fun `spanning tree cuts the gap between two internally close paragraphs`() {
        val rows = listOf(100f, 128f, 190f, 218f).mapIndexed { i, t -> row("r$i", 100f, t) }
        val groups = MangaTextLineGrouping.split(rows, 1000, 1000)
        assertEquals(listOf(listOf("r0", "r1"), listOf("r2", "r3")), groups.map { it.map { r -> r.id } })
    }

    @Test fun `different physical bubbles never merge even when rows would otherwise be close`() {
        val first = row("a", 100f, 100f)
        val second = row("b", 100f, 130f)
        val bubbles = listOf(MangaBubbleDetection(MangaShapeRect(80, 80, 320, 123), .9f, 0),
            MangaBubbleDetection(MangaShapeRect(80, 125, 320, 160), .9f, 0))
        val result = MangaContainerGeometry.regroup(1000, 1000, listOf(first, second), bubbles, mergeLooseLines = true)
        assertEquals(listOf("a", "b"), result.map { it.original })
    }

    @Test fun `whole AI blocks and mixed text directions retain separate identities`() {
        val ai = row("ai", 100f, 100f).copy(sourceLines = emptyList())
        val local = row("local", 100f, 130f)
        val other = row("vertical", 320f, 100f, 340f, 100f, true)
        val result = MangaContainerGeometry.regroup(1000, 1000, listOf(ai, local, other), emptyList(), mergeLooseLines = true)
        assertEquals(3, result.size)
        assertEquals(setOf("ai", "local", "vertical"), result.map { it.original }.toSet())
    }

    @Test fun `vertical fragments read down each column before moving to the left`() {
        val upper = row("top", 800f, 100f, 820f, 100f, true)
        val lower = row("bottom", 801f, 210f, 821f, 100f, true)
        val left = row("left", 760f, 100f, 780f, 100f, true)
        assertEquals(listOf("top", "bottom", "left"), MangaTextLineGrouping.order(listOf(lower, left, upper), 1000, 1000).map { it.original })
    }
}
