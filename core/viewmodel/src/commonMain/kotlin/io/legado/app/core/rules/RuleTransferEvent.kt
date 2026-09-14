package io.legado.app.core.rules

/**
 * 规则导入/导出/上传流程发给 UI 的事件。
 *
 * 与 `io.legado.app.base.BaseRuleEvent` 形状相同，但住在 `io.legado.app.core.rules`。
 * 原因不是命名偏好：`RuleTransferUseCase` 发事件，而任何 `import io.legado.app.base.**`
 * 都会被 `checkLegacyArchitecture` 的 `legacyBase` 棘轮拦下（新区域必须为零，既有区域只降不升）。
 * 规则导入导出既然要成为可被 Feature 复用的共享能力，它的事件类型就不能挂在待退役的 `base` 包下。
 *
 * `BaseRuleEvent` 目前仍是 `BaseRuleViewModel` 及其余三个规则页在用的类型；等那条基类链
 * 随 M2 退役后，两者合并为一个。
 */
sealed interface RuleTransferEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val url: String? = null
    ) : RuleTransferEvent
}
