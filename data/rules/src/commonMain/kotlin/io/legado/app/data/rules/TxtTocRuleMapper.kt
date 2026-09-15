package io.legado.app.data.rules

import io.legado.app.data.entities.TxtTocRule as TxtTocRuleEntity
import io.legado.app.domain.rules.TxtTocRule

/**
 * Room 实体 `io.legado.app.data.entities.TxtTocRule` ↔ 领域模型
 * `io.legado.app.domain.rules.TxtTocRule` 的双向映射（M3-4）。
 *
 * 位置：映射器住 `:data:rules`（M3 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 * 公开可见是因为 `:app` 这个 composition root 也要用：
 * - `AndroidTxtTocRuleImportCompat`：`io.legado.app.utils.parseTxtTocRules` 返回的是实体
 *   （旧键名 `rule` → `chapterRule` 的键名提升由 `:core:data/androidMain` 的
 *   `txtTocRuleJsonDeserializer` 在实体上完成），要在边界上转成领域模型；
 * - `ui/association/ImportTxtTocRuleViewModel.kt`：老导入路径（URL / URI / 纯文本）同一个
 *   理由，且这条路径**只**能做实体级解析——`JsonCodec` 不含那个键名提升；
 * - `ui/book/toc/rule/preview/TxtTocRulePreviewViewModel.kt`：平台岛（`LocalBook`）里
 *   `DefaultData.txtTocRules`（实体）落到端口前要转领域模型。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退——映射必须是恒等的：
 * 两边 `equals`/`hashCode` 都只按 `id` 判等（见领域模型注释），所以任何"整对象比较"式的
 * 测试都发现不了漏映射的字段。`TxtTocRuleMapperTest` 因此逐字段断言。
 *
 * ⚠️ [TxtTocRule.example] 可空：**不要**把 `null` 归一成 `""`。迁移前 `example` 默认为
 * `null`、UI 用 `?: ""` 只在展示层兜底，备份文件里两种写法（缺键 vs 空串）保留原样。
 */
fun TxtTocRuleEntity.toDomain(): TxtTocRule = TxtTocRule(
    id = id,
    name = name,
    chapterRule = chapterRule,
    volumeRule = volumeRule,
    example = example,
    serialNumber = serialNumber,
    enable = enable,
)

fun TxtTocRule.toEntity(): TxtTocRuleEntity = TxtTocRuleEntity(
    id = id,
    name = name,
    chapterRule = chapterRule,
    volumeRule = volumeRule,
    example = example,
    serialNumber = serialNumber,
    enable = enable,
)
