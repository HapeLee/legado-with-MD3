package io.legado.app.ui.config.themeManage

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemeExportData
import io.legado.app.help.config.ThemePackageManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThemeManageViewModel(
    private val themePackageManager: ThemePackageManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemeManageUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ThemeManageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        loadSavedThemes()
    }

    fun onIntent(intent: ThemeManageIntent) {
        when (intent) {
            ThemeManageIntent.LoadSavedThemes -> loadSavedThemes()
            is ThemeManageIntent.ExportPackage -> exportPackage(intent)
            is ThemeManageIntent.ImportPackage -> importPackage(intent.uri)
            is ThemeManageIntent.ImportLegacyJson -> importLegacyJson(intent.uri)
            is ThemeManageIntent.SaveTheme -> saveTheme(intent)
            is ThemeManageIntent.ApplySavedTheme -> applySavedTheme(intent.theme)
            is ThemeManageIntent.DeleteSavedTheme -> deleteSavedTheme(intent.theme)
        }
    }

    private fun loadSavedThemes() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val themes = themePackageManager.loadSavedThemes()
            _uiState.update {
                it.copy(
                    loading = false,
                    savedThemes = themes.toImmutableList(),
                )
            }
        }
    }

    private suspend fun refreshSavedThemes() {
        val themes = themePackageManager.loadSavedThemes()
        _uiState.update {
            it.copy(
                loading = false,
                savedThemes = themes.toImmutableList(),
            )
        }
    }

    private fun saveTheme(intent: ThemeManageIntent.SaveTheme) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val result = runCatching {
                themePackageManager.saveTheme(
                    name = intent.name,
                    data = intent.data,
                )
                intent.replacedTheme
                    ?.takeIf { it.name != intent.name }
                    ?.let { themePackageManager.deleteSavedTheme(it).getOrThrow() }
            }
            if (result.isSuccess) {
                refreshSavedThemes()
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        messageRes = R.string.theme_manage_save_failed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun applySavedTheme(theme: SavedTheme) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val result = themePackageManager.applySavedTheme(theme)
            if (result.isSuccess) {
                refreshSavedThemes()
                _effects.emit(ThemeManageEffect.RestartRequired)
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        messageRes = R.string.theme_manage_apply_failed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun deleteSavedTheme(theme: SavedTheme) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val result = themePackageManager.deleteSavedTheme(theme)
            if (result.isSuccess) {
                refreshSavedThemes()
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        messageRes = R.string.theme_manage_delete_failed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun exportPackage(intent: ThemeManageIntent.ExportPackage) {
        viewModelScope.launch {
            val result = themePackageManager.exportPackage(
                uri = Uri.parse(intent.uri),
                themeName = intent.themeName,
                themeData = intent.themeData,
                savedTheme = intent.savedTheme,
            )
            _effects.emit(
                if (result.isSuccess) {
                    ThemeManageEffect.ShowResult(R.string.theme_manage_export_success)
                } else {
                    ThemeManageEffect.ShowResult(
                        messageRes = R.string.theme_manage_export_failed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                }
            )
        }
    }

    private fun importPackage(uri: String) {
        viewModelScope.launch {
            emitImportResult(themePackageManager.importPackage(Uri.parse(uri)))
        }
    }

    private fun importLegacyJson(uri: String) {
        viewModelScope.launch {
            emitImportResult(themePackageManager.importLegacyJson(Uri.parse(uri)))
        }
    }

    private suspend fun emitImportResult(result: Result<Unit>) {
        if (result.isSuccess) {
            refreshSavedThemes()
        }
        _effects.emit(
            if (result.isSuccess) {
                ThemeManageEffect.ShowResult(
                    messageRes = R.string.theme_manage_import_success,
                    restartRequired = true,
                )
            } else {
                ThemeManageEffect.ShowResult(
                    messageRes = R.string.theme_manage_import_failed,
                    detail = result.exceptionOrNull()?.localizedMessage,
                )
            }
        )
    }
}

@Stable
data class ThemeManageUiState(
    val loading: Boolean = false,
    val savedThemes: ImmutableList<SavedTheme> = persistentListOf(),
)

sealed interface ThemeManageIntent {
    data object LoadSavedThemes : ThemeManageIntent

    data class ExportPackage(
        val uri: String,
        val themeName: String? = null,
        val themeData: ThemeExportData? = null,
        val savedTheme: SavedTheme? = null,
    ) : ThemeManageIntent

    data class ImportPackage(val uri: String) : ThemeManageIntent
    data class ImportLegacyJson(val uri: String) : ThemeManageIntent
    data class SaveTheme(
        val name: String,
        val data: ThemeExportData? = null,
        val replacedTheme: SavedTheme? = null,
    ) : ThemeManageIntent

    data class ApplySavedTheme(val theme: SavedTheme) : ThemeManageIntent
    data class DeleteSavedTheme(val theme: SavedTheme) : ThemeManageIntent
}

sealed interface ThemeManageEffect {
    data object RestartRequired : ThemeManageEffect

    data class ShowResult(
        @param:StringRes val messageRes: Int,
        val detail: String? = null,
        val restartRequired: Boolean = false,
    ) : ThemeManageEffect
}
