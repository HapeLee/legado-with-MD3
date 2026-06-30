package io.legado.app.ui.config.coverConfig

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.usecase.CoverAlbumUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CoverAlbumManageViewModel(
    private val context: Context,
    private val coverAlbumUseCase: CoverAlbumUseCase,
) : ViewModel() {

    private val editingAlbumId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<CoverAlbumDialog?>(null)
    private val _effects = MutableSharedFlow<CoverAlbumEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    val uiState = combine(
        coverAlbumUseCase.albums,
        coverAlbumUseCase.selection,
        editingAlbumId,
        dialog,
    ) { albums, selection, editingId, activeDialog ->
        CoverAlbumManageUiState(
            albums = albums.map { it.toUi() }.toImmutableList(),
            selectedAlbumId = selection.albumId,
            editingAlbumId = editingId,
            dialog = activeDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CoverAlbumManageUiState(),
    )

    fun onIntent(intent: CoverAlbumIntent) {
        when (intent) {
            CoverAlbumIntent.CreateClick -> dialog.value = CoverAlbumDialog.Create
            is CoverAlbumIntent.EditClick -> editingAlbumId.value = intent.albumId
            is CoverAlbumIntent.RenameClick -> {
                val album = uiState.value.albums.firstOrNull { it.id == intent.albumId } ?: return
                dialog.value = CoverAlbumDialog.Rename(album.id, album.name)
            }

            is CoverAlbumIntent.DeleteClick -> {
                val album = uiState.value.albums.firstOrNull { it.id == intent.albumId } ?: return
                dialog.value = CoverAlbumDialog.Delete(album.id, album.name)
            }

            is CoverAlbumIntent.SaveName -> saveName(intent.name)
            CoverAlbumIntent.ConfirmDelete -> confirmDelete()
            is CoverAlbumIntent.AddImagesClick -> {
                _effects.tryEmit(
                    CoverAlbumEffect.SelectImages(intent.albumId, intent.isDark)
                )
            }

            is CoverAlbumIntent.ImagesSelected -> addImages(
                albumId = intent.albumId,
                isDark = intent.isDark,
                uriStrings = intent.uriStrings,
            )

            is CoverAlbumIntent.RemoveImage -> removeImage(
                albumId = intent.albumId,
                isDark = intent.isDark,
                imageId = intent.imageId,
            )

            CoverAlbumIntent.DismissEditor -> editingAlbumId.value = null
            CoverAlbumIntent.DismissDialog -> dialog.value = null
        }
    }

    private fun saveName(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val activeDialog = dialog.value
        dialog.value = null
        launchOperation {
            when (activeDialog) {
                CoverAlbumDialog.Create -> {
                    val id = coverAlbumUseCase.createAlbum(trimmedName)
                    editingAlbumId.value = id
                }

                is CoverAlbumDialog.Rename -> {
                    coverAlbumUseCase.renameAlbum(activeDialog.albumId, trimmedName)
                }

                else -> Unit
            }
        }
    }

    private fun confirmDelete() {
        val activeDialog = dialog.value as? CoverAlbumDialog.Delete ?: return
        dialog.value = null
        editingAlbumId.update { id -> if (id == activeDialog.albumId) null else id }
        launchOperation {
            coverAlbumUseCase.deleteAlbum(activeDialog.albumId)
        }
    }

    private fun addImages(albumId: String, isDark: Boolean, uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        launchOperation {
            val inputs = uriStrings.map { uriString ->
                val uri = Uri.parse(uriString)
                CoverAlbumImageInput(
                    displayName = queryDisplayName(uri),
                    openStream = {
                        context.contentResolver.openInputStream(uri)
                            ?: error("无法读取图片")
                    },
                )
            }
            coverAlbumUseCase.addImages(albumId, isDark, inputs)
        }
    }

    private fun removeImage(albumId: String, isDark: Boolean, imageId: String) {
        launchOperation {
            coverAlbumUseCase.removeImage(albumId, isDark, imageId)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "cover_image"
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _effects.emit(
                    CoverAlbumEffect.ShowMessage(
                        error.localizedMessage ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }
}
