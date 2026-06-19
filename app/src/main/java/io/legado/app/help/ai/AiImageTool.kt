package io.legado.app.help.ai

import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AiImageTool {

    fun resolvedTools(): List<AiResolvedTool> = listOf(
        AiResolvedTool(
            name = "generate_image",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "generate_image")
                    put("description", "使用 AI 生成图片。可生成书籍封面、角色头像、场景插画等。请用中文描述画面。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", java.util.Collection<String>().apply { add("prompt") })
                        put("properties", JSONObject().apply {
                            put("prompt", JSONObject().apply {
                                put("type", "string")
                                put("description", "图片提示词，描述画面内容、风格、氛围等")
                            })
                            put("style", JSONObject().apply {
                                put("type", "string")
                                put("description", "风格：book_cover（书籍封面）、character（角色头像）、scene（场景）、illustration（插画）")
                            })
                            put("size", JSONObject().apply {
                                put("type", "string")
                                put("description", "尺寸：1024x1024、1024x1792、1792x1024")
                            })
                        })
                    })
                })
            },
            execute = { args -> generateImage(args) }
        ),
        AiResolvedTool(
            name = "generate_book_character_avatar",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "generate_book_character_avatar")
                    put("description", "为书中角色生成头像图片。请描述角色的外貌特征、穿着风格、气质等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", java.util.Collection<String>().apply { add("description") })
                        put("properties", JSONObject().apply {
                            put("description", JSONObject().apply {
                                put("type", "string")
                                put("description", "角色的详细外貌描述")
                            })
                            put("characterName", JSONObject().apply {
                                put("type", "string")
                                put("description", "角色名字")
                            })
                        })
                    })
                })
            },
            execute = { args -> generateCharacterAvatar(args) }
        )
    )

    private suspend fun generateImage(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val prompt = args?.optString("prompt", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val style = args?.optString("style", "illustration") ?: "illustration"
        val size = args?.optString("size", "1024x1024") ?: "1024x1024"

        if (prompt.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 prompt")
            }.toString(2)
        }

        val provider = AiChatService.getCurrentProvider() ?: AiDefaultConfig.DEFAULT_IMAGE_PROVIDER
        val apiKey = provider.apiKey
        val baseUrl = provider.baseUrl.trimEnd('/')

        if (apiKey.isEmpty() || baseUrl.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "请先在 AI 设置中配置图片生成服务的 API Key 和 Base URL。支持 OpenAI DALL-E 兼容接口。")
                put("promptUsed", buildPrompt(prompt, style))
            }.toString(2)
        }

        val finalPrompt = buildPrompt(prompt, style)

        val imageUrl = runCatching {
            val requestBody = JSONObject().apply {
                put("model", "dall-e-3")
                put("prompt", finalPrompt)
                put("size", size)
                put("n", 1)
            }.toString()

            val response = okHttpClient.newCallResponse {
                url("$baseUrl/images/generations")
                addHeader("Content-Type", "application/json")
                addHeader("Authorization", "Bearer $apiKey")
                postJson(requestBody)
            }

            response.use { rawResponse ->
                val payload = rawResponse.body?.string().orEmpty()
                val json = JSONObject(payload)
                json.optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    .orEmpty()
            }
        }.getOrDefault("")

        if (imageUrl.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "图片生成失败，请检查 API Key 和网络连接")
                put("promptUsed", finalPrompt)
            }.toString(2)
        }

        JSONObject().apply {
            put("ok", true)
            put("prompt", finalPrompt)
            put("style", style)
            put("size", size)
            put("imageUrl", imageUrl)
        }.toString(2)
    }

    private suspend fun generateCharacterAvatar(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val description = args?.optString("description", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val characterName = args?.optString("characterName", "") ?: ""

        if (description.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 description")
            }.toString(2)
        }

        val prompt = buildString {
            append("人物头像，")
            if (characterName.isNotBlank()) append("$characterName，")
            append(description)
            append("，肖像画，半身像，高细节，数字艺术")
        }

        val newArgs = JSONObject().apply {
            put("prompt", prompt)
            put("style", "character")
            put("size", "1024x1024")
        }

        generateImage(newArgs)
    }

    private fun buildPrompt(prompt: String, style: String): String {
        return when (style.lowercase()) {
            "book_cover", "book cover", "封面" ->
                "书籍封面设计，$prompt，简洁布局，高分辨率，专业设计"
            "character", "角色", "头像" ->
                "人物头像，$prompt，肖像画，半身像，高细节，数字艺术"
            "scene", "场景" ->
                "场景插画，$prompt，氛围渲染，电影级构图，高细节"
            else ->
                "$prompt，高质量，高细节"
        }
    }
}
