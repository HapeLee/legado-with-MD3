package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainNavigatorReadAloudTest {

    @Test
    fun `opens cloud TTS manager on top of reader`() {
        val reader = MainRouteReadBook(bookUrl = "book")
        val cloudTts = MainRouteCloudTtsEngines(bookUrl = "book")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        MainNavigator.navigateToRoute(backStack, MainRouteCloudTtsEngines())

        assertEquals(listOf(MainRouteHome, reader, MainRouteCloudTtsEngines()), backStack)
    }

    @Test
    fun `resets to home before cloud TTS manager from unrelated route`() {
        val cloudTts = MainRouteCloudTtsEngines()
        val backStack = mutableListOf<NavKey>(
            MainRouteHome,
            MainRouteSettings,
        )

        MainNavigator.navigateToRoute(backStack, MainRouteCloudTtsEngines())

        assertEquals(listOf(MainRouteHome, MainRouteCloudTtsEngines()), backStack)
    }

    @Test
    fun `read aloud entry does not stack a second reader on top of the reader`() {
        val reader = MainRouteReadBook(bookUrl = "book", sharedCoverKey = "cover")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        // 朗读通知解析出来的路由键不带 bookUrl（空 = 最后读过的那本），和栈顶那份永不相等。
        MainNavigator.navigateToRoute(backStack, MainRouteReadBook(readAloud = true))

        assertEquals(listOf(MainRouteHome, reader), backStack)
    }

    @Test
    fun `read aloud entry for the same book does not stack a second reader`() {
        val reader = MainRouteReadBook(bookUrl = "book")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook(bookUrl = "book", readAloud = true))

        assertEquals(listOf(MainRouteHome, reader), backStack)
    }

    @Test
    fun `reader route still opens when another book is on top`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteReadBook(bookUrl = "A"))

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook(bookUrl = "B"))

        // 换书不是叠一层：阅读页单例语义把栈收到书架 + 新书，返回直接回书架。
        assertEquals(listOf(MainRouteHome, MainRouteReadBook(bookUrl = "B")), backStack)
    }

    @Test
    fun `read aloud entry from another page still opens the reader`() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)

        MainNavigator.navigateToRoute(backStack, MainRouteReadBook(readAloud = true))

        assertEquals(listOf(MainRouteHome, MainRouteReadBook(readAloud = true)), backStack)
    }

    @Test
    fun `read aloud notification reuses the launcher activity`() {
        val source = mainSourceFile("io/legado/app/service/BaseReadAloudService.kt").readText()
        val body = functionBody(
            source,
            "private fun readAloudActivityPendingIntent",
            endMarker = "\n    )",
        )

        // 主界面是 standard launchMode：缺这两个 flag，每次从通知点回来都再开一个 MainActivity。
        assertTrue(body.contains("FLAG_ACTIVITY_NEW_TASK"))
        assertTrue(body.contains("FLAG_ACTIVITY_SINGLE_TOP"))
    }

    private companion object {
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

        /** 取从 `signature` 起、止于下一个 `endMarker` 的源码文本。 */
        fun functionBody(source: String, signature: String, endMarker: String): String {
            val start = source.indexOf(signature)
            assertTrue("$signature not found", start >= 0)
            val rest = source.substring(start)
            val end = rest.indexOf(endMarker, signature.length)
            return if (end < 0) rest else rest.substring(0, end)
        }
    }
}
