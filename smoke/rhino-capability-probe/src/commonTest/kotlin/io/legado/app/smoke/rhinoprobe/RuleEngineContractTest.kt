package io.legado.app.smoke.rhinoprobe

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RuleEngine 契约测试基类：每个 target（androidHostTest / desktopTest）提供 [createEngine]。
 * 证明 RhinoRuleEngine 在该 target 满足契约（算术 / 绑定 / 字符串）。
 */
abstract class RuleEngineContractTest {

    abstract fun createEngine(): RuleEngine

    @Test
    fun `evaluates arithmetic`() {
        val result = createEngine().eval("1 + 2")
        assertEquals(3.0, (result as Number).toDouble())
    }

    @Test
    fun `evaluates with bindings`() {
        val result = createEngine().eval("a * 2", mapOf("a" to 21))
        assertEquals(42.0, (result as Number).toDouble())
    }

    @Test
    fun `evaluates string concatenation`() {
        assertEquals("hello", createEngine().eval("'hel' + 'lo'"))
    }
}
