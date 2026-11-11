package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `AllBookmarkScreen` 顶栏导出菜单的迁移契约。
 *
 * 旧实现是 `TopBarActionButton(onClick = { showMenu = true })` + `RoundDropdownMenu`，
 * 两项纯命令（导出 JSON / 导出 Markdown），无勾选、无分组、无子菜单、无动态文案。
 *
 * 迁到 [AppActionMenu] 的 `Icon` 锚点形态属于**零外观变化**的一类：该分支在 Mochi 下穿的
 * `MochiBarIconButton` 就是 `TopBarActionButton` 的 Mochi 实现，COUI 下走 `CouiTopAppBarButton`，
 * Miuix 下由库渲染 Icon 按钮——三种壳与同排按钮一致，因此不引入新的外观基线。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位与 TalkBack 行为**。
 */
class AllBookmarkMenuContractTest {

    @Test
    fun topBarExportMenuUsesDeclarativeEntrypoint() {
        val source = sourceOf(SCREEN)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍自带菜单开合状态", source.contains("var showMenu"))
        assertTrue("缺少 AppActionMenu", source.contains("AppActionMenu("))
        assertTrue("trigger 未改为纯展示 AppIcon", source.contains("AppIcon("))
    }

    @Test
    fun everyExportActionHasDeclarationAndDispatchBranch() {
        val source = sourceOf(SCREEN)
        IDS.forEach { id ->
            assertTrue("未声明 $id", source.contains("id = \"$id\""))
            assertTrue("缺少 $id 的分派分支", source.contains("\"$id\" ->"))
        }
        assertEquals(
            "导出命令应保持两项",
            2,
            Regex("\"export_\\w+\" ->").findAll(source).count(),
        )
    }

    /** 同文件其它动作仍是独立按钮，本轮不涉及。 */
    @Test
    fun unrelatedTopBarActionsStayUntouched() {
        val source = sourceOf(SCREEN)
        assertEquals(
            "搜索按钮应保持非菜单形态",
            1,
            Regex("TopBarActionButton\\(").findAll(source).count(),
        )
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCREEN = "io/legado/app/ui/book/bookmark/AllBookmarkScreen.kt"
        val IDS = listOf("export_json", "export_markdown")
    }
}
