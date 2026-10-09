package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** 只动池：别名与关系是 AI 逐章写下的，用户在配音页换一次池不该把它们抹掉。 */
    @Test
    fun poolOnlyRewriteKeepsAliasesAndRelation() {
        val memory = "李星菲｜小菲、卧底｜男主的女儿｜女少女\n李振富｜老李｜父亲｜男老年"

        val next = requireNotNull(
            CastMemoryMirror.replaceLinePool(memory, "李振富", "男中年")
        )

        assertEquals(
            listOf(
                "李星菲｜小菲、卧底｜男主的女儿｜女少女",
                "李振富｜老李｜父亲｜男中年",
            ),
            next.lineSequence().toList(),
        )
    }

    /** 记忆里本来没有池这一栏：补上去，别把整行改出第三种形状。 */
    @Test
    fun addsThePoolColumnWhenTheLineHadNone() {
        assertEquals(
            "张三｜小三｜路人｜女中年",
            CastMemoryMirror.replaceLinePool("张三｜小三｜路人", "张三", "女中年"),
        )
    }

    /** 清掉池 = 去掉最后一栏，留下「名字｜别名｜关系」这种 AI 也认得的写法。 */
    @Test
    fun removesThePoolColumnWhenThePoolIsBlank() {
        assertEquals(
            "张三｜小三｜路人",
            CastMemoryMirror.replaceLinePool("张三｜小三｜路人｜女中年", "张三", ""),
        )
    }

    /** 没有对应的主名就什么都不做：按别名去改会把别人的池换掉。 */
    @Test
    fun poolUpdateDoesNotTouchOtherCharactersOrAppendANewLine() {
        val memory = "李星菲｜小菲｜妹妹｜女少女"

        assertNull(CastMemoryMirror.replaceLinePool(memory, "小菲", "男老年"))
        assertNull(CastMemoryMirror.replaceLinePool(memory, "李星菲", "女少女"))
        assertNull(CastMemoryMirror.replaceLinePool("", "李星菲", "女少女"))
    }

    /** 用户在记忆里把主名改了：这一条必须当成改名交给配音角色与人物档案。 */
    @Test
    fun detectsARenameWhenOnlyTheMainNameChanged() {
        val edits = CastMemoryMirror.diffUserEdits(
            "星菲｜小菲｜妹妹｜女少女\n雷奥尔｜｜主持审判",
            "李星菲｜小菲｜妹妹｜女少女\n雷奥尔｜｜主持审判",
        )

        assertEquals(listOf("星菲" to "李星菲"), edits.renames)
        assertEquals(emptyList<Pair<String, String>>(), edits.poolChanges)
    }

    /** 只动池栏 = 改池，不能顺手算成改名（名字没变）。 */
    @Test
    fun detectsAPoolChangeSeparatelyFromARename() {
        val edits = CastMemoryMirror.diffUserEdits(
            "李星菲｜小菲｜妹妹｜女少女",
            "李星菲｜小菲｜妹妹｜男老年",
        )

        assertEquals(emptyList<Pair<String, String>>(), edits.renames)
        assertEquals(listOf("李星菲" to "男老年"), edits.poolChanges)
    }

    /**
     * 行数不一样就不是用户逐行改：整段记忆是 AI 每章重写的，
     * 这时按位置配对会把「另一个人」认成改名，一次保存能毁掉整本角色表。
     */
    @Test
    fun ignoresAnythingThatChangesTheLineCount() {
        val edits = CastMemoryMirror.diffUserEdits(
            "李星菲｜小菲｜妹妹｜女少女",
            "李星菲｜小菲｜妹妹｜女少女\n梅林｜｜灰发少女",
        )

        assertEquals(emptyList<Pair<String, String>>(), edits.renames)
        assertEquals(emptyList<Pair<String, String>>(), edits.poolChanges)
    }

    /** 同一行里既改主名又改池：两件都要认出来，池挂到新名字上（改名先落库，那时行已经叫新名）。 */
    @Test
    fun infersARenameAndAPoolChangeTogether() {
        val edits = CastMemoryMirror.diffUserEdits(
            "星菲｜小菲｜妹妹｜女少女",
            "李星菲｜小菲｜妹妹｜男老年",
        )

        assertEquals(listOf("星菲" to "李星菲"), edits.renames)
        assertEquals(listOf("李星菲" to "男老年"), edits.poolChanges)
    }

    /** 中间插入一行不影响别人的判断：配对靠身份指纹，不靠行号。 */
    @Test
    fun stillDetectsARenameWhenAnotherLineWasInserted() {
        val edits = CastMemoryMirror.diffUserEdits(
            "星菲｜小菲｜妹妹｜女少女\n雷奥尔｜｜主持审判",
            "梅林｜｜灰发少女\n李星菲｜小菲｜妹妹｜女少女\n雷奥尔｜｜主持审判",
        )

        assertEquals(listOf("星菲" to "李星菲"), edits.renames)
        assertEquals(emptyList<Pair<String, String>>(), edits.poolChanges)
    }

    /** 两行的别名栏与关系栏完全一样时认不出谁是谁，什么都不推断。 */
    @Test
    fun refusesToGuessBetweenTwoLinesWithTheSameIdentity() {
        val edits = CastMemoryMirror.diffUserEdits(
            "甲｜｜路人\n乙｜｜路人",
            "丙｜｜路人",
        )

        assertEquals(emptyList<Pair<String, String>>(), edits.renames)
        assertEquals(emptyList<Pair<String, String>>(), edits.poolChanges)
    }

    /** 同一行既改了名字又改了关系：不是「改名」这个动作，什么都不推断。 */
    @Test
    fun doesNotInferARenameWhenOtherFieldsMovedToo() {
        val edits = CastMemoryMirror.diffUserEdits(
            "星菲｜小菲｜妹妹｜女少女",
            "李星菲｜小菲｜商会主｜女少女",
        )

        assertEquals(emptyList<Pair<String, String>>(), edits.renames)
        assertEquals(emptyList<Pair<String, String>>(), edits.poolChanges)
    }

    /** 配音页改了主名与池 → 记忆那一行跟着换，别名与关系一个字不动。 */
    @Test
    fun renamesTheMemoryLineWhenTheCastRowWasRenamed() {
        val next = CastMemoryMirror.renameLinePool(
            "星菲｜小菲、卧底｜男主的女儿｜女少女\n旁人｜｜路人｜男中年",
            from = "星菲",
            to = "李星菲",
            pool = "女青年",
        )

        assertEquals(
            "李星菲｜小菲、卧底｜男主的女儿｜女青年\n旁人｜｜路人｜男中年",
            next,
        )
    }

    /** AI 常把全名挂在别名栏：主名换成它之后，别名里那一个要摘掉，否则同一个人两种写法并存。 */
    @Test
    fun dropsTheAliasThatBecameTheMainName() {
        val next = CastMemoryMirror.renameLinePool(
            "星菲｜李星菲、卧底｜妹妹｜女少女",
            from = "星菲",
            to = "李星菲",
            pool = "女少女",
        )

        assertEquals("李星菲｜卧底｜妹妹｜女少女", next)
    }

    /** 新旧两个主名都已经有行（分裂已经发生）：只认新名那一行，不去动旧名那一行。 */
    @Test
    fun editsTheNewNameLineOnlyWhenBothNamesAlreadyExist() {
        val next = CastMemoryMirror.renameLinePool(
            "星菲｜小菲｜妹妹｜女少女\n李星菲｜｜会长｜女中年",
            from = "星菲",
            to = "李星菲",
            pool = "女青年",
        )

        assertEquals("星菲｜小菲｜妹妹｜女少女\n李星菲｜｜会长｜女青年", next)
    }

    /** 两边都查不到这一行：这本书的记忆里没有这个人，什么都不写。 */
    @Test
    fun leavesMemoryAloneWhenNoLineMatchesEitherName() {
        assertNull(
            CastMemoryMirror.renameLinePool(
                "旁人｜｜路人｜男中年",
                from = "星菲",
                to = "李星菲",
                pool = "女青年",
            )
        )
    }

    /** 没改名、只改池时行为与原来一致；池清空时不留尾栏。 */
    @Test
    fun keepsThreeFieldsWhenThePoolIsCleared() {
        assertEquals(
            "星菲｜小菲｜妹妹｜女青年",
            CastMemoryMirror.renameLinePool("星菲｜小菲｜妹妹｜女少女", "星菲", "星菲", "女青年"),
        )
        assertEquals(
            "星菲｜小菲｜妹妹",
            CastMemoryMirror.renameLinePool("星菲｜小菲｜妹妹｜女少女", "星菲", "星菲", ""),
        )
    }
}
