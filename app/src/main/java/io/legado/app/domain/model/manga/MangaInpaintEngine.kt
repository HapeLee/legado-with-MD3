package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
enum class MangaInpaintEngine {
    LOCAL, MI_GAN, LAMA;
    val downloadable: Boolean get() = this != LOCAL
    /** Bump when weights, tensor contract or preprocessing change. */
    val cacheIdentity: String get() = "$name-v1"
}

enum class MangaInpaintModelPhase { CHECKING, MISSING, DOWNLOADING, READY, FAILED }

@Stable
data class MangaInpaintModelState(
    val phase: MangaInpaintModelPhase = MangaInpaintModelPhase.CHECKING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
)
