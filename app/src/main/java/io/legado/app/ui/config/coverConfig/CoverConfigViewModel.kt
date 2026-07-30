package io.legado.app.ui.config.coverConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.DefaultData
import io.legado.app.model.BookCover
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CoverConfigViewModel(
    private val coverAlbumUseCase: CoverAlbumUseCase,
    private val settingsGateway: CoverSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CoverConfigUiState(settings = settingsGateway.currentSettings)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CoverConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                coverAlbumUseCase.albums,
                coverAlbumUseCase.selection,
                settingsGateway.settings,
            ) { albums, selection, settings ->
                settings to CoverAlbumSelectionUiState(
                    albums = albums.map { it.toUi() }.toImmutableList(),
                    selectedAlbumId = selection.albumId,
                )
            }.collect { (settings, albumSelection) ->
                _uiState.update {
                    it.copy(settings = settings, albumSelection = albumSelection)
                }
            }
        }
    }

    fun onIntent(intent: CoverConfigIntent) {
        when (intent) {
            is CoverConfigIntent.SetLoadOnlyOnWifi ->
                updateSettings { it.copy(loadOnlyOnWifi = intent.value) }
            is CoverConfigIntent.SetUseDefaultCover ->
                updateSettings { it.copy(useDefaultCover = intent.value) }
            is CoverConfigIntent.SetShowShadow ->
                updateSettings { it.copy(showShadow = intent.value) }
            is CoverConfigIntent.SetShowStroke ->
                updateSettings { it.copy(showStroke = intent.value) }
            is CoverConfigIntent.SetUseDefaultColor ->
                updateSettings { it.copy(useDefaultColor = intent.value) }
            is CoverConfigIntent.SetInfoOrientation ->
                updateSettings { it.copy(infoOrientation = intent.value) }
            is CoverConfigIntent.SetExploreFilterState ->
                updateSettings { it.copy(exploreFilterState = intent.value) }
            is CoverConfigIntent.SetShowName ->
                updateSettings { it.copy(showName = intent.value) }
            is CoverConfigIntent.SetShowAuthor ->
                updateSettings { it.copy(showAuthor = intent.value) }
            is CoverConfigIntent.SetShowNameDark ->
                updateSettings { it.copy(showNameDark = intent.value) }
            is CoverConfigIntent.SetShowAuthorDark ->
                updateSettings { it.copy(showAuthorDark = intent.value) }
            is CoverConfigIntent.SetTextColor ->
                updateSettings { it.copy(textColor = intent.value) }
            is CoverConfigIntent.SetShadowColor ->
                updateSettings { it.copy(shadowColor = intent.value) }
            is CoverConfigIntent.SetTextColorDark ->
                updateSettings { it.copy(textColorDark = intent.value) }
            is CoverConfigIntent.SetShadowColorDark ->
                updateSettings { it.copy(shadowColorDark = intent.value) }
            is CoverConfigIntent.ShowSheet -> {
                _uiState.update { it.copy(activeSheet = intent.sheet) }
                if (intent.sheet == CoverConfigSheet.Rule) loadRule()
            }
            CoverConfigIntent.DismissSheet ->
                _uiState.update { it.copy(activeSheet = null) }
            is CoverConfigIntent.SelectAlbum -> viewModelScope.launch(Dispatchers.IO) {
                coverAlbumUseCase.selectAlbum(intent.id)
            }
            is CoverConfigIntent.SetRuleEnabled ->
                _uiState.update { it.copy(rule = it.rule.copy(enabled = intent.value)) }
            is CoverConfigIntent.SetRuleSearchUrl ->
                _uiState.update { it.copy(rule = it.rule.copy(searchUrl = intent.value)) }
            is CoverConfigIntent.SetRuleExpression ->
                _uiState.update { it.copy(rule = it.rule.copy(coverRule = intent.value)) }
            CoverConfigIntent.RestoreDefaultRule -> {
                val rule = DefaultData.coverRule
                _uiState.update {
                    it.copy(
                        rule = CoverRuleUiState(rule.enable, rule.searchUrl, rule.coverRule)
                    )
                }
                _effects.tryEmit(CoverConfigEffect.ShowToast(R.string.restore_default))
            }
            CoverConfigIntent.SaveRule -> saveRule()
        }
    }

    private fun updateSettings(transform: (CoverSettings) -> CoverSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }

    private fun loadRule() {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = BookCover.getCoverRule()
            _uiState.update {
                it.copy(rule = CoverRuleUiState(rule.enable, rule.searchUrl, rule.coverRule))
            }
        }
    }

    private fun saveRule() {
        val state = _uiState.value.rule
        if (state.searchUrl.isBlank() || state.coverRule.isBlank()) {
            _effects.tryEmit(CoverConfigEffect.ShowToast(R.string.cover_rule_fields_required))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val rule = BookCover.CoverRule(state.enabled, state.searchUrl, state.coverRule)
            if (rule == DefaultData.coverRule) BookCover.delCoverRule() else BookCover.saveCoverRule(rule)
            _uiState.update { it.copy(activeSheet = null) }
        }
    }
}

internal fun io.legado.app.domain.model.CoverAlbum.toUi() = CoverAlbumItemUi(
    id = id,
    name = name,
    lightImages = lightImages.map {
        CoverAlbumImageUi(id = it.id, path = it.path)
    }.toImmutableList(),
    darkImages = darkImages.map {
        CoverAlbumImageUi(id = it.id, path = it.path)
    }.toImmutableList(),
)
