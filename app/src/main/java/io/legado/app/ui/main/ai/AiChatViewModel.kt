package io.legado.app.ui.main.ai

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.AiAgentJob
import io.legado.app.data.entities.AiAgentSession
import io.legado.app.data.entities.AiMemoryItem
import io.legado.app.help.ai.AiAgentStateStore
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.ai.AiMemoryContext
import io.legado.app.help.ai.AiTaskKeepAlive
import io.legado.app.help.ai.AiToolRegistry
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import splitties.init.appCtx
import java.util.UUID

class AiChatViewModel : ViewModel() {

    private val pendingThinkingLabel = appCtx.getString(R.string.ai_restore_thinking)

    val messagesLiveData = MutableLiveData<List<AiChatMessage>>(emptyList())
    val requestingLiveData = MutableLiveData(false)
    var isRequesting = false
        private set

    private val messages = mutableListOf<AiChatMessage>()
    private var currentCompanionId: String = AppConfig.aiCurrentChatCompanionId
    private var currentSessionId: String = AppConfig.aiCurrentChatSessionId ?: UUID.randomUUID().toString()
    private var windowSkillIds: Set<String> = emptySet()
    private var windowMcpServerIds: Set<String> = emptySet()
    private var lastTransientPublishAt: Long = 0L
    private var pendingTransientPublishJob: Job? = null

    companion object {
        private val requestScope = CoroutineScope(SupervisorJob() + IO)
        private var activeJob: Job? = null
        private var activeCompanionId: String? = null
        private var activeSessionId: String? = null
        private var activeViewModel: AiChatViewModel? = null
        private var activePendingContent: String = ""
        private var activeThinkingMessageId: String? = null
        private var activeThinkingKey: String? = null
        private var activeThinkingLabel: String? = null
        private var activePendingAssistantMessageId: String? = null
        private var activeAgentRun: AiAgentStateStore.Run? = null
        private val activeToolMessageIds = linkedMapOf<String, String>()
        private val dataImageRegex = Regex("data:image/[^\\s\"')]+")
        private const val MAX_STORED_TEXT_CHARS = 20_000
        private const val MAX_STORED_STATUS_CHARS = 4_000
        private const val TRANSIENT_UI_PUBLISH_INTERVAL_MS = 66L
    }

    init {
        restoreCurrentSession()
        activeViewModel = this
    }

    fun append(message: AiChatMessage) {
        messages.add(message)
        publish()
    }

