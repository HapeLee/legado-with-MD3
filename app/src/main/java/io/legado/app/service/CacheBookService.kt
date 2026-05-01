package io.legado.app.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.help.book.update
import io.legado.app.model.CacheBook
import io.legado.app.model.cache.CacheDownloadAdmissionQueue
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.LogUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 缓存书籍服务
 */
class CacheBookService : BaseService() {

    companion object {
        private const val MB = 1024L * 1024L
        private const val DIAGNOSTICS_LOG_INTERVAL_MILLIS = 5_000L

        var isRun = false
            private set
    }

    private val threadCount = OtherConfig.cacheBookThreadCount.coerceIn(1, CacheBook.maxDownloadConcurrency)
    private val maxActiveBookCount = (threadCount * 2).coerceAtLeast(1)
    private val admissionQueue = CacheDownloadAdmissionQueue(maxActiveBookCount)
    private val admissionLock = Any()
    private val admittingBookUrls = hashSetOf<String>()
    private val admissionGenerations = hashMapOf<String, Long>()
    private val admissionBuffers = hashMapOf<String, ArrayDeque<AdmissionRequest>>()
    private val admissionIdleWaiters = hashMapOf<String, MutableList<CompletableDeferred<Unit>>>()
    private lateinit var cachePool: ExecutorCoroutineDispatcher
    private val serviceCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceCommandMutex = Mutex()
    private val downloadJobLock = Any()
    private var downloadJob: Job? = null
    private var notificationContent = appCtx.getString(R.string.service_starting)
    private var mutex = Mutex()
    private var lastDiagnosticsLogTime = 0L
    private val notificationBuilder by lazy {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.offline_cache))
            .setContentIntent(
                activityPendingIntent(
                    MainActivity.createCacheIntent(this),
                    "cacheActivity"
                )
            )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<CacheBookService>(IntentAction.stop)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private data class AdmissionRequest(
        val request: CacheDownloadRequest,
        val fromAdmissionQueue: Boolean,
        val generation: Long,
    )

    override fun onCreate() {
        super.onCreate()
        if (::cachePool.isInitialized) {
            cachePool.close()
        }
        cachePool = Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
        isRun = true
        lifecycleScope.launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                delay(1000)
                drainPendingDownloadRequests()
                logDownloadDiagnostics()
                notificationContent = CacheBook.downloadSummary
                upCacheBookNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var startResult = START_REDELIVER_INTENT
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> {
                    val request = reconstructRequestFromIntent(intent) ?: run {
                        stopIfIdle()
                        return@let
                    }
                    addDownloadRequest(request)
                }
                IntentAction.remove -> {
                    val bookUrl = intent.getStringExtra("bookUrl")
                    val removeRequestId = intent.getLongExtra("removeRequestId", -1L)
                    bookUrl?.let {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val removed = removeBookCompletely(it)
                            if (removeRequestId >= 0L) {
                                CacheBook.completePendingRemoveRequest(removeRequestId, removed)
                            }
                            stopIfIdle()
                        }
                    }
                }
                IntentAction.stop -> {
                    startResult = START_NOT_STICKY
                    stopSelf()
                }
                IntentAction.pause -> {
                    serviceCommandScope.launch {
                        serviceCommandMutex.withLock {
                            CacheBook.pauseAllFromService()
                            notificationContent = CacheBook.downloadSummary
                            upCacheBookNotification()
                        }
                    }
                }
                IntentAction.resume -> {
                    serviceCommandScope.launch {
                        serviceCommandMutex.withLock {
                            CacheBook.resumeFromService()
                            ensureDownloadJob()
                            notificationContent = CacheBook.downloadSummary
                            upCacheBookNotification()
                        }
                    }
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        return startResult
    }

    override fun onDestroy() {
        isRun = false
        if (::cachePool.isInitialized) {
            cachePool.close()
        }
        synchronized(admissionQueue) {
            admissionQueue.clear()
        }
        synchronized(admissionLock) {
            admissionBuffers.clear()
            admittingBookUrls.clear()
            admissionIdleWaiters.values.flatten().forEach { it.complete(Unit) }
            admissionIdleWaiters.clear()
        }
        serviceCommandScope.launch {
            try {
                serviceCommandMutex.withLock {
                    CacheBook.close(clearFailureState = false)
                }
            } finally {
                serviceCommandScope.cancel()
            }
        }
        super.onDestroy()
    }

    private fun reconstructRequestFromIntent(intent: Intent): CacheDownloadRequest? {
        val bookUrl = intent.getStringExtra("bookUrl") ?: return null
        val sourceName = intent.getStringExtra("source")
        val source = sourceName?.let { name ->
            runCatching { CacheDownloadSource.valueOf(name) }.getOrDefault(CacheDownloadSource.Manual)
        } ?: CacheDownloadSource.Manual
        val indices = intent.getIntArrayExtra("indices")
        if (indices != null && indices.isNotEmpty()) {
            return CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Indices(indices.toSet()),
                source = source,
            )
        }
        val start = intent.getIntExtra("start", 0)
        val end = intent.getIntExtra("end", 0)
        if (end < start) return null
        return CacheDownloadRequest(
            bookUrl = bookUrl,
            selection = ChapterSelection.Range(start, end),
            source = source,
        )
    }

    private fun addDownloadRequest(request: CacheDownloadRequest) {
        addDownloadRequestsToQueue(listOf(request))
    }

    private fun addDownloadRequestsToQueue(requests: List<CacheDownloadRequest>) {
        if (requests.isEmpty()) return
        val queuedRequests = mutableListOf<CacheDownloadRequest>()
        val startRequests = mutableListOf<CacheDownloadRequest>()
        synchronized(admissionQueue) {
            // 快照当前已准入的书籍集合；同一批次内同一本书的后续请求直接准入，
            // 绕过 maxActiveBookCount 限制。并发批次各自持有独立的快照，
            // 因此准入上限是尽力而为的（best-effort），非严格保证。
            val activeBookUrls = admittedBookUrls().toMutableSet()
            requests.forEach { request ->
                if (!admissionQueue.shouldQueue(request, activeBookUrls)) {
                    startRequests.add(request)
                    activeBookUrls.add(request.bookUrl)
                    return@forEach
                }
                admissionQueue.add(request)
                queuedRequests.add(request)
            }
        }
        if (queuedRequests.isNotEmpty()) {
            CacheBook.addPendingAdmissions(queuedRequests)
            ensureDownloadJob()
        }
        startRequests.forEach { request ->
            submitDownloadRequest(request, fromAdmissionQueue = false)
        }
    }

    private fun submitDownloadRequest(
        request: CacheDownloadRequest,
        fromAdmissionQueue: Boolean,
    ) {
        val shouldStart = synchronized(admissionLock) {
            val generation = admissionGenerations[request.bookUrl] ?: 0L
            admissionBuffers.getOrPut(request.bookUrl) { ArrayDeque() }
                .addLast(AdmissionRequest(request, fromAdmissionQueue, generation))
            admittingBookUrls.add(request.bookUrl)
        }
        if (shouldStart) {
            startAdmissionJob(request.bookUrl)
        }
    }

    private fun startAdmissionJob(bookUrl: String) {
        execute(executeContext = Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                val admission = nextAdmissionRequest(bookUrl) ?: return@execute
                processAdmissionRequest(admission)
            }
        }.onFinally {
            finishAdmissionJob(bookUrl)
            drainPendingDownloadRequests()
            ensureDownloadJob()
        }
    }

    private fun nextAdmissionRequest(bookUrl: String): AdmissionRequest? {
        return synchronized(admissionLock) {
            val buffer = admissionBuffers[bookUrl] ?: return@synchronized null
            if (buffer.isEmpty()) return@synchronized null
            buffer.removeFirst()
        }
    }

    private suspend fun processAdmissionRequest(admission: AdmissionRequest) {
        val request = admission.request
        if (!admission.isCurrent()) return

        val cacheBook = CacheBook.getOrCreate(request.bookUrl) ?: run {
            markBookAdmissionFailed(request.bookUrl, getString(R.string.error_no_source))
            return
        }
        if (!admission.isCurrent()) {
            CacheBook.removeModelFromService(request.bookUrl, cacheBook)
            return
        }

        val book = cacheBook.book
        val chapterCount = appDb.bookChapterDao.getChapterCount(request.bookUrl)

        if (chapterCount == 0) {
            cacheBook.setLoading()
            mutex.withLock {
                val name = book.name
                if (!admission.isCurrent()) {
                    CacheBook.removeModelFromService(request.bookUrl, cacheBook)
                    return
                }
                if (book.tocUrl.isEmpty()) {
                    kotlin.runCatching {
                        WebBook.getBookInfoAwait(cacheBook.bookSource, book)
                    }.onFailure {
                        removeDownload(request.bookUrl)
                        markBookAdmissionFailed(
                            request.bookUrl,
                            getString(R.string.error_get_book_info)
                        )
                        AppLog.put(
                            "《$name》目录为空且加载详情页失败\n${it.localizedMessage}",
                            it,
                            true
                        )
                        return
                    }
                }

                if (!admission.isCurrent()) {
                    CacheBook.removeModelFromService(request.bookUrl, cacheBook)
                    return
                }
                WebBook.getChapterListAwait(cacheBook.bookSource, book).onFailure {
                    if (book.totalChapterNum > 0) {
                        book.totalChapterNum = 0
                        book.update()
                    }
                    removeDownload(request.bookUrl)
                    markBookAdmissionFailed(
                        request.bookUrl,
                        getString(R.string.error_get_chapter_list)
                    )
                    AppLog.put(
                        "《$name》目录为空且加载目录失败\n${it.localizedMessage}",
                        it,
                        true
                    )
                    return
                }.getOrNull()?.let { toc ->
                    appDb.bookChapterDao.insert(*toc.toTypedArray())
                }

                book.update()
            }
        }

        if (!admission.isCurrent()) {
            CacheBook.removeModelFromService(request.bookUrl, cacheBook)
            return
        }

        //添加章节到下载队列
        cacheBook.addRequest(request)
        if (admission.fromAdmissionQueue) {
            CacheBook.removePendingAdmission(request)
        }

        notificationContent = CacheBook.downloadSummary
        upCacheBookNotification()
    }

    private fun AdmissionRequest.isCurrent(): Boolean {
        return synchronized(admissionLock) {
            admissionGenerations[request.bookUrl].orZero() == generation
        }
    }

    private fun admittedBookUrls(): Set<String> {
        return CacheBook.cacheBookMap.keys.toHashSet().apply {
            synchronized(admissionLock) {
                addAll(admittingBookUrls)
            }
        }
    }

    private fun hasPendingDownloadRequests(): Boolean {
        return synchronized(admissionQueue) {
            !admissionQueue.isEmpty()
        }
    }

    private fun hasAdmittingRequests(): Boolean {
        return synchronized(admissionLock) {
            admittingBookUrls.isNotEmpty()
        }
    }

    private fun removeQueuedBook(bookUrl: String): Boolean {
        return synchronized(admissionQueue) {
            admissionQueue.removeBook(bookUrl)
        }
    }

    private suspend fun removeBookCompletely(bookUrl: String): Boolean {
        val removedQueued = removeQueuedBook(bookUrl)
        val removedAdmission = cancelAdmission(bookUrl)
        val removedActive = CacheBook.removeBookFromService(bookUrl)
        waitAdmissionIdle(bookUrl)
        return removedQueued || removedAdmission || removedActive
    }

    private fun markBookAdmissionFailed(bookUrl: String, message: String) {
        removeQueuedBook(bookUrl)
        CacheBook.markBookFailed(bookUrl, message)
    }

    private fun cancelAdmission(bookUrl: String): Boolean {
        return synchronized(admissionLock) {
            admissionGenerations[bookUrl] = admissionGenerations[bookUrl].orZero() + 1L
            val removedBuffered = admissionBuffers.remove(bookUrl)?.isNotEmpty() == true
            removedBuffered || bookUrl in admittingBookUrls
        }
    }

    private suspend fun waitAdmissionIdle(bookUrl: String) {
        val waiter = synchronized(admissionLock) {
            if (bookUrl !in admittingBookUrls) {
                null
            } else {
                CompletableDeferred<Unit>().also {
                    admissionIdleWaiters.getOrPut(bookUrl) { mutableListOf() }.add(it)
                }
            }
        }
        waiter?.await()
    }

    private fun finishAdmissionJob(bookUrl: String) {
        var restart = false
        val waiters = synchronized(admissionLock) {
            val buffer = admissionBuffers[bookUrl]
            if (buffer != null && buffer.isNotEmpty()) {
                restart = true
                emptyList()
            } else {
                admissionBuffers.remove(bookUrl)
                admittingBookUrls.remove(bookUrl)
                admissionIdleWaiters.remove(bookUrl).orEmpty()
            }
        }
        if (restart) {
            startAdmissionJob(bookUrl)
            return
        }
        waiters.forEach { it.complete(Unit) }
    }

    private fun drainPendingDownloadRequests() {
        while (true) {
            val request = synchronized(admissionQueue) {
                admissionQueue.pollReady(admittedBookUrls())
            } ?: return
            submitDownloadRequest(request, fromAdmissionQueue = true)
        }
    }

    private fun ensureDownloadJob() {
        synchronized(downloadJobLock) {
            if (downloadJob?.isActive == true) return
            downloadJob = lifecycleScope.launch(cachePool) {
                runDownloadLoop()
            }
        }
    }


    private fun removeDownload(bookUrl: String?) {
        CacheBook.cacheBookMap[bookUrl]?.stop()
        if (CacheBook.isRun) {
            ensureDownloadJob()
            return
        }
        stopIfIdle()
    }

    private suspend fun runDownloadLoop() {
        try {
            while (currentCoroutineContext().isActive) {
                drainPendingDownloadRequests()
                if (CacheBook.isGloballyPaused) {
                    delay(200)
                    continue
                }
                if (!CacheBook.isRun) {
                    if (!hasPendingDownloadRequests() && !hasAdmittingRequests()) break
                    delay(200)
                    continue
                }
                CacheBook.startProcessJob(cachePool)
            }
        } finally {
            val finishedJob = currentCoroutineContext()[Job]
            synchronized(downloadJobLock) {
                if (downloadJob == finishedJob) {
                    downloadJob = null
                }
            }
            stopIfIdle()
        }
    }

    private fun stopIfIdle() {
        if (!CacheBook.isRun && !hasPendingDownloadRequests() && !hasAdmittingRequests()) {
            stopSelf()
        }
    }

    private fun upCacheBookNotification() {
        val total = CacheBook.totalCount
        val progress = CacheBook.completedCount
        val pendingBookCount = synchronized(admissionQueue) { admissionQueue.size }
        val summary = if (pendingBookCount > 0) {
            "${CacheBook.downloadSummary} | 待入队:$pendingBookCount"
        } else {
            CacheBook.downloadSummary
        }

        notificationBuilder.apply {
            setContentText(summary)
            if (total > 0) {
                setProgress(total, progress, false)
            } else {
                setProgress(0, 0, true)
            }
        }

        notificationManager.notify(NotificationId.CacheBookService, notificationBuilder.build())
    }

    private fun logDownloadDiagnostics() {
        val now = System.currentTimeMillis()
        if (now - lastDiagnosticsLogTime < DIAGNOSTICS_LOG_INTERVAL_MILLIS) return
        lastDiagnosticsLogTime = now

        val pendingBookCount = synchronized(admissionQueue) { admissionQueue.size }
        if (!CacheBook.isRun && pendingBookCount == 0 && !hasAdmittingRequests()) return

        val diagnostics = CacheBook.diagnostics()
        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val totalMemoryMb = runtime.totalMemory() / MB
        val maxMemoryMb = runtime.maxMemory() / MB
        LogUtils.d("CacheBookDiagnostics") {
            "activeBooks=${diagnostics.activeBookCount}, " +
                    "admittingBooks=${admittingBookCount()}, " +
                    "pendingBooks=$pendingBookCount, " +
                    "waitingChapters=${diagnostics.waitingChapterCount}, " +
                    "runningChapters=${diagnostics.runningChapterCount}, " +
                    "chapterTasks=${diagnostics.trackedChapterTaskCount}, " +
                    "loadingBooks=${diagnostics.loadingBookCount}, " +
                    "retryingBooks=${diagnostics.retryingBookCount}, " +
                    "configuredThreads=$threadCount, " +
                    "heap=${usedMemoryMb}MB/${totalMemoryMb}MB max=${maxMemoryMb}MB"
        }
    }

    private fun admittingBookCount(): Int {
        return synchronized(admissionLock) {
            admittingBookUrls.size
        }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        startForeground(NotificationId.CacheBookService, notification)
    }

}
