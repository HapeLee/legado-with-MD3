package io.legado.app.data.repository.manga

import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.MD5Utils
import splitties.init.appCtx
import java.io.File

/** Dedicated local comic metadata loader for image directories and comic archives. */
internal class LocalMangaLoader(private val cacheRoot: File) : AutoCloseable {
    private val extractedRoots = linkedSetOf<File>()
    private val extractedBooks = mutableMapOf<String, File>()

    fun supports(book: Book): Boolean = book.originName.endsWith(".cbz", true) ||
            book.originName.endsWith(".zip", true) || localDirectory(book) != null

    fun chapters(book: Book): List<BookChapter> =
        imageGroups(book).keys.mapIndexed { index, title ->
            BookChapter(
                url = "local-manga://chapter/$index",
                title = title,
                bookUrl = book.bookUrl,
                index = index,
            )
        }

    fun load(book: Book, chapter: BookChapter): MangaChapterContent {
        val entry = imageGroups(book).entries.elementAtOrNull(chapter.index)
            ?: throw NoStackTraceException("Local comic chapter is missing")
        val urls = entry.value.map(ImageEntry::url)
        return MangaChapterContent(
            chapter.index,
            chapter.title,
            chapter.url,
            urls.mapIndexed { index, url -> MangaPageContent(url, index, urls.size) },
            false,
        )
    }

    private fun imageGroups(book: Book): Map<String, List<ImageEntry>> {
        val images = localDirectory(book)?.let(::directoryImages) ?: archiveImages(book)
        if (images.isEmpty()) throw NoStackTraceException("Local comic contains no images")
        val sorted = images.sortedWith(compareBy(AlphanumComparator) { it.path })
        val grouped = sorted.groupBy { image ->
            image.path.substringBeforeLast('/', "").takeIf { it.isNotBlank() } ?: book.name
        }
        return if (grouped.size == sorted.size) mapOf(book.name to sorted) else grouped
    }

    private fun archiveImages(book: Book): List<ImageEntry> {
        val root = extractedBooks.getOrPut(book.bookUrl) { extract(book) }
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            .map { ImageEntry(it.relativeTo(root).invariantSeparatorsPath, it.toURI().toString()) }
            .toList()
    }

    private fun extract(book: Book): File {
        val base = File(cacheRoot, MD5Utils.md5Encode16(book.bookUrl)).apply { mkdirs() }
        extractedRoots += base
        val files = ArchiveUtils.deCompress(book.bookUrl, base.path) { path ->
            path.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
        }
        return files.map(File::getParentFile).filterNotNull().reduceOrNull(::commonParent) ?: base
    }

    private fun localDirectory(book: Book): Any? {
        return if (book.bookUrl.startsWith("content://")) {
            val uri = book.bookUrl.toUri()
            DocumentFile.fromTreeUri(appCtx, uri)?.takeIf { it.isDirectory }
        } else {
            val path = if (book.bookUrl.startsWith("file:")) {
                java.net.URI(book.bookUrl).path
            } else book.bookUrl
            File(path).takeIf { it.isDirectory }
        }
    }

    private fun directoryImages(root: Any): List<ImageEntry> = when (root) {
        is File -> root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            .map { ImageEntry(it.relativeTo(root).invariantSeparatorsPath, it.toURI().toString()) }
            .toList()

        is DocumentFile -> documentImages(root, "")
        else -> emptyList()
    }

    private fun documentImages(directory: DocumentFile, prefix: String): List<ImageEntry> =
        directory.listFiles().flatMap { child ->
            val path =
                if (prefix.isEmpty()) child.name.orEmpty() else "$prefix/${child.name.orEmpty()}"
            when {
                child.isDirectory -> documentImages(child, path)
                child.isFile && child.name.orEmpty().substringAfterLast('.', "")
                    .lowercase() in IMAGE_EXTENSIONS ->
                    listOf(ImageEntry(path, child.uri.toString()))

                else -> emptyList()
            }
        }

    private fun commonParent(first: File, second: File): File {
        var candidate: File? = first
        while (candidate != null && !second.toPath().startsWith(candidate.toPath())) candidate =
            candidate.parentFile
        return candidate ?: first
    }

    override fun close() {
        extractedRoots.forEach { it.deleteRecursively() }
        extractedRoots.clear()
        extractedBooks.clear()
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    }

    private data class ImageEntry(val path: String, val url: String)
}
