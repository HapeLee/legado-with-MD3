package io.legado.app.domain.gateway

import io.legado.app.data.rules.toDomain
import io.legado.app.domain.rules.TxtTocRule
import io.legado.app.feature.txttocrules.TxtTocRuleImportCompat
import io.legado.app.utils.parseTxtTocRule
import io.legado.app.utils.parseTxtTocRules

/**
 * [TxtTocRuleImportCompat] 的 Android 实现。
 *
 * 实现体就是迁移前 `TxtTocRuleViewModel.parseImportRules` 的两条分支**原样搬过来**的：
 * 迁移前该 VM 直接调 `GSON.fromJsonArray<TxtTocRule>` / `GSON.fromJsonObject<TxtTocRule>`
 * 再 `getOrThrow()`，这里保持一致（`RuleTransferUseCase` 靠异常把导入状态置成 `Error`）。
 *
 * 为什么本文件必须留在 `:app`：解析要走 `io.legado.app.utils.GSON` 门面——只有它注册了
 * **实体** `io.legado.app.data.entities.TxtTocRule` 的旧键名兼容 deserializer
 * （`rule` → `chapterRule`）。真正的解析实现放在 `:core:data` 的 `utils/GsonExtensions.kt`
 * （与门面同包，见那里的注释）：本文件只做契约转接，自己**不** import `GSON`，所以不会给
 * G4 的 `gson` 棘轮新增计分点。
 *
 * ⚠️ **M3-4 起本类多做一步 `toDomain()`**：契约的返回类型从 Room 实体换成了领域模型
 * （`io.legado.app.domain.rules.TxtTocRule`），而键名提升只能发生在实体上——`parseTxtTocRules`
 * / `parseTxtTocRule` 就是按实体解析的（deserializer 注册在实体类型上，`JsonCodec` 不含它）。
 * 这一步映射是**恒等的**（见 `TxtTocRuleMapper`），不改任何字段语义；不做它会让领域模型上的
 * `@SerializedName` 缺失被静默吞掉（旧备份里的 `rule` 键会丢）。
 *
 * 住 `domain.gateway`（与 `AndroidReplaceRuleImportCompat` 同处）：「契约的 Android 实现」
 * 的落点，该区域没有 legacy 计分。
 */
class AndroidTxtTocRuleImportCompat : TxtTocRuleImportCompat {

    override fun parseRules(json: String): List<TxtTocRule> =
        parseTxtTocRules(json).map { it.toDomain() }

    override fun parseRule(json: String): TxtTocRule =
        parseTxtTocRule(json).toDomain()
}
