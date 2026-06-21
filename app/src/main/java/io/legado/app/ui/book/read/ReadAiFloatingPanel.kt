package io.legado.app.ui.book.read

import android.content.Intent
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import io.legado.app.R
import io.legado.app.data.entities.AiAgentJob
import io.legado.app.data.entities.AiAgentSession
import io.legado.app.data.entities.AiMemoryItem
import io.legado.app.help.ai.AiAgentStateStore
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.ai.AiMemoryContext
import io.legado.app.help.ai.AiMemoryStore
import io.legado.app.help.ai.AiTaskKeepAlive
import io.legado.app.help.ai.AiToolRegistry
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.config.AiWorldBookManageActivity
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiWorldBookBinding
import io.legado.app.ui.main.ai.compose.AiComposeMarkdownText
import io.legado.app.ui.main.ai.compose.AiComposeStyle
import io.legado.app.ui.main.ai.compose.AiCopyTextButton
import io.legado.app.ui.main.ai.compose.AiProcessStepType
import io.legado.app.ui.main.ai.compose.AiProcessStepUi
import io.legado.app.ui.main.ai.compose.AiProcessTimelineCard
import io.legado.app.ui.main.ai.compose.aiComposeStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ReadAiFloatingPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    data class ReadContext(
        val bookUrl: String,
        val bookName: String,
        val author: String,
        val sourceName: String,
        val chapterTitle: String,
        val chapterIndex: Int,
        val selectedText: String
    )

    data class Anchor(
        val centerX: Int,
        val topY: Int,
        val bottomY: Int
    )

    private val composeView = ComposeView(context)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private var readContext: ReadContext? = null
    private var currentSessionId: String = ""
    private var answerJob: Job? = null
    private var activeAgentRun: AiAgentStateStore.Run? = null
    private var streamingAssistantContent: String? = null
    private var streamingAssistantMessageId: String? = null
    private var activeThinkingMessageId: String? = null
    private var activeThinkingKey: String? = null
    private var activeThinkingLabel: String? = null
    private val activeToolMessageIds = linkedMapOf<String, String>()
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var imeBottomInset = 0
    private var savedWindowState: FloatingWindowState? = null

    private var messages by mutableStateOf<List<ReadAiMessage>>(emptyList())
    private var historySessions by mutableStateOf<List<ReadAiSession>>(emptyList())
    private var contextLabel by mutableStateOf("")
    private var showingHistory by mutableStateOf(false)
    private var requesting by mutableStateOf(false)
    private var modelLabel by mutableStateOf(currentModelLabel())
    private var fullscreen by mutableStateOf(false)
    private var windowSkillIds: Set<String> = emptySet()
    private var windowMcpServerIds: Set<String> = emptySet()

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
        composeView.setContent {
            ReadAiPanelContent(
                messages = messages,
                historySessions = historySessions,
                contextLabel = contextLabel,
                showingHistory = showingHistory,
                requesting = requesting,
                modelLabel = modelLabel,
                fullscreen = fullscreen,
                timeFormat = timeFormat,
                onTopDrag = ::handleDrag,
                onToggleFullscreen = ::toggleFullscreen,
                onSelectModel = ::selectModel,
                onOpenAbilities = ::showWindowAbilityDialog,
                onOpenSkills = ::showWindowSkillDialog,
                onOpenMcp = ::showWindowMcpDialog,
                onOpenWorldBooks = ::showReadAiWorldBookDialog,
                onNewChat = ::startNewChat,
                onToggleHistory = ::toggleHistory,
                onClose = ::close,
                onStop = ::stopAnswer,
                onSend = ::submitQuestion,
                onInputFocused = ::ensureAboveIme,
                onOpenSession = ::openHistorySession,
                onDeleteSession = ::deleteSession,
                onClearHistory = ::confirmClearHistory
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            updateImeBottomInset(insets)
            if (visibility == VISIBLE) {
                ensureInsideParent()
            }
            insets
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun attach(lifecycleOwner: LifecycleOwner) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun open(readContext: ReadContext, anchor: Anchor? = null) {
        this.readContext = readContext
        currentSessionId = ensureSession(readContext, createNew = false).id
        showingHistory = false
        modelLabel = currentModelLabel()
        contextLabel = buildContextLabel(readContext)
        renderCurrentSession()
        animate().cancel()
        translationY = 0f
        if (visibility != VISIBLE) {
            alpha = 0f
            visibility = VISIBLE
        } else {
            visibility = VISIBLE
        }
        bringToFront()
        doOnLayoutCompat {
            if (anchor != null && !fullscreen) {
                placeNearAnchor(anchor)
            }
            ensureInsideParent()
            if (alpha < 1f) {
                animate()
                    .alpha(1f)
                    .setDuration(160L)
                    .start()
            }
        }
        if (readContext.selectedText.isNotBlank()) {
            ask(readContext.selectedText)
        }
    }

    fun close() {
        if (fullscreen) {
            setFullscreenMode(false)
        }
        updateRequestingState()
        visibility = GONE
    }

    fun exitFullscreenIfNeeded(): Boolean {
        if (!fullscreen) return false
        setFullscreenMode(false)
        return true
    }

    private fun stopAnswer() {
        val context = readContext
        answerJob?.cancel()
        AiAgentStateStore.cancel(activeAgentRun, "User stopped read ai")
        activeAgentRun = null
        streamingAssistantContent = null
        streamingAssistantMessageId = null
        finishActiveProcessMessages(currentSessionId, success = false)
        if (context != null) {
            val pending = currentBookHistory(context).sessions
                .firstOrNull { it.id == currentSessionId }
                ?.messages
                ?.lastOrNull()
            if (pending?.role == ReadAiMessage.Role.ASSISTANT &&
                pending.content == resources.getString(R.string.ai_chat_thinking)
            ) {
                replaceMessage(context, pending.id, resources.getString(R.string.ai_chat_cancelled))
            }
        }
        updateRequestingState()
        if (!showingHistory) renderCurrentSession()
    }

    private fun startNewChat() {
        val context = readContext ?: return
        answerJob?.cancel()
        AiAgentStateStore.cancel(activeAgentRun, "Start new read ai chat")
        activeAgentRun = null
        streamingAssistantContent = null
        streamingAssistantMessageId = null
        finishActiveProcessMessages(currentSessionId, success = false)
        currentSessionId = ensureSession(context, createNew = true).id
        showingHistory = false
        contextLabel = buildContextLabel(context)
        renderCurrentSession()
    }

    private fun submitQuestion(question: String): Boolean {
        val content = question.trim()
        if (content.isBlank()) return false
        showingHistory = false
        ask(content)
        return true
    }

    private fun ask(question: String) {
        val context = readContext ?: return
        answerJob?.cancel()
        AiAgentStateStore.cancel(activeAgentRun, "Superseded by next read ai question")
        finishActiveProcessMessages(currentSessionId, success = false)
        val requestSessionId = currentSessionId
        appendMessage(context, ReadAiMessage.Role.USER, question)
        val pendingAssistantId = appendMessage(
            context,
            ReadAiMessage.Role.ASSISTANT,
            resources.getString(R.string.ai_chat_thinking)
        )
        val requestMessages = buildRequestMessages(context, question)
        val memoryContext = AiMemoryContext(
            scope = AiMemoryItem.SCOPE_BOOK,
            bookKey = AiMemoryStore.bookKey(context.bookName, context.author),
            sessionId = requestSessionId,
            companionId = READ_AI_COMPANION_ID,
            title = context.bookName.ifBlank { "阅读页问AI" }
        )
        val agentRun = AiAgentStateStore.startRun(
            sessionId = requestSessionId,
            scope = AiAgentSession.SCOPE_READ_AI,
            type = AiAgentJob.TYPE_READ_AI,
            currentGoal = question,
            currentTask = "阅读页问 AI",
            inputJson = JSONObject()
                .put("bookName", context.bookName)
                .put("author", context.author)
                .put("chapterTitle", context.chapterTitle)
                .put("chapterIndex", context.chapterIndex)
                .put("question", question.take(2_000))
                .toString()
        )
        activeAgentRun = agentRun
        streamingAssistantMessageId = pendingAssistantId
        val keepAliveId = AiTaskKeepAlive.retain(
            title = "阅读页问AI",
            content = "${context.bookName} · $question",
            kind = AiTaskKeepAlive.KIND_READ_AI
        )
        answerJob = requestScope.launch {
            try {
                post { updateRequestingState() }
                val result = runCatching {
                    withContext(IO) {
                        AiChatService.chatStream(
                            messages = requestMessages,
                            onPartial = { partial ->
                                if (partial.isNotBlank()) {
                                    AiTaskKeepAlive.update(keepAliveId, content = partial)
                                    post {
                                        streamingAssistantContent = partial
                                        if (!showingHistory) renderCurrentSession()
                                    }
                                }
                            },
                            onThinking = { thinking ->
                                if (thinking.isNotBlank()) {
                                    AiTaskKeepAlive.update(keepAliveId, progressText = thinking)
                                    post { upsertThinkingStatus(requestSessionId, thinking) }
                                }
                            },
                            onStatus = { status ->
                                post { upsertStatus(requestSessionId, status) }
                            },
                            includeStructuredBlocks = false,
                            toolOverride = AiToolRegistry.resolveReadTools(),
                            extraTools = AiToolRegistry.resolveMcpTools(windowMcpServerIds),
                            modelConfigOverride = AppConfig.aiAskModelConfig,
                            activeSkills = activeWindowSkills(),
                            agentRun = agentRun,
                            memoryContext = memoryContext
                        )
                    }
                }
                result.onSuccess { content ->
                    AiAgentStateStore.finish(
                        agentRun,
                        success = true,
                        outputJson = JSONObject().put("contentChars", content.length).toString()
                    )
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        AiAgentStateStore.cancel(agentRun, "Read ai cancelled")
                    } else {
                        AiAgentStateStore.finish(
                            agentRun,
                            success = false,
                            error = throwable.localizedMessage ?: throwable.javaClass.simpleName
                        )
                    }
                }
                post {
                    streamingAssistantContent = null
                    streamingAssistantMessageId = null
                    val content = result.fold(
                        onSuccess = { it.ifBlank { resources.getString(R.string.ai_chat_cancelled) } },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) {
                                resources.getString(R.string.ai_chat_cancelled)
                            } else {
                                resources.getString(
                                    R.string.ai_request_failed,
                                    throwable.localizedMessage
                                        ?: throwable.message
                                        ?: resources.getString(R.string.ai_request_cancelled)
                                )
                            }
                        }
                    )
                    replaceMessage(context, pendingAssistantId, content, requestSessionId)
                    finishActiveProcessMessages(requestSessionId, success = result.isSuccess)
                    answerJob = null
                    activeAgentRun = null
                    updateRequestingState()
                    if (!showingHistory) renderCurrentSession()
                }
            } finally {
                AiTaskKeepAlive.release(keepAliveId)
            }
        }
        updateRequestingState()
    }

    private fun renderCurrentSession() {
        val context = readContext ?: return
        val session = currentBookHistory(context).sessions.firstOrNull { it.id == currentSessionId }
        val sessionMessages = session?.messages.orEmpty()
        val displayMessages = streamingAssistantContent?.let { partial ->
            sessionMessages.dropLast(1) + (sessionMessages.lastOrNull()?.copy(content = partial)
                ?: ReadAiMessage(id = "read-ai-streaming", role = ReadAiMessage.Role.ASSISTANT, content = partial))
        } ?: sessionMessages
        messages = if (displayMessages.isEmpty()) {
            listOf(
                ReadAiMessage(
                    id = "read-ai-empty",
                    role = ReadAiMessage.Role.ASSISTANT,
                    content = resources.getString(R.string.ai_chat_empty)
                )
            )
        } else {
            displayMessages
        }
        updateRequestingState()
    }

    private fun toggleHistory() {
        showingHistory = !showingHistory
        if (showingHistory) {
            renderHistory()
        } else {
            renderCurrentSession()
        }
    }

    private fun renderHistory() {
        val context = readContext ?: return
        historySessions = currentBookHistory(context).sessions
    }

    private fun openHistorySession(sessionId: String) {
        val context = readContext ?: return
        currentSessionId = sessionId
        setCurrentSession(context, sessionId)
        showingHistory = false
        renderCurrentSession()
    }

    private fun ensureSession(context: ReadContext, createNew: Boolean): ReadAiSession {
        val history = currentBookHistory(context)
        if (!createNew) {
            val current = history.sessions.firstOrNull { it.id == history.currentSessionId }
                ?: history.sessions.firstOrNull()
            if (current != null) return current
        }
        val session = ReadAiSession(
            title = context.selectedText.lineSequence().firstOrNull()?.take(24).orEmpty()
                .ifBlank { resources.getString(R.string.ai_new_chat) },
            chapterTitle = context.chapterTitle,
            chapterIndex = context.chapterIndex
        )
        saveBookHistory(
            context,
            history.copy(
                updatedAt = System.currentTimeMillis(),
                currentSessionId = session.id,
                sessions = listOf(session) + history.sessions
            )
        )
        return session
    }

    private fun appendMessage(context: ReadContext, role: ReadAiMessage.Role, content: String): String {
        val message = ReadAiMessage(role = role, content = content)
        updateCurrentSession(context) { session ->
            val title = if (session.title.isBlank() && role == ReadAiMessage.Role.USER) {
                content.lineSequence().firstOrNull().orEmpty().take(24)
            } else {
                session.title
            }
            session.copy(
                title = title,
                updatedAt = System.currentTimeMillis(),
                messages = session.messages + message
            )
        }
        if (!showingHistory) renderCurrentSession()
        return message.id
    }

    private fun replaceMessage(
        context: ReadContext,
        messageId: String,
        content: String,
        sessionId: String = currentSessionId
    ) {
        updateSession(context, sessionId) { session ->
            session.copy(
                updatedAt = System.currentTimeMillis(),
                messages = session.messages.map {
                    if (it.id == messageId) it.copy(content = content) else it
                }
            )
        }
    }

    private fun deleteSession(sessionId: String) {
        val context = readContext ?: return
        val history = currentBookHistory(context)
        val sessions = history.sessions.filterNot { it.id == sessionId }
        if (sessions.isEmpty()) {
            AppConfig.aiReadHistoryList = AppConfig.aiReadHistoryList.filterNot { it.bookUrl == context.bookUrl }
            currentSessionId = ""
        } else {
            val nextId = if (currentSessionId == sessionId) sessions.first().id else currentSessionId
            currentSessionId = nextId
            saveBookHistory(
                context,
                history.copy(
                    updatedAt = System.currentTimeMillis(),
                    currentSessionId = nextId,
                    sessions = sessions
                )
            )
        }
        if (showingHistory) renderHistory() else renderCurrentSession()
    }

    private fun confirmClearHistory() {
        val context = readContext ?: return
        AlertDialog.Builder(this.context)
            .setMessage(R.string.ai_read_clear_history_confirm)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                AppConfig.aiReadHistoryList =
                    AppConfig.aiReadHistoryList.filterNot { it.bookUrl == context.bookUrl }
                currentSessionId = ""
                if (showingHistory) renderHistory() else renderCurrentSession()
            }
            .show()
    }

    private fun updateCurrentSession(context: ReadContext, mapper: (ReadAiSession) -> ReadAiSession) {
        updateSession(context, currentSessionId, mapper)
    }

    private fun updateSession(
        context: ReadContext,
        sessionId: String,
        mapper: (ReadAiSession) -> ReadAiSession
    ) {
        val history = currentBookHistory(context)
        val session = history.sessions.firstOrNull { it.id == sessionId }
            ?: ensureSession(context, createNew = false)
        val mapped = mapper(session)
        saveBookHistory(
            context,
            history.copy(
                updatedAt = System.currentTimeMillis(),
                currentSessionId = mapped.id,
                sessions = listOf(mapped) + history.sessions.filterNot { it.id == mapped.id }
            )
        )
    }

    private fun setCurrentSession(context: ReadContext, sessionId: String) {
        saveBookHistory(context, currentBookHistory(context).copy(currentSessionId = sessionId))
    }

    private fun currentBookHistory(context: ReadContext): ReadAiBookHistory {
        return AppConfig.aiReadHistoryList.firstOrNull { it.bookUrl == context.bookUrl }
            ?: ReadAiBookHistory(bookUrl = context.bookUrl, bookName = context.bookName)
    }

    private fun saveBookHistory(context: ReadContext, history: ReadAiBookHistory) {
        val list = AppConfig.aiReadHistoryList.toMutableList()
        val index = list.indexOfFirst { it.bookUrl == context.bookUrl }
        val normalized = history.copy(
            bookUrl = context.bookUrl,
            bookName = context.bookName,
            updatedAt = System.currentTimeMillis()
        )
        if (index >= 0) {
            list[index] = normalized
        } else {
            list.add(0, normalized)
        }
        AppConfig.aiReadHistoryList = list
        currentSessionId = normalized.currentSessionId
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        if (fullscreen) return false
        val parentView = parent as? ViewGroup ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = x
                startY = y
                parentView.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val targetX = startX + event.rawX - downRawX
                val targetY = startY + event.rawY - downRawY
                x = targetX.coerceIn(0f, max(0, parentView.width - width).toFloat())
                y = targetY.coerceIn(0f, maxPanelY(parentView))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ensureInsideParent()
                parentView.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun ensureInsideParent() {
        if (fullscreen) return
        val parentView = parent as? ViewGroup ?: return
        if (width <= 0 || height <= 0 || parentView.width <= 0 || parentView.height <= 0) return
        x = min(max(0f, x), max(0, parentView.width - width).toFloat())
        y = min(max(0f, y), maxPanelY(parentView))
    }

    private fun ensureAboveIme() {
        refreshImeBottomInset()
        ensureInsideParent()
        post {
            refreshImeBottomInset()
            ensureInsideParent()
        }
        postDelayed({
            refreshImeBottomInset()
            ensureInsideParent()
        }, 260L)
    }

    private fun maxPanelY(parentView: ViewGroup): Float {
        val margin = if (imeBottomInset > 0) 8.dpToPx() else 0
        return max(0, parentView.height - height - imeBottomInset - margin).toFloat()
    }

    private fun refreshImeBottomInset() {
        val insets = ViewCompat.getRootWindowInsets(this) ?: ViewCompat.getRootWindowInsets(rootView)
        if (insets != null) {
            updateImeBottomInset(insets)
        }
    }

    private fun updateImeBottomInset(insets: WindowInsetsCompat) {
        imeBottomInset = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            0
        }
    }

    private fun placeNearAnchor(anchor: Anchor) {
        if (fullscreen) return
        val parentView = parent as? ViewGroup ?: return
        if (width <= 0 || height <= 0 || parentView.width <= 0 || parentView.height <= 0) return
        val margin = 10.dpToPx()
        val preferredX = anchor.centerX - width / 2
        val maxX = (parentView.width - width - margin).coerceAtLeast(margin)
        x = preferredX.toFloat().coerceIn(margin.toFloat(), maxX.toFloat())
        val spaceAbove = anchor.topY - margin
        val spaceBelow = parentView.height - anchor.bottomY - margin
        y = if (spaceBelow >= height || spaceBelow >= spaceAbove) {
            (anchor.bottomY + margin).toFloat()
                .coerceAtMost((parentView.height - height - margin).toFloat())
        } else {
            (anchor.topY - height - margin).toFloat()
                .coerceAtLeast(margin.toFloat())
        }
    }

    private fun updateRequestingState() {
        requesting = answerJob?.isActive == true
    }

    private fun toggleFullscreen() {
        setFullscreenMode(!fullscreen)
    }

    private fun setFullscreenMode(enabled: Boolean) {
        if (fullscreen == enabled) return
        animate().cancel()
        translationY = 0f
        if (enabled) {
            savedWindowState = FloatingWindowState(
                width = layoutParams?.width ?: 320.dpToPx(),
                height = layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                x = x,
                y = y,
                composeWidth = composeView.layoutParams?.width ?: LayoutParams.MATCH_PARENT,
                composeHeight = composeView.layoutParams?.height ?: LayoutParams.WRAP_CONTENT
            )
            fullscreen = true
            x = 0f
            y = 0f
            updatePanelLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            updateComposeLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            bringToFront()
        } else {
            val state = savedWindowState
            fullscreen = false
            savedWindowState = null
            updatePanelLayout(
                state?.width ?: 320.dpToPx(),
                state?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            )
            updateComposeLayout(
                state?.composeWidth ?: LayoutParams.MATCH_PARENT,
                state?.composeHeight ?: LayoutParams.WRAP_CONTENT
            )
            x = state?.x ?: x
            y = state?.y ?: y
            post { ensureInsideParent() }
        }
        requestLayout()
    }

    private fun updatePanelLayout(width: Int, height: Int) {
        val params = layoutParams ?: ViewGroup.LayoutParams(width, height)
        params.width = width
        params.height = height
        layoutParams = params
    }

    private fun updateComposeLayout(width: Int, height: Int) {
        val params = composeView.layoutParams ?: LayoutParams(width, height)
        params.width = width
        params.height = height
        composeView.layoutParams = params
    }

    private fun currentModelLabel(): String {
        val model = AppConfig.aiAskModelConfig ?: return ""
        val providerName = AppConfig.aiProviderList.firstOrNull { it.id == model.providerId }
            ?.name
            ?.takeIf { it.isNotBlank() }
        return providerName?.let { "${model.modelId} - $it" } ?: model.modelId
    }

    private fun selectModel() {
        if (answerJob?.isActive == true) {
            context.toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        val models = AppConfig.aiModelConfigList
        if (models.isEmpty()) {
            context.toastOnUi(R.string.ai_no_models)
            return
        }
        val providerNameMap = AppConfig.aiProviderList.associateBy({ it.id }, { it.name })
        context.selector(
            context.getString(R.string.ai_current_model),
            models.map { model ->
                providerNameMap[model.providerId]?.takeIf { it.isNotBlank() }
                    ?.let { "${model.modelId} - $it" }
                    ?: model.modelId
            }
        ) { _, _, index ->
            AppConfig.aiAskModelId = models[index].id
            modelLabel = currentModelLabel()
        }
    }

    private fun showWindowAbilityDialog() {
        if (answerJob?.isActive == true) {
            context.toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        context.selector(
            "当前窗口能力",
            listOf(
                "新建对话",
                "历史记录",
                "Skill：${windowSkillIds.size} 个",
                "MCP：${windowMcpServerIds.size} 个",
                "世界书：${activeReadAiWorldBookCount()} 个",
                "清空 Skill/MCP"
            )
        ) { _, _, index ->
            when (index) {
                0 -> startNewChat()
                1 -> toggleHistory()
                2 -> showWindowSkillDialog()
                3 -> showWindowMcpDialog()
                4 -> showReadAiWorldBookDialog()
                5 -> {
                    windowSkillIds = emptySet()
                    windowMcpServerIds = emptySet()
                }
            }
        }
    }

    private fun openWorldBookManage() {
        context.startActivity(
            Intent(context, AiWorldBookManageActivity::class.java)
        )
    }

    private fun activeReadAiWorldBookCount(): Int {
        return AppConfig.aiWorldBookList.count { worldBook ->
            worldBook.enabled && worldBook.bindings.any { binding ->
                binding.enabled &&
                        binding.targetType == AiWorldBookBinding.TARGET_COMPANION &&
                        binding.targetKey == READ_AI_COMPANION_ID
            }
        }
    }

    private fun showReadAiWorldBookDialog() {
        val worldBooks = AppConfig.aiWorldBookList
        if (worldBooks.isEmpty()) {
            context.selector("世界书", listOf("打开世界书管理")) { _, _, _ ->
                openWorldBookManage()
            }
            return
        }
        val selected = worldBooks.filter { book ->
            book.bindings.any {
                it.enabled &&
                        it.targetType == AiWorldBookBinding.TARGET_COMPANION &&
                        it.targetKey == READ_AI_COMPANION_ID
            }
        }.mapTo(linkedSetOf()) { it.id }
        context.alert(title = "阅读页问 AI · 世界书") {
            multiChoiceItems(
                items = worldBooks.map { book ->
                    "${book.name}${if (book.enabled) "" else "（停用）"}"
                }.toTypedArray(),
                checkedItems = BooleanArray(worldBooks.size) { index -> worldBooks[index].id in selected }
            ) { _, which, isChecked ->
                if (isChecked) selected += worldBooks[which].id else selected -= worldBooks[which].id
            }
            okButton {
                AppConfig.aiWorldBookList = worldBooks.map { book ->
                    val withoutReadAi = book.bindings.filterNot {
                        it.targetType == AiWorldBookBinding.TARGET_COMPANION &&
                                it.targetKey == READ_AI_COMPANION_ID
                    }
                    if (book.id in selected) {
                        book.copy(
                            bindings = withoutReadAi + AiWorldBookBinding(
                                targetType = AiWorldBookBinding.TARGET_COMPANION,
                                targetKey = READ_AI_COMPANION_ID,
                                order = withoutReadAi.size
                            )
                        )
                    } else {
                        book.copy(bindings = withoutReadAi)
                    }
                }
            }
            neutralButton("管理") { openWorldBookManage() }
            cancelButton()
        }
    }

    private fun showWindowSkillDialog() {
        val skills = AppConfig.aiSkillList
        if (skills.isEmpty()) {
            context.toastOnUi("没有可用 Skill")
            return
        }
        val selected = windowSkillIds.toMutableSet()
        context.alert(title = "当前窗口 Skill") {
            multiChoiceItems(
                items = skills.map { skill -> skill.name.ifBlank { "Skill" } }.toTypedArray(),
                checkedItems = BooleanArray(skills.size) { index -> skills[index].id in selected }
            ) { _, which, isChecked ->
                if (isChecked) selected += skills[which].id else selected -= skills[which].id
            }
            okButton {
                windowSkillIds = selected.filterTo(linkedSetOf()) { id ->
                    AppConfig.aiSkillList.any { it.id == id }
                }
            }
            neutralButton("清空") {
                windowSkillIds = emptySet()
            }
            cancelButton()
        }
    }

    private fun showWindowMcpDialog() {
        val servers = AppConfig.aiMcpServerList.filter { it.enabled }
        if (servers.isEmpty()) {
            context.toastOnUi("没有已启用 MCP")
            return
        }
        val selected = windowMcpServerIds.toMutableSet()
        context.alert(title = "当前窗口 MCP") {
            multiChoiceItems(
                items = servers.map { server -> server.name.ifBlank { "MCP" } }.toTypedArray(),
                checkedItems = BooleanArray(servers.size) { index -> servers[index].id in selected }
            ) { _, which, isChecked ->
                if (isChecked) selected += servers[which].id else selected -= servers[which].id
            }
            okButton {
                windowMcpServerIds = selected.filterTo(linkedSetOf()) { id ->
                    AppConfig.aiMcpServerList.any { it.id == id && it.enabled }
                }
            }
            neutralButton("清空") {
                windowMcpServerIds = emptySet()
            }
            cancelButton()
        }
    }

    private fun activeWindowSkills() = AppConfig.aiSkillList.filter { it.id in windowSkillIds }

    private fun buildContextLabel(context: ReadContext): String {
        return context.bookName.ifBlank { resources.getString(R.string.book_name) }
    }

    private fun buildPrompt(context: ReadContext, question: String): String {
        return resources.getString(
            R.string.ai_read_prompt_template,
            context.bookName,
            context.author.ifBlank { resources.getString(R.string.unknown) },
            context.sourceName.ifBlank { resources.getString(R.string.unknown) },
            context.chapterTitle.ifBlank { resources.getString(R.string.unknown) },
            context.chapterIndex + 1,
            question,
            context.bookUrl
        )
    }

    private fun buildRequestMessages(context: ReadContext, question: String): List<AiChatMessage> {
        val historyMessages = currentBookHistory(context).sessions
            .firstOrNull { it.id == currentSessionId }
            ?.messages
            .orEmpty()
            .filterNot { it.isProcessMessage() }
            .dropLast(2)
            .takeLast(12)
            .mapNotNull { message ->
                val content = message.content.trim()
                if (content.isBlank()) return@mapNotNull null
                AiChatMessage(
                    role = when (message.role) {
                        ReadAiMessage.Role.USER -> AiChatMessage.Role.USER
                        ReadAiMessage.Role.ASSISTANT -> AiChatMessage.Role.ASSISTANT
                    },
                    content = content
                )
            }
        return historyMessages + AiChatMessage(
            role = AiChatMessage.Role.USER,
            content = buildPrompt(context, question)
        )
    }

    private fun doOnLayoutCompat(action: () -> Unit) {
        if (isLaidOut) {
            action()
        } else {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    removeOnLayoutChangeListener(this)
                    action()
                }
            })
        }
    }

    private fun upsertThinkingStatus(sessionId: String, thinking: String) {
        val context = readContext ?: return
        if (thinking.isBlank()) return
        val messageId = activeThinkingMessageId
            ?: createThinkingMessage(context, sessionId, activeThinkingKey ?: "thinking", activeThinkingLabel)
        updateSession(context, sessionId) { session ->
            session.copy(
                updatedAt = System.currentTimeMillis(),
                messages = session.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(
                            content = mergeThinkingContent(message.content, thinking),
                            pending = true,
                            collapsed = false,
                            statusLabel = resources.getString(R.string.ai_chat_thinking),
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        message
                    }
                }
            )
        }
        if (!showingHistory) renderCurrentSession()
    }

    private fun upsertStatus(sessionId: String, status: JSONObject) {
        when (status.optString("kind")) {
            "thinking" -> upsertThinkingEvent(sessionId, status)
            "tool" -> upsertToolEvent(sessionId, status)
        }
    }

    private fun upsertThinkingEvent(sessionId: String, status: JSONObject) {
        val key = status.optString("key").ifBlank { "thinking" }
        when (status.optString("stage")) {
            "start" -> {
                activeThinkingKey = key
                activeThinkingLabel = status.optString("label")
                    .ifBlank { resources.getString(R.string.ai_chat_thinking) }
            }
            "finish" -> {
                val content = status.optString("content")
                val label = status.optString("label").ifBlank {
                    resources.getString(R.string.ai_chat_thinking_done)
                }
                if (activeThinkingMessageId == null && content.isNotBlank()) {
                    createThinkingMessage(readContext ?: return, sessionId, key, label)
                }
                finishActiveThinking(
                    sessionId = sessionId,
                    fallback = status.optString("fallback"),
                    content = content,
                    removeIfBlank = status.optBoolean("removeIfBlank", false),
                    label = label
                )
            }
        }
    }

    private fun createThinkingMessage(
        context: ReadContext,
        sessionId: String,
        key: String,
        label: String?
    ): String {
        activeThinkingMessageId?.let { return it }
        val message = ReadAiMessage(
            role = ReadAiMessage.Role.ASSISTANT,
            content = "",
            pending = true,
            kind = ReadAiMessage.Kind.THINKING,
            statusKey = key,
            statusLabel = label?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.ai_chat_thinking),
            collapsed = false
        )
        activeThinkingMessageId = message.id
        insertProcessMessageBeforePendingAnswer(context, sessionId, message)
        return message.id
    }

    private fun finishActiveThinking(
        sessionId: String,
        fallback: String? = null,
        content: String = "",
        removeIfBlank: Boolean = false,
        label: String? = null
    ) {
        val context = readContext ?: return
        val messageId = activeThinkingMessageId ?: run {
            activeThinkingKey = null
            activeThinkingLabel = null
            return
        }
        updateSession(context, sessionId) { session ->
            val current = session.messages.firstOrNull { it.id == messageId }
            val finalContent = content.takeIf { it.isNotBlank() }
                ?: current?.content?.takeIf { it.isNotBlank() }
                ?: fallback.orEmpty()
            val mapped = if (removeIfBlank && finalContent.isBlank()) {
                session.messages.filterNot { it.id == messageId }
            } else {
                session.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(
                            content = finalContent,
                            pending = false,
                            collapsed = true,
                            statusLabel = label ?: message.statusLabel,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        message
                    }
                }
            }
            session.copy(updatedAt = System.currentTimeMillis(), messages = mapped)
        }
        activeThinkingMessageId = null
        activeThinkingKey = null
        activeThinkingLabel = null
        if (!showingHistory) renderCurrentSession()
    }

    private fun upsertToolEvent(sessionId: String, status: JSONObject) {
        val context = readContext ?: return
        val key = status.optString("key").ifBlank { status.optString("name").ifBlank { "tool" } }
        val name = status.optString("name").ifBlank { resources.getString(R.string.ai_tool_default_name) }
        val stage = status.optString("stage")
        val content = status.optString("content")
        val existingId = activeToolMessageIds[key]
        if (stage == "call" || existingId == null) {
            val message = ReadAiMessage(
                role = ReadAiMessage.Role.ASSISTANT,
                content = content,
                pending = stage != "result",
                kind = ReadAiMessage.Kind.TOOL,
                statusName = name,
                statusStage = stage,
                statusSuccess = status.optBoolean("success", true),
                statusLabel = status.optString("label"),
                statusDetail = content,
                statusKey = key,
                collapsed = stage == "result"
            )
            activeToolMessageIds[key] = message.id
            insertProcessMessageBeforePendingAnswer(context, sessionId, message)
            if (!showingHistory) renderCurrentSession()
            return
        }
        updateSession(context, sessionId) { session ->
            session.copy(
                updatedAt = System.currentTimeMillis(),
                messages = session.messages.map { message ->
                    if (message.id == existingId) {
                        val detail = buildString {
                            message.statusDetail?.takeIf { it.isNotBlank() }?.let {
                                append(it)
                                append("\n\n")
                            }
                            append(content)
                        }
                        message.copy(
                            content = content,
                            pending = false,
                            kind = ReadAiMessage.Kind.TOOL,
                            statusName = name,
                            statusStage = stage,
                            statusSuccess = status.optBoolean("success", true),
                            statusLabel = status.optString("label"),
                            statusDetail = detail,
                            collapsed = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        message
                    }
                }
            )
        }
        if (!showingHistory) renderCurrentSession()
    }

    private fun insertProcessMessageBeforePendingAnswer(
        context: ReadContext,
        sessionId: String,
        message: ReadAiMessage
    ) {
        updateSession(context, sessionId) { session ->
            val pendingAnswerId = streamingAssistantMessageId
            val index = pendingAnswerId?.let { id -> session.messages.indexOfFirst { it.id == id } } ?: -1
            val nextMessages = if (index >= 0) {
                session.messages.take(index) + message + session.messages.drop(index)
            } else {
                session.messages + message
            }
            session.copy(updatedAt = System.currentTimeMillis(), messages = nextMessages)
        }
    }

    private fun finishActiveProcessMessages(sessionId: String, success: Boolean) {
        val context = readContext ?: return
        updateSession(context, sessionId) { session ->
            session.copy(
                updatedAt = System.currentTimeMillis(),
                messages = session.messages.map { message ->
                    if (message.pending && message.isProcessMessage()) {
                        message.copy(
                            pending = false,
                            statusSuccess = if (message.kind == ReadAiMessage.Kind.TOOL) {
                                success
                            } else {
                                message.statusSuccess
                            },
                            collapsed = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        message
                    }
                }
            )
        }
        clearActiveProcessMessages()
    }

    private fun clearActiveProcessMessages() {
        activeThinkingMessageId = null
        activeThinkingKey = null
        activeThinkingLabel = null
        activeToolMessageIds.clear()
    }

    companion object {
        private val requestScope = CoroutineScope(SupervisorJob() + IO)
        private const val READ_AI_COMPANION_ID = "read_ai"
    }

    private data class FloatingWindowState(
        val width: Int,
        val height: Int,
        val x: Float,
        val y: Float,
        val composeWidth: Int,
        val composeHeight: Int
    )
}
@Composable
private fun ReadAiPanelContent(
    messages: List<ReadAiMessage>,
    historySessions: List<ReadAiSession>,
    contextLabel: String,
    showingHistory: Boolean,
    requesting: Boolean,
    modelLabel: String,
    fullscreen: Boolean,
    timeFormat: SimpleDateFormat,
    onTopDrag: (MotionEvent) -> Boolean,
    onToggleFullscreen: () -> Unit,
    onSelectModel: () -> Unit,
    onOpenAbilities: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenWorldBooks: () -> Unit,
    onNewChat: () -> Unit,
    onToggleHistory: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Boolean,
    onInputFocused: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val style = aiComposeStyle(context)
    var menuExpanded by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(if (fullscreen) 0.dp else 20.dp)
    val panelModifier = if (fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth()
    }
    Surface(
        modifier = panelModifier.clip(panelShape),
        shape = panelShape,
        color = style.colors.background,
        shadowElevation = 0.dp,
        border = if (fullscreen) null else BorderStroke(style.metrics.strokeWidth, style.colors.stroke)
    ) {
        Column(
            modifier = Modifier
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier)
                .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
                .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier)
                .then(if (fullscreen) Modifier.imePadding() else Modifier)
                .padding(
                    start = if (fullscreen) 16.dp else 12.dp,
                    top = if (fullscreen) 12.dp else 10.dp,
                    end = if (fullscreen) 16.dp else 12.dp,
                    bottom = if (fullscreen) 14.dp else 12.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(
                            if (fullscreen) Modifier else Modifier.pointerInteropFilter(
                                onTouchEvent = onTopDrag
                            )
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.ask_ai),
                        color = style.colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contextLabel,
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (modelLabel.isNotBlank()) {
                    Surface(
                        onClick = onSelectModel,
                        enabled = !requesting,
                        shape = RoundedCornerShape(style.metrics.chipRadius),
                        color = style.colors.accent.copy(alpha = if (requesting) 0.06f else 0.10f)
                    ) {
                        Text(
                            text = modelLabel,
                            color = if (requesting) style.colors.secondaryText else style.colors.accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 118.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                ReadAiIconButton(
                    iconRes = if (fullscreen) R.drawable.ic_exit else R.drawable.ic_fullscreen,
                    contentDescriptionRes = if (fullscreen) R.string.exit else R.string.full_screen,
                    style = style,
                    onClick = onToggleFullscreen
                )
                Box {
                    ReadAiIconButton(R.drawable.ic_settings, R.string.menu, style) {
                        menuExpanded = true
                    }
                    if (menuExpanded) {
                        ReadAiTopMenu(
                            style = style,
                            actions = buildList {
                                add(ReadAiMenuAction(stringResource(R.string.ai_new_chat), onNewChat))
                                add(ReadAiMenuAction(stringResource(R.string.ai_chat_history), onToggleHistory))
                                add(ReadAiMenuAction("Skill", onOpenSkills))
                                add(ReadAiMenuAction("MCP", onOpenMcp))
                                add(ReadAiMenuAction("世界书", onOpenWorldBooks))
                                add(ReadAiMenuAction("窗口能力", onOpenAbilities))
                                add(ReadAiMenuAction(stringResource(R.string.ai_current_model), onSelectModel))
                            },
                            onDismiss = { menuExpanded = false }
                        )
                    }
                }
                ReadAiIconButton(R.drawable.ic_close_x, R.string.close, style, onClose)
            }
            Box(
                modifier = if (fullscreen) Modifier.weight(1f) else Modifier.height(if (showingHistory) 300.dp else 340.dp)
            ) {
                if (showingHistory) {
                    ReadAiHistoryList(
                        sessions = historySessions,
                        style = style,
                        timeFormat = timeFormat,
                        onOpenSession = onOpenSession,
                        onDeleteSession = onDeleteSession,
                        onClearHistory = onClearHistory,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    )
                } else {
                    ReadAiMessageList(
                        messages = messages,
                        requesting = requesting,
                        style = style,
                        fullscreen = fullscreen,
                        showProcessChain = AppConfig.aiThinkingToolbarEnabled,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    )
                }
                ReadAiComposer(
                    requesting = requesting,
                    enterToSend = AppConfig.aiEnterToSend,
                    style = style,
                    onStop = onStop,
                    onSend = onSend,
                    onInputFocused = onInputFocused,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = if (fullscreen) 2.dp else 0.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadAiIconButton(
    iconRes: Int,
    contentDescriptionRes: Int,
    style: AiComposeStyle,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(contentDescriptionRes),
                tint = style.colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Immutable
private data class ReadAiMenuAction(
    val title: String,
    val invoke: () -> Unit
)

@Composable
private fun ReadAiTopMenu(
    style: AiComposeStyle,
    actions: List<ReadAiMenuAction>,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 44),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.composerSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(min = 132.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                actions.forEach { action ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 132.dp)
                            .clip(RoundedCornerShape(style.metrics.chipRadius))
                            .clickable {
                                onDismiss()
                                action.invoke()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = action.title,
                            color = style.colors.primaryText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadAiMessageList(
    messages: List<ReadAiMessage>,
    requesting: Boolean,
    style: AiComposeStyle,
    fullscreen: Boolean,
    showProcessChain: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val listState = rememberLazyListState()
    var stickToBottom by remember { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isEmpty() || lastVisible >= messages.lastIndex
        }
    }
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (isAtBottom) {
            stickToBottom = true
        } else if (listState.isScrollInProgress) {
            stickToBottom = false
        }
    }
    val uiItems = remember(messages, showProcessChain) {
        buildReadAiMessageItems(messages, showProcessChain)
    }
    LaunchedEffect(uiItems.size, uiItems.lastOrNull()?.id, messages.lastOrNull()?.content, requesting) {
        if (uiItems.isNotEmpty() && stickToBottom && !listState.isScrollInProgress) {
            listState.scrollToItem(uiItems.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(uiItems, key = { it.id }) { item ->
            when (item) {
                is ReadAiMessageItem.Message -> ReadAiMessageRow(
                    message = item.message,
                    streaming = requesting && item.message == messages.lastOrNull(),
                    style = style,
                    fullscreen = fullscreen
                )
                is ReadAiMessageItem.Process -> ReadAiProcessRow(
                    steps = item.steps,
                    style = style,
                    fullscreen = fullscreen
                )
            }
        }
    }
}

@Immutable
private sealed class ReadAiMessageItem {
    abstract val id: String

    data class Message(val message: ReadAiMessage) : ReadAiMessageItem() {
        override val id: String = message.id
    }

    data class Process(
        override val id: String,
        val steps: List<AiProcessStepUi>
    ) : ReadAiMessageItem()
}

private fun buildReadAiMessageItems(
    messages: List<ReadAiMessage>,
    showProcessChain: Boolean
): List<ReadAiMessageItem> {
    val result = mutableListOf<ReadAiMessageItem>()
    val processBuffer = mutableListOf<ReadAiMessage>()

    fun flushProcess() {
        if (processBuffer.isNotEmpty()) {
            if (showProcessChain) {
                val steps = processBuffer.map { it.toProcessStep() }
                result += ReadAiMessageItem.Process(
                    id = "process-${processBuffer.first().id}",
                    steps = steps
                )
            }
            processBuffer.clear()
        }
    }

    messages.forEach { message ->
        if (message.isProcessMessage()) {
            processBuffer += message
        } else {
            flushProcess()
            result += ReadAiMessageItem.Message(message)
        }
    }
    flushProcess()
    return result
}

@Composable
private fun ReadAiProcessRow(
    steps: List<AiProcessStepUi>,
    style: AiComposeStyle,
    fullscreen: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        AiProcessTimelineCard(
            steps = steps,
            style = style,
            modifier = Modifier.widthIn(min = 72.dp, max = if (fullscreen) 640.dp else 280.dp)
        )
    }
}

@Composable
private fun ReadAiMessageRow(
    message: ReadAiMessage,
    streaming: Boolean,
    style: AiComposeStyle,
    fullscreen: Boolean
) {
    val isUser = message.role == ReadAiMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 72.dp, max = if (fullscreen) 640.dp else 280.dp),
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = if (isUser) style.colors.userBubble else style.colors.composerSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                AiComposeMarkdownText(
                    content = message.content,
                    style = style,
                    color = if (isUser) style.colors.userText else style.colors.primaryText
                )
                if (!streaming && message.content.isNotBlank()) {
                    AiCopyTextButton(
                        text = message.content,
                        style = style,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

private fun ReadAiMessage.isProcessMessage(): Boolean {
    return when (kind ?: ReadAiMessage.Kind.TEXT) {
        ReadAiMessage.Kind.THINKING,
        ReadAiMessage.Kind.TOOL -> true
        else -> false
    }
}

private fun ReadAiMessage.toProcessStep(): AiProcessStepUi {
    val messageKind = kind ?: ReadAiMessage.Kind.TEXT
    val detail = statusDetail?.takeIf { it.isNotBlank() } ?: content
    return when (messageKind) {
        ReadAiMessage.Kind.TOOL -> AiProcessStepUi(
            id = id,
            type = AiProcessStepType.Tool,
            title = statusName?.takeIf { it.isNotBlank() }
                ?: statusLabel?.takeIf { it.isNotBlank() }
                ?: "工具调用",
            subtitle = summarizeReadAiProcessDetail(
                detail,
                statusLabel?.takeIf { it.isNotBlank() } ?: if (pending) "调用中" else "已完成"
            ),
            detail = detail,
            pending = pending,
            success = statusSuccess,
            collapsed = collapsed,
            payload = null
        )
        else -> AiProcessStepUi(
            id = id,
            type = AiProcessStepType.Thinking,
            title = statusLabel?.takeIf { it.isNotBlank() } ?: if (pending) "思考中" else "思考完成",
            subtitle = summarizeReadAiProcessDetail(detail, if (pending) "思考中" else "思考完成"),
            detail = detail,
            pending = pending,
            success = true,
            collapsed = collapsed,
            payload = null
        )
    }
}

private fun summarizeReadAiProcessDetail(raw: String, fallback: String): String {
    return raw
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .ifBlank { fallback }
        .let { if (it.length > 96) "${it.take(96)}..." else it }
}

private fun mergeThinkingContent(current: String, incoming: String): String {
    if (incoming.isBlank()) return current
    if (current.isBlank()) return incoming
    if (current.endsWith(incoming)) return current
    if (incoming.startsWith(current)) return incoming
    return current + incoming
}

@Composable
private fun ReadAiHistoryList(
    sessions: List<ReadAiSession>,
    style: AiComposeStyle,
    timeFormat: SimpleDateFormat,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.ai_read_history_empty),
                color = style.colors.secondaryText,
                fontSize = 13.sp
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sessions, key = { it.id }) { session ->
            Surface(
                onClick = { onOpenSession(session.id) },
                shape = RoundedCornerShape(style.metrics.cardRadius),
                color = style.colors.cardSurface,
                border = BorderStroke(style.metrics.strokeWidth, style.colors.stroke)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title.ifBlank { stringResource(R.string.ai_new_chat) },
                            color = style.colors.primaryText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                if (session.chapterTitle.isNotBlank()) {
                                    append(session.chapterTitle).append(" - ")
                                }
                                append(timeFormat.format(Date(session.updatedAt)))
                            },
                            color = style.colors.secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Surface(
                        onClick = { onDeleteSession(session.id) },
                        shape = RoundedCornerShape(style.metrics.chipRadius),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = style.colors.accent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        item {
            Surface(
                onClick = onClearHistory,
                shape = RoundedCornerShape(style.metrics.chipRadius),
                color = style.colors.accent.copy(alpha = 0.08f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ai_read_clear_history),
                        color = style.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadAiComposer(
    requesting: Boolean,
    enterToSend: Boolean,
    style: AiComposeStyle,
    onStop: () -> Unit,
    onSend: (String) -> Boolean,
    onInputFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    fun submitDraft() {
        val content = text.trim()
        if (!requesting && content.isNotEmpty() && onSend(content)) {
            text = ""
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(style.metrics.cardRadius),
        color = style.colors.composerSurface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 132.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isBlank()) {
                    Text(
                        text = stringResource(R.string.ai_chat_hint),
                        color = style.colors.secondaryText.copy(alpha = 0.72f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (enterToSend) submitDraft()
                        }
                    ),
                    textStyle = TextStyle(
                        color = style.colors.primaryText,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) {
                                onInputFocused()
                            }
                        }
                )
            }
            Surface(
                onClick = {
                    if (requesting) {
                        onStop()
                    } else {
                        submitDraft()
                    }
                },
                enabled = requesting || text.isNotBlank(),
                shape = CircleShape,
                color = style.colors.accent,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (requesting) R.drawable.ic_stop_black_24dp else R.drawable.ic_arrow_right
                        ),
                        contentDescription = stringResource(
                            if (requesting) R.string.ai_chat_stop else R.string.ai_chat_send
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
