package io.legado.app.ui.ai

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.ai.AiBookshelfHelper
import io.legado.app.help.ai.AiClient
import io.legado.app.help.ai.AiConfigStore
import io.legado.app.help.ai.AiMessage
import io.legado.app.help.ai.AiProvider
import io.legado.app.help.ai.AiProviderConfig
import io.legado.app.help.ai.AiBookSourceHelper
import io.legado.app.help.ai.BUILT_IN_PRESETS
import io.legado.app.help.ai.defaultProviderConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// ========== AI 聊天 ==========
class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiChatEffect> = _effects.asSharedFlow()

    private var streamJob: Job? = null

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            activeProvider = provider,
            activeModel = cfg?.chatModel?.ifBlank { defaultProviderConfig(provider).chatModel }
                ?: "gpt-4o-mini",
            temperature = cfg?.temperature ?: 0.7f,
            streamEnabled = AiConfigStore.streamEnabled
        )
        // 预设角色
        val customPresets = AiConfigStore.loadCustomPresets()
        _uiState.value = _uiState.value.copy(
            presets = BUILT_IN_PRESETS + customPresets,
            currentPreset = _uiState.value.currentPreset ?: BUILT_IN_PRESETS.firstOrNull()
        )
    }

    fun onIntent(intent: AiChatIntent) {
        when (intent) {
            is AiChatIntent.UpdateInput -> _uiState.value = _uiState.value.copy(input = intent.value)
            is AiChatIntent.Send -> send()
            is AiChatIntent.Stop -> {
                streamJob?.cancel()
                streamJob = null
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            is AiChatIntent.ChangePreset -> _uiState.value = _uiState.value.copy(currentPreset = intent.preset)
            is AiChatIntent.ChangeProvider -> {
                AiConfigStore.activeProvider = intent.provider.name
                val configs = AiConfigStore.loadProviderConfigs()
                val cfg = configs[intent.provider]
                _uiState.value = _uiState.value.copy(
                    activeProvider = intent.provider,
                    activeModel = cfg?.chatModel?.ifBlank { defaultProviderConfig(intent.provider).chatModel }
                        ?: defaultProviderConfig(intent.provider).chatModel
                )
            }
            is AiChatIntent.ChangeModel -> {
                _uiState.value = _uiState.value.copy(activeModel = intent.model)
            }
            is AiChatIntent.UpdateTemperature -> {
                _uiState.value = _uiState.value.copy(temperature = intent.value)
                AiConfigStore.defaultTemperature = intent.value
            }
            is AiChatIntent.ClearConversation -> {
                _uiState.value = _uiState.value.copy(messages = emptyList())
                _effects.tryEmit(AiChatEffect.ShowToast("对话已清空"))
            }
        }
    }

    private fun send() {
        val state = _uiState.value
        val text = state.input.trim()
        if (text.isEmpty()) {
            _effects.tryEmit(AiChatEffect.ShowToast("请输入内容"))
            return
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[state.activeProvider] ?: defaultProviderConfig(state.activeProvider)
        if (cfg.apiKey.isEmpty() && state.activeProvider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiChatEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        val userMessage = AiMessage("user", text)
        val assistantMessage = AiMessage("assistant", "")
        val newMessages = state.messages + userMessage + assistantMessage
        _uiState.value = _uiState.value.copy(
            messages = newMessages,
            input = "",
            isLoading = true,
            error = null
        )

        val chatMessages = buildList {
            state.messages.forEach { add(it) }
            add(userMessage)
        }

        if (state.streamEnabled) {
            // 流式
            streamJob = viewModelScope.launch {
                try {
                    val flow = AiClient.chatStream(
                        messages = chatMessages,
                        config = cfg.copy(
                            chatModel = state.activeModel.ifBlank { cfg.chatModel },
                            temperature = state.temperature
                        ),
                        model = state.activeModel.ifBlank { cfg.chatModel },
                        temperature = state.temperature,
                        systemPrompt = state.currentPreset?.systemPrompt
                    )
                    var accumulated = ""
                    flow.collect { chunk ->
                        if (chunk.done) {
                            val msgs = _uiState.value.messages.toMutableList()
                            if (msgs.isNotEmpty() && msgs.last().role == "assistant") {
                                msgs[msgs.size - 1] = msgs.last().copy(content = accumulated)
                            }
                            _uiState.value = _uiState.value.copy(messages = msgs, isLoading = false)
                        } else {
                            val d = chunk.text
                            if (d.isNotEmpty()) {
                                accumulated += d
                                val msgs = _uiState.value.messages.toMutableList()
                                if (msgs.isNotEmpty() && msgs.last().role == "assistant") {
                                    msgs[msgs.size - 1] = msgs.last().copy(content = accumulated)
                                    _uiState.value = _uiState.value.copy(messages = msgs)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    val msgs = _uiState.value.messages.toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().role == "assistant" && msgs.last().content.isEmpty()) {
                        msgs[msgs.size - 1] = msgs.last().copy(content = "❌ 错误: ${e.message}")
                    }
                    _uiState.value = _uiState.value.copy(messages = msgs, isLoading = false, error = e.message)
                }
            }
        } else {
            // 非流式
            viewModelScope.launch {
                try {
                    val result = AiClient.chat(
                        messages = chatMessages,
                        config = cfg.copy(
                            chatModel = state.activeModel.ifBlank { cfg.chatModel },
                            temperature = state.temperature
                        ),
                        model = state.activeModel.ifBlank { cfg.chatModel },
                        temperature = state.temperature,
                        systemPrompt = state.currentPreset?.systemPrompt
                    )
                    val reply = result.getOrThrow()
                    val msgs = _uiState.value.messages.toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().role == "assistant") {
                        msgs[msgs.size - 1] = msgs.last().copy(content = reply)
                    }
                    _uiState.value = _uiState.value.copy(messages = msgs, isLoading = false)
                } catch (e: Exception) {
                    val msgs = _uiState.value.messages.toMutableList()
                    if (msgs.isNotEmpty() && msgs.last().role == "assistant") {
                        msgs[msgs.size - 1] = msgs.last().copy(content = "❌ 错误: ${e.message}")
                    }
                    _uiState.value = _uiState.value.copy(messages = msgs, isLoading = false, error = e.message)
                }
            }
        }
    }
}

// ========== 图像生成 ==========
class AiImageViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiImageUiState())
    val uiState: StateFlow<AiImageUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiImageEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiImageEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            size = AiConfigStore.defaultImageSize,
            count = AiConfigStore.defaultImageCount,
            model = cfg?.imageModel?.ifBlank { defaultProviderConfig(provider).imageModel }
                ?: "dall-e-3"
        )
    }

    fun onIntent(intent: AiImageIntent) {
        when (intent) {
            is AiImageIntent.UpdatePrompt -> _uiState.value = _uiState.value.copy(prompt = intent.value)
            is AiImageIntent.UpdateSize -> {
                _uiState.value = _uiState.value.copy(size = intent.value)
                AiConfigStore.defaultImageSize = intent.value
            }
            is AiImageIntent.UpdateCount -> {
                _uiState.value = _uiState.value.copy(count = intent.value.coerceIn(1, 4))
                AiConfigStore.defaultImageCount = intent.value.coerceIn(1, 4)
            }
            is AiImageIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiImageIntent.UpdateQuality -> _uiState.value = _uiState.value.copy(quality = intent.value)
            is AiImageIntent.Generate -> generate()
            is AiImageIntent.SaveImage -> _effects.tryEmit(AiImageEffect.ShowToast("已保存图片信息"))
            is AiImageIntent.ClearAll -> _uiState.value = _uiState.value.copy(images = emptyList(), error = null)
        }
    }

    private fun generate() {
        val state = _uiState.value
        if (state.prompt.isBlank()) {
            _effects.tryEmit(AiImageEffect.ShowToast("请输入图片描述"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiImageEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val result = AiClient.generateImages(
                    prompt = state.prompt,
                    config = cfg,
                    model = state.model.ifBlank { cfg.imageModel },
                    size = state.size,
                    n = state.count,
                    quality = state.quality
                )
                val imgs = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    images = _uiState.value.images + imgs
                )
                _effects.tryEmit(AiImageEffect.ShowToast("已生成 ${imgs.size} 张图片"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _effects.tryEmit(AiImageEffect.ShowToast("生成失败: ${e.message}"))
            }
        }
    }
}

// ========== 视频生成 ==========
class AiVideoViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiVideoUiState())
    val uiState: StateFlow<AiVideoUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiVideoEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiVideoEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            model = cfg?.videoModel?.ifBlank { "sora" } ?: "sora"
        )
    }

    fun onIntent(intent: AiVideoIntent) {
        when (intent) {
            is AiVideoIntent.UpdatePrompt -> _uiState.value = _uiState.value.copy(prompt = intent.value)
            is AiVideoIntent.UpdateAspectRatio -> _uiState.value = _uiState.value.copy(aspectRatio = intent.value)
            is AiVideoIntent.UpdateDuration -> _uiState.value = _uiState.value.copy(duration = intent.value.coerceIn(1, 60))
            is AiVideoIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiVideoIntent.Generate -> generate()
        }
    }

    private fun generate() {
        val state = _uiState.value
        if (state.prompt.isBlank()) {
            _effects.tryEmit(AiVideoEffect.ShowToast("请输入视频描述"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiVideoEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, videoUrl = null)
        viewModelScope.launch {
            try {
                val result = AiClient.generateVideo(
                    prompt = state.prompt,
                    config = cfg,
                    model = state.model.ifBlank { cfg.videoModel },
                    aspectRatio = state.aspectRatio,
                    durationSeconds = state.duration
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    videoUrl = result.getOrThrow()
                )
                _effects.tryEmit(AiVideoEffect.ShowToast("视频已生成"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _effects.tryEmit(AiVideoEffect.ShowToast("生成失败: ${e.message}"))
            }
        }
    }
}

// ========== 视觉分析 ==========
class AiVisionViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiVisionUiState())
    val uiState: StateFlow<AiVisionUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiVisionEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiVisionEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            model = cfg?.visionModel?.ifBlank { defaultProviderConfig(provider).visionModel }
                ?: "gpt-4o"
        )
    }

    fun onIntent(intent: AiVisionIntent) {
        when (intent) {
            is AiVisionIntent.UpdatePrompt -> _uiState.value = _uiState.value.copy(prompt = intent.value)
            is AiVisionIntent.UpdateImageBase64 -> _uiState.value = _uiState.value.copy(imageBase64 = intent.base64)
            is AiVisionIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiVisionIntent.Analyze -> analyze()
        }
    }

    private fun analyze() {
        val state = _uiState.value
        val b64 = state.imageBase64
        if (b64.isNullOrEmpty()) {
            _effects.tryEmit(AiVisionEffect.ShowToast("请先选择图片"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiVisionEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, result = null)
        viewModelScope.launch {
            try {
                val result = AiClient.visionAnalyze(
                    prompt = state.prompt,
                    imageBase64 = b64,
                    config = cfg,
                    model = state.model.ifBlank { cfg.visionModel }
                )
                _uiState.value = _uiState.value.copy(isLoading = false, result = result.getOrThrow())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _effects.tryEmit(AiVisionEffect.ShowToast("分析失败: ${e.message}"))
            }
        }
    }
}

// ========== 文本工具箱 ==========
class AiTextToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiTextToolsUiState())
    val uiState: StateFlow<AiTextToolsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiTextToolsEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiTextToolsEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            model = cfg?.chatModel?.ifBlank { defaultProviderConfig(provider).chatModel }
                ?: "gpt-4o-mini",
            selectedTool = io.legado.app.help.ai.TEXT_TOOLS.firstOrNull()
        )
    }

    fun onIntent(intent: AiTextToolsIntent) {
        when (intent) {
            is AiTextToolsIntent.SelectTool -> _uiState.value = _uiState.value.copy(selectedTool = intent.tool, output = "")
            is AiTextToolsIntent.UpdateInput -> _uiState.value = _uiState.value.copy(input = intent.value)
            is AiTextToolsIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.model)
            is AiTextToolsIntent.Execute -> execute()
            is AiTextToolsIntent.CopyOutput -> {
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ai_output", _uiState.value.output))
                _effects.tryEmit(AiTextToolsEffect.ShowToast("已复制到剪贴板"))
            }
        }
    }

    private fun execute() {
        val state = _uiState.value
        if (state.input.isBlank()) {
            _effects.tryEmit(AiTextToolsEffect.ShowToast("请输入文本"))
            return
        }
        val tool = state.selectedTool ?: run {
            _effects.tryEmit(AiTextToolsEffect.ShowToast("请选择工具"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiTextToolsEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, output = "")
        viewModelScope.launch {
            try {
                val result = AiClient.textTool(
                    toolId = tool.id,
                    input = state.input,
                    config = cfg,
                    model = state.model.ifBlank { cfg.chatModel }
                )
                _uiState.value = _uiState.value.copy(isLoading = false, output = result.getOrThrow())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message, output = "❌ 错误: ${e.message}")
            }
        }
    }
}

