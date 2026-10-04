package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 打开一本新书时的阅读会话隔离。
 *
 * 阅读画布在首次组合就会请求分页，而开书初始化要跳到 IO 线程之后才把新章装进 `ReadBook`
 * 单例。未下载的书籍在这段时间里，单例仍是上一本书的内容——路由必须先把本书的 bookUrl 交给
 * `ReadBookController`，分页发布再据此判断会话是否还是别人的书，否则新页面会先画出上一本书的正文。
 */
class ReaderOpenBookIsolationTest {

    @Test
    fun `reader route hands its book url to the controller during composition`() {
        val source = mainSourceFile("io/legado/app/ui/main/MainNavGraph.kt").readText()
        val construction = section(
            source,
            "val controller = remember(readBookViewModel, readerSessionViewModel) {",
            "\n            }",
        )

        assertTrue(
            "路由要把 bookUrl 随构造交给控制器，LaunchedEffect 跑在组合之后，赶不上首帧",
            construction.contains("routeBookUrl = route.bookUrl"),
        )
        assertFalse(
            "绑定必须写在 remember 里，不能放进 LaunchedEffect",
            construction.contains("LaunchedEffect"),
        )
    }

    @Test
    fun `pagination publish waits for the route book to become the session book`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookController.kt").readText()
        val publish = section(source, "private fun publishReaderPageWindow(")
        val gateIndex = publish.indexOf("isReaderSessionStale()")
        val directPublish = publish.indexOf("publishDirectReaderPageWindow(")

        assertTrue("publishReaderPageWindow must ask the session gate", gateIndex >= 0)
        assertTrue(
            "会话还是上一本书时只能出占位窗，正文发布必须在闸门之后",
            directPublish > gateIndex,
        )
        assertTrue(
            "闸门命中要发加载窗，不能什么都不发",
            publish.contains("publishLoadingReaderWindow()"),
        )
    }

    @Test
    fun `the session gate only applies once the route has named a book`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookController.kt").readText()
        val gate = section(source, "private fun isReaderSessionStale()")

        assertTrue(
            "没有 bookUrl 的路由（如从朗读通知回来）不设限，否则阅读页会一直停在占位窗",
            gate.contains("?: return false"),
        )
        assertTrue(
            "比对的是会话当前书与路由要开的书",
            gate.contains("ReadBook.book?.bookUrl != expected"),
        )
    }

    private companion object {
        /** 取到 `startMarker` 起、止于下一个同缩进 `}` 的源码文本。 */
        fun section(source: String, startMarker: String, endMarker: String = "\n    }"): String {
            val start = source.indexOf(startMarker)
            assertTrue("$startMarker not found", start >= 0)
            val rest = source.substring(start)
            val end = rest.indexOf(endMarker, startMarker.length)
            return if (end < 0) rest else rest.substring(0, end)
        }

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
