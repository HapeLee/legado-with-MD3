package io.legado.app.help.ai

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import org.json.JSONArray
import org.json.JSONObject
import splitties.init.appCtx

object AiSettingsTool {

    private const val TOOL_GET_SETTINGS = "get_app_settings"
    private const val TOOL_SET_SETTING = "set_app_setting"
    private const val TOOL_SET_SETTINGS_BATCH = "set_app_settings_batch"

    private data class SettingDef(
        val key: String,
        val type: String,
        val values: Set<String> = emptySet(),
        val min: Int? = null,
        val max: Int? = null
    )

    private val settingDefs = listOf(
        SettingDef(PreferKey.themeMode, "int", min = 0, max = 3),
        SettingDef(PreferKey.showDiscovery, "boolean"),
        SettingDef(PreferKey.showRss, "boolean"),
        SettingDef(PreferKey.showReadRecord, "boolean"),
        SettingDef(PreferKey.modernDiscoveryPage, "boolean"),
        SettingDef(PreferKey.modernRssPage, "boolean"),
        SettingDef(PreferKey.mergeDiscoveryRss, "boolean"),
        SettingDef(PreferKey.defaultHomePage, "string", values = setOf("bookshelf", "explore", "rss", "my")),
        SettingDef(PreferKey.aiAssistantEnabled, "boolean"),
        SettingDef(PreferKey.aiEnterToSend, "boolean"),
        SettingDef(PreferKey.aiTavilyEnabled, "boolean"),
        SettingDef(PreferKey.aiTavilyTopic, "string", values = setOf("general", "news", "finance")),
        SettingDef(PreferKey.aiTavilySearchDepth, "string", values = setOf("basic", "advanced", "ultra-fast")),
        SettingDef(PreferKey.aiTavilyMaxResults, "int", min = 1, max = 10),
        SettingDef(PreferKey.aiAgentToolMaxAttempts, "int", min = 1, max = 5),
        SettingDef(PreferKey.aiAgentToolRetryBackoffMillis, "int", min = 0, max = 5000)
    )
    private val settingDefMap = settingDefs.associateBy { it.key }

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(
                name = TOOL_GET_SETTINGS,
                definition = getSettingsDefinition(),
                execute = { args -> getSettings(args) }
            ),
            AiResolvedTool(
                name = TOOL_SET_SETTING,
                definition = setSettingDefinition(),
                execute = { args -> setSetting(args) }
            ),
            AiResolvedTool(
                name = TOOL_SET_SETTINGS_BATCH,
                definition = setSettingsBatchDefinition(),
                execute = { args -> setSettingsBatch(args) }
            )
        )
    }

    private fun getSettingsDefinition() = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", TOOL_GET_SETTINGS)
            put("description", "读取应用可公开配置项。支持按 key 或分类读取。")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("keys", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply { put("type", "string") })
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("ui", "discovery", "subscription", "ai")))
                    })
                })
                put("additionalProperties", false)
            })
        })
    }

    private fun setSettingDefinition() = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", TOOL_SET_SETTING)
            put("description", "修改单个白名单设置项。")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("key", JSONObject().apply { put("type", "string") })
                    put("value", JSONObject().apply { put("description", "按 key 类型传值") })
                })
                put("required", JSONArray(listOf("key", "value")))
                put("additionalProperties", false)
            })
        })
    }

    private fun setSettingsBatchDefinition() = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", TOOL_SET_SETTINGS_BATCH)
            put("description", "批量修改白名单设置项。")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("items", JSONObject().apply {
                        put("type", "array")
                        put("items", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("key", JSONObject().apply { put("type", "string") })
                                put("value", JSONObject().apply { put("description", "按 key 类型传值") })
                            })
                            put("required", JSONArray(listOf("key", "value")))
                        })
                    })
                })
                put("required", JSONArray(listOf("items")))
                put("additionalProperties", false)
            })
        })
    }

    private fun getSettings(arguments: JSONObject?): String {
        val category = arguments?.optString("category")?.trim().orEmpty()
        val keys = linkedSetOf<String>().apply {
            arguments?.optJSONArray("keys")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            if (isEmpty()) {
                addAll(categoryKeys(category))
            }
        }
        val items = JSONArray()
        keys.forEach { key ->
            val def = settingDefMap[key] ?: return@forEach
            items.put(JSONObject().apply {
                put("key", key)
                put("type", def.type)
                put("value", readSettingValue(key, def.type))
            })
        }
        return JSONObject().apply {
            put("ok", true)
            put("count", items.length())
            put("items", items)
        }.toString()
    }

    private fun setSetting(arguments: JSONObject?): String {
        if (arguments == null) return jsonError("missing arguments")
        val key = arguments.optString("key").trim()
        val value = arguments.opt("value")
        val result = applySetting(key, value)
        return JSONObject().apply {
            put("ok", result.optBoolean("ok"))
            put("result", result)
        }.toString()
    }

    private fun setSettingsBatch(arguments: JSONObject?): String {
        if (arguments == null) return jsonError("missing arguments")
        val items = arguments.optJSONArray("items") ?: return jsonError("missing items")
        val results = JSONArray()
        var successCount = 0
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val key = item.optString("key").trim()
            val value = item.opt("value")
            val result = applySetting(key, value)
            if (result.optBoolean("ok")) successCount++
            results.put(result)
        }
        return JSONObject().apply {
            put("ok", successCount == results.length())
            put("successCount", successCount)
            put("totalCount", results.length())
            put("results", results)
        }.toString()
    }

    private fun applySetting(key: String, rawValue: Any?): JSONObject {
        val def = settingDefMap[key] ?: return JSONObject().apply {
            put("ok", false)
            put("key", key)
            put("error", "unsupported key")
        }
        return runCatching {
            when (def.type) {
                "boolean" -> {
                    val value = when (rawValue) {
                        is Boolean -> rawValue
                        is String -> rawValue.equals("true", true)
                        is Number -> rawValue.toInt() != 0
                        else -> throw IllegalArgumentException("invalid boolean")
                    }
                    appCtx.putPrefBoolean(key, value)
                }

                "int" -> {
                    val value = when (rawValue) {
                        is Number -> rawValue.toInt()
                        is String -> rawValue.toIntOrNull()
                            ?: throw IllegalArgumentException("invalid int")
                        else -> throw IllegalArgumentException("invalid int")
                    }
                    val limited = value.coerceIn(def.min ?: Int.MIN_VALUE, def.max ?: Int.MAX_VALUE)
                    appCtx.putPrefInt(key, limited)
                }

                else -> {
                    val value = rawValue?.toString()?.trim().orEmpty()
                    if (def.values.isNotEmpty() && value !in def.values) {
                        throw IllegalArgumentException("invalid enum")
                    }
                    appCtx.putPrefString(key, value)
                }
            }
            JSONObject().apply {
                put("ok", true)
                put("key", key)
                put("value", readSettingValue(key, def.type))
            }
        }.getOrElse {
            JSONObject().apply {
                put("ok", false)
                put("key", key)
                put("error", it.localizedMessage ?: "failed")
            }
        }
    }

    private fun readSettingValue(key: String, type: String): Any? {
        return when (type) {
            "boolean" -> appCtx.getPrefBoolean(key, false)
            "int" -> appCtx.getPrefInt(key, 0)
            else -> appCtx.getPrefString(key).orEmpty()
        }
    }

    private fun categoryKeys(category: String): List<String> {
        return when (category) {
            "discovery" -> listOf(
                PreferKey.showDiscovery,
                PreferKey.modernDiscoveryPage
            )

            "subscription" -> listOf(
                PreferKey.showRss,
                PreferKey.modernRssPage,
                PreferKey.mergeDiscoveryRss
            )

            "ai" -> listOf(
                PreferKey.aiAssistantEnabled,
                PreferKey.aiEnterToSend,
                PreferKey.aiTavilyEnabled,
                PreferKey.aiTavilyTopic,
                PreferKey.aiTavilySearchDepth,
                PreferKey.aiTavilyMaxResults,
                PreferKey.aiAgentToolMaxAttempts,
                PreferKey.aiAgentToolRetryBackoffMillis
            )

            else -> listOf(
                PreferKey.themeMode,
                PreferKey.defaultHomePage,
                PreferKey.showDiscovery,
                PreferKey.showRss,
                PreferKey.showReadRecord
            )
        }
    }

    private fun jsonError(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }.toString()
    }
}
