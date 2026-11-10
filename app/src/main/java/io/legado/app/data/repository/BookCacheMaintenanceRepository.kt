package io.legado.app.data.repository

import io.legado.app.constant.AppLog
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.model.ChapterWordCountUpdate
import io.legado.app.domain.gateway.BookCacheMaintenanceGateway
import io.legado.app.domain.model.CacheMaintenanceRules
import io.legado.app.domain.model.DeletedChapterCache
import io.legado.app.domain.model.DeletionProgress
import io.legado.app.domain.model.OrphanDeleteRequest
import io.legado.app.domain.model.OrphanedChapterGroup
import io.legado.app.domain.model.RecountOutcome
import io.legado.app.domain.model.WordCountRecountTarget
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
        // Room 的 IN 占位上限是 999，按书分批查询章节表。
        const val SQL_BATCH = 200
        // 字数回写按批提交事务，避免单个超长事务锁库。
        const val WORD_COUNT_BATCH = 512
        // 长任务定期让出 IO 线程并执行取消检查，别让下载/预取一直排队。
        const val YIELD_BATCH = 64
    }

    override suspend fun scanOrphanedChapters(
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): List<OrphanedChapterGroup> = withContext(Dispatchers.IO) {
        val books = bookDao.all.filterNot { it.isLocalTxt }
        val busyUrls = busyBookUrls()
        // 一次批量把全部书的章节主体读出来，替代逐本 5000 次 SQL。
        val subjects = loadChapterSubjects(books.map { it.bookUrl })
        val groups = mutableListOf<OrphanedChapterGroup>()
        val total = books.size
        books.forEachIndexed { index, book ->
            coroutineContext.ensureActive()
            if (index % YIELD_BATCH == 0) yield()
            onProgress(index + 1, total)
            if (book.bookUrl in busyUrls) return@forEachIndexed
            // 章节表读取失败的书不能当成「没有孤儿」，跳过让下次重扫再处理。
            if (book.bookUrl in subjects.failedBookUrls) return@forEachIndexed
            val orphans = collectOrphanEntries(
                book.getFolderName(),
                subjects.byBook[book.bookUrl].orEmpty(),
            ) ?: return@forEachIndexed
            if (orphans.isEmpty()) return@forEachIndexed
            groups += OrphanedChapterGroup(
                bookUrl = book.bookUrl,
                bookName = book.name,
                entryNames = orphans.map { it.name },
                bytes = orphans.sumOf(::lengthOf),
            )
        }
        // UiState 标了 @Stable，渲染边界不能持有可变集合实例。
        groups.toList()
    }

    override suspend fun deleteOrphanedChapters(
        requests: List<OrphanDeleteRequest>,
        onProgress: (DeletionProgress) -> Unit,
    ): DeletedChapterCache = withContext(Dispatchers.IO) {
        val books = bookDao.all.associateBy { it.bookUrl }
        var deleted = 0
        var freed = 0L
        var skippedBooks = 0
        var skippedEntries = 0
        val total = requests.size
        var handled = 0

        // 分批刷新忙碌状态与章节表。批次开始时的一次性快照会让「排在不前几本」
        // 的书在目录刷新 / 开始下载之后仍按旧数据被删；缩短窗口即可避免，
        // 又不必与目录更新、正文写入做跨组件协调。
        requests.chunked(YIELD_BATCH).forEach { chunk ->
            coroutineContext.ensureActive()
            yield()
            val busyUrls = busyBookUrls()
            val subjects = loadChapterSubjects(chunk.map { it.bookUrl })
            chunk.forEach { request ->
                coroutineContext.ensureActive()
                handled++
                val book = books[request.bookUrl]
                val removedUrl = when {
                    book == null || request.bookUrl in busyUrls -> {
                        skippedBooks++
                        null
                    }

                    // 章节表读取失败 ≠ 孤儿已消失，留在列表供重试。
                    request.bookUrl in subjects.failedBookUrls -> {
                        skippedBooks++
                        null
                    }

                    else -> {
                        val folder = book.getFolderName()
                        val currentOrphans = collectOrphanEntries(
                            folder,
                            subjects.byBook[request.bookUrl].orEmpty(),
                        )
                        if (currentOrphans == null) {
                            skippedBooks++
                            null
                        } else {
                            val safeNames = request.entryNames.intersect(
                                currentOrphans.mapTo(hashSetOf()) { it.name }
                            )
                            if (safeNames.isEmpty()) {
                                // 扫描之后孤儿已消失或章节恢复有效：这本书没有孤儿了，从列表移除。
                                request.bookUrl
                            } else {
                                val stats = deleteEntries(
                                    File(BookHelp.cachePath, folder),
                                    safeNames,
                                )
                                deleted += stats.deleted
                                freed += stats.freed
                                skippedEntries += stats.skipped
                                if (stats.skipped == 0 && stats.deleted > 0) request.bookUrl else null
                            }
                        }
                    }
                }
                // 每本都把累计结果回传：任务被取消时 ViewModel 仍能写回已提交的部分。
                onProgress(
                    DeletionProgress(
                        handled = handled,
                        total = total,
                        accumulated = DeletedChapterCache(
                            deleted, freed, skippedBooks, skippedEntries,
                        ),
                        removedBookUrl = removedUrl,
                    )
                )
            }
        }
        DeletedChapterCache(deleted, freed, skippedBooks, skippedEntries)
    }

    override suspend fun scanRecountTargets(
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): List<WordCountRecountTarget> = withContext(Dispatchers.IO) {
        // 触发以章节为准：章节在列表里、有缓存、却没记录字数，就该重算——
        // 不以整书字数字段为前置条件（跨设备拷贝 / 早期版本都会产生这种书）。
        val books = bookDao.all.filter { it.isOnLineTxt }
        val targets = mutableListOf<WordCountRecountTarget>()
        val total = books.size
        var handled = 0
        books.chunked(SQL_BATCH).forEach { batch ->
            val chapterBatch = loadChaptersGrouped(batch.map { it.bookUrl })
            batch.forEach { book ->
                coroutineContext.ensureActive()
                handled++
                if (handled % YIELD_BATCH == 0) yield()
                onProgress(handled, total)
                // 查询失败 ≠ 没有章节，这本不进候选，留待下次重扫。
                if (chapterBatch.failed) return@forEach
                val stats = surveyWordCounts(
                    book,
                    chapterBatch.byBook[book.bookUrl].orEmpty(),
                )
                // 只在确实有章节需要重算时才列入；整书字数是本次重算的收尾产物，
                // 不是独立的触发场景。
                if (stats.recountable > 0) {
                    targets += WordCountRecountTarget(
                        bookUrl = book.bookUrl,
                        bookName = book.name,
                        recountableChapters = stats.recountable,
                        unsolvedChapters = stats.unsolved,
                    )
                }
            }
        }
        targets.toList()
    }

    override suspend fun recountWordCounts(
        bookUrls: Set<String>,
        onProgress: (scanned: Int, total: Int) -> Unit,
    ): RecountOutcome = withContext(Dispatchers.IO) {
        var updatedChapters = 0
        var updatedBooks = 0
        // saveText 是 writeText 非原子写入；下载中的章节缓存可能只写了一半，
        // 拿它算出的字数会被写进数据库且此后不再重算，所以整本跳过。
        // busyUrls 改为按批刷新，不用批次开始时的旧快照。
        val books = bookDao.all.associateBy { it.bookUrl }
        val pendingUpdates = ArrayList<ChapterWordCountUpdate>(WORD_COUNT_BATCH)
        val handledUrls = HashSet<String>(bookUrls.size)
        val total = bookUrls.size
        var handled = 0

        bookUrls.chunked(SQL_BATCH).forEach { batch ->
            coroutineContext.ensureActive()
            yield()
            val busyUrls = busyBookUrls()
            val chapterBatch = loadChaptersGrouped(batch)
            batch.forEach { bookUrl ->
                coroutineContext.ensureActive()
                handled++
                onProgress(handled, total)
                // 查询失败的书不进 handledUrls，留在列表且保持勾选供重试。
                if (chapterBatch.failed) return@forEach
                if (bookUrl in busyUrls) return@forEach
                val book = books[bookUrl] ?: return@forEach
                if (!book.isOnLineTxt) return@forEach
                handledUrls += bookUrl
                val result = recountBook(
                    book,
                    chapterBatch.byBook[bookUrl].orEmpty(),
                    pendingUpdates,
                    { flushWordCounts(pendingUpdates) },
                    // 整书字数已有值（多半来自书源详情，含未缓存章节）时绝不覆盖，
                    // 只补章节字数；仅当原本为空才允许汇总写回。
                    allowBookUpdate = book.wordCount.isNullOrBlank(),
                )
                updatedChapters += result.recounted
                result.bookWordCount?.let { bookWordCount ->
                    // 整书字数是章节字数的汇总，必须先让 chapters 表落库再写 books 表，
                    // 避免其它组件在两次写入之间读到「整书有值、章节仍为空」的中间状态。
                    flushWordCounts(pendingUpdates)
                    // SQL 自带「当前为空」条件；返回 0 说明期间书源已写入整书字数，
                    // 此时不计入「已更新」，也不覆盖那个新值。
                    if (bookDao.upWordCount(book.bookUrl, bookWordCount) > 0) {
                        updatedBooks++
                    }
                }
            }
        }
        flushWordCounts(pendingUpdates)
        RecountOutcome(
            updatedChapters = updatedChapters,
            updatedBooks = updatedBooks,
            handledBookUrls = handledUrls.toSet(),
        )
    }

    /**
     * 一章一节地补字数。
     *
     * 章节字数的 UPDATE 攒够一批才提交事务；整书字数只在整本重算的收尾处汇总写一次：
     * 必须本书确实重算过章节、每章都拿到字数、且整书字数字段原本为空。
     * 前两条避免逐章重写整书字数与合计偏小，最后一条避免覆盖书源给的全书字数。
     */
    private fun recountBook(
        book: Book,
        chapters: List<BookChapter>,
        pendingUpdates: MutableList<ChapterWordCountUpdate>,
        flush: () -> Unit,
        allowBookUpdate: Boolean,
    ): RecountResult {
        val resolved = HashMap<String, Int>(chapters.size)
        var recounted = 0
        var unsolved = 0
        for (chapter in chapters) {
            if (chapter.isVolume) continue
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
            pendingUpdates += ChapterWordCountUpdate(
                book.bookUrl, chapter.url, StringUtils.wordCountFormat(length),
            )
            if (pendingUpdates.size >= WORD_COUNT_BATCH) flush()
            resolved[chapter.url] = length
            recounted++
        }
        // 整书字数是整本重算的收尾：本书确实重算过章节、且每章都拿到了字数，才汇总一次。
        // 计算放在章节循环之外，调用方也只写一次，不会每算完一章就重写整书字数。
        val bookWordCount = if (allowBookUpdate && recounted > 0 && unsolved == 0) {
            StringUtils.wordCountFormat(CacheMaintenanceRules.sumWordCounts(resolved.values))
        } else null
        return RecountResult(recounted, bookWordCount)
    }

    private fun flushWordCounts(pending: MutableList<ChapterWordCountUpdate>) {
        if (pending.isEmpty()) return
        bookChapterDao.updateWordCounts(pending.toList())
        pending.clear()
    }

    /** [recountable] 待重算的章节数，[unsolved] 拿不到字数的章节数。 */
    private fun surveyWordCounts(book: Book, chapters: List<BookChapter>): RecountStats {
        var recountable = 0
        var unsolved = 0
        for (chapter in chapters) {
            // 与 recountBook 保持一致：卷章节不参与统计，否则扫描显示的待重算数会多算。
            if (chapter.isVolume) continue
            val stored = CacheMaintenanceRules.parseStoredWordCount(chapter.wordCount)
            if (stored != null && stored > 0) continue
            if (BookHelp.hasContent(book, chapter)) recountable++ else unsolved++
        }
        return RecountStats(recountable, unsolved)
    }

    /**
     * 一次 IN 查询读一批书的章节，按 bookUrl 分组；每批处理完即可释放。
     *
     * [failed] 必须单独标记：「查询失败」与「成功但确实没有章节」都是空 Map，
     * 但前者不能当成处理完成，否则这些书会被从结果里移除、再也无从重试。
     */
    private data class ChapterBatch(
        val byBook: Map<String, List<BookChapter>>,
        val failed: Boolean,
    )

    private fun loadChaptersGrouped(bookUrls: List<String>): ChapterBatch =
        try {
            ChapterBatch(bookChapterDao.getChapterList(bookUrls).groupBy { it.bookUrl }, false)
        } catch (error: Exception) {
            AppLog.put("缓存维护读取章节列表失败，跳过该批", error)
            ChapterBatch(emptyMap(), true)
        }

    /** 批量算出每本书的合法章节主体名，替代逐本 5000 次 SQL。 */
    private fun loadChapterSubjects(bookUrls: List<String>): ChapterSubjects {
        val result = HashMap<String, MutableSet<String>>()
        val failed = HashSet<String>()
        bookUrls.chunked(SQL_BATCH).forEach { batch ->
            val chapterBatch = loadChaptersGrouped(batch)
            if (chapterBatch.failed) {
                failed += batch
            } else {
                chapterBatch.byBook.values.flatten().forEach { chapter ->
                    val set = result.getOrPut(chapter.bookUrl) { HashSet() }
                    set += chapter.getFileName().substringBeforeLast('.')
                }
            }
        }
        return ChapterSubjects(result, failed)
    }

    private data class ChapterSubjects(
        val byBook: Map<String, Set<String>>,
        val failedBookUrls: Set<String>,
    )

    /**
     * 取出该书缓存目录下查无对应章节的条目。
     *
     * [legalSubjects] 由调用方批量预加载；章节表读取失败时它是空集，此时不产出任何孤儿，
     * 避免把「查不到章节列表」误判成「所有缓存都是孤儿」。
     */
    private fun collectOrphanEntries(
        folderName: String,
        legalSubjects: Set<String>,
    ): List<File>? {
        val entries = File(BookHelp.cachePath, folderName).listFiles() ?: return null
        // 候选包含历史命名残留：它们同样查无此章，也应被列为孤儿。
        // 合法缓存的名字必然落在 legalSubjects 里，不在这里被误伤。
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

    private suspend fun deleteEntries(bookDir: File, names: Set<String>): DeleteStats {
        var deleted = 0
        var freed = 0L
        var skipped = 0
        names.forEach { name ->
            coroutineContext.ensureActive()
            val entry = File(bookDir, name)
            // 一次判定同时得出存在性与大小，省掉独立的 exists() stat。
            val before = lengthOfOrNull(entry) ?: return@forEach
            if (BookHelp.deleteChapterCacheEntryIfIdle(entry)) {
                deleted++
                // 按删除前后的实际差值计：只删正文、保留 images/ 时不能把图片体积算进释放量。
                val after = if (entry.exists()) lengthOf(entry) else 0L
                freed += (before - after).coerceAtLeast(0L)
            } else {
                skipped++
            }
        }
        return DeleteStats(deleted, freed, skipped)
    }

    private fun lengthOfOrNull(entry: File): Long? {
        if (entry.isFile) return entry.length()
        val children = entry.listFiles() ?: return null
        return children.sumOf(::lengthOf)
    }

    private fun lengthOf(entry: File): Long {
        if (entry.isFile) return entry.length()
        return entry.listFiles()?.sumOf(::lengthOf) ?: 0L
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

    private data class DeleteStats(val deleted: Int, val freed: Long, val skipped: Int)

    private data class RecountResult(
        val recounted: Int,
        /** 非空表示这本书可以写整书字数；由调用方在章节更新提交后落库。 */
        val bookWordCount: String?,
    )

    private data class RecountStats(
        val recountable: Int,
        val unsolved: Int,
    )
}
