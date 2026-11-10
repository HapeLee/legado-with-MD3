package io.legado.app.feature.settings.search

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class SettingsSearchEntry(
    val id: String,
    val title: String,
    val path: String,
    val description: String = "",
    val keywords: String = "",
)

@Stable
data class SettingsSearchUiState(
    val query: String = "",
    val results: PersistentList<SettingsSearchEntry> = persistentListOf(),
)

sealed interface SettingsSearchIntent {
    data class QueryChanged(val query: String) : SettingsSearchIntent
    data class IndexChanged(val entries: PersistentList<SettingsSearchEntry>) : SettingsSearchIntent
}
