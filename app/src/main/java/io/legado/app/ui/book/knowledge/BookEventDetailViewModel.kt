package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
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
import java.util.UUID

class BookEventDetailViewModel(
    bookUrl: String,
    eventId: String?,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EventDetailUiState(
            bookUrl = bookUrl,
            eventId = eventId,
            isLoading = eventId != null,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<EventDetailEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var currentEvent: BookCharacterEvent? = null

    init {
        load()
    }

    fun onIntent(intent: EventDetailIntent) {
        when (intent) {
            is EventDetailIntent.SetCharacterName -> _uiState.update { it.copy(characterName = intent.value) }
            is EventDetailIntent.SetChapterTitle -> _uiState.update { it.copy(chapterTitle = intent.value) }
            is EventDetailIntent.SetEventTimeText -> _uiState.update { it.copy(eventTimeText = intent.value) }
            is EventDetailIntent.SetContent -> _uiState.update { it.copy(content = intent.value) }
            is EventDetailIntent.SetImportance -> _uiState.update { it.copy(importance = intent.value) }
            EventDetailIntent.Save -> save()
        }
    }

    private fun load() {
        val state = _uiState.value
        val eventId = state.eventId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val events = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterEvents(
                        bookUrl = state.bookUrl,
                        characterId = null,
                        maxChapterIndex = null,
                        limit = 200,
                    )
                }
                val event = events.find { it.id == eventId }
                currentEvent = event
                if (event != null) {
                    val profiles = withContext(Dispatchers.IO) {
                        bookKnowledgeGateway.getCharacterProfiles(state.bookUrl, 200)
                    }
                    val charName = profiles.find { it.id == event.characterId }?.name.orEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            characterName = charName,
                            chapterTitle = event.chapterTitle,
                            eventTimeText = event.eventTimeText,
                            content = event.content,
                            importance = event.importance,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.tryEmit(EventDetailEffect.ShowToast(appCtx.getString(R.string.event_not_found)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    EventDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(
                            R.string.load_failed
                        )
                    )
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        val content = state.content.trim()
        if (content.isBlank()) {
            _effects.tryEmit(EventDetailEffect.ShowToast(appCtx.getString(R.string.event_content_empty)))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val charName = state.characterName.trim()
                val character = if (charName.isNotBlank()) {
                    bookKnowledgeGateway.getCharacterProfile(state.bookUrl, charName)
                        ?: withContext(Dispatchers.IO) {
                            val now = System.currentTimeMillis()
                            val profile = io.legado.app.data.entities.BookCharacterProfile(
                                id = UUID.randomUUID().toString(),
                                bookUrl = state.bookUrl,
                                name = charName,
                                source = io.legado.app.data.entities.BookCharacterProfile.SOURCE_USER,
                                createdAt = now,
                                updatedAt = now,
                            )
                            bookKnowledgeGateway.upsertCharacterProfile(profile)
                            profile
                        }
                } else null

                val now = System.currentTimeMillis()
                val existing = currentEvent
                val event = BookCharacterEvent(
                    id = existing?.id ?: state.eventId ?: UUID.randomUUID().toString(),
                    bookUrl = state.bookUrl,
                    characterId = character?.id.orEmpty(),
                    chapterTitle = state.chapterTitle.trim(),
                    eventTimeText = state.eventTimeText.trim(),
                    content = content,
                    importance = state.importance,
                    source = existing?.source ?: BookCharacterProfile.SOURCE_USER,
                    confidence = existing?.confidence ?: 1f,
                    schemaVersion = existing?.schemaVersion ?: 1,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertCharacterEvent(event)
                }
                currentEvent = event
                _uiState.update {
                    it.copy(
                        eventId = event.id,
                        isSaving = false,
                    )
                }
                _effects.tryEmit(EventDetailEffect.ShowToast(appCtx.getString(R.string.knowledge_saved)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(
                    EventDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(
                            R.string.save_failed
                        )
                    )
                )
            }
        }
    }
}
