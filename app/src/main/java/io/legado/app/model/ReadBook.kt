package io.legado.app.model

import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim.scrollPageAnim
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isPdf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.ReadBook.callBack
import io.legado.app.model.ReadBook.publishSnapshot
import io.legado.app.model.ReadBook.renderCallBack
import io.legado.app.model.ReadBook.snapshot
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.model.translation.TranslationChapterState
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.model.translation.TranslationManager
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.CacheBookService
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.read.pageestimate.ChapterContentHasher
import io.legado.app.ui.book.read.pageestimate.ChapterLengthInfo
import io.legado.app.ui.book.read.pageestimate.LocalPageEstimateCalibrationStore
import io.legado.app.ui.book.read.pageestimate.LocalPageEstimateMetrics
import io.legado.app.ui.book.read.pageestimate.PageEstimateConfig
import io.legado.app.ui.book.read.pageestimate.PageEstimateMetrics
import io.legado.app.ui.book.read.pageestimate.RoomExactChapterPageCountStore
import io.legado.app.ui.book.read.pageestimate.WholeBookPageCoordinator
import io.legado.app.ui.book.read.pageestimate.WholeBookPageState
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min


/**
 * ReadBook 会话的权威只读快照。
 *
 * 只承载 ReadBook **自己拥有、并在受控 mutator 中重新发布**的会话字段，全部为廉价标量，
 * 不含可变 Book / TextChapter / List / Map，滚动高频路径也不会深拷贝章节。
 *
 * 刻意不含 `isReadingAloud` / `isLoading`：前者真实来源是 `BaseReadAloudService.isRun`
 * （独立服务，ReadBook 变更不会触发其重发），后者只有 `msg`/`loadingChapters` 间接信号；
 * 塞进来会得到静默陈旧字段。需要时应让其来源方参与发布，另行引入。
 */
data class LegacyReaderSnapshot(
    val bookUrl: String? = null,
    val bookName: String? = null,
    val chapterIndex: Int = 0,
    val chapterPos: Int = 0,
    val chapterCount: Int = 0,
    val simulatedChapterCount: Int = 0,
    val isLocalBook: Boolean = true,
)

@Suppress("MemberVisibilityCanBePrivate")
object ReadBook : CoroutineScope by MainScope(), KoinComponent {
    var book: Book? = null
        private set
    var callBack: CallBack? = null

    /**
     * 渲染回调槽位（Track B2）：由 UI 层渲染控制器实现，承接指令式渲染协议。
     * 与状态槽位 [callBack] 分离，使指令式渲染不再穿过 ViewModel。
     */
    var renderCallBack: ReaderRenderCallback? = null
    var inBookshelf = false
    var chapterSize = 0
    var simulatedChapterSize = 0
    // 主线程写（翻页/换章/进度跳转），IO 线程读——layoutLoadedChapter 在
    // Dispatchers.IO 上用它们判断当前页是否已排版（见 when(chapter.index - durChapterIndex)
    // 与 page.containPos(durChapterPos)）。没有 @Volatile 时 IO 侧可能读到陈旧值，
    // 表现为「排版完了但没刷新到当前页」。
    @Volatile
    var durChapterIndex = 0
        private set

    @Volatile
    var durChapterPos = 0
        private set
    var isLocalBook = true
    var chapterChanged = false
    @Volatile
    var prevTextChapter: TextChapter? = null
    @Volatile
    var curTextChapter: TextChapter? = null
    @Volatile
    var nextTextChapter: TextChapter? = null
    var bookSource: BookSource? = null
    var msg: String? = null
    private val readRecordRepository: ReadRecordRepository by inject()
    private var lastReadLength: Long = 0
    private val loadingChapters = arrayListOf<Int>()
    private val readRecord = ReadRecord()
    private val chapterLayoutScheduler = LatestChapterTaskScheduler<ChapterLayoutTaskKey>(this) {
            _, error ->
        AppLog.put("ChapterProvider ERROR", error)
        appCtx.toastOnUi("ChapterProvider ERROR:\n${error.stackTraceStr}")
    }
    private val translationObserverJobs = ConcurrentHashMap<Int, Job>()
    private val prevChapterLoadingLock = Mutex()
    private val curChapterLoadingLock = Mutex()
    private val nextChapterLoadingLock = Mutex()
    var readStartTime: Long = System.currentTimeMillis()
    var isUiActive = false
    val isAutoSaveSessionRunning: Boolean
        get() = autoSaveJob != null


    /* 跳转进度前进度记录 */
    var lastBookProgress: BookProgress? = null

    /* web端阅读进度记录 */
    var webBookProgress: BookProgress? = null

    var preDownloadTask: Job? = null
    val downloadedChapters = hashSetOf<Int>()
    val downloadFailChapters = hashMapOf<Int, Int>()
    var contentProcessor: ContentProcessor? = null
    val downloadScope = CoroutineScope(SupervisorJob() + IO)
    val preDownloadSemaphore = Semaphore(2)

    val executor = globalExecutor

    private val ioScope = CoroutineScope(SupervisorJob() + IO)

    private var autoSaveJob: Job? = null

    private var currentActiveSession: ReadRecordSession? = null

    private val wholeBookPageCoordinator = WholeBookPageCoordinator(
        scope = this,
        calibrationStore = LocalPageEstimateCalibrationStore,
        exactPageCountStore = RoomExactChapterPageCountStore,
        metrics = PageEstimateMetrics { metric ->
            LocalPageEstimateMetrics.record(metric)
            AppLog.putDebug("PageEstimate $metric")
        },
    )
    //占位
    private var currentReadLength: Long = 10L
    private const val AUTO_SAVE_INTERVAL = 120 * 1000L

    private const val MIN_READ_DURATION = 10 * 1000L

    // region 会话所有权：外部只能通过下列语义化命令改写会话字段（book/durChapterIndex/durChapterPos
    // 已 private set）。判定用意图化谓词替代裸实体比较，避免调用方直接持有可变 Book。

    private val _snapshot = MutableStateFlow(LegacyReaderSnapshot())

    /** 权威会话快照。每个受控 mutator 在一次原子状态转移完成后 [publishSnapshot]。 */
    val snapshot: StateFlow<LegacyReaderSnapshot> = _snapshot.asStateFlow()

    private fun buildSnapshot() = LegacyReaderSnapshot(
        bookUrl = book?.bookUrl,
        bookName = book?.name,
        chapterIndex = durChapterIndex,
        chapterPos = durChapterPos,
        chapterCount = chapterSize,
        simulatedChapterCount = simulatedChapterSize,
        isLocalBook = isLocalBook,
    )

    /**
     * 在一次原子状态转移**完成后**调用（而非每次单字段赋值后），只发一次、不暴露中间态。
     * 过发无害（StateFlow 对相等值去重），漏发才会导致快照陈旧。
     */
    private fun publishSnapshot() {
        _snapshot.value = buildSnapshot()
    }

    /** 当前会话是否正指向该 URL 的书。 */
    fun isCurrentBook(bookUrl: String): Boolean = book?.bookUrl == bookUrl

    /** 当前会话是否正指向与 [other] 同名同作者的书。 */
    fun isCurrentBook(other: Book): Boolean = book?.isSameNameAuthor(other) == true

    /** 用同一本书的新实例替换当前会话持有的 Book（仅替换引用，不重置章节/进度）。 */
    fun replaceCurrentBook(book: Book) {
        this.book = book
        publishSnapshot()
    }

