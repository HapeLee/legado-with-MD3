package io.legado.app.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.feature.settings.coverconfig.CoverAlbumImageUi
import io.legado.app.feature.settings.coverconfig.CoverAlbumItemUi
import io.legado.app.feature.settings.coverconfig.CoverAlbumProvider
import io.legado.app.feature.settings.coverconfig.CoverAlbumSelectionUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * `CoverAlbumProvider` 的 Android 实现 —— 用 `:app` 既有的 `CoverAlbumUseCase` 把领域模型
 * 映射成共享层的 UI 类型。
 *
 * 演进：
 * - M5-12a 建它时只有三个成员（`selection` / `selectAlbum`），服务封面设置页；
 * - M5-14a 图库管理页迁进共享层，补上增删改与加图 —— 契约与实现各扩一次，不新造契约。
 *
 * ⚠️ **`addImages` 是本实现存在的主要理由**：它要把宿主选择器给的 URI 串变成
 * `CoverAlbumImageInput`（`displayName` 靠 `ContentResolver.query(... DISPLAY_NAME)`，
 * 内容靠 `openInputStream`）。`CoverAlbumImageInput` 携带 `java.io.InputStream`
 * ⇒ 这个类型**出不了 `:app`**，共享层只能收到 URI 串（详见 `CoverAlbumProvider` 的 KDoc）。
 *
 * 所有会落盘/读盘的方法都在 `Dispatchers.IO`（迁移前 VM 里显式写了 `Dispatchers.IO`，
 * 本片把它下沉到这里）。
 */
class AndroidCoverAlbumProvider(
    private val context: Context,
    private val coverAlbumUseCase: CoverAlbumUseCase,
) : CoverAlbumProvider {

    override val selection: Flow<CoverAlbumSelectionUiState> = combine(
        coverAlbumUseCase.albums,
        coverAlbumUseCase.selection,
    ) { albums, selection ->
        CoverAlbumSelectionUiState(
            albums = albums.map { it.toUi() }.toImmutableList(),
            selectedAlbumId = selection.albumId,
        )
    }

    override suspend fun selectAlbum(albumId: String?) {
        coverAlbumUseCase.selectAlbum(albumId)
    }

    override suspend fun createAlbum(name: String): String = coverAlbumUseCase.createAlbum(name)

    override suspend fun renameAlbum(albumId: String, name: String) {
        coverAlbumUseCase.renameAlbum(albumId, name)
    }

    override suspend fun deleteAlbum(albumId: String) {
        coverAlbumUseCase.deleteAlbum(albumId)
    }

    override suspend fun addImages(albumId: String, isDark: Boolean, uriStrings: List<String>) {
        withContext(Dispatchers.IO) {
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

    override suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String) {
        coverAlbumUseCase.removeImage(albumId, isDark, imageId)
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

    private fun io.legado.app.domain.model.CoverAlbum.toUi() = CoverAlbumItemUi(
        id = id,
        name = name,
        lightImages = lightImages.map {
            CoverAlbumImageUi(id = it.id, path = it.path)
        }.toImmutableList(),
        darkImages = darkImages.map {
            CoverAlbumImageUi(id = it.id, path = it.path)
        }.toImmutableList(),
    )
}