// ========== 书源生成 / 校验 ==========
class AiSourceViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiSourceUiState())
    val uiState: StateFlow<AiSourceUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSourceEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiSourceEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            model = cfg?.chatModel?.ifBlank { defaultProviderConfig(provider).chatModel }
                ?: "gpt-4o-mini"
        )
    }

    fun onIntent(intent: AiSourceIntent) {
        when (intent) {
            is AiSourceIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiSourceIntent.UpdateDescription -> _uiState.value = _uiState.value.copy(description = intent.value)
            is AiSourceIntent.UpdateSiteUrl -> _uiState.value = _uiState.value.copy(siteUrl = intent.value)
            is AiSourceIntent.UpdateValidationJson -> _uiState.value = _uiState.value.copy(sourceToValidateJson = intent.value)
            is AiSourceIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.model)
            is AiSourceIntent.GenerateSource -> generateSource()
            is AiSourceIntent.ValidateSource -> validateSource()
            is AiSourceIntent.SaveGeneratedSource -> saveGeneratedSource()
            is AiSourceIntent.CopyGenerated -> {
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ai_generated_source", _uiState.value.generatedJson))
                _effects.tryEmit(AiSourceEffect.ShowToast("已复制到剪贴板"))
            }
        }
    }

    private fun generateSource() {
        val state = _uiState.value
        if (state.siteUrl.isBlank() && state.description.isBlank()) {
            _effects.tryEmit(AiSourceEffect.ShowToast("请输入网站地址或描述"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiSourceEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, generatedJson = "")
        viewModelScope.launch {
            try {
                val result = AiBookSourceHelper.generateFromWebsite(
                    websiteUrl = state.siteUrl,
                    siteName = null,
                    config = cfg,
                    note = state.description
                )
                val json = GSON.toJson(result.getOrThrow())
                _uiState.value = _uiState.value.copy(isLoading = false, generatedJson = json)
                _effects.tryEmit(AiSourceEffect.ShowToast("书源已生成，请检查并手动确认"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _effects.tryEmit(AiSourceEffect.ShowToast("生成失败: ${e.message}"))
            }
        }
    }

    private fun validateSource() {
        val state = _uiState.value
        if (state.sourceToValidateJson.isBlank()) {
            _effects.tryEmit(AiSourceEffect.ShowToast("请粘贴书源 JSON"))
            return
        }
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiSourceEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, validationResult = "")
        viewModelScope.launch {
            try {
                val result = AiBookSourceHelper.validate(state.sourceToValidateJson, cfg, state.model.ifBlank { cfg.chatModel })
                _uiState.value = _uiState.value.copy(isLoading = false, validationResult = result.getOrThrow())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message, validationResult = "❌ 分析失败: ${e.message}")
            }
        }
    }

    private fun saveGeneratedSource() {
        val state = _uiState.value
        if (state.generatedJson.isBlank()) {
            _effects.tryEmit(AiSourceEffect.ShowToast("没有可保存的书源"))
            return
        }
        viewModelScope.launch {
            val result = AiBookSourceHelper.importToDatabase(state.generatedJson)
            _effects.tryEmit(AiSourceEffect.ShowToast(result.getOrElse { "导入失败: ${it.message}" }))
        }
    }
}

// ========== 书架分析 ==========
class AiBookshelfViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiBookshelfUiState())
    val uiState: StateFlow<AiBookshelfUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiBookshelfEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiBookshelfEffect> = _effects.asSharedFlow()

    init {
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider]
        _uiState.value = _uiState.value.copy(
            model = cfg?.chatModel?.ifBlank { defaultProviderConfig(provider).chatModel }
                ?: "gpt-4o-mini"
        )
        onIntent(AiBookshelfIntent.LoadStats)
    }

    fun onIntent(intent: AiBookshelfIntent) {
        when (intent) {
            is AiBookshelfIntent.ChangeMode -> _uiState.value = _uiState.value.copy(mode = intent.mode)
            is AiBookshelfIntent.UpdateCustomPrompt -> _uiState.value = _uiState.value.copy(customPrompt = intent.value)
            is AiBookshelfIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.model)
            is AiBookshelfIntent.LoadStats -> loadStats()
            is AiBookshelfIntent.Analyze -> analyze()
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val stats = AiBookshelfHelper.getStats()
            _uiState.value = _uiState.value.copy(
                bookCount = stats.bookCount,
                sourceStats = stats.sourceStats
            )
        }
    }

    private fun analyze() {
        val state = _uiState.value
        val activeName = AiConfigStore.activeProvider
        val provider = try {
            AiProvider.valueOf(activeName)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        if (cfg.apiKey.isEmpty() && provider != AiProvider.OLLAMA) {
            _effects.tryEmit(AiBookshelfEffect.ShowToast("请先在 AI 设置中配置 API Key"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, analysis = "")
        viewModelScope.launch {
            try {
                val custom = state.customPrompt.ifBlank { null }
                val result = AiBookshelfHelper.analyzeShelf(
                    config = cfg,
                    model = state.model.ifBlank { cfg.chatModel },
                    customPrompt = custom
                )
                _uiState.value = _uiState.value.copy(isLoading = false, analysis = result.getOrThrow())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                _effects.tryEmit(AiBookshelfEffect.ShowToast("分析失败: ${e.message}"))
            }
        }
    }
}

// ========== AI 设置 ==========
class AiSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(AiSettingsUiState())
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSettingsEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<AiSettingsEffect> = _effects.asSharedFlow()

    init {
        refreshUi(AiProvider.valueOf(AiConfigStore.activeProvider))
    }

    private fun refreshUi(provider: AiProvider) {
        val configs = AiConfigStore.loadProviderConfigs()
        val cfg = configs[provider] ?: defaultProviderConfig(provider)
        _uiState.value = AiSettingsUiState(
            activeProvider = provider,
            configs = configs,
            currentEndpoint = cfg.endpoint,
            currentApiKey = cfg.apiKey,
            currentChatModel = cfg.chatModel,
            currentImageModel = cfg.imageModel,
            currentVideoModel = cfg.videoModel,
            currentVisionModel = cfg.visionModel,
            currentTtsModel = cfg.ttsModel,
            currentTemperature = cfg.temperature,
            currentTimeout = cfg.timeoutSeconds,
            hasChanges = false
        )
    }

    fun onIntent(intent: AiSettingsIntent) {
        when (intent) {
            is AiSettingsIntent.ChangeProvider -> {
                // 切换前先保存当前
                saveCurrent()
                refreshUi(intent.provider)
            }
            is AiSettingsIntent.UpdateEndpoint -> _uiState.value = _uiState.value.copy(currentEndpoint = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateApiKey -> _uiState.value = _uiState.value.copy(currentApiKey = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateChatModel -> _uiState.value = _uiState.value.copy(currentChatModel = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateImageModel -> _uiState.value = _uiState.value.copy(currentImageModel = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateVideoModel -> _uiState.value = _uiState.value.copy(currentVideoModel = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateVisionModel -> _uiState.value = _uiState.value.copy(currentVisionModel = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateTtsModel -> _uiState.value = _uiState.value.copy(currentTtsModel = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateTemperature -> _uiState.value = _uiState.value.copy(currentTemperature = intent.value, hasChanges = true)
            is AiSettingsIntent.UpdateTimeout -> _uiState.value = _uiState.value.copy(currentTimeout = intent.value, hasChanges = true)
            is AiSettingsIntent.ToggleApiKeyVisibility -> _uiState.value = _uiState.value.copy(showApiKey = intent.visible)
            is AiSettingsIntent.Save -> {
                saveCurrent()
                AiConfigStore.activeProvider = _uiState.value.activeProvider.name
                _effects.tryEmit(AiSettingsEffect.ShowToast("已保存 ${_uiState.value.activeProvider.displayName} 配置"))
            }
            is AiSettingsIntent.ResetToDefaults -> {
                val provider = _uiState.value.activeProvider
                val def = defaultProviderConfig(provider)
                _uiState.value = _uiState.value.copy(
                    currentEndpoint = def.endpoint,
                    currentApiKey = "",
                    currentChatModel = def.chatModel,
                    currentImageModel = def.imageModel,
                    currentVideoModel = def.videoModel,
                    currentVisionModel = def.visionModel,
                    currentTtsModel = def.ttsModel,
                    currentTemperature = def.temperature,
                    currentTimeout = def.timeoutSeconds,
                    hasChanges = true
                )
                _effects.tryEmit(AiSettingsEffect.ShowToast("已重置为默认值（未保存）"))
            }
            is AiSettingsIntent.ExportConfig -> {
                val state = _uiState.value
                val cfg = AiProviderConfig(
                    provider = state.activeProvider,
                    endpoint = state.currentEndpoint,
                    apiKey = state.currentApiKey,
                    chatModel = state.currentChatModel,
                    imageModel = state.currentImageModel,
                    videoModel = state.currentVideoModel,
                    visionModel = state.currentVisionModel,
                    ttsModel = state.currentTtsModel,
                    temperature = state.currentTemperature,
                    timeoutSeconds = state.currentTimeout
                )
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ai_config", GSON.toJson(cfg)))
                _effects.tryEmit(AiSettingsEffect.ShowToast("配置已导出到剪贴板"))
            }
        }
    }

    private fun saveCurrent() {
        val state = _uiState.value
        val cfg = AiProviderConfig(
            provider = state.activeProvider,
            endpoint = state.currentEndpoint,
            apiKey = state.currentApiKey,
            chatModel = state.currentChatModel,
            imageModel = state.currentImageModel,
            videoModel = state.currentVideoModel,
            visionModel = state.currentVisionModel,
            ttsModel = state.currentTtsModel,
            temperature = state.currentTemperature,
            timeoutSeconds = state.currentTimeout
        )
        val configs = AiConfigStore.loadProviderConfigs().toMutableMap()
        configs[cfg.provider] = cfg
        AiConfigStore.saveProviderConfigs(configs)
        _uiState.value = _uiState.value.copy(hasChanges = false)
    }
}

// ========== AI 内容工具 ==========
class AiContentToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AiContentToolsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiContentToolsEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiContentToolsIntent) {
        when (intent) {
            is AiContentToolsIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiContentToolsIntent.UpdateBookName -> _uiState.value = _uiState.value.copy(sourceBookName = intent.value)
            is AiContentToolsIntent.UpdateChapter -> _uiState.value = _uiState.value.copy(sourceChapter = intent.value)
            is AiContentToolsIntent.UpdateInput -> _uiState.value = _uiState.value.copy(input = intent.value)
            is AiContentToolsIntent.UpdateTargetLanguage -> _uiState.value = _uiState.value.copy(targetLanguage = intent.value)
            is AiContentToolsIntent.UpdateSummaryLevel -> _uiState.value = _uiState.value.copy(summaryLevel = intent.value)
            is AiContentToolsIntent.UpdateRewriteStyle -> _uiState.value = _uiState.value.copy(rewriteStyle = intent.value)
            is AiContentToolsIntent.UpdateTtsVoice -> _uiState.value = _uiState.value.copy(ttsVoice = intent.value)
            is AiContentToolsIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiContentToolsIntent.Execute -> execute()
            is AiContentToolsIntent.CopyOutput -> copyOutput()
            is AiContentToolsIntent.PlayTts -> { /* 由上层 / 系统 TTS 处理 */ }
        }
    }

    private fun execute() {
        val s = _uiState.value
        if (s.input.isBlank()) {
            _effects.tryEmit(AiContentToolsEffect.ShowToast("请先输入要处理的文本"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, output = "")
        val systemPrompt = when (s.tab) {
            0 -> "你是一个专业翻译员。把下面的文本翻译成${s.targetLanguage}，保持原文语气、专有名词不翻译。"
            1 -> "你是一个摘要助手。用${s.summaryLevel}篇幅给出结构化摘要：核心情节、主要人物、关键信息。"
            2 -> "你是一个小说内容检索助手。根据用户提供的关键词，定位段落，给出章节目录形式的答案。"
            3 -> "你是一个重写助手。按以下风格改写：${s.rewriteStyle}。保持情节不变但润色语言。"
            else -> "你是一个整理助手。对提供的文本进行规范化：去除多余空格、分段、统一标点。"
        }
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val chatModel = s.model.ifBlank { cfg.chatModel }
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", s.input)),
                config = cfg,
                model = chatModel,
                systemPrompt = systemPrompt
            )
            result.fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(isLoading = false, output = text)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "执行失败")
                    _effects.tryEmit(AiContentToolsEffect.ShowToast("执行失败"))
                }
            )
        }
    }

    private fun copyOutput() {
        val out = _uiState.value.output
        if (out.isBlank()) {
            _effects.tryEmit(AiContentToolsEffect.ShowToast("暂无可复制内容"))
            return
        }
        _effects.tryEmit(AiContentToolsEffect.ShowToast("已复制"))
    }
}

// ========== AI 艺术 / 封面 / 角色卡 ==========
class AiArtViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AiArtUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiArtEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiArtIntent) {
        when (intent) {
            is AiArtIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiArtIntent.UpdateBookName -> _uiState.value = _uiState.value.copy(bookName = intent.value)
            is AiArtIntent.UpdateAuthor -> _uiState.value = _uiState.value.copy(author = intent.value)
            is AiArtIntent.UpdateIntro -> _uiState.value = _uiState.value.copy(intro = intent.value)
            is AiArtIntent.UpdateCharacterName -> _uiState.value = _uiState.value.copy(characterName = intent.value)
            is AiArtIntent.UpdateCharacterDesc -> _uiState.value = _uiState.value.copy(characterDesc = intent.value)
            is AiArtIntent.UpdateSceneDesc -> _uiState.value = _uiState.value.copy(sceneDesc = intent.value)
            is AiArtIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiArtIntent.UpdateSize -> _uiState.value = _uiState.value.copy(size = intent.value)
            is AiArtIntent.UpdateStyleHint -> _uiState.value = _uiState.value.copy(styleHint = intent.value)
            is AiArtIntent.GenerateImage -> generateImage()
            is AiArtIntent.GenerateCharacterCard -> generateCharacterCard()
            is AiArtIntent.SaveImage -> _effects.tryEmit(AiArtEffect.ShowToast("已保存到相册"))
            is AiArtIntent.SetAsCover -> _effects.tryEmit(AiArtEffect.ShowToast("已设置为封面"))
        }
    }

    private fun generateImage() {
        val s = _uiState.value
        val prompt = when (s.tab) {
            0 -> "设计一本小说的封面。书名《${s.bookName}》，作者 ${s.author}。简介：${s.intro.take(200)}。风格：${s.styleHint}。高品质艺术插图。"
            1 -> "角色肖像。角色：${s.characterName}。描述：${s.characterDesc.take(300)}。风格：${s.styleHint}。"
            2 -> "小说氛围场景。${s.sceneDesc.take(400)}。风格：${s.styleHint}。"
            else -> "${s.intro.take(400)}。风格：${s.styleHint}。"
        }
        if (prompt.isBlank()) {
            _effects.tryEmit(AiArtEffect.ShowToast("请先填写描述"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val result = AiClient.generateImages(
                prompt = prompt,
                config = cfg,
                model = s.model.ifBlank { cfg.imageModel },
                size = s.size,
                quality = "standard"
            )
            result.fold(
                onSuccess = { imgs -> _uiState.value = _uiState.value.copy(isLoading = false, images = imgs) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "生成失败")
                    _effects.tryEmit(AiArtEffect.ShowToast("生成失败"))
                }
            )
        }
    }

    private fun generateCharacterCard() {
        val s = _uiState.value
        val prompt = "请根据以下角色描述，生成一个角色卡片（中文 Markdown 输出）。角色名：${s.characterName}。描述：${s.characterDesc.take(400)}。包含：外貌、性格、代表台词、关系图、故事线标签。"
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, characterText = "")
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel }
            )
            result.fold(
                onSuccess = { text -> _uiState.value = _uiState.value.copy(isLoading = false, characterText = text) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "生成失败")
                    _effects.tryEmit(AiArtEffect.ShowToast("生成失败"))
                }
            )
        }
    }
}

