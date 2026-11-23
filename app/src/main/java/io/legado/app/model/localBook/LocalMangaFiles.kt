package io.legado.app.model.localBook

import android.net.Uri
import android.provider.DocumentsContract
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.LocalMangaDirectoryEmptyException
import io.legado.app.exception.LocalMangaDirectoryUnavailableException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.book.getLocalUri
import io.legado.app.help.book.isArchive
import io.legado.app.help.book.isImage
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileDoc
import io.legado.app.utils.compress.LibArchiveUtils
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openReadPfd
import io.legado.app.utils.treeChildren
import kotlin.io.path.invariantSeparatorsPathString
import splitties.init.appCtx
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

internal fun isLocalMangaImage(path: String): Boolean =
    path.substringAfterLast('/', path).substringAfterLast('.', "").lowercase() in
        localMangaImageExtensions

private val localMangaImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

/** Coil 需要 file URL 而不是裸路径；toURI() 同时负责空格与非 ASCII 字符的转义。 */
internal fun File.toLocalMangaImageUrl(): String =
    toURI().toASCIIString()

// A directory import has the image bit without the archive bit. Older CBZ records may have
// neither bit, so keep the explicit comic extension as a fallback.
internal val Book.isLocalMangaArchive: Boolean
    get() = isArchive || (!isImage && originName.endsWith(".cbz", ignoreCase = true))

internal const val LEGACY_FLAT_MANGA_CHAPTER_URL = "local-manga://chapter/0"

internal object LocalMangaFiles {
    fun chapters(book: Book): List<BookChapter> {
        val firstPages = mutableMapOf<String, String>()
        fun visit(path: String) {
            val title = localMangaChapterTitle(book.name, path)
            val firstPage = firstPages[title]
            if (firstPage == null || AlphanumComparator.compare(path, firstPage) < 0) {
                firstPages[title] = path
            }
        }
        if (book.isLocalMangaArchive) {
            val archive = FileDoc.fromUri(book.getLocalUri(), false)
            relativeMangaArchivePaths(archiveImageEntries(archive)).forEach(::visit)
        } else {
            visitDirectory(book) { path, _ -> visit(path) }
        }
        if (firstPages.isEmpty()) throw emptyMangaToc()
        val titles = firstPages.entries.sortedWith(compareBy(AlphanumComparator) { it.value }).map { it.key }
        return titles.mapIndexed { index, title ->
            BookChapter(
                url = "local-manga://chapter/v2/$index",
                title = title,
                bookUrl = book.bookUrl,
                index = index,
            )
        }
    }

    /**
     * CBZ/ZIP 漫画没有内嵌封面元数据：优先取包内 `cover.<图片扩展名>`，
     * 找不到再回退到自然序第一张图。落盘位置与 epub/mobi 的本地封面一致。
     * 用户手动设置过封面（customCoverUrl）时不生成，避免覆盖自定义封面。
     */
    fun upBookCover(book: Book) {
        if (!book.isLocalMangaArchive || !book.customCoverUrl.isNullOrEmpty()) return
        val coverPath = LocalBook.getCoverPath(book)
        val coverFile = File(coverPath)
        if (!coverFile.isFile || coverFile.length() == 0L) {
            val written = try {
                writeArchiveCover(book, coverFile)
            } catch (error: Exception) {
                AppLog.put("生成本地漫画封面失败：${book.name}", error)
                false
            }
            if (!written) return
        }
        if (book.coverUrl.isNullOrEmpty()) {
            book.coverUrl = coverPath
        }
    }

    fun isMangaArchive(archive: FileDoc): Boolean {
        val entries = archive.openReadPfd().getOrThrow().use {
            LibArchiveUtils.getFilesName(it) { name ->
                name.matches(bookFileRegex) || isLocalMangaImage(name)
            }
        }
        // A supported ebook takes precedence over standalone cover art.
        return entries.isNotEmpty() && entries.none { it.matches(bookFileRegex) }
    }

    private fun archiveImageEntries(archive: FileDoc): List<String> =
        archive.openReadPfd().getOrThrow().use {
            LibArchiveUtils.getFilesName(it, ::isLocalMangaImage)
        }

