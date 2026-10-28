package io.legado.app.domain.gateway

import io.legado.app.domain.model.MangaTranslationConfig
import io.legado.app.domain.model.MangaTranslationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class MangaTranslationStage { PREPARING, RECOGNIZING, DOWNLOADING, TRANSLATING, RENDERING }

data class MangaTranslationRequest(
    val imageUrl: String,
    val bookUrl: String,
    val sourceOrigin: String?,
    val chapterSubject: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val config: MangaTranslationConfig = MangaTranslationConfig(),
)

/** regionCount is null when reusing a rendered image from cache. */
data class MangaTranslationResult(val imagePath: String?, val regionCount: Int?)

interface MangaTranslationGateway {
    fun observeAiConfiguration(): Flow<MangaTranslationConfig> =
        flowOf(MangaTranslationConfig(MangaTranslationEngine.AI))
    suspend fun clearCache()
    suspend fun translate(
        request: MangaTranslationRequest,
        onStage: (MangaTranslationStage) -> Unit,
    ): MangaTranslationResult
}
