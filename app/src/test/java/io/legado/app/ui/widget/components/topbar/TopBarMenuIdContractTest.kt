package io.legado.app.ui.widget.components.topbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 顶栏 `dropDownMenuContent` → `menuGroups` 迁移的静态契约。
 *
 * 这批菜单的条目与副作用是直白的 `when(id)` / `if (id == ...)` 分派，拼错 ID 不会编译失败，
 * 只会运行时静默不响应。本测试不替代业务副作用基线，只锁住最容易回归的三件事：
 * 迁移后不再手画 content、每个声明的字面量 ID 都有分派、动态分组仍用 `group:` 稳定前缀。
 *
 * `TocScreen` 例外：它用 `TocMenuAction` 注册表 + `tocMenuBehaviorFor`，行为映射由
 * `TocMenuActionsTest` 的 5 例纯 JVM 基线逐项覆盖，本类只断言它也已离开 content 槽。
 */
class TopBarMenuIdContractTest {

    @Test
    fun migratedMenusUseDeclarativeGroupsOnly() {
        MIGRATED.forEach { path ->
            val source = sourceOf(path)
            assertTrue("$path 缺少 menuGroups", source.contains("menuGroups = persistentListOf("))
            assertFalse(
                "$path 仍传 dropDownMenuContent；TopBarActionMenu 会静默忽略它",
                source.contains("dropDownMenuContent"),
            )
            assertTrue("$path 缺少 onMenuAction 分派", source.contains("onMenuAction = { id ->"))
        }
    }

    @Test
    fun everyDeclaredActionIdHasADispatchBranch() {
        MIGRATED.forEach { path ->
            val source = sourceOf(path)
            val ids = ACTION_ID.findAll(source).map { it.groupValues[1] }.toList()
            if (path !in GROUP_ONLY_MENUS) {
                assertTrue("$path 没有声明任何字面量动作 ID", ids.isNotEmpty())
            }
            ids.forEach { id ->
                val occurrences = Regex(""""${Regex.escape(id)}"""").findAll(source).count()
                assertTrue(
                    "$path 的 ID \"$id\" 只出现在 AppAction 声明处，onMenuAction 里没有对应分派",
                    occurrences >= 2,
                )
            }
        }
    }

    @Test
    fun dynamicGroupMenusKeepStablePrefixRoundTrip() {
        GROUP_PREFIX_MENUS.forEach { path ->
            val source = sourceOf(path)
            assertTrue("$path 丢失动态分组 ID 前缀", source.contains("\"group:\""))
            assertTrue("$path 缺少 startsWith 分派", source.contains("startsWith(\"group:\")"))
            assertTrue("$path 缺少 removePrefix 解码", source.contains("removePrefix(\"group:\")"))
        }
    }

    @Test
    fun registryDrivenTocMenuIsAlsoMigrated() {
        val source = sourceOf("io/legado/app/ui/book/toc/TocScreen.kt")
        assertFalse("TocScreen 仍传 dropDownMenuContent", source.contains("dropDownMenuContent"))
        assertTrue("TocScreen 缺少 menuGroups", source.contains("menuGroups ="))
        assertTrue(
            "TocScreen 应经 tocMenuBehaviorFor 分派（行为基线见 TocMenuActionsTest）",
            source.contains("tocMenuBehaviorFor(id)"),
        )
    }

    @Test
    fun onlyPseudoSubmenuKeepsTopBarContent() {
        val remaining = mainJavaRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("dropDownMenuContent = {") }
            .map { it.name }
            .sorted()
            .toList()
        assertEquals(
            "顶栏手画菜单应只剩伪子菜单那一处；新增 content 菜单请优先用 menuGroups",
            listOf("RssSourceScreen.kt"),
            remaining,
        )
    }

    private fun sourceOf(path: String): String = File(mainJavaRoot(), path).readText()

    private fun mainJavaRoot(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            for (prefix in listOf("src/main/java", "app/src/main/java")) {
                val candidate = File(directory, prefix)
                if (candidate.isDirectory) return candidate
            }
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 src/main/java")
    }

    private companion object {
        val MIGRATED = listOf(
            "io/legado/app/ui/rss/subscription/RuleSubScreen.kt",
            "io/legado/app/ui/highlightTagRule/HighlightTagRuleScreen.kt",
            "io/legado/app/ui/dict/rule/DictRuleScreen.kt",
            "io/legado/app/ui/book/toc/rule/TxtRuleScreen.kt",
            "io/legado/app/ui/main/rss/RssScreen.kt",
            "io/legado/app/ui/main/explore/ExploreScreen.kt",
            "io/legado/app/feature/explore/ExploreMainRoute.kt",
            "io/legado/app/ui/book/import/remote/RemoteBookScreen.kt",
            "io/legado/app/ui/book/import/local/ImportBookScreen.kt",
        )

        /** 只靠动态 `group:` ID 的菜单，没有其它字面量动作 ID。 */
        val GROUP_ONLY_MENUS = setOf(
            "io/legado/app/ui/main/explore/ExploreScreen.kt",
            "io/legado/app/feature/explore/ExploreMainRoute.kt",
        )

        val GROUP_PREFIX_MENUS = GROUP_ONLY_MENUS + setOf(
            "io/legado/app/ui/main/rss/RssScreen.kt",
            "io/legado/app/ui/rss/favorites/RssFavoritesScreen.kt",
        )

        val ACTION_ID = Regex("""AppAction\(\s*"([a-z_]+)"\s*,""")
    }
}
