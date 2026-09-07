package io.legado.app.data.entities

import com.google.gson.JsonDeserializer
import io.legado.app.utils.INITIAL_GSON

/**
 * [TxtTocRule] 的 Android 特有反序列化兼容。
 *
 * [TxtTocRule] 本体已下沉 :core:data，其 `chapterRule` 字段原先带
 * `@SerializedName(value = "chapterRule", alternate = ["rule"])`，用于兼容旧版本备份
 * （旧备份里该字段键名为 `rule`）。`com.google.gson.annotations.SerializedName` 是 JVM 三方库
 * 注解，无法进 commonMain，故下沉时移除，改由本 deserializer 在反序列化阶段做等价的键名提升。
 *
 * 语义与原注解严格一致：仅当 `chapterRule` 缺失时才取 `rule`；非对象输入交给 [INITIAL_GSON]
 * 按原行为处理（抛异常），不静默丢数据。
 *
 * 注册点见 `GsonExtensions.kt` 的 `GSON`。
 */
val txtTocRuleJsonDeserializer = JsonDeserializer<TxtTocRule?> { json, _, _ ->
    if (json.isJsonObject) {
        val obj = json.asJsonObject
        if (!obj.has("chapterRule")) {
            obj.get("rule")?.takeIf { !it.isJsonNull }?.let { obj.add("chapterRule", it) }
        }
    }
    INITIAL_GSON.fromJson(json, TxtTocRule::class.java)
}
