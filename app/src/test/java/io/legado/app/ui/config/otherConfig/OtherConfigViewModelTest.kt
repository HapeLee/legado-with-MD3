package io.legado.app.ui.config.otherConfig

import android.app.Application
import android.os.Looper
import io.legado.app.R
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.DirectLinkRule
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.DownloadCacheSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class OtherConfigViewModelTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun languageChanged_updatesLocaleGatewayAndUiState() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val appLocaleGateway = FakeAppLocaleGateway()
        val viewModel = OtherConfigViewModel(
            appLocaleGateway = appLocaleGateway,
            readAloudSettingsGateway = FakeReadAloudSettingsGateway(),
            otherSettingsGateway = otherSettingsGateway,
            downloadCacheSettingsGateway = FakeDownloadCacheSettingsGateway(),
            checkSourceSettingsGateway = FakeCheckSourceSettingsGateway(),
            directLinkSettingsGateway = FakeDirectLinkSettingsGateway(),
            localPasswordGateway = FakeLocalPasswordGateway(),
            systemGateway = FakeOtherConfigSystemGateway(),
            initialState = OtherConfigUiState(),
        )

        viewModel.onIntent(OtherConfigIntent.LanguageChanged("en"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("en", appLocaleGateway.currentLanguage)
        assertEquals("en", viewModel.uiState.value.language)
    }

    @Test
    fun settingsFailures_areQueuedUntilUiAcknowledgesThem() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val viewModel = createViewModel(otherSettingsGateway = otherSettingsGateway)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        otherSettingsGateway.failure = IllegalStateException("boom")

        viewModel.onIntent(OtherConfigIntent.AutoRefreshChanged(true))
        viewModel.onIntent(OtherConfigIntent.DefaultToReadChanged(true))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val messages = viewModel.uiState.value.pendingMessages
        assertEquals(2, messages.size)
        val message = messages.first()
        assertEquals("boom", message.text)

        viewModel.onIntent(OtherConfigIntent.MessageShown(message.id))

        assertEquals(1, viewModel.uiState.value.pendingMessages.size)
    }

    @Test
    fun confirmCheckSource_writesThroughGateway() = runBlocking {
        val checkSourceGateway = FakeCheckSourceSettingsGateway()
        val viewModel = createViewModel(checkSourceSettingsGateway = checkSourceGateway)

        viewModel.onIntent(OtherConfigIntent.CheckSourceTimeoutChanged(42L))
        viewModel.onIntent(OtherConfigIntent.ConfirmCheckSource)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(42_000L, checkSourceGateway.lastUpdate?.timeoutMillis)
    }

    @Test
    fun clearWebViewData_emitsRestartRequest() = runBlocking {
        val viewModel = createViewModel()
        val effect = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.first { it == OtherConfigEffect.RestartApp }
        }

        viewModel.onIntent(OtherConfigIntent.ConfirmClearWebViewData)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(OtherConfigEffect.RestartApp, effect.await())
        assertEquals(
            R.string.clear_webview_data_success,
            viewModel.uiState.value.pendingMessages.firstOrNull()?.resId,
        )
    }

    @Test
    fun directRuleAndPassword_writeThroughGateways() = runBlocking {
        val directLinkGateway = FakeDirectLinkSettingsGateway()
        val localPasswordGateway = FakeLocalPasswordGateway()
        val viewModel = createViewModel(
            directLinkSettingsGateway = directLinkGateway,
            localPasswordGateway = localPasswordGateway,
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        viewModel.onIntent(OtherConfigIntent.ConfirmDirectLinkRule)
        viewModel.onIntent(OtherConfigIntent.SaveLocalPassword("secret"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Example", directLinkGateway.savedRule?.summary)
        assertEquals("secret", localPasswordGateway.savedPassword)
    }

    @Test
    fun processTextSettingFailure_rollsBackSystemComponent() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val systemGateway = FakeOtherConfigSystemGateway()
        val viewModel = createViewModel(
            otherSettingsGateway = otherSettingsGateway,
            systemGateway = systemGateway,
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        otherSettingsGateway.failure = IllegalStateException("boom")

        viewModel.onIntent(OtherConfigIntent.ProcessTextChanged(false))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(systemGateway.enabled)
    }

    private fun createViewModel(
        otherSettingsGateway: FakeOtherSettingsGateway = FakeOtherSettingsGateway(),
        checkSourceSettingsGateway: FakeCheckSourceSettingsGateway =
            FakeCheckSourceSettingsGateway(),
        directLinkSettingsGateway: FakeDirectLinkSettingsGateway =
            FakeDirectLinkSettingsGateway(),
        localPasswordGateway: FakeLocalPasswordGateway = FakeLocalPasswordGateway(),
        systemGateway: FakeOtherConfigSystemGateway = FakeOtherConfigSystemGateway(),
    ) = OtherConfigViewModel(
        appLocaleGateway = FakeAppLocaleGateway(),
        readAloudSettingsGateway = FakeReadAloudSettingsGateway(),
        otherSettingsGateway = otherSettingsGateway,
        downloadCacheSettingsGateway = FakeDownloadCacheSettingsGateway(),
        checkSourceSettingsGateway = checkSourceSettingsGateway,
        directLinkSettingsGateway = directLinkSettingsGateway,
        localPasswordGateway = localPasswordGateway,
        systemGateway = systemGateway,
        initialState = OtherConfigUiState(),
    )

    private class FakeAppLocaleGateway : AppLocaleGateway {
        private val state = MutableStateFlow("auto")
        override val currentLanguage: String
            get() = state.value
        override val language = state.asStateFlow()

        override fun setLanguage(language: String) {
            state.value = language
        }

        override fun synchronizeFromPlatform() = Unit

        override fun migrateLegacyLanguage(language: String) {
            setLanguage(language)
        }
    }

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings())
        var failure: Throwable? = null

        override val currentSettings: OtherSettings
            get() = state.value
        override val settings: Flow<OtherSettings> = state

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            failure?.let { throw it }
            state.value = transform(state.value)
        }
    }

    private class FakeReadAloudSettingsGateway : ReadAloudSettingsGateway {
        private val state = MutableStateFlow(ReadAloudSettings())

        override val currentSettings: ReadAloudSettings
            get() = state.value
        override val settings: Flow<ReadAloudSettings> = state

        override suspend fun update(
            transform: (ReadAloudSettings) -> ReadAloudSettings,
        ) {
            state.value = transform(state.value)
        }
    }

    private class FakeDownloadCacheSettingsGateway : DownloadCacheSettingsGateway {
        private val state = MutableStateFlow(DownloadCacheSettings())

        override val currentSettings: DownloadCacheSettings
            get() = state.value
        override val settings: Flow<DownloadCacheSettings> = state

        override suspend fun update(
            transform: (DownloadCacheSettings) -> DownloadCacheSettings,
        ) {
            state.value = transform(state.value)
        }
    }

    private class FakeCheckSourceSettingsGateway : CheckSourceSettingsGateway {
        private val state = MutableStateFlow(CheckSourceSettings())
        var lastUpdate: CheckSourceSettings? = null

        override val currentSettings: CheckSourceSettings
            get() = state.value
        override val settings: Flow<CheckSourceSettings> = state

        override suspend fun update(settings: CheckSourceSettings) {
            lastUpdate = settings
            state.value = settings
        }
    }

    private class FakeDirectLinkSettingsGateway : DirectLinkSettingsGateway {
        private val rule = DirectLinkRule(
            uploadUrl = "https://example.com",
            downloadUrlRule = "$.url",
            summary = "Example",
        )
        var savedRule: DirectLinkRule? = null

        override suspend fun loadRule(): DirectLinkRule = rule
        override suspend fun loadDefaultRules(): List<DirectLinkRule> = listOf(rule)
        override suspend fun saveRule(rule: DirectLinkRule) {
            savedRule = rule
        }
        override suspend fun testRule(rule: DirectLinkRule): String = "ok"
    }

    private class FakeLocalPasswordGateway : LocalPasswordGateway {
        var savedPassword: String? = null

        override suspend fun setPassword(password: String?) {
            savedPassword = password
        }
    }

    private class FakeOtherConfigSystemGateway : OtherConfigSystemGateway {
        var enabled = true

        override fun isProcessTextEnabled(): Boolean = enabled
        override suspend fun setProcessTextEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
        override suspend fun clearWebViewData() = Unit
    }
}
