package io.legado.app.ui.config.otherConfig

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsUpdate
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsUpdate
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
            initialState = OtherConfigUiState(),
        )

        viewModel.onIntent(OtherConfigIntent.LanguageChanged("en"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("en", appLocaleGateway.currentLanguage)
        assertEquals("en", viewModel.uiState.value.language)
    }

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
        val updates = mutableListOf<OtherSettingsUpdate>()

        override val currentSettings: OtherSettings
            get() = state.value
        override val settings: Flow<OtherSettings> = state

        override suspend fun update(update: OtherSettingsUpdate) {
            updates += update
        }
    }

    private class FakeReadAloudSettingsGateway : ReadAloudSettingsGateway {
        private val state = MutableStateFlow(ReadAloudSettings())

        override val currentSettings: ReadAloudSettings
            get() = state.value
        override val settings: Flow<ReadAloudSettings> = state

        override suspend fun update(update: ReadAloudSettingsUpdate) = Unit
    }
}
