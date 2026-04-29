package io.legado.app.model

import android.content.Context
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ConcurrentException
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.cache.CacheDownloadQueue
import io.legado.app.model.cache.CacheDownloadRepository
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.CacheDownloadStateStore
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.model.cache.ReadingCacheEvent
import io.legado.app.model.cache.ReadingCacheEvents
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.onEachParallel
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

object CacheBook {

    const val maxDownloadConcurrency = 8

    private data class QueueStats(
        val waitingCount: Int,
        val downloadingCount: Int
    )

    private data class ChapterKey(
        val bookUrl: String,
        val index: Int,
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
                updateSummary()
            }.onEachParallel(OtherConfig.cacheBookThreadCount.coerceIn(1, maxDownloadConcurrency)) {
                coroutineScope {
                    it.download(this, context)
                }
            }.onCompletion {
                updateSummary()
            }.collect()
        }
    }

    private val coordinator = CacheBookCoordinator()
    private val stateStore = CacheDownloadStateStore()
    private val pendingRequests = ConcurrentHashMap<Long, CacheDownloadRequest>()
    private val pendingRequestId = AtomicLong(0)
    val downloadStateFlow = stateStore.stateFlow

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

    @Volatile
    private var successDownloadCount = 0
    private val errorRetryMap = ConcurrentHashMap<ChapterKey, Int>()

    val cacheBookMap: ConcurrentHashMap<String, CacheBookModel>
        get() = coordinator.taskMap

    fun errorIndices(bookUrl: String): Set<Int> {
        return stateStore.bookState(bookUrl)?.failedIndices.orEmpty()
    }

    fun markBookFailed(bookUrl: String, message: String) {
        stateStore.markBookFailed(bookUrl, message)
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
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
            "正在下载:${stats.downloadingCount}|等待中:${stats.waitingCount}|失败:${stateStore.state.totalFailure}|成功:$successDownloadCount"
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
        start(
            context = context,
            request = CacheDownloadRequest(
                bookUrl = book.bookUrl,
                selection = ChapterSelection.Indices(selectedIndices.toSet()),
            ),
            isLocal = book.isLocal,
        )
    }

    fun start(context: Context, book: Book, startIndex: Int, endIndex: Int) {
        start(
            context = context,
            request = CacheDownloadRequest(
                bookUrl = book.bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
            ),
            isLocal = book.isLocal,
        )
    }

    fun start(context: Context, request: CacheDownloadRequest, isLocal: Boolean = false) {
        if (isLocal) return
        when (val selection = request.selection) {
            is ChapterSelection.Range -> {
                if (selection.end < selection.start) return
            }
            is ChapterSelection.Indices -> {
                if (selection.values.isEmpty()) return
            }
            is ChapterSelection.Single -> Unit
        }
        val requestId = pendingRequestId.incrementAndGet()
        pendingRequests[requestId] = request
        context.startService<CacheBookService> {
            action = IntentAction.start
            putExtra("requestId", requestId)
            putExtra("bookUrl", request.bookUrl)
            putExtra("source", request.source.name)
            when (val selection = request.selection) {
                is ChapterSelection.Range -> {
                    putExtra("start", selection.start)
                    putExtra("end", selection.end)
                }
                is ChapterSelection.Indices -> Unit
                is ChapterSelection.Single -> {
                    putExtra("start", selection.index)
                    putExtra("end", selection.index)
                }
            }
        }
    }

    fun takePendingRequest(requestId: Long): CacheDownloadRequest? {
        return pendingRequests.remove(requestId)
    }

    fun remove(context: Context, bookUrl: String) {
        context.startService<CacheBookService> {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
        }
    }

    fun removeBook(bookUrl: String): Boolean {
        val model = cacheBookMap.remove(bookUrl)
        model?.stop()
        stateStore.removeBook(bookUrl)
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
        return model != null
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
        successDownloadCount = 0
        errorRetryMap.clear()
        pendingRequests.clear()
        stateStore.clear()
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
            return stats.waitingCount + stats.downloadingCount + successDownloadCount + stateStore.state.totalFailure
        }

    val completedCount: Int
        get() = successDownloadCount + stateStore.state.totalFailure

    val downloadSummary: String
        get() {
            val stats = collectQueueStats()
            return "正在下载:${stats.downloadingCount} | 等待中:${stats.waitingCount} | 失败:${stateStore.state.totalFailure} | 成功:$successDownloadCount"
        }

    val isRun: Boolean
        get() = lastQueueStats.waitingCount > 0 || lastQueueStats.downloadingCount > 0

    private fun onTaskQueuesChanged(bookUrl: String) {
        cacheBookMap[bookUrl]?.let { model ->
            stateStore.updateBookQueue(
                bookUrl = bookUrl,
                waitingCount = model.queueCounts().first,
                runningIndices = model.downloadingIndices(),
            )
            _downloadingIndicesFlow.tryEmit(bookUrl to model.downloadingIndices())
            _downloadErrorFlow.tryEmit(bookUrl to errorIndices(bookUrl))
        }
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    private fun onTaskRemoved(bookUrl: String, clearState: Boolean = false) {
        cacheBookMap.remove(bookUrl)
        if (clearState) {
            stateStore.removeBook(bookUrl)
        }
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    class CacheBookModel(
        @Volatile var bookSource: BookSource,
        @Volatile var book: Book
    ) {

        private val queue = CacheDownloadQueue()
        private val onDownloadSet = linkedSetOf<Int>()
        private val pausedDownloadSet = hashSetOf<Int>()
        private val chapterTasks = hashMapOf<Int, Coroutine<*>>()
        private val tasks = CompositeCoroutine()
        private val repository = CacheDownloadRepository()
        private var isStopped = false
        private var waitingRetry = false
        private var isLoading = false

        private fun notifyDownloadSetChanged() {
            _downloadingIndicesFlow.tryEmit(book.bookUrl to onDownloadSet.toSet())
            if (cacheBookMap[book.bookUrl] === this) {
                stateStore.updateBookQueue(
                    bookUrl = book.bookUrl,
                    waitingCount = queue.waitingCount(),
                    runningIndices = onDownloadSet.toSet(),
                )
            }
        }

        private fun notifyErrorChanged() {
            val errors = errorIndices(book.bookUrl)
            _downloadErrorFlow.tryEmit(book.bookUrl to errors)
        }

        @Synchronized
        fun queueCounts(): Pair<Int, Int> = queue.waitingCount() to onDownloadSet.size

        @Synchronized
        fun isWaiting(index: Int): Boolean = queue.isWaiting(index)

        @Synchronized
        fun isDownloading(index: Int): Boolean = onDownloadSet.contains(index)

        @Synchronized
        fun downloadingIndices(): Set<Int> = onDownloadSet.toSet()

        @Synchronized
        fun isRun(): Boolean {
            return queue.waitingCount() > 0 || onDownloadSet.isNotEmpty() || isLoading
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
            queue.clear()
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
            when (val selection = request.selection) {
                is ChapterSelection.Range -> {
                    pausedDownloadSet.removeAll { it in selection.start..selection.end }
                }
                is ChapterSelection.Indices -> selection.values.forEach { pausedDownloadSet.remove(it) }
                is ChapterSelection.Single -> pausedDownloadSet.remove(selection.index)
            }
            queue.enqueue(request)
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
            val chapterKey = ChapterKey(book.bookUrl, chapter.index)
            successDownloadCount++
            errorRetryMap.remove(chapterKey)
            stateStore.markSuccess(book.bookUrl, chapter.index)
            notifyDownloadSetChanged()
            notifyErrorChanged()
            _cacheSuccessFlow.tryEmit(chapter)
        }

        @Synchronized
        private fun onPreError(chapter: BookChapter, error: Throwable) {
            waitingRetry = true
            if (error !is ConcurrentException) {
                errorRetryMap.merge(ChapterKey(book.bookUrl, chapter.index), 1) { old, inc -> old + inc }
                stateStore.markFailed(book.bookUrl, chapter.index)
            }
            onDownloadSet.remove(chapter.index)
            chapterTasks.remove(chapter.index)
        }

        @Synchronized
        private fun onPostError(chapter: BookChapter, error: Throwable) {
            val retryCount = errorRetryMap[ChapterKey(book.bookUrl, chapter.index)] ?: 0
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
        private fun onCancel(index: Int) {
            onDownloadSet.remove(index)
            chapterTasks.remove(index)
            if (!isStopped && !pausedDownloadSet.remove(index)) {
                queue.enqueue(ChapterSelection.Single(index))
            }
            notifyDownloadSetChanged()
        }

        @Synchronized
        private fun onFinally() {
            val bookUrl = book.bookUrl
            if (queue.waitingCount() == 0 && onDownloadSet.isEmpty()) {
                CacheBook.onTaskRemoved(bookUrl)
            } else {
                CacheBook.onTaskQueuesChanged(bookUrl)
            }
            notifyDownloadSetChanged()
        }

        @Synchronized
        fun removeDownload(index: Int): Boolean {
            val removedWaiting = queue.removeChapter(index)
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
            if (queue.waitingCount() == 0 && onDownloadSet.isEmpty()) {
                CacheBook.onTaskRemoved(book.bookUrl, clearState = true)
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
            val candidate = queue.next(book.bookUrl, onDownloadSet)
            if (candidate == null) {
                if (!isLoading && onDownloadSet.isEmpty()) {
                    CacheBook.onTaskRemoved(book.bookUrl)
                }
                return
            }
            val chapterIndex = candidate.chapterIndex
            if (onDownloadSet.contains(chapterIndex)) {
                return
            }
            val chapter = repository.getChapter(book.bookUrl, chapterIndex) ?: run {
                return
            }
            if (chapter.isVolume) {
                _cacheSuccessFlow.tryEmit(chapter)
                return
            }
            if (repository.hasImageContent(book, chapter)) {
                return
            }

            onDownloadSet.add(chapterIndex)
            notifyDownloadSetChanged()

            if (repository.hasContent(book, chapter)) {
                val task = repository.saveCachedImagesTask(
                    scope = scope,
                    context = context,
                    bookSource = bookSource,
                    book = book,
                    chapter = chapter,
                ).onSuccess {
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

            val task = repository.cacheContentTask(
                scope = scope,
                bookSource = bookSource,
                book = book,
                chapter = chapter,
                context = context,
                start = CoroutineStart.LAZY,
                executeContext = context,
            ).onSuccess {
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
            task.start()
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
            queue.removeChapter(chapter.index)
            notifyDownloadSetChanged()

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
            ReadingCacheEvents.emit(
                ReadingCacheEvent.ContentReady(
                    book = book,
                    chapter = chapter,
                    content = content,
                    resetPageOffset = resetPageOffset,
                    canceled = canceled,
                )
            )
        }
    }
}
