package io.legado.app.ui.main

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 路由注册表不变式：每一个 `MainRoute` 目的地都必须在 [MainNavGraph] 里有对应的 `entry<>`。
 *
 * Navigation 3 对未注册的 key 只在运行时抛 `IllegalStateException("Unknown screen …")`，
 * 编译与 lint 都不报错。合并上游时整份取对方的 `MainNavGraph.kt` 就会静默删掉我们的入口
 * （第八十六轮：朗读规则 hub 的 7 个 entry 全丢，点开即崩）。这条测试把该失效模式变成
 * 构建期失败。
 */
class MainNavRouteRegistryTest {

    @Test
    fun `every declared MainRoute has a NavGraph entry`() {
        val declared = collectDeclaredRoutes()
        val registered = entryRegex.findAll(navGraphSource())
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "以下目的地没有 entry<>，点开必崩：\n" +
                (declared - registered).toSortedSet().joinToString("\n") { "  $it" },
            registered.containsAll(declared)
        )
    }

    private fun collectDeclaredRoutes(): Set<String> {
        val declaration = Regex(
            """(?:^|\s)(?:data\s+)?(?:class|object)\s+(MainRoute\w*)\b"""
        )
        val routes = mutableSetOf<String>()
        sourceRoot().walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            declaration.findAll(file.readText()).forEach { match ->
                val name = match.groupValues[1]
                // MainRouteConst 是常量容器，不是目的地。
                if (name != "MainRouteConst") routes += name
            }
        }
        return routes
    }

    /**
     * 阅读页/详情页这类覆盖层页面还能再往上架子页。子页若不带覆盖层元数据，
     * `ModalOverlaySceneStrategy` 就整体退成 SinglePane：下层阅读页被拆掉组合
     * （先露书架、返回再从书架重开封面形变）。这条守住「白名单里的子页必须也是覆盖层」。
     */
    @Test
    fun `routes pushable on top of an overlay page carry overlay metadata`() {
        val pushable = routesPushableOverOverlay()
        val graph = navGraphSource()
        val plain = pushable.filterNot { route ->
            Regex("""entry<$route>\(metadata = ModalOverlaySceneStrategy\.\w+""").containsMatchIn(graph)
        }
        assertTrue(
            "这些子页压在阅读页上会把它整页拆掉，需要 modalOverlay()/pageSlide() 元数据：\n" +
                plain.joinToString("\n") { "  $it" },
            plain.isEmpty()
        )
    }

    /**
     * 从 `MainNavigator` 里读出「栈顶是阅读页时直接压栈」那一支的目的地名单。
     * 锚文本没了就说明导航器结构变了，这条测试要跟着改，不能静默放过。
     */
    private fun routesPushableOverOverlay(): Set<String> {
        val lines = File(sourceRoot(), "io/legado/app/ui/main/MainNavigator.kt")
            .readText().split('\n')
        val anchor = lines.indexOfFirst { "currentRoute is MainRouteReadBook" in it }
        assertTrue("MainNavigator 里找不到「栈顶是阅读页」那条白名单，解析口径要重新对", anchor >= 0)
        val head = ((anchor - 1) downTo 0).first { "-> {" in lines[it] }
        val routes = LinkedHashSet<String>()
        // 分支头那一行自己也写着最后一个目的地。
        Regex("""(MainRoute\w+)\s*->""").find(lines[head])?.let { routes += it.groupValues[1] }
        var index = head - 1
        while (index >= 0) {
            val match = Regex("""^\s*(?:is\s+)?(MainRoute\w+),\s*$""").find(lines[index])
                ?: break
            routes += match.groupValues[1]
            index--
        }
        assertTrue("解析不出任何目的地，解析口径要重新对", routes.isNotEmpty())
        return routes
    }

    private fun navGraphSource(): String =
        File(sourceRoot(), "io/legado/app/ui/main/MainNavGraph.kt").readText()

    /** 单测的工作目录随 Gradle 调用方式变化（模块目录或仓库根），向上找 src/main/java。 */
    private fun sourceRoot(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (candidate in listOf(
                File(directory, "app/src/main/java"),
                File(directory, "src/main/java")
            )) {
                if (candidate.isDirectory) return candidate
            }
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 src/main/java")
    }

    private companion object {
        val entryRegex = Regex("""\bentry<(\w+)>""")
    }
}
