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
        val violations = businessSingletonViolations(readViewSource())
        assertTrue(
            "ReadView 又直接摸业务单例了：${violations.joinToString()}。\n" +
                "业务意图请加进 ReaderEvent 由 ReadBookController.onEvent 翻译；\n" +
                "要读的页数据请加进 ReaderPageSource 由宿主喂进来。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `TextPageFactory 不引用业务单例`() {
        val violations = businessSingletonViolations(
            mainSourceFile(
                "io/legado/app/ui/book/read/page/provider/TextPageFactory.kt"
            ).readText()
        )
        assertTrue(
            "TextPageFactory 又直接摸业务单例了：${violations.joinToString()}。\n" +
                "取页器是渲染层，不下达业务命令：翻页/换章请经 ReaderPageSource 的 " +
                "setPageIndex/moveToNextChapter/moveToPrevChapter，加载消息读 pageSource.msg。\n" +
                "这三个命令是**同步**的（下达后立即取新位置的页并返回布尔给翻页委托），" +
                "所以不能改走即发即忘的 ReaderEvent。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `排版器与图片列不在运行期读业务单例`() {
        // 这两处以前是「排版协程/绘制期去读 ReadBook」，换书换源时会拿新会话的值配旧内容：
        // 排版协程用新源下旧书的图、旧页面重绘用新书目录找图。现在都由构造期定参钉住。
        val files = listOf(
            "io/legado/app/ui/book/read/page/provider/TextChapterLayout.kt",
            "io/legado/app/ui/book/read/page/entities/column/ImageColumn.kt",
        )
        val violations = files.flatMap { path ->
            businessSingletonViolations(mainSourceFile(path).readText())
                .map { "${path.substringAfterLast('/')}: $it" }
        }
        assertTrue(
            "又在运行期读业务单例了：${violations.joinToString()}。\n" +
                "排版/绘制要用的 book、bookSource 请由构造方在**建这一章时**传进来——" +
                "它们是那一次排版的输入，不是随时可变的全局。",
            violations.isEmpty(),
        )
    }

    @Test
    fun `pageSource 必须由构造期注入喂给 TextPageFactory`() {
        val source = stripComments(readViewSource())
        assertTrue(
            "TextPageFactory 不再由 ReadView 用构造期注入的 pageSource 建出来。\n" +
                "若改成让取页器自己找数据源（单例/GlobalContext），D2 的入站边界就绕过去了。",
            Regex("""TextPageFactory\(\s*this\s*,\s*pageSource\s*\)""").containsMatchIn(source),
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

        /**
         * 渲染层文件对业务单例的引用。
         *
         * 三种写法都算违规，不只是 `ReadBook.xxx` 限定访问——**成员 import 之后成员可以裸写**
         * （`import …ReadBook.durChapterIndex` 后直接用 `durChapterIndex`），
         * 局部别名（`val rb = ReadBook`）同理。只按 `ReadBook\.` 找，这两种一个都看不见。
         */
        fun businessSingletonViolations(rawSource: String): List<String> {
            val source = stripComments(rawSource)
            val singletons = "ReadBook|ReadAloud|BaseReadAloudService"

            val qualified = Regex("""\b($singletons)\.(\w+)""")
                .findAll(source)
                .map { "${it.groupValues[1]}.${it.groupValues[2]}" }

            val imports = Regex("""(?m)^\s*import\s+[\w.]*\b($singletons)\b[\w.]*""")
                .findAll(source)
                .map { "import: ${it.value.trim()}" }

            val aliases = Regex("""=\s*($singletons)\s*$""", RegexOption.MULTILINE)
                .findAll(source)
                .map { "别名: ${it.value.trim()}" }

            return (qualified + imports + aliases).distinct().toList()
        }

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
