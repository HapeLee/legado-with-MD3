package io.legado.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.ai.AiChatMessage
import io.legado.app.help.ai.AiChatService
import io.legado.app.help.ai.AiDefaultConfig
import io.legado.app.help.ai.AiProviderConfig
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

// ============ UI State ============
data class AiChatUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val thinking: String = "",
    val providerName: String = AiDefaultConfig.DEFAULT_PROVIDER.name,
    val model: String = AiDefaultConfig.DEFAULT_MODEL.modelId,
    val apiKey: String = "",
    val baseUrl: String = AiDefaultConfig.DEFAULT_PROVIDER.baseUrl,
    val showConfig: Boolean = false
)

sealed interface AiChatIntent {
    data class UpdateInput(val value: String) : AiChatIntent
    data object Send : AiChatIntent
    data object Stop : AiChatIntent
    data object Clear : AiChatIntent
    data class UpdateBaseUrl(val value: String) : AiChatIntent
    data class UpdateApiKey(val value: String) : AiChatIntent
    data class UpdateModel(val value: String) : AiChatIntent
    data class ToggleConfig(val show: Boolean) : AiChatIntent
    data class QuickSend(val prompt: String) : AiChatIntent
}

// ============ ViewModel ============
class AiChatViewModel(private val initialPrompt: String? = null) {
    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()

    private var sendJob: Job? = null

    fun onIntent(intent: AiChatIntent, scope: kotlinx.coroutines.CoroutineScope) {
        val current = _state.value
        when (intent) {
            is AiChatIntent.UpdateInput -> _state.value = current.copy(input = intent.value)
            is AiChatIntent.Send -> sendMessage(current.input, scope)
            is AiChatIntent.Stop -> {
                sendJob?.cancel()
                _state.value = current.copy(isSending = false, thinking = "")
            }
            is AiChatIntent.Clear -> {
                _state.value = current.copy(messages = emptyList(), thinking = "")
            }
            is AiChatIntent.UpdateBaseUrl -> _state.value = current.copy(baseUrl = intent.value)
            is AiChatIntent.UpdateApiKey -> _state.value = current.copy(apiKey = intent.value)
            is AiChatIntent.UpdateModel -> _state.value = current.copy(model = intent.value)
            is AiChatIntent.ToggleConfig -> _state.value = current.copy(showConfig = intent.show)
            is AiChatIntent.QuickSend -> sendMessage(intent.prompt, scope)
        }
    }

    private fun sendMessage(prompt: String, scope: kotlinx.coroutines.CoroutineScope) {
        val current = _state.value
        if (prompt.isBlank() || current.isSending) return
        if (current.apiKey.isBlank() || current.baseUrl.isBlank()) {
            _state.value = current.copy(showConfig = true)
            return
        }

        val userMessage = AiChatMessage(role = AiChatMessage.Role.USER, content = prompt)
        val assistantMessage = AiChatMessage(
            role = AiChatMessage.Role.ASSISTANT,
            content = "...",
            pending = true
        )
        _state.value = current.copy(
            messages = current.messages + userMessage + assistantMessage,
            input = "",
            isSending = true,
            thinking = "思考中..."
        )

        sendJob = scope.launch {
            try {
                val provider = AiProviderConfig(
                    name = current.providerName,
                    baseUrl = current.baseUrl,
                    apiKey = current.apiKey
                )
                val modelConfig = io.legado.app.help.ai.AiModelConfig(
                    providerId = provider.id,
                    modelId = current.model.ifBlank { AiDefaultConfig.DEFAULT_MODEL.modelId }
                )
                AiChatService.setProvider(provider, modelConfig)

                val conversationMsgs = _state.value.messages
                    .filter { !it.pending }
                    .takeLast(12)

                var responseText = ""
                AiChatService.chatStream(
                    messages = conversationMsgs,
                    onPartial = { partial ->
                        responseText = partial
                        _state.value = _state.value.copy(
                            messages = _state.value.messages.map { msg ->
                                if (msg.pending && msg.role == AiChatMessage.Role.ASSISTANT) {
                                    msg.copy(content = partial, pending = true)
                                } else msg
                            },
                            thinking = ""
                        )
                    },
                    onThinking = { thinkingPart ->
                        if (thinkingPart.isNotBlank()) {
                            _state.value = _state.value.copy(thinking = thinkingPart)
                        }
                    }
                )

                _state.value = _state.value.copy(
                    messages = _state.value.messages.map { msg ->
                        if (msg.pending && msg.role == AiChatMessage.Role.ASSISTANT) {
                            msg.copy(content = responseText, pending = false)
                        } else msg
                    },
                    isSending = false,
                    thinking = ""
                )
            } catch (e: Exception) {
                val errMsg = e.message ?: "请求失败"
                _state.value = _state.value.copy(
                    messages = _state.value.messages.map { msg ->
                        if (msg.pending) msg.copy(content = "❌ $errMsg", pending = false) else msg
                    },
                    isSending = false,
                    thinking = ""
                )
            }
        }
    }

