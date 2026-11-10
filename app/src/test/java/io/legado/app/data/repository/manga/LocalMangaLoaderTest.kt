package io.legado.app.data.repository.manga

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import io.legado.app.help.book.isLocalManga
import io.legado.app.model.localBook.LEGACY_FLAT_MANGA_CHAPTER_URL
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.LocalMangaFiles
import io.legado.app.model.localBook.groupLocalMangaPages
import io.legado.app.model.localBook.relativeMangaArchivePaths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.file.Files

class LocalMangaLoaderTest {

    @Test
    fun `local manga page URLs retain absolute paths with spaces and non-ASCII characters`() {
        val root = Files.createTempDirectory("local-manga-test").toFile()
        val cache = Files.createTempDirectory("local-manga-cache").toFile()
        try {
            val image = root.resolve("第 1 章/001.webp")
            image.parentFile!!.mkdirs()
            image.writeBytes(byteArrayOf(1))
            val book = Book(type = BookType.local or BookType.image, bookUrl = root.absolutePath)
            val chapter = LocalMangaFiles.chapters(book).single()
            val url = LocalMangaLoader(cache).use { it.load(book, chapter).pages.single().imageUrl }

            assertTrue(url.startsWith("file:///"))
            assertEquals(image.absolutePath, URI(url).path)
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }

    @Test
    fun `only explicitly classified zip files open as manga`() {
        val book = Book(
            type = BookType.local or BookType.text,
            bookUrl = "/library/book.zip",
            originName = "book.zip",
        )
        assertFalse(book.isLocalManga)
        assertTrue(book.copy(type = BookType.local or BookType.image).isLocalManga)
        assertTrue(book.copy(originName = "book.cbz").isLocalManga)
    }

    @Test
    fun `local manga never returns a text chapter`() {
        val book = Book(type = BookType.local or BookType.image, bookUrl = "/library/comic")
        assertNull(LocalBook.getContent(book, BookChapter(bookUrl = book.bookUrl)))
    }

    @Test
    fun `archive chapter paths strip only a shared parent`() {
        val paths = relativeMangaArchivePaths(
            listOf("Comic/Chapter 10/1.jpg", "./Comic/Chapter 2/2.png", "Comic/Chapter 2/1.png"),
        )
        val chapters = groupLocalMangaPages("Comic", paths) { it }
        assertEquals(listOf("Chapter 2", "Chapter 10"), chapters.keys.toList())
        assertEquals(listOf("Chapter 2/1.png", "Chapter 2/2.png"), chapters["Chapter 2"])
        assertEquals(listOf("page.jpg", "Bonus/extra.jpg"), relativeMangaArchivePaths(
            listOf("Comic/page.jpg", "Comic/Bonus/extra.jpg"),
        ))
    }

    @Test
    fun `single page chapters retain their directory boundaries`() {
        val root = Files.createTempDirectory("local-manga-single-page").toFile()
        val cache = Files.createTempDirectory("local-manga-cache").toFile()
        try {
            root.resolve("0001_First/0001.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            root.resolve("0002_Second/0001.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
            val book = Book(type = BookType.local or BookType.image, bookUrl = root.path, name = "Comic", originName = root.name)
            LocalMangaLoader(cache).use { loader ->
                val chapters = LocalMangaFiles.chapters(book)
                assertEquals(listOf("0001_First", "0002_Second"), chapters.map { it.title })
                assertEquals(listOf(1, 1), chapters.map { loader.load(book, it).pages.size })
                val oldBook = book.copy(totalChapterNum = 1)
                val oldChapter = chapters.first().copy(url = LEGACY_FLAT_MANGA_CHAPTER_URL, title = book.name)
                assertEquals(2, loader.load(oldBook, oldChapter).pages.size)
                assertEquals(1, loader.load(oldBook, chapters.first()).pages.size)
            }
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }

    @Test
    fun `directory subfolders become chapters and pages use natural order`() {
        val root = Files.createTempDirectory("local-manga-test").toFile()
        val cache = Files.createTempDirectory("local-manga-cache").toFile()
        try {
            val chapterOne = root.resolve("Chapter 1").apply { mkdirs() }
            chapterOne.resolve("10.jpg").writeBytes(byteArrayOf(1))
            chapterOne.resolve("2.jpg").writeBytes(byteArrayOf(2))
            val chapterTwo = root.resolve("Chapter 2").apply { mkdirs() }
            chapterTwo.resolve("1.png").writeBytes(byteArrayOf(3))
            val book = Book(
                type = BookType.local or BookType.image,
                bookUrl = root.absolutePath,
                name = "Comic",
                originName = root.name,
            )

            LocalMangaLoader(cache).use { loader ->
                val chapters = LocalMangaFiles.chapters(book)
                assertTrue(cache.listFiles().orEmpty().isEmpty())
                assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
                val pages = loader.load(book, chapters.first()).pages
                assertEquals(
                    listOf("2.jpg", "10.jpg"),
                    pages.map { it.imageUrl.substringAfterLast('/') })
                assertFalse(loader.load(book, chapters.last()).pages.isEmpty())
            }
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }
}
