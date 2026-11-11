package io.legado.app.ui.rss.source.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RSS 源编辑命令菜单迁移前后的行为基线：稳定 ID 集合、ID → Intent 映射必须与旧
 * `RoundDropdownMenu` 的 9 个条目逐项对齐，未知 ID 不得静默误触发。
 */
class RssSourceEditMenuActionsTest {
    @Test
    fun everyActionHasAnIntent() {
        RssSourceEditMenuAction.entries.forEach { action ->
            assertNotNull("${action.id} 缺少 Intent 映射", rssSourceEditIntentFor(action.id))
        }
    }

    @Test
    fun idsAreUniqueAndStable() {
        val ids = RssSourceEditMenuAction.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                "login", "auto_complete", "copy_source", "paste_source",
                "set_source_variable", "str_share", "cookie", "log", "help",
            ),
            ids,
        )
    }

    @Test
    fun mappingMatchesLegacyMenu() {
        assertEquals(RssSourceEditIntent.SaveLogin, rssSourceEditIntentFor("login"))
        assertEquals(
            RssSourceEditIntent.ToggleAutoComplete,
            rssSourceEditIntentFor("auto_complete"),
        )
        assertEquals(RssSourceEditIntent.Copy, rssSourceEditIntentFor("copy_source"))
        assertEquals(RssSourceEditIntent.Paste, rssSourceEditIntentFor("paste_source"))
        assertEquals(RssSourceEditIntent.SetVariable, rssSourceEditIntentFor("set_source_variable"))
        assertEquals(RssSourceEditIntent.Share, rssSourceEditIntentFor("str_share"))
        assertEquals(RssSourceEditIntent.ClearCookie, rssSourceEditIntentFor("cookie"))
        assertEquals(RssSourceEditIntent.ShowLog, rssSourceEditIntentFor("log"))
        assertEquals(RssSourceEditIntent.Help, rssSourceEditIntentFor("help"))
    }

    @Test
    fun unknownIdProducesNoAction() {
        assertNull(rssSourceEditIntentFor("unknown"))
        assertNull(rssSourceEditIntentFor(""))
    }
}
