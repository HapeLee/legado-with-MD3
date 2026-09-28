package io.legado.app.ui.book.audio

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.OtherSettingsRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.config.AppConfigStore
import io.legado.app.service.playback.PlaybackCapsuleGatewayAdapter
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.AudioPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AudioPlayViewModelInitializationTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private lateinit var database: AppDatabase
    private var previousLyric: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        previousLyric = AudioPlay.durLyric
    }

    @After
    fun tearDown() {
        viewModels.clear()
        AudioPlay.durLyric = previousLyric
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun openingWithoutLyricsHasEmptyInitialList() {
        AudioPlay.durLyric = null

        assertTrue(createViewModel().uiState.value.lyricLines.isEmpty())
    }

    @Test
    fun openingWithBlankLyricsHasEmptyInitialList() {
        AudioPlay.durLyric = ""

        assertTrue(createViewModel().uiState.value.lyricLines.isEmpty())
    }

    @Test
    fun initialLyricsRemainCachedAcrossUiUpdates() = runTest(dispatcher) {
        AudioPlay.durLyric = "[00:01.50]第一行\n[00:02]第二行"
        val viewModel = createViewModel()
        val initialLines = viewModel.uiState.value.lyricLines
        assertEquals(
            listOf(AudioLyricLine(1500, "第一行"), AudioLyricLine(2000, "第二行")),
            initialLines,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        runCurrent()
        viewModel.onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Timer))
        runCurrent()

        assertEquals(AudioPlaySheet.Timer, viewModel.uiState.value.activeSheet)
        assertSame(initialLines, viewModel.uiState.value.lyricLines)
    }

    private fun createViewModel(): AudioPlayViewModel {
        val application = RuntimeEnvironment.getApplication()
        val books = BookRepository(database.bookDao, database.bookChapterDao, database)
        val otherSettings = OtherSettingsRepository()
        val aloudSettings = ReadAloudSettingsRepository()
        return AudioPlayViewModel(
            application,
            AudioPlayCoordinator(
                application, books, otherSettings, aloudSettings,
                PlaybackCapsuleGatewayAdapter(application, ReadAloudSessionStore())
            ),
            books,
            otherSettings,
            aloudSettings,
            ReadSettingsRepository(SettingsRepository()),
        ).also { viewModels.put("audio", it) }
    }
}
