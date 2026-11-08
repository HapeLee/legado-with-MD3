package io.legado.app.feature.settings.search

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.*
import org.junit.Test

class SettingsSearchViewModelTest {
    @Test fun `exact title precedes prefix alias description and path matches`() {
        val entries = listOf(
            SettingsSearchEntry("path", "Other", "Volume"),
            SettingsSearchEntry("description", "Other", "Reading", description = "Volume"),
            SettingsSearchEntry("alias", "Buttons", "Reading", keywords = "Volume"),
            SettingsSearchEntry("prefix", "Volume while playing", "Reading"),
            SettingsSearchEntry("exact", "Volume", "Reading"),
        )
        assertEquals(listOf("exact", "prefix", "alias", "description", "path"),
            searchSettings(entries, " volume ").map { it.id })
    }

    @Test fun `all tokens must match across fields and blank queries return no results`() {
        val entries = listOf(
            SettingsSearchEntry("volume", "音量键翻页", "阅读设置 › 翻页控制", keywords = "实体按键"),
            SettingsSearchEntry("other", "音量键", "其他设置"),
        )
        assertEquals(listOf("volume"), searchSettings(entries, " 阅读设置\t音量键 ").map { it.id })
        assertEquals(listOf("volume"), searchSettings(entries, "实体按键").map { it.id })
        assertTrue(searchSettings(entries, "音量键 不存在").isEmpty())
        assertTrue(searchSettings(entries, "\n  ").isEmpty())
    }

    @Test fun `English matching does not depend on system locale and ties keep catalog order`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val entries = listOf(
                SettingsSearchEntry("a", "LIGHT", "Reading"),
                SettingsSearchEntry("b", "Light", "Reading"),
            )
            assertEquals(listOf("a", "b"), searchSettings(entries, "light").map { it.id })
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `query survives state owner recreation and language change rebuilds results`() {
        val handle = SavedStateHandle()
        val english = persistentListOf(SettingsSearchEntry("volume", "Volume keys", "Reading"))
        val original = SettingsSearchViewModel(handle)
        original.onIntent(SettingsSearchIntent.IndexChanged(english))
        original.onIntent(SettingsSearchIntent.QueryChanged("Volume"))
        assertEquals("volume", original.uiState.value.results.single().id)

        val recreated = SettingsSearchViewModel(handle)
        assertEquals("Volume", recreated.uiState.value.query)
        recreated.onIntent(SettingsSearchIntent.IndexChanged(english))
        assertEquals("volume", recreated.uiState.value.results.single().id)
        recreated.onIntent(SettingsSearchIntent.IndexChanged(persistentListOf(
            SettingsSearchEntry("volume", "音量键翻页", "阅读设置"),
        )))
        assertTrue(recreated.uiState.value.results.isEmpty())
        recreated.onIntent(SettingsSearchIntent.QueryChanged("音量"))
        assertEquals("volume", recreated.uiState.value.results.single().id)
        recreated.onIntent(SettingsSearchIntent.QueryChanged(""))
        assertTrue(recreated.uiState.value.results.isEmpty())
    }
}
