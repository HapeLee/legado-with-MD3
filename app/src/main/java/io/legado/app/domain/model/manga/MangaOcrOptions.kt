package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
enum class MangaOcrDetectionProfile(val maxSide: Int) { FAST(960), ACCURATE(1920) }

/** Null detection overrides follow the selected model's own inference.yml defaults. */
@Stable
@Serializable
data class MangaOcrOptions(
    val detectionProfile: MangaOcrDetectionProfile = MangaOcrDetectionProfile.FAST,
    val probabilityThreshold: Float? = null,
    val boxScoreThreshold: Float? = null,
    val unclipRatio: Float? = null,
    val upscale2x: Boolean = false,
    val invert: Boolean = false,
    val binarize: Boolean = false,
) {
    val hasPreprocessing: Boolean get() = upscale2x || invert || binarize
    val customDetection: Boolean get() = probabilityThreshold != null || boxScoreThreshold != null || unclipRatio != null

    fun validate() {
        require(probabilityThreshold == null || probabilityThreshold in .05f.. .95f) { "Invalid OCR probability threshold" }
        require(boxScoreThreshold == null || boxScoreThreshold in .05f..1f) { "Invalid OCR component score threshold" }
        require(unclipRatio == null || unclipRatio in .5f..3f) { "Invalid OCR unclip ratio" }
    }

    fun detectionThresholds(engine: MangaLocalOcrEngine): MangaOcrDetectionThresholds {
        require(engine.downloadable) { "Detector thresholds require Paddle OCR" }
        validate()
        val defaults = when (engine) {
            MangaLocalOcrEngine.PADDLE_V5, MangaLocalOcrEngine.PADDLE_V5_KOREAN, MangaLocalOcrEngine.PADDLE_V5_ESLAV -> MangaOcrDetectionThresholds(.3f, .6f, 1.5f)
            MangaLocalOcrEngine.PADDLE_V6_TINY -> MangaOcrDetectionThresholds(.2f, .4f, 1.4f)
            else -> MangaOcrDetectionThresholds(.2f, .45f, 1.4f)
        }
        return MangaOcrDetectionThresholds(probabilityThreshold ?: defaults.probability, boxScoreThreshold ?: defaults.score, unclipRatio ?: defaults.unclip)
    }

    /** Preserve old cache identities when all effective options are unchanged. */
    fun analysisIdentity(paddle: Boolean): String = buildString {
        if (hasPreprocessing) append("|ocr-input-v1:$upscale2x:$invert:$binarize")
        if (paddle && (detectionProfile != MangaOcrDetectionProfile.FAST || customDetection))
            append("|ocr-det-v1:$detectionProfile:$probabilityThreshold:$boxScoreThreshold:$unclipRatio")
    }
}

@Stable
data class MangaOcrDetectionThresholds(val probability: Float, val score: Float, val unclip: Float)
