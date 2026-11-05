package io.legado.app.ui.config.ai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import io.legado.app.data.entities.*
import io.legado.app.domain.gateway.*
import io.legado.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AiImageProviderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var draft: AiModelDraft? = null
    private var textCalls = 0
    @Before fun setup() { Dispatchers.setMain(dispatcher); RuntimeEnvironment.getApplication().injectAsAppCtx() }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(): AiProviderEditViewModel {
        val profiles = Proxy.newProxyInstance(AiProfileGateway::class.java.classLoader, arrayOf(AiProfileGateway::class.java)) { _, method, args ->
            when (method.name) {
                "observeProviders" -> flowOf(emptyList<AiProviderProfile>())
                "observeModels" -> flowOf(emptyList<AiModelProfile>())
                "saveProvider" -> (args[0] as AiProviderDraft).let { AiProviderProfile("provider", it.providerName, it.protocol, it.baseUrl) }
                "saveModel" -> (args[0] as AiModelDraft).also { draft = it }.let { AiModelProfile("model", it.providerId, it.modelName, it.modelId) }
                else -> error(method.name)
            }
        } as AiProfileGateway
        val text = Proxy.newProxyInstance(AiTextGateway::class.java.classLoader, arrayOf(AiTextGateway::class.java)) { _, _, _ -> textCalls++; error("Unexpected service request") } as AiTextGateway
        return AiProviderEditViewModel(null, profiles, text).also { store.put("provider", it) }
    }
    @Test fun `image preset creates an editable image model and saves output flags`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.onIntent(AiProviderEditIntent.ApplyProviderPreset("gemini_images"))
        vm.onIntent(AiProviderEditIntent.AddModel)
        assertEquals(AiProtocol.GEMINI_IMAGES, vm.uiState.value.protocol)
        assertTrue(vm.uiState.value.editingModel!!.imageEditing)
        assertEquals("gemini-nano-banana-2.1", vm.uiState.value.editingModel!!.modelId)
        vm.onIntent(AiProviderEditIntent.UpdateEditingImageGeneration(false))
        vm.onIntent(AiProviderEditIntent.SaveEditingModel); runCurrent()
        assertTrue(AiCapability.IMAGE_EDITING in draft!!.capabilities!!)
        assertFalse(AiCapability.IMAGE_GENERATION in draft!!.capabilities!!)
        assertEquals(0, textCalls)
    }
    @Test fun `native image connection test does not invoke paid generation or a text endpoint`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.onIntent(AiProviderEditIntent.ApplyProviderPreset("xai_images"))
        vm.onIntent(AiProviderEditIntent.TestConnection)
        vm.onIntent(AiProviderEditIntent.SyncModels); runCurrent()
        assertEquals(0, textCalls)
        assertFalse(vm.uiState.value.isTesting)
        assertFalse(vm.uiState.value.isFetchingModels)
    }
}
