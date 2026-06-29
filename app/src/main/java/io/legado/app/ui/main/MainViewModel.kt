package io.legado.app.ui.main

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val appStartupMaintenanceUseCase: AppStartupMaintenanceUseCase,
    private val webDavBackupUseCase: WebDavBackupUseCase,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(readMainUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        // 通过 snapshotFlow 直接观察 ThemeConfig 的 Compose State
        viewModelScope.launch {
            snapshotFlow {
                readMainUiState()
            }.collect { newState ->
                _uiState.value = newState
            }
        }
        deleteNotShelfBook()
    }

    fun upAllBookToc() {
        FlowEventBus.post(EventBus.UP_ALL_BOOK_TOC, Unit)
    }

    fun postLoad() {
        execute {
            appStartupMaintenanceUseCase.ensureDefaultHttpTts()
        }
    }

    fun restoreWebDav(name: String) {
        execute {
            webDavBackupUseCase.restore(name)
        }
    }

    suspend fun getLatestWebDavBackup() = webDavBackupUseCase.getLatestBackup()

    private fun deleteNotShelfBook() {
        execute {
            appStartupMaintenanceUseCase.deleteNotShelfBooks()
        }
    }

    fun setNavExtended(expanded: Boolean) {
        if (_uiState.value.navExtended == expanded) return
        _uiState.update { it.copy(navExtended = expanded) }
        ThemeConfig.navExtended = expanded
    }

    fun onPrefClickEvent(event: PrefClickEvent) {
        when (event) {
            is PrefClickEvent.OpenUrl -> _effects.tryEmit(MainEffect.OpenUrl(event.url))
            is PrefClickEvent.CopyUrl -> _effects.tryEmit(MainEffect.CopyUrl(event.url))
            is PrefClickEvent.ShowMd -> _effects.tryEmit(
                MainEffect.ShowMarkdown(
                    title = event.title,
                    path = event.path
                )
            )

            is PrefClickEvent.StartActivity -> {
                _effects.tryEmit(
                    MainEffect.StartActivity(
                        destination = event.destination,
                        configTag = event.configTag
                    )
                )
            }

            PrefClickEvent.ExitApp -> _effects.tryEmit(MainEffect.ExitApp)

            PrefClickEvent.OpenReadRecord -> _effects.tryEmit(MainEffect.NavigateToReadRecord)

            PrefClickEvent.OpenHighlightTagRule -> _effects.tryEmit(MainEffect.NavigateToHighlightTagRule)

            PrefClickEvent.OpenAbout -> _effects.tryEmit(MainEffect.NavigateToAbout)

            else -> Unit
        }
    }

}

sealed interface MainEffect {
    data class OpenUrl(val url: String) : MainEffect
    data class CopyUrl(val url: String) : MainEffect
    data class ShowMarkdown(val title: String, val path: String) : MainEffect
    data class StartActivity(
        val destination: Class<*>,
        val configTag: String? = null
    ) : MainEffect

    data object ExitApp : MainEffect
    data object NavigateToReadRecord : MainEffect
    data object NavigateToHighlightTagRule : MainEffect
    data object NavigateToAbout : MainEffect
}

@Stable
data class MainUiState(
    val destinations: ImmutableList<MainDestination> = MainDestination.mainDestinations,
    val defaultHomePage: String = "bookshelf",
    val showBottomView: Boolean = true,
    val useFloatingBottomBar: Boolean = false,
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    val labelVisibilityMode: String = "auto",
    val navExtended: Boolean = false,
)

private fun MainViewModel.readMainUiState(): MainUiState {
    val destinations = MainDestination.ordered(ThemeConfig.mainNavigationOrder).filter {
        when (it) {
            MainDestination.Home -> ThemeConfig.showHome
            MainDestination.Explore -> ThemeConfig.showDiscovery
            MainDestination.Rss -> ThemeConfig.showRss
            else -> true
        }
    }.toImmutableList()
    return MainUiState(
        destinations = destinations,
        defaultHomePage = ThemeConfig.defaultHomePage,
        showBottomView = ThemeConfig.showBottomView,
        useFloatingBottomBar = ThemeConfig.useFloatingBottomBar,
        useFloatingBottomBarLiquidGlass = ThemeConfig.useFloatingBottomBarLiquidGlass,
        labelVisibilityMode = ThemeConfig.labelVisibilityMode,
        navExtended = ThemeConfig.navExtended,
    )
}
