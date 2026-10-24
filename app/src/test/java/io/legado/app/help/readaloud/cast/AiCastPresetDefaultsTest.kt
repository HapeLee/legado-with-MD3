package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 出厂判断规则与输出契约的自述性。
 *
 * 模型能判对「久未出场的旧识」靠的是 characters 里的 last 与 contextAfter 这两路证据；
 * 提示词一旦被重排、合行或改写把它们说丢了，分配就会退回凭空造称谓角色（「客人」）。
 * 送出去的字段改了却不改这两段话，等于没改。
 */
class AiCastPresetDefaultsTest {

    private val rules = AiCastPresetStore.DEFAULT_JUDGMENT_RULES
    private val contract = AiCastPresetStore.DEFAULT_CONTRACT

    @Test
    fun `规则教模型用出场间隔与下一章开头认人`() {
        assertTrue(rules, rules.contains("用 last 认旧人"))
        assertTrue(rules, rules.contains("contextAfter"))
        assertTrue(rules, rules.contains("不许新建一个称谓角色去顶替具名角色"))
    }

    @Test
    fun `契约把 last 与 contextAfter 的口径写进输入结构`() {
        assertTrue(contract, contract.contains("name+pool+aliases+last"))
        assertTrue(contract, contract.contains("距上次说话过了几章"))
        assertTrue(contract, contract.contains("contextAfter 是下一章开头"))
    }

    @Test
    fun `十条判断规则各自独占一行`() {
        val lines = rules.split("\n")
        for (number in 1..10) {
            assertTrue(
                "第 $number 条没有行首起句：\n$rules",
                lines.any { it.startsWith("($number)") },
            )
        }
    }
}
