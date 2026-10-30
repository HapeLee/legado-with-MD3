package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.Flow

data class MangaTranslationPage(
    val key: String,
    val imageUrl: String,
    val chapterId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterSubject: String,
)

enum class MangaTranslationStatus { PENDING, RUNNING, DONE, SKIPPED, FAILED }

data class MangaTranslationTask(
    val page: MangaTranslationPage,
    val status: MangaTranslationStatus = MangaTranslationStatus.PENDING,
    val resultUri: String? = null,
)

data class MangaTranslationBookState(
    val tasks: List<MangaTranslationTask> = emptyList(),
    val running: Boolean = false,
    val showTranslation: Boolean = true,
    val message: String? = null,
    val failureId: Long = 0,
    val failureMessage: String? = null,
)

/** No platform resources cross this boundary. A paused native page may safely finish. */
interface MangaTranslationGateway {
    fun observeBook(bookUrl: String): Flow<MangaTranslationBookState>
    suspend fun enqueueChapter(bookUrl: String, pages: List<MangaTranslationPage>, preferredKey: String?)
    suspend fun resume(bookUrl: String)
    suspend fun pause(bookUrl: String)
    suspend fun prioritize(bookUrl: String, pageKey: String)
    suspend fun setShowTranslation(bookUrl: String, show: Boolean)
    suspend fun clearAll()
    suspend fun withModelsPaused(action: suspend () -> Unit)
    suspend fun clearBook(bookUrl: String)
    suspend fun validateLoadedPages(bookUrl: String, pages: List<MangaTranslationPage>)
    /** Call while models are paused, after changing the translation configuration. */
    suspend fun invalidateConfiguration()
    /** Synchronous lifetime handoff; implementations finish the current native page in their own scope. */
    fun detach(bookUrl: String)
}
