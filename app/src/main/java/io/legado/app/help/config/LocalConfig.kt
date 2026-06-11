package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.utils.getBoolean
import io.legado.app.utils.putBoolean
import io.legado.app.utils.putLong
import io.legado.app.utils.putString
import io.legado.app.utils.remove
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.get
import splitties.init.appCtx

@Suppress("ConstPropertyName")
object LocalConfig : SharedPreferences
by appCtx.getSharedPreferences("local", Context.MODE_PRIVATE) {

    private const val versionCodeKey = "appVersionCode"

    private val localPreferencesRepository: LocalPreferencesRepository
        by lazy { get(LocalPreferencesRepository::class.java) }

    /**
     * 本地密码,用来对需要备份的敏感信息加密,如 webdav 配置等
     */
    var password: String?
        get() = runBlocking {
            localPreferencesRepository.getPreference(LocalPreferencesKeys.PASSWORD, "").first()
                .ifEmpty { null }
        }
        set(value) {
            runBlocking {
                localPreferencesRepository.updatePreference(
                    LocalPreferencesKeys.PASSWORD, value ?: ""
                )
            }
        }

    var lastBackup: Long
        get() = runBlocking {
            localPreferencesRepository.getPreference(LocalPreferencesKeys.LAST_BACKUP, 0L).first()
        }
        set(value) {
            runBlocking {
                localPreferencesRepository.updatePreference(LocalPreferencesKeys.LAST_BACKUP, value)
            }
        }

    var privacyPolicyOk: Boolean
        get() = runBlocking {
            localPreferencesRepository.getPreference(LocalPreferencesKeys.PRIVACY_POLICY_OK, false)
                .first()
        }
        set(value) {
            runBlocking {
                localPreferencesRepository.updatePreference(
                    LocalPreferencesKeys.PRIVACY_POLICY_OK, value
                )
            }
        }

    var navExtended: Boolean
        get() = getBoolean("navExtended")
        set(value) {
            putBoolean("navExtended", value)
        }

    val readHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readHelpVersion", "firstRead")

    val backupHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "backupHelpVersion", "firstBackup")

    val readMenuHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readMenuHelpVersion", "firstReadMenu")

    val bookSourcesHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "bookSourceHelpVersion", "firstOpenBookSources")

    val webDavBookHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "webDavBookHelpVersion", "firstOpenWebDavBook")

    val ruleHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "ruleHelpVersion")

    val needUpHttpTTS: Boolean
        get() = !isLastVersion(6, "httpTtsVersion")

    val needUpTxtTocRule: Boolean
        get() = !isLastVersion(3, "txtTocRuleVersion")

    val needUpRssSources: Boolean
        get() = !isLastVersion(6, "rssSourceVersion")

    val needUpDictRule: Boolean
        get() = !isLastVersion(2, "needUpDictRule")

    var versionCode
        get() = getLong(versionCodeKey, 0)
        set(value) {
            edit { putLong(versionCodeKey, value) }
        }

    val isFirstOpenApp: Boolean
        get() {
            val value = getBoolean("firstOpen", true)
            if (value) {
                edit { putBoolean("firstOpen", false) }
            }
            return value
        }

    @Suppress("SameParameterValue")
    private fun isLastVersion(
        lastVersion: Int,
        versionKey: String,
        firstOpenKey: String? = null
    ): Boolean {
        var version = getInt(versionKey, 0)
        if (version == 0 && firstOpenKey != null) {
            if (!getBoolean(firstOpenKey, true)) {
                version = 1
            }
        }
        if (version < lastVersion) {
            edit { putInt(versionKey, lastVersion) }
            return false
        }
        return true
    }

    var bookInfoDeleteAlert: Boolean
        get() = getBoolean("bookInfoDeleteAlert", true)
        set(value) {
            putBoolean("bookInfoDeleteAlert", value)
        }

    var deleteBookOriginal: Boolean
        get() = getBoolean("deleteBookOriginal")
        set(value) {
            putBoolean("deleteBookOriginal", value)
        }

    var appCrash: Boolean
        get() = getBoolean("appCrash")
        set(value) {
            putBoolean("appCrash", value)
        }

}
