package io.legado.app.data.repository

import io.legado.app.constant.AppLog
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.CacheMaintenanceRules
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class BookCacheMaintenanceRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
) : BookCacheMaintenanceGateway {

    private companion object {
        // 与 BookHelp 里的 cacheImageFolderName 一致：书级图片根目录不隶属任何单章。
        const val BOOK_IMAGE_FOLDER_NAME = "images"
        const val CHAPTER_CONTENT_SUFFIX = ".nb"
    }

    override suspend fun scanOrphanedChapters(
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): List<OrphanedChapterGroup> = withContext(Dispatchers.IO) {
        val books = bookDao.all.filterNot { it.isLocalTxt }
        val busyUrls = busyBookUrls()
        val groups = mutableListOf<OrphanedChapterGroup>()
        val total = books.size
        books.forEachIndexed { index, book ->
            coroutineContext.ensureActive()
            onProgress(index + 1, total)
            if (book.bookUrl in busyUrls) return@forEachIndexed
            val orphans = collectOrphanEntries(book) ?: return@forEachIndexed
            if (orphans.isEmpty()) return@forEachIndexed
            groups += OrphanedChapterGroup(
                bookUrl = book.bookUrl,
                bookName = book.name,
                entryNames = orphans.map { it.name },
                bytes = orphans.sumOf(::lengthOf),
                containsImages = orphans.any { File(it, BOOK_IMAGE_FOLDER_NAME).exists() },
            )
        }
        groups
    }

    override suspend fun deleteOrphanedChapters(
        bookUrl: String,
        entryNames: Set<String>,
    ): DeletedChapterCache = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookUrl) ?: return@withContext DeletedChapterCache(0, 0L)
        if (book.bookUrl in busyBookUrls()) return@withContext DeletedChapterCache(0, 0L)
        val bookDir = bookDir(book)
        var deleted = 0
        var freed = 0L
        entryNames.forEach { name ->
            coroutineContext.ensureActive()
            val entry = File(bookDir, name)
            if (!entry.exists()) return@forEach
            val size = lengthOf(entry)
            if (FileUtils.delete(entry, true)) {
                deleted++
                freed += size
            }
        }
        DeletedChapterCache(deleted, freed)
    }

    override suspend fun scanRecountTargets(
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): List<WordCountRecountTarget> = withContext(Dispatchers.IO) {
        val books = bookDao.all.filter { it.isOnLineTxt && it.wordCount.isNullOrBlank() }
        val targets = mutableListOf<WordCountRecountTarget>()
        val total = books.size
        books.forEachIndexed { index, book ->
            coroutineContext.ensureActive()
            onProgress(index + 1, total)
            val (recountable, unsolved) = surveyWordCounts(book)
            if (recountable > 0) {
                targets += WordCountRecountTarget(
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    recountableChapters = recountable,
                    unsolvedChapters = unsolved,
                )
            }
        }
        targets
    }

    override suspend fun recountWordCounts(bookUrls: Set<String>): RecountOutcome =
        withContext(Dispatchers.IO) {
            var updatedChapters = 0
            var updatedBooks = 0
            var skippedChapters = 0
            bookUrls.forEach { bookUrl ->
                coroutineContext.ensureActive()
                val book = bookDao.getBook(bookUrl) ?: return@forEach
                if (!book.isOnLineTxt) return@forEach
                val result = recountBook(book)
                updatedChapters += result.recounted
                skippedChapters += result.unsolved
                if (result.bookUpdated) updatedBooks++
            }
            RecountOutcome(
                updatedChapters = updatedChapters,
                updatedBooks = updatedBooks,
                skippedChapters = skippedChapters,
            )
        }

    /**
     * 一章一节地补字数。
     *
     * 只有在该书**每个**非卷章节都拿到了可信字数时才回写整书字数，否则缓存没覆盖到的部分会让
     * 合计系统性偏小，那样比保持为空更糟。
     */
    private fun recountBook(book: Book): RecountResult {
        val chapters = bookChapterDao.getChapterList(book.bookUrl).filterNot { it.isVolume }
        val resolved = HashMap<String, Int>(chapters.size)
        var recounted = 0
        var unsolved = 0
        for (chapter in chapters) {
            val stored = CacheMaintenanceRules.parseStoredWordCount(chapter.wordCount)
            if (stored != null && stored > 0) {
                resolved[chapter.url] = stored
                continue
            }
            val length = HtmlFormatter.countReadableTextLength(BookHelp.getContent(book, chapter))
            if (length <= 0) {
                unsolved++
                continue
            }
            bookChapterDao.upWordCount(book.bookUrl, chapter.url, StringUtils.wordCountFormat(length))
            resolved[chapter.url] = length
            recounted++
        }
        val bookUpdated = recounted > 0 && unsolved == 0
        if (bookUpdated) {
            val total = CacheMaintenanceRules.sumWordCounts(resolved.values)
            bookDao.upWordCount(book.bookUrl, StringUtils.wordCountFormat(total))
        }
        return RecountResult(recounted, unsolved, bookUpdated)
    }

    /** [recountable] 待重算的章节数，[unsolved] 拿不到字数的章节数。 */
    private fun surveyWordCounts(book: Book): RecountStats {
        val chapters = bookChapterDao.getChapterList(book.bookUrl).filterNot { it.isVolume }
        var recountable = 0
        var unsolved = 0
        for (chapter in chapters) {
            val stored = CacheMaintenanceRules.parseStoredWordCount(chapter.wordCount)
            if (stored != null && stored > 0) continue
            if (BookHelp.hasContent(book, chapter)) recountable++ else unsolved++
        }
        return RecountStats(recountable, unsolved)
    }

    /**
     * 取出该书缓存目录下查无对应章节的条目。
     *
     * 返回 null 表示这本书应当跳过（报不出章节列表），空的 List 表示没有孤儿。
     */
    private fun collectOrphanEntries(book: Book): List<File>? {
        val legalSubjects = try {
            bookChapterDao.getChapterList(book.bookUrl)
                .mapTo(hashSetOf()) { it.getFileName().substringBeforeLast('.') }
        } catch (error: Exception) {
            AppLog.put("扫描孤立章节缓存失败，跳过该书: ${book.name}", error)
            return null
        }
        val bookDir = bookDir(book)
        val entries = bookDir.listFiles() ?: return null
        val candidates = entries.filter { it.isDirectory || it.name.endsWith(CHAPTER_CONTENT_SUFFIX) }
        val orphanNames = CacheMaintenanceRules.findOrphanSubjects(
            cachedEntryNames = candidates.map { it.name },
            legalSubjects = legalSubjects,
            bookImageFolderName = BOOK_IMAGE_FOLDER_NAME,
        )
        if (orphanNames.isEmpty()) return emptyList()
        val nameToEntry = entries.associateBy { it.name }
        return orphanNames.mapNotNull { nameToEntry[it] }
    }

    /**
     * 正在下载或正在阅读的书。这些书的缓存目录可能被并发写入，
     * 既不能在扫描时把它们识别成孤儿，也不能在执行阶段删除。
     */
    private fun busyBookUrls(): Set<String> {
        val downloading = CacheBook.downloadStateFlow.value.books.values
            .filter {
                it.waitingCount > 0 ||
                    it.runningIndices.isNotEmpty() ||
                    it.pausedIndices.isNotEmpty() ||
                    it.failedIndices.isNotEmpty()
            }
            .mapTo(mutableSetOf()) { it.bookUrl }
        ReadBook.book?.bookUrl?.let { downloading += it }
        return downloading
    }

    private fun bookDir(book: Book): File = File(BookHelp.cachePath, book.getFolderName())

    private fun lengthOf(entry: File): Long {
        if (entry.isFile) return entry.length()
        return entry.listFiles()?.sumOf(::lengthOf) ?: 0L
    }

    private data class RecountResult(
        val recounted: Int,
        val unsolved: Int,
        val bookUpdated: Boolean,
    )

    private data class RecountStats(
        val recountable: Int,
        val unsolved: Int,
    )
}
