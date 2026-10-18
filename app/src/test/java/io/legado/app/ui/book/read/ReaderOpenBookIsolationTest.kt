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
    fun `session gate uses its route initialization state`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookController.kt").readText()
        val gate = section(source, "private fun isReaderSessionStale()")

        assertTrue(
            "开书隔离必须读取本次阅读初始化状态，不能永久绑定路由的旧 URL",
            gate.contains("isRouteInitialized = routeInitialized"),
        )
        assertTrue(
            gate.contains("isReaderOpeningSessionStale(") &&
                    gate.contains("routeBookUrl = routeBookUrl") &&
                    gate.contains("currentBookUrl = ReadBook.book?.bookUrl"),
        )
    }

    @Test
    fun `route completion releases isolation and republishes content`() {
        val controller =
            mainSourceFile("io/legado/app/ui/book/read/ReadBookController.kt").readText()
        val completion = section(controller, "fun onRouteInitialized()")
        val release = completion.indexOf("routeInitialized = true")
        assertTrue("路由初始化完成必须先解除隔离", release >= 0)
        assertTrue(
            "解除隔离后主动重发，自动换源的正文可能已在此前到达",
            completion.indexOf("publishReaderPageWindow()") > release,
        )
        val graph = mainSourceFile("io/legado/app/ui/main/MainNavGraph.kt").readText()
        val initialization =
            section(graph, "readBookViewModel.initData(initRequest, initialBook) {")
        assertTrue(initialization.contains("controller.onRouteInitialized()"))
    }

    @Test
    fun `opening another book waits instead of showing the previous session`() {
        assertTrue(
            isReaderOpeningSessionStale(
                "book-a",
                "previous-book",
                isRouteInitialized = false
            )
        )
        assertTrue(isReaderOpeningSessionStale("book-a", null, isRouteInitialized = false))
        assertFalse(isReaderOpeningSessionStale("book-a", "book-a", isRouteInitialized = false))
    }

    @Test
    fun `changing source after opening does not wait for the old book url`() {
        assertFalse(
            isReaderOpeningSessionStale(
                "source-a/book",
                "source-a/book",
                isRouteInitialized = true
            )
        )
        assertFalse(
            isReaderOpeningSessionStale(
                "source-a/book",
                "source-b/book",
                isRouteInitialized = true
            )
        )
        assertFalse(
            isReaderOpeningSessionStale(
                "source-a/book",
                "source-c/book",
                isRouteInitialized = true
            )
        )
    }

    @Test
    fun `automatic source change before the first rendered page also releases the gate`() {
        // 初始化先接管会话，再自动换源；首个有效视口可能在两步都完成之后才到达。
        // 因此不能依靠“曾渲染过初始 URL”来解除隔离。
        assertFalse(
            isReaderOpeningSessionStale(
                "source-a/book",
                "source-b/book",
                isRouteInitialized = true
            )
        )
    }

    @Test
    fun `notification and last read routes do not require a book url`() {
        for (routeBookUrl in listOf(null, "", "  ")) {
            assertFalse(
                isReaderOpeningSessionStale(
                    routeBookUrl,
                    "current-book",
                    isRouteInitialized = false
                )
            )
            assertFalse(isReaderOpeningSessionStale(routeBookUrl, null, isRouteInitialized = false))
        }
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
