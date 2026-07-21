package io.legado.app.ui.config.otherConfig

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList

class OtherConfigViewModel(
    private val appLocaleGateway: AppLocaleGateway,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val checkSourceSettingsGateway: CheckSourceSettingsGateway,
    private val directLinkSettingsGateway: DirectLinkSettingsGateway,
    private val localPasswordGateway: LocalPasswordGateway,
    private val systemGateway: OtherConfigSystemGateway,
    initialState: OtherConfigUiState = OtherConfigUiState(),
) : ViewModel() {

    private var clearWebViewDataJob: Job? = null
    private var restartRequested = false
    private var nextMessageId = 0L

    private val _uiState = MutableStateFlow(
        otherSettingsGateway.currentSettings.toUiState(initialState)
            .let(checkSourceSettingsGateway.currentSettings::toUiState)
            .copy(
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
        viewModelScope.launch {
            checkSourceSettingsGateway.settings.collect { settings ->
                _uiState.update(settings::toUiState)
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
            is OtherConfigIntent.CheckSourceTimeoutChanged ->
                _uiState.update { it.copy(checkSourceTimeoutSeconds = intent.value) }
            is OtherConfigIntent.CheckSearchChanged -> _uiState.update {
                it.copy(
                    checkSearch = intent.value,
                    checkDiscovery = if (!intent.value && !it.checkDiscovery) true else it.checkDiscovery,
                )
            }
            is OtherConfigIntent.CheckDiscoveryChanged -> _uiState.update {
                it.copy(
                    checkDiscovery = intent.value,
                    checkSearch = if (!intent.value && !it.checkSearch) true else it.checkSearch,
                )
            }
            is OtherConfigIntent.CheckInfoChanged -> _uiState.update {
                it.copy(
                    checkInfo = intent.value,
                    checkCategory = if (intent.value) it.checkCategory else false,
                    checkContent = if (intent.value) it.checkContent else false,
                )
            }
            is OtherConfigIntent.CheckCategoryChanged -> _uiState.update {
                it.copy(
                    checkCategory = intent.value,
                    checkContent = if (intent.value) it.checkContent else false,
                )
            }
            is OtherConfigIntent.CheckContentChanged ->
                _uiState.update { it.copy(checkContent = intent.value) }
            OtherConfigIntent.ConfirmCheckSource -> {
                saveCheckSourceConfig()
            }
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
                    showMessage(R.string.clear_webview_data_success)
                    _effects.tryEmit(OtherConfigEffect.RestartApp)
                }.onFailure {
                    AppLog.put("清除 WebView 数据失败", it)
                    showMessage(R.string.clear_webview_data_failed)
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

    private fun saveCheckSourceConfig() {
        val state = _uiState.value
        if (state.checkSourceTimeoutSeconds <= 0L) {
            showMessage(R.string.error)
            return
        }
        viewModelScope.launch {
            runCatching {
                checkSourceSettingsGateway.update(
                    CheckSourceSettings(
                        timeoutMillis = state.checkSourceTimeoutSeconds * 1_000L,
                        checkSearch = state.checkSearch,
                        checkDiscovery = state.checkDiscovery,
                        checkInfo = state.checkInfo,
                        checkCategory = state.checkCategory,
                        checkContent = state.checkContent,
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(activeOverlay = null) }
            }.onFailure {
                showMessage(it.localizedMessage ?: "设置失败")
            }
        }
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
            showMessage(R.string.complete_required_information)
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

    private fun showMessage(@StringRes resId: Int) {
        _uiState.update {
            it.copy(
                pendingMessages = (
                    it.pendingMessages + OtherConfigMessage.resource(++nextMessageId, resId)
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

private fun CheckSourceSettings.toUiState(current: OtherConfigUiState): OtherConfigUiState =
    current.copy(
        checkSourceTimeoutSeconds = timeoutMillis / 1_000L,
        checkSearch = checkSearch,
        checkDiscovery = checkDiscovery,
        checkInfo = checkInfo,
        checkCategory = checkCategory,
        checkContent = checkContent,
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
