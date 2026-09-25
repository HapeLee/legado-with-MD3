package io.legado.app.help.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.help.config.AppConfigStore

/**
 * 「开启生物快捷」的写入侧：用 auth-per-use 密钥加密本地密码，产出信封。
 *
 * 加密同样需要一次认证——官方 CryptoObject 流程里，ENCRYPT_MODE 的 cipher 也要交给
 * authenticate()，认证通过后 doFinal 才成立。所以开启开关是一次真实的生物验证动作，
 * 而不是"写个空信封把开关点亮"。
 *
 * 职责边界：本组件只做「认证 + 产出信封」，不落盘、不改开关。
 * 落盘与开关状态由 [io.legado.app.domain.gateway.PrivateAccessGateway] 决定，
 * 这样"信封是否真的写成功"才有唯一的事实来源。
 */
object BiometricEnvelopeWriter {

    /**
     * @param onSealed 认证通过且信封封装成功
     * @param onError  能力不可用、没有本地密码、密钥不可用或封装失败——一律不要改开关
     * @param onCancel 用户主动放弃（返回键 / 点弹框外部 / 取消按钮）
     */
    fun launch(
        activity: FragmentActivity,
        title: CharSequence,
        subtitle: CharSequence?,
        negativeButtonText: CharSequence,
        onSealed: (ciphertext: String, iv: String) -> Unit,
        onError: () -> Unit,
        onCancel: () -> Unit,
    ) {
        if (!PrivatePasswordCipher.isBiometricCryptoSupported()) {
            onError()
            return
        }
        val password = AppConfigStore.getString(LocalPreferencesKeys.PASSWORD.name).orEmpty()
        if (password.isEmpty()) {
            // 没有本地密码就无从封装：生物快捷本来就只是"免手输密码"
            onError()
            return
        }
        val cipher = PrivatePasswordCipher.createEncryptCipher()
        if (cipher == null) {
            onError()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    val envelope = authenticated?.let {
                        PrivatePasswordCipher.sealPassword(it, password)
                    }
                    if (envelope == null) onError() else onSealed(envelope.ciphertext, envelope.iv)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onCancel()

                        else -> onError()
                    }
                }

                override fun onAuthenticationFailed() {
                    // 单次识别失败：系统弹框自己会提示重试，无需额外处理
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)
            .build()

        runCatching { prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure { onError() }
    }
}
