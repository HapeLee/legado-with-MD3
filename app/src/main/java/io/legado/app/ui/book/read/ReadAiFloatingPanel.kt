package io.legado.app.ui.book.read

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import io.legado.app.R
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiMarkdownRender
import io.legado.app.ui.main.ai.compose.AiComposeStyle
import io.legado.app.ui.main.ai.compose.aiComposeStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val markwon: Markwon by lazy {
        AiMarkdownRender.createMarkwon(context)
    }
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private var readContext: ReadContext? = null
    private var currentSessionId: String = ""
    private var answerJob: Job? = null
    private var streamingAssistantContent: String? = null
    private var streamingAssistantMessageId: String? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f

    private var messages by mutableStateOf<List<ReadAiMessage>>(emptyList())
    private var historySessions by mutableStateOf<List<ReadAiSession>>(emptyList())
    private var contextLabel by mutableStateOf("")
    private var showingHistory by mutableStateOf(false)
    private var requesting by mutableStateOf(false)
    private var modelLabel by mutableStateOf(currentModelLabel())

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
                markwon = markwon,
                timeFormat = timeFormat,
                onTopDrag = ::handleDrag,
                onSelectModel = ::selectModel,
                onNewChat = ::startNewChat,
                onToggleHistory = ::toggleHistory,
                onClose = ::close,
                onStop = ::stopAnswer,
                onSend = ::submitQuestion,
                onOpenSession = ::openHistorySession,
                onDeleteSession = ::deleteSession,
                onClearHistory = ::confirmClearHistory
            )
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
            if (anchor != null) {
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
        updateRequestingState()
        visibility = GONE
    }

    private fun stopAnswer() {
        val context = readContext
        answerJob?.cancel()
        streamingAssistantContent = null
        streamingAssistantMessageId = null
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
        streamingAssistantContent = null
        streamingAssistantMessageId = null
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
        val requestSessionId = currentSessionId
        appendMessage(context, ReadAiMessage.Role.USER, question)
        val pendingAssistantId = appendMessage(
            context,
            ReadAiMessage.Role.ASSISTANT,
            resources.getString(R.string.ai_chat_thinking)
        )
        val requestMessages = buildRequestMessages(context, question)
        streamingAssistantMessageId = pendingAssistantId
        answerJob = requestScope.launch {
            post { updateRequestingState() }
            val result = runCatching {
                withContext(IO) {
                    AiChatService.chatStream(
                        messages = requestMessages,
                        onPartial = { partial ->
                            if (partial.isNotBlank()) {
                                post {
                                    streamingAssistantContent = partial
                                    if (!showingHistory) renderCurrentSession()
                                }
                            }
                        },
                        includeStructuredBlocks = false
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
                answerJob = null
                updateRequestingState()
                if (!showingHistory) renderCurrentSession()
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
                y = targetY.coerceIn(0f, max(0, parentView.height - height).toFloat())
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
        val parentView = parent as? ViewGroup ?: return
        if (width <= 0 || height <= 0 || parentView.width <= 0 || parentView.height <= 0) return
        x = min(max(0f, x), max(0, parentView.width - width).toFloat())
        y = min(max(0f, y), max(0, parentView.height - height).toFloat())
    }

    private fun placeNearAnchor(anchor: Anchor) {
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

    private fun currentModelLabel(): String {
        val model = AppConfig.aiCurrentModelConfig ?: return ""
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
            AppConfig.aiCurrentModelId = models[index].id
            modelLabel = currentModelLabel()
        }
    }

    private fun buildContextLabel(context: ReadContext): String {
        return buildString {
            append(context.bookName.ifBlank { resources.getString(R.string.book_name) })
            if (context.chapterTitle.isNotBlank()) append(" - ").append(context.chapterTitle)
        }
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

    companion object {
        private val requestScope = CoroutineScope(SupervisorJob() + IO)
    }
}

@Composable
private fun ReadAiPanelContent(
    messages: List<ReadAiMessage>,
    historySessions: List<ReadAiSession>,
    contextLabel: String,
    showingHistory: Boolean,
    requesting: Boolean,
    modelLabel: String,
    markwon: Markwon,
    timeFormat: SimpleDateFormat,
    onTopDrag: (MotionEvent) -> Boolean,
    onSelectModel: () -> Unit,
    onNewChat: () -> Unit,
    onToggleHistory: () -> Unit,
    onClose: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Boolean,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    val style = aiComposeStyle(context)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = style.colors.background,
        shadowElevation = 12.dp,
        border = BorderStroke(style.metrics.strokeWidth, style.colors.stroke)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSelectModel,
                    enabled = !requesting,
                    shape = RoundedCornerShape(style.metrics.chipRadius),
                    color = style.colors.accent.copy(alpha = if (requesting) 0.06f else 0.10f)
                ) {
                    Text(
                        text = modelLabel.ifBlank { stringResource(R.string.ai_current_model_summary_empty) },
                        color = if (requesting) style.colors.secondaryText else style.colors.accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 132.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pointerInteropFilter(onTouchEvent = onTopDrag)
                )
                ReadAiIconButton(R.drawable.ic_add, R.string.ai_new_chat, style, onNewChat)
                ReadAiIconButton(R.drawable.ic_history, R.string.history, style, onToggleHistory)
                ReadAiIconButton(R.drawable.ic_close_x, R.string.close, style, onClose)
            }
            Text(
                text = contextLabel,
                color = style.colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (showingHistory) {
                ReadAiHistoryList(
                    sessions = historySessions,
                    style = style,
                    timeFormat = timeFormat,
                    onOpenSession = onOpenSession,
                    onDeleteSession = onDeleteSession,
                    onClearHistory = onClearHistory
                )
            } else {
                ReadAiMessageList(
                    messages = messages,
                    requesting = requesting,
                    style = style,
                    markwon = markwon
                )
            }
            ReadAiComposer(
                requesting = requesting,
                enterToSend = AppConfig.aiEnterToSend,
                style = style,
                onStop = onStop,
                onSend = onSend,
                modifier = Modifier.padding(top = 10.dp)
            )
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
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescriptionRes),
            tint = style.colors.primaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ReadAiMessageList(
    messages: List<ReadAiMessage>,
    requesting: Boolean,
    style: AiComposeStyle,
    markwon: Markwon
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
    LaunchedEffect(messages.size, messages.lastOrNull()?.content, requesting) {
        if (messages.isNotEmpty() && stickToBottom && !listState.isScrollInProgress) {
            listState.scrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            ReadAiMessageRow(
                message = message,
                streaming = requesting && message == messages.lastOrNull(),
                style = style,
                markwon = markwon
            )
        }
    }
}

@Composable
private fun ReadAiMessageRow(
    message: ReadAiMessage,
    streaming: Boolean,
    style: AiComposeStyle,
    markwon: Markwon
) {
    val isUser = message.role == ReadAiMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 72.dp, max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (isUser) 7.dp else 18.dp,
                bottomStart = if (isUser) 18.dp else 7.dp
            ),
            color = if (isUser) style.colors.userBubble else style.colors.assistantBubble,
            border = BorderStroke(
                style.metrics.strokeWidth,
                if (isUser) style.colors.userBubbleStroke else style.colors.assistantBubbleStroke
            )
        ) {
            ReadAiMarkdownText(
                messageId = message.id,
                content = message.content,
                streaming = streaming,
                markwon = markwon,
                textColor = if (isUser) style.colors.userText else style.colors.primaryText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ReadAiMarkdownText(
    messageId: String,
    content: String,
    streaming: Boolean,
    markwon: Markwon,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            TextView(it).apply {
                includeFontPadding = true
                setLineSpacing(2.dpToPx().toFloat(), 1f)
                textSize = 14f
                typeface = it.uiTypeface()
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgbCompat())
            textView.typeface = context.uiTypeface()
            if (streaming) {
                AiMarkdownRender.clearNativeSelectionWithLinkTap(textView)
                if (textView.text?.toString() != content) {
                    textView.text = content
                }
                textView.tag = null
            } else {
                val renderKey = AiMarkdownRender.renderKey(
                    messageId,
                    content,
                    pending = false,
                    textView = textView,
                    context = context
                )
                if (textView.tag != renderKey) {
                    markwon.setParsedMarkdown(
                        textView,
                        markwon.toMarkdown(content.ifBlank { " " })
                    )
                    textView.tag = renderKey
                }
                AiMarkdownRender.setNativeSelectionWithLinkTap(textView)
            }
        }
    )
}

@Composable
private fun ReadAiHistoryList(
    sessions: List<ReadAiSession>,
    style: AiComposeStyle,
    timeFormat: SimpleDateFormat,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
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
        shape = RoundedCornerShape(28.dp),
        color = style.colors.composerSurface,
        shadowElevation = 8.dp,
        border = BorderStroke(style.metrics.strokeWidth, style.colors.composerStroke)
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
                    modifier = Modifier.fillMaxWidth()
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

private fun Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
