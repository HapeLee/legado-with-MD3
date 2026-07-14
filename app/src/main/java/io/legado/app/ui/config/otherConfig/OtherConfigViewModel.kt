package io.legado.app.ui.config.otherConfig

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadAloudPreferences
import io.legado.app.data.repository.ReadAloudSettingsRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class OtherConfigViewModel(
    private val readAloudSettingsRepository: ReadAloudSettingsRepository
) : ViewModel() {

    companion object {
        private const val RESTART_DELAY_MILLIS = 3_000L
    }

    private val packageManager = appCtx.packageManager
    private val componentName = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name
    )
    private var clearWebViewDataJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            OtherConfig.processText = isProcessTextEnabled()
        }
    }

    val readAloudPreferences = readAloudSettingsRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReadAloudPreferences()
    )

    fun setMediaButtonOnExit(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsRepository.setMediaButtonOnExit(value)
        }
    }

    fun setReadAloudByMediaButton(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsRepository.setReadAloudByMediaButton(value)
        }
    }

    fun setIgnoreAudioFocus(value: Boolean) {
        viewModelScope.launch {
            readAloudSettingsRepository.setIgnoreAudioFocus(value)
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
                OtherConfig.processText = enable
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    fun clearWebViewData() {
        if (clearWebViewDataJob?.isActive == true) return

        clearWebViewDataJob = viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    WebViewDataCleaner.clear(appCtx)
                }.onSuccess {
                    appCtx.toastOnUi(R.string.clear_webview_data_success)
                    delay(RESTART_DELAY_MILLIS)
                    appCtx.restart()
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
        OtherConfig.defaultBookTreeUri = path
    }

    var checkSourceTimeout by mutableStateOf((CheckSource.timeout / 1000))
    var checkSearch by mutableStateOf(CheckSource.checkSearch)
    var checkDiscovery by mutableStateOf(CheckSource.checkDiscovery)
    var checkInfo by mutableStateOf(CheckSource.checkInfo)
    var checkCategory by mutableStateOf(CheckSource.checkCategory)
    var checkContent by mutableStateOf(CheckSource.checkContent)

    fun saveCheckSourceConfig(): Boolean {
        val timeoutLong = checkSourceTimeout
        if (timeoutLong <= 0) return false // 验证失败

        CheckSource.timeout = timeoutLong * 1000
        CheckSource.checkSearch = checkSearch
        CheckSource.checkDiscovery = checkDiscovery
        CheckSource.checkInfo = checkInfo
        CheckSource.checkCategory = checkCategory
        CheckSource.checkContent = checkContent

        CheckSource.putConfig()
        appCtx.putPrefString(PreferKey.checkSource, CheckSource.summary)
        return true
    }

    var uploadUrl by mutableStateOf("")
    var downloadUrlRule by mutableStateOf("")
    var summary by mutableStateOf("")
    var compress by mutableStateOf(false)

    fun initDirectLinkRule() {
        val rule = DirectLinkUpload.getRule()
        upView(rule)
    }

    fun upView(rule: DirectLinkUpload.Rule) {
        uploadUrl = rule.uploadUrl
        downloadUrlRule = rule.downloadUrlRule
        summary = rule.summary
        compress = rule.compress
    }

    fun saveDirectLinkRule(): Boolean {
        if (uploadUrl.isBlank() || downloadUrlRule.isBlank() || summary.isBlank()) return false
        val rule = DirectLinkUpload.Rule(uploadUrl, downloadUrlRule, summary, compress)
        DirectLinkUpload.putConfig(rule)
        return true
    }

    fun testRule(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val rule = DirectLinkUpload.Rule(uploadUrl, downloadUrlRule, summary, compress)
            runCatching {
                DirectLinkUpload.upLoad("test.json", "{}", "application/json", rule)
            }.onSuccess {
                onResult(it)
            }.onFailure {
                onResult(it.localizedMessage ?: "ERROR")
            }
        }
    }


}
