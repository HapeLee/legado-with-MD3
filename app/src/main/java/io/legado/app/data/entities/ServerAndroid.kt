package io.legado.app.data.entities

import org.json.JSONObject

/**
 * [Server] 的 Android 特有扩展：`config` 字段的 [JSONObject] 视图。
 *
 * [Server] 本体已下沉 :core:data，但 `org.json.JSONObject` 是 Android 专属，
 * 无法进 commonMain，故该方法作为 Android 侧扩展保留。
 */
fun Server.getConfigJsonObject(): JSONObject? {
    val json = config
    json ?: return null
    return JSONObject(json)
}
