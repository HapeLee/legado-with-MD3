package io.legado.app.ui.book.read.page

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Track D·D1/D2 —— `ReadView` 只做绘制/手势/动画，两个方向都不认识业务单例。
 *
 * - 出站（D1）：手势判定出的业务意图经 [ReaderEvent] 发出，由 `ReadBookController` 翻译。
 * - 入站（D2）：页数据经 [ReaderPageSource] 喂进来，不再直接问 `ReadBook`。
 *
 * 会悄悄失效的边界：
 *
 * 1. 有人图省事在 `ReadView` 或 `DataSource` 里直接摸 `ReadBook`/`ReadAloud`——
 *    两条回边被绕过，View 又长回业务层。
 * 2. 三个协作面（`callBack`/`eventListener`/`pageSource`）被改成可空或加 `activity as`
 *    兜底——漏接线从**编译错误**退化成**静默失效**（点了没反应、页面空白），真机上极难归因。
 */
class ReadViewBoundaryTest {

    @Test
    fun `ReadView 不引用业务单例`() {
        val source = stripComments(readViewSource())

        val readBookViolations = Regex("""\bReadBook\.(\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
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
            "ReadView 又直接摸业务单例了：${violations.joinToString()}。\n" +
                "业务意图请加进 ReaderEvent 由 ReadBookController.onEvent 翻译；\n" +
                "要读的页数据请加进 ReaderPageSource 由宿主喂进来。",
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

    @Test
    fun `ReadView CallBack 只剩瞬时 UI 副作用`() {
        val source = stripComments(readViewSource())
        val body = Regex("""interface\s+CallBack\s*\{([\s\S]*?)\n\s{4}\}""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("找不到 ReadView.CallBack 的声明")

        val members = Regex("""\b(?:fun|val|var)\s+(\w+)""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()

        val businessLeaks = members.filterNot { it in CALLBACK_UI_SIDE_EFFECTS }
        assertTrue(
            "ReadView.CallBack 又混进了非瞬时副作用的成员：${businessLeaks.joinToString()}。\n" +
                "业务/导航意图请走 ReaderEvent；CallBack 只留 " +
                "${CALLBACK_UI_SIDE_EFFECTS.joinToString()}——" +
                "前三者是 View 直接驱动宿主的瞬时副作用，isInitFinish 是首帧放行门闩。",
            businessLeaks.isEmpty(),
        )
    }

    @Test
    fun `callBack 也必须构造期注入且无 activity 兜底`() {
        val source = stripComments(readViewSource())
        val violations = buildList {
            if (Regex("""as\s+CallBack""").containsMatchIn(source)) {
                add("出现了 activity as CallBack 兜底")
            }
            if (!Regex("""private\s+val\s+callBack\s*:\s*CallBack\s*(?![?=])""")
                    .containsMatchIn(source)
            ) {
                add("callBack 不再是构造期注入的非空 private val")
            }
        }
        assertTrue(
            "${violations.joinToString()}。\n" +
                "宿主协作面漏接线必须是编译错误；activity as CallBack 还会把 ReadView " +
                "钉死在「宿主必须是实现了该接口的 Activity」上，JVM 里就构造不出来。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `DataSource 接口不内嵌 ReadBook`() {
        val source = stripComments(
            mainSourceFile("io/legado/app/ui/book/read/page/api/DataSource.kt").readText()
        )
        assertTrue(
            "DataSource 又把 ReadBook 写进默认实现了。\n" +
                "接口带着单例默认值，等于谁实现它谁就绑死在全局状态上——" +
                "页内位置请由实现方从 ReaderPageSource 取。",
            !Regex("""\bReadBook\b""").containsMatchIn(source),
        )
    }

    @Test
    fun `pageSource 必须构造期注入且不可为空`() {
        val source = stripComments(readViewSource())
        val violations = buildList {
            if (!Regex("""private\s+val\s+pageSource\s*:\s*ReaderPageSource\s*(?![?=])""")
                    .containsMatchIn(source)
            ) {
                add("pageSource 不再是构造期注入的非空 val")
            }
            if (Regex("""pageSource\s*\?""").containsMatchIn(source)) {
                add("pageSource 出现了可空调用")
            }
        }
        assertTrue(
            "${violations.joinToString()}。\n" +
                "页数据入口漏接线必须是编译错误，而不是正文空白。",
            violations.isEmpty(),
        )
    }

    private companion object {
        /** CallBack 允许保留的成员：三项瞬时 UI 副作用 + 首帧放行门闩 */
        val CALLBACK_UI_SIDE_EFFECTS = listOf(
            "isInitFinish",
            "screenOffTimerStart",
            "showTextActionMenu",
            "upSystemUiVisibility",
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
