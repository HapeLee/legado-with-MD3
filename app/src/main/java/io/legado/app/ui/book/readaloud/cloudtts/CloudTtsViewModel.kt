package io.legado.app.ui.book.readaloud.cloudtts

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.CloudTtsSynthesisRequest
import io.legado.app.domain.model.readaloud.CloudTtsVoiceCatalogType
import io.legado.app.domain.model.readaloud.CloudTtsVoiceConfig
import io.legado.app.domain.model.readaloud.ReadAloudEngineSelection
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.domain.model.readaloud.TtsEngineDescriptor
import io.legado.app.domain.model.readaloud.profile
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.readaloud.playback.CloudTtsAudioSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsFileSynthesizer
import io.legado.app.help.readaloud.playback.SystemTtsVoiceCatalog
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isDataUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CloudTtsViewModel(
    private val application: Application,
    private val engineGateway: CloudTtsEngineGateway,
    private val httpTtsEngineGateway: HttpTtsEngineGateway,
    private val voiceGateway: ReadAloudVoiceGateway,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val uploadRepository: UploadRepository,
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
    private var bookUrl: String? = null
    private var bookEngineValue: String? = null

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
            }.collect { (allEngines, allHttpEngines, allSavedVoices) ->
                val configuredHttpEngineIds = allHttpEngines.mapTo(mutableSetOf()) { it.sourceId }
                val orphanedVoiceIds = allSavedVoices.asSequence()
                    .filter { it.engineType == ReadAloudVoice.ENGINE_HTTP }
                    .filter { it.managedBy == ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS }
                    .filter { it.engineId !in configuredHttpEngineIds }
                    .mapTo(mutableSetOf()) { it.id }
                allSavedVoices
                    .filter { it.id in orphanedVoiceIds }
                    .forEach { voiceGateway.deleteVoice(it) }
                val savedVoices = allSavedVoices.filterNot { it.id in orphanedVoiceIds }
                engines = allEngines
                httpEngines = allHttpEngines
                voices = savedVoices
                _uiState.update { state -> state.copy(
                    loading = false,
                    engines = allEngines.map { engine ->
                        CloudTtsEngineItemUi(
                            engine.id,
                            engine.name,
                            engine.provider.profile.displayName,
                            isDefaultEngine(ReadAloudVoice.ENGINE_CLOUD, engine.id),
                        )
                    }.toImmutableList(),
                    systemEngines = systemEngineItems(),
                    httpEngines = allHttpEngines.map { engine ->
                        TtsManagedEngineItemUi(
                            engineType = ReadAloudVoice.ENGINE_HTTP,
                            engineId = engine.sourceId,
                            title = engine.displayName,
                            summary = engine.providerName,
                            selected = isDefaultEngine(ReadAloudVoice.ENGINE_HTTP, engine.sourceId),
                            loginUrl = engine.loginUrl,
                        )
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
            _uiState.update {
                it.copy(
                    availableEngines = engineOptions(),
                    systemEngines = systemEngineItems(),
                )
            }
        }
    }

    fun onIntent(intent: CloudTtsIntent) {
        when (intent) {
            is CloudTtsIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
            is CloudTtsIntent.SetBookContext -> setBookContext(intent.bookUrl)
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
            is CloudTtsIntent.SetDefaultEngine -> setDefaultEngine(
                intent.engineType,
                intent.engineId
            )

            CloudTtsIntent.ApplyDefaultEngineGlobally -> applyPendingDefaultEngine(forBook = false)
            CloudTtsIntent.ApplyDefaultEngineForBook -> applyPendingDefaultEngine(forBook = true)
            is CloudTtsIntent.EditHttpTts -> editHttpTts(intent.engineId)
            is CloudTtsIntent.SaveHttpTts -> saveHttpTts(intent.value)
            is CloudTtsIntent.DeleteHttpTts -> deleteHttpTts(intent.engineId)
            is CloudTtsIntent.OpenHttpTtsLogin -> intent.engineId.toLongOrNull()?.let {
                _effects.tryEmit(CloudTtsEffect.OpenHttpTtsLogin(it))
            }

            CloudTtsIntent.DismissHttpTtsEditor -> _uiState.update { it.copy(httpTtsEditor = null) }
            is CloudTtsIntent.ImportHttpTtsSource -> importHttpTtsSource(intent.text)
            CloudTtsIntent.ImportHttpTtsFile -> _effects.tryEmit(CloudTtsEffect.OpenHttpTtsImportPicker)
            is CloudTtsIntent.ImportHttpTtsFileSelected -> importHttpTtsFile(intent.uri)
            CloudTtsIntent.CancelHttpTtsImport -> _uiState.update { it.copy(httpTtsImportState = BaseImportUiState.Idle) }
            is CloudTtsIntent.ToggleHttpTtsImportSelection -> toggleHttpTtsImportSelection(intent.index)
            is CloudTtsIntent.ToggleHttpTtsImportAll -> toggleHttpTtsImportAll(intent.selected)
            is CloudTtsIntent.UpdateHttpTtsImportItem -> updateHttpTtsImportItem(
                intent.index,
                intent.value
            )

            CloudTtsIntent.SaveImportedHttpTts -> saveImportedHttpTts()
            CloudTtsIntent.ExportHttpTtsFile -> _effects.tryEmit(CloudTtsEffect.OpenHttpTtsExportPicker)
            is CloudTtsIntent.ExportHttpTtsFileSelected -> exportHttpTtsFile(intent.uri)
            CloudTtsIntent.ExportHttpTtsUrl -> exportHttpTtsUrl()
            CloudTtsIntent.ClearTtsCache -> {
                io.legado.app.utils.TTSCacheUtils.clearTtsCache()
                toast(application.getString(R.string.clear_cache_success))
            }

            is CloudTtsIntent.RequestDeleteVoice -> requestDeleteVoice(intent.id)
            CloudTtsIntent.ConfirmDeleteVoice -> confirmDeleteVoice()
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

    private fun systemEngineItems() = buildList {
        add(
            TtsManagedEngineItemUi(
                engineType = ReadAloudVoice.ENGINE_SYSTEM,
                engineId = "",
                title = application.getString(R.string.system_tts),
                selected = isDefaultEngine(ReadAloudVoice.ENGINE_SYSTEM, ""),
            )
        )
        addAll(systemEngines.map { engine ->
            TtsManagedEngineItemUi(
                engineType = ReadAloudVoice.ENGINE_SYSTEM,
                engineId = engine.sourceId,
                title = engine.displayName,
                summary = engine.providerName,
                selected = isDefaultEngine(ReadAloudVoice.ENGINE_SYSTEM, engine.sourceId),
            )
        })
    }.toImmutableList()

    private fun isDefaultEngine(engineType: String, engineId: String): Boolean {
        val value = bookEngineValue ?: readAloudSettingsGateway.currentSettings.ttsEngine
        return when (engineType) {
            ReadAloudVoice.ENGINE_HTTP -> value == engineId
            ReadAloudVoice.ENGINE_CLOUD -> GSON.fromJsonObject<ReadAloudEngineSelection>(value)
                .getOrNull()?.let { it.engineType == engineType && it.engineId == engineId } == true

            ReadAloudVoice.ENGINE_SYSTEM -> if (engineId.isBlank()) {
                value.isNullOrBlank()
            } else {
                GSON.fromJsonObject<SelectItem<String>>(value).getOrNull()?.value == engineId
            }

            else -> false
        }
    }

    private fun refreshEngineSelection() {
        _uiState.update { state ->
            state.copy(
                engines = engines.map { engine ->
                    CloudTtsEngineItemUi(
                        engine.id,
                        engine.name,
                        engine.provider.profile.displayName,
                        isDefaultEngine(ReadAloudVoice.ENGINE_CLOUD, engine.id),
                    )
                }.toImmutableList(),
                systemEngines = systemEngineItems(),
                httpEngines = httpEngines.map { engine ->
                    TtsManagedEngineItemUi(
                        engineType = ReadAloudVoice.ENGINE_HTTP,
                        engineId = engine.sourceId,
                        title = engine.displayName,
                        summary = engine.providerName,
                        selected = isDefaultEngine(ReadAloudVoice.ENGINE_HTTP, engine.sourceId),
                        loginUrl = engine.loginUrl,
                    )
                }.toImmutableList(),
            )
        }
    }

    private fun setDefaultEngine(engineType: String, engineId: String) = viewModelScope.launch {
        val (value, title) = when (engineType) {
            ReadAloudVoice.ENGINE_SYSTEM -> if (engineId.isBlank()) {
                null to application.getString(R.string.system_tts)
            } else {
                val name =
                    systemEngines.firstOrNull { it.sourceId == engineId }?.displayName ?: engineId
                GSON.toJson(SelectItem(name, engineId)) to name
            }

            ReadAloudVoice.ENGINE_HTTP -> engineId to
                    (httpEngines.firstOrNull { it.sourceId == engineId }?.displayName ?: engineId)

            ReadAloudVoice.ENGINE_CLOUD -> {
                val voice = voices.firstOrNull {
                    it.engineType == engineType && it.engineId == engineId && it.enabled && it.available
                }
                    ?: return@launch toast(application.getString(R.string.cloud_tts_default_requires_voice))
                val name = engines.firstOrNull { it.id == engineId }?.name ?: engineId
                GSON.toJson(
                    ReadAloudEngineSelection(
                        engineType = engineType,
                        engineId = engineId,
                        speakerId = voice.speakerId,
                        displayName = name,
                    )
                ) to name
            }

            else -> return@launch
        }
        if (bookUrl != null) {
            _uiState.update {
                it.copy(
                    activeDialog = CloudTtsDialog.DefaultEngineScope(
                        value,
                        title
                    )
                )
            }
        } else {
            applyDefaultEngine(value, forBook = false)
        }
    }

    private fun setBookContext(value: String?) = viewModelScope.launch {
        bookUrl = value
        bookEngineValue = withContext(Dispatchers.IO) {
            value?.let(appDb.bookDao::getBook)?.getTtsEngine()
        }
        refreshEngineSelection()
    }

    private fun applyPendingDefaultEngine(forBook: Boolean) = viewModelScope.launch {
        val dialog =
            _uiState.value.activeDialog as? CloudTtsDialog.DefaultEngineScope ?: return@launch
        applyDefaultEngine(dialog.value, forBook)
    }

    private suspend fun applyDefaultEngine(value: String?, forBook: Boolean) {
        if (forBook && bookUrl != null) {
            ReadBook.book?.takeIf { it.bookUrl == bookUrl }?.setTtsEngine(value)
            withContext(Dispatchers.IO) {
                appDb.bookDao.getBook(bookUrl!!)?.let { book ->
                    book.setTtsEngine(value)
                    appDb.bookDao.update(book)
                }
            }
            bookEngineValue = value
        } else {
            ReadBook.book?.takeIf { it.bookUrl == bookUrl }?.setTtsEngine(null)
            bookUrl?.let { url ->
                withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(url)?.let { book ->
                        book.setTtsEngine(null)
                        appDb.bookDao.update(book)
                    }
                }
            }
            readAloudSettingsGateway.update { it.copy(ttsEngine = value) }
            bookEngineValue = null
        }
        ReadAloud.upReadAloudClass()
        _uiState.update { it.copy(activeDialog = null) }
        refreshEngineSelection()
        toast(application.getString(R.string.read_aloud_default_engine_updated))
    }

    private fun editHttpTts(engineId: String?) = viewModelScope.launch {
        val value = withContext(Dispatchers.IO) {
            engineId?.toLongOrNull()?.let(appDb.httpTTSDao::get) ?: HttpTTS()
        }
        _uiState.update { it.copy(httpTtsEditor = value) }
    }

    private fun saveHttpTts(value: HttpTTS) = viewModelScope.launch {
        withContext(Dispatchers.IO) { appDb.httpTTSDao.insert(value) }
        _uiState.update { it.copy(httpTtsEditor = null) }
        toast(application.getString(R.string.success))
    }

    private fun deleteHttpTts(engineId: String) = viewModelScope.launch {
        val id = engineId.toLongOrNull() ?: return@launch
        withContext(Dispatchers.IO) { appDb.httpTTSDao.get(id)?.let(appDb.httpTTSDao::delete) }
        if (isDefaultEngine(ReadAloudVoice.ENGINE_HTTP, engineId)) {
            readAloudSettingsGateway.update { it.copy(ttsEngine = null) }
            ReadAloud.upReadAloudClass()
        }
    }

    private fun importHttpTtsFile(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            application.contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() }
        }
        if (!text.isNullOrBlank()) importHttpTtsSource(text)
    }

    private fun importHttpTtsSource(text: String) = viewModelScope.launch {
        _uiState.update { it.copy(httpTtsImportState = BaseImportUiState.Loading) }
        runCatching {
            val source = text.trim()
            val list = withContext(Dispatchers.IO) { parseHttpTtsSource(source) }
            val items = withContext(Dispatchers.IO) {
                list.map { value ->
                    val old = appDb.httpTTSDao.get(value.id)
                    val status = when {
                        old == null -> ImportStatus.New
                        value.lastUpdateTime > old.lastUpdateTime -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }
                    ImportItemWrapper(
                        data = value,
                        oldData = old,
                        isSelected = status != ImportStatus.Existing,
                        status = status,
                    )
                }
            }
            if (items.isEmpty()) throw NoStackTraceException(application.getString(R.string.wrong_format))
            BaseImportUiState.Success(source, items)
        }.onSuccess { result -> _uiState.update { it.copy(httpTtsImportState = result) } }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        httpTtsImportState = BaseImportUiState.Error(
                            error.localizedMessage ?: application.getString(R.string.wrong_format)
                        )
                    )
                }
            }
    }

    private suspend fun parseHttpTtsSource(text: String): List<HttpTTS> = when {
        text.isHttpTtsImportUri() -> Uri.parse(text).getQueryParameter("src")
            ?.let { parseHttpTtsSource(it) }
            ?: throw NoStackTraceException(application.getString(R.string.wrong_format))

        text.isJsonObject() -> listOf(HttpTTS.fromJson(text).getOrThrow())
        text.isJsonArray() -> HttpTTS.fromJsonArray(text).getOrThrow()
        text.isDataUrl() -> {
            val data = AppPattern.dataUriRegex.find(text)?.groupValues?.getOrNull(1)
                ?: throw NoStackTraceException(application.getString(R.string.wrong_format))
            parseHttpTtsSource(Base64.decode(data, Base64.DEFAULT).toString(Charsets.UTF_8))
        }

        text.isAbsUrl() -> parseHttpTtsSource(okHttpClient.newCallResponseBody {
            if (text.endsWith("#requestWithoutUA")) {
                url(text.substringBeforeLast("#requestWithoutUA")); header(AppConst.UA_NAME, "null")
            } else url(text)
        }.decompressed().text())

        else -> throw NoStackTraceException(application.getString(R.string.wrong_format))
    }

    private fun toggleHttpTtsImportSelection(index: Int) {
        val state =
            _uiState.value.httpTtsImportState as? BaseImportUiState.Success<HttpTTS> ?: return
        if (index !in state.items.indices) return
        val items = state.items.toMutableList()
        items[index] = items[index].copy(isSelected = !items[index].isSelected)
        _uiState.update { it.copy(httpTtsImportState = state.copy(items = items)) }
    }

    private fun toggleHttpTtsImportAll(selected: Boolean) {
        val state =
            _uiState.value.httpTtsImportState as? BaseImportUiState.Success<HttpTTS> ?: return
        _uiState.update {
            it.copy(
                httpTtsImportState = state.copy(
                items = state.items.map { item -> item.copy(isSelected = selected) }
            ))
        }
    }

    private fun updateHttpTtsImportItem(index: Int, value: HttpTTS) {
        val state =
            _uiState.value.httpTtsImportState as? BaseImportUiState.Success<HttpTTS> ?: return
        if (index !in state.items.indices) return
        val items = state.items.toMutableList()
        items[index] = items[index].copy(data = value)
        _uiState.update {
            it.copy(
                httpTtsImportState = state.copy(
                    items = items,
                    version = state.version + 1
                )
            )
        }
    }

    private fun saveImportedHttpTts() = viewModelScope.launch {
        val state = _uiState.value.httpTtsImportState as? BaseImportUiState.Success<HttpTTS>
            ?: return@launch
        val selected = state.items.filter { it.isSelected }.map { it.data }
        if (selected.isEmpty()) return@launch
        withContext(Dispatchers.IO) { appDb.httpTTSDao.insert(*selected.toTypedArray()) }
        _uiState.update { it.copy(httpTtsImportState = BaseImportUiState.Idle) }
        toast(application.getString(R.string.success))
    }

    private fun exportHttpTtsFile(uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val json = GSON.toJson(appDb.httpTTSDao.all)
            application.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
        toast(application.getString(R.string.export_success))
    }

    private fun exportHttpTtsUrl() = viewModelScope.launch {
        val url = withContext(Dispatchers.IO) {
            uploadRepository.upload(
                "httpTTS.json",
                GSON.toJson(appDb.httpTTSDao.all),
                "application/json"
            )
        }
        _effects.tryEmit(CloudTtsEffect.CopyText(url, application.getString(R.string.copy_url)))
    }

    private fun deleteEngine(id: String) = viewModelScope.launch {
        voices.filter { it.engineId == id }.forEach { voiceGateway.deleteVoice(it) }
        engines.firstOrNull { it.id == id }?.let { engineGateway.delete(it) }
        if (isDefaultEngine(ReadAloudVoice.ENGINE_CLOUD, id)) {
            readAloudSettingsGateway.update { it.copy(ttsEngine = null) }
            ReadAloud.upReadAloudClass()
        }
    }

    private fun requestDeleteVoice(id: String) {
        val voice = voices.firstOrNull {
            it.id == id && it.managedBy == ReadAloudVoice.MANAGED_BY_USER
        } ?: return
        _uiState.update {
            it.copy(
                activeDialog = CloudTtsDialog.DeleteVoice(
                    voice.id,
                    voice.displayName
                )
            )
        }
    }

    private fun confirmDeleteVoice() = viewModelScope.launch {
        val id = (_uiState.value.activeDialog as? CloudTtsDialog.DeleteVoice)?.id ?: return@launch
        val voice = voices.firstOrNull {
            it.id == id && it.managedBy == ReadAloudVoice.MANAGED_BY_USER
        }
        voice?.let { voiceGateway.deleteVoice(it) }
        val selection = GSON.fromJsonObject<ReadAloudEngineSelection>(
            readAloudSettingsGateway.currentSettings.ttsEngine
        ).getOrNull()
        if (voice != null && selection?.engineType == voice.engineType &&
            selection.engineId == voice.engineId && selection.speakerId == voice.speakerId
        ) {
            readAloudSettingsGateway.update { it.copy(ttsEngine = null) }
            ReadAloud.upReadAloudClass()
            refreshEngineSelection()
        }
        _uiState.update { it.copy(activeDialog = null) }
    }
    private fun toast(message: String) { _effects.tryEmit(CloudTtsEffect.ShowToast(message)) }
    private fun showError(message: String) { _uiState.update { it.copy(activeDialog = CloudTtsDialog.Error(message)) } }
    private fun copyError() {
        val message = (_uiState.value.activeDialog as? CloudTtsDialog.Error)?.message ?: return
        _effects.tryEmit(
            CloudTtsEffect.CopyText(
                message,
                application.getString(R.string.cloud_tts_error_copied),
            )
        )
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

private fun String.isHttpTtsImportUri(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    return uri.scheme in setOf("legado", "yuedu") &&
            uri.host == "import" && uri.path.equals("/httpTTS", ignoreCase = true)
}

private const val DEFAULT_ENGINE_VOICE_ID = "__engine_default__"
