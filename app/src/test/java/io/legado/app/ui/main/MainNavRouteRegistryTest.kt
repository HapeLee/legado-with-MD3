package io.legado.app.ui.main

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 路由注册表不变式：每一个 `MainRoute` 目的地都必须在 [MainNavGraph] 里有对应的 `entry<>`。
 *
 * Navigation 3 对未注册的 key 只在运行时抛 `IllegalStateException("Unknown screen …")`，
 * 编译与 lint 都不报错。合并上游时若整份取对方的 `MainNavGraph.kt`，我们的入口会被
 * 静默删掉、点开即崩。这条测试把该失效模式变成构建期失败。
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
     * 可压在书籍页之上的子页必须是普通 `entry<>`：走 NavDisplay 全局转场，与项目其余页面一致。
     *
     * 这些子页曾用 `pageSlide()` 覆盖层实现（那是书籍页自身也是覆盖层的时期）：好处是下层保持
     * 组合，代价是它们自行接管场景与返回 —— 动画与其余页面不一致、返回所有权分裂成两套。
     * 现在书籍页本身是普通 nav3 目的地、封面飞行交给共享元素，子页用普通入口才是一致的。
     */
    @Test
    fun `routes pushable on top of a book page stay plain entries`() {
        // 白名单里同时含书籍页自身（详情页可压在阅读页上），那不是子页，要单独校验。
        val pushable = routesPushableOverBookPage() - bookPageRoutes
        val graph = navGraphSource()
        val decorated = pushable.filter { route ->
            Regex("""entry<$route>\(\s*$overlayMetadataPattern""").containsMatchIn(graph)
        }
        assertTrue(
            "这些子页必须走全局转场（不要覆盖层场景元数据），否则会自行接管动画与返回：\n" +
                    decorated.joinToString("\n") { "  $it" },
            decorated.isEmpty()
        )
        // 书籍页同样必须是普通目的地：带上覆盖层场景会让下层常驻、转场与其余页面分裂。
        val decoratedBookPages = bookPageRoutes.filter { route ->
            Regex("""entry<$route>\(\s*$overlayMetadataPattern""").containsMatchIn(graph)
        }
        assertTrue(
            "书籍页也必须是普通 nav3 目的地（不要覆盖层场景元数据）：\n" +
                    decoratedBookPages.joinToString("\n") { "  $it" },
            decoratedBookPages.isEmpty()
        )
    }

    /**
     * 从 `MainNavigator` 里读出「栈顶是阅读页时直接压栈」那一支的目的地名单。
     * 锚点只在 `when (route)` 之后找：压栈之前还有若干去重闸门，它们也提阅读页，
     * 认错了行会顺着上一条分支解析出一份假名单。
     * 锚文本没了就说明导航器结构变了，这条测试要跟着改，不能静默放过。
     */
    private fun routesPushableOverBookPage(): Set<String> {
        val lines = File(sourceRoot(), "io/legado/app/ui/main/MainNavigator.kt")
            .readText().split('\n')
        val whenLine = lines.indexOfFirst { it.trim() == "when (route) {" }
        assertTrue("MainNavigator 里找不到 when (route) 分支，解析口径要重新对", whenLine >= 0)
        val anchor = (whenLine + 1 until lines.size)
            .firstOrNull { "currentRoute is MainRouteReadBook" in lines[it] } ?: -1
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
        return routes + setOf(
            "MainRouteToc",
            "MainRouteBookInfoEdit",
            "MainRouteReplaceRules",
            "MainRouteReplaceEdit",
            // 从详情页压上来的书内子页：同样必须自带覆盖层场景。
            "MainRouteBookCharacterDetail",
            "MainRouteBookCharacterNetwork",
            "MainRouteBookCharacterList",
            "MainRouteBookVoiceCasting",
            "MainRouteCloudTtsEngines",
            "MainRouteTtsCache",
            "MainRouteBookKnowledgeList",
            "MainRouteBookKnowledgeDetail",
            "MainRouteBookEventList",
            "MainRouteBookEventDetail",
        )
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

        /** 书籍页本身：它们是普通目的地，不参与子页名单。 */
        val bookPageRoutes = setOf(
            "MainRouteReadBook", "MainRouteReadManga", "MainRouteBookInfo"
        )

        /**
         * 覆盖层场景元数据（会自行接管场景与返回）。只匹配这一类，避免把无关的
         * `metadata { put(...) }`（例如 ViewModel key 或纯转场元数据）误判成覆盖层。
         */
        const val overlayMetadataPattern =
            """metadata\s*=\s*(?:ModalOverlaySceneStrategy\.\w+|modalOverlayEntryMetadata)"""
    }
}
