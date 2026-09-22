package io.legado.app.ui.config.privateConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.PrivateAccessSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class PrivateConfigViewModel(
    private val privateAccessGateway: PrivateAccessGateway,
    private val localPasswordGateway: LocalPasswordGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<PrivateConfigEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            privateAccessGateway.state.collect { access ->
                _uiState.update {
                    it.copy(
                        hasLocalPassword = access.hasPassword,
                        biometricEnabled = access.biometricEnabled,
                        biometricStatus = access.biometricStatus,
                    )
                }
            }
        }
        viewModelScope.launch {
            privateAccessGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            otherSettingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(firebaseEnable = settings.firebaseEnable) }
            }
        }
        privateAccessGateway.refreshBiometricStatus()
    }

    fun onIntent(intent: PrivateConfigIntent) {
        when (intent) {
            PrivateConfigIntent.ShowPasswordDialog ->
                _uiState.update { it.copy(showPasswordDialog = true) }

            PrivateConfigIntent.DismissPasswordDialog ->
                _uiState.update { it.copy(showPasswordDialog = false) }

            is PrivateConfigIntent.SavePassword ->
                savePassword(intent.password, intent.currentPassword)

            is PrivateConfigIntent.SetBiometricShortcutEnabled ->
                setBiometricShortcutEnabled(intent.enabled)

            is PrivateConfigIntent.StoreBiometricEnvelope ->
                storeBiometricEnvelope(intent.ciphertext, intent.iv)

            is PrivateConfigIntent.SetPrivateEnabled ->
                updateSettings { it.copy(enabled = intent.enabled) }

            is PrivateConfigIntent.SetVerifyOnEnterGroup ->
                updateSettings { it.copy(verifyOnEnterGroup = intent.enabled) }

            is PrivateConfigIntent.SetVerifyOnOpenBook ->
                updateSettings { it.copy(verifyOnOpenBook = intent.enabled) }

            is PrivateConfigIntent.SetVerifyOnAppStart ->
                updateSettings { it.copy(verifyOnAppStart = intent.enabled) }

            is PrivateConfigIntent.SetUnlockScope ->
                updateSettings { it.copy(unlockScope = intent.scope) }

            is PrivateConfigIntent.SetBackgroundGraceSeconds ->
                updateSettings { it.copy(backgroundGraceSeconds = intent.seconds) }

            PrivateConfigIntent.RequestNotificationPermission ->
                _effects.tryEmit(PrivateConfigEffect.RequestNotificationPermission)

            PrivateConfigIntent.RequestBatteryPermission ->
                _effects.tryEmit(PrivateConfigEffect.RequestBatteryPermission)

            is PrivateConfigIntent.SetFirebaseEnabled -> updateOtherSettings {
                it.copy(firebaseEnable = intent.enabled)
            }
        }
    }

    private fun savePassword(password: String, currentPassword: String) {
        viewModelScope.launch {
            if (_uiState.value.hasLocalPassword) {
                // 已有密码时，改密码 / 清密码都必须先过一次现有凭据。
                // 走 gateway 的同一个校验入口，不另开旁路，免得两条校验逻辑漂移。
                val verified = runCatching {
                    privateAccessGateway.verifyPassword(currentPassword)
                }.getOrDefault(false)
                if (!verified) {
                    showMessageRes(R.string.private_unlock_password_error)
                    return@launch
                }
            }
            runCatching { localPasswordGateway.setPassword(password) }
                .onSuccess {
                    _uiState.update { it.copy(showPasswordDialog = false) }
                    privateAccessGateway.refreshBiometricStatus()
                }
                .onFailure { showMessageRes(R.string.save_failed) }
        }
    }

    /**
     * 生物快捷解锁只是免手输密码的入口，而且开启必须先过一次真实认证：
     * auth-per-use 密钥连加密都要认证，信封只能在 BiometricPrompt 的回调里产出。
     * 所以这里只发一个"去认证"的 Effect，开关等认证结果回来才动，不做"看起来开了"的假开关。
     */
    private fun setBiometricShortcutEnabled(enabled: Boolean) {
        if (enabled) {
            if (!_uiState.value.biometricSupported) {
                showMessageRes(biometricUnavailableMessage(_uiState.value.biometricStatus))
                return
            }
            _effects.tryEmit(PrivateConfigEffect.RequestBiometricEnvelope)
            return
        }
        // 关闭是纯本地操作：清掉信封即可
        viewModelScope.launch {
            runCatching { privateAccessGateway.disableBiometricShortcut() }
                .onFailure { showMessageRes(R.string.save_failed) }
        }
    }

    /** 认证产出的信封交给 gateway 落盘；写失败就保持关闭并如实提示 */
    private fun storeBiometricEnvelope(ciphertext: String, iv: String) {
        viewModelScope.launch {
            val stored = runCatching {
                privateAccessGateway.storeBiometricEnvelope(ciphertext, iv)
            }.getOrDefault(false)
            if (stored) {
                privateAccessGateway.refreshBiometricStatus()
            } else {
                showMessageRes(R.string.private_biometric_unavailable)
            }
        }
    }

    private fun updateSettings(transform: (PrivateAccessSettings) -> PrivateAccessSettings) {
        viewModelScope.launch {
            runCatching { privateAccessGateway.updateSettings(transform) }
                .onFailure { showMessageRes(R.string.save_failed) }
        }
    }

    private fun updateOtherSettings(transform: (OtherSettings) -> OtherSettings) {
        viewModelScope.launch {
            runCatching { otherSettingsGateway.update(transform) }
                .onFailure { showMessageRes(R.string.save_failed) }
        }
    }

    private fun biometricUnavailableMessage(status: PrivateBiometricStatus): Int = when (status) {
        PrivateBiometricStatus.NotEnrolled -> R.string.private_biometric_not_enrolled
        PrivateBiometricStatus.NoHardware -> R.string.private_biometric_no_hardware
        PrivateBiometricStatus.UnsupportedSystem -> R.string.private_biometric_unsupported_system
        else -> R.string.private_biometric_unavailable
    }

    private fun showMessageRes(resId: Int) {
        _effects.tryEmit(PrivateConfigEffect.ShowMessage(appCtx.getString(resId)))
    }
}
