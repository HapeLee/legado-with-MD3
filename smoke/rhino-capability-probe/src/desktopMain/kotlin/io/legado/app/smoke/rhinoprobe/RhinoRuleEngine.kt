package io.legado.app.smoke.rhinoprobe

import org.mozilla.javascript.Context

/**
 * Rhino 实现：委托 `org.mozilla.javascript.Context`。
 * androidMain 与 desktopMain 都是 JVM，Rhino 是纯 JVM 库，故实现相同。
 * 样本（shutiao/legado）用 QuickJS + KSP 分派表；本仓库不换引擎（AGENTS.md：Rhino 书源规则
 * 高行为风险，换 QuickJS 单独立项含兼容测试，不进本规划关键路径）。
 */
class RhinoRuleEngine : RuleEngine {
    override fun eval(script: String, bindings: Map<String, Any?>): Any? {
        val ctx = Context.enter()
        return try {
            val scope = ctx.initStandardObjects()
            bindings.forEach { (key, value) -> scope.put(key, scope, value) }
            ctx.evaluateString(scope, script, "rule", 1, null)
        } finally {
            Context.exit()
        }
    }
}
