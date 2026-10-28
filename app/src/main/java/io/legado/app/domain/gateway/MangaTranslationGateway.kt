package io.legado.app.domain.gateway

enum class MangaTranslationStage { PREPARING, RECOGNIZING, DOWNLOADING, TRANSLATING, RENDERING }

data class MangaTranslationRequest(
    val imageUrl: String,
    val bookUrl: String,
    val sourceOrigin: String?,
    val chapterSubject: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)

/** regionCount is null when reusing a rendered image from cache. */
data class MangaTranslationResult(val imagePath: String?, val regionCount: Int?)

interface MangaTranslationGateway {
    suspend fun clearCache()
    suspend fun translate(
        request: MangaTranslationRequest,
        onStage: (MangaTranslationStage) -> Unit,
    ): MangaTranslationResult
}
