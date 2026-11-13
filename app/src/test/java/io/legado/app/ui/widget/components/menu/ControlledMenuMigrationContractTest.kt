package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第二十三轮：两处「展开态还要驱动页面其它行为」的菜单迁到 [AppActionMenu] 受控弹层模式。
 *
 * - 播放页 `menuExpanded` 还驱动「播放时自动隐藏控制栏」，触发按钮与开合都必须留在页面。
 * - 阅读器目录页用 `CompactToolIconBox(selected = menuExpanded)` 给工具按钮做展开态高亮。
 *
 * 静态契约只保证结构、受控参数与 ID 声明/分派一致；弹层定位、四引擎外观、TalkBack
 * 与真机触点仍需人工复核（COUI 在 Robolectric 下退回 Material 分支）。
 */
class ControlledMenuMigrationContractTest {

    @Test
    fun videoTopBarMenuIsControlledAndIdsAreDispatched() {
        val source = sourceOf(VIDEO_PLAYER)
        assertFalse("播放页仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = menuExpanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = onMenuDismiss"))
        assertTrue("受控模式下 trigger 应为空", source.contains("trigger = {}"))
        assertTrue("触发按钮应留在页面", source.contains("onClick = onMenuExpand"))
        VIDEO_IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
    }

    @Test
    fun readerBookSheetMenusAreAllControlled() {
        val source = sourceOf(READER_BOOK_SHEET)
        assertEquals(
            "目录页与书签页都迁完后，本文件不应再手画 RoundDropdownMenu",
            0,
            Regex("RoundDropdownMenu\\(").findAll(source).count(),
        )
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = menuExpanded"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = { menuExpanded = false }"))
        assertTrue("工具按钮展开态高亮丢失", source.contains("selected = menuExpanded"))
        assertTrue("独立开关应走 checked", source.contains("checked = if (action =="))
        READER_IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
        READER_BOOKMARK_IDS.forEach { (constant, id) ->
            assertTrue("未声明 $id", source.contains("$constant(\"$id\","))
            assertTrue("缺少 $constant 分派", source.contains("$constant.id ->"))
        }
    }

    @Test
    fun bothEntriesDeclareSameControlledContract() {
        listOf(APP_ACTION_MENU, APP_CHOICE_DROPDOWN).forEach { path ->
            val source = sourceOf(path)
            assertTrue("$path 缺 embeddedExpanded", source.contains("embeddedExpanded: Boolean? = null"))
            assertTrue("$path 缺 onDismissRequest", source.contains("onDismissRequest: () -> Unit = {}"))
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
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
        const val APP_CHOICE_DROPDOWN = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val VIDEO_PLAYER = "io/legado/app/ui/book/video/VideoPlayerScreen.kt"
        const val READER_BOOK_SHEET = "io/legado/app/ui/book/read/sheet/ReaderBookSheet.kt"
        val VIDEO_IDS = listOf(
            "CopyPlayUrl" to "copy_play_url",
            "OpenExternalPlayer" to "open_external_player",
            "ToggleFloating" to "toggle_floating",
            "PictureInPicture" to "picture_in_picture",
            "Settings" to "settings",
            "CustomButton" to "custom_button",
            "SourceLogin" to "source_login",
            "EditSource" to "edit_source",
        )
        val READER_IDS = listOf(
            "DownloadAll" to "download_all",
            "ExpandAllVolumes" to "expand_all_volumes",
            "CollapseAllVolumes" to "collapse_all_volumes",
            "UpdateToc" to "update_toc",
            "TxtTocRule" to "txt_toc_rule",
            "ToggleSplitLongChapter" to "toggle_split_long_chapter",
        )
        val READER_BOOKMARK_IDS = listOf(
            "ExportJson" to "export_bookmarks_json",
            "ExportMarkdown" to "export_bookmarks_markdown",
        )
    }
}
