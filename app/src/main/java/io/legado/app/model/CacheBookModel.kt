package io.legado.app.model

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ConcurrentException
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.cache.CacheDownloadCandidate
import io.legado.app.model.cache.CacheDownloadQueue
import io.legado.app.model.cache.CacheDownloadRepository
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.CacheDownloadStateStore
import io.legado.app.model.cache.ChapterSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class CacheBookModel(
    @Volatile var bookSource: BookSource,
    @Volatile var book: Book,
    private val host: Host,
) {

    interface Host {
        val stateStore: CacheDownloadStateStore
        val cacheBookMap: ConcurrentHashMap<String, CacheBookModel>
        fun incrementSuccessCount(): Int
        fun onTaskQueuesChanged(bookUrl: String)
        fun onTaskRemoved(bookUrl: String, clearState: Boolean = false)
        fun emitDownloadingIndices(bookUrl: String, indices: Set<Int>)
        fun emitDownloadError(bookUrl: String, indices: Set<Int>)
        fun emitChapterCached(chapter: BookChapter)
        fun errorIndices(bookUrl: String): Set<Int>
    }

    data class Diagnostics(
        val waitingChapterCount: Int,
        val runningChapterCount: Int,
        val trackedChapterTaskCount: Int,
        val isLoading: Boolean,
        val waitingRetry: Boolean,
    )

    private val queue = CacheDownloadQueue()
    private val onDownloadSet = linkedSetOf<Int>()
    private val canceledDownloadSet = hashSetOf<Int>()
    private val pausedChapterSet = linkedSetOf<Int>()
    private val pendingReadRequestMap = hashMapOf<Int, Boolean>()
    private val chapterTasks = hashMapOf<Int, Coroutine<*>>()
    private val tasks = CompositeCoroutine()
    private val repository = CacheDownloadRepository()
    private val retryCountMap = hashMapOf<Int, Int>()
    private var isStopped = false
    private var waitingRetry = false
    private var isLoading = false
    private var isPaused = false

    private fun notifyDownloadSetChanged() {
        host.emitDownloadingIndices(book.bookUrl, onDownloadSet.toSet())
        if (host.cacheBookMap[book.bookUrl] === this) {
            host.stateStore.updateBookQueue(
                bookUrl = book.bookUrl,
                waitingCount = queue.waitingCount(),
                runningIndices = onDownloadSet.toSet(),
                pausedIndices = pausedChapterSet.toSet(),
            )
        }
    }

    private fun notifyErrorChanged() {
        val errors = host.errorIndices(book.bookUrl)
        host.emitDownloadError(book.bookUrl, errors)
    }

    @Synchronized
    fun queueCounts(): Pair<Int, Int> = queue.waitingCount() to onDownloadSet.size

    @Synchronized
    fun isWaiting(index: Int): Boolean = queue.isWaiting(index)

    @Synchronized
    fun isDownloading(index: Int): Boolean = onDownloadSet.contains(index)

    @Synchronized
    fun isPaused(): Boolean = isPaused

    @Synchronized
    fun isPaused(index: Int): Boolean {
        return pausedChapterSet.contains(index) ||
                (isPaused && (queue.isWaiting(index) || onDownloadSet.contains(index)))
    }

    @Synchronized
    fun downloadingIndices(): Set<Int> = onDownloadSet.toSet()

    @Synchronized
    fun pausedIndices(): Set<Int> = pausedChapterSet.toSet()

    @Synchronized
    fun pausedCount(): Int {
        return pausedChapterSet.size + if (isPaused) {
            queue.waitingCount() + onDownloadSet.size
        } else {
            0
        }
    }

    @Synchronized
    fun isRun(): Boolean {
        return queue.waitingCount() > 0 || onDownloadSet.isNotEmpty() || isLoading || chapterTasks.isNotEmpty()
    }

    @Synchronized
    fun isStop(): Boolean {
        return isStopped || (!isRun() && !waitingRetry)
    }

    @Synchronized
    fun isLoading(): Boolean = isLoading

    @Synchronized
    fun hasQueuedDownloads(): Boolean {
        return queue.waitingCount() > 0 ||
                onDownloadSet.isNotEmpty() ||
                pausedChapterSet.isNotEmpty() ||
                isLoading ||
                chapterTasks.isNotEmpty()
    }

    @Synchronized
    fun hasRunnableDownloads(): Boolean {
        return !isPaused && (
                queue.waitingCount() > 0 ||
                        onDownloadSet.isNotEmpty() ||
                        isLoading ||
                        chapterTasks.isNotEmpty()
                )
    }

    @Synchronized
    fun diagnostics(): Diagnostics {
        return Diagnostics(
            waitingChapterCount = queue.waitingCount(),
            runningChapterCount = onDownloadSet.size,
            trackedChapterTaskCount = chapterTasks.size,
            isLoading = isLoading,
            waitingRetry = waitingRetry,
        )
    }

    @Synchronized
    fun setLoading() {
        isLoading = true
        host.onTaskQueuesChanged(book.bookUrl)
    }

    @Synchronized
    fun pause(): Boolean {
        if (!hasQueuedDownloads()) return false
        isPaused = true
        isLoading = false
        waitingRetry = false
        chapterTasks.values.toList().forEach { task ->
            tasks.delete(task)
            task.cancel()
        }
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
        return true
    }

    @Synchronized
    fun resume(): Boolean {
        if (!isPaused && pausedChapterSet.isEmpty()) return false
        isPaused = false
        if (pausedChapterSet.isNotEmpty()) {
            queue.enqueue(ChapterSelection.Indices(pausedChapterSet.toSet()))
            pausedChapterSet.clear()
        }
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
        return true
    }

    @Synchronized
    fun stop() {
        queue.clear()
        canceledDownloadSet.clear()
        pausedChapterSet.clear()
        chapterTasks.clear()
        pendingReadRequestMap.clear()
        tasks.clear()
        retryCountMap.clear()
        isStopped = true
        isPaused = false
        isLoading = false
        onDownloadSet.clear()
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
    }

    @Synchronized
    fun addDownload(start: Int, end: Int) {
        addRequest(
            CacheDownloadRequest(
                book.bookUrl,
                ChapterSelection.Range(start, end),
                CacheDownloadSource.ReadPreload,
            )
        )
    }

    @Synchronized
    fun addDownloads(indices: Iterable<Int>) {
        val values = indices.toSet()
        if (values.isEmpty()) return
        addRequest(
            CacheDownloadRequest(
                book.bookUrl,
                ChapterSelection.Indices(values),
                CacheDownloadSource.Manual,
            )
        )
    }

    @Synchronized
    fun addRequest(request: CacheDownloadRequest) {
        isStopped = false
        isPaused = false
        when (val selection = request.selection) {
            is ChapterSelection.Range -> {
                canceledDownloadSet.removeAll { it in selection.start..selection.end }
                pausedChapterSet.removeAll { it in selection.start..selection.end }
            }
            is ChapterSelection.Indices -> selection.values.forEach {
                canceledDownloadSet.remove(it)
                pausedChapterSet.remove(it)
            }
            is ChapterSelection.Single -> {
                canceledDownloadSet.remove(selection.index)
                pausedChapterSet.remove(selection.index)
            }
        }
        queue.enqueue(request)
        host.cacheBookMap[book.bookUrl] = this
        isLoading = false
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
    }

    fun addDownload(index: Int) {
        addDownload(index, index)
    }

    @Synchronized
    private fun onSuccess(chapter: BookChapter) {
        onDownloadSet.remove(chapter.index)
        chapterTasks.remove(chapter.index)
        host.incrementSuccessCount()
        retryCountMap.remove(chapter.index)
        host.stateStore.markSuccess(book.bookUrl, chapter.index)
        notifyDownloadSetChanged()
        notifyErrorChanged()
        host.emitChapterCached(chapter)
    }

    @Synchronized
    private fun onPreError(chapter: BookChapter, error: Throwable) {
        waitingRetry = true
        if (error !is ConcurrentException) {
            retryCountMap[chapter.index] = (retryCountMap[chapter.index] ?: 0) + 1
            host.stateStore.markFailed(book.bookUrl, chapter.index)
        }
        onDownloadSet.remove(chapter.index)
        chapterTasks.remove(chapter.index)
    }

    @Synchronized
    private fun onPostError(chapter: BookChapter, error: Throwable) {
        val retryCount = retryCountMap[chapter.index] ?: 0
        if (retryCount < 3 && !isStopped) {
            queue.enqueue(ChapterSelection.Single(chapter.index))
        } else {
            AppLog.put("下载${book.name}-${chapter.title}失败\n${error.localizedMessage}", error)
        }
        waitingRetry = false
    }

    @Synchronized
    private fun onError(chapter: BookChapter, error: Throwable) {
        onPreError(chapter, error)
        onPostError(chapter, error)
        notifyDownloadSetChanged()
        notifyErrorChanged()
    }

    @Synchronized
    private fun onCancel(index: Int, requeue: Boolean = true) {
        onDownloadSet.remove(index)
        chapterTasks.remove(index)
        val wasCanceled = canceledDownloadSet.remove(index)
        if (requeue && !isStopped && !wasCanceled) {
            queue.enqueue(ChapterSelection.Single(index))
        }
        notifyDownloadSetChanged()
    }

    @Synchronized
    private fun onFinally() {
        val bookUrl = book.bookUrl
        if (queue.waitingCount() == 0 && onDownloadSet.isEmpty() && pausedChapterSet.isEmpty()) {
            host.onTaskRemoved(bookUrl)
        } else {
            host.onTaskQueuesChanged(bookUrl)
        }
        notifyDownloadSetChanged()
    }

    @Synchronized
    fun removeDownload(index: Int): Boolean {
        val removedWaiting = queue.removeChapter(index)
        val removedPaused = pausedChapterSet.remove(index)
        val task = chapterTasks.remove(index)
        val removedRunning = onDownloadSet.contains(index) || task != null
        if (removedRunning) {
            canceledDownloadSet.add(index)
            onDownloadSet.remove(index)
            task?.let {
                tasks.delete(it)
                it.cancel()
            }
        }
        if (!removedWaiting && !removedRunning && !removedPaused) return false
        notifyDownloadSetChanged()
        if (queue.waitingCount() == 0 && onDownloadSet.isEmpty() && pausedChapterSet.isEmpty()) {
            host.onTaskRemoved(book.bookUrl, clearState = true)
        } else {
            host.onTaskQueuesChanged(book.bookUrl)
        }
        return true
    }

    @Synchronized
    fun pauseDownload(index: Int): Boolean {
        val removedWaiting = queue.removeChapter(index)
        val task = chapterTasks.remove(index)
        val removedRunning = onDownloadSet.contains(index) || task != null
        if (!removedWaiting && !removedRunning) return false
        pausedChapterSet.add(index)
        if (removedRunning) {
            canceledDownloadSet.add(index)
            onDownloadSet.remove(index)
            task?.let {
                tasks.delete(it)
                it.cancel()
            }
        }
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
        return true
    }

    @Synchronized
    fun resumeDownload(index: Int): Boolean {
        if (!pausedChapterSet.remove(index)) return false
        isPaused = false
        queue.enqueue(ChapterSelection.Single(index))
        notifyDownloadSetChanged()
        host.onTaskQueuesChanged(book.bookUrl)
        return true
    }

    /**
     * 从待下载列表内取第一条下载
     */
    fun download(scope: CoroutineScope, context: CoroutineContext) {
        val candidate = nextDownloadCandidate() ?: return
        val chapterIndex = candidate.chapterIndex
        val chapter = repository.getChapter(book.bookUrl, chapterIndex) ?: run {
            onSkipped(chapterIndex)
            return
        }
        if (chapter.isVolume) {
            host.emitChapterCached(chapter)
            onSkipped(chapterIndex)
            return
        }
        if (repository.hasImageContent(book, chapter)) {
            onSkipped(chapterIndex)
            return
        }

        if (repository.hasContent(book, chapter)) {
            val task = repository.saveCachedImagesTask(
                scope = scope,
                context = context,
                bookSource = bookSource,
                book = book,
                chapter = chapter,
                start = CoroutineStart.LAZY,
            )
            if (!attachTaskIfActive(task, chapter, chapterIndex)) {
                task.cancel()
                return
            }
            task.start()
            return
        }

        val task = repository.cacheContentTask(
            scope = scope,
            bookSource = bookSource,
            book = book,
            chapter = chapter,
            context = context,
            start = CoroutineStart.LAZY,
            executeContext = context,
        )
        if (!attachTaskIfActive(task, chapter, chapterIndex)) {
            task.cancel()
            return
        }
        task.start()
    }

    @Synchronized
    private fun nextDownloadCandidate(): CacheDownloadCandidate? {
        if (isPaused) return null
        val candidate = queue.next(book.bookUrl, onDownloadSet)
        if (candidate == null) {
            notifyDownloadSetChanged()
            if (!isLoading && onDownloadSet.isEmpty() && pausedChapterSet.isEmpty()) {
                host.onTaskRemoved(book.bookUrl)
            } else {
                host.onTaskQueuesChanged(book.bookUrl)
            }
            return null
        }
        val chapterIndex = candidate.chapterIndex
        if (onDownloadSet.contains(chapterIndex)) {
            return null
        }
        onDownloadSet.add(chapterIndex)
        notifyDownloadSetChanged()
        return candidate
    }

    @Synchronized
    private fun onSkipped(index: Int) {
        onDownloadSet.remove(index)
        chapterTasks.remove(index)
        notifyDownloadSetChanged()
        if (queue.waitingCount() == 0 && onDownloadSet.isEmpty() && pausedChapterSet.isEmpty() && !isLoading) {
            host.onTaskRemoved(book.bookUrl)
        } else {
            host.onTaskQueuesChanged(book.bookUrl)
        }
    }

    @Synchronized
    private fun <T> attachTaskIfActive(
        task: Coroutine<T>,
        chapter: BookChapter,
        chapterIndex: Int,
    ): Boolean {
        if (isStopped || isPaused || !onDownloadSet.contains(chapterIndex)) {
            if (!isStopped && isPaused && onDownloadSet.remove(chapterIndex)) {
                queue.enqueue(ChapterSelection.Single(chapterIndex))
                notifyDownloadSetChanged()
                host.onTaskQueuesChanged(book.bookUrl)
            }
            return false
        }
        attachCallbacks(task, chapter, chapterIndex)
        chapterTasks[chapterIndex] = task
        tasks.add(task)
        return true
    }

    private fun <T> attachCallbacks(
        task: Coroutine<T>,
        chapter: BookChapter,
        chapterIndex: Int,
    ) {
        task.onSuccess {
            onSuccess(chapter)
            (it as? String)?.let { content ->
                emitPendingReadContent(chapter, content)
            }
        }.onError {
            onPreError(chapter, it)
            delay(1000)
            onPostError(chapter, it)
            emitPendingReadError(chapter, it)
        }.onCancel {
            onCancel(chapterIndex)
            emitPendingReadCanceled(chapter)
        }.onFinally {
            chapterTasks.remove(chapterIndex)?.let { tasks.delete(it) }
            onFinally()
        }
    }

    suspend fun downloadAwait(chapter: BookChapter): String {
        synchronized(this) {
            onDownloadSet.add(chapter.index)
            queue.removeChapter(chapter.index)
            notifyDownloadSetChanged()
        }
        try {
            val content = repository.downloadContentAwait(bookSource, book, chapter)
            onSuccess(chapter)
            ReadBook.downloadedChapters.add(chapter.index)
            ReadBook.downloadFailChapters.remove(chapter.index)
            return content
        } catch (e: Exception) {
            if (e is CancellationException) {
                onCancel(chapter.index, requeue = false)
                return "download canceled"
            }
            onError(chapter, e)
            ReadBook.downloadFailChapters[chapter.index] =
                (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
            return "获取正文失败\n${e.localizedMessage}"
        } finally {
            host.onTaskQueuesChanged(book.bookUrl)
        }
    }

    fun download(
        scope: CoroutineScope,
        chapter: BookChapter,
        semaphore: Semaphore?,
        resetPageOffset: Boolean = false
    ) {
        if (!markChapterDownloadStarted(chapter.index)) {
            markPendingReadRequest(chapter.index, resetPageOffset)
            return
        }
        repository.downloadContentTask(
            scope = scope,
            bookSource = bookSource,
            book = book,
            chapter = chapter,
            start = CoroutineStart.LAZY,
            context = IO,
            executeContext = IO,
            semaphore = semaphore
        ).onSuccess { content ->
            onSuccess(chapter)
            ReadBook.downloadedChapters.add(chapter.index)
            ReadBook.downloadFailChapters.remove(chapter.index)
            downloadFinish(chapter, content, resetPageOffset)
            emitPendingReadContent(chapter, content)
        }.onError {
            onError(chapter, it)
            ReadBook.downloadFailChapters[chapter.index] =
                (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
            downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}", resetPageOffset)
            emitPendingReadError(chapter, it)
        }.onCancel {
            onCancel(chapter.index, requeue = false)
            downloadFinish(chapter, "download canceled", resetPageOffset, true)
        }.onFinally {
            host.onTaskQueuesChanged(book.bookUrl)
        }.start()
    }

    @Synchronized
    private fun markChapterDownloadStarted(index: Int): Boolean {
        if (onDownloadSet.contains(index)) return false
        onDownloadSet.add(index)
        queue.removeChapter(index)
        notifyDownloadSetChanged()
        return true
    }

    @Synchronized
    private fun markPendingReadRequest(index: Int, resetPageOffset: Boolean) {
        pendingReadRequestMap[index] = pendingReadRequestMap[index] == true || resetPageOffset
    }

    @Synchronized
    private fun consumePendingReadRequest(index: Int): Boolean? {
        return pendingReadRequestMap.remove(index)
    }

    private fun emitPendingReadContent(chapter: BookChapter, content: String) {
        val resetPageOffset = consumePendingReadRequest(chapter.index) ?: return
        downloadFinish(chapter, content, resetPageOffset)
    }

    private fun emitPendingReadError(chapter: BookChapter, error: Throwable) {
        val resetPageOffset = consumePendingReadRequest(chapter.index) ?: return
        downloadFinish(chapter, "获取正文失败\n${error.localizedMessage}", resetPageOffset)
    }

    private fun emitPendingReadCanceled(chapter: BookChapter) {
        val resetPageOffset = consumePendingReadRequest(chapter.index) ?: return
        downloadFinish(chapter, "download canceled", resetPageOffset)
    }

    private fun downloadFinish(
        chapter: BookChapter,
        content: String,
        resetPageOffset: Boolean = false,
        canceled: Boolean = false
    ) {
        if (ReadBook.book?.bookUrl == book.bookUrl) {
            ReadBook.contentLoadFinish(
                book = book,
                chapter = chapter,
                content = content,
                resetPageOffset = resetPageOffset,
                canceled = canceled,
            )
        }
    }
}
