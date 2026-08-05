package io.legado.app.ui.book.group

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupCoverFileExtensionTest {

    @Test
    fun `uses the image MIME subtype instead of forcing png`() {
        assertEquals("jpg", groupCoverFileExtension("image/jpeg"))
        assertEquals("png", groupCoverFileExtension("image/png"))
        assertEquals("webp", groupCoverFileExtension("image/webp"))
        assertEquals("svg", groupCoverFileExtension("image/svg+xml"))
    }

    @Test
    fun `falls back to jpg when MIME type is unavailable or invalid`() {
        assertEquals("jpg", groupCoverFileExtension(null))
        assertEquals("jpg", groupCoverFileExtension("application/octet-stream"))
    }
}
