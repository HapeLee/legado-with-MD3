package io.legado.app.feature.txttocrules

import io.legado.app.data.entities.TxtTocRule

/**
 * TXT 目录规则的**平台侧反序列化契约**（M1-3y）。
 *
 * 存在的唯一原因：`TxtTocRule.chapterRule` 需要兼容旧版本备份里 `rule` 这个键名。该兼容
 * 原先由 app 侧 `GSON` 门面注册的 `txtTocRuleJsonDeserializer` 完成（`@SerializedName(
 * alternate = ["rule"])` 无法进 commonMain，故下沉实体时改成了 deserializer）。
 *
 * 共享层的 [io.legado.app.core.platform.JsonCodec] **不含**这个 deserializer（它的 Android
 * 实现明确只对齐 `INITIAL_GSON`，未注册任何 rule 类型的自定义反序列化器），所以标准 JSON 的
 * 解析在这里也必须走平台实现——这与 `replacerules` 的
 * [io.legado.app.feature.replacerules.ReplaceRuleImportCompat] 不同：那里的标准格式能在共享层
 * 直接解，只有旧 jsonpath 格式才回落平台；这里**两种格式都需要**这个键名提升。
 *
 * 失败语义：解析不了必须**抛异常**（调用方会转成导入状态 `Error`），不要返回空列表或 null
 * 来静默吞掉坏数据。
 *
 * Android 实现 `AndroidTxtTocRuleImportCompat` 住 `:app` 的 `domain/gateway`，委托
 * `io.legado.app.utils.GSON`（含 deserializer），由 Koin 注入。
 */
interface TxtTocRuleImportCompat {

    /** 解析 JSON 数组为规则列表；失败抛异常。 */
    fun parseRules(json: String): List<TxtTocRule>

    /** 解析单个 JSON 对象为规则；失败抛异常。 */
    fun parseRule(json: String): TxtTocRule
}
