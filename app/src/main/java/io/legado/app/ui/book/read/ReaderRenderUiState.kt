package io.legado.app.ui.book.read

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal, atomically published state consumed by the Compose reading surface. */
@Stable
data class ReaderRenderUiState(
    val pageWindow: ReaderPageWindow = ReaderPageWindow(),
    val paginationError: String? = null,
    val background: ReaderBackgroundState = ReaderBackgroundState(),
    /** Explicit draw invalidation, independent of page identity and pagination geometry. */
    val redrawRevision: Long = 0L,
)

/** Lightweight state owner for the hot Canvas path only. */
class ReaderSessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderRenderUiState())
    val uiState = _uiState.asStateFlow()
    private var pendingState = _uiState.value
    private var entranceSettled = false

    fun submit(state: ReaderRenderUiState) {
        pendingState = state.copy(redrawRevision = _uiState.value.redrawRevision)
        if (entranceSettled) _uiState.value = pendingState
    }

    /** Re-record retained drawing layers on resume, even when their page state is unchanged. */
    fun requestRedraw() {
        val revision = _uiState.value.redrawRevision + 1L
        pendingState = pendingState.copy(redrawRevision = revision)
        _uiState.value = _uiState.value.copy(redrawRevision = revision)
    }

    /** Background participates in shared-bounds rendering and must not wait for page commit. */
    fun submitBackground(background: ReaderBackgroundState) {
        pendingState = pendingState.copy(background = background)
        _uiState.value = _uiState.value.copy(background = background)
    }

    /** A prepared page may start fading in while the shared-bounds transition is still running. */
    fun submitPageWindow(pageWindow: ReaderPageWindow) {
        pendingState = pendingState.copy(pageWindow = pageWindow)
        _uiState.value = _uiState.value.copy(pageWindow = pageWindow)
    }

    fun onEntranceStateChanged(settled: Boolean) {
        entranceSettled = settled
        if (settled) _uiState.value = pendingState
    }
}
