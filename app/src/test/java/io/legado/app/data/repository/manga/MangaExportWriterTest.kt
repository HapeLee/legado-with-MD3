package io.legado.app.data.repository.manga

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.model.localBook.LocalMangaFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipInputStream

class MangaExportWriterTest {
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jCYkAAAAASUVORK5CYII=",
    )

    @Test
    fun `folder and cbz preserve identical bytes page order and repeated images`() = runBlocking {
        val root = Files.createTempDirectory("manga-export").toFile()
        val cache = Files.createTempDirectory("manga-export-reader").toFile()
        try {
            val pages = listOf(
                MangaExportPage(1, "Chapter 10", 1) { png.inputStream() },
                MangaExportPage(1, "Chapter 10", 2) { png.inputStream() },
                MangaExportPage(2, "Chapter 2", 1) { png.inputStream() },
            )
            val prepared = MangaExportWriter.prepare(pages)
            val progress = mutableListOf<Pair<Int, Int>>()
            MangaExportWriter.writeFolder(prepared, { path ->
                root.resolve(path).apply { parentFile!!.mkdirs() }.outputStream()
            }) { completed, total -> progress += completed to total }
            assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)

            val output = ByteArrayOutputStream()
            MangaExportWriter.writeCbz(prepared, output) { _, _ -> }
            val archive = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    archive[entry.name] = zip.readBytes()
                }
            }
            assertEquals(
                listOf("0001_Chapter 10/0001.png", "0001_Chapter 10/0002.png", "0002_Chapter 2/0001.png"),
                archive.keys.toList(),
            )
            archive.forEach { (path, bytes) ->
                assertArrayEquals(png, bytes)
                assertArrayEquals(bytes, root.resolve(path).readBytes())
            }
            val book = Book(type = BookType.local or BookType.image, bookUrl = root.path, name = "Comic", originName = root.name)
            LocalMangaLoader(cache).use { loader ->
                val chapters = LocalMangaFiles.chapters(book)
                assertEquals(listOf("0001_Chapter 10", "0002_Chapter 2"), chapters.map { it.title })
                assertEquals(listOf(2, 1), chapters.map { loader.load(book, it).pages.size })
            }
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }

    @Test
    fun `unsafe titles cannot create nested paths and names are not overwritten`() = runBlocking {
        val page = MangaExportPage(1, "../../a\\b:*?", 1) { png.inputStream() }
        val path = MangaExportWriter.prepare(listOf(page)).single().path
        assertEquals(1, path.count { it == '/' })
        assertFalse(path.contains('\\'))
        assertTrue(path.endsWith("/0001.png"))
        assertEquals("Book (2).cbz", MangaExportWriter.uniqueName("Book", ".cbz", setOf("book.CBZ", "Book (1).cbz")))
        assertEquals("Book (1)", MangaExportWriter.uniqueName("Book", "", setOf("Book")))
    }

    @Test
    fun `preflight reports the exact missing or invalid page`() = runBlocking {
        for (open in listOf<() -> java.io.InputStream>(
            { throw IOException("missing") },
            { "<html>error</html>".byteInputStream() },
            { ByteArray(0).inputStream() },
        )) {
            val page = MangaExportPage(2, "Second chapter", 7, open)
            try {
                MangaExportWriter.prepare(listOf(page))
                error("Expected preflight failure")
            } catch (error: MangaExportPageException) {
                assertEquals(page, error.page)
            }
        }
    }

    @Test
    fun `streams close on destination failure and format changes are rejected`() = runBlocking {
        var closed = 0
        var changed = false
        val page = MangaExportPage(1, "Chapter", 1) {
            object : ByteArrayInputStream(if (changed) byteArrayOf(-1, -40, -1) else png) {
                override fun close() { closed++ }
            }
        }
        val prepared = MangaExportWriter.prepare(listOf(page))
        assertEquals(1, closed)
        try {
            MangaExportWriter.writeFolder(prepared, { throw IOException("storage full") }) { _, _ -> }
            error("Expected write failure")
        } catch (_: MangaExportPageException) {
            assertEquals(2, closed)
        }
        changed = true
        try {
            MangaExportWriter.writeFolder(prepared, { error("Must not create output for changed image") }) { _, _ -> }
            error("Expected changed-format failure")
        } catch (_: MangaExportPageException) {
            assertEquals(3, closed)
        }
    }
}
