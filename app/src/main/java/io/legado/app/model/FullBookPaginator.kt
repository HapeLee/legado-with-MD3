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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object FullBookPaginator : KoinComponent {
    private val otherSettingsGateway: OtherSettingsGateway by inject()
    private val readSettingsGateway: ReadSettingsGateway by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutex = Mutex()
    private val isRunning = AtomicBoolean(false)

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
                job = launch {
                    FullBookPageService.start(appCtx)
                    paginate(book, layoutKey)
                    FullBookPageService.stop(appCtx)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        isRunning.set(false)
        FullBookPageService.stop(appCtx)
    }

    private suspend fun paginate(book: Book, layoutKey: String) {
        isRunning.set(true)
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val existingCounts = appDb.chapterPageCountDao.getByBookAndLayout(book.bookUrl, layoutKey)
            .associateBy { it.chapterIndex }

        // 删除旧的布局缓存，保持数据库精简
        appDb.chapterPageCountDao.deleteOldLayouts(book.bookUrl, layoutKey)

        val processor = ContentProcessor.get(book)
        val totalChapters = chapters.size

        for ((index, chapter) in chapters.withIndex()) {
            if (!isRunning.get()) break
            
            // 更新进度通知
            FullBookPageService.update(appCtx, index + 1, totalChapters)
            
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
                    chapters.size
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
        isRunning.set(false)
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

    private var cachedPageCounts: Map<Int, Int>? = null
    private var cachedBookUrl: String? = null
    private var cachedLayoutKey: String? = null

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
        
        if (cachedBookUrl != bookUrl || cachedLayoutKey != layoutKey) {
            cachedBookUrl = bookUrl
            cachedLayoutKey = layoutKey
            // 异步刷新缓存，当前这次渲染可能还是旧的或空的
            scope.launch {
                val counts = appDb.chapterPageCountDao.getByBookAndLayout(bookUrl, layoutKey)
                    .associate { it.chapterIndex to it.pageCount }
                cachedPageCounts = counts
            }
        }

        val counts = cachedPageCounts ?: emptyMap()
        val totalChapters = book.totalChapterNum
        
        var totalPages = 0
        var currentIndex = 0
        var knownChaptersCount = 0
        var knownPagesSum = 0
        
        for (i in 0 until totalChapters) {
            val count = counts[i]
            if (count != null) {
                knownChaptersCount++
                knownPagesSum += count
            }
        }
        
        // 计算平均每章页数进行预测
        val avgPagesPerChapter = if (knownChaptersCount > 0) {
            knownPagesSum.toFloat() / knownChaptersCount
        } else {
            chapterPageSize.toFloat() // 回退到当前章节页数
        }

        for (i in 0 until totalChapters) {
            val count = if (i == chapterIndex) chapterPageSize else (counts[i] ?: avgPagesPerChapter.roundToInt())
            if (i < chapterIndex) {
                currentIndex += count
            }
            totalPages += count
        }
        
        return (currentIndex + pageIndex + 1) to totalPages
    }
}
