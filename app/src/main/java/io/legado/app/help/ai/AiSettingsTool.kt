package io.legado.app.help.ai

import android.content.Context
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx

object AiSettingsTool {

    fun resolvedTools(): List<AiResolvedTool> = listOf(
        AiResolvedTool(
            name = "get_app_settings",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "get_app_settings")
                    put("description", "读取应用的关键设置项，包括主题、字体、翻页方式、朗读设置、AI 配置等。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("keys", JSONObject().apply {
                                put("type", "array")
                                put("description", "要读取的设置键列表。如不传则返回常用项。")
                                put("items", JSONObject().put("type", "string"))
                            })
                        })
                    })
                })
            },
            execute = { args -> getAppSettings(args) }
        ),
        AiResolvedTool(
            name = "set_app_setting",
            definition = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "set_app_setting")
                    put("description", "修改应用的某个设置项。请只修改你确定含义的项。")
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("required", JSONArray().put("key").put("value"))
                        put("properties", JSONObject().apply {
                            put("key", JSONObject().apply {
                                put("type", "string")
                                put("description", "设置项的键名")
                            })
                            put("value", JSONObject().apply {
                                put("type", "string")
                                put("description", "设置项的新值（字符串形式）")
                            })
                        })
                    })
                })
            },
            execute = { args -> setAppSetting(args) }
        )
    )

    private val readableKeys = listOf(
        "theme" to "主题",
        "bgColor" to "阅读背景色",
        "textColor" to "文字颜色",
        "fontSize" to "字号",
        "fontPath" to "字体路径",
        "pageAnim" to "翻页动画",
        "autoNextPage" to "自动翻页",
        "ttsEngine" to "朗读引擎",
        "ttsRate" to "朗读语速",
        "aiProvider" to "AI 供应商",
        "aiModel" to "AI 模型"
    )

    private suspend fun getAppSettings(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val keysArray = args?.optJSONArray("keys")
        val keys = if (keysArray != null && keysArray.length() > 0) {
            (0 until keysArray.length()).map { keysArray.optString(it) }
        } else {
            readableKeys.map { it.first }
        }

        val result = JSONObject()
        keys.forEach { key ->
            val label = readableKeys.firstOrNull { it.first == key }?.second ?: key
            val value = try {
                when {
                    key == "theme" -> readPref(key, "default")
                    key == "fontSize" -> readPref(key, "18")
                    key == "aiProvider" -> AiChatService.getCurrentProvider()?.name ?: "未配置"
                    key == "aiModel" -> AiChatService.getCurrentModel()?.modelId ?: "未配置"
                    else -> readPref(key, "")
                }
            } catch (e: Exception) {
                "<读取失败: ${e.message}>"
            }
            result.put(key, JSONObject().apply {
                put("label", label)
                put("value", value)
            })
        }

        JSONObject().apply {
            put("ok", true)
            put("settings", result)
        }.toString(2)
    }

    private suspend fun setAppSetting(args: JSONObject?): String = withContext(Dispatchers.IO) {
        val key = args?.optString("key", "")?.takeIf { it.isNotBlank() }.orEmpty()
        val value = args?.optString("value", "")?.takeIf { it.isNotBlank() }.orEmpty()

        if (key.isEmpty() || value.isEmpty()) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "需要提供 key 和 value")
            }.toString(2)
        }

        val safeKeys = setOf(
            "bgColor", "textColor", "fontSize", "fontPath",
            "pageAnim", "autoNextPage", "ttsRate"
        )
        if (key !in safeKeys) {
            return@withContext JSONObject().apply {
                put("ok", false)
                put("error", "不允许修改的设置项: $key。允许修改的项：${safeKeys.joinToString()}")
            }.toString(2)
        }

        try {
            writePref(key, value)
            JSONObject().apply {
                put("ok", true)
                put("key", key)
                put("value", value)
                put("message", "设置已更新")
            }.toString(2)
        } catch (e: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", e.message ?: "未知错误")
            }.toString(2)
        }
    }

    private fun readPref(key: String, default: String): String {
        return runCatching {
            val prefs = appCtx.getSharedPreferences("config", Context.MODE_PRIVATE)
            prefs.getString(key, default) ?: default
        }.getOrDefault(default)
    }

    private fun writePref(key: String, value: String) {
        val prefs = appCtx.getSharedPreferences("config", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }
}
