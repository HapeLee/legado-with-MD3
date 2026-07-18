package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
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

private val ROLE_ORDER = mapOf(
    BookCharacterProfile.ROLE_MALE_LEAD to 0,
    BookCharacterProfile.ROLE_FEMALE_LEAD to 1,
    BookCharacterProfile.ROLE_MALE_SUPPORTING to 2,
    BookCharacterProfile.ROLE_FEMALE_SUPPORTING to 3,
)

class BookCharacterListViewModel(
    bookUrl: String,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CharacterListUiState(bookUrl = bookUrl, isLoading = true)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CharacterListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var allProfiles: List<BookCharacterProfile> = emptyList()

    init {
        load()
    }

    fun onIntent(intent: CharacterListIntent) {
        when (intent) {
            is CharacterListIntent.SetRoleFilter -> {
                _uiState.update { it.copy(roleFilter = intent.role) }
                applyFilter()
            }

            is CharacterListIntent.OpenCharacter -> {
                _effects.tryEmit(
                    CharacterListEffect.OpenCharacterDetail(
                        _uiState.value.bookUrl,
                        intent.characterId
                    )
                )
            }

            CharacterListIntent.AddCharacter -> {
                _effects.tryEmit(
                    CharacterListEffect.OpenCharacterDetail(
                        _uiState.value.bookUrl,
                        null
                    )
                )
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
                allProfiles = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterProfiles(state.bookUrl, 200)
                }
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    CharacterListEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                    )
                )
            }
        }
    }

    private fun applyFilter() {
        val filter = _uiState.value.roleFilter
        val characters = allProfiles
            .filter { filter.isEmpty() || it.role == filter }
            .sortedBy { ROLE_ORDER[it.role] ?: 99 }
            .map { p ->
                val tags = GSON.fromJsonArray<String>(p.tagsJson).getOrNull().orEmpty()
                CharacterListItemUi(
                    id = p.id,
                    name = p.name,
                    avatarUri = p.avatarUri,
                    role = p.role,
                    summary = p.summary,
                    tags = tags.joinToString(", "),
                )
            }.toImmutableList()
        _uiState.update { it.copy(isLoading = false, characters = characters) }
    }
}
