package io.legado.app.domain.rules

import io.legado.app.core.platform.systemTimeMillis

/**
 * 规则订阅的类型常量（M3-5）。
 *
 * ⚠️ **值即 Room 存储值**：`RuleSub.type` 以 `Int` 落库，本对象的四个 const 与其一一对应
 * （UI 侧 `stringArrayResource(R.array.rule_type)` 的下标也正是这四个值）。数值属持久化
 * 契约，**不得重排或改值**。
 *
 * ⚠️ 本对象与 `:core:data` 的 `io.legado.app.data.entities.RuleSubType` 是**语义镜像**，
 * 两份必须同步：实体侧那份被实体自身的 `type` 默认值引用，且 `:core:data` 不能反向依赖
 * `:domain:rules`（依赖方向是 data → domain），所以无法只留一份。两边的相等性由
 * `RuleSubMapperTest` 的用例逐条钉住。
 */
object RuleSubType {
    /** 书源。 */
    const val BOOK_SOURCE = 0

    /** 订阅源（RSS）。 */
    const val RSS_SOURCE = 1

    /** 替换规则。 */
    const val REPLACE_RULE = 2

    /** 自动识别。 */
    const val AUTO = 3
}

/**
 * 规则订阅的领域模型（M3-5）。
 *
 * 字段集合、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.RuleSub` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **本模型是全仓六个规则实体里唯一没有重写 `equals` / `hashCode` 的一个**，
 * 也就是说判等是 data class 的**全字段**比较，而不是其余五片（`ReplaceRule` /
 * `HighlightTagRule` / `DictRule` / `TxtTocRule` / `TagGroupRule`）那套「只按主键判等」。
 * 这不是遗漏：`RuleSub` 的实体本来就没重写，本类照抄该语义。**不要"对齐前四片"补一个
 * id-only 的 `equals`**——那会让领域模型与实体的判等分叉，而 `RuleSubMapperTest` 有一条
 * 用例专门钉住「仅 `name` 不同的两条规则不相等」。
 * （连带影响：映射测试在这里**可以**用整对象 `assertEquals`，前四片则不行。）
 *
 * ⚠️ [id] 是 **`val`**（其余字段是 `var`），同样照抄实体。本模型不走
 * `:core:platform` 的 `JsonCodec` 反射直写——`RuleSub` 没有导入/粘贴进领域模型的路径
 * （`Restore.kt` 按**实体**反序列化 `sourceSub.json`），所以 `val` 不会踩到 M3-4 那个
 * 「final 字段在 JVM 反射下与 ART 行为不一致」的坑。
 *
 * ⚠️ [update] 是"最后更新时间"的时间戳，与 [id] 的生成方式相同（都是
 * `systemTimeMillis()`）。新建订阅（`:app` 的 `RuleSubIntent.Add` → `RuleSub(customOrder = …)`）
 * 靠这两个默认值取当前毫秒，所以默认值必须与实体逐字一致。
 */
data class RuleSub(
    val id: Long = systemTimeMillis(),
    var name: String = "",
    var url: String = "",
    var type: Int = RuleSubType.BOOK_SOURCE,
    var customOrder: Int = 0,
    var autoUpdate: Boolean = false,
    var update: Long = systemTimeMillis(),
)
