package io.legado.app.help.coil

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MangaImageFileOwnerTest {
    @Test
    fun `preview and background share acquisition but changed or missing file is reacquired`() =
        runBlocking {
            val owner = MangaImageFileOwner()
            val file = File.createTempFile("manga-owner", ".png")
            var acquisitions = 0
            var closes = 0
            val fetch: suspend () -> Pair<File, Closeable> = {
                acquisitions++
                file.writeText("original")
                file to Closeable { closes++ }
            }
            try {
                owner.acquire("page", fetch)
                owner.acquire("page", fetch)
                assertEquals(1, acquisitions)
                file.writeText("changed size")
                owner.acquire("page", fetch)
                assertEquals(2, acquisitions)
                file.delete()
                owner.acquire("page", fetch)
                assertEquals(3, acquisitions)
                owner.close()
                assertEquals(3, closes)
                try {
                    owner.acquire("page", fetch)
                    error("Disposed owner accepted image")
                } catch (_: CancellationException) {
                    assertEquals(3, acquisitions)
                }
            } finally {
                owner.close()
                file.delete()
            }
        }

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
