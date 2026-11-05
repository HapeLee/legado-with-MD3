package io.legado.app.data.repository.ai

import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.entities.*
import io.legado.app.data.repository.AiProfileRepository
import io.legado.app.domain.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class AiImageProfileTest {
    private val providers = mutableMapOf("images" to AiProviderProfile("images", "Images", AiProtocol.OPENAI_IMAGES, "https://example.invalid/v1"))
    private val models = mutableMapOf<String, AiModelProfile>()
    private val dao = Proxy.newProxyInstance(AiProfileDao::class.java.classLoader, arrayOf(AiProfileDao::class.java)) { _, method, args ->
        when (method.name) {
            "getProvider" -> providers[args[0]]
            "getModel" -> models[args[0]]
            "insertModel" -> { val model = args[0] as AiModelProfile; models[model.id] = model; Unit }
            "observeModels" -> flowOf(models.values.toList())
            "observeProviders" -> flowOf(providers.values.toList())
            else -> error("Unexpected DAO operation: ${method.name}")
        }
    } as AiProfileDao
    private val repository = AiProfileRepository(dao)

    @Test fun `custom image model flags survive saving and configuration lookup`() = runBlocking {
        val model = repository.saveModel(AiModelDraft(providerId = "images", modelName = "Custom", modelId = "custom-edit", capabilities = setOf(AiCapability.IMAGE_EDITING)))
        assertEquals(setOf(AiCapability.IMAGE_EDITING), repository.getModelConfig(model.id)!!.capabilities)
        repository.saveModel(AiModelDraft(modelProfileId = model.id, providerId = "images", modelName = "Custom", modelId = "custom-edit", capabilities = setOf(AiCapability.IMAGE_GENERATION)))
        assertEquals(setOf(AiCapability.IMAGE_GENERATION), repository.getModelConfig(model.id)!!.capabilities)
    }

    @Test fun `image provider imports only recognized image models from a mixed models list`() = runBlocking {
        val saved = repository.importProviderModels("images", listOf(AiAvailableModel("gpt-image-1", "Image"), AiAvailableModel("gpt-5", "Text")))
        assertEquals(listOf("gpt-image-1"), saved.map { it.modelId })
        repository.saveModel(AiModelDraft(modelProfileId = saved.single().id, providerId = "images", modelName = "Image", modelId = "gpt-image-1", capabilities = setOf(AiCapability.IMAGE_GENERATION)))
        val refreshed = repository.importProviderModels("images", listOf(AiAvailableModel("gpt-image-1", "Image"))).single()
        assertEquals(setOf(AiCapability.IMAGE_GENERATION), AiImageModels.capabilities(refreshed.modelId, refreshed.capabilities))
    }

    @Test fun `image profile cannot become the default text model`() = runBlocking {
        val model = repository.saveModel(AiModelDraft(providerId = "images", modelName = "Custom", modelId = "custom-edit"))
        try { repository.setDefaultModel(model.id); fail("Expected a text-model guard") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("text model")) }
    }
}
