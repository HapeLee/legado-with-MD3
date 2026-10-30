package io.legado.app.feature.manga.translation

import androidx.lifecycle.ViewModelStore
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.gateway.MangaCloudOcrGateway
import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.model.manga.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MangaTranslationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private val page = MangaTranslationPage("p1", "book", "image", "chapter", null)

    private class Gateway : MangaTranslationGateway {
        val config = MutableStateFlow(MangaTranslationSettings())
        val global = MutableStateFlow(MangaTranslationGlobalSettings())
        val inherit = MutableStateFlow(false)
        var calls = 0
        val translated = mutableListOf<Pair<MangaTranslationPage, Boolean>>()
        val recomposed = mutableListOf<MangaTranslationPage>()
        var wait: CompletableDeferred<Unit>? = null
        var cacheWait: CompletableDeferred<Unit>? = null
        var globalUpdateWait: CompletableDeferred<Unit>? = null
        var failure = false
        var permissionRequired = false
        val failedKeys = mutableSetOf<String>()
        val failedCacheKeys = mutableSetOf<String>()
        var recompositions = 0
        var trace: MangaTranslationAiProgress? = null
        var progressCallback: ((MangaTranslationProgress) -> Unit)? = null
        val saved = mutableMapOf<String, MangaTranslationResult>()
        override fun globalSettings() = global
        override fun useGlobalSettings(bookUrl: String) = inherit
        override suspend fun setUseGlobalSettings(bookUrl: String, enabled: Boolean) { inherit.value = enabled }
        override suspend fun updateGlobal(transform: (MangaTranslationGlobalSettings) -> MangaTranslationGlobalSettings) {
            globalUpdateWait?.await()
            global.value = transform(global.value)
        }
        override fun settings(bookUrl: String) = config
        override suspend fun update(bookUrl: String, transform: (MangaTranslationSettings) -> MangaTranslationSettings) { config.value = transform(config.value) }
        override suspend fun glossary(bookUrl: String) = emptyMap<String, String>()
        override suspend fun setGlossary(bookUrl: String, terms: Map<String, String>) = Unit
        override suspend fun cached(page: MangaTranslationPage): MangaTranslationResult? {
            cacheWait?.await()
            if (page.key in failedCacheKeys) error("Cannot read cache")
            return saved[page.key]
        }
        override suspend fun recompose(page: MangaTranslationPage, progress: (MangaTranslationProgress) -> Unit): MangaTranslationResult {
            recompositions++
            recomposed += page
            require(saved[page.key]?.canRecompose == true) { "No saved anchors" }
            if (failure) error("Failed to recompose")
            return MangaTranslationResult("/recomposed.png", "recomposed", true).also { saved[page.key] = it }
        }
        override suspend fun translate(page: MangaTranslationPage, force: Boolean, progress: (MangaTranslationProgress) -> Unit): MangaTranslationResult {
            calls++
            translated += page to force
            progressCallback = progress
            trace?.let { progress(MangaTranslationProgress(MangaTranslationStage.ANALYZING, ai = it)) }
            wait?.let { gate -> withContext(NonCancellable) { gate.await() } }
            if (permissionRequired) throw MangaCloudOcrPermissionRequiredException("Local network permission required")
            if (failure || page.key in failedKeys) error("Failed")
            return MangaTranslationResult("/translated.png", "revision", true).also { saved[page.key] = it }
        }
        override suspend fun clearPage(page: MangaTranslationPage) { saved.remove(page.key) }
        override suspend fun clear(bookUrl: String) { saved.clear() }
    }

    @Test fun `clearing one translated page preserves other pages and chapter completion updates`() = runTest(dispatcher) {
        val gateway = Gateway()
        val other = page.copy(key = "p2", imageUrl = "other")
        gateway.saved[page.key] = MangaTranslationResult("/one.png", "one", true)
        gateway.saved[other.key] = MangaTranslationResult("/two.png", "two", true)
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page, other), page.key, true, loadedPages = listOf(page, other)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.chapterTranslated)
        vm.onIntent(MangaTranslationIntent.ClearPage(page))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.chapterTranslated)
        assertFalse(page.key in vm.uiState.value.images)
        assertTrue(other.key in vm.uiState.value.images)
        assertEquals(0, gateway.calls)
        vm.onIntent(MangaTranslationIntent.Translate(chapter = true, force = true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.chapterTranslated)
        assertTrue(gateway.translated.all { it.second })
    }

    private class CloudConfigs : MangaCloudOcrGateway {
        val configs = MutableStateFlow(mapOf(MangaCloudOcrProvider.BAIDU to MangaCloudOcrConfig(apiKey = "old-key", secretKey = "old-secret")))
        override fun observe(provider: MangaCloudOcrProvider) = configs.map { it[provider] ?: MangaCloudOcrConfig() }.distinctUntilChanged()
        override suspend fun get(provider: MangaCloudOcrProvider) = configs.value[provider] ?: MangaCloudOcrConfig()
        override suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) {
            configs.update { it + (provider to transform(it[provider] ?: MangaCloudOcrConfig())) }
        }
    }

    private fun model(gateway: Gateway, ocr: MangaOcrModelGateway = object : MangaOcrModelGateway {
        override val state = MutableStateFlow(mapOf(MangaLocalOcrEngine.PADDLE_V5 to MangaOcrModelState(MangaOcrModelPhase.MISSING))).asStateFlow()
        override suspend fun refresh() = Unit
        override suspend fun download(engine: MangaLocalOcrEngine) = Unit
        override suspend fun delete(engine: MangaLocalOcrEngine) = Unit
    }, cloud: MangaCloudOcrGateway = object : MangaCloudOcrGateway {
        override fun observe(provider: MangaCloudOcrProvider) = flow { emit(get(provider)) }
        override suspend fun get(provider: MangaCloudOcrProvider) = MangaCloudOcrConfig()
        override suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) = Unit
    }, inpaint: MangaInpaintModelGateway = object : MangaInpaintModelGateway {
        override val state = MutableStateFlow(emptyMap<MangaInpaintEngine, MangaInpaintModelState>()).asStateFlow()
        override suspend fun refresh() = Unit
        override suspend fun download(engine: MangaInpaintEngine) = Unit
        override suspend fun delete(engine: MangaInpaintEngine) = Unit
    }, key: String = "translation"): MangaTranslationViewModel {
        val profiles = Proxy.newProxyInstance(AiProfileGateway::class.java.classLoader, arrayOf(AiProfileGateway::class.java)) { _, _, _ -> flowOf(emptyList<Any>()) } as AiProfileGateway
        return MangaTranslationViewModel(gateway, profiles, ocr, cloud, inpaint).also { store.put(key, it) }
    }

    @Test fun `global mode edits defaults without binding a book or submitting a translation`() = runTest(dispatcher) {
        val gateway = Gateway().apply { global.value = MangaTranslationGlobalSettings(defaults = MangaTranslationSettings(targetLanguage = "fr")) }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.BindGlobal); runCurrent()
        assertTrue(vm.uiState.value.globalMode)
        assertEquals("fr", vm.uiState.value.settings.targetLanguage)
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(fontScale = .75f) }); runCurrent()
        assertEquals(.75f, gateway.global.value.defaults.fontScale)
        assertEquals(1f, gateway.config.value.fontScale)
        vm.onIntent(MangaTranslationIntent.Translate(chapter = true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.SaveGlossary); runCurrent()
        assertEquals(0, gateway.calls)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        assertFalse(vm.uiState.value.globalMode)
        assertEquals(gateway.config.value, vm.uiState.value.settings)
    }

    @Test fun `reader inheritance switch observes and updates its own book setting`() = runTest(dispatcher) {
        val gateway = Gateway().apply { inherit.value = true }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        assertTrue(vm.uiState.value.useGlobalSettings)
        vm.onIntent(MangaTranslationIntent.SetUseGlobalSettings(false)); runCurrent()
        assertFalse(vm.uiState.value.useGlobalSettings)
        assertEquals(0, gateway.calls)
    }

    @Test fun `page menu targets the selected adjacent chapter without changing the current page`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        val selected = page.copy(key = "adjacent", imageUrl = "other-image", chapterSubject = "other chapter")
        vm.onIntent(MangaTranslationIntent.Translate(force = true, page = selected)); runCurrent()
        assertEquals(listOf(selected to true), gateway.translated)
        assertEquals(page.key, vm.uiState.value.currentPageKey)
        vm.onIntent(MangaTranslationIntent.Recompose(page = selected)); runCurrent()
        assertEquals(listOf(selected), gateway.recomposed)
        assertEquals(1, gateway.calls)
        vm.onIntent(MangaTranslationIntent.Translate(page = selected.copy(bookUrl = "other book"))); runCurrent()
        vm.onIntent(MangaTranslationIntent.Recompose(page = selected.copy(bookUrl = "other book"))); runCurrent()
        assertEquals(1, gateway.calls); assertEquals(1, gateway.recompositions)
    }

    @Test fun `inpaint model downloads survive book changes and cancellation without starting translation`() = runTest(dispatcher) {
        val status = MutableStateFlow(mapOf(MangaInpaintEngine.MI_GAN to MangaInpaintModelState(MangaInpaintModelPhase.MISSING)))
        val gate = CompletableDeferred<Unit>()
        var deleted = false
        val models = object : MangaInpaintModelGateway {
            override val state = status.asStateFlow()
            override suspend fun refresh() = Unit
            override suspend fun download(engine: MangaInpaintEngine) {
                status.value = mapOf(engine to MangaInpaintModelState(MangaInpaintModelPhase.DOWNLOADING, 50, 100))
                try { gate.await() } finally { status.value = mapOf(engine to MangaInpaintModelState(MangaInpaintModelPhase.MISSING)) }
            }
            override suspend fun delete(engine: MangaInpaintEngine) { deleted = true }
        }
        val gateway = Gateway()
        val vm = model(gateway, inpaint = models)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.DownloadInpaint(MangaInpaintEngine.MI_GAN)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Bind("other", emptyList(), null, false)); runCurrent()
        assertEquals(MangaInpaintEngine.MI_GAN, vm.uiState.value.inpaintOperation)
        assertEquals(MangaInpaintModelPhase.DOWNLOADING, vm.uiState.value.inpaintModels.getValue(MangaInpaintEngine.MI_GAN).phase)
        vm.onIntent(MangaTranslationIntent.CancelInpaintDownload(MangaInpaintEngine.MI_GAN)); runCurrent()
        assertNull(vm.uiState.value.inpaintOperation)
        assertEquals(MangaInpaintModelPhase.MISSING, vm.uiState.value.inpaintModels.getValue(MangaInpaintEngine.MI_GAN).phase)
        vm.onIntent(MangaTranslationIntent.DeleteInpaint(MangaInpaintEngine.MI_GAN)); runCurrent()
        assertTrue(deleted); assertEquals(0, gateway.calls)
    }

    @Test fun `OCR download cancellation retry and deletion are independent of book and sheet changes`() = runTest(dispatcher) {
        val status = MutableStateFlow(mapOf(MangaLocalOcrEngine.PADDLE_V5 to MangaOcrModelState(MangaOcrModelPhase.MISSING, totalBytes = 100)))
        var downloads = 0
        var deletions = 0
        var wait = CompletableDeferred<Unit>()
        val ocr = object : MangaOcrModelGateway {
            override val state = status.asStateFlow()
            override suspend fun refresh() = Unit
            override suspend fun download(engine: MangaLocalOcrEngine) {
                downloads++
                status.value = mapOf(engine to MangaOcrModelState(MangaOcrModelPhase.DOWNLOADING, 50, 100))
                try { wait.await(); status.value = mapOf(engine to MangaOcrModelState(MangaOcrModelPhase.READY, 100, 100)) }
                catch (cancelled: CancellationException) { status.value = mapOf(engine to MangaOcrModelState(MangaOcrModelPhase.MISSING, totalBytes = 100)); throw cancelled }
            }
            override suspend fun delete(engine: MangaLocalOcrEngine) { deletions++; status.value = mapOf(engine to MangaOcrModelState(MangaOcrModelPhase.MISSING, totalBytes = 100)) }
        }
        val vm = model(Gateway(), ocr)
        vm.onIntent(MangaTranslationIntent.DownloadOcr(MangaLocalOcrEngine.PADDLE_V5)); runCurrent()
        vm.onIntent(MangaTranslationIntent.DownloadOcr(MangaLocalOcrEngine.PADDLE_V5)); runCurrent()
        assertEquals(1, downloads)
        vm.onIntent(MangaTranslationIntent.Close)
        vm.onIntent(MangaTranslationIntent.Bind("another book", emptyList(), null, true)); runCurrent()
        assertEquals(MangaOcrModelPhase.DOWNLOADING, vm.uiState.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase)
        vm.onIntent(MangaTranslationIntent.CancelOcrDownload(MangaLocalOcrEngine.PADDLE_V6_SMALL)); runCurrent()
        assertEquals(MangaOcrModelPhase.DOWNLOADING, vm.uiState.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase)
        vm.onIntent(MangaTranslationIntent.CancelOcrDownload(MangaLocalOcrEngine.PADDLE_V5)); runCurrent()
        assertEquals(MangaOcrModelPhase.MISSING, vm.uiState.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase)
        wait = CompletableDeferred()
        vm.onIntent(MangaTranslationIntent.DownloadOcr(MangaLocalOcrEngine.PADDLE_V5)); runCurrent()
        wait.complete(Unit); runCurrent()
        assertEquals(MangaOcrModelPhase.READY, vm.uiState.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase)
        vm.onIntent(MangaTranslationIntent.DeleteOcr(MangaLocalOcrEngine.PADDLE_V5)); runCurrent()
        assertEquals(1, deletions)
        assertEquals(MangaOcrModelPhase.MISSING, vm.uiState.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase)
    }

    @Test fun `cloud provider drafts save globally without invoking translation and reload independently`() = runTest(dispatcher) {
        val configs = mutableMapOf(MangaCloudOcrProvider.BAIDU to MangaCloudOcrConfig(apiKey = "saved-baidu"))
        val cloud = object : MangaCloudOcrGateway {
            override fun observe(provider: MangaCloudOcrProvider) = flow { emit(get(provider)) }
            override suspend fun get(provider: MangaCloudOcrProvider) = configs[provider] ?: MangaCloudOcrConfig()
            override suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) {
                configs[provider] = transform(get(provider))
            }
        }
        val gateway = Gateway().apply { config.value = config.value.copy(locator = MangaLocator.CLOUD) }
        val vm = model(gateway, cloud = cloud)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        assertEquals("saved-baidu", vm.uiState.value.cloudDraft.apiKey)
        vm.onIntent(MangaTranslationIntent.EditCloudOcr { it.copy(apiKey = "updated-baidu", secretKey = "secret") })
        vm.onIntent(MangaTranslationIntent.SaveCloudOcr); runCurrent()
        assertTrue(vm.uiState.value.cloudSaved)
        assertEquals("updated-baidu", configs.getValue(MangaCloudOcrProvider.BAIDU).apiKey)
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(cloudOcrProvider = MangaCloudOcrProvider.TENCENT) }); runCurrent()
        assertEquals(MangaCloudOcrProvider.TENCENT, vm.uiState.value.cloudProvider)
        assertEquals("", vm.uiState.value.cloudDraft.apiKey)
        assertEquals(0, gateway.calls)
        assertEquals("updated-baidu", configs.getValue(MangaCloudOcrProvider.BAIDU).apiKey)
    }

    @Test fun `reader and global cloud editors synchronize credentials while retaining unsaved edits`() = runTest(dispatcher) {
        val cloud = CloudConfigs()
        val gateway = Gateway().apply {
            config.value = config.value.copy(locator = MangaLocator.CLOUD)
            global.value = global.value.copy(defaults = config.value)
        }
        val reader = model(gateway, cloud = cloud)
        val global = model(gateway, cloud = cloud, key = "global")
        reader.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true))
        global.onIntent(MangaTranslationIntent.BindGlobal)
        runCurrent()
        reader.onIntent(MangaTranslationIntent.EditCloudOcr { it.copy(accurate = false) })
        global.onIntent(MangaTranslationIntent.EditCloudOcr { it.copy(apiKey = "new-key", secretKey = "new-secret") })
        global.onIntent(MangaTranslationIntent.SaveCloudOcr)
        runCurrent()
        assertEquals("new-key", reader.uiState.value.cloudDraft.apiKey)
        assertEquals("new-secret", reader.uiState.value.cloudDraft.secretKey)
        assertFalse(reader.uiState.value.cloudDraft.accurate)
        reader.onIntent(MangaTranslationIntent.SaveCloudOcr)
        runCurrent()
        val saved = cloud.get(MangaCloudOcrProvider.BAIDU)
        assertEquals("new-key", saved.apiKey)
        assertEquals("new-secret", saved.secretKey)
        assertFalse(saved.accurate)
        assertEquals(saved, global.uiState.value.cloudDraft)
        assertEquals(0, gateway.calls)
    }

    @Test fun `saving a cloud draft merges against credentials updated before the observer resumes`() = runTest(dispatcher) {
        val cloud = CloudConfigs()
        val gateway = Gateway().apply { config.value = config.value.copy(locator = MangaLocator.CLOUD) }
        val vm = model(gateway, cloud = cloud)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.EditCloudOcr { it.copy(accurate = false) })
        cloud.configs.update { it + (MangaCloudOcrProvider.BAIDU to it.getValue(MangaCloudOcrProvider.BAIDU).copy(apiKey = "rotated-key")) }
        vm.onIntent(MangaTranslationIntent.SaveCloudOcr)
        runCurrent()
        assertEquals("rotated-key", cloud.get(MangaCloudOcrProvider.BAIDU).apiKey)
        assertFalse(cloud.get(MangaCloudOcrProvider.BAIDU).accurate)
        assertTrue(vm.uiState.value.cloudSaved)
    }

    @Test fun `automatic translation needs both switches and a loaded original rather than a viewport`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        val offscreen = page.copy(key = "offscreen", chapterSubject = "next chapter")
        val bind = MangaTranslationIntent.Bind("book", listOf(page), page.key, true, listOf(offscreen))
        vm.onIntent(bind); runCurrent()
        assertEquals(0, gateway.calls)
        vm.onIntent(MangaTranslationIntent.ChangeGlobalSettings { it.copy(automatic = true) }); runCurrent()
        assertEquals(0, gateway.calls)
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = true) }); runCurrent()
        assertEquals(listOf(offscreen to false), gateway.translated)
        assertFalse(vm.uiState.value.images.containsKey(page.key))
        repeat(3) { vm.onIntent(bind); runCurrent() }
        assertEquals(1, gateway.calls)
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = false) }); runCurrent()
        vm.onIntent(bind.copy(loadedPages = listOf(page))); runCurrent()
        assertEquals(1, gateway.calls)
        assertTrue(vm.uiState.value.globalSettings.automatic)
    }

    @Test fun `enabled switches do not translate a page that has not finished loading`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true) }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        assertEquals(0, gateway.calls)
    }

    @Test fun `loaded pages queue during a request and changing chapters does not cancel them`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        val next = page.copy(key = "p2", chapterSubject = "chapter 2")
        val third = page.copy(key = "p3", chapterSubject = "chapter 2")
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true, listOf(page))); runCurrent()
        assertTrue(vm.uiState.value.busy)
        val nextChapter = MangaTranslationIntent.Bind("book", listOf(next, third), next.key, true, listOf(next, third))
        repeat(3) { vm.onIntent(nextChapter); runCurrent() }
        assertTrue(vm.uiState.value.busy)
        assertEquals(1, gateway.calls)
        gateway.wait!!.complete(Unit); runCurrent()
        assertEquals(listOf(page, next, third), gateway.translated.map { it.first })
        assertEquals(setOf(page.key, next.key, third.key), vm.uiState.value.images.keys)
        assertEquals(next.key, vm.uiState.value.currentPageKey)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun `one failed automatic page does not block later loaded pages or retry on scrolling`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); failedKeys += page.key }
        val vm = model(gateway)
        val next = page.copy(key = "p2")
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), next.key, true, listOf(page, next))
        vm.onIntent(bind); runCurrent()
        assertEquals(listOf(page, next), gateway.translated.map { it.first })
        assertNotNull(vm.uiState.value.images[next.key])
        repeat(3) { vm.onIntent(bind); runCurrent() }
        assertEquals(2, gateway.calls)
    }

    @Test fun `an unreadable cache blocks only its own automatic page without causing a paid fallback`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); failedCacheKeys += page.key }
        val vm = model(gateway)
        val next = page.copy(key = "p2")
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), next.key, true, listOf(page, next))
        vm.onIntent(bind); runCurrent()
        repeat(3) { vm.onIntent(bind); runCurrent() }
        assertEquals(listOf(next to false), gateway.translated)
        vm.onIntent(MangaTranslationIntent.ChangeGlobalSettings { it.copy(automatic = false) }); runCurrent()
        vm.onIntent(MangaTranslationIntent.ChangeGlobalSettings { it.copy(automatic = true) }); runCurrent()
        assertEquals(1, gateway.calls)
        gateway.failedCacheKeys.clear()
        vm.onIntent(MangaTranslationIntent.Translate(page = page)); runCurrent()
        assertEquals(page, gateway.translated.last().first)
    }

    @Test fun `loaded pages arriving during a manual task translate after that task finishes`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate(force = true)); runCurrent()
        val next = page.copy(key = "p2")
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page, next), page.key, true, listOf(page, next))); runCurrent()
        assertEquals(1, gateway.calls)
        gateway.wait!!.complete(Unit); runCurrent()
        assertEquals(listOf(page to true, next to false), gateway.translated)
    }

    @Test fun `a manual failure is not immediately retried by the automatic loaded queue`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred(); failedKeys += page.key }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true, listOf(page))); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        assertEquals(1, gateway.calls)
        assertNotNull(vm.uiState.value.error)
        gateway.failedKeys.clear()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        assertEquals(2, gateway.calls)
        assertNotNull(vm.uiState.value.images[page.key])
    }

    @Test fun `a failed chapter page does not block automatic translation of unattempted pages`() = runTest(dispatcher) {
        val gateway = Gateway().apply {
            config.value = config.value.copy(automatic = true)
            global.value = MangaTranslationGlobalSettings(true)
            failedKeys += page.key
        }
        val next = page.copy(key = "p2")
        val vm = model(gateway)
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), page.key, true)
        vm.onIntent(bind); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate(chapter = true)); runCurrent()
        vm.onIntent(bind.copy(loadedPages = listOf(page, next))); runCurrent()
        assertEquals(listOf(page to false, next to false), gateway.translated)
        assertNotNull(vm.uiState.value.images[next.key])
        repeat(2) { vm.onIntent(bind.copy(loadedPages = listOf(page, next))); runCurrent() }
        assertEquals(2, gateway.calls)
    }

    @Test fun `missing local permission pauses automatic work until an explicit retry succeeds`() = runTest(dispatcher) {
        val gateway = Gateway().apply {
            config.value = config.value.copy(automatic = true, locator = MangaLocator.CLOUD)
            global.value = MangaTranslationGlobalSettings(true)
            permissionRequired = true
        }
        val next = page.copy(key = "p2")
        val vm = model(gateway)
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), page.key, true, listOf(page, next))
        vm.onIntent(bind); runCurrent()
        repeat(3) { vm.onIntent(bind); runCurrent() }
        assertEquals(1, gateway.calls)
        assertNotNull(vm.uiState.value.error)
        gateway.permissionRequired = false
        vm.onIntent(MangaTranslationIntent.Translate(page = page)); runCurrent()
        assertEquals(listOf(page to false, page to false, next to false), gateway.translated)
        assertEquals(setOf(page.key, next.key), vm.uiState.value.images.keys)
    }

    @Test fun `leaving the reader preserves its automatic queue for a later active bind`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        val next = page.copy(key = "p2")
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), page.key, true, listOf(page, next))
        vm.onIntent(bind); runCurrent()
        vm.onIntent(MangaTranslationIntent.Deactivate); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy)
        assertEquals(1, gateway.calls)
        vm.onIntent(bind); runCurrent()
        assertNotNull(vm.uiState.value.images[next.key])
    }

    @Test fun `retrying a queued offscreen original removes its old identity until the new source loads`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        val offscreen = page.copy(key = "offscreen", chapterSubject = "next chapter")
        val bind = MangaTranslationIntent.Bind("book", listOf(page), page.key, true,
            loadedPages = listOf(page, offscreen), sourcePages = listOf(page, offscreen))
        vm.onIntent(bind); runCurrent()
        val revised = offscreen.copy(sourceRevision = 1)
        vm.onIntent(bind.copy(loadedPages = listOf(page), sourcePages = listOf(page, revised))); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        assertEquals(listOf(page to false), gateway.translated)
        vm.onIntent(bind.copy(loadedPages = listOf(page, revised), sourcePages = listOf(page, revised))); runCurrent()
        assertEquals(revised, gateway.translated.last().first)
        assertEquals(2, gateway.calls)
    }

    @Test fun `global default does not stop explicit book automatic translation`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); wait = CompletableDeferred() }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true, listOf(page))); runCurrent()
        assertTrue(vm.uiState.value.busy)
        vm.onIntent(MangaTranslationIntent.ChangeGlobalSettings { it.copy(automatic = false) }); runCurrent()
        assertTrue(vm.uiState.value.busy)
        gateway.wait!!.complete(Unit); runCurrent()
        assertNotNull(vm.uiState.value.images[page.key])
    }

    @Test fun `master switch does not cancel a manually requested translation`() = runTest(dispatcher) {
        val gateway = Gateway().apply { global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        vm.onIntent(MangaTranslationIntent.ChangeGlobalSettings { it.copy(automatic = false) }); runCurrent()
        assertTrue(vm.uiState.value.busy)
        gateway.wait!!.complete(Unit); runCurrent()
        assertNotNull(vm.uiState.value.images[page.key])
    }

    @Test fun `cancel pauses the automatic queue until a switch is enabled again`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); wait = CompletableDeferred() }
        val vm = model(gateway)
        val next = page.copy(key = "p2")
        val bind = MangaTranslationIntent.Bind("book", listOf(page, next), page.key, true, listOf(page, next))
        vm.onIntent(bind); runCurrent()
        vm.onIntent(MangaTranslationIntent.Cancel); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        repeat(3) { vm.onIntent(bind.copy(currentKey = next.key)); runCurrent() }
        assertEquals(1, gateway.calls)
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = false) }); runCurrent()
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(automatic = true) }); runCurrent()
        assertNotNull(vm.uiState.value.images[next.key])
    }

    @Test fun `saved loaded pages restore offscreen without requesting translation and inactive readers wait`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true) }
        val saved = page.copy(key = "saved")
        gateway.saved[saved.key] = MangaTranslationResult("/saved.png", "saved")
        val vm = model(gateway)
        val bind = MangaTranslationIntent.Bind("book", listOf(page), page.key, false, listOf(saved, page))
        vm.onIntent(bind); runCurrent()
        assertEquals(0, gateway.calls)
        vm.onIntent(bind.copy(active = true)); runCurrent()
        assertEquals(listOf(page to false), gateway.translated)
        assertEquals("/saved.png", vm.uiState.value.images[saved.key]?.path)
    }

    @Test fun `disabled automatic mode does not make a paid request`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true))
        runCurrent()
        assertEquals(0, gateway.calls)
        vm.onIntent(MangaTranslationIntent.Translate())
        runCurrent()
        assertEquals(1, gateway.calls)
        assertEquals("/translated.png", vm.uiState.value.images[page.key]?.path)
    }

    @Test fun `cancellation prevents late results from being attached to another book`() = runTest(dispatcher) {
        val gateway = Gateway().apply { wait = CompletableDeferred() }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        assertTrue(vm.uiState.value.busy)
        vm.onIntent(MangaTranslationIntent.Bind("other", listOf(page.copy(bookUrl = "other", key = "p2")), "p2", true)); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        assertTrue(vm.uiState.value.images.isEmpty())
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun `a failed automatic page is not retried repeatedly by bind events`() = runTest(dispatcher) {
        val gateway = Gateway().apply { config.value = config.value.copy(automatic = true); global.value = MangaTranslationGlobalSettings(true); failure = true }
        val vm = model(gateway)
        val bind = MangaTranslationIntent.Bind("book", listOf(page), page.key, true, listOf(page))
        vm.onIntent(bind); runCurrent()
        repeat(4) { vm.onIntent(bind); runCurrent() }
        assertEquals(1, gateway.calls)
        assertNotNull(vm.uiState.value.error)
        gateway.failure = false
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        assertEquals(2, gateway.calls)
        assertNull(vm.uiState.value.error)
    }

    @Test fun `refreshing original content removes its stale translated raster without an automatic charge`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        assertNotNull(vm.uiState.value.images[page.key])
        gateway.saved.clear() // The new source content has no matching saved artifact.
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page.copy(sourceRevision = 1)), page.key, true)); runCurrent()
        assertNull(vm.uiState.value.images[page.key])
        assertEquals(1, gateway.calls)
    }

    @Test fun `settings edits preserve the published image and explicit recompose never translates`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        vm.onIntent(MangaTranslationIntent.ChangeSettings { it.copy(fontScale = 1.5f, targetLanguage = "fr") }); runCurrent()
        assertEquals("/translated.png", vm.uiState.value.images[page.key]?.path)
        assertEquals(1, gateway.calls)
        vm.onIntent(MangaTranslationIntent.Recompose()); runCurrent()
        assertEquals(1, gateway.calls); assertEquals(1, gateway.recompositions)
        assertEquals("/recomposed.png", vm.uiState.value.images[page.key]?.path)
        gateway.failure = true
        vm.onIntent(MangaTranslationIntent.Recompose()); runCurrent()
        assertEquals("/recomposed.png", vm.uiState.value.images[page.key]?.path)
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun `reasoning remains inspectable through composition and closing while cancellation rejects late updates`() = runTest(dispatcher) {
        val gateway = Gateway().apply {
            wait = CompletableDeferred()
            trace = MangaTranslationAiProgress("request", "Model", reasoning = "provider reasoning", thinking = true)
        }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        assertEquals("provider reasoning", vm.uiState.value.ai?.reasoning)
        gateway.progressCallback!!(MangaTranslationProgress(MangaTranslationStage.ANALYZING,
            ai = gateway.trace!!.copy(running = false, thinking = false)))
        gateway.progressCallback!!(MangaTranslationProgress(MangaTranslationStage.COMPOSING))
        assertEquals("provider reasoning", vm.uiState.value.ai?.reasoning)
        assertEquals(MangaTranslationStage.COMPOSING, vm.uiState.value.progress.stage)
        vm.onIntent(MangaTranslationIntent.Close); runCurrent()
        assertTrue(vm.uiState.value.busy)
        vm.onIntent(MangaTranslationIntent.Cancel); runCurrent()
        gateway.progressCallback!!(MangaTranslationProgress(ai = gateway.trace!!.copy(reasoning = "late")))
        gateway.wait!!.complete(Unit); runCurrent()
        assertEquals("provider reasoning", vm.uiState.value.ai?.reasoning)
        assertFalse(vm.uiState.value.ai!!.running)
        assertFalse(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.images.isEmpty())
    }

    @Test fun `recreated reader restores saved image with automatic mode disabled`() = runTest(dispatcher) {
        val gateway = Gateway().apply { saved[page.key] = MangaTranslationResult("/saved.png", "saved", true) }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        assertEquals("/saved.png", vm.uiState.value.images[page.key]?.path)
        assertTrue(vm.uiState.value.currentPageCanRecompose)
        assertEquals(0, gateway.calls)
    }

    @Test fun `chapter recompose skips pages without saved anchors`() = runTest(dispatcher) {
        val gateway = Gateway().apply { saved[page.key] = MangaTranslationResult("/saved.png", "saved", true) }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page, page.copy(key = "untranslated")), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Recompose(chapter = true)); runCurrent()
        assertEquals(1, gateway.recompositions); assertEquals(0, gateway.calls)
        assertEquals(1, vm.uiState.value.total)
    }

    @Test fun `chapter with no saved anchors reports gateway error without translating`() = runTest(dispatcher) {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page, page.copy(key = "p2")), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Recompose(chapter = true)); runCurrent()
        assertEquals(0, gateway.calls)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun `refreshing a source during manual translation rejects its late result`() = runTest(dispatcher) {
        val gateway = Gateway().apply { wait = CompletableDeferred() }
        val vm = model(gateway)
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page), page.key, true)); runCurrent()
        vm.onIntent(MangaTranslationIntent.Translate()); runCurrent()
        vm.onIntent(MangaTranslationIntent.Bind("book", listOf(page.copy(sourceRevision = 1)), page.key, true)); runCurrent()
        gateway.wait!!.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.images[page.key])
        assertFalse(vm.uiState.value.busy)
        assertEquals(1, gateway.calls)
    }
}
