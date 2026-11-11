package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 详情页顶栏「更多」菜单迁移契约。
 *
 * 旧实现是 `TopBarActionButton(onClick) + BookInfoOverflowMenu(...)`
 * （内部再 `RoundDropdownMenu`，19 个条件项，勾选态用 `isSelected`）。
 * 迁移后由 `TopBarActionMenu` 渲染按钮与弹层，条目 ID = `book_` + `BookInfoMenuAction.name`，
 * 分派时用枚举反查，避免手写 ID 与枚举漂移。
 *
 * 条件可见性（自定义按钮 / 在书架 / 本地书 / 书源 / 私有锁）与勾选态必须逐条保留；
 * 静态契约只验证「ID 与枚举绑定」，**不证明真机条件分支与勾选渲染**。
 */
class BookInfoMenuContractTest {

    @Test
    fun overflowMenuUsesDeclarativeEntrypoint() {
        val source = sourceOf(SCREEN)
        assertTrue("缺少 TopBarActionMenu", source.contains("TopBarActionMenu("))
        assertFalse("旧的 BookInfoOverflowMenu 仍被调用", source.contains("BookInfoOverflowMenu("))
        assertFalse(
            "迁移后不应再自己维护更多菜单的展开状态",
            source.contains("onClick = { expanded = true }"),
        )
    }

    @Test
    fun everyMenuItemIsBoundToEnum() {
        val source = sourceOf(SCREEN)
        ITEMS.forEach { name ->
            assertTrue(
                "菜单项 $name 未绑定到 BookInfoMenuAction.$name",
                source.contains("BookInfoMenuAction.$name.name"),
            )
        }
        assertTrue("缺少 ID 前缀", source.contains("MENU_PREFIX"))
        assertTrue(
            "分派未走枚举反查，ID 与枚举可能漂移",
            source.contains("BookInfoMenuAction.entries"),
        )
    }

    @Test
    fun checkedStateComesFromDomainState() {
        val source = sourceOf(SCREEN)
        assertTrue("允许更新缺少勾选态", source.contains("checked = book.canUpdate"))
        assertTrue("长章拆分缺少勾选态", source.contains("checked = book.splitLongChapter"))
        assertTrue("删除提示缺少勾选态", source.contains("checked = state.deleteAlertEnabled"))
        assertTrue("私密标记缺少勾选态", source.contains("checked = state.bookPrivate"))
        assertTrue(
            "禁用阅读记录缺少勾选态",
            source.contains("checked = book.disableReadRecord"),
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
        const val SCREEN = "io/legado/app/ui/book/info/BookInfoScreen.kt"
        val ITEMS = listOf(
            "CustomButton",
            "Edit",
            "Refresh",
            "ReadRecord",
            "ToggleReadRecordDisabled",
            "SyncRemote",
            "Upload",
            "Login",
            "SetSourceVariable",
            "SetBookVariable",
            "CopyBookUrl",
            "CopyTocUrl",
            "Top",
            "ToggleCanUpdate",
            "ToggleSplitLongChapter",
            "ToggleDeleteAlert",
            "ClearCache",
            "ShowLog",
            "TogglePrivate",
        )
    }
}
