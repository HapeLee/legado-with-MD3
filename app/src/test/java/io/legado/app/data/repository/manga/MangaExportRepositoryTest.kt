package io.legado.app.data.repository.manga

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.manga.MangaExportFormat
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaExportRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MangaExportRepository
    private lateinit var root: File
    private lateinit var destination: File
    private lateinit var book: Book
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jCYkAAAAASUVORK5CYII=",
    )

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = MangaExportRepository(app, database)
        root = Files.createTempDirectory("manga-export-source").toFile()
        destination = Files.createTempDirectory("manga-export-target").toFile()
        root.resolve("Chapter/1.jpg").apply { parentFile!!.mkdirs(); writeBytes(png) }
        root.resolve("Chapter/2.jpg").writeBytes(png)
        book = Book(type = BookType.local or BookType.image, bookUrl = root.path, name = "Comic", originName = root.name)
        database.bookDao.insert(book)
    }

    @After
    fun cleanup() {
        database.close()
        root.deleteRecursively()
        destination.deleteRecursively()
    }

    @Test
    fun `exports both formats preserves actual encoding and keeps existing exports`() = runBlocking {
        val folder = repository.export(book.bookUrl, destination.toURI().toString(), MangaExportFormat.Folder, "Comic") { _, _ -> }
        assertEquals("Comic", folder)
        assertArrayEquals(png, destination.resolve("$folder/0001_Chapter/0001.png").readBytes())
        val cbz = repository.export(book.bookUrl, destination.toURI().toString(), MangaExportFormat.Cbz, "Comic") { _, _ -> }
        val second = repository.export(book.bookUrl, destination.toURI().toString(), MangaExportFormat.Cbz, "Comic") { _, _ -> }
        assertEquals("Comic.cbz", cbz)
        assertEquals("Comic (1).cbz", second)
        ZipFile(destination.resolve(cbz)).use { zip ->
            assertEquals(2, zip.size())
            assertArrayEquals(png, zip.getInputStream(zip.getEntry("0001_Chapter/0001.png")).use { it.readBytes() })
        }
    }

    @Test
    fun `missing source page creates no output and never requests a book source`() = runBlocking {
        // An online comic with a cached TOC but no chapter body must fail offline.
        val online = Book(type = BookType.image, bookUrl = "https://example.invalid/comic", name = "Offline")
        database.bookDao.insert(online)
        database.bookChapterDao.insert(BookChapter(
            bookUrl = online.bookUrl, url = "https://example.invalid/chapter", title = "Missing chapter",
        ))
        try {
            repository.export(online.bookUrl, destination.toURI().toString(), MangaExportFormat.Cbz, "Comic") { _, _ -> }
            error("Expected missing chapter failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("Missing chapter"))
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `online cached originals keep repeated URLs and fail if a required image disappears`() = runBlocking {
        val online = Book(type = BookType.image, bookUrl = "https://example.invalid/cached", name = "Cached originals")
        val chapter = BookChapter(bookUrl = online.bookUrl, url = "https://example.invalid/chapter", title = "Pages")
        database.bookDao.insert(online)
        database.bookChapterDao.insert(chapter)
        try {
            BookHelp.saveText(online, chapter, "<img src=\"1.jpg\">\n<img src=\"1.jpg\">\n<img src=\"2.jpg\">", notifyContentChange = false)
            BookHelp.writeImage(online, chapter, "https://example.invalid/1.jpg", png)
            BookHelp.writeImage(online, chapter, "https://example.invalid/2.jpg", png)
            val name = repository.export(online.bookUrl, destination.toURI().toString(), MangaExportFormat.Cbz, "Cached") { _, _ -> }
            ZipFile(destination.resolve(name)).use { zip ->
                assertEquals(listOf("0001_Pages/0001.png", "0001_Pages/0002.png", "0001_Pages/0003.png"),
                    zip.entries().asSequence().map { it.name }.toList())
            }
            BookHelp.getImage(online, chapter, "https://example.invalid/2.jpg").delete()
            try {
                repository.export(online.bookUrl, destination.toURI().toString(), MangaExportFormat.Cbz, "Cached") { _, _ -> }
                error("Expected missing image failure")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("3"))
                assertEquals(listOf(name), destination.listFiles().orEmpty().map { it.name })
            }
        } finally {
            BookHelp.clearCache(online)
        }
    }

    @Test
    fun `invalid images fail before creating a destination`() = runBlocking {
        root.resolve("Chapter/2.jpg").writeText("not an image")
        try {
            repository.export(book.bookUrl, destination.toURI().toString(), MangaExportFormat.Folder, "Comic") { _, _ -> }
            error("Expected invalid page failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("2"))
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `cancellation deletes only this exports partial output for both formats`() = runBlocking {
        destination.resolve("Existing.cbz").writeBytes(png)
        for (format in MangaExportFormat.entries) {
            try {
                repository.export(book.bookUrl, destination.toURI().toString(), format, "Comic") { completed, _ ->
                    if (completed == 1) throw CancellationException("cancel export")
                }
                error("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(listOf("Existing.cbz"), destination.listFiles().orEmpty().map { it.name })
                assertArrayEquals(png, destination.resolve("Existing.cbz").readBytes())
            }
        }
    }
}
