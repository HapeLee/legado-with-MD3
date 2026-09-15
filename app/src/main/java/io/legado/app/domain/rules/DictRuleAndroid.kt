package io.legado.app.domain.rules

import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlin.coroutines.coroutineContext

/**
 * [DictRule] 的 Android 特有扩展：按规则搜索字典。
 *
 * 领域模型本体与仓储端口已下沉 `:domain:rules` / `:data:rules`（M3-3），但搜索依赖
 * `AnalyzeUrl`（Rhino/okhttp/ExoPlayer 平台栈），无法进 commonMain，故作为 Android 侧扩展
 * 保留（与 `BookGroup.getManageName`、`Server.getConfigJsonObject` 同一模式）。
 *
 * ⚠️ 位置：M3-3 之前本文件住 `app/data/entities/`、包名 `io.legado.app.data.entities`，
 * 接收的是 Room 实体；接收者换成领域模型后，包名与目录一起挪到 `io.legado.app.domain.rules`
 * ——与 `:domain:rules` 包同名、但**住 `:app`**。这是 platform island 的常规形态
 * （`:app` 可以有共享层包名的平台实现，反过来不行）。
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
