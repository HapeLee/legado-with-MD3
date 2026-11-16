package io.legado.app.ui.book.changecover

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCoverOptionsTest {
    private val default = CoverOption(
        key = "default",
        label = "默认封面",
        coverUrl = "use_default_cover",
        isDefault = true,
    )
    private val album = listOf(
        CoverOption(
            key = "album:1:a",
            label = "图集",
            coverUrl = "/covers/a.jpg",
            albumPath = "/covers/a.jpg",
        )
    )

    @Test
    fun `all scope keeps local options and orders online covers by source order`() {
        val online = listOf(
            CoverOption("o:2", "源B", "https://cover/b.jpg", origin = "b", originOrder = 20),
            CoverOption("o:1", "源A", "https://cover/a.jpg", origin = "a", originOrder = 10),
        )

        val options = buildCoverOptions(default, album, online, null)

        assertEquals(listOf("default", "album:1:a", "o:1", "o:2"), options.map { it.key })
    }

    @Test
    fun `scope filters online covers but never hides local ones`() {
        val online = listOf(
            CoverOption("o:a", "源A", "https://cover/a.jpg", origin = "a", originOrder = 10),
            CoverOption("o:b", "源B", "https://cover/b.jpg", origin = "b", originOrder = 20),
        )

        val options = buildCoverOptions(default, album, online, setOf("b"))

        assertEquals(listOf("default", "album:1:a", "o:b"), options.map { it.key })
    }

    @Test
    fun `duplicate cover urls and blank urls keep only the first valid online cover`() {
        val online = listOf(
            CoverOption("o:b", "源B", "https://cover/same.jpg", origin = "b", originOrder = 20),
            CoverOption("o:a", "源A", "https://cover/same.jpg", origin = "a", originOrder = 10),
            CoverOption("o:empty", "源C", "", origin = "c", originOrder = 5),
        )

        val options = buildCoverOptions(default, album, online, null)

        assertEquals(listOf("default", "album:1:a", "o:a"), options.map { it.key })
    }

    @Test
    fun `empty scope hides every online cover`() {
        val online = listOf(
            CoverOption("o:a", "源A", "https://cover/a.jpg", origin = "a", originOrder = 10),
        )

        val options = buildCoverOptions(default, album, online, emptySet())

        assertEquals(listOf("default", "album:1:a"), options.map { it.key })
    }
}
