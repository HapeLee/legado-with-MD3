package io.legado.app.ui.main.bookshelf.autoGroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupPreflight
import io.legado.app.domain.model.BookshelfAutoGroupSource
import io.legado.app.domain.usecase.ApplyBookshelfAutoGroupPlanUseCase
import io.legado.app.domain.usecase.GenerateBookshelfAutoGroupPlanUseCase
import java.util.UUID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiAutoGroupViewModel(
    private val generatePlanUseCase: GenerateBookshelfAutoGroupPlanUseCase,
    private val applyPlanUseCase: ApplyBookshelfAutoGroupPlanUseCase,
) : ViewModel() {

    private var source: BookshelfAutoGroupSource? = null
    private var runningJob: Job? = null
    private var preflightJob: Job? = null
    private var activeSessionKey: Long? = null

    private val _uiState = MutableStateFlow(AiAutoGroupUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiAutoGroupEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiAutoGroupIntent) {
        when (intent) {
            is AiAutoGroupIntent.StartSession -> startSession(intent.sessionKey)
            AiAutoGroupIntent.CloseSession -> closeSession()
            AiAutoGroupIntent.Analyze -> analyze()
            AiAutoGroupIntent.DismissApplyConfirm -> {
                _uiState.update { it.copy(showApplyConfirm = false) }
            }
            AiAutoGroupIntent.RequestApply -> requestApply()
            AiAutoGroupIntent.ConfirmApply -> apply()
            AiAutoGroupIntent.Restart -> restartSession()
            AiAutoGroupIntent.CancelRunning -> cancelRunning()
            is AiAutoGroupIntent.RenameGroup -> renameGroup(intent.groupKey, intent.name)
            is AiAutoGroupIntent.RemoveGroup -> removeGroup(intent.groupKey)
            is AiAutoGroupIntent.MoveBook -> moveBook(intent.bookUrl, intent.targetGroupKey)
            is AiAutoGroupIntent.IgnoreBook -> ignoreBook(intent.bookUrl)
            is AiAutoGroupIntent.AddGroup -> addGroup(intent.name)
            is AiAutoGroupIntent.UpdateGroupingInstruction -> updateGroupingInstruction(intent.instruction)
            is AiAutoGroupIntent.SetIncrementalOnly -> updateIncrementalOption(intent.enabled)
            is AiAutoGroupIntent.SetIncludeBookIntro -> updateBookIntroOption(intent.enabled)
            is AiAutoGroupIntent.SetDeepThinkingEnabled -> updateDeepThinkingOption(intent.enabled)
            is AiAutoGroupIntent.UpdateRevisionInstruction -> {
                _uiState.update {
                    it.copy(revisionInstruction = intent.instruction.take(MAX_INSTRUCTION_CHARS))
                }
            }
            AiAutoGroupIntent.Revise -> revise()
        }
    }

    private fun startSession(sessionKey: Long) {
        if (activeSessionKey == sessionKey) return
        beginCleanSession(sessionKey, "")
    }

    private fun closeSession() {
        if (_uiState.value.phase != AiAutoGroupPhase.Applying) runningJob?.cancel()
        preflightJob?.cancel()
        runningJob = null
        preflightJob = null
        activeSessionKey = null
        source = null
        _uiState.value = AiAutoGroupUiState()
    }

    private fun restartSession() {
        val state = _uiState.value
        beginCleanSession(
            sessionKey = activeSessionKey ?: System.nanoTime(),
            groupingInstruction = state.groupingInstruction,
            options = state.toDomainOptions(),
        )
    }

    private fun beginCleanSession(
        sessionKey: Long,
        groupingInstruction: String,
        options: BookshelfAutoGroupOptions = BookshelfAutoGroupOptions(),
    ) {
        activeSessionKey = sessionKey
        source = null
        runningJob?.cancel()
        preflightJob?.cancel()
        _uiState.value = AiAutoGroupUiState(
            phase = AiAutoGroupPhase.LoadingSource,
            groupingInstruction = groupingInstruction,
            incrementalOnly = options.incrementalOnly,
            includeBookIntro = options.includeBookIntro,
            enableDeepThinking = options.enableDeepThinking,
        )
        loadSource()
    }

    private fun loadSource() {
        runningJob = viewModelScope.launch {
            runCatching {
                val loaded = generatePlanUseCase.loadSource()
                val state = _uiState.value
                loaded to generatePlanUseCase.preflight(
                    source = loaded,
                    groupingInstruction = state.groupingInstruction,
                    options = state.toDomainOptions(),
                )
            }.onSuccess { (loaded, preflight) ->
                source = loaded
                _uiState.update {
                    it.copy(
                        phase = AiAutoGroupPhase.Preflight,
                        bookCount = preflight.analyzedBookCount,
                        groupedBookCount = loaded.groupedBookCount,
                        existingGroupCount = loaded.existingGroupNames.size,
                        effectiveInputCharLimit = preflight.effectiveInputCharLimit,
                        estimatedRequestCount = preflight.estimatedRequestCount,
                        error = null,
                    )
                }
            }.onFailure(::handleTerminalFailure)
        }
    }

    private fun updateGroupingInstruction(instruction: String) {
        val bounded = instruction.take(MAX_INSTRUCTION_CHARS)
        _uiState.update { it.copy(groupingInstruction = bounded) }
        schedulePreflight()
    }

    private fun updateBookIntroOption(enabled: Boolean) {
        _uiState.update { it.copy(includeBookIntro = enabled) }
        schedulePreflight()
    }

    private fun updateIncrementalOption(enabled: Boolean) {
        _uiState.update { it.copy(incrementalOnly = enabled) }
        // Mode changes affect both the visible book count and request estimate, so refresh immediately.
        schedulePreflight(debounce = false)
    }

    private fun updateDeepThinkingOption(enabled: Boolean) {
        _uiState.update { it.copy(enableDeepThinking = enabled) }
    }

    private fun schedulePreflight(debounce: Boolean = true) {
        val loaded = source ?: return
        val state = _uiState.value
        preflightJob?.cancel()
        preflightJob = viewModelScope.launch {
            if (debounce) delay(PREFLIGHT_DEBOUNCE_MILLIS)
            runCatching {
                generatePlanUseCase.preflight(
                    source = loaded,
                    groupingInstruction = state.groupingInstruction,
                    options = state.toDomainOptions(),
                )
            }
                .onSuccess(::updatePreflight)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(error = error.toUiError()) }
                }
        }
    }

    private fun updatePreflight(preflight: BookshelfAutoGroupPreflight) {
        _uiState.update {
            it.copy(
                bookCount = preflight.analyzedBookCount,
                effectiveInputCharLimit = preflight.effectiveInputCharLimit,
                estimatedRequestCount = preflight.estimatedRequestCount,
                error = null,
            )
        }
    }

    private fun analyze() {
        val loadedSource = source ?: return loadSource()
        val state = _uiState.value
        preflightJob?.cancel()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AiAutoGroupPhase.Analyzing, error = null, currentBatch = 0, totalBatches = 0)
            }
            runCatching {
                generatePlanUseCase.generate(
                    source = loadedSource,
                    groupingInstruction = state.groupingInstruction,
                    options = state.toDomainOptions(),
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(currentBatch = progress.currentBatch, totalBatches = progress.totalBatches)
                        }
                    },
                )
            }.onSuccess(::showPlan)
                .onFailure(::handleTerminalFailure)
        }
    }

    private fun revise() {
        val loadedSource = source ?: return
        val instruction = _uiState.value.revisionInstruction.trim()
        if (instruction.isBlank()) {
            showMessage(AiAutoGroupMessage.EnterRevisionInstruction)
            return
        }
        val currentPlan = _uiState.value.toDomainPlan()
        val options = _uiState.value.toDomainOptions()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AiAutoGroupPhase.Revising, error = null, currentBatch = 0, totalBatches = 0)
            }
            runCatching {
                generatePlanUseCase.revise(
                    source = loadedSource,
                    currentPlan = currentPlan,
                    instruction = instruction,
                    options = options,
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(currentBatch = progress.currentBatch, totalBatches = progress.totalBatches)
                        }
                    },
                )
            }.onSuccess(::showPlan)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(phase = AiAutoGroupPhase.Reviewing, currentBatch = 0, totalBatches = 0)
                    }
                    _effects.tryEmit(AiAutoGroupEffect.ShowError(error.toUiError()))
                }
        }
    }

    private fun showPlan(plan: BookshelfAutoGroupPlan) {
        _uiState.update {
            it.copy(
                phase = AiAutoGroupPhase.Reviewing,
                groups = plan.groups.map { group -> group.toUi() }.toImmutableList(),
                ignoredBooks = plan.ignoredBooks.map { book -> book.toUi() }.toImmutableList(),
                revisionInstruction = "",
                currentBatch = 0,
                totalBatches = 0,
                error = null,
            )
        }
    }

    private fun requestApply() {
        val normalizedPlan = applyPlanUseCase.normalize(
            plan = _uiState.value.toDomainPlan(),
            existingGroupNames = source?.existingGroupNames.orEmpty().toSet(),
        )
        if (normalizedPlan.assignedBookCount == 0) {
            showMessage(AiAutoGroupMessage.NoApplicablePlan)
            return
        }
        _uiState.update {
            it.copy(
                groups = normalizedPlan.groups.map { group -> group.toUi() }.toImmutableList(),
                ignoredBooks = normalizedPlan.ignoredBooks.map { book -> book.toUi() }.toImmutableList(),
                showApplyConfirm = true,
            )
        }
    }

    private fun cancelRunning() {
        runningJob?.cancel()
        _uiState.update { state ->
            state.copy(
                phase = if (state.groups.isEmpty()) AiAutoGroupPhase.Preflight else AiAutoGroupPhase.Reviewing,
                currentBatch = 0,
                totalBatches = 0,
                error = null,
            )
        }
        showMessage(AiAutoGroupMessage.Cancelled)
    }

    private fun apply() {
        val state = _uiState.value
        val plan = state.toDomainPlan()
        val options = state.toDomainOptions()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(phase = AiAutoGroupPhase.Applying, showApplyConfirm = false, error = null)
            }
            runCatching { applyPlanUseCase.execute(plan, options) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(phase = AiAutoGroupPhase.Result, applyResult = result.toUi())
                    }
                    _effects.tryEmit(AiAutoGroupEffect.Applied)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    // 保留已审计划回到 Reviewing，避免失败后被迫重跑整个分析
                    _uiState.update {
                        it.copy(phase = AiAutoGroupPhase.Reviewing, currentBatch = 0, totalBatches = 0)
                    }
                    _effects.tryEmit(AiAutoGroupEffect.ShowError(error.toUiError()))
                }
        }
    }

    private fun renameGroup(groupKey: String, name: String) {
        val existingNames = source?.existingGroupNames.orEmpty().toSet()
        _uiState.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.key == groupKey) {
                        group.copy(name = name.take(24), reuseExisting = name.trim() in existingNames)
                    } else group
                }.toImmutableList()
            )
        }
    }

    private fun removeGroup(groupKey: String) {
        _uiState.update { state ->
            val removed = state.groups.firstOrNull { it.key == groupKey } ?: return@update state
            state.copy(
                groups = state.groups.filterNot { it.key == groupKey }.toImmutableList(),
                ignoredBooks = (state.ignoredBooks + removed.books.map {
                    AiAutoGroupIgnoredBookUi(it.bookUrl, it.name, it.author, "")
                }).toImmutableList(),
            )
        }
    }

    private fun moveBook(bookUrl: String, targetGroupKey: String) {
        _uiState.update { state ->
            val currentBook = state.groups.asSequence()
                .flatMap { it.books.asSequence() }
                .firstOrNull { it.bookUrl == bookUrl }
                ?: state.ignoredBooks.firstOrNull { it.bookUrl == bookUrl }?.toBookUi()
                ?: return@update state
            state.copy(
                groups = state.groups.map { group ->
                    val withoutBook = group.books.filterNot { it.bookUrl == bookUrl }
                    if (group.key == targetGroupKey) {
                        group.copy(books = (withoutBook + currentBook).toImmutableList())
                    } else {
                        group.copy(books = withoutBook.toImmutableList())
                    }
                }.filter { it.books.isNotEmpty() || it.key == targetGroupKey }.toImmutableList(),
                ignoredBooks = state.ignoredBooks.filterNot { it.bookUrl == bookUrl }.toImmutableList(),
            )
        }
    }

    private fun ignoreBook(bookUrl: String) {
        _uiState.update { state ->
            val book = state.groups.asSequence()
                .flatMap { it.books.asSequence() }
                .firstOrNull { it.bookUrl == bookUrl }
                ?: return@update state
            state.copy(
                groups = state.groups.map { group ->
                    group.copy(books = group.books.filterNot { it.bookUrl == bookUrl }.toImmutableList())
                }.filter { it.books.isNotEmpty() }.toImmutableList(),
                ignoredBooks = (state.ignoredBooks + AiAutoGroupIgnoredBookUi(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    reason = "",
                )).toImmutableList(),
            )
        }
    }

    private fun addGroup(name: String) {
        val finalName = name.trim().take(24)
        if (finalName.isBlank()) {
            showMessage(AiAutoGroupMessage.GroupNameRequired)
            return
        }
        val existingNames = source?.existingGroupNames.orEmpty().toSet()
        _uiState.update { state ->
            state.copy(
                groups = (state.groups + AiAutoGroupGroupUi(
                    key = UUID.randomUUID().toString(),
                    name = finalName,
                    description = "",
                    reuseExisting = finalName in existingNames,
                )).toImmutableList()
            )
        }
    }

    private fun handleTerminalFailure(error: Throwable) {
        if (error is CancellationException) throw error
        _uiState.update {
            it.copy(
                phase = AiAutoGroupPhase.Error,
                currentBatch = 0,
                totalBatches = 0,
                error = error.toUiError(),
            )
        }
    }

    private fun Throwable.toUiError(): AiAutoGroupErrorUi {
        val reason = (this as? BookshelfAutoGroupException)?.reason
        return when (reason) {
            BookshelfAutoGroupErrorReason.EmptyBookshelf -> AiAutoGroupErrorUi.EmptyBookshelf
            BookshelfAutoGroupErrorReason.MissingModel -> AiAutoGroupErrorUi.MissingModel
            BookshelfAutoGroupErrorReason.CapacityTooSmall -> AiAutoGroupErrorUi.CapacityTooSmall
            BookshelfAutoGroupErrorReason.GroupCapacityExceeded ->
                AiAutoGroupErrorUi.GroupCapacityExceeded
            BookshelfAutoGroupErrorReason.InvalidResponse -> AiAutoGroupErrorUi.InvalidResponse
            null -> AiAutoGroupErrorUi.Unexpected(localizedMessage)
        }
    }

    private fun showMessage(message: AiAutoGroupMessage) {
        _effects.tryEmit(AiAutoGroupEffect.ShowMessage(message))
    }

    private fun BookshelfAutoGroupPlanGroup.toUi() = AiAutoGroupGroupUi(
        key, name, description, reuseExisting, books.map { it.toUi() }.toImmutableList()
    )

    private fun BookshelfAutoGroupPlanBook.toUi() = AiAutoGroupBookUi(
        bookUrl, name, author, currentGroupNames.toImmutableList(), reason
    )

    private fun BookshelfAutoGroupIgnoredBook.toUi() = AiAutoGroupIgnoredBookUi(
        bookUrl, name, author, reason
    )

    private fun AiAutoGroupIgnoredBookUi.toBookUi() = AiAutoGroupBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        reason = reason,
    )

    private fun AiAutoGroupUiState.toDomainPlan() = BookshelfAutoGroupPlan(
        groups = groups.mapNotNull { group ->
            val name = group.name.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (group.books.isEmpty()) return@mapNotNull null
            BookshelfAutoGroupPlanGroup(
                key = group.key,
                name = name,
                description = group.description,
                reuseExisting = group.reuseExisting,
                books = group.books.map {
                    BookshelfAutoGroupPlanBook(
                        it.bookUrl, it.name, it.author, it.currentGroupNames, it.reason
                    )
                },
            )
        },
        ignoredBooks = ignoredBooks.map {
            BookshelfAutoGroupIgnoredBook(it.bookUrl, it.name, it.author, it.reason)
        },
    )

    private fun AiAutoGroupUiState.toDomainOptions() = BookshelfAutoGroupOptions(
        incrementalOnly = incrementalOnly,
        includeBookIntro = includeBookIntro,
        enableDeepThinking = enableDeepThinking,
    )

    private fun BookshelfAutoGroupApplyResult.toUi() = AiAutoGroupApplyResultUi(
        createdGroupCount, reusedGroupCount, updatedBookCount, ignoredBookCount
    )

    private companion object {
        const val MAX_INSTRUCTION_CHARS = 1_000
        const val PREFLIGHT_DEBOUNCE_MILLIS = 150L
    }
}
