package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * R1.4 —— 排版度量必须留在不可变快照里。
 *
 * 这些值由主线程写（`upStyle`/`upLayout`/`notifyViewSizeChange`），由 IO 线程上构造的
 * [TextChapterLayout] 读。改回逐字段的可变静态量会同时丢掉两件事：跨线程的
 * happens-before，以及组内原子性（读到「新 viewWidth 配旧 paddingLeft」的撕裂组合）。
 *
 * 两条不变式都做源码扫描——`ChapterProvider` 是 object 且依赖 Android 图形类型，
 * JVM 单测里无法构造。
 */
class ChapterProviderMetricsInvariantTest {

    @Test
    fun `排版度量不以可变静态量形式存在`() {
        val offenders = MUTABLE_METRIC.findAll(chapterProviderSource())
            .map { it.groupValues[1] }
            .filterNot { it in ALLOWED_MUTABLE }
            .toList()
            .sorted()

        assertEquals(
            "以下排版度量退回了可变静态量，请放进 LayoutMetrics 快照：\n" +
                offenders.joinToString("\n") { "  - var $it" },
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `排版任务一次性取整组度量而非逐字段读取`() {
        // 编译期常量随便读——它们不是度量，不会变。
        val constants = Regex("""const val ([a-zA-Z][A-Za-z0-9_]*)""")
            .findAll(chapterProviderSource())
            .map { it.groupValues[1] }
            .toSet() + "layoutMetrics"

        // import 行一起扫：`import ...ChapterProvider.viewWidth`（含 as 别名）之后成员可以裸写，
        // 原先跳过 import 行的写法对这种撕裂读取完全睁眼瞎。放行的只有编译期常量——
        // 它们不是度量，读多少次都不会和别的字段对不上。
        // 通配 import 不用管：Kotlin 不允许从 object 按需导入。
        val offenders = MEMBER_ACCESS.findAll(textChapterLayoutSource())
            .map { it.groupValues[1] }
            .filterNot { it in constants }
            .toList()
            .distinct()
            .sorted()

        assertEquals(
            "TextChapterLayout 在 IO 线程构造，必须用 ChapterProvider.layoutMetrics() " +
                "一次取到自洽的一组度量。以下是逐字段读取（成员 import 后的裸用同罪）：\n" +
                offenders.joinToString("\n") { "  - ChapterProvider.$it" },
            emptyList<String>(),
            offenders,
        )
    }

    private fun chapterProviderSource(): String =
        mainSourceFile("ChapterProvider.kt").readText()

    private fun textChapterLayoutSource(): String =
        mainSourceFile("TextChapterLayout.kt").readText()

    private fun mainSourceFile(fileName: String): File {
        val relativePath = "io/legado/app/ui/book/read/page/provider/$fileName"
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

    private companion object {
        val MEMBER_ACCESS = Regex("""\bChapterProvider\.([a-zA-Z][A-Za-z0-9_]*)""")

        /** object 顶层（4 空格缩进）的 `var 名字`，跳过 data class 里的 `val`。 */
        val MUTABLE_METRIC = Regex("""^ {4}(?:@JvmStatic\s+)?var ([a-zA-Z][A-Za-z0-9_]*)""", RegexOption.MULTILINE)

        /**
         * 允许保留可变的成员：
         * - `dashEffect` / `reviewPaint` / `linePaint`：绘制期读取的 Paint 类对象，本身可变，
         *   放进不可变快照没有意义（根治要让排版任务持有自己的副本，属 Track D2）。
         *   `linePaint` 原本是 `by lazy`，因此躲过了本断言，代价是改下划线粗细/字体后
         *   永远沿用首帧那份；改成 `upStyle()` 重建后才落进这一类。
         * - `upViewSizeRunnable`：主线程去抖用的 Runnable 句柄，不跨线程。
         */
        val ALLOWED_MUTABLE = setOf("dashEffect", "reviewPaint", "linePaint", "upViewSizeRunnable")
    }
}
