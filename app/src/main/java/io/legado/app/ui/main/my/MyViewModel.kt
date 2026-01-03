package io.legado.app.ui.main.my

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyUiState(
    val isWebServiceRun: Boolean = false,
    val webServiceAddress: String = ""
)

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class ShowMd(val title: String, val path: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object ToggleWebService : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

class MyViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        MyUiState(
            isWebServiceRun = WebService.isRun,
            webServiceAddress = WebService.hostAddress
        )
    )
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            FlowEventBus.with<String>(EventBus.WEB_SERVICE)
                .collect { address ->
                    _uiState.update { state ->
                        state.copy(
                            isWebServiceRun = address.isNotEmpty(),
                            webServiceAddress = address
                        )
                    }
                }
        }
    }

    fun onEvent(event: PrefClickEvent) {
        when (event) {
            PrefClickEvent.ToggleWebService -> {
                val currentIsRun = _uiState.value.isWebServiceRun

                if (!currentIsRun) {
                    WebService.start(getApplication())
                } else {
                    WebService.stop(getApplication())
                    _uiState.update { it.copy(isWebServiceRun = false, webServiceAddress = "") }
                }

            }
            else -> Unit
        }
    }

}
