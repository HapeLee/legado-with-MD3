package io.legado.app.ui.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import io.legado.app.data.entities.AiArtifact
import io.legado.app.help.config.AppConfigStore
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.domain.model.aiErrorKind
import io.legado.app.domain.model.reasoningContent
import io.legado.app.domain.model.textContent
import io.legado.app.domain.model.toolParts
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.PendingToolRun
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiArtifactGateway: AiArtifactGateway,
    private val generationUseCase: AiChatGenerationUseCase
) : ViewModel() {

    private val currentConversationId = MutableStateFlow<String?>(null)
    private var lastMessages: List<AiChatMessageUi> = emptyList()
    private var streamingJob: Job? = null
    private var pendingToolRun: PendingToolRun? = null
    private var thinkingStartTime: Long = 0L

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        observeConversations()
        observeCurrentMessages()
        observeArtifacts()
    }

    private fun observeArtifacts() {
        viewModelScope.launch {
            aiArtifactGateway.observeAllArtifacts().collect { artifacts ->
                _uiState.update { it.copy(workspaceArtifacts = artifacts.toImmutableList()) }
            }
        }
    }

    fun onIntent(intent: AiChatIntent) {
        when (intent) {
            AiChatIntent.NewConversation -> createConversation()
            is AiChatIntent.SelectConversation -> selectConversation(intent.id)
            is AiChatIntent.SendMessage -> sendMessage(intent.content)
            AiChatIntent.StopGenerating -> stopGenerating()
            AiChatIntent.ConfirmPendingTool -> confirmPendingTool()
            AiChatIntent.RejectPendingTool -> rejectPendingTool()
            is AiChatIntent.UpdateReasoningLevel -> updateReasoningLevel(intent.level)
            is AiChatIntent.RegenerateMessage -> regenerateMessage(intent.messageId)
            is AiChatIntent.SwitchBranch -> switchBranch(intent.messageId)
            is AiChatIntent.DeleteConversation -> deleteConversation(intent.id)
            is AiChatIntent.RenameConversation -> renameConversation(intent.id, intent.title)
            is AiChatIntent.SetReadingContext -> _uiState.update { it.copy(readingContext = intent.context) }
            is AiChatIntent.ApplySkill -> applySkill(intent.skillId)
            is AiChatIntent.OpenBookChat -> openBookChat(intent.context, intent.skillId, intent.skillName)
            AiChatIntent.SaveConversationToWorkspace -> saveConversationToWorkspace()
            AiChatIntent.ToggleWorkspace -> _uiState.update { it.copy(workspaceOpen = !it.workspaceOpen) }
            AiChatIntent.CloseWorkspace -> _uiState.update { it.copy(workspaceOpen = false) }
            AiChatIntent.ClearBookChatMessages -> clearBookChatMessages()
            AiChatIntent.NewBookChat -> newBookChat()
        }
    }

    /**
     * 阅读页「每本书一个对话小窗」：用 bookUrl 映射到独立会话；首次为该书新建会话并记住映射。
     * skillId 非空时只把技能名放进标题栏，并发一句轻量提示，AI 会通过 load_skill 自己加载正文。
     */
    private fun openBookChat(context: AiReadingContext, skillId: String?, skillName: String?) {
        _uiState.update { it.copy(readingContext = context, activeSkillName = skillName) }
        viewModelScope.launch {
            val key = bookChatKey(context.bookUrl)
            val existingId = AppConfigStore.getString(key)
            val conversation = when {
                existingId != null && aiChatGateway.getConversation(existingId) != null -> existingId
                else -> {
                    val title = "《${context.bookName}》对话".take(40)
                    val created = aiChatGateway.createConversation(title).id
                    AppConfigStore.putString(key, created)
                    created
                }
            }
            selectConversation(conversation)
            if (!skillId.isNullOrBlank()) {
                // 只发一句提示，不贴 SKILL.md 正文；AI 按需调用 load_skill 加载完整指令。
                val label = skillName?.takeIf { it.isNotBlank() } ?: skillId
                sendMessage("请使用「$label」技能帮我处理当前这本书。")
            }
        }
    }

    private fun bookChatKey(bookUrl: String): String =
        "aiBookChat_" + bookUrl.hashCode().toUInt().toString(16)

    /**
     * 悬浮小窗「清理对话」：停掉正在生成的流，清空当前会话消息，
     * 但保留会话本身与书映射，界面停留在空会话，可立即继续提问。
     */
    private fun clearBookChatMessages() {
        val id = currentConversationId.value
        if (id.isNullOrBlank()) return
        streamingJob?.cancel()
        pendingToolRun = null
        viewModelScope.launch {
            runCatching { aiChatGateway.clearMessages(id) }
            _effects.tryEmit(AiChatEffect.ShowMessage("已清理当前对话"))
        }
    }

    /**
     * 悬浮小窗「新对话」：为当前书新建一个会话并把 bookUrl 映射指向它，
     * 这样重开小窗也进入新会话，不会回到旧的。
     */
    private fun newBookChat() {
        val context = _uiState.value.readingContext ?: return
        streamingJob?.cancel()
        pendingToolRun = null
        viewModelScope.launch {
            runCatching {
                val created = aiChatGateway.createConversation("《${context.bookName}》对话".take(40))
                AppConfigStore.putString(bookChatKey(context.bookUrl), created.id)
                selectConversation(created.id)
                _effects.tryEmit(AiChatEffect.ShowMessage("已开启新对话"))
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "新建对话失败"))
            }
        }
    }

    /** 把当前会话的消息导出为工作区产物，归到当前书名下，便于在「工作区」按书查找。 */
    private fun saveConversationToWorkspace() {
        val state = _uiState.value
        val context = state.readingContext ?: return
        val lines = lastMessages.joinToString("\n\n") { msg ->
            val who = if (msg.role == AiMessageRole.USER) "我" else "AI"
            "$who：${msg.content}"
        }.trim()
        if (lines.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                aiArtifactGateway.upsertArtifact(
                    AiArtifact(
                        id = "chat_" + (currentConversationId.value ?: now.toString()),
                        taskType = "chat_save",
                        bookUrl = context.bookUrl,
                        contentHash = lines.hashCode().toUInt().toString(16),
                        promptHash = "",
                        modelProfileId = "",
                        status = AiArtifact.STATUS_SUCCESS,
                        output = "对话记录 · ${context.bookName}\n\n$lines",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            refreshArtifacts()
            _effects.tryEmit(AiChatEffect.ShowMessage("已保存到《${context.bookName}》的工作区"))
        }
    }

    private suspend fun refreshArtifacts() {
        runCatching {
            val artifacts = aiArtifactGateway.observeAllArtifacts().first()
            _uiState.update { it.copy(workspaceArtifacts = artifacts.toImmutableList()) }
        }
    }

    /** 从技能入口进入：只发一句轻量提示（技能名），AI 通过内置 load_skill 工具按需加载正文。 */
    private fun applySkill(skillId: String) {
        // 重组会重复下发同一 intent，用 applying 标志确保只自动发送一次。
        if (!appliedSkillIds.add(skillId)) return
        viewModelScope.launch {
            val skill = aiProfileGateway.listSkills().firstOrNull { it.id == skillId } ?: return@launch
            _uiState.update { it.copy(activeSkillName = skill.name) }
            sendMessage("请使用「${skill.name}」技能帮我处理当前这本书。")
        }
    }

    private val appliedSkillIds = mutableSetOf<String>()

    // ---- Conversation lifecycle ----

    private fun observeConversations() {
        viewModelScope.launch {
            aiChatGateway.observeConversations().collect { conversations ->
                val selectedId = currentConversationId.value
                _uiState.update { current ->
                    val previousConversations = current.conversations.associateBy { it.id }
                    current.copy(
                        conversations = conversations.map {
                            val previous = previousConversations[it.id]
                            AiChatConversationUi(
                                id = it.id,
                                title = it.title,
                                updatedAt = it.updatedAt,
                                isSelected = it.id == selectedId,
                                providerName = previous?.providerName.orEmpty(),
                                modelName = previous?.modelName.orEmpty()
                            )
                        }.toImmutableList()
                    )
                }
                if (selectedId == null) {
                    conversations.firstOrNull()?.let { selectConversation(it.id) }
                        ?: createConversation()
                }
            }
        }
    }

    private fun observeCurrentMessages() {
        viewModelScope.launch {
            currentConversationId
                .filterNotNull()
                .flatMapLatest { aiChatGateway.observeSelectedMessages(it) }
                .collect { messages ->
                    lastMessages = messages.map { msg ->
                        val parts = AiMessagePartJson.decode(msg.partsJson)
                        AiChatMessageUi(
                            id = msg.id,
                            role = msg.role,
                            parts = parts.toImmutableList(),
                            content = parts.textContent(),
                            reasoning = parts.reasoningContent(),
                            toolTrace = parts.toolTraceText(),
                            createdAt = msg.createdAt,
                            thinkingDuration = msg.thinkingDuration,
                            bookResults = parts.bookResults().toImmutableList(),
                            branchIndex = msg.branchIndex,
                            totalBranches = 1, // will be updated below
                            parentMessageId = msg.parentMessageId
                        )
                    }
                    // Update totalBranches for assistant messages with parent (batch query)
                    val conversationId = currentConversationId.value
                    val branchCounts = if (conversationId != null) {
                        runCatching { aiChatGateway.getBranchCounts(conversationId) }.getOrNull()
                    } else null
                    lastMessages = lastMessages.map { msg ->
                        if (msg.role == AiMessageRole.ASSISTANT && msg.parentMessageId != null) {
                            val count = branchCounts?.get(msg.parentMessageId) ?: 1
                            msg.copy(totalBranches = count)
                        } else msg
                    }
                    _uiState.update { it.copy(messages = lastMessages.toImmutableList()) }
                }
        }
    }

    private fun createConversation() {
        viewModelScope.launch {
            runCatching {
                aiChatGateway.createConversation()
            }.onSuccess {
                selectConversation(it.id)
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to create chat"))
            }
        }
    }

    private fun selectConversation(id: String) {
        streamingJob?.cancel()
        pendingToolRun = null
        currentConversationId.value = id
        viewModelScope.launch {
            val conversation = aiChatGateway.getConversation(id)
            val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            val providerName = preset?.model?.provider?.name.orEmpty()
            val modelName = preset?.model?.displayName.orEmpty()

            _uiState.update { current ->
                current.copy(
                    currentConversationId = id,
                    reasoningLevel = conversation?.reasoningLevel?.let {
                        runCatching { AiReasoningLevel.valueOf(it.uppercase()) }.getOrNull()
                    } ?: current.reasoningLevel,
                    streamingMessage = null,
                    pendingToolConfirmation = null,
                    isSending = false,
                    conversations = current.conversations.map {
                        if (it.id == id) {
                            it.copy(
                                isSelected = true,
                                providerName = providerName,
                                modelName = modelName
                            )
                        } else {
                            it.copy(isSelected = false)
                        }
                    }.toImmutableList()
                )
            }
        }
    }

    // ---- Generation ----

    private fun stopGenerating() {
        streamingJob?.cancel()
    }

    private fun confirmPendingTool() {
        val pending = pendingToolRun ?: return
        pendingToolRun = null
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isSending = true, pendingToolConfirmation = null)
            }
            continueAfterToolApproval(pending)
        }
    }

    private fun rejectPendingTool() {
        val pending = pendingToolRun ?: return
        pendingToolRun = null
        val rejectionText = "工具调用已被你拒绝，我不会执行这些操作。还需要我继续帮你做什么？"
        val assistantText = buildString {
            pending.fullText.trim().takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(rejectionText)
        }
        val rejectedToolParts = pending.deniedToolParts()
        val reasoning = pending.fullReasoning.takeIf { it.isNotBlank() }
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = false,
                    pendingToolConfirmation = null,
                    streamingMessage = AiChatMessageUi(
                        id = "tool_rejected_temp",
                        role = AiMessageRole.ASSISTANT,
                        content = assistantText,
                        reasoning = reasoning,
                        toolTrace = null,
                        createdAt = System.currentTimeMillis()
                    ).withDisplayParts(rejectedToolParts)
                )
            }
            val saveResult = runCatching {
                pending.conversationId?.let { conversationId ->
                    aiChatGateway.saveMessage(
                        conversationId = conversationId,
                        role = AiMessageRole.ASSISTANT,
                        parts = buildList {
                            reasoning?.let { add(AiMessagePart.Reasoning(it)) }
                            add(AiMessagePart.Text(assistantText))
                            addAll(rejectedToolParts)
                        },
                        parentMessageId = pending.parentMessageId
                    )
                }
            }
            saveResult.onSuccess {
                _uiState.update { it.copy(streamingMessage = null) }
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to save rejected tool response"))
            }
            if (streamingJob == currentCoroutineContext()[Job]) {
                streamingJob = null
            }
        }
    }

    private fun updateReasoningLevel(level: AiReasoningLevel) {
        _uiState.update { it.copy(reasoningLevel = level) }
        val conversationId = currentConversationId.value ?: return
        viewModelScope.launch {
            aiChatGateway.updateReasoningLevel(conversationId, level.name.lowercase())
        }
    }

    private fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            aiChatGateway.deleteConversation(conversationId)
            if (currentConversationId.value == conversationId) {
                currentConversationId.value = null
                _uiState.update { it.copy(messages = persistentListOf(), currentConversationId = null) }
                createConversation()
            }
        }
    }

    private fun renameConversation(conversationId: String, title: String) {
        viewModelScope.launch {
            aiChatGateway.updateConversationTitle(conversationId, title)
        }
    }

    private suspend fun generateConversationTitle(conversationId: String, userContent: String) {
        // 标题直接取自第一条问题，不再额外请求 AI：复杂任务下那次请求常被限流，
        // 导致标题一直停在默认值。去掉 Markdown 前缀并按句读截断，保证标题稳定可读。
        val title = userContent
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("#")
            ?.trim()
            ?.take(40)
            ?.ifBlank { null }
            ?: userContent.take(24)
        aiChatGateway.updateConversationTitle(conversationId, title)
    }

    private fun regenerateMessage(messageId: String) {
        val message = lastMessages.find { it.id == messageId }
            ?: return
        if (message.role != AiMessageRole.ASSISTANT || message.parentMessageId == null) return
        val parentUserMsg = lastMessages.find { it.id == message.parentMessageId }
            ?: return
        val historyBeforeParent = lastMessages.filter { it.createdAt < parentUserMsg.createdAt }
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val conversationId = currentConversationId.value ?: return@launch
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            var failureMessage: String? = null
            var wasCancelled = false
            var waitingForToolConfirmation = false
            try {
                val state = _uiState.value
                val request = generationUseCase.buildRequest(
                    userContent = parentUserMsg.content,
                    history = historyBeforeParent,
                    reasoningLevel = state.reasoningLevel,
                    conversationId = conversationId,
                    readingContext = state.readingContext
                )
                setStreamingPlaceholder()
                try {
                    collectStream(request, fullText, fullReasoning, toolTrace)
                    waitingForToolConfirmation = continueToolRounds(
                        conversationId = conversationId,
                        request = request,
                        fullText = fullText,
                        fullReasoning = fullReasoning,
                        toolTrace = toolTrace,
                        startRound = 0,
                        assistantTextStart = 0,
                        parentMessageId = message.parentMessageId
                    )
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                } catch (e: Exception) {
                    failureMessage = "Stream interrupted: ${e.message ?: "unknown error"}"
                    _effects.tryEmit(AiChatEffect.ShowAiError(e.aiErrorKind(), e.message ?: failureMessage))
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } catch (e: Exception) {
                failureMessage = e.message ?: "AI chat failed"
                _effects.tryEmit(AiChatEffect.ShowAiError(e.aiErrorKind(), failureMessage))
            } finally {
                val assistantContent = when {
                    waitingForToolConfirmation -> null
                    fullText.isNotEmpty() -> fullText.toString()
                    !wasCancelled && !failureMessage.isNullOrBlank() -> "请求失败：$failureMessage"
                    else -> null
                }
                if (assistantContent != null) {
                    val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                    runCatching {
                        aiChatGateway.saveRegeneratedMessage(
                            conversationId = conversationId,
                            role = AiMessageRole.ASSISTANT,
                            parts = generationUseCase.buildAssistantParts(
                                text = assistantContent,
                                reasoning = fullReasoning.toString(),
                                toolTrace = toolTrace
                            ),
                            parentMessageId = message.parentMessageId,
                            thinkingDuration = duration
                        )
                    }
                }
                _uiState.update {
                    if (waitingForToolConfirmation) it
                    else it.copy(isSending = false, streamingMessage = null)
                }
                if (streamingJob == currentCoroutineContext()[Job]) {
                    streamingJob = null
                }
            }
        }
    }

    private fun switchBranch(messageId: String) {
        viewModelScope.launch {
            runCatching {
                aiChatGateway.selectBranch(messageId)
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to switch branch"))
            }
        }
    }

    private fun sendMessage(rawContent: String) {
        val content = rawContent.trim()
        if (content.isBlank() || _uiState.value.isSending) return
        streamingJob?.cancel()
        val historySnapshot = lastMessages
        streamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            var conversationIdForMsg: String? = null
            var parentMessageId: String? = null
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            var failureMessage: String? = null
            var wasCancelled = false
            var waitingForToolConfirmation = false
            try {
                val conversationId = currentConversationId.value
                    ?: aiChatGateway.createConversation().id.also {
                        currentConversationId.value = it
                    }
                val userMessage = aiChatGateway.saveMessage(
                    conversationId = conversationId,
                    role = AiMessageRole.USER,
                    parts = listOf(AiMessagePart.Text(content))
                )
                conversationIdForMsg = userMessage.conversationId
                parentMessageId = userMessage.id
                val state = _uiState.value
                val request = generationUseCase.buildRequest(
                    userContent = content,
                    history = historySnapshot,
                    reasoningLevel = state.reasoningLevel,
                    conversationId = conversationIdForMsg,
                    readingContext = state.readingContext
                )
                setStreamingPlaceholder()
                try {
                    collectStream(request, fullText, fullReasoning, toolTrace)
                    waitingForToolConfirmation = continueToolRounds(
                        conversationId = conversationIdForMsg,
                        request = request,
                        fullText = fullText,
                        fullReasoning = fullReasoning,
                        toolTrace = toolTrace,
                        startRound = 0,
                        assistantTextStart = 0,
                        parentMessageId = parentMessageId
                    )
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                } catch (e: Exception) {
                    failureMessage = "Stream interrupted: ${e.message ?: "unknown error"}"
                    _effects.tryEmit(AiChatEffect.ShowAiError(e.aiErrorKind(), e.message ?: failureMessage))
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } catch (e: Exception) {
                failureMessage = e.message ?: "AI chat failed"
                _effects.tryEmit(AiChatEffect.ShowAiError(e.aiErrorKind(), failureMessage))
            } finally {
                val assistantContent = when {
                    waitingForToolConfirmation -> null
                    fullText.isNotEmpty() -> fullText.toString()
                    !wasCancelled && !failureMessage.isNullOrBlank() -> "请求失败：$failureMessage"
                    else -> null
                }
                if (conversationIdForMsg != null && assistantContent != null) {
                    val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                    runCatching {
                        aiChatGateway.saveMessage(
                            conversationId = conversationIdForMsg,
                            role = AiMessageRole.ASSISTANT,
                            parts = generationUseCase.buildAssistantParts(
                                text = assistantContent,
                                reasoning = fullReasoning.toString(),
                                toolTrace = toolTrace
                            ),
                            parentMessageId = parentMessageId,
                            thinkingDuration = duration
                        )
                    }
                    // Generate AI title for first conversation
                    if (historySnapshot.none { it.role == AiMessageRole.ASSISTANT }) {
                        generateConversationTitle(conversationIdForMsg, content)
                    }
                }
                _uiState.update {
                    if (waitingForToolConfirmation) it
                    else it.copy(isSending = false, streamingMessage = null)
                }
                if (streamingJob == currentCoroutineContext()[Job]) {
                    streamingJob = null
                }
            }
        }
    }

    private suspend fun continueAfterToolApproval(pending: PendingToolRun) {
        val fullText = StringBuilder(pending.fullText)
        val fullReasoning = StringBuilder(pending.fullReasoning)
        var waitingForToolConfirmation = false
        try {
            val assistantContent = fullText.substring(pending.assistantTextStart)
            val followUpRequest = generationUseCase.executeToolCalls(
                request = pending.request,
                assistantContent = assistantContent,
                toolTrace = pending.toolTrace,
                toolCalls = pending.toolCalls,
                onToolTraceUpdate = { updateStreamingToolTrace(pending.toolTrace) }
            )
            val nextAssistantTextStart = fullText.length
            collectStream(followUpRequest, fullText, fullReasoning, pending.toolTrace)
            waitingForToolConfirmation = continueToolRounds(
                conversationId = pending.conversationId,
                request = followUpRequest,
                fullText = fullText,
                fullReasoning = fullReasoning,
                toolTrace = pending.toolTrace,
                startRound = pending.round + 1,
                assistantTextStart = nextAssistantTextStart,
                parentMessageId = pending.parentMessageId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "AI tool failed"))
        } finally {
            if (!waitingForToolConfirmation && pending.conversationId != null && fullText.isNotEmpty()) {
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                runCatching {
                    aiChatGateway.saveMessage(
                        conversationId = pending.conversationId,
                        role = AiMessageRole.ASSISTANT,
                        parts = generationUseCase.buildAssistantParts(
                            text = fullText.toString(),
                            reasoning = fullReasoning.toString(),
                            toolTrace = pending.toolTrace
                        ),
                        parentMessageId = pending.parentMessageId,
                        thinkingDuration = duration
                    )
                }
            }
            _uiState.update {
                if (waitingForToolConfirmation) it
                else it.copy(isSending = false, streamingMessage = null)
            }
            if (streamingJob == currentCoroutineContext()[Job]) {
                streamingJob = null
            }
        }
    }

    private suspend fun continueToolRounds(
        conversationId: String?,
        request: io.legado.app.domain.model.AiGenerateRequest,
        fullText: StringBuilder,
        fullReasoning: StringBuilder,
        toolTrace: ToolTraceBuilder,
        startRound: Int,
        assistantTextStart: Int,
        parentMessageId: String? = null
    ): Boolean {
        var currentRequest = request
        var currentRound = startRound
        var currentAssistantTextStart = assistantTextStart
        while (true) {
            val toolCalls = toolTrace.pendingToolCalls()
            if (toolCalls.isEmpty()) return false
            if (toolCalls.any { generationUseCase.requiresConfirmation(it.name) }) {
                pendingToolRun = PendingToolRun(
                    conversationId = conversationId,
                    request = currentRequest,
                    fullText = fullText.toString(),
                    fullReasoning = fullReasoning.toString(),
                    toolTrace = toolTrace,
                    toolCalls = toolCalls,
                    assistantTextStart = currentAssistantTextStart,
                    round = currentRound,
                    parentMessageId = parentMessageId
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        pendingToolConfirmation = AiToolConfirmationUi(
                            title = toolCalls.joinToString { call -> call.name },
                            description = toolTrace.toString().take(2000)
                        )
                    )
                }
                return true
            }
            currentRequest = generationUseCase.executeToolCalls(
                request = currentRequest,
                assistantContent = fullText.substring(currentAssistantTextStart),
                toolTrace = toolTrace,
                toolCalls = toolCalls,
                onToolTraceUpdate = { updateStreamingToolTrace(toolTrace) }
            )
            currentRound += 1
            currentAssistantTextStart = fullText.length
            collectStream(currentRequest, fullText, fullReasoning, toolTrace)
        }
    }

    // ---- Streaming helpers ----

    private suspend fun collectStream(
        request: io.legado.app.domain.model.AiGenerateRequest,
        fullText: StringBuilder,
        fullReasoning: StringBuilder,
        toolTrace: ToolTraceBuilder
    ) {
        generationUseCase.collectStream(
            request = request,
            toolTrace = toolTrace,
            onContent = { delta ->
                // First content token: freeze thinking duration
                if (fullText.isEmpty() && thinkingStartTime > 0) {
                    val duration = ((System.currentTimeMillis() - thinkingStartTime) / 1000).toInt()
                    _uiState.update { state ->
                        state.streamingMessage?.let { msg ->
                            state.copy(streamingMessage = msg.copy(thinkingDuration = duration))
                        } ?: state
                    }
                }
                fullText.append(delta)
                _uiState.update { state ->
                    state.streamingMessage?.let { msg ->
                        state.copy(
                            streamingMessage = msg.copy(content = fullText.toString())
                                .withDisplayParts()
                        )
                    } ?: state
                }
            },
            onReasoning = { delta ->
                // First reasoning token: record start time
                if (fullReasoning.isEmpty()) {
                    thinkingStartTime = System.currentTimeMillis()
                }
                fullReasoning.append(delta)
                _uiState.update { state ->
                    state.streamingMessage?.let { msg ->
                        state.copy(
                            streamingMessage = msg.copy(reasoning = fullReasoning.toString())
                                .withDisplayParts()
                        )
                    } ?: state
                }
            },
            onToolTraceUpdate = { updateStreamingToolTrace(toolTrace) }
        )
    }

    private fun setStreamingPlaceholder() {
        thinkingStartTime = 0L
        _uiState.update {
            it.copy(
                streamingMessage = AiChatMessageUi(
                    id = "streaming_temp",
                    role = AiMessageRole.ASSISTANT,
                    content = "",
                    reasoning = null,
                    toolTrace = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun updateStreamingToolTrace(toolTrace: ToolTraceBuilder) {
        _uiState.update { state ->
            state.streamingMessage?.let { msg ->
                state.copy(
                    streamingMessage = msg.copy(
                        toolTrace = toolTrace.toString()
                    ).withDisplayParts(toolTrace.toParts())
                )
            } ?: state
        }
    }

    private fun AiChatMessageUi.withDisplayParts(
        toolParts: List<AiMessagePart> = parts.filter {
            it is AiMessagePart.Tool || it is AiMessagePart.BookResult
        }
    ): AiChatMessageUi {
        return copy(
            parts = buildList {
                reasoning?.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Reasoning(it)) }
                content.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Text(it)) }
                addAll(toolParts)
            }.toImmutableList()
        )
    }

    private fun PendingToolRun.deniedToolParts(): List<AiMessagePart> {
        val deniedCallIds = toolCalls.mapTo(mutableSetOf()) { it.id }
        return toolTrace.toParts().map { part ->
            if (part is AiMessagePart.Tool &&
                part.toolCallId in deniedCallIds &&
                part.output.isBlank()
            ) {
                part.copy(
                    output = "用户拒绝执行此工具调用。",
                    approvalState = AiToolApprovalState.DENIED
                )
            } else {
                part
            }
        }
    }

    // ---- Display helpers ----

    private fun List<AiMessagePart>.bookResults(): List<AiChatBookResultUi> {
        val explicit = filterIsInstance<AiMessagePart.BookResult>().map {
            AiChatBookResultUi(
                bookUrl = it.bookUrl,
                name = it.name,
                author = it.author,
                origin = it.origin,
                coverPath = it.coverPath,
                latestChapterTitle = it.latestChapterTitle,
                currentChapterTitle = it.currentChapterTitle,
                intro = it.intro
            )
        }
        if (explicit.isNotEmpty()) return explicit.distinctBy { it.bookUrl }
        val books = linkedMapOf<String, AiChatBookResultUi>()
        toolParts().forEach { tool ->
            val root = runCatching {
                GSON.fromJson(tool.output, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultUi()?.let { books.putIfAbsent(it.bookUrl, it) }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultUi()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
        }
        return books.values.toList()
    }

    private fun List<AiMessagePart>.toolTraceText(): String? {
        val tools = toolParts()
        if (tools.isEmpty()) return null
        return tools.joinToString("\n\n") { tool ->
            buildString {
                append("Tool: ")
                append(tool.toolName.ifBlank { tool.rawType.ifBlank { tool.toolCallId } })
                append('\n')
                append("ID: ")
                append(tool.toolCallId)
                if (tool.input.isNotBlank()) {
                    append('\n')
                    append(tool.input)
                }
                if (tool.approvalState != AiToolApprovalState.AUTO) {
                    append('\n')
                    append("Approval: ")
                    append(tool.approvalState.name.lowercase())
                }
                if (tool.output.isNotBlank()) {
                    append('\n')
                    append("Result: ")
                    append(tool.output.take(2000))
                }
            }
        }.takeIf { it.isNotBlank() }
    }

}

private fun JsonObject.toBookResultUi(): AiChatBookResultUi? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return AiChatBookResultUi(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? =
    get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }

private fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