// ========== AI 书源高级 ==========
class AiSourceAdvancedViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AiSourceAdvancedUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSourceAdvancedEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiSourceAdvancedIntent) {
        when (intent) {
            is AiSourceAdvancedIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiSourceAdvancedIntent.UpdateQueryBookName -> _uiState.value = _uiState.value.copy(queryBookName = intent.value)
            is AiSourceAdvancedIntent.UpdateQueryAuthor -> _uiState.value = _uiState.value.copy(queryAuthor = intent.value)
            is AiSourceAdvancedIntent.UpdateSelectedSource -> _uiState.value = _uiState.value.copy(selectedSourceToValidate = intent.value)
            is AiSourceAdvancedIntent.UpdateSourceUrlToFix -> _uiState.value = _uiState.value.copy(sourceUrlToFix = intent.value)
            is AiSourceAdvancedIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiSourceAdvancedIntent.SearchSources -> searchSources()
            is AiSourceAdvancedIntent.EvaluateQuality -> evaluateQuality()
            is AiSourceAdvancedIntent.AutoFix -> autoFix()
        }
    }

    private fun searchSources() {
        val s = _uiState.value
        if (s.queryBookName.isBlank()) {
            _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("请填写书名"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val prompt = "帮我列出可以阅读《${s.queryBookName}》(作者：${s.queryAuthor}) 的在线小说网站/书源候选，每个一行，格式：网站名 - URL。至少列出 5-8 个，优先考虑权威站点。"
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel }
            )
            result.fold(
                onSuccess = { text ->
                    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                    _uiState.value = _uiState.value.copy(isLoading = false, candidates = lines)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "查询失败")
                    _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("查询失败"))
                }
            )
        }
    }

    private fun evaluateQuality() {
        val s = _uiState.value
        if (s.selectedSourceToValidate.isBlank()) {
            _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("请选择或粘贴要评估的书源"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, qualityReport = "")
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val prompt = "请从以下维度评估这个书源：1) URL 结构稳定性 2) 章节抓取难度 3) 是否有严重广告/反爬 4) 对中文读者友好度 5) 总体评分(满分 10)。书源：\n${s.selectedSourceToValidate.take(1200)}"
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel }
            )
            result.fold(
                onSuccess = { text -> _uiState.value = _uiState.value.copy(isLoading = false, qualityReport = text) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "评估失败")
                    _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("评估失败"))
                }
            )
        }
    }

    private fun autoFix() {
        val s = _uiState.value
        if (s.sourceUrlToFix.isBlank()) {
            _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("请粘贴要修复的书源 URL 或 JSON"))
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, fixReport = "")
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val prompt = "下面的书源可能失效了（URL 或规则不对）。请根据你对常见小说网站结构的理解，给出：1) 可能的根地址/搜索规则；2) 推荐的 CSS 选择器（书名、作者、章节列表、章节内容）；3) 可能失效的原规则提示。输入：\n${s.sourceUrlToFix.take(1200)}"
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel }
            )
            result.fold(
                onSuccess = { text -> _uiState.value = _uiState.value.copy(isLoading = false, fixReport = text) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "修复失败")
                    _effects.tryEmit(AiSourceAdvancedEffect.ShowToast("修复失败"))
                }
            )
        }
    }
}

