package io.legado.app.ui.main.my

import android.app.Application
import androidx.lifecycle.ViewModelStore
import io.legado.app.constant.EventBus
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MyWebServiceStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        FlowEventBus.with<String>(EventBus.WEB_SERVICE).resetReplayCache()
        WebService.isRun = false
        WebService.hostAddress = ""
        Dispatchers.resetMain()
    }

    private class FakeAppShellSettingsGateway(
        initial: AppShellSettings = AppShellSettings(),
    ) : AppShellSettingsGateway {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppShellSettings> = state.asStateFlow()
        override val currentSettings: AppShellSettings
            get() = state.value

        override suspend fun update(transform: (AppShellSettings) -> AppShellSettings) {
            state.update(transform)
        }
    }

    @Test
    fun stoppedServiceDoesNotBecomeRunningFromAReplayedAddress() = runTest(dispatcher) {
        WebService.isRun = false
        WebService.hostAddress = "http://127.0.0.1:1122"
        FlowEventBus.with<String>(EventBus.WEB_SERVICE).tryEmit(WebService.hostAddress)
        val model = MyViewModel(RuntimeEnvironment.getApplication(), FakeAppShellSettingsGateway())
        viewModels.put("my", model)
        runCurrent()
        assertFalse(model.uiState.value.isWebServiceRun)
        assertEquals("", model.uiState.value.webServiceAddress)
    }

    @Test
    fun runningServiceKeepsCurrentAddressWhenOldEventIsReplayed() = runTest(dispatcher) {
        WebService.isRun = true
        WebService.hostAddress = "http://127.0.0.1:1133"
        FlowEventBus.with<String>(EventBus.WEB_SERVICE).tryEmit("http://127.0.0.1:1122")
        val model = MyViewModel(RuntimeEnvironment.getApplication(), FakeAppShellSettingsGateway())
        viewModels.put("my", model)
        runCurrent()
        assertTrue(model.uiState.value.isWebServiceRun)
        assertEquals(WebService.hostAddress, model.uiState.value.webServiceAddress)
    }

    @Test
    fun togglingEInkGlobalModeUpdatesGatewayAndUiState() = runTest(dispatcher) {
        val gateway = FakeAppShellSettingsGateway()
        val model = MyViewModel(RuntimeEnvironment.getApplication(), gateway)
        viewModels.put("my", model)
        runCurrent()
        assertFalse(model.uiState.value.eInkGlobalMode)

        model.onIntent(MyIntent.SetEInkGlobalMode(true))
        runCurrent()
        assertTrue(gateway.currentSettings.eInkGlobalMode)
        assertTrue(model.uiState.value.eInkGlobalMode)

        model.onIntent(MyIntent.SetEInkGlobalMode(false))
        runCurrent()
        assertFalse(gateway.currentSettings.eInkGlobalMode)
        assertFalse(model.uiState.value.eInkGlobalMode)
    }
}
