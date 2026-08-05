package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.collections.immutable.ImmutableList
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
import java.util.UUID

class BookKnowledgeDetailViewModel(
    bookUrl: String,
    entryId: String?,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KnowledgeDetailUiState(
            bookUrl = bookUrl,
            entryId = entryId,
            isLoading = entryId != null,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<KnowledgeDetailEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var currentEntry: BookKnowledgeEntry? = null

    init {
        load()
    }

    fun onIntent(intent: KnowledgeDetailIntent) {
        when (intent) {
            is KnowledgeDetailIntent.SetType -> _uiState.update { it.copy(type = intent.value) }
            is KnowledgeDetailIntent.SetTitle -> _uiState.update { it.copy(title = intent.value) }
            is KnowledgeDetailIntent.SetContent -> _uiState.update { it.copy(content = intent.value) }
            is KnowledgeDetailIntent.SetKeywordInput -> _uiState.update { it.copy(keywordInput = intent.value) }
            is KnowledgeDetailIntent.AddKeyword -> {
                val kw = intent.keyword.trim()
                if (kw.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            keywords = (it.keywords + kw).distinct().toImmutableList(),
                            keywordInput = "",
                        )
                    }
                }
            }

            is KnowledgeDetailIntent.RemoveKeyword -> _uiState.update {
                it.copy(keywords = it.keywords.filterIndexed { index, _ -> index != intent.index }
                    .toImmutableList())
            }

            is KnowledgeDetailIntent.SetScopeStart -> _uiState.update { it.copy(scopeStartChapter = intent.value) }
            is KnowledgeDetailIntent.SetScopeEnd -> _uiState.update { it.copy(scopeEndChapter = intent.value) }
            is KnowledgeDetailIntent.SetPriority -> _uiState.update { it.copy(priority = intent.value) }
            KnowledgeDetailIntent.Save -> save()
            KnowledgeDetailIntent.Delete -> delete()
        }
    }

    private fun load() {
        val state = _uiState.value
        val entryId = state.entryId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val entries = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.searchKnowledgeEntries(
                        bookUrl = state.bookUrl,
                        query = "",
                        type = null,
                        chapterIndex = null,
                        limit = 200,
                    )
                }
                val entry = entries.find { it.id == entryId }
                currentEntry = entry
                if (entry != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            type = entry.type,
                            title = entry.title,
                            content = entry.content,
                            keywords = entry.keywordsJson.toKeywordList(),
                            scopeStartChapter = entry.scopeStartChapter?.toString().orEmpty(),
                            scopeEndChapter = entry.scopeEndChapter?.toString().orEmpty(),
                            priority = entry.priority,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.tryEmit(KnowledgeDetailEffect.ShowToast(appCtx.getString(R.string.knowledge_not_found)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    KnowledgeDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                    )
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isBlank()) {
            _effects.tryEmit(KnowledgeDetailEffect.ShowToast(appCtx.getString(R.string.knowledge_title_empty)))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = System.currentTimeMillis()
                val existing = currentEntry
                val entry = BookKnowledgeEntry(
                    id = existing?.id ?: state.entryId ?: UUID.randomUUID().toString(),
                    bookUrl = state.bookUrl,
                    type = state.type,
                    title = title,
                    keywordsJson = state.keywords.toKeywordsJson(),
                    content = state.content.trim(),
                    scopeStartChapter = state.scopeStartChapter.toIntOrNull(),
                    scopeEndChapter = state.scopeEndChapter.toIntOrNull(),
                    priority = state.priority,
                    source = existing?.source ?: BookCharacterProfile.SOURCE_USER,
                    confidence = existing?.confidence ?: 1f,
                    schemaVersion = existing?.schemaVersion ?: 1,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertKnowledgeEntry(entry)
                }
                currentEntry = entry
                _uiState.update {
                    it.copy(
                        entryId = entry.id,
                        isSaving = false,
                    )
                }
                _effects.tryEmit(KnowledgeDetailEffect.ShowToast(appCtx.getString(R.string.knowledge_saved)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(
                    KnowledgeDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.save_failed)
                    )
                )
            }
        }
    }

    private fun delete() {
        val entry = currentEntry ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { bookKnowledgeGateway.deleteKnowledgeEntry(entry.id) }
                _effects.tryEmit(KnowledgeDetailEffect.NavigateBack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _effects.tryEmit(
                    KnowledgeDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.delete_failed)
                    )
                )
            }
        }
    }
}

private fun String?.toKeywordList(): ImmutableList<String> {
    return GSON.fromJsonArray<String>(this)
        .getOrNull()
        .orEmpty()
        .distinct()
        .toImmutableList()
}

private fun ImmutableList<String>.toKeywordsJson(): String {
    return GSON.toJson(this.toList())
}
