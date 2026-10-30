package io.legado.app.domain.model

/** Image output is independent of VISION (image input). Unknown names remain user-configurable. */
object AiImageModels {
    /** Storage provenance, removed before exposing effective capabilities to service callers. */
    const val EXPLICIT_CAPABILITIES = "_explicit_capabilities"
    val outputCapabilities = setOf(AiCapability.IMAGE_GENERATION, AiCapability.IMAGE_EDITING)

    fun inferCapabilities(modelId: String): Set<String>? {
        val id = modelId.lowercase()
        return when {
            "qwen-image-edit" in id -> setOf(AiCapability.IMAGE_EDITING, AiCapability.VISION)
            "gpt-image" in id || "seedream" in id || "grok-imagine-image" in id ||
                ("gemini" in id && ("image" in id || "nano-banana" in id)) ||
                "qwen-image-2" in id -> outputCapabilities + AiCapability.VISION
            "dall-e" in id || id.startsWith("imagen-") || "/imagen-" in id || "qwen-image" in id -> setOf(AiCapability.IMAGE_GENERATION)
            else -> null
        }
    }

    fun capabilities(modelId: String, stored: String): Set<String> = stored.split(',').map { it.trim() }
        .filter { it.isNotEmpty() }.toSet().let { saved ->
            // Old image profiles may have been incorrectly inferred as GPT/Gemini text models.
            if (EXPLICIT_CAPABILITIES in saved) saved - EXPLICIT_CAPABILITIES
            else if (saved.none { it in outputCapabilities }) inferCapabilities(modelId) ?: saved else saved
        }

    fun isTextModel(protocol: String, modelId: String, stored: String): Boolean =
        protocol !in AiProtocol.imageProtocols && inferCapabilities(modelId) == null && capabilities(modelId, stored).none { it in outputCapabilities }

    fun editProtocol(protocol: String): String? = when (protocol) {
        in AiProtocol.imageProtocols -> protocol
        AiProtocol.OPENAI_CHAT_COMPLETIONS, AiProtocol.OPENAI_RESPONSES -> AiProtocol.OPENAI_IMAGES
        else -> null
    }
}

data class AiImageRequest(
    val model: AiModelConfig,
    val prompt: String,
    val image: AiImageInput? = null,
    val endpointPath: String? = null,
) {
    override fun toString() = "AiImageRequest(model=${model.id}, editing=${image != null})"
}

class AiImageResult(val bytes: ByteArray) {
    override fun toString() = "AiImageResult(${bytes.size} bytes)"
}
