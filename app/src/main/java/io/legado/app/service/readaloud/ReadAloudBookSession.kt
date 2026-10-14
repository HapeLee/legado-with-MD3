package io.legado.app.service.readaloud

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.feature.reader.core.navigation.ReaderChapterPaginationSnapshot
import io.legado.app.feature.reader.core.navigation.ReaderPageNavigator
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.legacy.LegacyReaderChapterPaginationResult
import io.legado.app.feature.reader.legacy.LegacyReaderChapterPaginator
import io.legado.app.feature.reader.legacy.LegacyReaderPageDecorationFactory
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.globalExecutor
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.help.readaloud.cast.CastRenderOptions
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.ReaderPaginationEnvironment
import io.legado.app.model.cache.CacheDownloadRepository
import io.legado.app.model.reader.ReaderChapterInput
import io.legado.app.model.translation.TranslationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Android playback owns its book and chapter even while the reader displays another book. */
class ReadAloudBookSession : KoinComponent {
    data class Snapshot(
        val book: Book? = null,
        val bookSource: BookSource? = null,
        val chapterIndex: Int = -1,
        val chapterPosition: Int = 0,
        val input: ReaderChapterInput? = null,
        val pagination: ReaderChapterPaginationSnapshot? = null,
        val environment: ReaderPaginationEnvironment? = null,
        val generation: Long = 0,
        val titleMode: Int = 0,
        val paragraphIndent: String = "",
        val textFullJustify: Boolean = false,
        val styleName: String = "",
        val headerExtentPx: Float = 0f,
        val footerExtentPx: Float = 0f,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state = _state.asStateFlow()
    val book get() = _state.value.book
    val chapterIndex get() = _state.value.chapterIndex
    val input get() = _state.value.input
    val pagination get() = _state.value.pagination
    private val readSettings: ReadSettingsGateway by inject()
    private val otherSettings: OtherSettingsGateway by inject()
    private val readRecords: ReadRecordRepository by inject()
    private val readAloudSettings: ReadAloudSettingsGateway by inject()
    private var lastSavedAt = 0L
    private var lastSavedChapterIndex = -1
    private var readRecord: ReadRecordSession? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun owns(bookUrl: String?): Boolean = bookUrl != null && bookUrl == book?.bookUrl

    @Synchronized
    fun adopt(request: Snapshot) {
        finishReadTime()
        _state.value = request.copy(book = request.book?.copy(), generation = _state.value.generation + 1)
        lastSavedAt = 0L
        lastSavedChapterIndex = request.book?.durChapterIndex ?: -1
    }

    @Synchronized
    fun selectChapter(index: Int) {
        val current = _state.value
        _state.value = current.copy(
            chapterIndex = index, chapterPosition = 0,
            input = current.input?.takeIf { it.chapter.index == index },
            pagination = current.pagination?.takeIf { it.chapterIndex == index },
            generation = current.generation + 1,
        )
    }

    /** Reuse visible pagination only when both book and chapter belong to playback. */
    @Synchronized
    fun syncReaderInput() {
        val current = _state.value
        if (!owns(ReadBook.book?.bookUrl) || current.chapterIndex != ReadBook.durChapterIndex) return
        val input = ReadBook.readerChapterInputFor(current.chapterIndex) ?: return
        if (input.book.bookUrl != current.book?.bookUrl) return
        val pagination = ReadBook.readerPagination(current.chapterIndex) ?: return
        _state.value = current.copy(input = input, pagination = pagination,
            environment = ReadBook.paginationEnvironment ?: current.environment,
            titleMode = ReadBookConfig.titleMode, paragraphIndent = ReadBookConfig.paragraphIndent,
            textFullJustify = ReadBookConfig.textFullJustify, styleName = ReadBookConfig.durConfig.name,
            headerExtentPx = LegacyReaderPageDecorationFactory.headerExtentPx(),
            footerExtentPx = LegacyReaderPageDecorationFactory.footerExtentPx())
    }

    suspend fun prepareChapter(): Snapshot = withContext(Dispatchers.IO) {
        val request = _state.value
        if (request.input != null && request.pagination != null) return@withContext request
        val book = requireNotNull(request.book) { "没有朗读书籍" }
        val chapter = requireNotNull(appDb.bookChapterDao.getChapter(book.bookUrl, request.chapterIndex)) {
            "朗读章节不存在"
        }
        val translated = if (book.getTranslationMode()) {
            TranslationManager.getCachedTranslation(book, chapter).also {
                if (it == null) TranslationManager.startTranslation(book, chapter)
            }
        } else null
        val content = translated ?: BookHelp.getContent(book, chapter)
            ?: CacheDownloadRepository().downloadContentAwait(
                requireNotNull(request.bookSource) { "没有书源" }, book, chapter,
            )
        val processor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            processor.getTitleReplaceRules(),
            book.getUseReplaceRule(otherSettings.currentSettings.replaceEnableDefault),
            chineseConverterType = readSettings.currentSettings.chineseConverterType,
        )
        val processed = processor.getContent(book, chapter, content, includeTitle = false,
            paragraphIndent = request.paragraphIndent)
        val source = ReaderChapterSourceParser.parse(
            chapterIndex = chapter.index, title = displayTitle, paragraphs = processed.textList,
            includeTitle = false, adaptSpecialStyle = readSettings.currentSettings.adaptSpecialStyle,
            htmlSemanticTextResolver = AndroidReaderHtmlSemanticTextResolver,
            castLabels = if (readAloudSettings.currentSettings.multiRoleCast) {
                CastAssignmentStore.labelsForChapter(book.bookUrl, chapter.index)
            } else emptyMap(),
            bgmScenes = if (readAloudSettings.currentSettings.bgmAssign) {
                BgmSceneStore.labelsForChapter(book.bookUrl, chapter.index)
            } else emptyMap(),
        )
        val input = ReaderChapterInput(
            book, request.bookSource, chapter, displayTitle, processed, source,
            processed.hashCode(), processed.effectiveContentProcesses.hashCode(), source.hashCode(),
            request.bookSource.hashCode(), 0,
        )
        val environment = requireNotNull(request.environment) { "朗读分页环境未就绪" }
        val result = LegacyReaderChapterPaginator.paginate(
            book = book, bookSource = request.bookSource, chapter = chapter, displayTitle = displayTitle,
            content = processed, source = source, revision = request.generation,
            viewportWidthPx = environment.widthPx, viewportHeightPx = environment.heightPx,
            contentPaddingLeftPx = environment.contentPaddingLeftPx,
            contentPaddingTopPx = environment.contentPaddingTopPx,
            contentPaddingRightPx = environment.contentPaddingRightPx,
            contentPaddingBottomPx = environment.contentPaddingBottomPx,
            paginationStyle = environment.style,
            highlightRules = HighlightRuleRepository().loadEnabled(request.styleName),
            castOptions = CastRenderOptions.load(book.bookUrl, chapter.index),
            titleMode = request.titleMode, paragraphIndent = request.paragraphIndent,
            textFullJustify = request.textFullJustify,
            headerExtentPx = request.headerExtentPx, footerExtentPx = request.footerExtentPx,
        )
        val pages = when (result) {
            is LegacyReaderChapterPaginationResult.Success -> result.pages
            is LegacyReaderChapterPaginationResult.Unsupported -> error("朗读分页失败: ${result.reason}")
        }
        check(pages.isNotEmpty()) { "朗读分页为空" }
        val pagination = ReaderChapterPaginationSnapshot(
            chapter.index, pages.map(ReaderPageNavigator::pageStart), source.semanticContent.length,
            request.generation,
        )
        currentCoroutineContext().ensureActive()
        synchronized(this@ReadAloudBookSession) {
            if (_state.value.generation != request.generation) throw CancellationException("朗读请求已替换")
            _state.value = _state.value.copy(input = input, pagination = pagination)
            _state.value
        }
    }

