package io.legado.app.feature.settings.otherconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.AppLogStore
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-8a：从 `:app` 的 `io.legado.app.ui.config.otherConfig` 迁来（本片只迁逻辑层，见 Contract 注释）。
//
// 这个 VM **本来就已经全走 gateway** —— 它需要的八个依赖里七个是共享契约
// （`OtherSettingsGateway` / `ReadAloudSettingsGateway` / `AppLocaleGateway` /
// `DownloadCacheSettingsGateway` / `DirectLinkSettingsGateway` / `LocalPasswordGateway` /
// `OtherConfigSystemGateway`），都是先前切片的成果。所以本片只需处理两处平台耦合：
//
// 1. **`R.string.*` 三处 → [OtherConfigMessageRes] 枚举**（`showMessage` 的资源重载）。
//    契约侧同步改造，见 `OtherConfigContract` 的注释。
// 2. **`AppLog.put(...)` → `AppLogStore.put(...)`**（`:core:platform` 的内存环形缓冲 + 落盘）。
//    ⚠️ 语义有一处**刻意的差异**：`:app` 的 `AppLog` 保留了「轻提示（`toast = true` 时
//    `appCtx.toastOnUi`）」与「debug 构建的 Logcat 直投」，而共享的 `AppLogStore` 两者都不做
//    —— 这也是当初把它留在 `:app` 当薄适配层的原因（见 `AppLogStore` 的 KDoc）。
//    这里迁移前那行是 `AppLog.put(message, throwable)`（两参、**不带 toast**），所以行为等价：
//    只记录 + 落盘，不弹提示、也没有 Logcat 差异。
class OtherConfigViewModel(
    private val appLocaleGateway: AppLocaleGateway,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val directLinkSettingsGateway: DirectLinkSettingsGateway,
    private val localPasswordGateway: LocalPasswordGateway,
    private val systemGateway: OtherConfigSystemGateway,
    initialState: OtherConfigUiState = OtherConfigUiState(),
) : ViewModel() {

    private var clearWebViewDataJob: Job? = null
    private var restartRequested = false
    private var nextMessageId = 0L

    private val _uiState = MutableStateFlow(
        otherSettingsGateway.currentSettings.toUiState(initialState).copy(
            mediaButtonOnExit = readAloudSettingsGateway.currentSettings.mediaButtonOnExit,
            readAloudByMediaButton =
                readAloudSettingsGateway.currentSettings.readAloudByMediaButton,
            ignoreAudioFocus = readAloudSettingsGateway.currentSettings.ignoreAudioFocus,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<OtherConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            appLocaleGateway.language.collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        updateOtherSetting { it.copy(processText = systemGateway.isProcessTextEnabled()) }
        viewModelScope.launch {
            otherSettingsGateway.settings.collect { settings ->
                _uiState.update { settings.toUiState(it) }
            }
        }
        viewModelScope.launch {
            readAloudSettingsGateway.settings.collect { preferences ->
                _uiState.update {
                    it.copy(
                        mediaButtonOnExit = preferences.mediaButtonOnExit,
                        readAloudByMediaButton = preferences.readAloudByMediaButton,
                        ignoreAudioFocus = preferences.ignoreAudioFocus,
                    )
                }
            }
        }
        loadDirectLinkConfiguration()
    }

    fun onIntent(intent: OtherConfigIntent) {
        when (intent) {
            is OtherConfigIntent.LanguageChanged -> appLocaleGateway.setLanguage(intent.value)
            is OtherConfigIntent.UpdateToVariantChanged ->
                updateOtherSetting { it.copy(updateToVariant = intent.value) }
            is OtherConfigIntent.AutoCheckUpdateOnStartChanged ->
                updateOtherSetting { it.copy(autoCheckUpdateOnStart = intent.value) }
            is OtherConfigIntent.WebServiceAutoStartChanged ->
                updateOtherSetting { it.copy(webServiceAutoStart = intent.value) }
            is OtherConfigIntent.AutoRefreshChanged ->
                updateOtherSetting { it.copy(autoRefresh = intent.value) }
            is OtherConfigIntent.DefaultToReadChanged ->
                updateOtherSetting { it.copy(defaultToRead = intent.value) }
            is OtherConfigIntent.FirebaseEnableChanged ->
                updateOtherSetting { it.copy(firebaseEnable = intent.value) }
            is OtherConfigIntent.DefaultBookTreeUriChanged ->
                updateOtherSetting { it.copy(defaultBookTreeUri = intent.value) }
            is OtherConfigIntent.AntiAliasChanged ->
                updateOtherSetting { it.copy(antiAlias = intent.value) }
            is OtherConfigIntent.ReplaceEnableDefaultChanged ->
                updateOtherSetting { it.copy(replaceEnableDefault = intent.value) }
            is OtherConfigIntent.MediaButtonOnExitChanged ->
                updateReadAloudSetting { it.copy(mediaButtonOnExit = intent.value) }
            is OtherConfigIntent.ReadAloudByMediaButtonChanged ->
                updateReadAloudSetting { it.copy(readAloudByMediaButton = intent.value) }
            is OtherConfigIntent.IgnoreAudioFocusChanged ->
                updateReadAloudSetting { it.copy(ignoreAudioFocus = intent.value) }
            is OtherConfigIntent.AutoClearExpiredChanged ->
                updateOtherSetting { it.copy(autoClearExpired = intent.value) }
            is OtherConfigIntent.ShowAddToShelfAlertChanged ->
                updateOtherSetting { it.copy(showAddToShelfAlert = intent.value) }
            is OtherConfigIntent.ShowMangaUiChanged ->
                updateOtherSetting { it.copy(showMangaUi = intent.value) }
            is OtherConfigIntent.WebServiceWakeLockChanged ->
                updateOtherSetting { it.copy(webServiceWakeLock = intent.value) }
            is OtherConfigIntent.SourceEditMaxLineChanged ->
                updateOtherSetting { it.copy(sourceEditMaxLine = intent.value) }
            is OtherConfigIntent.WebPortChanged -> {
                updateOtherSetting(
                    onSuccess = {
                        _effects.tryEmit(OtherConfigEffect.RestartWebService)
                    },
                ) { it.copy(webPort = intent.value) }
            }
            is OtherConfigIntent.ProcessTextChanged -> setProcessTextEnable(intent.value)
            is OtherConfigIntent.RecordLogChanged ->
                updateOtherSetting { it.copy(recordLog = intent.value) }
            is OtherConfigIntent.RecordHeapDumpChanged ->
                updateOtherSetting { it.copy(recordHeapDump = intent.value) }
            is OtherConfigIntent.DirectUploadUrlChanged ->
                _uiState.update { it.copy(directUploadUrl = intent.value) }
            is OtherConfigIntent.DirectDownloadUrlRuleChanged ->
                _uiState.update { it.copy(directDownloadUrlRule = intent.value) }
            is OtherConfigIntent.DirectSummaryChanged ->
                _uiState.update { it.copy(directSummary = intent.value) }
            is OtherConfigIntent.DirectCompressChanged ->
                _uiState.update { it.copy(directCompress = intent.value) }
            is OtherConfigIntent.DirectRuleChanged -> _uiState.update {
                it.copy(
                    directUploadUrl = intent.uploadUrl,
                    directDownloadUrlRule = intent.downloadUrlRule,
                    directSummary = intent.summary,
                    directCompress = intent.compress,
                )
            }
            OtherConfigIntent.ConfirmDirectLinkRule -> {
                saveDirectLinkRule()
            }
            OtherConfigIntent.TestDirectLinkRule -> testRule()
            OtherConfigIntent.DismissDirectTestResult ->
                _uiState.update { it.copy(directTestResult = null) }
            is OtherConfigIntent.ShowOverlay -> {
                _uiState.update { it.copy(activeOverlay = intent.overlay) }
                if (intent.overlay == OtherConfigOverlay.DirectLinkUpload) {
                    loadDirectLinkConfiguration()
                }
            }
            OtherConfigIntent.DismissOverlay ->
                _uiState.update { it.copy(activeOverlay = null) }
            OtherConfigIntent.RequestNotificationPermission ->
                _effects.tryEmit(OtherConfigEffect.RequestNotificationPermission)
            OtherConfigIntent.RequestBatteryPermission ->
                _effects.tryEmit(OtherConfigEffect.RequestBatteryPermission)
            OtherConfigIntent.RequestSystemDirectory ->
                _effects.tryEmit(OtherConfigEffect.OpenSystemDirectory)
            OtherConfigIntent.ConfirmClearWebViewData -> {
                _uiState.update { it.copy(activeOverlay = null) }
                clearWebViewData()
            }
            is OtherConfigIntent.SaveLocalPassword -> saveLocalPassword(intent.password)
            is OtherConfigIntent.MessageShown -> {
                _uiState.update { state ->
                    state.copy(
                        pendingMessages = state.pendingMessages
                            .filterNot { it.id == intent.id }
                            .toImmutableList()
                    )
                }
            }
        }
    }

    private fun updateOtherSetting(
        onSuccess: () -> Unit = {},
        transform: (OtherSettings) -> OtherSettings,
    ) {
        viewModelScope.launch {
            runCatching { otherSettingsGateway.update(transform) }
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    showMessage(error.message ?: error.javaClass.simpleName)
                }
        }
    }

    private fun updateReadAloudSetting(
        transform: (ReadAloudSettings) -> ReadAloudSettings,
    ) {
        viewModelScope.launch {
            runCatching { readAloudSettingsGateway.update(transform) }
                .onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    private fun setProcessTextEnable(enable: Boolean) {
        viewModelScope.launch {
            val previous = systemGateway.isProcessTextEnabled()
            runCatching {
                systemGateway.setProcessTextEnabled(enable)
                otherSettingsGateway.update { it.copy(processText = enable) }
            }.onFailure {
                if (previous != enable) {
                    runCatching { systemGateway.setProcessTextEnabled(previous) }
                }
                showMessage(it.localizedMessage ?: "设置失败")
            }
        }
    }

    private fun clearWebViewData() {
        if (clearWebViewDataJob?.isActive == true || restartRequested) return

        clearWebViewDataJob = viewModelScope.launch {
            runCatching { systemGateway.clearWebViewData() }
                .onSuccess {
                    restartRequested = true
                    showMessage(OtherConfigMessageRes.ClearWebViewDataSuccess)
                    _effects.tryEmit(OtherConfigEffect.RestartApp)
                }.onFailure {
                    AppLogStore.put("清除 WebView 数据失败", it)
                    showMessage(OtherConfigMessageRes.ClearWebViewDataFailed)
                }
        }
    }

    private fun saveLocalPassword(password: String) {
        viewModelScope.launch {
            runCatching { localPasswordGateway.setPassword(password) }
                .onSuccess { _uiState.update { it.copy(activeOverlay = null) } }
                .onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    fun saveUserAgent(input: String) {
        viewModelScope.launch {
            runCatching {
                downloadCacheSettingsGateway.update { it.copy(userAgent = input) }
            }.onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    fun updateLocalBookDir(path: String) {
        updateOtherSetting { it.copy(defaultBookTreeUri = path) }
    }

    private fun updateDirectLinkRule(rule: DirectLinkRule) {
        _uiState.update {
            it.copy(
                directUploadUrl = rule.uploadUrl,
                directDownloadUrlRule = rule.downloadUrlRule,
                directSummary = rule.summary,
                directCompress = rule.compress,
            )
        }
    }

    private fun loadDirectLinkConfiguration() {
        viewModelScope.launch {
            runCatching {
                directLinkSettingsGateway.loadRule() to
                    directLinkSettingsGateway.loadDefaultRules()
            }.onSuccess { (rule, presets) ->
                updateDirectLinkRule(rule)
                _uiState.update { state ->
                    state.copy(
                        directRulePresets = presets.map(DirectLinkRule::toUi).toImmutableList()
                    )
                }
            }.onFailure {
                showMessage(it.localizedMessage ?: "设置失败")
            }
        }
    }

    private fun saveDirectLinkRule() {
        val state = _uiState.value
        if (state.directUploadUrl.isBlank() ||
            state.directDownloadUrlRule.isBlank() ||
            state.directSummary.isBlank()
        ) {
            showMessage(OtherConfigMessageRes.CompleteRequiredInformation)
            return
        }
        val rule = DirectLinkRule(
            state.directUploadUrl,
            state.directDownloadUrlRule,
            state.directSummary,
            state.directCompress,
        )
        viewModelScope.launch {
            runCatching { directLinkSettingsGateway.saveRule(rule) }
                .onSuccess { _uiState.update { it.copy(activeOverlay = null) } }
                .onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    private fun testRule() {
        val state = _uiState.value
        viewModelScope.launch {
            val rule = DirectLinkRule(
                state.directUploadUrl,
                state.directDownloadUrlRule,
                state.directSummary,
                state.directCompress,
            )
            runCatching { directLinkSettingsGateway.testRule(rule) }.onSuccess {
                _uiState.update { state -> state.copy(directTestResult = it) }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(directTestResult = it.localizedMessage ?: "ERROR")
                }
            }
        }
    }

    private fun showMessage(res: OtherConfigMessageRes) {
        _uiState.update {
            it.copy(
                pendingMessages = (
                    it.pendingMessages + OtherConfigMessage.resource(++nextMessageId, res)
                ).toImmutableList()
            )
        }
    }

    private fun showMessage(message: String) {
        _uiState.update {
            it.copy(
                pendingMessages = (
                    it.pendingMessages + OtherConfigMessage.text(++nextMessageId, message)
                ).toImmutableList()
            )
        }
    }
}

private fun DirectLinkRule.toUi() = DirectLinkRuleUi(
    uploadUrl = uploadUrl,
    downloadUrlRule = downloadUrlRule,
    summary = summary,
    compress = compress,
)

private fun OtherSettings.toUiState(current: OtherConfigUiState): OtherConfigUiState =
    current.copy(
        updateToVariant = updateToVariant,
        autoCheckUpdateOnStart = autoCheckUpdateOnStart,
        webServiceAutoStart = webServiceAutoStart,
        autoRefresh = autoRefresh,
        defaultToRead = defaultToRead,
        firebaseEnable = firebaseEnable,
        defaultBookTreeUri = defaultBookTreeUri,
        antiAlias = antiAlias,
        replaceEnableDefault = replaceEnableDefault,
        autoClearExpired = autoClearExpired,
        showAddToShelfAlert = showAddToShelfAlert,
        showMangaUi = showMangaUi,
        webServiceWakeLock = webServiceWakeLock,
        sourceEditMaxLine = sourceEditMaxLine,
        webPort = webPort,
        processText = processText,
        recordLog = recordLog,
        recordHeapDump = recordHeapDump,
    )
