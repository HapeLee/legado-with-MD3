package io.legado.app.ui.main.ai.compose

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.about.ReadRecordWidgetStore
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiChatCompanionConfig
import io.legado.app.ui.main.ai.AiChatSession
import io.legado.app.ui.main.ai.AiChatSpeechPlayer
import io.legado.app.ui.main.ai.AiChatViewModel
import io.legado.app.ui.book.character.compose.CharacterAvatar
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.abs

@Stable
data class AiChatScreenActions(
    val onSend: (String) -> Boolean,
    val onStop: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onNewChat: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onSelectModel: () -> Unit,
    val onOpenImageGallery: (() -> Unit)? = null,
    val onOpenWindowAbilities: (() -> Unit)? = null,
    val onOpenWorldBooks: (() -> Unit)? = null,
    val onToggleAutoSpeak: (() -> Unit)? = null,
    val onSpeakMessage: ((String, AiChatCompanionConfig, String) -> Unit)? = null,
    val onAddCompanion: (() -> Unit)? = null,
    val onSelectCompanion: ((String) -> Unit)? = null,
    val onSelectSession: ((String) -> Unit)? = null,
    val onSelectCompanionSession: ((String, String) -> Unit)? = null,
    val onNewCompanionChat: ((String) -> Unit)? = null,
    val onDeleteSession: ((AiChatSession) -> Unit)? = null,
    val onCompanionLongPress: ((AiChatCompanionConfig) -> Unit)? = null
)

@Stable
private class AiCompanionDrawerController {
    var progress by mutableFloatStateOf(0f)
        private set
    var open by mutableStateOf(false)
        private set
    var visible by mutableStateOf(false)
        private set
    var dragging by mutableStateOf(false)
        private set
    var animating by mutableStateOf(false)
        private set
    private var animationJob: Job? = null

    fun startDrag() {
        animationJob?.cancel()
        visible = true
        animating = false
        dragging = true
    }

    fun dragBy(deltaProgress: Float): Boolean {
        val next = (progress + deltaProgress).coerceIn(0f, 1f)
        if (next == progress) return false
        progress = next
        return true
    }

    fun endDrag(scope: kotlinx.coroutines.CoroutineScope, widthPx: Float, openDistancePx: Float) {
        dragging = false
        val openByDistance = progress * widthPx >= openDistancePx
        val closeByDistance = (1f - progress) * widthPx >= openDistancePx
        val target = when {
            open && (closeByDistance || progress < 0.72f) -> 0f
            !open && (openByDistance || progress >= 0.25f) -> 1f
            open -> 1f
            else -> 0f
        }
        settle(scope, target)
    }

    fun cancelDrag(scope: kotlinx.coroutines.CoroutineScope) {
        dragging = false
        settle(scope, if (open) 1f else 0f)
    }

    fun settle(scope: kotlinx.coroutines.CoroutineScope, target: Float, animateChange: Boolean = true) {
        val clampedTarget = target.coerceIn(0f, 1f)
        animationJob?.cancel()
        visible = clampedTarget > 0f || progress > 0f
        open = clampedTarget >= 0.5f
        if (!animateChange || progress == clampedTarget) {
            progress = clampedTarget
            animating = false
            visible = clampedTarget > 0f
            return
        }
        animating = true
        animationJob = scope.launch {
            val opening = clampedTarget > progress
            animate(
                initialValue = progress,
                targetValue = clampedTarget,
                animationSpec = tween(
                    durationMillis = if (opening) 220 else 170,
                    easing = if (opening) LinearOutSlowInEasing else FastOutLinearInEasing
                )
            ) { value, _ ->
                progress = value
            }
            progress = clampedTarget
            open = clampedTarget >= 0.5f
            animating = false
            visible = clampedTarget > 0f
        }
    }
}

