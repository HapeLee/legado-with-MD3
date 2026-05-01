package io.legado.app.ui.main

import android.app.Application
import android.content.SharedPreferences
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.constant.EventBus
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class MainViewModel(
    application: Application,
    private val appStartupMaintenanceUseCase: AppStartupMaintenanceUseCase,
    private val webDavBackupUseCase: WebDavBackupUseCase
) : BaseViewModel(application) {

    private val prefs = context.defaultSharedPreferences
    private val mainPreferenceKeys = setOf(
        PreferKey.showDiscovery,
        PreferKey.showRss,
        PreferKey.showBottomView,
        PreferKey.useFloatingBottomBar,
        PreferKey.useFloatingBottomBarLiquidGlass,
        PreferKey.defaultHomePage,
        PreferKey.labelVisibilityMode,
        NAV_EXTENDED_KEY
    )
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in mainPreferenceKeys) {
                _uiState.value = readMainUiState()
            }
        }

    private val _uiState = MutableStateFlow(readMainUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MainEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        deleteNotShelfBook()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onCleared()
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
        MainConfig.navExtended = expanded
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
}

data class MainUiState(
    val destinations: ImmutableList<MainDestination> = MainDestination.mainDestinations,
    val defaultHomePage: String = "bookshelf",
    val showBottomView: Boolean = true,
    val useFloatingBottomBar: Boolean = false,
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    val labelVisibilityMode: String = "auto",
    val navExtended: Boolean = false
)

private const val NAV_EXTENDED_KEY = "navExtended"

private fun MainViewModel.readMainUiState(): MainUiState {
    val showDiscovery = context.getPrefBoolean(PreferKey.showDiscovery, true)
    val showRss = context.getPrefBoolean(PreferKey.showRss, true)
    val destinations = MainDestination.mainDestinations.filter {
        when (it) {
            MainDestination.Explore -> showDiscovery
            MainDestination.Rss -> showRss
            else -> true
        }
    }.toImmutableList()
    return MainUiState(
        destinations = destinations,
        defaultHomePage = context.getPrefString(PreferKey.defaultHomePage, "bookshelf")
            ?: "bookshelf",
        showBottomView = context.getPrefBoolean(PreferKey.showBottomView, true),
        useFloatingBottomBar = context.getPrefBoolean(PreferKey.useFloatingBottomBar, false),
        useFloatingBottomBarLiquidGlass = context.getPrefBoolean(
            PreferKey.useFloatingBottomBarLiquidGlass,
            false
        ),
        labelVisibilityMode = context.getPrefString(PreferKey.labelVisibilityMode, "auto") ?: "auto",
        navExtended = context.getPrefBoolean(NAV_EXTENDED_KEY, false)
    )
}
