package io.legado.app.service.readaloud

import android.app.Application
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.help.config.AppConfigStore
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.playback.PlaybackCapsuleGatewayAdapter
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
import org.junit.Assert.assertNull
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
class PlaybackCapsuleGatewayAdapterTest {
    private val dispatcher = StandardTestDispatcher()
    private var previousBook: Book? = null
    private var previousChapter: BookChapter? = null
    private var previousChapterIndex = 0
    private var previousStatus = Status.STOP

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        previousChapter = AudioPlay.durChapter
        previousChapterIndex = AudioPlay.durChapterIndex
        previousBook = AudioPlay.book
        previousStatus = AudioPlay.status
        assertTrue(!AudioPlayService.isRun)
        assertTrue(!BaseReadAloudService.isRun)
        AudioPlay.book = Book(bookUrl = "audio-a", name = "有声书")
        AudioPlay.status = Status.STOP
    }

    @After
    fun tearDown() {
        AudioPlay.durChapter = previousChapter
        AudioPlay.durChapterIndex = previousChapterIndex
        AudioPlay.book = previousBook
        AudioPlay.status = previousStatus
        Dispatchers.resetMain()
    }

    private fun gateway() = PlaybackCapsuleGatewayAdapter(
        RuntimeEnvironment.getApplication(), ReadAloudSessionStore(),
    )

    @Test
    fun loadedBookWithoutServiceRemainsAResumablePausedCapsule() = runTest(dispatcher) {
        val gateway = gateway()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.state.collect { } }
        runCurrent()
        assertNull(gateway.state.value.source)
        // 旧模型可能残留 PLAY；没有服务时不能显示为播放中。
        AudioPlay.status = Status.PLAY
        gateway.prepareAudioBook("audio-a")
        runCurrent()
        assertEquals(PlaybackCapsuleSource.AudioBook, gateway.state.value.source)
        assertEquals("audio-a", gateway.state.value.bookUrl)
        assertTrue(gateway.state.value.isPaused)
        gateway.stop(PlaybackCapsuleSource.AudioBook)
        runCurrent()
        assertNull(gateway.state.value.source)
    }

    @Test
    fun stoppingOldServiceDoesNotClearNewPreparedBook() = runTest(dispatcher) {
        val gateway = gateway()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.state.collect { } }
        gateway.setSessionAvailable(PlaybackCapsuleSource.AudioBook, true)
        gateway.prepareAudioBook("audio-a")
        runCurrent()
        AudioPlay.book = Book(bookUrl = "audio-b", name = "第二本")
        gateway.prepareAudioBook("audio-b")
        // resetData 停止旧服务的回调可能晚于新书加载。
        gateway.setSessionAvailable(PlaybackCapsuleSource.AudioBook, false)
        runCurrent()
        assertEquals("audio-b", gateway.state.value.bookUrl)
        assertEquals(PlaybackCapsuleSource.AudioBook, gateway.state.value.source)
        assertTrue(gateway.state.value.isPaused)
    }

    @Test
    fun chapterMetadataFollowsTheLoadedAudioBook() = runTest(dispatcher) {
        AudioPlay.durChapterIndex = 2
        AudioPlay.durChapter = BookChapter(bookUrl = "audio-a", title = "第三章", index = 2)
        val gateway = gateway()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.state.collect { } }
        gateway.prepareAudioBook("audio-a")
        runCurrent()
        assertEquals("第三章", gateway.state.value.chapterTitle)
        assertEquals(2, gateway.state.value.chapterIndex)
        AudioPlay.book = Book(bookUrl = "audio-b")
        gateway.prepareAudioBook("audio-b")
        runCurrent()
        // 新书目录尚未加载时不能显示上一书的章节标题。
        assertEquals("", gateway.state.value.chapterTitle)
    }

    @Test
    fun destroyingItsOwnServiceClearsPreparedSession() = runTest(dispatcher) {
        val gateway = gateway()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.state.collect { } }
        gateway.prepareAudioBook("audio-a")
        gateway.setSessionAvailable(PlaybackCapsuleSource.AudioBook, true)
        runCurrent()
        gateway.setSessionAvailable(PlaybackCapsuleSource.AudioBook, false)
        runCurrent()
        assertNull(gateway.state.value.source)
    }
}
