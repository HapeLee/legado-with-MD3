package io.legado.app.feature.manga

import androidx.compose.runtime.Stable

@Stable
data class MangaTranslationState(
    val running: Boolean = false,
    val message: String? = null,
    val resultUri: String? = null,
    val showTranslation: Boolean = true,
)

@Stable
data class MangaTranslationControls(
    val automatic: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val processing: Boolean = false,
    val showTranslation: Boolean = true,
)
