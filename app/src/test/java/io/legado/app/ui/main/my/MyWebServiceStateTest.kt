package io.legado.app.ui.main.my

import android.app.Application
import androidx.lifecycle.ViewModelStore
import io.legado.app.constant.EventBus
import io.legado.app.service.WebService
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Test
    fun stoppedServiceDoesNotBecomeRunningFromAReplayedAddress() = runTest(dispatcher) {
        WebService.isRun = false
        WebService.hostAddress = "http://127.0.0.1:1122"
        FlowEventBus.with<String>(EventBus.WEB_SERVICE).tryEmit(WebService.hostAddress)
        val model = MyViewModel(RuntimeEnvironment.getApplication())
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
        val model = MyViewModel(RuntimeEnvironment.getApplication())
        viewModels.put("my", model)
        runCurrent()
        assertTrue(model.uiState.value.isWebServiceRun)
        assertEquals(WebService.hostAddress, model.uiState.value.webServiceAddress)
    }
}
