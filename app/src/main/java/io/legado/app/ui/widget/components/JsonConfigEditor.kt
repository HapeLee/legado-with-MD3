package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSliderSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.utils.GSON

sealed class JsonKeyEditorConfig {
    data class Slider(val range: ClosedFloatingPointRange<Float>, val steps: Int = 0) :
        JsonKeyEditorConfig()

    data class Dropdown(val displayEntries: Array<String>, val entryValues: Array<String>) :
        JsonKeyEditorConfig() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Dropdown) return false
            return displayEntries.contentEquals(other.displayEntries) && entryValues.contentEquals(
                other.entryValues
            )
        }

        override fun hashCode(): Int {
            return 31 * displayEntries.contentHashCode() + entryValues.contentHashCode()
        }
    }

    object Switch : JsonKeyEditorConfig()
}

@Composable
fun JsonConfigEditor(
    jsonString: String,
    onJsonStringChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyConfigs: Map<String, JsonKeyEditorConfig> = emptyMap()
) {
    val jsonObject = remember(jsonString) {
        runCatching {
            JsonParser.parseString(jsonString).asJsonObject
        }.getOrElse { JsonObject() }
    }

    Column(modifier = modifier) {
        jsonObject.entrySet().forEach { entry ->
            val key = entry.key
            val value = entry.value
            val config = keyConfigs[key]

            val displayTitle = when (key) {
                "columns" -> "列数 (Columns)"
                "rows" -> "行数 (Rows)"
                else -> key
            }

            when {
                (config is JsonKeyEditorConfig.Slider || key.contains("columns") || key.contains("rows")) &&
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> {
                    val range = (config as? JsonKeyEditorConfig.Slider)?.range ?: 0f..10f
                    val steps = (config as? JsonKeyEditorConfig.Slider)?.steps ?: 10
                    CompactSliderSettingItem(
                        title = displayTitle,
                        value = value.asFloat,
                        valueRange = range,
                        steps = steps,
                        onValueChange = {
                            val newObj = jsonObject.deepCopy()
                            if (it == it.toInt().toFloat()) {
                                newObj.addProperty(key, it.toInt())
                            } else {
                                newObj.addProperty(key, it)
                            }
                            onJsonStringChange(GSON.toJson(newObj))
                        }
                    )
                }

                config is JsonKeyEditorConfig.Dropdown -> {
                    CompactDropdownSettingItem(
                        title = displayTitle,
                        selectedValue = value.asString,
                        displayEntries = config.displayEntries,
                        entryValues = config.entryValues,
                        onValueChange = {
                            val newObj = jsonObject.deepCopy()
                            newObj.addProperty(key, it)
                            onJsonStringChange(GSON.toJson(newObj))
                        }
                    )
                }

                (config is JsonKeyEditorConfig.Switch || (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)) -> {
                    CompactSwitchSettingItem(
                        title = displayTitle,
                        checked = value.asBoolean,
                        onCheckedChange = {
                            val newObj = jsonObject.deepCopy()
                            newObj.addProperty(key, it)
                            onJsonStringChange(GSON.toJson(newObj))
                        }
                    )
                }

                else -> {
                    JsonRawEditor(
                        value = if (value.isJsonPrimitive) value.asString else GSON.toJson(value),
                        onValueChange = {
                            val newObj = jsonObject.deepCopy()
                            if (it.toLongOrNull() != null) {
                                newObj.addProperty(key, it.toLong())
                            } else if (it.toDoubleOrNull() != null) {
                                newObj.addProperty(key, it.toDouble())
                            } else if (it == "true" || it == "false") {
                                newObj.addProperty(key, it.toBoolean())
                            } else {
                                try {
                                    val element = JsonParser.parseString(it)
                                    newObj.add(key, element)
                                } catch (_: Exception) {
                                    newObj.addProperty(key, it)
                                }
                            }
                            onJsonStringChange(GSON.toJson(newObj))
                        },
                        label = displayTitle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
