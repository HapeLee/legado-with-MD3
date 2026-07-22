package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
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
import java.util.UUID

class BookCharacterNetworkViewModel(
    bookUrl: String,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CharacterNetworkUiState(
            bookUrl = bookUrl,
            isLoading = true,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CharacterNetworkEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var profileMap: Map<String, BookCharacterProfile> = emptyMap()
    private var relationMap: Map<String, BookCharacterRelation> = emptyMap()

    init {
        load()
    }

    fun onIntent(intent: CharacterNetworkIntent) {
        when (intent) {
            is CharacterNetworkIntent.OpenCharacter -> {
                _effects.tryEmit(
                    CharacterNetworkEffect.OpenCharacterDetail(
                        bookUrl = _uiState.value.bookUrl,
                        characterId = intent.characterId,
                    )
                )
            }

            is CharacterNetworkIntent.SetNewFromName -> _uiState.update { it.copy(newFromName = intent.value) }
            is CharacterNetworkIntent.SetNewToName -> _uiState.update { it.copy(newToName = intent.value) }
            is CharacterNetworkIntent.SetNewRelationType -> _uiState.update {
                it.copy(
                    newRelationType = intent.value
                )
            }

            is CharacterNetworkIntent.SetNewSummary -> _uiState.update { it.copy(newSummary = intent.value) }
            is CharacterNetworkIntent.SetNewAttitude -> _uiState.update { it.copy(newAttitude = intent.value) }
            is CharacterNetworkIntent.SetRelationType -> updateRelation(intent.relationId) {
                it.copy(relationType = intent.value)
            }

            is CharacterNetworkIntent.SetRelationSummary -> updateRelation(intent.relationId) {
                it.copy(summary = intent.value)
            }

            is CharacterNetworkIntent.SetRelationAttitude -> updateRelation(intent.relationId) {
                it.copy(attitude = intent.value)
            }

            is CharacterNetworkIntent.SaveRelation -> saveRelation(intent.relationId)
            is CharacterNetworkIntent.DeleteRelation -> deleteRelation(intent.relationId)
            CharacterNetworkIntent.AddRelation -> addRelation()
        }
    }

    fun refresh() {
        load()
    }

    private fun load() {
        val bookUrl = _uiState.value.bookUrl
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profiles = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterProfiles(bookUrl, 120)
                }
                val relations = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getBookCharacterRelations(bookUrl, 200)
                }
                profileMap = profiles.associateBy { it.id }
                relationMap = relations.associateBy { it.id }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        characters = profiles.map { profile ->
                            CharacterNodeUi(
                                id = profile.id,
                                name = profile.name,
                                avatarUri = profile.avatarUri,
                                summary = profile.summary,
                            )
                        }.toImmutableList(),
                        relations = relations.map { relation ->
                            relation.toEditorUi()
                        }.toImmutableList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    CharacterNetworkEffect.ShowToast(
                        e.localizedMessage
                            ?: appCtx.getString(R.string.relation_network_load_failed)
                    )
                )
            }
        }
    }

    private fun updateRelation(
        relationId: String,
        transform: (CharacterRelationEditorUi) -> CharacterRelationEditorUi,
    ) {
        _uiState.update { state ->
            state.copy(
                relations = state.relations.map {
                    if (it.id == relationId) transform(it) else it
                }.toImmutableList()
            )
        }
    }

    private fun addRelation() {
        val state = _uiState.value
        val fromName = state.newFromName.trim()
        val toName = state.newToName.trim()
        if (fromName.isBlank() || toName.isBlank()) {
            _effects.tryEmit(CharacterNetworkEffect.ShowToast(appCtx.getString(R.string.relation_fill_both)))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val fromProfile = findOrCreateProfile(fromName)
                val toProfile = findOrCreateProfile(toName)
                val now = System.currentTimeMillis()
                val relation = BookCharacterRelation(
                    id = UUID.randomUUID().toString(),
                    bookUrl = state.bookUrl,
                    fromCharacterId = fromProfile.id,
                    toCharacterId = toProfile.id,
                    relationType = state.newRelationType.trim(),
                    summary = state.newSummary.trim(),
                    attitude = state.newAttitude.trim(),
                    source = BookCharacterProfile.SOURCE_USER,
                    createdAt = now,
                    updatedAt = now,
                )
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertCharacterRelation(relation)
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        newFromName = "",
                        newToName = "",
                        newRelationType = "",
                        newSummary = "",
                        newAttitude = "",
                    )
                }
                _effects.tryEmit(CharacterNetworkEffect.ShowToast(appCtx.getString(R.string.relation_added)))
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(
                    CharacterNetworkEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.relation_add_failed)
                    )
                )
            }
        }
    }

    private fun saveRelation(relationId: String) {
        val state = _uiState.value
        val editor = state.relations.firstOrNull { it.id == relationId } ?: return
        val existing = relationMap[relationId] ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val relation = existing.copy(
                    relationType = editor.relationType.trim(),
                    summary = editor.summary.trim(),
                    attitude = editor.attitude.trim(),
                    source = BookCharacterProfile.SOURCE_USER,
                    updatedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertCharacterRelation(relation)
                }
                relationMap = relationMap + (relation.id to relation)
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(CharacterNetworkEffect.ShowToast(appCtx.getString(R.string.relation_saved)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(
                    CharacterNetworkEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.relation_save_failed)
                    )
                )
            }
        }
    }

    private fun deleteRelation(relationId: String) {
        if (relationMap[relationId] == null) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.deleteCharacterRelation(
                        relationId
                    )
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _effects.tryEmit(
                    CharacterNetworkEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.delete_failed)
                    )
                )
            }
        }
    }

    private suspend fun findOrCreateProfile(name: String): BookCharacterProfile {
        val bookUrl = _uiState.value.bookUrl
        profileMap.values.firstOrNull { it.name == name }?.let { return it }
        bookKnowledgeGateway.getCharacterProfile(bookUrl, name)?.let {
            profileMap = profileMap + (it.id to it)
            return it
        }
        val now = System.currentTimeMillis()
        val profile = BookCharacterProfile(
            id = UUID.randomUUID().toString(),
            bookUrl = bookUrl,
            name = name,
            source = BookCharacterProfile.SOURCE_USER,
            createdAt = now,
            updatedAt = now,
        )
        bookKnowledgeGateway.upsertCharacterProfile(profile)
        profileMap = profileMap + (profile.id to profile)
        return profile
    }

    private fun BookCharacterRelation.toEditorUi(): CharacterRelationEditorUi {
        return CharacterRelationEditorUi(
            id = id,
            fromCharacterId = fromCharacterId,
            toCharacterId = toCharacterId,
            fromName = profileMap[fromCharacterId]?.name
                ?: appCtx.getString(R.string.character_deleted),
            toName = profileMap[toCharacterId]?.name
                ?: appCtx.getString(R.string.character_deleted),
            relationType = relationType,
            summary = summary,
            attitude = attitude,
        )
    }
}
