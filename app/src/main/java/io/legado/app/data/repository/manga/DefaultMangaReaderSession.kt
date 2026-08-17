package io.legado.app.data.repository.manga

import io.legado.app.R
import io.legado.app.domain.gateway.MangaReaderDataGateway
import io.legado.app.domain.gateway.MangaReaderSession
import io.legado.app.domain.model.manga.MangaChapterState
import io.legado.app.domain.model.manga.MangaSessionCommand
import io.legado.app.domain.model.manga.MangaSessionEvent
import io.legado.app.domain.model.manga.MangaSessionId
import io.legado.app.domain.model.manga.MangaSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class DefaultMangaReaderSession(
    private val dataGateway: MangaReaderDataGateway,
    private val stateDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) : MangaReaderSession {

    private companion object {
        const val ADJACENT_LOAD_DISTANCE = 5
        const val PROGRESS_PERSIST_INTERVAL_MS = 3_000L
    }

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + stateDispatcher)
    private var loadJob = SupervisorJob(sessionJob)
    private var loadScope = CoroutineScope(loadJob + ioDispatcher)
    private var presentationJob: Job? = null
    private var scheduledProgressPersistJob: Job? = null
    private var lastProgressPersistAt = 0L
    private val commands = Channel<MangaSessionCommand>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(MangaSessionState(MangaSessionId.create()))
    override val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<MangaSessionEvent>(extraBufferCapacity = 16)
    override val events = _events.asSharedFlow()
    private var prefetchCount = 0

    init {
        sessionScope.launch {
            for (command in commands) {
                try {
                    reduce(command)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    // 单条命令失败不能终止命令循环，否则后续命令永久排队、阅读器失去响应
                    _events.tryEmit(
                        MangaSessionEvent.Message(
                            error.localizedMessage
                                ?: appCtx.getString(R.string.manga_reader_action_failed)
                        )
                    )
                }
            }
        }
    }

    override suspend fun execute(command: MangaSessionCommand) {
        commands.send(command)
    }

    private suspend fun reduce(command: MangaSessionCommand) {
        when (command) {
            is MangaSessionCommand.Open -> open(command)
            is MangaSessionCommand.OpenChapter -> moveTo(
                command.chapterIndex,
                command.pageIndex,
                persist = true,
            )
            MangaSessionCommand.PreviousChapter -> moveTo(
                _state.value.chapterIndex - 1,
                0,
                persist = true,
            )
            MangaSessionCommand.NextChapter -> moveTo(
                _state.value.chapterIndex + 1,
                0,
                persist = true,
            )
            is MangaSessionCommand.PromoteVisibleChapter -> promoteVisibleChapter(command)
            is MangaSessionCommand.VisiblePageChanged -> visiblePageChanged(command)
            MangaSessionCommand.PersistVisibleProgress -> {
                scheduledProgressPersistJob = null
                persistProgress()
            }
            is MangaSessionCommand.RetryChapter -> retryChapter(command.chapterIndex)
            is MangaSessionCommand.PrefetchCountChanged -> {
                prefetchCount = command.count.coerceAtLeast(0)
                prefetch(_state.value.chapterIndex)
            }
            is MangaSessionCommand.BookPresentationObserved -> {
                val state = _state.value
                val book = state.book ?: return
                if (book.bookUrl != command.bookUrl) return
                _state.value = state.copy(
                    book = book.copy(
                        scrollMode = command.presentation.scrollMode,
                        sidePaddingDp = command.presentation.sidePaddingDp,
                    )
                )
            }
            is MangaSessionCommand.ApplyProgress -> applyProgress(command)
            is MangaSessionCommand.ChapterLoaded -> chapterLoaded(command)
            MangaSessionCommand.Resume -> {
                _state.value.book?.let { withContext(ioDispatcher) { dataGateway.resume(it.bookUrl) } }
                _state.value = _state.value.copy(resumed = true)
            }
            MangaSessionCommand.Pause -> {
                cancelScheduledProgressPersist()
                persistProgress()
                _state.value.book?.let {
                    withContext(ioDispatcher) { dataGateway.pause(it.bookUrl, it.inBookshelf) }
                }
                _state.value = _state.value.copy(resumed = false)
            }
            MangaSessionCommand.NetworkAvailable -> syncProgress()
            MangaSessionCommand.Close -> shutdown()
        }
    }

    private suspend fun open(command: MangaSessionCommand.Open) {
        resetLoadScope()
        presentationJob?.cancel()
        cancelScheduledProgressPersist()
        val old = _state.value
        if (old.book != null) {
            persistProgress()
            if (old.resumed) withContext(ioDispatcher) {
                dataGateway.pause(old.book.bookUrl, old.book.inBookshelf)
            }
        }
        _state.value = MangaSessionState(
            sessionId = old.sessionId,
            revision = old.revision + 1,
            resumed = old.resumed,
        )
        runCatching {
            withContext(ioDispatcher) {
                dataGateway.openBook(command.bookUrl, command.inBookshelf, command.chapterChanged)
            }
        }.onSuccess { opened ->
            _state.value = _state.value.copy(
                book = opened.book,
                chapterIndex = opened.chapterIndex,
                pageIndex = opened.pageIndex,
                chapterCount = opened.chapterCount,
            )
            presentationJob = sessionScope.launch {
                dataGateway.observeBookPresentation(opened.book.bookUrl).collect { presentation ->
                    commands.send(
                        MangaSessionCommand.BookPresentationObserved(
                            opened.book.bookUrl,
                            presentation,
                        )
                    )
                }
            }
            opened.newerProgress?.let {
                _events.tryEmit(MangaSessionEvent.ConfirmProgress(it))
            }
            if (_state.value.resumed) {
                withContext(ioDispatcher) { dataGateway.resume(opened.book.bookUrl) }
            }
            loadWindow(opened.chapterIndex)
        }.onFailure { error ->
            val message = error.localizedMessage.orEmpty()
            _state.value = _state.value.copy(openError = message)
            _events.tryEmit(MangaSessionEvent.Message(message))
        }
    }

    private suspend fun moveTo(chapterIndex: Int, pageIndex: Int, persist: Boolean) {
        val state = _state.value
        if (chapterIndex !in 0 until state.chapterCount) return
        resetLoadScope()
        cancelScheduledProgressPersist()
        _state.value = state.copy(
            revision = state.revision + 1,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex.coerceAtLeast(0),
            previousChapter = MangaChapterState.Empty,
            currentChapter = MangaChapterState.Empty,
            nextChapter = MangaChapterState.Empty,
        )
        if (persist) persistProgress()
        loadWindow(chapterIndex)
    }

    private fun loadWindow(chapterIndex: Int) {
        retainChapterResources(chapterIndex)
        loadChapter(chapterIndex)
        prefetch(chapterIndex)
    }

    /**
     * Makes an already visible adjacent chapter current without replacing the reader window.
     * The UI has already crossed its transition page, so resetting the list here would cause a
     * jump back to the chapter start.
     */
    private fun promoteVisibleChapter(command: MangaSessionCommand.PromoteVisibleChapter) {
        val state = _state.value
        if (command.chapterIndex !in 0 until state.chapterCount ||
            command.chapterIndex == state.chapterIndex
        ) return
        val movingForward = command.chapterIndex > state.chapterIndex
        val target = if (movingForward) state.nextChapter else state.previousChapter
        if (target !is MangaChapterState.Ready) return
        _state.value = state.copy(
            revision = state.revision + 1,
            chapterIndex = command.chapterIndex,
            pageIndex = command.pageIndex.coerceAtLeast(0),
            previousChapter = if (movingForward) state.currentChapter else MangaChapterState.Empty,
            currentChapter = target,
            nextChapter = if (movingForward) MangaChapterState.Empty else state.currentChapter,
        )
        if (movingForward) loadChapter(command.chapterIndex + 1)
        else loadChapter(command.chapterIndex - 1)
        retainChapterResources(command.chapterIndex)
        prefetch(command.chapterIndex)
    }

    private fun prefetch(chapterIndex: Int) {
        val state = _state.value
        val book = state.book ?: return
        val indexes = (chapterIndex + 1..chapterIndex + prefetchCount)
            .filter { it in 0 until state.chapterCount }
        loadScope.launch {
            indexes.forEach { index ->
                val token = state.tokenFor(index) ?: return@forEach
                if (_state.value.accepts(token)) {
                    runCatching { dataGateway.prefetchChapter(book.bookUrl, index) }
                }
            }
        }
    }

    private fun loadChapter(chapterIndex: Int) {
        val state = _state.value
        if (chapterIndex !in 0 until state.chapterCount) return
        when (state.chapterStateAt(chapterIndex)) {
            is MangaChapterState.Loading,
            is MangaChapterState.Ready -> return
            MangaChapterState.Empty,
            is MangaChapterState.Failed -> Unit
            null -> return
        }
        val token = state.tokenFor(chapterIndex) ?: return
        setChapterState(chapterIndex, MangaChapterState.Loading(token))
        loadScope.launch {
            val result = runCatching {
                dataGateway.loadChapter(token.bookUrl, token.chapterIndex)
            }
            commands.send(MangaSessionCommand.ChapterLoaded(token, result))
        }
    }

    private fun chapterLoaded(command: MangaSessionCommand.ChapterLoaded) {
        if (!_state.value.accepts(command.token)) return
        val chapterState = command.result.fold(
            onSuccess = { MangaChapterState.Ready(command.token, it) },
            onFailure = {
                MangaChapterState.Failed(
                    token = command.token,
                    message = it.localizedMessage.orEmpty(),
                )
            },
        )
        setChapterState(command.token.chapterIndex, chapterState)
        if (chapterState is MangaChapterState.Ready &&
            command.token.chapterIndex == _state.value.chapterIndex
        ) {
            val lastPageIndex = chapterState.chapter.pages.lastIndex.coerceAtLeast(0)
            _state.value = _state.value.copy(
                pageIndex = _state.value.pageIndex.coerceIn(0, lastPageIndex)
            )
        }
    }

    private fun setChapterState(chapterIndex: Int, chapter: MangaChapterState) {
        val state = _state.value
        _state.value = when (chapterIndex - state.chapterIndex) {
            -1 -> state.copy(previousChapter = chapter)
            0 -> state.copy(currentChapter = chapter)
            1 -> state.copy(nextChapter = chapter)
            else -> state
        }
    }

    private fun retryChapter(chapterIndex: Int) {
        val state = _state.value
        val existing = when (chapterIndex - state.chapterIndex) {
            -1 -> state.previousChapter
            0 -> state.currentChapter
            1 -> state.nextChapter
            else -> return
        }
        if (existing is MangaChapterState.Loading) return
        loadChapter(chapterIndex)
    }

    private suspend fun visiblePageChanged(command: MangaSessionCommand.VisiblePageChanged) {
        val state = _state.value
        if (command.chapterIndex != state.chapterIndex) return
        val pageIndex = command.pageIndex.coerceAtLeast(0)
        _state.value = state.copy(pageIndex = pageIndex)
        val chapter = state.currentChapter as? MangaChapterState.Ready ?: return
        val lastPageIndex = chapter.chapter.pages.lastIndex
        if (pageIndex < ADJACENT_LOAD_DISTANCE) loadChapter(state.chapterIndex - 1)
        if (pageIndex >= lastPageIndex - ADJACENT_LOAD_DISTANCE + 1) {
            loadChapter(state.chapterIndex + 1)
        }
        scheduleProgressPersist()
    }

    private suspend fun applyProgress(command: MangaSessionCommand.ApplyProgress) {
        val book = _state.value.book ?: return
        withContext(ioDispatcher) { dataGateway.applyProgress(book.bookUrl, command.progress) }
        val chapterCount = _state.value.chapterCount
        if (chapterCount == 0) return
        moveTo(
            command.progress.chapterIndex.coerceIn(0, chapterCount - 1),
            command.progress.pageIndex,
            persist = false,
        )
    }

    private suspend fun persistProgress() {
        val state = _state.value
        val book = state.book ?: return
        withContext(ioDispatcher) {
            dataGateway.persistProgress(book.bookUrl, state.chapterIndex, state.pageIndex)
        }
        lastProgressPersistAt = System.currentTimeMillis()
    }

    private suspend fun scheduleProgressPersist() {
        if (scheduledProgressPersistJob?.isActive == true) return
        val remainingDelay = (lastProgressPersistAt + PROGRESS_PERSIST_INTERVAL_MS -
            System.currentTimeMillis()).coerceAtLeast(0L)
        if (remainingDelay == 0L) {
            persistProgress()
            return
        }
        scheduledProgressPersistJob = sessionScope.launch {
            delay(remainingDelay)
            commands.trySend(MangaSessionCommand.PersistVisibleProgress)
        }
    }

    private fun cancelScheduledProgressPersist() {
        scheduledProgressPersistJob?.cancel()
        scheduledProgressPersistJob = null
    }

    private fun resetLoadScope() {
        loadJob.cancel()
        loadJob = SupervisorJob(sessionJob)
        loadScope = CoroutineScope(loadJob + ioDispatcher)
    }

    private fun retainChapterResources(chapterIndex: Int) {
        val state = _state.value
        val bookUrl = state.book?.bookUrl ?: return
        val retained = (chapterIndex - 1..chapterIndex + 1)
            .filter { it in 0 until state.chapterCount }
            .toSet()
        loadScope.launch {
            dataGateway.retainChapterResources(bookUrl, retained)
        }
    }

    private suspend fun syncProgress() {
        val book = _state.value.book ?: return
        withContext(ioDispatcher) { dataGateway.syncProgress(book.bookUrl) }?.let {
            _events.tryEmit(MangaSessionEvent.ConfirmProgress(it))
        }
    }

    private fun MangaSessionState.chapterStateAt(chapterIndex: Int): MangaChapterState? = when (
        chapterIndex - this.chapterIndex
    ) {
        -1 -> previousChapter
        0 -> currentChapter
        1 -> nextChapter
        else -> null
    }

    override fun close() {
        commands.trySend(MangaSessionCommand.Close)
    }

    private suspend fun shutdown() {
        presentationJob?.cancel()
        cancelScheduledProgressPersist()
        persistProgress()
        _state.value.book?.let {
            withContext(ioDispatcher) { dataGateway.pause(it.bookUrl, it.inBookshelf) }
        }
        withContext(ioDispatcher) { dataGateway.releaseAllChapterResources() }
        commands.close()
        sessionScope.cancel()
    }
}
