package io.legado.app.help.readaloud.cast

import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.CastCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AI 给的称呼归并到本书已有角色主名（[canonicalCastName]）。
 *
 * 归并不到位时「重新分配」会把同一个人拆成两条 cast_characters：新那条没有气泡，
 * 而 [CastProfileMirror.ensure] 按名字找档案又会顺着 aliasesJson 命中已有那条并改名，
 * 于是档案与角色行错配——正文表现为气泡没了、头像还在、配音列表却不跟着变。
 */
class AiCastIdentityTest {

    private fun character(id: String, name: String, bubble: String = "") = CastCharacter(
        id = id,
        bookUrl = BOOK,
        name = name,
        poolLabel = "女青年",
        bubbleRuleJson = bubble,
    )

    private fun profile(
        id: String,
        name: String,
        aliases: List<String> = emptyList(),
        status: Int = BookCharacterProfile.STATUS_ACTIVE,
    ) = BookCharacterProfile(
        id = id,
        bookUrl = BOOK,
        name = name,
        aliasesJson = "[${aliases.joinToString(",") { "\"$it\"" }}]",
        status = status,
    )

    @Test
    fun `a known main name resolves to itself`() {
        val characters = listOf(character("c1", "李小花"))
        assertEquals("李小花", canonicalCastName("李小花", characters, emptyList()))
    }

    @Test
    fun `an alias in the profile resolves back to the existing character's main name`() {
        val characters = listOf(character("c1", "李小花", bubble = "{}"))
        val profiles = listOf(profile("c1", "李小花", aliases = listOf("小花", "小丫头")))
        assertEquals("李小花", canonicalCastName("小花", characters, profiles))
        assertEquals("李小花", canonicalCastName("小丫头", characters, profiles))
    }

    @Test
    fun `alias resolves through the profile main name when ids diverge`() {
        // 老数据：档案 id 与角色行 id 可能不同（档案镜像上线前建的角色）
        val characters = listOf(character("legacy-id", "李小花"))
        val profiles = listOf(profile("profile-id", "李小花", aliases = listOf("小花")))
        assertEquals("李小花", canonicalCastName("小花", characters, profiles))
    }

    @Test
    fun `a disabled profile does not resurrect a deleted character`() {
        val characters = listOf(character("c1", "李小花"))
        val profiles = listOf(
            profile("c9", "李小花", aliases = listOf("小花"),
                status = BookCharacterProfile.STATUS_DISABLED),
        )
        assertNull(canonicalCastName("小花", characters, profiles))
    }

    @Test
    fun `an orphan row sharing the alias yields to the profile's real character`() {
        // 归并上线前跑过分配的书留下的坏数据：孤儿角色行顶着别名、没有档案、也没有气泡，
        // 带气泡的那一条才是档案指向的人。按名字优先会永远命中孤儿，重新分配也修不回来。
        val characters = listOf(
            character("orphan", "小花"),
            character("c1", "李小花", bubble = """{"bgColor":"#123456"}"""),
        )
        val profiles = listOf(profile("c1", "小花", aliases = listOf("李小花")))
        // 两种称呼都归到带气泡的那一条，孤儿行就此不再被引用
        assertEquals("李小花", canonicalCastName("小花", characters, profiles))
        assertEquals("李小花", canonicalCastName("李小花", characters, profiles))
    }

    @Test
    fun `a name with no profile at all still resolves by exact match`() {
        // 只有角色行、档案还没建（backfillAll 之前）：不能因为查不到档案就说他是新人物
        val characters = listOf(character("c1", "李小花"))
        assertEquals("李小花", canonicalCastName("李小花", characters, emptyList()))
    }

    @Test
    fun `a genuinely new person stays new`() {
        val characters = listOf(character("c1", "李小花"))
        val profiles = listOf(profile("c1", "李小花", aliases = listOf("小花")))
        assertNull(canonicalCastName("隔壁老王", characters, profiles))
    }

    private companion object {
        const val BOOK = "file:///book"
    }
}
