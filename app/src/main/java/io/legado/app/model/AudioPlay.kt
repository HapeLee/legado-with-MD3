package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getBookSource
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object AudioPlay : CoroutineScope by MainScope() {

    private const val SEEK_SAVE_DELAY_MS = 300L
    private val readSavePermit = Semaphore(1)
    private val otherSettingsGateway get() = GlobalContext.get().get<OtherSettingsGateway>()
    private val readSettingsGateway get() = GlobalContext.get().get<ReadSettingsGateway>()

    enum class PlayMode {
        LIST_END_STOP,
        SINGLE_LOOP,
        RANDOM,
        LIST_LOOP;

        fun next(): PlayMode {
            return when (this) {
                LIST_END_STOP -> SINGLE_LOOP
                SINGLE_LOOP -> RANDOM
                RANDOM -> LIST_LOOP
                LIST_LOOP -> LIST_END_STOP
            }
        }
    }

    var playMode = PlayMode.LIST_END_STOP
    var speed = 1f  // 播放倍速（进程内记忆，AUDIO_SPEED 事件回写）
    var status = Status.STOP
    private var activityContext: Context? = null
    private var serviceContext: Context? = null
    private val context: Context get() = activityContext ?: serviceContext ?: appCtx
    var callback: CallBack? = null
    var book: Book? = null
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durChapter: BookChapter? = null
    var durPlayUrl = ""
    var durLyric: String? = null
    var durAudioSize = 0
    var inBookshelf = false
    var bookSource: BookSource? = null

    private data class ChapterLoad(val bookUrl: String, val index: Int, val job: Job)

    // 加载由主线程管理，换章或停止只取消当前章节的请求。
    private var chapterLoad: ChapterLoad? = null

    fun changePlayMode() {
        playMode = playMode.next()
        book?.setPlayMode(playMode.ordinal)
        saveRead()
        postEvent(EventBus.PLAY_MODE_CHANGED, playMode)
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> { action = IntentAction.upPlayMode }
        }
    }

    fun upData(book: Book) {
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            stopPlay()
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            durPlayUrl = ""
            durAudioSize = 0
        }
        upDurChapter()
    }

    fun resetData(book: Book) {
        stop()
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        bookSource = book.getBookSource()
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        durPlayUrl = ""
        durAudioSize = 0
        // 恢复本书记忆的播放模式
        PlayMode.entries.getOrNull(book.getPlayMode())?.let {
            playMode = it
            postEvent(EventBus.PLAY_MODE_CHANGED, it)
        }
        upDurChapter()
        SourceCallBack.callBackBook(SourceCallBack.START_READ, bookSource, book, durChapter)
        postEvent(EventBus.AUDIO_BUFFER_PROGRESS, 0)
    }

    private fun cancelChapterLoad() {
        val load = chapterLoad
        chapterLoad = null
        load?.job?.cancel()
        if (load != null) {
            durLyric = null
            callback?.upLyric(null)
        }
        upLoading(false)
    }

    fun loadOrUpPlayUrl() {
        if (durPlayUrl.isEmpty()) {
            loadPlayUrl()
        } else {
            upPlayUrl()
        }
    }

    /**
     * 加载播放URL
     */
    private fun loadPlayUrl() {
        val currentBook = book
        val source = bookSource
        if (currentBook == null || source == null) {
            appCtx.toastOnUi("book or source is null")
            return
        }
        val bookUrl = currentBook.bookUrl
        val index = durChapterIndex
        if (chapterLoad?.let { it.bookUrl == bookUrl && it.index == index } == true) return
        if (chapterLoad != null) cancelChapterLoad()

        val job = launch(start = CoroutineStart.LAZY) {
            val requestJob = coroutineContext.job
            var playbackRequested = false
            try {
                val chapter = withContext(IO) { appDb.bookChapterDao.getChapter(bookUrl, index) }
                if (!isCurrentChapterLoad(bookUrl, index, requestJob)) return@launch
                upDurChapter(chapter)
                if (chapter == null) return@launch
                if (chapter.isVolume) {
                    skipTo(index + 1)
                    return@launch
                }
                val content = withContext(IO) { WebBook.getContentAwait(source, currentBook, chapter) }
                if (!isCurrentChapterLoad(bookUrl, index, requestJob)) return@launch
                if (content.isEmpty()) {
                    appCtx.toastOnUi("未获取到资源链接")
                    return@launch
                }
                durPlayUrl = content
                durLyric = chapter.getVariable("lyric").takeIf { it.isNotBlank() }
                upPlayUrl()
                playbackRequested = true
                // 重新加载歌词，确保循环播放时歌词进度重置
                callback?.upLyric(durLyric)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("获取资源链接出错\n$error", error, true)
            } finally {
                if (chapterLoad?.job === requestJob) {
                    chapterLoad = null
                    if (!playbackRequested) {
                        upLoading(false)
                        durLyric = null
                        callback?.upLyric(null)
                    }
                }
            }
        }
        chapterLoad = ChapterLoad(bookUrl, index, job)
        upLoading(true)
        job.start()
    }

    private fun isCurrentChapterLoad(bookUrl: String, index: Int, job: Job) =
        chapterLoad?.job === job && book?.bookUrl == bookUrl && durChapterIndex == index

    private fun upPlayUrl() {
        if (isPlayToEnd()) {
            playNew()
        } else {
            play()
        }
    }

    /**
     * 播放当前章节
     */
    fun play() {
        context.startService<AudioPlayService> {
            action = IntentAction.play
        }
    }

    /**
     * 从头播放新章节
     */
    private fun playNew() {
        context.startService<AudioPlayService> {
            action = IntentAction.playNew
        }
    }

    /**
     * 更新当前章节
     */
    fun upDurChapter() {
        val book = book ?: return
        upDurChapter(appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex))
    }

    private fun upDurChapter(chapter: BookChapter?) {
        durChapter = chapter
        durAudioSize = chapter?.end?.toInt() ?: 0
        val title = chapter?.title ?: appCtx.getString(R.string.data_loading)
        postEvent(EventBus.AUDIO_SUB_TITLE, title)
        postEvent(EventBus.AUDIO_SIZE, durAudioSize)
        postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
    }

    fun pause(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.pause
            }
        }
    }

    fun resume(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.resume
            }
        }
    }

    fun stop() {
        cancelChapterLoad()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stop
            }
        }
    }

    fun adjustSpeed(adjust: Float) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustSpeed
                putExtra("adjust", adjust)
            }
        }
    }

    fun adjustGain(gain: Int) {
        book?.let {
            it.setAudioGain(gain)
            // 增益需随书持久化，否则重启/重新拉取书籍后丢失
            Coroutine.async { it.save() }
        }
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustGain
                putExtra("gain", gain)
            }
        }
    }

    fun adjustProgress(position: Int) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustProgress
                putExtra("position", position)
            }
        } else {
            seekPositionChanged(position)
            postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
        }
    }

    fun skipTo(index: Int) {
        if (index !in 0 until simulatedChapterSize) return
        stopPlay()
        durChapterIndex = index
        durChapterPos = 0
        durPlayUrl = ""
        saveRead()
        loadPlayUrl()
    }

    fun prev() {
        skipTo(durChapterIndex - 1)
    }

    fun next() {
        if (simulatedChapterSize <= 0) return
        stopPlay()
        when (playMode) {
            PlayMode.LIST_END_STOP -> {
                if (durChapterIndex + 1 < simulatedChapterSize) {
                    durChapterIndex += 1
                    durChapterPos = 0
                    durPlayUrl = ""
                    saveRead()
                    loadPlayUrl()
                }
            }

            PlayMode.SINGLE_LOOP -> {
                durChapterPos = 0
                durPlayUrl = ""
                saveRead()
                loadPlayUrl()
            }

            PlayMode.RANDOM -> {
                durChapterIndex = (0 until simulatedChapterSize).random()
                durChapterPos = 0
                durPlayUrl = ""
                saveRead()
                loadPlayUrl()
            }

            PlayMode.LIST_LOOP -> {
                durChapterIndex = (durChapterIndex + 1) % simulatedChapterSize
                durChapterPos = 0
                durPlayUrl = ""
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun setTimer(minute: Int) {
        val timer = PlaybackTimer.normalize(minute)
        if (AudioPlayService.isRun) {
            val intent = Intent(context, AudioPlayService::class.java)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", timer)
            context.startService(intent)
        } else {
            AudioPlayService.timeMinute = timer
            postEvent(EventBus.AUDIO_DS, timer)
        }
    }

    fun addTimer() {
        val intent = Intent(context, AudioPlayService::class.java)
        intent.action = IntentAction.addTimer
        context.startService(intent)
    }

    fun stopPlay() {
        if (chapterLoad != null) cancelChapterLoad()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stopPlay
            }
        }
    }

    fun saveRead() {
        val book = book ?: return
        val chapterIndex = durChapterIndex
        val chapterPosition = durChapterPos
        val source = bookSource
        // 记录调用时的章节与位置，按提交顺序保存，避免换章后读到新位置。
        Coroutine.async(semaphore = readSavePermit) {
            book.lastCheckCount = 0
            book.durChapterTime = System.currentTimeMillis()
            val chapterChanged = book.durChapterIndex != chapterIndex
            book.durChapterIndex = chapterIndex
            book.durChapterPos = chapterPosition
            if (chapterChanged) {
                appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex)?.let {
                    book.durChapterTitle = it.getDisplayTitle(
                        ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                        book.getUseReplaceRule(otherSettingsGateway.currentSettings.replaceEnableDefault),
                        chineseConverterType = readSettingsGateway.currentSettings.chineseConverterType,
                    )
                    SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, source, book, it)
                }
            }
            book.update()
        }
    }

    /**
     * 保存章节长度
     */
    fun saveDurChapter(audioSize: Long) {
        val chapter = durChapter ?: return
        Coroutine.async {
            durAudioSize = audioSize.toInt()
            chapter.end = audioSize
            appDb.bookChapterDao.update(chapter)
        }
    }

    private var seekSaveJob: Job? = null

    internal fun seekPositionChanged(position: Int) {
        durChapterPos = position.coerceAtLeast(0)
        seekSaveJob?.cancel()
        val bookUrl = book?.bookUrl ?: return
        val chapterIndex = durChapterIndex
        // 拖动中的事件只更新内存；停播后也可以保存最后一次定位。
        seekSaveJob = launch {
            delay(SEEK_SAVE_DELAY_MS)
            if (book?.bookUrl == bookUrl && durChapterIndex == chapterIndex) saveRead()
        }
    }

    fun playPositionChanged(position: Int) {
        durChapterPos = position
        if (seekSaveJob?.isActive != true) saveRead()
    }

    fun upLoading(loading: Boolean) {
        callback?.upLoading(loading)
    }

    private fun isPlayToEnd(): Boolean {
        return durChapterIndex + 1 == simulatedChapterSize
                && durChapterPos == durAudioSize
    }

    fun register(context: Context) {
        activityContext = context
        callback = context as CallBack
        // 主动更新歌词状态，确保重新进入时歌词控件能正确显示
        callback?.upLyric(durLyric)
    }

    fun unregister(context: Context) {
        if (activityContext === context) {
            activityContext = null
            callback = null
            // An active service owns pending chapter playback after the player UI closes.
            if (serviceContext == null && chapterLoad != null) cancelChapterLoad()
        }
    }

    fun registerService(context: Context) {
        serviceContext = context
    }

    fun unregisterService() {
        serviceContext = null
    }

    interface CallBack {

        fun upLoading(loading: Boolean)
        fun upLyric(lyric: String?)
        fun upLyricP(position: Int)

    }

}
