package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 本书角色记忆那一表『主名｜别名/身份｜关系｜池』的读写口径：
 * 官方人物详情页存过的内容要能原样落回这一行，AI 下一趟才读得到用户改的那一份。
 */
class CastMemoryMirrorTest {

    @Test
    fun parsesEachFieldOfAMemoryLine() {
        val line = requireNotNull(
            CastMemoryMirror.parse("李星菲｜小菲、卧底｜男主的女儿｜女少女").singleOrNull()
        )

        assertEquals("李星菲", line.name)
        assertEquals(listOf("小菲", "卧底"), line.aliases)
        assertEquals("男主的女儿", line.relation)
        assertEquals("女少女", line.pool)
    }

    /** 池那一栏可以省略（AI 拿不准时就不给）；半角竖线也要认，第二栏是别名不是关系。 */
    @Test
    fun toleratesAMissingPoolAndHalfWidthSeparators() {
        val line = requireNotNull(CastMemoryMirror.parse("张三|小三|路人").singleOrNull())

        assertEquals("张三", line.name)
        assertEquals(listOf("小三"), line.aliases)
        assertEquals("路人", line.relation)
        assertEquals("", line.pool)
    }

    @Test
    fun rendersBackTheSameShapeItParsed() {
        val memory = "李星菲｜小菲、卧底｜男主的女儿｜女少女"

        assertEquals(memory, CastMemoryMirror.parse(memory).let { CastMemoryMirror.render(it.single()) })
    }

    /** 换行而不是加行：同一个人留两行，AI 下一次就会当成两个人。 */
    @Test
    fun replacesTheExistingLineInsteadOfAppendingASecondOne() {
        val memory = "李星菲｜小菲｜妹妹｜女少女\n李振富｜老李｜父亲｜男老年"

        val next = CastMemoryMirror.replaceLine(
            memory,
            CastMemoryMirror.Line("李星菲", listOf("小菲", "星菲"), "同父异母的妹妹", "女中年"),
        )

        assertEquals(
            listOf(
                "李星菲｜小菲、星菲｜同父异母的妹妹｜女中年",
                "李振富｜老李｜父亲｜男老年",
            ),
            next.lineSequence().toList(),
        )
    }

    /** 档案里新增的人物在记忆里还没有行：追加到末尾，下一趟分配才认得他。 */
    @Test
    fun appendsACharacterTheMemoryHasNotSeenYet() {
        val next = CastMemoryMirror.replaceLine(
            "李振富｜老李｜父亲｜男老年",
            CastMemoryMirror.Line("王芸", emptyList(), "邻居", "女中年"),
        )

        assertEquals(2, next.lines().size)
        assertEquals("王芸｜｜邻居｜女中年", next.lines().last())
    }

    @Test
    fun blankLinesAndJunkDoNotBecomeCharacters() {
        assertEquals(
            emptyList<CastMemoryMirror.Line>(),
            CastMemoryMirror.parse("\n   \n｜｜\n"),
        )
    }
}
