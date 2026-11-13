package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第三十三轮：`ReadBookMenuBarTitle` 的两处共用菜单迁成受控 [AppActionMenu]。
 *
 * - `ReadMenuSourceDropdown`（登录 / 章节付费 / 编辑书源 / 禁用书源）：三处入口共用一份，
 *   登录与付费是条件项，迁移时在构造 `actions` 阶段就过滤，不在菜单里留空位；
 * - `ReadMenuLongPressDropdown`（换源 2 项 / 刷新 2 项）：顶栏、底栏、浮动图标三处共用，
 *   `menu == null` 时不组合弹层——旧实现在这种情况下会开出一个空菜单。
 *
 * 两处旧实现的每一项都是「点完就关」，与入口「先 dismiss 再 onAction」一致，所以没有
 * `AppActionMenu` 不支持的「动作后保持展开」需求；`PillDivider` 也未出现在这两处。
 *
 * `ReadBookMenuBarFloating` 那处 overflow 菜单（巨型菜单 + 内嵌图片样式子菜单）在本轮被确认为
 * **从来没有调用方**（`private fun OverflowDropdownMenu`，自 2026-07-31 起只躺在文件里），
 * 因此直接删除而不迁移——里面 `reSegment` / `delRubyTag` / `delHTag` 三项「点击不关菜单」的
 * 语义也就无需让入口去支持。
 *
 * 静态契约只锁结构与 ID 映射，不证明弹层定位、四引擎外观或 TalkBack。
 */
class ReadMenuDropdownContractTest {

    /** 书源菜单：两个条件项 + 四个动作分支都必须保留。 */
    @Test
    fun sourceMenuKeepsConditionalItemsAndPayload() {
        val source = codeOf(TITLE_BAR)
        assertTrue("登录项丢了条件", source.contains("!bookSource?.loginUrl.isNullOrBlank()"))
        assertTrue("付费项丢了条件", source.contains("if (payVisible)"))
        listOf(
            "ReadMenuSourceLoginAction -> onLogin()",
            "ReadMenuSourcePayAction -> onPay()",
            "ReadMenuSourceEditAction -> onEdit()",
            "ReadMenuSourceDisableAction -> onDisable()",
        ).forEach { branch ->
            assertTrue("缺少分派：$branch", source.contains(branch))
        }
        listOf(
            "private const val ReadMenuSourceLoginAction = \"read_menu_source_login\"",
            "private const val ReadMenuSourcePayAction = \"read_menu_source_pay\"",
            "private const val ReadMenuSourceEditAction = \"read_menu_source_edit\"",
            "private const val ReadMenuSourceDisableAction = \"read_menu_source_disable\"",
        ).forEach { declaration ->
            assertTrue("缺少稳定 ID：$declaration", source.contains(declaration))
        }
    }

    /** 长按二级菜单：两个变体、四个 intent 都必须保留。 */
    @Test
    fun longPressMenuKeepsBothVariants() {
        val source = codeOf(TITLE_BAR)
        assertTrue(
            "换源变体丢失",
            source.contains("ReadMenuLongPressMenu.ChangeSource -> persistentListOf("),
        )
        assertTrue(
            "刷新变体丢失",
            source.contains("ReadMenuLongPressMenu.Refresh -> persistentListOf("),
        )
        listOf(
            "ReadMenuLongPressChangeBook -> onIntent(ReadBookIntent.MenuBookChangeSource)",
            "ReadMenuLongPressChangeChapter -> onIntent(ReadBookIntent.MenuChapterChangeSource)",
            "ReadMenuLongPressRefreshDur -> onIntent(ReadBookIntent.MenuRefreshDur)",
            "ReadMenuLongPressRefreshAfter -> onIntent(ReadBookIntent.MenuRefreshAfter)",
        ).forEach { branch ->
            assertTrue("缺少分派：$branch", source.contains(branch))
        }
        assertTrue("空菜单未短路", source.contains("if (menu == null) return"))
    }

