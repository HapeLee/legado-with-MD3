package io.legado.app.domain.model

import java.io.InputStream

data class CoverAlbum(
    val id: String,
    val name: String,
    val lightImages: List<CoverAlbumImage>,
    val darkImages: List<CoverAlbumImage>,
)

data class CoverAlbumImage(
    val id: String,
    val fileName: String,
    val path: String,
)

data class CoverAlbumSelection(
    val albumId: String? = null,
)

data class CoverAlbumImageInput(
    val displayName: String,
    val openStream: () -> InputStream,
)
