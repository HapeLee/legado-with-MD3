package io.legado.app.ui.book.read

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * AI 域（章节摘要 / 划词净化 / 划词重写 / 重写预设）的状态契约。
 *
 * 由 [ReadAiDelegate] 独立持有，不再挂在 [ReadBookUiState] 上——AI 流式输出每秒刷新多次，
 * 挂在阅读态上会让整个 ReadBookUiState 反复 copy。
 * sheet 的开合仍由 [ReadBookUiState.activeSheet] 单一持有，保持互斥与返回键行为不变。
 */
@Stable
data class ReadAiUiState(
    val chapterSummary: ChapterSummaryUiState = ChapterSummaryUiState(),
    val aiTextClean: AiTextCleanUiState = AiTextCleanUiState(),
    val aiTextRewrite: AiTextRewriteUiState = AiTextRewriteUiState(),
    val aiRewritePresetConfig: AiRewritePresetConfigUiState = AiRewritePresetConfigUiState(),
)

@Stable
data class ChapterSummaryUiState(
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    val isLoading: Boolean = false,
    val summary: String = "",
    val reasoningText: String = "",
    val thinkingDuration: Int = 0,
    val errorMessage: String? = null,
)

@Stable
data class AiTextCleanUiState(
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val originalText: String = "",
    val replacementText: String = "",
    val streamingText: String = "",
    val reasoningText: String = "",
    val thinkingDuration: Int = 0,
    val errorMessage: String? = null,
)

@Stable
data class AiRewritePresetUi(
    val id: String,
    val name: String,
    val instruction: String,
)

@Stable
data class AiRewriteHistoryUi(
    val artifactId: String,
    val text: String,
    val timeText: String,
)

@Stable
data class AiTextRewriteUiState(
    val bookUrl: String = "",
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val originalText: String = "",
    val rewrittenText: String = "",
    val reasoningText: String = "",
    val thinkingDuration: Int = 0,
    val selectedPresetId: String = "",
    val presets: ImmutableList<AiRewritePresetUi> = persistentListOf(),
    val temporaryInstruction: String = "",
    val history: ImmutableList<AiRewriteHistoryUi> = persistentListOf(),
    val referenceCount: Int = 0,
    val errorMessage: String? = null,
)

@Stable
data class AiRewritePresetConfigUiState(
    val presets: ImmutableList<AiRewritePresetUi> = persistentListOf(),
    val editing: Boolean = false,
    val editingPresetId: String? = null,
    val editingName: String = "",
    val editingInstruction: String = "",
    val deletePreset: AiRewritePresetUi? = null,
    val errorMessage: String? = null,
)
