package io.legado.app.feature.settings.manga

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.MangaSettings

@Stable
data class MangaConfigUiState(
    val settings: MangaSettings = MangaSettings(),
    val automaticTranslation: Boolean = false,
    val translationLoaded: Boolean = false,
    val nextChapters: Int = 0,
    val previousChapters: Int = 0,
    val error: String? = null,
)

sealed interface MangaConfigIntent {
    data class ChangeSettings(val transform: (MangaSettings) -> MangaSettings) : MangaConfigIntent
    data class SetAutomaticTranslation(val enabled: Boolean) : MangaConfigIntent
    data class SetNextChapters(val count: Int) : MangaConfigIntent
    data class SetPreviousChapters(val count: Int) : MangaConfigIntent
    data object DismissError : MangaConfigIntent
}
