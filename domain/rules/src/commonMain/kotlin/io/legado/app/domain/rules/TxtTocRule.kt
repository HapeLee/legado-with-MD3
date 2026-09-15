package io.legado.app.domain.rules

import io.legado.app.core.platform.systemTimeMillis

/**
 * TXT 目录规则的领域模型（M3-4）。
 *
 * 字段集合、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.TxtTocRule` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ 与前四片一致，字段用 `var` 而非 `val`：这些字段要经 `:core:platform` 的
 * [io.legado.app.core.platform.JsonCodec]（Gson）反射直写。本片至少有四条真实路径走它——
 * `feature:txttocrules` 的 `TxtTocRuleTransferSpec.generateJson` / `pasteRule`，以及
 * `:app` 侧 `TxtTocRulePreviewViewModel` 的编辑回填。改成 `val`（final）后 JVM 17+ 的
 * 反射写入行为与 Android ART 不一致，而这两条路径都没有单测覆盖。
 *
 * ⚠️ 默认值里有三处**肉眼容易看错**，都与实体逐字对齐、不要"顺手改成更合理的值"：
 * - [serialNumber] 的默认值是 **`-1`**（不是 `0`）——迁移前的 DAO 用
 *   `select ifNull(min(serialNumber), 0)` 表达"没有规则"，`-1` 是"尚未排序"的既有标记；
 * - [enable] 字段名是 **`enable`** 而不是 `enabled`（`HighlightTagRule` 那边叫 `enabled`）；
 * - [example] 是**可空** `String?` 且默认为 `null`（不是空串），UI 侧用 `?: ""` 回填。
 *
 * ⚠️ 判等只看主键 [id]。所以映射用例必须**逐字段断言**，任何"整对象比较"式的测试都发现
 * 不了漏映射的字段（见 `TxtTocRuleMapperTest`）。
 *
 * ⚠️ [chapterRule] 在旧版本备份里的键名是 `rule`。该兼容**不在本类**：它由
 * `:core:data/androidMain` 的 `txtTocRuleJsonDeserializer` 在解析实体时做键名提升，
 * 平台契约 `io.legado.app.feature.txttocrules.TxtTocRuleImportCompat` 是唯一入口。
 * 换到本类来做会漏掉备份恢复（`Restore.kt` 按**实体**反序列化）这条路径。
 */
data class TxtTocRule(
    var id: Long = systemTimeMillis(),
    var name: String = "",
    var chapterRule: String = "",
    var volumeRule: String = "",
    var example: String? = null,
    var serialNumber: Int = -1,
    var enable: Boolean = true,
) {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is TxtTocRule) {
            return id == other.id
        }
        return false
    }

}
