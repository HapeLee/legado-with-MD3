package io.legado.app.feature.settings.manga

import androidx.lifecycle.ViewModelStore
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.manga.MangaTranslationGlobalSettings
import io.legado.app.domain.model.settings.MangaSettings
import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class MangaConfigViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var failure: Exception? = null
    private val manga = object : MangaSettingsGateway {
        override val settings = MutableStateFlow(MangaSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (MangaSettings) -> MangaSettings) {
            failure?.let { throw it }
            settings.value = transform(settings.value)
        }
    }
    private val reading = object : ReadSettingsGateway {
        override val settings = MutableStateFlow(ReadSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) { settings.value = transform(settings.value) }
    }
    private val automatic = MutableStateFlow(MangaTranslationGlobalSettings())
    private var globalWrites = 0
    @Suppress("UNCHECKED_CAST")
    private val translation = Proxy.newProxyInstance(MangaTranslationGateway::class.java.classLoader,
        arrayOf(MangaTranslationGateway::class.java)) { _, method, args ->
        when (method.name) {
            "globalSettings" -> automatic
            "updateGlobal" -> {
                automatic.value = (args[0] as (MangaTranslationGlobalSettings) -> MangaTranslationGlobalSettings)(automatic.value)
                globalWrites++
                Unit
            }
            else -> error("Global settings must not access a book or start translation: ${method.name}")
        }
    } as MangaTranslationGateway

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model() = MangaConfigViewModel(manga, translation, reading).also { store.put("manga-config", it) }

    @Test fun `global settings observe reader changes and share the existing master switch without accessing a book`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        assertTrue(vm.uiState.value.translationLoaded)
        manga.settings.value = manga.currentSettings.copy(volumeKeyPage = true, scrollMode = 2)
        automatic.value = MangaTranslationGlobalSettings(true)
        runCurrent()
        assertTrue(vm.uiState.value.settings.volumeKeyPage)
        assertEquals(2, vm.uiState.value.settings.scrollMode)
        assertTrue(vm.uiState.value.automaticTranslation)
        vm.onIntent(MangaConfigIntent.SetAutomaticTranslation(false)); runCurrent()
        assertFalse(automatic.value.automatic)
        assertEquals(1, globalWrites)
    }

    @Test fun `changes use latest settings and retain unrelated reader configuration`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        manga.settings.value = manga.currentSettings.copy(menuBottomBarBlur = true)
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(showMangaUi = false) })
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(pageScaleType = 3) })
        runCurrent()
        assertTrue(manga.currentSettings.menuBottomBarBlur)
        assertFalse(manga.currentSettings.showMangaUi)
        assertEquals(3, manga.currentSettings.pageScaleType)
    }

    @Test fun `chapter counts use shared reading configuration and clamp supported ranges`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        vm.onIntent(MangaConfigIntent.SetNextChapters(500))
        vm.onIntent(MangaConfigIntent.SetPreviousChapters(-10)); runCurrent()
        assertEquals(100, reading.currentSettings.preDownloadNum)
        assertEquals(0, reading.currentSettings.preDownloadNumPrev)
        assertEquals(100, vm.uiState.value.nextChapters)
        assertEquals(MangaSettings().chapterPrefetchCount, manga.currentSettings.chapterPrefetchCount)
        reading.settings.value = reading.currentSettings.copy(preDownloadNumPrev = 7); runCurrent()
        assertEquals(7, vm.uiState.value.previousChapters)
    }

    @Test fun `write failures remain visible until dismissed while cancellation is not an error`() = runTest(dispatcher) {
        val vm = model(); runCurrent()
        failure = IllegalStateException("Storage unavailable")
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableGray = true) }); runCurrent()
        assertEquals("Storage unavailable", vm.uiState.value.error)
        assertFalse(manga.currentSettings.enableGray)
        vm.onIntent(MangaConfigIntent.DismissError)
        assertNull(vm.uiState.value.error)
        failure = CancellationException("Cancelled")
        vm.onIntent(MangaConfigIntent.ChangeSettings { it.copy(enableGray = true) }); runCurrent()
        assertNull(vm.uiState.value.error)
    }
}
