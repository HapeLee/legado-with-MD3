package io.legado.app.ui.book.source.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 书源编辑命令菜单迁移前后的行为基线：稳定 ID 集合、ID → Intent 映射必须与旧
 * `RoundDropdownMenu` 的 10 个条目逐项对齐，未知 ID 不得静默误触发。
 */
class BookSourceEditMenuActionsTest {
    @Test
    fun everyActionHasAnIntent() {
        BookSourceEditMenuAction.entries.forEach { action ->
            assertNotNull("${action.id} 缺少 Intent 映射", bookSourceEditIntentFor(action.id))
        }
    }

    @Test
    fun idsAreUniqueAndStable() {
        val ids = BookSourceEditMenuAction.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                "login", "search", "cookie", "auto_complete", "copy_source",
                "paste_source", "set_source_variable", "str_share", "log", "help",
            ),
            ids,
        )
    }

    @Test
    fun mappingMatchesLegacyMenu() {
        assertEquals(BookSourceEditIntent.SaveAndLogin, bookSourceEditIntentFor("login"))
        assertEquals(BookSourceEditIntent.SaveAndSearch, bookSourceEditIntentFor("search"))
        assertEquals(BookSourceEditIntent.ClearCookie, bookSourceEditIntentFor("cookie"))
        assertEquals(
            BookSourceEditIntent.ToggleAutoComplete,
            bookSourceEditIntentFor("auto_complete"),
        )
        assertEquals(BookSourceEditIntent.Copy, bookSourceEditIntentFor("copy_source"))
        assertEquals(BookSourceEditIntent.Paste, bookSourceEditIntentFor("paste_source"))
        assertEquals(
            BookSourceEditIntent.SaveAndSetVariable,
            bookSourceEditIntentFor("set_source_variable"),
        )
        assertEquals(BookSourceEditIntent.Share, bookSourceEditIntentFor("str_share"))
        assertEquals(BookSourceEditIntent.ShowLog, bookSourceEditIntentFor("log"))
        assertEquals(BookSourceEditIntent.ShowHelp, bookSourceEditIntentFor("help"))
    }

    @Test
    fun unknownIdProducesNoAction() {
        assertNull(bookSourceEditIntentFor("unknown"))
        assertNull(bookSourceEditIntentFor(""))
    }
}
