package io.legado.app.domain.rules

import io.legado.app.core.platform.systemTimeMillis

/**
 * 标签分组规则的领域模型（M3-6）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.TagGroupRule` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **判等只按主键 [id]**：实体显式重写了 `equals` / `hashCode`
 * （`if (other is TagGroupRule) return id == other.id`），本类照抄（[equals] / [hashCode] 也一并
 * 显式写出，避免读者误以为"没重写 = 全字段判等"）。这与 M3-5 的 [RuleSub] **恰好相反**
 * ——那个实体是全仓六个规则实体里唯一没重写 `equals` 的，判等是全字段比较。**不要向上一片
 * 看齐**：改了这里，`TagGroupRuleViewModel` 的 `TagGroupRuleItemUi` 选择集
 * （`Set<Long>` 与 `it.rule` 混用）与拖拽排序的 key 语义都会跟着变。
 *
 * 连带后果（写在映射测试里当护栏）：因为是 id-only 判等，映射用例**不能**用整对象
 * `assertEquals(实体, 领域.toEntity())` 代替逐字段断言——漏映射任何字段，只要 `id` 对得上
 * 就照样通过。`TagGroupRuleMapperTest` 因此逐字段写。
 *
 * ⚠️ [id] 与其余字段一样是 **`var`**（与 [RuleSub] 的 `val id` 相反）。本模型要经
 * `:core:platform` 的 `JsonCodec`(Gson) 反序列化：Feature 的「粘贴规则」
 * （`TagGroupRuleViewModel.pasteRule` 的 `fromJsonObject(text, TagGroupRule::class)`）与
 * 「导入」（`TagGroupRuleTransferSpec.parseImportRules` 的 `decodeList` / `fromJsonObject`）
 * 都直接落到本类，所以字段必须以 `var` 暴露——`final` 字段在 JVM 反射写入与 ART 上的行为
 * 不一致，且**无单测覆盖**。全字段 `var` 同时是「Gson 序列化输出与实体逐字节一致」的前提
 * （`TagRulesImportExportCharacterizationTest` 有字节级断言）。
 *
 * ⚠️ [id] 的默认值取 `systemTimeMillis()`，与实体一致：`TagGroupRuleEditSheet` 用
 * `TagGroupRule()` 造新规则、再用 `rule.id == 0L` 判"是不是新建"。默认值一旦漂移，新建规则
 * 的 id 生成规则就静默改变。`domain/rules` 从 M3-1 起就
 * `implementation(":core:platform")`（当时为 `ReplaceRule.id` 的同类默认值），引用它不越界
 * ——G2 只拦 `android.*` / `java.io.*` / `kotlin.jvm.*` / 三方实现库。
 *
 * ⚠️ [order] 是字段名也是 SQL 关键字：DAO 侧写作 `` ORDER BY `order` ``（反引号），改名会
 * 同时打断 Room 查询与备份 JSON 的键名，属持久化契约，**不得重命名**。
 *
 * ⚠️ 字段声明顺序也与实体一致（`id` / `pattern` / `groupName` / `order`）：Gson 按声明顺序
 * 输出键，顺序漂移会让「`JsonCodec.toJson` 与 `GSON.toJson` 逐字节一致」的断言变红。
 */
data class TagGroupRule(
    var id: Long = systemTimeMillis(),
    var pattern: String = "",
    var groupName: String = "",
    var order: Int = 0,
) {

    override fun hashCode(): Int = id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is TagGroupRule) return id == other.id
        return false
    }

}
