package io.legado.app.model

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isLocal
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadStateStore
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.onEachParallel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

object CacheBook {

    const val maxDownloadConcurrency = 8

    data class Diagnostics(
        val activeBookCount: Int,
        val waitingChapterCount: Int,
        val runningChapterCount: Int,
        val trackedChapterTaskCount: Int,
        val loadingBookCount: Int,
        val retryingBookCount: Int,
    )

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
                while (currentCoroutineContext().isActive && taskMap.isNotEmpty() && !isPaused) {
                    if (!workingState.value) {
                        workingState.first { it }
                    }
                    var emitted = false
                    taskMap.forEach { (_, model) ->
                        if (model.hasLaunchableChapters()) {
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

    private val modelHost = ModelHostImpl()
    private val coordinator = CacheBookCoordinator()
    private val stateStore = CacheDownloadStateStore()
    private val pendingRemoveRequests = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()
    private val pendingRequestId = AtomicLong(0)
    @Volatile
    private var isPaused = false
    val downloadStateFlow = stateStore.stateFlow

    private val _cacheSuccessFlow = MutableSharedFlow<BookChapter>(extraBufferCapacity = 64)
    val cacheSuccessFlow = _cacheSuccessFlow.asSharedFlow()

    private val _downloadSummaryFlow = MutableStateFlow("")
    val downloadSummaryFlow = _downloadSummaryFlow.asStateFlow()

    private val _pendingAdmissionFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pendingAdmissionFlow = _pendingAdmissionFlow.asStateFlow()

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
    private var lastSummaryUpdateTime = 0L
    private const val SUMMARY_UPDATE_THROTTLE_MS = 100L

    private val successDownloadCount = AtomicInteger(0)

    val cacheBookMap: ConcurrentHashMap<String, CacheBookModel>
        get() = coordinator.taskMap

    fun errorIndices(bookUrl: String): Set<Int> {
        return stateStore.bookState(bookUrl)?.failedIndices.orEmpty()
    }

    fun markBookFailed(bookUrl: String, message: String) {
        removePendingAdmission(bookUrl)
        stateStore.markBookFailed(bookUrl, message)
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    fun diagnostics(): Diagnostics {
        var waiting = 0
        var running = 0
        var trackedTasks = 0
        var loading = 0
        var retrying = 0
        cacheBookMap.forEach { (_, model) ->
            val item = model.diagnostics()
            waiting += item.waitingChapterCount
            running += item.runningChapterCount
            trackedTasks += item.trackedChapterTaskCount
            if (item.isLoading) loading++
            if (item.waitingRetry) retrying++
        }
        return Diagnostics(
            activeBookCount = cacheBookMap.size,
            waitingChapterCount = waiting,
            runningChapterCount = running,
            trackedChapterTaskCount = trackedTasks,
            loadingBookCount = loading,
            retryingBookCount = retrying,
        )
    }

    private fun collectQueueStats(): QueueStats {
        val state = stateStore.state
        return QueueStats(
            waitingCount = state.totalWaiting + _pendingAdmissionFlow.value.values.sum(),
            downloadingCount = state.totalRunning,
        )
    }

    private fun updateSummary() {
        val now = System.currentTimeMillis()
        if (now - lastSummaryUpdateTime < SUMMARY_UPDATE_THROTTLE_MS) {
            return
        }
        lastSummaryUpdateTime = now
        val stats = collectQueueStats()
        lastQueueStats = stats
        _downloadSummaryFlow.value = buildSummary(stats)
    }

    private fun updateSummaryImmediate() {
        val stats = collectQueueStats()
        lastQueueStats = stats
        _downloadSummaryFlow.value = buildSummary(stats)
    }

    suspend fun getOrCreate(bookUrl: String): CacheBookModel? = withContext(Dispatchers.IO) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext null
        val source = appDb.bookSourceDao.getBookSource(book.origin) ?: return@withContext null
        getOrCreate(source, book)
    }

    @Synchronized
    fun getOrCreate(bookSource: BookSource, book: Book): CacheBookModel {
        cacheBookMap[book.bookUrl]?.let { model ->
            model.bookSource = bookSource
            model.book = book
            return model
        }
        val model = CacheBookModel(bookSource, book, modelHost)
        cacheBookMap[book.bookUrl] = model
        updateSummary()
        return model
    }

    private fun updateBookSource(newBookSource: BookSource) {
        // 只有在必要时才更新，且避免在 getOrCreate 中高频调用
        val sourceUrl = newBookSource.bookSourceUrl
        cacheBookMap.values.forEach { model ->
            if (model.bookSource.bookSourceUrl == sourceUrl && model.bookSource != newBookSource) {
                model.bookSource = newBookSource
            }
        }
    }

    suspend fun start(context: Context, book: Book, selectedIndices: List<Int>) {
        start(
            context = context,
            request = CacheDownloadRequest(
                bookUrl = book.bookUrl,
                selection = ChapterSelection.Indices(selectedIndices.toSet()),
            ),
            isLocal = book.isLocal,
        )
    }

    suspend fun start(context: Context, book: Book, startIndex: Int, endIndex: Int) {
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
        if (!request.hasValidSelection()) return
        isPaused = false
        startCacheBookService(context) {
            action = IntentAction.start
            putRequestExtras(request)
        }
    }

    suspend fun start(context: Context, requests: List<CacheDownloadRequest>) = withContext(Dispatchers.IO) {
        val validRequests = requests.filter { it.hasValidSelection() }
        if (validRequests.isEmpty()) return@withContext
        
        val urls = validRequests.map { it.bookUrl }.toSet()
        val localBookUrls = appDb.bookDao.getCacheableBooks(urls)
            .filter { it.isLocal }
            .map { it.bookUrl }
            .toSet()

        val finalRequests = validRequests.filterNot { it.bookUrl in localBookUrls }
        if (finalRequests.isEmpty()) return@withContext

        isPaused = false
        // 如果请求较多，可以通过 Intent 传递一个特殊的标志让 Service 自己去检查队列，
        // 或者分批发送。这里我们先简单处理，但确保不在主线程做数据库查询。
        finalRequests.forEach { request ->
            startCacheBookService(context) {
                action = IntentAction.start
                putRequestExtras(request)
            }
        }
    }

    private fun android.content.Intent.putRequestExtras(request: CacheDownloadRequest) {
        putExtra("bookUrl", request.bookUrl)
        putExtra("source", request.source.name)
        when (val selection = request.selection) {
            is ChapterSelection.Range -> {
                putExtra("start", selection.start)
                putExtra("end", selection.end)
            }
            is ChapterSelection.Indices -> {
                putExtra("indices", selection.values.toIntArray())
            }
            is ChapterSelection.Single -> {
                putExtra("start", selection.index)
                putExtra("end", selection.index)
            }
        }
    }

    private fun startCacheBookService(context: Context, configIntent: Intent.() -> Unit = {}): Boolean {
        val intent = Intent(context, CacheBookService::class.java).apply(configIntent)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            LogUtils.e("CacheBook", "启动下载服务失败: ${e.localizedMessage}")
            false
        }
    }

    fun remove(context: Context, bookUrl: String) {
        if (!CacheBookService.isRun) {
            removeBookFromService(bookUrl)
            return
        }
        val started = startCacheBookService(context) {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
        }
        if (!started) {
            removeBookFromService(bookUrl)
        }
    }

    suspend fun removeAwait(context: Context, bookUrl: String): Boolean {
        if (!CacheBookService.isRun) {
            return removeBookFromService(bookUrl)
        }
        val requestId = pendingRequestId.incrementAndGet()
        val removeRequest = CompletableDeferred<Boolean>()
        pendingRemoveRequests[requestId] = removeRequest
        val started = startCacheBookService(context) {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
            putExtra("removeRequestId", requestId)
        }
        if (!started) {
            pendingRemoveRequests.remove(requestId)
            return removeBookFromService(bookUrl)
        }
        return try {
            withTimeout(30_000L) {
                removeRequest.await()
            }
        } catch (_: TimeoutCancellationException) {
            pendingRemoveRequests.remove(requestId)
            false
        }
    }

    internal fun completePendingRemoveRequest(requestId: Long, removed: Boolean) {
        pendingRemoveRequests.remove(requestId)?.complete(removed)
    }

    internal fun removeBookFromService(bookUrl: String): Boolean {
        val model = cacheBookMap.remove(bookUrl)
        model?.stop()
        removePendingAdmission(bookUrl)
        stateStore.removeBook(bookUrl)
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
        return model != null
    }

    internal fun removeModelFromService(bookUrl: String, model: CacheBookModel): Boolean {
        val removed = cacheBookMap.remove(bookUrl, model)
        if (!removed) return false
        model.stop()
        stateStore.removeBook(bookUrl)
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
        return true
    }

    fun removeChapter(bookUrl: String, chapterIndex: Int): Boolean {
        return cacheBookMap[bookUrl]?.removeDownload(chapterIndex) == true
    }

    fun stop(context: Context) {
        if (CacheBookService.isRun) {
            val started = startCacheBookService(context) {
                action = IntentAction.stop
            }
            if (!started) {
                close(clearFailureState = false)
            }
        }
    }

    fun pause(context: Context) {
        if (CacheBookService.isRun) {
            val started = startCacheBookService(context) {
                action = IntentAction.pause
            }
            if (!started) {
                pauseAllFromService()
            }
        } else {
            pauseAllFromService()
        }
    }

    fun resume(context: Context): Boolean {
        if (!hasQueuedDownloads) return false
        isPaused = false
        val started = startCacheBookService(context) {
            action = IntentAction.resume
        }
        if (!started) {
            resumeFromService()
        }
        return true
    }

    internal fun pauseAllFromService(): Boolean {
        val hadTasks = hasQueuedDownloads
        if (!hadTasks) return false
        isPaused = true
        cacheBookMap.forEach { (bookUrl, model) ->
            model.pause()
            _queueChangedFlow.tryEmit(bookUrl)
        }
        updateSummary()
        return true
    }

    internal fun resumeFromService() {
        isPaused = false
        cacheBookMap.values.forEach { it.resume() }
        updateSummary()
        cacheBookMap.keys.forEach { _queueChangedFlow.tryEmit(it) }
    }

    fun pauseBook(context: Context, bookUrl: String): Boolean {
        val paused = cacheBookMap[bookUrl]?.pause() == true
        if (paused) {
            updateSummary()
            _queueChangedFlow.tryEmit(bookUrl)
        }
        return paused
    }

    fun resumeBook(context: Context, bookUrl: String): Boolean {
        val resumed = cacheBookMap[bookUrl]?.resume() == true
        if (resumed) {
            isPaused = false
            updateSummary()
            _queueChangedFlow.tryEmit(bookUrl)
            val started = startCacheBookService(context) {
                action = IntentAction.resume
            }
            if (!started) {
                resumeFromService()
            }
        }
        return resumed
    }

    fun pauseChapter(bookUrl: String, chapterIndex: Int): Boolean {
        val paused = cacheBookMap[bookUrl]?.pauseDownload(chapterIndex) == true
        if (paused) {
            updateSummary()
            _queueChangedFlow.tryEmit(bookUrl)
        }
        return paused
    }

    fun resumeChapter(context: Context, bookUrl: String, chapterIndex: Int): Boolean {
        val resumed = cacheBookMap[bookUrl]?.resumeDownload(chapterIndex) == true
        if (resumed) {
            isPaused = false
            updateSummary()
            _queueChangedFlow.tryEmit(bookUrl)
            val started = startCacheBookService(context) {
                action = IntentAction.resume
            }
            if (!started) {
                resumeFromService()
            }
        }
        return resumed
    }

    fun close(clearFailureState: Boolean = false) {
        isPaused = false
        cacheBookMap.forEach { (_, model) -> model.stop() }
        cacheBookMap.clear()
        successDownloadCount.set(0)
        pendingRemoveRequests.values.forEach { it.complete(false) }
        pendingRemoveRequests.clear()
        clearPendingAdmissions()
        if (clearFailureState) {
            stateStore.clear()
        } else {
            stateStore.clearRuntimeState()
        }
        updateSummaryImmediate()
    }

    fun shutdownPreservingPaused() {
        isPaused = false
        val toRemove = ArrayList<String>()
        cacheBookMap.forEach { (bookUrl, model) ->
            val keep = synchronized(model) {
                model.isPaused() || model.pausedIndices().isNotEmpty()
            }
            if (keep) return@forEach
            toRemove.add(bookUrl)
            model.stop()
        }
        toRemove.forEach { cacheBookMap.remove(it) }
        successDownloadCount.set(0)
        pendingRemoveRequests.values.forEach { it.complete(false) }
        pendingRemoveRequests.clear()
        clearPendingAdmissions()
        stateStore.clearRuntimeState()
        updateSummaryImmediate()
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
            return stats.waitingCount + stats.downloadingCount + successDownloadCount.get() + stateStore.state.totalFailure
        }

    val completedCount: Int
        get() = successDownloadCount.get() + stateStore.state.totalFailure

    val downloadSummary: String
        get() {
            val stats = collectQueueStats()
            return buildSummary(stats)
        }

    val isRun: Boolean
        get() = !isPaused && (
                lastQueueStats.waitingCount > 0 ||
                        lastQueueStats.downloadingCount > 0 ||
                        cacheBookMap.values.any { it.hasQueuedDownloads() }
                )

    val hasQueuedDownloads: Boolean
        get() = cacheBookMap.values.any { it.hasQueuedDownloads() } ||
                _pendingAdmissionFlow.value.isNotEmpty()

    val hasPausedDownloads: Boolean
        get() = (isPaused && hasQueuedDownloads) || cacheBookMap.values.any { it.isPaused() }

    val isGloballyPaused: Boolean
        get() = isPaused && hasQueuedDownloads

    private fun buildSummary(stats: QueueStats = collectQueueStats()): String {
        val hasGlobalPause = isPaused && (stats.waitingCount > 0 || stats.downloadingCount > 0)
        val downloadingCount = if (hasGlobalPause) 0 else stats.downloadingCount
        val waitingCount = if (hasGlobalPause) 0 else stats.waitingCount
        val modelPausedCount = cacheBookMap.values.sumOf {
            it.pausedCount()
        }
        val pausedCount = maxOf(stateStore.state.totalPaused, modelPausedCount) + if (hasGlobalPause) {
            stats.waitingCount + stats.downloadingCount
        } else {
            0
        }
        return "下载中:$downloadingCount | 等待:$waitingCount | 暂停:$pausedCount | 失败:${stateStore.state.totalFailure} | 已缓存:${successDownloadCount.get()}"
    }

    private fun CacheDownloadRequest.hasValidSelection(): Boolean {
        return when (val selection = selection) {
            is ChapterSelection.Range -> selection.end >= selection.start
            is ChapterSelection.Indices -> selection.values.isNotEmpty()
            is ChapterSelection.Single -> true
        }
    }

    fun addPendingAdmissions(requests: Iterable<CacheDownloadRequest>) {
        val counts = requests.groupingBy { it.bookUrl }
            .fold(0) { count, request -> count + request.pendingChapterCount() }
            .filterValues { it > 0 }
        if (counts.isEmpty()) return
        _pendingAdmissionFlow.update { pending ->
            pending + counts.mapValues { (bookUrl, count) ->
                pending[bookUrl].orZero() + count
            }
        }
        updateSummary()
        counts.keys.forEach { _queueChangedFlow.tryEmit(it) }
    }

    fun removePendingAdmission(request: CacheDownloadRequest) {
        val chapterCount = request.pendingChapterCount()
        if (chapterCount <= 0) return
        _pendingAdmissionFlow.update { pending ->
            val remaining = pending[request.bookUrl].orZero() - chapterCount
            if (remaining > 0) {
                pending + (request.bookUrl to remaining)
            } else {
                pending - request.bookUrl
            }
        }
        updateSummary()
        _queueChangedFlow.tryEmit(request.bookUrl)
    }

    fun removePendingAdmission(bookUrl: String) {
        if (!_pendingAdmissionFlow.value.containsKey(bookUrl)) return
        _pendingAdmissionFlow.update { it - bookUrl }
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    private fun clearPendingAdmissions() {
        val bookUrls = _pendingAdmissionFlow.value.keys
        if (bookUrls.isEmpty()) return
        _pendingAdmissionFlow.value = emptyMap()
        updateSummary()
        bookUrls.forEach { _queueChangedFlow.tryEmit(it) }
    }

    private fun CacheDownloadRequest.pendingChapterCount(): Int {
        return when (val selection = selection) {
            is ChapterSelection.Range -> selection.end - selection.start + 1
            is ChapterSelection.Indices -> selection.values.size
            is ChapterSelection.Single -> 1
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun notifyTaskQueuesChanged(bookUrl: String) {
        cacheBookMap[bookUrl]?.let { model ->
            stateStore.updateBookQueue(
                bookUrl = bookUrl,
                waitingCount = model.queueCounts().first,
                runningIndices = model.downloadingIndices(),
                pausedIndices = model.pausedIndices(),
            )
            _downloadingIndicesFlow.tryEmit(bookUrl to model.downloadingIndices())
            _downloadErrorFlow.tryEmit(bookUrl to errorIndices(bookUrl))
        }
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    private fun notifyTaskRemoved(bookUrl: String, clearState: Boolean = false) {
        cacheBookMap.remove(bookUrl)
        if (clearState) {
            stateStore.removeBook(bookUrl)
        }
        updateSummary()
        _queueChangedFlow.tryEmit(bookUrl)
    }

    private class ModelHostImpl : CacheBookModel.Host {
        override val stateStore: CacheDownloadStateStore
            get() = CacheBook.stateStore
        override val cacheBookMap: ConcurrentHashMap<String, CacheBookModel>
            get() = CacheBook.cacheBookMap

        override fun incrementSuccessCount(): Int = CacheBook.successDownloadCount.incrementAndGet()
        override fun onTaskQueuesChanged(bookUrl: String) {
            CacheBook.notifyTaskQueuesChanged(bookUrl)
        }
        override fun onTaskRemoved(bookUrl: String, clearState: Boolean) {
            CacheBook.notifyTaskRemoved(bookUrl, clearState)
        }
        override fun emitDownloadingIndices(bookUrl: String, indices: Set<Int>) {
            CacheBook._downloadingIndicesFlow.tryEmit(bookUrl to indices)
        }
        override fun emitDownloadError(bookUrl: String, indices: Set<Int>) {
            CacheBook._downloadErrorFlow.tryEmit(bookUrl to indices)
        }
        override fun emitChapterCached(chapter: BookChapter) {
            CacheBook._cacheSuccessFlow.tryEmit(chapter)
        }
        override fun errorIndices(bookUrl: String): Set<Int> =
            CacheBook.errorIndices(bookUrl)
    }
}
