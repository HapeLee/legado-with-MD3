package io.legado.app.domain.gateway

import io.legado.app.domain.model.MangaTranslationConfig
import io.legado.app.domain.model.MangaTranslationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

enum class MangaTranslationStage { PREPARING, RECOGNIZING, DOWNLOADING, TRANSLATING, RENDERING }

enum class MangaRecognitionStep { OCR_INITIALIZATION, OCR, CTD, BUBBLE }

/** Contains a safe diagnostic identifier, never recognized text or image URLs. */
class MangaRecognitionException(val step: MangaRecognitionStep, val diagnosticCode: String) :
    Exception(step.name)

@Serializable
enum class MangaLayoutDirection { AUTO, HORIZONTAL, VERTICAL }

data class MangaTranslationRequest(
    val imageUrl: String,
    val bookUrl: String,
    val sourceOrigin: String?,
    val chapterSubject: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val config: MangaTranslationConfig = MangaTranslationConfig(),
    val layoutDirection: MangaLayoutDirection = MangaLayoutDirection.AUTO,
    val fontScale: Float = 1f,
    val translationOverrides: Map<String, String> = emptyMap(),
)

/** regionCount is null when reusing a rendered image from cache. */
@Serializable
enum class MangaRegionFailure { REPAIR, LAYOUT, TOO_LARGE, MODEL }

@Serializable
data class MangaRegionTranslation(
    val id: Int, val source: String, val translation: String,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val failure: MangaRegionFailure? = null,
    val regionKey: String = "",
)

@Serializable
data class MangaTranslationResult(
    val imagePath: String?, val regionCount: Int?,
    val regions: List<MangaRegionTranslation> = emptyList(),
) {
    val pendingCount: Int get() = regions.count { it.failure != null }
}

interface MangaTranslationGateway {
    fun observeAiConfiguration(): Flow<MangaTranslationConfig> =
        flowOf(MangaTranslationConfig(MangaTranslationEngine.AI))
    suspend fun clearCache()
    suspend fun translate(
        request: MangaTranslationRequest,
        onStage: (MangaTranslationStage) -> Unit,
    ): MangaTranslationResult
}
