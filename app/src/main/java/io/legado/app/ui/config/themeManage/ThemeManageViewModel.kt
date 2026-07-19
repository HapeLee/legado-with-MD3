package io.legado.app.ui.config.themeManage

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.domain.model.settings.ThemeExportData
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
import kotlinx.coroutines.sync.Mutex

class ThemeManageViewModel(
    private val themePackageManager: ThemePackageManager,
) : ViewModel() {

    private val operationMutex = Mutex()

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
            ThemeManageIntent.MigrateLegacyThemes -> migrateLegacyThemes()
            ThemeManageIntent.OpenSaveDialog ->
                _uiState.update { it.copy(dialog = ThemeManageDialog.Save()) }
            is ThemeManageIntent.UpdateSaveName -> _uiState.update { state ->
                val dialog = state.dialog as? ThemeManageDialog.Save ?: return@update state
                state.copy(dialog = dialog.copy(name = intent.value))
            }
            is ThemeManageIntent.OpenApplyDialog ->
                _uiState.update { it.copy(dialog = ThemeManageDialog.Apply(intent.theme)) }
            is ThemeManageIntent.OpenDeleteDialog ->
                _uiState.update { it.copy(dialog = ThemeManageDialog.Delete(intent.theme)) }
            is ThemeManageIntent.OpenEditSheet ->
                _uiState.update { it.copy(dialog = ThemeManageDialog.Edit(intent.theme)) }
            ThemeManageIntent.DismissDialog ->
                _uiState.update { it.copy(dialog = null) }
            is ThemeManageIntent.RequestExport ->
                _effects.tryEmit(ThemeManageEffect.OpenExportDocument(intent.theme))
            ThemeManageIntent.RequestImportPackage ->
                _effects.tryEmit(ThemeManageEffect.OpenImportPackage)
            ThemeManageIntent.RequestImportLegacyJson ->
                _effects.tryEmit(ThemeManageEffect.OpenImportLegacyJson)
        }
    }

    private fun loadSavedThemes() {
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val themes = themePackageManager.loadSavedThemes()
            val hasLegacyThemes = themePackageManager.hasLegacySavedThemes()
            _uiState.update {
                it.copy(
                    loading = false,
                    savedThemes = themes.toImmutableList(),
                    hasLegacyThemes = hasLegacyThemes,
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
        launchExclusive {
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
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val result = themePackageManager.applySavedTheme(theme)
            if (result.isSuccess) {
                // Compose 由响应式设置直接更新；旧 View 由 BaseActivity 的配置兼容层处理。
                refreshSavedThemes()
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
        launchExclusive {
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
        launchExclusive {
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
        launchExclusive {
            emitImportResult(themePackageManager.importPackage(Uri.parse(uri)))
        }
    }

    private fun importLegacyJson(uri: String) {
        launchExclusive {
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
                )
            } else {
                ThemeManageEffect.ShowResult(
                    messageRes = R.string.theme_manage_import_failed,
                    detail = result.exceptionOrNull()?.localizedMessage,
                )
            }
        )
    }

    private fun migrateLegacyThemes() {
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val result = themePackageManager.migrateLegacySavedThemes()
            refreshSavedThemes()
            _uiState.update { it.copy(hasLegacyThemes = result.failedCount > 0) }
            _effects.emit(
                ThemeManageEffect.LegacyMigrationFinished(
                    migratedCount = result.migratedCount,
                    failedCount = result.failedCount,
                )
            )
        }
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        if (!operationMutex.tryLock()) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                operationMutex.unlock()
            }
        }
    }
}

@Stable
data class ThemeManageUiState(
    val loading: Boolean = false,
    val savedThemes: ImmutableList<SavedTheme> = persistentListOf(),
    val hasLegacyThemes: Boolean = false,
    val dialog: ThemeManageDialog? = null,
)

sealed interface ThemeManageDialog {
    data class Save(val name: String = "") : ThemeManageDialog
    data class Apply(val theme: SavedTheme) : ThemeManageDialog
    data class Delete(val theme: SavedTheme) : ThemeManageDialog
    data class Edit(val theme: SavedTheme) : ThemeManageDialog
}

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
    data object MigrateLegacyThemes : ThemeManageIntent
    data object OpenSaveDialog : ThemeManageIntent
    data class UpdateSaveName(val value: String) : ThemeManageIntent
    data class OpenApplyDialog(val theme: SavedTheme) : ThemeManageIntent
    data class OpenDeleteDialog(val theme: SavedTheme) : ThemeManageIntent
    data class OpenEditSheet(val theme: SavedTheme) : ThemeManageIntent
    data object DismissDialog : ThemeManageIntent
    data class RequestExport(val theme: SavedTheme? = null) : ThemeManageIntent
    data object RequestImportPackage : ThemeManageIntent
    data object RequestImportLegacyJson : ThemeManageIntent
}

sealed interface ThemeManageEffect {
    data class OpenExportDocument(val theme: SavedTheme?) : ThemeManageEffect
    data object OpenImportPackage : ThemeManageEffect
    data object OpenImportLegacyJson : ThemeManageEffect
    data class LegacyMigrationFinished(
        val migratedCount: Int,
        val failedCount: Int,
    ) : ThemeManageEffect

    data class ShowResult(
        @param:StringRes val messageRes: Int,
        val detail: String? = null,
    ) : ThemeManageEffect
}
