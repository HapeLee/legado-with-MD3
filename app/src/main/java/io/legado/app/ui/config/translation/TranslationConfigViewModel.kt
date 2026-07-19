package io.legado.app.ui.config.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.TranslationSettingsGateway
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranslationConfigViewModel(
    private val settingsGateway: TranslationSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TranslationConfigUiState(settings = settingsGateway.currentSettings)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TranslationConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onIntent(intent: TranslationConfigIntent) {
        viewModelScope.launch {
            settingsGateway.update { settings ->
                when (intent) {
                    is TranslationConfigIntent.SetProvider ->
                        settings.copy(provider = intent.value)
                    is TranslationConfigIntent.SetTargetLanguage ->
                        settings.copy(targetLanguage = intent.value)
                    is TranslationConfigIntent.SetMaxCharsPerChunk ->
                        settings.copy(maxCharsPerChunk = intent.value)
                }
            }
        }
    }
}