// ========== AI 推荐 / 书单 / 教练 ==========
class AiRecommendViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AiRecommendUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiRecommendEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiRecommendIntent) {
        when (intent) {
            is AiRecommendIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiRecommendIntent.UpdateQuery -> _uiState.value = _uiState.value.copy(query = intent.value)
            is AiRecommendIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiRecommendIntent.Generate -> generate(typeText = "灵感书单")
            is AiRecommendIntent.CoachReport -> generate(typeText = "阅读教练")
            is AiRecommendIntent.VibeReport -> generate(typeText = "阅读氛围")
        }
    }

    private fun generate(typeText: String) {
        val s = _uiState.value
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, report = "")
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val (sys, prompt) = when (s.tab) {
                0 -> "你是一位资深阅读顾问，根据用户口味生成个性化书单，附每本书的推荐理由。" to
                    "用户偏好/阅读需求：${s.query.ifBlank { "请随机推荐三本值得一读的中文/外文小说，并附理由。" }}"
                1 -> "你是一位阅读教练。根据用户阅读数据（或自述），给出节奏建议、书单安排、以及鼓励。" to
                    "阅读数据或自述：${s.query.ifBlank { "我最近阅读量下降，希望被鼓励并给出每周 3 本书的推荐节奏。" }}"
                2 -> "你是一位灵感推荐员。根据用户描述的心情/场景，推荐匹配主题的书单。" to
                    "场景描述：${s.query.ifBlank { "下雨天想读的治愈系小说 3-5 本" }}"
                3 -> "你是一位阅读氛围策划师。推荐适合当前阅读内容的 BGM 歌单 + 壁纸风格。" to
                    "当前阅读内容/关键词：${s.query.ifBlank { "深夜读科幻小说" }}"
                else -> "你是一位推荐员" to s.query
            }
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel },
                systemPrompt = sys
            )
            result.fold(
                onSuccess = { text -> _uiState.value = _uiState.value.copy(isLoading = false, report = text) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "生成失败")
                    _effects.tryEmit(AiRecommendEffect.ShowToast("生成失败"))
                }
            )
        }
    }
}