    fun handleInitialPrompt(scope: kotlinx.coroutines.CoroutineScope) {
        if (!initialPrompt.isNullOrBlank() && _state.value.messages.isEmpty()) {
            sendMessage(initialPrompt, scope)
        }
    }
}

// ============ UI ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    initialPrompt: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember { AiChatViewModel(initialPrompt) }
    val state by viewModel.state

    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isSending) {
        if (state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.handleInitialPrompt(scope)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(AiChatIntent.ToggleConfig(!state.showConfig), scope) }) {
                        Text(
                            text = if (state.showConfig) "完成" else "设置",
                            fontSize = 14.sp
                        )
                    }
                    IconButton(onClick = { viewModel.onIntent(AiChatIntent.Clear, scope) }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.showConfig) {
                ConfigPanel(
                    state = state,
                    onBaseUrlChange = { viewModel.onIntent(AiChatIntent.UpdateBaseUrl(it), scope) },
                    onApiKeyChange = { viewModel.onIntent(AiChatIntent.UpdateApiKey(it), scope) },
                    onModelChange = { viewModel.onIntent(AiChatIntent.UpdateModel(it), scope) }
                )
            } else {
                QuickPrompts { prompt ->
                    viewModel.onIntent(AiChatIntent.QuickSend(prompt), scope)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        EmptyHintCard(onSend = { prompt ->
                            viewModel.onIntent(AiChatIntent.QuickSend(prompt), scope)
                        })
                    }
                }

                items(state.messages) { msg ->
                    MessageBubble(msg, state.thinking)
                }
            }

            InputBar(
                input = state.input,
                onInputChange = { viewModel.onIntent(AiChatIntent.UpdateInput(it), scope) },
                onSend = { viewModel.onIntent(AiChatIntent.Send, scope) },
                onStop = { viewModel.onIntent(AiChatIntent.Stop, scope) },
                isSending = state.isSending
            )
        }
    }
}

@Composable
private fun ConfigPanel(
    state: AiChatUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("API 配置", fontWeight = FontWeight.Medium)
            Text("配置后，消息会发送到你指定的 OpenAI 兼容接口。支持 OpenAI、Anthropic Claude、Google Gemini、本地 Ollama 等。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.openai.com/v1") }
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = onModelChange,
                label = { Text("模型") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("gpt-4o-mini") }
            )
        }
    }
}

@Composable
private fun QuickPrompts(onSend: (String) -> Unit) {
    val prompts = listOf(
        "我最近在读什么书？",
        "推荐一本和《三体》类似的科幻小说",
        "帮我分析一下书中的主要人物关系",
        "生一张这本书的封面图片"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        prompts.take(3).forEach { prompt ->
            AssistChip(
                onClick = { onSend(prompt) },
                label = { Text(prompt, fontSize = 12.sp) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyHintCard(onSend: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("👋 你好！我是你的阅读 AI 助手。", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("我可以帮你：", fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• 查询书架和阅读记录", fontSize = 13.sp)
            Text("• 分析书籍内容、人物关系", fontSize = 13.sp)
            Text("• 推荐相似书籍", fontSize = 13.sp)
            Text("• 生成图片/封面/角色头像", fontSize = 13.sp)
            Text("• 搜索书源和书籍信息", fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("点击下方快捷问试试，或直接在输入框提问。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(msg: AiChatMessage, thinking: String) {
    val isUser = msg.role == AiChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
                .then(
                    if (isUser) Modifier
                        .fillMaxWidth(0.85f)
                    else Modifier.fillMaxWidth(0.9f)
                )
        ) {
            Column {
                if (thinking.isNotBlank() && !isUser && msg.pending) {
                    Text(
                        text = "💭 ${thinking.take(200)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = msg.content,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isSending: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("输入消息...") },
                maxLines = 4
            )
            if (isSending) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "停止")
                }
            } else {
                IconButton(onClick = onSend) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}
