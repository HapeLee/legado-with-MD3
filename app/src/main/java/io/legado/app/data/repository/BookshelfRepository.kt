package io.legado.app.data.repository

import io.legado.app.constant.AppLog
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.model.LocalBookMetricTarget
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.model.LocalBookMetricInvalidation
import io.legado.app.domain.model.LocalBookshelfSort
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.model.localBook.localBookFileSize
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.cnCompare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfRepository(
    private val bookDao: BookDao,
    private val settingsGateway: BookshelfSettingsGateway,
    cacheFile: File = File(appCtx.cacheDir, DEFAULT_CACHE_FILE_NAME),
    private val scope: CoroutineScope = defaultRepositoryScope(),
    private val fileSizeReader: suspend (bookUrl: String, mayBeMangaDirectory: Boolean) -> Long? =
        ::localBookFileSize,
) {
    internal companion object {
        const val DEFAULT_CACHE_FILE_NAME = "local_bookshelf_metrics_v1.json"
        private const val PERSIST_DEBOUNCE_MS = 300L

        fun defaultRepositoryScope(): CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                AppLog.put("本地书架排序后台协程异常: ${throwable.localizedMessage}", throwable)
            }
        )
    }

    private data class PendingMetricTarget(
        val target: LocalBookMetricTarget,
        val stamp: String,
        val token: Long,
        val generation: Long,
    )

    /** 在途测量的共享键：同一本书、同一校验戳、同一刷新代次只读一次。 */
    private data class InFlightKey(
        val bookUrl: String,
        val stamp: String,
        val generation: Long,
    )

    /** @param accepted 测量结果是否已按发起时的代次写回缓存；为 false 说明期间发生了刷新或单书失效。 */
    private class MeasureOutcome(val value: Long?, val accepted: Boolean)

    private data class CachedMetricPass(
        val batchToken: Long,
        val targets: List<LocalBookMetricTarget>,
        val resolved: MutableMap<String, Long>,
        val pending: List<PendingMetricTarget>,
    )

    private val cache = LocalBookMetricsCache(cacheFile)
    private val fileSizeRefresh = MutableStateFlow(0L)
    private val inFlightLock = Any()
    private val inFlightMeasures = HashMap<InFlightKey, Deferred<MeasureOutcome>>()
    private val persistScheduled = AtomicBoolean(false)

    /** 导入/重导/重绑发出的单书失效；订阅时会把仓库创建前积压的信号一并补发。 */
    private val metricInvalidationListener: (String) -> Unit = { invalidateLocalBookMetrics(it) }

    init {
        LocalBookMetricInvalidation.register(metricInvalidationListener)
        scope.coroutineContext[Job]?.invokeOnCompletion {
            LocalBookMetricInvalidation.unregister(metricInvalidationListener)
        }
    }

    fun flowSortSettings(): Flow<BookshelfSettings> = settingsGateway.settings

    /**
     * 按当前书单中实际参与排序的本地书按需读取/计算排序指标。
     *
     * - [booksFlow] 在当前收集周期内只订阅一次，排序方式在 [effectiveSortFlow] 上切换时不会重订数据库流。
     * - 非本地指标排序或书单不含本地书时直接配对空指标，不加载缓存、不扫描其他分组的本地文件与 SAF 目录。
     * - 多个分组同时订阅时复用同一份双层缓存。
     */
    fun flowBooksWithLocalMetrics(
        booksFlow: Flow<List<BookShelfItem>>,
        effectiveSortFlow: Flow<Int>,
    ): Flow<Pair<List<BookShelfItem>, Map<String, Long>>> = channelFlow {
        val sharedBooks = booksFlow.shareIn(this, SharingStarted.Eagerly, replay = 1)
        val metricsFlow = effectiveSortFlow
            .distinctUntilChanged()
            .flatMapLatest { effectiveSort ->
                flowLocalMetrics(effectiveSort, sharedBooks)
            }
        combine(sharedBooks, metricsFlow) { books, metrics ->
            books to metrics
        }.distinctUntilChanged().collect { send(it) }
    }

    fun flowBooksWithLocalMetrics(
        booksFlow: Flow<List<BookShelfItem>>,
        effectiveSort: Int,
    ): Flow<Pair<List<BookShelfItem>, Map<String, Long>>> {
        if (!LocalBookshelfSort.isMetric(effectiveSort)) {
            return booksFlow.map { books -> books to emptyMap() }
        }
        return flowBooksWithLocalMetrics(booksFlow, flowOf(effectiveSort))
    }

    fun flowLocalMetrics(
        sort: Int,
        booksFlow: Flow<List<BookShelfItem>>,
    ): Flow<Map<String, Long>> = when (sort) {
        LocalBookshelfSort.FILE_SIZE -> buildFileSizeMetricsFlow(
            booksFlow
                .map { books -> books.extractLocalMetricTargets() }
                .distinctUntilChanged()
        )
        else -> flowOf(emptyMap())
    }

    fun flowLocalMetrics(
        sort: Int,
        books: List<BookShelfItem>,
    ): Flow<Map<String, Long>> = flowLocalMetrics(sort, flowOf(books))

    fun invalidateLocalMetrics(sort: Int) {
        if (sort != LocalBookshelfSort.FILE_SIZE) return
        val generation = cache.clear(sort)
        cancelStaleMeasures { it.generation < generation }
        fileSizeRefresh.update { it + 1 }
        schedulePersist()
    }

    fun invalidateLocalBookMetrics(bookUrl: String) {
        if (bookUrl.isBlank()) return
        val generation = cache.invalidateFileSize(bookUrl)
        cancelStaleMeasures { it.bookUrl == bookUrl && it.generation < generation }
        fileSizeRefresh.update { it + 1 }
        schedulePersist()
    }

    /**
     * 取消已被刷新或单书失效作废的在途测量。
     *
     * 结果本来就会在写回时按代次丢弃，这里提前取消是为了让递归扫描（逐项检查取消）立即停下，
     * 不与新一轮读取争抢同一个 DocumentsProvider；不响应取消的阻塞查询仍由写回校验兜底。
     */
    private fun cancelStaleMeasures(predicate: (InFlightKey) -> Boolean) {
        val stale = synchronized(inFlightLock) {
            val removed = ArrayList<Deferred<MeasureOutcome>>()
            val iterator = inFlightMeasures.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (predicate(entry.key)) {
                    removed.add(entry.value)
                    iterator.remove()
                }
            }
            removed
        }
        stale.forEach { it.cancel() }
    }

    /**
     * 仅从已落盘/内存缓存中读取本地指标快照，绝不触发任何本地文件扫描。
     * 若当前列表存在未命中缓存的本地书，返回 null 让预加载跳过，避免预热错顺序或抢先扫盘。
     */
    internal fun tryGetCachedMetrics(sort: Int, books: List<BookShelfItem>): Map<String, Long>? {
        if (sort != LocalBookshelfSort.FILE_SIZE) return emptyMap()
        val localBooks = books.filter { it.isLocal }
        if (localBooks.isEmpty()) return emptyMap()
        cache.ensureLoaded()
        val result = HashMap<String, Long>(localBooks.size)
        for (book in localBooks) {
            val stamp = fileSizeStamp(book.type, book.originName, book.latestChapterTime)
            when (val hit = cache.lookupFileSize(book.bookUrl, stamp)) {
                is LocalBookMetricsCache.Lookup.Hit -> result[book.bookUrl] = hit.value
                LocalBookMetricsCache.Lookup.KnownMissing -> Unit
                is LocalBookMetricsCache.Lookup.Miss -> return null
            }
        }
        return result
    }

    private fun buildFileSizeMetricsFlow(
        targetsFlow: Flow<List<LocalBookMetricTarget>>,
    ): Flow<Map<String, Long>> = flow {
        var hasEmitted = false
        emitAll(
            combine(targetsFlow, fileSizeRefresh) { targets, _ -> targets }
                .transformLatest { targets ->
                    if (targets.isEmpty()) {
                        emit(emptyMap())
                        hasEmitted = true
                        return@transformLatest
                    }
                    val initial = withContext(Dispatchers.IO) {
                        cache.ensureLoaded()
                        collectCachedFileSizes(targets)
                    }
                    if (initial.pending.isEmpty()) {
                        if (initial.targets.all { cache.isTokenValid(it.bookUrl, initial.batchToken) }) {
                            emit(HashMap(initial.resolved))
                            hasEmitted = true
                        }
                        withContext(Dispatchers.IO) {
                            pruneAndPersistIfDirty()
                        }
                    } else {
                        if ((!hasEmitted || initial.resolved.isNotEmpty()) &&
                            initial.targets.all { cache.isTokenValid(it.bookUrl, initial.batchToken) }
                        ) {
                            emit(HashMap(initial.resolved))
                            hasEmitted = true
                        }
                        val completed = withContext(Dispatchers.IO) {
                            resolveFileSizes(initial)
                        }
                        if (completed != null) {
                            emit(completed)
                            hasEmitted = true
                        }
                    }
                }
                .distinctUntilChanged()
                .catch { error ->
                    if (error is CancellationException) throw error
                    AppLog.put("本地文件大小排序指标计算失败: ${error.localizedMessage}", error)
                    emit(emptyMap())
                }
        )
    }

    private fun collectCachedFileSizes(targets: List<LocalBookMetricTarget>): CachedMetricPass {
        val batchToken = cache.currentEpoch()
        val resolved = HashMap<String, Long>(targets.size)
        val pending = ArrayList<PendingMetricTarget>()
        for (target in targets) {
            val stamp = fileSizeStamp(target.type, target.originName, target.latestChapterTime)
            when (val hit = cache.lookupFileSize(target.bookUrl, stamp)) {
                is LocalBookMetricsCache.Lookup.Hit -> resolved[target.bookUrl] = hit.value
                LocalBookMetricsCache.Lookup.KnownMissing -> Unit
                is LocalBookMetricsCache.Lookup.Miss -> pending.add(
                    PendingMetricTarget(
                        target = target,
                        stamp = stamp,
                        token = hit.token,
                        generation = hit.generation,
                    )
                )
            }
        }
        return CachedMetricPass(
            batchToken = batchToken,
            targets = targets,
            resolved = resolved,
            pending = pending,
        )
    }

    private suspend fun resolveFileSizes(pass: CachedMetricPass): Map<String, Long>? {
        val completed = try {
            resolvePendingFileSizes(pass)
        } catch (error: Throwable) {
            // 被取消（切换分组、书单变化）时协程已不能再执行挂起的数据库查询，
            // 已写入内存的测量结果交给仓库作用域里的合并落盘任务处理
            schedulePersist()
            throw error
        }
        pruneAndPersistIfDirty()
        if (!completed) return null
        if (!pass.targets.all { cache.isTokenValid(it.bookUrl, pass.batchToken) }) {
            return null
        }
        return HashMap(pass.resolved)
    }

    /** @return false 表示期间发生了刷新或单书失效，本轮结果作废。 */
    private suspend fun resolvePendingFileSizes(pass: CachedMetricPass): Boolean {
        for (item in pass.pending) {
            currentCoroutineContext().ensureActive()
            val size = when (val cached = cache.lookupFileSize(item.target.bookUrl, item.stamp)) {
                is LocalBookMetricsCache.Lookup.Hit -> cached.value
                LocalBookMetricsCache.Lookup.KnownMissing -> null
                is LocalBookMetricsCache.Lookup.Miss -> {
                    if (!cache.isTokenValid(item.target.bookUrl, item.token)) {
                        return false
                    }
                    // 等待共享测量是可取消的挂起点：切换分组或书单变化时，
                    // 旧的收集周期可立即退出，不必等阻塞在 SAF 查询里的读取返回
                    val outcome = try {
                        sharedFileSizeMeasure(item).await()
                    } catch (cancelled: CancellationException) {
                        // 自身被取消就继续向上抛；否则是测量因失效被取消，本轮作废等待下一轮
                        currentCoroutineContext().ensureActive()
                        return false
                    }
                    if (!outcome.accepted) {
                        return false
                    }
                    outcome.value
                }
            }
            currentCoroutineContext().ensureActive()
            if (size != null && size >= 0L) {
                pass.resolved[item.target.bookUrl] = size
            }
        }
        return true
    }

    /**
     * 获取（或发起）一本书的共享大小测量。
     *
     * 书架会同时收集当前分组与全部分组的书单，冷缓存下多个订阅可能同时未命中同一本书；
     * 这里按「书籍 + 校验戳 + 刷新代次」合并在途读取，保证同一代次内只递归读取一次。
     * 测量在仓库作用域中执行，单个订阅取消不会打断其他仍在等待的订阅，结果写入后合并落盘；
     * 刷新或单书失效会推进代次并取消旧代次的测量，之后的未命中会发起新读取。
     */
    private fun sharedFileSizeMeasure(item: PendingMetricTarget): Deferred<MeasureOutcome> {
        val key = InFlightKey(item.target.bookUrl, item.stamp, item.generation)
        synchronized(inFlightLock) {
            inFlightMeasures[key]?.let { return it }
            // 与 cancelStaleMeasures 在同一把锁内互斥：失效先推进代次再取消，
            // 因此这里要么看到代次已失效直接放弃，要么登记后必定被随后的取消覆盖
            if (!cache.isTokenValid(item.target.bookUrl, item.token)) {
                return CompletableDeferred(MeasureOutcome(value = null, accepted = false))
            }
            val deferred = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val measured = fileSizeReader(item.target.bookUrl, item.target.mayBeMangaDirectory)
                ensureActive()
                val accepted = cache.putFileSize(item.target.bookUrl, item.stamp, measured, item.token)
                if (accepted) schedulePersist()
                MeasureOutcome(value = measured, accepted = accepted)
            }
            inFlightMeasures[key] = deferred
            deferred.invokeOnCompletion {
                synchronized(inFlightLock) {
                    if (inFlightMeasures[key] === deferred) inFlightMeasures.remove(key)
                }
            }
            deferred.start()
            return deferred
        }
    }

    private suspend fun pruneAndPersistIfDirty() {
        if (!cache.isDirty()) return
        val snapshotEpoch = cache.currentEpoch()
        val activeUrls = bookDao.getLocalShelfBookUrls().toHashSet()
        cache.prune(activeUrls, maxEpoch = snapshotEpoch)
        cache.persistIfDirty()
    }

    /**
     * 合并落盘：短时间内的多次请求（批量重导、后台测量逐本完成）只写一次缓存文件。
     */
    private fun schedulePersist() {
        if (!persistScheduled.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                delay(PERSIST_DEBOUNCE_MS)
            } finally {
                persistScheduled.set(false)
            }
            cache.ensureLoaded()
            pruneAndPersistIfDirty()
        }
    }

    private fun List<BookShelfItem>.extractLocalMetricTargets(): List<LocalBookMetricTarget> {
        if (isEmpty()) return emptyList()
        var targets: ArrayList<LocalBookMetricTarget>? = null
        for (book in this) {
            if (book.isLocal) {
                val list = targets ?: ArrayList<LocalBookMetricTarget>().also { targets = it }
                list.add(
                    LocalBookMetricTarget(
                        bookUrl = book.bookUrl,
                        originName = book.originName,
                        type = book.type,
                        latestChapterTime = book.latestChapterTime,
                    )
                )
            }
        }
        return targets ?: emptyList()
    }

    private fun fileSizeStamp(type: Int, originName: String, latestChapterTime: Long): String =
        "$type|$originName|$latestChapterTime"

    fun sortBooks(
        list: List<BookShelfItem>,
        group: BookGroup?,
        sort: Int,
        sortOrder: Int,
        localMetrics: Map<String, Long> = emptyMap(),
    ): List<BookShelfItem> {
        val effectiveSort = LocalBookshelfSort.resolveEffectiveSort(group?.bookSort, sort)
        if (!LocalBookshelfSort.isMetric(effectiveSort)) {
            return sortByStandard(list, effectiveSort, sortOrder)
        }
        val baseSort = LocalBookshelfSort.resolveBaseSort(sort)
        val baseSorted = sortByStandard(list, baseSort, sortOrder)
        val localBooks = baseSorted.filter { it.isLocal }
        if (localBooks.isEmpty()) return baseSorted

        val isDescending = sortOrder == 1
        val measured = ArrayList<Pair<BookShelfItem, Long>>(localBooks.size)
        val unknown = ArrayList<BookShelfItem>()
        for (book in localBooks) {
            val value = localMetrics[book.bookUrl]
            if (value == null) unknown.add(book) else measured.add(book to value)
        }
        val metricOrder = compareBy<Pair<BookShelfItem, Long>> { it.second }
        val sortedMeasured = measured.sortedWith(
            (if (isDescending) metricOrder.reversed() else metricOrder)
                .thenByDescending { it.first.durChapterTime }
                .thenBy { it.first.bookUrl }
        ).map { it.first }
        val sortedUnknown = unknown.sortedWith(
            compareByDescending<BookShelfItem> { it.durChapterTime }
                .thenBy { it.bookUrl }
        )
        val sortedLocal = ArrayList<BookShelfItem>(localBooks.size).apply {
            addAll(sortedMeasured)
            addAll(sortedUnknown)
        }
        val result = baseSorted.toMutableList()
        var localIndex = 0
        for (index in result.indices) {
            if (result[index].isLocal) {
                result[index] = sortedLocal[localIndex++]
            }
        }
        return result
    }

    private fun sortByStandard(
        list: List<BookShelfItem>,
        bookSort: Int,
        sortOrder: Int,
    ): List<BookShelfItem> {
        val isDescending = sortOrder == 1
        return when (bookSort) {
            1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
            else list.sortedBy { it.latestChapterTime }

            2 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
            else
                list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

            3 -> if (isDescending) list.sortedByDescending { it.order }
            else list.sortedBy { it.order }

            4 -> if (isDescending) list.sortedByDescending {
                max(
                    it.latestChapterTime,
                    it.durChapterTime
                )
            }
            else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

            5 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
            else
                list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }

            else -> if (isDescending) list.sortedByDescending { it.durChapterTime }
            else list.sortedBy { it.durChapterTime }
        }
    }
}
