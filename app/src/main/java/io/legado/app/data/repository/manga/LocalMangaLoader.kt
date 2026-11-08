package io.legado.app.data.repository.manga

import android.net.Uri
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.getLocalUri
import io.legado.app.model.localBook.LEGACY_FLAT_MANGA_CHAPTER_URL
import io.legado.app.model.localBook.LocalMangaFiles
import io.legado.app.model.localBook.groupLocalMangaPages
import io.legado.app.model.localBook.isLocalMangaArchive
import io.legado.app.model.localBook.isLocalMangaImage
import io.legado.app.model.localBook.toLocalMangaImageUrl
import io.legado.app.utils.ArchiveUtils
import java.io.File
import java.nio.file.Files

/** Owns the extracted images for this reader session. TOC loading uses metadata separately. */
internal class LocalMangaLoader(private val cacheRoot: File) : AutoCloseable {
    private val extractedBooks = mutableMapOf<String, ExtractedBook>()

    @Synchronized
    fun load(book: Book, chapter: BookChapter): MangaChapterContent {
        val images = if (book.isLocalMangaArchive) {
            val uri = book.getLocalUri()
            extractedBooks.getOrPut(book.bookUrl) { extract(uri) }.images
        } else buildList {
            LocalMangaFiles.visitDirectory(book) { path, url -> add(ImageEntry(path, url())) }
        }
        val groups = groupLocalMangaPages(book.name, images, ImageEntry::path)
        // Older catalogs collapsed folders that each contained a single page. Honor a saved
        // flat chapter's page offsets until its catalog is refreshed.
        val pages = if (book.totalChapterNum == 1 &&
            chapter.url == LEGACY_FLAT_MANGA_CHAPTER_URL && chapter.title == book.name &&
            groups.values.all { it.size == 1 }
        ) {
            groups.values.flatten()
        } else {
            groups.values.elementAtOrNull(chapter.index)
                ?: throw NoStackTraceException("Local comic chapter is missing")
        }
        val urls = pages.map(ImageEntry::url)
        return MangaChapterContent(
            chapter.index,
            chapter.title,
            chapter.url,
            urls.mapIndexed { index, url -> MangaPageContent(url, index, urls.size) },
            false,
            chapter.getFileName().substringBeforeLast('.'),
        )
    }

    private fun extract(uri: Uri): ExtractedBook {
        cacheRoot.mkdirs()
        val root = Files.createTempDirectory(cacheRoot.toPath(), "comic-").toFile()
        try {
            val files = ArchiveUtils.deCompress(uri, root.path, ::isLocalMangaImage)
                .map { it.canonicalFile }
                .distinct()
            val common = files.mapNotNull(File::getParentFile).reduceOrNull { first, second ->
                generateSequence(first, File::getParentFile).first {
                    second.toPath().startsWith(it.toPath())
                }
            } ?: root
            return ExtractedBook(root, files.map {
                ImageEntry(it.relativeTo(common).invariantSeparatorsPath, it.toLocalMangaImageUrl())
            })
        } catch (error: Exception) {
            root.deleteRecursively()
            throw error
        }
    }

    @Synchronized
    override fun close() {
        extractedBooks.values.forEach { it.root.deleteRecursively() }
        extractedBooks.clear()
    }

    private data class ExtractedBook(val root: File, val images: List<ImageEntry>)
    private data class ImageEntry(val path: String, val url: String)
}
