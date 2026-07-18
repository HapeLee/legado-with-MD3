package io.legado.app.ui.config.otherConfig

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsUpdate
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsUpdate
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.webView.WebViewDataCleaner
import io.legado.app.model.CheckSource
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfig
import io.legado.app.utils.putPrefString
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class OtherConfigViewModel(
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
) : ViewModel() {

    companion object {
        private const val RESTART_DELAY_MILLIS = 3_000L
    }

    private val packageManager = appCtx.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val componentName = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name
    )
    private var clearWebViewDataJob: Job? = null
    private var restartScheduled = false

    private val _uiState = MutableStateFlow(
        OtherConfigUiState(
            checkSourceTimeoutSeconds = CheckSource.timeout / 1000,
            checkSearch = CheckSource.checkSearch,
            checkDiscovery = CheckSource.checkDiscovery,
            checkInfo = CheckSource.checkInfo,
            checkCategory = CheckSource.checkCategory,
            checkContent = CheckSource.checkContent,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<OtherConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateOtherSetting(OtherSettingsUpdate.ProcessText(isProcessTextEnabled()))
        }
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
    }

    fun onIntent(intent: OtherConfigIntent) {
        when (intent) {
            is OtherConfigIntent.LanguageChanged -> {
                updateOtherSetting(OtherSettingsUpdate.Language(intent.value))
                _effects.tryEmit(OtherConfigEffect.ApplyLanguage(intent.value))
            }
            is OtherConfigIntent.UpdateToVariantChanged ->
                updateOtherSetting(OtherSettingsUpdate.UpdateToVariant(intent.value))
            is OtherConfigIntent.AutoCheckUpdateOnStartChanged ->
                updateOtherSetting(OtherSettingsUpdate.AutoCheckUpdateOnStart(intent.value))
            is OtherConfigIntent.WebServiceAutoStartChanged ->
                updateOtherSetting(OtherSettingsUpdate.WebServiceAutoStart(intent.value))
            is OtherConfigIntent.AutoRefreshChanged ->
                updateOtherSetting(OtherSettingsUpdate.AutoRefresh(intent.value))
            is OtherConfigIntent.DefaultToReadChanged ->
                updateOtherSetting(OtherSettingsUpdate.DefaultToRead(intent.value))
            is OtherConfigIntent.FirebaseEnableChanged ->
                updateOtherSetting(OtherSettingsUpdate.FirebaseEnable(intent.value))
            is OtherConfigIntent.DefaultBookTreeUriChanged ->
                updateOtherSetting(OtherSettingsUpdate.DefaultBookTreeUri(intent.value))
            is OtherConfigIntent.AntiAliasChanged ->
                updateOtherSetting(OtherSettingsUpdate.AntiAlias(intent.value))
            is OtherConfigIntent.ReplaceEnableDefaultChanged ->
                updateOtherSetting(OtherSettingsUpdate.ReplaceEnableDefault(intent.value))
            is OtherConfigIntent.MediaButtonOnExitChanged -> setMediaButtonOnExit(intent.value)
            is OtherConfigIntent.ReadAloudByMediaButtonChanged -> setReadAloudByMediaButton(intent.value)
            is OtherConfigIntent.IgnoreAudioFocusChanged -> setIgnoreAudioFocus(intent.value)
            is OtherConfigIntent.AutoClearExpiredChanged ->
                updateOtherSetting(OtherSettingsUpdate.AutoClearExpired(intent.value))
            is OtherConfigIntent.ShowAddToShelfAlertChanged ->
                updateOtherSetting(OtherSettingsUpdate.ShowAddToShelfAlert(intent.value))
            is OtherConfigIntent.ShowMangaUiChanged ->
                updateOtherSetting(OtherSettingsUpdate.ShowMangaUi(intent.value))
            is OtherConfigIntent.WebServiceWakeLockChanged ->
                updateOtherSetting(OtherSettingsUpdate.WebServiceWakeLock(intent.value))
            is OtherConfigIntent.SourceEditMaxLineChanged ->
                updateOtherSetting(OtherSettingsUpdate.SourceEditMaxLine(intent.value))
            is OtherConfigIntent.WebPortChanged -> {
                updateOtherSetting(OtherSettingsUpdate.WebPort(intent.value))
                _effects.tryEmit(OtherConfigEffect.RestartWebService)
            }
            is OtherConfigIntent.ProcessTextChanged -> setProcessTextEnable(intent.value)
            is OtherConfigIntent.RecordLogChanged ->
                updateOtherSetting(OtherSettingsUpdate.RecordLog(intent.value))
            is OtherConfigIntent.RecordHeapDumpChanged ->
                updateOtherSetting(OtherSettingsUpdate.RecordHeapDump(intent.value))
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
                if (saveCheckSourceConfig()) {
                    _uiState.update { it.copy(activeOverlay = null) }
                } else {
                    _effects.tryEmit(OtherConfigEffect.ShowMessage(R.string.error))
                }
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
                if (saveDirectLinkRule()) {
                    _uiState.update { it.copy(activeOverlay = null) }
                } else {
                    _effects.tryEmit(
                        OtherConfigEffect.ShowMessage(R.string.complete_required_information)
                    )
                }
            }
            OtherConfigIntent.TestDirectLinkRule -> testRule()
            OtherConfigIntent.DismissDirectTestResult ->
                _uiState.update { it.copy(directTestResult = null) }
            is OtherConfigIntent.ShowOverlay -> {
                _uiState.update { it.copy(activeOverlay = intent.overlay) }
                if (intent.overlay == OtherConfigOverlay.DirectLinkUpload) initDirectLinkRule()
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
        }
    }

    private fun updateOtherSetting(update: OtherSettingsUpdate) {
        viewModelScope.launch {
            runCatching { otherSettingsGateway.update(update) }
                .onFailure { error ->
                    _effects.tryEmit(
                        OtherConfigEffect.SettingsUpdateFailed(
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
        }
    }

    fun setMediaButtonOnExit(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsGateway.update(ReadAloudSettingsUpdate.MediaButtonOnExit(value))
        }
    }

    fun setReadAloudByMediaButton(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsGateway.update(ReadAloudSettingsUpdate.ReadAloudByMediaButton(value))
        }
    }

    fun setIgnoreAudioFocus(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsGateway.update(ReadAloudSettingsUpdate.IgnoreAudioFocus(value))
        }
    }

    fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setProcessTextEnable(enable: Boolean) {
        val state = if (enable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    state,
                    PackageManager.DONT_KILL_APP
                )
                updateOtherSetting(OtherSettingsUpdate.ProcessText(enable))
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    fun clearWebViewData() {
        if (clearWebViewDataJob?.isActive == true || restartScheduled) return

        clearWebViewDataJob = viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    WebViewDataCleaner.clear(appCtx)
                }.onSuccess {
                    restartScheduled = true
                    appCtx.toastOnUi(R.string.clear_webview_data_success)
                    mainHandler.postDelayed(
                        { appCtx.restart() },
                        RESTART_DELAY_MILLIS
                    )
                }.onFailure {
                    AppLog.put("清除 WebView 数据失败", it)
                    appCtx.toastOnUi(R.string.clear_webview_data_failed)
                }
            }
        }
    }

    fun setLocalPassword(password: String) {
        LocalConfig.password = password
    }

    fun saveUserAgent(input: String) {
        DownloadCacheConfig.userAgent = input
    }

    fun updateLocalBookDir(path: String) {
        updateOtherSetting(OtherSettingsUpdate.DefaultBookTreeUri(path))
    }

    fun saveCheckSourceConfig(): Boolean {
        val state = _uiState.value
        val timeoutLong = state.checkSourceTimeoutSeconds
        if (timeoutLong <= 0) return false // 验证失败

        CheckSource.timeout = timeoutLong * 1000
        CheckSource.checkSearch = state.checkSearch
        CheckSource.checkDiscovery = state.checkDiscovery
        CheckSource.checkInfo = state.checkInfo
        CheckSource.checkCategory = state.checkCategory
        CheckSource.checkContent = state.checkContent

        CheckSource.putConfig()
        appCtx.putPrefString(PreferKey.checkSource, CheckSource.summary)
        return true
    }

    fun initDirectLinkRule() {
        val rule = DirectLinkUpload.getRule()
        upView(rule)
    }

    fun upView(rule: DirectLinkUpload.Rule) {
        _uiState.update {
            it.copy(
                directUploadUrl = rule.uploadUrl,
                directDownloadUrlRule = rule.downloadUrlRule,
                directSummary = rule.summary,
                directCompress = rule.compress,
            )
        }
    }

    fun saveDirectLinkRule(): Boolean {
        val state = _uiState.value
        if (state.directUploadUrl.isBlank() ||
            state.directDownloadUrlRule.isBlank() ||
            state.directSummary.isBlank()
        ) return false
        val rule = DirectLinkUpload.Rule(
            state.directUploadUrl,
            state.directDownloadUrlRule,
            state.directSummary,
            state.directCompress,
        )
        DirectLinkUpload.putConfig(rule)
        return true
    }

    fun testRule() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            val rule = DirectLinkUpload.Rule(
                state.directUploadUrl,
                state.directDownloadUrlRule,
                state.directSummary,
                state.directCompress,
            )
            runCatching {
                DirectLinkUpload.upLoad("test.json", "{}", "application/json", rule)
            }.onSuccess {
                _uiState.update { state -> state.copy(directTestResult = it) }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(directTestResult = it.localizedMessage ?: "ERROR")
                }
            }
        }
    }


}

private fun OtherSettings.toUiState(current: OtherConfigUiState): OtherConfigUiState =
    current.copy(
        language = language,
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
