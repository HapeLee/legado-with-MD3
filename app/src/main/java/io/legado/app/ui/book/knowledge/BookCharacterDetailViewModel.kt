package io.legado.app.ui.book.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.help.readaloud.cast.BookCastStore
import io.legado.app.help.readaloud.cast.CastMemoryMirror
import io.legado.app.help.readaloud.cast.VoicePoolStore
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
import kotlin.uuid.Uuid

class BookCharacterDetailViewModel(
    bookUrl: String,
    characterId: String?,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CharacterDetailUiState(
            bookUrl = bookUrl,
            characterId = characterId,
            isLoading = true,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CharacterDetailEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var currentProfile: BookCharacterProfile? = null

    init {
        load()
    }

    fun onIntent(intent: CharacterDetailIntent) {
        when (intent) {
            is CharacterDetailIntent.SetName -> _uiState.update { it.copy(name = intent.value) }
            is CharacterDetailIntent.SetAliasesText -> _uiState.update { it.copy(aliasesText = intent.value) }
            is CharacterDetailIntent.SetAvatarUri -> {
                // 头像不是打字：选完/填完就该定下来，当场落库；只改界面状态要等手点「保存」才生效，
                // 退出重进即丢。
                _uiState.update { it.copy(avatarUri = intent.value) }
                save()
            }
            is CharacterDetailIntent.SetTagInput -> _uiState.update { it.copy(tagInput = intent.value) }
            is CharacterDetailIntent.AddTag -> {
                val tag = intent.tag.trim()
                if (tag.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            tags = (it.tags + tag).distinct().toImmutableList(),
                            tagInput = "",
                        )
                    }
                }
            }

            is CharacterDetailIntent.RemoveTag -> _uiState.update {
                it.copy(tags = it.tags.filterIndexed { index, _ -> index != intent.index }
                    .toImmutableList())
            }

            is CharacterDetailIntent.SetRole -> _uiState.update { it.copy(role = intent.value) }
            is CharacterDetailIntent.SetVoiceGender -> _uiState.update { it.copy(voiceGender = intent.value) }
            is CharacterDetailIntent.SetVoicePool -> _uiState.update { it.copy(voicePool = intent.value) }
            is CharacterDetailIntent.SetPersonality -> _uiState.update { it.copy(personality = intent.value) }
            is CharacterDetailIntent.SetSummary -> _uiState.update { it.copy(summary = intent.value) }
            CharacterDetailIntent.Save -> save()
            is CharacterDetailIntent.Delete -> delete(intent)
        }
    }

    private fun load() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val profile = state.characterId?.let {
                    withContext(Dispatchers.IO) {
                        bookKnowledgeGateway.getCharacterProfile(state.bookUrl, it)
                    }
                }
                currentProfile = profile
                val events = profile?.let {
                    withContext(Dispatchers.IO) {
                        bookKnowledgeGateway.getCharacterEvents(state.bookUrl, it.id, null, 30)
                    }
                }.orEmpty()
                val relations = profile?.let {
                    withContext(Dispatchers.IO) {
                        bookKnowledgeGateway.getCharacterRelations(state.bookUrl, it.id, 80)
                    }
                }.orEmpty()
                val profiles = withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.getCharacterProfiles(state.bookUrl, 80)
                }
                val nameMap = profiles.associate { it.id to it.name }
                val poolNames = withContext(Dispatchers.IO) { VoicePoolStore.enabledPoolNames() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        characterId = profile?.id ?: state.characterId,
                        name = profile?.name.orEmpty(),
                        aliasesText = profile?.aliasesJson.toAliasesText(),
                        avatarUri = profile?.avatarUri.orEmpty(),
                        tags = profile?.tagsJson.toTagList(),
                        role = profile?.role.orEmpty(),
                        voiceGender = profile?.voiceGender
                            ?: BookCharacterProfile.VOICE_GENDER_UNKNOWN,
                        voicePool = VoicePoolStore.poolNameOrEmpty(profile?.voiceAgeBand),
                        poolNames = poolNames.toImmutableList(),
                        personality = profile?.personality.orEmpty(),
                        summary = profile?.summary.orEmpty(),
                        events = events.map { event ->
                            CharacterEventUi(
                                id = event.id,
                                title = listOfNotNull(
                                    event.chapterTitle.takeIf { title -> title.isNotBlank() },
                                    event.eventTimeText.takeIf { time -> time.isNotBlank() },
                                ).joinToString(" · ")
                                    .ifBlank { appCtx.getString(R.string.character_events) },
                                content = event.content,
                            )
                        }.toImmutableList(),
                        relations = relations.map { relation ->
                            CharacterRelationUi(
                                id = relation.id,
                                fromName = nameMap[relation.fromCharacterId].orEmpty(),
                                toName = nameMap[relation.toCharacterId].orEmpty(),
                                relationType = relation.relationType,
                                summary = relation.summary,
                                attitude = relation.attitude,
                            )
                        }.toImmutableList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    CharacterDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.character_load_failed)
                    )
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isBlank()) {
            _effects.tryEmit(CharacterDetailEffect.ShowToast(appCtx.getString(R.string.character_name_empty)))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = System.currentTimeMillis()
                val existing = currentProfile
                val profile = BookCharacterProfile(
                    id = existing?.id ?: state.characterId ?: Uuid.random().toString(),
                    bookUrl = state.bookUrl,
                    name = name,
                    aliasesJson = state.aliasesText.toAliasesJson(),
                    avatarUri = state.avatarUri.trim().takeIf { it.isNotBlank() },
                    tagsJson = state.tags.toTagsJson(),
                    role = state.role,
                    voiceGender = state.voiceGender,
                    voiceAgeBand = state.voicePool,
                    personality = state.personality.trim(),
                    summary = state.summary.trim(),
                    status = existing?.status ?: BookCharacterProfile.STATUS_ACTIVE,
                    source = BookCharacterProfile.SOURCE_USER,
                    confidence = existing?.confidence ?: 1f,
                    schemaVersion = existing?.schemaVersion ?: 1,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                val avatarChanged = currentProfile?.avatarUri.orEmpty() != profile.avatarUri.orEmpty()
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertCharacterProfile(profile)
                    // 档案与配音角色是同一份信息（档案 id 就是角色 id）：这里改了名字或池，
                    // 分配表与朗读音色要立刻跟着改，否则朗读还在用旧池。
                    val castChanged = BookCastStore.syncFromProfile(
                        bookUrl = profile.bookUrl,
                        profileId = profile.id,
                        name = profile.name,
                        poolLabel = VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand),
                    )
                    // 本书角色记忆也认这一份：AI 下一趟读的就是它，不改这里人物页补的别名
                    // 与简介对下一次分配等于不存在。
                    CastMemoryMirror.applyProfileToMemory(profile.bookUrl, profile)
                    // 一次保存只重排一遍：头像与名字/池同时改时，两条各自 reload 会把同一章排两遍
                    if (castChanged || avatarChanged) {
                        // 胶囊上那张图与那行字都是分页时定下来的，不换一次重排就还是旧的
                        BookCastStore.reloadReaderChapter(profile.bookUrl)
                    }
                    if (avatarChanged) {
                        // 换掉的本地头像到这一步才真没人引用了：若在选完新图就立刻删旧文件，
                        // 用户不保存退出后，档案里留着的会是已被删掉的地址。
                        currentProfile?.avatarUri?.let { deleteCharacterAvatar(appCtx, it) }
                    }
                }
                currentProfile = profile
                _uiState.update {
                    it.copy(
                        characterId = profile.id,
                        isSaving = false,
                    )
                }
                _effects.tryEmit(CharacterDetailEffect.ShowToast(appCtx.getString(R.string.character_saved)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isSaving = false) }
                _effects.tryEmit(
                    CharacterDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.character_save_failed)
                    )
                )
            }
        }
    }

    private fun delete(intent: CharacterDetailIntent.Delete) {
        val profile = currentProfile ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.deleteCharacterProfile(
                        bookUrl = profile.bookUrl,
                        characterId = profile.id,
                        deleteRelations = intent.deleteRelations,
                        deleteEvents = intent.deleteEvents,
                    )
                    // 档案没了，以它建档的配音角色、分配句与音色绑定也不能留在表里
                    BookCastStore.deleteCharacter(profile.bookUrl, profile.id, profile.name)
                }
                _effects.tryEmit(CharacterDetailEffect.NavigateBack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _effects.tryEmit(
                    CharacterDetailEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.delete_failed)
                    )
                )
            }
        }
    }
}

private fun String?.toTagList(): ImmutableList<String> {
    return GSON.fromJsonArray<String>(this)
        .getOrNull()
        .orEmpty()
        .distinct()
        .toImmutableList()
}

private fun ImmutableList<String>.toTagsJson(): String {
    return GSON.toJson(this.toList())
}

private fun String?.toAliasesText(): String {
    return GSON.fromJsonArray<String>(this)
        .getOrNull()
        .orEmpty()
        .joinToString(", ")
}

private fun String.toAliasesJson(): String {
    return GSON.toJson(
        split(",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    )
}
