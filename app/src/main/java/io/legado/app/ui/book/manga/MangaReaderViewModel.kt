package io.legado.app.ui.book.manga

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.model.MangaFooterConfig
import io.legado.app.data.repository.MangaPaymentResult
import io.legado.app.data.repository.MangaReaderLaunchRequest
import io.legado.app.data.repository.MangaReaderSessionEvent
import io.legado.app.data.repository.MangaReaderSessionRepository
import io.legado.app.data.repository.MangaReaderSessionState
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.model.manga.MangaPage
import io.legado.app.model.manga.ReaderLoading
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

class MangaReaderViewModel(
    private val mangaSettingsGateway: MangaSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
    private val sessionRepository: MangaReaderSessionRepository,
) : ViewModel() {

    private var refreshContentJob: Job? = null
    private var latestMangaSettings = mangaSettingsGateway.currentSettings
    private val bookConfigWriteMutex = Mutex()

    private val _uiState = MutableStateFlow(MangaReaderUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MangaReaderEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        sessionRepository.openSession()
        viewModelScope.launch {
            sessionRepository.events.collect { event ->
                when (event) {
                    is MangaReaderSessionEvent.LoadFailed -> showError(event.message)
                    is MangaReaderSessionEvent.ConfirmProgress -> _uiState.update {
                        it.copy(activeDialog = MangaReaderDialog.ConfirmProgress(event.progress))
                    }
                    MangaReaderSessionEvent.Loading,
                    MangaReaderSessionEvent.LoadStarted -> showLoading()
                    is MangaReaderSessionEvent.Message -> {
                        enqueueMessage(text = event.message)
                    }
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.sessionState.collect(::refreshContent)
        }
        viewModelScope.launch {
            mangaSettingsGateway.settings.collect { settings ->
                latestMangaSettings = settings
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
            MangaReaderIntent.ResumeSession -> sessionRepository.resumeSession()
            MangaReaderIntent.PauseSession -> sessionRepository.pauseSession()
            MangaReaderIntent.NetworkAvailable -> sessionRepository.syncProgressOnNetworkAvailable()
            MangaReaderIntent.RefreshBookSource -> launchAction {
                sessionRepository.refreshBookSource()
                refreshContent()
            }
            MangaReaderIntent.ReloadContent -> sessionRepository.reloadOrUpdateContent()
            is MangaReaderIntent.ApplyReadingProgress -> sessionRepository.applyProgress(intent.progress)
            is MangaReaderIntent.OpenChapter -> launchAction {
                sessionRepository.openChapter(intent.chapterIndex, intent.pageIndex)
            }
            is MangaReaderIntent.ChangeSourceBook -> launchAction {
                showLoading()
                sessionRepository.changeSource(intent.book, intent.toc)
            }
            is MangaReaderIntent.AddExternalBookToShelf -> launchAction(
                successMessageRes = R.string.manga_reader_added_to_shelf,
            ) { sessionRepository.addToShelf(intent.book, intent.toc) }
            MangaReaderIntent.AddCurrentBookToShelf -> launchAction {
                _uiState.update { it.copy(activeDialog = null) }
                sessionRepository.addCurrentBookToShelf()
                _effects.tryEmit(MangaReaderEffect.Finish(bookshelfChanged = true))
            }
            MangaReaderIntent.DiscardCurrentBookAndExit -> launchAction {
                _uiState.update { it.copy(activeDialog = null) }
                sessionRepository.removeCurrentTemporaryBook()
                _effects.tryEmit(MangaReaderEffect.Finish())
            }
            MangaReaderIntent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            MangaReaderIntent.DisableCurrentSource -> launchAction {
                sessionRepository.disableSource()
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
                    sessionRepository.hasBook() && !sessionRepository.inBookshelf() &&
                            _uiState.value.confirmAddToShelf -> {
                        _uiState.update { it.copy(activeDialog = MangaReaderDialog.AddToShelf) }
                    }
                    sessionRepository.hasBook() && !sessionRepository.inBookshelf() -> onIntent(
                        MangaReaderIntent.DiscardCurrentBookAndExit
                    )
                    else -> _effects.tryEmit(MangaReaderEffect.Finish())
                }
            }
            MangaReaderIntent.ToggleMenu -> setMenuVisible(!_uiState.value.menuVisible)
            MangaReaderIntent.HideMenu -> setMenuVisible(false)
            MangaReaderIntent.Retry -> launchAction {
                showLoading()
                sessionRepository.refreshCurrentChapter()
            }
            MangaReaderIntent.PreviousChapter -> sessionRepository.moveToPreviousChapter(true)
            MangaReaderIntent.NextChapter -> sessionRepository.moveToNextChapter(true)
            MangaReaderIntent.OpenCatalog -> showSheet(MangaReaderSheet.Catalog)
            MangaReaderIntent.OpenBookInfo -> emitAndHide(MangaReaderEffect.OpenBookInfo)
            MangaReaderIntent.OpenChapterUrl -> emitAndHide(
                MangaReaderEffect.OpenChapterUrl(readSettingsGateway.currentSettings.readUrlInBrowser)
            )
            MangaReaderIntent.ChangeSource -> {
                setMenuVisible(false)
                _uiState.update {
                    it.copy(
                        activeSheet = MangaReaderSheet.ChangeSource,
                        changeSourceBook = sessionRepository.currentBook()?.let {
                            MangaBookSnapshot.from(it)
                        },
                    )
                }
            }
            MangaReaderIntent.RefreshChapter -> {
                setMenuVisible(false)
                launchAction {
                    showLoading()
                    sessionRepository.refreshCurrentChapter()
                }
            }
            is MangaReaderIntent.OpenSettings -> openSettings(intent.category)
            MangaReaderIntent.CloseSettings -> closeSettings()
            MangaReaderIntent.OpenSourceActions -> showSheet(MangaReaderSheet.SourceActions)
            MangaReaderIntent.ToggleAutoRead -> _uiState.update {
                it.copy(autoReadEnabled = !it.autoReadEnabled, menuVisible = false)
            }
            MangaReaderIntent.DismissSheet -> _uiState.update {
                it.copy(activeSheet = null, changeSourceBook = null, settingsCategory = null)
            }
            is MangaReaderIntent.UpdateSetting -> updateSetting(intent.key, intent.value)
            is MangaReaderIntent.UpdateClickAction -> updateClickAction(intent.index, intent.action)
            is MangaReaderIntent.PageStep -> requestPageStep(intent.direction)
            is MangaReaderIntent.SeekToPage -> seekToPage(intent.pageIndex)
            is MangaReaderIntent.VisibleItemChanged -> updateVisibleItem(
                intent.itemIndex,
                intent.currentChapterVisible,
            )
            is MangaReaderIntent.LongPressPage -> {
                if (_uiState.value.settings.longPressEnabled) {
                    saveImage(intent.imageUrl)
                }
            }
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
            runCatching {
                sessionRepository.initialize(
                    MangaReaderLaunchRequest(
                        bookUrl = intent.bookUrl,
                        inBookshelf = intent.inBookshelf,
                        chapterChanged = intent.chapterChanged,
                    )
                )
            }.onSuccess { progress ->
                _uiState.update { it.copy(inBookshelf = sessionRepository.inBookshelf()) }
                progress?.let { syncedProgress ->
                    _uiState.update {
                        it.copy(activeDialog = MangaReaderDialog.ConfirmProgress(syncedProgress))
                    }
                }
                refreshContent()
            }.onFailure { error ->
                showError(
                    error.localizedMessage ?: "",
                    fallbackRes = R.string.manga_reader_init_failed,
                )
            }
        }
    }

    private fun payCurrentChapter() {
        launchAction {
            when (val result = sessionRepository.payCurrentChapter()) {
                is MangaPaymentResult.OpenUrl -> _effects.tryEmit(
                    MangaReaderEffect.OpenPaymentUrl(
                        result.url,
                        result.sourceOrigin,
                        result.sourceName,
                        result.sourceType,
                    )
                )
                MangaPaymentResult.Refreshed -> Unit
            }
        }
    }

    private fun saveImage(url: String) {
        launchAction(
            successMessageRes = R.string.manga_reader_image_saved,
            failureMessageRes = R.string.manga_reader_save_failed,
        ) {
            check(sessionRepository.saveImage(url)) {
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
                    if (message != null) enqueueMessage(text = message)
                    else enqueueMessage(resId = failureMessageRes)
                }
        }
    }

    fun refreshContent() = refreshContent(sessionRepository.currentSessionState())

    private fun refreshContent(session: MangaReaderSessionState?) {
        if (session == null) return
        refreshContentJob?.cancel()
        refreshContentJob = viewModelScope.launch {
            val content = session.content
            if (!shouldExposeMangaPages(content.currentFinished)) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        pages = persistentListOf(),
                        currentItemIndex = 0,
                        scrollRequest = null,
                    )
                }
                return@launch
            }
            val items = content.items.mapIndexedNotNull { index, item ->
                when (item) {
                    is MangaPage -> MangaReaderItemUi.Page(
                        key = "page:${item.chapterIndex}:${item.index}:${item.imageUrl}",
                        imageUrl = item.imageUrl,
                        chapterIndex = item.chapterIndex,
                        chapterCount = item.chapterSize,
                        pageIndex = item.index,
                        pageCount = item.imageCount,
                        chapterName = item.chapterName,
                    )
                    is ReaderLoading -> MangaReaderItemUi.ChapterEdge(
                        key = "edge:${item.chapterIndex}:${item.index}:$index",
                        message = item.message.orEmpty(),
                    )
                    else -> null
                }
            }.toImmutableList()
            val safePosition = content.position.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val bookName = session.bookName
            val oldState = _uiState.value
            val shouldPosition = oldState.pages.isEmpty() || oldState.isLoading ||
                    oldState.bookName != bookName
            _uiState.update { old ->
                old.copy(
                    bookName = bookName,
                    bookAuthor = session.bookAuthor,
                    bookUrl = session.bookUrl,
                    chapterName = session.chapterName,
                    chapterUrl = session.chapterUrl,
                    sourceName = session.sourceName,
                    sourceUrl = session.sourceUrl,
                    sourceType = session.sourceType,
                    pages = items,
                    currentItemIndex = if (shouldPosition) safePosition else {
                        old.currentItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    },
                    chapterIndex = session.chapterIndex,
                    chapterCount = session.chapterCount,
                    isLoading = false,
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
            if (shouldPosition) updateVisibleItem(safePosition, currentChapterVisible = true)
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
        sessionRepository.closeSession()
        super.onCleared()
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
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(
                clickActions = state.settings.clickActions.mapIndexed { i, old ->
                    if (i == index) action else old
                }.toImmutableList()
            ))
        }
    }

    private fun updateSetting(key: MangaReaderSettingKey, value: Int) {
        val enabled = value != 0
        when (key) {
            MangaReaderSettingKey.SCROLL_MODE -> {
                persistBookConfig { sessionRepository.updateReadConfig { mangaScrollMode = value } }
                updateSettings { copy(scrollMode = value) }
            }
            MangaReaderSettingKey.SIDE_PADDING -> {
                persistBookConfig { sessionRepository.updateReadConfig { webtoonSidePaddingDp = value } }
                updateSettings { copy(sidePaddingPercent = value) }
            }
            MangaReaderSettingKey.BACKGROUND_RED,
            MangaReaderSettingKey.BACKGROUND_GREEN,
            MangaReaderSettingKey.BACKGROUND_BLUE -> {
                val old = _uiState.value.settings.backgroundColor
                val red = if (key == MangaReaderSettingKey.BACKGROUND_RED) value else (old.red * 255).toInt()
                val green = if (key == MangaReaderSettingKey.BACKGROUND_GREEN) value else (old.green * 255).toInt()
                val blue = if (key == MangaReaderSettingKey.BACKGROUND_BLUE) value else (old.blue * 255).toInt()
                val color = Color(red, green, blue)
                updateMangaPreference { it.copy(background = color.toArgb()) }
                updateSettings { copy(backgroundColor = color) }
            }
            MangaReaderSettingKey.DISABLE_SCALE -> setAndUpdate(
                { copy(disableMangaScale = enabled) }, { copy(disableScale = enabled) })
            MangaReaderSettingKey.DISABLE_SCROLL_ANIMATION -> setAndUpdate(
                { copy(disableMangaScrollAnimation = enabled) },
                { copy(disableScrollAnimation = enabled) })
            MangaReaderSettingKey.DISABLE_CROSS_FADE -> setAndUpdate(
                { copy(disableMangaCrossFade = enabled) }, { copy(disableCrossFade = enabled) })
            MangaReaderSettingKey.DISABLE_CLICK_SCROLL -> setAndUpdate(
                { copy(disableClickScroll = enabled) }, { copy(disableClickScroll = enabled) })
            MangaReaderSettingKey.LONG_PRESS -> setAndUpdate(
                { copy(longClick = enabled) }, { copy(longPressEnabled = enabled) })
            MangaReaderSettingKey.PRE_DOWNLOAD -> setAndUpdate(
                { copy(preDownloadNum = value) }, { copy(preDownloadCount = value) })
            MangaReaderSettingKey.AUTO_READ_SPEED -> setAndUpdate(
                { copy(autoPageSpeed = value) }, { copy(autoReadSpeed = value) })
            MangaReaderSettingKey.VOLUME_KEY_PAGE -> setAndUpdate(
                { copy(volumeKeyPage = enabled) }, { copy(volumeKeyPage = enabled) })
            MangaReaderSettingKey.REVERSE_VOLUME_KEY_PAGE -> setAndUpdate(
                { copy(reverseVolumeKeyPage = enabled) },
                { copy(reverseVolumeKeyPage = enabled) })
            MangaReaderSettingKey.HIDE_MANGA_TITLE -> {
                updateMangaPreference { it.copy(hideTitle = enabled) }
                updateSettings { copy(hideMangaTitle = enabled) }
                sessionRepository.reloadContent()
            }
            MangaReaderSettingKey.ENABLE_GRAY -> {
                updateMangaPreference {
                    it.copy(enableGray = enabled, enableEInk = if (enabled) false else it.enableEInk)
                }
                updateSettings { copy(enableGray = enabled, enableEInk = if (enabled) false else enableEInk) }
            }
            MangaReaderSettingKey.ENABLE_EINK -> {
                updateMangaPreference {
                    it.copy(enableEInk = enabled, enableGray = if (enabled) false else it.enableGray)
                }
                updateSettings { copy(enableEInk = enabled, enableGray = if (enabled) false else enableGray) }
            }
            MangaReaderSettingKey.EINK_THRESHOLD -> setAndUpdate(
                { copy(eInkThreshold = value) }, { copy(eInkThreshold = value) })
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
        }
    }

    private fun updateSettings(update: MangaReaderSettings.() -> MangaReaderSettings) {
        _uiState.update { it.copy(settings = it.settings.update()) }
    }

    private fun setAndUpdate(
        write: MangaSettings.() -> MangaSettings,
        update: MangaReaderSettings.() -> MangaReaderSettings,
    ) {
        updateMangaPreference(write)
        updateSettings(update)
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
        val config = MangaColorFilterConfig(
            r = current.filterRed,
            g = current.filterGreen,
            b = current.filterBlue,
            a = current.filterAlpha,
            l = current.brightness,
            autoBrightness = current.autoBrightness,
        )
        when (key) {
            MangaReaderSettingKey.FILTER_RED -> config.r = value
            MangaReaderSettingKey.FILTER_GREEN -> config.g = value
            MangaReaderSettingKey.FILTER_BLUE -> config.b = value
            MangaReaderSettingKey.FILTER_ALPHA -> config.a = value
            MangaReaderSettingKey.AUTO_BRIGHTNESS -> config.autoBrightness = value != 0
            MangaReaderSettingKey.BRIGHTNESS -> config.l = value
            else -> Unit
        }
        updateMangaPreference { it.copy(colorFilter = config.toJson()) }
        updateSettings {
            copy(
                filterRed = config.r,
                filterGreen = config.g,
                filterBlue = config.b,
                filterAlpha = config.a,
                autoBrightness = config.autoBrightness,
                brightness = config.l,
            )
        }
        _effects.tryEmit(MangaReaderEffect.SetWindowBrightness(config.autoBrightness, config.l))
    }

    private fun updateFooterSetting(key: MangaReaderSettingKey, value: Int) {
        val current = _uiState.value.settings
        val config = MangaFooterConfig(
            hideFooter = current.hideFooter,
            hideChapterName = current.hideChapterName,
            hidePageNumber = current.hidePageNumber,
            hidePageNumberLabel = current.hidePageNumberLabel,
            hideChapter = current.hideChapter,
            hideChapterLabel = current.hideChapterLabel,
            hideProgressRatio = current.hideProgress,
            hideProgressRatioLabel = current.hideProgressLabel,
            footerOrientation = current.footerAlignment,
        )
        val enabled = value != 0
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
        updateMangaPreference { it.copy(footerConfig = GSON.toJson(config)) }
        updateSettings {
            copy(
                hideFooter = config.hideFooter,
                hideChapterName = config.hideChapterName,
                hidePageNumber = config.hidePageNumber,
                hidePageNumberLabel = config.hidePageNumberLabel,
                hideChapter = config.hideChapter,
                hideChapterLabel = config.hideChapterLabel,
                hideProgress = config.hideProgressRatio,
                hideProgressLabel = config.hideProgressRatioLabel,
                footerAlignment = config.footerOrientation,
            )
        }
    }

    private fun requestPageStep(direction: Int) {
        val state = _uiState.value
        val target = mangaPageStepTarget(
            currentIndex = state.currentItemIndex,
            itemCount = state.pages.size,
            direction = direction,
        )
        if (target == null) {
            if (direction > 0) sessionRepository.moveToNextChapter(true)
            else sessionRepository.moveToPreviousChapter(true)
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
                    it.chapterIndex == sessionRepository.currentChapterIndex() && it.pageIndex == pageIndex
        }
        if (target >= 0) {
            _uiState.update { it.copy(scrollRequest = MangaScrollRequest(System.nanoTime(), target, false)) }
        }
    }

    private fun updateVisibleItem(itemIndex: Int, currentChapterVisible: Boolean) {
        val state = _uiState.value
        if (state.isLoading) return
        val item = state.pages.getOrNull(itemIndex) as? MangaReaderItemUi.Page ?: return
        when (mangaChapterSwitchDecision(
            currentChapterIndex = sessionRepository.currentChapterIndex(),
            visibleChapterIndex = item.chapterIndex,
            currentChapterVisible = currentChapterVisible,
        )) {
            MangaChapterSwitch.NEXT -> sessionRepository.moveToNextChapter()
            MangaChapterSwitch.PREVIOUS -> sessionRepository.moveToPreviousChapter()
            MangaChapterSwitch.NONE -> Unit
        }
        sessionRepository.setVisiblePage(item.chapterIndex, item.pageIndex)
        _uiState.update {
            it.copy(
                currentItemIndex = itemIndex,
                currentPage = item.pageIndex,
                pageCount = item.pageCount,
            )
        }
    }

    private fun readSettings(settings: MangaSettings): MangaReaderSettings {
        val colorFilter = GSON.fromJsonObject<MangaColorFilterConfig>(settings.colorFilter)
            .getOrNull() ?: MangaColorFilterConfig()
        val footer = GSON.fromJsonObject<MangaFooterConfig>(settings.footerConfig)
            .getOrNull() ?: MangaFooterConfig()
        return MangaReaderSettings(
        scrollMode = sessionRepository.effectiveScrollMode(settings.scrollMode),
        sidePaddingPercent = sessionRepository.effectiveSidePadding(settings.webtoonSidePaddingDp),
        backgroundColor = Color(settings.background),
        disableScale = settings.disableMangaScale,
        disableScrollAnimation = settings.disableMangaScrollAnimation,
        disableCrossFade = settings.disableMangaCrossFade,
        disableClickScroll = settings.disableClickScroll,
        longPressEnabled = settings.longClick,
        preDownloadCount = settings.preDownloadNum,
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
        sourceOrigin = sessionRepository.currentSourceOrigin(),
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
}
