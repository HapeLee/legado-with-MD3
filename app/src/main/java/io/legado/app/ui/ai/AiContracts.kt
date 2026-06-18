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

// ========== AI 内容工具 (翻译/摘要/检索/重写) ==========
@Stable
data class AiContentToolsUiState(
    val tabs: List<String> = listOf("翻译", "摘要", "智能检索", "风格重写", "章节朗读"),
    val tab: Int = 0,
    val sourceBookName: String = "",
    val sourceChapter: String = "",
    val input: String = "",
    val targetLanguage: String = "中文",
    val summaryLevel: String = "标准", // 简洁 / 标准 / 详细
    val rewriteStyle: String = "文学化",
    val ttsVoice: String = "default",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val output: String = "",
    val audioFile: String? = null
)

sealed interface AiContentToolsIntent {
    data class ChangeTab(val tab: Int) : AiContentToolsIntent
    data class UpdateBookName(val value: String) : AiContentToolsIntent
    data class UpdateChapter(val value: String) : AiContentToolsIntent
    data class UpdateInput(val value: String) : AiContentToolsIntent
    data class UpdateTargetLanguage(val value: String) : AiContentToolsIntent
    data class UpdateSummaryLevel(val value: String) : AiContentToolsIntent
    data class UpdateRewriteStyle(val value: String) : AiContentToolsIntent
    data class UpdateTtsVoice(val value: String) : AiContentToolsIntent
    data class UpdateModel(val value: String) : AiContentToolsIntent
    data object Execute : AiContentToolsIntent
    data object CopyOutput : AiContentToolsIntent
    data object PlayTts : AiContentToolsIntent
}

sealed interface AiContentToolsEffect {
    data class ShowToast(val message: String) : AiContentToolsEffect
}

// ========== AI 封面 / 角色卡 ==========
@Stable
data class AiArtUiState(
    val tabs: List<String> = listOf("书籍封面", "角色卡", "场景氛围图", "章节插画"),
    val tab: Int = 0,
    val bookName: String = "",
    val author: String = "",
    val intro: String = "",
    val characterName: String = "",
    val characterDesc: String = "",
    val sceneDesc: String = "",
    val model: String = "flux-dev",
    val size: String = "1024x1024",
    val styleHint: String = "中式古风",
    val isLoading: Boolean = false,
    val error: String? = null,
    val images: List<io.legado.app.help.ai.GeneratedImage> = emptyList(),
    val characterText: String = ""
)

sealed interface AiArtIntent {
    data class ChangeTab(val tab: Int) : AiArtIntent
    data class UpdateBookName(val value: String) : AiArtIntent
    data class UpdateAuthor(val value: String) : AiArtIntent
    data class UpdateIntro(val value: String) : AiArtIntent
    data class UpdateCharacterName(val value: String) : AiArtIntent
    data class UpdateCharacterDesc(val value: String) : AiArtIntent
    data class UpdateSceneDesc(val value: String) : AiArtIntent
    data class UpdateModel(val value: String) : AiArtIntent
    data class UpdateSize(val value: String) : AiArtIntent
    data class UpdateStyleHint(val value: String) : AiArtIntent
    data object GenerateImage : AiArtIntent
    data object GenerateCharacterCard : AiArtIntent
    data object SaveImage : AiArtIntent
    data object SetAsCover : AiArtIntent
}

sealed interface AiArtEffect {
    data class ShowToast(val message: String) : AiArtEffect
}

// ========== AI 书源高级 (搜索/评分/自动修源) ==========
@Stable
data class AiSourceAdvancedUiState(
    val tabs: List<String> = listOf("智能搜索书源", "书源质量评分", "自动修源"),
    val tab: Int = 0,
    val queryBookName: String = "",
    val queryAuthor: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val candidates: List<String> = emptyList(),
    val selectedSourceToValidate: String = "",
    val qualityReport: String = "",
    val sourceUrlToFix: String = "",
    val fixReport: String = ""
)

sealed interface AiSourceAdvancedIntent {
    data class ChangeTab(val tab: Int) : AiSourceAdvancedIntent
    data class UpdateQueryBookName(val value: String) : AiSourceAdvancedIntent
    data class UpdateQueryAuthor(val value: String) : AiSourceAdvancedIntent
    data class UpdateSelectedSource(val value: String) : AiSourceAdvancedIntent
    data class UpdateSourceUrlToFix(val value: String) : AiSourceAdvancedIntent
    data class UpdateModel(val value: String) : AiSourceAdvancedIntent
    data object SearchSources : AiSourceAdvancedIntent
    data object EvaluateQuality : AiSourceAdvancedIntent
    data object AutoFix : AiSourceAdvancedIntent
}

sealed interface AiSourceAdvancedEffect {
    data class ShowToast(val message: String) : AiSourceAdvancedEffect
}

// ========== AI 推荐 / 书单 / 阅读教练 ==========
@Stable
data class AiRecommendUiState(
    val tabs: List<String> = listOf("个性化书单", "阅读教练", "灵感书单", "阅读氛围"),
    val tab: Int = 0,
    val query: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val report: String = ""
)

sealed interface AiRecommendIntent {
    data class ChangeTab(val tab: Int) : AiRecommendIntent
    data class UpdateQuery(val value: String) : AiRecommendIntent
    data class UpdateModel(val value: String) : AiRecommendIntent
    data object Generate : AiRecommendIntent
    data object CoachReport : AiRecommendIntent
    data object VibeReport : AiRecommendIntent
}

sealed interface AiRecommendEffect {
    data class ShowToast(val message: String) : AiRecommendEffect
}

// ========== AI 归档 / 工具 ==========
@Stable
data class AiArchiveUiState(
    val tabs: List<String> = listOf("替换规则生成", "本地书归档", "文件重命名"),
    val tab: Int = 0,
    val inputDesc: String = "",
    val sampleText: String = "",
    val filePathPattern: String = "",
    val model: String = "gpt-4o-mini",
    val isLoading: Boolean = false,
    val error: String? = null,
    val output: String = ""
)

sealed interface AiArchiveIntent {
    data class ChangeTab(val tab: Int) : AiArchiveIntent
    data class UpdateInputDesc(val value: String) : AiArchiveIntent
    data class UpdateSampleText(val value: String) : AiArchiveIntent
    data class UpdateFilePathPattern(val value: String) : AiArchiveIntent
    data class UpdateModel(val value: String) : AiArchiveIntent
    data object GenerateReplaceRule : AiArchiveIntent
    data object GenerateArchivePlan : AiArchiveIntent
    data object GenerateRenamePlan : AiArchiveIntent
    data object CopyOutput : AiArchiveIntent
}

sealed interface AiArchiveEffect {
    data class ShowToast(val message: String) : AiArchiveEffect
}