@Composable
fun AiChatRoute(
    viewModel: AiChatViewModel,
    lifecycleOwner: LifecycleOwner,
    compactHeader: Boolean,
    refreshToken: Int,
    actions: AiChatScreenActions
) {
    var messages by remember { mutableStateOf(viewModel.messagesLiveData.value.orEmpty()) }
    var requesting by remember { mutableStateOf(viewModel.isRequesting) }
    DisposableEffect(viewModel, lifecycleOwner) {
        val messageObserver = Observer<List<AiChatMessage>> { messages = it.orEmpty() }
        val requestingObserver = Observer<Boolean> { requesting = it == true }
        viewModel.messagesLiveData.observe(lifecycleOwner, messageObserver)
        viewModel.requestingLiveData.observe(lifecycleOwner, requestingObserver)
        onDispose {
            viewModel.messagesLiveData.removeObserver(messageObserver)
            viewModel.requestingLiveData.removeObserver(requestingObserver)
        }
    }
    val modelLabel = remember(refreshToken, messages.size, requesting) {
        AppConfig.aiCurrentModelConfig?.modelId ?: ""
    }
    val companions = remember(refreshToken, messages.size, requesting) {
        viewModel.companions()
    }
    val currentCompanion = remember(refreshToken, messages.size, requesting) {
        viewModel.currentCompanion()
    }
    val currentSessionId = remember(refreshToken, messages.size, requesting, currentCompanion.id) {
        viewModel.activeSessionId()
    }
    val sessionRefreshKey = remember(refreshToken, messages.size, requesting, currentSessionId) {
        "$refreshToken:${messages.size}:$requesting:$currentSessionId"
    }
    val loadCompanionSessions: (String) -> List<AiChatSession> = remember(viewModel) {
        { companionId -> viewModel.historySessions(companionId) }
    }
    val userAvatar = remember(refreshToken) {
        ReadRecordWidgetStore.loadGoalConfig().avatar
    }
    val autoSpeakEnabled = remember(refreshToken) { AppConfig.aiChatAutoSpeakEnabled }
    val thinkingToolbarEnabled = remember(refreshToken) { AppConfig.aiThinkingToolbarEnabled }
    val enterToSend = remember(refreshToken) { AppConfig.aiEnterToSend }
    AiChatScreen(
        messages = messages,
        requesting = requesting,
        modelLabel = modelLabel,
        companions = companions,
        currentCompanion = currentCompanion,
        sessionRefreshKey = sessionRefreshKey,
        loadCompanionSessions = loadCompanionSessions,
        currentSessionId = currentSessionId,
        userAvatar = userAvatar,
        autoSpeakEnabled = autoSpeakEnabled,
        thinkingToolbarEnabled = thinkingToolbarEnabled,
        enterToSend = enterToSend,
        compactHeader = compactHeader,
        actions = actions
    )
}

