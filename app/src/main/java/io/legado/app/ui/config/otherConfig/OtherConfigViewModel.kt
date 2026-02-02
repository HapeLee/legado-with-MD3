package io.legado.app.ui.config.otherConfig

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.CheckSource
import io.legado.app.model.ImageProvider
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.utils.FileUtils
import io.legado.app.utils.putPrefString
import io.legado.app.utils.restart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.init.appCtx

class OtherConfigViewModel : ViewModel() {

    private val packageManager = appCtx.packageManager
    private val componentName = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name
    )

    fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun setProcessTextEnable(enable: Boolean) {
        val state = if (enable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            componentName,
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    fun clearCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            BookHelp.clearCache()
            FileUtils.delete(context.cacheDir.absolutePath)
            context.externalCacheDir?.deleteRecursively()
            context.cacheDir.deleteRecursively()
        }
    }

    fun shrinkDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.openHelper.writableDatabase.execSQL("VACUUM")
        }
    }

    fun clearWebViewData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            FileUtils.delete(context.getDir("webview", Context.MODE_PRIVATE))
            FileUtils.delete(context.getDir("hws_webview", Context.MODE_PRIVATE), true)
            delay(3000)
            appCtx.restart()
        }
    }

    fun setLocalPassword(password: String) {
        LocalConfig.password = password
    }

    fun saveUserAgent(input: String) {
        OtherConfig.userAgent = input
        AppConfig.userAgent = OtherConfig.userAgent
    }

    fun updateBitmapCacheSize(size: Int) {
        AppConfig.bitmapCacheSize = size
        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
    }

    fun updateLocalBookDir(path: String) {
        OtherConfig.defaultBookTreeUri = path
        AppConfig.defaultBookTreeUri = OtherConfig.defaultBookTreeUri
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