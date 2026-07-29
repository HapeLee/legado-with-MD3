package io.legado.app.ui.book.read.page

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Track D·D1 —— `ReadView` 只做绘制/手势/动画，不下达业务命令。
 *
 * 手势判定出的业务意图经 [ReaderEvent] 出站，由 `ReadBookController` 翻译成 Intent 或会话调用。
 * 两条会悄悄失效的边界：
 *
 * 1. 有人图省事在 `ReadView` 里直呼 `ReadBook`/`ReadAloud`/`BaseReadAloudService`——
 *    出站回边被绕过，View 又长回业务层。
 * 2. `eventListener` 被改成可空或加 `activity as` 兜底——漏接线从**编译错误**退化成
 *    **静默失效的点击**（点了没反应，真机上极难归因）。
 *
 * 只读取页数据的 `ReadBook` 成员是 D2 的范围（入站数据面），D1 不动，见白名单。
 */
class ReadViewOutboundBoundaryTest {

    @Test
    fun `ReadView 不直呼业务单例下达命令`() {
        val source = stripComments(readViewSource())

        val readBookViolations = Regex("""\bReadBook\.(\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .filterNot { it in READ_BOOK_DATA_PLANE_ALLOWLIST }
            .distinct()
            .map { "ReadBook.$it" }
            .toList()

        val aloudViolations = Regex("""\b(?:ReadAloud|BaseReadAloudService)\.(\w+)""")
            .findAll(source)
            .map { it.value }
            .distinct()
            .toList()

        val violations = readBookViolations + aloudViolations
        assertTrue(
            "ReadView 又直接下达业务命令了：${violations.joinToString()}。\n" +
                "业务意图请加进 ReaderEvent 并由 ReadBookController.onEvent 翻译；\n" +
                "只读页数据（${READ_BOOK_DATA_PLANE_ALLOWLIST.joinToString()}）属 Track D·D2，暂留。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `eventListener 必须构造期注入且不可为空`() {
        val source = stripComments(readViewSource())
        val violations = buildList {
            if (!Regex("""private\s+val\s+eventListener\s*:\s*ReaderEventListener\s*(?![?=])""")
                    .containsMatchIn(source)
            ) {
                add("eventListener 不再是构造期注入的非空 val")
            }
            if (Regex("""eventListener\s*\?""").containsMatchIn(source)) {
                add("eventListener 出现了可空调用")
            }
            if (Regex("""as\s+ReaderEventListener""").containsMatchIn(source)) {
                add("eventListener 出现了 activity as 兜底")
            }
        }
        assertTrue(
            "${violations.joinToString()}。\n" +
                "非空构造参数是结构性保证：漏接线必须是编译错误，" +
                "而不是点了没反应的静默失效手势。",
            violations.isEmpty(),
        )
    }

    private companion object {
        /** D2（入站数据面）的范围，D1 不动 */
        val READ_BOOK_DATA_PLANE_ALLOWLIST = listOf(
            "textChapter",
            "durChapterIndex",
            "simulatedChapterSize",
            "pageAnim",
        )

        fun readViewSource(): String =
            mainSourceFile("io/legado/app/ui/book/read/page/ReadView.kt").readText()

        fun stripComments(text: String): String = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
