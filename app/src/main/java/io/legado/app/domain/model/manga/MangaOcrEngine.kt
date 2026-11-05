package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
enum class MangaLocalOcrEngine {
    ML_KIT, PADDLE_V5, PADDLE_V6_SMALL, PADDLE_V6_MEDIUM, PADDLE_V6_MANGA,
    PADDLE_V5_KOREAN, PADDLE_V5_ESLAV, PADDLE_V6_TINY;

    val downloadable: Boolean get() = this != ML_KIT
    val sourceLanguages: List<String> get() = when (this) {
        ML_KIT -> listOf("ja", "zh", "ko", "en")
        PADDLE_V5_KOREAN -> listOf("ko", "en")
        PADDLE_V5_ESLAV -> listOf("ru", "uk", "be", "bg", "en")
        PADDLE_V6_TINY -> listOf("zh", "en")
        PADDLE_V6_MANGA -> listOf("ja")
        else -> listOf("ja", "zh", "en")
    }
}

enum class MangaOcrModelPhase { CHECKING, MISSING, DOWNLOADING, READY, FAILED }

@Stable
data class MangaOcrModelState(
    val phase: MangaOcrModelPhase = MangaOcrModelPhase.CHECKING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
)
