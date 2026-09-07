package io.legado.app.data.entities.rule

import com.google.gson.JsonDeserializer
import io.legado.app.utils.INITIAL_GSON

/**
 * rule 簇的 Android 特有反序列化兼容（P4-e）。
 *
 * 6 个主 rule 本体已下沉 :core:data，原先 companion object 里的
 * `JsonDeserializer`（依赖 `com.google.gson` JVM 三方库）无法进 commonMain，
 * 故下沉时移除，改由本文件的 deserializer 承接原语义，注册到 `GsonExtensions.kt` 的 `GSON`。
 *
 * 语义与原实现一致：JSON 对象走 [INITIAL_GSON] 反序列化；JSON 原始字符串（旧书源里
 * 规则可能以纯字符串存储）先取 `asString` 再反序列化；其它（null/数组等）返回 null。
 * 注意内部委托 **INITIAL_GSON**（非 GSON）避免 adapter 递归。
 */
val bookInfoRuleJsonDeserializer = JsonDeserializer<BookInfoRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, BookInfoRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, BookInfoRule::class.java)
        else -> null
    }
}

val contentRuleJsonDeserializer = JsonDeserializer<ContentRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, ContentRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, ContentRule::class.java)
        else -> null
    }
}

val exploreRuleJsonDeserializer = JsonDeserializer<ExploreRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, ExploreRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, ExploreRule::class.java)
        else -> null
    }
}

val searchRuleJsonDeserializer = JsonDeserializer<SearchRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, SearchRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, SearchRule::class.java)
        else -> null
    }
}

val tocRuleJsonDeserializer = JsonDeserializer<TocRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, TocRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, TocRule::class.java)
        else -> null
    }
}

val reviewRuleJsonDeserializer = JsonDeserializer<ReviewRule?> { json, _, _ ->
    when {
        json.isJsonObject -> INITIAL_GSON.fromJson(json, ReviewRule::class.java)
        json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, ReviewRule::class.java)
        else -> null
    }
}