    fun startRequest(
        userContent: String,
        thinkingText: String,
        cancelledText: String,
        failureMessage: (String) -> String
    ) {
        if (isRequesting || activeJob?.isActive == true) return
        setRequesting(true)
        activeCompanionId = currentCompanionId
        activeSessionId = currentSessionId
        val requestCompanionId = currentCompanionId
        val requestSessionId = currentSessionId
        activeViewModel = this
        activeThinkingMessageId = null
        activeThinkingKey = null
        activeThinkingLabel = null
        activePendingAssistantMessageId = null
        activeToolMessageIds.clear()
        append(AiChatMessage(role = AiChatMessage.Role.USER, content = userContent))
        activePendingContent = ""
        val requestMessages = snapshotForRequest()
        val activeSkills = activeWindowSkills()
        val activeMcpServerIds = windowMcpServerIds
        val companionExtraTools = if (currentCompanion().type == AiChatCompanionConfig.TYPE_CHARACTER) {
            AiToolRegistry.resolveNativeTools(AiToolRegistry.characterCompanionToolNames)
        } else {
            emptyList()
        }
        val agentRun = AiAgentStateStore.startRun(
            sessionId = requestSessionId,
            scope = AiAgentSession.SCOPE_CHAT,
            type = AiAgentJob.TYPE_CHAT,
            currentGoal = userContent,
            currentTask = "AI 回复生成",
            inputJson = JSONObject()
                .put("messageCount", requestMessages.size)
                .put("userContent", userContent.take(2_000))
                .put("companionId", requestCompanionId)
                .toString()
        )
        activeAgentRun = agentRun
        var updatedContextSummary = currentSessionSummary()
        val keepAliveId = AiTaskKeepAlive.retain(
            title = "AI回复生成中",
            content = userContent,
            kind = AiTaskKeepAlive.KIND_CHAT
        )
        activeJob = requestScope.launch {
            try {
                val result = runCatching {
                    AiChatService.chatStream(
                        messages = requestMessages,
                        onPartial = { partial ->
                            activePendingContent = partial
                            AiTaskKeepAlive.update(keepAliveId, content = partial)
                            targetFor(requestSessionId, requestCompanionId).upsertPendingAssistant(partial.ifBlank { "" })
                        },
                        onThinking = { thinking ->
                            AiTaskKeepAlive.update(keepAliveId, progressText = thinking)
                            targetFor(requestSessionId, requestCompanionId).upsertThinkingStatus(thinkingText, thinking)
                        },
                        onStatus = { status ->
                            AiTaskKeepAlive.update(
                                keepAliveId,
                                progressText = status.optString("label")
                                    .ifBlank { status.optString("name") }
                            )
                            targetFor(requestSessionId, requestCompanionId).upsertStatus(status)
                        },
                        contextSummary = updatedContextSummary,
                        onContextSummary = { summary ->
                            updatedContextSummary = summary
                        },
                        agentRun = agentRun,
                        memoryContext = AiMemoryContext(
                            scope = AiMemoryItem.SCOPE_GLOBAL,
                            sessionId = requestSessionId,
                            companionId = requestCompanionId,
                            title = "AI Chat"
                        ),
                        activeSkills = activeSkills,
                        extraTools = companionExtraTools + AiToolRegistry.resolveMcpTools(activeMcpServerIds)
                    )
                }
                targetFor(requestSessionId, requestCompanionId).setRequesting(false)
                activeJob = null
                activeCompanionId = null
                activeSessionId = null
                result.onSuccess { content ->
                    targetFor(requestSessionId, requestCompanionId).finishActiveThinking(removeIfBlank = true)
                    activePendingContent = ""
                    activeToolMessageIds.clear()
                    activeAgentRun = null
                    AiAgentStateStore.finish(
                        agentRun,
                        success = true,
                        outputJson = JSONObject().put("contentChars", content.length).toString()
                    )
                    updatedContextSummary?.let {
                        targetFor(requestSessionId, requestCompanionId).saveContextSummary(requestSessionId, it)
                    }
                    targetFor(requestSessionId, requestCompanionId).replacePendingAssistant(content.ifBlank { pendingThinkingLabel })
                }.onFailure { throwable ->
                    targetFor(requestSessionId, requestCompanionId).finishActiveThinking(fallback = throwable.localizedMessage)
                    targetFor(requestSessionId, requestCompanionId).finishActiveTools(false, throwable.localizedMessage ?: throwable.javaClass.simpleName)
                    activePendingContent = ""
                    activeToolMessageIds.clear()
                    if (throwable is CancellationException) {
                        activeAgentRun = null
                        AiAgentStateStore.cancel(agentRun, "User stopped generation")
                        targetFor(requestSessionId, requestCompanionId).replacePendingAssistant(cancelledText)
                        return@onFailure
                    }
                    val chatError = throwable as? AiChatException ?: AiChatException(
                        message = throwable.localizedMessage ?: throwable.javaClass.simpleName,
                        debugLog = throwable.stackTraceToString(),
                        cause = throwable
                    )
                    AppLog.put("AI 请求失败\n${chatError.debugLog}", chatError)
                    activeAgentRun = null
                    AiAgentStateStore.finish(agentRun, success = false, error = chatError.message)
                    targetFor(requestSessionId, requestCompanionId).failPendingAssistant(failureMessage(chatError.message))
                }
            } finally {
                AiTaskKeepAlive.release(keepAliveId)
            }
        }
    }

