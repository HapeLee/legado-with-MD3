package io.legado.app.domain.rules

import io.legado.app.core.platform.systemTimeMillis

/**
 * 替换规则的**领域模型**（M3-1：`core:data` 按域拆分的第一个样板）。
 *
 * 与 `:core:data` 的 Room 实体 `io.legado.app.data.entities.ReplaceRule` 的关系：
 * - **形态逐字对齐**：字段集合、字段可变性（`var`）、默认值、`equals`/`hashCode` 语义
 *   （只按 `id` 判等）全部一致。映射见 `:data:rules` 的 `ReplaceRuleMapper`，
 *   并有 `ReplaceRuleMapperTest` 的逐字段往返用例守着，改一处就会红。
 * - 本类**不带任何 Room / Android 注解**，是 `:domain:rules`（pure KMP）里唯一能被共享层
 *   引用的规则类型。实体仍归 `:core:data`（`data:database`「Room 唯一 owner」落地前不搬），
 *   `:app` 里 ~13 处直接用实体/DAO 的路径（`ContentProcessor` / `Restore` / `TocViewModel` …）
 *   本片**不动**。
 *
 * ⚠️ 为什么字段是 `var` 而不是 `val`（看起来"不够领域"）：
 * 这些字段要经 `:core:platform` 的 `JsonCodec`（Gson）反序列化——导入替换规则、编辑页
 * 「从剪贴板粘贴」两条真实路径都走 `JsonCodec.fromJsonObject(text, ReplaceRule::class)`。
 * 实体是 `var`（非 final 字段），Gson 用反射直写字段；若领域模型改成 `val`（final 字段），
 * 在 JVM 17+ 上 Gson 直写 final 字段的行为与 Android ART 上并不一致，而**单元测试覆盖不到
 * 这条路径**（`:app` / Feature 的用例都不碰 JsonCodec 解 ReplaceRule）。
 * ⇒ 本片取「形态完全对齐、行为绝不变」，可变性留给后续里程碑在补上等价性用例后统一收。
 *
 * 未纳入本类的实体成员（留在实体上）：
 * - `regex`（`@Transient @Ignore` 的 lazy）：只有 `:app` 的 `ContentProcessor` 用，
 *   正文净化的求值路径本片不迁；
 * - `getDisplayNameGroup()`：本体是 `String.format`（JVM-only，进不了 pure commonMain），
 *   且全仓已无调用方。
 */
data class ReplaceRule(
    var id: Long = systemTimeMillis(),
    var name: String = "",
    var group: String? = null,
    var pattern: String = "",
    var replacement: String = "",
    var scope: String? = null,
    var scopeTitle: Boolean = false,
    var scopeContent: Boolean = true,
    var excludeScope: String? = null,
    var isEnabled: Boolean = true,
    var isRegex: Boolean = true,
    var timeoutMillisecond: Long = 3000L,
    var order: Int = Int.MIN_VALUE,
) {

    /**
     * 与实体逐字一致：**只按 `id` 判等**。
     *
     * 这不是省事——UI 层依赖它：`ReplaceRuleUiState.effectiveRules` 是
     * `ImmutableList<ReplaceRule>`，同 id 的规则即使字段有差异也判等，避免无谓重组。
     * 映射用例因此**不能**用 `assertEquals(domain, entity.toDomain())` 这种整对象比较
     * （两边都只比 id），必须逐字段断言。
     */
    override fun equals(other: Any?): Boolean {
        if (other is ReplaceRule) {
            return other.id == id
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /** 与实体 `isValid()` 逐字一致：导入数组时按它过滤非法条目（含正则保护）。 */
    fun isValid(): Boolean {
        if (pattern.isEmpty()) {
            return false
        }
        // 判断正则表达式是否正确
        if (isRegex) {
            try {
                Regex(pattern)
            } catch (ex: Exception) {
                return false
            }
            // Pattern.compile 测试通过，但是部分情况下会替换超时、报错，
            // 一般发生在修改表达式时漏删了。
            if (pattern.endsWith('|') && !pattern.endsWith("\\|")) {
                return false
            }
        }
        return true
    }

    /** 与实体 `getValidTimeoutMillisecond()` 逐字一致：非正超时回落 3000ms。 */
    fun getValidTimeoutMillisecond(): Long {
        if (timeoutMillisecond <= 0) {
            return 3000L
        }
        return timeoutMillisecond
    }
}
