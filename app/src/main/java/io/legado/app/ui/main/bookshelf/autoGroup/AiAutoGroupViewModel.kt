package io.legado.app.ui.main.bookshelf.autoGroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupSource
import io.legado.app.domain.usecase.ApplyBookshelfAutoGroupPlanUseCase
import io.legado.app.domain.usecase.GenerateBookshelfAutoGroupPlanUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AiAutoGroupViewModel(
    private val generatePlanUseCase: GenerateBookshelfAutoGroupPlanUseCase,
    private val applyPlanUseCase: ApplyBookshelfAutoGroupPlanUseCase,
) : ViewModel() {

    private var source: BookshelfAutoGroupSource? = null
    private var runningJob: Job? = null
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
            is AiAutoGroupIntent.UpdateGroupingInstruction -> {
                _uiState.update { it.copy(groupingInstruction = intent.instruction) }
            }
            is AiAutoGroupIntent.UpdateRevisionInstruction -> {
                _uiState.update { it.copy(revisionInstruction = intent.instruction) }
            }
            AiAutoGroupIntent.Revise -> revise()
        }
    }

    private fun startSession(sessionKey: Long) {
        if (activeSessionKey == sessionKey) return
        beginCleanSession(
            sessionKey = sessionKey,
            groupingInstruction = "",
        )
    }

    private fun closeSession() {
        if (_uiState.value.phase != AiAutoGroupPhase.Applying) {
            runningJob?.cancel()
        }
        runningJob = null
        activeSessionKey = null
        source = null
        _uiState.value = AiAutoGroupUiState()
    }

    private fun restartSession() {
        val sessionKey = activeSessionKey ?: System.nanoTime()
        val groupingInstruction = _uiState.value.groupingInstruction
        beginCleanSession(
            sessionKey = sessionKey,
            groupingInstruction = groupingInstruction,
        )
    }

    private fun beginCleanSession(
        sessionKey: Long,
        groupingInstruction: String,
    ) {
        activeSessionKey = sessionKey
        source = null
        runningJob?.cancel()
        _uiState.value = AiAutoGroupUiState(
            phase = AiAutoGroupPhase.LoadingSource,
            groupingInstruction = groupingInstruction,
        )
        loadSource()
    }

    private fun loadSource() {
        runningJob = viewModelScope.launch {
            runCatching {
                generatePlanUseCase.loadSource()
            }.onSuccess { loaded ->
                source = loaded
                _uiState.update {
                    it.copy(
                        phase = AiAutoGroupPhase.Preflight,
                        bookCount = loaded.bookCount,
                        groupedBookCount = loaded.groupedBookCount,
                        existingGroupCount = loaded.existingGroupNames.size,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                showError(error)
            }
        }
    }

    private fun analyze() {
        val loadedSource = source ?: return loadSource()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = AiAutoGroupPhase.Analyzing, errorMessage = null) }
            runCatching {
                generatePlanUseCase.generate(
                    source = loadedSource,
                    groupingInstruction = _uiState.value.groupingInstruction,
                )
            }.onSuccess { plan ->
                _uiState.update {
                    it.copy(
                        phase = AiAutoGroupPhase.Reviewing,
                        groups = plan.groups.map { group -> group.toUi() }.toImmutableList(),
                        ignoredBooks = plan.ignoredBooks.map { book -> book.toUi() }.toImmutableList(),
                        revisionInstruction = "",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                showError(error)
            }
        }
    }

    private fun revise() {
        val loadedSource = source ?: return
        val instruction = _uiState.value.revisionInstruction.trim()
        if (instruction.isBlank()) {
            _effects.tryEmit(AiAutoGroupEffect.ShowMessage("请输入调整要求"))
            return
        }
        val currentPlan = _uiState.value.toDomainPlan()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update { it.copy(phase = AiAutoGroupPhase.Revising, errorMessage = null) }
            runCatching {
                generatePlanUseCase.revise(loadedSource, currentPlan, instruction)
            }.onSuccess { plan ->
                _uiState.update {
                    it.copy(
                        phase = AiAutoGroupPhase.Reviewing,
                        groups = plan.groups.map { group -> group.toUi() }.toImmutableList(),
                        ignoredBooks = plan.ignoredBooks.map { book -> book.toUi() }.toImmutableList(),
                        revisionInstruction = "",
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                showError(error)
            }
        }
    }

    private fun requestApply() {
        val state = _uiState.value
        if (state.assignedBookCount == 0) {
            _effects.tryEmit(AiAutoGroupEffect.ShowMessage("没有可执行的分组方案"))
            return
        }
        _uiState.update { it.copy(showApplyConfirm = true) }
    }

    private fun cancelRunning() {
        runningJob?.cancel()
        _uiState.update { state ->
            state.copy(
                phase = if (state.groups.isEmpty()) {
                    AiAutoGroupPhase.Preflight
                } else {
                    AiAutoGroupPhase.Reviewing
                },
                errorMessage = null,
            )
        }
        _effects.tryEmit(AiAutoGroupEffect.ShowMessage("已取消"))
    }

    private fun apply() {
        val plan = _uiState.value.toDomainPlan()
        runningJob?.cancel()
        runningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = AiAutoGroupPhase.Applying,
                    showApplyConfirm = false,
                    errorMessage = null,
                )
            }
            runCatching {
                applyPlanUseCase.execute(plan)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        phase = AiAutoGroupPhase.Result,
                        resultText = result.toResultText(),
                    )
                }
                _effects.tryEmit(AiAutoGroupEffect.Applied)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                showError(error)
            }
        }
    }

    private fun renameGroup(groupKey: String, name: String) {
        val existingNames = source?.existingGroupNames.orEmpty().toSet()
        _uiState.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.key == groupKey) {
                        group.copy(
                            name = name.take(24),
                            reuseExisting = name.trim() in existingNames,
                        )
                    } else {
                        group
                    }
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
                    AiAutoGroupIgnoredBookUi(
                        bookUrl = it.bookUrl,
                        name = it.name,
                        author = it.author,
                        reason = "已从建议分组移除",
                    )
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
                }.filter { it.books.isNotEmpty() || it.key == targetGroupKey }
                    .toImmutableList(),
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
                    group.copy(
                        books = group.books.filterNot { it.bookUrl == bookUrl }.toImmutableList()
                    )
                }.filter { it.books.isNotEmpty() }.toImmutableList(),
                ignoredBooks = (state.ignoredBooks + AiAutoGroupIgnoredBookUi(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    reason = "用户选择不处理",
                )).toImmutableList(),
            )
        }
    }

    private fun addGroup(name: String) {
        val finalName = name.trim().take(24)
        if (finalName.isBlank()) {
            _effects.tryEmit(AiAutoGroupEffect.ShowMessage("分组名称不能为空"))
            return
        }
        val existingNames = source?.existingGroupNames.orEmpty().toSet()
        _uiState.update { state ->
            state.copy(
                groups = (state.groups + AiAutoGroupGroupUi(
                    key = UUID.randomUUID().toString(),
                    name = finalName,
                    description = "用户新增",
                    reuseExisting = finalName in existingNames,
                )).toImmutableList()
            )
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                phase = AiAutoGroupPhase.Error,
                errorMessage = error.localizedMessage ?: "AI 自动分组失败",
            )
        }
    }

    private fun BookshelfAutoGroupPlanGroup.toUi() = AiAutoGroupGroupUi(
        key = key,
        name = name,
        description = description,
        reuseExisting = reuseExisting,
        books = books.map { it.toUi() }.toImmutableList(),
    )

    private fun BookshelfAutoGroupPlanBook.toUi() = AiAutoGroupBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        currentGroupNames = currentGroupNames.toImmutableList(),
        reason = reason,
    )

    private fun BookshelfAutoGroupIgnoredBook.toUi() = AiAutoGroupIgnoredBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        reason = reason,
    )

    private fun AiAutoGroupIgnoredBookUi.toBookUi() = AiAutoGroupBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        reason = reason,
    )

    private fun AiAutoGroupUiState.toDomainPlan() = BookshelfAutoGroupPlan(
        groups = groups.mapNotNull { group ->
            val name = group.name.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (group.books.isEmpty()) return@mapNotNull null
            BookshelfAutoGroupPlanGroup(
                key = group.key,
                name = name,
                description = group.description,
                reuseExisting = group.reuseExisting,
                books = group.books.map {
                    BookshelfAutoGroupPlanBook(
                        bookUrl = it.bookUrl,
                        name = it.name,
                        author = it.author,
                        currentGroupNames = it.currentGroupNames,
                        reason = it.reason,
                    )
                },
            )
        },
        ignoredBooks = ignoredBooks.map {
            BookshelfAutoGroupIgnoredBook(
                bookUrl = it.bookUrl,
                name = it.name,
                author = it.author,
                reason = it.reason,
            )
        },
    )

    private fun BookshelfAutoGroupApplyResult.toResultText(): String {
        return "已创建 ${createdGroupCount} 个分组，复用 ${reusedGroupCount} 个分组，更新 ${updatedBookCount} 本书，跳过 ${ignoredBookCount} 本书。"
    }
}
