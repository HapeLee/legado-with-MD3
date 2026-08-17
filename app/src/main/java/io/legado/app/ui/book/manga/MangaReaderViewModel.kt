package io.legado.app.ui.book.manga

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.model.MangaFooterConfig
import io.legado.app.data.repository.manga.MangaReaderActionPaymentResult
import io.legado.app.data.repository.manga.MangaReaderActionRepository
import io.legado.app.domain.gateway.MangaReaderSessionFactory
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.manga.MangaChapterState
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.MangaSessionEvent
import io.legado.app.domain.model.manga.MangaSessionState
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.help.coil.CoverFetcher
import io.legado.app.ui.book.manga.config.MangaColorFilterConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx

class MangaReaderViewModel(
    private val mangaSettingsGateway: MangaSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
    private val actionRepository: MangaReaderActionRepository,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase,
    sessionFactory: MangaReaderSessionFactory,
) : ViewModel() {

    private val readerSession = sessionFactory.create()

    private var refreshContentJob: Job? = null
    private var latestMangaSettings = mangaSettingsGateway.currentSettings
    private val bookConfigWriteMutex = Mutex()
    private var pendingExplicitChapterIndex: Int? = null
    private var pagerScrollInProgress = false
    private var deferredReadySession: MangaSessionState? = null

    private val _uiState = MutableStateFlow(MangaReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MangaReaderEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            readerSession.events.collect { event ->
                when (event) {
                    is MangaSessionEvent.ConfirmProgress -> _uiState.update {
                        it.copy(activeDialog = MangaReaderDialog.ConfirmProgress(event.progress.toBookProgress()))
                    }
                    is MangaSessionEvent.Message -> enqueueMessage(text = event.message)
                    is MangaSessionEvent.OpenPaymentUrl -> _effects.tryEmit(
                        MangaReaderEffect.OpenPaymentUrl(
                            event.url, event.sourceOrigin, event.sourceName, event.sourceType,
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            readerSession.state.collect(::refreshContent)
        }
        viewModelScope.launch {
            mangaSettingsGateway.settings.collect { settings ->
                latestMangaSettings = settings
                readerSession.execute(MangaSessionCommand.PrefetchCountChanged(settings.chapterPrefetchCount))
                _uiState.update { state ->
                    state.copy(settings = readSettings(settings))
                }
            }
        }
        viewModelScope.launch {
            otherSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(confirmAddToShelf = settings.showAddToShelfAlert) }
            }
        }
    }

    fun onIntent(intent: MangaReaderIntent) {
        when (intent) {
            is MangaReaderIntent.Initialize -> initialize(intent)
            MangaReaderIntent.ResumeSession -> executeSession(MangaSessionCommand.Resume)
            MangaReaderIntent.PauseSession -> executeSession(MangaSessionCommand.Pause)
            MangaReaderIntent.NetworkAvailable -> executeSession(MangaSessionCommand.NetworkAvailable)
            MangaReaderIntent.RefreshBookSource -> launchAction {
                actionRepository.refreshSource(readerSession.state.value.book?.sourceOrigin)
                refreshContent()
            }
            MangaReaderIntent.ReloadContent -> executeSession(
                MangaSessionCommand.RetryChapter(readerSession.state.value.chapterIndex)
            )
            is MangaReaderIntent.ApplyReadingProgress -> {
                pendingExplicitChapterIndex = intent.progress.durChapterIndex
                executeSession(MangaSessionCommand.ApplyProgress(intent.progress.toMangaProgress()))
            }
            is MangaReaderIntent.OpenChapter -> openChapter(intent.chapterIndex, intent.pageIndex)
            is MangaReaderIntent.ChangeSourceBook -> launchAction {
                showLoading()
                val currentUrl = requireNotNull(readerSession.state.value.book?.bookUrl)
                actionRepository.changeSource(currentUrl, intent.book, intent.toc)
                readerSession.execute(MangaSessionCommand.Open(intent.book.bookUrl, true, true))
            }
            is MangaReaderIntent.AddExternalBookToShelf -> launchAction(
                successMessageRes = R.string.manga_reader_added_to_shelf,
            ) { actionRepository.addToShelf(intent.book, intent.toc) }
            MangaReaderIntent.AddCurrentBookToShelf -> launchAction {
                _uiState.update { it.copy(activeDialog = null) }
                actionRepository.addCurrentBookToShelf(
                    requireNotNull(readerSession.state.value.book?.bookUrl)
                )
                _effects.tryEmit(MangaReaderEffect.Finish(bookshelfChanged = true))
            }
            MangaReaderIntent.DiscardCurrentBookAndExit -> launchAction {
                _uiState.update { it.copy(activeDialog = null) }
                actionRepository.removeTemporaryBook(
                    requireNotNull(readerSession.state.value.book?.bookUrl)
                )
                _effects.tryEmit(MangaReaderEffect.Finish())
            }
            MangaReaderIntent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            MangaReaderIntent.DisableCurrentSource -> launchAction {
                actionRepository.disableSource(readerSession.state.value.book?.sourceOrigin)
            }
            MangaReaderIntent.RequestPayCurrentChapter -> _uiState.update {
                it.copy(
                    activeSheet = null,
                    activeDialog = MangaReaderDialog.ConfirmPay(it.chapterName),
                )
            }
            MangaReaderIntent.PayCurrentChapter -> {
                _uiState.update { it.copy(activeDialog = null) }
                payCurrentChapter()
            }
            MangaReaderIntent.OpenSourceLogin -> {
                _uiState.value.sourceUrl?.let {
                    _effects.tryEmit(MangaReaderEffect.OpenSourceLogin(it))
                }
            }
            MangaReaderIntent.OpenSourceEdit -> {
                _uiState.value.sourceUrl?.let {
                    _effects.tryEmit(MangaReaderEffect.OpenSourceEdit(it))
                }
            }
            MangaReaderIntent.BackPressed -> {
                when {
                    _uiState.value.activeDialog != null -> {
                        _uiState.update { it.copy(activeDialog = null) }
                    }
                    _uiState.value.activeSheet != null -> {
                        _uiState.update { it.copy(activeSheet = null) }
                    }
                    _uiState.value.settingsCategory != null -> closeSettings()
                    _uiState.value.menuVisible -> setMenuVisible(false)
                    readerSession.state.value.book != null &&
                            readerSession.state.value.book?.inBookshelf == false &&
                            _uiState.value.confirmAddToShelf -> {
                        _uiState.update { it.copy(activeDialog = MangaReaderDialog.AddToShelf) }
                    }
                    readerSession.state.value.book != null &&
                            readerSession.state.value.book?.inBookshelf == false -> onIntent(
                        MangaReaderIntent.DiscardCurrentBookAndExit
                    )
                    else -> _effects.tryEmit(MangaReaderEffect.Finish())
                }
            }
            MangaReaderIntent.ToggleMenu -> setMenuVisible(!_uiState.value.menuVisible)
            MangaReaderIntent.HideMenu -> setMenuVisible(false)
            MangaReaderIntent.Retry -> launchAction {
                showLoading()
                invalidateCurrentChapter()
                executeSession(MangaSessionCommand.RetryChapter(readerSession.state.value.chapterIndex))
            }
            MangaReaderIntent.PreviousChapter -> openRelativeChapter(-1)
            MangaReaderIntent.NextChapter -> openRelativeChapter(1)
            MangaReaderIntent.OpenCatalog -> showSheet(MangaReaderSheet.Catalog)
            MangaReaderIntent.OpenBookInfo -> emitAndHide(MangaReaderEffect.OpenBookInfo)
            MangaReaderIntent.OpenChapterUrl -> emitAndHide(
                MangaReaderEffect.OpenChapterUrl(readSettingsGateway.currentSettings.readUrlInBrowser)
            )
            MangaReaderIntent.ChangeSource -> {
                setMenuVisible(false)
                _uiState.update { it.copy(activeSheet = MangaReaderSheet.ChangeSource) }
                launchAction {
                    val url = requireNotNull(readerSession.state.value.book?.bookUrl)
                    val snapshot = actionRepository.getBook(url)?.let(MangaBookSnapshot::from)
                    _uiState.update { it.copy(changeSourceBook = snapshot) }
                }
            }
            MangaReaderIntent.RefreshChapter -> {
                setMenuVisible(false)
                launchAction {
                    showLoading()
                    invalidateCurrentChapter()
                    readerSession.execute(
                        MangaSessionCommand.RetryChapter(readerSession.state.value.chapterIndex)
                    )
                }
            }
            is MangaReaderIntent.OpenSettings -> openSettings(intent.category)
            MangaReaderIntent.CloseSettings -> closeSettings()
            MangaReaderIntent.OpenSourceActions -> showSheet(MangaReaderSheet.SourceActions)
            MangaReaderIntent.OpenCacheActions -> showSheet(MangaReaderSheet.CacheActions)
            is MangaReaderIntent.CacheChapters -> cacheChapters(intent.selection)
            MangaReaderIntent.ToggleAutoRead -> _uiState.update {
                it.copy(
                    autoReadEnabled = !it.autoReadEnabled,
                    // 自动阅读设置页内切换保持菜单展开，便于继续调速度/关闭；工具行切换则收起
                    menuVisible = it.settingsCategory == MangaReaderSettingsCategory.AUTO_READ,
                )
            }
            MangaReaderIntent.DismissSheet -> _uiState.update {
                it.copy(activeSheet = null, changeSourceBook = null, settingsCategory = null)
            }
            is MangaReaderIntent.UpdateSetting -> updateSetting(intent.key, intent.value)
            is MangaReaderIntent.UpdateMenuPaletteStyle -> updateMangaPreference {
                it.copy(menuPaletteStyle = intent.value)
            }
            is MangaReaderIntent.UpdateClickAction -> updateClickAction(intent.index, intent.action)
            is MangaReaderIntent.RetryChapter -> executeSession(
                MangaSessionCommand.RetryChapter(intent.chapterIndex)
            )
            is MangaReaderIntent.PageLoadStarted -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Loading,
            )

            is MangaReaderIntent.PageLoadSucceeded -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Ready,
            )

            is MangaReaderIntent.PageLoadFailed -> updatePageLoadState(
                intent.key,
                MangaPageLoadState.Failed(intent.message),
            )

            is MangaReaderIntent.RetryPage -> retryPage(intent.key)
            is MangaReaderIntent.RetryFailedPagesInChapter -> retryFailedPages(intent.chapterIndex)
            is MangaReaderIntent.PageStep -> requestPageStep(intent.direction)
            is MangaReaderIntent.SeekToPage -> seekToPage(intent.pageIndex)
            is MangaReaderIntent.VisibleItemChanged -> updateVisibleItem(
                intent.itemIndex,
                intent.firstItemIndex,
                intent.lastItemIndex,
                intent.currentChapterVisible,
                intent.navigationId,
            )
            is MangaReaderIntent.PagerScrollChanged -> {
                pagerScrollInProgress = intent.inProgress
                if (!intent.inProgress) {
                    deferredReadySession?.let(::refreshContent)
                    deferredReadySession = null
                }
            }
            is MangaReaderIntent.LongPressPage -> {
                if (_uiState.value.settings.longPressEnabled) {
                    val fallback = findSpreadCompanion(intent.pageKey)
                    showSheet(
                        MangaReaderSheet.PageActions(
                            intent.pageKey,
                            intent.companionPageKey ?: fallback?.first,
                            if (intent.companionPageKey != null) intent.companionBeforePage
                            else fallback?.second == true,
                        )
                    )
                }
            }

            is MangaReaderIntent.ExecutePageAction -> executePageAction(intent.action)
            is MangaReaderIntent.MessageShown -> _uiState.update { state ->
                state.copy(
                    pendingMessages = state.pendingMessages
                        .filterNot { it.id == intent.id }
                        .toImmutableList()
                )
            }
        }
    }

    private fun initialize(intent: MangaReaderIntent.Initialize) {
        showLoading()
        viewModelScope.launch {
            readerSession.execute(
                MangaSessionCommand.Open(
                    bookUrl = intent.bookUrl,
                    inBookshelf = intent.inBookshelf,
                    chapterChanged = intent.chapterChanged,
                )
            )
        }
    }

    private fun payCurrentChapter() {
        launchAction {
            val state = readerSession.state.value
            when (val result = actionRepository.payCurrentChapter(
                requireNotNull(state.book?.bookUrl), state.chapterIndex,
            )) {
                is MangaReaderActionPaymentResult.OpenUrl -> _effects.tryEmit(
                    MangaReaderEffect.OpenPaymentUrl(
                        result.url,
                        result.sourceOrigin,
                        result.sourceName,
                        result.sourceType,
                    )
                )
                MangaReaderActionPaymentResult.Refreshed -> executeSession(
                    MangaSessionCommand.RetryChapter(state.chapterIndex)
                )
            }
        }
    }

    private fun saveImage(url: String) {
        launchAction(
            successMessageRes = R.string.manga_reader_image_saved,
            failureMessageRes = R.string.manga_reader_save_failed,
        ) {
            val book = requireNotNull(readerSession.state.value.book)
            check(actionRepository.saveImage(url, book.bookUrl, book.sourceOrigin)) {
                ""
            }
        }
    }

    private fun launchAction(
        @androidx.annotation.StringRes successMessageRes: Int? = null,
        @androidx.annotation.StringRes failureMessageRes: Int = R.string.manga_reader_action_failed,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { successMessageRes?.let { resId ->
                    enqueueMessage(resId = resId)
                } }
                .onFailure { error ->
                    val message = error.localizedMessage?.takeIf(String::isNotBlank)
                    // 加载型 action（先 showLoading()）失败时复位加载态并展示整屏错误，
                    // 避免永久转圈；其余副作用 action 仍只弹 toast
                    if (_uiState.value.isLoading) {
                        showError(message.orEmpty(), failureMessageRes)
                    } else if (message != null) {
                        enqueueMessage(text = message)
                    } else {
                        enqueueMessage(resId = failureMessageRes)
                    }
                }
        }
    }

    fun refreshContent() = refreshContent(readerSession.state.value)

    private fun refreshContent(session: MangaSessionState) {
        val book = session.book
        if (book == null) {
            session.openError?.let {
                showError(it, fallbackRes = R.string.manga_reader_init_failed)
            }
            return
        }
        val current = session.currentChapter
        if (current !is MangaChapterState.Ready) {
            if (pendingExplicitChapterIndex == session.chapterIndex) {
                showExplicitChapterPlaceholder(
                    chapterIndex = session.chapterIndex,
                    pageIndex = session.pageIndex,
                    errorMessage = (current as? MangaChapterState.Failed)?.message,
                )
            } else if (current is MangaChapterState.Failed) showError(current.message)
            else showLoading()
            return
        }
        if (pagerScrollInProgress &&
            (_uiState.value.chapterIndex != session.chapterIndex ||
                pendingExplicitChapterIndex == session.chapterIndex)
        ) {
            deferredReadySession = session
            _uiState.update { it.copy(isChapterLoading = true, errorMessage = null) }
            return
        }
        deferredReadySession = null
        val existingPages = _uiState.value.pages
            .filterIsInstance<MangaReaderItemUi.Page>()
            .associateBy(MangaReaderItemUi.Page::key)
        fun chapterItems(chapter: io.legado.app.domain.model.manga.MangaChapterContent) =
            if (chapter.isVolume && chapter.pages.isEmpty()) {
                listOf(
                    MangaReaderItemUi.ChapterEdge(
                        key = "volume:${chapter.chapterIndex}",
                        message = chapter.chapterTitle,
                    )
                )
            } else {
                chapter.pages.map { page ->
                    val key = "page:${chapter.chapterIndex}:${page.pageIndex}:${page.imageUrl}"
                    val existing = existingPages[key]
                    MangaReaderItemUi.Page(
                        key = key,
                        imageUrl = page.imageUrl,
                        bookUrl = book.bookUrl,
                        chapterIndex = chapter.chapterIndex,
                        chapterCount = session.chapterCount,
                        pageIndex = page.pageIndex,
                        pageCount = page.pageCount,
                        chapterName = chapter.chapterTitle,
                        loadState = existing?.loadState ?: MangaPageLoadState.Queued,
                        retryRevision = existing?.retryRevision ?: 0,
                    )
                }
            }
        fun chapterTransition(
            chapter: MangaChapterState,
            edgePrefix: String,
            targetChapterIndex: Int,
            loadingMessage: String,
        ): List<MangaReaderItemUi> {
            val direction = if (edgePrefix == "prev") {
                MangaChapterTransitionDirection.PREVIOUS
            } else {
                MangaChapterTransitionDirection.NEXT
            }
            val targetExists = targetChapterIndex in 0 until session.chapterCount
            val targetName = session.book?.chapterTitles?.getOrNull(targetChapterIndex)
                ?: appCtx.getString(
                    R.string.manga_reader_transition_chapter_number,
                    targetChapterIndex + 1,
                ).takeIf { targetExists }
            return when (chapter) {
            is MangaChapterState.Ready -> listOf(
                MangaReaderItemUi.ChapterTransition(
                    key = "transition:$edgePrefix:${session.chapterIndex}:ready",
                    direction = direction,
                    targetChapterIndex = targetChapterIndex,
                    currentChapterName = current.chapter.chapterTitle,
                    targetChapterName = chapter.chapter.chapterTitle,
                    targetStatus = MangaChapterTransitionStatus.READY,
                )
            )
            is MangaChapterState.Failed -> listOf(
                MangaReaderItemUi.ChapterTransition(
                    key = "edge:$edgePrefix:${session.chapterIndex}:failed",
                    direction = direction,
                    targetChapterIndex = targetChapterIndex,
                    currentChapterName = current.chapter.chapterTitle,
                    targetChapterName = targetName,
                    targetStatus = MangaChapterTransitionStatus.FAILED,
                    statusMessage = appCtx.getString(
                        R.string.manga_reader_adjacent_failed,
                        chapter.message.ifBlank {
                            appCtx.getString(R.string.manga_reader_unknown_error)
                        },
                    ),
                    retryChapterIndex = targetChapterIndex,
                )
            )
            MangaChapterState.Empty -> listOf(
                MangaReaderItemUi.ChapterTransition(
                    key = "edge:$edgePrefix:${session.chapterIndex}:empty",
                    direction = direction,
                    targetChapterIndex = targetChapterIndex.takeIf { targetExists },
                    currentChapterName = current.chapter.chapterTitle,
                    targetChapterName = targetName,
                    targetStatus = if (targetExists) {
                        MangaChapterTransitionStatus.WAITING
                    } else {
                        MangaChapterTransitionStatus.UNAVAILABLE
                    },
                )
            )
            is MangaChapterState.Loading -> listOf(
                MangaReaderItemUi.ChapterTransition(
                    key = "edge:$edgePrefix:${session.chapterIndex}:loading",
                    direction = direction,
                    targetChapterIndex = targetChapterIndex,
                    currentChapterName = current.chapter.chapterTitle,
                    targetChapterName = targetName,
                    targetStatus = MangaChapterTransitionStatus.LOADING,
                    statusMessage = loadingMessage,
                )
            )
            }
        }
        fun makePreviousItems(chapter: MangaChapterState): List<MangaReaderItemUi> = when (chapter) {
            is MangaChapterState.Ready -> chapterItems(chapter.chapter) + chapterTransition(
                chapter, "prev", session.chapterIndex - 1,
                appCtx.getString(R.string.manga_reader_loading_prev),
            )
            else -> chapterTransition(
                chapter, "prev", session.chapterIndex - 1,
                appCtx.getString(R.string.manga_reader_loading_prev),
            )
        }
        fun makeNextItems(chapter: MangaChapterState): List<MangaReaderItemUi> =
            chapterTransition(
                chapter, "next", session.chapterIndex + 1,
                appCtx.getString(R.string.manga_reader_loading_next),
            ) + if (chapter is MangaChapterState.Ready) chapterItems(chapter.chapter) else emptyList()

        refreshContentJob?.cancel()
        refreshContentJob = viewModelScope.launch {
            val previousItems = makePreviousItems(session.previousChapter)
            val nextItems = makeNextItems(session.nextChapter)
            val items = (previousItems + chapterItems(current.chapter) + nextItems).toImmutableList()
            val safePosition = (previousItems.size + session.pageIndex)
                .coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val oldState = _uiState.value
            val shouldPosition = shouldForceMangaChapterPosition(
                hasPages = oldState.pages.isNotEmpty(),
                isLoading = oldState.isLoading,
                currentBookUrl = oldState.bookUrl,
                targetBookUrl = book.bookUrl,
                pendingExplicitChapterIndex = pendingExplicitChapterIndex,
                targetChapterIndex = session.chapterIndex,
            )
            _uiState.update { old ->
                old.copy(
                    bookName = book.name,
                    bookAuthor = book.author,
                    bookUrl = book.bookUrl,
                    coverUrl = book.coverUrl,
                    customCoverUrl = book.customCoverUrl,
                    chapterName = current.chapter.chapterTitle,
                    chapterUrl = current.chapter.chapterUrl,
                    sourceName = book.sourceName,
                    sourceUrl = book.sourceOrigin,
                    sourceType = book.sourceType,
                    inBookshelf = book.inBookshelf,
                    pages = items,
                    currentItemIndex = if (shouldPosition) safePosition else {
                        val anchorKey = old.pages.getOrNull(old.currentItemIndex)?.key
                        anchorKey?.let { key ->
                            items.indexOfFirst { it.key == key }.takeIf { it >= 0 }
                        } ?: old.currentItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    },
                    currentPage = if (shouldPosition) session.pageIndex else old.currentPage,
                    pageCount = if (shouldPosition) current.chapter.pages.size else old.pageCount,
                    chapterIndex = session.chapterIndex,
                    chapterCount = session.chapterCount,
                    cacheAvailable = !book.isLocal,
                    isLoading = false,
                    isChapterLoading = false,
                    pendingChapterIndex = null,
                    errorMessage = null,
                    settings = readSettings(latestMangaSettings),
                    scrollRequest = if (shouldPosition) {
                        MangaScrollRequest(
                            id = System.nanoTime(),
                            itemIndex = safePosition,
                            animated = false,
                        )
                    } else old.scrollRequest,
                )
            }
            if (shouldPosition && pendingExplicitChapterIndex != session.chapterIndex) updateVisibleItem(
                itemIndex = safePosition,
                firstItemIndex = safePosition,
                lastItemIndex = safePosition,
                currentChapterVisible = true,
                navigationId = _uiState.value.navigationId,
            )
        }
    }

    fun showLoading() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                pages = persistentListOf(),
                currentItemIndex = 0,
                scrollRequest = null,
            )
        }
    }

    fun showError(message: String) {
        _uiState.update {
            it.copy(isLoading = false, errorMessage = MangaReaderText.Dynamic(message))
        }
    }

    private fun showError(message: String, @androidx.annotation.StringRes fallbackRes: Int) {
        if (message.isNotBlank()) showError(message)
        else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = MangaReaderText.Resource(
                        fallbackRes,
                        persistentListOf(""),
                    ),
                )
            }
        }
    }

    private fun enqueueMessage(
        @androidx.annotation.StringRes resId: Int? = null,
        text: String? = null,
        args: kotlinx.collections.immutable.ImmutableList<String> = persistentListOf(),
    ) {
        _uiState.update { state ->
            state.copy(
                pendingMessages = (state.pendingMessages +
                    MangaReaderMessage(
                        id = System.nanoTime(),
                        content = if (resId != null) {
                            MangaReaderText.Resource(resId, args)
                        } else {
                            MangaReaderText.Dynamic(requireNotNull(text))
                        },
                    )
                ).toImmutableList()
            )
        }
    }

    override fun onCleared() {
        readerSession.close()
        super.onCleared()
    }

    private fun executeSession(command: MangaSessionCommand) {
        // The session uses an ordered command channel. Enqueue immediately so a visible-page
        // promotion is always processed before the progress update that follows it.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { readerSession.execute(command) }
    }

    private suspend fun invalidateCurrentChapter() {
        val state = readerSession.state.value
        actionRepository.invalidateChapter(
            requireNotNull(state.book?.bookUrl),
            state.chapterIndex,
        )
    }

    private fun setMenuVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                menuVisible = visible,
                settingsCategory = if (visible) it.settingsCategory else null,
            )
        }
        _effects.tryEmit(MangaReaderEffect.SetSystemBarsVisible(visible))
    }

    private fun openSettings(category: MangaReaderSettingsCategory) {
        setMenuVisible(true)
        _uiState.update { it.copy(settingsCategory = category, activeSheet = null) }
    }

    private fun closeSettings() {
        _uiState.update { it.copy(settingsCategory = null) }
    }

    private fun emitAndHide(effect: MangaReaderEffect) {
        setMenuVisible(false)
        _effects.tryEmit(effect)
    }

    private fun showSheet(sheet: MangaReaderSheet) {
        setMenuVisible(false)
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    private fun updateClickAction(index: Int, action: Int) {
        if (index !in 0..8) return
        updateMangaPreference { settings ->
            when (index) {
                0 -> settings.copy(clickActionTL = action)
                1 -> settings.copy(clickActionTC = action)
                2 -> settings.copy(clickActionTR = action)
                3 -> settings.copy(clickActionML = action)
                4 -> settings.copy(clickActionMC = action)
                5 -> settings.copy(clickActionMR = action)
                6 -> settings.copy(clickActionBL = action)
                7 -> settings.copy(clickActionBC = action)
                8 -> settings.copy(clickActionBR = action)
                else -> settings
            }
        }
    }

    private fun updateSetting(key: MangaReaderSettingKey, value: Int) {
        val enabled = value != 0
        when (key) {
            MangaReaderSettingKey.SCROLL_MODE -> {
                persistBookConfig {
                    readerSession.state.value.book?.bookUrl?.let { bookUrl ->
                        actionRepository.updateReadConfig(bookUrl) { mangaScrollMode = value }
                    }
                }
            }
            MangaReaderSettingKey.SIDE_PADDING -> {
                persistBookConfig {
                    readerSession.state.value.book?.bookUrl?.let { bookUrl ->
                        actionRepository.updateReadConfig(bookUrl) { webtoonSidePaddingDp = value }
                    }
                }
            }
            MangaReaderSettingKey.BACKGROUND_RED,
            MangaReaderSettingKey.BACKGROUND_GREEN,
            MangaReaderSettingKey.BACKGROUND_BLUE -> {
                updateMangaPreference { settings ->
                    val old = Color(settings.background)
                    val red = if (key == MangaReaderSettingKey.BACKGROUND_RED) value else (old.red * 255).toInt()
                    val green = if (key == MangaReaderSettingKey.BACKGROUND_GREEN) value else (old.green * 255).toInt()
                    val blue = if (key == MangaReaderSettingKey.BACKGROUND_BLUE) value else (old.blue * 255).toInt()
                    settings.copy(background = Color(red, green, blue).toArgb())
                }
            }
            MangaReaderSettingKey.AUTO_BACKGROUND -> updateMangaPreference { it.copy(autoBackground = enabled) }
            MangaReaderSettingKey.PAGE_SCALE_TYPE -> updateMangaPreference { it.copy(pageScaleType = value) }
            MangaReaderSettingKey.ZOOM_START_POSITION -> updateMangaPreference { it.copy(zoomStartPosition = value) }
            MangaReaderSettingKey.WIDE_PAGE_MODE -> updateMangaPreference { it.copy(widePageMode = value) }
            MangaReaderSettingKey.DOUBLE_PAGE_MODE -> updateMangaPreference { it.copy(doublePageMode = value) }
            MangaReaderSettingKey.DOUBLE_PAGE_COVER_SINGLE -> updateMangaPreference {
                it.copy(doublePageCoverSingle = value != 0)
            }

            MangaReaderSettingKey.DOUBLE_PAGE_INVERT -> updateMangaPreference {
                it.copy(doublePageInvert = value != 0)
            }

            MangaReaderSettingKey.DOUBLE_PAGE_SHIFT -> updateMangaPreference {
                it.copy(doublePageShift = value != 0)
            }
            MangaReaderSettingKey.DISABLE_SCALE -> updateMangaPreference { it.copy(disableMangaScale = enabled) }
            MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION -> updateMangaPreference {
                it.copy(disableMangaScrollAnimation = enabled)
            }
            MangaReaderSettingKey.DISABLE_CROSS_FADE -> updateMangaPreference {
                it.copy(disableMangaCrossFade = enabled)
            }
            MangaReaderSettingKey.DISABLE_CLICK_SCROLL -> updateMangaPreference { it.copy(disableClickScroll = enabled) }
            MangaReaderSettingKey.LONG_PRESS -> updateMangaPreference { it.copy(longClick = enabled) }
            MangaReaderSettingKey.PRE_DOWNLOAD -> updateMangaPreference { it.copy(preDownloadNum = value) }
            MangaReaderSettingKey.CHAPTER_PREFETCH -> updateMangaPreference {
                it.copy(chapterPrefetchCount = value)
            }
            MangaReaderSettingKey.AUTO_READ_SPEED -> updateMangaPreference { it.copy(autoPageSpeed = value) }
            MangaReaderSettingKey.VOLUME_KEY_PAGE -> updateMangaPreference { it.copy(volumeKeyPage = enabled) }
            MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE -> updateMangaPreference {
                it.copy(reverseVolumeKeyPage = enabled)
            }
            MangaReaderSettingKey.HIDE_MANGA_TITLE -> {
                updateMangaPreference { it.copy(hideTitle = enabled) }
                executeSession(
                    MangaSessionCommand.RetryChapter(readerSession.state.value.chapterIndex)
                )
            }
            MangaReaderSettingKey.ENABLE_GRAY -> {
                updateMangaPreference {
                    it.copy(enableGray = enabled, enableEInk = if (enabled) false else it.enableEInk)
                }
            }
            MangaReaderSettingKey.ENABLE_EINK -> {
                updateMangaPreference {
                    it.copy(enableEInk = enabled, enableGray = if (enabled) false else it.enableGray)
                }
            }
            MangaReaderSettingKey.EINK_THRESHOLD -> updateMangaPreference { it.copy(eInkThreshold = value) }
            MangaReaderSettingKey.FILTER_RED,
            MangaReaderSettingKey.FILTER_GREEN,
            MangaReaderSettingKey.FILTER_BLUE,
            MangaReaderSettingKey.FILTER_ALPHA,
            MangaReaderSettingKey.AUTO_BRIGHTNESS,
            MangaReaderSettingKey.BRIGHTNESS -> updateColorSetting(key, value)
            MangaReaderSettingKey.HIDE_FOOTER,
            MangaReaderSettingKey.HIDE_CHAPTER_NAME,
            MangaReaderSettingKey.HIDE_PAGE_NUMBER,
            MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL,
            MangaReaderSettingKey.HIDE_CHAPTER,
            MangaReaderSettingKey.HIDE_CHAPTER_LABEL,
            MangaReaderSettingKey.HIDE_PROGRESS,
            MangaReaderSettingKey.HIDE_PROGRESS_LABEL,
            MangaReaderSettingKey.FOOTER_ALIGNMENT -> updateFooterSetting(key, value)
            MangaReaderSettingKey.MENU_TOP_BAR_LIQUID_GLASS -> updateMangaPreference {
                it.copy(menuTopBarLiquidGlass = enabled)
            }
            MangaReaderSettingKey.MENU_BOTTOM_BAR_LIQUID_GLASS -> updateMangaPreference {
                it.copy(menuBottomBarLiquidGlass = enabled)
            }
            MangaReaderSettingKey.MENU_BOTTOM_BAR_FLOATING -> updateMangaPreference {
                it.copy(menuBottomBarFloating = enabled)
            }
            MangaReaderSettingKey.MENU_BOTTOM_BAR_BLUR -> updateMangaPreference {
                it.copy(menuBottomBarBlur = enabled)
            }
            MangaReaderSettingKey.MENU_TOP_BAR_COMPACT -> updateMangaPreference {
                it.copy(menuTopBarCompact = enabled)
            }
            MangaReaderSettingKey.MENU_COLOR_SOURCE -> updateMangaPreference {
                it.copy(menuColorSource = value)
            }
            MangaReaderSettingKey.MENU_SEED_COLOR -> updateMangaPreference {
                it.copy(menuSeedColor = value)
            }
        }
    }

    private fun updateMangaPreference(transform: (MangaSettings) -> MangaSettings) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mangaSettingsGateway.update(transform)
        }
    }

    private fun persistBookConfig(block: suspend () -> Unit) {
        viewModelScope.launch {
            bookConfigWriteMutex.withLock { block() }
        }
    }

    private fun updateColorSetting(key: MangaReaderSettingKey, value: Int) {
        val current = _uiState.value.settings
        updateMangaPreference { settings ->
            val config = GSON.fromJsonObject<MangaColorFilterConfig>(settings.colorFilter)
                .getOrNull() ?: MangaColorFilterConfig()
            when (key) {
                MangaReaderSettingKey.FILTER_RED -> config.r = value
                MangaReaderSettingKey.FILTER_GREEN -> config.g = value
                MangaReaderSettingKey.FILTER_BLUE -> config.b = value
                MangaReaderSettingKey.FILTER_ALPHA -> config.a = value
                MangaReaderSettingKey.AUTO_BRIGHTNESS -> config.autoBrightness = value != 0
                MangaReaderSettingKey.BRIGHTNESS -> config.l = value
                else -> Unit
            }
            settings.copy(colorFilter = config.toJson())
        }
        _effects.tryEmit(
            MangaReaderEffect.SetWindowBrightness(
                auto = if (key == MangaReaderSettingKey.AUTO_BRIGHTNESS) value != 0 else current.autoBrightness,
                brightness = if (key == MangaReaderSettingKey.BRIGHTNESS) value else current.brightness,
            )
        )
    }

    private fun updateFooterSetting(key: MangaReaderSettingKey, value: Int) {
        val enabled = value != 0
        updateMangaPreference { settings ->
            val config = GSON.fromJsonObject<MangaFooterConfig>(settings.footerConfig)
                .getOrNull() ?: MangaFooterConfig()
            when (key) {
                MangaReaderSettingKey.HIDE_FOOTER -> config.hideFooter = enabled
                MangaReaderSettingKey.HIDE_CHAPTER_NAME -> config.hideChapterName = enabled
                MangaReaderSettingKey.HIDE_PAGE_NUMBER -> config.hidePageNumber = enabled
                MangaReaderSettingKey.HIDE_PAGE_NUMBER_LABEL -> config.hidePageNumberLabel = enabled
                MangaReaderSettingKey.HIDE_CHAPTER -> config.hideChapter = enabled
                MangaReaderSettingKey.HIDE_CHAPTER_LABEL -> config.hideChapterLabel = enabled
                MangaReaderSettingKey.HIDE_PROGRESS -> config.hideProgressRatio = enabled
                MangaReaderSettingKey.HIDE_PROGRESS_LABEL -> config.hideProgressRatioLabel = enabled
                MangaReaderSettingKey.FOOTER_ALIGNMENT -> config.footerOrientation = value
                else -> Unit
            }
            settings.copy(footerConfig = GSON.toJson(config))
        }
    }

    private var visibleItemRange: IntRange? = null

    private fun openChapter(chapterIndex: Int, pageIndex: Int) {
        if (chapterIndex !in 0 until readerSession.state.value.chapterCount ||
            pendingExplicitChapterIndex == chapterIndex
        ) return
        pendingExplicitChapterIndex = chapterIndex
        showExplicitChapterPlaceholder(chapterIndex, pageIndex, errorMessage = null)
        executeSession(MangaSessionCommand.OpenChapter(chapterIndex, pageIndex))
    }

    private fun cacheChapters(selection: MangaCacheSelection) {
        val state = _uiState.value
        if (!state.cacheAvailable || state.chapterCount <= 0) return
        if (selection == MangaCacheSelection.FOLLOWING && state.chapterIndex >= state.chapterCount - 1) {
            enqueueMessage(resId = R.string.manga_reader_no_next_chapter)
            _uiState.update { it.copy(activeSheet = null) }
            return
        }
        _uiState.update { it.copy(activeSheet = null) }
        launchAction(successMessageRes = R.string.manga_reader_cache_enqueued) {
            when (selection) {
                MangaCacheSelection.CURRENT -> cacheBookChaptersUseCase.execute(
                    state.bookUrl,
                    listOf(state.chapterIndex),
                )

                MangaCacheSelection.FOLLOWING -> cacheBookChaptersUseCase.executeRange(
                    state.bookUrl,
                    state.chapterIndex + 1,
                    state.chapterCount - 1,
                )

                MangaCacheSelection.ALL -> cacheBookChaptersUseCase.executeRange(
                    state.bookUrl,
                    0,
                    state.chapterCount - 1,
                )
            }
        }
    }

    private fun executePageAction(action: MangaPageAction) {
        val sheet = _uiState.value.activeSheet as? MangaReaderSheet.PageActions ?: return
        val selected = pageForKey(sheet.pageKey) ?: return
        val companion = sheet.companionPageKey?.let(::pageForKey)
        val pages = if (sheet.companionBeforePage) listOfNotNull(companion, selected)
        else listOfNotNull(selected, companion)
        val urls = when (action) {
            MangaPageAction.SAVE_SPREAD, MangaPageAction.SHARE_SPREAD, MangaPageAction.COPY_SPREAD -> pages.map { it.imageUrl }
            else -> listOf(selected.imageUrl)
        }
        _uiState.update { it.copy(activeSheet = null) }
        launchAction(
            successMessageRes = when (action) {
                MangaPageAction.SAVE, MangaPageAction.SAVE_SPREAD -> R.string.manga_reader_image_saved
                MangaPageAction.SET_COVER -> R.string.manga_reader_cover_set
                else -> null
            },
            failureMessageRes = R.string.manga_reader_action_failed,
        ) {
            val book = requireNotNull(readerSession.state.value.book)
            when (action) {
                MangaPageAction.SAVE, MangaPageAction.SAVE_SPREAD -> check(
                    actionRepository.saveImages(urls, book.bookUrl, book.sourceOrigin)
                )

                MangaPageAction.SHARE, MangaPageAction.SHARE_SPREAD -> _effects.tryEmit(
                    MangaReaderEffect.ShareImage(
                        actionRepository.prepareImageFile(
                            urls,
                            book.bookUrl,
                            book.sourceOrigin
                        ).path
                    )
                )

                MangaPageAction.COPY, MangaPageAction.COPY_SPREAD -> _effects.tryEmit(
                    MangaReaderEffect.CopyImage(
                        actionRepository.prepareImageFile(
                            urls,
                            book.bookUrl,
                            book.sourceOrigin
                        ).path
                    )
                )

                MangaPageAction.SET_COVER -> actionRepository.setBookCover(
                    book.bookUrl,
                    selected.imageUrl
                )
            }
        }
    }

    private fun pageForKey(key: String) =
        _uiState.value.pages.firstOrNull { it.key == key } as? MangaReaderItemUi.Page

    private fun findSpreadCompanion(key: String): Pair<String, Boolean>? {
        val state = _uiState.value
        if (state.settings.doublePageMode == 0) return null
        val pages = state.pages.filterIsInstance<MangaReaderItemUi.Page>()
        val index = pages.indexOfFirst { it.key == key }
        val selected = pages.getOrNull(index) ?: return null
        pages.getOrNull(index + 1)?.takeIf { it.chapterIndex == selected.chapterIndex }
            ?.let { return it.key to false }
        return pages.getOrNull(index - 1)?.takeIf { it.chapterIndex == selected.chapterIndex }
            ?.let { it.key to true }
    }

    private fun showExplicitChapterPlaceholder(
        chapterIndex: Int,
        pageIndex: Int,
        errorMessage: String?,
    ) {
        val title = readerSession.state.value.book?.chapterTitles?.getOrNull(chapterIndex)
            ?: appCtx.getString(R.string.manga_reader_transition_chapter_number, chapterIndex + 1)
        val failed = errorMessage != null
        val message = if (failed) {
            errorMessage.takeUnless(String::isBlank)
                ?: appCtx.getString(R.string.manga_reader_unknown_error)
        } else {
            appCtx.getString(R.string.manga_reader_loading_selected_chapter, title)
        }
        _uiState.update { state ->
            state.copy(
                chapterName = title,
                chapterIndex = chapterIndex,
                currentPage = pageIndex.coerceAtLeast(0),
                pageCount = 0,
                pages = persistentListOf(
                    mangaChapterLoadingItem(
                        chapterIndex = chapterIndex,
                        message = message,
                        failed = failed,
                    )
                ),
                currentItemIndex = 0,
                isLoading = false,
                isChapterLoading = !failed,
                pendingChapterIndex = chapterIndex,
                errorMessage = null,
                scrollRequest = null,
                navigationId = System.nanoTime(),
            )
        }
    }

    private fun openRelativeChapter(direction: Int) {
        openChapter(readerSession.state.value.chapterIndex + direction, pageIndex = 0)
    }

    private fun requestPageStep(direction: Int) {
        val state = _uiState.value
        val range = visibleItemRange?.takeIf { state.currentItemIndex in it }
            ?: (state.currentItemIndex..state.currentItemIndex)
        val target = mangaPageStepTarget(
            currentIndex = if (direction > 0) range.last else range.first,
            itemCount = state.pages.size,
            direction = direction,
        )
        if (target == null) {
            openRelativeChapter(direction)
            return
        }
        _uiState.update {
            it.copy(scrollRequest = MangaScrollRequest(System.nanoTime(), target, !it.settings.disableScrollAnimation))
        }
    }

    private fun seekToPage(pageIndex: Int) {
        val state = _uiState.value
        val target = state.pages.indexOfFirst {
            it is MangaReaderItemUi.Page &&
                    it.chapterIndex == readerSession.state.value.chapterIndex && it.pageIndex == pageIndex
        }
        if (target >= 0) {
            // A slider emits a continuous stream of targets. Animating every target cancels the
            // prior pager animation mid-frame, which can leave a horizontal spread between pages.
            _uiState.update { it.copy(scrollRequest = MangaScrollRequest(System.nanoTime(), target, false)) }
        }
    }

    private fun updateVisibleItem(
        itemIndex: Int,
        firstItemIndex: Int,
        lastItemIndex: Int,
        currentChapterVisible: Boolean,
        navigationId: Long,
    ) {
        val state = _uiState.value
        if (navigationId != state.navigationId) return
        if (state.isLoading) return
        val item = state.pages.getOrNull(itemIndex) as? MangaReaderItemUi.Page ?: return
        val requestedItem = state.scrollRequest?.itemIndex
            ?.let(state.pages::getOrNull) as? MangaReaderItemUi.Page
        if (requestedItem != null && item.chapterIndex != requestedItem.chapterIndex) return
        if (pendingExplicitChapterIndex != null &&
            item.chapterIndex != readerSession.state.value.chapterIndex
        ) return
        visibleItemRange = firstItemIndex.coerceAtMost(lastItemIndex)..
            lastItemIndex.coerceAtLeast(firstItemIndex)
        when (mangaChapterSwitchDecision(
            currentChapterIndex = readerSession.state.value.chapterIndex,
            visibleChapterIndex = item.chapterIndex,
            currentChapterVisible = currentChapterVisible,
        )) {
            MangaChapterSwitch.NEXT,
            MangaChapterSwitch.PREVIOUS -> executeSession(
                MangaSessionCommand.PromoteVisibleChapter(item.chapterIndex, item.pageIndex)
            )
            MangaChapterSwitch.NONE -> Unit
        }
        executeSession(MangaSessionCommand.VisiblePageChanged(item.chapterIndex, item.pageIndex))
        val completedExplicitNavigation = pendingExplicitChapterIndex == item.chapterIndex
        _uiState.update {
            it.copy(
                currentItemIndex = itemIndex,
                currentPage = item.pageIndex,
                pageCount = item.pageCount,
                scrollRequest = it.scrollRequest?.takeUnless { request ->
                    request.itemIndex == itemIndex
                },
                navigationId = if (completedExplicitNavigation) System.nanoTime() else it.navigationId,
            )
        }
        if (completedExplicitNavigation) {
            pendingExplicitChapterIndex = null
        }
    }

    private fun updatePageLoadState(key: String, loadState: MangaPageLoadState) {
        _uiState.update { state ->
            val index = state.pages.indexOfFirst { it.key == key }
            val page =
                state.pages.getOrNull(index) as? MangaReaderItemUi.Page ?: return@update state
            if (page.loadState == loadState) return@update state
            state.copy(
                pages = state.pages.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) page.copy(loadState = loadState) else item
                }.toImmutableList(),
            )
        }
    }

    private fun retryPage(key: String) {
        CoverFetcher.clearFailure(
            (_uiState.value.pages.firstOrNull { it.key == key } as? MangaReaderItemUi.Page)
                ?.imageUrl ?: return
        )
        _uiState.update { state ->
            val index = state.pages.indexOfFirst { it.key == key }
            val page =
                state.pages.getOrNull(index) as? MangaReaderItemUi.Page ?: return@update state
            state.copy(
                pages = state.pages.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) page.copy(
                        loadState = MangaPageLoadState.Queued,
                        retryRevision = page.retryRevision + 1,
                    ) else item
                }.toImmutableList(),
            )
        }
    }

    private fun retryFailedPages(chapterIndex: Int) {
        _uiState.value.pages.filterIsInstance<MangaReaderItemUi.Page>()
            .filter { it.chapterIndex == chapterIndex && it.loadState is MangaPageLoadState.Failed }
            .forEach { CoverFetcher.clearFailure(it.imageUrl) }
        _uiState.update { state ->
            state.copy(pages = state.pages.map { item ->
                if (item is MangaReaderItemUi.Page && item.chapterIndex == chapterIndex &&
                    item.loadState is MangaPageLoadState.Failed
                ) item.copy(
                    loadState = MangaPageLoadState.Queued,
                    retryRevision = item.retryRevision + 1
                )
                else item
            }.toImmutableList())
        }
    }

    private fun readSettings(settings: MangaSettings): MangaReaderSettings {
        val colorFilter = GSON.fromJsonObject<MangaColorFilterConfig>(settings.colorFilter)
            .getOrNull() ?: MangaColorFilterConfig()
        val footer = GSON.fromJsonObject<MangaFooterConfig>(settings.footerConfig)
            .getOrNull() ?: MangaFooterConfig()
        return MangaReaderSettings(
        scrollMode = readerSession.state.value.book?.scrollMode ?: settings.scrollMode,
        sidePaddingPercent = readerSession.state.value.book?.sidePaddingDp
            ?: settings.webtoonSidePaddingDp,
        backgroundColor = Color(settings.background),
        autoBackground = settings.autoBackground,
        pageScaleType = settings.pageScaleType,
        zoomStartPosition = settings.zoomStartPosition,
        widePageMode = settings.widePageMode,
        doublePageMode = settings.doublePageMode,
            doublePageCoverSingle = settings.doublePageCoverSingle,
            doublePageInvert = settings.doublePageInvert,
            doublePageShift = settings.doublePageShift,
        disableScale = settings.disableMangaScale,
        disableScrollAnimation = settings.disableMangaScrollAnimation,
        disableCrossFade = settings.disableMangaCrossFade,
        disableClickScroll = settings.disableClickScroll,
        longPressEnabled = settings.longClick,
        preDownloadCount = settings.preDownloadNum,
            chapterPrefetchCount = settings.chapterPrefetchCount,
        autoReadSpeed = settings.autoPageSpeed,
        volumeKeyPage = settings.volumeKeyPage,
        reverseVolumeKeyPage = settings.reverseVolumeKeyPage,
        hideMangaTitle = settings.hideTitle,
        autoBrightness = colorFilter.autoBrightness,
        brightness = colorFilter.l,
        enableGray = settings.enableGray,
        enableEInk = settings.enableEInk,
        eInkThreshold = settings.eInkThreshold,
        filterRed = colorFilter.r,
        filterGreen = colorFilter.g,
        filterBlue = colorFilter.b,
        filterAlpha = colorFilter.a,
        hideFooter = footer.hideFooter,
        hideChapterName = footer.hideChapterName,
        hidePageNumber = footer.hidePageNumber,
        hidePageNumberLabel = footer.hidePageNumberLabel,
        hideChapter = footer.hideChapter,
        hideChapterLabel = footer.hideChapterLabel,
        hideProgress = footer.hideProgressRatio,
        hideProgressLabel = footer.hideProgressRatioLabel,
        footerAlignment = footer.footerOrientation,
        menuTopBarLiquidGlass = settings.menuTopBarLiquidGlass,
        menuBottomBarLiquidGlass = settings.menuBottomBarLiquidGlass,
        menuBottomBarFloating = settings.menuBottomBarFloating,
        menuBottomBarBlur = settings.menuBottomBarBlur,
        menuTopBarCompact = settings.menuTopBarCompact,
        menuColorSource = settings.menuColorSource,
        menuSeedColor = Color(settings.menuSeedColor),
        menuPaletteStyle = settings.menuPaletteStyle,
        sourceOrigin = readerSession.state.value.book?.sourceOrigin,
        clickActions = listOf(
            settings.clickActionTL,
            settings.clickActionTC,
            settings.clickActionTR,
            settings.clickActionML,
            settings.clickActionMC,
            settings.clickActionMR,
            settings.clickActionBL,
            settings.clickActionBC,
            settings.clickActionBR,
        ).toImmutableList(),
    )
    }

    private fun io.legado.app.data.entities.BookProgress.toMangaProgress() = MangaProgressState(
        bookName = name,
        bookAuthor = author,
        chapterIndex = durChapterIndex,
        pageIndex = durChapterPos,
        chapterTitle = durChapterTitle,
        updatedAt = durChapterTime,
    )

    private fun MangaProgressState.toBookProgress() = io.legado.app.data.entities.BookProgress(
        name = bookName,
        author = bookAuthor,
        durChapterIndex = chapterIndex,
        durChapterPos = pageIndex,
        durChapterTime = updatedAt,
        durChapterTitle = chapterTitle,
    )
}