    private fun writeArchiveCover(book: Book, coverFile: File): Boolean {
        val archive = FileDoc.fromUri(book.getLocalUri(), false)
        // distinctBy 保留首次出现，条目名仍是归档内的原始写法，供单条读取时精确匹配。
        val entries = archiveImageEntries(archive)
            .mapNotNull { name ->
                runCatching { Paths.get(name).normalize().invariantSeparatorsPathString }
                    .getOrNull()?.let { it to name }
            }
            .distinctBy { it.first }
        val chosen = pickMangaCoverEntry(entries.map { it.first }) ?: return false
        val entryName = entries.first { it.first == chosen }.second
        val bytes = archive.openInputStream().getOrThrow().use {
            LibArchiveUtils.getByteArrayContent(it, entryName)
        }
        if (bytes == null || bytes.isEmpty()) return false
        // 先写临时文件再改名：中途失败不会留下半张封面被下次 exists 判断当成已完成。
        val tempFile = File("${coverFile.path}.tmp")
        coverFile.parentFile?.mkdirs()
        tempFile.writeBytes(bytes)
        if (!tempFile.renameTo(coverFile)) {
            coverFile.delete()
            if (!tempFile.renameTo(coverFile)) {
                tempFile.delete()
                return false
            }
        }
        return coverFile.isFile && coverFile.length() > 0L
    }

    fun checkDirectory(book: Book) {
        withDirectoryAccess {
            book.getLocalUri(directory = true)
        }
    }

