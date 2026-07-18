package io.legado.app.ui.config.otherConfig

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsUpdate
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsUpdate
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadAloudSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    fun languageChanged_updatesGatewayAndEmitsApplyLanguage() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val viewModel = OtherConfigViewModel(
            readAloudSettingsGateway = FakeReadAloudSettingsGateway(),
            otherSettingsGateway = otherSettingsGateway,
            initialState = OtherConfigUiState(),
        )
        val effect = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.effects.first()
        }

        viewModel.onIntent(OtherConfigIntent.LanguageChanged("en"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(OtherConfigEffect.ApplyLanguage("en"), effect.await())
        assertTrue(
            otherSettingsGateway.updates.contains(OtherSettingsUpdate.Language("en"))
        )
        assertEquals("en", viewModel.uiState.value.language)
    }

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings())
        val updates = mutableListOf<OtherSettingsUpdate>()

        override val settings: Flow<OtherSettings> = state

        override suspend fun update(update: OtherSettingsUpdate) {
            updates += update
            if (update is OtherSettingsUpdate.Language) {
                state.value = state.value.copy(language = update.value)
            }
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
