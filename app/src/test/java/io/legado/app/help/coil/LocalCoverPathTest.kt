package io.legado.app.help.coil

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalCoverPathTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `existing absolute cover file is local`() {
        val file = folder.newFile("cover.jpg")

        assertTrue(isLocalCoverPath(file.absolutePath))
    }

    @Test
    fun `content and file uris are local`() {
        assertTrue(isLocalCoverPath("content://media/external/images/1"))
        assertTrue(isLocalCoverPath("file:///storage/emulated/0/cover.jpg"))
    }

    @Test
    fun `network urls and default cover sentinel are not local`() {
        assertFalse(isLocalCoverPath("https://example.com/cover.jpg"))
        assertFalse(isLocalCoverPath("http://example.com/cover.jpg"))
        assertFalse(isLocalCoverPath("data:image/png;base64,AAAA"))
        assertFalse(isLocalCoverPath("use_default_cover"))
    }

    @Test
    fun `missing absolute path is not treated as a local cover`() {
        val missing = folder.root.resolve("missing.jpg")

        assertFalse(isLocalCoverPath(missing.absolutePath))
    }
}
