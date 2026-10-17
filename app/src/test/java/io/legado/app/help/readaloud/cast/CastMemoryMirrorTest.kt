package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * 真机数据：官方包里《转生于没有主角的学院》第 16 章的记忆原文。
     * 编号路人（新生甲/乙）、复数集体（…们）不进这张表，有名有姓的与「施卢斯父亲」这类
     * 真实称谓一个字都不能少。
     */
    @Test
    fun dropsNumberedExtrasFromARealMemoryTable() {
        val kept = CastMemoryMirror.dropThrowawayExtras(REAL_MEMORY).lines().filter { it.isNotBlank() }

        assertEquals(
            listOf("金振宇", "路德维希教授", "施卢斯父亲", "玛丽·艾尔"),
            kept.map { it.split('｜').first() },
        )
    }

    /** 编号写法被别人的别名/身份栏原样记着 = 某个有名字的人的另一种写法，这种行留下。 */
    @Test
    fun keepsAnExtraNameThatAnotherLineUsesAsAnAlias() {
        val memory = listOf(
            "金振宇｜新生甲的同学｜本章被嘲讽｜男青年",
            "新生甲｜围观学生｜嘲讽金振宇｜少年",
        ).joinToString("\n")

        assertEquals(memory, CastMemoryMirror.dropThrowawayExtras(memory))
    }

    @Test
    fun recognisesTheThreeWritingsOfAThrowawayExtra() {
        listOf("新生甲", "工作人员丙", "新生们", "旁边新生").forEach {
            assertTrue(it, CastMemoryMirror.isThrowawayExtra(it))
        }
        listOf("金振宇", "施卢斯父亲", "玛丽·艾尔", "乌鸦", "尤利娅", "路德维希教授").forEach {
            assertFalse(it, CastMemoryMirror.isThrowawayExtra(it))
        }
    }

    /**
     * 真机数据（《成为浪漫小说中的反派角色》埃诺克·巴拉尔 的别名栏原文）：
     * 模型把身份用 `/` 串成一串，下一趟整串抄回来再接一截，这一栏就每章长一点。
     * 拆平后同一批称呼是同一批条目，逐字相等的归并这才匹配得上。
     */
    @Test
    fun splitsSlashChainedAliasesIntoOneAppellationEach() {
        val merged = CastMemoryMirror.aliasWords(
            listOf("男主", "埃诺克", "男主/埃诺克/埃诺克大人", "男主/埃诺克/埃诺克大人/埃诺克小哥"),
        )

        assertEquals(listOf("男主", "埃诺克", "埃诺克大人", "埃诺克小哥"), merged)
    }

    /** 不是称呼的一并丢掉：只在这章成立的、模型自己标不确定的、回显输入键名的、空的。 */
    @Test
    fun dropsAliasesThatAreNotAppellations() {
        assertEquals(
            listOf("宿舍管理员"),
            CastMemoryMirror.aliasWords(
                listOf("宿舍管理员", "本章惊愕", "情报局内应？", "已有角色", "别名金振宇", ""),
            ),
        )
    }

    /**
     * 真机那本书的形状：上一次 AI 抄出去的是 `男主/埃诺克/埃诺克小哥`，这一趟它换了写法
     * （`男主/埃诺克/贝尔大人`）。旧那几截要跟着掉，用户自己补的一条不动。
     * 库里存的是 `/` 串起来的旧写法（`男主/埃诺克`）时也要认得出那是 AI 抄的，否则永远摘不掉。
     */
    @Test
    fun dropsAliasesTheMemoryNoLongerListsAndKeepsUserOnes() {
        val ledger = CastMemoryMirror.Line("埃诺克·巴拉尔", listOf("男主/埃诺克/埃诺克小哥"), "潜入精灵王国", "男青年")
        val line = CastMemoryMirror.Line("埃诺克·巴拉尔", listOf("男主/埃诺克/贝尔大人"), "潜入精灵王国", "男青年")

        val merged = CastMemoryMirror.mergeIdentity(
            line = line,
            stored = listOf("男主/埃诺克", "埃诺克小哥", "用户自己补的称呼"),
            storedSummary = "潜入精灵王国",
            ledger = ledger,
        )

        assertEquals(listOf("男主", "埃诺克", "贝尔大人", "用户自己补的称呼"), merged.aliases)
    }

    /** 上限只压记忆那一侧（模型每章能一直往里加的那一半）；凭据里没有的（用户自己补的）全留。 */
    @Test
    fun capsOnlyTheMemorySideOfTheAliasList() {
        val memoryAliases = (1..14).map { "称呼$it" }
        val ledger = CastMemoryMirror.Line("埃诺克·巴拉尔", listOf("称呼1"), "男主", "男青年")
        val line = CastMemoryMirror.Line("埃诺克·巴拉尔", memoryAliases, "男主", "男青年")

        val merged = CastMemoryMirror.mergeIdentity(
            line = line,
            stored = listOf("称呼1", "用户自己补的称呼"),
            storedSummary = "男主",
            ledger = ledger,
        )

        assertEquals(memoryAliases.take(12) + "用户自己补的称呼", merged.aliases)
    }

    /** 只有留得下来的那些行才算「有人记着这个编号」：群杂行互相提到对方，一起掉。 */
    @Test
    fun onlyNamedLinesCanRescueAnExtraName() {
        val kept = CastMemoryMirror.dropThrowawayExtras(
            listOf(
                "金振宇｜新生甲的同学｜男主｜男青年",
                "新生甲｜围观学生｜嘲讽金振宇｜少年",
                "路人己｜新生甲的同伙｜起哄｜少年",
            ).joinToString("\n"),
        ).lines()

        assertEquals(listOf("金振宇｜新生甲的同学｜男主｜男青年", "新生甲｜围观学生｜嘲讽金振宇｜少年"), kept)
    }

    /** 简介跟着记忆走，前提是那一句本来就是 AI 写的；用户自己写的那一份、以及没有凭据时都不改写。 */
    @Test
    fun updatesTheSummaryOnlyWhenMemoryIsTheOneThatWroteIt() {
        val line = CastMemoryMirror.Line("玛丽·艾尔", listOf("宿舍管理员"), "情报局内应", "女老年")
        val ledger = CastMemoryMirror.Line("玛丽·艾尔", listOf("宿舍管理员"), "宿舍管理员/拿拖把女士", "女老年")

        assertEquals(
            "情报局内应",
            CastMemoryMirror.mergeIdentity(line, emptyList(), "宿舍管理员/拿拖把女士", ledger).summary,
        )
        assertEquals(
            "情报局内应",
            CastMemoryMirror.mergeIdentity(line, emptyList(), "", ledger).summary,
        )
        assertEquals(
            "用户自己写的简介",
            CastMemoryMirror.mergeIdentity(line, emptyList(), "用户自己写的简介", ledger).summary,
        )
        assertEquals(
            "用户自己写的简介",
            CastMemoryMirror.mergeIdentity(line, emptyList(), "用户自己写的简介", null).summary,
        )
    }

    private companion object {
        val REAL_MEMORY = """
            金振宇｜主角/作家/附身施卢斯·海因克尔｜施卢斯为其别名；本章与路德维希交易｜男青年
            路德维希教授｜首席教授｜本章接受功劳交易｜男老年
            施卢斯父亲｜剑术记忆投影｜指导握剑｜男中年
            新生们｜教室学生｜围观恐慌｜少年
            新生甲｜围观学生｜嘲讽金振宇｜少年
            新生乙｜围观学生｜嘲讽金振宇｜少年
            考官们｜考试官｜递交成绩｜男中年
            玛丽·艾尔｜宿舍管理员｜被杀｜女老年
        """.trimIndent()
    }
}
