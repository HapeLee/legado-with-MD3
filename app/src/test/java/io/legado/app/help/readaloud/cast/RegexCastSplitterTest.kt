package io.legado.app.help.readaloud.cast

import io.legado.app.feature.reader.core.cast.CastMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正则角色的切分。
 *
 * 朗读单元是按章内坐标排的（队列要求区间不重叠），所以这里最要紧的三件事：
 * 命中换音色的那块要能单独成块、命中放音效的那块文字必须整个消失且**不留空格**、
 * 切完的坐标必须接着往下数而不是重排。
 */
class RegexCastSplitterTest {

    private fun voice(id: String, vararg patterns: String) =
        patterns.map { RegexCastEffect("v-$id", Regex(it), voiceId = id) }.toList()

    private fun sound(path: String, vararg patterns: String) =
        patterns.map { RegexCastEffect("s-$path", Regex(it), soundPath = path) }.toList()

    private fun split(raw: String, effects: List<RegexCastEffect>, base: Int = 0) =
        RegexCastSplitter.split(base, raw, CastMarkers.blank(raw), effects)

    @Test
    fun `no effects leaves the unit untouched`() {
        val result = split("前面爆炸后面", emptyList())
        assertEquals(1, result.parts.size)
        assertEquals("前面爆炸后面", result.parts[0].text)
        assertEquals(null, result.parts[0].voiceId)
        assertEquals("", result.trailingSound)
    }

    @Test
    fun `matched words become their own unit with that voice`() {
        val result = split("前面爆炸后面", voice("v1", "爆炸"))
        assertEquals(listOf("前面", "爆炸", "后面"), result.parts.map { it.text })
        assertEquals(listOf(0, 2, 4), result.parts.map { it.start })
        assertEquals(listOf(null, "v1", null), result.parts.map { it.voiceId })
    }

    @Test
    fun `sound match drops the words and queues the audio on the next unit`() {
        val result = split("前面爆炸后面", sound("/sdcard/boom.mp3", "爆炸"))
        // 「爆炸」整块消失，不留空格
        assertEquals(listOf("前面", "后面"), result.parts.map { it.text })
        // 坐标跳过被吃掉的那两个字：区间只留缝，不重叠
        assertEquals(listOf(0, 4), result.parts.map { it.start })
        assertEquals("/sdcard/boom.mp3", result.parts[1].sound)
        assertEquals("", result.parts[0].sound)
    }

    @Test
    fun `sound at the end of the unit rides on the previous unit`() {
        val result = split("前面爆炸", sound("/boom", "爆炸"))
        assertEquals(listOf("前面"), result.parts.map { it.text })
        assertEquals("/boom", result.parts[0].sound)
    }

    @Test
    fun `a unit that is nothing but a sound hands it back as trailing`() {
        val result = split("爆炸", sound("/boom", "爆炸"))
        assertTrue(result.parts.isEmpty())
        assertEquals("/boom", result.trailingSound)
    }

    @Test
    fun `cast markers are never matched inside`() {
        val raw = "<<苏晚（女青）>>爆炸了"
        val result = split(raw, voice("v1", "晚爆"))
        // 标记里的「晚」和外面的「爆」不能被拼成一次命中
        assertEquals(listOf(raw), result.parts.map { it.text })
    }

    @Test
    fun `repeated and regex patterns both match`() {
        val result = split("爆炸，爆炸，又爆炸", voice("v1", "爆炸"))
        assertEquals(5, result.parts.size)
        assertEquals(listOf("，", "，又"), result.parts.filter { it.voiceId == null }.map { it.text })
        val alternation = split("有雷声和爆炸", voice("v1", "雷声|爆炸"))
        assertEquals(listOf("有", "雷声", "和", "爆炸"), alternation.parts.map { it.text })
    }

    @Test
    fun `earlier rule wins where two rules overlap`() {
        val effects = voice("first", "爆炸") + sound("/boom", "爆炸了")
        val result = split("爆炸了", effects)
        assertEquals(listOf("first"), result.parts.mapNotNull { it.voiceId })
    }

    @Test
    fun `several sounds on one unit keep both`() {
        val effects = sound("/a", "爆") + sound("/b", "炸")
        val result = split("爆炸", effects)
        assertTrue(result.parts.isEmpty())
        assertEquals("/a\n/b", result.trailingSound)
    }

    @Test
    fun `empty match patterns do not loop forever`() {
        val result = split("abc", voice("v1", "x*"))
        assertEquals(listOf("abc"), result.parts.map { it.text })
    }

    @Test
    fun `uncompilable pattern falls back to literal text`() {
        // 用户填的是普通文本，少一个右括号不是合法正则 → 整串按字面量匹配，而不是整条规则作废
        val broken = "(山体崩碎音效"
        val regex = RegexCastRuleStore.compile(broken) ?: error("compile returned null")
        assertTrue(regex.matches(broken))
        assertEquals(1, regex.findAll("前${broken}后").count())
    }

    @Test
    fun `plain text and real regex both compile`() {
        assertEquals(1, RegexCastRuleStore.compile("爆炸")!!.findAll("前爆炸后").count())
        assertEquals(2, RegexCastRuleStore.compile("爆炸|雷声")!!.findAll("爆炸和雷声").count())
    }

    @Test
    fun `blank pattern has no effect`() {
        assertEquals(null, RegexCastRuleStore.compile(""))
        assertEquals(null, RegexCastRuleStore.compile("   "))
    }
}
