package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.CleanSelectedTextUseCase
import io.legado.app.domain.usecase.GenerateChapterSummaryUseCase
import io.legado.app.domain.usecase.SaveBookContentProcessUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.translation.TranslationManager
import io.legado.app.utils.MD5Utils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * 阅读页 AI 域：章节摘要、划词净化、划词重写、重写预设配置。
 *
 * 自持 [ReadAiUiState]，与阅读态解耦；只通过 [Host] 反向触达阅读页的
 * sheet 开合、菜单关闭、正文重载与 Toast。
 *
 * 章节读取仍走 [Host]——AI 域不自带 DAO 直连，避免把 `legacyDaoInjectionBaseline`
 * 的 ViewModel 棘轮债洗进宽松的 `legacyUiDaoAccessBaseline`。这几处随 R2.1
 * 的 `ReaderSession` 溶解一并清理。
 */
class ReadAiDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val generateChapterSummaryUseCase: GenerateChapterSummaryUseCase,
    private val cleanSelectedTextUseCase: CleanSelectedTextUseCase,
    private val aiTextFactoryUseCase: AiTextFactoryUseCase,
    private val saveBookContentProcessUseCase: SaveBookContentProcessUseCase,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiPromptPresetGateway: AiPromptPresetGateway,
) {

    /** AI 域对阅读页的全部反向依赖，实现在 `ReadBookViewModel`。 */
    interface Host {
        /** 当前活动 sheet，用于判断流式回调是否还属于当前会话。 */
        val activeSheet: ReadBookSheet?

        /** 当前章节名，开摘要/净化时作为标题回退值。 */
        val chapterName: String

        fun setActiveSheet(sheet: ReadBookSheet?)

        fun closeReadMenu()

        fun showToast(message: String)

        fun reloadChapterAfterContentProcessChanged(bookUrl: String, chapterIndex: Int)

        suspend fun findChapter(bookUrl: String, chapterIndex: Int): BookChapter?

        suspend fun listChapters(bookUrl: String): List<BookChapter>
    }

    private val _uiState = MutableStateFlow(ReadAiUiState())
    val uiState = _uiState.asStateFlow()

    private var chapterSummaryJob: Job? = null
    private var aiTextCleanJob: Job? = null
    private var aiTextRewriteJob: Job? = null
    private var pendingAiTextCleanRequest: PendingAiTextCleanRequest? = null
    private var pendingAiTextRewriteRequest: PendingAiTextRewriteRequest? = null

    /**
     * 关闭 sheet 时的收尾：取消在途任务并清空对应子状态。
     * `activeSheet` 本身由 `ReadBookViewModel` 统一置空。
     */
    fun onSheetDismissed(sheet: ReadBookSheet?) {
        when (sheet) {
            is ReadBookSheet.ChapterSummary -> {
                chapterSummaryJob?.cancel()
                _uiState.update { it.copy(chapterSummary = ChapterSummaryUiState()) }
            }

            is ReadBookSheet.AiTextClean -> {
                aiTextCleanJob?.cancel()
                pendingAiTextCleanRequest = null
                _uiState.update { it.copy(aiTextClean = AiTextCleanUiState()) }
            }

            is ReadBookSheet.AiRewritePresetConfig -> {
                _uiState.update { it.copy(aiRewritePresetConfig = AiRewritePresetConfigUiState()) }
            }

            else -> Unit
        }
    }

    // --- 章节摘要 ---

    fun openChapterSummary() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        val chapterTitle = host.chapterName
        host.closeReadMenu()
        _uiState.update {
            it.copy(
                chapterSummary = ChapterSummaryUiState(
                    bookUrl = book.bookUrl,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    isLoading = true,
                ),
            )
        }
        host.setActiveSheet(ReadBookSheet.ChapterSummary)
        generateChapterSummary(book.bookUrl, chapterIndex)
    }

    fun retryChapterSummary() {
        val summary = _uiState.value.chapterSummary
        if (summary.bookUrl.isBlank() || summary.chapterIndex < 0) return
        _uiState.update {
            it.copy(
                chapterSummary = summary.copy(
                    isLoading = true,
                    summary = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
        generateChapterSummary(summary.bookUrl, summary.chapterIndex)
    }

    private fun generateChapterSummary(bookUrl: String, chapterIndex: Int) {
        chapterSummaryJob?.cancel()
        chapterSummaryJob = scope.launch {
            val book = ReadBook.book
            if (book == null || book.bookUrl != bookUrl) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.ai_chapter_changed),
                )
                return@launch
            }
            val chapter = withContext(IO) {
                host.findChapter(bookUrl, chapterIndex)
            }
            if (chapter == null) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.no_chapter),
                )
                return@launch
            }
            val content = withContext(IO) {
                getEffectiveChapterContent(book, chapter)
            }
            if (content.isBlank()) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.ai_chapter_content_unavailable),
                )
                return@launch
            }
            try {
                withContext(IO) {
                    generateChapterSummaryUseCase.start(
                        book = book,
                        bookChapter = chapter,
                        contentOverride = content,
                        reasoningLevel = _uiState.value.chapterSummary.reasoningLevel,
                    )
                }
                generateChapterSummaryUseCase.observeTask(bookUrl, chapterIndex).collect { task ->
                    if (!isCurrentChapterSummary(
                            bookUrl,
                            chapterIndex
                        ) || task == null
                    ) return@collect
                    _uiState.update { state ->
                        val summary = state.chapterSummary
                        when (task.status) {
                            AiArtifact.STATUS_RUNNING -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = true,
                                    summary = task.output.orEmpty(),
                                    reasoningText = task.reasoning,
                                    errorMessage = null,
                                ),
                            )

                            AiArtifact.STATUS_SUCCESS -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = false,
                                    summary = task.output.orEmpty(),
                                    reasoningText = task.reasoning,
                                    errorMessage = null,
                                ),
                            )

                            AiArtifact.STATUS_FAILED -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = false,
                                    errorMessage = task.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                ),
                            )

                            else -> state
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    aiErrorMessage(error),
                )
            }
        }
    }

    private fun getEffectiveChapterContent(book: Book, chapter: BookChapter): String {
        val sourceContent = if (book.getTranslationMode()) {
            TranslationManager.getCachedTranslation(book, chapter)
                ?: BookHelp.getContent(book, chapter)
        } else {
            BookHelp.getContent(book, chapter)
        } ?: return ""
        return ContentProcessor.get(book)
            .getContent(book, chapter, sourceContent, includeTitle = false)
            .toString()
    }

    private fun isCurrentChapterSummary(bookUrl: String, chapterIndex: Int): Boolean {
        val summary = _uiState.value.chapterSummary
        return host.activeSheet is ReadBookSheet.ChapterSummary &&
                summary.bookUrl == bookUrl &&
                summary.chapterIndex == chapterIndex
    }

    private fun updateChapterSummaryError(
        bookUrl: String,
        chapterIndex: Int,
        message: String,
    ) {
        if (!isCurrentChapterSummary(bookUrl, chapterIndex)) return
        _uiState.update {
            it.copy(
                chapterSummary = it.chapterSummary.copy(
                    isLoading = false,
                    errorMessage = message,
                )
            )
        }
    }

    fun setChapterSummaryReasoningLevel(level: AiReasoningLevel) {
        _uiState.update { it.copy(chapterSummary = it.chapterSummary.copy(reasoningLevel = level)) }
    }

    // --- 划词净化 ---

    fun openAiTextClean(
        text: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val book = ReadBook.book ?: return
        if (text.isBlank()) {
            host.showToast(context.getString(R.string.ai_text_clean_empty_selection))
            return
        }
        if (chapterIndex != ReadBook.durChapterIndex) {
            host.showToast(context.getString(R.string.ai_chapter_changed))
            return
        }
        val chapterTitle = host.chapterName
        val visibleContent = ReadBook.curTextChapter?.getContent().orEmpty()
        val (contextBefore, contextAfter) = buildSelectionContext(
            content = visibleContent,
            selectedText = text,
            approximatePosition = chapterPosition,
        )
        val request = PendingAiTextCleanRequest(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            chapterPosition = chapterPosition,
            originalText = text,
            contextBefore = contextBefore,
            contextAfter = contextAfter,
        )
        pendingAiTextCleanRequest = request
        host.closeReadMenu()
        _uiState.update {
            it.copy(
                aiTextClean = AiTextCleanUiState(
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    chapterTitle = request.chapterTitle,
                    isLoading = true,
                    originalText = request.originalText,
                ),
            )
        }
        host.setActiveSheet(ReadBookSheet.AiTextClean)
        generateAiTextClean(request)
    }

    fun retryAiTextClean() {
        val request = pendingAiTextCleanRequest ?: return
        _uiState.update {
            it.copy(
                aiTextClean = it.aiTextClean.copy(
                    isLoading = true,
                    replacementText = "",
                    streamingText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
        generateAiTextClean(request)
    }

    private fun generateAiTextClean(request: PendingAiTextCleanRequest) {
        aiTextCleanJob?.cancel()
        aiTextCleanJob = scope.launch {
            try {
                val taskId = withContext(IO) {
                    cleanSelectedTextUseCase.start(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        selectedText = request.originalText,
                        contextBefore = request.contextBefore,
                        contextAfter = request.contextAfter,
                        reasoningLevel = _uiState.value.aiTextClean.reasoningLevel,
                    )
                }
                cleanSelectedTextUseCase.observeTaskById(taskId).collect { task ->
                    val snapshot = task ?: return@collect
                    if (!isCurrentAiTextClean(request)) return@collect
                    val clean = _uiState.value.aiTextClean
                    when (snapshot.status) {
                        AiArtifact.STATUS_RUNNING -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = true,
                                    streamingText = snapshot.output.orEmpty(),
                                    reasoningText = snapshot.reasoning,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_SUCCESS -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = false,
                                    replacementText = snapshot.output.orEmpty(),
                                    streamingText = "",
                                    reasoningText = snapshot.reasoning,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_FAILED -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = false,
                                    errorMessage = snapshot.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                )
                            )
                        }

                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAiTextClean(request)) {
                    _uiState.update {
                        it.copy(
                            aiTextClean = it.aiTextClean.copy(
                                isLoading = false,
                                errorMessage = aiErrorMessage(error),
                            )
                        )
                    }
                }
            }
        }
    }

    fun confirmAiTextClean() {
        val cleanState = _uiState.value.aiTextClean
        val book = ReadBook.book ?: return
        if (cleanState.isLoading || cleanState.isApplying || cleanState.errorMessage != null) return
        if (book.bookUrl != cleanState.bookUrl ||
            ReadBook.durChapterIndex != cleanState.chapterIndex
        ) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_chapter_changed)
                    )
                )
            }
            return
        }
        val pattern = normalizeAiReplacementText(cleanState.originalText)
        val replacement = normalizeAiReplacementText(cleanState.replacementText)
        if (pattern.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_empty_selection)
                    )
                )
            }
            return
        }
        if (pattern == replacement) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_no_change)
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(aiTextClean = it.aiTextClean.copy(isApplying = true))
        }
        scope.launch {
            try {
                saveBookContentProcessUseCase.saveReplacement(
                    bookUrl = cleanState.bookUrl,
                    chapterIndex = cleanState.chapterIndex,
                    chapterPosition = pendingAiTextCleanRequest?.chapterPosition ?: 0,
                    selectedText = pattern,
                    contextBefore = pendingAiTextCleanRequest?.contextBefore.orEmpty(),
                    contextAfter = pendingAiTextCleanRequest?.contextAfter.orEmpty(),
                    replacementText = replacement,
                ).getOrThrow()
                host.reloadChapterAfterContentProcessChanged(
                    bookUrl = cleanState.bookUrl,
                    chapterIndex = cleanState.chapterIndex,
                )
                pendingAiTextCleanRequest = null
                host.setActiveSheet(null)
                _uiState.update { it.copy(aiTextClean = AiTextCleanUiState()) }
                host.showToast(context.getString(R.string.ai_text_clean_rule_created))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        aiTextClean = it.aiTextClean.copy(
                            isApplying = false,
                            errorMessage = error.localizedMessage
                                ?: context.getString(R.string.error),
                        )
                    )
                }
            }
        }
    }

    fun setAiTextCleanReasoningLevel(level: AiReasoningLevel) {
        _uiState.update { it.copy(aiTextClean = it.aiTextClean.copy(reasoningLevel = level)) }
    }

    // --- 划词重写 ---

    fun openAiCurrentChapterRewrite() {
        val book = ReadBook.book ?: return
        val chapter = ReadBook.curTextChapter?.chapter ?: return
        scope.launch {
            val text = withContext(IO) {
                getEffectiveChapterContent(book, chapter).trim()
            }
            if (text.isBlank()) {
                host.showToast(context.getString(R.string.ai_chapter_content_unavailable))
                return@launch
            }
            openAiTextRewrite(
                text = text,
                chapterIndex = ReadBook.durChapterIndex,
                chapterPosition = 0,
            )
        }
    }

    /**
     * R2.1：章节标题查询改走 `Host` 的挂起方法（背后是 `BookRepository`，在 IO 上执行），
     * 因此整个打开流程移进 [scope]——原先是在主线程上同步查库。
     */
    suspend fun openAiTextRewrite(
        text: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val book = ReadBook.book ?: return
        val chapterTitle = host.findChapter(book.bookUrl, chapterIndex)?.title
            ?: host.chapterName
        val visibleContent = ReadBook.curTextChapter?.getContent().orEmpty()
        val (contextBefore, contextAfter) = buildSelectionContext(
            content = visibleContent,
            selectedText = text,
            approximatePosition = chapterPosition,
        )
        val presets = loadAiRewritePresets()
        val selectedPresetId = _uiState.value.aiTextRewrite.selectedPresetId
            .takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull()?.id.orEmpty()
        val request = PendingAiTextRewriteRequest(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            chapterPosition = chapterPosition,
            originalText = text,
            sourceContentHash = buildAiRewriteSourceContentHash(text),
            contextBefore = contextBefore,
            contextAfter = contextAfter,
        )
        pendingAiTextRewriteRequest = request
        host.closeReadMenu()
        val currentRewriteState = _uiState.value.aiTextRewrite
        val isSameRewriteTarget = currentRewriteState.bookUrl == request.bookUrl &&
                currentRewriteState.chapterIndex == request.chapterIndex &&
                currentRewriteState.originalText == request.originalText
        if (!isSameRewriteTarget) {
            aiTextRewriteJob?.cancel()
        }
        _uiState.update {
            it.copy(
                aiTextRewrite = if (isSameRewriteTarget) {
                    it.aiTextRewrite.copy(
                        chapterTitle = request.chapterTitle,
                        selectedPresetId = selectedPresetId,
                        presets = presets.toImmutableList(),
                    )
                } else {
                    AiTextRewriteUiState(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        originalText = request.originalText,
                        selectedPresetId = selectedPresetId,
                        presets = presets.toImmutableList(),
                    )
                },
            )
        }
        host.setActiveSheet(ReadBookSheet.AiTextRewrite)
        loadAiRewriteHistory(request, selectLatest = !isSameRewriteTarget)
    }

    fun selectAiRewritePreset(presetId: String) {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    selectedPresetId = presetId,
                    errorMessage = null,
                )
            )
        }
    }

    fun setAiRewriteTemporaryInstruction(instruction: String) {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    temporaryInstruction = instruction,
                    errorMessage = null,
                )
            )
        }
    }

    fun selectAiRewriteHistory(artifactId: String) {
        val historyItem = _uiState.value.aiTextRewrite.history
            .firstOrNull { it.artifactId == artifactId }
            ?: return
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    rewrittenText = historyItem.text,
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
    }

    private fun loadAiRewriteHistory(
        request: PendingAiTextRewriteRequest,
        selectLatest: Boolean,
    ) {
        scope.launch {
            val history = withContext(IO) {
                aiArtifactGateway.getArtifactsByContentHash(
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    taskType = AiTaskType.REWRITE_TEXT,
                    contentHash = request.sourceContentHash,
                ).mapNotNull { artifact ->
                    val text = artifact.output?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    AiRewriteHistoryUi(
                        artifactId = artifact.id,
                        text = text,
                        timeText = formatAiRewriteHistoryTime(artifact.updatedAt),
                    )
                }
            }
            val latest = history.firstOrNull()
            _uiState.update { state ->
                if (
                    state.aiTextRewrite.bookUrl != request.bookUrl ||
                    state.aiTextRewrite.chapterIndex != request.chapterIndex ||
                    state.aiTextRewrite.originalText != request.originalText
                ) {
                    state
                } else {
                    state.copy(
                        aiTextRewrite = state.aiTextRewrite.copy(
                            history = history.toImmutableList(),
                            rewrittenText = if (
                                selectLatest &&
                                !state.aiTextRewrite.isLoading &&
                                state.aiTextRewrite.rewrittenText.isBlank() &&
                                latest != null
                            ) {
                                latest.text
                            } else {
                                state.aiTextRewrite.rewrittenText
                            },
                        )
                    )
                }
            }
        }
    }

    private fun formatAiRewriteHistoryTime(timestamp: Long): String {
        val date = Date(timestamp)
        val dateText = android.text.format.DateFormat.getDateFormat(context).format(date)
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(date)
        return "$dateText $timeText"
    }

    private fun buildAiRewriteSourceContentHash(text: String): String {
        return MD5Utils.md5Encode(normalizeAiReplacementText(text))
    }

    fun generateSelectedAiTextRewrite() {
        val state = _uiState.value.aiTextRewrite
        val preset = state.presets.firstOrNull { it.id == state.selectedPresetId }
        if (preset == null) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_no_preset)
                    )
                )
            }
            return
        }
        val request = pendingAiTextRewriteRequest ?: return
        generateAiTextRewrite(
            request = request,
            preset = preset,
            temporaryInstruction = state.temporaryInstruction,
        )
    }

    fun retryAiTextRewrite() {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    isLoading = false,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                )
            )
        }
        generateSelectedAiTextRewrite()
    }

    fun setAiTextRewriteReasoningLevel(level: AiReasoningLevel) {
        _uiState.update { it.copy(aiTextRewrite = it.aiTextRewrite.copy(reasoningLevel = level)) }
    }

    private fun generateAiTextRewrite(
        request: PendingAiTextRewriteRequest,
        preset: AiRewritePresetUi,
        temporaryInstruction: String,
    ) {
        aiTextRewriteJob?.cancel()
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    isLoading = true,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                )
            )
        }
        aiTextRewriteJob = scope.launch {
            try {
                val referenceContext = buildAiRewriteReferenceContext(request)
                val aiRequest = AiTextFactoryUseCase.Request(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        inputText = request.originalText,
                        taskType = AiTaskType.REWRITE_TEXT,
                        userInstruction = buildAiRewriteInstruction(
                            preset.instruction,
                            temporaryInstruction,
                        ),
                        referenceText = referenceContext.text,
                        skipCache = true,
                        artifactContentHash = request.sourceContentHash,
                    reasoningLevel = _uiState.value.aiTextRewrite.reasoningLevel,
                    )
                val taskId = withContext(IO) { aiTextFactoryUseCase.start(aiRequest) }
                aiTextFactoryUseCase.observeTaskById(taskId).collect { task ->
                    val snapshot = task ?: return@collect
                    if (!isCurrentAiTextRewrite(request)) return@collect
                    val rewrite = _uiState.value.aiTextRewrite
                    when (snapshot.status) {
                        AiArtifact.STATUS_RUNNING -> _uiState.update {
                            it.copy(
                                aiTextRewrite = rewrite.copy(
                                    isLoading = true,
                                    rewrittenText = snapshot.output.orEmpty(),
                                    reasoningText = snapshot.reasoning,
                                    referenceCount = referenceContext.count,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_SUCCESS -> {
                            _uiState.update {
                                it.copy(
                                    aiTextRewrite = rewrite.copy(
                                        isLoading = false,
                                        rewrittenText = snapshot.output.orEmpty(),
                                        reasoningText = snapshot.reasoning,
                                        referenceCount = referenceContext.count,
                                        errorMessage = null,
                                    )
                                )
                            }
                            loadAiRewriteHistory(request, selectLatest = false)
                        }

                        AiArtifact.STATUS_FAILED -> _uiState.update {
                            it.copy(
                                aiTextRewrite = rewrite.copy(
                                    isLoading = false,
                                    errorMessage = snapshot.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                )
                            )
                        }

                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAiTextRewrite(request)) {
                    _uiState.update {
                        it.copy(
                            aiTextRewrite = it.aiTextRewrite.copy(
                                isLoading = false,
                                errorMessage = aiErrorMessage(error),
                            )
                        )
                    }
                }
            }
        }
    }

    fun confirmAiTextRewrite() {
        val rewriteState = _uiState.value.aiTextRewrite
        val book = ReadBook.book ?: return
        if (rewriteState.isLoading || rewriteState.isApplying || rewriteState.errorMessage != null) return
        if (book.bookUrl != rewriteState.bookUrl ||
            ReadBook.durChapterIndex != rewriteState.chapterIndex
        ) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_chapter_changed)
                    )
                )
            }
            return
        }
        val pattern = normalizeAiReplacementText(rewriteState.originalText)
        val replacement = normalizeAiReplacementText(rewriteState.rewrittenText)
        if (pattern.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_empty_selection)
                    )
                )
            }
            return
        }
        if (replacement.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_empty_result)
                    )
                )
            }
            return
        }
        if (pattern == replacement) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_no_change)
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(aiTextRewrite = it.aiTextRewrite.copy(isApplying = true))
        }
        scope.launch {
            try {
                saveBookContentProcessUseCase.saveReplacement(
                    bookUrl = rewriteState.bookUrl,
                    chapterIndex = rewriteState.chapterIndex,
                    chapterPosition = pendingAiTextRewriteRequest?.chapterPosition ?: 0,
                    selectedText = pattern,
                    contextBefore = pendingAiTextRewriteRequest?.contextBefore.orEmpty(),
                    contextAfter = pendingAiTextRewriteRequest?.contextAfter.orEmpty(),
                    replacementText = replacement,
                    kind = BookContentProcess.KIND_AI_REWRITE,
                ).getOrThrow()
                host.reloadChapterAfterContentProcessChanged(
                    bookUrl = rewriteState.bookUrl,
                    chapterIndex = rewriteState.chapterIndex,
                )
                pendingAiTextRewriteRequest = null
                host.setActiveSheet(null)
                _uiState.update { it.copy(aiTextRewrite = AiTextRewriteUiState()) }
                host.showToast(context.getString(R.string.ai_text_rewrite_saved))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        aiTextRewrite = it.aiTextRewrite.copy(
                            isApplying = false,
                            errorMessage = error.localizedMessage
                                ?: context.getString(R.string.error),
                        )
                    )
                }
            }
        }
    }

    // --- 重写预设配置 ---

    fun openAiRewritePresetConfig() {
        val presets = loadAiRewritePresets()
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = false,
                    presets = presets.toImmutableList(),
                    errorMessage = null,
                ),
            )
        }
        host.setActiveSheet(ReadBookSheet.AiRewritePresetConfig)
    }

    fun closeAiRewritePresetConfig() {
        val nextSheet = if (pendingAiTextRewriteRequest != null) {
            ReadBookSheet.AiTextRewrite
        } else {
            null
        }
        host.setActiveSheet(nextSheet)
        _uiState.update { it.copy(aiRewritePresetConfig = AiRewritePresetConfigUiState()) }
    }

    fun startAddAiRewritePreset() {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = true,
                    editingPresetId = null,
                    editingName = "",
                    editingInstruction = "",
                    errorMessage = null,
                )
            )
        }
    }

    fun startEditAiRewritePreset(preset: AiRewritePresetUi) {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = true,
                    editingPresetId = preset.id,
                    editingName = preset.name,
                    editingInstruction = preset.instruction,
                    errorMessage = null,
                )
            )
        }
    }

    fun setAiRewritePresetName(name: String) {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editingName = name,
                    errorMessage = null,
                )
            )
        }
    }

    fun setAiRewritePresetInstruction(instruction: String) {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editingInstruction = instruction,
                    errorMessage = null,
                )
            )
        }
    }

    fun saveAiRewritePreset() {
        val config = _uiState.value.aiRewritePresetConfig
        val name = config.editingName.trim()
        val instruction = config.editingInstruction.trim()
        if (name.isBlank() || instruction.isBlank()) {
            _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_preset_empty)
                    )
                )
            }
            return
        }
        val editingId = config.editingPresetId
        val savedPresets = if (editingId == null) {
            config.presets + AiRewritePresetUi(
                id = UUID.randomUUID().toString(),
                name = name,
                instruction = instruction,
            )
        } else {
            config.presets.map { preset ->
                if (preset.id == editingId) {
                    preset.copy(name = name, instruction = instruction)
                } else {
                    preset
                }
            }
        }
        saveAiRewritePresets(savedPresets)
        syncAiRewritePresets(savedPresets)
        clearAiRewritePresetDraft()
    }

    fun clearAiRewritePresetDraft() {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = false,
                    editingPresetId = null,
                    editingName = "",
                    editingInstruction = "",
                    errorMessage = null,
                )
            )
        }
    }

    fun requestDeleteAiRewritePreset(preset: AiRewritePresetUi) {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(deletePreset = preset)
            )
        }
    }

    fun dismissDeleteAiRewritePreset() {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(deletePreset = null)
            )
        }
    }

    fun deleteAiRewritePreset() {
        val deletePreset = _uiState.value.aiRewritePresetConfig.deletePreset ?: return
        val savedPresets = _uiState.value.aiRewritePresetConfig.presets
            .filterNot { it.id == deletePreset.id }
        aiPromptPresetGateway.deletePresetSync(deletePreset.id)
        syncAiRewritePresets(savedPresets)
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(deletePreset = null)
            )
        }
    }

    private fun syncAiRewritePresets(presets: List<AiRewritePresetUi>) {
        val selected = _uiState.value.aiTextRewrite.selectedPresetId
            .takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull()?.id.orEmpty()
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    presets = presets.toImmutableList(),
                    selectedPresetId = selected,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                ),
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    presets = presets.toImmutableList(),
                ),
            )
        }
    }

    private fun loadAiRewritePresets(): List<AiRewritePresetUi> {
        if (aiPromptPresetGateway.countByTaskTypeSync(AiTaskType.REWRITE_TEXT) == 0) {
            aiPromptPresetGateway.savePresetsSync(
                defaultAiRewritePresets().mapIndexed { index, preset ->
                    preset.toAiPromptPreset(index)
                }
            )
        }
        return aiPromptPresetGateway.getEnabledByTaskType(AiTaskType.REWRITE_TEXT)
            .map { it.toAiRewritePresetUi() }
    }

    private fun saveAiRewritePresets(presets: List<AiRewritePresetUi>) {
        aiPromptPresetGateway.savePresetsSync(
            presets.mapIndexed { index, preset ->
                preset.toAiPromptPreset(index)
            }
        )
    }

    private fun defaultAiRewritePresets(): List<AiRewritePresetUi> {
        return listOf(
            AiRewritePresetUi(
                id = "default_polish",
                name = context.getString(R.string.ai_rewrite_preset_polish_name),
                instruction = context.getString(R.string.ai_rewrite_preset_polish_instruction),
            ),
            AiRewritePresetUi(
                id = "default_concise",
                name = context.getString(R.string.ai_rewrite_preset_concise_name),
                instruction = context.getString(R.string.ai_rewrite_preset_concise_instruction),
            ),
            AiRewritePresetUi(
                id = "default_dialogue",
                name = context.getString(R.string.ai_rewrite_preset_dialogue_name),
                instruction = context.getString(R.string.ai_rewrite_preset_dialogue_instruction),
            ),
        )
    }

    private fun AiRewritePresetUi.toAiPromptPreset(sortNumber: Int): AiPromptPreset {
        val now = System.currentTimeMillis()
        return AiPromptPreset(
            id = id,
            taskType = AiTaskType.REWRITE_TEXT,
            name = name,
            instruction = instruction,
            builtIn = id.startsWith("default_"),
            sortNumber = sortNumber,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun AiPromptPreset.toAiRewritePresetUi(): AiRewritePresetUi {
        return AiRewritePresetUi(
            id = id,
            name = name,
            instruction = instruction,
        )
    }

    private fun buildAiRewriteInstruction(
        presetInstruction: String,
        temporaryInstruction: String,
    ): String {
        val temporary = temporaryInstruction.trim()
        if (temporary.isBlank()) return presetInstruction
        return buildString {
            append(presetInstruction)
            append("\n\nTemporary instruction for this rewrite only:\n")
            append(temporary)
        }
    }

    private suspend fun buildAiRewriteReferenceContext(
        request: PendingAiTextRewriteRequest,
    ): AiRewriteReferenceContext = withContext(IO) {
        val book = ReadBook.book
            ?.takeIf { it.bookUrl == request.bookUrl }
            ?: return@withContext AiRewriteReferenceContext()
        val terms = extractAiRewriteReferenceTerms(request.originalText)
        if (terms.isEmpty()) return@withContext AiRewriteReferenceContext()

        val chapters = host.listChapters(request.bookUrl)
            .asSequence()
            .filter { it.index != request.chapterIndex }
            .sortedWith(
                compareBy<BookChapter> { kotlin.math.abs(it.index - request.chapterIndex) }
                    .thenBy { it.index }
            )
            .take(AI_REWRITE_REFERENCE_SCAN_CHAPTERS)
            .toList()

        val excerpts = mutableListOf<String>()
        for (chapter in chapters) {
            coroutineContext.ensureActive()
            val content = BookHelp.getContent(book, chapter) ?: continue
            val term = terms.firstOrNull { term ->
                chapter.title.contains(term) || content.contains(term)
            } ?: continue
            val excerpt = extractAiRewriteReferenceExcerpt(content, term)
            if (excerpt.isBlank()) continue
            excerpts += buildString {
                append("Chapter ")
                append(chapter.index + 1)
                if (chapter.title.isNotBlank()) {
                    append(": ")
                    append(chapter.title)
                }
                append("\nKeyword: ")
                append(term)
                append("\n")
                append(excerpt)
            }
            if (excerpts.size >= AI_REWRITE_REFERENCE_MAX_EXCERPTS) break
        }

        if (excerpts.isEmpty()) {
            AiRewriteReferenceContext()
        } else {
            AiRewriteReferenceContext(
                text = excerpts.joinToString("\n\n---\n\n"),
                count = excerpts.size,
            )
        }
    }

    private fun extractAiRewriteReferenceTerms(text: String): List<String> {
        val stopWords = setOf(
            "自己", "他们", "她们", "你们", "我们", "这个", "那个", "什么", "只是", "没有",
            "不是", "已经", "知道", "起来", "一下", "心里", "眼前", "声音", "时候", "突然",
            "微微", "终于", "如果", "因为", "所以", "但是", "然后", "似乎", "仿佛", "开始",
        )
        val counts = linkedMapOf<String, Int>()
        fun addTerm(term: String) {
            val normalized = term.trim()
            if (normalized.length < 2 || normalized in stopWords) return
            counts[normalized] = (counts[normalized] ?: 0) + 1
        }

        Regex("""([\u4e00-\u9fa5]{2,4})(?:说|问|道|喊|叫|笑|答|叹|想|看|望|皱眉|点头|摇头)""")
            .findAll(text)
            .forEach { match -> addTerm(match.groupValues[1]) }
        Regex("""\b[A-Z][A-Za-z]{2,}\b""")
            .findAll(text)
            .forEach { match -> addTerm(match.value) }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(6)
    }

    private fun extractAiRewriteReferenceExcerpt(content: String, term: String): String {
        val paragraphs = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val paragraphIndex = paragraphs.indexOfFirst { it.contains(term) }
        if (paragraphIndex >= 0) {
            val start = (paragraphIndex - 1).coerceAtLeast(0)
            val end = (paragraphIndex + 2).coerceAtMost(paragraphs.size)
            return trimAiRewriteReferenceExcerpt(
                paragraphs.subList(start, end).joinToString("\n"),
                term,
            )
        }
        return trimAiRewriteReferenceExcerpt(content, term)
    }

    private fun trimAiRewriteReferenceExcerpt(text: String, term: String): String {
        if (text.length <= AI_REWRITE_REFERENCE_EXCERPT_CHARS) return text.trim()
        val index = text.indexOf(term).takeIf { it >= 0 } ?: 0
        val start = (index - AI_REWRITE_REFERENCE_EXCERPT_CHARS / 2).coerceAtLeast(0)
        val end = (start + AI_REWRITE_REFERENCE_EXCERPT_CHARS).coerceAtMost(text.length)
        return text.substring(start, end).trim()
    }

    private fun isCurrentAiTextClean(request: PendingAiTextCleanRequest): Boolean {
        val clean = _uiState.value.aiTextClean
        return host.activeSheet is ReadBookSheet.AiTextClean &&
                clean.bookUrl == request.bookUrl &&
                clean.chapterIndex == request.chapterIndex &&
                clean.originalText == request.originalText
    }

    private fun isCurrentAiTextRewrite(request: PendingAiTextRewriteRequest): Boolean {
        val rewrite = _uiState.value.aiTextRewrite
        return rewrite.bookUrl == request.bookUrl &&
                rewrite.chapterIndex == request.chapterIndex &&
                rewrite.originalText == request.originalText
    }

    private fun buildSelectionContext(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Pair<String, String> {
        if (content.isBlank()) return "" to ""
        val start = findClosestOccurrence(content, selectedText, approximatePosition)
        if (start < 0) return "" to ""
        val end = start + selectedText.length
        return content.substring((start - AI_TEXT_CONTEXT_CHARS).coerceAtLeast(0), start) to
                content.substring(end, (end + AI_TEXT_CONTEXT_CHARS).coerceAtMost(content.length))
    }

    private fun findClosestOccurrence(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Int {
        var match = content.indexOf(selectedText)
        if (match < 0) return -1
        var closest = match
        var closestDistance = kotlin.math.abs(match - approximatePosition)
        while (match >= 0) {
            val distance = kotlin.math.abs(match - approximatePosition)
            if (distance < closestDistance) {
                closest = match
                closestDistance = distance
            }
            match = content.indexOf(selectedText, match + 1)
        }
        return closest
    }

    private fun normalizeAiReplacementText(text: String): String {
        val indent = ReadBookConfig.paragraphIndent
        return text.lines()
            .joinToString("\n") { line -> line.removePrefix(indent).trim() }
            .trim()
    }

    private fun aiErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("No AI model configured", ignoreCase = true) == true ->
                context.getString(R.string.ai_model_not_configured)

            else -> error.localizedMessage ?: context.getString(R.string.error)
        }
    }
}

private const val AI_TEXT_CONTEXT_CHARS = 1000
private const val AI_REWRITE_REFERENCE_SCAN_CHAPTERS = 80
private const val AI_REWRITE_REFERENCE_MAX_EXCERPTS = 6
private const val AI_REWRITE_REFERENCE_EXCERPT_CHARS = 600

private data class PendingAiTextCleanRequest(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterPosition: Int,
    val originalText: String,
    val contextBefore: String,
    val contextAfter: String,
)

private data class PendingAiTextRewriteRequest(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterPosition: Int,
    val originalText: String,
    val sourceContentHash: String,
    val contextBefore: String,
    val contextAfter: String,
)

private data class AiRewriteReferenceContext(
    val text: String = "",
    val count: Int = 0,
)
