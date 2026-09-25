package io.legado.app.ui.config.privateConfig

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateBiometricStatus
import io.legado.app.domain.model.PrivateUnlockScope
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.PrivateAccessSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PrivateConfigViewModelTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun savePassword_writesThroughLocalPasswordGateway() = runBlocking {
        val passwordGateway = FakeLocalPasswordGateway()
        val viewModel = createViewModel(localPasswordGateway = passwordGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.SavePassword("secret"))
        idleMainLooper()

        assertEquals("secret", passwordGateway.savedPassword)
        assertFalse(viewModel.uiState.value.showPasswordDialog)
    }

    @Test
    fun verifyTimingToggles_persistThroughPrivateAccessGateway() = runBlocking {
        val accessGateway = FakePrivateAccessGateway()
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.SetVerifyOnEnterGroup(false))
        idleMainLooper()

        assertFalse(accessGateway.settingsValue.verifyOnEnterGroup)
        assertFalse(viewModel.uiState.value.settings.verifyOnEnterGroup)
    }

    @Test
    fun unlockScope_persistsEveryTime() = runBlocking {
        val accessGateway = FakePrivateAccessGateway()
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.SetUnlockScope(PrivateUnlockScope.EveryTime))
        idleMainLooper()

        assertEquals(PrivateUnlockScope.EveryTime, accessGateway.settingsValue.unlockScope)
        assertEquals(PrivateUnlockScope.EveryTime, viewModel.uiState.value.settings.unlockScope)
    }

    @Test
    fun biometricShortcut_reportsUnavailableDeviceInsteadOfPretendingSuccess() = runBlocking {
        val accessGateway = FakePrivateAccessGateway()
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }
        viewModel.onIntent(PrivateConfigIntent.SetBiometricShortcutEnabled(true))
        idleMainLooper()

        assertFalse(accessGateway.biometricEnabled)
        assertFalse(viewModel.uiState.value.biometricEnabled)
        assertTrue(effect.await() is PrivateConfigEffect.ShowMessage)
    }

    @Test
    fun enablingBiometricShortcut_asksForAuthenticationAndDoesNotFlipSwitchEarly() = runBlocking {
        val accessGateway = FakePrivateAccessGateway(PrivateBiometricStatus.Available)
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }
        viewModel.onIntent(PrivateConfigIntent.SetBiometricShortcutEnabled(true))
        idleMainLooper()

        // 信封要等一次真实认证才产出，所以这里只能请求认证，开关绝不能先亮起来
        assertEquals(PrivateConfigEffect.RequestBiometricEnvelope, effect.await())
        assertFalse(accessGateway.envelopeStored)
        assertFalse(accessGateway.biometricEnabled)
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun storedEnvelope_flipsSwitchOnlyAfterGatewayAccepts() = runBlocking {
        val accessGateway = FakePrivateAccessGateway(PrivateBiometricStatus.Available)
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.StoreBiometricEnvelope("ciphertext", "iv"))
        idleMainLooper()

        assertTrue(accessGateway.envelopeStored)
        assertTrue(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun emptyEnvelope_neverFlipsSwitch() = runBlocking {
        val accessGateway = FakePrivateAccessGateway(PrivateBiometricStatus.Available)
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.StoreBiometricEnvelope("", ""))
        idleMainLooper()

        assertFalse(accessGateway.envelopeStored)
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun disablingBiometricShortcut_clearsStoredEnvelope() = runBlocking {
        val accessGateway = FakePrivateAccessGateway(PrivateBiometricStatus.Available)
        val viewModel = createViewModel(privateAccessGateway = accessGateway)
        idleMainLooper()

        viewModel.onIntent(PrivateConfigIntent.StoreBiometricEnvelope("ciphertext", "iv"))
        idleMainLooper()
        assertTrue(viewModel.uiState.value.biometricEnabled)

        viewModel.onIntent(PrivateConfigIntent.SetBiometricShortcutEnabled(false))
        idleMainLooper()

        assertTrue(accessGateway.disabled)
        assertFalse(accessGateway.envelopeStored)
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createViewModel(
        privateAccessGateway: FakePrivateAccessGateway = FakePrivateAccessGateway(),
        localPasswordGateway: FakeLocalPasswordGateway = FakeLocalPasswordGateway(),
        otherSettingsGateway: FakeOtherSettingsGateway = FakeOtherSettingsGateway(),
    ) = PrivateConfigViewModel(
        privateAccessGateway = privateAccessGateway,
        localPasswordGateway = localPasswordGateway,
        otherSettingsGateway = otherSettingsGateway,
    )

    private class FakePrivateAccessGateway(
        biometricStatus: PrivateBiometricStatus = PrivateBiometricStatus.NoHardware,
    ) : PrivateAccessGateway {
        private val accessFlow = MutableStateFlow(
            PrivateAccessState(
                hasPassword = true,
                biometricEnabled = false,
                biometricStatus = biometricStatus,
            )
        )
        private val settingsFlow = MutableStateFlow(PrivateAccessSettings())

        var biometricEnabled = false
            private set

        /** 信封是否真的落过盘 */
        var envelopeStored = false
            private set

        var disabled = false
            private set

        val settingsValue: PrivateAccessSettings get() = settingsFlow.value

        override val state: Flow<PrivateAccessState> get() = accessFlow
        override val settings: Flow<PrivateAccessSettings> get() = settingsFlow

        override suspend fun updateSettings(transform: (PrivateAccessSettings) -> PrivateAccessSettings) {
            settingsFlow.value = transform(settingsFlow.value)
        }

        override fun isGrantedNow(bookUrl: String, group: Long): Boolean =
            accessFlow.value.isTargetGranted(bookUrl, group)

        override suspend fun verifyPasswordOnly(password: String): Boolean =
            password.isNotEmpty()

        override fun onAppBackground() = Unit

        override suspend fun verifyPassword(
            password: String,
            target: PrivateUnlockTarget?,
        ) = true

        override fun revoke(target: PrivateUnlockTarget) = Unit

        override suspend fun disableBiometricShortcut() {
            disabled = true
            biometricEnabled = false
            envelopeStored = false
            accessFlow.value = accessFlow.value.copy(biometricEnabled = false)
        }

        /** 与真实实现一致：空信封或能力不可用时拒绝写入，不假装成功 */
        override suspend fun storeBiometricEnvelope(ciphertext: String, iv: String): Boolean {
            if (ciphertext.isBlank() || iv.isBlank()) return false
            if (accessFlow.value.biometricStatus != PrivateBiometricStatus.Available) return false
            envelopeStored = true
            biometricEnabled = true
            accessFlow.value = accessFlow.value.copy(biometricEnabled = true)
            return true
        }

        override fun refreshBiometricStatus() = Unit
        override fun lock() = Unit
    }

    private class FakeLocalPasswordGateway : LocalPasswordGateway {
        var savedPassword: String? = null

        override suspend fun setPassword(password: String?) {
            savedPassword = password
        }
    }

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        private val settingsFlow = MutableStateFlow(OtherSettings())
        override val currentSettings: OtherSettings get() = settingsFlow.value
        override val settings: Flow<OtherSettings> get() = settingsFlow

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            settingsFlow.value = transform(settingsFlow.value)
        }
    }
}
