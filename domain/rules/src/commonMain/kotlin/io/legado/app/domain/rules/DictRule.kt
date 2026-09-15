package io.legado.app.domain.rules

/**
 * 字典规则的领域模型（M3-3）。
 *
 * 字段集合、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.DictRule` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ 与 M3-1/M3-2 一致，字段用 `var` 而非 `val`：这些字段要经 `:core:platform` 的
 * [io.legado.app.core.platform.JsonCodec]（Gson）反射直写。本片至少有四条真实路径走它——
 * `feature:dict` 的 `DictRuleTransferSpec.parseImportRules` / `pasteRule`，
 * 以及 `:app` 老导入路径的 `GSON.fromJsonObject<DictRule>` /
 * `GSON.fromJsonArray<DictRule>`。改成 `val`（final）后 JVM 17+ 的反射写入行为与
 * Android ART 不一致，而 `:app` 侧没有单测覆盖这条路径。
 *
 * ⚠️ **主键是 [name]（String），不是 `id: Long`**——与 `ReplaceRule` / `HighlightTagRule`
 * 都不同。判等因此按 `name` 走，`equals`/`hashCode` 也只认 `name`：
 * 两个字段完全不同的规则，只要 `name` 相同就算同一个。所以映射用例必须**逐字段断言**，
 * 任何"整对象比较"式的测试都发现不了漏映射的字段（见 `DictRuleMapperTest`）。
 *
 * 平台侧扩展（`search`）不在这里：它依赖 `AnalyzeUrl`（Rhino/okhttp 平台栈），
 * 住 `:app` 的 `io.legado.app.domain.rules.DictRuleAndroid.kt`。
 */
data class DictRule(
    var name: String = "",
    var urlRule: String = "",
    var showRule: String = "",
    var enabled: Boolean = true,
    var sortNumber: Int = 0,
) {

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is DictRule) {
            return name == other.name
        }
        return false
    }

}
