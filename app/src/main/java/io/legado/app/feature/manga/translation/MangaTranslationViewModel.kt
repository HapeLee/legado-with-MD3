package io.legado.app.feature.manga.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.MangaCloudOcrGateway
import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.model.AiCapability
import io.legado.app.domain.model.AiImageModels
import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrPermissionRequiredException
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import io.legado.app.domain.model.manga.MangaLocator
import io.legado.app.domain.model.manga.MangaTranslationPage
import io.legado.app.domain.model.manga.MangaTranslationProgress
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MangaTranslationViewModel(
    private val gateway: MangaTranslationGateway,
    profiles: AiProfileGateway,
    private val ocrModels: MangaOcrModelGateway,
    private val cloudOcr: MangaCloudOcrGateway,
    private val inpaintModels: MangaInpaintModelGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(MangaTranslationUiState())
    val uiState = _state.asStateFlow()
    private var bookUrl = ""
    private var pages = emptyList<MangaTranslationPage>()
    private var currentKey: String? = null
    private var active = false
    private var settingsJob: Job? = null
    private var task: Job? = null
    private var probe: Job? = null
    private var generation = 0L
    private var automaticTask = false
    private var automaticPaused = false
    private var runningPages = emptyList<MangaTranslationPage>()
    private val loadedPages = linkedMapOf<String, MangaTranslationPage>()
    private val observedPages = mutableMapOf<String, MangaTranslationPage>()
    private val checkedPages = mutableMapOf<String, MangaTranslationPage>()
    private var modelDownload: Job? = null
    private var inpaintDownload: Job? = null
    private var cloudConfigJob: Job? = null
    private var cloudSaveJob: Job? = null
    private var cloudBaseline: MangaCloudOcrConfig? = null
    private var permissionBlocked = false
    private val failedAutomatic = mutableSetOf<String>()

    private fun resetAutomaticFailures() {
        permissionBlocked = false
        checkedPages.keys.removeAll(failedAutomatic)
        failedAutomatic.clear()
    }

    init {
        viewModelScope.launch { inpaintModels.state.collect { status -> _state.update { it.copy(inpaintModels = status.toImmutableMap()) } } }
        viewModelScope.launch { attempt { inpaintModels.refresh() } }
        viewModelScope.launch { ocrModels.state.collect { status -> _state.update { it.copy(ocrModels = status.toImmutableMap()) } } }
        viewModelScope.launch { attempt { ocrModels.refresh() } }
        viewModelScope.launch {
            combine(profiles.observeModels(), profiles.observeProviders()) { models, providers ->
                models.filter { it.enabled }.mapNotNull { model ->
                    val provider = providers.firstOrNull { it.id == model.providerId && it.enabled } ?: return@mapNotNull null
                    val capabilities = AiImageModels.capabilities(model.modelId, model.capabilities)
                    MangaTranslationModelUi(model.id, "${provider.name} / ${model.displayName}",
                        AiCapability.IMAGE_EDITING in capabilities && AiImageModels.editProtocol(provider.protocol) != null,
                        AiImageModels.isTextModel(provider.protocol, model.modelId, model.capabilities),
                        AiCapability.VISION in capabilities)
                }.toImmutableList()
            }.collect { models -> _state.update { it.copy(models = models) } }
        }
    }

    fun onIntent(intent: MangaTranslationIntent) {
        when (intent) {
            MangaTranslationIntent.BindGlobal -> bindGlobal()
            is MangaTranslationIntent.SetUseGlobalSettings -> if (!_state.value.globalMode) {
                stop()
                val book = bookUrl
                viewModelScope.launch { attempt { gateway.setUseGlobalSettings(book, intent.enabled) } }
            }
            is MangaTranslationIntent.Bind -> bind(intent)
            MangaTranslationIntent.Open -> {
                _state.update { it.copy(open = true) }
                val book = bookUrl
                if (!_state.value.globalMode) viewModelScope.launch { attempt { val terms = gateway.glossary(book); if (book == bookUrl) _state.update { it.copy(glossaryDraft = terms.entries.joinToString("\n") { e -> "${e.key}=${e.value}" }) } } }
            }
            MangaTranslationIntent.Close -> _state.update { it.copy(open = false) }
            is MangaTranslationIntent.EditCloudOcr -> if (!_state.value.cloudLoading && !_state.value.cloudSaving) {
                _state.update { it.copy(cloudDraft = intent.transform(it.cloudDraft), cloudSaved = false) }
            }
            MangaTranslationIntent.SaveCloudOcr -> {
                val provider = _state.value.cloudProvider
                val baseline = cloudBaseline
                if (provider != null && baseline != null && !_state.value.cloudLoading && !_state.value.cloudSaving) {
                    stop()
                    val draft = _state.value.cloudDraft
                    _state.update { it.copy(cloudSaving = true, cloudSaved = false) }
                    cloudSaveJob = viewModelScope.launch {
                        try { attempt {
                            cloudOcr.update(provider) { it.mergeDraft(baseline, draft) }
                            if (_state.value.cloudProvider == provider) _state.update { it.copy(cloudSaved = true, error = null) }
                            resetAutomaticFailures()
                        } } finally { if (_state.value.cloudProvider == provider) _state.update { it.copy(cloudSaving = false) } }
                    }
                }
            }
            is MangaTranslationIntent.ChangeSettings -> {
                val previous = _state.value.settings
                val next = intent.transform(previous)
                if (previous.automatic && !next.automatic) automaticPaused = true
                if ((!next.automatic && previous.automatic && automaticTask) || previous.copy(automatic = false) != next.copy(automatic = false)) stop()
                val book = bookUrl
                val global = _state.value.globalMode
                viewModelScope.launch { attempt {
                    if (global) gateway.updateGlobal { it.copy(defaults = intent.transform(it.defaults).copy(automatic = false)) }
                    else gateway.update(book, intent.transform)
                } }
            }
            is MangaTranslationIntent.ChangeGlobalSettings -> {
                viewModelScope.launch { attempt { gateway.updateGlobal(intent.transform) } }
            }
            is MangaTranslationIntent.EditGlossary -> _state.update { it.copy(glossaryDraft = intent.text) }
            MangaTranslationIntent.SaveGlossary -> if (!_state.value.globalMode) {
                stop()
                val book = bookUrl
                val draft = _state.value.glossaryDraft
                viewModelScope.launch { attempt {
                    val terms = draft.lineSequence().filter { it.isNotBlank() }.associate { line ->
                        val separator = line.indexOf('=')
                        require(separator > 0 && separator < line.lastIndex) { "Use original=translation on each line" }
                        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                    }
                    gateway.setGlossary(book, terms)
                    if (book == bookUrl) { _state.update { it.copy(error = null) }; resetAutomaticFailures(); refresh() }
                } }
            }
            is MangaTranslationIntent.Translate -> start(targets(intent.chapter, intent.page), intent.force)
            is MangaTranslationIntent.Recompose -> start(targets(intent.chapter, intent.page), recompose = true)
            MangaTranslationIntent.Cancel -> { automaticPaused = true; stop() }
            MangaTranslationIntent.Deactivate -> { active = false; stop() }
            is MangaTranslationIntent.ClearPage -> if (intent.page.bookUrl == bookUrl && !_state.value.busy) {
                val page = intent.page
                failedAutomatic.add(page.key)
                viewModelScope.launch { attempt {
                    gateway.clearPage(page)
                    if (page.bookUrl == bookUrl) _state.update { it.copy(images = (it.images - page.key).toImmutableMap()) }
                } }
            }
            MangaTranslationIntent.Clear -> if (!_state.value.globalMode) {
                automaticPaused = true
                stop()
                val book = bookUrl
                viewModelScope.launch { attempt {
                    gateway.clear(book)
                    if (book == bookUrl) { _state.update { it.copy(images = persistentMapOf(), ai = null, error = null, diagnosticPath = null) }; failedAutomatic.clear(); checkedPages.clear() }
                } }
            }
            is MangaTranslationIntent.DownloadOcr -> if (intent.engine.downloadable && modelDownload?.isCompleted != false) {
                _state.update { it.copy(error = null, ocrOperation = intent.engine) }
                modelDownload = viewModelScope.launch {
                    try { attempt { ocrModels.download(intent.engine) } }
                    finally { _state.update { it.copy(ocrOperation = null) } }
                }
            }
            is MangaTranslationIntent.CancelOcrDownload -> if (_state.value.ocrOperation == intent.engine) modelDownload?.cancel()
            is MangaTranslationIntent.DeleteOcr -> if (intent.engine.downloadable && modelDownload?.isCompleted != false) {
                stop()
                _state.update { it.copy(ocrOperation = intent.engine) }
                modelDownload = viewModelScope.launch {
                    try { attempt { ocrModels.delete(intent.engine) } }
                    finally { _state.update { it.copy(ocrOperation = null) } }
                }
            }
            is MangaTranslationIntent.DownloadInpaint -> if (intent.engine.downloadable && inpaintDownload?.isCompleted != false) {
                _state.update { it.copy(error = null, inpaintOperation = intent.engine) }
                inpaintDownload = viewModelScope.launch {
                    try { attempt { inpaintModels.download(intent.engine) } }
                    finally { _state.update { it.copy(inpaintOperation = null) } }
                }
            }
            is MangaTranslationIntent.CancelInpaintDownload -> if (_state.value.inpaintOperation == intent.engine) inpaintDownload?.cancel()
            is MangaTranslationIntent.DeleteInpaint -> if (intent.engine.downloadable && inpaintDownload?.isCompleted != false) {
                stop()
                _state.update { it.copy(inpaintOperation = intent.engine) }
                inpaintDownload = viewModelScope.launch {
                    try { attempt { inpaintModels.delete(intent.engine) } }
                    finally { _state.update { it.copy(inpaintOperation = null) } }
                }
            }
        }
    }

    private fun bindGlobal() {
        if (_state.value.globalMode) return
        stop(); settingsJob?.cancel(); cloudConfigJob?.cancel(); cloudSaveJob?.cancel(); cloudBaseline = null
        bookUrl = ""; pages = emptyList(); currentKey = null; active = false
        loadedPages.clear(); observedPages.clear(); checkedPages.clear(); failedAutomatic.clear()
        _state.update { MangaTranslationUiState(globalMode = true, models = it.models, ocrModels = it.ocrModels,
            ocrOperation = it.ocrOperation, inpaintModels = it.inpaintModels, inpaintOperation = it.inpaintOperation) }
        settingsJob = viewModelScope.launch { attempt {
            gateway.globalSettings().collect { global ->
                _state.update { it.copy(globalSettings = global, settings = global.defaults, loaded = true) }
                if (global.defaults.locator == MangaLocator.CLOUD && _state.value.cloudProvider != global.defaults.cloudOcrProvider)
                    loadCloudConfig(global.defaults.cloudOcrProvider)
            }
        } }
    }

    private fun bind(intent: MangaTranslationIntent.Bind) {
        val changedBook = bookUrl != intent.bookUrl || _state.value.globalMode
        val changedPage = currentKey != intent.currentKey
        if (changedBook) {
            stop(); settingsJob?.cancel(); cloudConfigJob?.cancel(); cloudSaveJob?.cancel(); cloudBaseline = null; failedAutomatic.clear()
            loadedPages.clear(); observedPages.clear(); checkedPages.clear(); automaticPaused = false; permissionBlocked = false
            bookUrl = intent.bookUrl
            _state.update { MangaTranslationUiState(models = it.models, ocrModels = it.ocrModels, ocrOperation = it.ocrOperation,
                inpaintModels = it.inpaintModels, inpaintOperation = it.inpaintOperation) }
        }
        val available = (intent.sourcePages + intent.loadedPages).filter { it.bookUrl == bookUrl }.distinctBy { it.key }
        val changedSources = available.filter { observedPages[it.key]?.let { old -> old != it } == true }.map { it.key }.toSet()
        if (changedSources.isNotEmpty()) {
            if (runningPages.any { it.key in changedSources }) stop()
            changedSources.forEach { key -> failedAutomatic.remove(key); checkedPages.remove(key); loadedPages.remove(key) }
            _state.update { it.copy(images = (it.images - changedSources).toImmutableMap()) }
        }
        available.forEach { observedPages[it.key] = it }
        intent.loadedPages.filter { it.bookUrl == bookUrl }.forEach { loadedPages[it.key] = it }
        pages = intent.pages.filter { it.bookUrl == bookUrl }
        currentKey = intent.currentKey
        active = intent.active
        if (changedPage) _state.update { it.copy(error = null, diagnosticPath = null) }
        _state.update { it.copy(currentPageAvailable = pages.any { page -> page.key == currentKey }, currentPageKey = currentKey, chapterPages = pages.size, chapterPageKeys = pages.map { page -> page.key }.toImmutableList()) }
        if (!active) stop()
        if (changedBook && bookUrl.isNotBlank()) {
            settingsJob = viewModelScope.launch {
                attempt {
                    combine(gateway.settings(bookUrl), gateway.globalSettings(), gateway.useGlobalSettings(bookUrl)) { settings, global, inherit ->
                        Triple(settings, global, inherit)
                    }.collect { (settings, global, inherit) ->
                        val previous = _state.value.settings
                        val outputChanged = previous.copy(automatic = false) != settings.copy(automatic = false)
                        if (outputChanged) { stop(); resetAutomaticFailures() }
                        if (previous.automatic != settings.automatic) {
                            automaticPaused = false
                            resetAutomaticFailures()
                            if (automaticTask && !settings.automatic) stop()
                        }
                        _state.update { it.copy(settings = settings, globalSettings = global, useGlobalSettings = inherit, loaded = true) }
                        if (settings.locator == MangaLocator.CLOUD && _state.value.cloudProvider != settings.cloudOcrProvider) loadCloudConfig(settings.cloudOcrProvider)
                        refresh()
                    }
                }
            }
        } else refresh()
    }

    private fun loadCloudConfig(provider: MangaCloudOcrProvider) {
        cloudConfigJob?.cancel()
        cloudSaveJob?.cancel()
        cloudBaseline = null
        _state.update { it.copy(cloudProvider = provider, cloudLoading = true, cloudSaving = false, cloudSaved = false,
            cloudDraft = io.legado.app.domain.model.manga.MangaCloudOcrConfig()) }
        cloudConfigJob = viewModelScope.launch {
            try { attempt {
                cloudOcr.observe(provider).collect { config ->
                    if (_state.value.cloudProvider == provider) {
                        val previous = cloudBaseline
                        cloudBaseline = config
                        _state.update { it.copy(cloudLoading = false,
                            cloudDraft = if (previous == null) config else config.mergeDraft(previous, it.cloudDraft)) }
                        if (previous != null && previous != config) { resetAutomaticFailures(); refresh() }
                    }
                }
            } } finally { if (_state.value.cloudProvider == provider) _state.update { it.copy(cloudLoading = false) } }
        }
    }

    private fun refresh() {
        probe?.cancel()
        if (!active || !_state.value.loaded) return
        if (task?.isActive == true) return
        val candidates = (loadedPages.values + pages.filter { it.key == currentKey }).distinctBy { it.key }
        val token = generation
        probe = viewModelScope.launch {
            try {
                for (page in candidates) {
                    if (checkedPages[page.key] == page) continue
                    val result = try { gateway.cached(page) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (token != generation || page.bookUrl != bookUrl) return@launch
                        checkedPages[page.key] = page
                        failedAutomatic += page.key
                        _state.update { it.copy(error = error.message?.take(400) ?: error.javaClass.simpleName) }
                        continue
                    }
                    if (token != generation || page.bookUrl != bookUrl) return@launch
                    checkedPages[page.key] = page
                    if (result != null) {
                        _state.update { it.copy(images = (it.images + (page.key to MangaTranslatedImageUi(result.imagePath, result.revision, result.canRecompose))).toImmutableMap()) }
                    } else {
                        _state.update { it.copy(images = (it.images - page.key).toImmutableMap()) }
                    }
                }
                if (!automaticPaused && !permissionBlocked && _state.value.settings.automatic) {
                    loadedPages.values.firstOrNull { it.key !in _state.value.images && it.key !in failedAutomatic }
                        ?.let { start(listOf(it), automatic = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token == generation) _state.update { it.copy(error = error.message?.take(400)) }
            }
        }
    }

    private fun targets(chapter: Boolean, page: MangaTranslationPage?): List<MangaTranslationPage> = when {
        chapter -> pages
        page != null -> listOf(page).filter { it.bookUrl == bookUrl }
        else -> pages.filter { it.key == currentKey }
    }

    private fun start(selected: List<MangaTranslationPage>, force: Boolean = false, automatic: Boolean = false, recompose: Boolean = false) {
        if (selected.isEmpty() || !_state.value.loaded || task?.isActive == true) return
        if (!automatic) permissionBlocked = false
        val token = ++generation
        automaticTask = automatic
        runningPages = selected
        _state.update { it.copy(busy = true, done = 0, total = selected.size, progress = MangaTranslationProgress(), ai = null, diagnosticPath = null,
            elapsedSeconds = 0, phaseStartedAtSeconds = 0, error = null) }
        task = viewModelScope.launch {
            var failingPage: String? = null
            val started = System.nanoTime()
            val timer = launch {
                while (isActive) {
                    delay(1000)
                    if (generation == token) _state.update { it.copy(elapsedSeconds = ((System.nanoTime() - started) / 1_000_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                }
            }
            try {
                // Let the gateway report its localized missing-anchor error when the chapter
                // has no saved text. This fallback still cannot start an AI request.
                val targets = (if (recompose && selected.size > 1) selected.filter { failingPage = it.key; gateway.cached(it)?.canRecompose == true } else selected)
                    .ifEmpty { selected.take(1) }
                failingPage = null
                if (generation == token) _state.update { it.copy(total = targets.size) }
                targets.forEachIndexed { index, page ->
                    failingPage = page.key
                    val onProgress: (MangaTranslationProgress) -> Unit = { progress ->
                        if (generation == token) _state.update { it.copy(progress = progress, ai = progress.ai ?: it.ai,
                            diagnosticPath = progress.diagnosticPath ?: it.diagnosticPath,
                            phaseStartedAtSeconds = if (it.progress.stage != progress.stage) it.elapsedSeconds else it.phaseStartedAtSeconds) }
                    }
                    val result = if (recompose) gateway.recompose(page, onProgress) else gateway.translate(page, force, onProgress)
                    if (generation != token || page.bookUrl != bookUrl) return@launch
                    checkedPages[page.key] = page
                    failedAutomatic.remove(page.key)
                    _state.update { it.copy(done = index + 1, images = (it.images + (page.key to MangaTranslatedImageUi(result.imagePath, result.revision, result.canRecompose))).toImmutableMap()) }
                    failingPage = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == token) {
                    failingPage?.let(failedAutomatic::add)
                    if (error is MangaCloudOcrPermissionRequiredException) permissionBlocked = true
                    _state.update { it.copy(error = error.message?.take(400) ?: error.javaClass.simpleName) }
                }
            } finally {
                timer.cancel()
                if (generation == token) {
                    task = null
                    automaticTask = false
                    runningPages = emptyList()
                    _state.update { it.copy(busy = false, ai = it.ai?.copy(running = false, thinking = false)) }
                    refresh()
                }
            }
        }
    }

    private fun stop() {
        generation++
        probe?.cancel(); probe = null
        task?.cancel(); task = null; automaticTask = false
        runningPages = emptyList()
        _state.update { it.copy(busy = false, ai = it.ai?.copy(running = false, thinking = false)) }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _state.update { it.copy(error = error.message?.take(400) ?: error.javaClass.simpleName) } }
    }
}
