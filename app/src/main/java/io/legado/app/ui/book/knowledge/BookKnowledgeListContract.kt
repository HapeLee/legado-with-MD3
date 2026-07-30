package io.legado.app.ui.book.knowledge

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class KnowledgeListUiState(
    val bookUrl: String = "",
    val isLoading: Boolean = false,
    val typeFilter: String? = null,
    val entries: ImmutableList<KnowledgeEntryUi> = persistentListOf(),
)

@Stable
data class KnowledgeEntryUi(
    val id: String,
    val type: String,
    val title: String,
    val summary: String,
    val source: String,
)

sealed interface KnowledgeListIntent {
    data class SetTypeFilter(val type: String?) : KnowledgeListIntent
    data class OpenEntry(val entryId: String) : KnowledgeListIntent
    data object AddEntry : KnowledgeListIntent
}

sealed interface KnowledgeListEffect {
    data class OpenKnowledgeDetail(val bookUrl: String, val entryId: String?) : KnowledgeListEffect
    data class ShowToast(val message: String) : KnowledgeListEffect
}

@Stable
data class KnowledgeDetailUiState(
    val bookUrl: String = "",
    val entryId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val type: String = "world_rule",
    val title: String = "",
    val content: String = "",
    val keywords: ImmutableList<String> = persistentListOf(),
    val keywordInput: String = "",
    val scopeStartChapter: String = "",
    val scopeEndChapter: String = "",
    val priority: Int = 0,
)

sealed interface KnowledgeDetailIntent {
    data class SetType(val value: String) : KnowledgeDetailIntent
    data class SetTitle(val value: String) : KnowledgeDetailIntent
    data class SetContent(val value: String) : KnowledgeDetailIntent
    data class SetKeywordInput(val value: String) : KnowledgeDetailIntent
    data class AddKeyword(val keyword: String) : KnowledgeDetailIntent
    data class RemoveKeyword(val index: Int) : KnowledgeDetailIntent
    data class SetScopeStart(val value: String) : KnowledgeDetailIntent
    data class SetScopeEnd(val value: String) : KnowledgeDetailIntent
    data class SetPriority(val value: Int) : KnowledgeDetailIntent
    data object Save : KnowledgeDetailIntent
    data object Delete : KnowledgeDetailIntent
}

sealed interface KnowledgeDetailEffect {
    data class ShowToast(val message: String) : KnowledgeDetailEffect
    data object NavigateBack : KnowledgeDetailEffect
}
