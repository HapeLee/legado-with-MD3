package io.legado.app.ui.about

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
class AboutViewModelTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
    }

    @Test
    fun `更新渠道通过唯一UiState入口下发`() {
        val gateway = FakeOtherSettingsGateway("official_version")
        val viewModel = AboutViewModel(application, gateway)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        gateway.emit("beta")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("beta", viewModel.uiState.value.updateToVariant)
    }

    private class FakeOtherSettingsGateway(initialVariant: String) : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings(updateToVariant = initialVariant))

        override val currentSettings: OtherSettings get() = state.value
        override val settings: Flow<OtherSettings> = state

        fun emit(variant: String) {
            state.value = state.value.copy(updateToVariant = variant)
        }

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            state.value = transform(state.value)
        }
    }
}
