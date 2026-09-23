package io.legado.app.feature.settings.coverconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-14a：从 `:app` 的 `ui/config/coverConfig` 迁来。三处改动：
//   ① `Context` + `CoverAlbumUseCase` → 注入的 `CoverAlbumProvider`（见其 KDoc）；
//   ② `addImages` 里那段「URI 串 → 图片输入」的活（`Uri.parse` / `ContentResolver.query`
//      取 `DISPLAY_NAME` / `openInputStream`）整段搬进宿主实现 —— 共享层拿不到 `Context`，
//      而 `CoverAlbumImageInput` 携带 `openStream`（`java.io.InputStream`）本就出不了 `:app`；
//   ③ `launch(Dispatchers.IO)` 不再指定调度器（IO 在实现侧），与 M5-7 / M5-12a 同一处理。
//
// 其余（编辑态 / 对话框状态机、各 intent 分支、异常 → ShowMessage）**逐字保留**。
// ⚠️ 唯一的行为等价性说明：迁移前 `launchOperation` 显式跑在 `Dispatchers.IO`；现在
// `viewModelScope.launch`（Main）+ 实现内部各自 `withContext(IO)` ⇒ 净效果相同。

class CoverAlbumManageViewModel(
    private val provider: CoverAlbumProvider,
) : ViewModel() {

    private val editingAlbumId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<CoverAlbumDialog?>(null)
    private val _effects = MutableSharedFlow<CoverAlbumEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    val uiState = combine(
        provider.selection,
        editingAlbumId,
        dialog,
    ) { selection, editingId, activeDialog ->
        CoverAlbumManageUiState(
            albums = selection.albums,
            selectedAlbumId = selection.selectedAlbumId,
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
                    val id = provider.createAlbum(trimmedName)
                    editingAlbumId.value = id
                }

                is CoverAlbumDialog.Rename -> {
                    provider.renameAlbum(activeDialog.albumId, trimmedName)
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
            provider.deleteAlbum(activeDialog.albumId)
        }
    }

    private fun addImages(albumId: String, isDark: Boolean, uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        launchOperation {
            provider.addImages(albumId, isDark, uriStrings)
        }
    }

    private fun removeImage(albumId: String, isDark: Boolean, imageId: String) {
        launchOperation {
            provider.removeImage(albumId, isDark, imageId)
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
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
