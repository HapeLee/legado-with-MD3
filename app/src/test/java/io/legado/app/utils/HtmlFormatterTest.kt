package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlFormatterTest {

    @Test
    fun formatDisplayText_removesScriptAndStyleContents() {
        val result = HtmlFormatter.formatDisplayText(
            """
                <style>.intro { color: red; }</style>
                <p>第一段</p>
                <script>window.alert('bad')</script>
                <div>第二段</div>
            """.trimIndent()
        )

        assertEquals("　　第一段\n　　第二段", result)
        assertTrue(result.contains("第一段"))
        assertTrue(result.contains("第二段"))
        assertFalse(result.contains("color: red"))
        assertFalse(result.contains("window.alert"))
    }

    @Test
    fun formatDisplayText_keepsPlainText() {
        assertEquals("　　普通简介", HtmlFormatter.formatDisplayText("普通简介"))
    }

    @Test
    fun formatDisplayText_dropsBookMetaLines() {
        val result = HtmlFormatter.formatDisplayText(
            "书名：某某传<br>作者：张三<br>【分类】玄幻<br>最新章节：第一千章 大结局<br>简介：这是正文第一段<br>这是正文第二段"
        )

        assertEquals("　　这是正文第一段\n　　这是正文第二段", result)
    }

    @Test
    fun formatDisplayText_keepsContentStartingWithMetaWord() {
        assertEquals(
            "　　作者的话：这本书写了三年",
            HtmlFormatter.formatDisplayText("作者的话：这本书写了三年")
        )
    }

    @Test
    fun formatDisplayText_keepsPlainTextLineBreaks() {
        assertEquals(
            "　　第一段\n　　第二段",
            HtmlFormatter.formatDisplayText("第一段\n第二段")
        )
    }

    @Test
    fun formatSummaryText_dropsIndentAndLineBreaks() {
        assertEquals(
            "第一段 第二段",
            HtmlFormatter.formatSummaryText("<p>第一段</p><p>第二段</p>")
        )
    }

    @Test
    fun formatSummaryText_dropsLeadingIndentOfSingleParagraph() {
        assertEquals("普通简介", HtmlFormatter.formatSummaryText("　　普通简介"))
    }

    //聚合书源实际写进详情简介的状态面板
    private val aggregatedSourceIntro = """
        📡 当前服务：https://v1.example.cf
        🔑 账号状态：⚠️ 未登录 | 点击右上角 🔖 登录
        🏷 数据来源：百度
        ⚙️ 访问模式：服务器网络
        📖 阅读至：第一章 陨落的天才
        ❇️───────❇️───────❇️
        📚 最新章节： 《某某传》番外（下）
        ⏳ 更新时间： 2018-09-19 12:21:32
        📝 书籍字数： 532万
        🚩 书籍状态： 完结
        ‎
        📖 书籍简介：
        这里是属于斗气的世界，没有花俏艳丽的魔法。
        新书等级制度：斗者，斗师，大斗师，斗灵。
    """.trimIndent().replace("\n", "<br>")

    @Test
    fun formatIntroText_dropsSourceStatusPanel() {
        assertEquals(
            "　　这里是属于斗气的世界，没有花俏艳丽的魔法。\n　　新书等级制度：斗者，斗师，大斗师，斗灵。",
            HtmlFormatter.formatIntroText(aggregatedSourceIntro)
        )
    }

    @Test
    fun formatDisplayText_keepsSourceStatusPanel() {
        val result = HtmlFormatter.formatDisplayText(aggregatedSourceIntro)

        assertTrue(result.contains("当前服务"))
        assertTrue(result.contains("点击右上角"))
    }

    @Test
    fun formatIntroText_keepsIconLineWithoutLabel() {
        assertEquals(
            "　　🔥 这本书已经完结了, 放心入坑",
            HtmlFormatter.formatIntroText("🔥 这本书已经完结了, 放心入坑")
        )
    }

    @Test
    fun formatIntroText_keepsLongTextAfterIconLabel() {
        val long = "本书讲述了一个少年从家族没落到重回巅峰的故事, 其中有热血也有温情, 值得一读再读。"
        assertTrue(HtmlFormatter.formatIntroText("📖 简介：$long").contains(long))
    }
}
