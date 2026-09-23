package io.legado.app.feature.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.model.settings.BackupSettings
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// M5-9a：从 `:app` 的 `io.legado.app.ui.config.backupConfig` 迁来（本片只迁逻辑层，见 Contract 注释）。
//
// 需要处理的平台耦合有四类：
//  ① `R.string.*` 11 处 → [BackupConfigText] 枚举（含 `Loading` 对话框标题与 `ShowMessage` 提示）；
//  ② `io.legado.app.help.storage.BackupConfig` 的四组「忽略集」→ [BackupIgnoreStore] 契约
//     （4 组 × keys/titles/isIgnored/setIgnored/save，语义见 `BackupIgnoreKind`）；
//  ③ `Uri.parse(uri).toString()`（恢复本地备份那处）→ 直接用 `uri`。**行为等价**：
//     宿主早就把 `uri.toString()` 传进来了（`RestoreLocal(it.toString())`），
//     而 `Uri.parse(s).toString() == s`（`Uri` 不重新编码已解析的字符串）；
//  ④ `String?.isContentScheme()`（`utils/StringExtensions.kt`，实现就是
//     `startsWith("content://")`）→ 见 [isContentUriPath]：内联 + 注明出处。
//     那是个纯 Kotlin 单行谓词，搬一行进共享层比多开一个契约划算，但**要写清出处**，
//     免得日后 `:app` 那个帮助函数改了判据而这里悄悄漂移。
//
// 一处**有意的结构收敛**：迁移前有 4 个逐字同形的 loader（`loadIgnoreItems` /
// `loadBackupIgnoreItems` / `loadDbIgnoreItems` / `loadBackupDbIgnoreItems`，各 9 行）——
// 它们只差「读哪一组」。契约本身就是 kind 参数化的 ⇒ 收敛成一个 [loadIgnoreItems]。
// ⚠️ 反过来说，4 个 `saveXxx` **刻意不合并**：它们并不同形 ——
// `saveIgnoreItems` / `saveBackupIgnoreItems` 各写**两组**并关弹层，
// 而 `saveDbIgnoreItems` / `saveBackupDbIgnoreItems` 只写**一组**且**不关弹层**。
// 合并它们会静默改掉弹层关闭时机。
class BackupConfigViewModel(
    private val settingsGateway: BackupSettingsGateway,
    private val webDavBackupUseCase: WebDavBackupUseCase,
    private val backupRestoreUseCase: BackupRestoreUseCase,
    private val ignoreStore: BackupIgnoreStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BackupConfigUiState(
            settings = settingsGateway.currentSettings,
            ignoreItems = loadIgnoreItems(BackupIgnoreKind.RestoreConfig),
            backupIgnoreItems = loadIgnoreItems(BackupIgnoreKind.BackupConfig),
            dbIgnoreItems = loadIgnoreItems(BackupIgnoreKind.RestoreDb),
            backupDbIgnoreItems = loadIgnoreItems(BackupIgnoreKind.BackupDb),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BackupConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onIntent(intent: BackupConfigIntent) {
        when (intent) {

            is BackupConfigIntent.SetWebDavUrl -> update { it.copy(webDavUrl = intent.value) }
            is BackupConfigIntent.SetWebDavDir -> update { it.copy(webDavDir = intent.value) }
            is BackupConfigIntent.SetWebDavDeviceName ->
                update { it.copy(webDavDeviceName = intent.value) }
            is BackupConfigIntent.SetSyncBookProgress -> setSyncBookProgress(intent.value)
            is BackupConfigIntent.SetSyncBookProgressPlus ->
                update { it.copy(syncBookProgressPlus = intent.value) }
            is BackupConfigIntent.SetAutoCheckNewBackup ->
                update { it.copy(autoCheckNewBackup = intent.value) }
            is BackupConfigIntent.SetOnlyLatestBackup ->
                update { it.copy(onlyLatestBackup = intent.value) }
            is BackupConfigIntent.SetBackupSyncMode ->
                update { it.copy(backupSyncMode = intent.value) }
            is BackupConfigIntent.OpenSheet -> _uiState.update { it.copy(activeSheet = intent.sheet) }
            BackupConfigIntent.DismissSheet -> _uiState.update { it.copy(activeSheet = null) }
            BackupConfigIntent.OpenWebDavAuth -> openWebDavAuth()
            is BackupConfigIntent.EditWebDavAccount -> updateAuth { it.copy(account = intent.value) }
            is BackupConfigIntent.EditWebDavPassword -> updateAuth { it.copy(password = intent.value) }
            BackupConfigIntent.TogglePasswordVisibility ->
                updateAuth { it.copy(passwordVisible = !it.passwordVisible) }
            BackupConfigIntent.SaveWebDavAuth -> saveWebDavAuth()
            BackupConfigIntent.TestWebDav -> testWebDav()
            BackupConfigIntent.OpenIgnoreDialog ->
                _uiState.update { it.copy(activeSheet = BackupConfigSheet.IgnoreRestoreItems) }
            is BackupConfigIntent.ToggleIgnoreItem -> toggleIgnoreItem(intent.key, intent.value)
            BackupConfigIntent.SaveIgnoreItems -> saveIgnoreItems()
            BackupConfigIntent.OpenBackupIgnoreDialog ->
                _uiState.update { it.copy(activeSheet = BackupConfigSheet.IgnoreBackupItems) }

            is BackupConfigIntent.ToggleBackupIgnoreItem -> toggleBackupIgnoreItem(
                intent.key,
                intent.value
            )

            BackupConfigIntent.SaveBackupIgnoreItems -> saveBackupIgnoreItems()
            is BackupConfigIntent.ToggleDbIgnoreItem -> toggleDbIgnoreItem(intent.key, intent.value)
            BackupConfigIntent.SaveDbIgnoreItems -> saveDbIgnoreItems()
            is BackupConfigIntent.ToggleBackupDbIgnoreItem -> toggleBackupDbIgnoreItem(
                intent.key,
                intent.value
            )

            BackupConfigIntent.SaveBackupDbIgnoreItems -> saveBackupDbIgnoreItems()
            BackupConfigIntent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            BackupConfigIntent.SelectBackupDirectory -> launchDirectoryPicker(runBackup = false)
            BackupConfigIntent.SelectBackupAndRunDirectory -> launchDirectoryPicker(runBackup = true)
            is BackupConfigIntent.BackupDirectorySelected -> saveBackupPath(intent.path, intent.runBackup)
            is BackupConfigIntent.RequestBackup -> requestBackup(intent.mode)
            is BackupConfigIntent.PerformBackup -> performBackup(intent.path, intent.mode)
            BackupConfigIntent.RequestLocalRestore -> requestLocalRestore()
            is BackupConfigIntent.RestoreLocal -> restoreLocal(intent.uri)
            BackupConfigIntent.RequestNetworkRestore -> loadNetworkBackups()
            is BackupConfigIntent.RestoreNetwork -> restoreNetwork(intent.name)
            BackupConfigIntent.ConfirmLocalRestoreFallback -> requestLocalRestore()
            BackupConfigIntent.RequestImportOldData ->
                _effects.tryEmit(BackupConfigEffect.LaunchImportOldDataPicker)
        }
    }

    private fun update(transform: (BackupSettings) -> BackupSettings) {
        viewModelScope.launch { settingsGateway.update(transform) }
    }

    private fun setSyncBookProgress(value: Boolean) {
        viewModelScope.launch {
            settingsGateway.update {
                it.copy(
                    syncBookProgress = value,
                    syncBookProgressPlus = it.syncBookProgressPlus && value,
                )
            }
        }
    }

    private fun openWebDavAuth() {
        val settings = _uiState.value.settings
        _uiState.update {
            it.copy(
                activeDialog = BackupConfigDialog.WebDavAuth(
                    account = settings.webDavAccount,
                    password = settings.webDavPassword,
                )
            )
        }
    }

    private fun updateAuth(transform: (BackupConfigDialog.WebDavAuth) -> BackupConfigDialog.WebDavAuth) {
        _uiState.update { state ->
            val dialog = state.activeDialog as? BackupConfigDialog.WebDavAuth ?: return@update state
            state.copy(activeDialog = transform(dialog))
        }
    }

    private fun saveWebDavAuth() {
        val dialog = _uiState.value.activeDialog as? BackupConfigDialog.WebDavAuth ?: return
        viewModelScope.launch {
            settingsGateway.update {
                it.copy(webDavAccount = dialog.account, webDavPassword = dialog.password)
            }
            testWebDav()
        }
    }

    private fun testWebDav() {
        _uiState.update {
            it.copy(activeDialog = BackupConfigDialog.Loading(BackupConfigText.TestSyncLoading))
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { webDavBackupUseCase.test() }.getOrDefault(false)
            }
            _uiState.update { it.copy(activeDialog = null) }
            _effects.tryEmit(
                BackupConfigEffect.ShowMessage(
                    if (success) {
                        BackupConfigText.TestSyncSuccess
                    } else {
                        BackupConfigText.TestSyncFail
                    }
                )
            )
        }
    }

    private fun toggleIgnoreItem(key: String, value: Boolean) {
        _uiState.update { state ->
            state.copy(
                ignoreItems = state.ignoreItems.map { item ->
                    if (item.key == key) item.copy(checked = value) else item
                }.toImmutableList()
            )
        }
    }

    private fun saveIgnoreItems() {
        _uiState.value.ignoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.RestoreConfig, item.key, item.checked)
        }
        _uiState.value.dbIgnoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.RestoreDb, item.key, item.checked)
        }
        ignoreStore.save(BackupIgnoreKind.RestoreConfig)
        ignoreStore.save(BackupIgnoreKind.RestoreDb)
        _uiState.update { it.copy(activeSheet = null) }
    }

    private fun toggleBackupIgnoreItem(key: String, value: Boolean) {
        _uiState.update { state ->
            state.copy(
                backupIgnoreItems = state.backupIgnoreItems.map { item ->
                    if (item.key == key) item.copy(checked = value) else item
                }.toImmutableList()
            )
        }
    }

    private fun saveBackupIgnoreItems() {
        _uiState.value.backupIgnoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.BackupConfig, item.key, item.checked)
        }
        _uiState.value.backupDbIgnoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.BackupDb, item.key, item.checked)
        }
        ignoreStore.save(BackupIgnoreKind.BackupConfig)
        ignoreStore.save(BackupIgnoreKind.BackupDb)
        _uiState.update { it.copy(activeSheet = null) }
    }

    private fun toggleDbIgnoreItem(key: String, value: Boolean) {
        _uiState.update { state ->
            state.copy(
                dbIgnoreItems = state.dbIgnoreItems.map { item ->
                    if (item.key == key) item.copy(checked = value) else item
                }.toImmutableList()
            )
        }
    }

    private fun saveDbIgnoreItems() {
        _uiState.value.dbIgnoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.RestoreDb, item.key, item.checked)
        }
        ignoreStore.save(BackupIgnoreKind.RestoreDb)
    }

    private fun toggleBackupDbIgnoreItem(key: String, value: Boolean) {
        _uiState.update { state ->
            state.copy(
                backupDbIgnoreItems = state.backupDbIgnoreItems.map { item ->
                    if (item.key == key) item.copy(checked = value) else item
                }.toImmutableList()
            )
        }
    }

    private fun saveBackupDbIgnoreItems() {
        _uiState.value.backupDbIgnoreItems.forEach { item ->
            ignoreStore.setIgnored(BackupIgnoreKind.BackupDb, item.key, item.checked)
        }
        ignoreStore.save(BackupIgnoreKind.BackupDb)
    }

    private fun launchDirectoryPicker(runBackup: Boolean) {
        _uiState.update { it.copy(activeSheet = null) }
        _effects.tryEmit(
            if (runBackup) BackupConfigEffect.LaunchBackupAndRunDirectoryPicker
            else BackupConfigEffect.LaunchBackupDirectoryPicker
        )
    }

    private fun saveBackupPath(path: String, runBackup: Boolean) {
        viewModelScope.launch {
            settingsGateway.update { it.copy(backupPath = path) }
            if (runBackup && path.isNotEmpty()) requestBackup("both", path)
        }
    }

    private fun requestBackup(mode: String, selectedPath: String? = null) {
        _uiState.update { it.copy(activeSheet = null) }
        val path = selectedPath ?: _uiState.value.settings.backupPath.orEmpty()
        if (path.isEmpty() && mode != "webdav") return
        if (path.isNotEmpty() && !path.isContentUriPath()) {
            _effects.tryEmit(BackupConfigEffect.RequestStoragePermission(path, mode))
        } else {
            performBackup(path, mode)
        }
    }

    private fun performBackup(path: String, mode: String) {
        _uiState.update {
            it.copy(activeDialog = BackupConfigDialog.Loading(BackupConfigText.BackingUp))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backupRestoreUseCase.backup(path, mode) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(activeDialog = null) }
                        _effects.tryEmit(
                            BackupConfigEffect.ShowMessage(BackupConfigText.BackupSuccess)
                        )
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(activeDialog = null) }
                        _effects.tryEmit(
                            BackupConfigEffect.ShowMessage(
                                BackupConfigText.BackupFail,
                                error.localizedMessage,
                            )
                        )
                    }
                }
        }
    }

    private fun requestLocalRestore() {
        _uiState.update { it.copy(activeSheet = null, activeDialog = null) }
        _effects.tryEmit(BackupConfigEffect.LaunchRestoreFilePicker)
    }

    private fun restoreLocal(uri: String) {
        _uiState.update {
            it.copy(activeDialog = BackupConfigDialog.Loading(BackupConfigText.Restoring))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backupRestoreUseCase.restoreLocal(uri) }
                .fold(
                    onSuccess = { finishRestoreSuccess() },
                    onFailure = { finishRestoreFailure(it.localizedMessage) },
                )
        }
    }

    private fun loadNetworkBackups() {
        _uiState.update {
            it.copy(activeSheet = null, activeDialog = BackupConfigDialog.Loading(BackupConfigText.Loading))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { webDavBackupUseCase.getBackupNames() }
                .onSuccess { names ->
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                activeDialog = null,
                                activeSheet = BackupConfigSheet.RestoreFiles,
                                backupNames = names.toImmutableList(),
                            )
                        }
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                activeDialog = BackupConfigDialog.ConfirmLocalRestoreFallback(
                                    error.localizedMessage
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun restoreNetwork(name: String) {
        _uiState.update {
            it.copy(activeSheet = null, activeDialog = BackupConfigDialog.Loading(BackupConfigText.Restoring))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { webDavBackupUseCase.restore(name) }
                .fold(
                    onSuccess = { finishRestoreSuccess() },
                    onFailure = { finishRestoreFailure(it.localizedMessage, webDav = true) },
                )
        }
    }

    private suspend fun finishRestoreSuccess() = withContext(Dispatchers.Main) {
        _uiState.update { it.copy(activeDialog = null) }
        _effects.tryEmit(BackupConfigEffect.ShowMessage(BackupConfigText.RestoreSuccess))
    }

    private suspend fun finishRestoreFailure(message: String?, webDav: Boolean = false) =
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(activeDialog = null) }
            _effects.tryEmit(
                BackupConfigEffect.ShowMessage(
                    if (webDav) {
                        BackupConfigText.WebDavRestoreFail
                    } else {
                        BackupConfigText.RestoreFailWithError
                    },
                    message,
                )
            )
        }

    /** 见文件头注释的「有意的结构收敛」：4 个同形 loader 收敛成一个 kind 参数化版本。 */
    private fun loadIgnoreItems(kind: BackupIgnoreKind) = ignoreStore.keys(kind)
        .mapIndexed { index, key ->
            BackupIgnoreItem(
                key = key,
                title = ignoreStore.titles(kind)[index],
                checked = ignoreStore.isIgnored(kind, key),
            )
        }
        .toImmutableList()
}

/**
 * 见文件头注释第 ④ 条：`:app` 的 `String?.isContentScheme()`
 * （`utils/StringExtensions.kt`）实现就是 `this?.startsWith("content://") == true`。
 * 这里内联同名判据；`:app` 侧若改动，这里必须跟着改。
 */
private fun String.isContentUriPath(): Boolean = startsWith("content://")
