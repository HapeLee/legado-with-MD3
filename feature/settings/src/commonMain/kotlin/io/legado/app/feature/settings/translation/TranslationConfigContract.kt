package io.legado.app.feature.settings.translation

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.TranslationSettings

// M5-2d：从 `:app` 的 `io.legado.app.ui.config.translation` 迁来（**只改包名**，
// 结构与迁移前逐字一致）。
//
// `TranslationConfigEffect` 是**空的** sealed interface——迁移前就是这样：VM 准备了
// `effects` 流（`extraBufferCapacity = 16`）但没有任何分支用到它。保留而不是删除，
// 因为删除它会连带改 VM 的 `effects` 字段（那是行为之外的重构，本片不做）。
@Stable
data class TranslationConfigUiState(
    val settings: TranslationSettings = TranslationSettings(),
)

sealed interface TranslationConfigIntent {
    data class SetProvider(val value: String) : TranslationConfigIntent
    data class SetTargetLanguage(val value: String) : TranslationConfigIntent
    data class SetMaxCharsPerChunk(val value: Int) : TranslationConfigIntent
}

sealed interface TranslationConfigEffect