    fun stopRequest(cancelledText: String) {
        val job = activeJob ?: return
        job.cancel(CancellationException("User stopped generation"))
        activeJob = null
        activeCompanionId = null
        activeSessionId = null
        activePendingContent = ""
        AiAgentStateStore.cancel(activeAgentRun, "User stopped generation")
        activeAgentRun = null
        finishActiveThinking(fallback = cancelledText)
        finishActiveTools(false, cancelledText)
        activePendingAssistantMessageId = null
        activeToolMessageIds.clear()
        setRequesting(false)
        if (cancelledText.isNotBlank()) {
            replacePendingAssistant(cancelledText)
        }
    }

    fun replacePendingAssistant(content: String) {
        upsertPendingAssistant(content)
        finishPendingAssistant()
    }

    fun upsertPendingAssistant(content: String) {
        val messageId = activePendingAssistantMessageId
        val index = messageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (index >= 0) {
            messages[index] = messages[index].copy(content = content, pending = true)
        } else {
            val newMessage = AiChatMessage(
                role = AiChatMessage.Role.ASSISTANT,
                content = content,
                pending = true
            )
            activePendingAssistantMessageId = newMessage.id
            messages.add(newMessage)
        }
        publishTransient()
    }

    fun upsertThinkingStatus(thinkingTitle: String, thinking: String) {
        if (thinking.isBlank()) return
        val messageId = activeThinkingMessageId
            ?: createThinkingMessage(activeThinkingKey ?: "thinking", activeThinkingLabel ?: thinkingTitle)
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val current = messages[index].content
            val content = mergeThinkingContent(current, thinking)
            messages[index] = messages[index].copy(
                content = content,
                pending = true,
                collapsed = false,
                statusLabel = thinkingTitle,
                updatedAt = System.currentTimeMillis()
            )
            publishTransient()
        }
    }

    fun upsertStatus(status: org.json.JSONObject) {
        when (status.optString("kind")) {
            "thinking" -> upsertThinkingEvent(status)
            "tool" -> upsertToolEvent(status)
        }
    }

    private fun upsertThinkingEvent(status: org.json.JSONObject) {
        val stage = status.optString("stage")
        val key = status.optString("key").ifBlank { "thinking" }
        when (stage) {
            "start" -> {
                activeThinkingKey = key
                activeThinkingLabel = status.optString("label").ifBlank { pendingThinkingLabel }
            }
            "finish" -> {
                val content = status.optString("content")
                val label = status.optString("label").takeIf { it.isNotBlank() }
                if (activeThinkingMessageId == null && content.isNotBlank()) {
                    createThinkingMessage(key, label ?: activeThinkingLabel ?: pendingThinkingLabel)
                }
                finishActiveThinking(
                    fallback = status.optString("fallback"),
                    content = content,
                    removeIfBlank = status.optBoolean("removeIfBlank", false),
                    label = label
                )
            }
        }
    }

    private fun createThinkingMessage(key: String, label: String = pendingThinkingLabel): String {
        activeThinkingMessageId?.let { return it }
        val message = AiChatMessage(
            role = AiChatMessage.Role.ASSISTANT,
            content = "",
            pending = true,
            kind = AiChatMessage.Kind.THINKING,
            statusKey = key,
            statusLabel = label,
            collapsed = false
        )
        messages.add(message)
        activeThinkingMessageId = message.id
        publish(saveHistory = false)
        return message.id
    }

    fun finishActiveThinking(
        fallback: String? = null,
        content: String = "",
        removeIfBlank: Boolean = false,
        label: String? = null
    ) {
        val messageId = activeThinkingMessageId ?: run {
            activeThinkingKey = null
            activeThinkingLabel = null
            return
        }
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) {
            activeThinkingMessageId = null
            activeThinkingKey = null
            activeThinkingLabel = null
            return
        }
        val current = messages[index]
        val finalContent = content.takeIf { it.isNotBlank() }
            ?: current.content.takeIf { it.isNotBlank() }
            ?: fallback.orEmpty()
        if (finalContent.isBlank()) {
            messages.removeAt(index)
        } else {
            messages[index] = current.copy(
                content = finalContent,
                pending = false,
                collapsed = true,
                statusLabel = label ?: current.statusLabel,
                updatedAt = System.currentTimeMillis()
            )
        }
        activeThinkingMessageId = null
        activeThinkingKey = null
        activeThinkingLabel = null
        publish()
    }

    private fun upsertToolEvent(status: org.json.JSONObject) {
        val key = status.optString("key").ifBlank { status.optString("name").ifBlank { UUID.randomUUID().toString() } }
        val name = status.optString("name").ifBlank { appCtx.getString(R.string.ai_tool_default_name) }
        val stage = status.optString("stage")
        val content = status.optString("content")
        val messageId = activeToolMessageIds[key]
        if (stage == "call" || messageId == null) {
            val message = AiChatMessage(
                role = AiChatMessage.Role.ASSISTANT,
                content = content,
                pending = stage != "result",
                kind = AiChatMessage.Kind.TOOL,
                statusName = name,
                statusStage = stage,
                statusSuccess = status.optBoolean("success", true),
                statusLabel = status.optString("label"),
                statusDetail = content,
                statusKey = key,
                collapsed = false
            )
            messages.add(message)
            activeToolMessageIds[key] = message.id
            publish(saveHistory = stage == "result")
            return
        }
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val current = messages[index]
        val detail = buildString {
            current.statusDetail?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(content)
        }
        messages[index] = current.copy(
            content = content,
            pending = false,
            kind = AiChatMessage.Kind.TOOL,
            statusName = name,
            statusStage = stage,
            statusSuccess = status.optBoolean("success", true),
            statusLabel = status.optString("label"),
            statusDetail = detail,
            collapsed = true,
            updatedAt = System.currentTimeMillis()
        )
        publish()
    }

    private fun finishActiveTools(success: Boolean, label: String) {
        var changed = false
        activeToolMessageIds.values.forEach { id ->
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0 && messages[index].pending) {
                messages[index] = messages[index].copy(
                    pending = false,
                    statusSuccess = success,
                    statusLabel = label,
                    collapsed = true,
                    updatedAt = System.currentTimeMillis()
                )
                changed = true
            }
        }
        if (changed) {
            publish()
        }
    }

    fun finishPendingAssistant() {
        val messageId = activePendingAssistantMessageId
        val index = messageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (index >= 0) {
            messages[index] = messages[index].copy(pending = false)
            publish()
        }
        activePendingAssistantMessageId = null
    }

    fun failPendingAssistant(content: String) {
        val messageId = activePendingAssistantMessageId
        val index = messageId?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (index >= 0) {
            messages[index] = messages[index].copy(content = content, pending = false)
        } else {
            messages.add(AiChatMessage(role = AiChatMessage.Role.ASSISTANT, content = content))
        }
        activePendingAssistantMessageId = null
        publish()
    }

    fun clearCurrentSession() {
        messages.clear()
        AppConfig.aiChatSessionList =
            AppConfig.aiChatSessionList.filterNot {
                it.id == currentSessionId && it.companionId == currentCompanionId
            }
        currentSessionId = UUID.randomUUID().toString()
        AppConfig.aiCurrentChatSessionId = currentSessionId
        publish(saveHistory = false)
    }

    fun startNewSession() {
        startNewSession(currentCompanionId)
    }

    fun startNewSession(companionId: String) {
        if (!selectCompanionForSession(companionId)) return
        currentSessionId = UUID.randomUUID().toString()
        AppConfig.aiCurrentChatSessionId = currentSessionId
        messages.clear()
        setRequesting(false)
        publish(saveHistory = false)
    }

    fun historySessions(companionId: String = currentCompanionId): List<AiChatSession> {
        return sessionsForCompanion(companionId).sortedByDescending { it.updatedAt }
    }

    fun loadSession(sessionId: String) {
        loadSession(currentCompanionId, sessionId)
    }

    fun loadSession(companionId: String, sessionId: String): Boolean {
        if (!selectCompanionForSession(companionId)) return false
        val session = sessionsForCurrentCompanion().firstOrNull { it.id == sessionId } ?: return false
        currentSessionId = session.id
        AppConfig.aiCurrentChatSessionId = session.id
        messages.clear()
        messages.addAll(session.messages.map { it.copy(pending = false) })
        setRequesting(activeJob?.isActive == true &&
                activeSessionId == currentSessionId &&
                activeCompanionId == currentCompanionId)
        publish(saveHistory = false)
        return true
    }

    fun deleteSession(sessionId: String) {
        AppConfig.aiChatSessionList = AppConfig.aiChatSessionList.filterNot {
            it.id == sessionId && it.companionId == currentCompanionId
        }
        if (currentSessionId == sessionId) {
            currentSessionId = UUID.randomUUID().toString()
            AppConfig.aiCurrentChatSessionId = currentSessionId
            messages.clear()
            setRequesting(false)
            publish(saveHistory = false)
        }
    }

    fun clearAllSessions() {
        AppConfig.aiChatSessionList = AppConfig.aiChatSessionList
            .filterNot { it.companionId == currentCompanionId }
        currentSessionId = UUID.randomUUID().toString()
        AppConfig.aiCurrentChatSessionId = currentSessionId
        messages.clear()
        setRequesting(false)
        publish(saveHistory = false)
    }

    fun snapshotForRequest(): List<AiChatMessage> {
        return messages
            .filter { !it.pending && (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT }
            .map { it.copy(content = sanitizeImagePayloadsForRequest(it.content)) }
    }

    fun activeWindowSkillIds(): Set<String> = windowSkillIds

    fun setActiveWindowSkillIds(ids: Set<String>) {
        windowSkillIds = ids.filterTo(linkedSetOf()) { id ->
            AppConfig.aiSkillList.any { it.id == id }
        }
    }

    fun activeWindowMcpServerIds(): Set<String> = windowMcpServerIds

    fun setActiveWindowMcpServerIds(ids: Set<String>) {
        windowMcpServerIds = ids.filterTo(linkedSetOf()) { id ->
            AppConfig.aiMcpServerList.any { it.id == id && it.enabled }
        }
    }

    fun activeSessionId(): String = currentSessionId

    fun activeCompanionId(): String = currentCompanionId

    fun currentCompanion(): AiChatCompanionConfig =
        AppConfig.aiChatCompanionList.firstOrNull { it.id == currentCompanionId }
            ?: AppConfig.aiCurrentChatCompanion

    fun companions(): List<AiChatCompanionConfig> {
        val companions = AppConfig.aiChatCompanionList
        if (companions.size <= 1) return companions
        val lastSessionByCompanion = AppConfig.aiChatSessionList
            .groupBy { it.companionId.ifBlank { AiChatCompanionConfig.DEFAULT_COMPANION_ID } }
            .mapValues { entry -> entry.value.maxOfOrNull { it.updatedAt } ?: 0L }
        return companions.withIndex()
            .sortedWith(
                compareBy<IndexedValue<AiChatCompanionConfig>> {
                    if (it.value.id == AiChatCompanionConfig.DEFAULT_COMPANION_ID) 0 else 1
                }.thenByDescending {
                    lastSessionByCompanion[it.value.id] ?: Long.MIN_VALUE
                }.thenBy {
                    it.index
                }
            )
            .map { it.value }
    }

    fun switchCompanion(companionId: String): Boolean {
        if (isRequesting || activeJob?.isActive == true) return false
        if (!selectCompanionForSession(companionId)) return false
        currentSessionId = AppConfig.aiCurrentChatSessionId ?: UUID.randomUUID().toString()
        restoreCurrentSession()
        return true
    }

    fun currentContextSummary() = currentSessionSummary()

    fun restoreCurrentSession() {
        currentCompanionId = AppConfig.aiCurrentChatCompanionId
        messages.clear()
        val sessions = sessionsForCurrentCompanion()
        val storedSessionId = AppConfig.aiCurrentChatSessionId
        val session = sessions.firstOrNull { it.id == storedSessionId }
            ?: sessions.firstOrNull { it.id == currentSessionId }
            ?: sessions.firstOrNull()
        if (session != null) {
            currentSessionId = session.id
            AppConfig.aiCurrentChatSessionId = session.id
            messages.addAll(session.messages.map { it.copy(pending = false) })
        } else {
            currentSessionId = UUID.randomUUID().toString()
            AppConfig.aiCurrentChatSessionId = currentSessionId
        }
        val requesting = activeJob?.isActive == true &&
                activeSessionId == currentSessionId &&
                activeCompanionId == currentCompanionId
        if (requesting && messages.none { it.role == AiChatMessage.Role.ASSISTANT && it.pending }) {
            val restored = AiChatMessage(
                role = AiChatMessage.Role.ASSISTANT,
                content = activePendingContent.ifBlank { pendingThinkingLabel },
                pending = true
            )
            activePendingAssistantMessageId = restored.id
            messages.add(restored)
        }
        setRequesting(requesting)
        publish(saveHistory = false)
    }

    override fun onCleared() {
        super.onCleared()
        if (activeViewModel === this) {
            activeViewModel = null
        }
    }

    private fun setRequesting(value: Boolean) {
        isRequesting = value
        requestingLiveData.postValue(value)
    }

    private fun targetFor(sessionId: String, companionId: String): AiChatViewModel {
        return activeViewModel?.takeIf {
            it.currentSessionId == sessionId && it.currentCompanionId == companionId
        } ?: this
    }

    private fun publish(saveHistory: Boolean = true) {
        pendingTransientPublishJob?.cancel()
        pendingTransientPublishJob = null
        if (saveHistory) {
            saveCurrentSession()
        }
        messagesLiveData.postValue(messages.toList())
        lastTransientPublishAt = System.currentTimeMillis()
    }

    private fun publishTransient() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTransientPublishAt
        if (elapsed >= TRANSIENT_UI_PUBLISH_INTERVAL_MS) {
            pendingTransientPublishJob?.cancel()
            pendingTransientPublishJob = null
            messagesLiveData.postValue(messages.toList())
            lastTransientPublishAt = now
            return
        }
        if (pendingTransientPublishJob?.isActive == true) return
        pendingTransientPublishJob = requestScope.launch {
            delay(TRANSIENT_UI_PUBLISH_INTERVAL_MS - elapsed)
            messagesLiveData.postValue(messages.toList())
            lastTransientPublishAt = System.currentTimeMillis()
            pendingTransientPublishJob = null
        }
    }

    private fun saveCurrentSession() {
        val snapshot = messages.filterNot { it.pending }
            .map { sanitizeMessageForStorage(it) }
            .filter { it.content.isNotBlank() }
        val history = AppConfig.aiChatSessionList.toMutableList()
        val index = history.indexOfFirst {
            it.id == currentSessionId && it.companionId == currentCompanionId
        }
        if (snapshot.isEmpty()) {
            if (index >= 0) {
                history.removeAt(index)
                AppConfig.aiChatSessionList = history
            }
            return
        }
        val session = AiChatSession(
            id = currentSessionId,
            title = resolveSessionTitle(snapshot),
            companionId = currentCompanionId,
            updatedAt = System.currentTimeMillis(),
            messages = snapshot,
            contextSummary = currentSessionSummary()
        )
        if (index >= 0) {
            history[index] = session
        } else {
            history.add(0, session)
        }
        AppConfig.aiChatSessionList = history.sortedByDescending { it.updatedAt }
        AppConfig.aiCurrentChatSessionId = currentSessionId
    }

    private fun sanitizeMessageForStorage(message: AiChatMessage): AiChatMessage {
        val maxChars = when (message.kind ?: AiChatMessage.Kind.TEXT) {
            AiChatMessage.Kind.TEXT -> MAX_STORED_TEXT_CHARS
            AiChatMessage.Kind.STATUS,
            AiChatMessage.Kind.THINKING,
            AiChatMessage.Kind.TOOL -> MAX_STORED_STATUS_CHARS
        }
        return message.copy(
            content = sanitizeStoredText(message.content, maxChars),
            pending = false,
            statusDetail = message.statusDetail?.let { sanitizeStoredText(it, MAX_STORED_STATUS_CHARS) }
        )
    }

    private fun sanitizeStoredText(text: String, maxChars: Int): String {
        val clean = dataImageRegex.replace(text, "data:image/<stored-in-gallery>")
        return if (clean.length <= maxChars) {
            clean
        } else {
            clean.take(maxChars) + "\n...<truncated ${clean.length - maxChars} chars>"
        }
    }

    private fun resolveSessionTitle(messages: List<AiChatMessage>): String {
        val titleSource = messages.firstOrNull {
            it.role == AiChatMessage.Role.USER && (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT
        }?.content
            ?: messages.first().content
        return titleSource.replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .let {
                if (it.length > 24) "${it.take(24)}…" else it
            }
            .ifBlank { "AI Chat" }
    }

    private fun currentSessionSummary() =
        AppConfig.aiChatSessionList.firstOrNull {
            it.id == currentSessionId && it.companionId == currentCompanionId
        }?.contextSummary

    private fun sessionsForCurrentCompanion(): List<AiChatSession> {
        return sessionsForCompanion(currentCompanionId)
    }

    private fun sessionsForCompanion(companionId: String): List<AiChatSession> {
        val normalizedCompanionId = companionId.ifBlank { AiChatCompanionConfig.DEFAULT_COMPANION_ID }
        return AppConfig.aiChatSessionList.filter { session ->
            session.companionId.ifBlank { AiChatCompanionConfig.DEFAULT_COMPANION_ID } == normalizedCompanionId
        }
    }

    private fun selectCompanionForSession(companionId: String): Boolean {
        val target = AppConfig.aiChatCompanionList.firstOrNull {
            it.id == companionId && it.enabled
        } ?: return false
        currentCompanionId = target.id
        AppConfig.aiCurrentChatCompanionId = target.id
        return true
    }

    private fun activeWindowSkills(): List<AiSkillConfig> {
        if (windowSkillIds.isEmpty()) return emptyList()
        return AppConfig.aiSkillList.filter { it.id in windowSkillIds }
    }

    fun saveContextSummary(sessionId: String, summary: io.legado.app.ui.main.ai.AiContextSummary) {
        if (!AppConfig.aiContextCompressionEnabled || !summary.isValid) return
        AppConfig.aiChatSessionList = AppConfig.aiChatSessionList.map { session ->
            if (session.id == sessionId && session.companionId == currentCompanionId) {
                session.copy(contextSummary = summary)
            } else {
                session
            }
        }
    }

    private fun sanitizeImagePayloadsForRequest(content: String): String {
        if (!content.contains("data:image", ignoreCase = true)) return content
        return content.replace(dataImageRegex, "[image omitted]")
    }

    private fun mergeThinkingContent(current: String, incoming: String): String {
        if (incoming.isBlank()) return current
        if (current.isBlank()) return incoming
        if (current.endsWith(incoming)) return current
        if (incoming.startsWith(current)) return incoming
        return current + incoming
    }
}
