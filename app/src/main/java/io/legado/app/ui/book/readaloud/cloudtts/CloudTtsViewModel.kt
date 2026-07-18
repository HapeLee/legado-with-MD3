package io.legado.app.ui.book.readaloud.cloudtts

import android.app.Application
import io.legado.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsVoiceCatalogType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceConfig
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.TtsEngineDescriptor
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsVoiceCatalog
import io.legado.app.utils.GSON
import java.io.File
import java.util.UUID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CloudTtsViewModel(
    private val application: Application,
    private val engineGateway: CloudTtsEngineGateway,
    private val httpTtsEngineGateway: HttpTtsEngineGateway,
    private val voiceGateway: ReadAloudVoiceGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CloudTtsUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<CloudTtsEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private val synthesizer = CloudTtsAudioSynthesizer(engineGateway)
    private val systemCatalog = SystemTtsVoiceCatalog(application)
    private val systemSynthesizer = SystemTtsFileSynthesizer(application)
    private var engines = emptyList<CloudTtsEngine>()
    private var voices = emptyList<ReadAloudVoice>()
    private var systemEngines = emptyList<TtsEngineDescriptor>()
    private var httpEngines = emptyList<TtsEngineDescriptor>()

    init {
        viewModelScope.launch {
            combine(
                engineGateway.observeAll(),
                httpTtsEngineGateway.observeAll(),
                voiceGateway.observeVoices(),
            ) { allEngines, allHttpEngines, allVoices ->
                Triple(allEngines, allHttpEngines, allVoices.filter { voice ->
                    voice.engineType in setOf(
                        ReadAloudVoice.ENGINE_CLOUD,
                        ReadAloudVoice.ENGINE_SYSTEM,
                        ReadAloudVoice.ENGINE_HTTP,
                    )
                })
            }.collect { (allEngines, allHttpEngines, savedVoices) ->
                engines = allEngines
                httpEngines = allHttpEngines
                voices = savedVoices
                _uiState.update { state -> state.copy(
                    loading = false,
                    engines = allEngines.map { engine ->
                        CloudTtsEngineItemUi(engine.id, engine.name, engine.provider.profile.displayName)
                    }.toImmutableList(),
                    voices = savedVoices.map { voice ->
                        CloudTtsVoiceItemUi(
                            voice.id,
                            voice.displayName,
                            buildString {
                                append(engineName(voice.engineType, voice.engineId))
                                if (voice.managedBy != ReadAloudVoice.MANAGED_BY_USER) append(application.getString(R.string.cloud_tts_auto_synced_suffix))
                                if (!voice.available) append(application.getString(R.string.cloud_tts_unavailable_suffix))
                            },
                            deletable = voice.managedBy == ReadAloudVoice.MANAGED_BY_USER,
                            editable = voice.managedBy == ReadAloudVoice.MANAGED_BY_USER,
                        )
                    }.toImmutableList(),
                    availableEngines = engineOptions(),
                ) }
            }
        }
        viewModelScope.launch {
            systemEngines = systemCatalog.getEngines()
            _uiState.update { it.copy(availableEngines = engineOptions()) }
        }
    }

    fun onIntent(intent: CloudTtsIntent) {
        when (intent) {
            is CloudTtsIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            CloudTtsIntent.AddEngine -> _uiState.update { it.copy(
                engineEditor = CloudTtsEngineEditorUi(name = "MiMo", model = "mimo-v2.5-tts"),
            ) }
            CloudTtsIntent.AddVoice -> _uiState.update { it.copy(showVoiceEnginePicker = true) }
            is CloudTtsIntent.AddVoiceForEngine -> {
                _uiState.update { it.copy(showVoiceEnginePicker = false) }
                addVoice(intent.engineType, intent.engineId)
            }
            is CloudTtsIntent.EditEngine -> editEngine(intent.id)
            is CloudTtsIntent.DeleteEngine -> deleteEngine(intent.id)
            is CloudTtsIntent.DeleteVoice -> deleteVoice(intent.id)
            is CloudTtsIntent.EditVoice -> editVoice(intent.id)
            is CloudTtsIntent.UpdateEngineEditor -> _uiState.update { it.copy(engineEditor = intent.editor) }
            is CloudTtsIntent.UpdateVoiceEditor -> _uiState.update { it.copy(voiceEditor = intent.editor) }
            CloudTtsIntent.DismissEngineEditor -> _uiState.update { it.copy(engineEditor = null) }
            CloudTtsIntent.DismissVoiceEditor -> _uiState.update { it.copy(voiceEditor = null) }
            CloudTtsIntent.DismissVoiceEnginePicker -> _uiState.update {
                it.copy(showVoiceEnginePicker = false)
            }
            is CloudTtsIntent.SelectEngine -> selectEngine(intent.engineType, intent.engineId)
            CloudTtsIntent.DiscoverVoices -> discoverVoices()
            is CloudTtsIntent.SelectVoice -> selectVoice(intent.id)
            CloudTtsIntent.TestEngine -> testEngine()
            CloudTtsIntent.Preview -> preview()
            CloudTtsIntent.Save -> if (_uiState.value.engineEditor != null) saveEngine() else saveVoice()
            CloudTtsIntent.DismissError -> _uiState.update { it.copy(activeDialog = null) }
            CloudTtsIntent.CopyError -> copyError()
            is CloudTtsIntent.ReportError -> showError(intent.message)
        }
    }

    private fun editEngine(id: String) {
        val engine = engines.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(
            engineEditor = CloudTtsEngineEditorUi(
                editingEngineId = engine.id,
                name = engine.name,
                provider = engine.provider.storageValue,
                baseUrl = engine.baseUrl,
                apiKey = engine.apiKey,
                secretKey = engine.secretKey,
                region = engine.region,
                appId = engine.appId,
                model = engine.model,
                optionsJson = engine.optionsJson,
            ),
        ) }
    }

    private fun addVoice(engineType: String = "", engineId: String = "") {
        if (_uiState.value.availableEngines.isEmpty()) {
            return toast(application.getString(R.string.cloud_tts_no_available_engine))
        }
        val engine = _uiState.value.availableEngines.firstOrNull {
            it.engineType == engineType && it.engineId == engineId
        }
        _uiState.update { state -> state.copy(
            voiceEditor = TtsVoicePresetEditorUi(
                engineType = engine?.engineType.orEmpty(),
                engineId = engine?.engineId.orEmpty(),
                engineName = engine?.title.orEmpty(),
                speed = if (engine?.engineType == ReadAloudVoice.ENGINE_SYSTEM) "" else "1.0",
                pitch = if (engine?.engineType == ReadAloudVoice.ENGINE_SYSTEM) "" else "1.0",
                format = engine?.let { defaultFormat(it.engineType, it.engineId) }.orEmpty(),
                formatOptions = engine?.let { formatOptions(it.engineType, it.engineId) }
                    ?: persistentListOf(),
            ),
            discoveredVoices = persistentListOf(),
        ) }
        if (engine != null) discoverVoices()
    }

    private fun editVoice(id: String) {
        val voice = voices.firstOrNull {
            it.id == id && it.managedBy == ReadAloudVoice.MANAGED_BY_USER
        } ?: return
        val engine = _uiState.value.availableEngines.firstOrNull {
            it.engineType == voice.engineType && it.engineId == voice.engineId
        } ?: return toast(application.getString(R.string.cloud_tts_voice_engine_unavailable))
        val cloudConfig = if (voice.engineType == ReadAloudVoice.ENGINE_CLOUD) {
            runCatching {
                GSON.fromJson(voice.traitsJson, CloudTtsVoiceConfig::class.java)
            }.getOrNull()
        } else null
        val systemConfig = if (voice.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
            runCatching {
                GSON.fromJson(voice.traitsJson, SystemTtsVoiceConfig::class.java)
            }.getOrNull()
        } else null
        _uiState.update { state -> state.copy(
            voiceEditor = TtsVoicePresetEditorUi(
                editingVoiceId = voice.id,
                engineType = voice.engineType,
                engineId = voice.engineId,
                engineName = engine.title,
                voiceId = voice.speakerId.ifBlank {
                    if (voice.engineType == ReadAloudVoice.ENGINE_HTTP) DEFAULT_ENGINE_VOICE_ID else ""
                },
                voiceName = voice.displayName,
                locale = cloudConfig?.locale.orEmpty(),
                style = cloudConfig?.style.orEmpty(),
                role = cloudConfig?.role.orEmpty(),
                instructions = cloudConfig?.instructions.orEmpty(),
                automaticEmotion = cloudConfig?.automaticEmotion != false,
                characterPersonality = cloudConfig?.characterPersonality != false,
                thoughtPerformance = cloudConfig?.thoughtPerformance != false,
                speed = when {
                    cloudConfig != null -> cloudConfig.speed.toString()
                    systemConfig?.speechRate != null -> systemConfig.speechRate.toString()
                    else -> ""
                },
                pitch = when {
                    cloudConfig != null -> cloudConfig.pitch.toString()
                    systemConfig?.pitch != null -> systemConfig.pitch.toString()
                    else -> ""
                },
                volume = cloudConfig?.volume?.toString() ?: "1.0",
                format = cloudConfig?.format ?: defaultFormat(voice.engineType, voice.engineId),
                formatOptions = formatOptions(voice.engineType, voice.engineId),
            ),
            discoveredVoices = persistentListOf(),
        ) }
        discoverVoices()
    }

    private fun selectEngine(engineType: String, engineId: String) {
        if (_uiState.value.voiceEditor?.editingVoiceId != null) {
            return toast(application.getString(R.string.cloud_tts_cannot_change_engine))
        }
        val engine = _uiState.value.availableEngines.firstOrNull {
            it.engineType == engineType && it.engineId == engineId
        } ?: return
        _uiState.update { state -> state.copy(
            voiceEditor = state.voiceEditor?.copy(
                engineType = engine.engineType,
                engineId = engine.engineId,
                engineName = engine.title,
                voiceId = "",
                voiceName = "",
                locale = "",
                style = "",
                role = "",
                speed = if (engine.engineType == ReadAloudVoice.ENGINE_SYSTEM) "" else "1.0",
                pitch = if (engine.engineType == ReadAloudVoice.ENGINE_SYSTEM) "" else "1.0",
                format = defaultFormat(engine.engineType, engine.engineId),
                formatOptions = formatOptions(engine.engineType, engine.engineId),
            ),
            discoveredVoices = persistentListOf(),
        ) }
        discoverVoices()
    }

    private fun discoverVoices() = viewModelScope.launch {
        val editor = _uiState.value.voiceEditor ?: return@launch
        _uiState.update { it.copy(discovering = true) }
        runCatching {
            if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                systemCatalog.getVoices(editor.engineId).map { voice ->
                    CloudTtsDiscoveredVoiceUi(
                        id = voice.id,
                        label = listOf(voice.displayName, voice.locale)
                            .filter(String::isNotBlank).joinToString(" · "),
                        locale = voice.locale,
                        styles = persistentListOf(),
                        roles = persistentListOf(),
                    )
                }
            } else if (editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
                val engine = engines.firstOrNull { it.id == editor.engineId }
                    ?: error(application.getString(R.string.cloud_tts_engine_missing))
                synthesizer.fetchVoices(engine).map { voice ->
                    CloudTtsDiscoveredVoiceUi(
                        voice.id,
                        listOf(voice.displayName, voice.locale, voice.gender)
                            .filter(String::isNotBlank).joinToString(" · "),
                        voice.locale,
                        voice.styles.toImmutableList(),
                        voice.roles.toImmutableList(),
                    )
                }
            } else {
                listOf(CloudTtsDiscoveredVoiceUi(
                    id = DEFAULT_ENGINE_VOICE_ID,
                    label = application.getString(R.string.cloud_tts_engine_default_voice),
                    locale = "",
                    styles = persistentListOf(),
                    roles = persistentListOf(),
                ))
            }
        }.onSuccess { catalog ->
            _uiState.update { state ->
                val defaultVoice = catalog.singleOrNull()
                    ?.takeIf { editor.engineType == ReadAloudVoice.ENGINE_HTTP }
                state.copy(
                    discoveredVoices = catalog.toImmutableList(),
                    voiceEditor = if (defaultVoice == null) state.voiceEditor else {
                        state.voiceEditor?.copy(
                            voiceId = defaultVoice.id,
                            voiceName = editor.engineName,
                        )
                    },
                )
            }
            toast(application.getString(R.string.cloud_tts_voice_count, catalog.size))
        }.onFailure { showError(formatError(it)) }
        _uiState.update { it.copy(discovering = false) }
    }

    private fun selectVoice(id: String) {
        if (_uiState.value.voiceEditor?.editingVoiceId != null) {
            return toast(application.getString(R.string.cloud_tts_cannot_change_native_voice))
        }
        val voice = _uiState.value.discoveredVoices.firstOrNull { it.id == id } ?: return
        _uiState.update { state -> state.copy(voiceEditor = state.voiceEditor?.copy(
            voiceId = voice.id,
            voiceName = voice.label.substringBefore(" · "),
            locale = voice.locale,
            style = "",
            role = "",
        )) }
    }

    private fun saveEngine() = viewModelScope.launch {
        val engine = buildEngine() ?: return@launch
        engineGateway.upsert(engine)
        _uiState.update { it.copy(engineEditor = null) }
            toast(application.getString(R.string.cloud_tts_engine_saved))
    }

    private fun saveVoice() = viewModelScope.launch {
        val voice = buildVoice() ?: return@launch
        voiceGateway.upsertVoice(voice)
        _uiState.value.voiceEditor?.editingVoiceId
            ?.takeIf { it != voice.id }
            ?.let { oldId -> voices.firstOrNull { it.id == oldId } }
            ?.let { oldVoice -> voiceGateway.deleteVoice(oldVoice) }
        _uiState.update { it.copy(voiceEditor = null, discoveredVoices = persistentListOf()) }
        toast(application.getString(R.string.cloud_tts_voice_saved))
    }

    private fun testEngine() = viewModelScope.launch {
        val engine = buildEngine() ?: return@launch
        _uiState.update { it.copy(testing = true) }
        runCatching {
            val voice = synthesizer.fetchVoices(engine).firstOrNull()
                ?: error(application.getString(R.string.cloud_tts_no_test_voice))
            val file = File(application.cacheDir, "cloud_tts_preview/${engine.id.hashCode()}.audio")
            val request = CloudTtsSynthesisRequest(
                text = application.getString(R.string.cloud_tts_connection_test_text),
                voiceId = voice.id,
            )
            check(synthesizer.synthesize(engine, request, file)) {
                application.getString(R.string.cloud_tts_no_audio)
            }
        }.onSuccess { toast(application.getString(R.string.cloud_tts_connection_success)) }
            .onFailure { showError(formatError(it)) }
        _uiState.update { it.copy(testing = false) }
    }

    private fun preview() = viewModelScope.launch {
        val editor = _uiState.value.voiceEditor ?: return@launch
        if (editor.voiceId.isBlank()) {
            return@launch toast(application.getString(R.string.cloud_tts_select_voice_first))
        }
        _uiState.update { it.copy(testing = true) }
        runCatching {
            val file = File(application.cacheDir, "cloud_tts_preview/${editor.engineId.hashCode()}.audio")
            if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
                check(systemSynthesizer.synthesize(
                    engine = editor.engineId,
                    voiceName = editor.voiceId,
                    text = application.getString(R.string.system_tts_preview_text),
                    output = file,
                    speechRate = editor.speed.toFloatOrNull() ?: 1f,
                )) { application.getString(R.string.system_tts_preview_failed) }
            } else {
                val engine = engines.firstOrNull { it.id == editor.engineId }
                    ?: error(application.getString(R.string.cloud_tts_engine_missing))
                val request = buildRequest(editor, editor.voiceId) ?: return@launch
                check(synthesizer.synthesize(engine, request, file)) {
                    application.getString(R.string.cloud_tts_no_audio)
                }
            }
            file
        }.onSuccess { _effects.tryEmit(CloudTtsEffect.PlayPreview(it.absolutePath)) }
            .onFailure { showError(formatError(it)) }
        _uiState.update { it.copy(testing = false) }
    }

    private fun buildEngine(): CloudTtsEngine? {
        val editor = _uiState.value.engineEditor ?: return null
        val provider = CloudTtsProviderType.entries.firstOrNull {
            it.storageValue == editor.provider
        } ?: return null.also { toast(application.getString(R.string.cloud_tts_select_provider)) }
        if (editor.name.isBlank()) return null.also { toast(application.getString(R.string.cloud_tts_engine_name_required)) }
        if (editor.apiKey.isBlank()) return null.also { toast(application.getString(R.string.cloud_tts_api_key_required)) }
        if (provider == CloudTtsProviderType.AwsPolly && editor.secretKey.isBlank()) {
            return null.also { toast(application.getString(R.string.cloud_tts_aws_secret_required)) }
        }
        if (provider in setOf(CloudTtsProviderType.AzureSpeech, CloudTtsProviderType.AwsPolly) &&
            editor.region.isBlank() && editor.baseUrl.isBlank()
        ) return null.also { toast(application.getString(R.string.cloud_tts_region_or_url_required)) }
        if (provider == CloudTtsProviderType.Volcengine && editor.appId.isBlank()) {
            return null.also { toast(application.getString(R.string.cloud_tts_volc_app_id_required)) }
        }
        val old = editor.editingEngineId?.let { id -> engines.firstOrNull { it.id == id } }
        val now = System.currentTimeMillis()
        return CloudTtsEngine(
            id = old?.id ?: UUID.randomUUID().toString(),
            name = editor.name.trim(),
            provider = provider,
            baseUrl = editor.baseUrl.trim(),
            apiKey = editor.apiKey.trim(),
            secretKey = editor.secretKey.trim(),
            region = editor.region.trim(),
            appId = editor.appId.trim(),
            model = editor.model.trim(),
            optionsJson = editor.optionsJson.trim().ifBlank { "{}" },
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun buildVoice(): ReadAloudVoice? {
        val editor = _uiState.value.voiceEditor ?: return null
        if (editor.voiceId.isBlank()) return null.also { toast(application.getString(R.string.cloud_tts_select_voice_first)) }
        val speakerId = editor.voiceId.takeUnless { it == DEFAULT_ENGINE_VOICE_ID }.orEmpty()
        val request = if (editor.engineType == ReadAloudVoice.ENGINE_CLOUD) {
            buildRequest(editor, editor.voiceId) ?: return null
        } else null
        val systemConfig = if (editor.engineType == ReadAloudVoice.ENGINE_SYSTEM) {
            val speechRate = editor.speed.takeIf(String::isNotBlank)?.toFloatOrNull()
            val pitch = editor.pitch.takeIf(String::isNotBlank)?.toFloatOrNull()
            if (editor.speed.isNotBlank() && speechRate == null ||
                editor.pitch.isNotBlank() && pitch == null
            ) return null.also { toast(application.getString(R.string.cloud_tts_system_params_numeric)) }
            if (speechRate != null && (!speechRate.isFinite() || speechRate !in 0.1f..4f) ||
                pitch != null && (!pitch.isFinite() || pitch !in 0.1f..4f)
            ) return null.also { toast(application.getString(R.string.cloud_tts_system_params_range)) }
            SystemTtsVoiceConfig(speechRate = speechRate, pitch = pitch)
        } else null
        val now = System.currentTimeMillis()
        val id = SpeechIdentity.voiceId(editor.engineType, editor.engineId, speakerId)
        val old = editor.editingVoiceId?.let { editingId -> voices.firstOrNull { it.id == editingId } }
            ?: voices.firstOrNull { it.id == id }
        return ReadAloudVoice(
            id = id,
            engineType = editor.engineType,
            engineId = editor.engineId,
            speakerId = speakerId.trim(),
            displayName = editor.voiceName.trim().ifBlank { engineName(editor.engineType, editor.engineId) },
            traitsJson = request?.let { value -> GSON.toJson(CloudTtsVoiceConfig(
                locale = value.locale,
                style = value.style,
                role = value.role,
                instructions = value.instructions,
                automaticEmotion = editor.automaticEmotion,
                characterPersonality = editor.characterPersonality,
                thoughtPerformance = editor.thoughtPerformance,
                speed = value.speed,
                pitch = value.pitch,
                volume = value.volume,
                format = value.format,
            )) } ?: systemConfig?.let(GSON::toJson) ?: "{}",
            emotionCatalogJson = GSON.toJson(
                _uiState.value.discoveredVoices.firstOrNull { it.id == editor.voiceId }?.styles.orEmpty()
            ),
            createdAt = old?.createdAt ?: now,
            revision = (old?.revision ?: 0L) + 1L,
            updatedAt = now,
        )
    }

    private fun buildRequest(editor: TtsVoicePresetEditorUi, voiceId: String): CloudTtsSynthesisRequest? {
        val speed = editor.speed.toFloatOrNull()
        val pitch = editor.pitch.toFloatOrNull()
        val volume = editor.volume.toFloatOrNull()
        if (speed == null || pitch == null || volume == null) {
            toast(application.getString(R.string.cloud_tts_numeric_voice_parameters))
            return null
        }
        return CloudTtsSynthesisRequest(
            text = application.getString(R.string.cloud_tts_voice_preview_text),
            voiceId = voiceId,
            locale = editor.locale,
            style = editor.style,
            role = editor.role,
            instructions = editor.instructions,
            speed = speed,
            pitch = pitch,
            volume = volume,
            format = editor.format.ifBlank { "mp3" },
        )
    }

    private fun engineOptions(): kotlinx.collections.immutable.ImmutableList<TtsEngineOptionUi> =
        buildList {
            addAll(systemEngines.map { engine ->
                TtsEngineOptionUi(
                    engineType = ReadAloudVoice.ENGINE_SYSTEM,
                    engineId = engine.sourceId,
                    title = engine.displayName,
                    summary = application.getString(R.string.cloud_tts_system_engine_summary, engine.providerName),
                    catalogHint = application.getString(R.string.cloud_tts_system_catalog_hint),
                )
            })
            addAll(engines.map { engine ->
                TtsEngineOptionUi(
                    engineType = ReadAloudVoice.ENGINE_CLOUD,
                    engineId = engine.id,
                    title = engine.name,
                    summary = application.getString(R.string.cloud_tts_cloud_engine_summary, engine.provider.profile.displayName),
                    catalogHint = when (engine.provider.profile.voiceCatalogType) {
                        CloudTtsVoiceCatalogType.BuiltIn -> application.getString(R.string.cloud_tts_builtin_catalog)
                        CloudTtsVoiceCatalogType.Remote -> application.getString(R.string.cloud_tts_remote_catalog)
                        CloudTtsVoiceCatalogType.Partial -> application.getString(R.string.cloud_tts_partial_catalog)
                    },
                    remoteCatalog = engine.provider.profile.voiceCatalogType ==
                        CloudTtsVoiceCatalogType.Remote,
                )
            })
            addAll(httpEngines.map { engine ->
                TtsEngineOptionUi(
                    engineType = ReadAloudVoice.ENGINE_HTTP,
                    engineId = engine.sourceId,
                    title = engine.displayName,
                    summary = application.getString(R.string.cloud_tts_http_engine_summary),
                    catalogHint = application.getString(R.string.cloud_tts_http_catalog_hint),
                )
            })
        }.toImmutableList()

    private fun engineName(engineType: String, engineId: String): String = when (engineType) {
        ReadAloudVoice.ENGINE_SYSTEM -> systemEngines.firstOrNull {
            it.sourceId == engineId
        }?.displayName ?: engineId
        ReadAloudVoice.ENGINE_CLOUD -> engines.firstOrNull { it.id == engineId }?.name ?: engineId
        ReadAloudVoice.ENGINE_HTTP -> httpEngines.firstOrNull {
            it.sourceId == engineId
        }?.displayName ?: engineId
        else -> engineId
    }

    private fun defaultFormat(engineType: String, engineId: String): String =
        if (engineType == ReadAloudVoice.ENGINE_CLOUD) {
            engines.firstOrNull { it.id == engineId }
                ?.provider?.profile?.audioFormats?.firstOrNull().orEmpty().ifBlank { "mp3" }
        } else {
            ""
        }

    private fun formatOptions(
        engineType: String,
        engineId: String,
    ): kotlinx.collections.immutable.ImmutableList<String> =
        if (engineType == ReadAloudVoice.ENGINE_CLOUD) {
            engines.firstOrNull { it.id == engineId }
                ?.provider?.profile?.audioFormats.orEmpty().toImmutableList()
        } else {
            persistentListOf()
        }

    private fun deleteEngine(id: String) = viewModelScope.launch {
        voices.filter { it.engineId == id }.forEach { voiceGateway.deleteVoice(it) }
        engines.firstOrNull { it.id == id }?.let { engineGateway.delete(it) }
    }
    private fun deleteVoice(id: String) = viewModelScope.launch {
        voices.firstOrNull { it.id == id }?.let { voiceGateway.deleteVoice(it) }
    }
    private fun toast(message: String) { _effects.tryEmit(CloudTtsEffect.ShowToast(message)) }
    private fun showError(message: String) { _uiState.update { it.copy(activeDialog = CloudTtsDialog.Error(message)) } }
    private fun copyError() {
        val message = (_uiState.value.activeDialog as? CloudTtsDialog.Error)?.message ?: return
        _effects.tryEmit(CloudTtsEffect.CopyText(message))
    }
    private fun formatError(error: Throwable): String = buildString {
        append(application.getString(R.string.cloud_tts_request_failed))
        generateSequence(error) { it.cause }.mapNotNull { cause ->
            cause.localizedMessage?.takeIf(String::isNotBlank)?.let {
                "${cause::class.java.simpleName}: $it"
            }
        }.distinct().toList().takeIf { it.isNotEmpty() }?.let {
            append("\n\n").append(it.joinToString("\nCaused by: "))
        }
    }
}

private const val DEFAULT_ENGINE_VOICE_ID = "__engine_default__"
