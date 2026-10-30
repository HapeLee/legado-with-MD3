package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

data class MangaModelItem(
    val name: String,
    val role: String,
    val size: Long,
    val installed: Boolean = false,
    val progress: Int? = null,
    val status: MangaModelStatus = MangaModelStatus.MISSING,
    val error: String? = null,
)

enum class MangaModelStatus { MISSING, READY, VERIFYING, DOWNLOADING, IMPORTING, DELETING, FAILED }

/** A host-owned source; transfer must stream chunks rather than buffer a whole model. */
interface MangaModelSource {
    val name: String
    suspend fun transfer(write: (ByteArray, Int) -> Unit)
}

interface MangaModelGateway {
    val models: StateFlow<List<MangaModelItem>>
    val busy: StateFlow<Boolean>
    val importErrors: StateFlow<List<String>>
    fun refresh()
    fun download(name: String? = null)
    fun importModels(sources: List<MangaModelSource>)
    fun delete(name: String)
    fun cancel()
}
