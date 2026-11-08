package io.legado.app.data.repository.manga

import android.app.Application
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.MangaExportGateway
import io.legado.app.domain.model.manga.MangaExportFormat
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.model.localBook.LocalMangaFiles
import io.legado.app.utils.FileDoc
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.list
import io.legado.app.utils.openOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class MangaExportRepository(
    private val application: Application,
    private val database: AppDatabase,
) : MangaExportGateway {
    override suspend fun export(
        bookUrl: String,
        destination: String,
        format: MangaExportFormat,
        fileNameTemplate: String?,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val book = database.bookDao.getBook(bookUrl)
            ?.takeIf { it.isImage } ?: throw IOException(application.getString(R.string.no_book))
        // An export owns its local archive extraction, independently of the reader's lifetime.
        val localCache = File(application.cacheDir, "manga-export-${UUID.randomUUID()}")
        try {
            LocalMangaLoader(localCache).use { local ->
                val chapters = if (book.isLocal) {
                    LocalMangaFiles.chapters(book)
                } else database.bookChapterDao.getChapterList(bookUrl).sortedBy { it.index }
                if (chapters.isEmpty()) throw IOException(application.getString(R.string.feature_manga_export_empty))

                val pages = chapters.flatMap { chapter ->
                    currentCoroutineContext().ensureActive()
                    val urls = if (book.isLocal) local.load(book, chapter).pages.map { it.imageUrl }
                    else {
                        val content = BookHelp.getContent(book, chapter)
                        if (content == null && !chapter.isVolume) {
                            throw IOException(application.getString(R.string.feature_manga_export_chapter_missing, chapter.title))
                        }
                        BookHelp.flowImages(chapter, content.orEmpty()).toList()
                    }
                    if (urls.isEmpty() && !chapter.isVolume) {
                        throw IOException(application.getString(R.string.feature_manga_export_chapter_missing, chapter.title))
                    }
                    urls.mapIndexed { index, url ->
                        MangaExportPage(chapter.index + 1, chapter.title, index + 1) {
                            if (!book.isLocal) BookHelp.openCachedImage(book, chapter, url)
                            else application.contentResolver.openInputStream(url.toUri())
                                ?: throw IOException("Cannot open local comic image")
                        }
                    }
                }
                if (pages.isEmpty()) throw IOException(application.getString(R.string.feature_manga_export_empty))
                try {
                    // No destination is created until every requested page has passed preflight.
                    val prepared = MangaExportWriter.prepare(pages)
                    currentCoroutineContext().ensureActive()
                    val directory = FileDoc.fromDir(destination)
                    val base = MangaExportWriter.safeName(
                        book.getExportFileName("cbz", fileNameTemplate).removeSuffix(".cbz"),
                    )
                    val target = createTarget(directory, base, format)
                    try {
                        onProgress(0, prepared.size)
                        if (format == MangaExportFormat.Cbz) {
                            MangaExportWriter.writeCbz(prepared, target.openOutputStream().getOrThrow(), onProgress)
                        } else {
                            val chapterDirectories = mutableMapOf<String, FileDoc>()
                            MangaExportWriter.writeFolder(prepared, { path ->
                                val chapter = path.substringBefore('/')
                                val parent = chapterDirectories.getOrPut(chapter) { target.createFolderIfNotExist(chapter) }
                                parent.createFileIfNotExist(path.substringAfter('/')).openOutputStream().getOrThrow()
                            }, onProgress)
                        }
                        currentCoroutineContext().ensureActive()
                    } catch (error: Throwable) {
                        // Only remove this export's newly created target, including on cancellation.
                        runCatching { target.delete() }.exceptionOrNull()?.let(error::addSuppressed)
                        throw error
                    }
                    target.name
                } catch (error: MangaExportPageException) {
                    throw IOException(application.getString(
                        R.string.feature_manga_export_page_missing,
                        error.page.chapterTitle,
                        error.page.pageNumber,
                    ), error)
                }
            }
        } finally {
            localCache.deleteRecursively()
        }
    }

    private fun createTarget(directory: FileDoc, base: String, format: MangaExportFormat): FileDoc {
        val suffix = if (format == MangaExportFormat.Cbz) ".cbz" else ""
        val existing = directory.list().orEmpty().map { it.name }.toMutableSet()
        val name = MangaExportWriter.uniqueName(base, suffix, existing)
        directory.asDocumentFile()?.let { root ->
            // Create a new document, never reuse a pre-existing document returned by a lookup.
            val created = if (format == MangaExportFormat.Cbz) root.createFile("application/vnd.comicbook+zip", name)
            else root.createDirectory(name)
            return FileDoc.fromDocumentFile(created ?: throw IOException(application.getString(R.string.feature_manga_export_create_failed)))
        }
        val root = directory.asFile() ?: throw IOException(application.getString(R.string.feature_manga_export_create_failed))
        var candidate = name
        while (true) {
            val target = File(root, candidate)
            val created = if (format == MangaExportFormat.Cbz) target.createNewFile() else target.mkdir()
            if (created) return FileDoc.fromFile(target)
            if (!target.exists()) throw IOException(application.getString(R.string.feature_manga_export_create_failed))
            existing += candidate
            candidate = MangaExportWriter.uniqueName(base, suffix, existing)
        }
    }
}
