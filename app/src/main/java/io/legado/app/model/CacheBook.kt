package io.legado.app.model

import android.content.Context
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ConcurrentException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object CacheBook {

    private data class QueueStats(
        val waitingCount: Int,
        val downloadingCount: Int
    )

    private class CacheBookCoordinator {
        val taskMap = ConcurrentHashMap<String, CacheBookModel>()
        private val processMutex = Mutex()
        private val workingState = MutableStateFlow(true)

        fun setWorkingState(value: Boolean) {
            workingState.value = value
        }

        suspend fun startProcessJob(context: CoroutineContext) = processMutex.withLock {
            setWorkingState(true)
            flow {
                while (currentCoroutineContext().isActive && taskMap.isNotEmpty()) {
                    if (!workingState.value) {
                        workingState.first { it }
                    }
                    var emitted = false
                    taskMap.forEach { (_, model) ->
                        if (!model.isLoading()) {
                            emit(model)
                            emitted = true
                        }
                    }
                    if (!emitted) delay(800)
                }
            }.onStart {
                postEvent(EventBus.UP_DOWNLOAD_STATE, "")
                updateSummary()
            }.onEachParallel(OtherConfig.cacheBookThreadCount.coerceAtLeast(1)) {
                coroutineScope {
                    it.download(this, context)
                }
            }.onCompletion {
                postEvent(EventBus.UP_DOWNLOAD_STATE, "")
                updateSummary()
            }.collect()
        }
    }

    private val coordinator = CacheBookCoordinator()

    private val _cacheSuccessFlow = MutableSharedFlow<BookChapter>(extraBufferCapacity = 64)
    val cacheSuccessFlow = _cacheSuccessFlow.asSharedFlow()

    private val _downloadSummaryFlow = MutableStateFlow("")
    val downloadSummaryFlow = _downloadSummaryFlow.asStateFlow()

    private val _downloadingIndicesFlow =
        MutableStateFlow<Pair<String, Set<Int>>>("" to emptySet())
    val downloadingIndicesFlow = _downloadingIndicesFlow.asStateFlow()

    private val _queueChangedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val queueChangedFlow = _queueChangedFlow.asSharedFlow()

    private val _downloadErrorFlow =
        MutableStateFlow<Pair<String, Set<Int>>>("" to emptySet())
    val downloadErrorFlow = _downloadErrorFlow.asStateFlow()
    @Volatile
    private var lastQueueStats = QueueStats(0, 0)

    val successDownloadSet = ConcurrentHashMap.newKeySet<String>()
    val errorDownloadMap = ConcurrentHashMap<String, Int>()
    private val errorIndexMap = ConcurrentHashMap<String, MutableSet<Int>>()

    val cacheBookMap: ConcurrentHashMap<String, CacheBookModel>
        get() = coordinator.taskMap

    fun errorIndices(bookUrl: String): Set<Int> {
        return errorIndexMap[bookUrl]?.toSet().orEmpty()
    }

    private fun collectQueueStats(): QueueStats {
        var waiting = 0
        var downloading = 0
        cacheBookMap.forEach { (_, model) ->
            val (w, d) = model.queueCounts()
            waiting += w
            downloading += d
        }
        return QueueStats(waiting, downloading)
    }

    private fun updateSummary() {
        val stats = collectQueueStats()
        lastQueueStats = stats
        _downloadSummaryFlow.value =
            "正在下载:${stats.downloadingCount}|等待中:${stats.waitingCount}|失败:${errorDownloadMap.size}|成功:${successDownloadSet.size}"
    }

    @Synchronized
    fun getOrCreate(bookUrl: String): CacheBookModel? {
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        val source = appDb.bookSourceDao.getBookSource(book.origin) ?: return null
        return getOrCreate(source, book)
    }

    @Synchronized
    fun getOrCreate(bookSource: BookSource, book: Book): CacheBookModel {
        updateBookSource(bookSource)
        cacheBookMap[book.bookUrl]?.let { model ->
            model.bookSource = bookSource
            model.book = book
            return model
        }
        val model = CacheBookModel(bookSource, book)
        cacheBookMap[book.bookUrl] = model
        updateSummary()
        return model
    }

    private fun updateBookSource(newBookSource: BookSource) {
        cacheBookMap.forEach { (_, model) ->
            if (model.bookSource.bookSourceUrl == newBookSource.bookSourceUrl) {
                model.bookSource = newBookSource
            }
        }
    }

    fun start(context: Context, book: Book, selectedIndices: List<Int>) {
        if (!book.isLocal && selectedIndices.isNotEmpty()) {
            context.startService<CacheBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putIntegerArrayListExtra("indices", ArrayList(selectedIndices))
            }
        }
    }

    fun remove(context: Context, bookUrl: String) {
        context.startService<CacheBookService> {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
        }
    }

    fun removeBook(bookUrl: String): Boolean {
        val model = cacheBookMap.remove(bookUrl) ?: return false
        model.stop()
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
        postEvent(EventBus.UP_DOWNLOAD, bookUrl)
        return true
    }

    fun removeChapter(bookUrl: String, chapterIndex: Int): Boolean {
        return cacheBookMap[bookUrl]?.removeDownload(chapterIndex) == true
    }

    fun stop(context: Context) {
        if (CacheBookService.isRun) {
            context.startService<CacheBookService> {
                action = IntentAction.stop
            }
        }
    }

    fun close() {
        cacheBookMap.forEach { (_, model) -> model.stop() }
        cacheBookMap.clear()
        successDownloadSet.clear()
        errorDownloadMap.clear()
        errorIndexMap.clear()
        updateSummary()
    }

    fun setWorkingState(value: Boolean) {
        coordinator.setWorkingState(value)
    }

    suspend fun startProcessJob(context: CoroutineContext) {
        coordinator.startProcessJob(context)
    }

    val totalCount: Int
        get() {
            val stats = collectQueueStats()
            return stats.waitingCount + stats.downloadingCount + successDownloadSet.size + errorDownloadMap.size
        }

    val completedCount: Int
        get() = successDownloadSet.size + errorDownloadMap.size

    val downloadSummary: String
        get() {
            val stats = collectQueueStats()
            return "正在下载:${stats.downloadingCount} | 等待中:${stats.waitingCount} | 失败:${errorDownloadMap.size} | 成功:${successDownloadSet.size}"
        }

    val isRun: Boolean
        get() = lastQueueStats.waitingCount > 0 || lastQueueStats.downloadingCount > 0

    private fun onTaskQueuesChanged(bookUrl: String) {
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
        postEvent(EventBus.UP_DOWNLOAD, bookUrl)
    }

    private fun onTaskRemoved(bookUrl: String) {
        cacheBookMap.remove(bookUrl)
        updateSummary()
        postEvent(EventBus.UP_DOWNLOAD, bookUrl)
    }

    class CacheBookModel(
        @Volatile var bookSource: BookSource,
        @Volatile var book: Book
    ) {

        private val waitDownloadSet = linkedSetOf<Int>()
        private val onDownloadSet = linkedSetOf<Int>()
        private val pausedDownloadSet = hashSetOf<Int>()
        private val chapterTasks = hashMapOf<Int, Coroutine<*>>()
        private val tasks = CompositeCoroutine()
        private var isStopped = false
        private var waitingRetry = false
        private var isLoading = false

        init {
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        private fun notifyDownloadSetChanged() {
            _downloadingIndicesFlow.tryEmit(book.bookUrl to onDownloadSet.toSet())
        }

        private fun notifyErrorChanged() {
            val errors = errorIndexMap[book.bookUrl]?.toSet() ?: emptySet()
            _downloadErrorFlow.tryEmit(book.bookUrl to errors)
        }

        @Synchronized
        fun queueCounts(): Pair<Int, Int> = waitDownloadSet.size to onDownloadSet.size

        @Synchronized
        fun waitingIndices(): Set<Int> = waitDownloadSet.toSet()

        @Synchronized
        fun downloadingIndices(): Set<Int> = onDownloadSet.toSet()

        @Synchronized
        fun isRun(): Boolean {
            return waitDownloadSet.isNotEmpty() || onDownloadSet.isNotEmpty() || isLoading
        }

        @Synchronized
        fun isStop(): Boolean {
            return isStopped || (!isRun() && !waitingRetry)
        }

        @Synchronized
        fun isLoading(): Boolean = isLoading

        @Synchronized
        fun setLoading() {
            isLoading = true
            CacheBook.onTaskQueuesChanged(book.bookUrl)
        }

        @Synchronized
        fun stop() {
            waitDownloadSet.clear()
            pausedDownloadSet.clear()
            chapterTasks.clear()
            tasks.clear()
            isStopped = true
            isLoading = false
            onDownloadSet.clear()
            notifyDownloadSetChanged()
            CacheBook.onTaskQueuesChanged(book.bookUrl)
        }

        @Synchronized
        fun addDownload(start: Int, end: Int) {
            addDownloads(start..end)
        }

        @Synchronized
        fun addDownloads(indices: Iterable<Int>) {
            isStopped = false
            for (i in indices) {
                pausedDownloadSet.remove(i)
                if (!onDownloadSet.contains(i)) {
                    waitDownloadSet.add(i)
                }
            }
            cacheBookMap[book.bookUrl] = this
            isLoading = false
            notifyDownloadSetChanged()
            CacheBook.onTaskQueuesChanged(book.bookUrl)
        }

        fun addDownload(index: Int) {
            addDownload(index, index)
        }

        @Synchronized
        private fun onSuccess(chapter: BookChapter) {
            onDownloadSet.remove(chapter.index)
            chapterTasks.remove(chapter.index)
            successDownloadSet.add(chapter.primaryStr())
            errorDownloadMap.remove(chapter.primaryStr())
            errorIndexMap[book.bookUrl]?.remove(chapter.index)
            notifyDownloadSetChanged()
            notifyErrorChanged()
            _cacheSuccessFlow.tryEmit(chapter)
        }

        @Synchronized
        private fun onPreError(chapter: BookChapter, error: Throwable) {
            waitingRetry = true
            if (error !is ConcurrentException) {
                errorDownloadMap.merge(chapter.primaryStr(), 1) { old, inc -> old + inc }
                errorIndexMap.getOrPut(book.bookUrl) { ConcurrentHashMap.newKeySet() }
                    .add(chapter.index)
            }
            onDownloadSet.remove(chapter.index)
            chapterTasks.remove(chapter.index)
        }

        @Synchronized
        private fun onPostError(chapter: BookChapter, error: Throwable) {
            val retryCount = errorDownloadMap[chapter.primaryStr()] ?: 0
            if (retryCount < 3 && !isStopped) {
                waitDownloadSet.add(chapter.index)
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
        private fun onCancel(index: Int) {
            onDownloadSet.remove(index)
            chapterTasks.remove(index)
            if (!isStopped && !pausedDownloadSet.remove(index)) waitDownloadSet.add(index)
            notifyDownloadSetChanged()
        }

        @Synchronized
        private fun onFinally() {
            val bookUrl = book.bookUrl
            if (waitDownloadSet.isEmpty() && onDownloadSet.isEmpty()) {
                CacheBook.onTaskRemoved(bookUrl)
            } else {
                CacheBook.onTaskQueuesChanged(bookUrl)
            }
            notifyDownloadSetChanged()
        }

        @Synchronized
        fun removeDownload(index: Int): Boolean {
            val removedWaiting = waitDownloadSet.remove(index)
            val task = chapterTasks.remove(index)
            val removedRunning = onDownloadSet.contains(index) || task != null
            if (removedRunning) {
                pausedDownloadSet.add(index)
                task?.let {
                    tasks.delete(it)
                    it.cancel()
                }
            }
            if (!removedWaiting && !removedRunning) return false
            notifyDownloadSetChanged()
            if (waitDownloadSet.isEmpty() && onDownloadSet.isEmpty()) {
                CacheBook.onTaskRemoved(book.bookUrl)
            } else {
                CacheBook.onTaskQueuesChanged(book.bookUrl)
            }
            return true
        }

        /**
         * 从待下载列表内取第一条下载
         */
        @Synchronized
        fun download(scope: CoroutineScope, context: CoroutineContext) {
            val chapterIndex = waitDownloadSet.firstOrNull()
            if (chapterIndex == null) {
                if (!isLoading && onDownloadSet.isEmpty()) {
                    CacheBook.onTaskRemoved(book.bookUrl)
                }
                return
            }
            if (onDownloadSet.contains(chapterIndex)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: run {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (chapter.isVolume) {
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
                _cacheSuccessFlow.tryEmit(chapter)
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (BookHelp.hasImageContent(book, chapter)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }

            waitDownloadSet.remove(chapterIndex)
            onDownloadSet.add(chapterIndex)
            notifyDownloadSetChanged()

            if (BookHelp.hasContent(book, chapter)) {
                val task = Coroutine.async(scope, context, executeContext = context) {
                    BookHelp.getContent(book, chapter)?.let {
                        BookHelp.saveImages(bookSource, book, chapter, it, 1)
                    }
                }.onSuccess {
                    onSuccess(chapter)
                }.onError {
                    onPreError(chapter, it)
                    delay(1000)
                    onPostError(chapter, it)
                }.onCancel {
                    onCancel(chapterIndex)
                }.onFinally {
                    chapterTasks.remove(chapterIndex)?.let { tasks.delete(it) }
                    onFinally()
                }
                chapterTasks[chapterIndex] = task
                tasks.add(task)
                return
            }

            val task = WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                context = context,
                start = CoroutineStart.LAZY,
                executeContext = context
            ).onSuccess { content ->
                onSuccess(chapter)
                downloadFinish(chapter, content)
            }.onError {
                onPreError(chapter, it)
                delay(1000)
                onPostError(chapter, it)
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}")
            }.onCancel {
                onCancel(chapterIndex)
            }.onFinally {
                chapterTasks.remove(chapterIndex)?.let { tasks.delete(it) }
                onFinally()
            }
            chapterTasks[chapterIndex] = task
            tasks.add(task)
            task.start()
        }

        suspend fun downloadAwait(chapter: BookChapter): String {
            synchronized(this) {
                onDownloadSet.add(chapter.index)
                waitDownloadSet.remove(chapter.index)
                notifyDownloadSetChanged()
            }
            try {
                val content = WebBook.getContentAwait(bookSource, book, chapter)
                onSuccess(chapter)
                ReadBook.downloadedChapters.add(chapter.index)
                ReadBook.downloadFailChapters.remove(chapter.index)
                return content
            } catch (e: Exception) {
                if (e is CancellationException) onCancel(chapter.index)
                onError(chapter, e)
                ReadBook.downloadFailChapters[chapter.index] =
                    (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
                return "获取正文失败\n${e.localizedMessage}"
            } finally {
                CacheBook.onTaskQueuesChanged(book.bookUrl)
            }
        }

        @Synchronized
        fun download(
            scope: CoroutineScope,
            chapter: BookChapter,
            semaphore: Semaphore?,
            resetPageOffset: Boolean = false
        ) {
            if (onDownloadSet.contains(chapter.index)) return
            onDownloadSet.add(chapter.index)
            waitDownloadSet.remove(chapter.index)
            notifyDownloadSetChanged()

            WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                start = CoroutineStart.LAZY,
                executeContext = IO,
                semaphore = semaphore
            ).onSuccess { content ->
                onSuccess(chapter)
                ReadBook.downloadedChapters.add(chapter.index)
                ReadBook.downloadFailChapters.remove(chapter.index)
                downloadFinish(chapter, content, resetPageOffset)
            }.onError {
                onError(chapter, it)
                ReadBook.downloadFailChapters[chapter.index] =
                    (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}", resetPageOffset)
            }.onCancel {
                onCancel(chapter.index)
                downloadFinish(chapter, "download canceled", resetPageOffset, true)
            }.onFinally {
                CacheBook.onTaskQueuesChanged(book.bookUrl)
            }.start()
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
                    canceled = canceled
                )
            }
        }
    }
}
