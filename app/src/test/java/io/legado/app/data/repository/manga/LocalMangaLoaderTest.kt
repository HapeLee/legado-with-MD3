package io.legado.app.data.repository.manga

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files

class LocalMangaLoaderTest {

    @Test
    fun `single page chapters retain their directory boundaries`() {
        val root = Files.createTempDirectory("local-manga-single-page").toFile()
        val cache = Files.createTempDirectory("local-manga-cache").toFile()
        try {
            root.resolve("0001_First/0001.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            root.resolve("0002_Second/0001.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
            val book = Book(type = BookType.local or BookType.image, bookUrl = root.path, name = "Comic", originName = root.name)
            LocalMangaLoader(cache).use { loader ->
                val chapters = loader.chapters(book)
                assertEquals(listOf("0001_First", "0002_Second"), chapters.map { it.title })
                assertEquals(listOf(1, 1), chapters.map { loader.load(book, it).pages.size })
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
                val chapters = loader.chapters(book)
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
