package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

enum class MangaImageModel { CTD, BUBBLE, AOT }
enum class MangaModelStatus { MISSING, INSTALLING, VERIFYING, READY, DELETING, FAILED }
data class MangaModelState(
    val model: MangaImageModel, val status: MangaModelStatus = MangaModelStatus.MISSING,
    val bytes: Long = 0, val total: Long = 0, val error: String? = null,
)

/** Import locator is owned by the Android document picker; the source is never modified. */
interface MangaImageModelGateway {
    val states: StateFlow<Map<MangaImageModel, MangaModelState>>
    suspend fun download(model: MangaImageModel)
    suspend fun import(model: MangaImageModel, locator: String)
    suspend fun delete(model: MangaImageModel)
}
