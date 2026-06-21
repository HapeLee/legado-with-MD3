package io.legado.app.help.ai

import org.json.JSONArray
import org.json.JSONObject

object AiImageTool {
    fun resolvedTools(): List<AiResolvedTool> = listOf(
        AiResolvedTool(
            name = "generate_image",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "generate_image")
                    put("description", "Generate an image and return an image url or data url.")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("prompt", JSONObject().apply {
                                put("type", "string")
                                put("description", "Image prompt")
                            })
                            put("providerId", JSONObject().apply {
                                put("type", "string")
                                put("description", "Optional image provider id. Use only when user explicitly selects an image model; otherwise omit it.")
                            })
                        })
                        put("required", JSONArray().put("prompt"))
                    })
                })
            },
            execute = { args ->
                val prompt = args?.optString("prompt").orEmpty().trim()
                if (prompt.isBlank()) {
                    JSONObject().put("ok", false).put("success", false).put("error", "prompt is empty").toString()
                } else {
                    val providerId = args?.optString("providerId").orEmpty().trim()
                    val provider = if (providerId.isBlank()) {
                        null
                    } else {
                        AiImageService.providerByIdOrNull(providerId)
                    }
                    if (providerId.isNotBlank() && provider == null) {
                        JSONObject()
                            .put("ok", false)
                            .put("success", false)
                            .put("error", "image provider is unavailable: $providerId")
                            .toString()
                    } else {
                        val targetProvider = provider ?: AiImageService.currentProviderOrNull()
                        runCatching {
                            val image = AiImageService.generateAndStore(
                                prompt,
                                provider,
                                metadata = AiImageGalleryManager.ImageMetadata(
                                    sourceType = AiImageGalleryManager.SOURCE_TYPE_CHAT,
                                    sourceText = prompt
                                )
                            )
                            JSONObject()
                                .put("ok", true)
                                .put("success", true)
                                .put("type", "image")
                                .put("imageId", image.id)
                                .put("imagePath", image.localPath)
                                .put("name", image.name)
                                .put("prompt", prompt)
                                .put("provider", image.providerName)
                                .put("model", image.model)
                                .toString()
                        }.getOrElse {
                            JSONObject()
                                .put("ok", false)
                                .put("success", false)
                                .put("error", it.localizedMessage ?: it.javaClass.simpleName)
                                .apply {
                                    targetProvider?.let { current ->
                                        put("provider", current.displayName())
                                        put("providerType", current.type)
                                        put("baseUrl", current.baseUrl)
                                        put("model", current.model)
                                    }
                                }
                                .toString()
                        }
                    }
                }
            }
        )
    )
}
