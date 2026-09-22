package io.legado.app.ui.config.privateConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.domain.model.PrivateUnlockScope
import io.legado.app.domain.model.settings.PrivateAccessSettings

@Stable
data class PrivateConfigUiState(
    val hasLocalPassword: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricStatus: PrivateBiometricStatus = PrivateBiometricStatus.Unavailable,
    val settings: PrivateAccessSettings = PrivateAccessSettings(),
    val firebaseEnable: Boolean = true,
    val showPasswordDialog: Boolean = false,
) {
    /** 设备具备加密绑定的生物识别能力时才允许开启快捷解锁 */
    val biometricSupported: Boolean
        get() = biometricStatus == PrivateBiometricStatus.Available
}

sealed interface PrivateConfigIntent {
    data object ShowPasswordDialog : PrivateConfigIntent
    data object DismissPasswordDialog : PrivateConfigIntent

    /**
     * [currentPassword] 只在"已经有本地密码"时参与校验。
     *
     * 改密码必须先证明知道旧密码：本地密码是私密内容的唯一凭据，拿到一台已解锁手机的人
     * 若能直接把它改成自己的，私密保险箱就等于没锁。
     */
    data class SavePassword(
        val password: String,
        val currentPassword: String = "",
    ) : PrivateConfigIntent

    data class SetBiometricShortcutEnabled(val enabled: Boolean) : PrivateConfigIntent

    /** 生物认证通过、信封封装完成后回传；由 gateway 决定落盘与开关状态 */
    data class StoreBiometricEnvelope(val ciphertext: String, val iv: String) : PrivateConfigIntent

    /** 私密功能总开关：关掉即整体休眠（不验证、不脱敏），密码与标记都留着 */
    data class SetPrivateEnabled(val enabled: Boolean) : PrivateConfigIntent

    data class SetVerifyOnEnterGroup(val enabled: Boolean) : PrivateConfigIntent
    data class SetVerifyOnOpenBook(val enabled: Boolean) : PrivateConfigIntent
    data class SetVerifyOnAppStart(val enabled: Boolean) : PrivateConfigIntent
    data class SetUnlockScope(val scope: PrivateUnlockScope) : PrivateConfigIntent

    /** "离开应用前有效"频率下的宽限期（秒），0 = 离开即失效 */
    data class SetBackgroundGraceSeconds(val seconds: Int) : PrivateConfigIntent

    data object RequestNotificationPermission : PrivateConfigIntent
    data object RequestBatteryPermission : PrivateConfigIntent
    data class SetFirebaseEnabled(val enabled: Boolean) : PrivateConfigIntent
}

sealed interface PrivateConfigEffect {
    data class ShowMessage(val message: String) : PrivateConfigEffect
    data object RequestNotificationPermission : PrivateConfigEffect
    data object RequestBatteryPermission : PrivateConfigEffect

    /**
     * 开启生物快捷需要先过一次真实认证（auth-per-use 密钥加密也要认证），
     * 由宿主弹 BiometricPrompt 并回传信封。
     */
    data object RequestBiometricEnvelope : PrivateConfigEffect
}
