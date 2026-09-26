package io.legado.app.help.coil

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MangaImageFileOwnerTest {
    @Test
    fun `late acquisition after disposal closes its lease`() {
        val owner = MangaImageFileOwner()
        var closes = 0
        owner.close()
        try {
            owner.attach(File("page.jpg"), Closeable { closes++ })
            error("Disposed owner accepted image")
        } catch (_: CancellationException) {
            assertEquals(1, closes)
        }
    }

    @Test
    fun `abandoned composition closes request lease once and duplicate acquisition is released`() {
        val owner = MangaImageFileOwner()
        var closes = 0
        val file = File("page.jpg")
        owner.attach(file, Closeable { closes++ })
        owner.attach(file, Closeable { closes++ })
        assertEquals(1, closes)
        owner.onAbandoned()
        owner.close()
        assertEquals(2, closes)
    }
}
