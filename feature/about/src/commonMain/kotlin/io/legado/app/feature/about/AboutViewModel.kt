package io.legado.app.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「关于」页的状态宿主（M5-1c）。
 *
 * 与迁移前的三点结构差异，都是「搬进共享层」的必然结果，行为逐条保持：
 *
 *  1. **不再继承 `BaseViewModel` / 不再持有 `Application`**。迁移前那些 `execute { }` 的块
 *     跑在 `Dispatchers.IO`、回调切回 `Dispatchers.Main`；现在块内是**契约调用**，IO 由各实现
 *     自己 `withContext`（Android 实现都在 IO 上），协程回到 `viewModelScope`（Main）继续，
 *     与 `executeContext = Dispatchers.Main` 等价。
 *  2. **平台动作全部改成契约调用**：更新检查 → [AppUpdateChecker]；崩溃日志/保存日志/堆转储 →
 *     [AboutDiagnostics]；内置 markdown → [BundledTextReader]。
 *  3. **文案不再就地取**：迁移前是 `context.getString(R.string.x)`，现在发
 *     `AboutEffect.ShowMessage(AboutMessage.X)` 由 UI 侧查表（理由见 [AboutMessage]）。
 *
 * 仍然留在本类的判定，是**共享层本来就看得见**的两件事：备份目录是否已设置
 * （`BackupSettings.backupPath`）与两个开关是否打开（`OtherSettings.recordLog` /
 * `recordHeapDump`）。迁移前它们也在 VM 里判，只是当时顺手用了 `context`。
 */
class AboutViewModel(
    private val otherSettingsGateway: OtherSettingsGateway,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val updateChecker: AppUpdateChecker,
    private val diagnostics: AboutDiagnostics,
    private val bundledTextReader: BundledTextReader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutUiState(updateToVariant = otherSettingsGateway.currentSettings.updateToVariant)
    )
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AboutEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            otherSettingsGateway.settings
                .map { it.updateToVariant }
                .distinctUntilChanged()
                .collect { updateToVariant ->
                    _uiState.update { it.copy(updateToVariant = updateToVariant) }
                }
        }
    }

    fun onIntent(intent: AboutIntent) {
        when (intent) {
            is AboutIntent.DismissSheet -> _uiState.update { it.copy(sheet = AboutSheet.None) }
            is AboutIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
            is AboutIntent.CheckUpdate -> checkUpdate()
            is AboutIntent.ShowMdFile -> showMdFile(intent.title, intent.fileName)
            is AboutIntent.ShowCrashLogs -> showCrashLogs()
            is AboutIntent.SaveLog -> saveLog()
            is AboutIntent.CreateHeapDump -> createHeapDump()
            is AboutIntent.OpenUrl -> _effects.tryEmit(AboutEffect.OpenUrl(intent.url))
            is AboutIntent.ClearCrashLogs -> clearCrashLogs()
            is AboutIntent.ReadCrashFile -> readCrashFile(intent.entry)
            is AboutIntent.StartDownload -> startDownload()
        }
    }

    private fun checkUpdate() {
        _uiState.update { it.copy(dialog = AboutDialog.CheckingUpdate) }
        viewModelScope.launch {
            updateChecker.check()
                .onSuccess { updateInfo ->
                    _uiState.update {
                        it.copy(sheet = AboutSheet.Update(updateInfo))
                    }
                }
                .onFailure { e ->
                    _effects.tryEmit(
                        AboutEffect.ShowMessage(
                            AboutMessage.CheckUpdateFailed,
                            detail = e.localizedMessage,
                        )
                    )
                }
            // 迁移前 onSuccess / onError / onFinally 三处都收掉进度对话框，等价于这里收一次。
            _uiState.update { it.copy(dialog = null) }
        }
    }

    private fun showMdFile(title: String, fileName: String) {
        viewModelScope.launch {
            // 读不到（文件缺失/读取失败）就不弹层——迁移前异常走 `execute{}.onError` 的空白分支，
            // 用户可见结果同样是「什么都没发生」。
            val content = bundledTextReader.readText(fileName) ?: return@launch
            _uiState.update { it.copy(sheet = AboutSheet.Markdown(title, content)) }
        }
    }

    private fun showCrashLogs() {
        viewModelScope.launch {
            val files = diagnostics.listCrashLogs()
            _uiState.update {
                it.copy(
                    crashLogFiles = files.toImmutableList(),
                    sheet = AboutSheet.CrashLogs
                )
            }
        }
    }

    private fun readCrashFile(entry: CrashLogEntry) {
        viewModelScope.launch {
            diagnostics.readCrashLog(entry)
                .onSuccess { content ->
                    _uiState.update { it.copy(sheet = AboutSheet.Markdown(entry.name, content)) }
                }
                .onFailure {
                    _effects.tryEmit(AboutEffect.ShowText(it.localizedMessage ?: ""))
                }
        }
    }

    private fun clearCrashLogs() {
        viewModelScope.launch {
            runCatching { diagnostics.clearCrashLogs() }
                .onFailure {
                    _effects.tryEmit(AboutEffect.ShowText(it.localizedMessage ?: ""))
                }
            // 迁移前无论成败都在 onFinally 里重列一次（清空后列表要变空）。
            val files = diagnostics.listCrashLogs()
            _uiState.update { it.copy(crashLogFiles = files.toImmutableList()) }
        }
    }

    private fun saveLog() {
        viewModelScope.launch {
            val backupPath = backupSettingsGateway.currentSettings.backupPath
            // ⚠️ 判空只到 `== null`，**不**用 `isNullOrEmpty()`：迁移前这里是 `?: run { ... }`，
            // 而只有 `loadCrashLogFiles` 用的是 `!isNullOrEmpty()`。两处的这个不对称是原有的，
            // 保持一致以免改变「备份目录被设成空串」时的走向。
            if (backupPath == null) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.BackupDirNotSet))
                return@launch
            }
            if (!otherSettingsGateway.currentSettings.recordLog) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.LogRecordingDisabled))
                delay(3000)
            }
            // 失败由实现侧记 AppLog（迁移前那段 `onError { AppLog.put(..., toast = true) }`
            // 只在平台侧可表达，见 `AboutDiagnostics` 的 KDoc）。
            runCatching { diagnostics.saveLogs() }
            _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.SavedToBackupDir))
        }
    }

    private fun createHeapDump() {
        viewModelScope.launch {
            val backupPath = backupSettingsGateway.currentSettings.backupPath
            if (backupPath == null) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.BackupDirNotSet))
                return@launch
            }
            if (!otherSettingsGateway.currentSettings.recordHeapDump) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.HeapDumpRecordingDisabled))
                delay(3000)
            }
            _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.HeapDumpCreating))
            // `System.gc()` 与 `CrashHandler.doHeapDump(true)` 在实现侧（迁移前就在这里）。
            val saved = runCatching { diagnostics.createHeapDump() }.getOrDefault(false)
            if (!saved) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.HeapDumpNotFound))
            } else {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.SavedToBackupDir))
            }
        }
    }

    private fun startDownload() {
        val sheet = _uiState.value.sheet
        if (sheet is AboutSheet.Update) {
            val info = sheet.updateInfo
            if (info.downloadUrl.isBlank() || info.fileName.isBlank()) {
                _effects.tryEmit(AboutEffect.ShowMessage(AboutMessage.DownloadInfoIncomplete))
            } else {
                _effects.tryEmit(AboutEffect.StartDownload(info.downloadUrl, info.fileName))
            }
        }
    }
}
