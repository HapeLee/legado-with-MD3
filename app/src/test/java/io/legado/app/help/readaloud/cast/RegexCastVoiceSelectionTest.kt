package io.legado.app.help.readaloud.cast

import io.legado.app.data.entities.RegexCastRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 正则角色的音色候选。
 *
 * 这份名单决定朗读要不要升级到文件合成（消费方 `ReadAloud.findCoordinatorHttpSeed`）：
 * 漏掉 HTTP/云端那条时系统直读会把规则选的音色过滤成默认音色，表现为「设了换音色不生效」。
 * 所以「只选了池」的规则必须把池内启用的每一条都算进来，不能只算随机取到的那一条。
 */
class RegexCastVoiceSelectionTest {

    private val pools = mapOf(
        "pool-mixed" to listOf("voice-system", "voice-http"),
        "pool-empty" to emptyList<String>(),
    )

    private fun rule(
        pattern: String = "她",
        itemId: String = "",
        poolId: String = "",
        poolKind: String = RegexCastRule.POOL_ROLE,
        groupId: String = "",
    ) = RegexCastRule(
        id = 1L,
        name = "规则",
        pattern = pattern,
        poolKind = poolKind,
        poolId = poolId,
        itemId = itemId,
        groupId = groupId,
    )

    private fun select(vararg rules: RegexCastRule) =
        RegexCastRuleStore.selectVoiceIds(rules.toList(), emptySet(), pools)

    @Test
    fun `rule with a concrete voice contributes that voice only`() {
        assertEquals(setOf("voice-http"), select(rule(itemId = "voice-http")))
    }

    @Test
    fun `rule that only picked a pool contributes every enabled member`() {
        assertEquals(
            setOf("voice-system", "voice-http"),
            select(rule(poolId = "pool-mixed")),
        )
    }

    @Test
    fun `sound-effect rules contribute no voice because those words are not spoken`() {
        assertEquals(emptySet<String>(), select(rule(poolId = "pool-mixed", poolKind = RegexCastRule.POOL_BGM)))
    }

    @Test
    fun `rules inside a disabled group are skipped`() {
        val disabled = RegexCastRuleStore.selectVoiceIds(
            listOf(rule(itemId = "voice-http", groupId = "g-off")),
            setOf("g-off"),
            pools,
        )
        assertEquals(emptySet<String>(), disabled)
    }

    @Test
    fun `blank pattern and empty pool leave nothing to switch to`() {
        assertEquals(emptySet<String>(), select(rule(pattern = "   ", itemId = "voice-http")))
        assertEquals(emptySet<String>(), select(rule(poolId = "pool-empty")))
    }

    @Test
    fun `literal mode keeps regex metacharacters as plain text`() {
        // 关掉「按正则匹配」后 `(` 只是普通字符，不能因为编不过被整条丢掉
        assertEquals(setOf("voice-http"), select(RegexCastRule(1L, "规则", "(", useRegex = false, itemId = "voice-http")))
    }
}
