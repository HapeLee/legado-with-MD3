package io.legado.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.security.PrivatePasswordCipher
import io.legado.app.help.webView.WebViewDataCleaner
import io.legado.app.receiver.SharedReceiverActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectLinkSettingsRepository : DirectLinkSettingsGateway {

    override suspend fun loadRule(): DirectLinkRule = withContext(Dispatchers.IO) {
        DirectLinkUpload.getRule().toDomain()
    }

    override suspend fun loadDefaultRules(): List<DirectLinkRule> =
        withContext(Dispatchers.IO) { DirectLinkUpload.defaultRules.map { it.toDomain() } }

    override suspend fun saveRule(rule: DirectLinkRule) = withContext(Dispatchers.IO) {
        DirectLinkUpload.putConfig(rule.toLegacy())
    }

    override suspend fun testRule(rule: DirectLinkRule): String =
        DirectLinkUpload.upLoad(
            fileName = "test.json",
            file = "{}",
            contentType = "application/json",
            rule = rule.toLegacy(),
        )

    private fun DirectLinkUpload.Rule.toDomain() = DirectLinkRule(
        uploadUrl = uploadUrl,
        downloadUrlRule = downloadUrlRule,
        summary = summary,
        compress = compress,
    )

    private fun DirectLinkRule.toLegacy() = DirectLinkUpload.Rule(
        uploadUrl = uploadUrl,
        downloadUrlRule = downloadUrlRule,
        summary = summary,
        compress = compress,
    )
}

/**
 * 本地密码是备份加密与私密内容的共同凭据。
 *
 * salt / 校验值 / 生物信封必须与密码在同一次落盘里更新，否则会出现"密码改了、但生物快捷
 * 还在解旧信封"的半更新状态。信封本身无法在这里重建——它必须由一次真实的生物认证产出，
 * 所以密码一旦变化就如实关掉生物快捷，交给用户重新开启。
 */
class LocalPasswordRepository : LocalPasswordGateway {

    override suspend fun setPassword(password: String?) {
        val normalized = password.orEmpty()
        val previous = AppConfigStore.getString(LocalPreferencesKeys.PASSWORD.name).orEmpty()
        val values = mutableMapOf<String, Any?>(
            LocalPreferencesKeys.PASSWORD.name to normalized
        )
        when {
            normalized.isEmpty() -> {
                // 清掉密码就把派生凭据一起清掉，不留半套状态
                values[LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name] = ""
                values[LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name] = ""
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name] = ""
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name] = ""
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name] = false
            }

            previous == normalized -> {
                // 密码没变：刷新校验值即可，信封里装的还是同一个密码，仍然有效
                putVerifier(values, normalized)
            }

            else -> {
                putVerifier(values, normalized)
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name] = ""
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name] = ""
                values[LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name] = false
            }
        }
        AppConfigStore.putAllAndAwait(values)
    }

    private fun putVerifier(values: MutableMap<String, Any?>, password: String) {
        val salt = PrivatePasswordCipher.generateSalt()
        values[LocalPreferencesKeys.PRIVATE_PASSWORD_SALT.name] = salt
        values[LocalPreferencesKeys.PRIVATE_PASSWORD_VERIFIER.name] =
            PrivatePasswordCipher.deriveVerifier(password, salt).orEmpty()
    }
}

class OtherConfigSystemRepository(
    private val context: Context,
) : OtherConfigSystemGateway {

    private val componentName = ComponentName(
        context,
        SharedReceiverActivity::class.java.name,
    )

    override fun isProcessTextEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(componentName) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    override suspend fun setProcessTextEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.packageManager.setComponentEnabledSetting(
            componentName,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    override suspend fun clearWebViewData() {
        WebViewDataCleaner.clear(context)
    }
}
