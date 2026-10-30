package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaTranslationPage
import io.legado.app.domain.model.manga.MangaTranslationResult
import io.legado.app.domain.model.manga.MangaTranslationSettings
import io.legado.app.domain.model.manga.MangaTranslationGlobalSettings
import io.legado.app.domain.model.manga.MangaTranslationProgress
import kotlinx.coroutines.flow.Flow

interface MangaTranslationGateway {
    fun globalSettings(): Flow<MangaTranslationGlobalSettings>
    suspend fun updateGlobal(transform: (MangaTranslationGlobalSettings) -> MangaTranslationGlobalSettings)
    fun useGlobalSettings(bookUrl: String): Flow<Boolean>
    suspend fun setUseGlobalSettings(bookUrl: String, enabled: Boolean)
    fun settings(bookUrl: String): Flow<MangaTranslationSettings>
    suspend fun update(bookUrl: String, transform: (MangaTranslationSettings) -> MangaTranslationSettings)
    suspend fun glossary(bookUrl: String): Map<String, String>
    suspend fun setGlossary(bookUrl: String, terms: Map<String, String>)
    suspend fun cached(page: MangaTranslationPage): MangaTranslationResult?
    suspend fun translate(page: MangaTranslationPage, force: Boolean = false, progress: (MangaTranslationProgress) -> Unit): MangaTranslationResult
    /** Uses saved text/anchors with SMART or PATCH composition; never invokes an AI service. */
    suspend fun recompose(page: MangaTranslationPage, progress: (MangaTranslationProgress) -> Unit): MangaTranslationResult
    suspend fun clearPage(page: MangaTranslationPage)
    suspend fun clear(bookUrl: String)
}
