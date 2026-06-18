package io.legado.app.ui.ai

import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import io.legado.app.help.ai.AiPreset
import io.legado.app.help.ai.AiProvider
import io.legado.app.help.ai.AiMessage
import io.legado.app.help.ai.GeneratedImage

// ========== 聊天 ==========
@Stable
data class AiChatUiState(
    val messages: List<AiMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeProvider: AiProvider = AiProvider.OPENAI,
    val activeModel: String = "",
    val temperature: Float = 0.7f,
    val streamEnabled: Boolean = io.legado.app.help.ai.AiConfigStore.streamEnabled,
    val presets: List<AiPreset> = emptyList(),
    val currentPreset: AiPreset? = null
)

sealed interface AiChatIntent {
    data class UpdateInput(val value: String) : AiChatIntent
    data object Send : AiChatIntent
    data object Stop : AiChatIntent
    data class ChangePreset(val preset: AiPreset) : AiChatIntent
    data class ChangeProvider(val provider: AiProvider) : AiChatIntent
    data class ChangeModel(val model: String) : AiChatIntent
    data class UpdateTemperature(val value: Float) : AiChatIntent
    data object ClearConversation : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowToast(val message: String) : AiChatEffect
}

// ========== 图像 ==========
@Stable
data class AiImageUiState(
    val prompt: String = "",
    val size: String = "1024x1024",
    val count: Int = 1,
    val model: String = "dall-e-3",
    val quality: String = "standard",
    val isLoading: Boolean = false,
    val error: String? = null,
    val images: List<GeneratedImage> = emptyList()
)

sealed interface AiImageIntent {
    data class UpdatePrompt(val value: String) : AiImageIntent
    data class UpdateSize(val value: String) : AiImageIntent
    data class UpdateCount(val value: Int) : AiImageIntent
    data class UpdateModel(val value: String) : AiImageIntent
    data class UpdateQuality(val value: String) : AiImageIntent
    data object Generate : AiImageIntent
    data object SaveImage : AiImageIntent
    data object ClearAll : AiImageIntent
}

sealed interface AiImageEffect {
    data class ShowToast(val message: String) : AiImageEffect
}

// ========== 视频 ==========
@Stable
data class AiVideoUiState(
    val prompt: String = "",
    val aspectRatio: String = "16:9",
    val duration: Int = 10,
    val model: String = "sora",
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoUrl: String? = null
)

sealed interface AiVideoIntent {
    data class UpdatePrompt(val value: String) : AiVideoIntent
    data class UpdateAspectRatio(val value: String) : AiVideoIntent
    data class UpdateDuration(val value: Int) : AiVideoIntent
    data class UpdateModel(val value: String) : AiVideoIntent
    data object Generate : AiVideoIntent
}

sealed interface AiVideoEffect {
    data class ShowToast(val message: String) : AiVideoEffect
}

// ========== 视觉分析 ==========
@Stable
data class AiVisionUiState(
    val prompt: String = "请详细描述这张图片的内容",
    val imageBase64: String? = null,
    val model: String = "gpt-4o",
    val isLoading: Boolean = false,
    val error: String? = null,
    val result: String? = null
)

sealed interface AiVisionIntent {
    data class UpdatePrompt(val value: String) : AiVisionIntent
    data class UpdateImageBase64(val base64: String) : AiVisionIntent
    data class UpdateModel(val value: String) : AiVisionIntent
    data object Analyze : AiVisionIntent
}

sealed interface AiVisionEffect {
    data class ShowToast(val message: String) : AiVisionEffect
}

