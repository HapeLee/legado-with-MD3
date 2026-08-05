package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.CoverAlbumGateway
import io.legado.app.domain.model.CoverAlbumImageInput

class CoverAlbumUseCase(
    private val gateway: CoverAlbumGateway,
) {

    val albums = gateway.albums
    val selection = gateway.selection

    fun selectedImagePaths(isDark: Boolean) = gateway.selectedImagePaths(isDark)

    suspend fun createAlbum(name: String) = gateway.createAlbum(name)

    suspend fun importAlbum(
        name: String,
        lightImages: List<CoverAlbumImageInput>,
        darkImages: List<CoverAlbumImageInput>,
    ) = gateway.importAlbum(name, lightImages, darkImages)

    suspend fun renameAlbum(albumId: String, name: String) =
        gateway.renameAlbum(albumId, name)

    suspend fun deleteAlbum(albumId: String) = gateway.deleteAlbum(albumId)

    suspend fun addImages(
        albumId: String,
        isDark: Boolean,
        images: List<CoverAlbumImageInput>,
    ) = gateway.addImages(albumId, isDark, images)

    suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String) =
        gateway.removeImage(albumId, isDark, imageId)

    suspend fun selectAlbum(albumId: String?) = gateway.selectAlbum(albumId)
}