    @Synchronized
    fun updateProgress(position: Int, forceSave: Boolean = false, expectedChapterIndex: Int = chapterIndex) {
        val current = _state.value
        if (current.chapterIndex != expectedChapterIndex) return
        val book = current.book ?: return
        val now = System.currentTimeMillis()
        val updated = book.copy(
            durChapterIndex = current.chapterIndex, durChapterPos = position.coerceAtLeast(0),
            durChapterTitle = current.input?.displayTitle ?: book.durChapterTitle,
            durChapterTime = now,
        )
        _state.value = current.copy(book = updated, chapterPosition = updated.durChapterPos)
        if (owns(ReadBook.book?.bookUrl) && ReadBook.isUiActive) {
            lastSavedChapterIndex = current.chapterIndex
            return
        }
        if (!forceSave && now - lastSavedAt < 1000L) return
        lastSavedAt = now
        val chapterChanged = lastSavedChapterIndex != current.chapterIndex
        lastSavedChapterIndex = current.chapterIndex
        // Narrow update: playback must not overwrite book settings changed in another screen.
        globalExecutor.execute {
            runCatching {
                appDb.bookDao.upReadAloudProgress(updated.bookUrl, updated.durChapterIndex,
                    updated.durChapterPos, updated.durChapterTitle, updated.durChapterTime)
                if (chapterChanged) {
                    current.input?.chapter?.let { chapter ->
                        SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, current.bookSource, updated, chapter)
                    }
                }
            }.onFailure { AppLog.put("保存朗读进度失败", it) }
        }
    }

    fun uploadProgress() {
        val book = book ?: return
        persistenceScope.launch {
            AppWebDav.uploadBookProgress(book) { ReadBook.onProgressUploaded(book) }
        }
    }

    @Synchronized
    fun upReadTime() {
        val book = book ?: return
        if (owns(ReadBook.book?.bookUrl) && ReadBook.isUiActive) {
            finishReadTime()
            ReadBook.upReadTime()
            return
        }
        val now = System.currentTimeMillis()
        val record = readRecord ?: ReadRecordSession(
            bookUrl = book.bookUrl, bookName = book.name, bookAuthor = book.author,
            startTime = now, endTime = now, words = chapterIndex.toLong(),
        )
        readRecord = record.copy(endTime = now, words = chapterIndex.toLong())
        if (now - record.startTime >= 120_000L) finishReadTime()
    }

    @Synchronized
    fun finishReadTime() {
        val record = readRecord ?: return
        readRecord = null
        if (record.endTime - record.startTime < 10_000L) return
        persistenceScope.launch {
            runCatching { readRecords.saveReadSession(record) }
                .onFailure { AppLog.put("保存朗读时长失败", it) }
        }
    }

    companion object {
        fun captureReader(): Snapshot? {
            val book = ReadBook.book?.copy() ?: return null
            val index = ReadBook.durChapterIndex
            return Snapshot(
                book = book, bookSource = ReadBook.bookSource, chapterIndex = index,
                chapterPosition = ReadBook.durChapterPos,
                input = ReadBook.readerChapterInputFor(index),
                pagination = ReadBook.readerPagination(index),
                environment = ReadBook.paginationEnvironment,
                titleMode = ReadBookConfig.titleMode,
                paragraphIndent = ReadBookConfig.paragraphIndent,
                textFullJustify = ReadBookConfig.textFullJustify,
                styleName = ReadBookConfig.durConfig.name,
                headerExtentPx = LegacyReaderPageDecorationFactory.headerExtentPx(),
                footerExtentPx = LegacyReaderPageDecorationFactory.footerExtentPx(),
            )
        }
    }
}
