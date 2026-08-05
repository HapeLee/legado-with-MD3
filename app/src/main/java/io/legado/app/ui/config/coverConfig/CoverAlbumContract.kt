package io.legado.app.ui.config.coverConfig

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CoverAlbumItemUi(
    val id: String,
    val name: String,
    val lightImages: ImmutableList<CoverAlbumImageUi> = persistentListOf(),
    val darkImages: ImmutableList<CoverAlbumImageUi> = persistentListOf(),
)

@Stable
data class CoverAlbumImageUi(
    val id: String,
    val path: String,
)

@Stable
data class CoverAlbumSelectionUiState(
    val albums: ImmutableList<CoverAlbumItemUi> = persistentListOf(),
    val selectedAlbumId: String? = null,
)

@Stable
data class CoverAlbumManageUiState(
    val albums: ImmutableList<CoverAlbumItemUi> = persistentListOf(),
    val selectedAlbumId: String? = null,
    val editingAlbumId: String? = null,
    val dialog: CoverAlbumDialog? = null,
)

sealed interface CoverAlbumIntent {
    data object CreateClick : CoverAlbumIntent
    data class EditClick(val albumId: String) : CoverAlbumIntent
    data class RenameClick(val albumId: String) : CoverAlbumIntent
    data class DeleteClick(val albumId: String) : CoverAlbumIntent
    data class SaveName(val name: String) : CoverAlbumIntent
    data object ConfirmDelete : CoverAlbumIntent
    data class AddImagesClick(val albumId: String, val isDark: Boolean) : CoverAlbumIntent
    data class ImagesSelected(
        val albumId: String,
        val isDark: Boolean,
        val uriStrings: List<String>,
    ) : CoverAlbumIntent

    data class RemoveImage(
        val albumId: String,
        val isDark: Boolean,
        val imageId: String,
    ) : CoverAlbumIntent

    data object DismissEditor : CoverAlbumIntent
    data object DismissDialog : CoverAlbumIntent
}

sealed interface CoverAlbumEffect {
    data class SelectImages(val albumId: String, val isDark: Boolean) : CoverAlbumEffect
    data class ShowMessage(val message: String) : CoverAlbumEffect
}

sealed interface CoverAlbumDialog {
    data object Create : CoverAlbumDialog
    data class Rename(val albumId: String, val currentName: String) : CoverAlbumDialog
    data class Delete(val albumId: String, val name: String) : CoverAlbumDialog
}
