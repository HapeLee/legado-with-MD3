package io.legado.app.feature.reader.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色名的可写字集：它同时是「能不能存」与「标记语法会不会被名字撑坏」的边界。
 * 两套语法（正文标记 / 本书记忆的一行）必须共用同一份禁列，否则名字能存进记忆却撑坏解析。
 */
class CastSyntaxTest {

    private val syntax = CastSyntax.DEFAULT

    /** AI 自己就产出音译名，`·` 进不了名字等于这些角色永远建不成配音行。 */
    @Test
    fun allowsTheMiddleDotUsedByTransliteratedNames() {
        assertTrue(syntax.isValidName("弗朗茨·罗库斯"))
        assertTrue(syntax.isValidName("伊安·罗德布雷克"))
    }

    /** 记忆一行的字段与别名分隔符（CastMemoryMirror 就按它们切），任何一套都不能进名字。 */
    @Test
    fun rejectsEveryMemorySeparator() {
        assertFalse(syntax.isValidName("甲｜乙"))
        assertFalse(syntax.isValidName("甲|乙"))
        assertFalse(syntax.isValidName("甲，乙"))
        assertFalse(syntax.isValidName("甲、乙"))
        assertFalse(syntax.isValidName("甲.乙"))
    }

    /** 标记的包裹符号与池括号也不能进名字，否则解析会认到别人身上。 */
    @Test
    fun rejectsTheMarkerDelimiters() {
        assertFalse(syntax.isValidName("甲>乙"))
        assertFalse(syntax.isValidName("甲（乙"))
        assertFalse(syntax.isValidName("“甲”"))
        assertFalse(syntax.isValidName(""))
    }

    /** 生成侧一直是括号形式；名字里带 `·` 也要能原样往返。 */
    @Test
    fun roundTripsAMarkerWhoseNameContainsTheMiddleDot() {
        val text = syntax.markerText("弗朗茨·罗库斯", "男中年")

        assertEquals("<<弗朗茨·罗库斯（男中年）>>", text)
        assertEquals("弗朗茨·罗库斯" to "男中年", text?.let { syntax.parse(it) })
    }

    /** 没有池时只输出名字，解析回来池为空串而不是 null。 */
    @Test
    fun parsesAMarkerWithoutAPool() {
        assertEquals("李雷班" to "", syntax.parse("<<李雷班>>"))
        assertNull(syntax.parse("没有标记的正文"))
    }
}
