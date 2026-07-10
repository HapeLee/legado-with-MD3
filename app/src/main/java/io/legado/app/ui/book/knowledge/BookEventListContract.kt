package io.legado.app.ui.book.knowledge

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class EventListUiState(
    val bookUrl: String = "",
    val isLoading: Boolean = false,
    val entries: ImmutableList<EventListItemUi> = persistentListOf(),
)

@Stable
data class EventListItemUi(
    val id: String,
    val chapterTitle: String,
    val eventTimeText: String,
    val content: String,
    val importance: Int,
    val characterName: String,
)

sealed interface EventListIntent {
    data class OpenEvent(val eventId: String) : EventListIntent
    data object AddEvent : EventListIntent
}

sealed interface EventListEffect {
    data class OpenEventDetail(val bookUrl: String, val eventId: String?) : EventListEffect
    data class ShowToast(val message: String) : EventListEffect
}

@Stable
data class EventDetailUiState(
    val bookUrl: String = "",
    val eventId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val characterName: String = "",
    val chapterTitle: String = "",
    val eventTimeText: String = "",
    val content: String = "",
    val importance: Int = 0,
)

sealed interface EventDetailIntent {
    data class SetCharacterName(val value: String) : EventDetailIntent
    data class SetChapterTitle(val value: String) : EventDetailIntent
    data class SetEventTimeText(val value: String) : EventDetailIntent
    data class SetContent(val value: String) : EventDetailIntent
    data class SetImportance(val value: Int) : EventDetailIntent
    data object Save : EventDetailIntent
}

sealed interface EventDetailEffect {
    data class ShowToast(val message: String) : EventDetailEffect
}
