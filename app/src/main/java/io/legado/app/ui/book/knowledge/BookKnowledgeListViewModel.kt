package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookKnowledgeEntry
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

class BookKnowledgeListViewModel(
    bookUrl: String,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KnowledgeListUiState(bookUrl = bookUrl, isLoading = true)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<KnowledgeListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        load()
    }

    fun onIntent(intent: KnowledgeListIntent) {
        when (intent) {
            is KnowledgeListIntent.SetTypeFilter -> {
                _uiState.update { it.copy(typeFilter = intent.type) }
                load()
            }

            is KnowledgeListIntent.OpenEntry -> {
                val bookUrl = _uiState.value.bookUrl
                _effects.tryEmit(KnowledgeListEffect.OpenKnowledgeDetail(bookUrl, intent.entryId))
            }

            KnowledgeListIntent.AddEntry -> {
                val bookUrl = _uiState.value.bookUrl
                _effects.tryEmit(KnowledgeListEffect.OpenKnowledgeDetail(bookUrl, null))
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
                val entries = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.searchKnowledgeEntries(
                        bookUrl = state.bookUrl,
                        query = "",
                        type = state.typeFilter,
                        chapterIndex = null,
                        limit = 50,
                    )
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = entries.map { entry ->
                            KnowledgeEntryUi(
                                id = entry.id,
                                type = entry.type,
                                title = entry.title,
                                summary = entry.content.take(100),
                                source = entry.source,
                            )
                        }.toImmutableList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    KnowledgeListEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.knowledge_list_load_failed)
                    )
                )
            }
        }
    }
}