@Composable
fun AiChatScreen(
    messages: List<AiChatMessage>,
    requesting: Boolean,
    modelLabel: String,
    companions: List<AiChatCompanionConfig>,
    currentCompanion: AiChatCompanionConfig,
    sessionRefreshKey: String,
    loadCompanionSessions: (String) -> List<AiChatSession>,
    currentSessionId: String,
    userAvatar: String?,
    autoSpeakEnabled: Boolean,
    thinkingToolbarEnabled: Boolean,
    enterToSend: Boolean,
    compactHeader: Boolean,
    actions: AiChatScreenActions
) {
    val context = LocalContext.current
    val style = aiComposeStyle(context)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var toolPreviewPayload by remember { mutableStateOf<AiToolDisplayPayload?>(null) }
    var processExpandSignal by remember { mutableStateOf(0) }
    val companionDrawerController = remember { AiCompanionDrawerController() }
    var expandedCompanionId by rememberSaveable { mutableStateOf(currentCompanion.id) }
    var lastAutoSpokenMessageId by rememberSaveable { mutableStateOf("") }
    var stickToBottom by rememberSaveable { mutableStateOf(true) }
    var positionedConversationKey by rememberSaveable { mutableStateOf("") }
    var jumpButtonsVisible by rememberSaveable { mutableStateOf(false) }
    val uiItems = remember(context, messages, thinkingToolbarEnabled) {
        buildAiChatUiItems(
            context = context,
            messages = messages,
            showProcessChain = thinkingToolbarEnabled
        )
    }
    val displayItems = remember(uiItems) {
        uiItems.asReversed()
    }
    val autoScrollSignal = remember(messages) {
        messages.takeLast(8).joinToString("|") { message ->
            val lengthBucket = if (message.pending) {
                message.content.length / 80
            } else {
                message.content.length
            }
            "${message.id}:${message.updatedAt}:$lengthBucket:${message.pending}:${message.collapsed}"
        }
    }
    val bottomThresholdPx = remember(density) { with(density) { 32.dp.toPx().toInt() } }
    val isAtBottom by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount <= 1 ||
                (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= bottomThresholdPx)
        }
    }
    val conversationRootId = remember(uiItems) { uiItems.firstOrNull()?.id.orEmpty() }
    LaunchedEffect(uiItems.isEmpty()) {
        if (uiItems.isEmpty()) {
            positionedConversationKey = ""
            stickToBottom = true
        }
    }
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (isAtBottom) {
            stickToBottom = true
        } else if (listState.isScrollInProgress) {
            stickToBottom = false
        }
    }
    LaunchedEffect(listState.isScrollInProgress, displayItems.size) {
        if (displayItems.size <= 1) {
            jumpButtonsVisible = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress) {
            jumpButtonsVisible = true
        } else {
            delay(900L)
            jumpButtonsVisible = false
        }
    }
    LaunchedEffect(messages.lastOrNull()?.id, messages.lastOrNull()?.role) {
        if (messages.lastOrNull()?.role == AiChatMessage.Role.USER) {
            stickToBottom = true
            if (displayItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(conversationRootId) {
        if (displayItems.isNotEmpty() && positionedConversationKey != conversationRootId) {
            listState.scrollToItem(0)
            stickToBottom = true
            positionedConversationKey = conversationRootId
        }
    }
    LaunchedEffect(requesting, autoScrollSignal, processExpandSignal, stickToBottom) {
        if (displayItems.isNotEmpty() && stickToBottom && !listState.isScrollInProgress) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(
        autoSpeakEnabled,
        messages.lastOrNull()?.id,
        messages.lastOrNull()?.pending,
        messages.lastOrNull()?.updatedAt
    ) {
        val last = messages.lastOrNull()
        if (autoSpeakEnabled &&
            last != null &&
            last.role == AiChatMessage.Role.ASSISTANT &&
            !last.pending &&
            (last.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.TEXT &&
            last.id != lastAutoSpokenMessageId &&
            System.currentTimeMillis() - last.updatedAt <= 60_000
        ) {
            lastAutoSpokenMessageId = last.id
            actions.onSpeakMessage?.invoke(last.content, currentCompanion, last.id)
        }
    }
    LaunchedEffect(currentCompanion.id) {
        if (expandedCompanionId.isBlank()) {
            expandedCompanionId = currentCompanion.id
        }
    }
    val drawerOpenDistancePx = remember(density) { with(density) { 72.dp.toPx() } }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(style.colors.pageBackground)
    ) {
        val drawerWidthPx = remember(maxWidth, density) {
            min(
                with(density) { maxWidth.toPx() * 0.88f },
                with(density) { 340.dp.toPx() }
            ).coerceAtLeast(1f)
        }
        val messageBubbleMaxWidth = remember(maxWidth) {
            maxWidth * 0.76f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawerWidthPx, drawerOpenDistancePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val touchSlop = viewConfiguration.touchSlop
                        var lockedToDrawer = false
                        var totalX = 0f
                        var totalY = 0f
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            if (!lockedToDrawer) {
                                totalX += delta.x
                                totalY += delta.y
                                val absX = abs(totalX)
                                val absY = abs(totalY)
                                if (absX <= touchSlop && absY <= touchSlop) {
                                    continue
                                }
                                val horizontalIntent = absX > absY * 1.25f
                                val canOpenOrClose = totalX > 0f || companionDrawerController.open
                                if (!horizontalIntent || !canOpenOrClose) {
                                    cancelled = true
                                    break
                                }
                                lockedToDrawer = true
                                companionDrawerController.startDrag()
                                val overSlopX = totalX - if (totalX > 0f) touchSlop else -touchSlop
                                if (companionDrawerController.dragBy(overSlopX / drawerWidthPx)) {
                                    change.consume()
                                }
                            } else if (companionDrawerController.dragBy(delta.x / drawerWidthPx)) {
                                change.consume()
                            }
                        }
                        if (lockedToDrawer) {
                            companionDrawerController.endDrag(coroutineScope, drawerWidthPx, drawerOpenDistancePx)
                        } else if (!cancelled) {
                            companionDrawerController.cancelDrag(coroutineScope)
                        }
                    }
                }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AiChatTopBar(
                modelLabel = modelLabel,
                currentCompanion = currentCompanion,
                requesting = requesting,
                compactHeader = compactHeader,
                autoSpeakEnabled = autoSpeakEnabled,
                style = style,
                onOpenCompanionDrawer = { companionDrawerController.settle(coroutineScope, 1f) },
                actions = actions
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (uiItems.isEmpty()) {
                    AiEmptyState(style = style)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 14.dp,
                            top = 10.dp,
                            end = 14.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = displayItems,
                            key = { it.id },
                            contentType = { item ->
                                when (item) {
                                    is AiChatUiItem.User -> "user"
                                    is AiChatUiItem.Assistant -> "assistant"
                                }
                            }
                        ) { item ->
                            AiMessageRow(
                                item = item,
                                currentCompanion = currentCompanion,
                                userAvatar = userAvatar,
                                style = style,
                                bubbleMaxWidth = messageBubbleMaxWidth,
                                onSpeak = actions.onSpeakMessage,
                                onToolPreview = { toolPreviewPayload = it },
                                onProcessExpanded = {
                                    processExpandSignal += 1
                                    coroutineScope.launch {
                                        if (displayItems.isNotEmpty() && stickToBottom) {
                                            listState.scrollToItem(0)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        AiComposer(
            requesting = requesting,
            enterToSend = enterToSend,
            style = style,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        if (uiItems.size > 1) {
            AnimatedVisibility(
                visible = jumpButtonsVisible,
                enter = slideInHorizontally(
                    animationSpec = tween(180, easing = LinearOutSlowInEasing),
                    initialOffsetX = { it + 24 }
                ) + fadeIn(tween(100)),
                exit = slideOutHorizontally(
                    animationSpec = tween(150, easing = FastOutLinearInEasing),
                    targetOffsetX = { it + 24 }
                ) + fadeOut(tween(90)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(end = 16.dp, bottom = 92.dp)
            ) {
                AiJumpButtons(
                    style = style,
                    onPrevious = {
                        jumpButtonsVisible = true
                        val current = listState.firstVisibleItemIndex
                        val target = (current + 1).coerceAtMost(displayItems.lastIndex)
                        coroutineScope.launch { listState.animateScrollToItem(target) }
                    },
                    onNext = {
                        jumpButtonsVisible = true
                        val current = listState.firstVisibleItemIndex
                        val target = (current - 1).coerceAtLeast(0)
                        coroutineScope.launch { listState.animateScrollToItem(target) }
                    }
                )
            }
        }
        toolPreviewPayload?.let { payload ->
            AiToolPreviewDialog(
                payload = payload,
                style = style,
                onDismiss = { toolPreviewPayload = null }
            )
        }
        AiCompanionDrawerHost(
            controller = companionDrawerController,
            companions = companions,
            currentCompanionId = currentCompanion.id,
            expandedCompanionId = expandedCompanionId,
            sessionRefreshKey = sessionRefreshKey,
            loadCompanionSessions = loadCompanionSessions,
            currentSessionId = currentSessionId,
            style = style,
            actions = actions,
            drawerWidthPx = drawerWidthPx,
            onToggleCompanion = { companionId ->
                expandedCompanionId = if (expandedCompanionId == companionId) {
                    ""
                } else {
                    companionId
                }
            },
            onDismiss = { companionDrawerController.settle(coroutineScope, 0f) }
        )
    }
    }
}

@Composable
private fun AiChatTopBar(
    modelLabel: String,
    currentCompanion: AiChatCompanionConfig,
    requesting: Boolean,
    compactHeader: Boolean,
    autoSpeakEnabled: Boolean,
    style: AiComposeStyle,
    onOpenCompanionDrawer: () -> Unit,
    actions: AiChatScreenActions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (compactHeader) 22.dp else 16.dp,
                top = if (compactHeader) 8.dp else 10.dp,
                end = if (compactHeader) 14.dp else 12.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenCompanionDrawer) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = null,
                tint = style.colors.primaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentCompanion.name.ifBlank { stringResource(R.string.ai) },
                color = style.colors.primaryText,
                fontSize = if (compactHeader) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (modelLabel.isNotBlank()) {
            Surface(
                onClick = actions.onSelectModel,
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    tint = style.colors.primaryText,
                    modifier = Modifier.size(21.dp)
                )
            }
            if (menuExpanded) {
                AiModernTopMenu(
                    style = style,
                    actions = buildList {
                        add(AiTopMenuAction(stringResource(R.string.ai_new_chat)) { actions.onNewChat() })
                        add(AiTopMenuAction(stringResource(R.string.ai_chat_history)) { actions.onOpenHistory() })
                        actions.onOpenWindowAbilities?.let { openAbilities ->
                            add(AiTopMenuAction("窗口能力", openAbilities))
                        }
                        actions.onOpenWorldBooks?.let { openWorldBooks ->
                            add(AiTopMenuAction("浏览世界书", openWorldBooks))
                        }
                        actions.onToggleAutoSpeak?.let { toggleAutoSpeak ->
                            add(AiTopMenuAction("自动播放语音：${if (autoSpeakEnabled) "开" else "关"}", toggleAutoSpeak))
                        }
                        add(AiTopMenuAction(stringResource(R.string.ai_setting)) { actions.onOpenSettings() })
                        actions.onOpenImageGallery?.let { openGallery ->
                            add(AiTopMenuAction(stringResource(R.string.ai_image_gallery), openGallery))
                        }
                    },
                    onDismiss = { menuExpanded = false }
                )
            }
        }
    }
}

@Immutable
private data class AiTopMenuAction(
    val title: String,
    val invoke: () -> Unit
)

@Composable
private fun AiModernTopMenu(
    style: AiComposeStyle,
    actions: List<AiTopMenuAction>,
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
private fun AiCompanionDrawerHost(
    controller: AiCompanionDrawerController,
    companions: List<AiChatCompanionConfig>,
    currentCompanionId: String,
    expandedCompanionId: String,
    sessionRefreshKey: String,
    loadCompanionSessions: (String) -> List<AiChatSession>,
    currentSessionId: String,
    style: AiComposeStyle,
    actions: AiChatScreenActions,
    drawerWidthPx: Float,
    onToggleCompanion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!controller.visible) return
    AiCompanionDrawer(
        companions = companions,
        currentCompanionId = currentCompanionId,
        expandedCompanionId = expandedCompanionId,
        sessionRefreshKey = sessionRefreshKey,
        loadCompanionSessions = loadCompanionSessions,
        currentSessionId = currentSessionId,
        style = style,
        actions = actions,
        controller = controller,
        drawerWidthPx = drawerWidthPx,
        onToggleCompanion = onToggleCompanion,
        onDismiss = onDismiss
    )
}

@Composable
private fun AiCompanionDrawer(
    companions: List<AiChatCompanionConfig>,
    currentCompanionId: String,
    expandedCompanionId: String,
    sessionRefreshKey: String,
    loadCompanionSessions: (String) -> List<AiChatSession>,
    currentSessionId: String,
    style: AiComposeStyle,
    actions: AiChatScreenActions,
    controller: AiCompanionDrawerController,
    drawerWidthPx: Float,
    onToggleCompanion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.22f * controller.progress.coerceIn(0f, 1f)
                }
                .background(Color.Black)
                .clickable { onDismiss() }
        )
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = style.colors.composerSurface,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
                .widthIn(max = 340.dp)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    val progress = controller.progress.coerceIn(0f, 1f)
                    translationX = -drawerWidthPx * (1f - progress)
                    alpha = progress.coerceAtLeast(0.001f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI 酒馆",
                            color = style.colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "角色与会话",
                            color = style.colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                    actions.onAddCompanion?.let { add ->
                        IconButton(onClick = add) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = style.colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_x),
                            contentDescription = null,
                            tint = style.colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        DrawerSectionTitle("助手", style)
                    }
                    items(
                        items = companions,
                        key = { it.id },
                        contentType = { "companion" }
                    ) { companion ->
                        val expanded = companion.id == expandedCompanionId
                        val companionSessions = remember(companion.id, expanded, sessionRefreshKey) {
                            if (expanded) {
                                loadCompanionSessions(companion.id)
                            } else {
                                emptyList()
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AiCompanionDrawerItem(
                                companion = companion,
                                selected = companion.id == currentCompanionId,
                                expanded = expanded,
                                style = style,
                                onSelect = {
                                    onToggleCompanion(companion.id)
                                },
                                onLongPress = actions.onCompanionLongPress?.let { action ->
                                    {
                                        onDismiss()
                                        action(companion)
                                    }
                                }
                            )
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 110)),
                                exit = shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                                ) + fadeOut(animationSpec = tween(durationMillis = 90))
                            ) {
                                AiCompanionSessionPanel(
                                    sessions = companionSessions,
                                    currentSessionId = currentSessionId,
                                    style = style,
                                    onNewChat = {
                                        actions.onNewCompanionChat?.invoke(companion.id)
                                            ?: actions.onNewChat()
                                        onDismiss()
                                    },
                                    onSelect = { session ->
                                        actions.onSelectCompanionSession?.invoke(companion.id, session.id)
                                            ?: run {
                                                if (companion.id != currentCompanionId) {
                                                    actions.onSelectCompanion?.invoke(companion.id)
                                                }
                                                actions.onSelectSession?.invoke(session.id)
                                            }
                                        onDismiss()
                                    },
                                    onLongPress = actions.onDeleteSession
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AiCompanionSessionPanel(
    sessions: List<AiChatSession>,
    currentSessionId: String,
    style: AiComposeStyle,
    onNewChat: () -> Unit,
    onSelect: (AiChatSession) -> Unit,
    onLongPress: ((AiChatSession) -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(style.metrics.cardRadius),
        color = style.colors.cardSurface.copy(alpha = 0.90f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "会话",
                    color = style.colors.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(style.metrics.chipRadius),
                    color = style.colors.accent.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "新建",
                        color = style.colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            if (sessions.isEmpty()) {
                Text(
                    text = "还没有历史会话",
                    color = style.colors.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            } else {
                sessions.take(8).forEach { session ->
                    AiSessionDrawerItem(
                        session = session,
                        selected = session.id == currentSessionId,
                        style = style,
                        onSelect = { onSelect(session) },
                        onLongPress = onLongPress?.let { action -> { action(session) } }
                    )
                }
                if (sessions.size > 8) {
                    Text(
                        text = "还有 ${sessions.size - 8} 个会话",
                        color = style.colors.secondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(
    text: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = style.colors.secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AiCompanionDrawerItem(
    companion: AiChatCompanionConfig,
    selected: Boolean,
    expanded: Boolean,
    style: AiComposeStyle,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val isDefault = companion.id == AiChatCompanionConfig.DEFAULT_COMPANION_ID
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.metrics.cardRadius))
            .background(if (selected) style.colors.accent.copy(alpha = 0.13f) else style.colors.cardSurface.copy(alpha = 0.92f))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) style.colors.accent.copy(alpha = 0.72f) else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(9.dp))
        AiCompanionAvatar(companion, style, 42)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = companion.name,
                color = style.colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    isDefault -> "默认系统提示词"
                    companion.bookKey.isNotBlank() -> "角色 · ${displayBookKeyLabel(companion.bookKey)}"
                    else -> "角色"
                },
                color = style.colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            tint = style.colors.secondaryText.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AiSessionDrawerItem(
    session: AiChatSession,
    selected: Boolean,
    style: AiComposeStyle,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val timeFormat = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.metrics.cardRadius))
            .background(if (selected) style.colors.accent.copy(alpha = 0.12f) else style.colors.composerSurface.copy(alpha = 0.72f))
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) style.colors.accent else Color.Transparent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = session.title.ifBlank { "未命名会话" },
                color = style.colors.primaryText,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${timeFormat.format(java.util.Date(session.updatedAt))} · ${session.messages.size} 条消息",
                color = style.colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AiCompanionAvatar(
    companion: AiChatCompanionConfig,
    style: AiComposeStyle,
    sizeDp: Int
) {
    if (companion.avatar.isNotBlank()) {
        CharacterAvatar(
            path = companion.avatar,
            contentDescription = companion.name,
            sizeDp = sizeDp
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(style.colors.toolSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (companion.id == AiChatCompanionConfig.DEFAULT_COMPANION_ID) {
                        R.drawable.ic_bottom_ai_e
                    } else {
                        R.drawable.ic_bottom_person_e
                    }
                ),
                contentDescription = null,
                tint = style.colors.accent,
                modifier = Modifier.size((sizeDp * 0.55f).dp)
            )
        }
    }
}

private fun displayBookKeyLabel(bookKey: String): String {
    val value = bookKey.trim()
    if (!value.startsWith("work:")) return value
    val body = value.removePrefix("work:")
    val parts = body.split('/', limit = 2)
    return when {
        parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() -> "${parts[1]} · ${parts[0]}"
        body.isNotBlank() -> body
        else -> value
    }
}

@Composable
private fun AiEmptyState(style: AiComposeStyle) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bottom_ai_e),
                    contentDescription = stringResource(R.string.ai),
                    tint = style.colors.secondaryText,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.ai_chat_empty),
                    color = style.colors.secondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AiMessageRow(
    item: AiChatUiItem,
    currentCompanion: AiChatCompanionConfig,
    userAvatar: String?,
    style: AiComposeStyle,
    bubbleMaxWidth: androidx.compose.ui.unit.Dp,
    onSpeak: ((String, AiChatCompanionConfig, String) -> Unit)?,
    onToolPreview: (AiToolDisplayPayload) -> Unit,
    onProcessExpanded: () -> Unit
) {
    when (item) {
        is AiChatUiItem.User -> AiUserMessageRow(item, userAvatar, style, bubbleMaxWidth)
        is AiChatUiItem.Assistant -> AiAssistantMessageRow(
            message = item,
            companion = currentCompanion,
            style = style,
            bubbleMaxWidth = bubbleMaxWidth,
            onSpeak = onSpeak,
            onToolPreview = onToolPreview,
            onProcessExpanded = onProcessExpanded
        )
    }
}

@Composable
private fun AiUserMessageRow(
    message: AiChatUiItem.User,
    userAvatar: String?,
    style: AiComposeStyle,
    bubbleMaxWidth: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        AiChatBubbleSurface(
            isUser = true,
            style = style,
            modifier = Modifier.widthIn(max = bubbleMaxWidth)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.End
            ) {
                AiMarkdownRichText(
                    text = message.content,
                    style = style,
                    color = style.colors.userText
                )
                AiCopyTextButton(
                    text = message.content,
                    style = style,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        AiUserAvatar(
            avatar = userAvatar,
            style = style,
            sizeDp = 34
        )
    }
}

@Composable
private fun AiAssistantMessageRow(
    message: AiChatUiItem.Assistant,
    companion: AiChatCompanionConfig,
    style: AiComposeStyle,
    bubbleMaxWidth: androidx.compose.ui.unit.Dp,
    onSpeak: ((String, AiChatCompanionConfig, String) -> Unit)?,
    onToolPreview: (AiToolDisplayPayload) -> Unit,
    onProcessExpanded: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AiCompanionAvatar(companion, style, 36)
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.widthIn(max = bubbleMaxWidth),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            message.parts.forEach { part ->
                key(part.id) {
                    when (part) {
                        is AiMessagePartUi.Text -> AiAssistantTextPart(
                            part = part,
                            companion = companion,
                            style = style,
                            onSpeak = onSpeak
                        )
                        is AiMessagePartUi.ProcessChain -> AiProcessPart(part, style, onToolPreview, onProcessExpanded)
                        is AiMessagePartUi.SearchBooks -> AiSearchBookInlinePart(part, style, onToolPreview)
                        is AiMessagePartUi.Images -> AiImageInlinePart(part, style, onToolPreview)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAssistantTextPart(
    part: AiMessagePartUi.Text,
    companion: AiChatCompanionConfig,
    style: AiComposeStyle,
    onSpeak: ((String, AiChatCompanionConfig, String) -> Unit)?
) {
    AiChatBubbleSurface(isUser = false, style = style) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (part.pending) {
                SelectionContainer {
                    Text(
                        text = part.content,
                        color = style.colors.primaryText,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }
            } else {
                AiComposeMarkdownText(
                    content = part.content,
                    style = style
                )
            }
            if (!part.pending && part.content.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    AiCopyTextButton(
                        text = part.content,
                        style = style
                    )
                    if (onSpeak != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AiMessageSpeakButton(
                            partId = part.id,
                            content = part.content,
                            companion = companion,
                            style = style,
                            onSpeak = onSpeak
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageSpeakButton(
    partId: String,
    content: String,
    companion: AiChatCompanionConfig,
    style: AiComposeStyle,
    onSpeak: (String, AiChatCompanionConfig, String) -> Unit
) {
    val speechState by AiChatSpeechPlayer.playbackState.collectAsState()
    val isSpeaking = speechState.key == partId && speechState.active
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(style.colors.accent.copy(alpha = if (isSpeaking) 0.20f else 0.10f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onSpeak(content, companion, partId)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                if (isSpeaking) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
            ),
            contentDescription = null,
            tint = style.colors.accent,
            modifier = Modifier.size(if (isSpeaking) 15.dp else 16.dp)
        )
    }
}

@Composable
private fun AiChatBubbleSurface(
    isUser: Boolean,
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(style.metrics.cardRadius)
    Surface(
        shape = shape,
        color = if (isUser) style.colors.userBubble else style.colors.composerSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        content()
    }
}

@Composable
private fun AiUserAvatar(
    avatar: String?,
    style: AiComposeStyle,
    sizeDp: Int
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(style.colors.toolSurface),
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { imageView ->
            val target = avatar.orEmpty()
            if (imageView.tag != target) {
                imageView.tag = target
                ImageLoader.load(context, avatar)
                    .placeholder(R.drawable.ic_read_record_default_avatar)
                    .error(R.drawable.ic_read_record_default_avatar)
                    .centerCrop()
                    .into(imageView)
            }
        },
        onRelease = { it.releaseComposeImage() }
    )
}

@Composable
private fun AiFallbackAvatar(
    iconRes: Int,
    style: AiComposeStyle,
    sizeDp: Int,
    accent: Boolean
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(if (accent) style.colors.accent.copy(alpha = 0.12f) else style.colors.toolSurface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (accent) style.colors.accent else style.colors.secondaryText,
            modifier = Modifier.size((sizeDp * 0.56f).dp)
        )
    }
}

@Composable
private fun AiProcessPart(
    part: AiMessagePartUi.ProcessChain,
    style: AiComposeStyle,
    onToolPreview: (AiToolDisplayPayload) -> Unit,
    onProcessExpanded: () -> Unit
) {
    AiProcessTimelineCard(
        steps = part.steps,
        style = style,
        onToolClick = { step ->
            step.payload?.let(onToolPreview)
        },
        onExpandedChange = onProcessExpanded
    )
}

@Composable
private fun AiSearchBookInlinePart(
    part: AiMessagePartUi.SearchBooks,
    style: AiComposeStyle,
    onToolPreview: (AiToolDisplayPayload) -> Unit
) {
    AiInfoPill(
        text = "书籍结果 ${part.books.size} 条",
        style = style,
        onClick = {
            onToolPreview(
                AiToolDisplayPayload(
                    type = AiToolPreviewType.BookResults,
                    title = "书籍结果",
                    summary = "共 ${part.books.size} 条",
                    raw = "",
                    books = part.books
                )
            )
        }
    )
}

@Composable
private fun AiImageInlinePart(
    part: AiMessagePartUi.Images,
    style: AiComposeStyle,
    onToolPreview: (AiToolDisplayPayload) -> Unit
) {
    AiInfoPill(
        text = "图片结果 ${part.images.size} 张",
        style = style,
        onClick = {
            onToolPreview(
                AiToolDisplayPayload(
                    type = AiToolPreviewType.ImageResult,
                    title = "图片结果",
                    summary = "共 ${part.images.size} 张",
                    raw = "",
                    images = part.images
                )
            )
        }
    )
}

@Composable
private fun AiInfoPill(
    text: String,
    style: AiComposeStyle,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(style.metrics.chipRadius),
        color = style.colors.accent.copy(alpha = 0.10f)
    ) {
        Text(
            text = text,
            color = style.colors.accent,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AiJumpButtons(
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiJumpButton(style = style, rotation = 180f, onClick = onPrevious)
        AiJumpButton(style = style, rotation = 0f, onClick = onNext)
    }
}

@Composable
private fun AiJumpButton(
    style: AiComposeStyle,
    rotation: Float,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = style.colors.cardSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = style.colors.primaryText,
                modifier = Modifier
                    .size(22.dp)
                    .then(Modifier.graphicsLayer(rotationZ = rotation))
            )
        }
    }
}

@Composable
private fun AiComposer(
    requesting: Boolean,
    enterToSend: Boolean,
    style: AiComposeStyle,
    actions: AiChatScreenActions,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submitDraft() {
        val content = text.trim()
        if (!requesting && content.isNotEmpty() && actions.onSend(content)) {
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Surface(
                onClick = {
                    if (requesting) {
                        actions.onStop()
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

private const val searchBookScheme = "legado-search-book://"
