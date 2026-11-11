package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十轮：`SmallPlainButton` 触发的列表项命令菜单迁到 [AppActionMenu] 的 `Row` 锚点。
 *
 * 三处原本都是 `SmallPlainButton(onClick = { showMenu = true })` + `RoundDropdownMenu`，
 * 条目动态、纯命令，trigger 是列表项 / 卡片里的小号图标按钮。迁到 `Row` 锚点后壳仍由
 * `SmallPlainButton(onClick = null)` 自己画，因此点击区与选中态不变。
 *
 * [CastRoleRowActions] 的 `selected = editing` 是**业务态**（该角色正在行内编辑），不是菜单
 * 展开态，纯展示 trigger 里必须原样保留。
 *
 * 静态契约只保证结构与 ID 一致性，**不证明弹层定位、真机外观与 TalkBack 行为**。
 */
class AppActionRowAnchorMigrationTest {

    @Test
    fun bookSourceActionsUseRowAnchor() {
        val source = sourceOf(BOOK_SOURCE)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未使用 Row 锚点", source.contains("anchor = AppMenuAnchor.Row"))
        assertTrue("缺少可访问名称", source.contains("title = stringResource(R.string.menu)"))
        IDS.forEach { id ->
            assertTrue("未声明 $id", source.contains("id = \"$id\""))
            assertTrue("缺少 $id 分派", source.contains("\"$id\" ->"))
        }
    }

    @Test
    fun castPoolGroupActionsUseRowAnchor() {
        val source = sourceOf(CAST_POOL)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍自带菜单开合状态", source.contains("showMenu"))
        assertTrue("未使用 Row 锚点", source.contains("anchor = AppMenuAnchor.Row"))
    }

    @Test
    fun castRoleActionsKeepBusinessSelectedState() {
        val source = sourceOf(CAST_ROLE)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未使用 Row 锚点", source.contains("anchor = AppMenuAnchor.Row"))
        assertTrue("editing 高亮丢失", source.contains("selected = editing"))
        assertTrue("删除项未标危险色", source.contains("destructive = true"))
    }

    /** 行级「更多」菜单原来用 `moreMenuBookUrl` 做单例展开，迁到 Row 锚点后状态整条消失。 */
    @Test
    fun bookshelfManageBookRowActionsUseRowAnchor() {
        val source = sourceOf(MANAGE)
        assertFalse("仍自带单例展开状态", source.contains("moreMenuBookUrl"))
        assertTrue("书行动作未用 Row 锚点", source.contains("anchor = AppMenuAnchor.Row"))
    }

    @Test
    fun audioPlayActionsUseRowAnchor() {
        val source = sourceOf(AUDIO_PLAY)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertFalse("仍自带菜单开合状态", source.contains("menuExpanded"))
        assertTrue("未使用 Row 锚点", source.contains("anchor = AppMenuAnchor.Row"))
        // 唤醒锁与媒体控制是开关态，不是「当前选中项」
        assertTrue("唤醒锁开关态丢失", source.contains("checked = state.wakeLockEnabled"))
        assertTrue("媒体控制开关态丢失", source.contains("checked = state.mediaControlEnabled"))
    }

    @Test
    fun displayOnlyTriggersAcceptNullOnClick() {
        listOf(
            "io/legado/app/ui/widget/components/button/series/SmallTonalButton.kt",
            "io/legado/app/ui/widget/components/button/series/MediumPlainButton.kt",
        ).forEach { path ->
            assertTrue(
                "$path 未支持纯展示形态",
                sourceOf(path).contains("onClick: (() -> Unit)?"),
            )
        }
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
        const val BOOK_SOURCE = "io/legado/app/ui/book/source/manage/BookSourceScreen.kt"
        const val CAST_POOL = "io/legado/app/ui/book/readaloud/cast/CastPoolWidgets.kt"
        const val CAST_ROLE = "io/legado/app/ui/book/readaloud/casting/CastRoleEditing.kt"
        const val MANAGE = "io/legado/app/ui/book/manage/BookshelfManageScreen.kt"
        const val AUDIO_PLAY = "io/legado/app/ui/book/audio/AudioPlayScreen.kt"
        val IDS = listOf("to_top", "to_bottom", "login", "search", "debug", "delete", "toggle_explore")
    }
}