    /** 清空当前会话持有的 Book（仅置空引用，不动章节/进度状态）。 */
    fun clearCurrentBook() {
        book = null
        publishSnapshot()
    }

    /** 更新当前章节内的阅读位置。 */
    fun updateReadingPosition(position: Int) {
        durChapterPos = position
        publishSnapshot()
    }

    // endregion

    fun resetData(book: Book) {
        wholeBookPageCoordinator.clear()
        ReadBook.book = book
        readRecord.bookName = book.name
        readRecord.bookAuthor = book.author
        readRecord.readTime = appDb.readRecordDao.getReadTime("", book.name, book.author) ?: 0
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        contentProcessor = ContentProcessor.get(book)
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        isLocalBook = book.isLocal
        clearTextChapter()
        renderCallBack?.upContent()
        callBack?.upMenuView()
        renderCallBack?.upPageAnim()
        upWebBook(book)
        lastBookProgress = null
        webBookProgress = null
        TextFile.clear()
        requestWholeBookPageEstimate()
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
        publishSnapshot()
    }

    fun upData(book: Book) {
        ReadBook.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            clearTextChapter()
        }
        // 还在排版中的当前章不能丢: 丢了会重新加载一遍, 反而多闪一次占位页
        if (curTextChapter?.isCompleted == false && curTextChapter?.isLayoutRunning != true) {
            curTextChapter = null
        }
        if (nextTextChapter?.isCompleted == false) {
            nextTextChapter = null
        }
        if (prevTextChapter?.isCompleted == false) {
            prevTextChapter = null
        }
        callBack?.upMenuView()
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
        requestWholeBookPageEstimate()
        publishSnapshot()
    }

    fun requestWholeBookPageEstimate() {
        val book = book ?: return
        if (!FullBookPaginator.isNeeded()) {
            FullBookPaginator.stop()
            wholeBookPageCoordinator.clear()
            return
        }
        if (book.isImage || book.isPdf) {
            FullBookPaginator.stop()
            wholeBookPageCoordinator.clear()
            return
        }
        val contentWidth = ChapterProvider.visibleWidth
        val contentHeight = ChapterProvider.visibleHeight
        if (contentWidth <= 0 || contentHeight <= 0) return
        val processor = contentProcessor ?: ContentProcessor.get(book)

        val config = PageEstimateConfig(
            readerType = pageAnim(),
            textSizePx = ChapterProvider.contentPaint.textSize,
            textHeightPx = ChapterProvider.contentPaintTextHeight,
            lineSpacingPx = ChapterProvider.contentPaintTextHeight *
                (ChapterProvider.lineSpacingExtra - 1f),
            paragraphSpacingPx = ChapterProvider.contentPaintTextHeight *
                ChapterProvider.paragraphSpacing / 10f,
            titleTextSizePx = ChapterProvider.titlePaint.textSize,
            titleTextHeightPx = ChapterProvider.titlePaintTextHeight,
            titleLineSpacingPx = ChapterProvider.titlePaintTextHeight *
                (ChapterProvider.titleLineSpacingExtra - 1f),
            titleTopSpacingPx = ChapterProvider.titleTopSpacing.toFloat(),
            titleBottomSpacingPx = ChapterProvider.titleBottomSpacing.toFloat(),
            endPaddingPx = 20.dpToPx().toFloat(),
            contentWidthPx = contentWidth,
            contentHeightPx = contentHeight,
            fontKey = ReadBookConfig.textFont,
            titleFontKey = ReadBookConfig.titleFont,
            letterSpacing = ReadBookConfig.letterSpacing,
            paragraphIndent = ReadBookConfig.paragraphIndent,
            titleMode = ReadBookConfig.titleMode,
            doublePage = ChapterProvider.doublePage,
            useZhLayout = ReadBookConfig.useZhLayout,
            textFullJustify = ReadBookConfig.textFullJustify,
            contentKey = buildString {
                append(book.config.useReplaceRule)
                append('|').append(book.config.delTag)
                append('|').append(book.config.translationMode)
                append('|').append(AppConfig.chineseConverterType)
                append('|').append(AppConfig.replaceEnableDefault)
                append('|').append(processor.getTitleReplaceRules())
                append('|').append(processor.getContentReplaceRules())
            },
        )
        val bookUrl = book.bookUrl
        val showChapterTitle = ReadBookConfig.titleMode != 2
        val wholeBookAverageChapterLength = book.wordCount.toContentLength()
            ?.div(chapterSize.coerceAtLeast(1))
        val layoutGeneration = wholeBookPageCoordinator.requestEstimate(
            config = config,
            bookId = bookUrl,
            fallbackContentLength = wholeBookAverageChapterLength,
        ) {
            val chapters = appDb.bookChapterDao.getChapterList(bookUrl)
            val bytesPerChar = chapters.bytesPerChar()
            chapters.map { chapter ->
                val cachedContent = if (book.isLocalTxt) {
                    null
                } else {
                    BookHelp.getCachedContentInfo(book, chapter)
                }
                ChapterLengthInfo(
                    chapterIndex = chapter.index,
                    chapterId = chapter.url,
                    titleLength = chapter.title.length,
                    includeTitle = showChapterTitle || chapter.isVolume,
                    contentLength = if (chapter.isVolume) {
                        0
                    } else {
                        chapter.wordCount.toContentLength()
                            ?: cachedContent?.contentLength
                            ?: chapter.charCountFromOffset(bytesPerChar)
                    },
                    contentHash = if (book.isLocalTxt) {
                        ChapterContentHasher.fromLocalOffsets(chapter.start, chapter.end)
                    } else {
                        cachedContent?.let {
                            ChapterContentHasher.fromLengthAndPrefix(it.contentLength, it.prefix)
                        }
                    },
                )
            }
        }
        FullBookPaginator.startIfNeeded(layoutGeneration)
    }

    fun updateWholeBookPageDemand() {
        if (!FullBookPaginator.isNeeded()) {
            FullBookPaginator.stop()
            wholeBookPageCoordinator.clear()
            return
        }
        if (wholeBookPageCoordinator.hasEstimate()) {
            FullBookPaginator.startIfNeeded(wholeBookPageCoordinator.generation)
        } else {
            requestWholeBookPageEstimate()
        }
    }

    fun getWholeBookPageState(chapterIndex: Int, localPageIndex: Int): WholeBookPageState? =
        wholeBookPageCoordinator.getState(chapterIndex, localPageIndex)

    /** Silently paginates uncached local-book chapters and feeds the whole-book coordinator. */
    suspend fun paginateLocalBookPages(layoutGeneration: Long) {
        val book = book?.takeIf { it.isLocal && !it.isImage && !it.isPdf } ?: return
        if (!wholeBookPageCoordinator.awaitInitialized(layoutGeneration)) return
        val chapters = withContext(IO) { appDb.bookChapterDao.getChapterList(book.bookUrl) }
        val processor = ContentProcessor.get(book)
        val useReplaceRule = book.getUseReplaceRule(AppConfig.replaceEnableDefault)
        for (chapter in chapters) {
            ensureActive()
            if (wholeBookPageCoordinator.isChapterExact(chapter.index, layoutGeneration)) continue
            val content = withContext(IO) { BookHelp.getContent(book, chapter) } ?: continue
            val displayTitle = chapter.getDisplayTitle(
                processor.getTitleReplaceRules(),
                useReplaceRule,
                chineseConverterType = AppConfig.chineseConverterType,
            )
            val processed = processor.getContent(book, chapter, content, includeTitle = false)
            wholeBookPageCoordinator.updateChapterContent(
                chapterIndex = chapter.index,
                actualLength = processed.textList
                    .sumOf { it.length.toLong() }
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                contentHash = if (book.isLocalTxt) {
                    ChapterContentHasher.fromLocalOffsets(chapter.start, chapter.end)
                } else {
                    ChapterContentHasher.fromContent(content)
                },
                layoutGeneration = layoutGeneration,
            )
            val textChapter = ChapterProvider.getTextChapterAsync(
                this, book, bookSource, chapter, displayTitle, processed, chapters.size,
            )
            try {
                var pageCount = 0
                withTimeout(30_000L) {
                    for (page in textChapter.layoutChannel) {
                        ensureActive()
                        pageCount++
                    }
                }
                wholeBookPageCoordinator.correctChapter(
                    chapterIndex = chapter.index,
                    realPageCount = pageCount.coerceAtLeast(1),
                    layoutGeneration = layoutGeneration,
                )
            } finally {
                textChapter.cancelLayout()
            }
        }
    }

    private fun String?.toContentLength(): Int? {
        val value = this?.replace(",", "")?.trim() ?: return null
        val match = Regex("""(\d+(?:\.\d+)?)\s*([万千百]?)""").find(value) ?: return null
        val multiplier = when (match.groupValues[2]) {
            "万" -> 10_000f
            "千" -> 1_000f
            "百" -> 100f
            else -> 1f
        }
        return (match.groupValues[1].toFloatOrNull()?.times(multiplier))
            ?.toInt()
            ?.takeIf { it > 0 }
    }

    private fun BookChapter.offsetContentLength(): Long? {
        val startOffset = start ?: return null
        val endOffset = end ?: return null
        return (endOffset - startOffset).takeIf { it > 0 }
    }

    /**
     * 本地 TXT 的 start/end 是**字节**偏移（TextFile 全程按 ByteArray 切分），
     * 而 wordCount 和缓存正文长度都是**字符**数，混用会让中文章节虚高 2~3 倍。
     * 用两者都已知的章节现算字节/字符比，比猜字符集靠谱。
     */
    private fun List<BookChapter>.bytesPerChar(): Float? {
        var bytes = 0L
        var chars = 0L
        forEach { chapter ->
            val byteLength = chapter.offsetContentLength() ?: return@forEach
            val charLength = chapter.wordCount.toContentLength() ?: return@forEach
            bytes += byteLength
            chars += charLength
        }
        if (chars <= 0L) return null
        return (bytes.toFloat() / chars).takeIf { it.isFinite() && it >= 1f }
    }

    /**
     * 比值测不出来时返回 null —— 交给协调器的「已知章节长度中位数」兜底，
     * 好过按字符集拍一个常数进去。
     */
    private fun BookChapter.charCountFromOffset(bytesPerChar: Float?): Int? {
        val byteLength = offsetContentLength() ?: return null
        val ratio = bytesPerChar ?: return null
        return (byteLength / ratio)
            .toLong()
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .takeIf { it > 0 }
    }

    private fun correctWholeBookPageCount(textChapter: TextChapter) {
        wholeBookPageCoordinator.correctChapter(
            chapterIndex = textChapter.chapter.index,
            realPageCount = textChapter.pageSize,
            layoutGeneration = textChapter.pageEstimateGeneration,
        )
    }

    fun upWebBook(book: Book) {
        if (book.isLocal) {
            bookSource = null
            if (book.getImageStyle().isNullOrBlank() && (book.isImage || book.isPdf)) {
                book.setImageStyle(Book.imgStyleFull)
            }
        } else {
            appDb.bookSourceDao.getBookSource(book.origin)?.let {
                bookSource = it
                if (book.getImageStyle().isNullOrBlank()) {
                    var imageStyle = it.getContentRule().imageStyle
                    if (imageStyle.isNullOrBlank() && (book.isImage || book.isPdf)) {
                        imageStyle = Book.imgStyleFull
                    }
                    book.setImageStyle(imageStyle)
                    if (imageStyle.equals(Book.imgStyleSingle, true)) {
                        book.setPageAnim(0)
                    }
                }
            } ?: let {
                bookSource = null
            }
        }
    }

    fun upReadBookConfig(book: Book) {
        val oldIndex = ReadBookConfig.styleSelect
        ReadSessionState.isComic = book.isImage
        if (oldIndex != ReadBookConfig.styleSelect) {
            ReadConfigUpdateBus.post(
                setOf(
                    ConfigUpdateAction.UpdateBackground,
                    ConfigUpdateAction.UpdateStyle,
                    ConfigUpdateAction.ReloadContent,
                    ConfigUpdateAction.RebuildWholeBookPageIndex,
                )
            )
            if (ReadConfig.readBarStyleFollowPage) {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
        }
    }

    fun setProgress(progress: BookProgress) {
        if (progress.durChapterIndex < chapterSize &&
            (durChapterIndex != progress.durChapterIndex
                    || durChapterPos != progress.durChapterPos)
        ) {
            durChapterIndex = progress.durChapterIndex
            durChapterPos = progress.durChapterPos
            publishSnapshot()
            saveRead()
            clearTextChapter()
            renderCallBack?.upContent()
            loadContent(resetPageOffset = true)
        }
    }

    //暂时保存跳转前进度
    fun saveCurrentBookProgress() {
        if (lastBookProgress != null) return //避免进度条连续跳转不能覆盖最初的进度记录
        lastBookProgress = book?.let { BookProgress(it) }
    }

    //恢复跳转前进度
    fun restoreLastBookProgress() {
        lastBookProgress?.let {
            setProgress(it)
            lastBookProgress = null
        }
    }

    fun clearTextChapter() {
        clearExpiredChapterLoadingJob(true)
        clearTranslationObserverJobs()
        prevTextChapter = null
        curTextChapter = null
        nextTextChapter = null
    }

    private fun clearTranslationObserverJobs() {
        translationObserverJobs.entries.filter { it.key !in durChapterIndex - 1..durChapterIndex + 1 }
            .forEach { (index, job) ->
                job.cancel()
                translationObserverJobs.remove(index)
            }
    }

    fun clearSearchResult() {
        curTextChapter?.clearSearchResult()
        prevTextChapter?.clearSearchResult()
        nextTextChapter?.clearSearchResult()
    }

    fun uploadProgress(toast: Boolean = false, successAction: (() -> Unit)? = null) {
        book?.let {
            launch(IO) {
                AppWebDav.uploadBookProgress(it, toast) {
                    successAction?.invoke()
                }
                ensureActive()
                it.update()
            }
        }
    }

    /**
     * 同步阅读进度
     * 如果当前进度快于服务器进度或者没有进度进行上传，如果慢与服务器进度则执行传入动作
     */
    fun syncProgress(
        newProgressAction: ((progress: BookProgress) -> Unit)? = null,
        uploadSuccessAction: (() -> Unit)? = null,
        syncSuccessAction: (() -> Unit)? = null
    ) {
        if (!ReadConfig.syncBookProgress) return
        val book = book ?: return
        Coroutine.async {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败", it)
        }.onSuccess { progress ->
            if (progress == null || progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                // 服务器没有进度或者进度比服务器快，上传现有进度
                Coroutine.async {
                    AppWebDav.uploadBookProgress(book, onSuccess = uploadSuccessAction)
                    book.update()
                }
            } else if (progress.durChapterIndex > book.durChapterIndex ||
                progress.durChapterPos > book.durChapterPos
            ) {
                // 进度比服务器慢，执行传入动作
                newProgressAction?.invoke(progress)
            } else {
                syncSuccessAction?.invoke()
            }
        }
    }

    fun startReadSession() {
        synchronized(this) {
            readStartTime = System.currentTimeMillis()
            initReadTime()
            startAutoSaveSession()
        }
    }

    fun initReadTime() {
        synchronized(this) {
            val currentBookName = book?.name ?: return
            val currentBookAuthor = book?.author ?: ""
            if (currentActiveSession != null &&
                (currentActiveSession!!.bookName != currentBookName || currentActiveSession!!.bookAuthor != currentBookAuthor)
            ) {
                commitReadSession()
            }

            if (currentActiveSession == null) {
                lastReadLength = currentReadLength
                currentActiveSession = ReadRecordSession(
                    deviceId = "",
                    bookName = currentBookName,
                    bookAuthor = currentBookAuthor,
                    startTime = readStartTime,
                    endTime = readStartTime,
                    words = durChapterIndex.toLong()
                )
            }
        }
    }

    fun upReadTime() {
        synchronized(this) {
            val currentLength = currentReadLength
            val currentBookName = book?.name ?: return
            val currentBookAuthor = book?.author ?: ""
            val endTime = System.currentTimeMillis()

            if (currentActiveSession == null ||
                currentActiveSession!!.bookName != currentBookName ||
                currentActiveSession!!.bookAuthor != currentBookAuthor
            ) {
                initReadTime()
                return
            }

            currentActiveSession = currentActiveSession!!.copy(
                endTime = endTime,
                words = durChapterIndex.toLong()
            )

            readStartTime = endTime
            lastReadLength = currentLength
        }
    }

    fun startAutoSaveSession() {
        synchronized(this) {
            autoSaveJob?.cancel()
            autoSaveJob = ioScope.launch {
                while (isActive) {
                    delay(AUTO_SAVE_INTERVAL)
                    commitSessionInternal()
                }
            }
        }
    }

    fun stopAutoSaveSession() {
        synchronized(this) {
            autoSaveJob?.cancel()
            autoSaveJob = null
        }
    }

    fun commitReadSession() {
        val sessionToCommit = synchronized(this) {
            val session = currentActiveSession
            currentActiveSession = null
            session
        } ?: return
        ioScope.launch {
            saveSessionToDb(sessionToCommit)
        }
    }

    /**
     * 内部提交逻辑（auto-save 专用）：保存后立即创建新 session 保证连续记录
     */
    private suspend fun commitSessionInternal() {
        val sessionToSave = synchronized(this) { currentActiveSession } ?: return
        val sessionDuration = sessionToSave.endTime - sessionToSave.startTime
        if (sessionDuration < MIN_READ_DURATION) {
            return
        }
        try {
            readRecordRepository.saveReadSession(sessionToSave)
        } catch (e: Exception) {
            AppLog.put("保存阅读会话出错: ${sessionToSave.bookName}", e)
            return
        }
        // 保存成功后立即创建新 session，避免 auto-save 空窗期
        synchronized(this) {
            val current = currentActiveSession
            if (current != null &&
                current.bookName == sessionToSave.bookName &&
                current.bookAuthor == sessionToSave.bookAuthor
            ) {
                currentActiveSession = current.copy(
                    startTime = sessionToSave.endTime,
                    endTime = sessionToSave.endTime,
                    words = durChapterIndex.toLong()
                )
            }
        }
    }

    /**
     * 将 session 写入数据库（pause 专用，不重建 session）
     */
    private suspend fun saveSessionToDb(session: ReadRecordSession) {
        val sessionDuration = session.endTime - session.startTime
        if (sessionDuration < MIN_READ_DURATION) return
        try {
            readRecordRepository.saveReadSession(session)
        } catch (e: Exception) {
            AppLog.put("保存阅读会话出错: ${session.bookName}", e)
        }
    }

    fun upMsg(msg: String?) {
        if (ReadBook.msg != msg) {
            ReadBook.msg = msg
            renderCallBack?.upContent()
            // msg 参与 syncFromReadBook；渲染已下沉后靠快照驱动 VM 状态刷新
            publishSnapshot()
        }
    }

    fun moveToNextPage(): Boolean {
        var hasNextPage = false
        curTextChapter?.let {
            val nextPagePos = it.getNextPageLength(durChapterPos)
            if (nextPagePos >= 0) {
                hasNextPage = true
                it.getPage(durPageIndex)?.removePageAloudSpan()
                durChapterPos = nextPagePos
                renderCallBack?.cancelSelect()
                renderCallBack?.upContent()
                saveRead(true)
                publishSnapshot()
            }
        }
        return hasNextPage
    }

    fun moveToPrevPage(): Boolean {
        var hasPrevPage = false
        curTextChapter?.let {
            val prevPagePos = it.getPrevPageLength(durChapterPos)
            if (prevPagePos >= 0) {
                hasPrevPage = true
                durChapterPos = prevPagePos
                renderCallBack?.upContent()
                saveRead(true)
                publishSnapshot()
            }
        }
        return hasPrevPage
    }

    fun moveToNextChapter(upContent: Boolean, upContentInPlace: Boolean = true): Boolean {
        if (durChapterIndex < simulatedChapterSize - 1) {
            durChapterPos = 0
            durChapterIndex++
            clearExpiredChapterLoadingJob()
            prevTextChapter = curTextChapter
            curTextChapter = nextTextChapter
            nextTextChapter = null
            if (curTextChapter == null) {
                AppLog.putDebug("moveToNextChapter-章节未加载,开始加载")
                if (upContentInPlace) renderCallBack?.upContent()
                loadContent(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                AppLog.putDebug("moveToNextChapter-章节已加载,刷新视图")
                renderCallBack?.upContent()
            }
            loadContent(durChapterIndex.plus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            AppLog.putDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            publishSnapshot()
            return true
        } else {
            AppLog.putDebug("跳转下一章失败,没有下一章")
            return false
        }
    }

    suspend fun moveToNextChapterAwait(
        upContent: Boolean,
        upContentInPlace: Boolean = true
    ): Boolean {
        if (durChapterIndex < simulatedChapterSize - 1) {
            durChapterPos = 0
            durChapterIndex++
            clearExpiredChapterLoadingJob()
            prevTextChapter = curTextChapter
            curTextChapter = nextTextChapter
            nextTextChapter = null
            if (curTextChapter == null) {
                AppLog.putDebug("moveToNextChapter-章节未加载,开始加载")
                if (upContentInPlace) renderCallBack?.upContentAwait()
                loadContentAwait(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                AppLog.putDebug("moveToNextChapter-章节已加载,刷新视图")
                renderCallBack?.upContentAwait()
            }
            loadContent(durChapterIndex.plus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            AppLog.putDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            publishSnapshot()
            return true
        } else {
            AppLog.putDebug("跳转下一章失败,没有下一章")
            return false
        }
    }

    fun moveToPrevChapter(
        upContent: Boolean,
        toLast: Boolean = true,
        upContentInPlace: Boolean = true
    ): Boolean {
        if (durChapterIndex > 0) {
            durChapterPos = if (toLast) prevTextChapter?.lastReadLength ?: Int.MAX_VALUE else 0
            durChapterIndex--
            clearExpiredChapterLoadingJob()
            nextTextChapter = curTextChapter
            curTextChapter = prevTextChapter
            prevTextChapter = null
            if (curTextChapter == null) {
                if (upContentInPlace) renderCallBack?.upContent()
                loadContent(durChapterIndex, upContent, resetPageOffset = false)
            } else if (upContent && upContentInPlace) {
                renderCallBack?.upContent()
            }
            loadContent(durChapterIndex.minus(1), upContent, false)
            saveRead()
            callBack?.upMenuView()
            curPageChanged()
            publishSnapshot()
            return true
        } else {
            return false
        }
    }

    fun skipToPage(index: Int, success: (() -> Unit)? = null) {
        durChapterPos = curTextChapter?.getReadLength(index) ?: index
        renderCallBack?.upContent {
            success?.invoke()
        }
        curPageChanged()
        saveRead(true)
        publishSnapshot()
    }

    fun setPageIndex(index: Int) {
        recycleRecorders(durPageIndex, index)
        durChapterPos = curTextChapter?.getReadLength(index) ?: index
        saveRead(true)
        curPageChanged(true)
        publishSnapshot()
    }

    fun recycleRecorders(beforeIndex: Int, afterIndex: Int) {
        if (!ReadConfig.optimizeRender) {
            return
        }
        executor.execute {
            val textChapter = curTextChapter ?: return@execute
            if (afterIndex > beforeIndex) {
                textChapter.getPage(afterIndex - 2)?.recycleRecorders()
            }
            if (afterIndex < beforeIndex) {
                textChapter.getPage(afterIndex + 3)?.recycleRecorders()
            }
        }
    }

    fun openChapter(
        index: Int,
        durChapterPos: Int = 0,
        upContent: Boolean = true,
        success: (() -> Unit)? = null
    ) {
        // 实时读取章节数而不是依赖缓存 chapterSize：更新目录后 chapterSize 若未同步，
        // 新增章节的 index 会超出旧值而被下方守卫静默吞掉，表现为「点击新章节无法跳转」。
        val chapterCount = book?.bookUrl?.let { appDb.bookChapterDao.getChapterCount(it) }
            ?: chapterSize
        if (index < chapterCount) {
            clearTextChapter()
            if (upContent) renderCallBack?.upContent()
            durChapterIndex = index
            ReadBook.durChapterPos = durChapterPos
            publishSnapshot()
            saveRead()
            loadContent(resetPageOffset = true) {
                success?.invoke()
            }
        }
    }

    /**
     * 当前页面变化
     */
    private fun curPageChanged(
        pageChanged: Boolean = false,
        preserveReadAloudPosition: Boolean = false,
    ) {
        renderCallBack?.pageChanged()
        curTextChapter?.let {
            if (BaseReadAloudService.isRun && it.isCompleted) {
                if (shouldRestartReadAloudAfterContentLoad(
                        preserveReadAloudPosition = preserveReadAloudPosition,
                        serviceChapterIndex = BaseReadAloudService.currentChapterIndex,
                        loadedChapterIndex = it.chapter.index,
                    )
                ) {
                    if (isScroll && pageChanged) {
                        ReadAloud.pause(appCtx)
                    } else {
                        readAloud(!BaseReadAloudService.pause)
                    }
                } else {
                    ReadAloud.syncLayout()
                }
            }
        }
        upReadTime()
        preDownload()
    }

    /**
     * 朗读
     */
    fun readAloud(play: Boolean = true, startPos: Int = 0) {
        book ?: return
        val textChapter = curTextChapter ?: return
        if (textChapter.isCompleted) {
            ReadAloud.play(appCtx, play, startPos = startPos)
        }
    }

    fun syncReadAloudPage(chapterIndex: Int, chapterPos: Int) {
        if (durChapterIndex != chapterIndex || durChapterPos == chapterPos) return
        durChapterPos = chapterPos
        renderCallBack?.upContent(resetPageOffset = false)
        saveRead(pageChanged = true)
        publishSnapshot()
    }

    /**
     * 当前页数
     */
    val durPageIndex: Int
        get() {
            return curTextChapter?.getPageIndexByCharIndex(durChapterPos) ?: 0
        }

    /**
     * 是否排版到了当前阅读位置
     */
    val isLayoutAvailable inline get() = durPageIndex >= 0

    val isScroll inline get() = pageAnim() == scrollPageAnim

    val contentLoadFinish get() = curTextChapter != null || msg != null

    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    fun textChapter(chapterOnDur: Int = 0): TextChapter? {
        val chapter = when (chapterOnDur) {
            0 -> curTextChapter
            1 -> nextTextChapter
            -1 -> prevTextChapter
            else -> null
        }
        if (chapter != null && !chapter.isLayoutSizeMatch()) {
            return null
        }
        return chapter
    }

    /**
     * 导航到阅读页时提前加载, 让加载与导航动画/组合并行.
     * 阅读页真正初始化时若章节已排版完成, 首帧直接显示正文, 不再闪占位页.
     * 任何一个前提不满足就直接返回, 由阅读页按原有流程加载.
     */
    fun prefetchForOpen(bookUrl: String) {
        if (callBack != null) return
        if (BaseReadAloudService.isRun) return
        if (ChapterProvider.visibleWidth <= 0 || ChapterProvider.visibleHeight <= 0) return
        if (book?.bookUrl == bookUrl && curTextChapter?.isCompleted == true) return
        ioScope.launch {
            val book = appDb.bookDao.getBook(bookUrl) ?: return@launch
            // 需要联网补目录、本地文件缺失或已改动的, 都交给阅读页的完整初始化流程
            if (!book.isLocal && book.tocUrl.isEmpty()) return@launch
            if (book.isLocal) {
                if (book.isLocalModified()) return@launch
                val readable = runCatching {
                    LocalBook.getBookInputStream(book).close()
                }.isSuccess
                if (!readable) return@launch
            }
            if (appDb.bookChapterDao.getChapterCount(bookUrl) == 0) return@launch
            if (callBack != null) return@launch
            resetData(book)
            // 不能走 loadContent: Coroutine.async 会先派发到主线程,
            // 而导航动画和阅读页组合正把主线程占满, 预加载会排到几百毫秒之后.
            loadContentAwait(durChapterIndex, resetPageOffset = true)
        }
    }

    /**
     * 加载当前章节和前后一章内容
     * @param resetPageOffset 滚动阅读是否重置滚动位置
     * @param success 当前章节加载完成回调
     */
    fun loadContent(
        resetPageOffset: Boolean,
        preserveReadAloudPosition: Boolean = false,
        success: (() -> Unit)? = null
    ) {
        loadContent(
            durChapterIndex,
            resetPageOffset = resetPageOffset,
            preserveReadAloudPosition = preserveReadAloudPosition,
        ) {
            success?.invoke()
        }
        loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset)
        loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
    }

    fun relayoutContent() {
        loadContent(
            resetPageOffset = false,
            preserveReadAloudPosition = true,
        )
    }

    fun loadOrUpContent(success: (() -> Unit)? = null) {
        val curChapter = curTextChapter
        if (curChapter == null || !curChapter.isLayoutSizeMatch()) {
            val preserveReadAloudPosition = BaseReadAloudService.isRun &&
                    BaseReadAloudService.currentChapterIndex == durChapterIndex
            curTextChapter = null
            nextTextChapter = null
            prevTextChapter = null
            loadContent(
                durChapterIndex,
                preserveReadAloudPosition = preserveReadAloudPosition,
            ) {
                success?.invoke()
            }
        } else {
            renderCallBack?.upContent()
            publishSnapshot()
        }
        val nextChapter = nextTextChapter
        if (nextChapter == null || !nextChapter.isLayoutSizeMatch()) {
            nextTextChapter = null
            loadContent(durChapterIndex + 1)
        }
        val prevChapter = prevTextChapter
        if (prevChapter == null || !prevChapter.isLayoutSizeMatch()) {
            prevTextChapter = null
            loadContent(durChapterIndex - 1)
        }
    }

    /**
     * 加载章节内容
     * @param index 章节序号
     * @param upContent 是否更新视图
     * @param resetPageOffset 滚动阅读是否重置滚动位置
     * @param success 加载完成回调
     */
    fun loadContent(
        index: Int,
        upContent: Boolean = true,
        resetPageOffset: Boolean = false,
        preserveReadAloudPosition: Boolean = false,
        success: (() -> Unit)? = null
    ) {
        Coroutine.async {
            val book = book!!
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: run {
                if (index == durChapterIndex) {
                    upMsg("章节不存在")
                }
                return@async
            }
            if (addLoading(index)) {
                val content = if (book.getTranslationMode()) {
                    TranslationManager.getCachedTranslation(book, chapter)
                        ?: run {
                            TranslationManager.startTranslation(book, chapter)?.let { taskFlow ->
                                startTranslationObserver(taskFlow, book, chapter)
                            }
                            BookHelp.getContent(book, chapter)
                        }
                } else {
                    BookHelp.getContent(book, chapter)
                }
                content?.let {
                    contentLoadFinish(
                        book,
                        chapter,
                        it,
                        upContent,
                        resetPageOffset,
                        preserveReadAloudPosition,
                        success = success
                    )
                } ?: download(
                    downloadScope,
                    chapter,
                    resetPageOffset,
                    preserveReadAloudPosition = preserveReadAloudPosition,
                )
            }
        }.onError {
            removeLoading(index)
            if (index == durChapterIndex) {
                upMsg("加载正文出错\n${it.localizedMessage}")
            }
            AppLog.put("加载正文出错\n${it.localizedMessage}", it)
        }
    }

    suspend fun loadContentAwait(
        index: Int,
        upContent: Boolean = true,
        resetPageOffset: Boolean = false,
        success: (() -> Unit)? = null
    ) = withContext(IO) {
        if (addLoading(index)) {
            try {
                val book = book!!
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)!!
                val content = if (book.getTranslationMode()) {
                    TranslationManager.getCachedTranslation(book, chapter)
                        ?: run {
                            TranslationManager.startTranslation(book, chapter)?.let { taskFlow ->
                                startTranslationObserver(taskFlow, book, chapter)
                            }
                            BookHelp.getContent(book, chapter) ?: downloadAwait(chapter)
                        }
                } else {
                    BookHelp.getContent(book, chapter) ?: downloadAwait(chapter)
                }
                contentLoadFinishAwait(book, chapter, content, upContent, resetPageOffset)
                success?.invoke()
            } catch (e: Exception) {
                AppLog.put("加载正文出错\n${e.localizedMessage}")
            } finally {
                removeLoading(index)
            }
        }
    }

    /**
     * 下载正文
     */
    private suspend fun downloadIndex(index: Int) {
        if (index < 0) return
        if (index > chapterSize - 1) {
            upToc()
            return
        }
        val book = book ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return
        if (BookHelp.hasContent(book, chapter)) {
            downloadedChapters.add(chapter.index)
        } else {
            delay(1000)
            if (addLoading(index)) {
                download(
                    scope = downloadScope,
                    chapter = chapter,
                    resetPageOffset = false,
                    semaphore = preDownloadSemaphore,
                )
            }
        }
    }

    /**
     * 下载正文
     */
    private fun download(
        scope: CoroutineScope,
        chapter: BookChapter,
        resetPageOffset: Boolean,
        preserveReadAloudPosition: Boolean = false,
        semaphore: Semaphore? = null,
        success: (() -> Unit)? = null
    ) {
        val book = book ?: return removeLoading(chapter.index)
        val bookSource = bookSource
        if (bookSource != null) {
            val started =
                CacheBook.getOrCreate(bookSource, book).download(
                    scope = scope,
                    chapter = chapter,
                    semaphore = semaphore,
                    resetPageOffset = resetPageOffset,
                    preserveReadAloudPosition = preserveReadAloudPosition,
                )
            if (!started) {
                removeLoading(chapter.index)
            }
        } else {
            val msg = if (book.isLocal) "无内容" else "没有书源"
            contentLoadFinish(
                book,
                chapter,
                "加载正文失败\n$msg",
                resetPageOffset = resetPageOffset,
                preserveReadAloudPosition = preserveReadAloudPosition,
                success = success
            )
        }
    }

    private suspend fun downloadAwait(chapter: BookChapter): String {
        val book = book!!
        val bookSource = bookSource
        if (bookSource != null) {
            return CacheBook.getOrCreate(bookSource, book).downloadAwait(chapter)
        } else {
            val msg = if (book.isLocal) "无内容" else "没有书源"
            return "加载正文失败\n$msg"
        }
    }

    /**
     * Start observing a translation task for real-time UI updates.
     * Collects mixedContent updates and calls contentLoadFinish to refresh the page.
     * The observer stops automatically when translation completes or fails.
     */
    private fun startTranslationObserver(taskFlow: MutableStateFlow<TranslationChapterState>, book: Book, chapter: BookChapter) {
        val chapterIndex = chapter.index
        translationObserverJobs[chapterIndex]?.cancel()

        val job = launch {
            try {
                taskFlow.takeWhile { state ->
                    when (state.status) {
                        TranslationChapterStatus.Translating,
                        TranslationChapterStatus.Thinking -> {
                            state.mixedContent?.let { mixed ->
                                contentLoadFinish(
                                    book,
                                    chapter,
                                    mixed,
                                    upContent = true,
                                    resetPageOffset = false,
                                )
                            }
                        }
                        else -> Unit
                    }
                    state.status == TranslationChapterStatus.Translating ||
                        state.status == TranslationChapterStatus.Thinking
                }.collect {}
            } finally {
                coroutineContext[Job]?.let { job ->
                    translationObserverJobs.remove(chapterIndex, job)
                }
            }
        }
        translationObserverJobs[chapterIndex] = job
    }

    @Synchronized
    private fun addLoading(index: Int): Boolean {
        if (loadingChapters.contains(index)) return false
        loadingChapters.add(index)
        return true
    }

    @Synchronized
    fun removeLoading(index: Int) {
        loadingChapters.remove(index)
    }

    /**
     * 内容加载完成
     */
    @Synchronized
    fun contentLoadFinish(
        book: Book,
        chapter: BookChapter,
        content: String,
        upContent: Boolean = true,
        resetPageOffset: Boolean,
        preserveReadAloudPosition: Boolean = false,
        canceled: Boolean = false,
        success: (() -> Unit)? = null
    ) {
        removeLoading(chapter.index)
        if (canceled || chapter.index !in durChapterIndex - 1..durChapterIndex + 1) {
            return
        }
        chapterLayoutScheduler.submit(chapter.taskKey()) {
            layoutLoadedChapter(
                book = book,
                chapter = chapter,
                content = content,
                upContent = upContent,
                resetPageOffset = resetPageOffset,
                preserveReadAloudPosition = preserveReadAloudPosition,
            )
            withContext(Main) {
                success?.invoke()
            }
        }
    }

    private suspend fun CoroutineScope.layoutLoadedChapter(
        book: Book,
        chapter: BookChapter,
        content: String,
        upContent: Boolean,
        resetPageOffset: Boolean,
        preserveReadAloudPosition: Boolean,
    ) {
        if (!isCurrentLocalChapter(chapter)) return
        val pageEstimateGeneration = wholeBookPageCoordinator.generation
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            contentProcessor.getTitleReplaceRules(),
            book.getUseReplaceRule(AppConfig.replaceEnableDefault),
            chineseConverterType = AppConfig.chineseConverterType,
        )
        val contents = contentProcessor
            .getContent(book, chapter, content, includeTitle = false)
        ensureActive()
        wholeBookPageCoordinator.updateChapterContent(
            chapterIndex = chapter.index,
            actualLength = contents.textList
                .sumOf { it.length.toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            contentHash = if (book.isLocalTxt) {
                ChapterContentHasher.fromLocalOffsets(chapter.start, chapter.end)
            } else {
                ChapterContentHasher.fromContent(content)
            },
            layoutGeneration = pageEstimateGeneration,
        )
        val textChapter = ChapterProvider.getTextChapterAsync(
            this, book, bookSource, chapter, displayTitle, contents, simulatedChapterSize
        ).apply {
            this.pageEstimateGeneration = pageEstimateGeneration
        }
        if (!isCurrentLocalChapter(chapter)) {
            textChapter.cancelLayout()
            return
        }
        when (val offset = chapter.index - durChapterIndex) {
            0 -> curChapterLoadingLock.withLock {
                publishTextChapter(textChapter) {
                    curTextChapter?.cancelLayout()
                    curTextChapter = textChapter
                }
                callBack?.upMenuView()
                var available = false
                val layoutCompleted = collectLayoutPages(textChapter) { page ->
                    val index = page.index
                    if (!available && page.containPos(durChapterPos)) {
                        if (upContent) {
                            renderCallBack?.upContent(offset, resetPageOffset)
                        }
                        available = true
                    }
                    if (upContent && isScroll) {
                        if (max(index - 3, 0) < durPageIndex) {
                            renderCallBack?.upContent(offset, false)
                        }
                    }
                    renderCallBack?.onLayoutPageCompleted(index, page)
                }
                if (layoutCompleted) correctWholeBookPageCount(textChapter)
                if (upContent) renderCallBack?.upContent(offset, !available && resetPageOffset)
                curPageChanged(
                    preserveReadAloudPosition = preserveReadAloudPosition,
                )
                renderCallBack?.contentLoadFinish()
                // 布局全部完成后发一次：seekMax 依赖 curTextChapter.pages.size，
                // 渲染下沉后靠快照驱动 VM 的进度/章节状态刷新
                publishSnapshot()
            }

            -1 -> prevChapterLoadingLock.withLock {
                publishTextChapter(textChapter) {
                    prevTextChapter?.cancelLayout()
                    prevTextChapter = textChapter
                }
                if (collectLayoutPages(textChapter) {}) {
                    correctWholeBookPageCount(textChapter)
                }
                if (upContent) renderCallBack?.upContent(offset, resetPageOffset)
            }

            1 -> nextChapterLoadingLock.withLock {
                publishTextChapter(textChapter) {
                    nextTextChapter?.cancelLayout()
                    nextTextChapter = textChapter
                }
                val layoutCompleted = collectLayoutPages(textChapter) { page ->
                    if (page.index > 1) return@collectLayoutPages
                    if (upContent) renderCallBack?.upContent(offset, resetPageOffset)
                }
                if (layoutCompleted) correctWholeBookPageCount(textChapter)
            }

            else -> textChapter.cancelLayout()
        }
    }

    /**
     * 发布排版好的章节.
     * 阅读页正在显示时切到主线程赋值, 保持原有的线程约束, 不与正在进行的帧交错;
     * 阅读页还没显示时(导航期间的预加载)直接赋值: 此时主线程正被导航动画和
     * 阅读页组合占满, 等一次主线程派发要几百毫秒, 预加载就白做了.
     * 三个章节字段都是 @Volatile, 直接赋值对随后显示的阅读页可见.
     */
    private suspend fun publishTextChapter(textChapter: TextChapter, assign: () -> Unit) {
        if (!isUiActive) {
            currentCoroutineContext().ensureActive()
            assign()
        } else {
            withContext(Main) {
                currentCoroutineContext().ensureActive()
                assign()
            }
        }
    }

    /**
     * Safely collect pages from a TextChapterLayout's channel with timeout protection.
     * Prevents indefinite blocking if the layout job hangs without closing the channel.
     */
    private suspend fun collectLayoutPages(
        textChapter: TextChapter,
        onPage: (TextPage) -> Unit,
    ): Boolean {
        return try {
            withTimeout(30_000L) {
                for (page in textChapter.layoutChannel) {
                    ensureActive()
                    onPage(page)
                }
            }
            true
        } catch (_: TimeoutCancellationException) {
            AppLog.put("Layout channel timeout for chapter ${textChapter.chapter.index}")
            false
        }
    }

    suspend fun contentLoadFinishAwait(
        book: Book,
        chapter: BookChapter,
        content: String,
        upContent: Boolean = true,
        resetPageOffset: Boolean
    ) {
        removeLoading(chapter.index)
        if (chapter.index !in durChapterIndex - 1..durChapterIndex + 1) {
            return
        }
        chapterLayoutScheduler.submit(chapter.taskKey()) {
            layoutLoadedChapter(
                book = book,
                chapter = chapter,
                content = content,
                upContent = upContent,
                resetPageOffset = resetPageOffset,
                preserveReadAloudPosition = false,
            )
        }.await()
    }

    @Synchronized
    fun upToc() {
        val bookSource = bookSource ?: return
        val book = book ?: return
        if (!book.canUpdate) return
        if (System.currentTimeMillis() - book.lastCheckTime < 600000) return
        book.lastCheckTime = System.currentTimeMillis()
        WebBook.getChapterList(this, bookSource, book).onSuccess(IO) { cList ->
            if (book.bookUrl == ReadBook.book?.bookUrl
                && cList.size > chapterSize
            ) {
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*cList.toTypedArray())
                saveRead()
                chapterSize = cList.size
                simulatedChapterSize = book.simulatedTotalChapterNum()
                publishSnapshot()
                nextTextChapter ?: loadContent(durChapterIndex + 1)
            }
        }
    }

    fun pageAnim(): Int {
        return book?.getPageAnim() ?: ReadBookConfig.pageAnim
    }

    fun setCharset(charset: String) {
        book?.let {
            it.charset = charset
            if (it.isLocalTxt) {
                TextFile.clear()
                clearTextChapter()
            }
            callBack?.loadChapterList(it)
        }
        saveRead()
    }

    fun saveRead(pageChanged: Boolean = false) {
        val book = book ?: return
        executor.execute {
            kotlin.runCatching {
                book.lastCheckCount = 0
                book.durChapterTime = System.currentTimeMillis()
                val chapterChanged = book.durChapterIndex != durChapterIndex
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durChapterPos
                if (!pageChanged || chapterChanged) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)?.let {
                        book.durChapterTitle = it.getDisplayTitle(
                            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                            book.getUseReplaceRule(AppConfig.replaceEnableDefault),
                            chineseConverterType = AppConfig.chineseConverterType,
                        )
                        SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, bookSource, book, it)
                    }
                }
                book.update()
            }.onFailure {
                AppLog.put("保存书籍阅读进度信息出错\n$it", it)
            }
        }
    }

    /**
     * 预下载
     */
    private fun preDownload() {
        if (book?.isLocal == true) return
        executor.execute {
            if (ReadConfig.preDownloadNum < 2) {
                return@execute
            }
            preDownloadTask?.cancel()
            preDownloadTask = launch(IO) {
                //预下载
                launch {
                    val maxChapterIndex =
                        min(durChapterIndex + ReadConfig.preDownloadNum, chapterSize)
                    for (i in durChapterIndex.plus(2)..maxChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i)
                    }
                }
                launch {
                    val minChapterIndex = durChapterIndex - min(5, ReadConfig.preDownloadNum)
                    for (i in durChapterIndex.minus(2) downTo minChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i)
                    }
                }
            }
        }
    }

    fun cancelPreDownloadTask() {
        if (contentLoadFinish) {
            preDownloadTask?.cancel()
            downloadScope.coroutineContext.cancelChildren()
        }
    }

    fun onChapterListUpdated(newBook: Book) {
        if (newBook.isSameNameAuthor(book)) {
            // 本地 TXT 改目录规则后，旧目录中的章节序号已经不再能表示同一段正文。
            // 在替换目录前保留当前章节的文件偏移，随后在新目录中重新定位，避免目录
            // 已更新而阅读页仍显示旧 TextChapter 的标题和序号。
            val currentChapterStart = curTextChapter?.chapter?.start
            book = newBook
            chapterSize = newBook.totalChapterNum
            simulatedChapterSize = newBook.simulatedTotalChapterNum()
            if (newBook.isLocalTxt && currentChapterStart != null) {
                val chapters = appDb.bookChapterDao.getChapterList(newBook.bookUrl)
                val matchedIndex = chapters.indexOfFirst { chapter ->
                    val start = chapter.start
                    val end = chapter.end
                    start != null && end != null && currentChapterStart in start until end
                }
                durChapterIndex = if (matchedIndex >= 0) {
                    matchedIndex
                } else {
                    durChapterIndex.coerceIn(0, (simulatedChapterSize - 1).coerceAtLeast(0))
                }
                durChapterPos = 0
                newBook.durChapterIndex = durChapterIndex
                newBook.durChapterPos = durChapterPos
            } else if (simulatedChapterSize > 0 && durChapterIndex > simulatedChapterSize - 1) {
                durChapterIndex = simulatedChapterSize - 1
            }
            clearTextChapter()
            synchronized(this) {
                loadingChapters.clear()
                downloadedChapters.clear()
                downloadFailChapters.clear()
            }
            requestWholeBookPageEstimate()
            publishSnapshot()
            if (callBack == null) {
                return
            } else {
                loadContent(true)
            }
        }
    }

    /**
     * TXT 目录重建不会取消尚在读文件的旧内容任务。旧任务回调时章节索引可能仍然
     * 合法，但标题和偏移已经属于上一版目录，不能再进入排版或覆盖当前正文。
     */
    private fun isCurrentLocalChapter(chapter: BookChapter): Boolean {
        if (book?.isLocalTxt != true) return true
        val current =
            appDb.bookChapterDao.getChapter(chapter.bookUrl, chapter.index) ?: return false
        return current.url == chapter.url &&
                current.title == chapter.title &&
                current.start == chapter.start &&
                current.end == chapter.end
    }

    private fun clearExpiredChapterLoadingJob(clearAll: Boolean = false) {
        if (clearAll) {
            chapterLayoutScheduler.cancelAll()
        } else {
            chapterLayoutScheduler.cancelIf { key ->
                key.chapterIndex !in durChapterIndex - 1..durChapterIndex + 1
            }
        }
    }

    private fun BookChapter.taskKey() = ChapterLayoutTaskKey(
        bookUrl = bookUrl,
        chapterId = url,
        chapterIndex = index,
    )

    private data class ChapterLayoutTaskKey(
        val bookUrl: String,
        val chapterId: String,
        val chapterIndex: Int,
    )

    /**
     * 注册回调
     */
    fun register(cb: CallBack) {
        callBack?.notifyBookChanged()
        callBack = cb
    }


    /**
     * 注册渲染回调（UI 层渲染控制器）。视图就绪后立即同步一次当前内容，
     * 避免在注册前已完成的内容加载导致首帧漏渲染。
     */
    fun registerRender(cb: ReaderRenderCallback) {
        renderCallBack = cb
        if (curTextChapter != null) {
            cb.upContent()
        }
    }

    /**
     * 取消注册渲染回调
     */
    fun unregisterRender(cb: ReaderRenderCallback) {
        if (renderCallBack === cb) {
            renderCallBack = null
        }
    }

    /**
     * 取消注册回调
     */
    fun unregister(cb: CallBack) {
        if (callBack === cb) {
            callBack = null
        }
        msg = null
        preDownloadTask?.cancel()
        downloadScope.coroutineContext.cancelChildren()
        coroutineContext.cancelChildren()
        clearExpiredChapterLoadingJob(true)
        // Move expensive cleanup off the main thread
        CoroutineScope(SupervisorJob() + IO).launch {
            ImageProvider.clear()
            if (!CacheBookService.isRun) {
                CacheBook.close()
            }
        }
    }

    /**
     * 渲染子集回调（Track B）：指令式渲染协议——重绘、分页、动画、选择取消、
     * 排版进度。**只应由 UI 层的渲染控制器实现**，不得穿过 ViewModel/业务层。
     */
    interface ReaderRenderCallback : LayoutProgressListener {
        fun upContent(
            relativePosition: Int = 0,
            resetPageOffset: Boolean = true,
            success: (() -> Unit)? = null
        )

        suspend fun upContentAwait(
            relativePosition: Int = 0,
            resetPageOffset: Boolean = true,
            success: (() -> Unit)? = null
        )

        fun pageChanged()

        fun contentLoadFinish()

        fun upPageAnim(upRecorder: Boolean = false)

        fun cancelSelect()
    }

    /**
     * 业务/UI 状态回调子集：菜单刷新、目录加载、换书通知、进度确认。
     *
     * Track B2 起本接口已与渲染子集解耦——不再穿过任何指令式渲染方法。
     * 渲染走独立的 [renderCallBack]（由 UI 层渲染控制器实现），业务状态刷新
     * 走 [snapshot] → ViewModel 的反应式收集。由 ReadBookViewModel 实现。
     */
    interface CallBack {
        fun upMenuView()

        fun loadChapterList(book: Book)

        fun notifyBookChanged()

        fun sureNewProgress(progress: BookProgress)
    }

}
