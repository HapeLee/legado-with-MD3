package io.legado.app.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.constant.EventBus
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.ui.config.mainConfig.MainConfig
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun onPrefClickEvent(context: Context, event: PrefClickEvent) {
        when (event) {
            is PrefClickEvent.OpenUrl -> context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(event.url)
                )
            )

            is PrefClickEvent.CopyUrl -> context.sendToClip(event.url)
            is PrefClickEvent.ShowMd -> {
                if (context is AppCompatActivity) {
                    val title = event.title.ifBlank { context.getString(io.legado.app.R.string.help) }
                    val mdText = String(context.assets.open("web/help/md/${event.path}.md").readBytes())
                    context.showDialogFragment(TextDialog(title, mdText, TextDialog.Mode.MD))
                }
            }

            is PrefClickEvent.StartActivity -> {
                context.startActivity(Intent(context, event.destination).apply {
                    event.configTag?.let { putExtra("configTag", it) }
                })
            }

            PrefClickEvent.ExitApp -> {
                if (context is androidx.activity.ComponentActivity) {
                    context.finish()
                }
            }

            else -> Unit
        }
    }

}

data class MainUiState(
    val destinations: List<MainDestination> = MainDestination.mainDestinations,
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
    }
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
