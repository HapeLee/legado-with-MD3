package io.legado.app.feature.settings.thememanage

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.settings.ThemeExportData
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

/**
 * M5-16a：从 `:app` 的 `ui/config/themeManage/ThemeManageViewModel.kt` 迁入（**逻辑逐字保留**）。
 *
 * 三处与迁移前的**有意差异**，都不是顺手改的：
 *
 * 1. **平台操作走窄契约** [ThemeManagePlatform]（迁移前直接注入 `:app` 的 `ThemePackageManager`
 *    —— 1254 行，深绑 `Context` / `Uri` / `AppCompatDelegate` / GSON）。理由见契约 KDoc。
 * 2. **UI 状态里的主题是投影** [SavedThemeSummary] 而不是 `:app` 的 `SavedTheme`
 *    （后者携带 GSON 反射的 `ThemePackageManifest`，进不了 `commonMain`）。
 * 3. **结果文案从 `@StringRes Int` 换成语义枚举** [ThemeManageText]（迁移前是
 *    `R.string.*` 的 id）。共享层拿不到 `R`；而这条 effect 由**宿主壳**消费（它要
 *    `context.toastOnUi`）⇒ 在宿主侧把枚举映射回 `:app` 自己的 `R.string.*`。
 *    ⚠️ 因此本片**没有**把 7 条文案搬进 `:feature:settings` 的 composeResources：文案属于
 *    宿主那次 toast 的渲染，搬过来只会得到两份要同步的副本。
 *
 * `LegacyMigrationFinished` 仍然只带两个计数（迁移前也是如此）——拼文案要用
 * `theme_manage_migrate_success` / `_partial` 两个**带参数**的宿主文案，留在宿主拼。
 */
