package io.legado.app.domain.usecase

import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class RefineSpeechWithAiParamsTest {

    @Test
    fun `off disables thinking even when the model defaults to medium`() {
        val params = speechAnalysisParams(preset(AiReasoningLevel.MEDIUM), AiReasoningLevel.OFF)

        assertEquals(AiReasoningLevel.OFF, params.reasoningLevel)
        assertEquals(0f, params.temperature ?: -1f)
    }

    @Test
    fun `auto keeps the preset and model level`() {
        val params = speechAnalysisParams(preset(AiReasoningLevel.MEDIUM), AiReasoningLevel.AUTO)

        assertEquals(AiReasoningLevel.MEDIUM, params.reasoningLevel)
    }

    @Test
    fun `an explicit level overrides the model default`() {
        val params = speechAnalysisParams(preset(AiReasoningLevel.MEDIUM), AiReasoningLevel.HIGH)

        assertEquals(AiReasoningLevel.HIGH, params.reasoningLevel)
    }

    private fun preset(reasoningLevel: AiReasoningLevel) = AiTaskPresetConfig(
        id = "preset-chat",
        taskType = "chat",
        name = "Default Chat",
        model = AiModelConfig(
            id = "model-1",
            provider = AiProviderConfig(
                id = "zhipu",
                name = "Zhipu AI",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                apiKey = "test"
            ),
            displayName = "GLM-4.7-Flash",
            modelId = "glm-4.7-flash",
            defaultParams = AiGenerationParams(reasoningLevel = reasoningLevel)
        ),
        promptTemplate = "prompt",
        params = AiGenerationParams(
            temperature = 0.7f,
            reasoningLevel = reasoningLevel
        )
    )
}
