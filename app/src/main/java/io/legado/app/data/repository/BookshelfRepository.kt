package io.legado.app.data.repository

import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.model.LocalBookMetricTarget
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.model.LocalBookshelfSort
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.model.localBook.localBookFileSize
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.cnCompare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import splitties.init.appCtx
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfRepository(
    private val database: AppDatabase,
    private val settingsGateway: BookshelfSettingsGateway,
    cacheFile: File = File(appCtx.cacheDir, DEFAULT_CACHE_FILE_NAME),
    private val scope: CoroutineScope = defaultRepositoryScope(),
) {
    private companion object {
        const val DEFAULT_CACHE_FILE_NAME = "local_bookshelf_metrics_v1.json"

        fun defaultRepositoryScope(): CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                AppLog.put("本地书架排序后台协程异常: ${throwable.localizedMessage}", throwable)
            }
        )
    }

    private data class CachedMetricPass(
        val resolved: MutableMap<String, Long>,
        val pending: List<LocalBookMetricTarget>,
    )

    private val cache = LocalBookMetricsCache(cacheFile)
    private val fileSizeRefresh = MutableStateFlow(0L)

    private val localTargetsFlow: Flow<List<LocalBookMetricTarget>> =
        database.bookDao.flowLocalBookMetricTargets()
            .debounceSkippingFirst(SHELF_INVALIDATE_DEBOUNCE_MS)
            .distinctUntilChanged()

    private val fileSizeMetricsFlow: Flow<Map<String, Long>> = buildFileSizeMetricsFlow()

    fun flowSortSettings(): Flow<BookshelfSettings> = settingsGateway.settings

    fun flowLocalMetrics(sort: Int): Flow<Map<String, Long>> = when (sort) {
        LocalBookshelfSort.FILE_SIZE -> fileSizeMetricsFlow
        else -> flowOf(emptyMap())
    }

    fun invalidateLocalMetrics(sort: Int) {
        if (sort != LocalBookshelfSort.FILE_SIZE) return
        cache.clear(sort)
        fileSizeRefresh.update { it + 1 }
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
                LocalBookMetricsCache.Lookup.Miss -> return null
            }
        }
        return result
    }

    private fun buildFileSizeMetricsFlow(): Flow<Map<String, Long>> {
        var hasEmitted = false
        return combine(localTargetsFlow, fileSizeRefresh) { targets, _ -> targets }
            .transformLatest { targets ->
                cache.ensureLoaded()
                val initial = collectCachedFileSizes(targets)
                if (initial.pending.isEmpty() || !hasEmitted || initial.resolved.isNotEmpty()) {
                    emit(HashMap(initial.resolved))
                    hasEmitted = true
                }
                if (initial.pending.isNotEmpty()) {
                    val completed = resolveFileSizes(targets, initial)
                    emit(completed)
                    hasEmitted = true
                } else {
                    pruneAndPersist(targets)
                }
            }
            .distinctUntilChanged()
            .catch { error ->
                if (error is CancellationException) throw error
                AppLog.put("本地文件大小排序指标计算失败: ${error.localizedMessage}", error)
                emit(emptyMap())
            }
            .flowOn(Dispatchers.IO)
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1,
            )
    }

    private fun collectCachedFileSizes(targets: List<LocalBookMetricTarget>): CachedMetricPass {
        val resolved = HashMap<String, Long>(targets.size)
        val pending = ArrayList<LocalBookMetricTarget>()
        for (target in targets) {
            val stamp = fileSizeStamp(target.type, target.originName, target.latestChapterTime)
            when (val hit = cache.lookupFileSize(target.bookUrl, stamp)) {
                is LocalBookMetricsCache.Lookup.Hit -> resolved[target.bookUrl] = hit.value
                LocalBookMetricsCache.Lookup.KnownMissing -> Unit
                LocalBookMetricsCache.Lookup.Miss -> pending.add(target)
            }
        }
        return CachedMetricPass(resolved, pending)
    }

    private suspend fun resolveFileSizes(
        targets: List<LocalBookMetricTarget>,
        pass: CachedMetricPass,
    ): Map<String, Long> {
        try {
            for (target in pass.pending) {
                currentCoroutineContext().ensureActive()
                val stamp = fileSizeStamp(target.type, target.originName, target.latestChapterTime)
                val size = localBookFileSize(target.bookUrl, target.mayBeMangaDirectory)
                cache.putFileSize(target.bookUrl, stamp, size)
                if (size != null) {
                    pass.resolved[target.bookUrl] = size
                }
            }
            cache.prune(targets.mapTo(HashSet(targets.size)) { it.bookUrl })
        } finally {
            cache.persistIfDirty()
        }
        return HashMap(pass.resolved)
    }

    private fun pruneAndPersist(targets: List<LocalBookMetricTarget>) {
        cache.prune(targets.mapTo(HashSet(targets.size)) { it.bookUrl })
        cache.persistIfDirty()
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
