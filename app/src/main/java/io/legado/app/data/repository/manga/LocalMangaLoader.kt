package io.legado.app.data.repository.manga

import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaPageContent
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
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
            chapter.getFileName().substringBeforeLast('.'),
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

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

        private fun localDirectory(book: Book): Any? {
            if (book.bookUrl.startsWith("content://")) {
                return runCatching {
                    val uri = book.bookUrl.toUri()
                    // 子目录形态 content://…/tree/<root>/document/<docId>：
                    // DocumentFile.fromTreeUri 只按 treeDocumentId 解析，会退化成树根，
                    // 这里显式按 documentId 重建目录 URI（与 FileDoc.asDocumentFile 一致）。
                    val segments = uri.pathSegments
                    val isSubDirectory = segments.firstOrNull() == "tree" &&
                        segments.size >= 4 &&
                        segments[segments.size - 2] == "document"
                    val documentFile = if (isSubDirectory) {
                        val directoryUri = DocumentsContract.buildDocumentUriUsingTree(
                            uri,
                            DocumentsContract.getDocumentId(uri),
                        )
                        FileDoc("", true, 0, 0, directoryUri).asDocumentFile()
                    } else {
                        DocumentFile.fromTreeUri(appCtx, uri)
                    }
                    documentFile?.takeIf { it.isDirectory }
                }.getOrNull()
            }
            val path = if (book.bookUrl.startsWith("file:")) {
                java.net.URI(book.bookUrl).path
            } else book.bookUrl
            return File(path).takeIf { it.isDirectory }
        }

        /**
         * 目录型本地书（图片目录）存在性判断，走 DocumentFile/File 元数据，不开任何流。
         * 必须 runCatching：SAF 单文档 content:// URI（document/xxx）会让
         * [DocumentFile.fromTreeUri] 抛 IllegalArgumentException，而本函数会用于所有本地书。
         */
        fun directoryExists(book: Book): Boolean =
            runCatching { localDirectory(book) }.getOrNull() != null

    }

    private data class ImageEntry(val path: String, val url: String)
}
