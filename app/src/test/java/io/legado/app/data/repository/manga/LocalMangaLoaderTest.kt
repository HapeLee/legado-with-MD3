package io.legado.app.data.repository.manga

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import io.legado.app.help.book.isLocalManga
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.LocalMangaFiles
import io.legado.app.model.localBook.groupLocalMangaPages
import io.legado.app.model.localBook.relativeMangaArchivePaths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalMangaLoaderTest {

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
