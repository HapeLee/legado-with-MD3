package io.legado.app.ui.main.my

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.legado.app.service.WebService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MyUiState(
    val isWebServiceRun: Boolean = false,
    val webServiceAddress: String = ""
)

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class ShowMd(val title: String, val path: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object ShowWebServiceMenu : PrefClickEvent()
    object ToggleWebService : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

class MyViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = if (WebService.isRun) {
                WebService.hostAddress
            } else ""
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    fun onEvent(event: PrefClickEvent) {
        when (event) {

            PrefClickEvent.ToggleWebService -> {
                val newRun = !_uiState.value.isWebServiceRun
                if (newRun) {
                    WebService.start(getApplication())
                } else {
                    WebService.stop(getApplication())
                }
                updateWebServiceState()
            }

            PrefClickEvent.ShowWebServiceMenu -> {
                // UI 自己处理 showMenu
            }

            else -> Unit
        }
    }

    private fun updateWebServiceState() {
        _uiState.value = MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = if (WebService.isRun) {
                WebService.hostAddress
            } else ""
        )
    }
}
