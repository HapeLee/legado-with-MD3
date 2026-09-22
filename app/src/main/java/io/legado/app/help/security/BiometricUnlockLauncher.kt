package io.legado.app.help.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.help.config.AppConfigStore

/**
 * 系统生物验证弹框的宿主封装。
 *
 * 只做两件事：探测设备能力，以及在拿到 CryptoObject 的前提下发起验证并把解出的密码交回去。
 * 校验与解锁仍然由 [PrivateAccessGateway] 用密码完成，生物路径不会绕过密码。
 *
 * 之所以只接受 BIOMETRIC_STRONG：官方明确「允许锁屏凭据回退时不能传 CryptoObject」，
 * 而锁屏凭据回退在本设计里由应用内密码承担，不需要 DEVICE_CREDENTIAL。
 *
 * 这里直接读 [AppConfigStore] 而不走 PrivateAccessGateway：信封是平台侧凭据
 * （Keystore 加密的产物），domain 契约不该把它暴露出去——gateway 只接收
 * "认证后产出的信封"这个结果。
 */
object BiometricUnlockLauncher {

    fun detectStatus(context: Context): PrivateBiometricStatus {
        if (!PrivatePasswordCipher.isBiometricCryptoSupported()) {
            return PrivateBiometricStatus.UnsupportedSystem
        }
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> PrivateBiometricStatus.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> PrivateBiometricStatus.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> PrivateBiometricStatus.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> PrivateBiometricStatus.Unavailable
            else -> PrivateBiometricStatus.Unavailable
        }
    }

    /**
     * 密钥作废后的凭据清理。
     *
     * 信封是用旧密钥封的，密钥一旦被作废，它就永远解不开了——留着只会让开关一直
     * 显示"已开启"，而每次解锁都静默回退到手输密码。这里直接落盘而不是走 gateway：
     * 调用点都在系统验证回调里，等不了挂起函数；写的是同一份 AppConfigStore，
     * 开关对应的 flow 会立刻跟上。
     */
    private fun clearInvalidatedEnvelope() {
        AppConfigStore.putAll(
            mapOf(
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENABLED.name to false,
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name to "",
                LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name to "",
            )
        )
    }

    /**
     * 发起验证。
     *
     * @param onPassword 生物验证成功且信封解出密码（正常路径）
     * @param onFallback 需要改用应用内密码：设备不可用、密钥失效或用户点了"使用密码"。
     *                   密钥被指纹变更作废时，本组件会先清掉已失效的信封与开关再回调
     * @param onCancel   用户主动取消验证（返回键、点弹框外部、系统取消）；默认与 [onFallback]
     *                   一致，只有"启动验证"这类必须闭环的场景才需要区分并另行处理
     * @param onError    真正的错误（次数锁定等），调用方应提示用户
     */
    fun launch(
        activity: FragmentActivity,
        title: CharSequence,
        subtitle: CharSequence?,
        negativeButtonText: CharSequence,
        onPassword: (String) -> Unit,
        onFallback: () -> Unit,
        onCancel: () -> Unit = onFallback,
        onError: (String) -> Unit,
    ) {
        val ciphertext =
            AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_BIOMETRIC_ENVELOPE.name)
        val iv = AppConfigStore.getString(LocalPreferencesKeys.PRIVATE_BIOMETRIC_IV.name)
        if (ciphertext.isNullOrBlank() || iv.isNullOrBlank()) {
            onFallback()
            return
        }
        val cipher = when (val result = PrivatePasswordCipher.createDecryptCipher(iv)) {
            is PrivatePasswordCipher.DecryptResult.Ready -> result.cipher

            PrivatePasswordCipher.DecryptResult.KeyInvalidated -> {
                // 密钥被"新增/变更指纹"永久作废：信封已经解不开，重建密钥也救不回来。
                // 不清掉的话开关会一直显示"已开启"，而每次解锁都在这里静默回退。
                clearInvalidatedEnvelope()
                onFallback()
                return
            }

            PrivatePasswordCipher.DecryptResult.Unavailable -> {
                onFallback()
                return
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cryptoCipher = result.cryptoObject?.cipher
                    val password = cryptoCipher?.let {
                        PrivatePasswordCipher.decryptPassword(it, ciphertext)
                    }
                    if (password.isNullOrEmpty()) onFallback() else onPassword(password)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        // 点了"使用密码"：这是换一条凭据，不是放弃验证
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallback()

                        // 用户按返回/取消：这是放弃与否的明确表态
                        BiometricPrompt.ERROR_USER_CANCELED -> onCancel()

                        // 系统取消（旋转、切后台、熄屏…）：不是用户放弃。
                        // 当成拒绝会让"转个屏就退出应用"这种荒唐行为出现在启动门槛上
                        BiometricPrompt.ERROR_CANCELED -> onFallback()

                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // 单次识别失败，系统弹框自己会提示重试，无需额外处理
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
            .onFailure { onFallback() }
    }
}
