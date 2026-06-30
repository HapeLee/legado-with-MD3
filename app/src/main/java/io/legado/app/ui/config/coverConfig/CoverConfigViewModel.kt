package io.legado.app.ui.config.coverConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.usecase.CoverAlbumUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoverConfigViewModel(
    private val coverAlbumUseCase: CoverAlbumUseCase,
) : ViewModel() {

    val albumState = combine(
        coverAlbumUseCase.albums,
        coverAlbumUseCase.selection,
    ) { albums, selection ->
        CoverAlbumSelectionUiState(
            albums = albums.map { it.toUi() }.toImmutableList(),
            selectedAlbumId = selection.albumId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CoverAlbumSelectionUiState(),
    )

    fun selectAlbum(albumId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            coverAlbumUseCase.selectAlbum(albumId)
        }
    }

    fun updateShowName(show: Boolean, isNight: Boolean = false) {
        if (isNight) {
            CoverConfig.coverShowNameN = show
        } else {
            CoverConfig.coverShowName = show
        }
    }

    fun updateShowAuthor(show: Boolean, isNight: Boolean = false) {
        if (isNight) {
            CoverConfig.coverShowAuthorN = show
        } else {
            CoverConfig.coverShowAuthor = show
        }
    }

    fun updateCoverStyle() {
        // no-op: Compose CoilBookCover reads CoverConfig preferences directly
    }
}

internal fun io.legado.app.domain.model.CoverAlbum.toUi() = CoverAlbumItemUi(
    id = id,
    name = name,
    lightImages = lightImages.map {
        CoverAlbumImageUi(
            id = it.id,
            path = it.path,
        )
    }.toImmutableList(),
    darkImages = darkImages.map {
        CoverAlbumImageUi(
            id = it.id,
            path = it.path,
        )
    }.toImmutableList(),
)