    /** 两处都必须是受控弹层，且入口不再渲染自己的 trigger。 */
    @Test
    fun bothMenusAreControlledAppActionMenus() {
        val source = codeOf(TITLE_BAR)
        assertEquals("仍手画菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertEquals(
            "两处都应由调用方 expanded 驱动",
            2,
            Regex("embeddedExpanded = expanded").findAll(source).count(),
        )
        assertEquals(
            "两处受控弹层都不应渲染 trigger",
            2,
            Regex("trigger = \\{\\}").findAll(source).count(),
        )
        assertFalse("仍残留只服务旧菜单的子组件", source.contains("ChangeSourceMenuItems"))
        assertFalse("仍残留只服务旧菜单的子组件", source.contains("RefreshMenuItems"))
        assertFalse("仍引用旧菜单项图标壳", source.contains("MenuItemIcon"))
    }

    /** 六处调用点各自把所在 Box 交给弹层做锚点。 */
    @Test
    fun everyCallSiteProvidesAnchorContainer() {
        val title = codeOf(TITLE_BAR)
        assertEquals(
            "书源菜单两处调用都应传锚点",
            2,
            Regex(
                "ReadMenuSourceDropdown\\(\\s*expanded = sourceMenuExpanded,\\s*" +
                    "modifier = Modifier\\.matchParentSize\\(\\)"
            ).findAll(title).count(),
        )
        assertTrue(
            "顶栏图标长按入口未传锚点",
            Regex(
                "ReadMenuLongPressDropdown\\(\\s*menu = longPressMenu,\\s*" +
                    "expanded = expandedMenuId == def\\.id,\\s*" +
                    "modifier = Modifier\\.matchParentSize\\(\\)"
            ).findAll(title).count() == 1,
        )
        assertTrue(
            "书籍抽屉入口未传锚点",
            Regex(
                "ReadMenuSourceDropdown\\(\\s*expanded = sourceMenuExpanded,\\s*" +
                    "modifier = Modifier\\.matchParentSize\\(\\)"
            ).findAll(codeOf(BOOK_SHEET)).count() == 1,
        )
        assertTrue(
            "底栏工具按钮入口未传锚点",
            Regex(
                "ReadMenuLongPressDropdown\\(\\s*menu = button\\.longPressMenu,\\s*" +
                    "expanded = expandedMenu,\\s*" +
                    "modifier = Modifier\\.matchParentSize\\(\\)"
            ).findAll(codeOf(TOOL_BAR)).count() == 1,
        )
        assertTrue(
            "浮动图标入口未传锚点",
            Regex(
                "ReadMenuLongPressDropdown\\(\\s*menu = iconDef\\.longPressMenu,\\s*" +
                    "expanded = expandedMenu,\\s*" +
                    "modifier = Modifier\\.matchParentSize\\(\\)"
            ).findAll(codeOf(FLOATING_BAR)).count() == 1,
        )
    }

    /**
     * `ReadBookMenuBarFloating` 的 overflow 菜单没有调用方，本轮连函数一起删除；这条断言防它
     * 被重新长出来（它内含「点击不关菜单」的开关项，一旦复活就会撞上入口「先关再回调」的语义）。
     */
    @Test
    fun deadFloatingOverflowMenuStaysDeleted() {
        val source = codeOf(FLOATING_BAR)
        assertEquals("仍保留旧菜单", 0, Regex("RoundDropdownMenu").findAll(source).count())
        assertFalse("仍保留无调用方的私有菜单函数", source.contains("OverflowDropdownMenu"))
        assertFalse("仍引用旧菜单项图标壳", source.contains("MenuItemIcon"))
        assertTrue("已迁移的长按入口丢失", source.contains("ReadMenuLongPressDropdown("))
    }

    private fun codeOf(path: String): String = sourceOf(path)
        .lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

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
        const val TITLE_BAR = "io/legado/app/ui/book/read/ReadBookMenuBarTitle.kt"
        const val BOOK_SHEET = "io/legado/app/ui/book/read/sheet/ReaderBookSheet.kt"
        const val TOOL_BAR = "io/legado/app/ui/book/read/ReadBookMenuBarTool.kt"
        const val FLOATING_BAR = "io/legado/app/ui/book/read/ReadBookMenuBarFloating.kt"
    }
}
