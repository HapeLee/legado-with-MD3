package io.legado.app.feature.settings.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MangaConfigViewModel(
    private val manga: MangaSettingsGateway,
    private val translation: MangaTranslationGateway,
    private val reading: ReadSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MangaConfigUiState(settings = manga.currentSettings,
        nextChapters = reading.currentSettings.preDownloadNum, previousChapters = reading.currentSettings.preDownloadNumPrev))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { attempt { manga.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } } } }
        viewModelScope.launch { attempt { translation.globalSettings().collect { settings ->
            _uiState.update { it.copy(automaticTranslation = settings.automatic, translationLoaded = true) }
        } } }
        viewModelScope.launch { attempt { reading.settings.collect { settings ->
            _uiState.update { it.copy(nextChapters = settings.preDownloadNum, previousChapters = settings.preDownloadNumPrev) }
        } } }
    }

    fun onIntent(intent: MangaConfigIntent) {
        if (intent == MangaConfigIntent.DismissError) {
            _uiState.update { it.copy(error = null) }
            return
        }
        viewModelScope.launch {
            attempt {
                when (intent) {
                    is MangaConfigIntent.ChangeSettings -> manga.update(intent.transform)
                    is MangaConfigIntent.SetAutomaticTranslation -> translation.updateGlobal { it.copy(automatic = intent.enabled) }
                    is MangaConfigIntent.SetNextChapters -> reading.update { it.copy(preDownloadNum = intent.count.coerceIn(0, 100)) }
                    is MangaConfigIntent.SetPreviousChapters -> reading.update { it.copy(preDownloadNumPrev = intent.count.coerceIn(0, 100)) }
                    MangaConfigIntent.DismissError -> Unit
                }
            }
        }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { _uiState.update { it.copy(error = failure.message ?: failure.javaClass.simpleName) } }
    }
}
