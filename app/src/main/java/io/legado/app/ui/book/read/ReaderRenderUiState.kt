package io.legado.app.ui.book.read

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.navigation.ReaderRenderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal, atomically published state consumed by the Compose reading surface. */
@Stable
data class ReaderRenderUiState(
    val renderState: ReaderRenderState = ReaderRenderState(),
    val background: ReaderBackgroundState = ReaderBackgroundState(),
) {
    val pageWindow: ReaderPageWindow get() = renderState.pageWindow
    val paginationError: String? get() = renderState.paginationError
}

/** Lightweight state owner for the hot Canvas path only. */
class ReaderSessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderRenderUiState())
    val uiState = _uiState.asStateFlow()
    private var pendingState = _uiState.value
    private var entranceSettled = false

    fun submit(state: ReaderRenderUiState) {
        pendingState = state
        if (entranceSettled) _uiState.value = state
    }

    /** Background participates in shared-bounds rendering and must not wait for page commit. */
    fun submitBackground(background: ReaderBackgroundState) {
        pendingState = pendingState.copy(background = background)
        _uiState.value = _uiState.value.copy(background = background)
    }

    /** A prepared page may start fading in while the shared-bounds transition is still running. */
    fun submitPageWindow(pageWindow: ReaderPageWindow) {
        pendingState = pendingState.copy(renderState = pendingState.renderState.copy(pageWindow = pageWindow))
        _uiState.value = _uiState.value.copy(renderState = _uiState.value.renderState.copy(pageWindow = pageWindow))
    }

    fun onEntranceStateChanged(settled: Boolean) {
        entranceSettled = settled
        if (settled) _uiState.value = pendingState
    }
}
