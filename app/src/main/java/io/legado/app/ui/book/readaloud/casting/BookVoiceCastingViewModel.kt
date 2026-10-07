package io.legado.app.ui.book.readaloud.casting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.help.readaloud.cast.BookCastStore
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.ui.book.knowledge.deleteCharacterAvatar
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BookVoiceCastingViewModel(
    private val bookUrl: String,
    private val bookKnowledgeGateway: BookKnowledgeGateway,
    private val voiceGateway: ReadAloudVoiceGateway,
    /** 只为拿排版配置名列表：角色气泡弹层里「应用排版」那一节与高亮规则用同一份候选。 */
    private val styleGateway: ReadStyleGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookVoiceCastingUiState(bookUrl = bookUrl))
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookVoiceCastingEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var profiles: List<BookCharacterProfile> = emptyList()
    private var castRows: Map<String, BookCastStore.BookCharacterRow> = emptyMap()
    private var voices: List<ReadAloudVoice> = emptyList()
    private var bindings: List<BookVoiceBinding> = emptyList()

    /** 音色 → 引擎显示名，进页面/刷新时取一次，给旁白的音色候选当副标题。 */
    private var engineNames: Map<String, String> = emptyMap()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: BookVoiceCastingIntent) {
        when (intent) {
            BookVoiceCastingIntent.Refresh -> load()
            is BookVoiceCastingIntent.SetNarratorVoice -> setNarratorVoice(intent.voiceId)
            is BookVoiceCastingIntent.SaveCharacterOrder -> saveCharacterOrder(intent.characterIds)
            is BookVoiceCastingIntent.SetAvatar -> setAvatar(intent.characterId, intent.avatarUri)
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val (liveProfiles, castRowsById) = withContext(Dispatchers.IO) {
                    // 人物档案与配音角色是同一批人：rowsForBook 会双向补齐缺的那一边
                    // （只有档案的补成角色、只有角色的补出档案），所以先跑它再读列表，
                    // 否则老角色会在这页显示成「还没有人物档案」。
                    val rows = BookCastStore.rowsForBook(bookUrl).associateBy { it.id }
                    engineNames = VoicePoolStore.voiceEngineNames()
                    bookKnowledgeGateway.getCharacterProfiles(bookUrl, 200)
                        .filter { it.status == BookCharacterProfile.STATUS_ACTIVE } to rows
                }
                profiles = liveProfiles
                castRows = castRowsById
                combine(
                    voiceGateway.observeVoices(),
                    voiceGateway.observeBindings(bookUrl),
                ) { latestVoices, latestBindings -> latestVoices to latestBindings }
                    .collect { (latestVoices, latestBindings) ->
                        voices = latestVoices
                        bindings = latestBindings
                        publishState()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(
                    BookVoiceCastingEffect.ShowToast(
                        e.localizedMessage ?: appCtx.getString(R.string.load_failed)
                    )
                )
            }
        }
    }

    private fun publishState() {
        val voicesById = voices.associateBy(ReadAloudVoice::id)
        val bindingsBySubject = bindings.associateBy { it.subjectType to it.subjectId }
        // 只列旁白：不列「未知男/未知女/未知」三条兜底行——旁白这里只需要选一个音色
        val specialItems = listOf(
            specialItem(BookVoiceBinding.SUBJECT_NARRATOR, CastingSubjectKind.Narrator),
        )
        val characterItems = profiles.map { profile ->
            val row = castRows[profile.id]
            VoiceCastingItemUi(
                subjectType = BookVoiceBinding.SUBJECT_CHARACTER,
                subjectId = profile.id,
                kind = CastingSubjectKind.Character,
                name = row?.name ?: profile.name,
                // 档案的 role 存的是 male_lead 这类英文键，直接显示就成了英文；
                // 这里翻成中文名，未知值原样保留以便暴露新增取值
                description = castRoleLabelRes(profile.role)?.let(appCtx::getString)
                    ?: profile.role,
                role = profile.role,
                avatarUri = profile.avatarUri,
                poolLabel = row?.poolLabel
                    ?: VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand),
                voiceEffect = row?.voiceEffect.orEmpty(),
                chapterCount = row?.chapterCount ?: 0,
                lineCount = row?.lineCount ?: 0,
                sortOrder = row?.sortOrder ?: 0,
                bubbleRuleJson = row?.bubbleRuleJson.orEmpty(),
            )
        }.sortedWith(
            // 拖过排序的（sortOrder > 0）就按用户排的那份走；没排过的排在后面，
            // 其中男女主 / 男女配默认置顶（角色档次 ordinal 小的在前），再按台词数、名字。
            compareBy<VoiceCastingItemUi> { if (it.sortOrder > 0) it.sortOrder else Int.MAX_VALUE }
                .thenBy { castRoleTierOf(it.role).ordinal }
                .thenByDescending { it.lineCount }
                .thenBy { it.name }
        )
        val items = (specialItems + characterItems).map { item ->
            val binding = bindingsBySubject[item.subjectType to item.subjectId]
            val voice = binding?.voiceId?.let(voicesById::get)
            item.copy(
                hasBinding = binding != null,
                voiceId = binding?.voiceId.orEmpty(),
                voiceName = voice?.displayName.orEmpty(),
                voiceAvailable = voice?.let { it.available && it.enabled } == true,
            )
        }
        val voiceOptions = voices.map { voice ->
            VoiceOptionUi(
                id = voice.id,
                name = voice.displayName,
                engineType = voice.engineType,
                engineName = engineNames[voice.id] ?: voice.engineId,
                selectable = voice.available && voice.enabled,
            )
        }.sortedWith(
            compareByDescending<VoiceOptionUi> { it.selectable }
                .thenBy { it.engineType }
                .thenBy { it.name.lowercase() }
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                items = items.toImmutableList(),
                voices = voiceOptions.toImmutableList(),
                configNames = styleGateway.currentState.items
                    .map { config -> config.name }
                    .filter { it.isNotBlank() }
                    .toImmutableList(),
            )
        }
    }

    private fun specialItem(subject: String, kind: CastingSubjectKind) = VoiceCastingItemUi(
        subjectType = subject,
        subjectId = subject,
        kind = kind,
        name = "",
    )

    /**
     * 旁白的音色绑定。旁白不是角色，没有名字/声音池/变声器可存，只有这一条绑定：
     * 空 voiceId 就是删掉绑定，朗读侧回落到朗读引擎自己的默认发音人。
     */
    private fun setNarratorVoice(voiceId: String) {
        val subject = BookVoiceBinding.SUBJECT_NARRATOR
        val binding = bindings.firstOrNull {
            it.subjectType == subject && it.subjectId == subject
        }
        if (voiceId.isBlank()) {
            val doomed = binding ?: return
            viewModelScope.launch {
                try {
                    voiceGateway.deleteBinding(doomed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    showSaveError(e)
                }
            }
            return
        }
        val voice = voices.firstOrNull { it.id == voiceId && it.available && it.enabled } ?: return
        viewModelScope.launch {
            try {
                voiceGateway.upsertBinding(
                    BookVoiceBinding(
                        bookUrl = bookUrl,
                        subjectType = subject,
                        subjectId = subject,
                        voiceId = voice.id,
                        locked = true,
                        source = BookVoiceBinding.SOURCE_USER,
                        confidence = 1f,
                        createdAt = binding?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSaveError(e)
            }
        }
    }

    private fun saveCharacterOrder(characterIds: List<String>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BookCastStore.saveCharacterOrder(bookUrl, characterIds)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSaveError(e)
            }
        }
    }

    /**
     * 卡片菜单里换头像：只改档案上那一列，其余字段原样写回。
     *
     * 换下来的那份是本地图就删掉（已经没人引用它了）；正文胶囊上那张图是分页时
     * 定下来的地址，不重排一次就还是旧头像。
     */
    private fun setAvatar(characterId: String, avatarUri: String) {
        val profile = profiles.firstOrNull { it.id == characterId } ?: return
        val updated = profile.copy(avatarUri = avatarUri.trim().takeIf { it.isNotBlank() })
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    bookKnowledgeGateway.upsertCharacterProfile(updated)
                    if (profile.avatarUri != updated.avatarUri) {
                        deleteCharacterAvatar(appCtx, profile.avatarUri)
                    }
                    BookCastStore.reloadReaderChapter(bookUrl)
                }
                profiles = profiles.map { if (it.id == updated.id) updated else it }
                publishState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showSaveError(e)
            }
        }
    }

    private fun showSaveError(error: Throwable) {
        _effects.tryEmit(
            BookVoiceCastingEffect.ShowToast(
                error.localizedMessage ?: appCtx.getString(R.string.save_error)
            )
        )
    }
}
