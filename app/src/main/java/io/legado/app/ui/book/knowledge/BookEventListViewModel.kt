package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.BookKnowledgeGateway
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookEventListViewModel(
    bookUrl: String,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EventListUiState(bookUrl = bookUrl, isLoading = true)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<EventListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        load()
    }

    fun onIntent(intent: EventListIntent) {
        when (intent) {
            is EventListIntent.OpenEvent -> {
                val bookUrl = _uiState.value.bookUrl
                _effects.tryEmit(EventListEffect.OpenEventDetail(bookUrl, intent.eventId))
            }

            EventListIntent.AddEvent -> {
                val bookUrl = _uiState.value.bookUrl
                _effects.tryEmit(EventListEffect.OpenEventDetail(bookUrl, null))
            }
        }
    }

    fun refresh() {
        load()
    }

    private fun load() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val events = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterEvents(
                        bookUrl = state.bookUrl,
                        characterId = null,
                        maxChapterIndex = null,
                        limit = 100,
                    )
                }
                val profiles = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterProfiles(state.bookUrl, 200)
                }
                val nameMap = profiles.associate { it.id to it.name }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = events.map { event ->
                            EventListItemUi(
                                id = event.id,
                                chapterTitle = event.chapterTitle,
                                eventTimeText = event.eventTimeText,
                                content = event.content,
                                importance = event.importance,
                                characterName = nameMap[event.characterId].orEmpty(),
                            )
                        }.toImmutableList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    EventListEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.event_list_load_failed)
                    )
                )
            }
        }
    }
}
