package io.legado.app.feature.settings.coverconfig

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * M5-12a：从 `:app` 的 `ui/config/coverConfig` 迁来，**逐字**（本文件本就零 Android 依赖：
 * 只用 Compose 的 `Stable` 与 `kotlinx.collections.immutable`）。
 *
 * ⚠️ 本片搬它是**被逼的**：`CoverConfigContract.CoverConfigUiState` 持有
 * `CoverAlbumSelectionUiState`，而后者在 `:app` ⇒ 只搬那一半契约会编译不过。
 * 本文件里的 `CoverAlbumManageUiState` / `CoverAlbumIntent` / `CoverAlbumEffect` /
 * `CoverAlbumDialog` 属于**封面图库那一半**（其 Screen/VM 仍在 `:app`，下一片再迁），
 * 但它们与本文件同一份契约，无法拆开搬 ⇒ 一起上来。
 */
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
