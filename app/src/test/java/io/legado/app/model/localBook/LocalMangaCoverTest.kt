package io.legado.app.model.localBook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMangaCoverTest {

    @Test
    fun `cover image wins over ordinary pages`() {
        assertEquals(
            "cover.png",
            pickMangaCoverEntry(listOf("001.jpg", "cover.png", "002.jpg")),
        )
    }

    @Test
    fun `cover name is matched ignoring case`() {
        assertEquals(
            "Comic/Cover.JPEG",
            pickMangaCoverEntry(listOf("Comic/1.jpg", "Comic/Cover.JPEG")),
        )
    }

    @Test
    fun `shallowest cover wins when several chapters carry one`() {
        assertEquals(
            "Comic/cover.webp",
            pickMangaCoverEntry(listOf("Comic/ch1/cover.jpg", "Comic/cover.webp", "Comic/ch2/cover.png")),
        )
    }

    @Test
    fun `falls back to the first page in natural order`() {
        assertEquals(
            "Comic/1.png",
            pickMangaCoverEntry(listOf("Comic/10.jpg", "Comic/2.jpg", "Comic/1.png")),
        )
    }

    @Test
    fun `non image cover names are ignored`() {
        assertNull(pickMangaCoverEntry(listOf("cover.txt", "readme.md")))
        assertEquals("page.jpg", pickMangaCoverEntry(listOf("page.jpg", "cover.txt")))
        assertNull(pickMangaCoverEntry(emptyList()))
    }
}
