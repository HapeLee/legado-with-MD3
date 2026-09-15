package io.legado.app.domain.rules

import io.legado.app.core.platform.systemTimeMillis

/**
 * 高亮标签规则的**领域模型**（M3-2：`core:data` 按域拆分的第二片，样板见 M3-1 的 [ReplaceRule]）。
 *
 * 与 `:core:data` 的 Room 实体 `io.legado.app.data.entities.HighlightTagRule` 的关系：
 * - **形态逐字对齐**：字段集合、字段可变性（`var`）、默认值、`equals`/`hashCode` 语义
 *   （只按 `id` 判等）全部一致。映射见 `:data:rules` 的 `HighlightTagRuleMapper`，
 *   并有 `HighlightTagRuleMapperTest` 的逐字段往返用例守着，改一处就会红。
 * - 本类**不带任何 Room / Android 注解**，是 `:domain:rules` 里可被共享层引用的规则类型。
 *   实体仍归 `:core:data`（`data:database`「Room 唯一 owner」落地前不搬）。
 *
 * ⚠️ 为什么字段是 `var` 而不是 `val`：与 [ReplaceRule] 同理 —— 这些字段要经
 * `:core:platform` 的 `JsonCodec`（Gson）反序列化，本片里至少四条真实路径走它：
 * 导入规则文本、导出后的剪贴板粘贴（`HighlightTagRuleViewModel.pasteRule`）、
 * 单对象导入、以及 `HighlightTagRuleEditSheet` 的「粘贴」按钮。
 * 实体的字段是非 final 的，Gson 用反射直写；改成 `val`（final）后 JVM 17+ 上的反射写入
 * 行为与 Android ART 不一致，而 `:app` 侧没有单测覆盖这条路径。
 * ⇒ 取「形态完全对齐、行为绝不变」，可变性留待补上等价性用例后统一收。
 *
 * 本类**不带实体上的业务方法**：实体的形态就是纯数据 + 判等覆写，没有 `isValid()` 之类的成员
 * （与 [ReplaceRule] 不同），因此这里没有需要同步搬过来的行为。
 */
data class HighlightTagRule(
    var id: Long = systemTimeMillis(),
    var title: String = "",
    var pattern: String = "",
    var enabled: Boolean = true,
    var order: Int = 0,
) {

    /**
     * 与实体逐字一致：**只按 `id` 判等**。
     *
     * 这不是省事——`HighlightTagRuleItemUi.rule` 与列表的 `key` 都依赖它；同一 id 的规则
     * 即使字段有差异也判等，避免无谓重组。映射用例因此**不能**用整对象比较
     * （两边都只比 id），必须逐字段断言。
     */
    override fun equals(other: Any?): Boolean {
        if (other is HighlightTagRule) return id == other.id
        return false
    }

    override fun hashCode(): Int = id.hashCode()
}
