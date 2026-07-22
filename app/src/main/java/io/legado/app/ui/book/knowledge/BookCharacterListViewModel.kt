package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.usecase.IdentifyBookCharactersUseCase
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val identifyBookCharacters: IdentifyBookCharactersUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CharacterListUiState(bookUrl = bookUrl, isLoading = true)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CharacterListEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var allProfiles: List<BookCharacterProfile> = emptyList()
    private var identifiedCandidates: List<IdentifyBookCharactersUseCase.Candidate> = emptyList()

    init {
        load()
        loadPersistedCandidates()
        observeIdentifyTask()
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
            CharacterListIntent.OpenAiIdentify -> _uiState.update {
                it.copy(
                    aiSheet = it.aiSheet ?: CharacterIdentifySheet(),
                    isAiSheetVisible = true,
                )
            }

            CharacterListIntent.DismissAiIdentify -> _uiState.update { it.copy(isAiSheetVisible = false) }
            CharacterListIntent.RunAiIdentify -> identifyCharacters()
            is CharacterListIntent.ToggleAiCandidate -> _uiState.update { state ->
                state.copy(aiSheet = state.aiSheet?.copy(candidates = state.aiSheet.candidates.map { candidate ->
                    if (candidate.id == intent.id) candidate.copy(selected = !candidate.selected) else candidate
                }.toImmutableList()))
            }

            CharacterListIntent.SaveAiCandidates -> saveIdentifiedCandidates()
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

    private fun loadPersistedCandidates() {
        val bookUrl = _uiState.value.bookUrl
        viewModelScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                identifyBookCharacters.loadLatest(bookUrl)
            }
            if (candidates.isNotEmpty()) {
                identifiedCandidates = candidates
                _uiState.update { state ->
                    val sheet = state.aiSheet
                    if (sheet?.loading == true || sheet?.candidates?.isNotEmpty() == true) state else {
                        state.copy(
                            aiSheet = sheet?.copy(candidates = candidates.toCandidateUi())
                                ?: CharacterIdentifySheet(candidates = candidates.toCandidateUi())
                        )
                    }
                }
            }
        }
    }

    private fun observeIdentifyTask() {
        val bookUrl = _uiState.value.bookUrl
        viewModelScope.launch {
            identifyBookCharacters.observeTask(bookUrl).collectLatest { task ->
                when (task?.status) {
                    AiArtifact.STATUS_RUNNING -> _uiState.update { state ->
                        val sheet = state.aiSheet ?: CharacterIdentifySheet()
                        state.copy(
                            aiSheet = sheet.copy(
                                loading = true,
                                error = null,
                                reasoning = task.reasoning,
                                toolNames = task.toolNames.toImmutableList(),
                                startedAt = if (sheet.startedAt == 0L) System.currentTimeMillis() else sheet.startedAt,
                            )
                        )
                    }

                    AiArtifact.STATUS_SUCCESS -> {
                        identifiedCandidates = identifyBookCharacters.decodeCandidates(task.output)
                        _uiState.update { state ->
                            state.copy(
                                aiSheet = (state.aiSheet ?: CharacterIdentifySheet()).copy(
                                    loading = false,
                                    error = null,
                                    candidates = identifiedCandidates.toCandidateUi(),
                                    reasoning = task.reasoning,
                                    toolNames = task.toolNames.toImmutableList(),
                                )
                            )
                        }
                    }

                    AiArtifact.STATUS_FAILED -> _uiState.update { state ->
                        state.copy(
                            aiSheet = (state.aiSheet ?: CharacterIdentifySheet()).copy(
                                loading = false,
                                error = task.errorMessage ?: appCtx.getString(R.string.load_failed),
                            )
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun identifyCharacters() {
        val bookUrl = _uiState.value.bookUrl
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiSheet = CharacterIdentifySheet(
                        loading = true,
                        startedAt = System.currentTimeMillis()
                    ),
                    isAiSheetVisible = true,
                )
            }
            try {
                withContext(Dispatchers.IO) { identifyBookCharacters.start(bookUrl) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        aiSheet = CharacterIdentifySheet(
                            error = e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                        ),
                        isAiSheetVisible = true,
                    )
                }
            }
        }
    }

    private fun saveIdentifiedCandidates() {
        val sheet = _uiState.value.aiSheet ?: return
        val selected = sheet.candidates.filter { it.selected }.mapNotNull { item ->
            identifiedCandidates.getOrNull(item.id.toIntOrNull() ?: -1)
        }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(aiSheet = sheet.copy(loading = true)) }
            try {
                withContext(Dispatchers.IO) {
                    identifyBookCharacters.save(
                        _uiState.value.bookUrl,
                        selected
                    )
                }
                _uiState.update { it.copy(aiSheet = null, isAiSheetVisible = false) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        aiSheet = sheet.copy(
                            loading = false,
                            error = e.localizedMessage ?: appCtx.getString(R.string.save_failed)
                        )
                    )
                }
            }
        }
    }
}

private fun List<IdentifyBookCharactersUseCase.Candidate>.toCandidateUi() =
    mapIndexed { index, candidate ->
        CharacterIdentifyCandidateUi(
            id = index.toString(),
            name = candidate.name,
            summary = listOf(candidate.summary, candidate.evidence)
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
    }.toImmutableList()
