package io.legado.app.domain.gateway

import io.legado.app.domain.model.CoverAlbum
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.model.CoverAlbumSelection
import kotlinx.coroutines.flow.StateFlow

interface CoverAlbumGateway {

    val albums: StateFlow<List<CoverAlbum>>
    val selection: StateFlow<CoverAlbumSelection>

    fun selectedImagePaths(isDark: Boolean): List<String>

    suspend fun createAlbum(name: String): String
    suspend fun importAlbum(
        name: String,
        lightImages: List<CoverAlbumImageInput>,
        darkImages: List<CoverAlbumImageInput>,
    ): String
    suspend fun renameAlbum(albumId: String, name: String)
    suspend fun deleteAlbum(albumId: String)
    suspend fun addImages(
        albumId: String,
        isDark: Boolean,
        images: List<CoverAlbumImageInput>,
    )
    suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String)
    suspend fun selectAlbum(albumId: String?)
}
