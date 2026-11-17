package io.legado.app.ui.book.read

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.AiErrorKind
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.ui.ai.chat.AiChatEffect
import io.legado.app.ui.ai.chat.AiChatIntent
import io.legado.app.ui.ai.chat.AiReadingContext
import io.legado.app.ui.ai.chat.AiWorkspaceSheet
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

private fun aiMiniErrorText(kind: AiErrorKind): Int = when (kind) {
    AiErrorKind.RATE_LIMIT -> R.string.ai_error_rate_limit
    AiErrorKind.AUTH -> R.string.ai_error_auth
    AiErrorKind.SERVER -> R.string.ai_error_server
    AiErrorKind.NETWORK -> R.string.ai_error_network
    AiErrorKind.UNKNOWN -> R.string.ai_error_generic
}

/**
 * 阅读页内的 AI 对话悬浮小窗（比笔记小框稍大、可拖动，每本书一个独立会话）。
 * 内部 ViewModel 以 bookUrl 为 key，挂载时发 OpenBookChat 找/建会话；技能只显示名称，
 * 正文交给 AI 通过内置 load_skill 工具按需加载。
 */
@Composable
fun AiBookChatWindow(
    bookUrl: String,
    bookName: String,
    chapterTitle: String,
    skillId: String?,
    skillName: String?,
    onDismiss: () -> Unit,
) {
    val viewModel: AiChatViewModel = koinViewModel(key = "bookChat_${bookUrl.hashCode()}")
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = configuration.screenWidthDp.toFloat()
    val screenH = configuration.screenHeightDp.toFloat()
    val winW = (screenW - 48f).coerceAtMost(560f)
    val winH = (screenH * 0.58f).coerceIn(360f, 620f)

    var offsetX by remember { mutableFloatStateOf((screenW - winW) / 2f) }
    var offsetY by remember { mutableFloatStateOf((screenH - winH) / 2f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(screenW, screenH) {
        offsetX = offsetX.coerceIn(0f, (screenW - winW).coerceAtLeast(0f))
        offsetY = offsetY.coerceIn(0f, (screenH - winH).coerceAtLeast(0f))
    }
    val shownX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (dragging) snap() else tween(200, easing = FastOutSlowInEasing),
        label = "miniChatX",
    )
    val shownY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = if (dragging) snap() else tween(200, easing = FastOutSlowInEasing),
        label = "miniChatY",
    )

    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AiChatEffect.ShowMessage -> toast = effect.message
                is AiChatEffect.ShowAiError -> toast = context.getString(aiMiniErrorText(effect.kind))
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }
    LaunchedEffect(bookUrl, skillId) {
        viewModel.onIntent(
            AiChatIntent.OpenBookChat(
                context = AiReadingContext(
                    bookUrl = bookUrl,
                    bookName = bookName,
                    chapterTitle = chapterTitle,
                ),
                skillId = skillId,
                skillName = skillName,
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LegadoTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
        modifier = Modifier
            .offset(x = shownX.dp, y = shownY.dp)
            .width(winW.dp)
            .heightIn(min = 360.dp, max = 620.dp),
    ) {
        Column {
            // 顶部抓手：拖动窗口；右侧放工作区 / 保存 / 关闭。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragCancel = { dragging = false },
                            onDragEnd = { dragging = false },
                        ) { change, amount ->
                            change.consume()
                            offsetX = (offsetX + amount.x / density.density)
                                .coerceIn(0f, (screenW - winW).coerceAtLeast(0f))
                            offsetY = (offsetY + amount.y / density.density)
                                .coerceIn(0f, (screenH - winH).coerceAtLeast(0f))
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = skillName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ai_chat),
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = bookName,
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SmallPlainButton(
                    onClick = { viewModel.onIntent(AiChatIntent.ClearBookChatMessages) },
                    icon = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.ai_clear_chat),
                )
                SmallPlainButton(
                    onClick = { viewModel.onIntent(AiChatIntent.NewBookChat) },
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.ai_new_chat),
                )
                SmallPlainButton(
                    onClick = { viewModel.onIntent(AiChatIntent.ToggleWorkspace) },
                    icon = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.ai_workspace),
                )
                SmallPlainButton(
                    onClick = { viewModel.onIntent(AiChatIntent.SaveConversationToWorkspace) },
                    icon = Icons.Default.Save,
                    contentDescription = stringResource(R.string.ai_save_conversation),
                )
                SmallPlainButton(
                    onClick = onDismiss,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }

            if (toast != null) {
                AppText(
                    text = toast.orEmpty(),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 消息列表。
            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size, state.streamingMessage != null) {
                if (state.messages.isNotEmpty()) {
                    runCatching { listState.animateScrollToItem(state.messages.size - 1) }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    val isUser = msg.role == AiMessageRole.USER
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUser) LegadoTheme.colorScheme.primaryContainer
                        else LegadoTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        AppText(
                            text = msg.content,
                            style = LegadoTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                state.streamingMessage?.let { streaming ->
                    item {
                        AppText(
                            text = streaming.content.ifBlank { "…" },
                            style = LegadoTheme.typography.bodyMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            // 输入栏。
            var draft by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = LegadoTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 96.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        enabled = !state.isSending,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = LegadoTheme.typography.bodyMedium.copy(
                            color = LegadoTheme.colorScheme.onSurface
                        ),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                AppText(
                                    text = stringResource(R.string.ai_chat_input_hint),
                                    style = LegadoTheme.typography.bodyMedium,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.size(6.dp))
                SmallPlainButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotBlank()) {
                            draft = ""
                            viewModel.onIntent(AiChatIntent.SendMessage(text))
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.ai_send),
                )
            }
        }
    }

    if (state.workspaceOpen) {
        AiWorkspaceSheet(
            artifacts = state.workspaceArtifacts,
            onDismiss = { viewModel.onIntent(AiChatIntent.CloseWorkspace) },
        )
    }
}
