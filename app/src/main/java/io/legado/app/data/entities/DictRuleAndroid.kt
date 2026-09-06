package io.legado.app.data.entities

import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlin.coroutines.coroutineContext

/**
 * [DictRule] 的 Android 特有扩展：按规则搜索字典。
 *
 * 实体本体已下沉 :core:data，但搜索依赖 `AnalyzeUrl`（Rhino/okhttp/ExoPlayer
 * 平台栈），无法进 commonMain，故作为 Android 侧扩展保留（与
 * `BookGroup.getManageName`、`Server.getConfigJsonObject` 同一模式）。
 */
suspend fun DictRule.search(word: String): String {
    val analyzeUrl = AnalyzeUrl(urlRule, key = word, coroutineContext = coroutineContext)
    val body = analyzeUrl.getStrResponseAwait().body
    if (showRule.isBlank()) {
        return body!!
    }
    val analyzeRule = AnalyzeRule().setCoroutineContext(coroutineContext)
    return analyzeRule.getString(showRule, mContent = body)
}