class ThemeManageViewModel(
    private val platform: ThemeManagePlatform,
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
            is ThemeManageIntent.UpdateSearchQuery -> _uiState.update {
                it.copy(searchQuery = intent.value)
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
            val themes = platform.loadSavedThemes()
            val hasLegacyThemes = platform.hasLegacySavedThemes()
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
        val themes = platform.loadSavedThemes()
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
                platform.saveTheme(
                    name = intent.name,
                    data = intent.data,
                )
                intent.replacedTheme
                    ?.takeIf { it.name != intent.name }
                    ?.let { platform.deleteSavedTheme(it.name).getOrThrow() }
            }
            if (result.isSuccess) {
                refreshSavedThemes()
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        text = ThemeManageText.SaveFailed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun applySavedTheme(theme: SavedThemeSummary) {
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val result = platform.applySavedTheme(theme.name)
            if (result.isSuccess) {
                // Compose 由响应式设置直接更新；旧 View 由 BaseActivity 的配置兼容层处理。
                refreshSavedThemes()
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        text = ThemeManageText.ApplyFailed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun deleteSavedTheme(theme: SavedThemeSummary) {
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val result = platform.deleteSavedTheme(theme.name)
            if (result.isSuccess) {
                refreshSavedThemes()
            } else {
                _uiState.update { it.copy(loading = false) }
                _effects.emit(
                    ThemeManageEffect.ShowResult(
                        text = ThemeManageText.DeleteFailed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                )
            }
        }
    }

    private fun exportPackage(intent: ThemeManageIntent.ExportPackage) {
        launchExclusive {
            val result = platform.exportPackage(
                uri = intent.uri,
                themeName = intent.themeName,
                themeData = intent.themeData,
                savedThemeName = intent.savedThemeName,
            )
            _effects.emit(
                if (result.isSuccess) {
                    ThemeManageEffect.ShowResult(ThemeManageText.ExportSuccess)
                } else {
                    ThemeManageEffect.ShowResult(
                        text = ThemeManageText.ExportFailed,
                        detail = result.exceptionOrNull()?.localizedMessage,
                    )
                }
            )
        }
    }

    private fun importPackage(uri: String) {
        launchExclusive {
            emitImportResult(platform.importPackage(uri))
        }
    }

    private fun importLegacyJson(uri: String) {
        launchExclusive {
            emitImportResult(platform.importLegacyJson(uri))
        }
    }

    private suspend fun emitImportResult(result: Result<Unit>) {
        if (result.isSuccess) {
            refreshSavedThemes()
        }
        _effects.emit(
            if (result.isSuccess) {
                ThemeManageEffect.ShowResult(
                    text = ThemeManageText.ImportSuccess,
                )
            } else {
                ThemeManageEffect.ShowResult(
                    text = ThemeManageText.ImportFailed,
                    detail = result.exceptionOrNull()?.localizedMessage,
                )
            }
        )
    }

    private fun migrateLegacyThemes() {
        launchExclusive {
            _uiState.update { it.copy(loading = true) }
            val result = platform.migrateLegacySavedThemes()
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
    val savedThemes: ImmutableList<SavedThemeSummary> = persistentListOf(),
    val searchQuery: String = "",
    val hasLegacyThemes: Boolean = false,
    val dialog: ThemeManageDialog? = null,
)

sealed interface ThemeManageDialog {
    data class Save(val name: String = "") : ThemeManageDialog
    data class Apply(val theme: SavedThemeSummary) : ThemeManageDialog
    data class Delete(val theme: SavedThemeSummary) : ThemeManageDialog
    data class Edit(val theme: SavedThemeSummary) : ThemeManageDialog
}

sealed interface ThemeManageIntent {
    data object LoadSavedThemes : ThemeManageIntent

    data class ExportPackage(
        val uri: String,
        val themeName: String? = null,
        val themeData: ThemeExportData? = null,
        val savedThemeName: String? = null,
    ) : ThemeManageIntent

    data class ImportPackage(val uri: String) : ThemeManageIntent
    data class ImportLegacyJson(val uri: String) : ThemeManageIntent
    data class SaveTheme(
        val name: String,
        val data: ThemeExportData? = null,
        val replacedTheme: SavedThemeSummary? = null,
    ) : ThemeManageIntent

    data class ApplySavedTheme(val theme: SavedThemeSummary) : ThemeManageIntent
    data class DeleteSavedTheme(val theme: SavedThemeSummary) : ThemeManageIntent
    data object MigrateLegacyThemes : ThemeManageIntent
    data object OpenSaveDialog : ThemeManageIntent
    data class UpdateSaveName(val value: String) : ThemeManageIntent
    data class UpdateSearchQuery(val value: String) : ThemeManageIntent
    data class OpenApplyDialog(val theme: SavedThemeSummary) : ThemeManageIntent
    data class OpenDeleteDialog(val theme: SavedThemeSummary) : ThemeManageIntent
    data class OpenEditSheet(val theme: SavedThemeSummary) : ThemeManageIntent
    data object DismissDialog : ThemeManageIntent
    data class RequestExport(val theme: SavedThemeSummary? = null) : ThemeManageIntent
    data object RequestImportPackage : ThemeManageIntent
    data object RequestImportLegacyJson : ThemeManageIntent
}

sealed interface ThemeManageEffect {
    data class OpenExportDocument(val theme: SavedThemeSummary?) : ThemeManageEffect
    data object OpenImportPackage : ThemeManageEffect
    data object OpenImportLegacyJson : ThemeManageEffect
    data class LegacyMigrationFinished(
        val migratedCount: Int,
        val failedCount: Int,
    ) : ThemeManageEffect

    /**
     * 结果提示。[text] 是**语义枚举**，由宿主壳映射回它自己的 `R.string.*`
     * （迁移前这里直接是 `@param:StringRes val messageRes: Int`）。理由见 VM KDoc 第 3 条。
     */
    data class ShowResult(
        val text: ThemeManageText,
        val detail: String? = null,
    ) : ThemeManageEffect
}

/** 与 `:app` 的 `R.string.theme_manage_*` 一一对应（7 条，无参数）。 */
enum class ThemeManageText {
    SaveFailed,
    ApplyFailed,
    DeleteFailed,
    ExportSuccess,
    ExportFailed,
    ImportSuccess,
    ImportFailed,
}
