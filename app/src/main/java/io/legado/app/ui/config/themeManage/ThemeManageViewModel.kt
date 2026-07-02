package io.legado.app.ui.config.themeManage

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemeExportData
import io.legado.app.help.config.ThemeImportExport
import io.legado.app.help.config.ThemePackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ThemeManageViewModel(
    private val themePackageManager: ThemePackageManager,
) : ViewModel() {

    private val _effects = MutableSharedFlow<ThemeManageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: ThemeManageIntent) {
        when (intent) {
            is ThemeManageIntent.ExportPackage -> exportPackage(intent)
            is ThemeManageIntent.ImportPackage -> importPackage(intent.uri)
            is ThemeManageIntent.ImportLegacyJson -> importLegacyJson(intent.uri)
            is ThemeManageIntent.SaveTheme -> saveTheme(intent)
            is ThemeManageIntent.ApplySavedTheme -> applySavedTheme(intent.theme)
        }
    }

    private fun saveTheme(intent: ThemeManageIntent.SaveTheme) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                themePackageManager.saveTheme(
                    name = intent.name,
                    data = intent.data ?: ThemeImportExport.exportFromCurrent(),
                )
                intent.replacedTheme
                    ?.takeIf { it.name != intent.name }
                    ?.let(ThemeImportExport::deleteSavedTheme)
            }
            if (result.isSuccess) {
                _effects.emit(ThemeManageEffect.SavedThemesChanged)
            } else {
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
            val result = themePackageManager.applySavedTheme(theme)
            if (result.isSuccess) {
                _effects.emit(ThemeManageEffect.SavedThemesChanged)
                _effects.emit(ThemeManageEffect.RestartRequired)
            } else {
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        messageRes = R.string.theme_manage_apply_failed,
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
        _effects.emit(
            if (result.isSuccess) {
                ThemeManageEffect.ShowResult(
                    messageRes = R.string.theme_manage_import_success,
                    restartRequired = true,
                    savedThemesChanged = true,
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

sealed interface ThemeManageIntent {
    data class ExportPackage(
        val uri: String,
        val themeName: String? = null,
        val themeData: ThemeExportData? = null,
    ) : ThemeManageIntent

    data class ImportPackage(val uri: String) : ThemeManageIntent
    data class ImportLegacyJson(val uri: String) : ThemeManageIntent
    data class SaveTheme(
        val name: String,
        val data: ThemeExportData? = null,
        val replacedTheme: SavedTheme? = null,
    ) : ThemeManageIntent

    data class ApplySavedTheme(val theme: SavedTheme) : ThemeManageIntent
}

sealed interface ThemeManageEffect {
    data object SavedThemesChanged : ThemeManageEffect
    data object RestartRequired : ThemeManageEffect

    data class ShowResult(
        @param:StringRes val messageRes: Int,
        val detail: String? = null,
        val restartRequired: Boolean = false,
        val savedThemesChanged: Boolean = false,
    ) : ThemeManageEffect
}
