package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.repository.VideoContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.core.context.GlobalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 视频书加入书架后先把详情和目录补齐（与上游 VideoBookPreloader 一致）。
 *
 * 不预取的话，播放页打开时要串行等「详情 → 目录 → 正文」三次请求，
 * 用户会盯着黑屏；这里在后台并发补齐，播放页直接命中已有目录。
 */
object VideoBookPreloader {

    private const val MAX_CONCURRENT = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val runningKeys = ConcurrentHashMap.newKeySet<String>()

    private val contentRepository get() = GlobalContext.get().get<VideoContentRepository>()

    /** 幂等：同一本书在预取完成前重复调用只会跑一次。 */
    fun preload(bookUrl: String) {
        if (!runningKeys.add(bookUrl)) return
        scope.launch {
            try {
                semaphore.withPermit {
                    val book = appDb.bookDao.getBook(bookUrl) ?: return@withPermit
                    if (!book.isVideo) return@withPermit
                    // 已有目录说明不必再拉，避免覆盖用户正在用的目录
                    if (appDb.bookChapterDao.getChapterList(bookUrl).isNotEmpty()) return@withPermit
                    val source = appDb.bookSourceDao.getBookSource(book.origin) ?: return@withPermit
                    if (book.tocUrl.isEmpty()) {
                        contentRepository.loadBookInfo(source, book)
                    }
                    val chapters = contentRepository.loadChapters(source, book)
                    if (chapters.isEmpty()) return@withPermit
                    chapters.forEachIndexed { index, chapter -> chapter.index = index }
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                    book.update()
                }
            } catch (_: Throwable) {
                // 预取是尽力而为，失败留给播放页自己重试
            } finally {
                runningKeys.remove(bookUrl)
            }
        }
    }
}
