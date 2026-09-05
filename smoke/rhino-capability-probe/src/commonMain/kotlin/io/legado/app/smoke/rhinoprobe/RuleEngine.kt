package io.legado.app.smoke.rhinoprobe

/**
 * D4 PoC：JS 规则引擎 capability 契约。共享层只见 `String` 脚本与 `Map` 绑定，
 * 不暴露 Rhino/QuickJS 类型。actual（Rhino）在 androidMain/desktopMain。
 *
 * 返回 [Any?]：PoC 阶段保持最小；真实 RuleEngine capability 会用领域返回类型
 * （布尔判定 / 字符串提取 / 数值），在 P1 正式立契约时按真实消费方收敛。
 */
interface RuleEngine {
    fun eval(script: String, bindings: Map<String, Any?> = emptyMap()): Any?
}
