package io.legado.app.data.repository.manga.translation

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class MangaBackgroundGroupsTest {
    @Test fun `contact seam blends repaired tones without changing unmasked pixels`() {
        val width = 24; val height = 16
        val original = IntArray(width * height) { 0xff334455.toInt() }
        val left = BooleanArray(original.size) { it / width in 2..13 && it % width in 2..11 }
        val right = BooleanArray(original.size) { it / width in 2..13 && it % width in 12..21 }
        val colors = original.copyOf()
        colors.indices.forEach { if (left[it]) colors[it] = 0xffeeeeee.toInt() else if (right[it]) colors[it] = 0xfffefefe.toInt() }
        val contacts = MangaBackgroundContacts(width, height).apply { add(left); add(right) }
        contacts.blend(colors)
        assertTrue((colors[8 * width + 11] and 255) > 238)
        assertTrue((colors[8 * width + 12] and 255) < 254)
        colors.indices.filter { !left[it] && !right[it] }.forEach { assertEquals(original[it], colors[it]) }
    }

    @Test fun `high contrast contact and distant padding are preserved`() {
        val colors = IntArray(40) { if (it % 10 < 5) 0xff000000.toInt() else -1 }
        val before = colors.copyOf()
        MangaBackgroundContacts(10, 4).apply {
            add(BooleanArray(40) { it % 10 < 5 }); add(BooleanArray(40) { it % 10 >= 5 }); blend(colors)
        }
        assertArrayEquals(before, colors)
        val sources = listOf(Rect(0, 0, 20, 20), Rect(20, 0, 40, 20), Rect(80, 0, 90, 20))
        val padded = sources.map { Rect(it).apply { inset(-30, -30) } }
        assertEquals(listOf(listOf(0, 1), listOf(2)), mangaBackgroundGroups(sources, padded))
        val huge = sources.map { Rect(0, 0, 2048, 2048) }
        assertEquals(3, mangaBackgroundGroups(sources, huge).size)
    }

    @Test fun `ellipsis display tokens retain every source character`() {
        for (text in listOf("前...後……", "．．．．．．", "😀…！"))
            assertEquals(text, mangaVerticalGlyphs(text).joinToString(""))
        assertEquals(listOf("...", "..."), mangaVerticalGlyphs("......"))
    }
}
