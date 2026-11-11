package io.legado.app.ui.book.toc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 目录页顶栏菜单迁移前后的行为基线：稳定 ID 集合、ID → 行为映射必须与旧
 * `dropDownMenuContent` 的 10 个条目逐项对齐，未知 ID 不得静默误触发。
 */
class TocMenuActionsTest {
    @Test
    fun everyActionHasABehavior() {
        TocMenuAction.entries.forEach { action ->
            assertNotNull("${action.id} 缺少行为映射", tocMenuBehaviorFor(action.id))
        }
    }

    @Test
    fun idsAreUniqueAndStable() {
        val ids = TocMenuAction.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                "use_replace", "show_word_count", "reverse_toc", "update_toc",
                "replace_rule", "add_replace_rule", "local_toc_rule",
                "split_long_chapters", "export_bookmarks_json",
                "export_bookmarks_markdown",
            ),
            ids,
        )
    }

    @Test
    fun intentActionsMapToLegacyMenu() {
        assertEquals(
            TocMenuBehavior.Dispatch(TocIntent.ToggleUseReplace),
            tocMenuBehaviorFor("use_replace"),
        )
        assertEquals(
            TocMenuBehavior.Dispatch(TocIntent.ToggleShowWordCount),
            tocMenuBehaviorFor("show_word_count"),
        )
        assertEquals(
            TocMenuBehavior.Dispatch(TocIntent.ReverseToc),
            tocMenuBehaviorFor("reverse_toc"),
        )
        assertEquals(
            TocMenuBehavior.Dispatch(TocIntent.UpdateToc),
            tocMenuBehaviorFor("update_toc"),
        )
        assertEquals(
            TocMenuBehavior.Dispatch(TocIntent.ToggleSplitLongChapter),
            tocMenuBehaviorFor("split_long_chapters"),
        )
    }

    @Test
    fun navigationAndExportActionsKeepTheirKind() {
        assertEquals(TocMenuBehavior.OpenReplaceRuleList, tocMenuBehaviorFor("replace_rule"))
        assertEquals(TocMenuBehavior.OpenNewReplaceRule, tocMenuBehaviorFor("add_replace_rule"))
        assertEquals(TocMenuBehavior.EditLocalTocRule, tocMenuBehaviorFor("local_toc_rule"))
        assertEquals(
            TocMenuBehavior.ExportBookmarks(isMarkdown = false),
            tocMenuBehaviorFor("export_bookmarks_json"),
        )
        assertEquals(
            TocMenuBehavior.ExportBookmarks(isMarkdown = true),
            tocMenuBehaviorFor("export_bookmarks_markdown"),
        )
    }

    @Test
    fun unknownIdProducesNoAction() {
        assertNull(tocMenuBehaviorFor("unknown"))
        assertNull(tocMenuBehaviorFor(""))
    }
}
