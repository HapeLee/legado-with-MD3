package io.legado.app.base.rules

import io.legado.app.core.rules.BuiltInRulesImporter
import io.legado.app.help.DefaultData

/**
 * [BuiltInRulesImporter] 的 Android 实现。
 *
 * 实现体只是把 ViewModel 里原来的 `DefaultData.importDefaultTocRules()` 调用转过来；
 * `DefaultData` 依赖 `appDb` 与 assets，按设计留在 `:app`，接口随规则基类下沉，
 * 便于 TXT 目录规则页提升为 `:feature:*` 模块。
 */
class AndroidBuiltInRulesImporter : BuiltInRulesImporter {

    override suspend fun importTxtTocRules() {
        DefaultData.importDefaultTocRules()
    }
}
