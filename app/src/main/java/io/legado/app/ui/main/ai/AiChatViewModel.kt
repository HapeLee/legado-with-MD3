package io.legado.app.ui.main.ai

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.util.UUID

class AiChatViewModel : ViewModel() {

    private val pendingThinkingLabel = appCtx.getString(R.string.ai_restore_thinking)

    val messagesLiveData = MutableLiveData<List<AiChatMessage>>(emptyList())
    val requestingLiveData = MutableLiveData(false)
    var isRequesting = false
        private set

    private val messages = mutableListOf<AiChatMessage>()
    private var currentSessionId: String = AppConfig.aiCurrentChatSessionId ?: UUID.randomUUID().toString()

    companion object {
        private val requestScope = CoroutineScope(SupervisorJob() + IO)
        private var activeJob: Job? = null
        private var activeSessionId: String? = null
        private var activeViewModel: AiChatViewModel? = null
        private var activePendingContent: String = ""
        private var activeThinkingMessageId: String? = null
        private var activeThinkingKey: String? = null
        private var activeThinkingLabel: String? = null
        private var activePendingAssistantMessageId: String? = null
        private val activeToolMessageIds = linkedMapOf<String, String>()
        private val dataImageRegex = Regex("data:image/[^\\s\"')]+")
        private const val MAX_STORED_TEXT_CHARS = 20_000
        private const val MAX_STORED_STATUS_CHARS = 4_000
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
        activeSessionId = currentSessionId
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
        var updatedContextSummary = currentSessionSummary()
        activeJob = requestScope.launch {
            val result = runCatching {
                AiChatService.chatStream(
                    messages = requestMessages,
                    onPartial = { partial ->
                        activePendingContent = partial
                        targetFor(requestSessionId).upsertPendingAssistant(partial.ifBlank { "" })
                    },
                    onThinking = { thinking ->
                        targetFor(requestSessionId).upsertThinkingStatus(thinkingText, thinking)
                    },
                    onStatus = { status ->
                        targetFor(requestSessionId).upsertStatus(status)
                    },
                    contextSummary = updatedContextSummary,
                    onContextSummary = { summary ->
                        updatedContextSummary = summary
                    }
                )
            }
            targetFor(requestSessionId).setRequesting(false)
            activeJob = null
            activeSessionId = null
            result.onSuccess { content ->
                targetFor(requestSessionId).finishActiveThinking(removeIfBlank = true)
                activePendingContent = ""
                activeToolMessageIds.clear()
                updatedContextSummary?.let { targetFor(requestSessionId).saveContextSummary(requestSessionId, it) }
                targetFor(requestSessionId).replacePendingAssistant(content.ifBlank { pendingThinkingLabel })
            }.onFailure { throwable ->
                targetFor(requestSessionId).finishActiveThinking(fallback = throwable.localizedMessage)
                targetFor(requestSessionId).finishActiveTools(false, throwable.localizedMessage ?: throwable.javaClass.simpleName)
                activePendingContent = ""
                activeToolMessageIds.clear()
                if (throwable is CancellationException) {
                    targetFor(requestSessionId).replacePendingAssistant(cancelledText)
                    return@onFailure
                }
                val chatError = throwable as? AiChatException ?: AiChatException(
                    message = throwable.localizedMessage ?: throwable.javaClass.simpleName,
                    debugLog = throwable.stackTraceToString(),
                    cause = throwable
                )
                AppLog.put("AI 请求失败\n${chatError.debugLog}", chatError)
                targetFor(requestSessionId).failPendingAssistant(failureMessage(chatError.message))
            }
        }
    }

    fun stopRequest(cancelledText: String) {
        val job = activeJob ?: return
        job.cancel(CancellationException("User stopped generation"))
        activeJob = null
        activeSessionId = null
        activePendingContent = ""
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
        publish()
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
            publish()
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
        publish()
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
            publish()
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
            }
        }
        publish()
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
            AppConfig.aiChatSessionList.filterNot { it.id == currentSessionId }
        currentSessionId = UUID.randomUUID().toString()
        AppConfig.aiCurrentChatSessionId = currentSessionId
        publish(saveHistory = false)
    }

    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        AppConfig.aiCurrentChatSessionId = currentSessionId
        messages.clear()
        setRequesting(false)
        publish(saveHistory = false)
    }

    fun historySessions(): List<AiChatSession> {
        return AppConfig.aiChatSessionList.sortedByDescending { it.updatedAt }
    }

    fun loadSession(sessionId: String) {
        val session = AppConfig.aiChatSessionList.firstOrNull { it.id == sessionId } ?: return
        currentSessionId = session.id
        AppConfig.aiCurrentChatSessionId = session.id
        messages.clear()
        messages.addAll(session.messages.map { it.copy(pending = false) })
        setRequesting(activeJob?.isActive == true && activeSessionId == currentSessionId)
        publish(saveHistory = false)
    }

    fun deleteSession(sessionId: String) {
        AppConfig.aiChatSessionList = AppConfig.aiChatSessionList.filterNot { it.id == sessionId }
        if (currentSessionId == sessionId) {
            currentSessionId = UUID.randomUUID().toString()
            AppConfig.aiCurrentChatSessionId = currentSessionId
            messages.clear()
            setRequesting(false)
            publish(saveHistory = false)
        }
    }

    fun clearAllSessions() {
        AppConfig.aiChatSessionList = emptyList()
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

    fun currentContextSummary() = currentSessionSummary()

    fun restoreCurrentSession() {
        val sessions = AppConfig.aiChatSessionList
        val session = sessions.firstOrNull { it.id == currentSessionId } ?: sessions.firstOrNull()
        if (session != null) {
            currentSessionId = session.id
            AppConfig.aiCurrentChatSessionId = session.id
            messages.addAll(session.messages.map { it.copy(pending = false) })
        } else {
            AppConfig.aiCurrentChatSessionId = currentSessionId
        }
        val requesting = activeJob?.isActive == true && activeSessionId == currentSessionId
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

    private fun targetFor(sessionId: String): AiChatViewModel {
        return activeViewModel?.takeIf { it.currentSessionId == sessionId } ?: this
    }

    private fun publish(saveHistory: Boolean = true) {
        if (saveHistory) {
            saveCurrentSession()
        }
        messagesLiveData.postValue(messages.toList())
    }

    private fun saveCurrentSession() {
        val snapshot = messages.filterNot { it.pending }
            .map { sanitizeMessageForStorage(it) }
            .filter { it.content.isNotBlank() }
        val history = AppConfig.aiChatSessionList.toMutableList()
        val index = history.indexOfFirst { it.id == currentSessionId }
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
        AppConfig.aiChatSessionList.firstOrNull { it.id == currentSessionId }?.contextSummary

    fun saveContextSummary(sessionId: String, summary: io.legado.app.ui.main.ai.AiContextSummary) {
        if (!AppConfig.aiContextCompressionEnabled || !summary.isValid) return
        AppConfig.aiChatSessionList = AppConfig.aiChatSessionList.map { session ->
            if (session.id == sessionId) session.copy(contextSummary = summary) else session
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
