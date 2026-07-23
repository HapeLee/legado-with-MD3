package io.legado.app.model

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ChapterPageCount
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.CustomTipPlaceholder
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.service.FullBookPageService
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import kotlin.math.roundToInt

object FullBookPaginator : KoinComponent {
    private val otherSettingsGateway: OtherSettingsGateway by inject()
    private val readSettingsGateway: ReadSettingsGateway by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutex = Mutex()

    private val _state = MutableStateFlow(PaginationState())
    val state = _state.asStateFlow()

    fun start(book: Book?) {
        if (book == null || !book.isLocal) {
            stop()
            return
        }

        if (!isFullPagePlaceholderActive()) {
            stop()
            return
        }

        scope.launch {
            mutex.withLock {
                job?.cancelAndJoin()
                val layoutKey = getLayoutKey()
                _state.update { it.copy(isRunning = true, progress = 0, total = 0) }
                job = launch {
                    FullBookPageService.start(appCtx)
                    paginate(book, layoutKey)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    private suspend fun paginate(book: Book, layoutKey: String) {
        try {
            val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            val existingCounts = appDb.chapterPageCountDao.getByBookAndLayout(book.bookUrl, layoutKey)
                .associateBy { it.chapterIndex }

            // 删除旧的布局缓存，保持数据库精简
            appDb.chapterPageCountDao.deleteOldLayouts(book.bookUrl, layoutKey)

            val processor = ContentProcessor.get(book)
            val totalChapters = chapters.size
            _state.update { it.copy(total = totalChapters) }

            // 初始化缓存（如果之前没有或者不匹配）
            refreshCache(book.bookUrl, layoutKey, totalChapters)

            for ((index, chapter) in chapters.withIndex()) {
                if (!state.value.isRunning) break

                // 更新进度通知
                _state.update { it.copy(progress = index + 1) }

                if (existingCounts.containsKey(chapter.index)) continue

                try {
                    val content = BookHelp.getContent(book, chapter) ?: continue
                    val displayTitle = chapter.getDisplayTitle(
                        processor.getTitleReplaceRules(),
                        book.getUseReplaceRule(otherSettingsGateway.currentSettings.replaceEnableDefault),
                        chineseConverterType = readSettingsGateway.currentSettings.chineseConverterType,
                    )

                    val bookContent = processor.getContent(book, chapter, content, includeTitle = false)

                    if (ChapterProvider.visibleWidth <= 0) break

                    val textChapter = ChapterProvider.getTextChapterAsync(
                        this@FullBookPaginator.scope, book, chapter, displayTitle,
                        bookContent,
                        chapters.size,
                    )

                    // 等待排版完成
                    var pageCount = 0
                    while (true) {
                        textChapter.layoutChannel.receiveCatching().getOrNull() ?: break
                        pageCount++
                    }

                    appDb.chapterPageCountDao.insert(
                        ChapterPageCount(
                            bookUrl = book.bookUrl,
                            chapterIndex = chapter.index,
                            layoutKey = layoutKey,
                            pageCount = pageCount
                        )
                    )
                    // 每一章完成后更新缓存标记，让下一帧 UI 能刷新
                    markCacheDirty()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }
        } finally {
            _state.update { it.copy(isRunning = false) }
        }
    }

    /**
     * 生成当前布局配置的唯一特征码
     */
    fun getLayoutKey(): String {
        val config = ReadBookConfig
        val sb = StringBuilder()
        sb.append(ChapterProvider.visibleWidth).append(",")
        sb.append(ChapterProvider.visibleHeight).append(",")
        sb.append(config.textSize).append(",")
        sb.append(config.titleSize).append(",")
        sb.append(config.lineSpacingExtra).append(",")
        sb.append(config.paragraphSpacing).append(",")
        sb.append(config.paddingLeft).append(",")
        sb.append(config.paddingTop).append(",")
        sb.append(config.paddingRight).append(",")
        sb.append(config.paddingBottom).append(",")
        sb.append(config.textFont).append(",")
        sb.append(config.titleFont).append(",")
        sb.append(config.textBold).append(",")
        sb.append(config.titleBold).append(",")
        sb.append(config.textItalic).append(",")
        sb.append(config.paragraphIndent).append(",")
        sb.append(config.useZhLayout)
        return MD5Utils.md5Encode16(sb.toString())
    }

    /**
     * 检测当前激活的页眉/页脚是否使用了相关占位符
     */
    private fun isFullPagePlaceholderActive(): Boolean {
        val config = ReadBookConfig
        val templates = listOf(
            config.customTipHeaderLeft,
            config.customTipHeaderMiddle,
            config.customTipHeaderRight,
            config.customTipFooterLeft,
            config.customTipFooterMiddle,
            config.customTipFooterRight
        )
        
        val targetKeys = setOf(
            CustomTipPlaceholder.FULL_PAGE_INDEX.key,
            CustomTipPlaceholder.FULL_PAGE_SIZE.key
        )
        
        return templates.any { template ->
            CustomTipPlaceholder.extractPlaceholders(template).any { it in targetKeys }
        }
    }

    private var cache: PaginatorCache? = null
    private var isCacheRefreshing = false

    private class PaginatorCache(
        val bookUrl: String,
        val layoutKey: String,
        val totalChapters: Int,
        val counts: IntArray,          // -1 表示未知
        val sumKnownPrefix: IntArray,  // 已知章节的页数前缀和
        val countKnownPrefix: IntArray, // 已知章节的数量前缀和
        val totalKnownPages: Int,
        val knownChaptersCount: Int,
        var isDirty: Boolean = false
    )

    private fun markCacheDirty() {
        cache?.isDirty = true
    }

    private suspend fun refreshCache(bookUrl: String, layoutKey: String, totalChapters: Int) {
        if (totalChapters <= 0) return
        val dbCounts = appDb.chapterPageCountDao.getByBookAndLayout(bookUrl, layoutKey)
            .associateBy { it.chapterIndex }
            .mapValues { it.value.pageCount }

        val counts = IntArray(totalChapters) { -1 }
        val sumKnownPrefix = IntArray(totalChapters + 1)
        val countKnownPrefix = IntArray(totalChapters + 1)
        var totalKnownPages = 0
        var knownChaptersCount = 0

        var currentSum = 0
        var currentCount = 0

        for (i in 0 until totalChapters) {
            sumKnownPrefix[i] = currentSum
            countKnownPrefix[i] = currentCount

            val count = dbCounts[i] ?: -1
            counts[i] = count
            if (count != -1) {
                currentSum += count
                currentCount++
                totalKnownPages += count
                knownChaptersCount++
            }
        }
        sumKnownPrefix[totalChapters] = currentSum
        countKnownPrefix[totalChapters] = currentCount

        cache = PaginatorCache(
            bookUrl, layoutKey, totalChapters,
            counts, sumKnownPrefix, countKnownPrefix,
            totalKnownPages, knownChaptersCount
        )
    }

    /**
     * 获取全文进度数据
     * 尽量返回精确值，如果缺失则使用平均值预测
     */
    fun getFullPageData(
        book: Book,
        chapterIndex: Int,
        pageIndex: Int,
        chapterPageSize: Int
    ): Pair<Int, Int> {
        val layoutKey = getLayoutKey()
        val bookUrl = book.bookUrl
        val totalChapters = book.totalChapterNum
        val currentCache = cache

        if (currentCache == null || currentCache.bookUrl != bookUrl || currentCache.layoutKey != layoutKey || currentCache.isDirty) {
            if (!isCacheRefreshing) {
                isCacheRefreshing = true
                scope.launch {
                    try {
                        refreshCache(bookUrl, layoutKey, totalChapters)
                    } finally {
                        isCacheRefreshing = false
                    }
                }
            }
            if (currentCache == null || currentCache.bookUrl != bookUrl || currentCache.layoutKey != layoutKey) {
                return (pageIndex + 1) to chapterPageSize // 缓存没准备好时退回到当前章节进度
            }
        }

        // 使用缓存进行 O(1) 计算
        val c = currentCache
        
        // 计算平均值
        val avg = if (c.knownChaptersCount > 0) {
            c.totalKnownPages.toFloat() / c.knownChaptersCount
        } else {
            chapterPageSize.toFloat()
        }

        // 当前章节全局索引
        val knownSumBefore = c.sumKnownPrefix[chapterIndex]
        val unknownCountBefore = chapterIndex - c.countKnownPrefix[chapterIndex]
        val currentIndex = knownSumBefore + (unknownCountBefore * avg).roundToInt()

        // 全局总页数
        // 逻辑：(已知总页数 - 缓存中的当前章节页数 + 实时当前章节页数) + (未知章节数 * 平均值)
        val currentChapterCachedCount = c.counts.getOrElse(chapterIndex) { -1 }
        val totalPages = if (currentChapterCachedCount != -1) {
            // 当前章节在缓存中已知
            val base = c.totalKnownPages - currentChapterCachedCount + chapterPageSize
            val unknownTotal = c.totalChapters - c.knownChaptersCount
            base + (unknownTotal * avg).roundToInt()
        } else {
            // 当前章节在缓存中未知
            val base = c.totalKnownPages + chapterPageSize
            val unknownTotal = c.totalChapters - c.knownChaptersCount - 1
            base + (unknownTotal.coerceAtLeast(0) * avg).roundToInt()
        }

        return (currentIndex + pageIndex + 1) to totalPages
    }
}