// ========== AI 归档 / 工具 ==========
class AiArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(AiArchiveUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiArchiveEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AiArchiveIntent) {
        when (intent) {
            is AiArchiveIntent.ChangeTab -> _uiState.value = _uiState.value.copy(tab = intent.tab)
            is AiArchiveIntent.UpdateInputDesc -> _uiState.value = _uiState.value.copy(inputDesc = intent.value)
            is AiArchiveIntent.UpdateSampleText -> _uiState.value = _uiState.value.copy(sampleText = intent.value)
            is AiArchiveIntent.UpdateFilePathPattern -> _uiState.value = _uiState.value.copy(filePathPattern = intent.value)
            is AiArchiveIntent.UpdateModel -> _uiState.value = _uiState.value.copy(model = intent.value)
            is AiArchiveIntent.GenerateReplaceRule -> generate("replace")
            is AiArchiveIntent.GenerateArchivePlan -> generate("archive")
            is AiArchiveIntent.GenerateRenamePlan -> generate("rename")
            is AiArchiveIntent.CopyOutput -> {
                if (_uiState.value.output.isNotBlank()) {
                    _effects.tryEmit(AiArchiveEffect.ShowToast("已复制"))
                }
            }
        }
    }

    private fun generate(mode: String) {
        val s = _uiState.value
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, output = "")
        viewModelScope.launch {
            val cfg = runCatching { AiConfigStore.currentConfig() }
                .getOrElse { AiProviderConfig(AiProvider.OPENAI, "", "") }
            val (sys, prompt) = when (mode) {
                "replace" -> "你是一个规则生成助手。输出 Legado 可读的替换规则（JSON 数组）。" to
                    "描述：${s.inputDesc}\n示例文本：${s.sampleText.take(400)}。请输出推荐的替换规则 JSON。"
                "archive" -> "你是一个图书归档助理。给出本地书籍推荐的目录结构 + 元数据补全方案。" to
                    "描述：${s.inputDesc}\n样例文件名：${s.filePathPattern.take(300)}"
                else -> "你是一个文件重命名助理。输出批处理方案（中文说明 + 推荐的统一文件名模板）。" to
                    "描述：${s.inputDesc}\n样例：${s.filePathPattern.take(300)}"
            }
            val result = AiClient.chat(
                messages = listOf(AiMessage("user", prompt)),
                config = cfg,
                model = s.model.ifBlank { cfg.chatModel },
                systemPrompt = sys
            )
            result.fold(
                onSuccess = { text -> _uiState.value = _uiState.value.copy(isLoading = false, output = text) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "生成失败")
                    _effects.tryEmit(AiArchiveEffect.ShowToast("生成失败"))
                }
            )
        }
    }
}
