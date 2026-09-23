package io.legado.app.feature.settings.coverconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.model.settings.CoverSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-12a：从 `:app` 的 `ui/config/coverConfig` 迁来。三处改动：
//   ① `BookCover` / `DefaultData` → 注入的 `CoverRulePlatform`（见其 KDoc）；
//   ② `R.string.*` → `CoverConfigToast` 枚举（见 `CoverConfigText`）；
//   ③ `Dispatchers.IO` 下沉到实现侧 ⇒ 这里的 `launch` 不再指定调度器（M5-7 同一处理）。
//
// ⚠️ 一处**时序**变化：`RestoreDefaultRule` 迁移前是同步读 `DefaultData.coverRule`
// （`by lazy` 从 assets 读）并立即更新 state；现在 `default()` 是 suspend ⇒ 必须放进
// `launch`。表现为「点『恢复默认』后 state 晚一帧更新」—— 同为异步状态更新，
// 与 `loadRule()` / `saveRule()` 一致，用户不可分辨。

class CoverConfigViewModel(
    private val coverAlbumProvider: CoverAlbumProvider,
    private val settingsGateway: CoverSettingsGateway,
    private val coverRulePlatform: CoverRulePlatform,
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
                coverAlbumProvider.selection,
                settingsGateway.settings,
            ) { albumSelection, settings -> settings to albumSelection }
                .collect { (settings, albumSelection) ->
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
            is CoverConfigIntent.SelectAlbum -> viewModelScope.launch {
                coverAlbumProvider.selectAlbum(intent.id)
            }
            is CoverConfigIntent.SetRuleEnabled ->
                _uiState.update { it.copy(rule = it.rule.copy(enabled = intent.value)) }
            is CoverConfigIntent.SetRuleSearchUrl ->
                _uiState.update { it.copy(rule = it.rule.copy(searchUrl = intent.value)) }
            is CoverConfigIntent.SetRuleExpression ->
                _uiState.update { it.copy(rule = it.rule.copy(coverRule = intent.value)) }
            CoverConfigIntent.RestoreDefaultRule -> restoreDefaultRule()
            CoverConfigIntent.SaveRule -> saveRule()
        }
    }

    private fun updateSettings(transform: (CoverSettings) -> CoverSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }

    private fun loadRule() {
        viewModelScope.launch {
            val rule = coverRulePlatform.current()
            _uiState.update {
                it.copy(rule = CoverRuleUiState(rule.enabled, rule.searchUrl, rule.expression))
            }
        }
    }

    private fun restoreDefaultRule() {
        viewModelScope.launch {
            val rule = coverRulePlatform.default()
            _uiState.update {
                it.copy(rule = CoverRuleUiState(rule.enabled, rule.searchUrl, rule.expression))
            }
            _effects.tryEmit(CoverConfigEffect.ShowToast(CoverConfigToast.RestoredDefault))
        }
    }

    private fun saveRule() {
        val state = _uiState.value.rule
        if (state.searchUrl.isBlank() || state.coverRule.isBlank()) {
            _effects.tryEmit(CoverConfigEffect.ShowToast(CoverConfigToast.RuleFieldsRequired))
            return
        }
        viewModelScope.launch {
            val spec = CoverRuleSpec(
                enabled = state.enabled,
                searchUrl = state.searchUrl,
                expression = state.coverRule,
            )
            // 与默认规则相同 ⇒ 删掉自定义配置（回落到内置默认），否则写入
            if (spec == coverRulePlatform.default()) {
                coverRulePlatform.delete()
            } else {
                coverRulePlatform.save(spec)
            }
            _uiState.update { it.copy(activeSheet = null) }
        }
    }
}

