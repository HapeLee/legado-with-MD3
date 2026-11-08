package io.legado.app.feature.settings.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.util.Locale
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsSearchViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private var entries: List<SettingsSearchEntry> = emptyList()
    private val _uiState = MutableStateFlow(
        SettingsSearchUiState(query = savedStateHandle[QueryKey] ?: ""),
    )
    val uiState = _uiState.asStateFlow()

    fun onIntent(intent: SettingsSearchIntent) {
        when (intent) {
            is SettingsSearchIntent.QueryChanged -> {
                savedStateHandle[QueryKey] = intent.query
                _uiState.update { it.copy(query = intent.query, results = searchSettings(entries, intent.query).toPersistentList()) }
            }
            is SettingsSearchIntent.IndexChanged -> {
                entries = intent.entries
                _uiState.update { it.copy(results = searchSettings(entries, it.query).toPersistentList()) }
            }
        }
    }

    private companion object {
        const val QueryKey = "settingsSearchQuery"
    }
}

/** Small, local catalog: every query token must match; original catalog order breaks ties. */
internal fun searchSettings(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    if (normalized.isEmpty()) return emptyList()
    val tokens = normalized.split(Regex("\\s+")).distinct()
    return entries.mapNotNull { entry ->
        val title = entry.title.lowercase(Locale.ROOT)
        val keywords = entry.keywords.lowercase(Locale.ROOT)
        val description = entry.description.lowercase(Locale.ROOT)
        val path = entry.path.lowercase(Locale.ROOT)
        var score = when {
            title == normalized -> 1000
            title.startsWith(normalized) -> 400
            else -> 0
        }
        for (token in tokens) {
            score += when {
                token in title -> 100
                token in keywords -> 60
                token in description -> 30
                token in path -> 10
                else -> return@mapNotNull null
            }
        }
        entry to score
    }.sortedByDescending { it.second }.map { it.first }
}
