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
