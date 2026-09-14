package io.legado.app.data.rules

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.*

/**
 * 替换规则导入文本的**旧格式兼容解析**（jsonpath）。
 *
 * M1-3x 从 `io.legado.app.help` 搬到 `io.legado.app.data.rules`：它是替换规则导入导出的唯一
 * 旧格式入口，而 `io.legado.app.help.**` 是 G4 `checkLegacyArchitecture` 盯的 legacy 静态门面
 * 命名空间——新代码（`:feature:replacerules` 转 CMP 后的共享层实现 `AndroidReplaceRuleImportCompat`）
 * 一旦 import 它，就会在 `legacyHelp` 棘轮上「新区域首次出现」。搬进干净包名后新区域计数为 0，
 * **不靠放宽基线过关**；与 M1-3b 把 `RuleTransferUseCase` 放进 `io.legado.app.core.rules`
 * 是同一手法。旧包名 `io.legado.app.help` 下因此不再有本模块的文件。
 *
 * 实现本身一行未改——两条分支（Gson 标准格式优先、旧键名回落）与 jsonpath 依赖保持不变，
 * 行为基线见 androidHostTest 的 `ReplaceAnalyzerTest`。
 */
object ReplaceAnalyzer {

    fun jsonToReplaceRules(json: String): Result<MutableList<ReplaceRule>> {
        return kotlin.runCatching {
            val replaceRules = mutableListOf<ReplaceRule>()
            val items: List<Map<String, Any>> = jsonPath.parse(json).read("$")
            for (item in items) {
                val jsonItem = jsonPath.parse(item)
                jsonToReplaceRule(jsonItem.jsonString()).getOrThrow().let {
                    if (it.isValid()) {
                        replaceRules.add(it)
                    }
                }
            }
            replaceRules
        }
    }

    fun jsonToReplaceRule(json: String): Result<ReplaceRule> {
        return runCatching {
            val replaceRule: ReplaceRule? =
                GSON.fromJsonObject<ReplaceRule>(json.trim()).getOrNull()
            if (replaceRule == null || replaceRule.pattern.isEmpty()) {
                val jsonItem = jsonPath.parse(json.trim())
                val rule = ReplaceRule()
                rule.id = jsonItem.readLong("$.id") ?: System.currentTimeMillis()
                rule.pattern = jsonItem.readString("$.regex") ?: ""
                if (rule.pattern.isEmpty()) throw NoStackTraceException("格式不对")
                rule.name = jsonItem.readString("$.replaceSummary") ?: ""
                rule.replacement = jsonItem.readString("$.replacement") ?: ""
                rule.isRegex = jsonItem.readBool("$.isRegex") == true
                rule.scope = jsonItem.readString("$.useTo")
                rule.isEnabled = jsonItem.readBool("$.enable") == true
                rule.order = jsonItem.readInt("$.serialNumber") ?: 0
                return@runCatching rule
            }
            return@runCatching replaceRule
        }
    }

}