    // A chapter exists only if its directory contains images. Walk names and MIME types to keep
    // nested/flat chapter rules, and use one provider query per SAF directory.
    fun visitDirectory(book: Book, visit: (String, () -> String) -> Unit) {
        var foundImage = false
        val visitImage: (String, () -> String) -> Unit = { path, url ->
            foundImage = true
            visit(path, url)
        }
        withDirectoryAccess {
            val url = book.bookUrl
            val children = if (url.startsWith("content://")) null else {
                localFile(url).listFiles()
            }
            if (children == null) {
                visitResolvedDirectory(book.getLocalUri(directory = true), visitImage)
            } else {
                visitFiles(children, "", visitImage)
            }
        }
        if (!foundImage) {
            throw LocalMangaDirectoryEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
    }

    private inline fun withDirectoryAccess(action: () -> Unit) {
        try {
            action()
        } catch (error: SecurityException) {
            throw LocalMangaDirectoryUnavailableException(error)
        } catch (error: FileNotFoundException) {
            throw LocalMangaDirectoryUnavailableException(error)
        }
    }

    private fun visitResolvedDirectory(uri: Uri, visit: (String, () -> String) -> Unit) {
        if (uri.scheme == "content") {
            visitDocuments(uri, "", visit)
        } else {
            visitFiles(listDirectory(File(requireNotNull(uri.path))), "", visit)
        }
    }

    private fun visitFiles(
        children: Array<File>,
        prefix: String,
        visit: (String, () -> String) -> Unit,
    ) {
        children.forEach { child ->
            val path = prefix + child.name
            if (child.isDirectory && !Files.isSymbolicLink(child.toPath())) {
                visitFiles(listDirectory(child), "$path/", visit)
            } else if (isLocalMangaImage(child.name) && child.isFile) {
                visit(path) { child.toLocalMangaImageUrl() }
            }
        }
    }

    private fun visitDocuments(
        uri: Uri,
        prefix: String,
        visit: (String, () -> String) -> Unit,
    ) {
        val id = DocumentsContract.getDocumentId(uri)
        treeChildren(appCtx, uri, id, requireCursor = true).forEach { child ->
            val path = prefix + child.name
            if (child.isDir) {
                visitDocuments(child.uri, "$path/", visit)
            } else if (isLocalMangaImage(child.name)) {
                visit(path) { child.uri.toString() }
            }
        }
    }

    private fun listDirectory(directory: File): Array<File> {
        if (!directory.isDirectory) throw FileNotFoundException(directory.path)
        return directory.listFiles() ?: throw SecurityException(directory.path)
    }

    private fun localFile(url: String): File =
        if (url.startsWith("file:", ignoreCase = true)) File(URI(url)) else File(url)
}

/** Resolve the authorized document itself; a tree URI can point at a nested document. */
internal fun readableMangaDirectoryUri(uri: Uri): Uri {
    if (uri.scheme != "content") {
        val directory = File(requireNotNull(uri.path))
        if (!directory.isDirectory) throw FileNotFoundException(directory.path)
        if (!directory.canRead()) throw SecurityException(directory.path)
        return uri
    }
    val documentUri = if (DocumentsContract.isTreeUri(uri) && uri.pathSegments.size == 2) {
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    } else {
        uri
    }
    val error = try {
        checkMangaDocumentDirectory(documentUri)
        return documentUri
    } catch (error: SecurityException) {
        error
    } catch (error: FileNotFoundException) {
        error
    }

    // Reselecting this folder can grant it under a different tree URI.
    val documentId = DocumentsContract.getDocumentId(documentUri)
    for (permission in appCtx.contentResolver.persistedUriPermissions) {
        val tree = permission.uri
        if (!permission.isReadPermission || tree.authority != uri.authority ||
            !DocumentsContract.isTreeUri(tree)
        ) continue
        val candidate = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        if (candidate == documentUri) continue
        try {
            checkMangaDocumentDirectory(candidate)
            return candidate
        } catch (_: SecurityException) {
            // This tree does not grant access to the requested document.
        } catch (_: FileNotFoundException) {
            // This tree no longer contains the requested document.
        }
    }
    throw error
}

private fun checkMangaDocumentDirectory(uri: Uri) {
    val resolver = appCtx.contentResolver
    val mimeType = DocumentsContract.Document.COLUMN_MIME_TYPE
    val isDirectory = resolver.query(uri, arrayOf(mimeType), null, null, null)?.use { cursor ->
        cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
    } == true
    if (!isDirectory) throw FileNotFoundException(uri.toString())

    val documentId = DocumentsContract.getDocumentId(uri)
    val childrenUri = if (DocumentsContract.isTreeUri(uri)) {
        DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
    } else {
        DocumentsContract.buildChildDocumentsUri(requireNotNull(uri.authority), documentId)
    }
    resolver.query(
        childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null,
    )?.close() ?: throw FileNotFoundException(uri.toString())
}

/** Normalize the same common parent that the reader strips from extracted images. */
internal fun relativeMangaArchivePaths(entries: List<String>): List<String> {
    val paths = entries.map { Paths.get(it).normalize() }.distinct()
    if (paths.any { it.isAbsolute || it.startsWith("..") }) {
        throw SecurityException("Comic archive entry is outside its root")
    }
    var common = paths.firstOrNull()?.parent
    while (true) {
        val parent = common ?: break
        if (paths.all { it.startsWith(parent) }) break
        common = parent.parent
    }
    // 章节标题靠 '/' 切分；Path.toString() 在 Windows 上是反斜杠，会把整本书并成一章。
    return paths.map { (common?.relativize(it) ?: it).invariantSeparatorsPathString }
}

/**
 * 选封面条目：文件名为 `cover` 的图片优先（层数最浅，再自然序），
 * 否则回退自然序第一张——与章节排序同口径，保证封面就是阅读器第一页。
 */
internal fun pickMangaCoverEntry(relativePaths: List<String>): String? {
    val images = relativePaths.filter(::isLocalMangaImage)
    if (images.isEmpty()) return null
    val byDepthThenName = compareBy<String> { path -> path.count { it == '/' } }
        .then(AlphanumComparator)
    return images.filter(::isMangaCoverName).minWithOrNull(byDepthThenName)
        ?: images.minWithOrNull(AlphanumComparator)
}

private fun isMangaCoverName(path: String): Boolean {
    val name = path.substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return false
    return name.substring(0, dot).equals("cover", ignoreCase = true) &&
        name.substring(dot + 1).lowercase() in localMangaImageExtensions
}

internal fun <T> groupLocalMangaPages(
    bookName: String,
    pages: List<T>,
    path: (T) -> String,
): Map<String, List<T>> {
    if (pages.isEmpty()) throw emptyMangaToc()
    val sorted = pages.sortedWith(compareBy(AlphanumComparator, path))
    return sorted.groupBy { localMangaChapterTitle(bookName, path(it)) }
}

private fun localMangaChapterTitle(bookName: String, pagePath: String): String =
    pagePath.substringBeforeLast('/', "").ifBlank { bookName }

private fun emptyMangaToc(): TocEmptyException =
    TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
