package io.legado.app.domain.gateway

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.feature.replacerules.ReplaceRuleImportCompat
import io.legado.app.data.rules.ReplaceAnalyzer

/**
 * [ReplaceRuleImportCompat] 的 Android 实现。
 *
 * 实现体就是迁移前 `ReplaceRuleViewModel.parseImportRules` 的两条分支**原样搬过来**的：
 * 迁移前该 VM 直接调 `ReplaceAnalyzer.jsonToReplaceRules/jsonToReplaceRule` 再 `getOrThrow()`，
 * 这里保持一致（`RuleTransferUseCase` 靠异常把导入状态置成 `Error`）。
 *
 * 为什么本文件必须留在 `:app`：`ReplaceAnalyzer` 依赖 `com.jayway.jsonpath`（JVM 三方库），
 * 只能在平台侧，见 `core/data/build.gradle.kts` 的注解。共享层（`commonMain`）已经覆盖了
 * 标准格式的解析（`JsonCodec`），只有旧格式 / 混合数组才会走到这里。
 *
 * 住 `domain.gateway` 而不是 `base.rules` 的原因是 G4 棘轮：`io.legado.app.base.**` 是
 * `legacyBase` 盯的命名空间，`:app` 的 composition root（`io.legado.app.di`）已经为
 * `AndroidRuleTransferPlatform` / `AndroidBuiltInRulesImporter` 记着 2 处基线，
 * 第三个 import 就会越界。`domain.gateway` 区域没有 legacy 计分，且这里本就是
 * 「契约的 Android 实现」的落点（`AndroidReplaceRuleSettingsGateway` 等同住）。
 */
class AndroidReplaceRuleImportCompat : ReplaceRuleImportCompat {

    override fun parseRules(json: String): List<ReplaceRule> =
        ReplaceAnalyzer.jsonToReplaceRules(json).getOrThrow()

    override fun parseRule(json: String): ReplaceRule =
        ReplaceAnalyzer.jsonToReplaceRule(json).getOrThrow()
}
