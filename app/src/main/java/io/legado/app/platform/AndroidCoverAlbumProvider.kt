package io.legado.app.platform

import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.feature.settings.coverconfig.CoverAlbumImageUi
import io.legado.app.feature.settings.coverconfig.CoverAlbumItemUi
import io.legado.app.feature.settings.coverconfig.CoverAlbumProvider
import io.legado.app.feature.settings.coverconfig.CoverAlbumSelectionUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * M5-12a：`CoverAlbumProvider` 的 Android 实现 —— 用 `:app` 既有的 `CoverAlbumUseCase`
 * 把领域模型映射成共享层的 UI 类型。
 *
 * 映射关系（迁移前这段在 `CoverConfigViewModel` 的 `init` 里）：
 * `useCase.albums` + `useCase.selection` ⇒ `CoverAlbumSelectionUiState(albums, selectedAlbumId)`。
 *
 * ⚠️ 领域模型 → UI 的映射（`toUi()`）放在**这里**而不是共享层：共享层不该看见
 * `io.legado.app.domain.model.CoverAlbum`（它在 `:core:model`，本可以看，但映射属于
 * 「谁持有领域模型谁负责」⇒ 宿主侧更合适，也让共享 VM 彻底不依赖领域模型）。
 */
class AndroidCoverAlbumProvider(
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