// ========== 文本工具 ==========
@Stable
data class AiTextToolsUiState(
    val tools: List<io.legado.app.help.ai.TextTool> = io.legado.app.help.ai.TEXT_TOOLS,
    val selectedTool: io.legado.app.help.ai.TextTool? = io.legado.app.help.ai.TEXT_TOOLS.firstOrNull(),
    val input: String = "",
    val output: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface AiTextToolsIntent {
    data class SelectTool(val tool: io.legado.app.help.ai.TextTool) : AiTextToolsIntent
    data class UpdateInput(val value: String) : AiTextToolsIntent
    data class UpdateModel(val value: String) : AiTextToolsIntent
    data object Execute : AiTextToolsIntent
    data object CopyOutput : AiTextToolsIntent
}

sealed interface AiTextToolsEffect {
    data class ShowToast(val message: String) : AiTextToolsEffect
}

// ========== 书源生成 ==========
@Stable
data class AiSourceUiState(
    val tab: Int = 0,
    val description: String = "",
    val siteUrl: String = "",
    val sourceToValidateJson: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val generatedJson: String = "",
    val validationResult: String = ""
)

sealed interface AiSourceIntent {
    data class ChangeTab(val tab: Int) : AiSourceIntent
    data class UpdateDescription(val value: String) : AiSourceIntent
    data class UpdateSiteUrl(val value: String) : AiSourceIntent
    data class UpdateValidationJson(val value: String) : AiSourceIntent
    data class UpdateModel(val value: String) : AiSourceIntent
    data object GenerateSource : AiSourceIntent
    data object ValidateSource : AiSourceIntent
    data object SaveGeneratedSource : AiSourceIntent
    data object CopyGenerated : AiSourceIntent
}

sealed interface AiSourceEffect {
    data class ShowToast(val message: String) : AiSourceEffect
}

// ========== 书架分析 ==========
@Stable
data class AiBookshelfUiState(
    val mode: Int = 0,
    val bookCount: Int = 0,
    val sourceStats: Map<String, Int> = emptyMap(),
    val customPrompt: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val analysis: String = ""
)

sealed interface AiBookshelfIntent {
    data class ChangeMode(val mode: Int) : AiBookshelfIntent
    data class UpdateCustomPrompt(val value: String) : AiBookshelfIntent
    data class UpdateModel(val value: String) : AiBookshelfIntent
    data object LoadStats : AiBookshelfIntent
    data object Analyze : AiBookshelfIntent
}

sealed interface AiBookshelfEffect {
    data class ShowToast(val message: String) : AiBookshelfEffect
}

// ========== AI 设置 ==========
@Stable
data class AiSettingsUiState(
    val activeProvider: AiProvider = AiProvider.OPENAI,
    val configs: Map<AiProvider, io.legado.app.help.ai.AiProviderConfig> = emptyMap(),
    val currentEndpoint: String = "",
    val currentApiKey: String = "",
    val currentChatModel: String = "",
    val currentImageModel: String = "",
    val currentVideoModel: String = "",
    val currentVisionModel: String = "",
    val currentTtsModel: String = "",
    val currentTemperature: Float = 0.7f,
    val currentTimeout: Int = 120,
    val showApiKey: Boolean = false,
    val hasChanges: Boolean = false
)

sealed interface AiSettingsIntent {
    data class ChangeProvider(val provider: AiProvider) : AiSettingsIntent
    data class UpdateEndpoint(val value: String) : AiSettingsIntent
    data class UpdateApiKey(val value: String) : AiSettingsIntent
    data class UpdateChatModel(val value: String) : AiSettingsIntent
    data class UpdateImageModel(val value: String) : AiSettingsIntent
    data class UpdateVideoModel(val value: String) : AiSettingsIntent
    data class UpdateVisionModel(val value: String) : AiSettingsIntent
    data class UpdateTtsModel(val value: String) : AiSettingsIntent
    data class UpdateTemperature(val value: Float) : AiSettingsIntent
    data class UpdateTimeout(val value: Int) : AiSettingsIntent
    data class ToggleApiKeyVisibility(val visible: Boolean) : AiSettingsIntent
    data object Save : AiSettingsIntent
    data object ResetToDefaults : AiSettingsIntent
    data object ExportConfig : AiSettingsIntent
}

sealed interface AiSettingsEffect {
    data class ShowToast(val message: String) : AiSettingsEffect
    data class ExportConfig(val json: String) : AiSettingsEffect
}
