package io.legado.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.EventBus
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(
    private val appStartupMaintenanceUseCase: AppStartupMaintenanceUseCase,
    private val webDavBackupUseCase: WebDavBackupUseCase,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        buildMainUiState(
            appShellSettingsGateway.currentSettings,
            themeSettingsGateway.currentSettings,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                appShellSettingsGateway.settings,
                themeSettingsGateway.settings,
                ::buildMainUiState,
            ).collect { _uiState.value = it }
        }
        viewModelScope.launch { appStartupMaintenanceUseCase.deleteNotShelfBooks() }
    }

    fun onIntent(intent: MainUiIntent) {
        when (intent) {
            is MainUiIntent.SetNavigationRailExpanded -> setNavExtended(intent.expanded)
            is MainUiIntent.HandlePreferenceClick -> handlePrefClick(intent.event)
        }
    }

    fun upAllBookToc() {
        FlowEventBus.post(EventBus.UP_ALL_BOOK_TOC, Unit)
    }

    fun restoreWebDav(name: String) {
        viewModelScope.launch { webDavBackupUseCase.restore(name) }
    }

    suspend fun getLatestWebDavBackup() = webDavBackupUseCase.getLatestBackup()

    private fun setNavExtended(expanded: Boolean) {
        if (_uiState.value.navExtended == expanded) return
        viewModelScope.launch {
            appShellSettingsGateway.update { it.copy(navExtended = expanded) }
        }
    }

    private fun handlePrefClick(event: PrefClickEvent) {
        when (event) {
            is PrefClickEvent.OpenUrl -> _effects.tryEmit(MainEffect.OpenUrl(event.url))
            is PrefClickEvent.CopyUrl -> _effects.tryEmit(MainEffect.CopyUrl(event.url))
            is PrefClickEvent.ShowMd -> _effects.tryEmit(
                MainEffect.ShowMarkdown(title = event.title, path = event.path)
            )
            is PrefClickEvent.StartActivity -> _effects.tryEmit(
                MainEffect.StartActivity(
                    destination = event.destination,
                    configTag = event.configTag,
                )
            )
            PrefClickEvent.ExitApp -> _effects.tryEmit(MainEffect.ExitApp)
            PrefClickEvent.OpenReadRecord -> _effects.tryEmit(MainEffect.NavigateToReadRecord)
            PrefClickEvent.OpenHighlightTagRule ->
                _effects.tryEmit(MainEffect.NavigateToHighlightTagRule)
            PrefClickEvent.OpenAbout -> _effects.tryEmit(MainEffect.NavigateToAbout)
            else -> Unit
        }
    }
}

private fun buildMainUiState(
    appShell: AppShellSettings,
    theme: ThemeSettings,
): MainUiState {
    val destinations = MainDestination.ordered(appShell.mainNavigationOrder).filter {
        when (it) {
            MainDestination.Home -> appShell.showHome
            MainDestination.Explore -> appShell.showDiscovery
            MainDestination.Rss -> appShell.showRss
            else -> true
        }
    }
    return MainUiState(
        destinations = destinations.toImmutableList(),
        defaultHomePage = appShell.defaultHomePage,
        showBottomView = appShell.showBottomView,
        useFloatingBottomBar = appShell.useFloatingBottomBar,
        useFloatingBottomBarLiquidGlass = appShell.useFloatingBottomBarLiquidGlass,
        labelVisibilityMode = appShell.labelVisibilityMode,
        navExtended = appShell.navExtended,
        navIconHome = appShell.navIconHome,
        navIconBookshelf = appShell.navIconBookshelf,
        navIconExplore = appShell.navIconExplore,
        navIconRss = appShell.navIconRss,
        navIconMy = appShell.navIconMy,
        deepPersonalizationActive = theme.appTheme == "12" && theme.enableDeepPersonalization,
        secondaryThemeColor = theme.secondaryThemeColor,
        secondaryThemeColorNight = theme.secondaryThemeColorNight,
    )
}
